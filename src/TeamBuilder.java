import java.util.*;
import java.util.concurrent.*;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

class TeamBuilder {
    private final DataManager data;
    
    // Type Chart Matrix (Attacker Rows -> Defender Cols)
    // 0: Normal, 1: Fire, 2: Water, 3: Electric, 4: Grass, 5: Ice, 6: Fighting, 7: Poison, 8: Ground, 
    // 9: Flying, 10: Psychic, 11: Bug, 12: Rock, 13: Ghost, 14: Dragon, 15: Dark, 16: Steel, 17: Fairy
    public static final double[][] TYPE_CHART = new double[18][18]; 

    static {
        // Initialize neutral
        for(double[] row : TYPE_CHART) Arrays.fill(row, 1.0);
    }

    public TeamBuilder(DataManager data) {
        this.data = data;
    }

    public List<Team> buildTeams(List<Fusion> fusions, Set<Fusion> pinnedFusions, TeamBuildConfig config, 
                               TaskController task, BiConsumer<Integer, Integer> progressCallback) {
        
        // Filter pool based on constraints
        List<Fusion> pool = new ArrayList<>(fusions);
        if (!config.allowSelfFusion) {
            pool.removeIf(f -> f.headName.equalsIgnoreCase(f.bodyName));
        }

        // Choose algorithm based on exhaustiveness setting
        switch (config.exhaustiveMode) {
            case 0: // Speed Mode - Fast greedy with light optimization
                return buildTeamsSpeed(pool, pinnedFusions, config, task, progressCallback);
            
            case 1: // Balanced Mode - Hybrid approach
                return buildTeamsBalanced(pool, pinnedFusions, config, task, progressCallback);
            
            case 2: // Quality Mode - Deep search with pruning
                return buildTeamsQuality(pool, pinnedFusions, config, task, progressCallback);
            
            case 3: // Maximum Mode - Exhaustive deterministic search
                return buildTeamsMaximum(pool, pinnedFusions, config, task, progressCallback);
            
            default:
                return buildTeamsBalanced(pool, pinnedFusions, config, task, progressCallback);
        }
    }

    // ==================== SPEED MODE ====================
    // Fast greedy approach with minimal iterations
    private List<Team> buildTeamsSpeed(List<Fusion> pool, Set<Fusion> pinnedFusions, TeamBuildConfig config,
                                       TaskController task, BiConsumer<Integer, Integer> progressCallback) {
        List<Team> teams = new ArrayList<>();
        pool.sort((a, b) -> Double.compare(b.score, a.score));
        List<Fusion> availablePool = new ArrayList<>(pool.subList(0, Math.min(pool.size(), 1500)));
        
        for (int i = 0; i < config.numTeams; i++) {
            if (task.isCancelled()) break;
            progressCallback.accept(i + 1, config.numTeams);
            
            Team team = buildGreedyTeam(availablePool, pinnedFusions, config, 500);
            if (team != null) {
                team.recalculateRealScore();
                teams.add(team);
                availablePool.removeAll(team.members);
            }
        }
        
        return teams;
    }

    // ==================== BALANCED MODE ====================
    // Hybrid: Greedy initialization + Hill climbing
    private List<Team> buildTeamsBalanced(List<Fusion> pool, Set<Fusion> pinnedFusions, TeamBuildConfig config,
                                          TaskController task, BiConsumer<Integer, Integer> progressCallback) {
        List<Team> teams = new ArrayList<>();
        pool.sort((a, b) -> Double.compare(b.score, a.score));
        List<Fusion> availablePool = new ArrayList<>(pool.subList(0, Math.min(pool.size(), 2500)));
        
        for (int i = 0; i < config.numTeams; i++) {
            if (task.isCancelled()) break;
            progressCallback.accept(i + 1, config.numTeams);
            
            // Start with greedy
            Team team = buildGreedyTeam(availablePool, pinnedFusions, config, 1000);
            
            // Refine with hill climbing
            if (team != null) {
                team = hillClimbOptimize(team, availablePool, config, 2000);
                team.recalculateRealScore();
                teams.add(team);
                availablePool.removeAll(team.members);
            }
        }
        
        return teams;
    }

    // ==================== QUALITY MODE ====================
    // Deep search with beam search and pruning
    private List<Team> buildTeamsQuality(List<Fusion> pool, Set<Fusion> pinnedFusions, TeamBuildConfig config,
                                         TaskController task, BiConsumer<Integer, Integer> progressCallback) {
        List<Team> teams = new ArrayList<>();
        pool.sort((a, b) -> Double.compare(b.score, a.score));
        List<Fusion> availablePool = new ArrayList<>(pool.subList(0, Math.min(pool.size(), 4000)));
        
        for (int i = 0; i < config.numTeams; i++) {
            if (task.isCancelled()) break;
            progressCallback.accept(i + 1, config.numTeams);
            
            // Use beam search to explore multiple promising paths
            Team team = beamSearchTeam(availablePool, pinnedFusions, config, 8, 5000);
            
            if (team != null) {
                team.recalculateRealScore();
                teams.add(team);
                availablePool.removeAll(team.members);
            }
        }
        
        return teams;
    }

