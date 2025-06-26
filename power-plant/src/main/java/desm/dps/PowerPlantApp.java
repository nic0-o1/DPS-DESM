package desm.dps;

import desm.dps.config.AppConfig;
import desm.dps.grpc.PortInUseException;
import desm.dps.rest.RegistrationConflictException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Scanner;

public class PowerPlantApp {
    private static final Logger logger = LoggerFactory.getLogger(PowerPlantApp.class);
    private static final Scanner scanner = new Scanner(System.in);
    private static final AppConfig config = AppConfig.getInstance();

    public static void main(String[] args) {
        PowerPlant powerPlant = startPowerPlant();

        if (powerPlant == null) {
            logger.error("Could not start the power plant. Exiting.");
            System.exit(1);
        }

        runCommandLoop(powerPlant);

        scanner.close();
        logger.info("Application shut down.");
    }

    /**
     * The main application loop. It orchestrates the entire startup process,
     * handling retries for different failure scenarios.
     * @return The successfully started PowerPlant instance, or null if startup fails permanently.
     */
    private static PowerPlant startPowerPlant() {
        PowerPlant powerPlant = null;
        int plantId = -1;
        boolean isFirstAttempt = true; // Flag to control initial header printing

        while (true) {
            try {
                // Only show the main header on the very first run
                if (isFirstAttempt) {
                    System.out.println("===== Power Plant Configuration =====");
                    isFirstAttempt = false; // Set the flag to false after the first run
                }

                plantId = getPlantIdFromUser();
                int plantPort = getPortFromUser();

                // Attempt to create and start the plant.
                PowerPlantInfo selfInfo = new PowerPlantInfo(plantId, "localhost", plantPort, System.currentTimeMillis());
                powerPlant = new PowerPlant(selfInfo, config.getAdminServerBaseUrl(), config.getMqttBrokerUrl(), config.getEnergyRequestTopic(), config.getPollutionPublishTopic());

                powerPlant.start();

                System.out.println("\nPowerPlant " + plantId + " started successfully.");
                System.out.println("Connected to admin server at: " + config.getAdminServerBaseUrl());
                return powerPlant; // Success! Return the instance.

            } catch (RegistrationConflictException e) {
                if (powerPlant != null) powerPlant.shutdown();
                System.out.println("\n--- REGISTRATION FAILED ---");
                System.out.println("REASON: " + e.getMessage());
                System.out.println("Please choose a different Plant ID.\n");
                logger.error("STARTUP ERROR: {}", e.getMessage());

            } catch (PortInUseException e) {
                if (powerPlant != null) powerPlant.shutdown();
                System.out.println("\n--- STARTUP FAILED ---");
                System.out.println("REASON: " + e.getMessage());
                System.out.println("Please choose a different Port.\n");
                logger.error("STARTUP ERROR: {}", e.getMessage());

            } catch (Exception e) {
                logger.error("FATAL STARTUP ERROR for plant {}: {}", plantId, e.getMessage(), e);
                if (powerPlant != null) powerPlant.shutdown();
                return null; // Fatal error, cannot recover.
            }
        }
    }

    /**
     * Prompts the user for a Plant ID until a valid integer is provided.
     * @return The parsed integer ID.
     */
    private static int getPlantIdFromUser() {
        while (true) {
            try {
                System.out.print("Enter Plant ID: ");
                String input = scanner.nextLine().trim();
                if (input.isEmpty()) {
                    // *** FIX: Print to System.out to guarantee order ***
                    System.out.println("Error: Plant ID cannot be empty.");
                    continue;
                }
                return Integer.parseInt(input);
            } catch (NumberFormatException e) {
                // *** FIX: Print to System.out to guarantee order ***
                System.out.println("Error: ID must be a valid integer. Please try again.");
            }
        }
    }

    /**
     * Prompts the user for a Port number until a valid integer is provided.
     * @return The parsed integer port.
     */
    private static int getPortFromUser() {
        while (true) {
            try {
                System.out.print("Enter Port: ");
                String input = scanner.nextLine().trim();
                if (input.isEmpty()) {
                    // *** FIX: Print to System.out to guarantee order ***
                    System.out.println("Error: Port cannot be empty.");
                    continue;
                }
                return Integer.parseInt(input);
            } catch (NumberFormatException e) {
                // *** FIX: Print to System.out to guarantee order ***
                System.out.println("Error: Port must be a valid integer. Please try again.");
            }
        }
    }

    /**
     * Runs the post-startup command loop, waiting for the user to type 'exit'.
     * @param powerPlant The running power plant instance to be shut down.
     */
    private static void runCommandLoop(PowerPlant powerPlant) {
        System.out.println("PowerPlant is running.");
        System.out.println("Enter 'exit' to shut down the PowerPlant:");

        while (scanner.hasNextLine()) {
            String command = scanner.nextLine().trim().toLowerCase();
            if (command.equals("exit")) {
                break;
            } else if (!command.isEmpty()) {
                System.out.println("Unknown command. Enter 'exit' to shut down.");
            }
        }

        System.out.println("Shutting down PowerPlant " + powerPlant.getSelfInfo().plantId() + "...");
        powerPlant.shutdown();
        System.out.println("PowerPlant " + powerPlant.getSelfInfo().plantId() + " shut down successfully.");
    }
}