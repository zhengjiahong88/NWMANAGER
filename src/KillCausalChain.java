import java.util.List;

record KillCausalChain(KillExplanation kill,
                       DecisionTrace teamManeuver,
                       DecisionTrace initiatingMovement,
                       DecisionTrace attackDecision,
                       DecisionTrace victimReaction,
                       DecisionTrace allyCoverDecision,
                       List<DecisionTrace> physicalResolution) {
    String observed(DecisionTrace trace) {
        return trace == null ? "NONE_OBSERVED" : trace.action() + ":" + trace.primaryReason();
    }

    boolean completeOrExplicit() {
        return kill != null && physicalResolution != null;
    }

    String deterministicKey(UiLanguage language) {
        var resolution = new StringBuilder();
        for (var trace : physicalResolution)
            resolution.append('[').append(trace.deterministicKey(language)).append(']');
        return kill.deterministicKey(language) + "|movement=" + key(initiatingMovement, language) +
                "|teamManeuver=" + key(teamManeuver, language) +
                "|attack=" + key(attackDecision, language) + "|reaction=" +
                key(victimReaction, language) + "|cover=" + key(allyCoverDecision, language) +
                "|resolution=" + resolution;
    }

    private String key(DecisionTrace trace, UiLanguage language) {
        return trace == null ? "NONE_OBSERVED" : trace.deterministicKey(language);
    }
}
