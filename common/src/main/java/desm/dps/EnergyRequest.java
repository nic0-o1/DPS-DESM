package desm.dps;

import java.util.Objects;

/**
 * Represents a request for a specific amount of energy.
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
