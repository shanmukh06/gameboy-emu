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
        detectMBC();   // auto setup (no manual verify needed)
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

            case 0x01:
                mbc = new MBC1();
                break;

            case 0x03:
                hasBattery = true;
                mbc = new MBC1();
                break;

            case 0x13:
                hasBattery = true;
                mbc = new MBC3();
                break;

            case 0x1B:
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

        public int read(int addr) {
            if (addr <= 0x7FFF) {
                return rom[addr];
            }
            return 0xFF;
        }

        public void write(int addr, int data) {
            // ROM only → ignore
        }

        public int[] getRam() {
            return new int[0];
        }
    }

    // ============================================================
    // ======================= MBC1 ================================
    // ============================================================

    private class MBC1 implements MBC {

        private int romBank = 1;
        private boolean ramEnabled = false;

        private final int[] ram = new int[0x8000];

        public int read(int addr) {
            if (addr <= 0x3FFF) {
                return rom[addr];
            }

            if (addr <= 0x7FFF) {
                int offset = (romBank * 0x4000) + (addr - 0x4000);
                return rom[offset % rom.length];
            }

            if (addr >= 0xA000 && addr <= 0xBFFF) {
                if (!ramEnabled) return 0xFF;
                return ram[addr - 0xA000];
            }

            return 0xFF;
        }

        public void write(int addr, int data) {

            if (addr <= 0x1FFF) {
                ramEnabled = (data & 0x0F) == 0x0A;
            }

            else if (addr <= 0x3FFF) {
                romBank = data & 0x1F;
                if (romBank == 0) romBank = 1;
            }

            else if (addr >= 0xA000 && addr <= 0xBFFF) {
                if (ramEnabled) {
                    ram[addr - 0xA000] = data;
                }
            }
        }

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

        public int read(int addr) {
            if (addr <= 0x3FFF) return rom[addr];

            if (addr <= 0x7FFF) {
                int offset = (romBank * 0x4000) + (addr - 0x4000);
                return rom[offset % rom.length];
            }

            if (addr >= 0xA000 && addr <= 0xBFFF) {
                if (!ramEnabled) return 0xFF;
                int offset = (ramBank * 0x2000) + (addr - 0xA000);
                return ram[offset];
            }

            return 0xFF;
        }

        public void write(int addr, int data) {

            if (addr <= 0x1FFF) {
                ramEnabled = data == 0x0A;
            }

            else if (addr <= 0x3FFF) {
                romBank = (data == 0) ? 1 : data;
            }

            else if (addr <= 0x5FFF) {
                ramBank = data & 0x03;
            }

            else if (addr >= 0xA000 && addr <= 0xBFFF) {
                if (!ramEnabled) return;
                int offset = (ramBank * 0x2000) + (addr - 0xA000);
                ram[offset] = data;
            }
        }

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

        public int read(int addr) {
            if (addr <= 0x3FFF) return rom[addr];

            if (addr <= 0x7FFF) {
                int offset = (romBank * 0x4000) + (addr - 0x4000);
                return rom[offset % rom.length];
            }

            if (addr >= 0xA000 && addr <= 0xBFFF) {
                if (!ramEnabled) return 0xFF;
                int offset = (ramBank * 0x2000) + (addr - 0xA000);
                return ram[offset];
            }

            return 0xFF;
        }

        public void write(int addr, int data) {

            if (addr <= 0x1FFF) {
                ramEnabled = data == 0x0A;
            }

            else if (addr <= 0x2FFF) {
                romBank = (romBank & 0x100) | data;
            }

            else if (addr <= 0x3FFF) {
                romBank = ((data & 1) << 8) | (romBank & 0xFF);
            }

            else if (addr <= 0x5FFF) {
                ramBank = data & 0x0F;
            }

            else if (addr >= 0xA000 && addr <= 0xBFFF) {
                if (!ramEnabled) return;
                int offset = (ramBank * 0x2000) + (addr - 0xA000);
                ram[offset] = data;
            }
        }

        public int[] getRam() {
            return ram;
        }
    }
}