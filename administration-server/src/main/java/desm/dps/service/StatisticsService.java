package desm.dps.service;

import desm.dps.repository.MeasurementRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class StatisticsService {
    private static final Logger logger = LoggerFactory.getLogger(StatisticsService.class);
    private final MeasurementRepository measurementRepository;

    public StatisticsService(MeasurementRepository measurementRepository) {
        this.measurementRepository = measurementRepository;
    }

    /**
     * Computes the average CO2 emission levels across all plants within a specified time range.
     * This is based on the timestamps when the lists of averages were sent by the plants. [cite: 107, 15]
     * @param t1 Start timestamp of the query range.
     * @param t2 End timestamp of the query range.
     * @return The computed average, or Double.NaN if no data is available.
     */
    public double getAverageCo2BetweenTimestamps(long t1, long t2) {
        if (t1 > t2) {
            logger.warn("Invalid timestamp range for CO2 average: t1 ({}) > t2 ({}).", t1, t2);
            throw new IllegalArgumentException("Start timestamp (t1) cannot be after end timestamp (t2).");
        }
        logger.info("Calculating average CO2 emissions between timestamps: {} and {}", t1, t2);
        return measurementRepository.getAverageCo2BetweenTimestamps(t1, t2);
    }

}
