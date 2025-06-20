package desm.dps;

import java.util.Objects;

/**
 * Represents the connection information for a power plant.
 * This class has been refactored to be an immutable data object.
 */
public record PowerPlantInfo(int plantId, String address,
                             int port, long registrationTime) {

    public PowerPlantInfo(int plantId, String address, int port,  long registrationTime) {
        // --- Validation: Fail-fast by checking invariants at construction time ---
        this.plantId = plantId;
        this.address = Objects.requireNonNull(address, "address cannot be null");
        this.registrationTime = registrationTime;

//        if (plantId.isBlank()) {
//            throw new IllegalArgumentException("plantId cannot be blank");
//        }
        if (address.isBlank()) {
            throw new IllegalArgumentException("address cannot be blank");
        }
        if (port < 0 || port > 65535) {
            throw new IllegalArgumentException("Port must be between 0 and 65535. Received: " + port);
        }
        this.port = port;
    }

    // --- toString, equals, and hashCode are preserved as they were correctly implemented ---
    @Override
    public String toString() {
        return "Plant ID: " + plantId + ", Address: " + address + ", Port: " + port + ", Registration time: " +registrationTime+ "\n";
    }

}
