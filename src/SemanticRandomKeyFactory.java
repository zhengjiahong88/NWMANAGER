import java.util.HashMap;

final class SemanticRandomKeyFactory {
    private final HashMap<Prefix, Integer> localIndices = new HashMap<>();

    SemanticRandomDrawKey next(long tick, DifferentialTickStage stage, String requesterId,
                               String subjectId, RandomDrawPurpose purpose) {
        var prefix = new Prefix(tick, stage, requesterId, subjectId, purpose);
        var index = localIndices.getOrDefault(prefix, 0);
        localIndices.put(prefix, index + 1);
        return new SemanticRandomDrawKey(tick, stage, requesterId, subjectId, purpose, index);
    }

    void discardBefore(long tick) {
        localIndices.keySet().removeIf(prefix -> prefix.tick < tick);
    }

    private record Prefix(long tick, DifferentialTickStage stage, String requesterId,
                          String subjectId, RandomDrawPurpose purpose) {
    }
}
