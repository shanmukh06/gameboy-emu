public class Cable {
  private int sb = 0x00;
  private int sc = 0x7E;

  private Memory mem;

  private static final int CYCLES_PER_BIT = 512;
  private static final int BITS_PER_BYTE = 8;

  private boolean transferring = false;
  private int cycleAcc = 0;
  private int bitsShifted = 0;

  private int outShift = 0;
  private int inShift = 0;

  public Cable() {}

  public void attachMemory(Memory memory) {
    this.mem = memory;
  }


  public int readSB() { return sb & 0xFF; }
  public int readSC() { return sc & 0xFF; }

  public void writeSB(int data) {
    sb = data & 0xFF;
  }

  public void writeSC(int data) {
    sc = data & 0xFF;

    if ((sc & 0x80) != 0) {
      startTransfer();
    }
  }

  private void startTransfer() {
    if (transferring) return;

    transferring = true;
    cycleAcc = 0;
    bitsShifted = 0;

    outShift = sb & 0xFF;
    inShift = 0;
  }

  private boolean internalClock() {
    return (sc & 0x01) != 0;
  }


  public void tick(int cycles) {
    if (!transferring) return;

    if (!internalClock()) return;

    cycleAcc += cycles;

    while (transferring && cycleAcc >= CYCLES_PER_BIT) {
      cycleAcc -= CYCLES_PER_BIT;
      shiftOneBitNoPartner();
    }
  }


  private void shiftOneBitNoPartner() {
    outShift = ((outShift << 1) & 0xFF);

    int inBit = 1;
    inShift = ((inShift << 1) | inBit) & 0xFF;

    bitsShifted++;
    if (bitsShifted >= BITS_PER_BYTE) {
      finishTransfer();
    }
  }

  private void finishTransfer() {
    sb = inShift & 0xFF;

    sc &= 0x7F;

    transferring = false;

    requestSerialInterrupt();
  }

  private void requestSerialInterrupt() {
    if (mem == null) return;

    int IF_ADDR = 0xFF0F;
    int iflag = mem.read8(IF_ADDR);
    mem.directWrite8(IF_ADDR, iflag | 0x08);
  }

  public boolean isTransferring() {
    return transferring;
  }

  public void stop() {
    transferring = false;
    sc &= 0x7F;
  }
}