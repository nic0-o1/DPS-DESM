package desm.dps.election;

import desm.dps.EnergyRequest;
import desm.dps.PowerPlant;
import desm.dps.PowerPlantInfo;
import desm.dps.grpc.Bid;
import desm.dps.grpc.ElectCoordinatorToken;
import desm.dps.grpc.EnergyWinnerAnnouncement;
import desm.dps.grpc.PlantGrpcClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Manages distributed election process for energy requests using ring-based algorithm.
 * Thread-safe implementation without native synchronization data structures.
 */
public class ElectionManager {
	private static final Logger logger = LoggerFactory.getLogger(ElectionManager.class);
	private static final double INVALID_BID = -1.0;
	private static final int CLEANUP_DELAY_SECONDS = 30;
	private static final int TEST_SLEEP_DURATION_MS = 22000; // Remove for production

	private final PowerPlant powerPlant;
	private final PlantGrpcClient grpcClient;
	private final ScheduledExecutorService cleanupExecutor;

	private final Map<String, ElectionState> elections = new HashMap<>();
	private final Object electionsLock = new Object();

	public ElectionManager(PowerPlant powerPlant, PlantGrpcClient grpcClient) {
		this.powerPlant = powerPlant;
		this.grpcClient = grpcClient;
		this.cleanupExecutor = Executors.newScheduledThreadPool(2, r -> {
			Thread t = new Thread(r);
			t.setDaemon(true);
			t.setName("ElectionCleanup-" + t.getId());
			return t;
		});
	}

	/**
	 * Immutable state holder for election data with thread-safe operations.
	 */
	private static class ElectionState {
		private final EnergyRequest request;
		private final double myBid;
		private final Object stateLock = new Object();

		private volatile Bid bestBidSeen;
		private volatile boolean winnerAnnounced = false;
		private volatile boolean hasInitiated = false;

		ElectionState(EnergyRequest request, double myBid) {
			this.request = request;
			this.myBid = myBid;
			this.bestBidSeen = createEmptyBid();
		}

		private static Bid createEmptyBid() {
			return Bid.newBuilder()
					.setPlantId("")
					.setPrice(Double.MAX_VALUE)
					.build();
		}

		boolean updateBestBid(Bid newBid) {
			synchronized (stateLock) {
				if (BidComparator.isBetter(newBid, this.bestBidSeen)) {
					this.bestBidSeen = newBid;
					return true;
				}
				return false;
			}
		}

		Bid getBestBid() {
			return this.bestBidSeen;
		}

		boolean isWinnerAnnounced() {
			return this.winnerAnnounced;
		}

		boolean trySetWinnerAnnounced() {
			synchronized (stateLock) {
				if (!this.winnerAnnounced) {
					this.winnerAnnounced = true;
					return true;
				}
				return false;
			}
		}

		boolean trySetInitiated() {
			synchronized (stateLock) {
				if (!this.hasInitiated) {
					this.hasInitiated = true;
					return true;
				}
				return false;
			}
		}

		EnergyRequest getRequest() {
			return request;
		}

		double getMyBid() {
			return myBid;
		}

		boolean isValidBid() {
			return myBid >= 0;
		}
	}

	/**
	 * Utility class for bid comparison logic.
	 */
	private static class BidComparator {
		static boolean isBetter(Bid candidate, Bid current) {
			if (candidate == null) return false;
			if (current == null || isEmptyBid(current)) return true;
			if (candidate.getPrice() < current.getPrice()) return true;
			return candidate.getPrice() == current.getPrice() &&
					candidate.getPlantId().compareTo(current.getPlantId()) < 0;
		}

		private static boolean isEmptyBid(Bid bid) {
			return Objects.equals(bid.getPlantId(), "");
		}
	}

	/**
	 * Processes new energy request by creating election state and potentially initiating election.
	 */
	public void processNewEnergyRequest(EnergyRequest energyRequest) {
		String selfId = getSelfPlantId();
		String requestId = energyRequest.getRequestID();

		double price = calculateBidPrice();
		ElectionState state = getOrCreateElectionState(requestId, energyRequest, price);

//		performTestSleep(selfId); // Remove for production

		if (!state.isValidBid()) {
			logger.info("Plant {} is BUSY, participating passively in election for request {}",
					selfId, requestId);
			return;
		}

		if (state.trySetInitiated()) {
			logger.info("Plant {} generated price ${} for request {}", selfId, price, requestId);
			initiateElection(state);
		} else {
			logger.info("Plant {} received duplicate MQTT request for {}", selfId, requestId);
		}
	}

