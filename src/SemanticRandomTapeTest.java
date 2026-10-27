final class SemanticRandomTapeTest {
    public static void main(String[] args) {
        new SemanticRandomTapeTest().verifySemanticTape();
    }

    private void verifySemanticTape() {
        var referenceKeys = new SemanticRandomKeyFactory();
        var replayKeys = new SemanticRandomKeyFactory();
        var deadOnly = referenceKeys.next(71, DifferentialTickStage.AFTER_ATTENTION_UPDATE,
                "B2", "R1", RandomDrawPurpose.GROUP3_PERCEPTION_POSITION_NOISE);
        var referenceCommon = referenceKeys.next(71, DifferentialTickStage.AFTER_ATTENTION_UPDATE,
                "R2", "B3", RandomDrawPurpose.GROUP3_PERCEPTION_POSITION_NOISE);
        var replayCommon = replayKeys.next(71, DifferentialTickStage.AFTER_ATTENTION_UPDATE,
                "R2", "B3", RandomDrawPurpose.GROUP3_PERCEPTION_POSITION_NOISE);
        var secondCommon = referenceKeys.next(71, DifferentialTickStage.AFTER_ATTENTION_UPDATE,
                "R2", "B3", RandomDrawPurpose.GROUP3_PERCEPTION_POSITION_NOISE);
        require(referenceCommon.equals(replayCommon),
                "B2 activity changed a survivor semantic key");
        require(referenceCommon.localIndex() == 0 && secondCommon.localIndex() == 1,
                "local indices are not scoped by semantic prefix");

        var tape = new SemanticRandomTape();
        tape.record(deadOnly, 0.125, 17, "dead-perception-position-x");
        tape.record(referenceCommon, 0.875, 18, "survivor-perception-position-x");
        var replayed = tape.replay(replayCommon, 41);
        require(Double.doubleToLongBits(replayed) == Double.doubleToLongBits(0.875),
                "semantic replay was not bit-exact");
        var audit = tape.audit("B2");
        require(audit.replayedDraws() == 1 && audit.unusedDeadAgentKeys() == 1 &&
                        audit.unusedSurvivorKeys() == 0 && audit.fallbackDraws() == 0,
                "dead-key audit is incorrect: " + audit);

        var missing = replayKeys.next(72, DifferentialTickStage.AFTER_COMBAT_DECISIONS,
                "B1", "R1", RandomDrawPurpose.ATTACK_RELEASE_DECISION);
        var firstFallback = tape.deterministicFallback(41, missing);
        var secondFallback = tape.deterministicFallback(41, missing);
        require(Double.doubleToLongBits(firstFallback) == Double.doubleToLongBits(secondFallback),
                "fallback is not deterministic");
        require(Double.doubleToLongBits(tape.replay(missing, 41)) ==
                        Double.doubleToLongBits(firstFallback), "replay fallback changed value");
        var fallbackAudit = tape.audit("B2");
        require(fallbackAudit.fallbackDraws() == 1 &&
                        fallbackAudit.unmatchedReplayRequests() == 1 &&
                        fallbackAudit.firstUnmatchedReplayRequest().equals(missing),
                "unmatched replay request was not reported");

        var duplicateRejected = false;
        try {
            tape.record(referenceCommon, 0.5, 19, "duplicate");
        } catch (IllegalStateException exception) {
            duplicateRejected = exception.getMessage().startsWith("DUPLICATE_SEMANTIC_RANDOM_KEY");
        }
        require(duplicateRejected, "duplicate semantic key was accepted");

        var survivorOnlyTape = new SemanticRandomTape();
        survivorOnlyTape.record(referenceCommon, 0.875, 18, "survivor");
        var survivorAudit = survivorOnlyTape.audit("B2");
        require(survivorAudit.unusedSurvivorKeys() == 1 &&
                        survivorAudit.firstSurvivorKeyOnlyInA().equals(referenceCommon),
                "SURVIVOR_KEY_ONLY_IN_A was not distinguished");
        System.out.println("SemanticRandomTapeTest passed: stable keys, local indices, exact replay, " +
                "deterministic fallback and unused-key classes verified.");
    }

    private void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
