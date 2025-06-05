package desm.dps;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Scanner;

public class RenewableProviderApp {
    // Logger for tracing application lifecycle events
    private static final Logger logger = LoggerFactory.getLogger(RenewableProviderApp.class);

    public static void main(String[] args) {
        logger.info("Starting Renewable Provider Application...");
        RenewableProvider provider = new RenewableProvider();

        // Start the provider (establish connections, begin publishing, etc.)
        provider.start();
        logger.info("RenewableProvider started.");

        // Add a shutdown hook to ensure the provider stops cleanly if the JVM is terminated
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Shutdown hook triggered. Stopping RenewableProvider...");
            provider.stop();
            logger.info("RenewableProvider stopped via shutdown hook.");
        }));

        // Wait for user to press Enter before shutting down
        Scanner scanner = new Scanner(System.in);
        logger.info("Press ENTER to stop the application...");
        scanner.nextLine();
        scanner.close();

        // If the provider is still running, stop it now
        if (provider.running) {
            logger.info("User requested shutdown. Stopping RenewableProvider...");
            provider.stop();
            logger.info("RenewableProvider stopped.");
        }

        logger.info("Renewable Provider Application finished.");
    }
}
