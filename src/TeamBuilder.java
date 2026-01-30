import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class TeamBuilder {
    private final DataManager data;
    
    // Theoretical maximum bonus a team can gain from "Secondary Info" (Role diversity, etc.)
    // Used for the pruning "ceiling". 
    // +1.5 for Role Diversity is the main positive factor. 
    // Penalties are negative, so we assume a perfect team has 0 penalties.
    // We add a safety margin to ensure we never prune a valid solution.
    private static final double MAX_POSSIBLE_DELTA = 2.0;

    // Shared global maximum score for pruning across all threads
    private volatile double globalMaxScore = -Double.MAX_VALUE;
    private final Object resultLock = new Object();
    private Team globalBestTeam = null;

    public TeamBuilder(DataManager data) {
        this.data = data;
    }

    /**
     * Main entry point for the Team Builder.
     */
    public List<Team> buildTeams(List<Fusion> fusions, Set<Fusion> pinnedFusions, TeamBuildConfig config, 
                               TaskController task, BiConsumer<Integer, Integer> progressCallback) {
        
        System.out.println("Starting Deterministic Branch and Bound Search...");
        long startTime = System.currentTimeMillis();

        // 1. PREPARE & SORT DATA (Step A)
        List<Fusion> pool = new ArrayList<>(fusions);
        
        // Apply basic filtering
        if (!config.allowSelfFusion) {
            pool.removeIf(f -> f.headName.equalsIgnoreCase(f.bodyName));
        }
        
        // Essential: Sort by BaseScore Descending for the Branch and Bound to work
        pool.sort((a, b) -> Double.compare(b.score, a.score));

        // Handle Pinned Fusions
        List<Fusion> pinnedList = pinnedFusions != null ? new ArrayList<>(pinnedFusions) : new ArrayList<>();
        // Remove pinned members from the pool so we don't pick them twice
        pool.removeAll(pinnedList);

        // 2. ESTABLISH LOWER BOUND (Step B)
        // Calculate the score of the "Greedy" top 6 to set an initial baseline (Smax)
        initializeLowerBound(pool, pinnedList, config);

        // 3. PARALLEL ITERATIVE SEARCH (Step C)
        // We split the search space. 
        // Core 1 checks combinations starting with Pool[0]
        // Core 2 checks combinations starting with Pool[1] (excluding Pool[0])
        // ... and so on.
        
        int n = pool.size();
        int k = 6 - pinnedList.size(); // Remaining slots to fill

        if (k <= 0) {
            // Edge case: User pinned 6 or more pokemon
            Team t = new Team();
            t.members.addAll(pinnedList.subList(0, 6));
            t.recalculateRealScore();
            return Collections.singletonList(t);
        }

        // Executor for parallel tasks
        int cores = Runtime.getRuntime().availableProcessors();
        ForkJoinPool customThreadPool = new ForkJoinPool(cores);
        AtomicInteger completedTasks = new AtomicInteger(0);

        try {
            // optimization: We don't need to check EVERY index as a start.
            // If the base score of pool[i] is so low that even with perfect future picks 
            // we can't beat the globalMax, we stop creating new tasks.
            // However, inside the parallel stream, we let the internal pruning handle this logic 
            // to keep the outer loop clean.
            
            customThreadPool.submit(() -> 
                IntStream.range(0, n).parallel().forEach(i -> {
                    if (task.isCancelled()) return;

                    // Pruning at the root level:
                    // If starting with this pokemon makes it mathematically impossible to win, skip.
                    double currentBase = 0;
                    for(Fusion p : pinnedList) currentBase += p.score;
                    currentBase += pool.get(i).score;
                    
                    if (canPrune(currentBase, i, k - 1, pool)) {
                        return; // Skip this branch entirely
                    }

                    // Start the recursive solver for this branch
                    // We create a new list for the current path to ensure thread safety
                    List<Fusion> currentMembers = new ArrayList<>(pinnedList);
                    currentMembers.add(pool.get(i));
                    
                    solveBranch(pool, currentMembers, currentBase, i, config, task);
                    
                    // Update progress (rough estimate)
                    int done = completedTasks.incrementAndGet();
                    if (done % 100 == 0 || done == n) {
                        progressCallback.accept(done, n);
                    }
                })
            ).get();
            
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }

        long duration = System.currentTimeMillis() - startTime;
        System.out.println("Search finished in " + duration + "ms. Best Score: " + globalMaxScore);

        if (globalBestTeam != null) {
            // Return only the absolute best team found
            return Collections.singletonList(globalBestTeam);
        }
        
        return new ArrayList<>();
    }

    /**
     * Step B: Establish a "Lower Bound" quickly using the top available fusions.
     */
    private void initializeLowerBound(List<Fusion> pool, List<Fusion> pinned, TeamBuildConfig config) {
        Team greedyTeam = new Team();
        greedyTeam.members.addAll(pinned);
        
        int needed = 6 - greedyTeam.members.size();
        for (int i = 0; i < Math.min(needed, pool.size()); i++) {
            greedyTeam.members.add(pool.get(i));
        }
        
        if (greedyTeam.members.size() == 6) {
            double score = calculateTotalScore(greedyTeam.members, config);
            updateGlobalBest(score, greedyTeam);
        }
    }

    /**
     * Step C: Recursive Branch and Bound Solver.
     * * @param pool The full sorted list of candidates.
     * @param currentTeam The members currently in this group.
     * @param currentBaseScore The sum of base scores of members in currentTeam.
     * @param lastIndex The index in 'pool' of the last added member (to ensure forward iteration).
     */
    private void solveBranch(List<Fusion> pool, List<Fusion> currentTeam, double currentBaseScore, 
                             int lastIndex, TeamBuildConfig config, TaskController task) {
        
        if (task.isCancelled()) return;

        // 1. Check if Team is Full
        if (currentTeam.size() == 6) {
            // Calculate final score including Delta (Synergy)
            double delta = calculateDelta(currentTeam, config);
            double finalScore = currentBaseScore + delta;
            
            if (finalScore > globalMaxScore) {
                Team newBest = new Team();
                newBest.members.addAll(currentTeam);
                newBest.realScore = finalScore; // Cache the score
                updateGlobalBest(finalScore, newBest);
            }
            return;
        }

        // 2. PRUNING (The Ceiling Check)
        int slotsRemaining = 6 - currentTeam.size();
        
        // Check if we have enough candidates left
        if (lastIndex + 1 >= pool.size()) return;

        // PRUNING RULE:
        // Max Possible Score = Current Base + (Sum of next best 'slotsRemaining' base scores) + Max Delta
        // If this theoretical ceiling is <= globalMaxScore, we stop.
        if (canPrune(currentBaseScore, lastIndex, slotsRemaining, pool)) {
            return;
        }

        // 3. Branching
        // Iterate through remaining candidates
        for (int i = lastIndex + 1; i < pool.size(); i++) {
            Fusion candidate = pool.get(i);

            // Optimization: Validation check before recursion
            // If adding this candidate violates hard constraints (Species/Types), skip immediately
            if (!isValidAddition(currentTeam, candidate, config)) {
                continue;
            }

            // Add candidate
            currentTeam.add(candidate);
            double newBaseScore = currentBaseScore + candidate.score;

            // Recurse
            solveBranch(pool, currentTeam, newBaseScore, i, config, task);

            // Backtrack
            currentTeam.remove(currentTeam.size() - 1);
        }
    }

    /**
     * Determines if the current branch should be pruned based on the mathematical ceiling.
     */
    private boolean canPrune(double currentBase, int lastIndex, int slotsRemaining, List<Fusion> pool) {
        double maxFutureBase = 0;
        
        // Sum the scores of the next best available fusions
        // Since the list is sorted, picking the immediate next ones gives the highest possible base score
        int count = 0;
        for (int i = lastIndex + 1; i < pool.size() && count < slotsRemaining; i++) {
            maxFutureBase += pool.get(i).score;
            count++;
        }
        
        // If we ran out of fusions to fill the team, we must stop
        if (count < slotsRemaining) return true;

        double theoreticalCeiling = currentBase + maxFutureBase + MAX_POSSIBLE_DELTA;
        
        // If the absolute best we can do is worse than what we already have, PRUNE.
        return theoreticalCeiling <= globalMaxScore;
    }

    private synchronized void updateGlobalBest(double score, Team team) {
        if (score > globalMaxScore) {
            globalMaxScore = score;
            globalBestTeam = team;
            // System.out.println("New Best Found: " + String.format("%.2f", score)); // Optional logging
        }
    }

    // ==================== SCORING & DELTA LOGIC ==================== //

    /**
     * Calculates the objective function: Score(P) = Sum(Base) + Delta
     */
    private double calculateTotalScore(List<Fusion> members, TeamBuildConfig config) {
        double baseSum = 0;
        for (Fusion f : members) baseSum += f.score;
        return baseSum + calculateDelta(members, config);
    }

    /**
     * Calculates "Delta" (Δ) - the adjustment based on profile differences (Synergy, Penalties).
     */
    private double calculateDelta(List<Fusion> members, TeamBuildConfig config) {
        double delta = 0.0;

        // --- Penalties (Hard constraints turned into soft penalties for scoring) ---
        
        // Species Clause (Don't use same pokemon twice)
        if (!config.allowSameSpeciesInTeam) {
            Set<String> species = new HashSet<>();
            for (Fusion f : members) {
                if (!species.add(f.headName.toLowerCase())) delta -= 5.0; // Heavy penalty
                if (!species.add(f.bodyName.toLowerCase())) delta -= 5.0;
            }
        }

        // Type Sharing (Don't stack too many of one type)
        if (!config.allowSharedTypes) {
            Map<String, Integer> typeCounts = new HashMap<>();
            for (Fusion f : members) {
                // Split dual types (e.g., "Fire/Flying")
                String[] types = f.typing.contains("/") ? f.typing.split("/") : new String[]{f.typing};
                for(String t : types) {
                    typeCounts.put(t, typeCounts.getOrDefault(t, 0) + 1);
                }
            }
            for (int count : typeCounts.values()) {
                if (count > 2) delta -= (count * 2.0);
            }
        }

        // Defensive Coverage (Penalize shared weaknesses)
        // Bitmask checking is extremely fast
        int[] sharedWeaknesses = new int[18];
        for (Fusion f : members) {
            for (int i = 0; i < 18; i++) {
                if (((f.weaknessMask >> i) & 1) == 1) sharedWeaknesses[i]++;
            }
        }
        for (int count : sharedWeaknesses) {
            if (count > 2) delta -= (count * 0.5);
        }

        // --- Bonuses (Synergy) ---

        // Role Diversity (Rewarding a balanced team)
        Set<String> roles = members.stream().map(f -> f.role).collect(Collectors.toSet());
        if (roles.size() >= 5) delta += 1.5;
        else if (roles.size() >= 4) delta += 0.8;

        return delta;
    }

    private boolean isValidAddition(List<Fusion> current, Fusion candidate, TeamBuildConfig config) {
        // Fast fail for strict mode constraints to save recursion depth
        if (!config.allowSameSpeciesInTeam) {
            for(Fusion f : current) {
                if (f.headName.equalsIgnoreCase(candidate.headName) || 
                    f.headName.equalsIgnoreCase(candidate.bodyName) ||
                    f.bodyName.equalsIgnoreCase(candidate.headName) ||
                    f.bodyName.equalsIgnoreCase(candidate.bodyName)) {
                    return false;
                }
            }
        }
        return true;
    }
}