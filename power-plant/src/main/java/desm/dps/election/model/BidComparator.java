package desm.dps.election.model;

import desm.dps.grpc.Bid;

/**
 * A stateless utility class for comparing two bids.
 * A bid is "better" if its price is lower. In case of a tie, the bid from the
 * plant with the lexicographically bigger ID wins.
 */
public final class BidComparator {
    private BidComparator() {}

    public static boolean isBetter(Bid candidate, Bid current) {
        if (candidate == null) return false;
        // Any valid bid is better than an empty or null one.
        if (current == null || current.getPlantId().isEmpty()) return true;

        // Lower price wins.
        if (candidate.getPrice() < current.getPrice()) return true;

        // If prices are equal, the bigger Plant ID wins as a tie-breaker.
        return candidate.getPrice() == current.getPrice() &&
                candidate.getPlantId().compareTo(current.getPlantId()) > 0;
    }
}
