import java.util.List;

record DifferentialStageComparison(
        long tick,
        double simulationTime,
        long sequence,
        DifferentialTickStage stage,
        boolean identical,
        List<DifferentialFieldDifference> collectiveDifferences,
        List<DifferentialAgentDifference> agentDifferences
) {
    DifferentialStageComparison {
        collectiveDifferences = List.copyOf(collectiveDifferences);
        agentDifferences = List.copyOf(agentDifferences);
    }
}
