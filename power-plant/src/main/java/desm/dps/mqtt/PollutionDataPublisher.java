package desm.dps.mqtt;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import desm.dps.PollutionData;
import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages publishing {@link PollutionData} to a specific MQTT topic.
 */
public class PollutionDataPublisher {
    private static final Logger logger = LoggerFactory.getLogger(PollutionDataPublisher.class);

    private static final int QOS_LEVEL = 2;

    private final String brokerUrl;
    private final String clientId;
    private final String topic;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private IMqttClient mqttClient;

    /**
     * Constructs a new PollutionDataPublisher.
     *
     * @param brokerUrl The full URI of the MQTT broker (e.g., "tcp://localhost:1883").
     * @param clientId  A base client ID, which will be suffixed to ensure uniqueness.
     * @param topic     The MQTT topic to publish pollution data to.
     */
    public PollutionDataPublisher(String brokerUrl, String clientId, String topic) {
        this.brokerUrl = brokerUrl;
        this.clientId = clientId + "_pollution_publisher";
        this.topic = topic;
    }

    /**
     * Connects to the MQTT broker.
     *
     * @throws MqttException if the connection fails.
     */
    public void start() throws MqttException {
        mqttClient = new MqttClient(brokerUrl, clientId, new MemoryPersistence());
        MqttConnectOptions connOpts = new MqttConnectOptions();
        connOpts.setCleanSession(true);
        connOpts.setAutomaticReconnect(true);

        logger.info("PollutionDataPublisher connecting to MQTT broker: {} with client ID: {}", brokerUrl, clientId);
        mqttClient.connect(connOpts);
        logger.info("PollutionDataPublisher connected to MQTT broker.");
    }

    /**
     * Serializes and publishes pollution data to the MQTT topic.
     * If the client is disconnected, it will attempt to reconnect before publishing.
     *
     * @param data The {@link PollutionData} to publish.
     */
    public void publish(PollutionData data) {
        if (mqttClient == null || !mqttClient.isConnected()) {
            logger.warn("MQTT client not connected. Attempting to reconnect before publishing for plant {}", data.plantId());
            try {
                // Rely on automatic reconnect or re-initiate connection.
                if (mqttClient == null) start();
                if (!mqttClient.isConnected()) mqttClient.reconnect();
            } catch (MqttException e) {
                logger.error("Failed to reconnect MQTT client for pollution data publisher: {}", e.getMessage());
                return;
            }
        }

        if (!mqttClient.isConnected()) {
            logger.error("Still not connected after reconnect attempt. Aborting publish for plant {}", data.plantId());
            return;
        }

        try {
            String payload = objectMapper.writeValueAsString(data);
            MqttMessage message = new MqttMessage(payload.getBytes());
            message.setQos(QOS_LEVEL);

            mqttClient.publish(topic, message);
            logger.info("Published pollution data for plant {} to topic {}: {}", data.plantId(), topic, payload);
        } catch (JsonProcessingException e) {
            logger.error("Error serializing PollutionData for plant {}", data.plantId(), e);
        } catch (MqttException e) {
            logger.error("Error publishing pollution data for plant {} to topic {}: {}", data.plantId(), topic, e.getMessage());
        }
    }

    /**
     * Gracefully disconnects from the MQTT broker and closes the client.
     */
    public void stop() {
        try {
            if (mqttClient != null && mqttClient.isConnected()) {
                mqttClient.disconnect();
                logger.info("PollutionDataPublisher disconnected from MQTT broker.");
            }
        } catch (MqttException e) {
            logger.error("Error while disconnecting PollutionDataPublisher", e);
        } finally {
            try {
                if (mqttClient != null) {
                    mqttClient.close();
                }
            } catch (MqttException e) {
                logger.error("Error closing PollutionDataPublisher client", e);
            }
        }
    }
}