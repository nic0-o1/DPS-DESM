package desm.dps;

import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.paho.client.mqttv3.MqttMessage;

import java.nio.charset.StandardCharsets;

public class EnergyRequestPublisher {
    private static final String MQTT_BROKER_URL = "tcp://localhost:1883";
    private static final String ENERGY_REQUEST_TOPIC = "desm/energy/requests";

    private MqttClient client;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public EnergyRequestPublisher() {
        try {
            String publisherId = MqttClient.generateClientId();
            client = new MqttClient(MQTT_BROKER_URL, publisherId);
            MqttConnectOptions options = new MqttConnectOptions();
            options.setCleanSession(true);
            client.connect(options);

            client.connect(options);

            System.out.println("Renewable Provider connected to MQTT broker at " + MQTT_BROKER_URL);


        }
        catch (MqttException e) {
            System.err.println("Error connecting to MQTT broker: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void publishRequest(EnergyRequest energyRequest) {
        if (client == null || !client.isConnected()) {
            System.err.println("MQTT client not connected. Cannot publish message.");
            return;
        }

        try {
            // Convert EnergyRequest object to JSON string
            String jsonRequest = objectMapper.writeValueAsString(energyRequest);
            MqttMessage message = new MqttMessage(jsonRequest.getBytes(StandardCharsets.UTF_8));
            message.setQos(2); // Quality of Service 1: At least once delivery
            message.setRetained(true); // Retain the message so new subscribers get the last request

            client.publish(ENERGY_REQUEST_TOPIC, message);
            System.out.println("Published energy request: " + energyRequest);
        } catch (MqttException e) {
            System.err.println("Error publishing MQTT message: " + e.getMessage());
            e.printStackTrace();
            // Handle publish error
        } catch (Exception e) {
            System.err.println("Error serializing or publishing message: " + e.getMessage());
            e.printStackTrace();
        }
    }
    public void disconnect() {
        try {
            if (client != null && client.isConnected()) {
                client.disconnect();
                System.out.println("Renewable Provider disconnected from MQTT broker.");
            }
        } catch (MqttException e) {
            System.err.println("Error disconnecting from MQTT broker: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
