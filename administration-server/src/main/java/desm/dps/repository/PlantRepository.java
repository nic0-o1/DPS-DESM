package desm.dps.repository;

import desm.dps.PowerPlantInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A thread-safe, in-memory repository for managing registered {@link PowerPlantInfo} objects.
 * As a Spring {@link Service}, this class is instantiated as a singleton by the dependency
 * injection container, ensuring a single, consistent source for all plant
 *  registrations across the application.
 */
@Service
public class PlantRepository {
    private static final Logger logger = LoggerFactory.getLogger(PlantRepository.class);

    private final Map<Integer, PowerPlantInfo> registeredPlants = new HashMap<>();
    private final Object lock = new Object();

    /**
     * Registers a new power plant if its ID is not already present.
     * This operation is atomic and thread-safe.
     *
     * @param toRegister The {@link PowerPlantInfo} of the plant to register.
     * @return {@code true} if the plant was successfully registered, {@code false} if a plant with the same ID already exists.
     */
    public boolean registerPlant(PowerPlantInfo toRegister) {
        synchronized (lock) {
            if (registeredPlants.containsKey(toRegister.plantId())) {
                logger.warn("Attempt to register a duplicate plant with ID {}. Registration denied.", toRegister.plantId());
                return false;
            }
            registeredPlants.put(toRegister.plantId(), toRegister);
            logger.info("Registered new plant (ID: {}) in repository.", toRegister.plantId());
            return true;
        }
    }

    /**
     * Retrieves a power plant by its unique ID.
     * This operation is thread-safe.
     *
     * @param plantId The ID of the plant to retrieve.
     * @return The {@link PowerPlantInfo} if found, otherwise {@code null}.
     */
    public PowerPlantInfo getPlantById(int plantId) {
        synchronized (lock) {
            return registeredPlants.get(plantId);
        }
    }

    /**
     * Retrieves a snapshot of all currently registered power plants.
     * This operation is thread-safe.
     *
     * @return An unmodifiable {@link List} containing all registered plants.
     *         Attempting to modify the returned list will result in an {@link UnsupportedOperationException}.
     */
    public List<PowerPlantInfo> getAllPlants() {
        synchronized (lock) {
            return List.copyOf(registeredPlants.values());
        }
    }
}