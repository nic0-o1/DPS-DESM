package desm.dps.rest;

/**
 * A checked exception thrown when a power plant attempts to register with an ID
 * that already exists on the administrative server.
 */
public class RegistrationConflictException extends Exception {
    public RegistrationConflictException(String message) {
        super(message);
    }
}