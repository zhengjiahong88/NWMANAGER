final class SupportPlan {
    final Musketeer supporter, fixer, primaryDefender, formerOpponent;
    final EngagementSlot flankSide;
    final Pos supportDestination;
    final double startedAt, expiresAt, availableWindow, timeToSupport, timeToIntercept;
    final SupportReason reason;
    SupportPlanState state = SupportPlanState.PREPARING_TO_LEAVE;
    Engagement engagement;
    double stateStartedAt, localAdvantageAt = Double.NaN;
    String finalReason = "";
    boolean reachedFlankSide, reachedPrimaryDefender, attackedPrimaryDefender;

    SupportPlan(Musketeer supporter, Musketeer fixer, Musketeer primaryDefender,
                Musketeer formerOpponent, SupportOpportunity opportunity, double startedAt,
                double expiresAt) {
        this.supporter = supporter;
        this.fixer = fixer;
        this.primaryDefender = primaryDefender;
        this.formerOpponent = formerOpponent;
        flankSide = opportunity.flankSide().slot();
        supportDestination = opportunity.supportDestination();
        this.startedAt = this.stateStartedAt = startedAt;
        this.expiresAt = expiresAt;
        availableWindow = opportunity.availableWindow();
        timeToSupport = opportunity.timeToSupport();
        timeToIntercept = opportunity.timeForOpponentToIntercept();
        reason = opportunity.reason();
    }

    void changeState(SupportPlanState state, double simulationTime) {
        this.state = state;
        stateStartedAt = simulationTime;
    }
}
