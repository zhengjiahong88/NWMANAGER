import java.util.List;
import java.util.Map;

record CoverNetworkSnapshot(
        double connectivity,
        double weakestMemberCoverage,
        List<CanonicalCoverLinkSnapshot> viableLinks,
        List<CanonicalCoverLinkSnapshot> relevantRejectedLinks,
        Map<String, Integer> incomingViableCounts,
        Map<String, Integer> outgoingViableCounts,
        List<String> activeParticipants
) {
    CoverNetworkSnapshot {
        viableLinks = List.copyOf(viableLinks);
        relevantRejectedLinks = List.copyOf(relevantRejectedLinks);
        incomingViableCounts = java.util.Collections.unmodifiableMap(
                new java.util.TreeMap<>(incomingViableCounts));
        outgoingViableCounts = java.util.Collections.unmodifiableMap(
                new java.util.TreeMap<>(outgoingViableCounts));
        activeParticipants = List.copyOf(activeParticipants);
    }
}
