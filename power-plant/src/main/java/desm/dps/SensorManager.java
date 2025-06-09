package desm.dps;

import desm.dps.Simulators.Buffer;
import desm.dps.Simulators.Measurement;
import desm.dps.Simulators.PollutionSensor;
import desm.dps.Simulators.Simulator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Manages the sensor readings and processes them in a sliding window to compute average CO2 values.
 */
public class SensorManager extends Thread {
    private static final Logger logger = LoggerFactory.getLogger(SensorManager.class);

    private static final int WINDOW_SIZE = 8;       // Full window size
    private static final int OLDEST_TO_DISCARD = 4; // Overlap size (50%)

    private final Buffer rawMeasurementBuffer;
    private final Simulator pollutionSensor;

    private final List<Measurement> currentWindow;
    private final List<Double> computedAverages;

    private final Object windowLock = new Object();
    private volatile boolean stopCondition = false;

    public SensorManager(String name) {
        super(name);
        this.rawMeasurementBuffer = new MeasurementBuffer();
        this.pollutionSensor = new PollutionSensor(this.rawMeasurementBuffer);
        this.currentWindow = new ArrayList<>();
        this.computedAverages = new ArrayList<>();
    }

    @Override
    public void run() {
        logger.info("SensorManager starting...");
        pollutionSensor.start();

        while (!stopCondition) {
            List<Measurement> newMeasurements = rawMeasurementBuffer.readAllAndClean();

            if (!newMeasurements.isEmpty()) {
                synchronized (windowLock) {
                    currentWindow.addAll(newMeasurements);
                    logger.debug("Added {} new measurements. Current window size: {}", newMeasurements.size(), currentWindow.size());

                    while (currentWindow.size() >= WINDOW_SIZE) {
                        List<Measurement> window = new ArrayList<>(currentWindow.subList(0, WINDOW_SIZE));
                        double average = computeAverage(window);
                        computedAverages.add(average);

                        logger.info("Computed average {} from window of size {}. Total averages stored: {}",
                                String.format("%.2f", average), WINDOW_SIZE, computedAverages.size());

                        for (int i = 0; i < OLDEST_TO_DISCARD && !currentWindow.isEmpty(); i++) {
                            currentWindow.remove(0);
                        }
                        logger.debug("Discarded {} oldest measurements. Remaining window size: {}", OLDEST_TO_DISCARD, currentWindow.size());
                    }
                }
            }
        }

        pollutionSensor.stopMeGently();
        try {
            pollutionSensor.join(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warn("Interrupted while joining pollution sensor thread.");
        }

        logger.info("SensorManager stopped.");
    }

    /**
     * Computes the average value from a list of measurements.
     */
    private double computeAverage(List<Measurement> window) {
        double sum = 0;
        for (Measurement m : window) {
            sum += m.getValue();
        }
        return sum / window.size();
    }

    /**
     * Returns all computed averages and clears the internal list.
     * Thread-safe method.
     */
    public List<Double> getAndClearAverages() {
        synchronized (windowLock) {
            if (computedAverages.isEmpty()) {
                return new ArrayList<>();
            }
            List<Double> averagesToReturn = new ArrayList<>(computedAverages);
            computedAverages.clear();
            logger.debug("Retrieved and cleared {} averages.", averagesToReturn.size());
            return averagesToReturn;
        }
    }

    /**
     * Requests the SensorManager to stop and interrupts its loop.
     */
    public void stopManager() {
        logger.info("Stopping SensorManager...");
        this.stopCondition = true;
        this.interrupt();
    }
}
