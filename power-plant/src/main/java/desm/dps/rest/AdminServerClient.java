package desm.dps.rest;

import desm.dps.PowerPlantInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * A client for communicating with the administrative server's REST API.
 * This class is responsible for registering power plants and handling server responses.
 */
public final class AdminServerClient {

    private static final Logger log = LoggerFactory.getLogger(AdminServerClient.class);
    private static final String PLANTS_ENDPOINT = "/plants";

    private final String baseUrl;
    private final RestTemplate restTemplate;

    /**
     * Original public constructor for full backward compatibility.
     * @param baseUrl The base URL of the admin server.
     */
    public AdminServerClient(String baseUrl) {
        this.baseUrl = Objects.requireNonNull(baseUrl, "baseUrl cannot be null");
        this.restTemplate = new RestTemplate();
    }

    /**
     * Registers a power plant with the admin server.
     * The logic, method signature, and return behavior are identical to the original version.
     *
     * @param plantInfo The information of the plant to register.
     * @return A List of all registered plants on success; returns null on any failure.
     */
    public List<PowerPlantInfo> register(PowerPlantInfo plantInfo) {
        final String endpoint = baseUrl + PLANTS_ENDPOINT;
        final HttpEntity<PowerPlantInfo> request = createRequestEntity(plantInfo);

        log.info("Registering plant: {}", plantInfo.plantId());

        try {
            ResponseEntity<PowerPlantInfo[]> response = restTemplate.postForEntity(
                    endpoint, request, PowerPlantInfo[].class);

            log.info("POST to {} responded with status: {}", endpoint, response.getStatusCode());

            if (response.getStatusCode() == HttpStatus.OK || response.getStatusCode() == HttpStatus.CREATED) {
                PowerPlantInfo[] plants = response.getBody();
                if (plants != null) {
                    log.info("Plant '{}' registered successfully. Total registered plants: {}",
                            plantInfo.plantId(), plants.length);
                    return Arrays.asList(plants);
                }
            }

            log.warn("Registration was not successful for plant '{}'. Status: {}, Body was null: {}",
                    plantInfo.plantId(), response.getStatusCode(), response.getBody() == null);
            return null;

        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.CONFLICT) {
                log.error("Registration failed: Plant ID '{}' already exists (HTTP 409 Conflict).", plantInfo.plantId());
            } else {
                log.error("Registration request failed with HTTP error: {} {}. Response: {}",
                        e.getStatusCode(), e.getStatusText(), e.getResponseBodyAsString(), e);
            }
            return null; // Return null on failure

        } catch (RestClientException e) {
            log.error("Failed to register plant '{}' due to a communication error.", plantInfo.plantId(), e);
            return null; // Return null on failure
        }
    }

    /**
     * Helper method to encapsulate the creation of the request entity.
     */
    private HttpEntity<PowerPlantInfo> createRequestEntity(PowerPlantInfo plantInfo) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(plantInfo, headers);
    }
}