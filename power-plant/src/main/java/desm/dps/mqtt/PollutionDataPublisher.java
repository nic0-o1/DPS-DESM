package desm.dps.mqtt;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import desm.dps.PollutionData;
import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PollutionDataPublisher {
    private static final Logger logger = LoggerFactory.getLogger(PollutionDataPublisher.class);
    private final String brokerUrl;
    private final String clientId;
    private final String topic;
    private IMqttClient mqttClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final int QOS = 1;

    public PollutionDataPublisher(String brokerUrl, String clientId, String topic) {
        this.brokerUrl = brokerUrl;
        this.clientId = clientId + "_pollution_publisher"; // Ensure unique client ID
        this.topic = topic;
    }

    public void start() throws MqttException {
        mqttClient = new MqttClient(brokerUrl, clientId, new MemoryPersistence());
        MqttConnectOptions connOpts = new MqttConnectOptions();
        connOpts.setCleanSession(true);
        connOpts.setAutomaticReconnect(true);

        logger.info("PollutionDataPublisher connecting to MQTT broker: {} with client ID: {}", brokerUrl, clientId);
        mqttClient.connect(connOpts);
        logger.info("PollutionDataPublisher connected to MQTT broker.");
    }

    public void publish(PollutionData data) {
        if (mqttClient == null || !mqttClient.isConnected()) {
            logger.warn("MQTT client not connected. Cannot publish pollution data for plant {}", data.plantId());
            try {
                start();
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
            message.setQos(QOS);

            mqttClient.publish(topic, message);
            logger.info("Published pollution data for plant {} to topic {}: {}", data.plantId(), topic, payload);
        } catch (JsonProcessingException e) {
            logger.error("Error serializing PollutionData for plant {}: {}", data.plantId(), e.getMessage());
        } catch (MqttException e) {
            logger.error("Error publishing pollution data for plant {} to topic {}: {}", data.plantId(), topic, e.getMessage());
        }
    }

    public void stop() {
        try {
            if (mqttClient != null && mqttClient.isConnected()) {
                mqttClient.disconnect();
                logger.info("PollutionDataPublisher disconnected from MQTT broker.");
            }
            if (mqttClient != null) {
                mqttClient.close();
            }
        } catch (MqttException e) {
            logger.error("Error stopping PollutionDataPublisher: {}", e.getMessage());
        }
    }
}
