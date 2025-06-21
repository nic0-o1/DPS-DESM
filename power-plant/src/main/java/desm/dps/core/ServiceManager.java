package desm.dps.core;

import desm.dps.PowerPlant;
import desm.dps.PowerPlantInfo;
import desm.dps.election.ElectionManager;
import desm.dps.grpc.PlantGrpcClient;
import desm.dps.grpc.PlantGrpcService;
import desm.dps.mqtt.EnergyRequestSubscriber;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;

/**
 * Manages the lifecycle of all network services and background threads for the PowerPlant.
 * This class centralizes the logic for starting and stopping components in the correct order.
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

    private volatile Server grpcServer;
    private volatile EnergyRequestSubscriber energyRequestSubscriber;

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
     * Starts the core network services (gRPC, MQTT).
     *
     * @throws IOException   if the gRPC server fails to start.
     * @throws MqttException if the MQTT subscriber fails to start.
     */
    public void startServices() throws IOException, MqttException {
        startGrpcServer();
        startMqttSubscriber();
    }

    /**
     * Shuts down all managed services in a graceful manner.
     */
    public void shutdownServices() {
        stopMqttSubscriber();
        shutdownGrpcServer();
        shutdownGrpcClient();
        pollutionMonitor.stop();
    }

    /**
     * Iterates through a list of known plants and announces this plant's presence via gRPC.
     *
     * @param knownPlants A list of plants to notify.
     */
    public void announcePresenceTo(List<PowerPlantInfo> knownPlants) {
        logger.info("Announcing presence to {} known plants...", knownPlants.size());
        for (PowerPlantInfo otherPlant : knownPlants) {
            grpcClient.announcePresence(otherPlant, selfInfo);
        }
    }

    private void startGrpcServer() throws IOException {
        PlantGrpcService plantGrpcService = new PlantGrpcService(powerPlant, electionManager);
        grpcServer = ServerBuilder.forPort(selfInfo.port())
                .addService(plantGrpcService)
                .build()
                .start();
        logger.info("gRPC Server started for plant {} on port {}", selfInfo.plantId(), selfInfo.port());
    }

    private void startMqttSubscriber() throws MqttException {
        energyRequestSubscriber = new EnergyRequestSubscriber(
                mqttBrokerUrl,
                selfInfo.plantId() + "_subscriber",
                energyRequestTopic,
                powerPlant
        );
        energyRequestSubscriber.start();
        logger.info("MQTT Subscriber started for plant {} on topic {}", selfInfo.plantId(), energyRequestTopic);
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

//    /**
//     * A utility method to safely interrupt and join a thread.
//     *
//     * @param thread     The thread to stop.
//     * @param threadName A descriptive name for logging.
//     * @param plantId    The ID of the plant for logging context.
//     */
//    public static void interruptAndJoinThread(Thread thread, String threadName, int plantId) {
//        if (thread == null) return;
//        thread.interrupt();
//        try {
//            thread.join(THREAD_JOIN_TIMEOUT_MS);
//        } catch (InterruptedException e) {
//            Thread.currentThread().interrupt();
//            logger.warn("Interrupted while joining {} thread for plant {}.", threadName, plantId);
//        }
//    }
}