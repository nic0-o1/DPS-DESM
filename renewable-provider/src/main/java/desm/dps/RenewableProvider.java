package desm.dps;

import desm.dps.mqtt.EnergyRequestPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * RenewableProvider is responsible for generating and publishing energy requests
 * at regular intervals using a background thread.
 */
public class RenewableProvider {
    private static final Logger logger = LoggerFactory.getLogger(RenewableProvider.class);

    /** Interval between energy request publications in milliseconds */
    private static final long PUBLISH_INTERVAL_MS = 10 * 1000; // 10 seconds in milliseconds

    /** Generator for creating energy requests */
    private final RequestGenerator requestGenerator;

    /** Publisher for sending energy requests via MQTT */
    private final EnergyRequestPublisher requestPublisher;

    /** Flag indicating whether the provider is currently running */
    volatile boolean running = false;

    /** Background thread that handles the request publishing loop */
    private Thread providerThread;

    /**
     * Constructs a new RenewableProvider with default request generator and publisher.
     */
    public RenewableProvider(){
        this.requestGenerator = new RequestGenerator();
        this.requestPublisher = new EnergyRequestPublisher();
        logger.info("RenewableProvider initialized successfully");
    }

    /**
     * Starts the renewable provider, beginning the periodic publication of energy requests.
     * If the provider is already running, this method has no effect.
     */
    public void start(){
        if(running){
            logger.warn("Renewable Provider is already running - ignoring start request");
            return;
        }

        running = true;
        logger.info("Starting Renewable Provider - will publish requests every {} seconds",
                PUBLISH_INTERVAL_MS / 1000);

        // Create and start the background thread for publishing requests
        providerThread = new Thread(() -> {
            while (running) {
                try {
                    // Generate and publish an energy request
                    EnergyRequest request = requestGenerator.generateRequest();

                    logger.debug("Publishing energy request: {}", request);
                    requestPublisher.publishRequest(request);
                    logger.info("Successfully published energy request");

                    // Wait for the specified interval before next publication
                    logger.debug("Sleeping for {} ms before next request", PUBLISH_INTERVAL_MS);
                    Thread.sleep(PUBLISH_INTERVAL_MS);

                    // Set running to false after one iteration (preserving original logic)
                    running = false;
                    logger.debug("Setting running flag to false after single iteration");

                } catch (InterruptedException e) {
                    logger.info("Renewable Provider thread interrupted - stopping gracefully");
                    Thread.currentThread().interrupt();
                    running = false; // Stop the loop on interruption
                } catch (Exception e) {
                    logger.error("Error in Renewable Provider thread: {}", e.getMessage(), e);
                    // Depending on requirements, you might want to continue or stop on other errors
                }
            }

            logger.info("Provider thread stopping - disconnecting MQTT client");
            requestPublisher.disconnect(); // Disconnect MQTT client when the thread stops
            logger.debug("Renewable Provider thread stopped");
        }, "RenewableProvider-Thread");

        providerThread.start();
        logger.info("Renewable Provider started successfully");
    }

    /**
     * Stops the renewable provider, terminating the background thread and disconnecting resources.
     * If the provider is not running, this method has no effect.
     */
    public void stop() {
        if (!running) {
            logger.warn("Renewable Provider is not running - ignoring stop request");
            return;
        }

        logger.info("Stopping Renewable Provider...");
        running = false; // Signal the thread to stop

        // Interrupt the provider thread if it exists
        if (providerThread != null) {
            logger.debug("Interrupting provider thread");
            providerThread.interrupt();
        }

        // Wait for the thread to complete
        try {
            if (providerThread != null) {
                logger.debug("Waiting for provider thread to join");
                providerThread.join();
                logger.debug("Provider thread joined successfully");
            }
        } catch (InterruptedException e) {
            logger.error("Interrupted while waiting for provider thread to stop", e);
            Thread.currentThread().interrupt();
        }

        logger.info("Renewable Provider stopped successfully");
    }
}