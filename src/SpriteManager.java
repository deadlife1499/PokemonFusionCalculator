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
    private static final String FOLDER_PATH = "sprites/";

    public SpriteManager(String filename) {
        File folder = new File(FOLDER_PATH);
        if (!folder.exists()) folder.mkdirs();
        loadIds(filename);
    }

    private void loadIds(String filename) {
        File file = new File(filename);
        if (!file.exists()) return;

        try (Scanner sc = new Scanner(file)) {
            while (sc.hasNextLine()) {
                String line = sc.nextLine().trim();
                String[] p = CSVUtils.parseLine(line);
                if (p.length >= 2) {
                    // Normalize name to be safe
                    nameToId.put(p[1].toLowerCase().trim(), p[0].trim());
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public String getId(String pokemonName) {
        String norm = pokemonName.toLowerCase().trim();
        // If ID map missing, try to hash name to a number 1-898 as desperate fallback
        // or just return the name for custom sprites
        return nameToId.getOrDefault(norm, "0");
    }

    public ImageIcon getSprite(String pokemonName) {
        String norm = pokemonName.toLowerCase().trim();
        if (imageCache.containsKey(norm)) return imageCache.get(norm);

        File f = new File(FOLDER_PATH + norm + ".png");
        if (f.exists()) {
            try {
                BufferedImage img = ImageIO.read(f);
                Image scaled = img.getScaledInstance(200, 200, Image.SCALE_SMOOTH);
                ImageIcon icon = new ImageIcon(scaled);
                imageCache.put(norm, icon);
                return icon;
            } catch (Exception e) { e.printStackTrace(); }
        }
        return null;
    }

    // Returns true if downloaded, false if skipped/failed
    public boolean downloadSprite(String pokemonName) {
        String id = getId(pokemonName);
        if (id.equals("0")) return false; // Can't download without ID

        String norm = pokemonName.toLowerCase().trim();
        File f = new File(FOLDER_PATH + norm + ".png");
        if (f.exists()) return false;

        String[] urls = {
            "https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/other/official-artwork/" + id + ".png",
            "https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/" + id + ".png"
        };

        for (String u : urls) {
            try {
                URL url = new URL(u);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestProperty("User-Agent", "Mozilla/5.0");
                if (conn.getResponseCode() == 200) {
                    BufferedImage img = ImageIO.read(conn.getInputStream());
                    ImageIO.write(img, "png", f);
                    return true;
                }
            } catch (Exception ignored) {}
        }
        return false;
    }
}