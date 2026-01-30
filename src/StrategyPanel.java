import java.awt.*;
import java.util.HashMap;
import java.util.Map;
import javax.swing.*;
import javax.swing.table.*;

public class StrategyPanel extends JPanel {
    private JTable table;
    private DefaultTableModel model;
    
    // Standard Gen 6+ Type Chart
    // 0:Normal, 1:Fire, 2:Water, 3:Electric, 4:Grass, 5:Ice, 6:Fighting, 7:Poison, 8:Ground
    // 9:Flying, 10:Psychic, 11:Bug, 12:Rock, 13:Ghost, 14:Dragon, 15:Dark, 16:Steel, 17:Fairy
    private final String[] typeHeaders = {"Member", "Nor", "Fir", "Wat", "Ele", "Gra", "Ice", "Fig", "Poi", "Gro", "Fly", "Psy", "Bug", "Roc", "Gho", "Dra", "Dar", "Ste", "Fai"};
    
    // 2.0 = Weakness, 0.5 = Resistance, 0.0 = Immunity
    private static final double[][] TYPE_CHART = {
        // Defending:
        // Nor Fir Wat Ele Gra Ice Fig Poi Gro Fly Psy Bug Roc Gho Dra Dar Ste Fai  <-- Attacking
        {1, 1, 1, 1, 1, 1, 2, 1, 1, 1, 1, 1, 1, 0, 1, 1, 1, 1}, // Normal
        {1, 0.5, 2, 1, 0.5, 0.5, 1, 1, 2, 1, 1, 0.5, 2, 1, 1, 1, 0.5, 0.5}, // Fire
        {1, 0.5, 0.5, 2, 2, 0.5, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 0.5, 1}, // Water
        {1, 1, 1, 0.5, 1, 1, 1, 1, 2, 0.5, 1, 1, 1, 1, 1, 1, 0.5, 1}, // Electric
        {1, 2, 0.5, 0.5, 0.5, 2, 1, 2, 0.5, 2, 1, 2, 1, 1, 1, 1, 1, 1}, // Grass
        {1, 2, 1, 1, 1, 0.5, 2, 1, 1, 1, 1, 1, 2, 1, 1, 1, 2, 1}, // Ice
        {1, 1, 1, 1, 1, 1, 1, 1, 1, 2, 2, 0.5, 0.5, 1, 1, 0.5, 1, 2}, // Fighting
        {1, 1, 1, 1, 0.5, 1, 0.5, 0.5, 2, 1, 2, 0.5, 1, 1, 1, 1, 1, 0.5}, // Poison
        {1, 1, 2, 0, 2, 2, 1, 0.5, 1, 1, 1, 1, 0.5, 1, 1, 1, 1, 1}, // Ground
        {1, 1, 1, 2, 0.5, 2, 0.5, 1, 0, 1, 1, 0.5, 2, 1, 1, 1, 1, 1}, // Flying
        {1, 1, 1, 1, 1, 1, 0.5, 1, 1, 1, 0.5, 2, 1, 2, 1, 2, 1, 1}, // Psychic
        {1, 2, 1, 1, 0.5, 1, 0.5, 1, 0.5, 2, 1, 1, 2, 1, 1, 1, 1, 1}, // Bug
        {0.5, 0.5, 2, 1, 2, 1, 2, 0.5, 2, 0.5, 1, 1, 1, 1, 1, 1, 2, 1}, // Rock
        {0, 1, 1, 1, 1, 1, 0, 0.5, 1, 1, 1, 0.5, 1, 2, 1, 2, 1, 1}, // Ghost
        {1, 0.5, 0.5, 0.5, 0.5, 2, 1, 1, 1, 1, 1, 1, 1, 1, 2, 1, 1, 2}, // Dragon
        {1, 1, 1, 1, 1, 1, 2, 1, 1, 1, 0, 2, 1, 0.5, 1, 0.5, 1, 2}, // Dark
        {0.5, 2, 1, 1, 0.5, 0.5, 2, 0, 2, 0.5, 0.5, 0.5, 0.5, 1, 0.5, 1, 0.5, 0.5}, // Steel
        {1, 1, 1, 1, 1, 1, 0.5, 2, 1, 1, 1, 0.5, 1, 1, 0, 0.5, 2, 1}  // Fairy
    };

    private Map<String, Integer> typeIndexMap;

    public StrategyPanel() {
        setLayout(new BorderLayout());
        
        initializeTypeMap();
        
        model = new DefaultTableModel(typeHeaders, 0);
        table = new JTable(model) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }

