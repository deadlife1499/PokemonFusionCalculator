import java.awt.*;
import javax.swing.*;

public class PokemonFusionGUI extends JFrame {
    private UIComponents ui;
    private DataManager data;
    private FusionCalculator calculator;
    private TeamBuilder teamBuilder;

    public static void main(String[] args) {
        try { 
            // Set the Look and Feel to the system default for a native look
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); 
        } catch (Exception ignored) {}
        
        // Ensure the GUI is created on the Event Dispatch Thread
        SwingUtilities.invokeLater(() -> new PokemonFusionGUI().setVisible(true));
    }

    public PokemonFusionGUI() {
        super("Pokemon Infinite Fusion Calculator v9.0");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1500, 980);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        try {
            // 1. Initialize Data Manager (Loads CSVs)
            // This can throw IOException if files are missing, so we MUST catch it.
            data = new DataManager();
            
            // 2. Initialize Logic Classes
            // Passing 'data' ensures they have access to the loaded Pokemon/Moves
            calculator = new FusionCalculator(data);
            teamBuilder = new TeamBuilder(data);
            
            // 3. Initialize UI Components
            ui = new UIComponents(this, data, calculator, teamBuilder);
            
            // 4. Build the Visual Interface
            add(ui.createTopPanel(), BorderLayout.NORTH);
            add(ui.createLeftPanel(), BorderLayout.WEST);
            add(ui.createTabbedPane(), BorderLayout.CENTER);
            
            // 5. Final Setup
            ui.initializeData();
            ui.log("✓ Application initialized successfully");
            ui.log("→ Add Pokemon to your roster and click 'Calculate All Fusions'");
            
        } catch (Exception e) {
            // CRITICAL FIX: Handle errors during startup (like missing files)
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, 
                "Error initializing application:\n" + e.getMessage(), 
                "Startup Error", 
                JOptionPane.ERROR_MESSAGE);
            System.exit(1);
        }
    }
}