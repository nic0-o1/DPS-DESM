package desm.dps.core;

import desm.dps.PowerPlantInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Manages the list of known power plants and the logical ring topology used for communication.
 */
public class PlantRegistry {
    private static final Logger logger = LoggerFactory.getLogger(PlantRegistry.class);

    private final PowerPlantInfo selfInfo;

    private final List<PowerPlantInfo> otherPlantsList = new ArrayList<>();
    private final Object lock = new Object();

    // A volatile, cached array of the complete ring for fast, lock-free lookups.
    private volatile PowerPlantInfo[] cachedRingArray = null;

    public PlantRegistry(PowerPlantInfo selfInfo) {
        this.selfInfo = selfInfo;
    }

    /**
     * Adds a list of newly discovered plants from the admin server.
     *
     * @param initialPlants The list of plants to add.
     */
    public void addInitialPlants(List<PowerPlantInfo> initialPlants) {
        synchronized (lock) {
            for (PowerPlantInfo plant : initialPlants) {
                if (!plant.plantId().equals(selfInfo.plantId()) && !plantExistsUnsafe(plant.plantId())) {
                    otherPlantsList.add(plant);
                }
            }
            sortAndInvalidateCache();
        }
    }

    /**
     * Adds a single new plant to the registry if it's not already present.
     *
     * @param newPlant The plant to add.
     */
    public void addPlant(PowerPlantInfo newPlant) {
        if (newPlant == null || newPlant.plantId().equals(selfInfo.plantId())) {
            return;
        }
        synchronized (lock) {
            if (plantExistsUnsafe(newPlant.plantId())) {
                return;
            }
            otherPlantsList.add(newPlant);
            sortAndInvalidateCache();
            logger.debug("Added new plant {}. Total known other plants: {}", newPlant.plantId(), otherPlantsList.size());
        }
    }

    /**
     * Removes a plant from the registry by its ID.
     *
     * @param plantId The ID of the plant to remove.
     */
    public void removePlant(String plantId) {
        if (plantId == null || plantId.isEmpty()) {
            return;
        }
        synchronized (lock) {
            if (otherPlantsList.removeIf(plant -> plant.plantId().equals(plantId))) {
                sortAndInvalidateCache();
                logger.debug("Removed plant {}. Total known other plants: {}", plantId, otherPlantsList.size());
            }
        }
    }

    /**
     * Gets the next plant in the logical ring, which is sorted by plant ID.
     * This method is highly optimized for reads using a lock-free cache.
     *
     * @param currentPlantId The ID of the plant from which to find the next one.
     * @return The {@link PowerPlantInfo} of the next plant in the ring.
     */
    public PowerPlantInfo getNextInRing(String currentPlantId) {
        if (currentPlantId == null) {
            return selfInfo;
        }
        PowerPlantInfo[] ring = this.cachedRingArray;
        if (ring != null) {
            PowerPlantInfo next = findNextInArray(ring, currentPlantId);
            if (next != null) return next;
        }
        synchronized (lock) {
            ring = this.cachedRingArray; // Double-check after acquiring lock
            if (ring == null) {
                ring = buildCompleteRing();
                this.cachedRingArray = ring;
            }
            return findNextInArray(ring, currentPlantId);
        }
    }

    /**
     * Returns a thread-safe snapshot of the current list of other plants.
     *
     * @return A new list containing the other known plants.
     */
    public List<PowerPlantInfo> getOtherPlantsSnapshot() {
        synchronized (lock) {
            return new ArrayList<>(otherPlantsList);
        }
    }

    /**
     * Gets the count of other known plants.
     * @return The number of other plants in the registry.
     */
    public int getOtherPlantsCount() {
        synchronized (lock) {
            return otherPlantsList.size();
        }
    }

    // --- Unsafe helpers ---

    private boolean plantExistsUnsafe(String plantId) {
        return otherPlantsList.stream().anyMatch(p -> p.plantId().equals(plantId));
    }

    private void sortAndInvalidateCache() {
        otherPlantsList.sort(Comparator.comparing(PowerPlantInfo::plantId));
        this.cachedRingArray = null; // Invalidate cache on any modification
    }

    private PowerPlantInfo[] buildCompleteRing() {
        List<PowerPlantInfo> allPlants = new ArrayList<>(otherPlantsList.size() + 1);
        allPlants.addAll(otherPlantsList);
        allPlants.add(selfInfo);
        allPlants.sort(Comparator.comparing(PowerPlantInfo::plantId));
        return allPlants.toArray(new PowerPlantInfo[0]);
    }

    private PowerPlantInfo findNextInArray(PowerPlantInfo[] ring, String currentPlantId) {
        for (int i = 0; i < ring.length; i++) {
            if (ring[i].plantId().equals(currentPlantId)) {
                return ring[(i + 1) % ring.length]; // Wrap around
            }
        }
        if (ring.length > 0) {
            logger.warn("Plant {} not found in ring. Defaulting to first plant.", currentPlantId);
            return ring[0];
        }
        return selfInfo;
    }
}