package desm.dps.core;

import desm.dps.PollutionData;
import desm.dps.PowerPlantInfo;
import desm.dps.SensorManager;
import desm.dps.mqtt.PollutionDataPublisher;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Encapsulates the pollution monitoring and data publishing feature.
 */
public class PollutionMonitor {
    private static final Logger logger = LoggerFactory.getLogger(PollutionMonitor.class);
    private static final int POLLUTION_PUBLISH_INTERVAL_MS = 10_000;

    private final PowerPlantInfo selfInfo;
    private final SensorManager sensorManager;
    private final PollutionDataPublisher pollutionDataPublisher;
    private Thread publisherThread;
    private volatile boolean shouldPublish = false;

    public PollutionMonitor(PowerPlantInfo selfInfo, String mqttBrokerUrl, String pollutionTopic) {
        this.selfInfo = selfInfo;
        this.sensorManager = new SensorManager(selfInfo.plantId() + "-PollutionMonitor");
        this.pollutionDataPublisher = new PollutionDataPublisher(mqttBrokerUrl, String.valueOf(selfInfo.plantId()), pollutionTopic);
    }

    /**
     * Starts the pollution monitoring and publishing services.
     * This will start the sensor manager and the background publishing thread.
     *
     * @throws MqttException if the publisher client fails to connect.
     */
    public void start() throws MqttException {
        logger.info("Starting pollution monitoring for plant {}.", selfInfo.plantId());
        sensorManager.start();
        pollutionDataPublisher.start();
        shouldPublish = true;
        publisherThread = new Thread(this::publishingLoop, selfInfo.plantId() + "-PollutionPublisher");
        publisherThread.setDaemon(true);
        publisherThread.start();
    }

    /**
     * Stops all pollution monitoring services gracefully.
     */
    public void stop() {
        if (!shouldPublish && publisherThread == null) return;
        logger.info("Stopping pollution monitoring for plant {}.", selfInfo.plantId());
        shouldPublish = false;

        if (sensorManager != null) {
            sensorManager.stopManager();
        }
        if (pollutionDataPublisher != null) {
            pollutionDataPublisher.stop();
        }
        publisherThread = null;
    }

    private void publishingLoop() {
        logger.info("Pollution data publishing thread started.");
        while (shouldPublish && !Thread.currentThread().isInterrupted()) {
            try {
                publishSensorData();
                Thread.sleep(POLLUTION_PUBLISH_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                logger.error("Error in pollution data publishing loop for plant {}: {}", selfInfo.plantId(), e.getMessage(), e);
            }
        }
        logger.info("Pollution data publishing thread stopped.");
    }

    private void publishSensorData() {
        List<Double> averages = sensorManager.getAndClearAverages();
        if (averages != null && !averages.isEmpty()) {
            PollutionData data = new PollutionData(
                    selfInfo.plantId(),
                    System.currentTimeMillis(),
                    averages
            );
            pollutionDataPublisher.publish(data);
            logger.debug("Published pollution data for plant {}.", selfInfo.plantId());
        }
    }
}