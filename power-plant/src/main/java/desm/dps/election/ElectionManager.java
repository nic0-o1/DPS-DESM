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
 * Central coordinator for election processes in the distributed energy system.
 * Manages election lifecycle, handles incoming tokens, and processes winner announcements.
 */
public class ElectionManager {
	private static final Logger logger = LoggerFactory.getLogger(ElectionManager.class);

	private static final int TEST_SLEEP_DURATION_MS = 22000;

	private final PowerPlant powerPlant;
	private final ElectionStateRepository stateRepository;
	private final RingAlgorithmProcessor algorithmProcessor;
	private final ElectionCommunicator communicator;

	public ElectionManager(PowerPlant powerPlant, PlantGrpcClient grpcClient) {
		this.powerPlant = powerPlant;
		ScheduledExecutorService cleanupExecutor = Executors.newScheduledThreadPool(1,
				r -> new Thread(r, "ElectionCleanup"));
		this.communicator = new ElectionCommunicator(grpcClient);
		this.stateRepository = new ElectionStateRepository(cleanupExecutor);
		this.algorithmProcessor = new RingAlgorithmProcessor(powerPlant, this.communicator,
				this::handleMyInitiatedElectionCompletion);
	}

	/**
	 * Starts an active election for a new energy request.
	 * Synchronizes on election state to prevent concurrent initiation.
	 */
	public void startActiveElection(EnergyRequest energyRequest) {
		final int selfId = powerPlant.getSelfInfo().plantId();
//		if (selfId == 1 || selfId == 2) {
//			performTestSleep();
//		}
		final String requestId = energyRequest.requestID();

		ElectionState state = stateRepository.get(requestId);
		if (state == null) {
			final double price = powerPlant.generatePrice();
			state = stateRepository.getOrCreate(requestId, energyRequest, price);
		}

		synchronized (state.getStateLock()) {
			if (state.isParticipant()) {
				logger.debug("Aborting election initiation for {}: already a participant.", requestId);
				return;
			}

			state.becomeParticipant();
			logger.info("Plant {} generated price ${} for request {} and is INITIATING ELECTION.",
					selfId, String.format("%.2f", state.getMyBid()), requestId);
			algorithmProcessor.initiate(state);
		}
	}

	/**
	 * Starts an election for a dequeued energy request when the plant becomes available.
	 * Generates a new price and initiates election if not already participating.
	 */
	public void startElectionForDequeuedRequest(EnergyRequest energyRequest) {
		final int selfId = powerPlant.getSelfInfo().plantId();
		final String requestId = energyRequest.requestID();

		ElectionState state = stateRepository.get(requestId);
		if (state != null && state.isWinnerAnnounced()) {
			logger.info("Not starting election for dequeued request {}, a winner was already decided.", requestId);
			return;
		}

		final double price = powerPlant.generatePrice();
		state = stateRepository.getOrCreate(requestId, energyRequest, price);
		state.updateMyBid(price);

		synchronized (state.getStateLock()) {
			if (state.isParticipant()) {
				logger.debug("Aborting dequeued election for {}: already a participant.", requestId);
				return;
			}
			state.becomeParticipant();
			logger.info("Plant {} (now free) generated price ${} for dequeued request {} and is INITIATING ELECTION.",
					selfId, String.format("%.2f", price), requestId);
			algorithmProcessor.initiate(state);
		}
	}

