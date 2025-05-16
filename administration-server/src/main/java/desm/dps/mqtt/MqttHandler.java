package desm.dps.mqtt;

import com.fasterxml.jackson.databind.ObjectMapper;
import desm.dps.MeasurementBatch;
import org.eclipse.paho.client.mqttv3.*;

public class MqttHandler implements MqttCallback {

    private static final String MQTT_BROKER_URL = "tcp://localhost:1883"; // Needs configuration
    private static final String POLLUTION_TOPIC = "desm/pollution/batches"; // Topic plants publish to
    private static final int QOS = 2;

    private MqttClient client;
    //private final MeasurementRepository measurementRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public MqttHandler(){
        connect();
    }

    private void connect(){
        try {
            String clientId = MqttClient.generateClientId();
            client = new MqttClient(MQTT_BROKER_URL, clientId);

            MqttConnectOptions options = new MqttConnectOptions();
            options.setCleanSession(true);

            client.setCallback(this);

            client.connect(options);
            System.out.println("Admin Server: Connected to MQTT broker.");

            client.subscribe(POLLUTION_TOPIC, QOS);
            System.out.println("Admin Server: Subscribed to MQTT topic: " + POLLUTION_TOPIC + " with QoS " + QOS);

        } catch (MqttException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void connectionLost(Throwable throwable) {

    }

    @Override
    public void messageArrived(String s, MqttMessage mqttMessage) throws Exception {
        String payload = new String(mqttMessage.getPayload());
        System.out.println("Admin Server: Received message: " + payload);

        try {
            MeasurementBatch batch = objectMapper.readValue(payload, MeasurementBatch.class);

            //measurementRepository.addMeasurementBatch(batch);
        }
        catch (Exception e) {
            System.err.println("Admin Server: Error processing received MQTT message: " + e.getMessage());
            e.printStackTrace();
        }

    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken iMqttDeliveryToken) {

    }
}
