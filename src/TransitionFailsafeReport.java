import java.util.List;

record TransitionFailsafeReport(SimulationScenario scenario, ActiveCombatController controller,
                                RosterSnapshot roster, RosterSnapshot processedRoster,
                                RosterSnapshot pendingTransition, TransitionTrace lastTransition,
                                DeadReferenceAudit deadReferenceAudit,
                                List<String> currentEngagements, List<String> simplifiedStack) {
    TransitionFailsafeReport {
        currentEngagements = List.copyOf(currentEngagements);
        simplifiedStack = List.copyOf(simplifiedStack);
    }
}
