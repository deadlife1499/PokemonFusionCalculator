import javax.swing.*;
import java.awt.*;

public class PokemonFusionGUI extends JFrame {
    private final UIComponents ui;
    private final DataManager data;
    private final FusionCalculator calculator;
    private final TeamBuilder teamBuilder;

    public static void main(String[] args) {
        try { 
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); 
        } catch (Exception ignored) {}
        SwingUtilities.invokeLater(() -> new PokemonFusionGUI().setVisible(true));
    }

    public PokemonFusionGUI() {
        super("Pokemon Infinite Fusion Calculator v9.0");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1500, 980);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        // Initialize components
        data = new DataManager();
        calculator = new FusionCalculator(data);
        teamBuilder = new TeamBuilder(data);
        ui = new UIComponents(this, data, calculator, teamBuilder);
        
        // Build UI
        add(ui.createTopPanel(), BorderLayout.NORTH);
        add(ui.createLeftPanel(), BorderLayout.WEST);
        add(ui.createTabbedPane(), BorderLayout.CENTER);
        
        ui.initializeData();
        ui.log("✓ Application initialized successfully");
        ui.log("→ Add Pokemon to your roster and click 'Calculate All Fusions'");
    }
}
