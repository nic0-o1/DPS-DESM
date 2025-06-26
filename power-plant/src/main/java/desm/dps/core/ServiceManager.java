package desm.dps.core;

import desm.dps.PowerPlant;
import desm.dps.PowerPlantInfo;
import desm.dps.election.ElectionManager;
import desm.dps.grpc.PlantGrpcClient;
import desm.dps.grpc.PlantGrpcService;
import desm.dps.grpc.PortInUseException;
import desm.dps.mqtt.EnergyRequestSubscriber;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.BindException;
import java.util.List;

/**
 * Manages the lifecycle of all network services and background components for the PowerPlant.
 * This class centralizes the logic for starting and stopping the gRPC server, MQTT clients,
 * and the pollution monitor in the correct order.
 */
public class ServiceManager {
    private static final Logger logger = LoggerFactory.getLogger(ServiceManager.class);

    private final PowerPlant powerPlant;
    private final PowerPlantInfo selfInfo;
    private final PlantGrpcClient grpcClient;
    private final ElectionManager electionManager;
    private final PollutionMonitor pollutionMonitor;
    private final String mqttBrokerUrl;
    private final String energyRequestTopic;

    private Server grpcServer;
    private EnergyRequestSubscriber energyRequestSubscriber;

    public ServiceManager(PowerPlant powerPlant, PowerPlantInfo selfInfo, PlantGrpcClient grpcClient, ElectionManager electionManager,
                          PollutionMonitor pollutionMonitor, String mqttBrokerUrl, String energyRequestTopic) {
        this.powerPlant = powerPlant;
        this.selfInfo = selfInfo;
        this.grpcClient = grpcClient;
        this.electionManager = electionManager;
        this.pollutionMonitor = pollutionMonitor;
        this.mqttBrokerUrl = mqttBrokerUrl;
        this.energyRequestTopic = energyRequestTopic;
    }

    /**
     * Starts the gRPC server to listen for incoming requests from other plants.
     *
     * @throws PortInUseException if the configured port is already occupied.
     */
    public void startGrpcServer() throws PortInUseException {
        try {
            PlantGrpcService plantGrpcService = new PlantGrpcService(powerPlant, electionManager);
            grpcServer = ServerBuilder.forPort(selfInfo.port())
                    .addService(plantGrpcService)
                    .build()
                    .start();
            logger.info("gRPC Server started for Plant {} on port {}", selfInfo.plantId(), selfInfo.port());
        } catch (IOException e) {
            if (e.getCause() instanceof BindException) {
                throw new PortInUseException("Port " + selfInfo.port() + " is already in use.", e);
            }
            throw new RuntimeException("Failed to start gRPC server due to an unexpected I/O error.", e);
        }
    }

    /**
     * Starts the MQTT subscriber to listen for broadcasted energy requests.
     *
     * @throws MqttException if the client fails to connect or subscribe.
     */
    public void startMqttSubscriber() throws MqttException {
        energyRequestSubscriber = new EnergyRequestSubscriber(
                mqttBrokerUrl,
                selfInfo.plantId() + "_subscriber",
                energyRequestTopic,
                powerPlant
        );
        energyRequestSubscriber.start();
        logger.info("MQTT Subscriber started for Plant {} on topic {}", selfInfo.plantId(), energyRequestTopic);
    }

    /**
     * Starts the pollution monitor, which includes its internal sensor simulation
     * and MQTT publisher for pollution data.
     *
     * @throws MqttException if the internal pollution data publisher fails to connect.
     */
    public void startPollutionMonitor() throws MqttException {
        if (pollutionMonitor != null) {
            pollutionMonitor.start();
            logger.info("Pollution Monitor started for Plant {}.", selfInfo.plantId());
        }
    }

    /**
     * Iterates through a list of known plants and announces this plant's presence via gRPC.
     *
     * @param knownPlants A list of plants to notify.
     */
    public void announcePresenceTo(List<PowerPlantInfo> knownPlants) {
        if (knownPlants.isEmpty()) {
            logger.info("No other plants to announce presence to.");
            return;
        }
        logger.info("Announcing presence to {} known plants...", knownPlants.size());
        for (PowerPlantInfo otherPlant : knownPlants) {
            grpcClient.announcePresence(otherPlant, selfInfo);
        }
    }

    /**
     * Shuts down all managed services in a graceful and controlled order.
     */
    public void shutdownServices() {
        logger.info("Shutting down all services for Plant {}...", selfInfo.plantId());
        // Stop components that run as separate threads or have external connections first.
        stopMqttSubscriber();
        if (pollutionMonitor != null) {
            pollutionMonitor.stop();
        }
        // Then shut down network clients and servers.
        shutdownGrpcClient();
        shutdownGrpcServer();
        logger.info("All services for Plant {} have been shut down.", selfInfo.plantId());
    }

    private void stopMqttSubscriber() {
        if (energyRequestSubscriber != null) {
            energyRequestSubscriber.stop();
            logger.debug("Stopped MQTT subscriber for plant {}", selfInfo.plantId());
        }
    }

    private void shutdownGrpcServer() {
        if (grpcServer != null && !grpcServer.isShutdown()) {
            grpcServer.shutdown();
            logger.debug("Shut down gRPC server for plant {}", selfInfo.plantId());
        }
    }

    private void shutdownGrpcClient() {
        if (grpcClient != null) {
            grpcClient.shutdown();
            logger.debug("Shut down gRPC client for plant {}", selfInfo.plantId());
        }
    }
}