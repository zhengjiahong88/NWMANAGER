import java.util.List;

record DeadAgentDifferentialComparison(
        String leftRun,
        String rightRun,
        boolean identical,
        Long firstPositionDivergenceTick,
        Long firstVelocityDivergenceTick,
        Long firstBodyAngleDivergenceTick,
        Long firstWeaponAngleDivergenceTick,
        Long firstStateDivergenceTick,
        Long firstTargetDivergenceTick,
        Long firstAttentionDivergenceTick,
        Long firstFormationDivergenceTick,
        Long firstCoverDivergenceTick,
        Long firstAttackOpportunityDivergenceTick,
        Long firstCombatEventDivergenceTick,
        Long firstTransitionDivergenceTick,
        List<String> affectedAgents,
        boolean winnerChanged,
        boolean matchCompletionChanged,
        double matchEndTimeDifference,
        List<String> details
) {
    DeadAgentDifferentialComparison {
        affectedAgents = List.copyOf(affectedAgents);
        details = List.copyOf(details);
    }
}
