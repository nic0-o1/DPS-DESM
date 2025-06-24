package desm.dps.election.internal;

import desm.dps.PowerPlant;
import desm.dps.PowerPlantInfo;
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
     * Forwards the winner announcement to the next plant in the ring to continue circulation.
     * This replaces the previous broadcast implementation.
     *
     * @param nextPlant    The next plant in the ring.
     * @param announcement The winner announcement message to forward.
     */
    public void forwardWinnerAnnouncement(PowerPlantInfo nextPlant, EnergyWinnerAnnouncement announcement) {
        // We only send if there is a next plant. The gRPC client will handle communication
        // errors if the plant is down, and the PowerPlant logic will eventually remove it.
        if (nextPlant != null) {
            grpcClient.announceEnergyWinner(nextPlant, announcement);
        }
    }
}