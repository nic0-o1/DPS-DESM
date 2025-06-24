package desm.dps.election;

import desm.dps.EnergyRequest;
import desm.dps.PowerPlant;
import desm.dps.PowerPlantInfo;
import desm.dps.election.internal.ElectionCommunicator;
import desm.dps.election.internal.ElectionStateRepository;
import desm.dps.election.internal.RingAlgorithmProcessor;
import desm.dps.election.model.ElectionState;
import desm.dps.grpc.ElectCoordinatorToken;
import desm.dps.grpc.EnergyWinnerAnnouncement;
import desm.dps.grpc.PlantGrpcClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Manages the leader election process for a power plant.
 * This class acts as the central hub for all election-related events, such as
 * new energy requests, incoming election tokens, and winner announcements. It orchestrates
 * the logic of the ring-based election algorithm by delegating tasks to specialized
 * internal components.
 */
public class ElectionManager {
	private static final Logger logger = LoggerFactory.getLogger(ElectionManager.class);

	/** A special value indicating that the plant will not place a competitive bid. */
	private static final double INVALID_BID_PRICE = -1.0;

	private static final int TEST_SLEEP_DURATION_MS = 22000;

	private final PowerPlant powerPlant;
	private final ElectionStateRepository stateRepository;
	private final RingAlgorithmProcessor algorithmProcessor;
	private final ElectionCommunicator communicator;

	/**
	 * Constructs an ElectionManager for a given power plant.
	 *
	 * @param powerPlant The power plant this manager belongs to.
	 * @param grpcClient The gRPC client for communicating with other plants.
	 */
	public ElectionManager(PowerPlant powerPlant, PlantGrpcClient grpcClient) {
		this.powerPlant = powerPlant;
		// A dedicated thread for cleaning up old election states.
		ScheduledExecutorService cleanupExecutor = Executors.newScheduledThreadPool(1,
				r -> new Thread(r, "ElectionCleanup"));
		this.communicator = new ElectionCommunicator(grpcClient);
		this.stateRepository = new ElectionStateRepository(cleanupExecutor);
		this.algorithmProcessor = new RingAlgorithmProcessor(powerPlant, this.communicator);
	}

	/**
	 * Processes a new energy request, potentially initiating a new election.
	 * @param energyRequest The details of the energy request.
	 */
	public void processNewEnergyRequest(EnergyRequest energyRequest) {
		// performTestSleep();
		final int selfId = powerPlant.getSelfInfo().plantId();
		final String requestId = energyRequest.requestID();
		logger.debug("Plant {} processing new energy request {}", selfId, requestId);

		// Determine the bid price. If the plant is busy, it participates passively with an invalid price.
		final double price = powerPlant.isBusy() ? INVALID_BID_PRICE : powerPlant.generatePrice();
		final ElectionState state = stateRepository.getOrCreate(requestId, energyRequest, price);

		if (!state.isValidBid()) {
			logger.info("Plant {} is busy; participating passively in election for request {}.", selfId, requestId);
			return;
		}

		if (state.trySetInitiated()) {
			logger.info("Plant {} generated price ${} for request {}", selfId, price, energyRequest.requestID());
			algorithmProcessor.initiate(state);
		} else {
			// This occurs if we receive the same request again after already initiating or participating.
			logger.info("Plant {} ignoring duplicate energy request for {}.", selfId, requestId);
		}
	}

