record CanonicalCoverLinkSnapshot(
        String protectorId,
        String protectedId,
        String enemyId,
        boolean viable,
        double alignEta,
        double prepareEta,
        double contactEta,
        double punishEta,
        double lineSafety,
        double perceptionConfidence,
        double quality,
        String rejectionReason
) implements Comparable<CanonicalCoverLinkSnapshot> {
    @Override
    public int compareTo(CanonicalCoverLinkSnapshot other) {
        var comparison = protectorId.compareTo(other.protectorId);
        if (comparison != 0) return comparison;
        comparison = protectedId.compareTo(other.protectedId);
        return comparison != 0 ? comparison : enemyId.compareTo(other.enemyId);
    }
}
