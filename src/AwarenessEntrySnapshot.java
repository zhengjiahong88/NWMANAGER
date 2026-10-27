record AwarenessEntrySnapshot(
        String opponentId,
        String attentionLevel,
        Pos lastKnownPosition,
        Pos lastKnownVelocity,
        double observationTime,
        long observationCount,
        double confidence,
        boolean stateDetailFresh
) implements Comparable<AwarenessEntrySnapshot> {
    @Override
    public int compareTo(AwarenessEntrySnapshot other) {
        return opponentId.compareTo(other.opponentId);
    }
}
