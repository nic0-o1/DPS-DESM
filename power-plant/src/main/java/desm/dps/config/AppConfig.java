package desm.dps.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class AppConfig {

    private static final String CONFIG_FILE = "application.properties";
    private static AppConfig instance;
    private final Properties properties;

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

    // --- Getter methods for each property ---

    public String getPlantId() {
        return properties.getProperty("plant.id");
    }

    public int getPlantPort() {
        return Integer.parseInt(properties.getProperty("plant.port"));
    }

    public String getAdminServerBaseUrl() {
        return properties.getProperty("admin.server.base-url");
    }

    public String getMqttBrokerUrl() {
        return properties.getProperty("mqtt.broker.url");
    }

    public String getEnergyRequestTopic() {
        return properties.getProperty("mqtt.topic.energy-requests");
    }

    public String getPollutionPublishTopic() {
        return properties.getProperty("mqtt.topic.pollution-publish");
    }

    public double getMinPrice() {
        return Double.parseDouble(properties.getProperty("price.min", "0.1")); // default value
    }

    public double getMaxPrice() {
        return Double.parseDouble(properties.getProperty("price.max", "0.9")); // default value
    }
}
