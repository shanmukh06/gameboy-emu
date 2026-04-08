public interface memory {
    int read8(int addr);
    void write8(int addr, int value);

    default int read16(int addr){
        return (read8(addr+1) << 8) | read8(addr);
    }

    default void write16(int addr, int value){
        write8(addr, value&0xFF);
        write8(addr+1, (value >> 8) & 0xFF);
    }
}

class test_mem implements memory {
    private byte[] mem = new byte[0x10000];

    @Override
    public int read8(int addr) {
        return mem[addr & 0xFFFF] & 0xFF;
    }

    @Override
    public void write8(int addr, int value){
        mem[addr & 0xFFFF] = (byte) (value & 0xFF);
    }

    public void loadProgram(int startAddr, byte[] program){
        System.arraycopy(program, 0, mem, startAddr & 0xFFFF, program.length);
    }

    public void reset(){
        for (int i = 0; i < mem.length; i++) {
            mem[i] = 0;
        }
    }
}