	/**
	 * Handles an incoming election token as part of the ring algorithm.
	 * @param token The election token received from another plant.
	 */
	public void handleElectionToken(ElectCoordinatorToken token) {
		final int selfId = powerPlant.getSelfInfo().plantId();
		final String requestId = token.getEnergyRequestId();
		logger.trace("Plant {} received election token for request {}.", selfId, requestId);

		if (selfId == token.getInitiatorId()) {
			logger.debug("Plant {} (initiator) received its completed token for request {}.", selfId, requestId);
			ElectionState state = stateRepository.get(requestId);

			if (state == null) {
				logger.error("CRITICAL: Plant {} (initiator) has no state for its own election {}. Dropping token.", selfId, requestId);
				return;
			}

			if (state.isWinnerAnnounced()) {
				logger.info("Plant {} (initiator) ignoring completed token for request {}, as winner is already known.", selfId, requestId);
				return;
			}

			if (algorithmProcessor.complete(state, token)) {
				stateRepository.scheduleCleanup(requestId);
			}
			return;
		}

		logger.debug("Plant {} (participant) processing token for request {}.", selfId, requestId);
		ElectionState state = stateRepository.get(requestId);


		if (state == null) {
			logger.info("Plant {} is joining election for request {} initiated by plant {}.", selfId, requestId, token.getInitiatorId());

			double myPrice = powerPlant.isBusy() ? INVALID_BID_PRICE : powerPlant.generatePrice();

			EnergyRequest request = new EnergyRequest(requestId, (int) token.getEnergyAmountKwh(), System.currentTimeMillis());

			state = stateRepository.getOrCreate(requestId, request, myPrice);

			if (state.isValidBid()) {
				logger.info("Plant {} (participant) generated a bid of ${} for request {}.", selfId, myPrice, requestId);
			} else {
				logger.info("Plant {} is busy; participating passively in election for request {}.", selfId, requestId);
			}
		}


		// If a winner is already known (e.g., from a faster, concurrent election), drop this token.
		if (state.isWinnerAnnounced()) {
			logger.debug("Plant {} dropping token for request {}, winner already announced.", selfId, requestId);
			return;
		}

		// Update the token with our bid if applicable and forward it to the next plant.
		algorithmProcessor.forward(state, token);
	}

	/**
	 * Processes a winner announcement received from another plant.
	 * @param announcement The winner announcement message.
	 */
	public void processEnergyWinnerAnnouncement(EnergyWinnerAnnouncement announcement) {
		final int selfId = powerPlant.getSelfInfo().plantId();
		final String requestId = announcement.getEnergyRequestId();
		final int winnerId = announcement.getWinningPlantId();
		final int announcementInitiatorId = announcement.getInitiatorId(); // The plant that determined the winner.

		logger.trace("Plant {} received winner announcement for request {}.", selfId, requestId);

		// If this plant originated the announcement, it has completed the ring. Stop circulation.
		if (selfId == announcementInitiatorId) {
			logger.info("Winner announcement for request {} completed its circulation back to initiator {}.", requestId, selfId);
			return;
		}

		ElectionState state = stateRepository.get(requestId);
		if (state == null) {
			// This can happen if the plant's local state timed out and was cleaned up before the
			// announcement arrived. Announcement must be forwarded to maintain ring integrity.
			logger.warn("Plant {} received winner announcement for an unknown or cleaned-up request {}. Forwarding to preserve ring communication.", selfId, requestId);
			PowerPlantInfo nextPlant = powerPlant.getNextPlantInRing(selfId);
			communicator.forwardWinnerAnnouncement(nextPlant, announcement);
			return;
		}

		if (state.trySetWinnerAnnounced()) {
			logger.info("Plant {} acknowledges winner {} for request {} with price ${}.", selfId, winnerId, requestId, announcement.getWinningPrice());

			// If this plant is the winner, it fulfills the request.
			if (winnerId == selfId) {
				logger.info("VICTORY! This plant ({}) won the election for request {}. Fulfilling energy request.", selfId, requestId);
				powerPlant.fulfillEnergyRequest(state.getRequest(), announcement.getWinningPrice());
			}

			// Forward the announcement to the next plant in the ring.
			PowerPlantInfo nextPlant = powerPlant.getNextPlantInRing(selfId);
			communicator.forwardWinnerAnnouncement(nextPlant, announcement);

			// This plant's role in this election is complete. Schedule local state for cleanup.
			stateRepository.scheduleCleanup(requestId);
		} else {
			// This is expected if the announcement is received more than once.
			logger.debug("Plant {} already processed winner for request {}. Ignoring duplicate announcement.", selfId, requestId);
		}
	}

	/**
	 * Pauses the current thread for a fixed duration. Used for testing race conditions
	 * and simulating network delays.
	 */
	private void performTestSleep() {
		final int selfId = powerPlant.getSelfInfo().plantId();
		try {
			logger.warn(">>> [TEST-ONLY] PLANT {} PAUSING FOR {} SECONDS BEFORE ELECTION <<<", selfId, TEST_SLEEP_DURATION_MS / 1000);
			Thread.sleep(TEST_SLEEP_DURATION_MS);
			logger.warn(">>> [TEST-ONLY] PLANT {} RESUMING... <<<", selfId);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			logger.error("Test sleep was interrupted for Plant {}", selfId, e);
		}
	}
}