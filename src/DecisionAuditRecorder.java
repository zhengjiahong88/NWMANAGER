import java.util.ArrayDeque;
import java.util.List;

final class DecisionAuditRecorder {
    private static final int MAX_AUDITS = 16_384;
    private final boolean enabled;
    private final ArrayDeque<AgentDecisionAudit> audits = new ArrayDeque<>();
    private final DecisionSelectionMetrics metrics = new DecisionSelectionMetrics();
    private long droppedAudits;

    DecisionAuditRecorder(boolean enabled) {
        this.enabled = enabled;
    }

    boolean enabled() {
        return enabled;
    }

    void record(AgentDecisionAudit audit) {
        if (!enabled) return;
        if (audits.size() == MAX_AUDITS) {
            audits.removeFirst();
            ++droppedAudits;
        }
        audits.addLast(audit);
        metrics.record(audit);
    }

    List<AgentDecisionAudit> audits() {
        return List.copyOf(audits);
    }

    DecisionSelectionMetrics metrics() {
        return metrics;
    }

    long droppedAudits() {
        return droppedAudits;
    }
}
