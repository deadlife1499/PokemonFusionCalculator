import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.ConcurrentHashMap;

public class SpriteManager {
    private final Map<String, String> nameToId = new HashMap<>();
    private final Map<String, ImageIcon> imageCache = new ConcurrentHashMap<>();

    // Ensure the folder path has a trailing slash
    private static final String FOLDER_PATH = "sprites/";

    public SpriteManager(String filename) {
        File folder = new File(FOLDER_PATH);
        if (!folder.exists()) {
            folder.mkdirs();
        }

        // Use the specific file path you mentioned earlier
        // If your main class passes just "pokedex_data.csv", this ensures we look in
        // the right folder if needed.
        // If the file is in the root, change this to just 'filename'.
        File csvFile = new File(filename);
        if (!csvFile.exists() && filename.equals("pokedex_data.csv")) {
            // Fallback: Check if it's inside the sprites folder
            csvFile = new File(FOLDER_PATH + filename);
        }

        if (csvFile.exists()) {
            loadIds(csvFile);
        } else {
            System.err.println(
                    "SpriteManager Warning: Could not find CSV at " + filename + " or " + FOLDER_PATH + filename);
        }
    }

    private void loadIds(File file) {
        try (Scanner sc = new Scanner(file)) {
            System.out.println("--- MAPPING KNOWN IDS ---");
            int idCounter = 0;

            while (sc.hasNextLine()) {
                String line = sc.nextLine().trim();

                // Skip empty lines or comments
                if (line.isEmpty() || line.startsWith("#"))
                    continue;

                // Assumes you have CSVUtils class in your project.
                // If not, you can replace this with: String[] p = line.split(",");
                String[] p = CSVUtils.parseLine(line);

                // Skip the Header row ("Name,Evolution,Locations...")
                if (p.length > 0 && p[0].equalsIgnoreCase("Name"))
                    continue;

                if (p.length >= 1) {
                    idCounter++; // 1 for Bulbasaur, 2 for Ivysaur, etc.

                    String name = p[0].trim();
                    String id = String.valueOf(idCounter);

                    // Map the name to the counter ID
                    nameToId.put(name.toLowerCase(), id);

                    // Manual fixes for symbols
                    if (name.equals("Nidoran♀"))
                        nameToId.put("nidoran-f", id);
                    if (name.equals("Nidoran♂"))
                        nameToId.put("nidoran-m", id);
                }
            }
            System.out.println("✓ Mapped " + nameToId.size() + " Pokemon from local file (Gen 1-2).");

        } catch (Exception e) {
            System.err.println("Error reading ID CSV:");
            e.printStackTrace();
        }
    }

    public String getId(String pokemonName) {
        String norm = pokemonName.toLowerCase().trim();
        return nameToId.getOrDefault(norm, "0");
    }

    /**
     * Self-Healing Logic:
     * If we don't know the ID (e.g. Salamence, Sylveon), ask the API.
     */
    private String fetchIdFromApi(String name) {
        try {
            System.out.println("... Auto-discovering ID for: " + name);
            // Handle edge cases for API naming conventions
            String apiName = name.replace(".", "").replace(" ", "-").replace("'", "");

            URL url = new URL("https://pokeapi.co/api/v2/pokemon/" + apiName);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");
            conn.setConnectTimeout(3000); // 3 second timeout per request

            if (conn.getResponseCode() == 200) {
                try (Scanner sc = new Scanner(conn.getInputStream())) {
                    // Quick regex scan for "id": 123 in the JSON response
                    String result = sc.findWithinHorizon("\"id\":\\s*(\\d+)", 0);
                    if (result != null) {
                        String id = result.replaceAll("[^0-9]", "");
                        System.out.println("   -> Found ID: " + id);
                        return id;
                    }
                }
            } else {
                System.out.println("   -> API lookup failed (HTTP " + conn.getResponseCode() + ")");
            }
        } catch (Exception e) {
            System.err.println("   -> Network error looking up ID for " + name);
        }
        return "0";
    }

    public ImageIcon getSprite(String pokemonName) {
        String norm = pokemonName.toLowerCase().trim();
        if (imageCache.containsKey(norm))
            return imageCache.get(norm);

        File f = new File(FOLDER_PATH + norm + ".png");
        if (f.exists()) {
            try {
                BufferedImage img = ImageIO.read(f);
                if (img != null) {
                    Image scaled = img.getScaledInstance(200, 200, Image.SCALE_SMOOTH);
                    ImageIcon icon = new ImageIcon(scaled);
                    imageCache.put(norm, icon);
                    return icon;
                }
            } catch (Exception e) {
                System.err.println("Error reading image: " + f.getName());
            }
        }
        return null;
    }

    public boolean downloadSprite(String pokemonName) {
        String norm = pokemonName.toLowerCase().trim();
        File f = new File(FOLDER_PATH + norm + ".png");

        // If it already exists, we count it as a success (or skip)
        if (f.exists())
            return false;

        // 1. Try local map (Fast)
        String id = getId(norm);

        // 2. If missing, try API auto-discovery (The Fix)
        if (id.equals("0")) {
            id = fetchIdFromApi(norm);
            if (!id.equals("0")) {
                nameToId.put(norm, id); // Save for next time so we don't ask API again
            }
        }

        if (id.equals("0")) {
            System.out.println("Skipping " + pokemonName + " (Could not find ID locally or via API).");
            return false;
        }

        String[] urls = {
                "https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/other/official-artwork/" + id
                        + ".png",
                "https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/" + id + ".png"
        };

        for (String u : urls) {
            try {
                URL url = new URL(u);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestProperty("User-Agent", "Mozilla/5.0");
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);

                if (conn.getResponseCode() == 200) {
                    BufferedImage img = ImageIO.read(conn.getInputStream());
                    if (img != null) {
                        ImageIO.write(img, "png", f);
                        System.out.println("Downloaded: " + pokemonName + " (ID: " + id + ")");
                        return true;
                    }
                }
            } catch (Exception ignored) {
            }
        }
        return false;
    }
}