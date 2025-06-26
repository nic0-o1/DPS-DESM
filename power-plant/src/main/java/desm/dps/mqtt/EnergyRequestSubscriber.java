package desm.dps.mqtt;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import desm.dps.EnergyRequest;
import desm.dps.PowerPlant;
import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MqttDefaultFilePersistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages the connection to an MQTT broker to subscribe to a topic for energy requests.
 * It listens for messages, deserializes them into {@link EnergyRequest} objects,
 * and passes them to the {@link PowerPlant} for processing.
 */
public class EnergyRequestSubscriber implements MqttCallback {

    private static final Logger logger = LoggerFactory.getLogger(EnergyRequestSubscriber.class);

    // QoS 2 ensures that each message is received exactly once.
    private static final int QOS_LEVEL = 2;

    private final String brokerUri;
    private final String clientId;
    private final String topic;
    private final PowerPlant powerPlant;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private MqttClient mqttClient;

    /**
     * Constructs a new EnergyRequestSubscriber.
     *
     * @param brokerUri  The full URI of the MQTT broker (e.g., "tcp://localhost:1883").
     * @param clientId   A unique identifier for this MQTT client.
     * @param topic      The MQTT topic to subscribe to for energy requests.
     * @param powerPlant The PowerPlant instance that will handle the received requests.
     */
    public EnergyRequestSubscriber(String brokerUri, String clientId, String topic, PowerPlant powerPlant) {
        this.brokerUri = brokerUri;
        this.clientId = clientId;
        this.topic = topic;
        this.powerPlant = powerPlant;
    }

    /**
     * Connects to the MQTT broker and subscribes to the energy request topic.
     *
     * @throws MqttException if connecting or subscribing fails.
     */
    public void start() throws MqttException {
        // Use a persistent file store for QoS 1 and 2 message states.
        MqttClientPersistence persistence = new MqttDefaultFilePersistence();
        mqttClient = new MqttClient(brokerUri, clientId, persistence);

        MqttConnectOptions connOpts = new MqttConnectOptions();
        connOpts.setCleanSession(true);
        connOpts.setAutomaticReconnect(true);

        logger.info("Connecting to MQTT broker at {} with client ID '{}'", brokerUri, clientId);
        mqttClient.setCallback(this);
        mqttClient.connect(connOpts);
        logger.info("Successfully connected to MQTT broker.");

        logger.info("Subscribing to topic '{}' with QoS {}", topic, QOS_LEVEL);
        mqttClient.subscribe(topic, QOS_LEVEL);
        logger.info("Successfully subscribed to topic '{}'", topic);
    }

    /**
     * Gracefully unsubscribes from the topic, disconnects from the broker, and closes the client.
     */
    public void stop() {
        if (this.mqttClient != null && this.mqttClient.isConnected()) {
            try {
                logger.info("Unsubscribing from topic '{}' for client '{}'", topic, clientId);
                mqttClient.unsubscribe(topic);
                logger.info("Disconnecting client '{}' from MQTT broker.", clientId);
                mqttClient.disconnect();
                logger.info("Successfully disconnected MQTT client.");
            } catch (MqttException e) {
                logger.error("Error during MQTT disconnection for client '{}'", clientId, e);
            } finally {
                try {
                    mqttClient.close();
                } catch (MqttException e) {
                    logger.error("Error closing MQTT client '{}'", clientId, e);
                }
            }
        }
    }

    @Override
    public void connectionLost(Throwable cause) {
        // The Paho client's automatic reconnect feature will handle reconnection attempts.
        logger.error("MQTT connection lost for client '{}'. Cause: {}", clientId, cause.getMessage(), cause);
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) {
        try {
            String payload = new String(message.getPayload());
            System.out.println(payload);

            EnergyRequest energyRequest = objectMapper.readValue(payload, EnergyRequest.class);

            // Validate the deserialized request before processing.
            if (energyRequest != null && energyRequest.requestID() != null && !energyRequest.requestID().trim().isEmpty()) {
                logger.debug("Forwarding valid energy request with ID '{}' for processing.", energyRequest.requestID());
                powerPlant.handleIncomingEnergyRequest(energyRequest);
            } else {
                logger.warn("Received invalid or malformed energy request. Payload: {}", payload);
            }
        } catch (JsonProcessingException e) {
            logger.error("Failed to deserialize JSON payload on topic '{}'. Payload: '{}'", topic, new String(message.getPayload()), e);
        } catch (Exception e) {
            logger.error("An unexpected error occurred while processing MQTT message on topic '{}'.", topic, e);
        }
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
        // Not used for a subscriber-only client.
    }
}