final class Engagement {
    final EngagementType type;
    final Musketeer primaryTarget, fixer, flanker;
    final Musketeer duelistOne, duelistTwo;
    final EngagementSlot fixerSlot, flankerSlot;
    final double createdAt;
    final GroupEngagement group;
    final Group3v3Engagement group3;
    final TwoVsOneCoordination coordination;
    Musketeer interceptor;
    EngagementStatus status;
    double contestedTime;
    boolean fixerBehindDefender;

    Engagement(EngagementType type, Musketeer primaryTarget, Musketeer fixer, Musketeer flanker,
               EngagementSlot fixerSlot, EngagementSlot flankerSlot, double createdAt) {
        this.type = type;
        this.primaryTarget = primaryTarget;
        this.fixer = fixer;
        this.flanker = flanker;
        duelistOne = duelistTwo = null;
        this.fixerSlot = fixerSlot;
        this.flankerSlot = flankerSlot;
        this.createdAt = createdAt;
        group = null;
        group3 = null;
        coordination = new TwoVsOneCoordination(primaryTarget, createdAt);
        status = EngagementStatus.FORMING;
    }

    Engagement(Musketeer duelistOne, Musketeer duelistTwo, double createdAt) {
        type = EngagementType.DUEL;
        primaryTarget = fixer = flanker = null;
        fixerSlot = flankerSlot = null;
        this.duelistOne = duelistOne;
        this.duelistTwo = duelistTwo;
        this.createdAt = createdAt;
        group = null;
        group3 = null;
        coordination = null;
        status = EngagementStatus.FORMING;
    }

    Engagement(GroupEngagement group, double createdAt) {
        type = EngagementType.GROUP_2V2;
        primaryTarget = fixer = flanker = duelistOne = duelistTwo = null;
        fixerSlot = flankerSlot = null;
        this.group = group;
        group3 = null;
        coordination = null;
        this.createdAt = createdAt;
        status = EngagementStatus.FORMING;
    }

    Engagement(Group3v3Engagement group3, double createdAt) {
        type = EngagementType.GROUP_3V3;
        primaryTarget = fixer = flanker = duelistOne = duelistTwo = null;
        fixerSlot = flankerSlot = null;
        group = null;
        this.group3 = group3;
        coordination = null;
        this.createdAt = createdAt;
        status = EngagementStatus.FORMING;
    }

    void activate() {
        if (status == EngagementStatus.FORMING) status = EngagementStatus.ACTIVE;
    }

    void assignInterceptor(Musketeer interceptor) {
        this.interceptor = interceptor;
        interceptor.assignTacticalIntent(this, TacticalIntent.INTERCEPT, null, flanker,
                "controlled_interceptor_assignment");
        interceptor.interceptionPhase = InterceptionPhase.APPROACH_INTERCEPT_POINT;
        interceptor.interceptionPhaseTime = 0;
        interceptor.interceptionReevaluationTime = 0;
        interceptor.committedInterceptionPoint = null;
    }

    Pos fixerDestination() {
        return fixerSlot.destinationAround(primaryTarget, 1.28);
    }

    Pos flankerDestination() {
        return flankerSlot.destinationAround(primaryTarget, 1.34);
    }
}
