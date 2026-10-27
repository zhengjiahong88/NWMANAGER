import java.util.List;

record DecisionPointDescriptor(DecisionPointType type, String productionEntryPoint,
                               String actualStructure, List<String> currentSubBranches) {
    DecisionPointDescriptor {
        currentSubBranches = List.copyOf(currentSubBranches);
    }
}
