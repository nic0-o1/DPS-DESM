package desm.dps;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.Arrays;
import java.util.InputMismatchException;
import java.util.List;
import java.util.Scanner;

/**
 * Client application for interacting with the power plant administration system.
 */
public class AdministrationClientApp {
    // API endpoints
    private static final String API_BASE_URL = "http://localhost:8080";
    private static final String ENDPOINT_PLANTS = "/plants";
    private static final String ENDPOINT_POLLUTION = "/pollution";

    // Menu options
    private static final int OPTION_GET_PLANTS = 1;
    private static final int OPTION_GET_POLLUTION = 2;
    private static final int OPTION_EXIT = 3;

    private final RestTemplate restTemplate;
    private final Scanner scanner;

    /**
     * Constructor initializing required components.
     */
    public AdministrationClientApp() {
        this.restTemplate = new RestTemplate();
        this.scanner = new Scanner(System.in);
    }

    /**
     * Main method to start the application.
     */
    public static void main(String[] args) {
        AdministrationClientApp app = new AdministrationClientApp();
        app.run();
    }

    /**
     * Main application loop.
     */
    public void run() {
        boolean running = true;

        try {
            while (running) {
                displayMenu();
                int choice = getUserChoice();

                switch (choice) {
                    case OPTION_GET_PLANTS:
                        getAllPlants();
                        break;
                    case OPTION_GET_POLLUTION:
                        getPollutionData();
                        break;
                    case OPTION_EXIT:
                        running = false;
                        break;
                    default:
                        System.out.println("Invalid choice. Please enter a number between 1 and 3.");
                }

                if (running) {
                    System.out.println("\nPress Enter to continue...");
                    scanner.nextLine(); // Wait for user to press Enter
                }
            }
        } finally {
            scanner.close();
        }
    }

    /**
     * Display the menu options to the user.
     */
    private void displayMenu() {
        System.out.println("\n===== CLIENT ADMINISTRATION =====");
        System.out.println("1. Get all power plants");
        System.out.println("2. Get pollution data");
        System.out.println("3. Exit");
        System.out.print("Enter your choice (1-3): ");
    }

    /**
     * Get and validate user input for menu choice.
     *
     * @return The validated user choice
     */
    private int getUserChoice() {
        int choice = 0;
        try {
            choice = scanner.nextInt();
            scanner.nextLine();
        } catch (InputMismatchException e) {
            System.out.println("Please enter a valid number.");
            scanner.nextLine();
        }
        return choice;
    }

    /**
     * Retrieves all power plants from the server.
     */
    private void getAllPlants() {
        try {
            String url = API_BASE_URL + ENDPOINT_PLANTS;
            ResponseEntity<PowerPlantInfo[]> response = restTemplate.getForEntity(url, PowerPlantInfo[].class);

            if (response.getStatusCode() == HttpStatus.OK) {
                PowerPlantInfo[] plants = response.getBody();
                if (plants != null && plants.length > 0) {
                    List<PowerPlantInfo> plantList = Arrays.asList(plants);
                    System.out.println("\n===== POWER PLANTS =====");
                    plantList.forEach(System.out::println);
                    System.out.println("Total plants: " + plantList.size());
                } else {
                    System.out.println("No power plants found.");
                }
            } else {
                System.out.println("Server returned status: " + response.getStatusCode());
            }
        } catch (ResourceAccessException e) {
            System.out.println("Cannot connect to server");
        } catch (HttpClientErrorException e) {
            System.out.println("Error: " + e.getStatusCode() + " - " + e.getStatusText());
        } catch (Exception e) {
            System.out.println("An unexpected error occurred: " + e.getMessage());
        }
    }

    /**
     * Retrieves pollution data from the server.
     */
    private void getPollutionData() {
        //TODO: get pollution data
    }
}