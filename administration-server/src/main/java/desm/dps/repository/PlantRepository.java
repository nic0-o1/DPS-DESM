package desm.dps.repository;

import desm.dps.PowerPlantInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;

@Service
public class PlantRepository {
    private static final Logger logger = LoggerFactory.getLogger(PlantRepository.class);

    private final HashMap<Integer, PowerPlantInfo> registeredPlants = new HashMap<>();

    public synchronized boolean registerPlant(PowerPlantInfo toRegister) {
        if (registeredPlants.containsKey(toRegister.plantId())) {
            return false;
        }
        registeredPlants.put(toRegister.plantId(), toRegister);
        logger.info("Registered new plant: {}", toRegister);
        return true;
    }

    public synchronized PowerPlantInfo getPlantById(int plantId) {
        return registeredPlants.get(plantId);
    }

    public synchronized List<PowerPlantInfo> getAllPlants() {
        return List.copyOf(registeredPlants.values());
    }
}