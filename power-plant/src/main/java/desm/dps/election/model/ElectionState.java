package desm.dps.election.model;

import desm.dps.EnergyRequest;
import desm.dps.grpc.Bid;

/**
 * Holds the complete state for a single election instance for a specific energy request.
 * This class is designed to be thread-safe. All mutations of its internal state
 * are synchronized on a single lock object.
 */
public class ElectionState {

    public enum ParticipationStatus {
        /** The plant is aware of the election but has not yet initiated or forwarded a token. */
        PASSIVE,
        /** The plant has actively participated by initiating or forwarding a token. */
        PARTICIPANT
    }

    private final EnergyRequest request;
    private final Object stateLock = new Object();

    // --- State fields guarded by stateLock ---
    private double myBid;
    private Bid bestBid;
    private ParticipationStatus participationStatus = ParticipationStatus.PASSIVE;
    private boolean winnerHasBeenAnnounced = false;
    // --- End of guarded state fields ---

    public ElectionState(EnergyRequest request, double price) {
        this.request = request;
        this.myBid = price;
        this.bestBid = null; // Initialized with no best bid.
    }

    /**
     * Returns the lock object that protects the internal state of this instance.
     * This can be used for composing atomic operations from outside the class.
     *
     * @return The internal lock object.
     */
    public Object getStateLock() {
        return this.stateLock;
    }

    public EnergyRequest getRequest() {
        return request;
    }

    public double getMyBid() {
        synchronized (stateLock) {
            return myBid;
        }
    }

    public Bid getBestBid() {
        synchronized (stateLock) {
            return bestBid;
        }
    }

    /**
     * Checks if the plant's current bid is valid (non-negative).
     *
     * @return {@code true} if the bid is valid.
     */
    public boolean isValidBid() {
        // myBid is only mutated under a lock, but reading a double can be non-atomic on 32-bit systems.
        // For safety and consistency, we lock here as well.
        synchronized (stateLock) {
            return myBid >= 0;
        }
    }

    public boolean isParticipant() {
        synchronized (stateLock) {
            return this.participationStatus == ParticipationStatus.PARTICIPANT;
        }
    }

    public void updateMyBid(double newPrice) {
        synchronized (stateLock) {
            this.myBid = newPrice;
        }
    }

    /**
     * Marks this plant as an active participant in the election.
     * This is a one-way transition from PASSIVE to PARTICIPANT.
     */
    public void becomeParticipant() {
        synchronized (stateLock) {
            if (this.participationStatus == ParticipationStatus.PASSIVE) {
                this.participationStatus = ParticipationStatus.PARTICIPANT;
            }
        }
    }

    /**
     * Compares a new candidate bid with the current best bid and updates the best bid if the
     * candidate is better. The comparison is done using {@link BidComparator}.
     *
     * @param newBid The candidate bid to evaluate.
     * @return {@code true} if the best bid was updated, {@code false} otherwise.
     */
    public boolean updateBestBid(Bid newBid) {
        synchronized (stateLock) {
            if (BidComparator.isBetter(newBid, this.bestBid)) {
                this.bestBid = newBid;
                return true;
            }
            return false;
        }
    }

    public boolean isWinnerAnnounced() {
        synchronized (stateLock) {
            return this.winnerHasBeenAnnounced;
        }
    }

    /**
     * Atomically sets the winner announcement flag to true.
     * This operation is idempotent and ensures that the winner is announced only once.
     *
     * @return {@code true} if the flag was successfully set from false to true,
     *         {@code false} if the flag was already true.
     */
    public boolean trySetWinnerAnnounced() {
        synchronized (stateLock) {
            if (!winnerHasBeenAnnounced) {
                winnerHasBeenAnnounced = true;
                return true;
            }
            return false;
        }
    }
}