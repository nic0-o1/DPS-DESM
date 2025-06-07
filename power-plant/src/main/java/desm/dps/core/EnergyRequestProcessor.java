package desm.dps.core;

import desm.dps.EnergyRequest;
import desm.dps.election.ElectionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedList;
import java.util.Queue;

/**
 * Manages the state and processing logic for energy requests. This class encapsulates
 * the "busy" state of the plant, a queue for pending requests, and the logic
 * for simulating energy production. It is designed to be thread-safe.
 */
public class EnergyRequestProcessor {
    private static final Logger logger = LoggerFactory.getLogger(EnergyRequestProcessor.class);

    private final String selfPlantId;
    private final ElectionManager electionManager;

    // --- State variables protected by synchronization ---
    private volatile boolean isBusy = false;
    private volatile String currentRequestId = null;
    private final Object processingLock = new Object();

    private final Queue<EnergyRequest> pendingRequests = new LinkedList<>();
    private final Object pendingRequestsLock = new Object();

    public EnergyRequestProcessor(String selfPlantId, ElectionManager electionManager) {
        this.selfPlantId = selfPlantId;
        this.electionManager = electionManager;
    }

    /**
     * Processes an incoming request. If the plant is not busy, it initiates an election.
     * If the plant is busy, the request is queued for later processing.
     *
     * @param energyRequest The request to process.
     */
    public void processIncomingRequest(EnergyRequest energyRequest) {
        boolean processedImmediately;
        synchronized (processingLock) {
            // If not busy, we can process this immediately.
            if (!isBusy) {
                electionManager.processNewEnergyRequest(energyRequest);
                processedImmediately = true;
            } else {
                processedImmediately = false;
            }
        }

        if (!processedImmediately) {
            // If we were busy, queue the request.
            synchronized (pendingRequestsLock) {
                pendingRequests.add(energyRequest);
                logger.info("Plant {} is busy. Queued request {}. Queue size: {}",
                        selfPlantId, energyRequest.getRequestID(), pendingRequests.size());
            }
        }
    }
    /**
     * [NEW METHOD]
     * Removes a request from the pending queue by its ID. This is called when another
     * plant has won the election for this request, making the queued item obsolete.
     *
     * @param requestId The ID of the request to remove.
     */
    public void removeRequestById(String requestId) {
        if (requestId == null || requestId.isEmpty()) {
            return;
        }
        synchronized (pendingRequestsLock) {
            boolean removed = pendingRequests.removeIf(req -> requestId.equals(req.getRequestID()));
            if (removed) {
                logger.info("Removed completed request {} from queue as it is being handled by another plant.", requestId);
            }
        }
    }

    /**
     * Fulfills a request after winning the bid. Sets the plant to a busy state
     * and starts a background thread to simulate energy production.
     *
     * @param request The request to fulfill.
     * @param price The winning bid price.
     */
    public void fulfillRequest(EnergyRequest request, double price) {
        synchronized (processingLock) {
            if (isBusy) {
                logger.warn("Attempted to fulfill request {} but plant {} is already busy with {}",
                        request.getRequestID(), selfPlantId, currentRequestId);
                return;
            }
            isBusy = true;
            currentRequestId = request.getRequestID();
        }

        logger.info("Plant {} won bid for request {} with price ${}. Fulfilling {} kWh.",
                selfPlantId, request.getRequestID(), price, request.getAmountKWh());
        startEnergyProduction(request);
    }

    private void startEnergyProduction(EnergyRequest request) {
        long processingTimeMillis = Math.max(1, (long) request.getAmountKWh()) * 8; // Simulation logic
        logger.info("Energy production for request {} will take ~{} ms.", request.getRequestID(), processingTimeMillis);

        Thread productionThread = new Thread(() -> {
            try {
                Thread.sleep(processingTimeMillis);
            } catch (InterruptedException e) {
                logger.warn("Energy production for request {} was interrupted in plant {}", request.getRequestID(), selfPlantId);
                Thread.currentThread().interrupt();
            } finally {
                logger.info("Plant {} finished fulfilling request {}.", selfPlantId, request.getRequestID());
                onProductionFinished();
            }
        }, "EnergyProduction-" + request.getRequestID());

        productionThread.setDaemon(true);
        productionThread.start();
    }

    /**
     * Called when energy production is complete. It clears the busy state and
     * processes the next request from the queue, if one exists.
     */
    private void onProductionFinished() {
        EnergyRequest nextRequest;
        synchronized (pendingRequestsLock) {
            nextRequest = pendingRequests.poll(); // Dequeue if present
        }

        // Clear the busy state to allow new requests to be processed.
        synchronized (processingLock) {
            isBusy = false;
            currentRequestId = null;
        }

        // If there was a pending request, process it now.
        if (nextRequest != null) {
            logger.info("Processing dequeued request {}.", nextRequest.getRequestID());
            processIncomingRequest(nextRequest);
        } else {
            logger.info("No pending requests. Plant {} is now idle.", selfPlantId);
        }
    }

    /**
     * Checks if the plant is currently busy.
     *
     * @return true if busy, false otherwise.
     */
    public boolean isBusy() {
        return isBusy;
    }

    /**
     * Gets the ID of the current request being processed.
     *
     * @return The request ID, or null if idle.
     */
    public String getCurrentRequestId() {
        return currentRequestId;
    }
}
