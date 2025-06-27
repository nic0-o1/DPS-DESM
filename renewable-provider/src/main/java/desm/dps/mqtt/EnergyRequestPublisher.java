package desm.dps.mqtt;

import com.fasterxml.jackson.databind.ObjectMapper;
import desm.dps.EnergyRequest;
import desm.dps.config.AppConfig;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;

/**
 * A client responsible for publishing {@link EnergyRequest} messages to an MQTT topic.
 * This class handles its own connection lifecycle, establishing a connection upon
 * instantiation and providing a method to disconnect.
 */
public class EnergyRequestPublisher {

	private static final Logger logger = LoggerFactory.getLogger(EnergyRequestPublisher.class);

	private final String brokerUrl;
	private final String topic;
	private final int qos;
	private final MqttClient client;
	private final ObjectMapper objectMapper = new ObjectMapper();

	/**
	 * Initializes the MQTT publisher by loading configuration from {@link AppConfig}
	 * and establishing a connection to the MQTT broker.
	 *
	 * @throws RuntimeException if configuration loading or the MQTT connection fails,
	 *                          to prevent the application from using a non-functional publisher.
	 */
	public EnergyRequestPublisher() {
		AppConfig config = AppConfig.getInstance();
		this.brokerUrl = config.getMqttBrokerUrl();
		this.topic = config.getMqttEnergyRequestsTopic();
		this.qos = config.getMqttQos();
		String clientIdPrefix = config.getMqttClientIdPrefix();
		this.client = createAndConnectMqttClient(clientIdPrefix);
	}

	/**
	 * Creates an MQTT client instance and establishes a connection to the broker.
	 *
	 * @param clientIdPrefix The prefix used to generate a unique client identifier.
	 * @return A connected {@link MqttClient} instance ready for publishing.
	 * @throws RuntimeException if the MQTT connection cannot be established.
	 */
	private MqttClient createAndConnectMqttClient(String clientIdPrefix) {
		try {
			String uniqueClientId = clientIdPrefix + MqttClient.generateClientId();
			MqttClient mqttClient = new MqttClient(brokerUrl, uniqueClientId);

			MqttConnectOptions connectionOptions = new MqttConnectOptions();
			connectionOptions.setCleanSession(true);

			logger.info("Connecting to MQTT broker at {} with client ID '{}'", brokerUrl, uniqueClientId);
			mqttClient.connect(connectionOptions);
			logger.info("Successfully connected to MQTT broker.");

			return mqttClient;
		} catch (MqttException e) {
			logger.error("Error connecting to MQTT broker at {}: {}", brokerUrl, e.getMessage(), e);
			throw new RuntimeException("MQTT connection failed. See logs for details.", e);
		}
	}

	/**
	 * Serializes an energy request to JSON and publishes it to the configured MQTT topic.
	 *
	 * @param energyRequest The energy request object to publish.
	 */
	public void publishRequest(EnergyRequest energyRequest) {
		if (client == null || !client.isConnected()) {
			logger.warn("MQTT client is not connected; cannot publish energy request {}.", energyRequest.requestID());
			return;
		}

		try {
			String jsonPayload = objectMapper.writeValueAsString(energyRequest);
			MqttMessage mqttMessage = new MqttMessage(jsonPayload.getBytes(StandardCharsets.UTF_8));
			mqttMessage.setQos(qos);

			client.publish(topic, mqttMessage);
			logger.info("Published energy request to topic '{}': {}", topic, energyRequest);

		} catch (MqttException e) {
			logger.error("Error publishing MQTT message for request ID {} to topic '{}'. Cause: {}",
					energyRequest.requestID(), topic, e.getMessage(), e);
		} catch (Exception e) {
			logger.error("An unexpected error occurred while publishing request ID {}. Cause: {}",
					energyRequest.requestID(), e.getMessage(), e);
		}
	}

	/**
	 * Cleanly disconnects from the MQTT broker and releases client resources.
	 */
	public void disconnect() {
		if (client != null && client.isConnected()) {
			try {
				client.disconnect();
				logger.info("Disconnected from MQTT broker at {}.", brokerUrl);
			} catch (MqttException e) {
				logger.error("Error while disconnecting from MQTT broker at {}: {}", brokerUrl, e.getMessage(), e);
			}
		}
	}
}