package desm.dps;

import java.util.List;
import java.util.Objects;

/**
 * An immutable record representing a batch of pollution data from a specific power plant.
 * It contains the plant's ID, a timestamp, and a list of computed CO2 averages.
 *
 * @param plantId The ID of the power plant that produced this data.
 * @param listComputationTimestamp The timestamp when the list of averages was finalized.
 * @param averages A list of computed average pollution values. A defensive copy is made to ensure immutability.
 */
public record PollutionData(int plantId, long listComputationTimestamp, List<Double> averages) {

    /**
     * Constructs a new PollutionData object.
     * To ensure true immutability, this constructor creates a defensive, unmodifiable copy of the provided list of averages.
     *
     * @throws NullPointerException if the {@code averages} list is null.
     */
    public PollutionData(int plantId, long listComputationTimestamp, List<Double> averages) {
        this.plantId = plantId;
        this.listComputationTimestamp = listComputationTimestamp;
        this.averages = List.copyOf(Objects.requireNonNull(averages, "Averages list cannot be null."));
    }

    /**
     * Returns the list of computed pollution averages.
     *
     * @return An unmodifiable list of averages. Any attempt to modify the returned list
     *         (e.g., via {@code .add()}) will result in an {@link UnsupportedOperationException}.
     */
    @Override
    public List<Double> averages() {
        return this.averages;
    }
}