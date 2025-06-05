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

public class ElectionManager {
	private static final Logger logger = LoggerFactory.getLogger(ElectionManager.class);

	// Core components
	private final PowerPlant powerPlant;
	private final PlantGrpcClient grpcClient;

	// State management: Using HashMap, access MUST be synchronized.
	private final Map<String, ElectionState> elections = new HashMap<>();
	// A dedicated lock object for managing access to the 'elections' map.
	private final Object electionsLock = new Object();

	public ElectionManager(PowerPlant powerPlant, PlantGrpcClient grpcClient) {
		this.powerPlant = powerPlant;
		this.grpcClient = grpcClient;
	}

	/**
	 * Encapsulates election state. Uses volatile and a dedicated lock
	 * for thread-safe access without concurrent collections.
	 */
	private static class ElectionState {
		final EnergyRequest request;
		final double myBid;

		// Volatile ensures visibility of the reference across threads.
		// Updates must be synchronized to ensure atomicity.
		private volatile Bid bestBidSeen;
		// Volatile ensures visibility; updates must be synchronized.
		private volatile boolean winnerAnnounced = false;
		// Lock for managing updates to bestBidSeen and winnerAnnounced together.
		private final Object stateLock = new Object();

		ElectionState(EnergyRequest request, double myBid) {
			this.request = request;
			this.myBid = myBid;
			this.bestBidSeen = Bid.newBuilder()
					.setPlantId("")
					.setPrice(Double.MAX_VALUE)
					.build();
		}

		// Updates the best bid if the new one is better.
		// Synchronized to ensure atomic read-compare-write.
		boolean updateBestBid(Bid newBid) {
			// Quick, non-locking check for a potential quick exit.
			// It's okay if this reads slightly stale data,
			// as the synchronized block performs the definitive check.
			if (!isBetterBid(newBid, this.bestBidSeen)) {
				return false;
			}

			synchronized (stateLock) {
				if (isBetterBid(newBid, this.bestBidSeen)) {
					this.bestBidSeen = newBid;
					return true;
				}
				return false;
			}
		}

		// Gets the current best bid. Volatile ensures we see the latest write.
		Bid getBestBid() {
			return this.bestBidSeen;
		}

		// Checks if winner is announced. Volatile ensures we see the latest write.
		boolean isWinnerAnnounced() {
			return this.winnerAnnounced;
		}

		// Sets winner announced flag. Returns true if this call set it, false otherwise.
		// Synchronized to ensure atomic check-and-set.
		boolean setWinnerAnnounced() {
			synchronized (stateLock) {
				if (this.winnerAnnounced) {
					return false; // Already announced.
				}
				this.winnerAnnounced = true;
				return true; // We were the one to set it.
			}
		}

		// Helper: Compares bids. Does not need synchronization as it's pure logic.
		private boolean isBetterBid(Bid candidate, Bid current) {
			if (candidate == null) return false;
			if (current == null || current.getPrice() == Double.MAX_VALUE) return true;

			if (candidate.getPrice() < current.getPrice()) {
				return true;
			}
			return candidate.getPrice() == current.getPrice() &&
					candidate.getPlantId().compareTo(current.getPlantId()) < 0;
		}
	}

	public void initiateElection(EnergyRequest energyRequest, double price) {
		String requestId = energyRequest.getRequestID();
		String selfId = powerPlant.getSelfInfo().getPlantId();

		ElectionState state = getOrCreateElectionState(requestId, energyRequest, price);

		Bid initialBid = Bid.newBuilder()
				.setPlantId(selfId)
				.setPrice(price)
				.build();

		state.updateBestBid(initialBid);

		ElectCoordinatorToken token = ElectCoordinatorToken.newBuilder()
				.setInitiatorId(selfId)
				.setEnergyRequestId(requestId)
				.setBestBid(state.getBestBid())
				.build();

		PowerPlantInfo nextPlant = powerPlant.getNextPlantInRing(selfId);
		logger.info("Plant {} initiating election for request {}, forwarding to {}",
				selfId, requestId, nextPlant.getPlantId());

		grpcClient.forwardElectionToken(nextPlant, token);
	}

//	public void handleElectionToken(ElectCoordinatorToken token) {
//		String selfId = powerPlant.getSelfInfo().getPlantId();
//		String initiatorId = token.getInitiatorId();
//		String requestId = token.getEnergyRequestId();
//
//		ElectionState state = getElectionState(requestId);
//		if (state == null) {
//			logger.warn("Plant {} received token for unknown or cleaned-up request {}. Ignoring.", selfId, requestId);
//			return;
//		}
//
//		// Rely on volatile read for a quick check.
//		if (state.isWinnerAnnounced()) {
//			logger.debug("Plant {} ignoring token for request {}, winner already announced.", selfId, requestId);
//			return;
//		}
//
//		state.updateBestBid(token.getBestBid());
//
//		if (selfId.equals(initiatorId)) {
//			completeElectionRound(requestId, state);
//			return;
//		}
//
//		Bid forwardBid = state.getBestBid();
//
//		if (!powerPlant.isBusy()) {
//			Bid myBid = Bid.newBuilder()
//					.setPlantId(selfId)
//					.setPrice(state.myBid)
//					.build();
//
//			if (state.updateBestBid(myBid)) {
//				forwardBid = myBid; // Our bid was better, update forwardBid
//				logger.debug("Plant {} inserting its better bid ${} for request {}",
//						selfId, state.myBid, requestId);
//			}
//		}
//
//		ElectCoordinatorToken forwardToken = ElectCoordinatorToken.newBuilder(token)
//				.setBestBid(forwardBid)
//				.build();
//
//		PowerPlantInfo nextPlant = powerPlant.getNextPlantInRing(selfId);
//		logger.debug("Plant {} forwarding token for ER {} to {} (Initiator: {}, Best: {} at ${})",
//				selfId, requestId, nextPlant.getPlantId(), initiatorId, forwardBid.getPlantId(), forwardBid.getPrice());
//		grpcClient.forwardElectionToken(nextPlant, forwardToken);
//	}

