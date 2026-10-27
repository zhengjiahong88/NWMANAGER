import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

final class DifferentialStageRingBuffer {
    private final int capacityTicks;
    private final LinkedHashMap<Long, List<DifferentialStageSnapshot>> snapshots = new LinkedHashMap<>();

    DifferentialStageRingBuffer(int capacityTicks) {
        this.capacityTicks = capacityTicks;
    }

    void add(DifferentialStageSnapshot snapshot) {
        snapshots.computeIfAbsent(snapshot.tick(), ignored -> new ArrayList<>()).add(snapshot);
        while (snapshots.size() > capacityTicks) snapshots.remove(snapshots.keySet().iterator().next());
    }

    List<DifferentialStageSnapshot> flattened() {
        var result = new ArrayList<DifferentialStageSnapshot>();
        for (var tickSnapshots : snapshots.values()) result.addAll(tickSnapshots);
        return List.copyOf(result);
    }
}
