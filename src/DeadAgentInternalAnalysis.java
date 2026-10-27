record DeadAgentInternalAnalysis(
        long seed,
        long firstDifferentTick,
        DeadAgentInternalComparison causalWindow,
        long firstPropagatingTick,
        DeadAgentInternalComparison propagatingWindow,
        FirstDifferentialContamination firstPropagatingContamination,
        DifferentialPropagationTimeline propagationTimeline,
        DeadAgentInternalComparison firstSurvivorWindow,
        DeadAgentDifferentialComparison endOfTickComparison,
        DeadAgentDifferentialResult current,
        DeadAgentDifferentialResult neutralized
) {
}
