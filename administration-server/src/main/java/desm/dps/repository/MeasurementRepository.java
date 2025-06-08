package desm.dps.repository;

import desm.dps.PollutionData;
import desm.dps.StoredPollutionDataEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@Scope(ConfigurableBeanFactory.SCOPE_SINGLETON)
public class MeasurementRepository {
    private static final Logger logger = LoggerFactory.getLogger(MeasurementRepository.class);

    // Stores pollution data entries. Each plant ID maps to a list of its reported data batches.
    // Using a simple List<StoredPollutionDataEntry> for all entries, as queries span all plants.
    private final List<StoredPollutionDataEntry> allPollutionEntries;
    private final Object lock = new Object(); // For synchronizing access to allPollutionEntries [cite: 116]

    public MeasurementRepository() {
        allPollutionEntries = new ArrayList<>();
    }
    /**
     * Adds pollution data received from a power plant.
     * This operation is synchronized to ensure thread-safe modification of the shared data structure. [cite: 104]
     * @param data The PollutionData object containing plant ID, timestamp, and averages.
     */
    public void addPollutionData(PollutionData data) {
        if (data == null || data.plantId() == null || data.averages() == null) {
            logger.warn("Received null or incomplete pollution data. Ignoring.");
            return;
        }
        synchronized (lock) {
            StoredPollutionDataEntry entry = new StoredPollutionDataEntry(
                    data.plantId(),
                    data.listComputationTimestamp(),
                    new ArrayList<>(data.averages()) // Store a copy
            );
            allPollutionEntries.add(entry);
            logger.info("Stored pollution data from plant {}: {} averages, timestamp {}.",
                    data.plantId(), data.averages().size(), data.listComputationTimestamp());
        }
    }

    /**
     * Calculates the average of the CO2 emission levels sent by all plants
     * whose listComputationTimestamp falls within the given time range [t1, t2].
     * Each emission level is represented by one list of averages sent by a plant.
     * This method computes the average of each list, then averages those averages.
     * This operation is synchronized for thread-safe reading of the shared data structure. [cite: 104]
     * @param t1 The start timestamp (inclusive).
     * @param t2 The end timestamp (inclusive).
     * @return The average CO2 value, or Double.NaN if no data is found in the range.
     */
    public double getAverageCo2BetweenTimestamps(long t1, long t2) {
        List<Double> emissionLevelAverages = new ArrayList<>();
        synchronized (lock) {
            logger.info("Total entries in repository: {}", allPollutionEntries.size());
            if (allPollutionEntries.isEmpty()) {
                logger.info("No pollution entries available for averaging.");
                return Double.NaN;
            }
            for (StoredPollutionDataEntry entry : allPollutionEntries) {
                boolean inRange = entry.listComputationTimestamp() >= t1 && entry.listComputationTimestamp() <= t2;
                logger.debug("Entry: plantId={}, timestamp={}, inRange={}",
                        entry.plantId(), entry.listComputationTimestamp(),
                        inRange);
                if (inRange) {
                    // Calculate the average of this emission level (list of averages)
                    if (!entry.averages().isEmpty()) {
                        double sum = 0;
                        for (Double value : entry.averages()) {
                            sum += value;
                        }
                        double emissionLevelAverage = sum / entry.averages().size();
                        emissionLevelAverages.add(emissionLevelAverage);
                        logger.debug("Plant {} emission level average: {} (from {} values)",
                                entry.plantId(), String.format("%.2f", emissionLevelAverage), entry.averages().size());
                    }
                }
            }


            if (emissionLevelAverages.isEmpty()) {
                logger.info("No pollution data found between timestamps {} and {} for averaging. All values: {}", t1, t2, allPollutionEntries);
                return Double.NaN;
            }
        }

        // Calculate the average of all emission level averages
        double sum = 0;
        for (Double emissionAverage : emissionLevelAverages) {
            sum += emissionAverage;
        }
        double overallAverage = sum / emissionLevelAverages.size();
        logger.info("Calculated overall average CO2 of {} from {} emission levels between {} and {}.",
                String.format("%.2f", overallAverage), emissionLevelAverages.size(), t1, t2);
        return overallAverage;
    }
}
