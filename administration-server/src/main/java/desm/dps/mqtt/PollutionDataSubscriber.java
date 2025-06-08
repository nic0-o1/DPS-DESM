package desm.dps.mqtt;

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

@Component
public class PollutionDataSubscriber implements MqttCallback {

    private static final Logger logger = LoggerFactory.getLogger(PollutionDataSubscriber.class);

    @Value("${mqtt.broker.url:tcp://localhost:1883}")
    private String brokerUrl;

    @Value("${mqtt.topic.pollution:desm/pollution/data}")
    private String topic;

    @Value("${mqtt.client.id:adminServer}")
    private String baseClientId;

    @Value("${mqtt.qos:1}")
    private int qos;

    private IMqttClient mqttClient;
    private final MeasurementRepository measurementRepository;
    private final ObjectMapper objectMapper;
    private final String clientId;

    public PollutionDataSubscriber(MeasurementRepository measurementRepository) {
        this.measurementRepository = measurementRepository;
        this.objectMapper = new ObjectMapper();
        this.clientId = baseClientId + "_admin_pollution_subscriber";
    }

    @PostConstruct
    public void start() {
        try {
            initializeMqttClient();
            connectToMqttBroker();
            subscribeToTopic();
        } catch (MqttException e) {
            logger.error("Failed to initialize MQTT subscriber", e);
            throw new RuntimeException("MQTT initialization failed", e);
        }
    }

    @PreDestroy
    public void stop() {
        try {
            if (mqttClient != null && mqttClient.isConnected()) {
                mqttClient.unsubscribe(topic);
                mqttClient.disconnect();
                logger.info("PollutionDataSubscriber disconnected from MQTT broker");
            }
            if (mqttClient != null) {
                mqttClient.close();
            }
        } catch (MqttException e) {
            logger.error("Error stopping PollutionDataSubscriber: {}", e.getMessage());
        }
    }

    private void initializeMqttClient() throws MqttException {
        mqttClient = new MqttClient(brokerUrl, clientId, new MemoryPersistence());
        mqttClient.setCallback(this);
    }

    private void connectToMqttBroker() throws MqttException {
        MqttConnectOptions connOpts = new MqttConnectOptions();
        connOpts.setCleanSession(true);
        connOpts.setAutomaticReconnect(true);

        logger.info("PollutionDataSubscriber connecting to MQTT broker: {} with client ID: {}",
                brokerUrl, clientId);
        mqttClient.connect(connOpts);
        logger.info("PollutionDataSubscriber connected to MQTT broker");
    }

    private void subscribeToTopic() throws MqttException {
        mqttClient.subscribe(topic, qos);
        logger.info("PollutionDataSubscriber subscribed to topic: {} with QoS {}", topic, qos);
    }

    @Override
    public void connectionLost(Throwable cause) {
        logger.error("MQTT connection lost for PollutionDataSubscriber (client {}): {}",
                clientId, cause.getMessage(), cause);
        // Automatic reconnect should handle this if enabled
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) {
        try {
            String payload = new String(message.getPayload());
            logger.info("Received pollution data on topic '{}': {}", topic, payload);

            PollutionData pollutionData = objectMapper.readValue(payload, PollutionData.class);

            if (isValidPollutionData(pollutionData)) {
                measurementRepository.addPollutionData(pollutionData);
                logger.debug("Successfully stored pollution data from plant: {}", pollutionData.plantId());
            } else {
                logger.warn("Invalid pollution data received (null or missing plantId): {}", payload);
            }
        } catch (Exception e) {
            logger.error("Error processing MQTT message on topic {}: {}", topic, e.getMessage(), e);
        }
    }

    private boolean isValidPollutionData(PollutionData pollutionData) {
        return pollutionData != null && pollutionData.plantId() != null;
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
        // Not used for subscriber clients
    }
}