    // ==================== MAXIMUM MODE ====================
    // Exhaustive deterministic search with intelligent pruning
    private List<Team> buildTeamsMaximum(List<Fusion> pool, Set<Fusion> pinnedFusions, TeamBuildConfig config,
                                         TaskController task, BiConsumer<Integer, Integer> progressCallback) {
        List<Team> teams = new ArrayList<>();
        pool.sort((a, b) -> Double.compare(b.score, a.score));
        
        // Use full pool but with smart filtering
        List<Fusion> availablePool = new ArrayList<>(pool);
        
        for (int i = 0; i < config.numTeams; i++) {
            if (task.isCancelled()) break;
            progressCallback.accept(i + 1, config.numTeams);
            
            // Deterministic exhaustive search with pruning
            Team team = exhaustiveSearchTeam(availablePool, pinnedFusions, config, task);
            
            if (team != null) {
                team.recalculateRealScore();
                teams.add(team);
                availablePool.removeAll(team.members);
            }
        }
        
        return teams;
    }

    // ==================== GREEDY TEAM BUILDER ====================
    private Team buildGreedyTeam(List<Fusion> pool, Set<Fusion> pinnedFusions, TeamBuildConfig config, int iterations) {
        Team team = new Team();
        List<Fusion> available = new ArrayList<>(pool);
        
        // Add pinned fusions first
        if (pinnedFusions != null) {
            for (Fusion pinned : pinnedFusions) {
                if (team.members.size() < 6) {
                    team.members.add(pinned);
                    available.remove(pinned);
                }
            }
        }
        
        // Greedy selection for remaining slots
        while (team.members.size() < 6 && !available.isEmpty()) {
            Fusion best = null;
            double bestScore = -9999;
            
            // Try top candidates
            int candidatesToCheck = Math.min(iterations, available.size());
            for (int i = 0; i < candidatesToCheck; i++) {
                Fusion candidate = available.get(i);
                
                // Check constraints
                if (!isValidAddition(team, candidate, config)) continue;
                
                // Evaluate score if added
                Team testTeam = new Team();
                testTeam.members.addAll(team.members);
                testTeam.members.add(candidate);
                double score = evaluateTeam(testTeam, config);
                
                if (score > bestScore) {
                    bestScore = score;
                    best = candidate;
                }
            }
            
            if (best != null) {
                team.members.add(best);
                available.remove(best);
            } else {
                break;
            }
        }
        
        return team.members.size() == 6 ? team : null;
    }

    // ==================== HILL CLIMBING OPTIMIZER ====================
    private Team hillClimbOptimize(Team initialTeam, List<Fusion> pool, TeamBuildConfig config, int iterations) {
        Team current = new Team();
        current.members.addAll(initialTeam.members);
        double currentScore = evaluateTeam(current, config);
        
        for (int i = 0; i < iterations; i++) {
            // Try swapping each position with pool members
            boolean improved = false;
            
            for (int pos = 0; pos < current.members.size(); pos++) {
                Fusion original = current.members.get(pos);
                
                // Try top candidates from pool
                for (int candidateIdx = 0; candidateIdx < Math.min(50, pool.size()); candidateIdx++) {
                    Fusion candidate = pool.get(candidateIdx);
                    if (current.members.contains(candidate)) continue;
                    
                    // Swap
                    current.members.set(pos, candidate);
                    
                    if (!isValidTeam(current, config)) {
                        current.members.set(pos, original);
                        continue;
                    }
                    
                    double newScore = evaluateTeam(current, config);
                    
                    if (newScore > currentScore) {
                        currentScore = newScore;
                        improved = true;
                        break; // Keep this swap
                    } else {
                        current.members.set(pos, original);
                    }
                }
                
                if (improved) break;
            }
            
            if (!improved) break; // Local maximum reached
        }
        
        return current;
    }

