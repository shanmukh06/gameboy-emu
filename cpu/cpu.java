public class cpu {

    @FunctionalInterface
    private interface op { int run(); }

    @FunctionalInterface
    private interface cbop { int run(); }

    // Register id encoding: 0:B 1:C 2:D 3:E 4:H 5:L 6:(HL) 7:A
    private static final int REG_B = 0, REG_C = 1, REG_D = 2, REG_E = 3;
    private static final int REG_H = 4, REG_L = 5, REG_HL_PTR = 6, REG_A = 7;

    // Interrupt registers (standard Game Boy)
    private static final int ADDR_IF = 0xFF0F;
    private static final int ADDR_IE = 0xFFFF;

    private final registers r;
    private final memory mem;

    private final op[] ops = new op[256];
    private final cbop[] cbOps = new cbop[256];

    private boolean halted = false;

    // Interrupt master enable + EI delay
    private boolean ime = false;
    private boolean imeEnablePending = false;

    public cpu(registers r, memory m) {
        this.r = r;
        this.mem = m;
        initOps();
        initCbOps();
    }

    // ---------------- fetch helpers ----------------
    private int fetch8() {
        int v = mem.read8(r.getPC());
        r.setPC(r.getPC() + 1);
        return v & 0xFF;
    }

    private int fetch16() {
        int lo = fetch8();
        int hi = fetch8();
        return ((hi << 8) | lo) & 0xFFFF;
    }

    // ---------------- reg helpers ----------------
    private int readReg8(int id) {
        switch (id) {
            case REG_B: return r.getB();
            case REG_C: return r.getC();
            case REG_D: return r.getD();
            case REG_E: return r.getE();
            case REG_H: return r.getH();
            case REG_L: return r.getL();
            case REG_A: return r.getA();
            case REG_HL_PTR: return mem.read8(r.getHL());
            default: throw new IllegalArgumentException("bad reg id " + id);
        }
    }

    private void writeReg8(int id, int val) {
        val &= 0xFF;
        switch (id) {
            case REG_B: r.setB(val); return;
            case REG_C: r.setC(val); return;
            case REG_D: r.setD(val); return;
            case REG_E: r.setE(val); return;
            case REG_H: r.setH(val); return;
            case REG_L: r.setL(val); return;
            case REG_A: r.setA(val); return;
            case REG_HL_PTR: mem.write8(r.getHL(), val); return;
            default: throw new IllegalArgumentException("bad reg id " + id);
        }
    }

    private int cyclesRegOrHL(int regId) {
        return (regId == REG_HL_PTR) ? 8 : 4;
    }

    // ---------------- stack helpers ----------------
    private void push16(int value) {
        value &= 0xFFFF;
        int sp = r.getSP();
        sp = (sp - 1) & 0xFFFF;
        mem.write8(sp, (value >> 8) & 0xFF);
        sp = (sp - 1) & 0xFFFF;
        mem.write8(sp, value & 0xFF);
        r.setSP(sp);
    }

    private int pop16() {
        int sp = r.getSP();
        int lo = mem.read8(sp);
        sp = (sp + 1) & 0xFFFF;
        int hi = mem.read8(sp);
        sp = (sp + 1) & 0xFFFF;
        r.setSP(sp);
        return ((hi << 8) | lo) & 0xFFFF;
    }

    // ---------------- flag helpers ----------------
    private void setZ(boolean v) { r.setFlagIf(registers.FLAG_Z, v); }
    private void setN(boolean v) { r.setFlagIf(registers.FLAG_N, v); }
    private void setH(boolean v) { r.setFlagIf(registers.FLAG_H, v); }
    private void setC(boolean v) { r.setFlagIf(registers.FLAG_C, v); }

    private void setFlagsAdd8(int a, int b, int fullRes) {
        int res8 = fullRes & 0xFF;
        setZ(res8 == 0);
        setN(false);
        setH(((a & 0xF) + (b & 0xF)) > 0xF);
        setC(fullRes > 0xFF);
    }

    private void setFlagsSub8(int a, int b, int fullRes) {
        int res8 = fullRes & 0xFF;
        setZ(res8 == 0);
        setN(true);
        setH((a & 0xF) < (b & 0xF));
        setC(a < b);
    }

    private int adc8(int a, int b) {
        int c = r.isCarry() ? 1 : 0;
        int full = a + b + c;
        int res8 = full & 0xFF;
        setZ(res8 == 0);
        setN(false);
        setH(((a & 0xF) + (b & 0xF) + c) > 0xF);
        setC(full > 0xFF);
        return res8;
    }

    private int sbc8(int a, int b) {
        int c = r.isCarry() ? 1 : 0;
        int full = a - b - c;
        int res8 = full & 0xFF;
        setZ(res8 == 0);
        setN(true);
        setH((a & 0xF) < ((b & 0xF) + c));
        setC(a < (b + c));
        return res8;
    }

    private int inc8(int x) {
        x &= 0xFF;
        int res = (x + 1) & 0xFF;
        setZ(res == 0);
        setN(false);
        setH(((x & 0xF) + 1) > 0xF);
        return res;
    }

    private int dec8(int x) {
        x &= 0xFF;
        int res = (x - 1) & 0xFF;
        setZ(res == 0);
        setN(true);
        setH((x & 0xF) == 0);
        return res;
    }

    private void addHL(int rr) {
        int hl = r.getHL();
        int full = hl + rr;
        setN(false);
        setH(((hl & 0x0FFF) + (rr & 0x0FFF)) > 0x0FFF);
        setC(full > 0xFFFF);
        r.setHL(full);
        // Z unaffected
    }

    private int addSP_e8(int sp, int e8) {
        int e = (byte) (e8 & 0xFF); // sign extend
        int res = (sp + e) & 0xFFFF;
        setZ(false);
        setN(false);
        setH(((sp & 0xF) + (e & 0xF)) > 0xF);
        setC(((sp & 0xFF) + (e & 0xFF)) > 0xFF);
        return res;
    }

    // ---------------- DAA ----------------
    // This matches the standard LR35902 DAA behavior.
    private void daa() {
        int a = r.getA();
        boolean n = r.isSubtract();
        boolean h = r.isHalfCarry();
        boolean c = r.isCarry();

        int adj = 0;
        boolean newC = c;

        if (!n) {
            if (h || (a & 0x0F) > 9) adj |= 0x06;
            if (c || a > 0x99) { adj |= 0x60; newC = true; }
            a = (a + adj) & 0xFF;
        } else {
            if (h) adj |= 0x06;
            if (c) adj |= 0x60;
            a = (a - adj) & 0xFF;
        }

        r.setA(a);
        setZ(a == 0);
        setH(false);
        setC(newC);
        // N unchanged
    }

    // ---------------- rotate helpers (A only, non-CB) ----------------
    private void rlca() {
        int a = r.getA();
        int carry = (a >> 7) & 1;
        int res = ((a << 1) | carry) & 0xFF;
        r.setA(res);
        setZ(false);
        setN(false);
        setH(false);
        setC(carry == 1);
    }

    private void rrca() {
        int a = r.getA();
        int carry = a & 1;
        int res = ((carry << 7) | (a >> 1)) & 0xFF;
        r.setA(res);
        setZ(false);
        setN(false);
        setH(false);
        setC(carry == 1);
    }

    private void rla() {
        int a = r.getA();
        int oldC = r.isCarry() ? 1 : 0;
        int newC = (a >> 7) & 1;
        int res = ((a << 1) | oldC) & 0xFF;
        r.setA(res);
        setZ(false);
        setN(false);
        setH(false);
        setC(newC == 1);
    }

    private void rra() {
        int a = r.getA();
        int oldC = r.isCarry() ? 1 : 0;
        int newC = a & 1;
        int res = ((oldC << 7) | (a >> 1)) & 0xFF;
        r.setA(res);
        setZ(false);
        setN(false);
        setH(false);
        setC(newC == 1);
    }

    // ---------------- interrupts (minimum viable) ----------------
    private boolean hasPendingInterrupt() {
        int ie = mem.read8(ADDR_IE) & 0x1F;
        int iff = mem.read8(ADDR_IF) & 0x1F;
        return (ie & iff) != 0;
    }

    private int serviceInterruptsIfNeeded() {
        if (!ime) return 0;

        int ie = mem.read8(ADDR_IE) & 0x1F;
        int iff = mem.read8(ADDR_IF) & 0x1F;
        int pending = ie & iff;
        if (pending == 0) return 0;

        // Service highest priority interrupt (bit 0..4)
        int bit;
        int vector;
        if ((pending & 0x01) != 0) { bit = 0; vector = 0x40; }
        else if ((pending & 0x02) != 0) { bit = 1; vector = 0x48; }
        else if ((pending & 0x04) != 0) { bit = 2; vector = 0x50; }
        else if ((pending & 0x08) != 0) { bit = 3; vector = 0x58; }
        else { bit = 4; vector = 0x60; }

        // Clear IF bit
        iff &= ~(1 << bit);
        mem.write8(ADDR_IF, iff);

        ime = false;
        halted = false;

        push16(r.getPC());
        r.setPC(vector);

        return 20; // interrupt servicing cost
    }

    // ---------------- CB helpers ----------------
    private int cbCycles(int regId, int regCycles, int hlCycles) {
        return (regId == REG_HL_PTR) ? hlCycles : regCycles;
    }

    private int rlc(int x) {
        int c = (x >> 7) & 1;
        int res = ((x << 1) | c) & 0xFF;
        setZ(res == 0);
        setN(false);
        setH(false);
        setC(c == 1);
        return res;
    }

    private int rrc(int x) {
        int c = x & 1;
        int res = ((c << 7) | (x >> 1)) & 0xFF;
        setZ(res == 0);
        setN(false);
        setH(false);
        setC(c == 1);
        return res;
    }

    private int rl(int x) {
        int oldC = r.isCarry() ? 1 : 0;
        int newC = (x >> 7) & 1;
        int res = ((x << 1) | oldC) & 0xFF;
        setZ(res == 0);
        setN(false);
        setH(false);
        setC(newC == 1);
        return res;
    }

    private int rr(int x) {
        int oldC = r.isCarry() ? 1 : 0;
        int newC = x & 1;
        int res = ((oldC << 7) | (x >> 1)) & 0xFF;
        setZ(res == 0);
        setN(false);
        setH(false);
        setC(newC == 1);
        return res;
    }

    private int sla(int x) {
        int newC = (x >> 7) & 1;
        int res = (x << 1) & 0xFF;
        setZ(res == 0);
        setN(false);
        setH(false);
        setC(newC == 1);
        return res;
    }

    private int sra(int x) {
        int newC = x & 1;
        int msb = x & 0x80;
        int res = (msb | (x >> 1)) & 0xFF;
        setZ(res == 0);
        setN(false);
        setH(false);
        setC(newC == 1);
        return res;
    }

    private int srl(int x) {
        int newC = x & 1;
        int res = (x >> 1) & 0xFF;
        setZ(res == 0);
        setN(false);
        setH(false);
        setC(newC == 1);
        return res;
    }

    private int swap(int x) {
        int res = ((x & 0xF) << 4) | ((x >> 4) & 0xF);
        res &= 0xFF;
        setZ(res == 0);
        setN(false);
        setH(false);
        setC(false);
        return res;
    }

    // ---------------- init base ops ----------------
    private void initOps() {
        for (int i = 0; i < 256; i++) {
            final int opcode = i;
            ops[i] = () -> {
                int pc = (r.getPC() - 1) & 0xFFFF;
                throw new IllegalStateException(
                        String.format("Unimplemented opcode %02X at PC=%04X", opcode, pc)
                );
            };
        }

        // basics
        ops[0x00] = () -> 4; // NOP
        ops[0x10] = () -> { halted = true; return 4; }; // STOP placeholder
        ops[0x76] = () -> { halted = true; return 4; }; // HALT

        // CB prefix dispatch
        ops[0xCB] = () -> {
            int cb = fetch8();
            return cbOps[cb & 0xFF].run();
        };

        // DI/EI
        ops[0xF3] = () -> { ime = false; imeEnablePending = false; return 4; }; // DI
        ops[0xFB] = () -> { imeEnablePending = true; return 4; }; // EI (delayed)

        // rotates A
        ops[0x07] = () -> { rlca(); return 4; }; // RLCA
        ops[0x0F] = () -> { rrca(); return 4; }; // RRCA
        ops[0x17] = () -> { rla();  return 4; }; // RLA
        ops[0x1F] = () -> { rra();  return 4; }; // RRA

        // DAA
        ops[0x27] = () -> { daa(); return 4; };

        // misc flags
        ops[0x2F] = () -> { // CPL
            r.setA((~r.getA()) & 0xFF);
            setN(true);
            setH(true);
            return 4;
        };
        ops[0x37] = () -> { // SCF
            setN(false); setH(false); setC(true);
            return 4;
        };
        ops[0x3F] = () -> { // CCF
            setN(false); setH(false); setC(!r.isCarry());
            return 4;
        };

        // 16-bit LD rr,nn
        ops[0x01] = () -> { r.setBC(fetch16()); return 12; };
        ops[0x11] = () -> { r.setDE(fetch16()); return 12; };
        ops[0x21] = () -> { r.setHL(fetch16()); return 12; };
        ops[0x31] = () -> { r.setSP(fetch16()); return 12; };

        // LD (nn),SP
        ops[0x08] = () -> {
            int addr = fetch16();
            int sp = r.getSP();
            mem.write8(addr, sp & 0xFF);
            mem.write8((addr + 1) & 0xFFFF, (sp >> 8) & 0xFF);
            return 20;
        };

        // INC/DEC rr
        ops[0x03] = () -> { r.setBC(r.getBC() + 1); return 8; };
        ops[0x13] = () -> { r.setDE(r.getDE() + 1); return 8; };
        ops[0x23] = () -> { r.setHL(r.getHL() + 1); return 8; };
        ops[0x33] = () -> { r.setSP(r.getSP() + 1); return 8; };

        ops[0x0B] = () -> { r.setBC(r.getBC() - 1); return 8; };
        ops[0x1B] = () -> { r.setDE(r.getDE() - 1); return 8; };
        ops[0x2B] = () -> { r.setHL(r.getHL() - 1); return 8; };
        ops[0x3B] = () -> { r.setSP(r.getSP() - 1); return 8; };

        // ADD HL,rr
        ops[0x09] = () -> { addHL(r.getBC()); return 8; };
        ops[0x19] = () -> { addHL(r.getDE()); return 8; };
        ops[0x29] = () -> { addHL(r.getHL()); return 8; };
        ops[0x39] = () -> { addHL(r.getSP()); return 8; };

        // LD r,n
        ops[0x06] = () -> { r.setB(fetch8()); return 8; };
        ops[0x0E] = () -> { r.setC(fetch8()); return 8; };
        ops[0x16] = () -> { r.setD(fetch8()); return 8; };
        ops[0x1E] = () -> { r.setE(fetch8()); return 8; };
        ops[0x26] = () -> { r.setH(fetch8()); return 8; };
        ops[0x2E] = () -> { r.setL(fetch8()); return 8; };
        ops[0x3E] = () -> { r.setA(fetch8()); return 8; };

        // LD (HL),n
        ops[0x36] = () -> { mem.write8(r.getHL(), fetch8()); return 12; };

        // INC/DEC r
        int[] regOrder = {REG_B, REG_C, REG_D, REG_E, REG_H, REG_L, REG_HL_PTR, REG_A};
        for (int i = 0; i < 8; i++) {
            final int regId = regOrder[i];
            final int incOp = 0x04 + (i * 8);
            final int decOp = 0x05 + (i * 8);

            ops[incOp] = () -> {
                int v = readReg8(regId);
                int res = inc8(v);
                writeReg8(regId, res);
                return (regId == REG_HL_PTR) ? 12 : 4;
            };

            ops[decOp] = () -> {
                int v = readReg8(regId);
                int res = dec8(v);
                writeReg8(regId, res);
                return (regId == REG_HL_PTR) ? 12 : 4;
            };
        }

        // LD r,r (0x40..0x7F except 0x76 HALT)
        for (int dst = 0; dst < 8; dst++) {
            for (int src = 0; src < 8; src++) {
                int opcode = 0x40 | (dst << 3) | src;
                if (opcode == 0x76) continue;
                final int d = dst, s = src;
                ops[opcode] = () -> {
                    int v = readReg8(s);
                    writeReg8(d, v);
                    return (d == REG_HL_PTR || s == REG_HL_PTR) ? 8 : 4;
                };
            }
        }

        // Missing non-CB loads: (BC)/(DE)
        ops[0x02] = () -> { mem.write8(r.getBC(), r.getA()); return 8; }; // LD (BC),A
        ops[0x0A] = () -> { r.setA(mem.read8(r.getBC())); return 8; };   // LD A,(BC)
        ops[0x12] = () -> { mem.write8(r.getDE(), r.getA()); return 8; }; // LD (DE),A
        ops[0x1A] = () -> { r.setA(mem.read8(r.getDE())); return 8; };   // LD A,(DE)

        // LDI/LDD A <-> (HL)
        ops[0x22] = () -> { mem.write8(r.getHL(), r.getA()); r.setHL(r.getHL() + 1); return 8; };
        ops[0x2A] = () -> { r.setA(mem.read8(r.getHL())); r.setHL(r.getHL() + 1); return 8; };
        ops[0x32] = () -> { mem.write8(r.getHL(), r.getA()); r.setHL(r.getHL() - 1); return 8; };
        ops[0x3A] = () -> { r.setA(mem.read8(r.getHL())); r.setHL(r.getHL() - 1); return 8; };

        // Absolute memory loads
        ops[0xEA] = () -> { int addr = fetch16(); mem.write8(addr, r.getA()); return 16; }; // LD (nn),A
        ops[0xFA] = () -> { int addr = fetch16(); r.setA(mem.read8(addr)); return 16; };   // LD A,(nn)

        // High RAM (FF00+n)
        ops[0xE0] = () -> { int addr = 0xFF00 | fetch8(); mem.write8(addr, r.getA()); return 12; };
        ops[0xF0] = () -> { int addr = 0xFF00 | fetch8(); r.setA(mem.read8(addr)); return 12; };

        // (FF00 + C)
        ops[0xE2] = () -> { int addr = 0xFF00 | r.getC(); mem.write8(addr, r.getA()); return 8; };
        ops[0xF2] = () -> { int addr = 0xFF00 | r.getC(); r.setA(mem.read8(addr)); return 8; };

        // SP/HL
        ops[0xF9] = () -> { r.setSP(r.getHL()); return 8; }; // LD SP,HL
        ops[0xE8] = () -> { int e8 = fetch8(); r.setSP(addSP_e8(r.getSP(), e8)); return 16; }; // ADD SP,e8
        ops[0xF8] = () -> { int e8 = fetch8(); r.setHL(addSP_e8(r.getSP(), e8)); return 12; }; // LD HL,SP+e8

        // JP/JR
        ops[0xC3] = () -> { int addr = fetch16(); r.setPC(addr); return 16; }; // JP nn
        ops[0xE9] = () -> { r.setPC(r.getHL()); return 4; }; // JP (HL)

        ops[0xC2] = () -> { int addr=fetch16(); if (!r.isZero()) { r.setPC(addr); return 16; } return 12; };
        ops[0xCA] = () -> { int addr=fetch16(); if ( r.isZero()) { r.setPC(addr); return 16; } return 12; };
        ops[0xD2] = () -> { int addr=fetch16(); if (!r.isCarry()) { r.setPC(addr); return 16; } return 12; };
        ops[0xDA] = () -> { int addr=fetch16(); if ( r.isCarry()) { r.setPC(addr); return 16; } return 12; };

        ops[0x18] = () -> { int off=(byte)fetch8(); r.setPC((r.getPC()+off)&0xFFFF); return 12; };
        ops[0x20] = () -> { int off=(byte)fetch8(); if (!r.isZero()) { r.setPC((r.getPC()+off)&0xFFFF); return 12; } return 8; };
        ops[0x28] = () -> { int off=(byte)fetch8(); if ( r.isZero()) { r.setPC((r.getPC()+off)&0xFFFF); return 12; } return 8; };
        ops[0x30] = () -> { int off=(byte)fetch8(); if (!r.isCarry()) { r.setPC((r.getPC()+off)&0xFFFF); return 12; } return 8; };
        ops[0x38] = () -> { int off=(byte)fetch8(); if ( r.isCarry()) { r.setPC((r.getPC()+off)&0xFFFF); return 12; } return 8; };

        // CALL/RET/RETI
        ops[0xC9] = () -> { r.setPC(pop16()); return 16; }; // RET
        ops[0xD9] = () -> { r.setPC(pop16()); ime = true; return 16; }; // RETI
        ops[0xCD] = () -> { int addr=fetch16(); push16(r.getPC()); r.setPC(addr); return 24; }; // CALL nn

        ops[0xC0] = () -> { if (!r.isZero()) { r.setPC(pop16()); return 20; } return 8; };
        ops[0xC8] = () -> { if ( r.isZero()) { r.setPC(pop16()); return 20; } return 8; };
        ops[0xD0] = () -> { if (!r.isCarry()) { r.setPC(pop16()); return 20; } return 8; };
        ops[0xD8] = () -> { if ( r.isCarry()) { r.setPC(pop16()); return 20; } return 8; };

        ops[0xC4] = () -> { int addr=fetch16(); if (!r.isZero()) { push16(r.getPC()); r.setPC(addr); return 24; } return 12; };
        ops[0xCC] = () -> { int addr=fetch16(); if ( r.isZero()) { push16(r.getPC()); r.setPC(addr); return 24; } return 12; };
        ops[0xD4] = () -> { int addr=fetch16(); if (!r.isCarry()) { push16(r.getPC()); r.setPC(addr); return 24; } return 12; };
        ops[0xDC] = () -> { int addr=fetch16(); if ( r.isCarry()) { push16(r.getPC()); r.setPC(addr); return 24; } return 12; };

        // PUSH/POP
        ops[0xC5] = () -> { push16(r.getBC()); return 16; };
        ops[0xD5] = () -> { push16(r.getDE()); return 16; };
        ops[0xE5] = () -> { push16(r.getHL()); return 16; };
        ops[0xF5] = () -> { push16(r.getAF()); return 16; };

        ops[0xC1] = () -> { r.setBC(pop16()); return 12; };
        ops[0xD1] = () -> { r.setDE(pop16()); return 12; };
        ops[0xE1] = () -> { r.setHL(pop16()); return 12; };
        ops[0xF1] = () -> { r.setAF(pop16()); return 12; };

        // RST
        ops[0xC7] = () -> { push16(r.getPC()); r.setPC(0x00); return 16; };
        ops[0xCF] = () -> { push16(r.getPC()); r.setPC(0x08); return 16; };
        ops[0xD7] = () -> { push16(r.getPC()); r.setPC(0x10); return 16; };
        ops[0xDF] = () -> { push16(r.getPC()); r.setPC(0x18); return 16; };
        ops[0xE7] = () -> { push16(r.getPC()); r.setPC(0x20); return 16; };
        ops[0xEF] = () -> { push16(r.getPC()); r.setPC(0x28); return 16; };
        ops[0xF7] = () -> { push16(r.getPC()); r.setPC(0x30); return 16; };
        ops[0xFF] = () -> { push16(r.getPC()); r.setPC(0x38); return 16; };

        // ALU register forms (0x80..0xBF)
        for (int src = 0; src < 8; src++) {
            final int s = src;

            ops[0x80 | src] = () -> { // ADD
                int a=r.getA(), b=readReg8(s); int full=a+b; r.setA(full); setFlagsAdd8(a,b,full); return cyclesRegOrHL(s);
            };
            ops[0x88 | src] = () -> { r.setA(adc8(r.getA(), readReg8(s))); return cyclesRegOrHL(s); }; // ADC

            ops[0x90 | src] = () -> { // SUB
                int a=r.getA(), b=readReg8(s); int full=a-b; r.setA(full); setFlagsSub8(a,b,full); return cyclesRegOrHL(s);
            };
            ops[0x98 | src] = () -> { r.setA(sbc8(r.getA(), readReg8(s))); return cyclesRegOrHL(s); }; // SBC

            ops[0xA0 | src] = () -> { // AND
                int res = r.getA() & readReg8(s); r.setA(res);
                setZ((res&0xFF)==0); setN(false); setH(true); setC(false);
                return cyclesRegOrHL(s);
            };
            ops[0xA8 | src] = () -> { // XOR
                int res = r.getA() ^ readReg8(s); r.setA(res);
                setZ((res&0xFF)==0); setN(false); setH(false); setC(false);
                return cyclesRegOrHL(s);
            };
            ops[0xB0 | src] = () -> { // OR
                int res = r.getA() | readReg8(s); r.setA(res);
                setZ((res&0xFF)==0); setN(false); setH(false); setC(false);
                return cyclesRegOrHL(s);
            };
            ops[0xB8 | src] = () -> { // CP
                int a=r.getA(), b=readReg8(s); int full=a-b; setFlagsSub8(a,b,full);
                return cyclesRegOrHL(s);
            };
        }

        // ALU immediate forms
        ops[0xC6] = () -> { int a=r.getA(), b=fetch8(); int full=a+b; r.setA(full); setFlagsAdd8(a,b,full); return 8; };
        ops[0xCE] = () -> { int b=fetch8(); r.setA(adc8(r.getA(), b)); return 8; };
        ops[0xD6] = () -> { int a=r.getA(), b=fetch8(); int full=a-b; r.setA(full); setFlagsSub8(a,b,full); return 8; };
        ops[0xDE] = () -> { int b=fetch8(); r.setA(sbc8(r.getA(), b)); return 8; };

        ops[0xE6] = () -> { int res = r.getA() & fetch8(); r.setA(res);
            setZ((res&0xFF)==0); setN(false); setH(true); setC(false); return 8; };
        ops[0xEE] = () -> { int res = r.getA() ^ fetch8(); r.setA(res);
            setZ((res&0xFF)==0); setN(false); setH(false); setC(false); return 8; };
        ops[0xF6] = () -> { int res = r.getA() | fetch8(); r.setA(res);
            setZ((res&0xFF)==0); setN(false); setH(false); setC(false); return 8; };
        ops[0xFE] = () -> { int a=r.getA(), b=fetch8(); int full=a-b; setFlagsSub8(a,b,full); return 8; };
    }

    // ---------------- init CB ops ----------------
    private void initCbOps() {
        for (int i = 0; i < 256; i++) {
            final int cb = i;
            cbOps[i] = () -> {
                int pc = (r.getPC() - 2) & 0xFFFF; // opcode CB + cb byte
                throw new IllegalStateException(
                        String.format("Unimplemented CB opcode %02X at PC=%04X", cb, pc)
                );
            };
        }

        // CB group layout:
        // 00-07 RLC r
        // 08-0F RRC r
        // 10-17 RL  r
        // 18-1F RR  r
        // 20-27 SLA r
        // 28-2F SRA r
        // 30-37 SWAP r
        // 38-3F SRL r
        // 40-7F BIT b,r
        // 80-BF RES b,r
        // C0-FF SET b,r

        // Rotate/shift/swap groups (each is 8 regs)
        for (int reg = 0; reg < 8; reg++) {
            final int rr = reg;

            cbOps[0x00 | reg] = () -> { int v=readReg8(rr); v=rlc(v); writeReg8(rr,v); return cbCycles(rr, 8, 16); };
            cbOps[0x08 | reg] = () -> { int v=readReg8(rr); v=rrc(v); writeReg8(rr,v); return cbCycles(rr, 8, 16); };
            cbOps[0x10 | reg] = () -> { int v=readReg8(rr); v=rl(v);  writeReg8(rr,v); return cbCycles(rr, 8, 16); };
            cbOps[0x18 | reg] = () -> { int v=readReg8(rr); v=rr(v);  writeReg8(rr,v); return cbCycles(rr, 8, 16); };
            cbOps[0x20 | reg] = () -> { int v=readReg8(rr); v=sla(v); writeReg8(rr,v); return cbCycles(rr, 8, 16); };
            cbOps[0x28 | reg] = () -> { int v=readReg8(rr); v=sra(v); writeReg8(rr,v); return cbCycles(rr, 8, 16); };
            cbOps[0x30 | reg] = () -> { int v=readReg8(rr); v=swap(v);writeReg8(rr,v); return cbCycles(rr, 8, 16); };
            cbOps[0x38 | reg] = () -> { int v=readReg8(rr); v=srl(v); writeReg8(rr,v); return cbCycles(rr, 8, 16); };
        }

        // BIT/RES/SET
        for (int bit = 0; bit < 8; bit++) {
            for (int reg = 0; reg < 8; reg++) {
                final int b = bit;
                final int rr = reg;

                // BIT b,r : Z set if bit is 0, N=0, H=1, C unaffected
                int bitOp = 0x40 | (bit << 3) | reg;
                cbOps[bitOp] = () -> {
                    int v = readReg8(rr);
                    boolean zero = ((v >> b) & 1) == 0;
                    setZ(zero);
                    setN(false);
                    setH(true);
                    // C unchanged
                    return cbCycles(rr, 8, 12);
                };

                // RES b,r : clear bit
                int resOp = 0x80 | (bit << 3) | reg;
                cbOps[resOp] = () -> {
                    int v = readReg8(rr);
                    v = v & ~(1 << b);
                    writeReg8(rr, v);
                    return cbCycles(rr, 8, 16);
                };

                // SET b,r : set bit
                int setOp = 0xC0 | (bit << 3) | reg;
                cbOps[setOp] = () -> {
                    int v = readReg8(rr);
                    v = v | (1 << b);
                    writeReg8(rr, v);
                    return cbCycles(rr, 8, 16);
                };
            }
        }
    }

    // ---------------- execute one instruction ----------------
    public int step() {
        // EI delay: IME becomes true before executing the next instruction after EI.
        if (imeEnablePending) {
            ime = true;
            imeEnablePending = false;
        }

        // If HALTed, we still need to wake on any pending interrupt
        if (halted) {
            if (hasPendingInterrupt()) halted = false;
            // if still halted, just burn cycles
            if (halted) return 4;
        }

        // Service interrupt if IME and pending
        int irqCycles = serviceInterruptsIfNeeded();
        if (irqCycles != 0) return irqCycles;

        int opcode = fetch8();
        return ops[opcode & 0xFF].run();
    }

    public boolean isHalted() { return halted; }
    public void setHalted(boolean halted) { this.halted = halted; }

    public boolean isIme() { return ime; }
    public void setIme(boolean v) { this.ime = v; }
}