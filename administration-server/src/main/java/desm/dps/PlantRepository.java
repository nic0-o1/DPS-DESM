package desm.dps;

import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class PlantRepository {
    private final HashMap<String, PowerPlantInfo> registeredPlants = new HashMap<>();

    public synchronized boolean registerPlant(PowerPlantInfo toRegister) {
        if (registeredPlants.containsKey(toRegister.getPlantId())) {
            return false;
        }
        registeredPlants.put(toRegister.getPlantId(), toRegister);
        System.out.println("Registered new plant: " + toRegister);
        return true;
    }

    public synchronized PowerPlantInfo getPlantById(String plantId) {
        return registeredPlants.get(plantId);
    }

    public synchronized List<PowerPlantInfo> getAllPlants() {
        return List.copyOf(registeredPlants.values());
    }
}
