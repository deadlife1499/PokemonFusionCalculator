import java.io.*;
import java.util.*;

public class PokedexDatabase {
    private final Map<String, PokedexInfo> db = new HashMap<>();
    
    public PokedexDatabase(String filename) {
        loadFromFile(filename);
    }
    
    private void loadFromFile(String filename) {
        File file = new File(filename);
        if (!file.exists()) {
            System.err.println("Pokedex database not found: " + filename);
            return;
        }
        
        try (Scanner s = new Scanner(file)) {
            // Skip header if it exists
            if (s.hasNextLine()) {
                String head = s.nextLine();
                if (!head.startsWith("Name")) {
                     // Handle case where there is no header
                }
            }
            
            while (s.hasNextLine()) {
                String line = s.nextLine().trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                
                // Use the robust CSVUtils parser
                String[] p = CSVUtils.parseLine(line);
                
                // Name, Evolution, Locations, Notes
                if (p.length >= 1) {
                    String name = p[0].trim();
                    String evo = (p.length > 1) ? p[1].trim() : "";
                    String loc = (p.length > 2) ? p[2].trim() : "";
                    String notes = (p.length > 3) ? p[3].trim() : "";
                    
                    PokedexInfo info = new PokedexInfo(name, evo, loc, notes);
                    db.put(name.toLowerCase(), info);
                }
            }
            System.out.println("✓ Loaded " + db.size() + " Pokedex entries");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    public PokedexInfo getInfo(String name) {
        return db.get(name.toLowerCase());
    }
    
    public static class PokedexInfo {
        public final String name;
        public final String evolution;
        public final String locations;
        public final String notes;
        
        public PokedexInfo(String name, String evolution, String locations, String notes) {
            this.name = name;
            this.evolution = evolution;
            this.locations = locations;
            this.notes = notes;
        }
    }
}