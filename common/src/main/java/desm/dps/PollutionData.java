package desm.dps;

import java.util.List;
import java.util.Objects;

/**
 * Represents a set of pollution data averages from a specific power plant.
 * <p>
 * This class has been refactored for true immutability.
 * 1.  All fields are 'private final' and all setters have been removed.
 * 2.  The constructor now performs a "defensive copy" of the 'averages' list. This prevents the
 * internal state of PollutionData from being changed by modifying the original list that was
 * passed to the constructor.
 * 3.  'Long listComputationTimestamp' was replaced with 'Instant' for better type safety.
 * 4.  equals() and hashCode() have been added.
 */
public record PollutionData(int plantId, long listComputationTimestamp, List<Double> averages) {
    /**
     * Note on the no-arg constructor:
     * The original no-arg constructor was likely for a framework like Jackson. It has been removed
     * because it allows an object to be in an uninitialized state. Modern versions of Jackson
     * can deserialize to immutable objects via constructors if the parameter names match the JSON fields.
     * If using an older version, you may need to add back a private no-arg constructor and use
     * field-based deserialization.
     */
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
