package desm.dps.grpc;

import desm.dps.PowerPlant;
import desm.dps.PowerPlantInfo;
import desm.dps.election.ElectionManager;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implements the server-side gRPC service for handling incoming requests from other power plants.
 * This service acts as the entry point for all inter-plant communication.
 */
public class PlantGrpcService extends PlantCommunicationServiceGrpc.PlantCommunicationServiceImplBase {
    private static final Logger logger = LoggerFactory.getLogger(PlantGrpcService.class);

    private final PowerPlant powerPlant;
    private final ElectionManager electionManager;

    public PlantGrpcService(PowerPlant powerPlant, ElectionManager electionManager) {
        this.powerPlant = powerPlant;
        this.electionManager = electionManager;
    }

    /**
     * Handles presence announcements from other plants that are joining the ring topology.
     * The announcing plant is added to the local view of the ring.
     *
     * @param request        The announcement request containing the new plant's metadata.
     * @param responseObserver The observer for sending back an acknowledgment.
     */
    @Override
    public void announcePresence(AnnouncePresenceRequest request, StreamObserver<Ack> responseObserver) {
        PowerPlantMeta meta = request.getNewPlantInfo();
        PowerPlantInfo newPlantInfo = new PowerPlantInfo(
                meta.getPlantId(),
                meta.getAddress().getHost(),
                meta.getAddress().getPort(),
                meta.getRegistrationTime()
        );

        logger.info("Received presence announcement from Plant {}", newPlantInfo.plantId());
        powerPlant.addOtherPlant(newPlantInfo);

        Ack ack = Ack.newBuilder()
                .setSuccess(true)
                .setMessage("Presence acknowledged by Plant " + powerPlant.getSelfInfo().plantId())
                .build();
        responseObserver.onNext(ack);
        responseObserver.onCompleted();
    }

    /**
     * Handles an incoming election token from the previous plant in the ring.
     * The method immediately sends an ACK to the caller and then passes the token to the
     * {@link ElectionManager} for asynchronous processing to avoid blocking the gRPC thread.
     *
     * @param request        The election token.
     * @param responseObserver The observer for sending back an acknowledgment.
     */
    @Override
    public void forwardElectionToken(ElectCoordinatorToken request, StreamObserver<Ack> responseObserver) {
        logger.info("gRPC service received ForwardElectionToken for ER {} from initiator {}.",
                request.getEnergyRequestId(), request.getInitiatorId());

        // Acknowledge the request immediately to free up the gRPC thread.
        responseObserver.onNext(Ack.newBuilder().setSuccess(true).build());
        responseObserver.onCompleted();

        // Process the token asynchronously.
        try {
            electionManager.handleElectionToken(request);
        } catch (Exception e) {
            logger.error("Error during asynchronous processing of election token for ER: {}", request.getEnergyRequestId(), e);
        }
    }

    /**
     * Handles a winner announcement message that is circulating the ring.
     * The method immediately sends an ACK to the caller and then passes the announcement to the
     * {@link ElectionManager} for asynchronous processing.
     *
     * @param request        The winner announcement.
     * @param responseObserver The observer for sending back an acknowledgment.
     */
    @Override
    public void announceEnergyWinner(EnergyWinnerAnnouncement request, StreamObserver<Ack> responseObserver) {
        logger.info("gRPC service received AnnounceEnergyWinner for ER: {}. Winner is Plant {} at ${}",
                request.getEnergyRequestId(), request.getWinningPlantId(), request.getWinningPrice());

        // Acknowledge the request immediately.
        responseObserver.onNext(Ack.newBuilder().setSuccess(true).build());
        responseObserver.onCompleted();

        // Process the announcement asynchronously.
        try {
            electionManager.processEnergyWinnerAnnouncement(request);
        } catch (Exception e) {
            logger.error("Error during asynchronous processing of winner announcement for ER: {}", request.getEnergyRequestId(), e);
        }
    }
}