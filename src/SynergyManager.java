import java.io.*;
import java.util.*;

class SynergyManager {
    private final List<SynergyRule> rules = new ArrayList<>();

    public SynergyManager(String filename) {
        loadRules(filename);
    }

    private void loadRules(String filename) {
        File file = new File(filename);
        if (!file.exists()) {
            // Load default backup rules if file is missing
            loadDefaults();
            return;
        }

        try (Scanner sc = new Scanner(file)) {
            while (sc.hasNextLine()) {
                String line = sc.nextLine().trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                
                String[] p = CSVUtils.parseLine(line);
                if (p.length >= 4) {
                    rules.add(new SynergyRule(p[0], p[1], p[2], Double.parseDouble(p[3])));
                }
            }
            System.out.println("✓ Loaded " + rules.size() + " synergy rules");
        } catch (Exception e) {
            e.printStackTrace();
            loadDefaults();
        }
    }

    private void loadDefaults() {
        // Fallback defaults so the app works out of the box
        rules.add(new SynergyRule("Levitate", "type", "Ground", -0.05)); // Redundant
        rules.add(new SynergyRule("Levitate", "weakness", "Ground", 0.15)); // Good
        rules.add(new SynergyRule("Huge Power", "stat_atk", ">100", 0.20));
        rules.add(new SynergyRule("Speed Boost", "stat_spe", ">90", 0.15));
    }

    public double calculateSynergy(String ability, String typeLine, int hp, int atk, int def, int spa, int spd, int spe) {
        double bonus = 0.0;
        String ab = ability.toLowerCase();
        String ty = typeLine.toLowerCase();

        for (SynergyRule rule : rules) {
            if (!rule.abilityName.equalsIgnoreCase(ab)) continue;

            boolean conditionMet = false;
            switch (rule.checkType) {
                case "type":
                    conditionMet = ty.contains(rule.checkValue.toLowerCase());
                    break;
                case "stat_atk":
                    conditionMet = checkStat(atk, rule.checkValue);
                    break;
                case "stat_spa":
                    conditionMet = checkStat(spa, rule.checkValue);
                    break;
                case "stat_spe":
                    conditionMet = checkStat(spe, rule.checkValue);
                    break;
                case "bulk": // (Def+SpD)
                    conditionMet = checkStat(def + spd, rule.checkValue);
                    break;
            }
            if (conditionMet) bonus += rule.scoreModifier;
        }
        return bonus;
    }

    private boolean checkStat(int stat, String check) {
        if (check.startsWith(">")) return stat > Integer.parseInt(check.substring(1));
        if (check.startsWith("<")) return stat < Integer.parseInt(check.substring(1));
        return false;
    }

    private static class SynergyRule {
        String abilityName, checkType, checkValue;
        double scoreModifier;

        SynergyRule(String a, String t, String v, double s) {
            abilityName = a; checkType = t; checkValue = v; scoreModifier = s;
        }
    }
}