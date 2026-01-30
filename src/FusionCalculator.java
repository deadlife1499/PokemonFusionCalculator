import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

class FusionCalculator {
    private final DataManager data;
    private int avgAtk, avgSpa, avgSpe, avgBulk;
    
    public FusionCalculator(DataManager data) {
        this.data = data;
        calculateStatBaselines();
    }
    
    private void calculateStatBaselines() {
        long totalAtk = 0, totalSpa = 0, totalSpe = 0, totalBulk = 0;
        int count = data.pokemon.count();
        if (count == 0) return;
        
        for (String name : data.pokemon.getAllNames()) {
            Pokemon p = data.pokemon.get(name);
            totalAtk += p.atk;
            totalSpa += p.spa;
            totalSpe += p.spe;
            totalBulk += (p.hp + p.def + p.spd);
        }
        
        this.avgAtk = (int)((totalAtk / count) * 1.2);
        this.avgSpa = (int)((totalSpa / count) * 1.2);
        this.avgSpe = (int)((totalSpe / count) * 1.1);
        this.avgBulk = (int)((totalBulk / count) * 1.2);
    }
    
    public void calculateAll(List<Pokemon> roster, ScoringWeights weights, 
                           boolean hiddenPenalty, FusionPool pool, 
                           TaskController task, java.util.function.Consumer<Integer> progressCallback) {
        
        int total = roster.size() * roster.size();
        int count = 0;
        int batchSize = 0;
        
        for (Pokemon head : roster) {
            for (Pokemon body : roster) {
                if (task.isCancelled()) return;
                
                // NEW: Get ALL variants (one per ability) instead of just the best one
                List<Fusion> variants = calculateVariants(head, body, weights, hiddenPenalty);
                
                for (Fusion f : variants) {
                    pool.add(f);
                }
                
                count++;
                batchSize++;
                
                if (batchSize >= 1000) {
                    // No trim called here to ensure exhaustive data
                    batchSize = 0;
                    progressCallback.accept(count);
                }
            }
        }
    }
    
    // NEW METHOD: Returns a list of fusions, one for each valid ability
    public List<Fusion> calculateVariants(Pokemon head, Pokemon body, ScoringWeights weights, boolean hiddenPenalty) {
        List<Fusion> variants = new ArrayList<>();
        
        // 1. Calculate Base Stats (Same for all variants)
        int hp = Math.round((head.hp * 2 + body.hp) / 3.0f);
        int spa = Math.round((head.spa * 2 + body.spa) / 3.0f);
        int spd = Math.round((head.spd * 2 + body.spd) / 3.0f);
        int atk = Math.round((head.atk + body.atk * 2) / 3.0f);
        int def = Math.round((head.def + body.def * 2) / 3.0f);
        int spe = Math.round((head.spe + body.spe * 2) / 3.0f);
        int bst = hp + atk + def + spa + spd + spe;
        
        String t1 = head.type1;
        String t2 = body.type2.equalsIgnoreCase("None") ? body.type1 : body.type2;
        if (t1.equalsIgnoreCase(t2)) {
            t2 = body.type1.equalsIgnoreCase(t1) ? "None" : body.type1;
        }
        String typing = t2.equalsIgnoreCase("None") || t1.equalsIgnoreCase(t2) ? t1 : t1 + "/" + t2;
        int rank = data.typeRankings.getRank(typing);
        
        // 2. Get Ability Combinations
        List<AbilityResult> abilities = getAllAbilityCombinations(head, body, typing, hp, atk, def, spa, spd, spe, hiddenPenalty);
        
        // 3. Create a distinct Fusion object for EACH ability
        for (AbilityResult ab : abilities) {
            Fusion f = new Fusion();
            f.headName = head.name;
            f.bodyName = body.name;
            f.hp = hp; f.atk = atk; f.def = def; 
            f.spa = spa; f.spd = spd; f.spe = spe; f.bst = bst;
            f.typing = typing;
            f.rank = rank;
            
            f.chosenAbility = ab.name;
            // Store the full list in each object just for reference, though we focused on one
            f.allAbilities = abilities; 
            
            // Calculate Score specifically for THIS ability
            double moveScore = (data.movesets.getBestScore(head.name, f.atk, f.spa) +
                                data.movesets.getBestScore(body.name, f.atk, f.spa)) / 2.0;
            
            double statScore = normalize(f.bst, 250, 680);
            double typeScore = 1.0 - normalize(f.rank, 1, 171);
            double abilityScore = Math.min(1.0, ab.score); // Use the specific ability score
            
            double total = weights.getTotal();
            double baseScore = (statScore * weights.stat + 
                                typeScore * weights.type + 
                                abilityScore * weights.ability + 
                                moveScore * weights.moveset) / total;
            
            double bonus = ab.synergy * 0.08;
            bonus += calculateStatBonus(f);
            
            double rawScore = baseScore + bonus;
            if (rawScore > 0.85) {
                double excess = rawScore - 0.85;
                rawScore = 0.85 + (excess * 0.3);
            }
            
            f.score = clamp(rawScore, 0.0, 1.0);
            f.score = Math.round(f.score * 1000.0) / 1000.0;
            f.role = determineDynamicRole(f, ab.score);
            
            variants.add(f);
        }
        
        return variants;
    }
    
