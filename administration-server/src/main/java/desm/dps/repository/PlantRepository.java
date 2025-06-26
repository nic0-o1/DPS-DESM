package desm.dps.repository;

import desm.dps.PowerPlantInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A thread-safe, in-memory repository for managing the registration of power plants.
 */
@Service
public class PlantRepository {
    private static final Logger logger = LoggerFactory.getLogger(PlantRepository.class);

    private final Map<Integer, PowerPlantInfo> registeredPlants = new HashMap<>();
    private final Object lock = new Object();

    /**
     * Registers a new power plant.
     *
     * @param toRegister The information of the plant to register.
     * @return {@code true} if the plant was successfully registered, {@code false} if a plant with the same ID already exists.
     */
    public boolean registerPlant(PowerPlantInfo toRegister) {
        synchronized (lock) {
            if (registeredPlants.containsKey(toRegister.plantId())) {
                return false;
            }
            registeredPlants.put(toRegister.plantId(), toRegister);
            logger.info("Registered new plant in repository: {}", toRegister);
            return true;
        }
    }

    /**
     * Retrieves a power plant by its unique ID.
     *
     * @param plantId The ID of the plant to find.
     * @return The {@link PowerPlantInfo} if found, otherwise {@code null}.
     */
    public PowerPlantInfo getPlantById(int plantId) {
        synchronized (lock) {
            return registeredPlants.get(plantId);
        }
    }

    /**
     * Retrieves an immutable list of all currently registered power plants.
     *
     * @return An unmodifiable list of all plants.
     */
    public List<PowerPlantInfo> getAllPlants() {
        synchronized (lock) {
            return List.copyOf(registeredPlants.values());
        }
    }
}