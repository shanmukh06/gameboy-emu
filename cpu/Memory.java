import java.util.Arrays;

public class Memory {

  public final int[] io = new int[0x7F + 1];
  private final int[] bios = new int[256];
  private final int[] vram = new int[0x1FFF + 1];
  private final int[] wRam0 = new int[0xFFF + 1];
  private final int[] wRam1 = new int[0xFFF + 1];
  private final int[] oam = new int[0x9F + 1];
  private final int[] hRam = new int[0x7E + 1];

  public int joypad;
  int dividerVariable = 0;
  int currentClockSpeed = 1024;
  int timerVariable = 0;
  int memoryCycles = 0;

  private Cable linkCable;
  private int interruptEnable;
  private Cartridge cartridge;
  private boolean booting;

  // ------------------------------------------------------------------
  // Read
  // ------------------------------------------------------------------

  public int read8(int address) {
    if (cartridge == null) return 0xFF;
    if (address <= 0x3FFF) {
      if (booting && address < 0x100) return bios[address];
      return cartridge.read8(address);
    } else if (address <= 0x7FFF) {
      return cartridge.read8(address);
    } else if (address <= 0x9FFF) {
      return vram[address - 0x8000];
    } else if (address <= 0xBFFF) {
      return cartridge.read8(address);
    } else if (address <= 0xCFFF) {
      return wRam0[address - 0xC000];
    } else if (address <= 0xDFFF) {
      return wRam1[address - 0xD000];
    } else if (address <= 0xEFFF) {
      return wRam0[(address - 0x2000) - 0xC000];
    } else if (address <= 0xFDFF) {
      return wRam1[(address - 0x2000) - 0xD000];
    } else if (address <= 0xFE9F) {
      return oam[address - 0xFE00];
    } else if (address <= 0xFEFF) {
      return 0xFF;
    } else if (address <= 0xFF7F) {
      if (address == 0xFF00) return getJoypad();

      // Serial (Link Cable)
      if (linkCable != null) {
        if (address == 0xFF01) return linkCable.readSB(); // SB
        if (address == 0xFF02) return linkCable.readSC(); // SC
      }

      return io[address - 0xFF00];
    } else if (address <= 0xFFFE) {
      return hRam[address - 0xFF80];
    } else if (address == 0xFFFF) {
      return interruptEnable;
    } else {
      System.out.println("Can't read address " + Integer.toHexString(address));
      return 0;
    }
  }

  // ------------------------------------------------------------------
  // Write helpers
  // ------------------------------------------------------------------

  /** DMA transfer: copy 160 bytes from (data << 8) into OAM. */
  private void handleDMA(int data) {
    char src = (char) (data << 8);
    for (int i = 0; i < 0xA0; i++) {
      write8(0xFE00 + i, read8(src + i));
    }
  }

  /** Update timer control register and reset timerVariable if clock changed. */
  private void handleTimerControl(int data) {
    io[0xFF07 - 0xFF00] = data;

    int clockSpeed;
    switch (data & 0x03) {
      case 1:  clockSpeed = 16;   break;
      case 2:  clockSpeed = 64;   break;
      case 3:  clockSpeed = 256;  break;
      default: clockSpeed = 1024; break;
    }

    if (clockSpeed != currentClockSpeed) {
      timerVariable = 0;
      currentClockSpeed = clockSpeed;
    }
  }

  // ------------------------------------------------------------------
  // Write
  // ------------------------------------------------------------------

  public void write8(int address, int data) {
    data &= 0xFF;

    if (address >= 0x8000 && address <= 0x9FFF) {
      vram[address - 0x8000] = data;
    } else if (address <= 0xBFFF) {
      cartridge.write8(address, data);
    } else if (address <= 0xCFFF) {
      wRam0[address - 0xC000] = data;
    } else if (address <= 0xDFFF) {
      wRam1[address - 0xD000] = data;
    } else if (address <= 0xEFFF) {
      wRam0[(address - 0x2000) - 0xC000] = data;
    } else if (address <= 0xFDFF) {
      wRam1[(address - 0x2000) - 0xD000] = data;
    } else if (address <= 0xFE9F) {
      oam[address - 0xFE00] = data;
    } else if (address <= 0xFEFF) {
      return;
    } else if (address <= 0xFF7F) {

      if (address == 0xFF04 || address == 0xFF44) {
        data = 0;
      } else if (address == 0xFF46) {
        handleDMA(data);
      } else if (address == 0xFF07) {
        handleTimerControl(data);
        return; // handleTimerControl already writes io[]
      } else if (address == 0xFF50) {
        booting = false;
      }

      // Serial (Link Cable)
      if (linkCable != null) {
        if (address == 0xFF01) linkCable.writeSB(data); // SB
        if (address == 0xFF02) linkCable.writeSC(data); // SC
      }

      io[address - 0xFF00] = data;

    } else if (address <= 0xFFFE) {
      hRam[address - 0xFF80] = data;
    } else if (address == 0xFFFF) {
      interruptEnable = data;
    } else {
      System.out.println("Can't write address " + Integer.toHexString(address));
    }
  }

  /** Direct write bypasses IO register side-effects (used by PPU/DMA internally). */
  public void directWrite8(int address, int data) {
    data &= 0xFF;

    if (address >= 0x8000 && address <= 0x9FFF) {
      vram[address - 0x8000] = data;
    } else if (address <= 0xBFFF) {
      cartridge.write8(address, data);
    } else if (address <= 0xCFFF) {
      wRam0[address - 0xC000] = data;
    } else if (address <= 0xDFFF) {
      wRam1[address - 0xD000] = data;
    } else if (address <= 0xEFFF) {
      wRam0[(address - 0x2000) - 0xC000] = data;
    } else if (address <= 0xFDFF) {
      wRam1[(address - 0x2000) - 0xD000] = data;
    } else if (address <= 0xFE9F) {
      oam[address - 0xFE00] = data;
    } else if (address <= 0xFEFF) {
      return;
    } else if (address <= 0xFF7F) {
      io[address - 0xFF00] = data;
    } else if (address <= 0xFFFE) {
      hRam[address - 0xFF80] = data;
    } else if (address == 0xFFFF) {
      interruptEnable = data;
    } else {
      System.out.println("Can't write address " + Integer.toHexString(address));
    }
  }

  // ------------------------------------------------------------------
  // Joypad
  // ------------------------------------------------------------------

  private int getJoypad() {
    int selected = io[0];
    // Bit 5 high → Button keys; otherwise Direction keys
    if (((selected >> 5) & 1) != 0) {
      return selected | ((joypad >> 4) & 0xF);
    } else {
      return selected | (joypad & 0xF);
    }
  }

  // ------------------------------------------------------------------
  // Accessors + ticking
  // ------------------------------------------------------------------

  public Cartridge getCartridge() { return cartridge; }
  public void setCartridge(Cartridge cartridge) { this.cartridge = cartridge; }

  public void setLinkCable(Cable linkCable) {
    this.linkCable = linkCable;
    if (this.linkCable != null) this.linkCable.attachMemory(this);
  }

  public void disconnectLinkCable() { linkCable = null; }

  public void tick(int cpuCycles) {
    if (linkCable != null) linkCable.tick(cpuCycles);
  }

  public void reset() {
    Arrays.fill(bios,  0);
    Arrays.fill(vram,  0);
    Arrays.fill(wRam0, 0);
    Arrays.fill(wRam1, 0);
    Arrays.fill(oam,   0);
    Arrays.fill(io,    0);
    Arrays.fill(hRam,  0);

    cartridge = null;
    booting = false;
    interruptEnable = 0;
    joypad = 0xFF;
  }
}
