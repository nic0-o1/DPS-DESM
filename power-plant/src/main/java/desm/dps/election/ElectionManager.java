package desm.dps.election;

import desm.dps.EnergyRequest;
import desm.dps.PowerPlant;
import desm.dps.PowerPlantInfo;
import desm.dps.election.internal.ElectionCommunicator;
import desm.dps.election.internal.ElectionStateRepository;
import desm.dps.election.internal.RingAlgorithmProcessor;
import desm.dps.election.model.BidComparator;
import desm.dps.election.model.ElectionState;
import desm.dps.grpc.Bid;
import desm.dps.grpc.ElectCoordinatorToken;
import desm.dps.grpc.EnergyWinnerAnnouncement;
import desm.dps.grpc.PlantGrpcClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Acts as the central coordinator for all election processes within a power plant.
 *
 * This manager's responsibilities include:
 * - Initiating elections for new and dequeued energy requests.
 * - Handling incoming election tokens according to the ring algorithm's rules.
 * - Processing winner announcements, whether initiated by this plant or others.
 * - Coordinating with subsystems like {@link ElectionStateRepository} and {@link RingAlgorithmProcessor}.
 */
public class ElectionManager {
	private static final Logger logger = LoggerFactory.getLogger(ElectionManager.class);

	// A utility constant for testing purposes only.
	private static final int TEST_SLEEP_DURATION_MS = 22000;

	private final PowerPlant powerPlant;
	private final ElectionStateRepository stateRepository;
	private final RingAlgorithmProcessor algorithmProcessor;
	private final ElectionCommunicator communicator;

	/**
	 * Constructs an ElectionManager, initializing all its internal components.
	 *
	 * @param powerPlant The main PowerPlant facade, used for context like self ID and busy state.
	 * @param grpcClient The gRPC client used for all inter-plant communication.
	 */
	public ElectionManager(PowerPlant powerPlant, PlantGrpcClient grpcClient) {
		this.powerPlant = powerPlant;
		ScheduledExecutorService cleanupExecutor = Executors.newScheduledThreadPool(1,
				r -> new Thread(r, "ElectionCleanup"));
		this.communicator = new ElectionCommunicator(grpcClient);
		this.stateRepository = new ElectionStateRepository(cleanupExecutor);
		// The local completion handler is passed as a method reference.
		this.algorithmProcessor = new RingAlgorithmProcessor(powerPlant, this.communicator,
				this::handleMyInitiatedElectionCompletion);
	}

	/**
	 * Starts an election for a new energy request, assuming the plant is currently idle.
	 *
	 * This method synchronizes on the specific election's state object to ensure that
	 * only one thread can initiate participation for a given request.
	 *
	 * @param energyRequest The new energy request to process.
	 */
	public void startActiveElection(EnergyRequest energyRequest) {
//		performTestSleep();
		final int selfId = powerPlant.getSelfInfo().plantId();
		final String requestId = energyRequest.requestID();

		// Get or create the state for this election.
		ElectionState state = stateRepository.getOrCreate(requestId, energyRequest, powerPlant.generatePrice());

		synchronized (state.getStateLock()) {
			if (state.isParticipant()) {
				logger.debug("Aborting election initiation for ER {}: already a participant.", requestId);
				return;
			}
			state.becomeParticipant();
			logger.info("Plant {} generated price ${} for request {} and is INITIATING ELECTION.",
					selfId, String.format("%.2f", state.getMyBid()), requestId);
			algorithmProcessor.initiate(state);
		}
	}

