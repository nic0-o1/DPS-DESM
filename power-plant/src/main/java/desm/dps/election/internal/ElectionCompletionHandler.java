package desm.dps.election.internal;

import desm.dps.grpc.EnergyWinnerAnnouncement;

/**
 * A functional interface for a handler that processes a winner announcement.
 * This is typically used to apply the election result locally when an election concludes
 * or when this plant is the sole participant.
 */
@FunctionalInterface
public interface ElectionCompletionHandler {
    /**
     * Processes the final election result.
     *
     * @param announcement The details of the winning plant and bid.
     */
    void process(EnergyWinnerAnnouncement announcement);
}