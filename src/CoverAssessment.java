record CoverAssessment(
        boolean covered,
        Musketeer protectedAlly,
        Musketeer coveringAgent,
        Musketeer punishableEnemy,
        double punishEta,
        double enemyRecoveryWindow,
        double counterThreatEta,
        double lineSafety,
        double score,
        String reason
) {
    static CoverAssessment unavailable(Musketeer ally, Musketeer coverer, String reason) {
        return new CoverAssessment(false, ally, coverer, null, Double.POSITIVE_INFINITY,
                0, Double.POSITIVE_INFINITY, 0, 0, reason);
    }
}