	public void handleElectionToken(ElectCoordinatorToken token) {
		String selfId = powerPlant.getSelfInfo().getPlantId();
		String initiatorId = token.getInitiatorId();
		String requestId = token.getEnergyRequestId();

		ElectionState state = getElectionState(requestId);

		if (state == null) {
			// This plant has no local state for this election.
			// This could mean:
			// 1. It was busy when the energy request MQTT message arrived and thus didn't initiate/create state.
			// 2. It hasn't processed the MQTT message for this requestID yet (token arrived faster).
			// 3. The election was completed, and its state was cleaned up.
			// In cases 1 and 2 (most relevant to the problem), it MUST forward the token.

			if (selfId.equals(initiatorId)) {
				// This plant is the INITIATOR of the token, but its own election state is missing.
				// This is a problematic state. It cannot properly complete its initiated election round.
				// Forwarding might lead to loops if the state is never restored.
				// Not forwarding means this initiator's election attempt effectively fails here.
				logger.error("CRITICAL: Plant {} (initiator) received its token for request {} but has NO local election state. " +
								"Cannot complete election. Token will NOT be forwarded further by this initiator to prevent potential loops and election failure.",
						selfId, requestId);
				// No further action for this token by this initiator with lost state.
			} else {
				// This plant is NOT the initiator and has no local state (e.g., was busy, or MQTT not yet processed for this plant).
				// It must forward the token to ensure the election started by 'initiatorId' can continue.
				PowerPlantInfo nextPlant = powerPlant.getNextPlantInRing(selfId);
				logger.info("Plant {} (not initiator, no local state for request {}) is forwarding token from initiator {} to {}. Current best bid in token: {}@${}.",
						selfId, requestId, initiatorId, nextPlant.getPlantId(),
						token.getBestBid().getPlantId(), token.getBestBid().getPrice());
				grpcClient.forwardElectionToken(nextPlant, token); // Forward the token as received.
			}
			return; // Finished processing token for this plant (which had no local state for this election).
		}

		// If state is not null, proceed with the existing logic:
		if (state.isWinnerAnnounced()) { // Volatile read check
			logger.debug("Plant {} ignoring token for request {}, winner already announced.", selfId, requestId);
			return;
		}

		state.updateBestBid(token.getBestBid()); // Update with bid from token

		if (selfId.equals(initiatorId)) {
			// I am the initiator, token returned. State exists.
			completeElectionRound(requestId, state);
			return;
		}

		// Not the initiator, but I have state (meaning I did initiate or process this request earlier and I'm not busy for bidding now, or became available).
		Bid forwardBid = state.getBestBid(); // Start with the current best (which includes token's bid)

		if (!powerPlant.isBusy()) { // Check if I can bid *now*
			// Create my bid based on the price stored in my ElectionState
			Bid myBid = Bid.newBuilder()
					.setPlantId(selfId)
					.setPrice(state.myBid) // myBid was set when ElectionState was created
					.build();

			if (state.updateBestBid(myBid)) { // If my bid is better
				forwardBid = myBid; // Update the bid to be forwarded
				logger.debug("Plant {} inserting its better bid ${} for request {}",
						selfId, state.myBid, requestId);
			}
		} else {
			logger.debug("Plant {} is busy, not inserting its own bid for request {}. Forwarding current best from token.", selfId, requestId);
		}

		ElectCoordinatorToken forwardToken = ElectCoordinatorToken.newBuilder(token) // Create new token based on incoming
				.setBestBid(forwardBid) // Set the potentially updated best bid
				.build();

		PowerPlantInfo nextPlant = powerPlant.getNextPlantInRing(selfId);
		logger.debug("Plant {} forwarding token for ER {} to {} (Initiator: {}, Best: {} at ${})",
				selfId, requestId, nextPlant.getPlantId(), initiatorId, forwardBid.getPlantId(), forwardBid.getPrice());
		grpcClient.forwardElectionToken(nextPlant, forwardToken);
	}

