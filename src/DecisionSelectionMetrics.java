import java.util.EnumMap;
import java.util.Map;

final class DecisionSelectionMetrics {
    static final double LIGHT_MISTAKE = 0.02, MEDIUM_MISTAKE = 0.10, SEVERE_MISTAKE = 0.25;

    long decisions, decisionsWithMultipleEligibleCandidates;
    long bestCandidateSelected, secondCandidateSelected, lowerCandidateSelected;
    long nonMaximumSelections, lightMistakes, mediumMistakes, severeMistakes;
    double cumulativeSelectionRegret, maximumSelectionRegret;
    final Map<DecisionPointType, Long> decisionsByType = new EnumMap<>(DecisionPointType.class);
    final Map<AgentActionType, Long> selectedActions = new EnumMap<>(AgentActionType.class);

    void record(AgentDecisionAudit audit) {
        ++decisions;
        decisionsByType.merge(audit.decisionPoint(), 1L, Long::sum);
        if (audit.selectedAction() != null) selectedActions.merge(audit.selectedAction(), 1L, Long::sum);
        var eligible = audit.candidates().stream().filter(ActionCandidateTrace::eligible)
                .sorted((left, right) -> Double.compare(right.finalScore(), left.finalScore())).toList();
        if (eligible.size() > 1) ++decisionsWithMultipleEligibleCandidates;
        var selectedRank = -1;
        for (var n = 0; n < eligible.size(); ++n)
            if (eligible.get(n).selected()) {
                selectedRank = n;
                break;
            }
        if (selectedRank == 0) ++bestCandidateSelected;
        else if (selectedRank == 1) ++secondCandidateSelected;
        else if (selectedRank > 1) ++lowerCandidateSelected;
        var regret = audit.selectionRegret();
        if (Double.isNaN(regret)) return;
        cumulativeSelectionRegret += regret;
        maximumSelectionRegret = Math.max(maximumSelectionRegret, regret);
        if (regret > 0) ++nonMaximumSelections;
        if (regret > LIGHT_MISTAKE) ++lightMistakes;
        if (regret > MEDIUM_MISTAKE) ++mediumMistakes;
        if (regret > SEVERE_MISTAKE) ++severeMistakes;
    }
}
