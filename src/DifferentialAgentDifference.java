import java.util.List;

record DifferentialAgentDifference(String agentId, List<DifferentialFieldDifference> differences) {
    DifferentialAgentDifference {
        differences = List.copyOf(differences);
    }
}
