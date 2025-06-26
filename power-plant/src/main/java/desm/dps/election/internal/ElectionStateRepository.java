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
 * Manages the lifecycle of {@link ElectionState} objects for all ongoing elections.
 * This repository provides a thread-safe way to store, retrieve, and eventually
 * clean up the state associated with each energy request.
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
     * Retrieves the election state for a given request ID, or creates a new one if it doesn't exist.
     * This method is intended for plants that receive the initial energy request via broadcast (e.g., MQTT).
     *
     * @param requestId The unique identifier for the energy request.
     * @param request   The full {@link EnergyRequest} object.
     * @param price     The initial bid price for the current power plant.
     * @return The existing or newly created {@link ElectionState}.
     */
    public ElectionState getOrCreate(String requestId, EnergyRequest request, double price) {
        synchronized (electionsLock) {
            return elections.computeIfAbsent(requestId, id -> {
                logger.info("Creating new election state for ER '{}' with initial bid {}", id, price);
                return new ElectionState(request, price);
            });
        }
    }

    /**
     * Retrieves the election state for a given request ID, or creates a new one if it doesn't exist.
     * This method is intended for plants that join an election late upon receiving a token,
     * reconstructing the necessary context from the token's data.
     *
     * @param requestId    The unique identifier for the energy request.
     * @param energyAmount The energy amount required, taken from the election token.
     * @param price        The bid price for the current power plant.
     * @return The existing or newly created {@link ElectionState}.
     */
    public ElectionState getOrCreateFromToken(String requestId, int energyAmount, double price) {
        synchronized (electionsLock) {
            return elections.computeIfAbsent(requestId, id -> {
                logger.info("Creating new election state for ER '{}' from token with bid {}", id, price);
                EnergyRequest requestFromToken = new EnergyRequest(id, energyAmount, System.currentTimeMillis());
                return new ElectionState(requestFromToken, price);
            });
        }
    }

    /**
     * Retrieves an existing election state.
     *
     * @param requestId The unique identifier for the election.
     * @return The {@link ElectionState} if found, otherwise {@code null}.
     */
    public ElectionState get(String requestId) {
        synchronized (electionsLock) {
            return elections.get(requestId);
        }
    }

    /**
     * Schedules the removal of an election's state from the repository after a fixed delay.
     * This prevents memory leaks by cleaning up state for completed or timed-out elections.
     *
     * @param requestId The unique identifier of the election to clean up.
     */
    public void scheduleCleanup(String requestId) {
        cleanupExecutor.schedule(() -> {
            boolean removed;
            synchronized (electionsLock) {
                removed = elections.remove(requestId) != null;
            }
            if (removed) {
                logger.debug("Cleaned up election state for request {}", requestId);
            }
        }, CLEANUP_DELAY_SECONDS, TimeUnit.SECONDS);
    }
}