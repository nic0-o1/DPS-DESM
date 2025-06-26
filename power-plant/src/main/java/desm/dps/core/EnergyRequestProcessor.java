package desm.dps.core;

import desm.dps.EnergyRequest;
import desm.dps.election.ElectionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedList;
import java.util.Queue;

public class EnergyRequestProcessor {
    private static final Logger logger = LoggerFactory.getLogger(EnergyRequestProcessor.class);
    private static final int PRODUCTION_MULTIPLIER = 1;  // for test only

    private final int selfPlantId;
    private final ElectionManager electionManager;
    private volatile boolean isBusy = false;
    private volatile String currentRequestId = null;
    private final Object processingLock = new Object();
    private final Queue<EnergyRequest> pendingRequests = new LinkedList<>();
    private final Object pendingRequestsLock = new Object();

    public EnergyRequestProcessor(int selfPlantId, ElectionManager electionManager) {
        this.selfPlantId = selfPlantId;
        this.electionManager = electionManager;
    }

    /**
     * -- REVISED LOGIC --
     * This method is now only called by the PowerPlant facade when it has determined
     * the plant is NOT busy. It immediately starts an election for the request.
     */
    public void startElectionForNewRequest(EnergyRequest energyRequest) {
        // No isBusy check needed here, the decision was made by the caller.
        electionManager.startActiveElection(energyRequest);
    }

    /**
     * -- REVISED LOGIC --
     * This method is now only called by the PowerPlant facade when it has determined
     * the plant IS busy.
     */
    public void queueRequest(EnergyRequest energyRequest) {
        synchronized (pendingRequestsLock) {
            // Prevent adding the same request ID to the queue multiple times.
            if (pendingRequests.stream().noneMatch(r -> r.requestID().equals(energyRequest.requestID()))) {
                pendingRequests.add(energyRequest);
                logger.info("Plant {} is busy. Queued request {}. Queue size: {}",
                        selfPlantId, energyRequest.requestID(), pendingRequests.size());
            } else {
                logger.warn("Request {} is already in the queue. Ignoring duplicate queue attempt.", energyRequest.requestID());
            }
        }
    }

    public void removeRequestById(String requestId) {
        if (requestId == null || requestId.isEmpty()) return;
        synchronized (pendingRequestsLock) {
            boolean removed = pendingRequests.removeIf(req -> requestId.equals(req.requestID()));
            if (removed) {
                logger.info("Removed completed request {} from queue as it is being handled by another plant.", requestId);
            }
        }
    }

    public void fulfillRequest(EnergyRequest request, double price) {
        synchronized (processingLock) {
            if (isBusy) {
                logger.warn("Attempted to fulfill request {} but plant {} is already busy with {}",
                        request.requestID(), selfPlantId, currentRequestId);
                // Even if busy, we must remove it from our queue if we somehow won the bid.
                removeRequestById(request.requestID());
                return;
            }
            isBusy = true;
            currentRequestId = request.requestID();
        }

        // Before fulfilling, ensure it's not in our own queue. This prevents the double-processing bug.
        removeRequestById(request.requestID());

        logger.info("Plant {} won bid for request {} with price ${}. Fulfilling {} kWh.",
                selfPlantId, request.requestID(), String.format("%.2f", price), request.amountKWh());
        startEnergyProduction(request);
    }

    private void startEnergyProduction(EnergyRequest request) {
        long processingTimeMillis = request.amountKWh();
        logger.info("Energy production for request {} will take ~{} ms.", request.requestID(), processingTimeMillis);
        Thread productionThread = new Thread(() -> {
            try {
                Thread.sleep(processingTimeMillis * PRODUCTION_MULTIPLIER);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.warn("Energy production for request {} was interrupted in plant {}", request.requestID(), selfPlantId);
            } finally {
                logger.info("Plant {} finished fulfilling request {}.", selfPlantId, request.requestID());
                onProductionFinished();
            }
        }, "EnergyProduction-" + request.requestID());
        productionThread.setDaemon(true);
        productionThread.start();
    }

    private void onProductionFinished() {
        EnergyRequest nextRequestToProcess;
        synchronized (pendingRequestsLock) {
            nextRequestToProcess = pendingRequests.poll();
        }

        synchronized (processingLock) {
            isBusy = false;
            currentRequestId = null;
        }

        if (nextRequestToProcess != null) {
            logger.info("Processing dequeued request {}.", nextRequestToProcess.requestID());
            electionManager.startElectionForDequeuedRequest(nextRequestToProcess);
        } else {
            logger.info("No pending requests. Plant {} is now idle.", selfPlantId);
        }
    }

    public boolean isBusy() {
        return isBusy;
    }
}