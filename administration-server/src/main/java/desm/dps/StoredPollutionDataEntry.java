package desm.dps;

import java.util.List;

/**
 * @param plantId                  ID of the plant that sent this data
 * @param listComputationTimestamp Timestamp when the plant computed this list of averages
 * @param averages                 The list of CO2 averages
 */
public record StoredPollutionDataEntry(String plantId, long listComputationTimestamp, List<Double> averages) {
}