	/**
	 * Handles incoming election token in the ring-based election algorithm.
	 */
	public void handleElectionToken(ElectCoordinatorToken token) {
		String selfId = getSelfPlantId();
		String initiatorId = token.getInitiatorId();
		String requestId = token.getEnergyRequestId();

		ElectionState state = getOrCreateElectionStateFromToken(token);
		state.trySetInitiated();

		if (state.isWinnerAnnounced()) {
			logger.debug("Plant {} dropping token for request {}, winner already announced",
					selfId, requestId);
			return;
		}

		if (selfId.equals(initiatorId)) {
			completeElectionRound(state, token);
		} else {
			forwardTokenInRing(state, token);
		}
	}

	/**
	 * Processes winner announcement from other plants.
	 */
	public void processEnergyWinnerAnnouncement(EnergyWinnerAnnouncement announcement) {
		String selfId = getSelfPlantId();
		String requestId = announcement.getEnergyRequestId();
		String winnerId = announcement.getWinningPlantId();

		ElectionState state = getElectionState(requestId);
		if (state == null) {
			logger.debug("Plant {} received winner announcement for unknown request {}",
					selfId, requestId);
			return;
		}

		if (state.trySetWinnerAnnounced()) {
			logger.info("Plant {} acknowledges winner {} for request {} at ${}",
					selfId, winnerId, requestId, announcement.getWinningPrice());

			if (winnerId.equals(selfId)) {
				logger.info("Plant {} is the winner! Fulfilling request {}", selfId, requestId);
				fulfillEnergyRequest(state, announcement.getWinningPrice());
			}

			scheduleCleanup(requestId);
		}
	}

	/**
	 * Shuts down the election manager and cleanup resources.
	 */
	public void shutdown() {
		cleanupExecutor.shutdown();
		try {
			if (!cleanupExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
				cleanupExecutor.shutdownNow();
			}
		} catch (InterruptedException e) {
			cleanupExecutor.shutdownNow();
			Thread.currentThread().interrupt();
		}
	}

	// Private helper methods

	private void initiateElection(ElectionState state) {
		String selfId = getSelfPlantId();
		Bid myBid = createBid(selfId, state.getMyBid());
		state.updateBestBid(myBid);

		ElectCoordinatorToken token = createElectionToken(selfId, state, myBid);
		PowerPlantInfo nextPlant = getNextPlantInRing(selfId);

		if (nextPlant == null || nextPlant.getPlantId().equals(selfId)) {
			logger.warn("Plant {} is alone in ring, declaring self winner for request {}",
					selfId, state.getRequest().getRequestID());
			completeElectionRound(state, token);
			return;
		}

		logger.info("Plant {} initiating election for request {}, forwarding to {}",
				selfId, state.getRequest().getRequestID(), nextPlant.getPlantId());
		grpcClient.forwardElectionToken(nextPlant, token);
	}

	private void forwardTokenInRing(ElectionState state, ElectCoordinatorToken incomingToken) {
		String selfId = getSelfPlantId();

		state.updateBestBid(incomingToken.getBestBid());
		Bid bestBid = getBestBidSafely(state);

		if (state.isValidBid()) {
			Bid myBid = createBid(selfId, state.getMyBid());
			logger.info("Plant {} considering bid ${} for request {}, current best: {} @ ${}",
					selfId, myBid.getPrice(), state.getRequest().getRequestID(),
					bestBid.getPlantId(), bestBid.getPrice());

			if (state.updateBestBid(myBid)) {
				bestBid = getBestBidSafely(state);
				logger.info("Plant {} has better bid, updating token", selfId);
			}
		} else {
			logger.info("Plant {} is busy, forwarding token with best bid: {} @ ${}",
					selfId, bestBid.getPlantId(), bestBid.getPrice());
		}

		ElectCoordinatorToken forwardToken = incomingToken.toBuilder()
				.setBestBid(bestBid)
				.build();
		grpcClient.forwardElectionToken(getNextPlantInRing(selfId), forwardToken);
	}