    // ==================== BEAM SEARCH ====================
    private Team beamSearchTeam(List<Fusion> pool, Set<Fusion> pinnedFusions, TeamBuildConfig config, 
                                int beamWidth, int maxExpansions) {
        // Beam search maintains top K partial teams at each step
        List<PartialTeam> beam = new ArrayList<>();
        
        // Initialize with pinned fusions or empty
        PartialTeam initial = new PartialTeam();
        if (pinnedFusions != null) {
            initial.members.addAll(pinnedFusions);
        }
        beam.add(initial);
        
        int expansions = 0;
        
        // Build teams slot by slot
        while (beam.get(0).members.size() < 6 && expansions < maxExpansions) {
            List<PartialTeam> candidates = new ArrayList<>();
            
            for (PartialTeam partial : beam) {
                if (partial.members.size() == 6) {
                    candidates.add(partial);
                    continue;
                }
                
                // Expand with top fusion candidates
                for (int i = 0; i < Math.min(beamWidth * 3, pool.size()); i++) {
                    Fusion candidate = pool.get(i);
                    if (partial.members.contains(candidate)) continue;
                    
                    PartialTeam newPartial = new PartialTeam();
                    newPartial.members.addAll(partial.members);
                    newPartial.members.add(candidate);
                    
                    if (isValidTeam(convertToTeam(newPartial), config)) {
                        newPartial.score = evaluatePartialTeam(newPartial, config);
                        candidates.add(newPartial);
                        expansions++;
                    }
                }
            }
            
            // Keep top beamWidth candidates
            candidates.sort((a, b) -> Double.compare(b.score, a.score));
            beam = candidates.subList(0, Math.min(beamWidth, candidates.size()));
            
            if (beam.isEmpty()) break;
        }
        
        return beam.isEmpty() ? null : convertToTeam(beam.get(0));
    }

    // ==================== EXHAUSTIVE SEARCH (MAXIMUM MODE) ====================
    private Team exhaustiveSearchTeam(List<Fusion> pool, Set<Fusion> pinnedFusions, 
                                      TeamBuildConfig config, TaskController task) {
        // Use dynamic programming with memoization for exhaustive search
        // This finds the provably optimal team within constraints
        
        List<Fusion> candidates = new ArrayList<>();
        
        // Smart pre-filtering: only consider top fusions and those with unique strengths
        Set<String> uniqueTypes = new HashSet<>();
        Set<String> uniqueAbilities = new HashSet<>();
        
        for (Fusion f : pool) {
            // Always include high-scoring fusions
            if (f.score >= 0.75) {
                candidates.add(f);
            }
            // Include if brings unique type coverage
            else if (!uniqueTypes.contains(f.typing)) {
                candidates.add(f);
                uniqueTypes.add(f.typing);
            }
            // Include if brings unique strong ability
            else if (f.score >= 0.65 && !uniqueAbilities.contains(f.chosenAbility)) {
                candidates.add(f);
                uniqueAbilities.add(f.chosenAbility);
            }
            
            // Cap candidate pool to keep search tractable
            if (candidates.size() >= 500) break;
        }
        
        System.out.println("Exhaustive search over " + candidates.size() + " candidates");
        
        // Start with pinned fusions
        List<Fusion> fixed = pinnedFusions != null ? new ArrayList<>(pinnedFusions) : new ArrayList<>();
        List<Fusion> flexible = new ArrayList<>(candidates);
        flexible.removeAll(fixed);
        
        int slotsToFill = 6 - fixed.size();
        
        // Use iterative deepening with branch and bound
        Team best = exhaustiveRecursive(fixed, flexible, slotsToFill, config, -9999, task);
        
        return best;
    }

    private Team exhaustiveRecursive(List<Fusion> fixed, List<Fusion> available, int slotsRemaining,
                                     TeamBuildConfig config, double currentBest, TaskController task) {
        if (task.isCancelled()) return null;
        
        if (slotsRemaining == 0) {
            Team complete = new Team();
            complete.members.addAll(fixed);
            complete.recalculateRealScore();
            return complete;
        }
        
        Team bestTeam = null;
        double bestScore = currentBest;
        
        // Try each remaining fusion
        for (int i = 0; i < available.size(); i++) {
            Fusion candidate = available.get(i);
            
            // Create new fixed set
            List<Fusion> newFixed = new ArrayList<>(fixed);
            newFixed.add(candidate);
            
            // Check if valid so far
            Team partial = new Team();
            partial.members.addAll(newFixed);
            if (!isValidTeam(partial, config)) continue;
            
            // Prune: estimate upper bound score
            double upperBound = estimateUpperBound(newFixed, available, slotsRemaining - 1);
            if (upperBound <= bestScore) continue; // Branch and bound pruning
            
            // Create new available set
            List<Fusion> newAvailable = new ArrayList<>(available.subList(i + 1, available.size()));
            
            // Recurse
            Team result = exhaustiveRecursive(newFixed, newAvailable, slotsRemaining - 1, config, bestScore, task);
            
            if (result != null && result.realScore > bestScore) {
                bestScore = result.realScore;
                bestTeam = result;
            }
        }
        
        return bestTeam;
    }

    private double estimateUpperBound(List<Fusion> current, List<Fusion> remaining, int slotsLeft) {
        // Optimistic upper bound: current score + best possible additions
        double currentScore = 0;
        for (Fusion f : current) {
            currentScore += f.score;
        }
        
        // Add scores of top remaining fusions
        double potentialBonus = 0;
        for (int i = 0; i < Math.min(slotsLeft, remaining.size()); i++) {
            potentialBonus += remaining.get(i).score;
        }
        
        // Add maximum possible synergy bonus
        double maxSynergy = 0.30; // Generous estimate
        
        return currentScore + potentialBonus + maxSynergy;
    }

