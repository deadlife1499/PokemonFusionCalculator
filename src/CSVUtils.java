import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JTable;
import javax.swing.table.TableModel;

public class CSVUtils {
    // Robust CSV splitter that handles quoted strings containing commas
    public static String[] parseLine(String line) {
        List<String> tokens = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        boolean inQuotes = false;
        
        for (char c : line.toCharArray()) {
            if (c == '\"') {
                inQuotes = !inQuotes; // Toggle state
            } else if (c == ',' && !inQuotes) {
                tokens.add(sb.toString().trim());
                sb.setLength(0); // Reset buffer
            } else {
                sb.append(c);
            }
        }
        tokens.add(sb.toString().trim());
        return tokens.toArray(new String[0]);
    }

    public static void exportTableToCSV(JTable table, File file) throws IOException {
        try (PrintWriter pw = new PrintWriter(new FileWriter(file))) {
            TableModel model = table.getModel();
            
            // Header
            for (int i = 0; i < model.getColumnCount(); i++) {
                pw.print(model.getColumnName(i));
                pw.print(i == model.getColumnCount() - 1 ? "" : ",");
            }
            pw.println();
            
            // Data
            for (int i = 0; i < model.getRowCount(); i++) {
                for (int j = 0; j < model.getColumnCount(); j++) {
                    Object val = model.getValueAt(i, j);
                    // Escape quotes and wrap in quotes if contains comma
                    String s = val == null ? "" : val.toString();
                    if (s.contains(",") || s.contains("\"")) {
                        s = "\"" + s.replace("\"", "\"\"") + "\"";
                    }
                    pw.print(s);
                    pw.print(j == model.getColumnCount() - 1 ? "" : ",");
                }
                pw.println();
            }
        }
    }
}