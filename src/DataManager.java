import java.util.*;
import java.io.*;
import java.util.stream.Collectors;

class DataManager {
    public final PokemonDatabase pokemon;
    public final AbilityDatabase abilities;
    public final MovesetDatabase movesets;
    public final TypeRankings typeRankings;
    
    // NEW MANAGERS
    public final SynergyManager synergy;
    public final SpriteManager sprites;
    
    public DataManager() {
        pokemon = new PokemonDatabase("pokemon.csv");
        abilities = new AbilityDatabase("abilities.csv");
        movesets = new MovesetDatabase("movesets.csv");
        typeRankings = new TypeRankings("typeRanks.csv");
        
        // Initialize new systems
        synergy = new SynergyManager("synergies.csv");
        sprites = new SpriteManager("pokedex_ids.csv");
    }
    
    public String getDatabaseStats() {
        return String.format("%d Pokemon | %d Abilities | Rules Loaded", 
            pokemon.count(), abilities.count());
    }
}

class PokemonDatabase {
    private final Map<String, Pokemon> db = new HashMap<>();
    
    public PokemonDatabase(String filename) {
        loadFromFile(filename);
    }
    
    private void loadFromFile(String filename) {
        File file = new File(filename);
        if (!file.exists()) {
            System.err.println("ERROR: Pokemon database not found: " + filename);
            return;
        }
        
        try(Scanner s = new Scanner(file)) {
            while(s.hasNextLine()) {
                String line = s.nextLine().trim();
                if(line.isEmpty() || line.startsWith("Name,") || line.startsWith("#")) continue;
                
                // Use safe parsing
                String[] p = CSVUtils.parseLine(line);
                
                if(p.length >= 9) {
                    List<String> abs = new ArrayList<>();
                    for(int i = 9; i < Math.min(p.length, 12); i++) {
                        if(!p[i].trim().isEmpty()) abs.add(p[i].trim());
                    }
                    
                    Pokemon pokemon = new Pokemon(p[0].trim(), p[1].trim(), p[2].trim(),
                        Integer.parseInt(p[3].trim()), Integer.parseInt(p[4].trim()),
                        Integer.parseInt(p[5].trim()), Integer.parseInt(p[6].trim()),
                        Integer.parseInt(p[7].trim()), Integer.parseInt(p[8].trim()),
                        abs.toArray(new String[0]));
                    db.put(pokemon.name.toLowerCase(), pokemon);
                }
            }
            System.out.println("✓ Loaded " + db.size() + " Pokemon");
        } catch(Exception e) { 
            e.printStackTrace();
        }
    }
    
    public Pokemon get(String name) { 
        return db.get(name.toLowerCase()); 
    }
    
    public List<String> search(String query) {
        if(query.isEmpty()) {
            return db.values().stream()
                .map(p -> p.name)
                .sorted()
                .collect(Collectors.toList());
        }
        return db.keySet().stream()
            .filter(n -> n.contains(query.toLowerCase()))
            .map(n -> db.get(n).name)
            .sorted()
            .collect(Collectors.toList());
    }
    
    public Set<String> getAllNames() { return db.keySet(); }
    public int count() { return db.size(); }
}

class AbilityDatabase {
    private final Map<String, Double> scores = new HashMap<>();
    
    public AbilityDatabase(String filename) {
        loadFromFile(filename);
    }
    
    private void loadFromFile(String filename) {
        File file = new File(filename);
        if (!file.exists()) {
            System.err.println("ERROR: Abilities database not found: " + filename);
            return;
        }
        
        try(Scanner sc = new Scanner(file)) {
            while(sc.hasNextLine()) {
                String line = sc.nextLine().trim();
                if(!line.isEmpty() && !line.startsWith("#")) {
                    String[] p = CSVUtils.parseLine(line);
                    if(p.length == 2) {
                        scores.put(p[0].trim().toLowerCase(), 
                                 Double.parseDouble(p[1].trim()));
                    }
                }
            }
            System.out.println("✓ Loaded " + scores.size() + " abilities");
        } catch(Exception e) {
            e.printStackTrace();
        }
    }
    
    public double getScore(String ability) { 
        return scores.getOrDefault(ability.toLowerCase(), 0.5); 
    }
    
    public int count() { return scores.size(); }
}

class MovesetDatabase {
    private final Map<String, double[]> movesets = new HashMap<>();
    
    public MovesetDatabase(String filename) {
        loadFromFile(filename);
    }
    
    private void loadFromFile(String filename) {
        File file = new File(filename);
        if (!file.exists()) {
            System.err.println("ERROR: Movesets database not found: " + filename);
            return;
        }
        
        try(Scanner sc = new Scanner(file)) {
            while(sc.hasNextLine()) {
                String line = sc.nextLine().trim();
                if(!line.isEmpty() && !line.startsWith("#")) {
                    String[] p = CSVUtils.parseLine(line);
                    if(p.length >= 4) {
                        movesets.put(p[0].trim().toLowerCase(), 
                            new double[]{Double.parseDouble(p[1].trim()),
                                         Double.parseDouble(p[2].trim()),
                                         Double.parseDouble(p[3].trim())});
                    }
                }
            }
            System.out.println("✓ Loaded " + movesets.size() + " movesets");
        } catch(Exception e) {
            e.printStackTrace();
        }
    }
    
    public double getBestScore(String name, int atk, int spa) {
        double[] scores = movesets.getOrDefault(name.toLowerCase(), 
                                              new double[]{0.6, 0.6, 0.4});
        return (atk > spa) ? scores[0] : scores[1];
    }
    
    public int count() { return movesets.size(); }
}

class TypeRankings {
    private final Map<String, Integer> rankings = new HashMap<>();
    private int uniqueCount = 0;
    
    public TypeRankings(String filename) {
        loadFromFile(filename);
    }
    
    private void loadFromFile(String filename) {
        File file = new File(filename);
        if (!file.exists()) {
            System.err.println("ERROR: Type rankings not found: " + filename);
            return;
        }
        
        try(Scanner sc = new Scanner(file)) {
            while(sc.hasNextLine()) {
                String line = sc.nextLine().trim();
                if(!line.isEmpty() && !line.startsWith("Type,") && !line.startsWith("#")) {
                    String[] p = CSVUtils.parseLine(line);
                    if(p.length == 2) {
                        String typing = p[0].trim();
                        int rank = Integer.parseInt(p[1].trim());
                        rankings.put(typing, rank);
                        uniqueCount++;
                        
                        if(typing.contains("/")) {
                            String[] types = typing.split("/");
                            rankings.put(types[1] + "/" + types[0], rank);
                        }
                    }
                }
            }
            System.out.println("✓ Loaded " + uniqueCount + " type combinations");
        } catch(Exception e) {
            e.printStackTrace();
        }
    }
    
    public int getRank(String typing) { 
        return rankings.getOrDefault(typing, 172); 
    }
    
    public int count() { return rankings.size(); }
    public int uniqueCount() { return uniqueCount; }
}