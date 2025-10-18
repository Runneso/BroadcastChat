package chat.common.dedupstore;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class DeduplicationStore {
    private final ConcurrentHashMap<String, Long> messageStore = new ConcurrentHashMap<>();
    private final long ttl;
    private final ScheduledExecutorService cleanupScheduler = Executors.newSingleThreadScheduledExecutor();

    public DeduplicationStore(long ttl, TimeUnit unit) {
        this.ttl = unit.toMillis(ttl);
        this.cleanupScheduler.scheduleAtFixedRate(this::cleanup, 1, 1, TimeUnit.MINUTES);
    }

    public boolean isDuplicate(String messageId) {
        return messageStore.putIfAbsent(messageId, System.currentTimeMillis()) != null;
    }

    private void cleanup() {
        long now = System.currentTimeMillis();
        messageStore.entrySet().removeIf(entry -> (now - entry.getValue()) > ttl);
    }

    public void shutdown() {
        cleanupScheduler.shutdown();
    }
}

