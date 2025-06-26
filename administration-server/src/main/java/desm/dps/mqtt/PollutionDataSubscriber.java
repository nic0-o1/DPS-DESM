package desm.dps.mqtt;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import desm.dps.PollutionData;
import desm.dps.repository.MeasurementRepository;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * A Spring component that subscribes to an MQTT topic to receive pollution data from power plants.
 * Received data is deserialized and persisted via the {@link MeasurementRepository}.
 * The component's lifecycle (startup, shutdown) is managed by Spring.
 */
@Component
public class PollutionDataSubscriber implements MqttCallback {

    private static final Logger logger = LoggerFactory.getLogger(PollutionDataSubscriber.class);

    @Value("${mqtt.broker.url:tcp://localhost:1883}")
    private String brokerUrl;

    @Value("${mqtt.topic.pollution:desm/pollution/data}")
    private String topic;

    @Value("${mqtt.client.id:adminServer}")
    private String baseClientId;

    @Value("${mqtt.qos:2}")
    private int qos;

    private IMqttClient mqttClient;
    private final MeasurementRepository measurementRepository;
    private final ObjectMapper objectMapper;
    // This is no longer final and will be initialized in the start() method.
    private String clientId;

    public PollutionDataSubscriber(MeasurementRepository measurementRepository) {
        this.measurementRepository = measurementRepository;
        this.objectMapper = new ObjectMapper();
        // DO NOT initialize clientId here, as baseClientId is not yet injected.
    }

    /**
     * Initializes and starts the MQTT client after dependency injection is complete.
     * This method connects to the broker and subscribes to the pollution data topic.
     *
     * @throws RuntimeException if the MQTT initialization fails, to prevent the application from starting in a bad state.
     */
    @PostConstruct
    public void start() {
        try {
            // FIX: Initialize clientId here, after @Value fields have been populated.
            this.clientId = baseClientId + "_admin_pollution_subscriber";

            mqttClient = new MqttClient(brokerUrl, clientId, new MemoryPersistence());
            mqttClient.setCallback(this);

            MqttConnectOptions connOpts = new MqttConnectOptions();
            connOpts.setCleanSession(true);
            connOpts.setAutomaticReconnect(true);

            logger.info("PollutionDataSubscriber connecting to MQTT broker: {} with client ID: {}", brokerUrl, clientId);
            mqttClient.connect(connOpts);
            logger.info("PollutionDataSubscriber successfully connected to MQTT broker.");

            mqttClient.subscribe(topic, qos);
            logger.info("Subscribed to topic '{}' with QoS {}", topic, qos);

        } catch (MqttException e) {
            logger.error("Failed to initialize MQTT subscriber for client ID {}: {}", clientId, e.getMessage(), e);
            throw new RuntimeException("MQTT client initialization failed", e);
        }
    }

    /**
     * Gracefully disconnects from the MQTT broker and closes the client during application shutdown.
     */
    @PreDestroy
    public void stop() {
        try {
            if (mqttClient != null && mqttClient.isConnected()) {
                logger.info("Unsubscribing and disconnecting PollutionDataSubscriber (client ID: {})", clientId);
                mqttClient.unsubscribe(topic);
                mqttClient.disconnect();
                logger.info("PollutionDataSubscriber disconnected successfully.");
            }
        } catch (MqttException e) {
            logger.error("Error during MQTT disconnection for client '{}'", clientId, e);
        } finally {
            try {
                if (mqttClient != null) {
                    mqttClient.close();
                }
            } catch (MqttException e) {
                logger.error("Error closing MQTT client '{}'", clientId, e);
            }
        }
    }

    @Override
    public void connectionLost(Throwable cause) {
        logger.error("MQTT connection lost for client '{}'. Automatic reconnect will be attempted. Cause: {}",
                clientId, cause.getMessage(), cause);
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) {
        try {
            String payload = new String(message.getPayload());
            // This log should now appear in your server logs.
            logger.info("Received pollution data on topic '{}': {}", topic, payload);

            PollutionData pollutionData = objectMapper.readValue(payload, PollutionData.class);

            if (pollutionData != null) {
                measurementRepository.addPollutionData(pollutionData);
                logger.debug("Successfully stored pollution data from Plant {}", pollutionData.plantId());
            } else {
                logger.warn("Received malformed pollution data which deserialized to null. Payload: {}", payload);
            }
        } catch (JsonProcessingException e) {
            logger.error("Failed to deserialize JSON payload on topic '{}'. Payload: '{}'", topic, new String(message.getPayload()), e);
        } catch (Exception e) {
            logger.error("An unexpected error occurred while processing message on topic '{}'", topic, e);
        }
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
        // This callback is not used for a subscriber client.
    }
}