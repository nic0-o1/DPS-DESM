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
 * MQTT publisher responsible for distributing energy requests to message broker.
 */
public class EnergyRequestPublisher {

	private static final Logger logger = LoggerFactory.getLogger(EnergyRequestPublisher.class);

	private final String brokerUrl;      // MQTT broker connection URL
	private final String topic;          // Topic for energy request messages
	private final int qos;               // Quality of Service level for message delivery

	private final MqttClient client;

	private final ObjectMapper objectMapper = new ObjectMapper();

	/**
	 * Initializes the MQTT publisher with configuration from AppConfig.
	 *
	 * @throws RuntimeException if configuration loading or MQTT connection fails
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
	 * Creates MQTT client instance and establishes broker connection.
	 *
	 * @param clientIdPrefix Prefix for generating unique client identifier
	 * @return Connected MqttClient instance ready for publishing
	 * @throws RuntimeException if MQTT connection cannot be established
	 */
	private MqttClient createAndConnectMqttClient(String clientIdPrefix) {
		try {
			String uniqueClientId = clientIdPrefix + MqttClient.generateClientId();
			MqttClient mqttClient = new MqttClient(brokerUrl, uniqueClientId);

			MqttConnectOptions connectionOptions = new MqttConnectOptions();
			connectionOptions.setCleanSession(true);

			mqttClient.connect(connectionOptions);
			logger.info("Connected to MQTT broker at {} with client ID '{}'", brokerUrl, uniqueClientId);

			return mqttClient;

		} catch (MqttException e) {
			logger.error("Error connecting to MQTT broker at {}: {}", brokerUrl, e.getMessage(), e);
			throw new RuntimeException("MQTT connection failed", e);
		}
	}

	/**
	 * Publishes an energy request to the configured MQTT topic.
	 *
	 * @param energyRequest The energy request object to be published as JSON message
	 */
	public void publishRequest(EnergyRequest energyRequest) {
		if (client == null || !client.isConnected()) {
			logger.warn("MQTT client is not connected; cannot publish message");
			return;
		}

		try {
			String jsonPayload = objectMapper.writeValueAsString(energyRequest);

			MqttMessage mqttMessage = new MqttMessage(jsonPayload.getBytes(StandardCharsets.UTF_8));
			mqttMessage.setQos(qos);

			client.publish(topic, mqttMessage);
			logger.info("Published energy request to topic '{}': {}", topic, energyRequest);

		} catch (MqttException e) {
			logger.error("Error publishing MQTT message to '{}' : {}", topic, e.getMessage(), e);
		} catch (Exception e) {
			logger.error("Error serializing or publishing message: {}", e.getMessage(), e);
		}
	}

	/**
	 * Cleanly disconnects from MQTT broker and releases client resources.
	 *
	 */
	public void disconnect() {
		try {
			// Only disconnect if client exists and is currently connected
			if (client != null && client.isConnected()) {
				client.disconnect();
				logger.info("Disconnected from MQTT broker at {}", brokerUrl);
			}
		} catch (MqttException e) {
			logger.error("Error disconnecting from MQTT broker at {}: {}", brokerUrl, e.getMessage(), e);
		}
	}
}
