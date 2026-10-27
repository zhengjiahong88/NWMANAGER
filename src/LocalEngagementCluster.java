import java.util.List;

record LocalEngagementCluster(
        List<Musketeer> blueMembers,
        List<Musketeer> redMembers,
        Pos center,
        double interactionRadius,
        double minimumImpactEta,
        double isolationConfidence,
        boolean contested,
        String reason
) {
    LocalEngagementCluster {
        blueMembers = List.copyOf(blueMembers);
        redMembers = List.copyOf(redMembers);
    }
}
