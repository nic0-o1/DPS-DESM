package desm.dps.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Manages application configuration by loading properties from a resource file.
 *
 * This class implements the classic thread-safe Singleton pattern to ensure that
 * configuration is loaded only once and that a single, globally accessible instance
 * is used throughout the application.
 */
public class AppConfig {

    private static final String CONFIG_FILE = "application.properties";
    private static volatile AppConfig instance; // volatile to ensure visibility across threads
    private final Properties properties;

    /**
     * Private constructor to prevent direct instantiation and enforce the singleton pattern.
     * It loads properties from the classpath resource defined by {@code CONFIG_FILE}.
     *
     * @throws RuntimeException if the configuration file cannot be found or loaded.
     */
    private AppConfig() {
        properties = new Properties();
        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream(CONFIG_FILE)) {
            if (inputStream == null) {
                throw new IOException("Configuration file '" + CONFIG_FILE + "' not found in the classpath.");
            }
            properties.load(inputStream);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load configuration from classpath resource: " + CONFIG_FILE, e);
        }
    }

    /**
     * Provides global, thread-safe access to the singleton instance of {@code AppConfig}.
     * The instance is created on the first call (lazy initialization).
     *
     * @return The single instance of {@code AppConfig}.
     */
    public static AppConfig getInstance() {
        // Double-Checked Locking for lazy initialization in a multithreaded environment.
        if (instance == null) {
            synchronized (AppConfig.class) {
                if (instance == null) {
                    instance = new AppConfig();
                }
            }
        }
        return instance;
    }

    // --- GETTER METHODS FOR CONFIGURATION PROPERTIES ---

    /** @return The configured plant ID from the "plant.id" property. */
    public String getPlantId() {
        return properties.getProperty("plant.id");
    }

    /** @return The configured plant port from the "plant.port" property. */
    public int getPlantPort() {
        return Integer.parseInt(properties.getProperty("plant.port"));
    }

    /** @return The configured admin server base URL from the "admin.server.base-url" property. */
    public String getAdminServerBaseUrl() {
        return properties.getProperty("admin.server.base-url");
    }

    /** @return The configured MQTT broker URL from the "mqtt.broker.url" property. */
    public String getMqttBrokerUrl() {
        return properties.getProperty("mqtt.broker.url");
    }

    /** @return The configured energy requests topic from the "mqtt.topic.energy-requests" property. */
    public String getEnergyRequestTopic() {
        return properties.getProperty("mqtt.topic.energy-requests");
    }

    /** @return The configured pollution data topic from the "mqtt.topic.pollution-publish" property. */
    public String getPollutionPublishTopic() {
        return properties.getProperty("mqtt.topic.pollution-publish");
    }

    /** @return The configured minimum price from the "price.min" property, or 0.1 if not specified. */
    public double getMinPrice() {
        return Double.parseDouble(properties.getProperty("price.min", "0.1"));
    }

    /** @return The configured maximum price from the "price.max" property, or 0.9 if not specified. */
    public double getMaxPrice() {
        return Double.parseDouble(properties.getProperty("price.max", "0.9"));
    }
}