package desm.dps.grpc;

import desm.dps.PowerPlant;
import desm.dps.PowerPlantInfo;
import desm.dps.grpc.PlantCommunicationServiceGrpc.PlantCommunicationServiceBlockingStub;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Manages gRPC client-side communication with other power plants.
 * This class handles creating, caching, and using gRPC channels and stubs in a thread-safe manner.
 * It also encapsulates logic for handling communication failures, which typically result in
 * removing the unresponsive plant from the local topology.
 */
public class PlantGrpcClient {
	private static final Logger logger = LoggerFactory.getLogger(PlantGrpcClient.class);
	private static final long GRPC_TIMEOUT_SECONDS = 45;

	private final PowerPlant powerPlant;
	private final Object stubsLock = new Object();

	// --- State fields guarded by stubsLock ---
	private final Map<Integer, PlantCommunicationServiceBlockingStub> blockingStubs = new HashMap<>();
	private final Map<Integer, ManagedChannel> channels = new HashMap<>();
	// --- End of guarded state fields ---

	public PlantGrpcClient(PowerPlant powerPlant) {
		this.powerPlant = powerPlant;
	}

	/**
	 * Retrieves a cached gRPC blocking stub for a target plant, or creates a new one if it doesn't exist.
	 * This method is thread-safe.
	 *
	 * @param targetPlant The plant to communicate with.
	 * @return A gRPC blocking stub for the target plant.
	 */
	private PlantCommunicationServiceBlockingStub getBlockingStub(PowerPlantInfo targetPlant) {
		int targetId = targetPlant.plantId();
		synchronized (stubsLock) {
			if (!blockingStubs.containsKey(targetId)) {
				logger.info("Creating new gRPC client stub for Plant {} at {}:{}",
						targetPlant.plantId(), targetPlant.address(), targetPlant.port());
				ManagedChannel channel = ManagedChannelBuilder.forAddress(targetPlant.address(), targetPlant.port())
						.usePlaintext()
						.build();
				channels.put(targetId, channel);
				blockingStubs.put(targetId, PlantCommunicationServiceGrpc.newBlockingStub(channel));
			}
			return blockingStubs.get(targetId);
		}
	}

	/**
	 * Announces this plant's presence to another plant.
	 * If the RPC call fails, the target plant is removed from this plant's ring topology.
	 *
	 * @param targetPlant The plant to announce presence to.
	 * @param selfInfo    Information about this plant.
	 */
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

			logger.info("Announcing presence of Plant {} to Plant {}", selfInfo.plantId(), targetPlant.plantId());
			stub.withDeadlineAfter(GRPC_TIMEOUT_SECONDS, TimeUnit.SECONDS).announcePresence(request);
		} catch (StatusRuntimeException e) {
			logger.warn("RPC to announcePresence to Plant {} failed: {}. Removing plant.", targetPlant.plantId(), e.getStatus());
			powerPlant.removeOtherPlant(targetPlant.plantId());
		}
	}

	/**
	 * Forwards an election token to a target plant.
	 * If the RPC call fails, the target plant is removed from this plant's ring topology.
	 *
	 * @param targetPlant The plant to forward the token to.
	 * @param token       The election token.
	 */
	public void forwardElectionToken(PowerPlantInfo targetPlant, ElectCoordinatorToken token) {
		try {
			PlantCommunicationServiceBlockingStub stub = getBlockingStub(targetPlant);
			String bestBidder = token.getBestBid().getPlantId() == 0 ? "None" : String.valueOf(token.getBestBid().getPlantId());
			logger.info("Forwarding token to Plant {} for ER {} (Best Bid: Plant {} @ ${})",
					targetPlant.plantId(),
					token.getEnergyRequestId(),
					bestBidder,
					token.getBestBid().getPrice()
			);

			stub.withDeadlineAfter(GRPC_TIMEOUT_SECONDS, TimeUnit.SECONDS).forwardElectionToken(token);
		} catch (StatusRuntimeException e) {
			logger.error("RPC to forwardElectionToken to Plant {} failed: {}.",
					targetPlant.plantId(), e.getStatus(), e);
		}
	}

	/**
	 * Announces the election winner to a target plant.
	 * If the RPC call fails, the target plant is removed from this plant's ring topology.
	 *
	 * @param targetPlant  The plant to announce the winner to.
	 * @param announcement The winner announcement message.
	 */
	public void announceEnergyWinner(PowerPlantInfo targetPlant, EnergyWinnerAnnouncement announcement) {
		try {
			PlantCommunicationServiceBlockingStub stub = getBlockingStub(targetPlant);
			logger.debug("Announcing winner for ER {} to Plant {}", announcement.getEnergyRequestId(), targetPlant.plantId());
			stub.withDeadlineAfter(GRPC_TIMEOUT_SECONDS, TimeUnit.SECONDS).announceEnergyWinner(announcement);
		} catch (StatusRuntimeException e) {
			logger.warn("RPC to announceEnergyWinner to Plant {} failed: {}. Removing plant.",
					targetPlant.plantId(), e.getStatus());
			powerPlant.removeOtherPlant(targetPlant.plantId());
		}
	}

	/**
	 * Shuts down all active gRPC channels and clears connection caches.
	 */
	public void shutdown() {
		ArrayList<ManagedChannel> channelsToShutDown;
		synchronized (stubsLock) {
			channelsToShutDown = new ArrayList<>(channels.values());
			channels.clear();
			blockingStubs.clear();
		}

		logger.info("Shutting down {} gRPC client channels...", channelsToShutDown.size());
		for (ManagedChannel channel : channelsToShutDown) {
			try {
				channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
			} catch (InterruptedException e) {
				logger.warn("Interrupted while shutting down gRPC client channel. Forcing shutdown.", e);
				channel.shutdownNow();
				Thread.currentThread().interrupt();
			}
		}
		logger.info("All gRPC client channels shut down.");
	}
}