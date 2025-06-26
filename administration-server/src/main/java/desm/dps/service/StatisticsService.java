package desm.dps.service;

import desm.dps.repository.MeasurementRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * A service layer for handling business logic related to statistical calculations.
 */
@Service
public class StatisticsService {
    private static final Logger logger = LoggerFactory.getLogger(StatisticsService.class);
    private final MeasurementRepository measurementRepository;

    public StatisticsService(MeasurementRepository measurementRepository) {
        this.measurementRepository = measurementRepository;
    }

    /**
     * Computes the average CO2 emission levels across all plants within a specified time range.
     * This method validates the timestamp range before querying the repository.
     *
     * @param t1 The start timestamp of the query range.
     * @param t2 The end timestamp of the query range.
     * @return The computed average, or {@link Double#NaN} if no data is available in the range.
     * @throws IllegalArgumentException if t1 is after t2.
     */
    public double getAverageCo2BetweenTimestamps(long t1, long t2) {
        if (t1 > t2) {
            throw new IllegalArgumentException("Start timestamp (t1) cannot be after end timestamp (t2).");
        }
        logger.info("Service calculating average CO2 emissions between timestamps: {} and {}", t1, t2);
        return measurementRepository.getAverageCo2BetweenTimestamps(t1, t2);
    }
}