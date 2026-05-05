# Game Boy Emulator

This project is a high-fidelity **Game Boy emulator** architected in **Java**, focusing on low-level system accuracy and modular hardware abstraction. It emulates the core components of the original handheld, including the CPU, memory management, and graphics rendering.

---

## 🚀 Key Features

* **Core CPU Emulation**: Implements a cycle-accurate **Sharp LR35902** processor engine, featuring the full instruction set and precise register state transitions.
* **Memory Management**: A comprehensive **Memory Management Unit (MMU)** that handles ROM/RAM banking, virtual cartridges, and internal memory mapping.
* **Graphics Pipeline**: Developed a real-time rendering module within `LCD.java` to emulate original display characteristics and frame interrupts.
* **Integrated GUI**: A JavaFX-based interface provided by `MainFrame.java` for interactive gameplay and ROM selection.
* **Hardware Extensions**: Support for specialized features such as **Link Cable** emulation (`Cable.java`) and configurable controller input logic.

---

## 🛠️ Project Structure

The codebase is organized into modular components to reflect the original hardware architecture:

| Component | Responsibility |
| :--- | :--- |
| **`cpu/cpu.java`** | Main instruction execution and interrupt handling. |
| **`cpu/registers.java`** | Manages 8-bit and 16-bit CPU registers and flags. |
| **`cpu/Memory.java`** | Handles the 64KB address space and memory-mapped I/O. |
| **`cpu/LCD.java`** | Controls pixel processing, PPU modes, and scanline timing. |
| **`cpu/Cartridge.java`** | Manages ROM loading and Memory Bank Controllers (MBC1, MBC3, MBC5). |
| **`views/MainFrame.java`** | Defines the graphical user interface using JavaFX. |

---

## 🔧 Installation & Build

This project uses **Maven** for dependency management and build automation.

### Prerequisites
* Java Development Kit (JDK) 25 or higher (configured in `pom.xml`).
* Apache Maven.

### Build the Project
To compile the project:
```bash
mvn clean compile
```
To run the project:
```bash
mvn javafx:run
```
