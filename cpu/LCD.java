import java.awt.Color;
import java.util.Arrays;

/**
 * Emulates the Gameboy LCD screen and rendering pipeline.
 */
public class LCD {

    // Screen dimensions
    public static final int LCD_WIDTH = 160;
    public static final int LCD_HEIGHT = 144;
    private static final int TOTAL_PIXELS = LCD_WIDTH * LCD_HEIGHT * 3;

    // Hardware Registers
    private static final int REG_LCDC = 0xFF40; // LCD Control
    private static final int REG_STAT = 0xFF41; // LCD Status
    private static final int REG_SCY  = 0xFF42; // Scroll Y
    private static final int REG_SCX  = 0xFF43; // Scroll X
    private static final int REG_LY   = 0xFF44; // LCD Y Coordinate
    private static final int REG_LYC  = 0xFF45; // LY Compare
    private static final int REG_WY   = 0xFF4A; // Window Y
    private static final int REG_WX   = 0xFF4B; // Window X
    private static final int OAM_BASE = 0xFE00; // Object Attribute Memory

    public final int[] buffer = new int[TOTAL_PIXELS];
    private final int[] gfx = new int[TOTAL_PIXELS];
    
    // Standard Gameboy palette representation
    private final Color[] colors = new Color[]{
        Color.WHITE, Color.LIGHT_GRAY, Color.DARK_GRAY, Color.BLACK
    };

    public int total = 0;
    private int lcdCycles = 0;
    
    private Memory memory;
    private cpu cpu;

    public LCD() {
        // Constructor remains empty as in the original
    }

    public void tick() {
        int scanline = memory.read8(REG_LY);

        // Determine and set LCD Mode
        if (scanline >= LCD_HEIGHT) {
            changeLCDMode(LcdMode.VBLANK);
        } else {
            if (lcdCycles <= LcdMode.HBLANK.cycles) {
                changeLCDMode(LcdMode.HBLANK);
            } else if (lcdCycles <= LcdMode.OAM.cycles) {
                changeLCDMode(LcdMode.OAM);
            } else if (lcdCycles <= LcdMode.DATA.cycles) {
                changeLCDMode(LcdMode.DATA);
            }
        }

        // Handle cycle timing (456 cycles per scanline)
        if (lcdCycles > 456) {
            if (scanline < LCD_HEIGHT) {
                drawScanline();
            } else if (scanline == LCD_HEIGHT) {
                // VBLANK start, push frame to buffer
                System.arraycopy(gfx, 0, buffer, 0, gfx.length);
                cpu.requestInterrupt(0);
            } else if (scanline > 153) {
                // Reset scanline after VBLANK ends
                total = 0;
                memory.directWrite8(REG_LY, 0);
            }

            lcdCycles = 0;
            incScanline();
        }

        lcdCycles++;
        total += lcdCycles;
    }

    private void drawScanline() {
        if (isBgDisplayEnabled()) {
            drawBackground();
        }
        if (isObjDisplayEnabled()) {
            drawSprites();
        }
    }

    private void drawBackground() {
        int backgroundTileDataStart = bgWinTileDataStart();
        boolean isSigned = (backgroundTileDataStart == 0x8800);
        
        int scrollX = memory.read8(REG_SCX);
        int scrollY = memory.read8(REG_SCY);
        int windowX = memory.read8(REG_WX) - 7;
        int windowY = memory.read8(REG_WY);
        int ly = memory.read8(REG_LY);

        boolean windowEnabled = isWindowEnabled() && (windowY <= ly);
        int y = windowEnabled ? (ly - windowY) % 256 : (ly + scrollY) % 256;

        for (int pixel = 0; pixel < LCD_WIDTH; ++pixel) {
            int x = (pixel + scrollX) % 256;

            if (windowEnabled && pixel >= windowX) {
                x = pixel - windowX;
            }

            int tileX = x / 8;
            int tileY = y / 8;
            int tileOffset = (tileY * 32) + tileX;

            int backgroundMapStart = windowEnabled ? windowTileMapStart() : bgTileMapStart();
            
            // Fetch tile identifier
            int tileNumber = memory.read8(backgroundMapStart + tileOffset);
            if (isSigned) {
                tileNumber = ((byte) tileNumber) + 128;
            }

            int tileAddress = backgroundTileDataStart + (tileNumber * 16) + ((y % 8) * 2);

            int data1 = memory.read8(tileAddress);
            int data2 = memory.read8(tileAddress + 1);

            int colorBit = 7 - (x % 8);
            
            // Extract the 2-bit color index
            int colorNumber = isBitSet(data2, colorBit) ? 2 : 0;
            if (isBitSet(data1, colorBit)) colorNumber |= 1;

            setPixel(pixel, ly, colorNumber);
        }
    }

