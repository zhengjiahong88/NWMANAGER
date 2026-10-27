record AttackOpportunity(boolean viable, Musketeer attacker, Musketeer target,
                         Musketeer ally, Musketeer opposingCoverer,
                         AttackOpportunityReason primaryReason,
                         double availableWindow, double impactEta,
                         double targetExposure, double lineSafety,
                         double allyCoverQuality, double opposingCoverQuality,
                         double revengeRisk, double counterAttackRisk,
                         double score, AttackOpportunityRejection rejectionReason,
                         AttackOpportunityEvidence evidence) {
    static AttackOpportunity unavailable(Musketeer attacker, Musketeer target,
                                         AttackOpportunityRejection rejection) {
        return new AttackOpportunity(false, attacker, target, null, null, null,
                0, Double.POSITIVE_INFINITY, 0, 0, 0, 0, 1, 1, 0, rejection,
                AttackOpportunityEvidence.insufficient());
    }
}
