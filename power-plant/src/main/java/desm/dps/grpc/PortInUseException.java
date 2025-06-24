package desm.dps.grpc;

/**
 * Custom exception thrown when a power plant tries to start its gRPC server
 * on a port that is already in use.
 */
public class PortInUseException extends Exception {
    public PortInUseException(String message, Throwable cause) {
        super(message, cause);
    }
}
