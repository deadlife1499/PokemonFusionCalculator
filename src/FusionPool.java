import java.util.*;

public class FusionPool {
    private final List<Fusion> fusions = new ArrayList<>();
    // Removed MAX_SIZE limit as requested
    
    public synchronized void add(Fusion f) {
        fusions.add(f);
    }
    
    public synchronized void trim() {
        // No-op: We keep all fusions now
    }
    
    public synchronized void sort() {
        fusions.sort((a, b) -> Double.compare(b.score, a.score));
    }
    
    public synchronized List<Fusion> getList() {
        return new ArrayList<>(fusions);
    }
    
    public synchronized int size() {
        return fusions.size();
    }
    
    public synchronized void clear() {
        fusions.clear();
    }
}