package desm.dps;

import desm.dps.config.AppConfig;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.Arrays;
import java.util.List;
import java.util.Scanner;

/**
 * Service layer responsible for communicating with the power plant administration's REST API.
 * It encapsulates the logic for making HTTP requests and handling responses.
 */
public class AdministrationService {
    private final RestTemplate restTemplate = new RestTemplate();
    private final String apiBaseUrl;
    private final String endpointPlants;
    private final String endpointPollution;

    /**
     * Constructs an AdministrationService and initializes API endpoint configuration
     * by loading it from the central {@link AppConfig}.
     */
    public AdministrationService() {
        AppConfig config = AppConfig.getInstance();
        this.apiBaseUrl = config.getApiBaseUrl();
        this.endpointPlants = config.getPlantsEndpoint();
        this.endpointPollution = config.getPollutionEndpoint();
    }

    /**
     * Fetches all power plants from the API and prints their details to the console.
     * Handles connection errors and empty results gracefully.
     */
    public void printAllPlants() {
        try {
            String url = apiBaseUrl + endpointPlants;
            ResponseEntity<PowerPlantInfo[]> response = restTemplate.getForEntity(url, PowerPlantInfo[].class);

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                List<PowerPlantInfo> plants = Arrays.asList(response.getBody());
                if (!plants.isEmpty()) {
                    System.out.println("\n===== POWER PLANTS =====");
                    plants.forEach(System.out::println);
                    System.out.println("------------------------");
                    System.out.println("Total plants found: " + plants.size());
                } else {
                    System.out.println("No power plants found.");
                }
            } else {
                System.out.println("Server responded with status: " + response.getStatusCode());
            }
        } catch (ResourceAccessException e) {
            System.err.println("Error: Cannot connect to the API server at " + apiBaseUrl + ". Please check the server status and network connection.");
        } catch (HttpClientErrorException e) {
            System.err.println("Error from server: " + e.getStatusCode() + " - " + e.getResponseBodyAsString());
        } catch (Exception e) {
            System.err.println("An unexpected error occurred: " + e.getMessage());
        }
    }

    /**
     * Prompts the user for a time range, fetches CO2 pollution data from the API,
     * and prints the result.
     *
     * @param scanner The Scanner instance to read user input.
     */
    public void printPollutionData(Scanner scanner) {
        System.out.println("\n===== POLLUTION DATA REQUEST =====");
        System.out.print("Enter start timestamp: ");
        String t1 = scanner.nextLine().trim();
        System.out.print("Enter end timestamp: ");
        String t2 = scanner.nextLine().trim();

        if (t1.isEmpty() || t2.isEmpty()) {
            System.out.println("Info: Both start and end timestamps are required.");
            return;
        }

        try {
            Long.parseLong(t1);
            Long.parseLong(t2);
        } catch (NumberFormatException e) {
            System.out.println("Info: Timestamps must be valid whole numbers.");
            return;
        }

        try {
            String url = String.format("%s%s?t1=%s&t2=%s", apiBaseUrl, endpointPollution, t1, t2);
            ResponseEntity<Double> response = restTemplate.getForEntity(url, Double.class);

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                System.out.println("\n===== CO2 EMISSION STATISTICS =====");
                System.out.println("Query Period: from " + t1 + " to " + t2);
                System.out.printf("Result: Average CO2 emission level is %.2f%n", response.getBody());
            } else {
                System.out.println("\nInfo: The server responded successfully but provided no data.");
            }
        } catch (ResourceAccessException e) {
            System.err.println("Error: Cannot connect to the API server at " + apiBaseUrl + ". Please check the server status and network connection.");
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                System.out.println("\n===== CO2 EMISSION STATISTICS =====");
                System.out.println("Query Period: from " + t1 + " to " + t2);
                System.out.println("Result: No CO2 data was found for the specified time period.");
            }
            else if (e.getStatusCode() == HttpStatus.BAD_REQUEST) {
                System.out.println("\nInfo: Invalid request. Please check that timestamps are in the correct format and range.");
            }
            else {
                System.err.println("\nAn error occurred while communicating with the server (Status: " + e.getStatusCode() + ")");
            }
        } catch (Exception e) {
            System.err.println("\nAn unexpected error occurred: " + e.getMessage());
        }
    }
}