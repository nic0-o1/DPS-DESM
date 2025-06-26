package desm.dps.election.internal;

import desm.dps.PowerPlantInfo;
import desm.dps.grpc.ElectCoordinatorToken;
import desm.dps.grpc.EnergyWinnerAnnouncement;
import desm.dps.grpc.PlantGrpcClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A dedicated component for handling network communication related to the election process.
 * It acts as an abstraction over the gRPC client for election-specific messages.
 */
public class ElectionCommunicator {
    private static final Logger logger = LoggerFactory.getLogger(ElectionCommunicator.class);
    private final PlantGrpcClient grpcClient;

    public ElectionCommunicator(PlantGrpcClient grpcClient) {
        this.grpcClient = grpcClient;
    }

    /**
     * Forwards an election token to the next power plant in the logical ring.
     *
     * @param nextPlant The plant to forward the token to.
     * @param token     The election token to be forwarded.
     */
    public void forwardToken(PowerPlantInfo nextPlant, ElectCoordinatorToken token) {
        logger.debug("Forwarding election token for ER '{}' to Plant {}", token.getEnergyRequestId(), nextPlant.plantId());
        grpcClient.forwardElectionToken(nextPlant, token);
    }

    /**
     * Forwards the winner announcement to the next power plant to continue its circulation around the ring.
     *
     * @param nextPlant    The next plant in the ring. Can be null if this is the only plant.
     * @param announcement The winner announcement message to forward.
     */
    public void forwardWinnerAnnouncement(PowerPlantInfo nextPlant, EnergyWinnerAnnouncement announcement) {
        if (nextPlant != null) {
            logger.debug("Forwarding winner announcement for ER '{}' to Plant {}", announcement.getEnergyRequestId(), nextPlant.plantId());
            grpcClient.announceEnergyWinner(nextPlant, announcement);
        } else {
            logger.debug("Not forwarding winner announcement for ER '{}'; no next plant in ring.", announcement.getEnergyRequestId());
        }
    }
}