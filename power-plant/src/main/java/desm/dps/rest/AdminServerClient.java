package desm.dps.rest;

import desm.dps.PowerPlantInfo;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

import java.util.Arrays;
import java.util.List;

public class AdminServerClient {
    private final String baseUrl;
    private final RestTemplate restTemplate;

    public AdminServerClient(String baseUrl) {
        this.baseUrl = baseUrl;
        this.restTemplate = new RestTemplate();
    }

    public List<PowerPlantInfo> register(PowerPlantInfo plantInfo) {
        String endpoint = baseUrl + "/plants";

//        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<PowerPlantInfo> request = new HttpEntity<>(plantInfo, headers);

            System.out.println("Registering plant: " + plantInfo.getPlantId());

            ResponseEntity<PowerPlantInfo[]> response = restTemplate.postForEntity(
                    endpoint, request, PowerPlantInfo[].class);

            System.out.println("POST Response: " + response.getStatusCode());

            if (response.getStatusCode() == HttpStatus.OK ||
                    response.getStatusCode() == HttpStatus.CREATED) {

                PowerPlantInfo[] plants = response.getBody();
                if (plants != null) {
                    System.out.println("Plant " + plantInfo.getPlantId() + " registered successfully");
                    System.out.println("Total registered plants: " + plants.length);
                    return Arrays.asList(plants);
                }
            }

            return null;

//        } catch (HttpClientErrorException e) {
//            if (e.getStatusCode() == HttpStatus.CONFLICT) {
//                System.err.println("Registration failed: Plant ID " + plantInfo.getPlantId() + " already exists");
//            } else {
//                System.err.println("Registration request failed with HTTP error: " + e.getStatusCode());
//                System.err.println("Error response: " + e.getResponseBodyAsString());
//            }
//            return null;
//        } catch (RestClientException e) {
//            System.err.println("Failed to register plant " + plantInfo.getPlantId() + ": " + e.getClass());
//            return null;
//        }
    }
}