    private List<AbilityResult> getAllAbilityCombinations(Pokemon head, Pokemon body, String typing, 
                                                          int hp, int atk, int def, int spa, int spd, int spe, 
                                                          boolean hiddenPenalty) {
        List<AbilityResult> results = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        
        for (Pokemon p : new Pokemon[]{head, body}) {
            for (int i = 0; i < p.abilities.size(); i++) {
                String ab = p.abilities.get(i);
                if (ab == null || ab.equalsIgnoreCase("None") || ab.isEmpty()) continue;
                if (seen.contains(ab.toLowerCase())) continue;
                seen.add(ab.toLowerCase());
                
                double score = data.abilities.getScore(ab);
                if (hiddenPenalty && i == 2) score *= 0.8;
                
                double synergy = data.synergy.calculateSynergy(ab, typing, hp, atk, def, spa, spd, spe);
                double total = score + synergy * 0.1;
                
                results.add(new AbilityResult(ab, Math.min(1.0, score), synergy, total));
            }
        }
        
        // Sort best to worst
        results.sort((a, b) -> Double.compare(b.totalScore, a.totalScore));
        return results;
    }

    private String determineDynamicRole(Fusion f, double abilityScore) {
        if (abilityScore >= 0.95) return "Ability Carry";
        
        int higherOffense = Math.max(f.atk, f.spa);
        int bulk = f.hp + f.def + f.spd;
        
        if (f.spe > avgSpe && higherOffense > avgAtk) return "Sweeper";
        if (bulk > avgBulk) return "Wall/Tank";
        if (higherOffense > (avgAtk * 1.15)) return "Wallbreaker"; 
        if (f.atk > avgAtk && f.spa > avgSpa) return "Mixed Attacker";
        if (f.spe < (avgSpe * 0.8) && higherOffense > avgAtk) return "Slow Pivot";
        if (f.spe > avgSpe && bulk > (avgBulk * 0.9)) return "Fast Support";
        
        return "Balanced";
    }
    
    private double calculateStatBonus(Fusion f) {
        double bonus = 0.0;
        if (f.spe >= 135) bonus += 0.04;
        else if (f.spe >= 120) bonus += 0.02;
        
        int maxOffense = Math.max(f.atk, f.spa);
        if (maxOffense >= 145) bonus += 0.04;
        else if (maxOffense >= 135) bonus += 0.02;
        
        boolean isVeryBulky = f.hp > 110 && (f.def > 110 && f.spd > 110);
        if (isVeryBulky) bonus += 0.03;
        
        if (f.bst < 400) bonus -= 0.05;
        
        return bonus;
    }
    
    private double normalize(double value, double min, double max) {
        return Math.max(0.0, Math.min((value - min) / (max - min), 1.0));
    }
    
    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(value, max));
    }
    
    static class AbilityResult {
        String name;
        double score;
        double synergy;
        double totalScore;
        
        AbilityResult(String n, double s, double syn, double tot) {
            name = n; score = s; synergy = syn; totalScore = tot;
        }
    }

    public String getDetailedBreakdown(Fusion f, ScoringWeights weights) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== SCORING BREAKDOWN ===\n");
        sb.append("Fusion: ").append(f.getDisplayName()).append("\n");
        sb.append("Role: ").append(f.role).append("\n\n");
        
        double statScore = normalize(f.bst, 250, 680);
        sb.append(String.format("Base Stat Score: %.3f (BST: %d)\n", statScore, f.bst));
        
        double typeScore = 1.0 - normalize(f.rank, 1, 171);
        sb.append(String.format("Type Score: %.3f (Rank: %d - %s)\n", typeScore, f.rank, f.typing));
        
        // Find result for the chosen ability
        AbilityResult ar = null;
        for(AbilityResult a : f.allAbilities) {
            if(a.name.equals(f.chosenAbility)) { ar = a; break; }
        }
        
        if (ar != null) {
            double abTotal = Math.min(1.0, ar.score + ar.synergy * 0.1);
            sb.append(String.format("Ability Score: %.3f (%s)\n", abTotal, f.chosenAbility));
            sb.append(String.format("   Base: %.2f | Synergy Bonus: %+.2f\n", ar.score, ar.synergy * 0.1));
        }
        
        sb.append("\nTotal Score: ").append(f.score).append("\n");
        return sb.toString();
    }
}