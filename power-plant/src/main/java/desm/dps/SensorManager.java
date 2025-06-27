package desm.dps;

import desm.dps.Simulators.Buffer;
import desm.dps.Simulators.Measurement;
import desm.dps.Simulators.PollutionSensor;
import desm.dps.Simulators.Simulator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Manages sensor readings by processing them in a sliding window to compute periodic averages.
 * This class runs as a dedicated thread, consuming measurements from a buffer and producing
 * average values.
 */
public class SensorManager extends Thread {
    private static final Logger logger = LoggerFactory.getLogger(SensorManager.class);

    private static final int WINDOW_SIZE = 8;
    private static final int DISCARD_COUNT = 4; // Number of oldest items to discard to create a 50% overlap.

    private final Buffer rawMeasurementBuffer;
    private final Simulator pollutionSensor;
    private final Object processingLock = new Object();

    // --- State fields guarded by processingLock ---
    private final Deque<Measurement> currentWindow;
    private final List<Double> computedAverages;
    // --- End of guarded state fields ---

    private volatile boolean stopCondition = false;

    public SensorManager(String name) {
        super(name);
        this.rawMeasurementBuffer = new MeasurementBuffer();
        this.pollutionSensor = new PollutionSensor(this.rawMeasurementBuffer);
        this.currentWindow = new LinkedList<>();
        this.computedAverages = new ArrayList<>();
    }

    /**
     * The main execution loop of the sensor manager. It continuously reads new measurements,
     * updates the sliding window, and computes averages when the window is full.
     */
    @Override
    public void run() {
        logger.info("SensorManager thread starting...");
        pollutionSensor.start();

        while (!stopCondition) {
            List<Measurement> newMeasurements = rawMeasurementBuffer.readAllAndClean();

            if (!newMeasurements.isEmpty()) {
                synchronized (processingLock) {
                    currentWindow.addAll(newMeasurements);
                    logger.debug("Added {} new measurements. Current window size: {}.", newMeasurements.size(), currentWindow.size());

                    // Process all full windows that can be formed from the current data.
                    while (currentWindow.size() >= WINDOW_SIZE) {
                        processWindow();
                    }
                }
            }
        }

        shutdownSensor();
        logger.info("SensorManager thread stopped.");
    }

    /**
     * Processes one full window of measurements: computes the average and slides the window forward.
     * This method must be called within a block synchronized on {@code processingLock}.
     */
    private void processWindow() {
        List<Measurement> windowSnapshot = currentWindow.stream()
                .limit(WINDOW_SIZE)
                .collect(Collectors.toList());

        double average = computeAverage(windowSnapshot);
        computedAverages.add(average);

        logger.info("Computed average CO2: {}. Total averages stored: {}.",
                String.format("%.2f", average), computedAverages.size());

        // Slide the window by removing the oldest measurements.
        for (int i = 0; i < DISCARD_COUNT && !currentWindow.isEmpty(); i++) {
            currentWindow.removeFirst();
        }
        logger.debug("Discarded {} measurements. Remaining in window: {}.", DISCARD_COUNT, currentWindow.size());
    }

    /**
     * Computes the average value from a list of measurements.
     */
    private double computeAverage(List<Measurement> measurements) {
        if (measurements == null || measurements.isEmpty()) {
            return 0.0;
        }
        return measurements.stream()
                .mapToDouble(Measurement::getValue)
                .average()
                .orElse(0.0);
    }

    /**
     * Retrieves all computed averages and clears the internal storage. This is a thread-safe operation.
     *
     * @return A snapshot of all computed averages. Returns an empty list if none are available.
     */
    public List<Double> getAndClearAverages() {
        synchronized (processingLock) {
            if (computedAverages.isEmpty()) {
                return new ArrayList<>();
            }
            List<Double> averagesToReturn = new ArrayList<>(computedAverages);
            computedAverages.clear();
            logger.debug("Retrieved and cleared {} computed averages.", averagesToReturn.size());
            return averagesToReturn;
        }
    }

    /**
     * Signals the SensorManager to stop its execution loop and clean up resources.
     */
    public void stopManager() {
        logger.info("Stopping SensorManager thread...");
        this.stopCondition = true;
        this.interrupt(); // Interrupt in case the thread is sleeping.
    }

    /**
     * Handles the graceful shutdown of the underlying pollution sensor.
     */
    private void shutdownSensor() {
        pollutionSensor.stopMeGently();
        try {
            pollutionSensor.join(1000); // Wait up to 1 second for the sensor thread to die.
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warn("Interrupted while waiting for pollution sensor thread to join.");
        }
    }
}