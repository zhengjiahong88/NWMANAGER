import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

final class SemanticRandomTape {
    private final LinkedHashMap<SemanticRandomDrawKey, RandomDrawRecord> records = new LinkedHashMap<>();
    private final ArrayList<RandomDrawRecord> chronologicalRecords = new ArrayList<>();
    private final Set<SemanticRandomDrawKey> replayedKeys = new HashSet<>();
    private int duplicateKeys, replayedDraws, fallbackDraws, unmatchedReplayRequests;
    private SemanticRandomDrawKey firstUnmatchedReplayRequest;

    void record(SemanticRandomDrawKey key, double value, long legacyGlobalDrawIndex, String caller) {
        if (records.containsKey(key)) {
            ++duplicateKeys;
            throw new IllegalStateException("DUPLICATE_SEMANTIC_RANDOM_KEY: " + key);
        }
        var record = new RandomDrawRecord(key, value, legacyGlobalDrawIndex, caller);
        records.put(key, record);
        chronologicalRecords.add(record);
    }

    double replay(SemanticRandomDrawKey key, long baseSeed) {
        var record = records.get(key);
        if (record != null) {
            ++replayedDraws;
            replayedKeys.add(key);
            return record.value();
        }
        ++fallbackDraws;
        ++unmatchedReplayRequests;
        if (firstUnmatchedReplayRequest == null) firstUnmatchedReplayRequest = key;
        return deterministicFallback(baseSeed, key);
    }

    double deterministicFallback(long baseSeed, SemanticRandomDrawKey key) {
        var hash = mix(baseSeed ^ key.tick());
        hash = mix(hash ^ key.stage().ordinal());
        hash = mix(hash ^ stableHash(key.requesterId()));
        hash = mix(hash ^ stableHash(key.subjectId()));
        hash = mix(hash ^ key.purpose().ordinal());
        hash = mix(hash ^ key.localIndex());
        return (hash >>> 11) * 0x1.0p-53;
    }

    SemanticRandomReplayAudit audit(String controlledDeadAgentId) {
        var unusedDead = 0;
        var unusedSurvivors = 0;
        SemanticRandomDrawKey firstSurvivor = null;
        for (var key : records.keySet()) {
            if (replayedKeys.contains(key)) continue;
            if (key.requesterId().equals(controlledDeadAgentId)) ++unusedDead;
            else {
                ++unusedSurvivors;
                if (firstSurvivor == null || key.compareTo(firstSurvivor) < 0) firstSurvivor = key;
            }
        }
        return new SemanticRandomReplayAudit(chronologicalRecords.size(), records.size(), duplicateKeys,
                replayedDraws, fallbackDraws, unusedDead, unusedSurvivors, unmatchedReplayRequests,
                firstUnmatchedReplayRequest, firstSurvivor);
    }

    List<RandomDrawRecord> chronologicalRecords() {
        return List.copyOf(chronologicalRecords);
    }

    Map<SemanticRandomDrawKey, RandomDrawRecord> records() {
        return Collections.unmodifiableMap(new TreeMap<>(records));
    }

    private long stableHash(String value) {
        var hash = 0xCBF29CE484222325L;
        for (var character : value.toCharArray()) hash = (hash ^ character) * 0x100000001B3L;
        return hash;
    }

    private long mix(long value) {
        value = (value ^ value >>> 30) * 0xBF58476D1CE4E5B9L;
        value = (value ^ value >>> 27) * 0x94D049BB133111EBL;
        return value ^ value >>> 31;
    }
}
