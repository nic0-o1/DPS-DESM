package desm.dps.service;

import desm.dps.PowerPlantInfo;
import desm.dps.repository.PlantRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

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
        if (plantInfo == null) {
            logger.warn("Attempted to register a null plantInfo object.");
            return false;
        }
        // Assuming plant IDs should be positive integers.
        if (plantInfo.plantId() <= 0) {
            logger.warn("Attempted to register a plant with invalid ID: {}.", plantInfo.plantId());
            return false;
        }
        if (plantInfo.address() == null || plantInfo.address().trim().isEmpty()) {
            logger.warn("Attempted to register plant ID {} with null or empty address.", plantInfo.plantId());
            return false;
        }
        if (plantInfo.port() <= 0 || plantInfo.port() > 65535) { // Standard port range
            logger.warn("Attempted to register plant ID {} with invalid port: {}.", plantInfo.plantId(), plantInfo.port());
            return false;
        }

        logger.info("Attempting to register plant with ID: {}", plantInfo.plantId());
        boolean success = plantRepository.registerPlant(plantInfo);
        if (success) {
            logger.info("Successfully registered plant: {}", plantInfo.plantId());
        } else {
            logger.warn("Failed to register plant: ID {} already exists or another repository issue occurred.", plantInfo.plantId());
        }
        return success;
    }

    /**
     * Retrieves a power plant by its ID.
     * @param plantId The ID of the plant to retrieve.
     * @return The PowerPlantInfo object if found, otherwise null.
     */
    public PowerPlantInfo getPlantById(int plantId) {
        // Assuming plant IDs should be positive integers.
        if (plantId <= 0) {
            logger.warn("Attempted to retrieve plant with an invalid ID: {}", plantId);
            return null;
        }
        logger.info("Retrieving plant by ID: {}", plantId);
        PowerPlantInfo plant = plantRepository.getPlantById(plantId);
        if (plant == null) {
            logger.info("No plant found with ID: {}", plantId);
        } else {
            logger.info("Found plant with ID {}: {}", plantId, plant);
        }
        return plant;
    }
}