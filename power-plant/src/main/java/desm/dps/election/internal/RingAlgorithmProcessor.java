package desm.dps.election.internal;

import desm.dps.PowerPlant;
import desm.dps.PowerPlantInfo;
import desm.dps.election.model.ElectionState;
import desm.dps.grpc.Bid;
import desm.dps.grpc.ElectCoordinatorToken;

/**
 * Encapsulates the core logic of the ring-based election algorithm.
 *
 */
public final class RingAlgorithmProcessor {

    private final PowerPlant powerPlant;

    private final ElectionCommunicator communicator;

    /**
     * Constructs a processor for the ring election algorithm.
     *
     * @param powerPlant   The main PowerPlant instance, providing context and state.
     * @param communicator The communication handler for sending messages to other plants.
     */
    public RingAlgorithmProcessor(PowerPlant powerPlant, ElectionCommunicator communicator) {
        this.powerPlant = powerPlant;
        this.communicator = communicator;
    }

    /**
     * Starts a new election process.
     * This method is called by the plant that first receives or creates an energy request.
     * It creates the initial election token, includes its own bid, and forwards it
     * to the next plant in the ring.
     *
     * @param state The state object for the election being initiated.
     */
    public void initiate(ElectionState state) {
        int selfId = powerPlant.getSelfInfo().plantId();

        // Create this plant's own bid for the energy request.
        Bid myBid = createBid(selfId, state.getMyBid());

        // The initiator immediately considers its own bid as the best one seen so far.
        state.updateBestBid(myBid);

        // Create the election token that will circulate the ring.
        ElectCoordinatorToken token = createToken(selfId, state, myBid);
        PowerPlantInfo nextPlant = powerPlant.getNextPlantInRing(selfId);

        // Handle the edge case of a single-node ring. If this plant is the only one,
        // the election completes immediately.
        if (nextPlant == null || nextPlant.plantId() == selfId) {
            complete(state, token);
            return;
        }

        // Forward the token to the next participant to continue the election.
        communicator.forwardToken(nextPlant, token);
    }

    /**
     * Processes an incoming election token from another plant.
     * This plant compares the best bid in the token with its own bid, updates the token
     * if its bid is better, and then forwards the token to the next plant.
     *
     * @param state         The state object for the ongoing election.
     * @param incomingToken The election token received from the previous plant in the ring.
     */
    public void forward(ElectionState state, ElectCoordinatorToken incomingToken) {
        // First, update the local state with the best bid seen so far from the token.
        state.updateBestBid(incomingToken.getBestBid());
        Bid bestBid = state.getBestBid();

        // If this plant has a valid bid, it should participate.
        if (state.isValidBid()) {
            Bid myBid = createBid(powerPlant.getSelfInfo().plantId(), state.getMyBid());

            // If this plant's bid is better than the current best, update the best bid.
            if (state.updateBestBid(myBid)) {
                bestBid = state.getBestBid(); // The best bid is now our bid.
            }
        }

        ElectCoordinatorToken forwardToken = incomingToken.toBuilder().setBestBid(bestBid).build();
        PowerPlantInfo nextPlantInRing = powerPlant.getNextPlantInRing(powerPlant.getSelfInfo().plantId());

        communicator.forwardToken(nextPlantInRing, forwardToken);
    }

    /**
     * Completes the election process.
     * This method is called when the election token has returned to the initiator.
     * It determines the final winner, and if the winner hasn't already been announced for this election,
     * it broadcasts the result to all other plants. If this plant is the winner, it also
     * fulfills the energy request.
     *
     * @param state The state object for the election being completed.
     * @param token The final election token after circulating the entire ring.
     * @return {@code true} if this call successfully announced the winner; {@code false} if the
     *         winner was already announced (e.g., due to a concurrent completion message).
     */
    public boolean complete(ElectionState state, ElectCoordinatorToken token) {
        // Final update to ensure the state has the definitive winning bid from the token.
        state.updateBestBid(token.getBestBid());
        Bid winnerBid = state.getBestBid();

        // Atomically check and set the "winner announced" flag. This prevents duplicate announcements
        if (state.trySetWinnerAnnounced()) {
            // Check if this plant is the winner.
            if (winnerBid.getPlantId() == powerPlant.getSelfInfo().plantId()) {
                // If so, trigger the business logic to fulfill the request.
                powerPlant.fulfillEnergyRequest(state.getRequest(), winnerBid.getPrice());
            }
            // Broadcast the winner's information to all other plants so they know the election is over.
            communicator.broadcastWinner(state.getRequest().requestID(), winnerBid);
            return true;
        }

        // The winner was already announced by another thread or a concurrent message. Do nothing.
        return false;
    }

    /**
     * Private helper to create a gRPC Bid message.
     */
    private Bid createBid(int plantId, double price) {
        return Bid.newBuilder().setPlantId(plantId).setPrice(price).build();
    }

    /**
     * Private helper to create the initial gRPC election token.
     */
    private ElectCoordinatorToken createToken(int selfId, ElectionState state, Bid bestBid) {
        return ElectCoordinatorToken.newBuilder()
                .setInitiatorId(selfId)
                .setEnergyRequestId(state.getRequest().requestID())
                .setBestBid(bestBid)
                .setEnergyAmountKwh(state.getRequest().amountKWh())
                .build();
    }
}