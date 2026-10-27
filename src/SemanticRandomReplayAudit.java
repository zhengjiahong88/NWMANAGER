record SemanticRandomReplayAudit(
        int totalRecordedDraws,
        int uniqueSemanticKeys,
        int duplicateKeys,
        int replayedDraws,
        int fallbackDraws,
        int unusedDeadAgentKeys,
        int unusedSurvivorKeys,
        int unmatchedReplayRequests,
        SemanticRandomDrawKey firstUnmatchedReplayRequest,
        SemanticRandomDrawKey firstSurvivorKeyOnlyInA
) {
}
