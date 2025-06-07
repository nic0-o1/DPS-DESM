package desm.dps.election.internal;

import desm.dps.PowerPlant;
import desm.dps.PowerPlantInfo;
import desm.dps.election.model.ElectionState;
import desm.dps.grpc.Bid;
import desm.dps.grpc.ElectCoordinatorToken;

/**
 * Contains the core logic of the ring election algorithm.
 * This class is stateless and operates on a given ElectionState to determine the next action.
 */
public class RingAlgorithmProcessor {
    private final PowerPlant powerPlant;
    private final ElectionCommunicator communicator;

    public RingAlgorithmProcessor(PowerPlant powerPlant, ElectionCommunicator communicator) {
        this.powerPlant = powerPlant;
        this.communicator = communicator;
    }

    public void initiate(ElectionState state) {
        String selfId = powerPlant.getSelfInfo().getPlantId();
        Bid myBid = createBid(selfId, state.getMyBid());
        state.updateBestBid(myBid);

        ElectCoordinatorToken token = createToken(selfId, state, myBid);
        PowerPlantInfo nextPlant = powerPlant.getNextPlantInRing(selfId);

        if (nextPlant == null || nextPlant.getPlantId().equals(selfId)) {
            complete(state, token);
            return;
        }
        communicator.forwardToken(nextPlant, token);
    }

    public void forward(ElectionState state, ElectCoordinatorToken incomingToken) {
        state.updateBestBid(incomingToken.getBestBid());
        Bid bestBid = state.getBestBid();
        if (state.isValidBid()) {
            Bid myBid = createBid(powerPlant.getSelfInfo().getPlantId(), state.getMyBid());
            if (state.updateBestBid(myBid)) {
                bestBid = state.getBestBid();
            }
        }
        ElectCoordinatorToken forwardToken = incomingToken.toBuilder().setBestBid(bestBid).build();
        communicator.forwardToken(powerPlant.getNextPlantInRing(powerPlant.getSelfInfo().getPlantId()), forwardToken);
    }

    public boolean complete(ElectionState state, ElectCoordinatorToken token) {
        state.updateBestBid(token.getBestBid());
        Bid winnerBid = state.getBestBid();
        if (state.trySetWinnerAnnounced()) {
            if (winnerBid.getPlantId().equals(powerPlant.getSelfInfo().getPlantId())) {
                powerPlant.fulfillEnergyRequest(state.getRequest(), winnerBid.getPrice());
            }
            communicator.broadcastWinner(state.getRequest().getRequestID(), winnerBid);
            return true;
        }
        return false;
    }

    private Bid createBid(String plantId, double price) { return Bid.newBuilder().setPlantId(plantId).setPrice(price).build(); }
    private ElectCoordinatorToken createToken(String selfId, ElectionState state, Bid bestBid) {
        return ElectCoordinatorToken.newBuilder()
                .setInitiatorId(selfId)
                .setEnergyRequestId(state.getRequest().getRequestID())
                .setBestBid(bestBid)
                .setEnergyAmountKwh(state.getRequest().getAmountKWh())
                .build();
    }
}
