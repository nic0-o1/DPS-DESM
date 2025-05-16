package desm.dps;

import java.util.Scanner;

public class RenewableProviderApp {
    public static void main(String[] args) {
        System.out.println("Starting Renewable Provider Application...");
        RenewableProvider provider = new RenewableProvider();

        provider.start();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Shutdown hook activated. Stopping provider...");
            provider.stop();
            System.out.println("Provider stopped by shutdown hook.");
        }));

        Scanner scanner = new Scanner(System.in);
        scanner.nextLine(); // Wait for user input
        scanner.close();

        if (provider.running) {
            provider.stop();
        }

        System.out.println("Renewable Provider Application finished.");
    }
}