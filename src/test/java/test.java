import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class MemoryTest {

    @Test
    void testReadWrite8() {
        test_mem mem = new test_mem();

        mem.write8(0xC000, 0x12);
        assertEquals(0x12, mem.read8(0xC000));

        // should mask to 8-bit
        mem.write8(0xC001, 0x1FF);
        assertEquals(0xFF, mem.read8(0xC001));
    }

    @Test
    void testAddressWraparound() {
        test_mem mem = new test_mem();

        mem.write8(0x0000, 0xAB);
        // 0x10000 wraps to 0x0000 in 16-bit address space
        assertEquals(0xAB, mem.read8(0x10000));
    }

    @Test
    void testReadWrite16LittleEndian() {
        memory mem = new test_mem();

        mem.write16(0x2000, 0xBEEF);
        assertEquals(0xEF, mem.read8(0x2000));
        assertEquals(0xBE, mem.read8(0x2001));

        assertEquals(0xBEEF, mem.read16(0x2000));
    }

    @Test
    void testLoadProgram() {
        test_mem mem = new test_mem();

        byte[] program = new byte[] {
                (byte) 0x3E, (byte) 0x42, // LD A,0x42 (just an example)
                (byte) 0x00               // NOP
        };

        mem.loadProgram(0x0100, program);

        assertEquals(0x3E, mem.read8(0x0100));
        assertEquals(0x42, mem.read8(0x0101));
        assertEquals(0x00, mem.read8(0x0102));
    }
}