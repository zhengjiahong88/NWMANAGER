record SupportOpportunity(
        boolean viable,
        double score,
        double availableWindow,
        double timeToSupport,
        double timeForOpponentToRecover,
        double timeForOpponentToIntercept,
        double leaveRisk,
        double supportValue,
        SupportReason reason,
        String rejectionReason,
        FlankSideAssessment flankSide,
        Pos supportDestination,
        Pos interceptionPoint
) {
    static SupportOpportunity unavailable(String rejectionReason) {
        return new SupportOpportunity(false, 0, 0, Double.POSITIVE_INFINITY,
                Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, 1, 0,
                null, rejectionReason, null, null, null);
    }
}
