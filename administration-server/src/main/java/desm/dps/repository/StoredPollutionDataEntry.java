package desm.dps.repository;

import java.util.List;

/**
 * An immutable record representing a single pollution data submission stored in the repository.
 *
 * @param plantId                  The ID of the plant that sent this data.
 * @param listComputationTimestamp The timestamp when the plant computed this list of averages.
 * @param averages                 The immutable list of CO2 averages from the plant.
 */
public record StoredPollutionDataEntry(int plantId, long listComputationTimestamp, List<Double> averages) {
}