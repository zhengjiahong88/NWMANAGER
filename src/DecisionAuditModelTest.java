import java.util.List;

final class DecisionAuditModelTest {
    public static void main(String[] args) {
        new DecisionAuditModelTest().verifyModelAndMetrics();
    }

    private void verifyModelAndMetrics() {
        var best = new ActionCandidateTrace(AgentActionType.BLOCK_HIGH, true, true,
                "R1", null, 0.80, 0.75, 0.75, 1, 0.65,
                List.of(new DecisionModifierTrace("reaction", 0.08, "AgentStats.reaction")),
                List.of(), false);
        var selected = new ActionCandidateTrace(AgentActionType.RETREAT, true, true,
                "R1", null, 0.68, 0.61, 0.61, 0.55, 0.35,
                List.of(new DecisionModifierTrace("decision", -0.07, "tactical noise")),
                List.of(), true);
        var rejected = new ActionCandidateTrace(AgentActionType.ATTACK_HIGH, true, false,
                "R1", null, 0.70, 0.64, Double.NaN, 0, 0, List.of(),
                List.of("BLOCK_STUN"), false);
        var audit = new AgentDecisionAudit(12, 0.10, "B1", "DUEL", "BLOCK_STUN",
                DecisionPointType.DEFENSIVE_RESPONSE, "R1", "R1", null,
                List.of(best, selected, rejected), AgentActionType.RETREAT, "R1",
                0.75, 0.61, 0.14,
                new DecisionErrorBreakdown(0.03, Double.NaN, Double.NaN, 0.08,
                        -0.07, Double.NaN, 0.14, Double.NaN, Double.NaN, 0.02),
                "SURVIVE", "DUEL", "defensive_priority", "trace-12-B1");
        var disabled = new DecisionAuditRecorder(false);
        disabled.record(audit);
        require(disabled.audits().isEmpty() && disabled.metrics().decisions == 0,
                "Disabled recorder must be inert.");
        var enabled = new DecisionAuditRecorder(true);
        enabled.record(audit);
        var metrics = enabled.metrics();
        require(enabled.audits().size() == 1, "Audit was not recorded.");
        require(metrics.decisions == 1 && metrics.decisionsWithMultipleEligibleCandidates == 1,
                "Decision counters are invalid.");
        require(metrics.secondCandidateSelected == 1 && metrics.nonMaximumSelections == 1,
                "Selection rank was not measured.");
        require(metrics.lightMistakes == 1 && metrics.mediumMistakes == 1 && metrics.severeMistakes == 0,
                "Regret thresholds are invalid.");
        require(new DecisionPointCatalog().entries().size() == DecisionPointType.values().length,
                "Every real decision point must be catalogued once.");
        require(DecisionErrorBreakdown.report(Double.NaN).equals("NONE_RECORDED"),
                "Missing values must not be reported as zero.");
        System.out.println("DecisionAuditModelTest passed.");
    }

    private void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