	private void completeElectionRound(String requestId, ElectionState state) {
		String selfId = powerPlant.getSelfInfo().getPlantId();
		Bid currentBestBid = state.getBestBid();

		logger.info("Plant {} completed its election round for request {}. Best bid seen: {} at ${}",
				selfId, requestId, currentBestBid.getPlantId(), currentBestBid.getPrice());

		if (selfId.equals(currentBestBid.getPlantId())) {
			if (state.setWinnerAnnounced()) {
				logger.info("Plant {} identified itself as the winner for request {}. Initiating fulfillment and announcing...",
						selfId, requestId);
				powerPlant.fulfillEnergyRequest(state.request, currentBestBid.getPrice());
				announceWinner(requestId, currentBestBid.getPlantId(), currentBestBid.getPrice());
			} else {
				logger.debug("Plant {} identified as winner, but announcement already made/in_progress.", selfId);
			}
		} else {
			logger.debug("Plant {} completed its round, but {} is the winner. Not announcing.",
					selfId, currentBestBid.getPlantId());
		}
	}

	private void announceWinner(String requestId, String winnerId, double winnerPrice) {
		EnergyWinnerAnnouncement announcement = EnergyWinnerAnnouncement.newBuilder()
				.setWinningPlantId(winnerId)
				.setWinningPrice(winnerPrice)
				.setEnergyRequestId(requestId)
				.build();

		for (PowerPlantInfo plant : powerPlant.getOtherPlants()) {
			if (!plant.getPlantId().equals(powerPlant.getSelfInfo().getPlantId())) {
				logger.info("Announcing Energy Winner {} for ER {} to {}", winnerId, requestId, plant.getPlantId());
				grpcClient.announceEnergyWinner(plant, announcement);
			}
		}

		processEnergyWinnerAnnouncement(announcement);
	}

	public void processEnergyWinnerAnnouncement(EnergyWinnerAnnouncement announcement) {
		String requestId = announcement.getEnergyRequestId();
		String winnerId = announcement.getWinningPlantId();
		double winnerPrice = announcement.getWinningPrice();
		String selfId = powerPlant.getSelfInfo().getPlantId();

		ElectionState state = getElectionState(requestId);
		if (state == null) {
			logger.debug("Plant {} ignoring winner announcement for request {} (cleaned up).", selfId, requestId);
			return;
		}

		// Try to set winner announced. If we succeed, we process.
		// If it was already set, we log a debug message and stop.
		if (state.setWinnerAnnounced()) {
			logger.info("Plant {} acknowledges winner {} for request {} at ${}",
					selfId, winnerId, requestId, winnerPrice);

			if (winnerId.equals(selfId)) {
				powerPlant.fulfillEnergyRequest(state.request, winnerPrice);
			}
			scheduleCleanup(requestId);
		} else {
			logger.debug("Plant {} ignoring duplicate winner announcement for request {}", selfId, requestId);
		}
	}

	// Helper methods using 'synchronized (electionsLock)' for map access.
	private ElectionState getOrCreateElectionState(String requestId, EnergyRequest request, double price) {
		synchronized (electionsLock) {
			return elections.computeIfAbsent(requestId, k -> new ElectionState(request, price));
		}
	}

	private ElectionState getElectionState(String requestId) {
		synchronized (electionsLock) {
			return elections.get(requestId);
		}
	}

	/**
	 * Schedules cleanup using a simple new Thread with a sleep.
	 * This is simple but less efficient than a thread pool or scheduler.
	 */
	private void scheduleCleanup(String requestId) {
		new Thread(() -> {
			try {
				// Wait a few seconds to allow any in-flight messages to arrive.
				Thread.sleep(5000);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt(); // Restore interrupt status
				logger.warn("Cleanup thread for {} interrupted.", requestId);
			} finally {
				// Ensure removal happens within the lock.
				synchronized (electionsLock) {
					elections.remove(requestId);
				}
				logger.debug("Cleaned up election state for request {}", requestId);
			}
		}, "Cleanup-" + requestId.substring(0, 6)).start(); // Give the thread a name
	}

	// No shutdown method needed as we are not using a managed executor.
	// However, be aware these cleanup threads might run briefly after main stops
	// unless the application forces exit.
}