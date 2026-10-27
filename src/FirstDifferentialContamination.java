import java.util.List;

record FirstDifferentialContamination(
        long tick,
        double simulationTime,
        DifferentialTickStage stage,
        String primaryCategory,
        String primaryField,
        List<String> affectedAgents,
        String leftValue,
        String rightValue,
        List<DifferentialFieldDifference> simultaneousDifferences
) {
    FirstDifferentialContamination {
        affectedAgents = List.copyOf(affectedAgents);
        simultaneousDifferences = List.copyOf(simultaneousDifferences);
    }
}
