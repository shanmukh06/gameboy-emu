import views.MainFrame;

import javafx.application.Application;
import javafx.stage.Stage;


public class Main extends Application {

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        // 1. Initialize core components
        Gameboy gameboy = new Gameboy();
        MainFrame mainFrame = new MainFrame();
        EmulatorController emulator = new EmulatorController();

        // 2. Wire the application together
        emulator.setGameboy(gameboy);
        emulator.setMainFrame(mainFrame);
        
        // 3. Setup the controller and UI events
        emulator.initialize(primaryStage);

        // 4. Configure and show the primary window
        primaryStage.setTitle(emulator.getTitle());
        primaryStage.setScene(mainFrame.getScene());
        
        // Prevents users from resizing the window and breaking the pixel scaling
        primaryStage.setResizable(false); 
        
        primaryStage.show();
    }
}