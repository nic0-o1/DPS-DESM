package desm.dps.core;

import desm.dps.EnergyRequest;
import desm.dps.election.ElectionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedList;
import java.util.Queue;
import java.util.Objects;

/**
 * Manages the processing state (busy/idle) of a power plant and a queue of pending energy requests.
 * This class acts as a state machine, ensuring that the plant only handles one primary request at a time
 * while queueing any subsequent requests that arrive during production.
 */
public class EnergyRequestProcessor {
    private static final Logger logger = LoggerFactory.getLogger(EnergyRequestProcessor.class);
    private static final int PRODUCTION_MULTIPLIER = 7;

    private final int selfPlantId;
    private final ElectionManager electionManager;

    // A dedicated lock for the request queue.
    private final Object queueLock = new Object();
    private final Queue<EnergyRequest> pendingRequests = new LinkedList<>();

    // A dedicated lock protecting the busy state of the plant.
    private final Object stateLock = new Object();
    private boolean isBusy = false;
    private String currentRequestId = null;


    public EnergyRequestProcessor(int selfPlantId, ElectionManager electionManager) {
        this.selfPlantId = selfPlantId;
        this.electionManager = electionManager;
    }

    /**
     * Immediately starts an election for a new energy request.
     *
     * @param energyRequest The new request to start an election for.
     */
    public void startElectionForNewRequest(EnergyRequest energyRequest) {
        electionManager.startActiveElection(energyRequest);
    }

    /**
     * Adds an energy request to the pending queue.
     *
     * @param energyRequest The request to be queued.
     */
    public void queueRequest(EnergyRequest energyRequest) {
        synchronized (queueLock) {
            // Prevent adding the same request ID to the queue multiple times.
            boolean alreadyExists = pendingRequests.stream()
                    .anyMatch(r -> r.requestID().equals(energyRequest.requestID()));

            if (!alreadyExists) {
                pendingRequests.add(energyRequest);
                logger.info("Plant {} is busy. Queued request {}. Queue size is now: {}",
                        selfPlantId, energyRequest.requestID(), pendingRequests.size());
            } else {
                logger.warn("Request {} is already in the queue. Ignoring duplicate add attempt.", energyRequest.requestID());
            }
        }
    }

    /**
     * Removes a request from the pending queue by its ID. This is typically used when another plant
     * has won the election for a request that was in this plant's queue.
     *
     * @param requestId The ID of the request to remove.
     */
    public void removeRequestById(String requestId) {
        if (requestId == null || requestId.isEmpty()) return;
        synchronized (queueLock) {
            boolean removed = pendingRequests.removeIf(req -> requestId.equals(req.requestID()));
            if (removed) {
                logger.info("Removed request {} from queue as it is being handled elsewhere.", requestId);
            }
        }
    }

    /**
     * Fulfills an energy request that this plant has won. This method marks the plant as busy
     * and starts the energy production simulation.
     *
     * @param request The energy request to fulfill.
     * @param price The winning bid price for this request.
     */
    public void fulfillRequest(EnergyRequest request, double price) {
        synchronized (stateLock) {
            if (isBusy) {
                logger.warn("Attempted to fulfill request {} but plant {} is already busy with {}.",
                        request.requestID(), selfPlantId, currentRequestId);
                removeRequestById(request.requestID());
                return;
            }
            isBusy = true;
            currentRequestId = request.requestID();
        }

        // Ensure the request is removed from our own queue to prevent processing it again later.
        removeRequestById(request.requestID());

        logger.info("Plant {} won bid for ER {} with price ${}. Fulfilling {} kWh.",
                selfPlantId, request.requestID(), String.format("%.2f", price), request.amountKWh());
        startEnergyProduction(request);
    }

    /**
     * Simulates energy production in a separate, non-blocking thread.
     *
     * @param request The request being fulfilled.
     */
    private void startEnergyProduction(EnergyRequest request) {
        long processingTimeMillis = (long) request.amountKWh() * PRODUCTION_MULTIPLIER;
        logger.info("Energy production for ER {} will take approximately {} ms.", request.requestID(), processingTimeMillis);

        Thread productionThread = new Thread(() -> {
            try {
                Thread.sleep(processingTimeMillis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.warn("Energy production for ER {} was interrupted at Plant {}.", request.requestID(), selfPlantId);
            } finally {
                logger.info("Plant {} finished fulfilling ER {}.", selfPlantId, request.requestID());
                onProductionFinished(request.requestID());
            }
        }, "EnergyProduction-" + request.requestID());
        productionThread.setDaemon(true);
        productionThread.start();
    }

    /**
     * A callback executed after production for a request is finished.
     * It resets the plant's busy state and triggers an election for the next pending request, if any.
     */
    private void onProductionFinished(String finishedRequestId) {
        synchronized (stateLock) {
            // Sanity check to ensure we are resetting the state for the correct request.
            if (!Objects.equals(this.currentRequestId, finishedRequestId)) {
                logger.error("State inconsistency: Finished production for ER {} but current request was {}.",
                        finishedRequestId, this.currentRequestId);
            }
            isBusy = false;
            currentRequestId = null;
        }

        EnergyRequest nextRequestToProcess;
        synchronized (queueLock) {
            nextRequestToProcess = pendingRequests.poll(); // Retrieve and remove the next request.
        }

        if (nextRequestToProcess != null) {
            logger.info("Plant {} is now idle. Processing next request from queue: {}", selfPlantId, nextRequestToProcess.requestID());
            electionManager.startElectionForDequeuedRequest(nextRequestToProcess);
        } else {
            logger.info("No pending requests in queue. Plant {} is now idle.", selfPlantId);
        }
    }

    /**
     * Checks if the plant is currently fulfilling a request. This method is thread-safe.
     *
     * @return {@code true} if the plant is busy, {@code false} otherwise.
     */
    public boolean isBusy() {
        synchronized (stateLock) {
            return isBusy;
        }
    }
}