    private void drawSprites() {
        boolean use8x16 = isBitSet(memory.read8(REG_LCDC), 2);
        int spriteHeight = use8x16 ? 16 : 8;
        int scanline = memory.read8(REG_LY);

        // Render up to 40 sprites
        for (int sprite = 0; sprite < 40; sprite++) {
            int index = sprite * 4;
            int yPos = memory.read8(OAM_BASE + index) - 16;
            int xPos = memory.read8(OAM_BASE + index + 1) - 8;
            int tileLocation = memory.read8(OAM_BASE + index + 2);
            int attributes = memory.read8(OAM_BASE + index + 3);

            boolean yFlip = isBitSet(attributes, 6);
            boolean xFlip = isBitSet(attributes, 5);

            // Check if sprite intercepts the current scanline
            if (scanline >= yPos && scanline < (yPos + spriteHeight)) {
                int line = scanline - yPos;

                if (yFlip) {
                    line = spriteHeight - 1 - line;
                }

                int dataAddress = (0x8000 + (tileLocation * 16)) + (line * 2);
                int data1 = memory.read8(dataAddress);
                int data2 = memory.read8(dataAddress + 1);

                for (int tilePixel = 7; tilePixel >= 0; --tilePixel) {
                    int colorBit = xFlip ? (7 - tilePixel) : tilePixel;

                    int colorNum = isBitSet(data2, colorBit) ? 2 : 0;
                    if (isBitSet(data1, colorBit)) colorNum |= 1;

                    // Color 0 is transparent for sprites
                    if (colorNum != 0) {
                        int pixel = xPos + (7 - tilePixel);
                        
                        // Prevent out-of-bounds drawing
                        if (pixel >= 0 && pixel < LCD_WIDTH) {
                            setPixel(pixel, scanline, colorNum);
                        }
                    }
                }
            }
        }
    }

    /**
     * Helper to write RGB values into the graphics array.
     */
    private void setPixel(int x, int y, int colorIndex) {
        int baseIndex = ((y * LCD_WIDTH) + x) * 3;
        gfx[baseIndex] = colors[colorIndex].getRed();
        gfx[baseIndex + 1] = colors[colorIndex].getGreen();
        gfx[baseIndex + 2] = colors[colorIndex].getBlue();
    }

    private void incScanline() {
        int scanline = memory.read8(REG_LY);
        memory.directWrite8(REG_LY, scanline + 1);
        checkLYC();
    }

    private void checkLYC() {
        int stat = memory.read8(REG_STAT);
        int scanline = memory.read8(REG_LY);
        int lyc = memory.read8(REG_LYC);

        if (scanline == lyc) {
            stat |= (1 << 2);
            // LYC Interrupt
            if (isBitSet(stat, 6)) {
                cpu.requestInterrupt(1);
            }
        } else {
            stat &= ~(1 << 2);
        }

        memory.directWrite8(REG_STAT, stat);
    }

    private void changeLCDMode(LcdMode newMode) {
        if (newMode == getLCDMode()) return;

        int stat = memory.read8(REG_STAT);

        switch (newMode) {
            case HBLANK:
                stat &= ~3; // Clear bottom 2 bits (Mode 0)
                if (isBitSet(stat, 3)) cpu.requestInterrupt(1);
                break;
            case VBLANK:
                stat = (stat & ~3) | 1; // Set Mode 1
                if (isBitSet(stat, 4)) cpu.requestInterrupt(1);
                break;
            case OAM:
                stat = (stat & ~3) | 2; // Set Mode 2
                if (isBitSet(stat, 5)) cpu.requestInterrupt(1);
                break;
            case DATA:
                stat |= 3; // Set Mode 3
                break;
        }

        memory.directWrite8(REG_STAT, stat);
    }

    private LcdMode getLCDMode() {
        int statMode = memory.read8(REG_STAT) & 0x3;
        switch (statMode) {
            case 0: return LcdMode.HBLANK;
            case 1: return LcdMode.VBLANK;
            case 2: return LcdMode.OAM;
            case 3: return LcdMode.DATA;
            default: return LcdMode.HBLANK; // Fallback
        }
    }

    public void reset() {
        Arrays.fill(gfx, 0);
        lcdCycles = 0;
    }

    public void setMemory(Memory memory) {
        this.memory = memory;
    }

    public void setCpu(cpu cpu) {
    this.cpu = cpu;
    }

    // --- Bitwise & Register Checking Helpers ---

    private boolean isBitSet(int value, int bitPosition) {
        return ((value >> bitPosition) & 1) != 0;
    }

    private boolean isBgDisplayEnabled() {
        return isBitSet(memory.read8(REG_LCDC), 0);
    }

    private boolean isObjDisplayEnabled() {
        return isBitSet(memory.read8(REG_LCDC), 1);
    }

    private boolean isWindowEnabled() {
        return isBitSet(memory.read8(REG_LCDC), 5);
    }

    private boolean isLcdEnabled() {
        return isBitSet(memory.read8(REG_LCDC), 7);
    }

    private int bgTileMapStart() {
        return isBitSet(memory.read8(REG_LCDC), 3) ? 0x9C00 : 0x9800;
    }

    private int bgWinTileDataStart() {
        return isBitSet(memory.read8(REG_LCDC), 4) ? 0x8000 : 0x8800;
    }

    private int windowTileMapStart() {
        return isBitSet(memory.read8(REG_LCDC), 6) ? 0x9C00 : 0x9800;
    }

    private int objSize() {
        return isBitSet(memory.read8(REG_LCDC), 2) ? 16 : 8;
    }


    enum LcdMode {
        HBLANK(20560.3137254902 / 70224),
        VBLANK(919.8035087719298 / 70224),
        OAM(52428.8 / 70224),
        DATA(24385.48837209302 / 70224);

        public final int cycles;

        LcdMode(double cycles) {
            this.cycles = (int) cycles;
        }
    }
}