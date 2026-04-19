public class cpu_test {

    private static void assertEq(String name, int expected, int actual) {
        expected &= 0xFFFF;
        actual &= 0xFFFF;
        if (expected != actual) {
            throw new AssertionError(name + " expected 0x" + Integer.toHexString(expected)
                    + " got 0x" + Integer.toHexString(actual));
        }
    }

    private static void assertTrue(String name, boolean cond) {
        if (!cond) throw new AssertionError("Assertion failed: " + name);
    }

    public static void main(String[] args) {
        test_basic_loads();
        test_bc_addressing();
        test_cb_ops();
        test_daa();
        test_interrupt_service();
        System.out.println("All CPU tests passed.");
    }

    // ------------- tests -------------

    private static void test_basic_loads() {
        registers r = new registers();
        test_mem mem = new test_mem();

        byte[] program = new byte[] {
                (byte)0x06, (byte)0x12, // LD B,0x12
                (byte)0x0E, (byte)0x34, // LD C,0x34
                (byte)0x3E, (byte)0x56, // LD A,0x56
                (byte)0x00,             // NOP
                (byte)0x76              // HALT
        };

        mem.loadProgram(0x0000, program);
        r.setPC(0x0000);
        r.setSP(0xFFFE);

        cpu c = new cpu(r, mem);

        int steps = 0;
        while (!c.isHalted() && steps < 100) { c.step(); steps++; }

        assertEq("B", 0x12, r.getB());
        assertEq("C", 0x34, r.getC());
        assertEq("A", 0x56, r.getA());
        assertTrue("HALT reached", c.isHalted());

        System.out.println("test_basic_loads OK");
    }

    private static void test_bc_addressing() {
        registers r = new registers();
        test_mem mem = new test_mem();

        // We'll set BC = 0x2000 and verify:
        // A=0xAB; LD (BC),A; A=0x00; LD A,(BC) -> A=0xAB
        byte[] program = new byte[] {
                (byte)0x3E, (byte)0xAB, // LD A,0xAB
                (byte)0x02,             // LD (BC),A
                (byte)0x3E, (byte)0x00, // LD A,0x00
                (byte)0x0A,             // LD A,(BC)
                (byte)0x76              // HALT
        };

        mem.loadProgram(0x0000, program);
        r.setPC(0x0000);
        r.setSP(0xFFFE);
        r.setBC(0x2000);

        cpu c = new cpu(r, mem);

        int steps = 0;
        while (!c.isHalted() && steps < 100) { c.step(); steps++; }

        assertEq("mem[BC]", 0xAB, mem.read8(0x2000));
        assertEq("A after LD A,(BC)", 0xAB, r.getA());

        System.out.println("test_bc_addressing OK");
    }

    private static void test_cb_ops() {
        registers r = new registers();
        test_mem mem = new test_mem();

        // LD A,0x81
        // CB 07 (RLC A) => A=0x03, C=1
        // CB 47 (BIT 0,A) => bit0=1 => Z=0, H=1, N=0
        // CB 8F (RES 1,A) => A: 0x03 -> 0x01
        // CB FF (SET 7,A) => A: 0x01 -> 0x81
        // HALT
        byte[] program = new byte[] {
                (byte)0x3E, (byte)0x81,
                (byte)0xCB, (byte)0x07,
                (byte)0xCB, (byte)0x47,
                (byte)0xCB, (byte)0x8F,
                (byte)0xCB, (byte)0xFF,
                (byte)0x76
        };

        mem.loadProgram(0x0000, program);
        r.setPC(0x0000);
        r.setSP(0xFFFE);

        cpu c = new cpu(r, mem);

        int steps = 0;
        while (!c.isHalted() && steps < 200) { c.step(); steps++; }

        assertEq("A", 0x81, r.getA());
        assertTrue("C set after RLC", r.isCarry());
        assertTrue("H set after BIT", r.isHalfCarry());
        assertTrue("Z clear after BIT when bit=1", !r.isZero());

        System.out.println("test_cb_ops OK");
    }

    private static void test_daa() {
        registers r = new registers();
        test_mem mem = new test_mem();

        // BCD: 0x15 + 0x27 = 0x42
        // LD A,0x15
        // ADD A,0x27
        // DAA
        // HALT
        byte[] program = new byte[] {
                (byte)0x3E, (byte)0x15,
                (byte)0xC6, (byte)0x27,
                (byte)0x27,
                (byte)0x76
        };

        mem.loadProgram(0x0000, program);
        r.setPC(0x0000);
        r.setSP(0xFFFE);

        cpu c = new cpu(r, mem);

        int steps = 0;
        while (!c.isHalted() && steps < 100) { c.step(); steps++; }

        assertEq("A after DAA", 0x42, r.getA());
        assertTrue("N should be 0", !r.isSubtract());

        System.out.println("test_daa OK");
    }

    private static void test_interrupt_service() {
        registers r = new registers();
        test_mem mem = new test_mem();

        // Put code at 0x0100:
        // EI
        // NOP
        // NOP
        byte[] program = new byte[] {
                (byte)0xFB, // EI (IME enabled after the next instruction)
                (byte)0x00, // NOP
                (byte)0x00  // NOP
        };
        mem.loadProgram(0x0100, program);

        // Set interrupt enable + request VBlank (bit0)
        mem.write8(0xFFFF, 0x01); // IE
        mem.write8(0xFF0F, 0x01); // IF

        r.setPC(0x0100);
        r.setSP(0xFFFE);

        cpu c = new cpu(r, mem);

        // Step EI
        c.step();
        // Step NOP (IME becomes true around here in your model)
        c.step();
        // Next step should service interrupt => PC=0x0040 and stack has return address 0x0102
        c.step();

        assertEq("PC after interrupt", 0x0040, r.getPC());
        assertEq("SP after push", 0xFFFC, r.getSP());

        int lo = mem.read8(0xFFFC);
        int hi = mem.read8(0xFFFD);
        int ret = ((hi << 8) | lo) & 0xFFFF;
        assertEq("return address pushed", 0x0102, ret);

        assertEq("IF cleared", 0x00, mem.read8(0xFF0F) & 0x1F);

        System.out.println("test_interrupt_service OK");
    }
}