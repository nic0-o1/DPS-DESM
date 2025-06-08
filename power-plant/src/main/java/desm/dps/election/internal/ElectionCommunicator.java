package desm.dps.election.internal;

import desm.dps.PowerPlant;
import desm.dps.PowerPlantInfo;
import desm.dps.grpc.Bid;
import desm.dps.grpc.ElectCoordinatorToken;
import desm.dps.grpc.EnergyWinnerAnnouncement;
import desm.dps.grpc.PlantGrpcClient;

/**
 * Handles all network communication for the election process.
 */
public class ElectionCommunicator {
    private final PowerPlant powerPlant;
    private final PlantGrpcClient grpcClient;

    public ElectionCommunicator(PowerPlant powerPlant, PlantGrpcClient grpcClient) {
        this.powerPlant = powerPlant;
        this.grpcClient = grpcClient;
    }

    /**
     * Forwards an election token to the next plant in the ring.
     */
    public void forwardToken(PowerPlantInfo nextPlant, ElectCoordinatorToken token) {
        grpcClient.forwardElectionToken(nextPlant, token);
    }

    /**
     * Broadcasts the winner of an election to all other known plants.
     * This logic is identical to the original implementation.
     */
    public void broadcastWinner(String requestId, Bid winnerBid) {
        EnergyWinnerAnnouncement announcement = EnergyWinnerAnnouncement.newBuilder()
                .setWinningPlantId(winnerBid.getPlantId())
                .setWinningPrice(winnerBid.getPrice())
                .setEnergyRequestId(requestId)
                .build();

        for (PowerPlantInfo plant : powerPlant.getOtherPlants()) {
            // Your original logic did not send the announcement to itself, which is correct.
            // This loop over `getOtherPlants()` naturally achieves that.
            if (!plant.plantId().equals(powerPlant.getSelfInfo().plantId())) {
                grpcClient.announceEnergyWinner(plant, announcement);
            }
        }
    }
}