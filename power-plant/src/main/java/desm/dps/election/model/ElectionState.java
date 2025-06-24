package desm.dps.election.model;

import desm.dps.EnergyRequest;
import desm.dps.grpc.Bid;

/**
 * A thread-safe container for the state of a single ongoing election.
 * It encapsulates the original request, this plant's bid, the best bid seen so far,
 * and flags to manage the election lifecycle.
 */
public final class ElectionState {
    private final EnergyRequest request;
    private final double myBid;
    private final Object stateLock = new Object();

    private volatile Bid bestBidSeen;
    private volatile boolean winnerAnnounced = false;
    private volatile boolean hasInitiated = false;

    public ElectionState(EnergyRequest request, double myBid) {
        this.request = request;
        this.myBid = myBid;
        // Initialize with a placeholder bid that any valid bid can beat.
        // A plant ID of 0 is considered invalid/placeholder.
        this.bestBidSeen = Bid.newBuilder().setPlantId(0).setPrice(Double.MAX_VALUE).build();
    }

    public boolean updateBestBid(Bid newBid) {
        synchronized (stateLock) {
            if (BidComparator.isBetter(newBid, this.bestBidSeen)) {
                this.bestBidSeen = newBid;
                return true;
            }
            return false;
        }
    }

    public Bid getBestBid() {
        synchronized (stateLock) {
            return this.bestBidSeen;
        }
    }

    public boolean isWinnerAnnounced() {
        return this.winnerAnnounced;
    }

    public boolean trySetWinnerAnnounced() {
        synchronized (stateLock) {
            if (!this.winnerAnnounced) {
                this.winnerAnnounced = true;
                return true;
            }
            return false;
        }
    }

    public boolean trySetInitiated() {
        synchronized (stateLock) {
            if (!this.hasInitiated) {
                this.hasInitiated = true;
                return true;
            }
            return false;
        }
    }

    public EnergyRequest getRequest() {
        return request;
    }

    public double getMyBid() {
        return myBid;
    }

    public boolean isValidBid() {
        return myBid >= 0;
    }
}
