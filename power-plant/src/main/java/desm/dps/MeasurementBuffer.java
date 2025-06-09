package desm.dps;

import desm.dps.Simulators.Buffer;
import desm.dps.Simulators.Measurement;

import java.util.ArrayList;
import java.util.List;

public class MeasurementBuffer implements Buffer {
    // Volatile to ensure visibility of the reference to the list if it were to be re-assigned,
    // though with clear() and add(), direct modifications are more common.
    // The internal state of ArrayList is not volatile.
    private final List<Measurement> measurements = new ArrayList<>();
    private final Object lock = new Object(); // Lock for synchronizing access to 'measurements'

    @Override
    public void addMeasurement(Measurement m) {
        synchronized (lock) {
            measurements.add(m);
        }
    }

    /**
     * Reads all current measurements from the buffer and then clears the buffer.
     * This method is synchronized to ensure thread-safe access and modification
     * of the internal measurements list. [cite: 82, 83]
     * @return A list containing all measurements that were in the buffer.
     */
    @Override
    public List<Measurement> readAllAndClean() {
        synchronized (lock) {
            if (measurements.isEmpty()) {
                return new ArrayList<>(); // Return empty list if no measurements
            }
            // Create a new list to return, so the caller has a snapshot
            // and is not affected by subsequent modifications to the internal list
            // if they held onto the reference for too long (though it's cleared here).
            List<Measurement> currentMeasurements = new ArrayList<>(measurements);
            measurements.clear(); // Clear the internal buffer after reading [cite: 83]
            return currentMeasurements;
        }
    }
}