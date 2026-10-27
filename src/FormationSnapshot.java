import java.util.List;
import java.util.Map;

record FormationSnapshot(
        Pos center,
        Pos forward,
        Pos lateral,
        double width,
        double depth,
        double cohesion,
        String leftRoleAgent,
        String centerRoleAgent,
        String rightRoleAgent,
        List<String> historicalRosterMembers,
        List<String> activeParticipants,
        Map<String, Double> lateralProjections,
        Map<String, Double> forwardProjections
) {
    FormationSnapshot {
        historicalRosterMembers = List.copyOf(historicalRosterMembers);
        activeParticipants = List.copyOf(activeParticipants);
        lateralProjections = java.util.Collections.unmodifiableMap(new java.util.TreeMap<>(lateralProjections));
        forwardProjections = java.util.Collections.unmodifiableMap(new java.util.TreeMap<>(forwardProjections));
    }
}
