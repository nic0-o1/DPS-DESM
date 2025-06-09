package desm.dps.election;

import desm.dps.EnergyRequest;
import desm.dps.PowerPlant;
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
 * A facade for the election system. It orchestrates the election process by delegating
 * tasks to specialized internal components, keeping this public-facing class clean and simple.
 */
public class ElectionManager {
	private static final Logger logger = LoggerFactory.getLogger(ElectionManager.class);
	private static final double INVALID_BID_PRICE = -1.0;

	private final PowerPlant powerPlant;
	private final ElectionStateRepository stateRepository;
	private final RingAlgorithmProcessor algorithmProcessor;
	private final ScheduledExecutorService cleanupExecutor;

	public ElectionManager(PowerPlant powerPlant, PlantGrpcClient grpcClient) {
		this.powerPlant = powerPlant;
		this.cleanupExecutor = Executors.newScheduledThreadPool(1, r -> new Thread(r, "ElectionCleanup"));
		ElectionCommunicator communicator = new ElectionCommunicator(powerPlant, grpcClient);
		this.stateRepository = new ElectionStateRepository(cleanupExecutor);
		this.algorithmProcessor = new RingAlgorithmProcessor(powerPlant, communicator);
	}

	/**
	 * Main entry point to process a new energy request.
	 */
	public void processNewEnergyRequest(EnergyRequest energyRequest) {
		String selfId = powerPlant.getSelfInfo().plantId();
		double price = powerPlant.isBusy() ? INVALID_BID_PRICE : powerPlant.generatePrice();
		ElectionState state = stateRepository.getOrCreate(energyRequest.requestID(), energyRequest, price);

		if (!state.isValidBid()) {
			logger.info("Plant {} is BUSY, participating passively in election for request {}", selfId, energyRequest.requestID());
			return;
		}
		if (state.trySetInitiated()) {
			logger.info("Plant {} generated price ${} for request {}", selfId, price, energyRequest.requestID());
			algorithmProcessor.initiate(state);
		} else {
			logger.info("Plant {} received duplicate MQTT request for {}", selfId, energyRequest.requestID());
		}
	}

	/**
	 * Handles an incoming election token from the network.
	 */
	public void handleElectionToken(ElectCoordinatorToken token) {
		double price = powerPlant.isBusy() ? INVALID_BID_PRICE : powerPlant.generatePrice();
		EnergyRequest request = new EnergyRequest(token.getEnergyRequestId(), (int) token.getEnergyAmountKwh(), System.currentTimeMillis());
		ElectionState state = stateRepository.getOrCreate(token.getEnergyRequestId(), request, price);
		state.trySetInitiated();

		if (state.isWinnerAnnounced()) {
			logger.debug("Plant {} dropping token for request {}, winner already announced", powerPlant.getSelfInfo().plantId(), token.getEnergyRequestId());
			return;
		}
		if (powerPlant.getSelfInfo().plantId().equals(token.getInitiatorId())) {
			if (algorithmProcessor.complete(state, token)) {
				stateRepository.scheduleCleanup(token.getEnergyRequestId());
			}
		} else {
			algorithmProcessor.forward(state, token);
		}
	}

	/**
	 * Processes a winner announcement received from another plant.
	 * This logic is now identical to the original working implementation.
	 */
	public void processEnergyWinnerAnnouncement(EnergyWinnerAnnouncement announcement) {
		String selfId = powerPlant.getSelfInfo().plantId();
		String requestId = announcement.getEnergyRequestId();
		String winnerId = announcement.getWinningPlantId();

		ElectionState state = stateRepository.get(requestId);
		if (state == null) {
			logger.debug("Plant {} received winner announcement for unknown request {}", selfId, requestId);
			return;
		}
		if (state.trySetWinnerAnnounced()) {
			logger.info("Plant {} acknowledges winner {} for request {} at ${}", selfId, winnerId, requestId, announcement.getWinningPrice());
			if (winnerId.equals(selfId)) {
				logger.info("Plant {} is the winner! Fulfilling request {}", selfId, requestId);
				powerPlant.fulfillEnergyRequest(state.getRequest(), announcement.getWinningPrice());
			}
			stateRepository.scheduleCleanup(requestId);
		}
	}


}