	private void completeElectionRound(ElectionState state, ElectCoordinatorToken token) {
		String selfId = getSelfPlantId();
		state.updateBestBid(token.getBestBid());
		Bid winnerBid = getBestBidSafely(state);

		if (state.trySetWinnerAnnounced()) {
			logger.info("Plant {} completed election for request {}, winner: {} @ ${}",
					selfId, state.getRequest().getRequestID(),
					winnerBid.getPlantId(), winnerBid.getPrice());

			if (winnerBid.getPlantId().equals(selfId)) {
				logger.info("Plant {} is the winner! Fulfilling request {}",
						selfId, state.getRequest().getRequestID());
				fulfillEnergyRequest(state, winnerBid.getPrice());
			}

			announceWinnerToOthers(state.getRequest().getRequestID(), winnerBid);
			scheduleCleanup(state.getRequest().getRequestID());
		} else {
			logger.info("Plant {} completed election for request {}, but winner already announced",
					selfId, state.getRequest().getRequestID());
		}
	}

	private void announceWinnerToOthers(String requestId, Bid winnerBid) {
		EnergyWinnerAnnouncement announcement = EnergyWinnerAnnouncement.newBuilder()
				.setWinningPlantId(winnerBid.getPlantId())
				.setWinningPrice(winnerBid.getPrice())
				.setEnergyRequestId(requestId)
				.build();

		for (PowerPlantInfo plant : powerPlant.getOtherPlants()) {
			if (!plant.getPlantId().equals(getSelfPlantId())) {
				grpcClient.announceEnergyWinner(plant, announcement);
			}
		}
	}

	private ElectionState getOrCreateElectionState(String requestId, EnergyRequest request, double price) {
		synchronized (electionsLock) {
			return elections.computeIfAbsent(requestId, k -> new ElectionState(request, price));
		}
	}

	private ElectionState getOrCreateElectionStateFromToken(ElectCoordinatorToken token) {
		String requestId = token.getEnergyRequestId();
		EnergyRequest request = new EnergyRequest(
				requestId,
				(int) token.getEnergyAmountKwh(),
				System.currentTimeMillis()
		);
		double price = calculateBidPrice();
		return getOrCreateElectionState(requestId, request, price);
	}

	private ElectionState getElectionState(String requestId) {
		synchronized (electionsLock) {
			return elections.get(requestId);
		}
	}

	private void scheduleCleanup(String requestId) {
		cleanupExecutor.schedule(() -> {
			synchronized (electionsLock) {
				elections.remove(requestId);
			}
			logger.debug("Cleaned up election state for request {}", requestId);
		}, CLEANUP_DELAY_SECONDS, TimeUnit.SECONDS);
	}

	// Utility methods

	private String getSelfPlantId() {
		return powerPlant.getSelfInfo().getPlantId();
	}

	private double calculateBidPrice() {
		return powerPlant.isBusy() ? INVALID_BID : powerPlant.generatePrice();
	}

	private PowerPlantInfo getNextPlantInRing(String selfId) {
		return powerPlant.getNextPlantInRing(selfId);
	}

	private Bid createBid(String plantId, double price) {
		return Bid.newBuilder()
				.setPlantId(plantId)
				.setPrice(price)
				.build();
	}

	private ElectCoordinatorToken createElectionToken(String selfId, ElectionState state, Bid bestBid) {
		return ElectCoordinatorToken.newBuilder()
				.setInitiatorId(selfId)
				.setEnergyRequestId(state.getRequest().getRequestID())
				.setBestBid(bestBid)
				.setEnergyAmountKwh(state.getRequest().getAmountKWh())
				.build();
	}

	private Bid getBestBidSafely(ElectionState state) {
		synchronized (state.stateLock) {
			return state.getBestBid();
		}
	}

	private void fulfillEnergyRequest(ElectionState state, double price) {
		powerPlant.fulfillEnergyRequest(state.getRequest(), price);
	}

	private void performTestSleep(String selfId) {
		// Remove this entire method for production
		try {
			logger.warn(">>> PLANT {} PAUSING FOR {} SECONDS TO ALLOW LATE JOINER <<<",
					selfId, TEST_SLEEP_DURATION_MS / 1000);
			Thread.sleep(TEST_SLEEP_DURATION_MS);
			logger.warn(">>> PLANT {} RESUMING... <<<", selfId);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			logger.error("Test sleep was interrupted", e);
		}
	}
}