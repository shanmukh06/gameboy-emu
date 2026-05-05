public class cpu_test {
    public static void main(String[] args) {
        registers r = new registers();
        Memory mem = new Memory();
        mem.reset();

        DummyCartridge cart = new DummyCartridge();

        byte[] program = new byte[] {
                (byte)0x06, (byte)0x12,
                (byte)0x0E, (byte)0x34,
                (byte)0x3E, (byte)0x56,
                (byte)0x00,
                (byte)0x76
        };

        cart.loadProgram(0x0000, program);
        mem.setCartridge(cart);

        r.setPC(0x0000);
        r.setSP(0xFFFE);

        cpu c = new cpu(r, mem);

        int steps = 0;
        while (!c.isHalted() && steps < 100) {
            c.step();
            steps++;
        }

        if (r.getB() != 0x12) throw new AssertionError("B expected 0x12, got " + Integer.toHexString(r.getB()));
        if (r.getC() != 0x34) throw new AssertionError("C expected 0x34, got " + Integer.toHexString(r.getC()));
        if (r.getA() != 0x56) throw new AssertionError("A expected 0x56, got " + Integer.toHexString(r.getA()));

        System.out.println("CPU basic load test passed.");
        System.out.println(r.toString());
    }
}