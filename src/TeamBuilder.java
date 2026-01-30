import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class TeamBuilder {
    private final DataManager data;
    
    // Optimization limit
    private static final int SEARCH_POOL_LIMIT = 120;
    private static final double MAX_POSSIBLE_DELTA = 3.0; // Increased slightly for slider logic

    private volatile double globalMaxScore = -Double.MAX_VALUE;
    private Team globalBestTeam = null;

    public TeamBuilder(DataManager data) {
        this.data = data;
    }

    public List<Team> buildTeams(List<Fusion> fusions, Set<Fusion> pinnedFusions, TeamBuildConfig config, 
                               TaskController task, BiConsumer<Integer, Integer> progressCallback) {
        
        System.out.println("Starting Optimized Branch and Bound Search...");
        long startTime = System.currentTimeMillis();
        globalMaxScore = -Double.MAX_VALUE;
        globalBestTeam = null;

        List<Fusion> pool = new ArrayList<>(fusions);
        
        // Hard Filter: Self Fusion only if set to 100 (Hard Ban)
        if (config.selfFusionClauseVal == 100) {
            pool.removeIf(f -> f.headName.equalsIgnoreCase(f.bodyName));
        }
        
        pool.sort((a, b) -> Double.compare(b.score, a.score));

        List<Fusion> pinnedList = pinnedFusions != null ? new ArrayList<>(pinnedFusions) : new ArrayList<>();
        pool.removeAll(pinnedList);
        
        if (pool.size() > SEARCH_POOL_LIMIT) {
            pool = new ArrayList<>(pool.subList(0, SEARCH_POOL_LIMIT));
        }
        
        initializeLowerBound(pool, pinnedList, config);

        int n = pool.size();
        int k = 6 - pinnedList.size(); 

        if (k <= 0) {
            Team t = new Team();
            t.members.addAll(pinnedList.subList(0, 6));
            t.recalculateRealScore();
            return Collections.singletonList(t);
        }

        int cores = Runtime.getRuntime().availableProcessors();
        ForkJoinPool threadPool = new ForkJoinPool(cores);
        AtomicInteger completedBranches = new AtomicInteger(0);
        
        final List<Fusion> finalPool = Collections.unmodifiableList(pool);

        try {
            threadPool.submit(() -> 
                IntStream.range(0, finalPool.size()).parallel().forEach(i -> {
                    if (task.isCancelled()) return;

                    double currentBase = 0;
                    for(Fusion p : pinnedList) currentBase += p.score;
                    currentBase += finalPool.get(i).score;
                    
                    if (canPrune(currentBase, i, k - 1, finalPool)) {
                        completedBranches.incrementAndGet();
                        return; 
                    }

                    List<Fusion> currentMembers = new ArrayList<>(pinnedList);
                    currentMembers.add(finalPool.get(i));
                    
                    solveBranch(finalPool, currentMembers, currentBase, i, config, task);
                    
                    int done = completedBranches.incrementAndGet();
                    progressCallback.accept(done, n);
                })
            ).get();
            
        } catch (Exception e) {
            e.printStackTrace();
        }

        long duration = System.currentTimeMillis() - startTime;
        System.out.println("Search finished in " + duration + "ms. Best Score: " + globalMaxScore);

        if (globalBestTeam != null) {
            return Collections.singletonList(globalBestTeam);
        }
        return new ArrayList<>();
    }

    private void initializeLowerBound(List<Fusion> pool, List<Fusion> pinned, TeamBuildConfig config) {
        Team greedyTeam = new Team();
        greedyTeam.members.addAll(pinned);
        
        for (Fusion candidate : pool) {
            if (greedyTeam.members.size() >= 6) break;
            if (isValidAddition(greedyTeam.members, candidate, config)) {
                greedyTeam.members.add(candidate);
            }
        }
        
        if (greedyTeam.members.size() == 6) {
            double score = calculateTotalScore(greedyTeam.members, config);
            updateGlobalBest(score, greedyTeam);
        }
    }

    private void solveBranch(List<Fusion> pool, List<Fusion> currentTeam, double currentBaseScore, 
                             int lastIndex, TeamBuildConfig config, TaskController task) {
        
        if (task.isCancelled()) return;

        if (currentTeam.size() == 6) {
            double delta = calculateDelta(currentTeam, config);
            double finalScore = currentBaseScore + delta;
            
            Team t = new Team();
            t.members.addAll(currentTeam);
            t.realScore = finalScore;
            // Store the "delta" specifically for the UI to display the bonus/penalty
            t.balanceBonus = delta; 
            
            updateGlobalBest(finalScore, t);
            return;
        }

        int slotsRemaining = 6 - currentTeam.size();
        
        if (lastIndex + 1 >= pool.size()) return;
        if (canPrune(currentBaseScore, lastIndex, slotsRemaining, pool)) return;

        for (int i = lastIndex + 1; i < pool.size(); i++) {
            Fusion candidate = pool.get(i);

            if (!isValidAddition(currentTeam, candidate, config)) continue;

            currentTeam.add(candidate);
            solveBranch(pool, currentTeam, currentBaseScore + candidate.score, i, config, task);
            currentTeam.remove(currentTeam.size() - 1);
        }
    }

    private boolean canPrune(double currentBase, int lastIndex, int slotsRemaining, List<Fusion> pool) {
        double maxFutureBase = 0;
        int count = 0;
        
        for (int i = lastIndex + 1; i < pool.size() && count < slotsRemaining; i++) {
            maxFutureBase += pool.get(i).score;
            count++;
        }
        
        if (count < slotsRemaining) return true;

        double theoreticalCeiling = currentBase + maxFutureBase + MAX_POSSIBLE_DELTA;
        return theoreticalCeiling <= globalMaxScore;
    }

    private synchronized void updateGlobalBest(double score, Team team) {
        if (score > globalMaxScore) {
            globalMaxScore = score;
            globalBestTeam = new Team();
            globalBestTeam.members.addAll(team.members);
            globalBestTeam.realScore = score;
            globalBestTeam.balanceBonus = team.balanceBonus;
        }
    }

    private double calculateTotalScore(List<Fusion> members, TeamBuildConfig config) {
        double baseSum = members.stream().mapToDouble(f -> f.score).sum();
        return baseSum + calculateDelta(members, config);
    }

    private double calculateDelta(List<Fusion> members, TeamBuildConfig config) {
        double delta = 0.0;

        // 1. Species Clause (Slider Logic)
        if (config.speciesClauseVal > 0) {
            Set<String> species = new HashSet<>();
            int dupes = 0;
            for (Fusion f : members) {
                if (!species.add(f.headName.toLowerCase())) dupes++;
                if (!species.add(f.bodyName.toLowerCase())) dupes++;
            }
            if (dupes > 0) {
                // If Slider is < 100, it's a soft penalty. If 100, we shouldn't be here (pruned earlier).
                double weight = config.speciesClauseVal / 20.0; // 0.0 to 5.0
                delta -= (dupes * weight);
            }
        }

        // 2. Type Sharing (Slider Logic)
        if (config.typeClauseVal > 0) {
            Map<String, Integer> typeCounts = new HashMap<>();
            for (Fusion f : members) {
                String[] types = f.typing.contains("/") ? f.typing.split("/") : new String[]{f.typing};
                for(String t : types) {
                    typeCounts.put(t, typeCounts.getOrDefault(t, 0) + 1);
                }
            }
            int violations = 0;
            for (int count : typeCounts.values()) {
                if (count > 2) violations += (count - 2);
            }
            if (violations > 0) {
                double weight = config.typeClauseVal / 20.0;
                delta -= (violations * weight);
            }
        }
        
        // 3. Self Fusion (Soft Penalty if not hard banned)
        if (config.selfFusionClauseVal > 0 && config.selfFusionClauseVal < 100) {
            for(Fusion f : members) {
                if (f.headName.equalsIgnoreCase(f.bodyName)) {
                    double weight = config.selfFusionClauseVal / 20.0;
                    delta -= weight;
                }
            }
        }

        // 4. Role Diversity Bonus (Fixed)
        Set<String> roles = members.stream().map(f -> f.role).collect(Collectors.toSet());
        if (roles.size() >= 5) delta += 1.5;
        else if (roles.size() >= 4) delta += 0.8;

        return delta;
    }

    private boolean isValidAddition(List<Fusion> current, Fusion candidate, TeamBuildConfig config) {
        // Hard Pruning ONLY if slider is 100 (Hard Requirement)
        
        if (config.speciesClauseVal == 100) {
            for(Fusion f : current) {
                if (f.headName.equalsIgnoreCase(candidate.headName) || 
                    f.headName.equalsIgnoreCase(candidate.bodyName) ||
                    f.bodyName.equalsIgnoreCase(candidate.headName) ||
                    f.bodyName.equalsIgnoreCase(candidate.bodyName)) {
                    return false;
                }
            }
        }
        
        if (config.typeClauseVal == 100) {
            // Strict: Do not add if it causes any type count > 2 (simplified check)
            // Or usually "Monotype" check? The user said "not working at all to hard requirements".
            // Let's assume hard requirement = "No Shared Types" (strict diversity) or "Max 2" strict.
            // Let's enforce "Max 2" strictly here.
            Map<String, Integer> counts = new HashMap<>();
            // Tally existing
            for(Fusion f : current) {
                for(String t : f.typing.split("/")) counts.put(t, counts.getOrDefault(t, 0) + 1);
            }
            // Tally candidate
            for(String t : candidate.typing.split("/")) {
                if (counts.getOrDefault(t, 0) >= 2) return false;
            }
        }
        
        return true;
    }
}