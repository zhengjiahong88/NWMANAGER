public final class ModernGeometryTest {
    private static final double DT = 1.0 / 120.0;

    public static void main(String[] args) {
        verifyEmergentGroupMovement();
        verifyWeaponLaneAvoidance();
        verifyCoveredAgentCanHold();
        verifyAttackLineCreation();
        verifyTwoVsOneGeometry();
        verifyUndetectedFlankerDoesNotDriveDefense();
        System.out.println("Modern geometry tests passed.");
    }

    private static void verifyEmergentGroupMovement() {
        var simulation = simulation(SimulationScenario.MODERN_2V2, 101L);
        var group = simulation.engagements().getFirst().group;
        var minimumRadius = new double[4];
        var maximumRadius = new double[4];
        java.util.Arrays.fill(minimumRadius, Double.POSITIVE_INFINITY);
        double speedDifference = 0, maximumPartnerDistance = 0;
        for (var tick = 0; tick < 8 * 120; ++tick) {
            for (var agent : simulation.agents) {
                agent.state = CombatState.BLOCKING;
                agent.stateTime = 0;
                agent.decisionTime = 20;
            }
            simulation.update(DT);
            var minimumSpeed = Double.POSITIVE_INFINITY;
            var maximumSpeed = 0.0;
            for (var n = 0; n < simulation.agents.length; ++n) {
                var agent = simulation.agents[n];
                var radius = agent.pos.minus(group.center).hypotenuse();
                minimumRadius[n] = Math.min(minimumRadius[n], radius);
                maximumRadius[n] = Math.max(maximumRadius[n], radius);
                minimumSpeed = Math.min(minimumSpeed, agent.velocity.hypotenuse());
                maximumSpeed = Math.max(maximumSpeed, agent.velocity.hypotenuse());
            }
            speedDifference += maximumSpeed - minimumSpeed;
            maximumPartnerDistance = Math.max(maximumPartnerDistance,
                    Math.max(simulation.blueTeam[0].pos.minus(simulation.blueTeam[1].pos).hypotenuse(),
                            simulation.redTeam[0].pos.minus(simulation.redTeam[1].pos).hypotenuse()));
        }
        var metrics = simulation.modernMetrics();
        var radiusChanges = 0;
        for (var n = 0; n < minimumRadius.length; ++n)
            if (maximumRadius[n] - minimumRadius[n] >= 0.08) ++radiusChanges;
        require(metrics.localManeuversStarted >= 12 && metrics.holds > 0,
                "A: persistent local maneuvers must include individual holds.");
        require(metrics.lateralSteps > 0 && metrics.diagonalAdvances > 0 &&
                        metrics.giveGroundManeuvers > 0,
                "A: lateral, diagonal, and give-ground steps must all be possible.");
        require(speedDifference / (8 * 120) >= 0.08,
                "A: the four agents must temporarily move at different speeds.");
        require(radiusChanges >= 3,
                "A: agents must not remain attached to a fixed collective radius.");
        require(metrics.localDirectionReversals > 0,
                "A: local direction changes must emerge without reversing the whole team.");
        require(metrics.groupAngularDisplacement >= Math.toRadians(3),
                "A: some collective circulation must remain possible.");
        require(metrics.teamManeuverContribution > metrics.localManeuverContribution * 0.55 &&
                        metrics.localManeuverContribution > metrics.teamManeuverContribution * 0.10,
                "A: the persistent duo maneuver must lead while local maneuvers remain meaningful.");
        require(metrics.localManeuversInterrupted < metrics.localManeuversStarted * 0.15 &&
                        metrics.maneuverDurationSum /
                                Math.max(1, metrics.localManeuversCompleted +
                                        metrics.localManeuversInterrupted) >= 0.25,
                "A: persistent maneuvers must not thrash between local choices.");
        require(maximumPartnerDistance < 2.65,
                "A: local movement must preserve team cohesion.");
    }

    private static void verifyWeaponLaneAvoidance() {
        var simulation = simulation(SimulationScenario.MODERN_2V2, 102L);
        var a1 = simulation.blueTeam[0];
        var a2 = simulation.blueTeam[1];
        var b1 = simulation.redTeam[0];
        a1.pos = new Pos(3.40, 3.30);
        a2.pos = new Pos(3.15, 4.70);
        b1.pos = new Pos(4.90, 3.30);
        b1.angle = Math.PI;
        b1.weaponYaw = 0;
        b1.state = CombatState.ATTACK_HOLD;
        observedHold(a1, b1);
        observedHold(a2, b1);
        simulation.refreshLocalManeuver(a1);
        simulation.refreshLocalManeuver(a2);
        require(a1.localManeuver.type == LocalManeuverType.AVOID_PRIMARY_WEAPON_LINE,
                "B: A1 must leave B1's credible weapon lane laterally.");
        require(a2.localManeuver.type != LocalManeuverType.AVOID_PRIMARY_WEAPON_LINE,
                "B: A2 must not mechanically copy A1's avoidance.");
    }

    private static void verifyCoveredAgentCanHold() {
        var simulation = simulation(SimulationScenario.MODERN_2V2, 103L);
        var a1 = simulation.blueTeam[0];
        var a2 = simulation.blueTeam[1];
        var b1 = simulation.redTeam[0];
        a1.pos = new Pos(3.10, 3.00);
        a2.pos = new Pos(3.05, 4.35);
        b1.pos = new Pos(4.75, 3.00);
        a1.primaryMark = b1;
        a1.target = b1;
        simulation.refreshLocalManeuver(a1);
        require(a1.localManeuver.type == LocalManeuverType.HOLD_POSITION,
                "C: an agent already covering its ally must be able to hold position.");
    }

    private static void verifyAttackLineCreation() {
        var simulation = simulation(SimulationScenario.MODERN_2V2, 104L);
        var a1 = simulation.blueTeam[0];
        var a2 = simulation.blueTeam[1];
        var b1 = simulation.redTeam[0];
        a2.pos = new Pos(2.80, 3.50);
        a1.pos = new Pos(3.70, 3.50);
        b1.pos = new Pos(5.15, 3.50);
        a2.primaryMark = b1;
        a2.target = b1;
        var originalState = a2.state;
        simulation.refreshLocalManeuver(a2);
        require(a2.localManeuver.type == LocalManeuverType.CREATE_ATTACK_LINE,
                "D: A2 must step aside when A1 obstructs its attack line.");
        require(a2.state == originalState,
                "D: creating an attack line must not automatically launch a stab.");
    }

    private static void observedHold(Musketeer observer, Musketeer attacker) {
        var awareness = observer.opponentAwareness.get(attacker);
        awareness.visible = true;
        awareness.memoryTime = 1;
        awareness.lastKnown = attacker.pos;
        awareness.perceivedVelocity = new Pos();
        awareness.observedBodyAngle = attacker.angle;
        awareness.observedWeaponAngle = attacker.weaponWorldAngle();
        awareness.observedProjectedRange = attacker.projectedThrustLength();
        awareness.observedState = CombatState.ATTACK_HOLD;
        awareness.observedStateTime = 0.4;
        awareness.observedAttackDirection = CombatDirection.UP;
    }

    private static void verifyTwoVsOneGeometry() {
        var simulation = simulation(SimulationScenario.CONTROLLED_2V1, 2_021L);
        for (var tick = 0; tick < 8 * 120 && simulation.winningTeam == null; ++tick)
            simulation.update(DT);
        var metrics = simulation.modernMetrics();
        require(metrics.fixerGeometrySamples > 0 &&
                        metrics.fixerFrontAngleSum / metrics.fixerGeometrySamples < Math.toRadians(95),
                "The fixer must remain predominantly in the defender's frontal sector.");
        require(metrics.attackersSeparationSamples > 0,
                "The defender must measure two detected threats in the 2v1.");
        require(metrics.backEscapesWithoutDetection == 0,
                "A defender must never escape from an undetected rear threat.");
    }

    private static void verifyUndetectedFlankerDoesNotDriveDefense() {
        var simulation = simulation(SimulationScenario.CONTROLLED_2V1, 77L);
        var engagement = simulation.engagements().getFirst();
        var defender = engagement.primaryTarget;
        var fixer = engagement.fixer;
        var flanker = engagement.flanker;
        defender.pos = new Pos(4.6, 3.5);
        defender.angle = Math.PI;
        fixer.pos = new Pos(2.9, 3.5);
        flanker.pos = new Pos(6.3, 3.5);
        var awareness = defender.opponentAwareness.get(flanker);
        awareness.visible = false;
        awareness.memoryTime = 0;
        defender.rearThreat = null;
        defender.multiThreatAttention.focusedThreat = fixer;
        defender.multiThreatAttention.focusLockRemaining = 1;
        flanker.state = CombatState.ATTACK_HOLD;
        simulation.update(DT);
        require(defender.multiThreatAttention.focusedThreat == fixer,
                "An invisible, untracked flanker must not become the defender's focus.");
        require(defender.backEscapeTime <= 0 &&
                        simulation.modernMetrics().backEscapesWithoutDetection == 0,
                "An untracked rear attacker must not trigger a back escape.");
    }

    private static DuelSimulation simulation(SimulationScenario scenario, long seed) {
        return new DuelSimulation(AgentConfiguration.defaultBlue(),
                AgentConfiguration.defaultBlueTwo(), AgentConfiguration.defaultRed(),
                AgentConfiguration.defaultRedTwo(), scenario, seed);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
