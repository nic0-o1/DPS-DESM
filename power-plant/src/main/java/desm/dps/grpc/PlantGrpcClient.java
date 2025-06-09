package desm.dps.grpc;

import desm.dps.PowerPlant;
import desm.dps.PowerPlantInfo;
import desm.dps.grpc.PlantCommunicationServiceGrpc.PlantCommunicationServiceBlockingStub;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class PlantGrpcClient {
	private static final Logger logger = LoggerFactory.getLogger(PlantGrpcClient.class);
	private final Map<Integer, PlantCommunicationServiceBlockingStub> blockingStubs = new HashMap<>();
	private final Map<Integer, ManagedChannel> channels = new HashMap<>();
	private final PowerPlant powerPlant;

	public PlantGrpcClient(PowerPlant powerPlant) {
		this.powerPlant = powerPlant;
	}

	private PlantCommunicationServiceBlockingStub getBlockingStub(PowerPlantInfo targetPlant) {
		int targetId = targetPlant.plantId();
		if (!blockingStubs.containsKey(targetId)) {
			ManagedChannel channel = ManagedChannelBuilder.forAddress(targetPlant.address(), targetPlant.port())
					.usePlaintext()
					.build();
			channels.put(targetId, channel);
			blockingStubs.put(targetId, PlantCommunicationServiceGrpc.newBlockingStub(channel));
			logger.info("Created new gRPC client stub for {} at {}:{}",
					targetPlant.plantId(), targetPlant.address(), targetPlant.port());
		}
		return blockingStubs.get(targetId);
	}

	public void announcePresence(PowerPlantInfo targetPlant, PowerPlantInfo selfInfo) {
		try {
			PlantCommunicationServiceBlockingStub stub = getBlockingStub(targetPlant);
			PowerPlantMeta selfMeta = PowerPlantMeta.newBuilder()
					.setPlantId(selfInfo.plantId())
					.setAddress(PlantAddress.newBuilder()
							.setHost(selfInfo.address())
							.setPort(selfInfo.port())
							.build())
					.build();
			AnnouncePresenceRequest request = AnnouncePresenceRequest.newBuilder().setNewPlantInfo(selfMeta).build();

			logger.info("Announcing presence of {} to {}", selfInfo.plantId(), targetPlant.plantId());
			stub.withDeadlineAfter(5, TimeUnit.SECONDS).announcePresence(request);
		} catch (StatusRuntimeException e) {
			logger.warn("RPC to announcePresence to {} failed: {}", targetPlant.plantId(), e.getStatus());
			powerPlant.removeOtherPlant(targetPlant.plantId());
		}
	}

	public void forwardElectionToken(PowerPlantInfo targetPlant, ElectCoordinatorToken token) {
		try {
			PlantCommunicationServiceBlockingStub stub = getBlockingStub(targetPlant);
			logger.info("Forwarding token to {} for ER {} (Best Bid: {} @ ${})",
					targetPlant.plantId(),
					token.getEnergyRequestId(),
					token.getBestBid().getPlantId() == 0 ? "None" : token.getBestBid().getPlantId(),
					token.getBestBid().getPrice()
			);

			stub.withDeadlineAfter(10, TimeUnit.SECONDS).forwardElectionToken(token);
		} catch (StatusRuntimeException e) {
			logger.error("RPC to forwardElectionToken to {} failed: {}. Removing plant from ring.",
					targetPlant.plantId(), e.getStatus());
			powerPlant.removeOtherPlant(targetPlant.plantId());
		}
	}

	public void announceEnergyWinner(PowerPlantInfo targetPlant, EnergyWinnerAnnouncement announcement) {
		try {
			PlantCommunicationServiceBlockingStub stub = getBlockingStub(targetPlant);
			logger.debug("Announcing winner for ER {} to {}", announcement.getEnergyRequestId(), targetPlant.plantId());
			stub.withDeadlineAfter(10, TimeUnit.SECONDS).announceEnergyWinner(announcement);
		} catch (StatusRuntimeException e) {
			logger.warn("RPC to announceEnergyWinner to {} failed: {}. Removing plant.",
					targetPlant.plantId(), e.getStatus());
			powerPlant.removeOtherPlant(targetPlant.plantId());
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