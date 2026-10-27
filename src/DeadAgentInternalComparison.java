import java.util.List;

record DeadAgentInternalComparison(
        boolean identical,
        FirstDifferentialContamination firstContamination,
        List<DifferentialStageComparison> stageComparisons
) {
    DeadAgentInternalComparison {
        stageComparisons = List.copyOf(stageComparisons);
    }
}
