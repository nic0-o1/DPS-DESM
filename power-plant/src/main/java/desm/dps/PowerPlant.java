package desm.dps;

import desm.dps.config.AppConfig;
import desm.dps.core.EnergyRequestProcessor;
import desm.dps.core.PlantRegistry;
import desm.dps.core.PollutionMonitor;
import desm.dps.core.ServiceManager;
import desm.dps.election.ElectionManager;
import desm.dps.grpc.PlantGrpcClient;
import desm.dps.grpc.PortInUseException;
import desm.dps.rest.AdminServerClient;
import desm.dps.rest.RegistrationConflictException;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Random;

/**
 * The main facade class for a power plant, coordinating all its subsystems and managing its lifecycle.
 * It handles startup, shutdown, and delegation of incoming energy requests.
 */
public class PowerPlant {
    private static final Logger logger = LoggerFactory.getLogger(PowerPlant.class);

    private final PowerPlantInfo selfInfo;
    private final AdminServerClient adminClient;
    private final AppConfig config;
    private final Random random = new Random();

    private final PlantRegistry plantRegistry;
    private final EnergyRequestProcessor energyRequestProcessor;
    private final ServiceManager serviceManager;
    private final ElectionManager electionManager;
    private final PollutionMonitor pollutionMonitor;

    private volatile boolean isShutdown = false;

    public PowerPlant(PowerPlantInfo selfInfo, String adminServerBaseUrl, String mqttBrokerUrl, String energyRequestTopic,
                      String pollutionPublishTopic) {
        if (selfInfo == null || adminServerBaseUrl == null || mqttBrokerUrl == null || energyRequestTopic == null || pollutionPublishTopic == null) {
            throw new IllegalArgumentException("All constructor parameters must be non-null.");
        }
        this.selfInfo = selfInfo;
        this.adminClient = new AdminServerClient(adminServerBaseUrl);
        this.config = AppConfig.getInstance();

        this.plantRegistry = new PlantRegistry(selfInfo);
        PlantGrpcClient grpcClient = new PlantGrpcClient(this);
        this.electionManager = new ElectionManager(this, grpcClient);
        this.energyRequestProcessor = new EnergyRequestProcessor(selfInfo.plantId(), this.electionManager);
        this.pollutionMonitor = new PollutionMonitor(selfInfo, mqttBrokerUrl, pollutionPublishTopic);

        this.serviceManager = new ServiceManager(this, selfInfo, grpcClient, electionManager, pollutionMonitor,
                mqttBrokerUrl, energyRequestTopic);

        logger.info("Initialized PowerPlant Facade for ID: {}", selfInfo.plantId());
    }

    /**
     * Starts the power plant and all its services. This includes starting the gRPC server,
     * registering with the admin server, discovering other plants, starting MQTT clients,
     * and announcing its presence to the network.
     *
     * @throws PortInUseException if the gRPC port is already in use.
     * @throws RegistrationConflictException if this plant's ID is already registered.
     * @throws MqttException if any MQTT client fails to connect.
     */
    public void start() throws PortInUseException, RegistrationConflictException, MqttException {
        if (isShutdown) {
            throw new IllegalStateException("Cannot start a shutdown PowerPlant.");
        }
        logger.info("Starting PowerPlant {}", selfInfo.plantId());

        serviceManager.startGrpcServer();
        List<PowerPlantInfo> initialOtherPlants = adminClient.register(selfInfo);

        if (!initialOtherPlants.isEmpty()) {
            plantRegistry.addInitialPlants(initialOtherPlants);
            logger.info("Registered with Admin Server. Discovered {} other plants.", plantRegistry.getOtherPlantsCount());
        } else {
            logger.info("Registered with Admin Server. This is the first plant in the network.");
        }

        serviceManager.startMqttSubscriber();
        serviceManager.startPollutionMonitor();
        serviceManager.announcePresenceTo(plantRegistry.getOtherPlantsSnapshot());

        logger.info("PowerPlant {} is fully started and operational.", selfInfo.plantId());
    }

    /**
     * Initiates a graceful shutdown of the power plant and all its associated services.
     */
    public void shutdown() {
        if (isShutdown) return;
        isShutdown = true;
        logger.info("Shutting down PowerPlant {}", selfInfo.plantId());
        serviceManager.shutdownServices();
        logger.info("PowerPlant {} has been shut down.", selfInfo.plantId());
    }

    /**
     * Handles an incoming energy request by either starting an election immediately
     * or queueing the request if the plant is currently busy with another one.
     *
     * @param energyRequest The request to be processed.
     */
    public void handleIncomingEnergyRequest(EnergyRequest energyRequest) {
        if (energyRequest == null) {
            logger.warn("Received a null energy request. Ignoring.");
            return;
        }
        logger.info("Facade received Energy Request {} for {} kWh.", energyRequest.requestID(), energyRequest.amountKWh());

        if (isBusy()) {
            energyRequestProcessor.queueRequest(energyRequest);
        } else {
            energyRequestProcessor.startElectionForNewRequest(energyRequest);
        }
    }

    /**
     * Fulfills an energy request. This method is called when the plant has won an election
     * and is now responsible for the request.
     *
     * @param request The request to fulfill.
     * @param price The winning price for this request.
     */
    public void fulfillEnergyRequest(EnergyRequest request, double price) {
        if (request == null) {
            logger.warn("Cannot fulfill a null request.");
            return;
        }
        energyRequestProcessor.fulfillRequest(request, price);
    }

    /**
     * Generates a pseudo-random price for an energy bid based on configured min/max values.
     *
     * @return A price rounded to two decimal places.
     */
    public double generatePrice() {
        double minPrice = config.getMinPrice();
        double maxPrice = config.getMaxPrice();
        double price = minPrice + (maxPrice - minPrice) * random.nextDouble();
        return Math.round(price * 100.0) / 100.0;
    }

    // --- DELEGATE METHODS TO SUBSYSTEMS ---

    public void addOtherPlant(PowerPlantInfo newPlant) {
        plantRegistry.addPlant(newPlant);
    }

    public void removeOtherPlant(int plantId) {
        plantRegistry.removePlant(plantId);
    }

    public PowerPlantInfo getNextPlantInRing(int currentPlantId) {
        return plantRegistry.getNextInRing(currentPlantId);
    }

    public List<PowerPlantInfo> getOtherPlants() {
        return plantRegistry.getOtherPlantsSnapshot();
    }

    public PowerPlantInfo getSelfInfo() {
        return selfInfo;
    }

    public boolean isBusy() {
        return energyRequestProcessor.isBusy();
    }

    public void removeRequestFromQueue(String requestId) {
        energyRequestProcessor.removeRequestById(requestId);
    }
}