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
     *
     * @param plantInfo The information of the plant to register.
     * @return A List of all registered plants on success.
     * @throws RegistrationConflictException if the plant ID already exists on the server (HTTP 409).
     * @throws RestClientException if there is a communication error with the server.
     */
    // CHANGE 1: The method signature now declares that it can throw our custom exception.
    public List<PowerPlantInfo> register(PowerPlantInfo plantInfo) throws RegistrationConflictException {
        final String endpoint = baseUrl + PLANTS_ENDPOINT;
        final HttpEntity<PowerPlantInfo> request = createRequestEntity(plantInfo);

        log.info("Registering plant: {}", plantInfo.plantId());

        try {
            ResponseEntity<PowerPlantInfo[]> response = restTemplate.postForEntity(
                    endpoint, request, PowerPlantInfo[].class);

            log.info("POST to {} responded with status: {}", endpoint, response.getStatusCode());

            if (response.getStatusCode().is2xxSuccessful()) { // More robust check for 2xx status codes
                PowerPlantInfo[] plants = response.getBody();
                if (plants != null) {
                    log.info("Plant '{}' registered successfully. Total registered plants: {}",
                            plantInfo.plantId(), plants.length);
                    return Arrays.asList(plants);
                }
            }

            // If we reach here, something was wrong with the successful response
            log.warn("Registration response was not as expected for plant '{}'. Status: {}, Body was null: {}",
                    plantInfo.plantId(), response.getStatusCode(), response.getBody() == null);
            // Throw a generic exception for unexpected success-range responses
            throw new RestClientException("Registration returned a success status but had an invalid body.");

        } catch (HttpClientErrorException e) {
            // CHANGE 2: Specifically handle the CONFLICT status by throwing our custom exception.
            if (e.getStatusCode() == HttpStatus.CONFLICT) {
                String errorMessage = String.format("Plant ID '%s' already exists", plantInfo.plantId());
                log.error("Registration failed: {} (HTTP 409 Conflict).", errorMessage);
                throw new RegistrationConflictException(errorMessage);
            } else {
                // For all other client-side HTTP errors (4xx), log and re-throw a generic exception.
                log.error("Registration request failed with HTTP error: {} {}. Response: {}",
                        e.getStatusCode(), e.getStatusText(), e.getResponseBodyAsString(), e);
                throw e; // Re-throw the original exception
            }
        } catch (RestClientException e) {
            // This catches server-side errors (5xx) and connection errors.
            log.error("Failed to register plant '{}' due to a communication error.", plantInfo.plantId(), e);
            throw e; // Re-throw the original exception
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