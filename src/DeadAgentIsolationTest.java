import java.util.ArrayList;

final class DeadAgentIsolationTest {
    private static final double DT = 1.0 / 120.0;

    public static void main(String[] args) {
        new DeadAgentIsolationTest().verifyDeadAgentIsolation();
    }

    private void verifyDeadAgentIsolation() {
        var simulation = fixture(9_601L);
        placeNearCenter(simulation);
        for (var tick = 0; tick < 60; ++tick) simulation.update(DT);
        var dead = simulation.blueTeam[1];
        require(dead.alive, "B2 must still be alive before the controlled elimination.");
        simulation.eliminateForDeadAgentTest(dead);
        var deathPosition = dead.pos;
        var deathBodyAngle = dead.angle;
        var deathWeaponAngle = dead.weaponWorldAngle();
        for (var tick = 0; tick < 2_000; ++tick) simulation.update(DT);
        var audit = dead.deadAgentAudit;
        System.out.println("dead displacement after death=" + audit.maximumDisplacementAfterDeath);
        System.out.println("movement updates=" + audit.movementUpdates);
        System.out.println("velocity integrations=" + audit.velocityIntegrations);
        System.out.println("body rotation updates=" + audit.bodyRotationUpdates);
        System.out.println("weapon rotation updates=" + audit.weaponRotationUpdates);
        System.out.println("collision checks=" + audit.agentCollisionChecks);
        System.out.println("collision resolutions=" + audit.agentCollisionResolutions);
        System.out.println("weapon collision checks=" + audit.weaponCollisionChecks);
        System.out.println("avoidance participations=" + audit.avoidanceParticipations);
        System.out.println("boundary corrections=" + audit.boundaryCorrections);
        System.out.println("formation participations=" + audit.formationParticipations);
        System.out.println("cover participations=" + audit.coverNetworkParticipations);
        System.out.println("cluster participations=" + audit.clusterParticipations);
        System.out.println("attention participations=" + audit.attentionParticipations);
        System.out.println("target selection participations=" + audit.targetSelectionParticipations);
        var violations = new ArrayList<String>();
        expect(!dead.alive, "B2 became alive again", violations);
        expect(dead.state == CombatState.DEAD, "combat state is " + dead.state, violations);
        expect(dead.velocity.equals(new Pos()), "velocity=" + dead.velocity, violations);
        expect(dead.desiredVelocity.equals(new Pos()), "desiredVelocity=" + dead.desiredVelocity, violations);
        expect(audit.movementUpdates == 0, "movementUpdates=" + audit.movementUpdates, violations);
        expect(audit.velocityIntegrations == 0,
                "velocityIntegrations=" + audit.velocityIntegrations, violations);
        expect(audit.bodyRotationUpdates == 0,
                "bodyRotationUpdates=" + audit.bodyRotationUpdates, violations);
        expect(audit.weaponRotationUpdates == 0,
                "weaponRotationUpdates=" + audit.weaponRotationUpdates, violations);
        expect(audit.agentCollisionChecks == 0,
                "agentCollisionChecks=" + audit.agentCollisionChecks, violations);
        expect(audit.agentCollisionResolutions == 0,
                "agentCollisionResolutions=" + audit.agentCollisionResolutions, violations);
        expect(audit.weaponCollisionChecks == 0,
                "weaponCollisionChecks=" + audit.weaponCollisionChecks, violations);
        expect(audit.avoidanceParticipations == 0,
                "avoidanceParticipations=" + audit.avoidanceParticipations, violations);
        expect(audit.boundaryCorrections == 0,
                "boundaryCorrections=" + audit.boundaryCorrections, violations);
        expect(audit.formationParticipations == 0,
                "formationParticipations=" + audit.formationParticipations, violations);
        expect(audit.coverNetworkParticipations == 0,
                "coverNetworkParticipations=" + audit.coverNetworkParticipations, violations);
        expect(audit.clusterParticipations == 0,
                "clusterParticipations=" + audit.clusterParticipations, violations);
        expect(audit.attentionParticipations == 0,
                "attentionParticipations=" + audit.attentionParticipations, violations);
        expect(audit.targetSelectionParticipations == 0,
                "targetSelectionParticipations=" + audit.targetSelectionParticipations, violations);
        expect(audit.maximumDisplacementAfterDeath <= 1e-9,
                "maximumDisplacementAfterDeath=" + audit.maximumDisplacementAfterDeath, violations);
        expect(dead.pos.equals(deathPosition), "deathPosition=" + deathPosition + ", final=" + dead.pos,
                violations);
        expect(Math.abs(dead.angle - deathBodyAngle) <= 1e-9,
                "body angle changed from " + deathBodyAngle + " to " + dead.angle, violations);
        expect(Math.abs(dead.weaponWorldAngle() - deathWeaponAngle) <= 1e-9,
                "weapon angle changed from " + deathWeaponAngle + " to " + dead.weaponWorldAngle(),
                violations);
        if (!violations.isEmpty()) throw new AssertionError("Dead Agent isolation failed: " + violations);
        System.out.println("DeadAgentIsolationTest passed: every post-death counter is zero.");
    }

    private void placeNearCenter(DuelSimulation simulation) {
        for (var n = 0; n < 3; ++n) {
            simulation.blueTeam[n].pos = new Pos(2.30, 2.30 + n * 1.20);
            simulation.redTeam[n].pos = new Pos(5.70, 2.30 + n * 1.20);
            simulation.blueTeam[n].velocity = simulation.redTeam[n].velocity = new Pos();
        }
    }

    private void expect(boolean condition, String message, ArrayList<String> violations) {
        if (!condition) violations.add(message);
    }

    private DuelSimulation fixture(long seed) {
        var blue = new TeamConfiguration3("Blue", AgentConfiguration.defaultBlue(),
                AgentConfiguration.defaultBlueTwo(), AgentConfiguration.defaultBlueThree());
        var red = new TeamConfiguration3("Red", AgentConfiguration.defaultRed(),
                AgentConfiguration.defaultRedTwo(), AgentConfiguration.defaultRedThree());
        return new DuelSimulation(blue, red, SimulationScenario.CONTROLLED_3V3, seed);
    }

    private void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
