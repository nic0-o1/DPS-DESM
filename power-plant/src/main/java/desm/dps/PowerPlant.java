package desm.dps;

import desm.dps.election.ElectionManager;
import desm.dps.grpc.PlantGrpcClient;
import desm.dps.grpc.PlantGrpcService;
import desm.dps.mqtt.EnergyRequestSubscriber;
import desm.dps.mqtt.PollutionDataPublisher;
import desm.dps.rest.AdminServerClient;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;

public class PowerPlant {

    private static final Logger logger = LoggerFactory.getLogger(PowerPlant.class);
    private static final double MIN_PRICE = 0.1;
    private static final double MAX_PRICE = 0.9;
    private static final int POLLUTION_PUBLISH_INTERVAL_MS = 10_000;
    private static final int THREAD_JOIN_TIMEOUT_MS = 2_000;


    // Immutable state
    private final PowerPlantInfo selfInfo;
    private final String mqttBrokerUrl;
    private final String energyRequestTopic;
    private final String pollutionDataTopic;
    private final AdminServerClient adminClient;
    private final PlantGrpcClient grpcClient;
    private final ElectionManager electionManager;
    private final Random random = new Random();

    // Mutable state with proper synchronization
    private volatile EnergyRequestSubscriber energyRequestSubscriber;
    private volatile Server grpcServer;
    private volatile boolean isBusy = false;
    private volatile boolean isShutdown = false;

    // Thread-safe plant management using manual synchronization
    private final List<PowerPlantInfo> otherPlantsList = new ArrayList<>();
    private final Object otherPlantsLock = new Object();

    // Processing state
    private volatile String currentRequestId = null;
    private final Object processingLock = new Object();

    // Sensors and pollution monitoring
    private final SensorManager sensorManager;
    private final PollutionDataPublisher pollutionDataPublisher;
    private Thread pollutionPublisherThread;
    private volatile boolean shouldPublishPollution = false;

    // Performance optimizations
    private volatile PowerPlantInfo[] cachedRingArray = null;
    private volatile long ringCacheVersion = 0;

    public PowerPlant(PowerPlantInfo selfInfo, String adminServerBaseUrl,
                      String mqttBrokerUrl, String energyRequestTopic) {
        validateConstructorParameters(selfInfo, adminServerBaseUrl, mqttBrokerUrl, energyRequestTopic);

        this.selfInfo = selfInfo;
        this.mqttBrokerUrl = mqttBrokerUrl;
        this.energyRequestTopic = energyRequestTopic;
        this.pollutionDataTopic = "desm/pollution/data";
        this.adminClient = new AdminServerClient(adminServerBaseUrl);
        this.grpcClient = new PlantGrpcClient(this);
        this.electionManager = new ElectionManager(this, grpcClient);

        // Start with empty list - no pre-allocation needed
        this.sensorManager = new SensorManager();
        this.pollutionDataPublisher = new PollutionDataPublisher(
                this.mqttBrokerUrl, this.selfInfo.getPlantId(), pollutionDataTopic
        );

        logger.info("Initialized PowerPlant with ID: {}", selfInfo.getPlantId());
    }

    private void validateConstructorParameters(PowerPlantInfo selfInfo, String adminServerBaseUrl,
                                               String mqttBrokerUrl, String energyRequestTopic) {
        if (selfInfo == null || adminServerBaseUrl == null ||
                mqttBrokerUrl == null || energyRequestTopic == null) {
            throw new IllegalArgumentException("All constructor parameters must be non-null");
        }
    }

    public void start() throws IOException, MqttException {
        validateNotShutdown();

        logger.info("Starting PowerPlant {}", selfInfo.getPlantId());

        try {
            initializeServices();
            logger.info("PowerPlant {} fully started and operational", selfInfo.getPlantId());
        } catch (Exception e) {
            shutdown();
            throw e;
        }
    }

    private void validateNotShutdown() {
        if (isShutdown) {
            throw new IllegalStateException("Cannot start a shutdown PowerPlant");
        }
    }

