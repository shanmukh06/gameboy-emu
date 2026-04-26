public class Gameboy {

  private final Memory memory;
  private final cpu cpu;
  private final registers regs;
  private final Cable cable;

  private Cartridge cartridge;

  private static final int CYCLES_PER_FRAME = 70224;

  public Gameboy() {
    this.memory = new Memory();
    this.regs = new registers();
    this.cpu = new cpu(regs, memory);

    this.cable = new Cable();
    memory.setLinkCable(cable);

    reset();
  }

  public void loadGame(String romPath) {
    this.cartridge = new Cartridge(romPath);
    memory.setCartridge(this.cartridge);

    regs.setPC(0x0100);
    regs.setSP(0xFFFE);
  }

  public void reset() {
    memory.reset();

    if (cartridge != null) {
      memory.setCartridge(cartridge);
    }

    regs.setPC(0x0000);
    regs.setSP(0xFFFE);
  }

  public void runFrame() {
    int cycles = 0;

    while (cycles < CYCLES_PER_FRAME && !cpu.isHalted()) {
      int elapsed = cpu.step();
      cycles += elapsed;

      memory.tick(elapsed);
    }
  }

  public void runFrames(int frames) {
    for (int i = 0; i < frames; i++) runFrame();
  }

  public Memory getMemory() { return memory; }
  public cpu getCpu() { return cpu; }
  public registers getRegisters() { return regs; }
  public Cartridge getCartridge() { return cartridge; }
}