package desm.dps;

import desm.dps.config.AppConfig;
import desm.dps.core.EnergyRequestProcessor;
import desm.dps.core.PlantRegistry;
import desm.dps.core.PollutionMonitor;
import desm.dps.core.ServiceManager;
import desm.dps.election.ElectionManager;
import desm.dps.grpc.PlantGrpcClient;
import desm.dps.rest.AdminServerClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.Random;

/**
 * Acts as a Facade for the power plant system. It provides a simplified, high-level interface
 * for starting, stopping, and interacting with the plant, while hiding the complex
 * network of internal components that manage state, services, and processing logic.
 */
public class PowerPlant {
    private static final Logger logger = LoggerFactory.getLogger(PowerPlant.class);

    // --- Core Immutable State & Dependencies ---
    private final PowerPlantInfo selfInfo;
    private final AdminServerClient adminClient;
    private final Random random = new Random();

    // --- Subsystem Components (Hidden behind the Facade) ---
    private final PlantRegistry plantRegistry;
    private final EnergyRequestProcessor energyRequestProcessor;
    private final ServiceManager serviceManager;
    private final PollutionMonitor pollutionMonitor;

    private volatile boolean isShutdown = false;

    private final AppConfig config;

    /**
     * Constructs a new PowerPlant instance.
     * Initializes all the internal subsystems required for the plant's operation.
     *
     * @param selfInfo              Information about this power plant.
     * @param adminServerBaseUrl    The base URL of the central administration server.
     * @param mqttBrokerUrl         The URL of the MQTT broker for messaging.
     * @param energyRequestTopic    The MQTT topic for listening to energy requests.
     * @param pollutionPublishTopic The MQTT topic for publishing pollution data
     */
    public PowerPlant(PowerPlantInfo selfInfo, String adminServerBaseUrl, String mqttBrokerUrl, String energyRequestTopic,
                      String pollutionPublishTopic) {
        if (selfInfo == null || adminServerBaseUrl == null || mqttBrokerUrl == null || energyRequestTopic == null || pollutionPublishTopic == null) {
            throw new IllegalArgumentException("All constructor parameters must be non-null");
        }
        this.selfInfo = selfInfo;
        this.adminClient = new AdminServerClient(adminServerBaseUrl);

        // Instantiate the hidden subsystem components
        this.plantRegistry = new PlantRegistry(selfInfo);
        PlantGrpcClient grpcClient = new PlantGrpcClient(this);
        ElectionManager electionManager = new ElectionManager(this, grpcClient);
        this.energyRequestProcessor = new EnergyRequestProcessor(selfInfo.plantId(), electionManager);
        this.pollutionMonitor = new PollutionMonitor(selfInfo, mqttBrokerUrl, pollutionPublishTopic);
        this.serviceManager = new ServiceManager(this, selfInfo, grpcClient, electionManager, pollutionMonitor,
                mqttBrokerUrl, energyRequestTopic);

        this.config = AppConfig.getInstance();

        logger.info("Initialized PowerPlant Facade for ID: {}", selfInfo.plantId());
    }

    /**
     * Starts the power plant and all its associated services.
     * This method orchestrates the startup sequence by delegating to the service manager,
     * registering with the admin server, and announcing its presence.
     *
     * @throws IOException   if there is an error starting network services like the gRPC server.
     * @throws MqttException if there is an error connecting to the MQTT broker.
     */
    public void start() throws IOException, MqttException {
        if (isShutdown) {
            throw new IllegalStateException("Cannot start a shutdown PowerPlant.");
        }
        logger.info("Starting PowerPlant {}", selfInfo.plantId());
            serviceManager.startServices();
            registerAndAnnounce();
            // To enable pollution monitoring, uncomment the following line:
            pollutionMonitor.start();
            logger.info("PowerPlant {} is fully started and operational.", selfInfo.plantId());
    }

    /**
     * Shuts down the power plant and all its services gracefully.
     * Delegates the entire shutdown process to the service manager.
     */
    public void shutdown() {
        if (isShutdown) {
            return;
        }
        isShutdown = true;
        logger.info("Shutting down PowerPlant {}", selfInfo.plantId());
        serviceManager.shutdownServices();
        logger.info("PowerPlant {} has been shut down.", selfInfo.plantId());
    }

