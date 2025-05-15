package desm.dps;

import java.util.Scanner;

public class RenewableProviderApp {
    public static void main(String[] args) {
        System.out.println("Starting Renewable Provider Application...");
        RenewableProvider provider = new RenewableProvider();

        // Start the provider's request generation and publishing
        provider.start();

        // Add a shutdown hook to stop the provider gracefully on Ctrl+C or process termination
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Shutdown hook activated. Stopping provider...");
            provider.stop();
            System.out.println("Provider stopped by shutdown hook.");
        }));

        // Keep the main thread alive and wait for user input to stop (optional command-line control)
        System.out.println("Provider is running. Press Enter to stop.");
        Scanner scanner = new Scanner(System.in);
        scanner.nextLine(); // Wait for user input
        scanner.close();

        // Stopping the provider via the main thread if not already stopped by the shutdown hook
        // The shutdown hook is generally more reliable for external termination signals.
        if (provider.running) { // Check the volatile flag
            provider.stop();
        }

        System.out.println("Renewable Provider Application finished.");
    }
}