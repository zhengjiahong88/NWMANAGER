record SemanticRandomDrawKey(
        long tick,
        DifferentialTickStage stage,
        String requesterId,
        String subjectId,
        RandomDrawPurpose purpose,
        int localIndex
) implements Comparable<SemanticRandomDrawKey> {
    SemanticRandomDrawKey {
        if (stage == null || requesterId == null || subjectId == null || purpose == null)
            throw new IllegalArgumentException("A semantic random key cannot contain null.");
        if (localIndex < 0) throw new IllegalArgumentException("localIndex must be non-negative.");
    }

    @Override
    public int compareTo(SemanticRandomDrawKey other) {
        var comparison = Long.compare(tick, other.tick);
        if (comparison != 0) return comparison;
        comparison = Integer.compare(stage.ordinal(), other.stage.ordinal());
        if (comparison != 0) return comparison;
        comparison = requesterId.compareTo(other.requesterId);
        if (comparison != 0) return comparison;
        comparison = subjectId.compareTo(other.subjectId);
        if (comparison != 0) return comparison;
        comparison = Integer.compare(purpose.ordinal(), other.purpose.ordinal());
        return comparison != 0 ? comparison : Integer.compare(localIndex, other.localIndex);
    }
}
