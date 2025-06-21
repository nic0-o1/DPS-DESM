package desm.dps;

import java.util.Objects;

/**
 * Represents the connection information for a power plant.
 */
public record PowerPlantInfo(int plantId, String address,
                             int port, long registrationTime) {

    public PowerPlantInfo(int plantId, String address, int port,  long registrationTime) {
        this.plantId = plantId;
        this.address = Objects.requireNonNull(address, "address cannot be null");
        this.registrationTime = registrationTime;

        if (address.isBlank()) {
            throw new IllegalArgumentException("address cannot be blank");
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
