package desm.dps;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class RequestGenerator {

    // Minimum and maximum bounds for the random energy amount (in kWh)
    private static final int MIN_AMOUNT_KWH = 5000;
    private static final int MAX_AMOUNT_KWH = 15000; // inclusive

    /**
     * Generates a new EnergyRequest with:
     * - a UUID-based request ID
     * - a random amount between MIN_AMOUNT_KWH and MAX_AMOUNT_KWH
     * - the current timestamp in ISO-8601 format
     */
    public EnergyRequest generateRequest() {
        // Create a unique identifier for this request
        String requestId = UUID.randomUUID().toString();

        // Pick a random integer between MIN_AMOUNT_KWH and MAX_AMOUNT_KWH (inclusive)
        int amountKWh = ThreadLocalRandom.current()
                .nextInt(MIN_AMOUNT_KWH, MAX_AMOUNT_KWH + 1);

        // Use Instant.now() to get the current time, and Instant.toString() gives ISO-8601 format
        Long timestamp = System.currentTimeMillis();

        // Build the EnergyRequest object
        return new EnergyRequest(requestId, amountKWh, timestamp);
    }
}
