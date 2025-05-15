package desm.dps;

public class RenewableProvider {
    private static final long PUBLISH_INTERVAL_MS = 10 * 1000; // 10 seconds in milliseconds

    private final RequestGenerator requestGenerator;
    private final EnergyRequestPublisher requestPublisher;
    volatile boolean running = false; // Use volatile for visibility
    private Thread providerThread;

    public RenewableProvider(){
        this.requestGenerator = new RequestGenerator();
        this.requestPublisher = new EnergyRequestPublisher();
    }
    public void start(){
        if(running){
            System.out.println("Renewable Provider is already running.");
            return;
        }
        running = true;
        System.out.println("Renewable Provider starting. Publishing requests every " + (PUBLISH_INTERVAL_MS / 1000) + " seconds.");

        providerThread = new Thread(() -> {
            while (running) {
                try {
                    EnergyRequest request = requestGenerator.generateRequest();
                    requestPublisher.publishRequest(request);

                    // Wait for the specified interval
                    Thread.sleep(PUBLISH_INTERVAL_MS);

                } catch (InterruptedException e) {
                    System.out.println("Renewable Provider thread interrupted.");
                    // Restore the interrupt flag
                    Thread.currentThread().interrupt();
                    running = false; // Stop the loop on interruption
                } catch (Exception e) {
                    System.err.println("Error in Renewable Provider thread: " + e.getMessage());
                    e.printStackTrace();
                    // Depending on requirements, you might want to continue or stop on other errors
                }
            }
            System.out.println("Renewable Provider thread stopped.");
            requestPublisher.disconnect(); // Disconnect MQTT client when the thread stops
        });
        providerThread.start();
    }
    public void stop() {
        if (!running) {
            System.out.println("Renewable Provider is not running.");
            return;
        }
        System.out.println("Stopping Renewable Provider...");
        running = false; // Signal the thread to stop

        // Interrupt the thread to wake it up from sleep if it's waiting
        if (providerThread != null) {
            providerThread.interrupt();
        }

        // Optionally, wait for the thread to finish (join)
        try {
            if (providerThread != null) {
                providerThread.join();
            }
        } catch (InterruptedException e) {
            System.err.println("Interrupted while waiting for provider thread to stop.");
            Thread.currentThread().interrupt();
        }
        System.out.println("Renewable Provider stopped.");
    }
}

