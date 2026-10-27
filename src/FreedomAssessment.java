record FreedomAssessment(
        boolean free,
        double confidence,
        double timeToSupport,
        double timeForOpponentToContest,
        String reason
) {
    static FreedomAssessment engaged(String reason) {
        return new FreedomAssessment(false, 0, Double.POSITIVE_INFINITY,
                Double.POSITIVE_INFINITY, reason);
    }
}
