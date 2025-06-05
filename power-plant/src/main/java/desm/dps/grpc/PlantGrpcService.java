package desm.dps.grpc;

import desm.dps.PowerPlant;
import desm.dps.PowerPlantInfo;
import desm.dps.election.ElectionManager;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PlantGrpcService extends PlantCommunicationServiceGrpc.PlantCommunicationServiceImplBase {
    private static final Logger logger = LoggerFactory.getLogger(PlantGrpcService.class);
    private final PowerPlant powerPlant;
    private final ElectionManager electionManager;

    public PlantGrpcService(PowerPlant powerPlant, ElectionManager electionManager) {
        this.powerPlant = powerPlant;
        this.electionManager = electionManager;
    }

    @Override
    public void announcePresence(AnnouncePresenceRequest request, StreamObserver<Ack> responseObserver) {
        try {
            PowerPlantMeta meta = request.getNewPlantInfo();
            PowerPlantInfo newPlantInfo = new PowerPlantInfo(
                    meta.getPlantId(),
                    meta.getAddress().getHost(),
                    meta.getAddress().getPort()
            );
            logger.info("Received presence announcement from: {}", newPlantInfo.getPlantId());
            powerPlant.addOtherPlant(newPlantInfo);

            Ack ack = Ack.newBuilder()
                    .setSuccess(true)
                    .setMessage("Presence acknowledged by " + powerPlant.getSelfInfo().getPlantId())
                    .build();
            responseObserver.onNext(ack);
            responseObserver.onCompleted();
        } catch (Exception e) {
            logger.error("Error handling presence announcement", e);
            Ack ack = Ack.newBuilder()
                    .setSuccess(false)
                    .setMessage("Error: " + e.getMessage())
                    .build();
            responseObserver.onNext(ack);
            responseObserver.onCompleted();
        }
    }

    @Override
    public void forwardElectionToken(ElectCoordinatorToken request, StreamObserver<Ack> responseObserver) {
        try {
            String selfId = powerPlant.getSelfInfo().getPlantId();
            logger.info("gRPC service received ForwardElectionToken for ER: {} from initiator: {} (current best: {} at ${})",
                    request.getEnergyRequestId(),
                    request.getInitiatorId(),
                    request.getBestBid().getPlantId(),
                    request.getBestBid().getPrice());

            // Process the token asynchronously to avoid blocking the gRPC thread
            // But first send acknowledgment
            Ack ack = Ack.newBuilder().setSuccess(true).build();
            responseObserver.onNext(ack);
            responseObserver.onCompleted();

            // Now process the token (this prevents gRPC timeout issues)
            try {
                electionManager.handleElectionToken(request);
            } catch (Exception e) {
                logger.error("Error processing election token for ER: {}", request.getEnergyRequestId(), e);
            }

        } catch (Exception e) {
            logger.error("Error handling election token", e);
            Ack ack = Ack.newBuilder()
                    .setSuccess(false)
                    .setMessage("Error: " + e.getMessage())
                    .build();
            responseObserver.onNext(ack);
            responseObserver.onCompleted();
        }
    }

    @Override
    public void announceEnergyWinner(EnergyWinnerAnnouncement request, StreamObserver<Ack> responseObserver) {
        try {
            logger.info("gRPC service received AnnounceEnergyWinner for ER: {}, Winner: {} at ${}",
                    request.getEnergyRequestId(),
                    request.getWinningPlantId(),
                    request.getWinningPrice());

            // Send acknowledgment first
            Ack ack = Ack.newBuilder().setSuccess(true).build();
            responseObserver.onNext(ack);
            responseObserver.onCompleted();

            // Process asynchronously
            try {
                electionManager.processEnergyWinnerAnnouncement(request);
            } catch (Exception e) {
                logger.error("Error processing winner announcement for ER: {}", request.getEnergyRequestId(), e);
            }

        } catch (Exception e) {
            logger.error("Error handling winner announcement", e);
            Ack ack = Ack.newBuilder()
                    .setSuccess(false)
                    .setMessage("Error: " + e.getMessage())
                    .build();
            responseObserver.onNext(ack);
            responseObserver.onCompleted();
        }
    }
}