package desm.dps.election.model;

import desm.dps.grpc.Bid;

/**
 * A stateless utility class for comparing two {@link Bid} objects.
 * The comparison criteria are:
 * 1. A lower price is better.
 * 2. If prices are equal, the bid from the plant with the higher ID wins.
 */
public final class BidComparator {

    /** Private constructor to prevent instantiation of this utility class. */
    private BidComparator() {}

    /**
     * Determines if a candidate bid is better than the current best bid.
     *
     * @param candidate The new bid to evaluate. Can be null.
     * @param current   The existing best bid. Can be null or represent an empty bid.
     * @return {@code true} if the candidate bid is better than the current one.
     */
    public static boolean isBetter(Bid candidate, Bid current) {
        if (candidate == null) {
            return false;
        }
        // Any valid bid is better than a non-existent one.
        if (current == null || current.getPlantId() == 0) {
            return true;
        }

        // A lower price is always better.
        if (candidate.getPrice() < current.getPrice()) {
            return true;
        }

        // If prices are equal, use the larger Plant ID as a tie-breaker.
        return candidate.getPrice() == current.getPrice() &&
                candidate.getPlantId() > current.getPlantId();
    }
}