    /**
     * Handles an incoming energy request by delegating it to the internal processor.
     *
     * @param energyRequest The energy request received from the network.
     */
    public void handleIncomingEnergyRequest(EnergyRequest energyRequest) {
        if (energyRequest == null) {
            logger.warn("Received a null energy request. Ignoring.");
            return;
        }
        logger.info("Facade received Energy Request {} for {} kWh", energyRequest.requestID(), energyRequest.amountKWh());
        energyRequestProcessor.processIncomingRequest(energyRequest);
    }

    /**
     * Instructs the plant to fulfill an energy request after winning a bid.
     * This is delegated to the internal processor.
     *
     * @param request The request to fulfill.
     * @param price   The winning price for the bid.
     */
    public void fulfillEnergyRequest(EnergyRequest request, double price) {
        if (request == null) {
            logger.warn("Cannot fulfill a null request.");
            return;
        }
        energyRequestProcessor.fulfillRequest(request, price);
    }

    /**
     * Generates a random price for an energy bid. The price is constrained
     * and rounded to two decimal places.
     *
     * @return A randomly generated price.
     */
    public double generatePrice() {
        double minPrice = config.getMinPrice();
        double maxPrice = config.getMaxPrice();
        double price = minPrice + (maxPrice - minPrice) * random.nextDouble();
        return Math.round(price * 100.0) / 100.0;
    }

    /**
     * Registers this plant with the central admin server and announces its presence
     * to other plants discovered in the process.
     */
    private void registerAndAnnounce() {
        List<PowerPlantInfo> initialOtherPlants = adminClient.register(selfInfo);
        if (initialOtherPlants != null && !initialOtherPlants.isEmpty()) {
            plantRegistry.addInitialPlants(initialOtherPlants);
            logger.info("Registered with Admin Server. Discovered {} other plants.", plantRegistry.getOtherPlantsCount());
            serviceManager.announcePresenceTo(plantRegistry.getOtherPlantsSnapshot());
        } else {
            logger.warn("Registration with Admin Server yielded no other plants.");
        }
    }

    // --- Getters and Delegates to Subsystems ---

    /**
     * Adds a newly discovered plant to the internal registry.
     * @param newPlant The information of the plant to add.
     */
    public void addOtherPlant(PowerPlantInfo newPlant) {
        plantRegistry.addPlant(newPlant);
    }

    /**
     * Removes a plant from the internal registry, typically after it goes offline.
     * @param plantId The ID of the plant to remove.
     */
    public void removeOtherPlant(String plantId) {
        plantRegistry.removePlant(plantId);
    }

    /**
     * Finds the next plant in the logical communication ring.
     * @param currentPlantId The ID of the current plant in the ring.
     * @return The {@link PowerPlantInfo} of the next plant.
     */
    public PowerPlantInfo getNextPlantInRing(String currentPlantId) {
        return plantRegistry.getNextInRing(currentPlantId);
    }

    /**
     * Retrieves a snapshot of all other known plants.
     * @return A list of {@link PowerPlantInfo} for other plants.
     */
    public List<PowerPlantInfo> getOtherPlants() {
        return plantRegistry.getOtherPlantsSnapshot();
    }

    /**
     * Gets this plant's own information.
     * @return The {@link PowerPlantInfo} for this instance.
     */
    public PowerPlantInfo getSelfInfo() {
        return selfInfo;
    }

    /**
     * Checks if the plant is currently busy fulfilling a request.
     * @return true if the plant is busy, false otherwise.
     */
    public boolean isBusy() {
        return energyRequestProcessor.isBusy();
    }

    /**
     * Gets the ID of the request the plant is currently processing.
     * @return The request ID, or null if the plant is not busy.
     */
    public String getCurrentRequestId() {
        return energyRequestProcessor.getCurrentRequestId();
    }

    /**
     * Removes a specific request from the energy request queue.
     * This is typically called when another plant has won the bid for the request.
     *
     * @param requestId The ID of the request to remove.
     */
    public void removeRequestFromQueue(String requestId) {
        energyRequestProcessor.removeRequestById(requestId);
    }

}