package desm.dps;

import java.util.Objects;

/**
 * An immutable record holding the essential connection and identification information for a power plant.
 *
 * @param plantId The unique numerical identifier for the power plant.
 * @param address The network address (hostname or IP) of the power plant.
 * @param port The network port for gRPC communication.
 * @param registrationTime The timestamp when the plant was first registered or initialized.
 */
public record PowerPlantInfo(int plantId, String address, int port, long registrationTime) {

    /**
     * Constructs new PowerPlantInfo.
     *
     * @throws NullPointerException if {@code address} is null.
     * @throws IllegalArgumentException if {@code address} is blank or if {@code port} is outside the valid range of 0-65535.
     */
    public PowerPlantInfo(int plantId, String address, int port, long registrationTime) {
        this.plantId = plantId;
        this.address = Objects.requireNonNull(address, "Address cannot be null.");
        this.registrationTime = registrationTime;

        if (address.isBlank()) {
            throw new IllegalArgumentException("Address cannot be blank.");
        }
        if (port < 0 || port > 65535) {
            throw new IllegalArgumentException("Port must be between 0 and 65535. Received: " + port);
        }
        this.port = port;
    }

    @Override
    public String toString() {
        return "Plant ID: " + plantId + ", Address: " + address + ", Port: " + port + ", Registration time: " +registrationTime+ "\n";
    }
}