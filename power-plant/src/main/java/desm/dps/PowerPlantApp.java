package desm.dps;

import desm.dps.config.AppConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Scanner;

public class PowerPlantApp {
    private static final Logger logger = LoggerFactory.getLogger(PowerPlantApp.class);

    public static void main(String[] args) {


        // Read configuration from console
//        Scanner scanner = new Scanner(System.in);
//
//        System.out.println("===== Power Plant Configuration =====");
//        System.out.print("Enter Plant ID: ");
//        int plantId;
//        try {
//            plantId = Integer.parseInt(scanner.nextLine().trim());
//        } catch (NumberFormatException e) {
//            System.err.println("Error: Plant ID must be a valid integer");
//            scanner.close();
//            System.exit(1);
//            return;
//        }
//
//        System.out.print("Enter gRPC Host: ");
//        String grpcHost = scanner.nextLine().trim();
//
//        System.out.print("Enter gRPC Port: ");
//        int grpcPort;
//        try {
//            grpcPort = Integer.parseInt(scanner.nextLine().trim());
//        } catch (NumberFormatException e) {
//            System.err.println("Error: Port must be a valid integer");
//            scanner.close();
//            System.exit(1);
//            return;
//        }
//
//        System.out.print("Enter Admin Server Base URL: ");
//        String adminServerBaseUrl = scanner.nextLine().trim();
//
//        scanner.close();

        AppConfig config = AppConfig.getInstance();

        int plantId = 2;
        int plantPort = 56002;

        String adminServerBaseUrl = config.getAdminServerBaseUrl();
        String mqttBrokerUrl = config.getMqttBrokerUrl();
        String energyRequestTopic = config.getEnergyRequestTopic();
        String pollutionPublishTopic = config.getPollutionPublishTopic();

        // Initialize power plant
        PowerPlantInfo selfInfo = new PowerPlantInfo(plantId, "localhost", plantPort);
        PowerPlant powerPlant = new PowerPlant(selfInfo, adminServerBaseUrl, mqttBrokerUrl, energyRequestTopic, pollutionPublishTopic);

        try {
            // Start the power plant
            powerPlant.start();
            System.out.println("PowerPlant " + plantId + " started successfully");
            System.out.println("Connected to admin server at: " + adminServerBaseUrl);

            // Keep program running until user chooses to exit
            System.out.println("PowerPlant is running.");
            System.out.println("Enter 'exit' or 'quit' to shut down the PowerPlant:");

            // Create a new scanner for command input
            Scanner commandScanner = new Scanner(System.in);
            String command;

            // Listen for exit commands
            while (true) {
                command = commandScanner.nextLine().trim().toLowerCase();
                if (command.equals("exit") || command.equals("quit")) {
                    break;
                } else if (!command.isEmpty()) {
                    System.out.println("Unknown command. Enter 'exit' or 'quit' to shut down.");
                }
            }

            // Clean shutdown when user exits
            System.out.println("Shutting down PowerPlant " + plantId + "...");
            powerPlant.shutdown();
            System.out.println("PowerPlant " + plantId + " shut down successfully.");
            commandScanner.close();
        } catch (Exception e) {
            if (isPortBindingError(e)) {
                logger.error("STARTUP FAILED: PowerPlant {} could not bind to port {} - port already in use", plantId, plantPort);
            } else {
                logger.error("STARTUP FAILED: PowerPlant {} encountered an unexpected error during startup {}", plantId, e.getMessage());
            }

            // Attempt graceful shutdown of any initialized resources
            try {
                powerPlant.shutdown();
            } catch (Exception shutdownException) {
                logger.warn("Error during shutdown: {}", e.getMessage());
            }
        }
    }

    // Helper method
    private static boolean isPortBindingError(Throwable e) {
        Throwable current = e;
        while (current != null) {
            if (current instanceof java.net.BindException ||
                    (current.getMessage() != null && current.getMessage().contains("Failed to bind"))) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}