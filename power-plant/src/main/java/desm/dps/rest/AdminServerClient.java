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
 * This class is responsible for registering the power plant and retrieving the list
 * of other plants already in the system.
 */
public final class AdminServerClient {

    private static final Logger logger = LoggerFactory.getLogger(AdminServerClient.class);
    private static final String PLANTS_ENDPOINT = "/plants";

    private final String baseUrl;
    private final RestTemplate restTemplate;

    /**
     * Constructs a client for the admin server.
     *
     * @param baseUrl The base URL of the admin server. Must not be null.
     */
    public AdminServerClient(String baseUrl) {
        this.baseUrl = Objects.requireNonNull(baseUrl, "Admin server base URL cannot be null");
        this.restTemplate = new RestTemplate();
    }

    /**
     * Registers this power plant with the admin server and retrieves a list of other known plants.
     *
     * @param plantInfo The information of the plant to register.
     * @return A list of all other registered plants on success. Returns an empty list if this is the first plant.
     * @throws RegistrationConflictException if the plant ID already exists on the server (HTTP 409).
     * @throws RestClientException if there is a communication error or another HTTP error from the server.
     */
    public List<PowerPlantInfo> register(PowerPlantInfo plantInfo) throws RegistrationConflictException {
        final String endpoint = baseUrl + PLANTS_ENDPOINT;
        final HttpEntity<PowerPlantInfo> request = createRequestEntity(plantInfo);

        logger.info("Registering Plant {} with admin server at {}", plantInfo.plantId(), endpoint);

        try {
            ResponseEntity<PowerPlantInfo[]> response = restTemplate.postForEntity(
                    endpoint, request, PowerPlantInfo[].class);

            logger.info("POST to {} responded with status: {}", endpoint, response.getStatusCode());

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                PowerPlantInfo[] plants = response.getBody();
                logger.info("Plant {} registered successfully. {} other plants returned by server.",
                        plantInfo.plantId(), plants.length);
                return Arrays.asList(plants);
            }

            logger.warn("Registration for Plant {} received a success status ({}) but the response body was invalid.",
                    plantInfo.plantId(), response.getStatusCode());
            throw new RestClientException("Registration returned a success status but had an invalid response body.");

        } catch (HttpClientErrorException e) {
            // Specifically handle the CONFLICT status by throwing a custom, checked exception.
            if (e.getStatusCode() == HttpStatus.CONFLICT) {
                String errorMessage = String.format("Registration failed because Plant ID '%s' already exists on the server.", plantInfo.plantId());
                logger.error(errorMessage);
                throw new RegistrationConflictException(errorMessage);
            }
            // For all other 4xx errors, log and re-throw the original exception.
            logger.error("Registration request for Plant {} failed with HTTP error: {} {}. Response: {}",
                    plantInfo.plantId(), e.getStatusCode(), e.getStatusText(), e.getResponseBodyAsString(), e);
            throw e;
        } catch (RestClientException e) {
            // Catches connection errors and server-side errors (5xx).
            logger.error("Failed to register Plant {} due to a communication error with the admin server.", plantInfo.plantId(), e);
            throw e;
        }
    }

    /**
     * A helper method to create an HTTP request entity with the correct headers.
     *
     * @param plantInfo The object to be used as the request body.
     * @return An {@link HttpEntity} containing the plant info and JSON headers.
     */
    private HttpEntity<PowerPlantInfo> createRequestEntity(PowerPlantInfo plantInfo) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(plantInfo, headers);
    }
}