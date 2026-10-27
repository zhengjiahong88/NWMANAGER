import java.util.List;

record DifferentialAgentSnapshot(
        String agentId,
        Pos position,
        Pos velocity,
        Pos desiredVelocity,
        double bodyAngle,
        double weaponAngle,
        String combatState,
        String actionState,
        String formationRole,
        String approachRole,
        String teamManeuver,
        String localManeuver,
        DifferentialAttentionSnapshot attention,
        List<AwarenessEntrySnapshot> awarenessEntries,
        String targetId,
        String plannedTargetId,
        String movementTargetId,
        String revengeTargetId,
        String counterCoverTargetId,
        String bestAttackOpportunityTargetId,
        int viableIncomingCoverLinks,
        int viableOutgoingCoverLinks,
        String attackOpportunityReason,
        boolean attackOpportunityViable,
        DifferentialSteeringSnapshot steering
) implements Comparable<DifferentialAgentSnapshot> {
    DifferentialAgentSnapshot {
        awarenessEntries = List.copyOf(awarenessEntries);
    }

    @Override
    public int compareTo(DifferentialAgentSnapshot other) {
        return agentId.compareTo(other.agentId);
    }
}