    private void initializeServices() throws IOException, MqttException {
        startGrpcServer();
        registerWithAdminServer();
        announcePresenceToKnownPlants();
        startMqttSubscriber();
//        startSensorServices();
//        startPollutionPublishingThread();
    }

    private void startSensorServices() throws MqttException {
        sensorManager.start();
        pollutionDataPublisher.start();
    }

    private void startPollutionPublishingThread() {
        shouldPublishPollution = true;
        pollutionPublisherThread = createPollutionPublisherThread();
        pollutionPublisherThread.start();
    }

    private Thread createPollutionPublisherThread() {
        return new Thread(this::pollutionPublishingLoop,
                selfInfo.getPlantId() + "-PollutionPublisher");
    }

    private void pollutionPublishingLoop() {
        logger.info("Pollution data publishing thread started for plant {}", selfInfo.getPlantId());

        while (shouldPublishPollution && !Thread.currentThread().isInterrupted()) {
            try {
                publishPollutionDataIfAvailable();
                Thread.sleep(POLLUTION_PUBLISH_INTERVAL_MS);
            } catch (InterruptedException e) {
                handlePollutionThreadInterruption();
                break;
            } catch (Exception e) {
                logger.error("Error in pollution data publishing for plant {}: {}",
                        selfInfo.getPlantId(), e.getMessage(), e);
            }
        }

        logger.info("Pollution data publishing thread stopped for plant {}", selfInfo.getPlantId());
    }

    private void publishPollutionDataIfAvailable() {
        List<Double> averages = sensorManager.getAndClearAverages();
        if (averages != null && !averages.isEmpty()) {
            PollutionData data = createPollutionData(averages);
            pollutionDataPublisher.publish(data);
            logger.debug("Published pollution data for plant {}", selfInfo.getPlantId());
        } else {
            logger.debug("No new pollution averages to publish for plant {}", selfInfo.getPlantId());
        }
    }

    private PollutionData createPollutionData(List<Double> averages) {
        return new PollutionData(
                selfInfo.getPlantId(),
                System.currentTimeMillis(),
                averages
        );
    }

    private void handlePollutionThreadInterruption() {
        logger.info("Pollution publishing thread interrupted for plant {}", selfInfo.getPlantId());
        Thread.currentThread().interrupt();
    }

    private void startGrpcServer() throws IOException {
        PlantGrpcService plantGrpcService = new PlantGrpcService(this, electionManager);

        this.grpcServer = ServerBuilder.forPort(selfInfo.getPort())
                .addService(plantGrpcService)
                .build()
                .start();

        logger.info("gRPC Server started for plant {} on port {}",
                selfInfo.getPlantId(), selfInfo.getPort());
    }

    private void registerWithAdminServer() {
        List<PowerPlantInfo> initialOtherPlants = adminClient.register(selfInfo);
        if (initialOtherPlants != null && !initialOtherPlants.isEmpty()) {
            addInitialPlants(initialOtherPlants);
            logger.debug("Registered with Admin Server. Known plants: {}", otherPlantsList.size());
        } else {
            logger.warn("Failed to register with Admin Server or no other plants returned for plant {}",
                    selfInfo.getPlantId());
        }
    }

    private void addInitialPlants(List<PowerPlantInfo> initialPlants) {
        synchronized (otherPlantsLock) {
            for (PowerPlantInfo plant : initialPlants) {
                if (!plant.getPlantId().equals(selfInfo.getPlantId())) {
                    addOtherPlantUnsafe(plant);
                }
            }
        }
    }

    private void startMqttSubscriber() throws MqttException {
        EnergyRequestSubscriber subscriber = new EnergyRequestSubscriber(
                mqttBrokerUrl,
                selfInfo.getPlantId() + "_subscriber",
                energyRequestTopic,
                this
        );
        subscriber.start();

        this.energyRequestSubscriber = subscriber;
        logger.info("MQTT Subscriber started for plant {} on topic {}",
                selfInfo.getPlantId(), energyRequestTopic);
    }

