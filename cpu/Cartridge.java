import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

public class Cartridge {

    private int[] rom;
    private String romName;
    private boolean hasBattery = false;

    private MBC mbc;

    // ---------------- CONSTRUCTOR ----------------
    public Cartridge(String path) {
        loadRom(path);
        detectMBC();
    }

    // ---------------- LOAD ROM ----------------
    private void loadRom(String path) {
        try {
            byte[] data = Files.readAllBytes(Paths.get(path));
            rom = new int[data.length];

            for (int i = 0; i < data.length; i++) {
                rom[i] = data[i] & 0xFF;
            }

            int idx = path.replace("\\", "/").lastIndexOf("/");
            String name = (idx >= 0) ? path.substring(idx + 1) : path;
            romName = name.substring(0, name.lastIndexOf('.'));

            System.out.println("Loaded ROM: " + romName);

        } catch (IOException e) {
            throw new RuntimeException("Failed to load ROM", e);
        }
    }

    // ---------------- MBC DETECTION ----------------
    private void detectMBC() {
        int type = rom[0x0147];

        switch (type) {
            case 0x00:
                mbc = new NoMBC();
                break;

            // MBC1
            case 0x01: // MBC1
            case 0x02: // MBC1+RAM
                mbc = new MBC1();
                break;

            case 0x03: // MBC1+RAM+BATTERY
                hasBattery = true;
                mbc = new MBC1();
                break;

            // MBC3
            case 0x13: // MBC3+RAM+BATTERY (common)
                hasBattery = true;
                mbc = new MBC3();
                break;

            // MBC5
            case 0x1B: // MBC5+RAM+BATTERY
                hasBattery = true;
                mbc = new MBC5();
                break;

            default:
                throw new RuntimeException("Unsupported MBC: " + Integer.toHexString(type));
        }
    }

    // ---------------- PUBLIC API ----------------
    public int read8(int address) {
        return mbc.read(address & 0xFFFF);
    }

    public void write8(int address, int data) {
        mbc.write(address & 0xFFFF, data & 0xFF);
    }

    public String getRomName() {
        return romName;
    }

    public boolean hasBattery() {
        return hasBattery;
    }

    public int[] getRam() {
        return mbc.getRam();
    }

    // ============================================================
    // ======================= MBC INTERFACE =======================
    // ============================================================

    private interface MBC {
        int read(int addr);
        void write(int addr, int data);
        int[] getRam();
    }

    // ============================================================
    // ======================= NO MBC ==============================
    // ============================================================

    private class NoMBC implements MBC {

        @Override
        public int read(int addr) {
            if (addr <= 0x7FFF) {
                return rom[addr];
            }
            return 0xFF;
        }

        @Override
        public void write(int addr, int data) {
            // ROM only → ignore
        }

        @Override
        public int[] getRam() {
            return new int[0];
        }
    }

    // ============================================================
    // ======================= MBC1 ================================
    // ============================================================

    private class MBC1 implements MBC {

        private int romBankLow5 = 1;   // 1..31 (0 not allowed)
        private int bankHigh2 = 0;     // 0..3 (upper ROM bits or RAM bank)
        private int mode = 0;          // 0=ROM banking, 1=RAM banking
        private boolean ramEnabled = false;

        // NOTE: Proper size depends on header; keeping your original allocation
        private final int[] ram = new int[0x8000];

        private int romBankCount() {
            return Math.max(1, rom.length / 0x4000);
        }

        private int effectiveRomBank() {
            int bank = (bankHigh2 << 5) | (romBankLow5 & 0x1F);
            bank %= romBankCount();
            if (bank == 0) bank = 1; // bank 0 not allowed in switchable area
            return bank;
        }

        private int effectiveRomBank0() {
            if (mode == 0) return 0;
            int bank = (bankHigh2 << 5) % romBankCount();
            return bank;
        }

        @Override
        public int read(int addr) {
            if (addr <= 0x3FFF) {
                int bank0 = effectiveRomBank0();
                int offset = (bank0 * 0x4000) + addr;
                return rom[offset % rom.length];
            }

            if (addr <= 0x7FFF) {
                int bank = effectiveRomBank();
                int offset = (bank * 0x4000) + (addr - 0x4000);
                return rom[offset % rom.length];
            }

            if (addr >= 0xA000 && addr <= 0xBFFF) {
                if (!ramEnabled) return 0xFF;
                int ramBank = (mode == 1) ? (bankHigh2 & 0x03) : 0;
                int offset = (ramBank * 0x2000) + (addr - 0xA000);
                offset %= ram.length;
                return ram[offset] & 0xFF;
            }

            return 0xFF;
        }

