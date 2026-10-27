final class DeadAgentNeutralizationDifferentialTest {
    public static void main(String[] args) {
        new DeadAgentNeutralizationDifferentialTest().verifyLogicalNeutralization();
    }

    private void verifyLogicalNeutralization() {
        var runner = new DeadAgentDifferentialRunner();
        var result = runner.runDeadAgentScenario(41,
                DeadAgentExperimentMode.LOGICALLY_NEUTRALIZED);
        var audit = result.deadAgentAudit;
        var dead = result.deadAgentState;
        require(!dead.alive() && dead.state() == CombatState.DEAD,
                "D1 must preserve the dead logical state");
        require(!dead.hiddenAfterDeath() && dead.renderOffset().equals(new Pos()),
                "D1 must remain visible without a rendering offset");
        require(dead.finalPosition().equals(dead.deathPosition()),
                "deathPosition=" + dead.deathPosition() + " finalPosition=" + dead.finalPosition());
        require(dead.velocity().equals(new Pos()) && dead.desiredVelocity().equals(new Pos()),
                "D1 velocities must remain zero");
        require(Math.abs(dead.angularVelocity()) <= 1e-9,
                "angularVelocity=" + dead.angularVelocity());
        require(Math.abs(dead.bodyAngle() - dead.deathBodyAngle()) <= 1e-9,
                "body angle changed after death");
        require(Math.abs(dead.weaponAngle() - dead.deathWeaponAngle()) <= 1e-9,
                "weapon angle changed after death");
        require(audit.movementUpdates == 0, "movementUpdates=" + audit.movementUpdates);
        require(audit.velocityIntegrations == 0, "velocityIntegrations=" + audit.velocityIntegrations);
        require(audit.bodyRotationUpdates == 0, "bodyRotationUpdates=" + audit.bodyRotationUpdates);
        require(audit.weaponRotationUpdates == 0, "weaponRotationUpdates=" + audit.weaponRotationUpdates);
        require(audit.avoidanceParticipations == 0,
                "avoidanceParticipations=" + audit.avoidanceParticipations);
        require(audit.boundaryCorrections == 0, "boundaryCorrections=" + audit.boundaryCorrections);
        require(audit.formationParticipations == 0,
                "formationParticipations=" + audit.formationParticipations);
        require(audit.coverNetworkParticipations == 0,
                "coverNetworkParticipations=" + audit.coverNetworkParticipations);
        require(audit.attentionParticipations == 0,
                "attentionParticipations=" + audit.attentionParticipations);
        require(audit.targetSelectionParticipations == 0,
                "targetSelectionParticipations=" + audit.targetSelectionParticipations);
        require(audit.agentCollisionChecks == 0 && audit.agentCollisionResolutions == 0 &&
                        audit.weaponCollisionChecks == 0 && audit.clusterParticipations == 0,
                "collision or cluster participation changed in D1");
        require(audit.maximumDisplacementAfterDeath <= 1e-9,
                "maximumDisplacementAfterDeath=" + audit.maximumDisplacementAfterDeath);
        var current = runner.runDeadAgentScenario(41,
                DeadAgentExperimentMode.CURRENT_BUGGED_BEHAVIOR);
        var comparison = new DeadAgentDifferentialComparator().compare(current, result);
        System.out.println("D1 audit: movement=0 velocity=0 rotations=0 avoidance=0 boundary=0 " +
                "formation=0 cover=0 attention=0 targetSelection=0 displacement=" +
                audit.maximumDisplacementAfterDeath);
        System.out.println("Exploratory A/D1 identical=" + comparison.identical() +
                " affected=" + comparison.affectedAgents());
        System.out.println("Exploratory first ticks: velocity=" +
                comparison.firstVelocityDivergenceTick() + " position=" +
                comparison.firstPositionDivergenceTick() + " attention=" +
                comparison.firstAttentionDivergenceTick() + " target=" +
                comparison.firstTargetDivergenceTick() + " combatEvent=" +
                comparison.firstCombatEventDivergenceTick());
        System.out.println("DeadAgentNeutralizationDifferentialTest 6.2b.1 passed.");
    }

    private void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
