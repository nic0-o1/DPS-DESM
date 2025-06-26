package desm.dps;

import java.util.Objects;

/**
 * An immutable record representing a request for a specific amount of energy.
 * It is uniquely identified by a request ID and includes a timestamp of its creation.
 *
 * @param requestID The unique identifier for this energy request. Cannot be null.
 * @param amountKWh The amount of energy requested in kilowatt-hours. Must be a positive value.
 * @param timestamp The time the request was created, typically as a Unix timestamp.
 */
public record EnergyRequest(String requestID, int amountKWh, long timestamp) {

    /**
     * Constructs a new EnergyRequest.
     *
     * @throws NullPointerException if {@code requestID} is null.
     * @throws IllegalArgumentException if {@code amountKWh} is not positive.
     */
    public EnergyRequest(String requestID, int amountKWh, long timestamp) {
        this.requestID = Objects.requireNonNull(requestID, "Request ID cannot be null.");
        this.timestamp = timestamp;

        if (amountKWh <= 0) {
            throw new IllegalArgumentException("Energy amount (amountKWh) must be positive. Received: " + amountKWh);
        }
        this.amountKWh = amountKWh;
    }

    @Override
    public String toString() {
        return "EnergyRequest[requestID='" + requestID + "', amountKWh=" + amountKWh + ", timestamp=" + timestamp + "]";
    }
}