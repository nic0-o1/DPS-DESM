package desm.dps;

import desm.dps.Simulators.Buffer;
import desm.dps.Simulators.Measurement;

import java.util.ArrayList;
import java.util.List;

/**
 * A thread-safe buffer for storing {@link Measurement} objects.
 * This implementation uses manual synchronization to manage concurrent access
 * from a producer thread (e.g., a sensor) and a consumer thread.
 */
public class MeasurementBuffer implements Buffer {
    private final List<Measurement> measurements = new ArrayList<>();
    private final Object lock = new Object(); // A dedicated lock for synchronizing access to the list.

    /**
     * Adds a single measurement to the buffer in a thread-safe manner.
     *
     * @param m The measurement to add.
     */
    @Override
    public void addMeasurement(Measurement m) {
        synchronized (lock) {
            measurements.add(m);
        }
    }

    /**
     * Atomically retrieves all measurements currently in the buffer and then clears it.
     * This ensures that no measurements are lost or read more than once.
     *
     * @return A new list containing a snapshot of all measurements that were in the buffer.
     *         Returns an empty list if the buffer was empty.
     */
    @Override
    public List<Measurement> readAllAndClean() {
        synchronized (lock) {
            if (measurements.isEmpty()) {
                return new ArrayList<>();
            }
            // Create a copy to return. The caller receives a snapshot of the buffer's
            // state, and the internal buffer can be safely cleared.
            List<Measurement> currentMeasurements = new ArrayList<>(measurements);
            measurements.clear();
            return currentMeasurements;
        }
    }
}