import java.util.*;

public class Fusion { 
    public String headName, bodyName, typing, role, chosenAbility; 
    public int rank, hp, atk, def, spa, spd, spe, bst; 
    public double score;
    public List<FusionCalculator.AbilityResult> allAbilities = new ArrayList<>();
    
    // Bitmasks for lightning-fast defensive checks
    public long weaknessMask = 0L;

    public String getDisplayName() {
        return capitalize(headName) + " + " + capitalize(bodyName);
    }

    private String capitalize(String s) {
        return s == null || s.isEmpty() ? s : 
            s.substring(0, 1).toUpperCase() + s.substring(1).toLowerCase();
    }

    /**
     * Converts the typing string into a bitmask of weaknesses.
     */
    public void buildBitmasks(Map<String, Long> weaknessLookup) {
        this.weaknessMask = 0L;
        if (typing == null) return;
        for (String t : typing.split("/")) {
            this.weaknessMask |= weaknessLookup.getOrDefault(t.trim(), 0L);
        }
    }
}