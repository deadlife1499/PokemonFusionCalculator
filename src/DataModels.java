import java.util.*;

class Pokemon {
    String name, type1, type2;
    int hp, atk, def, spa, spd, spe;
    List<String> abilities = new ArrayList<>();
    
    public Pokemon(String n, String t1, String t2, int h, int a, int d, 
                  int sa, int sd, int s, String... abs) {
        name = n; type1 = t1; type2 = t2; 
        hp = h; atk = a; def = d; spa = sa; spd = sd; spe = s;
        if(abs != null) Collections.addAll(abilities, abs);
    }
    
    public int getBST() {
        return hp + atk + def + spa + spd + spe;
    }
}

class Team {
    List<Fusion> members = new ArrayList<>();
    double simScore = 0.0;
    double realScore = 0.0;
    double balanceBonus = 0.0;
    
    void add(Fusion f, double dynScore) { 
        members.add(f); 
        simScore += dynScore; 
    }
    
    void recalculateRealScore() { 
        realScore = members.stream().mapToDouble(f -> f.score).sum();
        balanceBonus = calculateBalanceBonus();
        realScore += balanceBonus;
    }
    
    // IMPROVED: Calculate team balance bonus based on role diversity
    private double calculateBalanceBonus() {
        Map<String, Integer> roleCounts = new HashMap<>();
        for (Fusion f : members) {
            roleCounts.put(f.role, roleCounts.getOrDefault(f.role, 0) + 1);
        }
        
        double bonus = 0.0;
        
        // Ideal team has diverse roles
        int uniqueRoles = roleCounts.size();
        if (uniqueRoles >= 5) bonus += 0.15;      // Very diverse
        else if (uniqueRoles >= 4) bonus += 0.08; // Good diversity
        else if (uniqueRoles >= 3) bonus += 0.03; // Some diversity
        
        // Check for good role coverage
        boolean hasSweeper = roleCounts.containsKey("Sweeper");
        boolean hasWall = roleCounts.containsKey("Wall/Tank");
        boolean hasWallbreaker = roleCounts.containsKey("Wallbreaker");
        boolean hasSupport = roleCounts.containsKey("Fast Support") || roleCounts.containsKey("Slow Pivot");
        
        if (hasSweeper && hasWall) bonus += 0.05;
        if (hasWallbreaker) bonus += 0.03;
        if (hasSupport) bonus += 0.02;
        
        // Penalty for too many of same role (imbalanced)
        for (int count : roleCounts.values()) {
            if (count >= 4) bonus -= 0.10;  // 4+ of same role is bad
            else if (count >= 3) bonus -= 0.05;  // 3 of same role is suboptimal
        }
        
        // Check offensive/defensive balance
        int offensive = 0, defensive = 0, mixed = 0;
        for (Fusion f : members) {
            if (f.role.equals("Sweeper") || f.role.equals("Wallbreaker")) offensive++;
            else if (f.role.equals("Wall/Tank")) defensive++;
            else if (f.role.equals("Mixed Attacker") || f.role.equals("Balanced")) mixed++;
        }
        
        // Ideal is 2-3 offensive, 1-2 defensive, rest support/mixed
        if (offensive >= 2 && offensive <= 3 && defensive >= 1 && defensive <= 2) {
            bonus += 0.05;
        }
        
        // Penalty for all offense or all defense
        if (offensive >= 5) bonus -= 0.08;
        if (defensive >= 4) bonus -= 0.08;
        
        return bonus;
    }
    
    public boolean isFull() {
        return members.size() >= 6;
    }
}

class ScoringWeights {
    double stat, type, ability, moveset;
    
    public ScoringWeights(double s, double t, double a, double m) {
        stat = s; type = t; ability = a; moveset = m;
    }
    
    public double getTotal() {
        return stat + type + ability + moveset;
    }
    
    public static ScoringWeights getDefaults() {
        return new ScoringWeights(0.40, 0.30, 0.25, 0.05);
    }
}