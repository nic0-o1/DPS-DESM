package desm.dps.election.internal;

import desm.dps.PowerPlant;
import desm.dps.PowerPlantInfo;
import desm.dps.election.model.ElectionState;
import desm.dps.grpc.Bid;
import desm.dps.grpc.ElectCoordinatorToken;
import desm.dps.grpc.EnergyWinnerAnnouncement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implements the core logic of the ring-based election algorithm.
 * This class is responsible for initiating an election, forwarding tokens,
 * and completing the election once a full circle has been made.
 */
public final class RingAlgorithmProcessor {

    private static final Logger logger = LoggerFactory.getLogger(RingAlgorithmProcessor.class);

    private final PowerPlant powerPlant;
    private final ElectionCommunicator communicator;
    private final ElectionCompletionHandler localProcessor;

    public RingAlgorithmProcessor(PowerPlant powerPlant, ElectionCommunicator communicator,
                                  ElectionCompletionHandler localProcessor) {
        this.powerPlant = powerPlant;
        this.communicator = communicator;
        this.localProcessor = localProcessor;
    }

    /**
     * Initiates an election for a given energy request.
     * It sets this plant's bid as the initial best bid and creates the election token.
     * The process may abort if a better bid is received from another concurrent process
     * before the token can be sent.
     *
     * @param state The current state of the election.
     */
    public void initiate(ElectionState state) {
        int selfId = powerPlant.getSelfInfo().plantId();
        Bid myBid = createBid(selfId, state.getMyBid());

        state.updateBestBid(myBid);

        if (state.getBestBid().getPlantId() != selfId) {
            logger.warn("Aborting election initiation for ER {}. A better bid from Plant {} was received concurrently.",
                    state.getRequest().requestID(), state.getBestBid().getPlantId());
            return;
        }

        ElectCoordinatorToken token = createToken(selfId, state, state.getBestBid());
        PowerPlantInfo nextPlant = powerPlant.getNextPlantInRing(selfId);

        // If there's no next plant, or we are the only one, complete the election immediately.
        if (nextPlant == null || nextPlant.plantId() == selfId) {
            logger.info("Initiating and completing election for ER {} locally (single node case).", state.getRequest().requestID());
            complete(state, token);
            return;
        }

        logger.info("Initiating election for ER {}. Forwarding token to next plant {}.", token.getEnergyRequestId(), nextPlant.plantId());
        communicator.forwardToken(nextPlant, token);
    }

    /**
     * Forwards an election token received from another plant.
     * The best bid in the local state is updated before passing the token along.
     *
     * @param state         The current state of the election.
     * @param incomingToken The token received from the previous plant in the ring.
     */
    public void forward(ElectionState state, ElectCoordinatorToken incomingToken) {
        state.updateBestBid(incomingToken.getBestBid());

        int selfId = powerPlant.getSelfInfo().plantId();
        PowerPlantInfo nextPlantInRing = powerPlant.getNextPlantInRing(selfId);

        if (nextPlantInRing == null) {
            logger.warn("Cannot forward token for ER '{}'; no next plant found in ring.", incomingToken.getEnergyRequestId());
            return;
        }

        logger.debug("Forwarding token for ER '{}' from initiator {} to plant {}.",
                incomingToken.getEnergyRequestId(), incomingToken.getInitiatorId(), nextPlantInRing.plantId());
        communicator.forwardToken(nextPlantInRing, incomingToken);
    }

    /**
     * Completes an election after the token has returned to the initiator.
     * It finalizes the best bid, creates a winner announcement, and processes it locally.
     *
     * @param state The current state of the election.
     * @param token The election token that has completed its circulation.
     * @return {@code true} if the election was successfully completed, {@code false} if a winner
     *         was already announced by a concurrent process.
     */
    public boolean complete(ElectionState state, ElectCoordinatorToken token) {
        state.updateBestBid(token.getBestBid());

        if (!state.trySetWinnerAnnounced()) {
            logger.warn("Completion for ER {} aborted; a winner was already announced.", token.getEnergyRequestId());
            return false;
        }

        EnergyWinnerAnnouncement announcement = EnergyWinnerAnnouncement.newBuilder()
                .setWinningPlantId(state.getBestBid().getPlantId())
                .setWinningPrice(state.getBestBid().getPrice())
                .setEnergyRequestId(state.getRequest().requestID())
                .setInitiatorId(token.getInitiatorId())
                .build();

        logger.info("Completing election for ER {}. Winner is Plant {} with price {}. Processing result.",
                announcement.getEnergyRequestId(), announcement.getWinningPlantId(), announcement.getWinningPrice());
        localProcessor.process(announcement);
        return true;
    }

    private Bid createBid(int plantId, double price) {
        return Bid.newBuilder().setPlantId(plantId).setPrice(price).build();
    }

    private ElectCoordinatorToken createToken(int selfId, ElectionState state, Bid bestBid) {
        return ElectCoordinatorToken.newBuilder()
                .setInitiatorId(selfId)
                .setEnergyRequestId(state.getRequest().requestID())
                .setBestBid(bestBid)
                .setEnergyAmountKwh(state.getRequest().amountKWh())
                .build();
    }
}