    private void announcePresenceToKnownPlants() {
        PowerPlantInfo[] currentPlants = getCurrentOtherPlants();
        for (PowerPlantInfo otherPlant : currentPlants) {
            if (otherPlant != null && !otherPlant.getPlantId().equals(selfInfo.getPlantId())) {
                grpcClient.announcePresence(otherPlant, selfInfo);
            }
        }
    }

    public void handleIncomingEnergyRequest(EnergyRequest energyRequest) {
        if (energyRequest == null) {
            logger.warn("Received null energy request");
            return;
        }

        logger.info("Received Energy Request {} for {} kWh",
                energyRequest.getRequestID(), energyRequest.getAmountKWh());

        // FINAL AND CORRECT: Delegate ALL processing to the ElectionManager.
        // The manager will handle busy checks, price generation, and initiation.
        electionManager.processNewEnergyRequest(energyRequest);
    }

    public double generatePrice() {
        double price = MIN_PRICE + (MAX_PRICE - MIN_PRICE) * random.nextDouble();
        return Math.round(price * 100.0) / 100.0;
//        return 0.01;
    }

    public void fulfillEnergyRequest(EnergyRequest request, double price) {
        if (request == null) {
            logger.warn("Cannot fulfill null request");
            return;
        }

        if (!trySetBusyState(request)) {
            return;
        }

        logger.info("Plant {} won bid for request {} with price ${}. Fulfilling {} kWh",
                selfInfo.getPlantId(), request.getRequestID(), price, request.getAmountKWh());

        startEnergyProductionThread(request);
    }

    private boolean trySetBusyState(EnergyRequest request) {
        synchronized (processingLock) {
            if (isBusy) {
                logger.warn("Attempted to fulfill request {} but plant {} is already busy with {}",
                        request.getRequestID(), selfInfo.getPlantId(), currentRequestId);
                return false;
            }

            isBusy = true;
            currentRequestId = request.getRequestID();
            return true;
        }
    }

    private void startEnergyProductionThread(EnergyRequest request) {
        long processingTimeMillis = Math.max(1, (long) request.getAmountKWh());
        logger.debug("Energy production for request {} will take {} ms",
                request.getRequestID(), processingTimeMillis);

        Thread processingThread = createEnergyProductionThread(request, processingTimeMillis);
        processingThread.setDaemon(true);
        processingThread.start();
    }

    private Thread createEnergyProductionThread(EnergyRequest request, long processingTimeMillis) {
        return new Thread(() -> {
            try {
                Thread.sleep(processingTimeMillis*15);
                clearBusyState();
                logger.info("Plant {} finished fulfilling request {}. Now available",
                        selfInfo.getPlantId(), request.getRequestID());
            } catch (InterruptedException e) {
                handleEnergyProductionInterruption(request);
            }
        }, "EnergyProduction-" + request.getRequestID());
    }

    private void clearBusyState() {
        synchronized (processingLock) {
            isBusy = false;
            currentRequestId = null;
        }
    }

    private void handleEnergyProductionInterruption(EnergyRequest request) {
        logger.warn("Energy production for request {} interrupted in plant {}",
                request.getRequestID(), selfInfo.getPlantId());
        clearBusyState();
        Thread.currentThread().interrupt();
    }

    public void addOtherPlant(PowerPlantInfo newPlant) {
        if (!isValidNewPlant(newPlant)) {
            return;
        }

        synchronized (otherPlantsLock) {
            if (plantAlreadyExists(newPlant.getPlantId())) {
                return;
            }
            addOtherPlantUnsafe(newPlant);
        }
    }

    private boolean isValidNewPlant(PowerPlantInfo newPlant) {
        return newPlant != null && !newPlant.getPlantId().equals(selfInfo.getPlantId());
    }

