final class TwoVsOneCoordination {
    final Musketeer defender;
    Musketeer primaryAttacker, secondaryAttacker;
    TwoVsOneCoordinationPhase phase = TwoVsOneCoordinationPhase.BUILD_GEOMETRY;
    FlankReadiness flankReadiness = FlankReadiness.FLANK_NOT_READY;
    double phaseStartedAt, releaseWindowStart, releaseWindowEnd, lastAttackAt,
            lastProgressAt;
    boolean primaryThreatCredible, defenderReacting, secondaryLineSafe;
    boolean secondaryReleased;
    SecondaryReleaseAssessment secondaryAssessment;
    int abortedPlans;
    String lastRejection = "";
    double lastRejectionAt;

    TwoVsOneCoordination(Musketeer defender, double simulationTime) {
        this.defender = defender;
        phaseStartedAt = lastAttackAt = lastProgressAt = simulationTime;
    }
}
