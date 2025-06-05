package desm.dps.grpc;

import desm.dps.PowerPlant;
import desm.dps.PowerPlantInfo;
import io.grpc.ManagedChannel;
import desm.dps.grpc.PlantCommunicationServiceGrpc.*;

import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;


public class PlantGrpcClient {
	private static final Logger logger = LoggerFactory.getLogger(PlantGrpcClient.class);
	private final Map<String, PlantCommunicationServiceBlockingStub> blockingStubs = new HashMap<>();
	private final Map<String, ManagedChannel> channels = new HashMap<>();
	private final PowerPlant powerPlant;

	public PlantGrpcClient(PowerPlant powerPlant) {
		this.powerPlant = powerPlant;
	}

	private PlantCommunicationServiceBlockingStub getBlockingStub(PowerPlantInfo targetPlant) {
		String targetAddress = targetPlant.getAddress() + ":" + targetPlant.getPort();
		if (!blockingStubs.containsKey(targetPlant.getPlantId())) {
			ManagedChannel channel = ManagedChannelBuilder.forAddress(targetPlant.getAddress(), targetPlant.getPort())
					.usePlaintext()
					.build();
			channels.put(targetPlant.getPlantId(), channel);
			blockingStubs.put(targetPlant.getPlantId(), PlantCommunicationServiceGrpc.newBlockingStub(channel));
			logger.info("Created new gRPC client stub for {} at {}", targetPlant.getPlantId(), targetAddress);
		}
		return blockingStubs.get(targetPlant.getPlantId());
	}

	public void announcePresence(PowerPlantInfo targetPlant, PowerPlantInfo selfInfo) {
		PlantCommunicationServiceBlockingStub stub = getBlockingStub(targetPlant);
		PowerPlantMeta selfMeta = PowerPlantMeta.newBuilder()
				.setPlantId(selfInfo.getPlantId())
				.setAddress(PlantAddress.newBuilder()
						.setHost(selfInfo.getAddress())
						.setPort(selfInfo.getPort())
						.build())
				.build();
		AnnouncePresenceRequest request = AnnouncePresenceRequest.newBuilder()
				.setNewPlantInfo(selfMeta)
				.build();
		try {
			logger.info("Announcing presence of {} to {}", selfInfo.getPlantId(), targetPlant.getPlantId());
			Ack ack = stub.withDeadlineAfter(5, TimeUnit.SECONDS).announcePresence(request);
			logger.info("Presence announcement to {} {}", targetPlant.getPlantId(), ack.getSuccess() ? "succeeded" : "failed: " + ack.getMessage());
		} catch (StatusRuntimeException e) {
			logger.warn("RPC to announcePresence to {} failed: {}", targetPlant.getPlantId(), e.getStatus(), e);
		}
	}

	public boolean forwardElectionToken(PowerPlantInfo targetPlant, ElectCoordinatorToken token) {
		PlantCommunicationServiceBlockingStub stub = getBlockingStub(targetPlant);
		try {
			logger.info("Forwarding Election Token for ER {} to {} (Initiator: {}, Best Bid: {} at ${})",
					token.getEnergyRequestId(),
					targetPlant.getPlantId(),
					token.getInitiatorId(),
					token.getBestBid().getPlantId(),
					token.getBestBid().getPrice());

			Ack response = stub.withDeadlineAfter(10, TimeUnit.SECONDS).forwardElectionToken(token);

			if (response.getSuccess()) {
				logger.debug("Successfully forwarded election token for ER {} to {}",
						token.getEnergyRequestId(), targetPlant.getPlantId());
				return true;
			} else {
				logger.warn("Failed to forward election token for ER {} to {}: {}",
						token.getEnergyRequestId(), targetPlant.getPlantId(), response.getMessage());
				return false;
			}
		} catch (StatusRuntimeException e) {
			logger.error("RPC to forwardElectionToken to {} failed: {}. ER: {}",
					targetPlant.getPlantId(), e.getStatus(), token.getEnergyRequestId(), e);

			// Remove the plant if it's unreachable
			powerPlant.removeOtherPlant(targetPlant.getPlantId());
			return false;
		} catch (Exception e) {
			logger.error("Unexpected error forwarding election token to {}: {}",
					targetPlant.getPlantId(), e.getMessage(), e);
			return false;
		}
	}

	public void announceEnergyWinner(PowerPlantInfo targetPlant, EnergyWinnerAnnouncement announcement) {
		PlantCommunicationServiceBlockingStub stub = getBlockingStub(targetPlant);
		try {
			logger.info("Announcing Energy Winner {} for ER {} to {}",
					announcement.getWinningPlantId(),
					announcement.getEnergyRequestId(),
					targetPlant.getPlantId());

			Ack response = stub.withDeadlineAfter(10, TimeUnit.SECONDS).announceEnergyWinner(announcement);

			if (!response.getSuccess()) {
				logger.warn("Winner announcement to {} failed: {}",
						targetPlant.getPlantId(), response.getMessage());
			}
		} catch (StatusRuntimeException e) {
			logger.warn("RPC to announceEnergyWinner to {} failed: {}", targetPlant.getPlantId(), e.getStatus(), e);
			powerPlant.removeOtherPlant(targetPlant.getPlantId());
		} catch (Exception e) {
			logger.error("Unexpected error announcing winner to {}: {}",
					targetPlant.getPlantId(), e.getMessage(), e);
		}
	}

	public void shutdown() {
		for (ManagedChannel channel : channels.values()) {
			try {
				channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
			} catch (InterruptedException e) {
				logger.warn("Interrupted while shutting down gRPC client channel.", e);
				channel.shutdownNow();
				Thread.currentThread().interrupt();
			}
		}
		channels.clear();
		blockingStubs.clear();
		logger.info("All gRPC client channels shut down.");
	}
}