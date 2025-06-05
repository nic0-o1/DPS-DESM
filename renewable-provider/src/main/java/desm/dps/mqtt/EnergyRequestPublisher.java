package desm.dps.mqtt;

import com.fasterxml.jackson.databind.ObjectMapper;
import desm.dps.EnergyRequest;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

/**
 * A publisher class responsible for publishing energy requests to an MQTT
 * broker. All configuration (broker URL, topic, QoS, client ID prefix) is
 * loaded from application.properties.
 */
public class EnergyRequestPublisher {

	private static final Logger logger = LoggerFactory.getLogger(EnergyRequestPublisher.class);

	// Loaded from application.properties
	private final String brokerUrl;
	private final String topic;
	private final int qos;

    private final MqttClient client;
	private final ObjectMapper objectMapper = new ObjectMapper();

	public EnergyRequestPublisher() {
		// Load properties from application.properties
		Properties props = new Properties();
		try (InputStream in = getClass().getClassLoader().getResourceAsStream("application.properties")) {
			if (in == null) {
				throw new RuntimeException("Could not find application.properties on classpath");
			}
			props.load(in);
		} catch (IOException e) {
			logger.error("Failed to load application.properties: {}", e.getMessage(), e);
			throw new RuntimeException("Unable to load MQTT configuration", e);
		}

		this.brokerUrl = props.getProperty("mqtt.broker.url");
		this.topic = props.getProperty("mqtt.topic.energy.requests");
		this.qos = Integer.parseInt(props.getProperty("mqtt.qos", "2"));
        String clientIdPrefix = props.getProperty("mqtt.client.id", "EnergyPublisher-");

		try {
			// Generate a unique client ID by appending a random suffix
			String clientId = clientIdPrefix + MqttClient.generateClientId();
			client = new MqttClient(brokerUrl, clientId);

			MqttConnectOptions options = new MqttConnectOptions();
			options.setCleanSession(true);

			client.connect(options);
			logger.info("Connected to MQTT broker at {} with client ID '{}'", brokerUrl, clientId);

		} catch (MqttException e) {
			logger.error("Error connecting to MQTT broker at {}: {}", brokerUrl, e.getMessage(), e);
			throw new RuntimeException("MQTT connection failed", e);
		}
	}

	/**
	 * Publishes an energy request to the MQTT broker. The request is serialized
	 * to JSON and published with the configured QoS.
	 *
	 * @param energyRequest The energy request to be published
	 */
	public void publishRequest(EnergyRequest energyRequest) {
		if (client == null || !client.isConnected()) {
			logger.warn("MQTT client is not connected; cannot publish message");
			return;
		}

		try {
			String jsonRequest = objectMapper.writeValueAsString(energyRequest);
			MqttMessage message = new MqttMessage(jsonRequest.getBytes(StandardCharsets.UTF_8));
			message.setQos(qos);

			client.publish(topic, message);
			logger.info("Published energy request to topic '{}': {}", topic, energyRequest);

		} catch (MqttException e) {
			logger.error("Error publishing MQTT message to '{}' : {}", topic, e.getMessage(), e);
		} catch (Exception e) {
			logger.error("Error serializing or publishing message: {}", e.getMessage(), e);
		}
	}

	/**
	 * Disconnects from the MQTT broker. Should be called when the publisher is
	 * no longer needed to clean up resources.
	 */
	public void disconnect() {
		try {
			if (client != null && client.isConnected()) {
				client.disconnect();
				logger.info("Disconnected from MQTT broker at {}", brokerUrl);
			}
		} catch (MqttException e) {
			logger.error("Error disconnecting from MQTT broker at {}: {}", brokerUrl, e.getMessage(), e);
		}
	}
}
