import java.util.List;

record DifferentialStageSnapshot(
        long tick,
        double simulationTime,
        long sequence,
        DifferentialTickStage stage,
        String checkpointAgentId,
        long randomDrawCount,
        long randomStateHash,
        Group3CollectiveSnapshot collective,
        List<DifferentialAgentSnapshot> survivors
) {
    DifferentialStageSnapshot {
        survivors = List.copyOf(survivors);
    }
}