	/**
	 * Starts an election for a previously queued energy request, typically after the plant becomes idle.
	 *
	 * This method generates a fresh price for the bid, as market conditions may have changed
	 * while the request was in the queue.
	 *
	 * @param energyRequest The dequeued energy request to process.
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
		// Get or create the state, which also updates the bid price if the state already exists.
		state = stateRepository.getOrCreate(requestId, energyRequest, price);
		state.updateMyBid(price);

		synchronized (state.getStateLock()) {
			if (state.isParticipant()) {
				logger.debug("Aborting dequeued election for ER {}: already a participant.", requestId);
				return;
			}
			state.becomeParticipant();
			logger.info("Plant {} (now free) generated price ${} for dequeued request {} and is INITIATING ELECTION.",
					selfId, String.format("%.2f", state.getMyBid()), requestId);
			algorithmProcessor.initiate(state);
		}
	}

	/**
	 * Handles an incoming election token from another plant. The logic follows a strict hierarchy:
	 * 1. If this plant is the initiator of the token, complete the election.
	 * 2. If this plant is busy, forward the token without participating.
	 * 3. If a winner for this request is already known, drop the token as it is obsolete.
	 * 4. Compare this plant's bid with the token's bid and decide whether to initiate a
	 *    new election, forward the token, or discard it based on bid strength and
	 *    participation status.
	 *
	 * @param token The election token received from another plant.
	 */
	public void handleElectionToken(ElectCoordinatorToken token) {
		final int selfId = powerPlant.getSelfInfo().plantId();
		final String requestId = token.getEnergyRequestId();

		// 1. If the token has returned to its initiator, the election is over.
		if (selfId == token.getInitiatorId()) {
			ElectionState state = stateRepository.get(requestId);
			if (state != null) {
				algorithmProcessor.complete(state, token);
			} else {
				logger.warn("Received my own token for ER {}, but state is missing. Cannot complete election.", requestId);
			}
			return;
		}

		// 2. If this plant is busy, it cannot participate. Forward the token passively.
		if (powerPlant.isBusy()) {
			logger.info("Plant {} is busy, forwarding election token for ER {} without participating.", selfId, requestId);
			PowerPlantInfo nextPlant = powerPlant.getNextPlantInRing(selfId);
			if (nextPlant != null) {
				communicator.forwardToken(nextPlant, token);
			}
			return;
		}

		// Get or create the election state. If we are a late joiner, a new bid is generated.
		ElectionState state = stateRepository.getOrCreateFromToken(
				requestId, token.getEnergyAmountKwh(), powerPlant.generatePrice()
		);

		synchronized (state.getStateLock()) {
			// 3. If a winner has already been announced, this token is obsolete. Drop it.
			if (state.isWinnerAnnounced()) {
				logger.debug("Dropping token for ER {}, a winner was already announced.", requestId);
				return;
			}

			// 4. Compare bids to make a decision using the centralized comparator.
			Bid myBid = Bid.newBuilder().setPlantId(selfId).setPrice(state.getMyBid()).build();
			boolean amIStronger = BidComparator.isBetter(myBid, token.getBestBid());

			if (state.isParticipant()) {
				// -- This plant is already an active participant in an election for this request --
				if (amIStronger) {
					logger.info("Participant: Plant {} (bid ${}) is stronger. DISCARDING weaker incoming token from initiator {}.",
							selfId, String.format("%.2f", myBid.getPrice()), token.getInitiatorId());
				} else {
					logger.info("Participant: Plant {} (bid ${}) is weaker. FORWARDING stronger token from initiator {}.",
							selfId, String.format("%.2f", myBid.getPrice()), token.getInitiatorId());
					algorithmProcessor.forward(state, token); // Adopt and forward the better token.
				}
			} else {
				// -- This plant is a "late joiner" to the election --
				state.becomeParticipant();
				if (amIStronger) {
					logger.info("Late Joiner: Plant {} (bid ${}) is stronger. INITIATING own election and discarding incoming token.",
							selfId, String.format("%.2f", myBid.getPrice()));
					algorithmProcessor.initiate(state); // Start our own election round.
				} else {
					logger.info("Late Joiner: Plant {} (bid ${}) is weaker. FORWARDING incoming token.",
							selfId, String.format("%.2f", myBid.getPrice()));
					algorithmProcessor.forward(state, token); // Join the current election round.
				}
			}
		}
	}

