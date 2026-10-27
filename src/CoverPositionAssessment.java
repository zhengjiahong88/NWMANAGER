record CoverPositionAssessment(
        boolean physicallyCovering,
        double revengeEta,
        double expectedVulnerabilityWindow,
        double etaMargin,
        double lineSafety,
        Pos preferredCoverPosition,
        String rejectionReason
) {
    static CoverPositionAssessment unavailable(Pos preferredPosition, String reason) {
        return new CoverPositionAssessment(false, Double.POSITIVE_INFINITY, 0,
                Double.NEGATIVE_INFINITY, 0, preferredPosition, reason);
    }
}