    private boolean plantAlreadyExists(String plantId) {
        return otherPlantsList.stream()
                .anyMatch(plant -> plant.getPlantId().equals(plantId));
    }

    private void addOtherPlantUnsafe(PowerPlantInfo newPlant) {
        otherPlantsList.add(newPlant);
        sortPlants();
        invalidateRingCache();

        logger.debug("Added new plant {}. Total known plants: {}",
                newPlant.getPlantId(), otherPlantsList.size());
    }

    private void sortPlants() {
        otherPlantsList.sort(Comparator.comparing(PowerPlantInfo::getPlantId));
    }

    private void invalidateRingCache() {
        cachedRingArray = null;
        ringCacheVersion++;
    }

    public void removeOtherPlant(String plantId) {
        if (plantId == null || plantId.isEmpty()) {
            return;
        }

        synchronized (otherPlantsLock) {
            boolean removed = otherPlantsList.removeIf(plant -> plant.getPlantId().equals(plantId));
            if (removed) {
                invalidateRingCache();
                logger.debug("Removed plant {}. Total known plants: {}", plantId, otherPlantsList.size());
            }
        }
    }



    /**
     * Gets the next plant in the logical ring topology.
     *
     * Ring Logic Explanation:
     * 1. All plants (including self) are arranged in a sorted ring by plantId
     * 2. Each plant knows its position and can find the next plant in sequence
     * 3. When reaching the end of the ring, it wraps around to the beginning
     *
     * Performance Optimizations:
     * - Uses cached ring array for O(1) lookups when possible
     * - Falls back to synchronized ring creation only when cache is invalid
     * - Cache is invalidated when plants are added/removed
     *
     * Thread Safety:
     * - Fast path: lock-free access to cached ring (volatile read)
     * - Slow path: synchronized ring creation and caching
     */
    public PowerPlantInfo getNextPlantInRing(String currentPlantId) {
        if (currentPlantId == null) {
            return selfInfo;
        }

        // Fast path: try cached ring first (lock-free)
        PowerPlantInfo nextFromCache = findNextInCachedRing(currentPlantId);
        if (nextFromCache != null) {
            return nextFromCache;
        }

        // Slow path: create fresh ring under synchronization
        return createFreshRingAndFindNext(currentPlantId);
    }

    /**
     * Attempts to find the next plant using the cached ring array.
     * Returns null if cache is invalid or plant not found.
     */
    private PowerPlantInfo findNextInCachedRing(String currentPlantId) {
        PowerPlantInfo[] cachedRing = this.cachedRingArray; // volatile read
        if (cachedRing == null) {
            return null;
        }

        for (int i = 0; i < cachedRing.length; i++) {
            if (cachedRing[i].getPlantId().equals(currentPlantId)) {
                int nextIndex = (i + 1) % cachedRing.length;
                return cachedRing[nextIndex];
            }
        }
        return null; // Plant not found in cached ring
    }

    /**
     * Creates a fresh ring including all known plants plus self,
     * caches it, and finds the next plant.
     */
    private PowerPlantInfo createFreshRingAndFindNext(String currentPlantId) {
        synchronized (otherPlantsLock) {
            if (otherPlantsList.isEmpty()) {
                logger.debug("No other plants known. Returning self as next in ring for plant {}",
                        currentPlantId);
                return selfInfo;
            }

            // Create complete sorted ring: other plants + self
            PowerPlantInfo[] completeRing = buildCompleteRing();

            // Cache the ring for future use
            this.cachedRingArray = completeRing; // volatile write

            return findNextInRing(completeRing, currentPlantId);
        }
    }

    /**
     * Builds a complete ring array containing all other plants plus self,
     * sorted by plantId to ensure consistent ring ordering across all plants.
     */
    private PowerPlantInfo[] buildCompleteRing() {
        int ringSize = otherPlantsList.size() + 1;
        PowerPlantInfo[] completeRing = new PowerPlantInfo[ringSize];

        // Copy other plants
        for (int i = 0; i < otherPlantsList.size(); i++) {
            completeRing[i] = otherPlantsList.get(i);
        }

        // Add self
        completeRing[otherPlantsList.size()] = selfInfo;

        // Sort to ensure consistent ring ordering
        Arrays.sort(completeRing, Comparator.comparing(PowerPlantInfo::getPlantId));

        return completeRing;
    }

