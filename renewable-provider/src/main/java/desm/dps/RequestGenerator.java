package desm.dps;

import java.util.Random;
import java.util.UUID;

public class RequestGenerator {
    private static final int MIN_AMOUNT_KWH = 5000;
    private static final int MAX_AMOUNT_KWH = 15000; // Inclusive

    private final Random random = new Random();

    public EnergyRequest generateRequest() {
        String requestId = UUID.randomUUID().toString(); // Unique ID for the request
        int amountKWh = random.nextInt(MAX_AMOUNT_KWH - MIN_AMOUNT_KWH + 1) + MIN_AMOUNT_KWH;
        long timestamp = System.currentTimeMillis(); // Current timestamp

        return new EnergyRequest(requestId, amountKWh, timestamp);
    }
}
