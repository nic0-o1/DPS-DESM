package desm.dps.election.internal;

import desm.dps.EnergyRequest;
import desm.dps.election.model.ElectionState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Manages the storage and lifecycle of ElectionState objects.
 * This class is thread-safe and encapsulates all access to the central map of ongoing elections.
 */
public class ElectionStateRepository {
    private static final Logger logger = LoggerFactory.getLogger(ElectionStateRepository.class);
    private static final int CLEANUP_DELAY_SECONDS = 30;

    private final Map<String, ElectionState> elections = new HashMap<>();
    private final Object electionsLock = new Object();
    private final ScheduledExecutorService cleanupExecutor;

    public ElectionStateRepository(ScheduledExecutorService cleanupExecutor) {
        this.cleanupExecutor = cleanupExecutor;
    }

    /**
     * Safely gets or creates the election state for a given request ID.
     */
    public ElectionState getOrCreate(String requestId, EnergyRequest request, double price) {
        synchronized (electionsLock) {
            return elections.computeIfAbsent(requestId, _ -> {
                EnergyRequest requestToStore = (request != null) ? request : new EnergyRequest(requestId, 0, System.currentTimeMillis());
                return new ElectionState(requestToStore, price);
            });
        }
    }

    /**
     * Safely retrieves an existing election state. Returns null if not found.
     * This mirrors the logic of the original working implementation.
     */
    public ElectionState get(String requestId) {
        synchronized (electionsLock) {
            return elections.get(requestId);
        }
    }

    /**
     * Schedules the removal of a completed election's state from the map after a delay.
     */
    public void scheduleCleanup(String requestId) {
        cleanupExecutor.schedule(() -> {
            synchronized (electionsLock) {
                elections.remove(requestId);
            }
            logger.debug("Cleaned up election state for request {}", requestId);
        }, CLEANUP_DELAY_SECONDS, TimeUnit.SECONDS);
    }
}
