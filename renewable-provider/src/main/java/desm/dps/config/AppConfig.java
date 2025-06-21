package desm.dps.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Configuration manager for loading and accessing application properties.
 */
public class AppConfig {

    private static final String CONFIG_FILE = "application.properties";
    private static AppConfig instance;
    private final Properties properties;

    /**
     * Private constructor that loads configuration from application.properties.
     *
     * @throws RuntimeException if configuration file cannot be found or loaded
     */
    private AppConfig() {
        properties = new Properties();
        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream(CONFIG_FILE)) {
            if (inputStream == null) {
                throw new IOException("Configuration file '" + CONFIG_FILE + "' not found in the classpath.");
            }
            properties.load(inputStream);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load configuration file: " + CONFIG_FILE, e);
        }
    }

    /**
     * Gets the singleton instance of the AppConfig.
     *
     * @return The single instance of AppConfig.
     */
    public static synchronized AppConfig getInstance() {
        if (instance == null) {
            instance = new AppConfig();
        }
        return instance;
    }

    // --- MQTT Configuration ---

    /**
     * Gets the MQTT broker URL.
     *
     * @return MQTT broker connection URL
     */
    public String getMqttBrokerUrl() {
        return properties.getProperty("mqtt.broker.url");
    }

    /**
     * Gets the MQTT topic for energy requests.
     *
     * @return MQTT topic for publishing energy requests
     */
    public String getMqttEnergyRequestsTopic() {
        return properties.getProperty("mqtt.topic.energy.requests");
    }

    /**
     * Gets the MQTT Quality of Service level.
     *
     * @return MQTT QoS level, defaults to 2 if not specified
     */
    public int getMqttQos() {
        return Integer.parseInt(properties.getProperty("mqtt.qos", "2"));
    }

    /**
     * Gets the MQTT client ID prefix.
     *
     * @return MQTT client ID prefix, defaults to "EnergyPublisher-" if not specified
     */
    public String getMqttClientIdPrefix() {
        return properties.getProperty("mqtt.client.id", "EnergyPublisher-");
    }
}
