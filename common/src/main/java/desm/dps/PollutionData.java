package desm.dps;

import java.util.List;
import java.util.Objects;

/**
 * Represents a set of pollution data averages from a specific power plant.
 */
public record PollutionData(int plantId, long listComputationTimestamp, List<Double> averages) {
    public PollutionData(int plantId, long listComputationTimestamp, List<Double> averages) {
        // --- Validation ---
        this.plantId = plantId;
        this.listComputationTimestamp = listComputationTimestamp;
        Objects.requireNonNull(averages, "averages list cannot be null");

        // --- Defensive Copying for Immutability ---
        // List.copyOf() creates an unmodifiable copy. This is critical for true immutability,
        // as it protects the internal list from outside modification.
        this.averages = List.copyOf(averages);
    }

    /**
     * Returns the list of averages.
     *
     * @return An unmodifiable list of averages. Any attempt to modify it (e.g., .add())
     * will throw an UnsupportedOperationException, protecting the object's integrity.
     */
    @Override
    public List<Double> averages() {
        return averages;
    }
}
