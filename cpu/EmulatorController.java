import views.MainFrame; 

import java.io.File;
import java.util.HashSet;
import java.util.Set;

import javafx.animation.AnimationTimer;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.input.KeyCode;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

public class EmulatorController {

    private static final int RENDER_WIDTH = 160;
    private static final int RENDER_HEIGHT = 144;
    private static final int SCALE_FACTOR = 2;

    private final Set<KeyCode> pressed;
    private boolean emulatorRunning = false;
    
    private Gameboy gameboy;
    private MainFrame mainFrame;

    // Pre-allocate the screen image to avoid heavy Garbage Collection during 60FPS rendering
    private final WritableImage screenImage;
    private final PixelWriter pixelWriter;

    public EmulatorController() {
        this.pressed = new HashSet<>();
        this.screenImage = new WritableImage(RENDER_WIDTH, RENDER_HEIGHT);
        this.pixelWriter = screenImage.getPixelWriter();
    }

    public void initialize(final Stage primaryStage) {
        // Setup File Chooser
        FileChooser fileChooser = mainFrame.getFileChooser();
        fileChooser.setTitle("Open ROM");
        fileChooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("Gameboy ROMs", "*.gb", "*.gbc")
        );

        // Load Game Event
        mainFrame.getOpenItem().setOnAction(event -> {
            gameboy.setCpuRunning(false);
            File file = fileChooser.showOpenDialog(primaryStage);

            if (file != null) {
                gameboy.loadGame(new Cartridge(file.toString()));
            }
        });

        // Input Events
        mainFrame.getScene().setOnKeyPressed(e -> {
            if (!pressed.contains(e.getCode())) {
                pressed.add(e.getCode());
                handleJoypadInput(e.getCode(), true);
            }
        });

        mainFrame.getScene().setOnKeyReleased(e -> {
            pressed.remove(e.getCode());
            handleJoypadInput(e.getCode(), false);
        });

        // Save RAM on Close
        primaryStage.setOnCloseRequest(event -> gameboy.saveRam());

        start();
    }

    /**
     * Maps keyboard inputs to Gameboy Joypad bits.
     * 0:A, 1:B, 2:Select, 3:Start, 4:Right, 5:Left, 6:Up, 7:Down
     */
    private void handleJoypadInput(KeyCode code, boolean isPressed) {
        switch (code) {
            case S:     gameboy.updateJoypad(0, isPressed); break; // A
            case A:     gameboy.updateJoypad(1, isPressed); break; // B
            case X:     gameboy.updateJoypad(2, isPressed); break; // Select
            case Z:     gameboy.updateJoypad(3, isPressed); break; // Start
            case RIGHT: gameboy.updateJoypad(4, isPressed); break; // Right
            case LEFT:  gameboy.updateJoypad(5, isPressed); break; // Left
            case UP:    gameboy.updateJoypad(6, isPressed); break; // Up
            case DOWN:  gameboy.updateJoypad(7, isPressed); break; // Down
            case L:
                // Debug/Macro: Press Start, Select, B, A simultaneously
                gameboy.updateJoypad(3, isPressed);
                gameboy.updateJoypad(2, isPressed);
                gameboy.updateJoypad(1, isPressed);
                gameboy.updateJoypad(0, isPressed);
                break;
            default:
                break; // Ignore unmapped keys
        }
    }

    /**
     * Renders the raw RGB pixel array directly to the JavaFX Canvas.
     */
    private void render(int[] pixels) {
        GraphicsContext graphicsContext = mainFrame.getGraphics();

        // Write RGB data into the pre-allocated JavaFX WritableImage
        for (int y = 0; y < RENDER_HEIGHT; ++y) {
            for (int x = 0; x < RENDER_WIDTH; ++x) {
                int baseIndex = ((y * RENDER_WIDTH) + x) * 3;
                int r = pixels[baseIndex];
                int g = pixels[baseIndex + 1];
                int b = pixels[baseIndex + 2];

                // Convert RGB to 32-bit ARGB integer (Alpha = 255)
                int argb = (0xFF << 24) | (r << 16) | (g << 8) | b;
                pixelWriter.setArgb(x, y, argb);
            }
        }

        double canvasWidth = mainFrame.getScene().getWidth();
        double canvasHeight = mainFrame.getScene().getHeight();
        graphicsContext.clearRect(0, 0, canvasWidth, canvasHeight);

        // Draw the image natively scaled up
        graphicsContext.drawImage(screenImage, 0, 0, RENDER_WIDTH * SCALE_FACTOR, RENDER_HEIGHT * SCALE_FACTOR);
    }

    public void start() {
        if (emulatorRunning) {
            System.out.println("Emulator already running");
            return;
        }

        emulatorRunning = true;

        new AnimationTimer() {
            @Override
            public void handle(long now) {
                if (!emulatorRunning) {
                    System.out.println("Exiting Game Loop");
                    this.stop();
                    return;
                }

                if (gameboy != null && gameboy.getCpuRunning()) {
                    gameboy.runFrame();
                }

                if (gameboy != null) {
                    render(gameboy.getBuffer());
                }
            }
        }.start();
    }

    public void stop() {
        if (gameboy != null) {
            gameboy.reset();
            gameboy.setCpuRunning(false);
        }
        emulatorRunning = false;
    }

    public void pause() {
        if (gameboy != null) {
            gameboy.setCpuRunning(!gameboy.getCpuRunning());
        }
    }

    public String getTitle() {
        return "Gameboy Emulator";
    }

    public void setGameboy(Gameboy gameboy) {
        this.gameboy = gameboy;
    }

    public void setMainFrame(MainFrame mainFrame) {
        this.mainFrame = mainFrame;
    }
}