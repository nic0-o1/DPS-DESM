package desm.dps.service;

import desm.dps.PowerPlantInfo;
import desm.dps.repository.PlantRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * A service layer that handles business logic for power plant management.
 * It validates data before interacting with the {@link PlantRepository}.
 */
@Service
public class PlantService {
    private static final Logger logger = LoggerFactory.getLogger(PlantService.class);

    private final PlantRepository plantRepository;

    @Autowired
    public PlantService(PlantRepository plantRepository) {
        this.plantRepository = plantRepository;
    }

    /**
     * Retrieves a list of all registered power plants.
     *
     * @return A list of {@link PowerPlantInfo} objects.
     */
    public List<PowerPlantInfo> getAllPlants() {
        logger.debug("Service layer retrieving all registered plants.");
        return plantRepository.getAllPlants();
    }

    /**
     * Registers a new power plant after performing validation checks.
     *
     * @param plantInfo The {@link PowerPlantInfo} object to register.
     * @return {@code true} if the plant was successfully registered; {@code false} if the data is invalid
     *         or if a plant with the same ID already exists.
     */
    public boolean registerPlant(PowerPlantInfo plantInfo) {
        if (!isPlantInfoValid(plantInfo)) {
            return false;
        }
        logger.debug("Attempting to register valid plant with ID: {}", plantInfo.plantId());
        return plantRepository.registerPlant(plantInfo);
    }

    /**
     * Retrieves a power plant by its ID after validating the ID format.
     *
     * @param plantId The ID of the plant to retrieve.
     * @return The {@link PowerPlantInfo} object if found, otherwise {@code null}.
     */
    public PowerPlantInfo getPlantById(int plantId) {
        if (plantId <= 0) {
            logger.warn("Attempted to retrieve plant with an invalid (non-positive) ID: {}", plantId);
            return null;
        }
        logger.debug("Service layer retrieving plant by ID: {}", plantId);
        return plantRepository.getPlantById(plantId);
    }

    /**
     * Validates the fields of a {@link PowerPlantInfo} object.
     *
     * @param plantInfo The object to validate.
     * @return {@code true} if all fields are valid, {@code false} otherwise.
     */
    private boolean isPlantInfoValid(PowerPlantInfo plantInfo) {
        if (plantInfo == null) {
            logger.warn("Validation failed: plantInfo object is null.");
            return false;
        }
        if (plantInfo.plantId() <= 0) {
            logger.warn("Validation failed: plant ID {} is not a positive integer.", plantInfo.plantId());
            return false;
        }
        if (plantInfo.address() == null || plantInfo.address().trim().isEmpty()) {
            logger.warn("Validation failed for Plant {}: address is null or empty.", plantInfo.plantId());
            return false;
        }
        if (plantInfo.port() <= 0 || plantInfo.port() > 65535) {
            logger.warn("Validation failed for Plant {}: port {} is outside the valid range (1-65535).", plantInfo.plantId(), plantInfo.port());
            return false;
        }
        return true;
    }
}