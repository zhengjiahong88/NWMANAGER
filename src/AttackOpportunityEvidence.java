record AttackOpportunityEvidence(
        AttackOpportunityReason reason,
        double targetFacingErrorToAttacker,
        double targetFacingErrorToAlly,
        double targetWeaponErrorToAttacker,
        double targetWeaponErrorToAlly,
        CombatState observedTargetState,
        double observedStateRemaining,
        double targetVelocityIntoAttackLane,
        double opposingCoverEta,
        double attackerRecoveryEta,
        double attackLineClearance,
        double perceptionConfidence,
        double previousDistanceToThreatLane,
        double predictedDistanceToThreatLane,
        double laneEntrySpeed,
        double timeUntilLaneEntry,
        double timeInsideLane,
        boolean movingIntoLineLowEvidence
) {
    static AttackOpportunityEvidence insufficient() {
        return new AttackOpportunityEvidence(null, Double.NaN, Double.NaN, Double.NaN,
                Double.NaN, CombatState.NEUTRAL, 0, 0, Double.POSITIVE_INFINITY,
                Double.POSITIVE_INFINITY, 0, 0, Double.NaN, Double.NaN, 0,
                Double.POSITIVE_INFINITY, 0, true);
    }
}