            @Override
            public Component prepareRenderer(TableCellRenderer renderer, int row, int col) {
                Component c = super.prepareRenderer(renderer, row, col);
                if (col == 0) {
                    c.setBackground(new Color(245, 245, 245));
                    c.setForeground(Color.BLACK);
                    c.setFont(new Font("Segoe UI", Font.BOLD, 12));
                    return c;
                }
                
                Object valObj = getValueAt(row, col);
                String val = valObj != null ? valObj.toString() : "";
                
                // Color Logic based on effectiveness
                try {
                    double v = Double.parseDouble(val);
                    if (v == 0) {
                        c.setBackground(new Color(220, 220, 220)); // Immune (Grey)
                        c.setForeground(new Color(100, 100, 100));
                    } else if (v >= 4.0) {
                        c.setBackground(new Color(255, 100, 100)); // 4x Weak (Dark Red)
                        c.setForeground(Color.WHITE);
                    } else if (v >= 2.0) {
                        c.setBackground(new Color(255, 180, 180)); // 2x Weak (Light Red)
                        c.setForeground(Color.BLACK);
                    } else if (v <= 0.25) {
                        c.setBackground(new Color(100, 200, 100)); // 0.25x Resist (Dark Green)
                        c.setForeground(Color.WHITE);
                    } else if (v <= 0.5) {
                        c.setBackground(new Color(180, 255, 180)); // 0.5x Resist (Light Green)
                        c.setForeground(Color.BLACK);
                    } else {
                        c.setBackground(Color.WHITE);
                        c.setForeground(new Color(240, 240, 240)); // Hide 1x text slightly
                    }
                } catch (Exception e) {
                    c.setBackground(Color.WHITE);
                }
                return c;
            }
        };
        
        table.setRowHeight(28);
        table.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 11));
        table.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        
        // Center text
        DefaultTableCellRenderer centerRenderer = new DefaultTableCellRenderer();
        centerRenderer.setHorizontalAlignment(JLabel.CENTER);
        for(int i=1; i<table.getColumnCount(); i++) {
            table.getColumnModel().getColumn(i).setCellRenderer(centerRenderer);
            table.getColumnModel().getColumn(i).setPreferredWidth(35);
        }
        table.getColumnModel().getColumn(0).setPreferredWidth(160);

        add(new JScrollPane(table), BorderLayout.CENTER);
        
        JPanel legend = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 5));
        legend.add(createLegendItem(new Color(255, 100, 100), "4x"));
        legend.add(createLegendItem(new Color(255, 180, 180), "2x"));
        legend.add(createLegendItem(Color.WHITE, "1x"));
        legend.add(createLegendItem(new Color(180, 255, 180), "0.5x"));
        legend.add(createLegendItem(new Color(100, 200, 100), "0.25x"));
        legend.add(createLegendItem(new Color(220, 220, 220), "0x"));
        add(legend, BorderLayout.NORTH);
    }
    
    private JLabel createLegendItem(Color c, String text) {
        JLabel l = new JLabel(text);
        l.setOpaque(true);
        l.setBackground(c);
        l.setBorder(BorderFactory.createLineBorder(Color.LIGHT_GRAY));
        l.setPreferredSize(new Dimension(30, 20));
        l.setHorizontalAlignment(SwingConstants.CENTER);
        return l;
    }

    private void initializeTypeMap() {
        typeIndexMap = new HashMap<>();
        String[] types = {"Normal", "Fire", "Water", "Electric", "Grass", "Ice", "Fighting", "Poison", "Ground", 
                          "Flying", "Psychic", "Bug", "Rock", "Ghost", "Dragon", "Dark", "Steel", "Fairy"};
        for (int i = 0; i < types.length; i++) {
            typeIndexMap.put(types[i].toLowerCase(), i);
        }
    }

    public void displayTeam(Team team) {
        model.setRowCount(0);
        if (team == null) return;
        
        for (Fusion f : team.members) {
            Object[] row = new Object[19];
            row[0] = f.getDisplayName();
            
            String[] types = f.typing.contains("/") ? f.typing.split("/") : new String[]{f.typing};
            
            // Calculate effectiveness for each attacking type (columns 1-18)
            for (int i = 0; i < 18; i++) {
                double eff = 1.0;
                for (String t : types) {
                    Integer defIndex = typeIndexMap.get(t.toLowerCase());
                    if (defIndex != null) {
                        // TYPE_CHART[defending][attacking]
                        eff *= TYPE_CHART[defIndex][i];
                    }
                }
                
                // Account for Abilities (Simplified)
                if (eff > 0) {
                    String ab = f.chosenAbility.toLowerCase();
                    String atkType = typeHeaders[i+1].toLowerCase(); // approximate check
                    
                    if (ab.contains("levitate") && i == 8) eff = 0; // Ground
                    if (ab.contains("flash fire") && i == 1) eff = 0; // Fire
                    if (ab.contains("volt absorb") && i == 3) eff = 0; // Electric
                    if (ab.contains("water absorb") && i == 2) eff = 0; // Water
                    if (ab.contains("dry skin") && i == 2) eff = 0; // Water
                    if (ab.contains("sap sipper") && i == 4) eff = 0; // Grass
                    if (ab.contains("motor drive") && i == 3) eff = 0; // Electric
                    if (ab.contains("storm drain") && i == 2) eff = 0; // Water
                    
                    // Filter Thick Fat (Fire/Ice)
                    if (ab.contains("thick fat") && (i == 1 || i == 5)) eff *= 0.5;
                    // Filter Heatproof (Fire)
                    if (ab.contains("heatproof") && i == 1) eff *= 0.5;
                }
                
                // Format string to remove .0 if integer
                if (eff == Math.floor(eff)) {
                    row[i+1] = String.format("%.0f", eff);
                } else {
                    row[i+1] = String.format("%.2f", eff).replace("0.", ".");
                }
            }
            model.addRow(row);
        }
    }
}