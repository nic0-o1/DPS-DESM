package desm.dps;

import java.util.Scanner;

public class PowerPlantApp {
    public static void main(String[] args) {
        // Read configuration from console
//        Scanner scanner = new Scanner(System.in);
//
//        System.out.println("===== Power Plant Configuration =====");
//        System.out.print("Enter Plant ID: ");
//        String plantId = scanner.nextLine().trim();
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
        String plantId = "003";
        String adminServerBaseUrl = "http://localhost:8080";
        String grpcHost = "localhost";
        String grpcPort = "8888";

        String mqttBrokerUrl =  "tcp://localhost:1883";
        String energyRequestTopic ="desm/energy/requests";

        // Initialize power plant
        PowerPlantInfo selfInfo = new PowerPlantInfo(plantId, "localhost", 56003);
        PowerPlant powerPlant = new PowerPlant(selfInfo, adminServerBaseUrl,mqttBrokerUrl, energyRequestTopic);

        try {
            // Start the power plant
            powerPlant.start();
            System.out.println("PowerPlant " + plantId + " started successfully on " + grpcHost + ":" + grpcPort);
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
            System.err.println("ERROR: Failed to start PowerPlant " + plantId + ": " + e.getMessage());
            e.printStackTrace();
            System.out.println("Attempting to shut down any initialized resources...");
            powerPlant.shutdown();
            System.err.println("PowerPlant startup failed. Exiting application.");
        }
    }
}
