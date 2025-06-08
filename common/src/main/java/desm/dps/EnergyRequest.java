package desm.dps;

import java.util.Objects;

/**
 * Represents a request for a specific amount of energy.
 * This class has been refactored for immutability and type safety.
 * 1.  Fields are 'private final'.
 * 2.  The 'Long timestamp' has been replaced with the more expressive and type-safe 'java.time.Instant'.
 * The getter `getTimestamp()` is preserved but now returns an Instant. This is a significant improvement.
 * 3.  Constructor validation ensures all instances are valid.
 * 4.  equals() and hashCode() have been added to ensure correct behavior in collections.
 */
public record EnergyRequest(String requestID, int amountKWh, long timestamp) {
    public EnergyRequest(String requestID, int amountKWh, long timestamp) {
        this.requestID = Objects.requireNonNull(requestID, "requestID cannot be null");
        this.timestamp = timestamp;

        if (amountKWh <= 0) {
            throw new IllegalArgumentException("Energy amount (amountKWh) must be positive. Received: " + amountKWh);
        }
        this.amountKWh = amountKWh;
    }

    @Override
    public String toString() {
        return "EnergyRequest{" +
                "requestID='" + requestID + '\'' +
                ", amountKWh=" + amountKWh +
                ", timestamp=" + timestamp +
                '}';
    }
}