    /**
     * Finds the next plant in the ring after the given plant ID.
     * Uses modulo arithmetic to wrap around at the end.
     */
    private PowerPlantInfo findNextInRing(PowerPlantInfo[] ring, String currentPlantId) {
        for (int i = 0; i < ring.length; i++) {
            if (ring[i].getPlantId().equals(currentPlantId)) {
                int nextIndex = (i + 1) % ring.length; // Wrap around
                return ring[nextIndex];
            }
        }

        // Fallback: if plant not found, return first plant
        logger.warn("Could not find plant {} in ring. Defaulting to first available plant",
                currentPlantId);
        return ring[0];
    }

    private PowerPlantInfo[] getCurrentOtherPlants() {
        synchronized (otherPlantsLock) {
            return otherPlantsList.toArray(new PowerPlantInfo[0]);
        }
    }

    public List<PowerPlantInfo> getOtherPlants() {
        synchronized (otherPlantsLock) {
            return new ArrayList<>(otherPlantsList);
        }
    }

    public PowerPlantInfo getSelfInfo() {
        return selfInfo;
    }

    public boolean isBusy() {
        return isBusy;
    }

    public String getCurrentRequestId() {
        return currentRequestId;
    }

    public void shutdown() {
        if (isShutdown) {
            return;
        }

        isShutdown = true;
        logger.info("Shutting down PowerPlant {}", selfInfo.getPlantId());

        shutdownPollutionServices();
        shutdownNetworkServices();

        logger.info("PowerPlant {} has been shut down", selfInfo.getPlantId());
    }

    private void shutdownPollutionServices() {
        stopPollutionPublishing();
        stopSensorManager();
        stopPollutionDataPublisher();
    }

    private void stopPollutionPublishing() {
        shouldPublishPollution = false;
        if (pollutionPublisherThread != null) {
            interruptAndJoinThread(pollutionPublisherThread, "pollution publisher");
        }
    }

    private void stopSensorManager() {
        if (sensorManager != null) {
            sensorManager.stopManager();
            interruptAndJoinThread(sensorManager, "SensorManager");
        }
    }

    private void stopPollutionDataPublisher() {
        if (pollutionDataPublisher != null) {
            pollutionDataPublisher.stop();
        }
    }

    private void shutdownNetworkServices() {
        stopMqttSubscriber();
        shutdownGrpcServer();
        shutdownGrpcClient();
    }

    private void stopMqttSubscriber() {
        EnergyRequestSubscriber subscriber = this.energyRequestSubscriber;
        if (subscriber != null) {
            try {
                subscriber.stop();
                logger.debug("Stopped MQTT subscriber for plant {}", selfInfo.getPlantId());
            } catch (MqttException e) {
                logger.warn("Error stopping MQTT subscriber for plant {}: {}",
                        selfInfo.getPlantId(), e.getMessage());
            }
        }
    }

    private void shutdownGrpcServer() {
        Server server = this.grpcServer;
        if (server != null && !server.isShutdown()) {
            server.shutdown();
            logger.debug("Shut down gRPC server for plant {}", selfInfo.getPlantId());
        }
    }

    private void shutdownGrpcClient() {
        if (grpcClient != null) {
            grpcClient.shutdown();
            logger.debug("Shut down gRPC client for plant {}", selfInfo.getPlantId());
        }
    }

    private void interruptAndJoinThread(Thread thread, String threadName) {
        thread.interrupt();
        try {
            thread.join(THREAD_JOIN_TIMEOUT_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warn("Interrupted while joining {} thread for plant {}.",
                    threadName, selfInfo.getPlantId());
        }
    }
}