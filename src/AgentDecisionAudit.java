import java.util.List;

record AgentDecisionAudit(long tick, double simulationTime, String agentId,
                          String activeController, String combatState,
                          DecisionPointType decisionPoint,
                          String currentTargetId, String primaryThreatId, String secondaryThreatId,
                          List<ActionCandidateTrace> candidates,
                          AgentActionType selectedAction, String selectedTargetId,
                          double bestEligibleScore, double selectedScore, double selectionRegret,
                          DecisionErrorBreakdown errors,
                          String currentTacticalRole, String currentPlan,
                          String existingDecisionReason, String existingDecisionTraceId) {
    AgentDecisionAudit {
        if (agentId == null || agentId.isBlank()) throw new IllegalArgumentException("Agent id is required.");
        if (decisionPoint == null) throw new IllegalArgumentException("Decision point is required.");
        candidates = List.copyOf(candidates);
        if (errors == null) errors = DecisionErrorBreakdown.unrecorded();
    }
}