	/**
	 * A callback handler for processing the completion of an election that was initiated by this plant.
	 *
	 * This method determines if this plant won or lost, takes appropriate action (like fulfilling
	 * the request), and then forwards the winner announcement to begin its circulation around the ring.
	 *
	 * @param announcement The final winner announcement for the election.
	 */
	private void handleMyInitiatedElectionCompletion(EnergyWinnerAnnouncement announcement) {
		final int selfId = powerPlant.getSelfInfo().plantId();
		final String requestId = announcement.getEnergyRequestId();
		final int winnerId = announcement.getWinningPlantId();

		ElectionState state = stateRepository.get(requestId);
		if (state == null) {
			logger.warn("Processing completion for ER {}, but its state is missing.", requestId);
			return;
		}

		logger.info("Election for ER {} (initiated by this plant) has concluded. Final Winner is Plant {}.", requestId, winnerId);

		if (winnerId == selfId) {
			if (!state.isValidBid()) {
				logger.error("VICTORY-ERROR! Plant {} won election for {} but with an invalid bid of ${}. Aborting fulfillment.",
						selfId, requestId, state.getMyBid());
			} else {
				logger.info("VICTORY! This plant ({}) won the election for request {}. Fulfilling energy request.", selfId, requestId);
				powerPlant.fulfillEnergyRequest(state.getRequest(), announcement.getWinningPrice());
			}
		} else {
			logger.info("This plant ({}) lost the election it initiated for ER {}. The winner was Plant {}.", selfId, requestId, winnerId);
		}

		// Forward the announcement to the next plant to begin its circulation.
		PowerPlantInfo nextPlant = powerPlant.getNextPlantInRing(selfId);
		if (nextPlant != null && nextPlant.plantId() != selfId) {
			communicator.forwardWinnerAnnouncement(nextPlant, announcement);
		}

		stateRepository.scheduleCleanup(requestId);
	}

	/**
	 * Processes a winner announcement that is circulating the ring and was initiated by another plant.
	 *
	 * This plant acknowledges the winner, cleans up any local state related to the request (e.g.,
	 * removing it from the pending queue), and forwards the announcement to the next plant in the ring.
	 *
	 * @param announcement The winner announcement received from another plant.
	 */
	public void processEnergyWinnerAnnouncement(EnergyWinnerAnnouncement announcement) {
		final int selfId = powerPlant.getSelfInfo().plantId();
		final String requestId = announcement.getEnergyRequestId();
		final int announcementInitiatorId = announcement.getInitiatorId();

		// If the announcement has made a full circle back to its initiator, stop forwarding it.
		if (selfId == announcementInitiatorId) {
			logger.info("Winner announcement for ER {} completed its circulation back to initiator {}. Halting propagation.",
					requestId, selfId);
			return;
		}

		ElectionState state = stateRepository.get(requestId);
		if (state == null) {
			// This plant was not actively participating but may have the request queued.
			logger.info("Plant {} acknowledges winner {} for ER {} (was not an active participant).",
					selfId, announcement.getWinningPlantId(), requestId);
			powerPlant.removeRequestFromQueue(requestId);
		} else {
			if (state.trySetWinnerAnnounced()) {
				logger.info("Plant {} acknowledges winner {} for ER {} with price ${}.",
						selfId, announcement.getWinningPlantId(), requestId, String.format("%.2f", announcement.getWinningPrice()));
				if (announcement.getWinningPlantId() != selfId) {
					powerPlant.removeRequestFromQueue(requestId);
				}
				stateRepository.scheduleCleanup(requestId);
			} else {
				logger.debug("Plant {} already processed winner for ER {}. Ignoring duplicate announcement.", selfId, requestId);
			}
		}

		// Continue circulating the announcement to the next plant.
		PowerPlantInfo nextPlant = powerPlant.getNextPlantInRing(selfId);
		if (nextPlant != null) {
			communicator.forwardWinnerAnnouncement(nextPlant, announcement);
		}
	}

	/**
	 * Pauses the current thread for a fixed duration.
	 *
	 * NOTE: This method is for testing purposes only to simulate network delays or
	 * processing race conditions and should not be used in production code.
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