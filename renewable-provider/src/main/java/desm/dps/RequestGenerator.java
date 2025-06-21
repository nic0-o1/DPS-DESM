package desm.dps;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Utility class for generating energy requests with randomized amounts.
 * This class is designed to be thread-safe and stateless.
 */
public class RequestGenerator {

    // Minimum and maximum bounds for the random energy amount
    private static final int MIN_AMOUNT_KWH = 5000;
    private static final int MAX_AMOUNT_KWH = 15000;

    /**
     * Generates a new EnergyRequest with randomized parameters.
     * This method creates an energy request with:
     * - A unique request ID using UUID for guaranteed uniqueness across distributed systems
     * - A random energy amount within the defined commercial range (5000-15000 kWh)
     * - The current system timestamp for request tracking and ordering
     *
     * @return A new EnergyRequest instance with randomized amount and unique ID
     */
    public EnergyRequest generateRequest() {

        final String requestId = UUID.randomUUID().toString();

        final int amountKWh = generateRandomAmount();

        final long timestamp = System.currentTimeMillis();

        return new EnergyRequest(requestId, amountKWh, timestamp);
    }

    /**
     * Generates a random energy amount within the defined bounds.
     *
     * @return Random integer between MIN_AMOUNT_KWH (inclusive) and MAX_AMOUNT_KWH (inclusive)
     * @throws IllegalStateException if random generation fails
     */
    private int generateRandomAmount() {
        try {
            return ThreadLocalRandom.current().nextInt(MIN_AMOUNT_KWH, MAX_AMOUNT_KWH + 1);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("Failed to generate random energy amount", e);
        }
    }
}
