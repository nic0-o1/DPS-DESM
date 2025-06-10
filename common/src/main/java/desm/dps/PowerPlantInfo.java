package desm.dps;

import java.util.Objects;

/**
 * Represents the connection information for a power plant.
 * This class has been refactored to be an immutable data object.
 * 1.  All fields are 'private final' to ensure they cannot be changed after construction.
 * 2.  Setters have been removed.
 * 3.  Validation is performed in the constructor to ensure an object can never be in an invalid state ("fail-fast").
 * The method names `getPlantId()`, `getAddress()`, etc., are preserved for backward compatibility.
 */
public record PowerPlantInfo(int plantId, String address,
                             int port) { // 'final' prevents subclassing, which can break immutability
    // The no-arg constructor has been removed as it would allow for an object with an invalid null state.
    // All necessary data is now required upon creation.

    public PowerPlantInfo(int plantId, String address, int port) {
        // --- Validation: Fail-fast by checking invariants at construction time ---
        this.plantId = plantId;
        this.address = Objects.requireNonNull(address, "address cannot be null");

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
        return "Plant ID: " + plantId + ", Address: " + address + ", Port: " + port + "\n";
    }

}
