import javax.swing.*;
import javax.swing.table.*;
import java.awt.*;

public class StrategyPanel extends JPanel {
    private JTable table;
    private DefaultTableModel model;
    // Shortened headers for space
    private final String[] typeHeaders = {"Member", "Nor", "Fir", "Wat", "Ele", "Gra", "Ice", "Fig", "Poi", "Gro", "Fly", "Psy", "Bug", "Roc", "Gho", "Dra", "Dar", "Ste", "Fai"};

    public StrategyPanel() {
        setLayout(new BorderLayout());
        model = new DefaultTableModel(typeHeaders, 0);
        table = new JTable(model) {
            @Override
            public Component prepareRenderer(TableCellRenderer renderer, int row, int col) {
                Component c = super.prepareRenderer(renderer, row, col);
                if (col == 0) {
                    c.setBackground(new Color(245, 245, 245));
                    c.setForeground(Color.BLACK);
                    return c;
                }
                
                Object valObj = getValueAt(row, col);
                String val = valObj != null ? valObj.toString() : "";
                
                // Color Logic
                try {
                    if (val.equals("0")) {
                        c.setBackground(new Color(200, 200, 200)); // Immune (Grey)
                        c.setForeground(Color.DARK_GRAY);
                    } else if (val.equals("4") || val.equals("2")) {
                        c.setBackground(new Color(255, 180, 180)); // Weak (Red)
                        c.setForeground(Color.BLACK);
                    } else if (val.equals("0.5") || val.equals("0.25")) {
                        c.setBackground(new Color(180, 255, 180)); // Resist (Green)
                        c.setForeground(Color.BLACK);
                    } else {
                        c.setBackground(Color.WHITE);
                        c.setForeground(Color.LIGHT_GRAY);
                    }
                } catch (Exception e) {
                    c.setBackground(Color.WHITE);
                }
                return c;
            }
        };
        
        table.setRowHeight(30);
        table.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 11));
        table.setFont(new Font("Segoe UI", Font.BOLD, 12));
        
        // Center text
        DefaultTableCellRenderer centerRenderer = new DefaultTableCellRenderer();
        centerRenderer.setHorizontalAlignment(JLabel.CENTER);
        for(int i=1; i<table.getColumnCount(); i++) {
            table.getColumnModel().getColumn(i).setCellRenderer(centerRenderer);
            table.getColumnModel().getColumn(i).setPreferredWidth(35);
        }
        table.getColumnModel().getColumn(0).setPreferredWidth(150);

        add(new JScrollPane(table), BorderLayout.CENTER);
        
        JPanel legend = new JPanel(new FlowLayout(FlowLayout.CENTER));
        legend.add(new JLabel("<html><span style='background:#FFB4B4'>&nbsp;&nbsp;</span> Weak (2x/4x)</html>"));
        legend.add(new JLabel("<html><span style='background:#B4FFB4'>&nbsp;&nbsp;</span> Resist (0.5x/0.25x)</html>"));
        legend.add(new JLabel("<html><span style='background:#C8C8C8'>&nbsp;&nbsp;</span> Immune (0x)</html>"));
        add(legend, BorderLayout.NORTH);
    }

    public void displayTeam(Team team) {
        model.setRowCount(0);
        if (team == null) return;
        
        // Calculate coverage for each member
        for (Fusion f : team.members) {
            Object[] row = new Object[19];
            row[0] = f.getDisplayName();
            
            // Re-calculate types for visualization (Mock logic - in real app, use DataManager type chart)
            // Here we use the bitmask as a proxy for weaknesses, simplified for this snippet
            // 0:Norm, 1:Fire, 2:Watr, 3:Elec, 4:Gras, 5:Ice, 6:Figh, 7:Pois, 8:Grou, 9:Flyi, 10:Psyc, 11:Bug, 12:Rock, 13:Ghos, 14:Drag, 15:Dark, 16:Stee, 17:Fair
            
            for (int i = 0; i < 18; i++) {
                boolean isWeak = ((f.weaknessMask >> i) & 1) == 1;
                // Ideally you would pass the TypeChart here. For now, we only highlight Weaknesses accurately from the mask.
                if (isWeak) row[i+1] = "2"; 
                else row[i+1] = "-"; 
            }
            model.addRow(row);
        }
    }
}