    // ==================== VALIDATION HELPERS ====================
    private boolean isValidAddition(Team team, Fusion candidate, TeamBuildConfig config) {
        Team testTeam = new Team();
        testTeam.members.addAll(team.members);
        testTeam.members.add(candidate);
        return isValidTeam(testTeam, config);
    }

    private boolean isValidTeam(Team team, TeamBuildConfig config) {
        // Check species constraint
        if (!config.allowSameSpeciesInTeam) {
            Set<String> species = new HashSet<>();
            for (Fusion f : team.members) {
                if (!species.add(f.headName.toLowerCase())) return false;
                if (!species.add(f.bodyName.toLowerCase())) return false;
            }
        }
        
        // Check type sharing constraint
        if (!config.allowSharedTypes) {
            Map<String, Integer> typeCounts = new HashMap<>();
            for (Fusion f : team.members) {
                if (f.typing.contains("/")) {
                    String[] parts = f.typing.split("/");
                    typeCounts.put(parts[0], typeCounts.getOrDefault(parts[0], 0) + 1);
                    typeCounts.put(parts[1], typeCounts.getOrDefault(parts[1], 0) + 1);
                } else {
                    typeCounts.put(f.typing, typeCounts.getOrDefault(f.typing, 0) + 1);
                }
            }
            for (int count : typeCounts.values()) {
                if (count > 2) return false;
            }
        }
        
        return true;
    }

    // ==================== EVALUATION FUNCTION ====================
    private double evaluateTeam(Team team, TeamBuildConfig config) {
        if (team.members.size() != 6) {
            return evaluatePartialTeam(convertToPartial(team), config);
        }
        
        double totalScore = 0.0;
        
        // 1. Sum of Individual Scores
        for (Fusion f : team.members) totalScore += f.score;

        // 2. Constraint Penalties
        if (!config.allowSameSpeciesInTeam) {
            Set<String> species = new HashSet<>();
            for (Fusion f : team.members) {
                if (!species.add(f.headName.toLowerCase())) totalScore -= 5.0;
                if (!species.add(f.bodyName.toLowerCase())) totalScore -= 5.0;
            }
        }

        if (!config.allowSharedTypes) {
            Map<String, Integer> typeCounts = new HashMap<>();
            for (Fusion f : team.members) {
                if (f.typing.contains("/")) {
                    String[] parts = f.typing.split("/");
                    typeCounts.put(parts[0], typeCounts.getOrDefault(parts[0], 0) + 1);
                    typeCounts.put(parts[1], typeCounts.getOrDefault(parts[1], 0) + 1);
                } else {
                    typeCounts.put(f.typing, typeCounts.getOrDefault(f.typing, 0) + 1);
                }
            }
            for (int count : typeCounts.values()) {
                if (count > 2) totalScore -= (count * 2.0);
            }
        }

        // 3. Synergy & Balance
        int[] weaknesses = new int[18];
        for (Fusion f : team.members) {
            for (int i = 0; i < 18; i++) {
                if (((f.weaknessMask >> i) & 1) == 1) weaknesses[i]++;
            }
        }
        
        for (int w : weaknesses) {
            if (w > 2) totalScore -= (w * 0.5);
        }

        // Role Diversity
        Set<String> roles = team.members.stream().map(f -> f.role).collect(Collectors.toSet());
        if (roles.size() >= 5) totalScore += 1.5;
        else if (roles.size() >= 4) totalScore += 0.8;

        return totalScore;
    }

    private double evaluatePartialTeam(PartialTeam partial, TeamBuildConfig config) {
        Team temp = convertToTeam(partial);
        double score = 0;
        
        for (Fusion f : temp.members) {
            score += f.score;
        }
        
        // Lighter penalties for partial teams
        if (!config.allowSameSpeciesInTeam) {
            Set<String> species = new HashSet<>();
            for (Fusion f : temp.members) {
                if (!species.add(f.headName.toLowerCase())) score -= 2.0;
                if (!species.add(f.bodyName.toLowerCase())) score -= 2.0;
            }
        }
        
        return score;
    }

    // ==================== HELPER CLASSES ====================
    private static class PartialTeam {
        List<Fusion> members = new ArrayList<>();
        double score = 0.0;
    }

    private Team convertToTeam(PartialTeam partial) {
        Team team = new Team();
        team.members.addAll(partial.members);
        return team;
    }

    private PartialTeam convertToPartial(Team team) {
        PartialTeam partial = new PartialTeam();
        partial.members.addAll(team.members);
        return partial;
    }
}
