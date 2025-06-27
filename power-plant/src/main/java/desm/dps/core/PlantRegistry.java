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

    private volatile PowerPlantInfo[] cachedRingArray = null;

    public PlantRegistry(PowerPlantInfo selfInfo) {
        this.selfInfo = selfInfo;
    }

    /**
     * Populates the registry with an initial list of plants, for instance, from an admin server.
     * Skips this plant's own info and any duplicates. This operation is thread-safe.
     *
     * @param initialPlants The list of plants to add.
     */
    public void addInitialPlants(List<PowerPlantInfo> initialPlants) {
        synchronized (lock) {
            for (PowerPlantInfo plant : initialPlants) {
                // Ensure we don't add ourselves or an existing plant.
                if (plant.plantId() != selfInfo.plantId() && !plantExistsUnsafe(plant.plantId())) {
                    otherPlantsList.add(plant);
                }
            }
            sortAndInvalidateCache();
        }
    }

    /**
     * Adds a single new plant to the registry if it's not already present.
     * This operation is thread-safe and invalidates the ring cache.
     *
     * @param newPlant The plant to add.
     */
    public void addPlant(PowerPlantInfo newPlant) {
        if (newPlant == null || newPlant.plantId() == selfInfo.plantId()) {
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
     * This operation is thread-safe and invalidates the ring cache.
     *
     * @param plantId The ID of the plant to remove.
     */
    public void removePlant(int plantId) {
        synchronized (lock) {
            if (otherPlantsList.removeIf(plant -> plant.plantId() == plantId)) {
                sortAndInvalidateCache();
                logger.debug("Removed plant {}. Total known other plants: {}", plantId, otherPlantsList.size());
            }
        }
    }

    /**
     * Gets the next plant in the logical ring. The ring is sorted by registration time.
     * This method is highly optimized for reads using a lock-free "fast path" that relies on a volatile cache.
     * A lock is only acquired if the cache needs to be rebuilt.
     *
     * @param currentPlantId The ID of the plant from which to find the next one. If null, returns this plant's own info.
     * @return The {@link PowerPlantInfo} of the next plant in the ring.
     */
    public PowerPlantInfo getNextInRing(Integer currentPlantId) {
        if (currentPlantId == null) {
            return selfInfo;
        }
        // Fast path: Read the volatile cache. No lock is acquired if the cache is valid.
        PowerPlantInfo[] ring = this.cachedRingArray;
        if (ring != null) {
            PowerPlantInfo next = findNextInArray(ring, currentPlantId);
            if (next != null) {
                return next;
            }
        }
        // Slow path: Acquire lock to check and potentially rebuild the cache.
        synchronized (lock) {
            // Double-checked locking: re-read the cache in case another thread built it while we waited for the lock.
            ring = this.cachedRingArray;
            if (ring == null) {
                logger.info("Rebuilding sorted plant ring cache...");
                ring = buildCompleteRing();
                this.cachedRingArray = ring;
            }
            return findNextInArray(ring, currentPlantId);
        }
    }

    /**
     * Returns a thread-safe snapshot of the current list of other known plants.
     *
     * @return A new {@link ArrayList} containing the other known plants.
     */
    public List<PowerPlantInfo> getOtherPlantsSnapshot() {
        synchronized (lock) {
            return new ArrayList<>(otherPlantsList);
        }
    }

    /**
     * Gets the count of other known plants in the registry.
     *
     * @return The number of other plants.
     */
    public int getOtherPlantsCount() {
        synchronized (lock) {
            return otherPlantsList.size();
        }
    }

    // --- Unsafe helpers ---

    /**
     * Checks if a plant exists. This is an internal helper and must be called from within a synchronized block.
     */
    private boolean plantExistsUnsafe(int plantId) {
        return otherPlantsList.stream().anyMatch(p -> p.plantId() == plantId);
    }

    /**
     * Sorts the list of other plants and invalidates the cache.
     * This is an internal helper and must be called from within a synchronized block.
     */
    private void sortAndInvalidateCache() {
        otherPlantsList.sort(Comparator.comparing(PowerPlantInfo::registrationTime));
        this.cachedRingArray = null; // Invalidate cache on any modification
    }

    /**
     * Builds the complete, sorted ring including this plant.
     * This is an internal helper and must be called from within a synchronized block.
     */
    private PowerPlantInfo[] buildCompleteRing() {
        List<PowerPlantInfo> allPlants = new ArrayList<>(otherPlantsList.size() + 1);
        allPlants.addAll(otherPlantsList);
        allPlants.add(selfInfo);
        allPlants.sort(Comparator.comparing(PowerPlantInfo::registrationTime));
        return allPlants.toArray(new PowerPlantInfo[0]);
    }

    /**
     * Finds the next plant in a given array representing the ring.
     */
    private PowerPlantInfo findNextInArray(PowerPlantInfo[] ring, int currentPlantId) {
        for (int i = 0; i < ring.length; i++) {
            if (ring[i].plantId() == currentPlantId) {
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