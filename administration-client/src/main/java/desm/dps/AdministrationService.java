package desm.dps;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.Scanner;

public class AdministrationService {
    private final RestTemplate restTemplate = new RestTemplate();
    private final String apiBaseUrl;
    private final String endpointPlants;
    private final String endpointPollution;

    public AdministrationService() {
        Properties props = new Properties();
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("application.properties")) {
            if (in == null) {
                throw new RuntimeException("Could not find application.properties on classpath");
            }
            props.load(in);
        } catch (IOException e) {
            throw new RuntimeException("Unable to load configuration", e);
        }

        this.apiBaseUrl = props.getProperty("rest.endpoint");
        this.endpointPlants = props.getProperty("rest.plants");
        this.endpointPollution = props.getProperty("rest.pollution");
    }

    public void printAllPlants() {
        try {
            String url = apiBaseUrl + endpointPlants;
            ResponseEntity<PowerPlantInfo[]> response = restTemplate.getForEntity(url, PowerPlantInfo[].class);

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                List<PowerPlantInfo> plants = Arrays.asList(response.getBody());
                if (!plants.isEmpty()) {
                    System.out.println("\n===== POWER PLANTS =====");
                    plants.forEach(System.out::println);
                    System.out.println("Total plants: " + plants.size());
                } else {
                    System.out.println("No power plants found.");
                }
            } else {
                System.out.println("Server returned status: " + response.getStatusCode());
            }
        } catch (ResourceAccessException e) {
            System.out.println("Cannot connect to server.");
        } catch (HttpClientErrorException e) {
            System.out.println("Error: " + e.getStatusCode() + " - " + e.getStatusText());
        } catch (Exception e) {
            System.out.println("Unexpected error: " + e.getMessage());
        }
    }

    public void printPollutionData(Scanner scanner) {
        System.out.println("\n===== POLLUTION DATA REQUEST =====");
        System.out.print("Enter start timestamp (t1): ");
        String t1 = scanner.nextLine().trim();
        System.out.print("Enter end timestamp (t2): ");
        String t2 = scanner.nextLine().trim();

        if (t1.isEmpty() || t2.isEmpty()) {
            System.out.println("Error: Both timestamps are required.");
            return;
        }

        try {
            Long.parseLong(t1);
            Long.parseLong(t2);
        } catch (NumberFormatException e) {
            System.out.println("Error: Timestamps must be valid numbers.");
            return;
        }

        try {
            String url = apiBaseUrl + endpointPollution + "?t1=" + t1 + "&t2=" + t2;
            ResponseEntity<Double> response = restTemplate.getForEntity(url, Double.class);

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                System.out.println("\n===== CO2 EMISSION STATISTICS =====");
                System.out.println("Query Period: " + t1 + " to " + t2);
                System.out.printf("Average CO2 emission level: %.2f%n", response.getBody());
            } else {
                System.out.println("Server returned status: " + response.getStatusCode());
            }
        } catch (ResourceAccessException e) {
            System.out.println("Cannot connect to server.");
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.BAD_REQUEST) {
                System.out.println("Invalid timestamp format or parameters.");
            } else if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                System.out.println("No CO2 data found for the specified time period.");
            } else {
                System.out.println("Error: " + e.getStatusCode() + " - " + e.getStatusText());
            }
        } catch (Exception e) {
            System.out.println("Unexpected error: " + e.getMessage());
        }
    }
}