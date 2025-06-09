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

@RestController
@RequestMapping("/plants")
public class PlantController {
    private static final Logger logger = LoggerFactory.getLogger(PlantController.class);

    private final PlantService plantService;

    @Autowired
    public PlantController(PlantService plantService) {
        this.plantService = plantService;
    }

    @GetMapping
    public ResponseEntity<List<PowerPlantInfo>> getAllPlants() {
        try {
            logger.info("Received request to get all plants.");
            List<PowerPlantInfo> plants = plantService.getAllPlants();
            return ResponseEntity.ok(plants);
        } catch (Exception e) {
            logger.error("Error processing request for all plants: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping
    public ResponseEntity<?> addPlant(@RequestBody PowerPlantInfo plantInfo) {
        try {
            // Basic controller-level check: is the request body itself provided?
            if (plantInfo == null) {
                logger.warn("Received request to register a null plantInfo object.");
                return ResponseEntity.badRequest().body("Request body (PowerPlantInfo) is missing.");
            }
            // Detailed validation of plantInfo's fields is now primarily in PlantService

            logger.info("Received request to register new plant with ID: {}", plantInfo.plantId());
            boolean registered = plantService.registerPlant(plantInfo);

            if (registered) {
                logger.info("Plant registered successfully: {}", plantInfo.plantId());
                // Return 201 Created with the list of all plants
                return ResponseEntity.status(HttpStatus.CREATED).body(plantService.getAllPlants());
            } else {
                // Determine if it's a conflict (already exists) or bad data rejected by service
                if (plantService.getPlantById(plantInfo.plantId()) != null) {
                    logger.warn("Conflict: Plant with ID {} already exists.", plantInfo.plantId());
                    return ResponseEntity.status(HttpStatus.CONFLICT).body("Plant with ID '" + plantInfo.plantId() + "' already exists.");
                } else {
                    // If not a conflict, it means the service rejected it due to invalid data
                    logger.warn("Bad request: Plant data for ID {} was invalid as per service validation.", plantInfo.plantId());
                    return ResponseEntity.badRequest().body("Invalid plant data. Please check ID, address, and port requirements.");
                }
            }
        } catch (Exception e) {
            logger.error("Error processing request to register plant {}: {}", plantInfo.plantId(), e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("An internal server error occurred while registering the plant.");
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getPlantById(@PathVariable int id) {
        try {
            logger.info("Received request to get plant by ID: {}", id);

            PowerPlantInfo plant = plantService.getPlantById(id);

            if (plant != null) {
                logger.info("Plant found with ID {}: {}", id, plant);
                return ResponseEntity.ok(plant);
            } else {
                logger.info("No plant found with ID: {}", id);
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            logger.error("Error processing request for plant ID {}: {}", id, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("An internal server error occurred while retrieving the plant.");
        }
    }
}