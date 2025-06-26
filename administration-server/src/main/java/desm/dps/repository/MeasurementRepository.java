package desm.dps.repository;

import desm.dps.PollutionData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * A thread-safe, in-memory repository for storing and retrieving pollution measurements.
 * This class acts as a singleton data store for all pollution data received from power plants.
 */
@Service
@Scope(ConfigurableBeanFactory.SCOPE_SINGLETON)
public class MeasurementRepository {
    private static final Logger logger = LoggerFactory.getLogger(MeasurementRepository.class);

    private final List<StoredPollutionDataEntry> allPollutionEntries = new ArrayList<>();
    private final Object lock = new Object();

    /**
     * Adds pollution data from a power plant to the central repository.
     * This operation is thread-safe.
     *
     * @param data The {@link PollutionData} object to store.
     */
    public void addPollutionData(PollutionData data) {
        if (data == null || data.averages() == null) {
            logger.warn("Attempted to add null or incomplete pollution data. Ignoring.");
            return;
        }
        synchronized (lock) {
            StoredPollutionDataEntry entry = new StoredPollutionDataEntry(
                    data.plantId(),
                    data.listComputationTimestamp(),
                    data.averages() // data.averages() is already an immutable list
            );
            allPollutionEntries.add(entry);
            logger.debug("Stored pollution data from Plant {}: {} averages with timestamp {}.",
                    data.plantId(), data.averages().size(), data.listComputationTimestamp());
        }
    }

    /**
     * Calculates the overall average of CO2 emissions from all data entries
     * whose computation timestamp falls within the given time range [t1, t2].
     * The method first calculates the average for each plant's list of data within the range,
     * and then calculates the average of those resulting averages. This operation is thread-safe.
     *
     * @param t1 The start timestamp of the query range (inclusive).
     * @param t2 The end timestamp of the query range (inclusive).
     * @return The overall average CO2 value, or {@link Double#NaN} if no data is found in the range.
     */
    public double getAverageCo2BetweenTimestamps(long t1, long t2) {
        List<Double> perPlantAverages = new ArrayList<>();
        synchronized (lock) {
            logger.debug("Calculating average CO2 across {} total entries for range [{}, {}].", allPollutionEntries.size(), t1, t2);
            for (StoredPollutionDataEntry entry : allPollutionEntries) {
                if (entry.listComputationTimestamp() >= t1 && entry.listComputationTimestamp() <= t2) {
                    if (!entry.averages().isEmpty()) {
                        double plantAverage = entry.averages().stream()
                                .mapToDouble(Double::doubleValue)
                                .average()
                                .orElse(0.0);
                        perPlantAverages.add(plantAverage);
                        logger.trace("Included entry from Plant {} with average {}.", entry.plantId(), plantAverage);
                    }
                }
            }
        }

        if (perPlantAverages.isEmpty()) {
            logger.warn("No pollution data found between timestamps {} and {} to calculate an average.", t1, t2);
            return Double.NaN;
        }

        double overallAverage = perPlantAverages.stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(Double.NaN);

        logger.info("Calculated overall average CO2 of {} from {} data points between {} and {}.",
                String.format("%.2f", overallAverage), perPlantAverages.size(), t1, t2);
        return overallAverage;
    }
}