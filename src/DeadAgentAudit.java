final class DeadAgentAudit {
    int movementUpdates, velocityIntegrations, bodyRotationUpdates, weaponRotationUpdates;
    int agentCollisionChecks, agentCollisionResolutions, weaponCollisionChecks;
    int avoidanceParticipations, boundaryCorrections;
    int formationParticipations, coverNetworkParticipations, clusterParticipations,
            attentionParticipations, targetSelectionParticipations;
    double maximumDisplacementAfterDeath;
    private Pos deathPosition;
    private boolean active;

    void begin(Pos position) {
        if (active) return;
        deathPosition = position;
        active = true;
    }

    boolean active() {
        return active;
    }

    void observe(Pos position) {
        if (active) maximumDisplacementAfterDeath = Math.max(maximumDisplacementAfterDeath,
                position.minus(deathPosition).hypotenuse());
    }
}