        @Override
        public void write(int addr, int data) {
            data &= 0xFF;

            if (addr <= 0x1FFF) {
                ramEnabled = (data & 0x0F) == 0x0A;
                return;
            }

            if (addr <= 0x3FFF) {
                romBankLow5 = data & 0x1F;
                if (romBankLow5 == 0) romBankLow5 = 1;
                return;
            }

            if (addr <= 0x5FFF) {
                bankHigh2 = data & 0x03;
                return;
            }

            if (addr <= 0x7FFF) {
                mode = data & 0x01;
                return;
            }

            if (addr >= 0xA000 && addr <= 0xBFFF) {
                if (!ramEnabled) return;
                int ramBank = (mode == 1) ? (bankHigh2 & 0x03) : 0;
                int offset = (ramBank * 0x2000) + (addr - 0xA000);
                offset %= ram.length;
                ram[offset] = data;
            }
        }

        @Override
        public int[] getRam() {
            return ram;
        }
    }

    // ============================================================
    // ======================= MBC3 ================================
    // ============================================================

    private class MBC3 implements MBC {

        private int romBank = 1;
        private int ramBank = 0;
        private boolean ramEnabled = false;

        private final int[] ram = new int[0x8000];

        @Override
        public int read(int addr) {
            if (addr <= 0x3FFF) return rom[addr];

            if (addr <= 0x7FFF) {
                int offset = (romBank * 0x4000) + (addr - 0x4000);
                return rom[offset % rom.length];
            }

            if (addr >= 0xA000 && addr <= 0xBFFF) {
                if (!ramEnabled) return 0xFF;
                int offset = (ramBank * 0x2000) + (addr - 0xA000);
                offset %= ram.length;
                return ram[offset] & 0xFF;
            }

            return 0xFF;
        }

        @Override
        public void write(int addr, int data) {
            data &= 0xFF;

            if (addr <= 0x1FFF) {
                ramEnabled = (data & 0x0F) == 0x0A;
                return;
            }

            if (addr <= 0x3FFF) {
                romBank = (data == 0) ? 1 : (data & 0x7F);
                return;
            }

            if (addr <= 0x5FFF) {
                ramBank = data & 0x03; // RTC not implemented
                return;
            }

            if (addr >= 0xA000 && addr <= 0xBFFF) {
                if (!ramEnabled) return;
                int offset = (ramBank * 0x2000) + (addr - 0xA000);
                offset %= ram.length;
                ram[offset] = data;
            }
        }

        @Override
        public int[] getRam() {
            return ram;
        }
    }

    // ============================================================
    // ======================= MBC5 ================================
    // ============================================================

    private class MBC5 implements MBC {

        private int romBank = 1;
        private int ramBank = 0;
        private boolean ramEnabled = false;

        private final int[] ram = new int[0x100000];

        @Override
        public int read(int addr) {
            if (addr <= 0x3FFF) return rom[addr];

            if (addr <= 0x7FFF) {
                int offset = (romBank * 0x4000) + (addr - 0x4000);
                return rom[offset % rom.length];
            }

            if (addr >= 0xA000 && addr <= 0xBFFF) {
                if (!ramEnabled) return 0xFF;
                int offset = (ramBank * 0x2000) + (addr - 0xA000);
                offset %= ram.length;
                return ram[offset] & 0xFF;
            }

            return 0xFF;
        }

        @Override
        public void write(int addr, int data) {
            data &= 0xFF;

            if (addr <= 0x1FFF) {
                ramEnabled = (data & 0x0F) == 0x0A;
                return;
            }

            if (addr <= 0x2FFF) {
                romBank = (romBank & 0x100) | data;
                return;
            }

            if (addr <= 0x3FFF) {
                romBank = ((data & 1) << 8) | (romBank & 0xFF);
                return;
            }

            if (addr <= 0x5FFF) {
                ramBank = data & 0x0F;
                return;
            }

            if (addr >= 0xA000 && addr <= 0xBFFF) {
                if (!ramEnabled) return;
                int offset = (ramBank * 0x2000) + (addr - 0xA000);
                offset %= ram.length;
                ram[offset] = data;
            }
        }

        @Override
        public int[] getRam() {
            return ram;
        }
    }
}