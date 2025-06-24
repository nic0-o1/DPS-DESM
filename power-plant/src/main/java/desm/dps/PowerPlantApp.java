package desm.dps;

import desm.dps.config.AppConfig;
import desm.dps.grpc.PortInUseException;
import desm.dps.rest.RegistrationConflictException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Scanner;

public class PowerPlantApp {
    private static final Logger logger = LoggerFactory.getLogger(PowerPlantApp.class);

    public static void main(String[] args) {

        AppConfig config = AppConfig.getInstance();
        Scanner scanner = new Scanner(System.in);
        System.out.println("===== Power Plant Configuration =====");

        PowerPlant powerPlant = null;
        int plantId = -1;
        int plantPort = -1;

        while (true) {
            try {
                System.out.print("Enter Plant ID: ");
                plantId = Integer.parseInt(scanner.nextLine().trim());

                System.out.print("Enter Port: ");
                plantPort = Integer.parseInt(scanner.nextLine().trim());

                String adminServerBaseUrl = config.getAdminServerBaseUrl();
                String mqttBrokerUrl = config.getMqttBrokerUrl();
                String energyRequestTopic = config.getEnergyRequestTopic();
                String pollutionPublishTopic = config.getPollutionPublishTopic();

                PowerPlantInfo selfInfo = new PowerPlantInfo(plantId, "localhost", plantPort, System.currentTimeMillis());
                powerPlant = new PowerPlant(selfInfo, adminServerBaseUrl, mqttBrokerUrl, energyRequestTopic, pollutionPublishTopic);

                powerPlant.start();


                System.out.println("PowerPlant " + plantId + " started successfully");
                System.out.println("Connected to admin server at: " + adminServerBaseUrl);
                break;

            } catch (NumberFormatException e) {
                System.err.println("Error: ID and Port must be valid integers. Please try again.");

            } catch (RegistrationConflictException e) {
                System.err.println("\n--- REGISTRATION FAILED ---");
                System.err.println("REASON: " + e.getMessage());
                System.err.println("Please choose a different Plant ID.\n");
                if (powerPlant != null) {
                    powerPlant.shutdown();
                }

            }
            catch (PortInUseException e) {
                System.err.println("\n--- STARTUP FAILED ---");
                System.err.println("REASON: " + e.getMessage());
                System.err.println("Choose a different Port.\n");
                if (powerPlant != null) {
                    powerPlant.shutdown();
                }
            } catch (Exception e) {
                logger.error("FATAL STARTUP ERROR: PowerPlant {} could not be started.", plantId, e);
                if (powerPlant != null) {
                    powerPlant.shutdown();
                }
                scanner.close();
                System.exit(1);
            }
        }

        System.out.println("PowerPlant is running.");
        System.out.println("Enter 'exit' to shut down the PowerPlant:");

        String command;
        try {
            while (true) {
                command = scanner.nextLine().trim().toLowerCase();
                if (command.equals("exit")) {
                    break;
                } else if (!command.isEmpty()) {
                    System.out.println("Unknown command. Enter 'exit' to shut down.");
                }
            }

            System.out.println("Shutting down PowerPlant " + plantId + "...");
            if (powerPlant != null) {
                powerPlant.shutdown();
            }
            System.out.println("PowerPlant " + plantId + " shut down successfully.");
        } catch (Exception _) {
        }
        finally {
            scanner.close();
        }
    }
}