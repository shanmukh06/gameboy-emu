public class Gameboy {

  private final Memory memory;
  private final cpu cpu;
  private final registers regs;
  private final Cable cable;
  private final LCD lcd;

  private Cartridge cartridge;

  // UI uses this to pause/resume emulation
  private boolean cpuRunning = false;

  // DMG cycles per frame (approx)
  private static final int CYCLES_PER_FRAME = 70224;

  public Gameboy() {
    this.memory = new Memory();
    this.regs = new registers();
    this.cpu = new cpu(regs, memory);

    this.cable = new Cable();
    memory.setLinkCable(cable);

    this.lcd = new LCD();
    lcd.setMemory(memory);
    lcd.setCpu(cpu); // NOTE: requires you to fix LCD.setCpu(...) as described earlier

    reset();
  }

  /** UI-friendly load: EmulatorController calls this. */
  public void loadGame(Cartridge cartridge) {
    reset();

    this.cartridge = cartridge;
    memory.setCartridge(cartridge);

    // Start after boot ROM area (since you're not running BIOS here)
    regs.setPC(0x0100);
    regs.setSP(0xFFFE);

    setCpuRunning(true);
  }

  /** Convenience overload if you want to load via path (runner/tests). */
  public void loadGame(String romPath) {
    loadGame(new Cartridge(romPath));
  }

  public void runFrame() {
    int cycles = 0;

    while (cycles < CYCLES_PER_FRAME && !cpu.isHalted()) {
      int elapsed = cpu.step();
      cycles += elapsed;

      // Link cable timing uses CPU cycles
      memory.tick(elapsed);

      // Your LCD.tick() advances one "dot"/cycle at a time
      for (int i = 0; i < elapsed; i++) {
        lcd.tick();
      }
    }
  }

  /** Used by UI rendering. */
  public int[] getBuffer() {
    return lcd.buffer;
  }

  /**
   * Joypad bit mapping matches your EmulatorController:
   * 0:A, 1:B, 2:Select, 3:Start, 4:Right, 5:Left, 6:Up, 7:Down
   */
  public void updateJoypad(int bit, boolean pressed) {
    boolean beforeHigh = ((memory.joypad >> bit) & 1) != 0;

    if (pressed) memory.joypad &= ~(1 << bit);   // pressed => 0
    else memory.joypad |= (1 << bit);            // released => 1

    boolean afterHigh = ((memory.joypad >> bit) & 1) != 0;

    // Interrupt on transition from 1 -> 0 (released -> pressed)
    if (beforeHigh && !afterHigh) {
      cpu.requestInterrupt(4); // Joypad interrupt
    }
  }

  /** UI calls this on close. Safe no-op until you implement battery saves. */
  public void saveRam() {
    // TODO: implement battery-backed RAM save if/when you add it
    // For now, do nothing so the app compiles and closes cleanly.
  }

  public boolean getCpuRunning() {
    return cpuRunning;
  }

  public void setCpuRunning(boolean value) {
    cpuRunning = value;
  }

  public void reset() {
    memory.reset();
    regs.reset();
    lcd.reset();

    cpu.setHalted(false);
    cpu.setIme(false);

    cpuRunning = false;

    // If a cart was already inserted, keep it inserted after reset
    if (cartridge != null) {
      memory.setCartridge(cartridge);
    }
  }

  // ---- Accessors (optional) ----
  public Memory getMemory() { return memory; }
  public cpu getCpu() { return cpu; }
  public registers getRegisters() { return regs; }
  public Cartridge getCartridge() { return cartridge; }
}