package desm.dps.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Manages application configuration by loading properties from a file.
 * This class follows the Singleton pattern to ensure a single, globally accessible
 * point for configuration data.
 */
public class AppConfig {

    private static final String CONFIG_FILE = "application.properties";
    private static AppConfig instance;
    private final Properties properties;

    /**
     * Private constructor to prevent direct instantiation. It loads configuration
     * from the application.properties file found in the classpath.
     *
     * @throws RuntimeException if the configuration file cannot be found or loaded.
     */
    private AppConfig() {
        properties = new Properties();
        // Use try-with-resources to ensure the InputStream is closed automatically
        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream(CONFIG_FILE)) {
            if (inputStream == null) {
                // Thrown if the properties file is not on the classpath
                throw new IOException("Configuration file '" + CONFIG_FILE + "' not found in the classpath.");
            }
            properties.load(inputStream);
        } catch (IOException e) {
            // Wraps IOException in a RuntimeException to avoid forcing callers to handle it
            throw new RuntimeException("Failed to load configuration file: " + CONFIG_FILE, e);
        }
    }

    /**
     * Provides thread-safe access to the singleton instance of AppConfig.
     *
     * @return The single, shared instance of AppConfig.
     */
    public static synchronized AppConfig getInstance() {
        if (instance == null) {
            instance = new AppConfig();
        }
        return instance;
    }

    // --- REST API Configuration ---

    /**
     * Gets the base URL for the REST API.
     *
     * @return The API base URL
     */
    public String getApiBaseUrl() {
        return properties.getProperty("rest.endpoint");
    }

    /**
     * Gets the specific endpoint for retrieving power plants.
     *
     * @return The power plants API endpoint
     */
    public String getPlantsEndpoint() {
        return properties.getProperty("rest.plants");
    }

    /**
     * Gets the specific endpoint for retrieving pollution data.
     *
     * @return The pollution data API endpoint
     */
    public String getPollutionEndpoint() {
        return properties.getProperty("rest.pollution");
    }
}
