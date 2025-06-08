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
 * Handles subscribing to an MQTT topic to receive energy requests.
 * This class is responsible for establishing a connection to the MQTT broker,
 * subscribing to a specific topic, and processing incoming messages by deserializing
 * them into EnergyRequest objects and forwarding them to the PowerPlant.
 */
public class EnergyRequestSubscriber implements MqttCallback {

    private static final Logger logger = LoggerFactory.getLogger(EnergyRequestSubscriber.class);

    private static final int QOS_LEVEL = 2;

    private final String brokerUri;
    private final String clientId;
    private final String topic;
    private final PowerPlant powerPlant;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private volatile MqttClient mqttClient;

    /**
     * Constructs a new EnergyRequestSubscriber.
     *
     * @param brokerUri   The full URI of the MQTT broker (e.g., "tcp://localhost:1883").
     * @param clientId    A unique identifier for this MQTT client.
     * @param topic       The topic to subscribe to for energy requests.
     * @param powerPlant  The PowerPlant instance that will handle the received requests.
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
        // Use a persistent memory store for QoS 1 and 2 messages.
        MqttClientPersistence persistence = new MqttDefaultFilePersistence();
        mqttClient = new MqttClient(brokerUri, clientId, persistence);

        MqttConnectOptions connOpts = createConnectionOptions();

        logger.info("Connecting to MQTT broker at {} with client ID '{}'", brokerUri, clientId);
        mqttClient.setCallback(this);
        mqttClient.connect(connOpts);
        logger.info("Successfully connected to MQTT broker.");

        logger.info("Subscribing to topic '{}' with QoS {}", topic, QOS_LEVEL);
        mqttClient.subscribe(topic, QOS_LEVEL);
        logger.info("Successfully subscribed to topic '{}'", topic);
    }

    /**
     * Creates and configures the connection options for the MQTT client.
     *
     * @return A configured MqttConnectOptions object.
     */
    private MqttConnectOptions createConnectionOptions() {
        MqttConnectOptions connOpts = new MqttConnectOptions();
        // A clean session means the broker discards all state for the client upon disconnection.
        // For durable subscriptions, this should be set to false.
        connOpts.setCleanSession(true);
        // Enable automatic reconnection attempts by the Paho client library.
        connOpts.setAutomaticReconnect(true);
        return connOpts;
    }

    /**
     * Unsubscribes from the topic, disconnects from the broker, and closes the client.
     * This method is safe to call even if the client is already disconnected.
     */
    public void stop() {
        // Use a local reference to the volatile client for thread safety.
        MqttClient client = this.mqttClient;
        if (client != null && client.isConnected()) {
            try {
                logger.info("Unsubscribing from topic '{}' for client '{}'", topic, clientId);
                client.unsubscribe(topic);
                logger.info("Disconnecting client '{}' from MQTT broker.", clientId);
                client.disconnect();
                logger.info("Successfully disconnected.");
            } catch (MqttException e) {
                logger.error("Error during MQTT disconnection for client '{}': {}", clientId, e.getMessage(), e);
            } finally {
                try {
                    client.close();
                } catch (MqttException e) {
                    logger.error("Error closing MQTT client '{}': {}", clientId, e.getMessage(), e);
                }
            }
        }
    }

    @Override
    public void connectionLost(Throwable cause) {
        // This callback is triggered when the connection is lost unexpectedly.
        // Since setAutomaticReconnect(true) is used, the Paho library will handle
        // reconnection attempts in the background. We log this event for monitoring.
        logger.error("MQTT connection lost for client '{}'. Cause: {}", clientId, cause.getMessage(), cause);
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) {
        try {
            String payload = new String(message.getPayload());
            logger.info("Energy request received on topic '{}':\n{}", topic, payload);

            EnergyRequest energyRequest = objectMapper.readValue(payload, EnergyRequest.class);

            // Validate the deserialized request before processing.
            if (energyRequest != null && energyRequest.getRequestID() != null && !energyRequest.getRequestID().trim().isEmpty()) {
                logger.debug("Forwarding valid energy request with ID '{}' for processing.", energyRequest.getRequestID());
                powerPlant.handleIncomingEnergyRequest(energyRequest);
            } else {
                logger.warn("Invalid or malformed energy request received. Payload: {}", payload);
            }
        } catch (JsonProcessingException e) {
            logger.error("Failed to deserialize JSON payload on topic '{}'. Error: {}", topic, e.getMessage());
        } catch (Exception e) {
            // Catching a broader exception to ensure the callback thread never dies.
            logger.error("An unexpected error occurred while processing MQTT message on topic '{}'.", topic, e);
        }
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
        // This callback is for when this client *publishes* a message.
        // Since this class is a subscriber-only, this method is not used.
    }
}