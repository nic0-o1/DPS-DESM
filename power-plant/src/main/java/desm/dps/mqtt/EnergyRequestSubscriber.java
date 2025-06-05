package desm.dps.mqtt;

import com.fasterxml.jackson.databind.ObjectMapper;
import desm.dps.EnergyRequest;
import desm.dps.PowerPlant;
import org.eclipse.paho.client.mqttv3.*;

public class EnergyRequestSubscriber implements MqttCallback {
    private final String brokerUrl;
    private final String clientId;
    private final String topic;
    private MqttClient mqttClient;
    private final PowerPlant powerPlant;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final int QOS = 2;


    public EnergyRequestSubscriber(String brokerUrl, String clientId, String topic, PowerPlant powerPlant) {
        this.brokerUrl = brokerUrl;
        this.clientId = clientId;
        this.topic = topic;
        this.powerPlant = powerPlant;
    }

    public void start() throws MqttException {
        mqttClient = new MqttClient(brokerUrl, clientId);
        MqttConnectOptions connOpts = new MqttConnectOptions();
        connOpts.setCleanSession(true); // Or false for persistent session [cite: 497]
        // connOpts.setAutomaticReconnect(true); // Useful for robustness

        System.out.println("Connecting to MQTT broker: " + brokerUrl + " with client ID: " + clientId);
        mqttClient.setCallback(this);
        mqttClient.connect(connOpts);
        System.out.println("Connected to MQTT broker. Subscribing to topic: " + topic);
        mqttClient.subscribe(topic, QOS); // QoS 2 for "exactly once"
        System.out.println("Subscribed to topic: " + topic + " with QoS " + QOS);
    }

    public void stop() throws MqttException {
        if (mqttClient != null && mqttClient.isConnected()) {
            mqttClient.unsubscribe(topic);
            mqttClient.disconnect();
            mqttClient.close();
            System.out.println("Disconnected from MQTT broker and closed client " + clientId);
        }
    }

    @Override
    public void connectionLost(Throwable cause) {
        System.err.println("MQTT connection lost for client " + clientId + ": " + cause.getMessage());
        // Implement reconnection logic if needed and not handled by setAutomaticReconnect
        // For example, schedule a reconnect attempt.
        // Be careful with immediate, tight-loop reconnection. Add delays/backoff.
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) {
        try {
            String payload = new String(message.getPayload());
            System.out.println("Energy request received on topic '" + topic + "':");
            System.out.println(payload);

            EnergyRequest energyRequest = objectMapper.readValue(payload, EnergyRequest.class);
            if (energyRequest != null && energyRequest.getRequestID() != null) {
                System.out.println("Processing energy request with ID: " + energyRequest.getRequestID());
                powerPlant.handleIncomingEnergyRequest(energyRequest);
            } else {
                System.err.println("Invalid energy request received - missing requestID: " + payload);
            }
        } catch (Exception e) {
            System.err.println("Error processing MQTT message on topic " + topic + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
    }
}