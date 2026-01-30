import java.util.concurrent.atomic.AtomicBoolean;

public class TaskController {
    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private final AtomicBoolean running = new AtomicBoolean(true);

    public void cancel() {
        cancelled.set(true);
        running.set(false);
    }

    public boolean isCancelled() {
        return cancelled.get();
    }
    
    public void finish() {
        running.set(false);
    }
    
    public boolean isRunning() {
        return running.get();
    }
}