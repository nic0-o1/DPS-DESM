package desm.dps.service;

import desm.dps.PowerPlantInfo;
import desm.dps.repository.PlantRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
// Removed import java.util.Optional;

@Service
public class PlantService {
    private static final Logger logger = LoggerFactory.getLogger(PlantService.class);

    private final PlantRepository plantRepository;

    @Autowired
    public PlantService(PlantRepository plantRepository) {
        this.plantRepository = plantRepository;
    }

    public List<PowerPlantInfo> getAllPlants() {
        logger.info("Retrieving all registered plants.");
        return plantRepository.getAllPlants();
    }

    /**
     * Registers a new power plant after validating its data.
     * @param plantInfo The PowerPlantInfo object to register.
     * @return true if the plant was successfully registered, false if data is invalid or a plant with the same ID already exists.
     */
    public boolean registerPlant(PowerPlantInfo plantInfo) {
        // Service layer validation for business rules and data integrity
        if (plantInfo == null) {
            logger.warn("Attempted to register a null plantInfo object.");
            return false;
        }
        if (plantInfo.getPlantId() == null || plantInfo.getPlantId().trim().isEmpty()) {
            logger.warn("Attempted to register a plant with null or empty ID.");
            return false;
        }
        if (plantInfo.getAddress() == null || plantInfo.getAddress().trim().isEmpty()) {
            logger.warn("Attempted to register plant ID {} with null or empty address.", plantInfo.getPlantId());
            return false;
        }
        if (plantInfo.getPort() <= 0 || plantInfo.getPort() > 65535) { // Standard port range
            logger.warn("Attempted to register plant ID {} with invalid port: {}.", plantInfo.getPlantId(), plantInfo.getPort());
            return false;
        }

        logger.info("Attempting to register plant with ID: {}", plantInfo.getPlantId());
        // The repository's registerPlant method handles the check for pre-existing ID
        boolean success = plantRepository.registerPlant(plantInfo);
        if (success) {
            logger.info("Successfully registered plant: {}", plantInfo.getPlantId());
        } else {
            // This 'else' will now predominantly mean the plant ID already exists,
            // as other validations are handled above.
            logger.warn("Failed to register plant: ID {} already exists or another repository issue occurred.", plantInfo.getPlantId());
        }
        return success;
    }

    /**
     * Retrieves a power plant by its ID.
     * @param plantId The ID of the plant to retrieve.
     * @return The PowerPlantInfo object if found, otherwise null.
     */
    public PowerPlantInfo getPlantById(String plantId) {
        if (plantId == null || plantId.trim().isEmpty()) {
            logger.warn("Attempted to retrieve plant with null or empty ID.");
            return null;
        }
        logger.info("Retrieving plant by ID: {}", plantId);
        PowerPlantInfo plant = plantRepository.getPlantById(plantId);
        if (plant == null) {
            logger.info("No plant found with ID: {}", plantId);
        } else {
            logger.info("Found plant with ID {}: {}", plantId, plant);
        }
        return plant; // Directly return the object or null
    }
}