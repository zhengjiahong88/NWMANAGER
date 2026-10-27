import java.util.List;

record ActionCandidateTrace(AgentActionType action, boolean generated, boolean eligible,
                            String targetId, String supportedAllyId,
                            double objectiveScore, double perceivedScore, double finalScore,
                            double selectionWeight, double selectionProbability,
                            List<DecisionModifierTrace> modifiers,
                            List<String> rejectionReasons, boolean selected) {
    ActionCandidateTrace {
        if (action == null) throw new IllegalArgumentException("Candidate action is required.");
        modifiers = List.copyOf(modifiers);
        rejectionReasons = List.copyOf(rejectionReasons);
        if (!generated && (eligible || selected))
            throw new IllegalArgumentException("A non-generated candidate cannot be eligible or selected.");
        if (selected && !eligible)
            throw new IllegalArgumentException("A selected candidate must be eligible.");
    }
}
