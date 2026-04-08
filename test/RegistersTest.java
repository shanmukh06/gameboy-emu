import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

public class RegistersTest {

    @Test
    void test8BitSetGet() {
        registers r = new registers();

        r.setA(0x123); // overflow
        assertEquals(0x23, r.getA());

        r.setB(0xFF);
        assertEquals(0xFF, r.getB());
    }

    @Test
    void test16BitPairs() {
        registers r = new registers();

        r.setBC(0x1234);
        assertEquals(0x12, r.getB());
        assertEquals(0x34, r.getC());
        assertEquals(0x1234, r.getBC());
    }

    @Test
    void testFlags() {
        registers r = new registers();

        r.setFlag(registers.FLAG_Z);
        assertTrue(r.isZero());

        r.clearFlag(registers.FLAG_Z);
        assertFalse(r.isZero());
    }

    @Test
    void testPCIncrement() {
        registers r = new registers();

        r.setPC(0x1000);
        int old = r.incPC();

        assertEquals(0x1000, old);
        assertEquals(0x1001, r.getPC());
    }

    @Test
    void testReset() {
        registers r = new registers();
        r.reset();

        assertEquals(0x01B0, r.getAF());
        assertEquals(0x0013, r.getBC());
        assertEquals(0xFFFE, r.getSP());
        assertEquals(0x0100, r.getPC());
    }
}