	/**
	 * Handles incoming election tokens from other plants.
	 * Determines whether to complete election (if initiator), forward without participating (if busy),
	 * or participate in the election by comparing bids.
	 */
	public void handleElectionToken(ElectCoordinatorToken token) {
		final int selfId = powerPlant.getSelfInfo().plantId();
		final String requestId = token.getEnergyRequestId();

		if (selfId == token.getInitiatorId()) {
			ElectionState state = stateRepository.get(requestId);
			if (state != null) {
				algorithmProcessor.complete(state, token);
			}
			return;
		}

		if (powerPlant.isBusy()) {
			logger.info("Plant {} is busy, forwarding election token {} without participating.",
					selfId, requestId);
			PowerPlantInfo nextPlant = powerPlant.getNextPlantInRing(selfId);
			if (nextPlant != null) communicator.forwardToken(nextPlant, token);
			return;
		}

		ElectionState state = stateRepository.getOrCreateFromToken(
				requestId, token.getEnergyAmountKwh(), powerPlant.generatePrice()
		);

		synchronized (state.getStateLock()) {
			if (state.isWinnerAnnounced()) {
				logger.debug("Plant {} dropping token for request {}, winner already announced.",
						selfId, requestId);
				return;
			}

			double myPrice = state.getMyBid();
			double tokenPrice = token.getBestBid().getPrice();
			boolean amIStronger = myPrice < tokenPrice ||
					(myPrice == tokenPrice && selfId > token.getBestBid().getPlantId());

			if (!state.isParticipant()) {
				state.becomeParticipant();
				if (amIStronger) {
					logger.info("Late Joiner: Plant {} (bid ${}) is stronger than token (bid ${}). Starting my own election.",
							selfId, String.format("%.2f", myPrice), String.format("%.2f", tokenPrice));
					algorithmProcessor.initiate(state);
				} else {
					logger.info("Late Joiner: Plant {} (bid ${}) is yielding to token (bid ${}). Forwarding.",
							selfId, String.format("%.2f", myPrice), String.format("%.2f", tokenPrice));
					algorithmProcessor.forward(state, token);
				}
			} else {
				if (amIStronger) {
					logger.info("Participant: Plant {} (bid ${}) DISCARDING weaker incoming token (bid ${}).",
							selfId, String.format("%.2f", myPrice), String.format("%.2f", tokenPrice));
				} else {
					state.updateBestBid(token.getBestBid());
					logger.info("Participant: Plant {} (bid ${}) is yielding to and forwarding stronger token (bid ${}).",
							selfId, String.format("%.2f", myPrice), String.format("%.2f", tokenPrice));
					algorithmProcessor.forward(state, token);
				}
			}
		}
	}

	/**
	 * Handles completion of elections that this plant initiated.
	 * Processes the winner announcement and forwards it to continue circulation.
	 */
	private void handleMyInitiatedElectionCompletion(EnergyWinnerAnnouncement announcement) {
		final int selfId = powerPlant.getSelfInfo().plantId();
		final String requestId = announcement.getEnergyRequestId();
		final int winnerId = announcement.getWinningPlantId();

		ElectionState state = stateRepository.get(requestId);
		if (state == null) {
			logger.warn("Processing completion for request {}, but state is missing.", requestId);
			return;
		}

		logger.info("Election for request {} that I initiated has concluded. Final Winner is Plant {}.",
				requestId, winnerId);

		if (winnerId == selfId) {
			if (!state.isValidBid()) {
				logger.error("VICTORY-ERROR! This plant ({}) won election for {} but with an invalid bid of ${}! Aborting fulfillment.",
						selfId, requestId, state.getMyBid());
			} else {
				logger.info("VICTORY! This plant ({}) won the election for request {}. Fulfilling energy request.",
						selfId, requestId);
				powerPlant.fulfillEnergyRequest(state.getRequest(), announcement.getWinningPrice());
			}
		} else {
			logger.info("This plant ({}) lost the election it initiated for request {}. The winner was Plant {}.",
					selfId, requestId, winnerId);
		}

		PowerPlantInfo nextPlant = powerPlant.getNextPlantInRing(selfId);
		if (nextPlant != null && nextPlant.plantId() != selfId) {
			communicator.forwardWinnerAnnouncement(nextPlant, announcement);
		}

		stateRepository.scheduleCleanup(requestId);
	}

	/**
	 * Processes winner announcements received from other plants.
	 * Handles acknowledgment, request queue cleanup, and continues circulation to next plant.
	 */
	public void processEnergyWinnerAnnouncement(EnergyWinnerAnnouncement announcement) {
		final int selfId = powerPlant.getSelfInfo().plantId();
		final String requestId = announcement.getEnergyRequestId();
		final int announcementInitiatorId = announcement.getInitiatorId();

		if (selfId == announcementInitiatorId) {
			logger.info("Winner announcement for request {} completed its circulation back to initiator {}. Halting propagation.",
					requestId, selfId);
			return;
		}

		ElectionState state = stateRepository.get(requestId);
		if (state == null) {
			logger.info("Plant {} acknowledges winner {} for request {} (was not an active participant).",
					selfId, announcement.getWinningPlantId(), requestId);
			powerPlant.removeRequestFromQueue(requestId);
		} else {
			if (state.trySetWinnerAnnounced()) {
				logger.info("Plant {} acknowledges winner {} for request {} with price ${}.",
						selfId, announcement.getWinningPlantId(), requestId,
						String.format("%.2f", announcement.getWinningPrice()));
				if (announcement.getWinningPlantId() != selfId) {
					powerPlant.removeRequestFromQueue(requestId);
				}
				stateRepository.scheduleCleanup(requestId);
			} else {
				logger.debug("Plant {} already processed winner for request {}. Ignoring duplicate announcement.",
						selfId, requestId);
			}
		}

		PowerPlantInfo nextPlant = powerPlant.getNextPlantInRing(selfId);
		if (nextPlant != null) {
			communicator.forwardWinnerAnnouncement(nextPlant, announcement);
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