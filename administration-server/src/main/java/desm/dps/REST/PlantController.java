package desm.dps.REST;

import desm.dps.PowerPlantInfo;
import desm.dps.service.PlantService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST Controller for managing power plant registrations.
 * Provides endpoints to register a new plant and retrieve information about existing ones.
 */
@RestController
@RequestMapping("/plants")
public class PlantController {
    private static final Logger logger = LoggerFactory.getLogger(PlantController.class);

    private final PlantService plantService;

    @Autowired
    public PlantController(PlantService plantService) {
        this.plantService = plantService;
    }

    /**
     * Handles GET requests to retrieve a list of all registered power plants.
     *
     * @return A ResponseEntity containing the list of all plants or an internal server error.
     */
    @GetMapping
    public ResponseEntity<List<PowerPlantInfo>> getAllPlants() {
        logger.info("Received request to get all registered plants.");
        try {
            List<PowerPlantInfo> plants = plantService.getAllPlants();
            return ResponseEntity.ok(plants);
        } catch (Exception e) {
            logger.error("An unexpected error occurred while retrieving all plants.", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Handles POST requests to register a new power plant.
     *
     * @param plantInfo The {@link PowerPlantInfo} from the request body.
     * @return A ResponseEntity with status 201 (Created) and the list of all plants on success,
     *         409 (Conflict) if the plant ID already exists,
     *         400 (Bad Request) if the data is invalid,
     *         or 500 (Internal Server Error) on failure.
     */
    @PostMapping
    public ResponseEntity<?> addPlant(@RequestBody PowerPlantInfo plantInfo) {
        if (plantInfo == null) {
            logger.warn("Registration request failed: request body is missing.");
            return ResponseEntity.badRequest().body("Request body (PowerPlantInfo) is missing.");
        }
        logger.info("Received registration request for Plant ID: {}", plantInfo.plantId());

        try {
            boolean registered = plantService.registerPlant(plantInfo);

            if (registered) {
                logger.info("Plant {} registered successfully.", plantInfo.plantId());
                return ResponseEntity.status(HttpStatus.CREATED).body(plantService.getAllPlants());
            } else {
                // If registration failed, check if it was because the plant already exists.
                if (plantService.getPlantById(plantInfo.plantId()) != null) {
                    logger.warn("Registration failed for Plant {}: ID already exists.", plantInfo.plantId());
                    return ResponseEntity.status(HttpStatus.CONFLICT).body("Plant with ID '" + plantInfo.plantId() + "' already exists.");
                } else {
                    // Otherwise, the service rejected it due to invalid data.
                    logger.warn("Registration failed for Plant {}: data was invalid.", plantInfo.plantId());
                    return ResponseEntity.badRequest().body("Invalid plant data. Please check ID, address, and port requirements.");
                }
            }
        } catch (Exception e) {
            logger.error("An internal error occurred while registering Plant {}.", plantInfo.plantId(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("An internal server error occurred.");
        }
    }

    /**
     * Handles GET requests to retrieve a specific power plant by its ID.
     *
     * @param id The ID of the plant to retrieve.
     * @return A ResponseEntity with the plant info if found,
     *         404 (Not Found) if not found,
     *         or 500 (Internal Server Error) on failure.
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> getPlantById(@PathVariable int id) {
        logger.info("Received request to get plant by ID: {}", id);
        try {
            PowerPlantInfo plant = plantService.getPlantById(id);
            if (plant != null) {
                logger.debug("Found plant with ID {}: {}", id, plant);
                return ResponseEntity.ok(plant);
            } else {
                logger.info("No plant found with ID: {}", id);
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            logger.error("An internal error occurred while retrieving Plant {}.", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}