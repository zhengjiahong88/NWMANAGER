import java.util.List;

record SurvivorTickSnapshot(
        long tick,
        double simulationTime,
        List<AgentTickSnapshot> agents,
        String activeController,
        String groupPhase
) {
    SurvivorTickSnapshot {
        agents = List.copyOf(agents);
    }
}
