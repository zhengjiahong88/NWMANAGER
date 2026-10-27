final class TeamManeuverTest {
    private static final double DT = 1.0 / 120.0;

    public static void main(String[] args) {
        var test = new TeamManeuverTest();
        test.verifyAVisibleDuoRotation();
        test.verifyBOpposedIndependentRotations();
        test.verifyCRotationAgainstPressure();
        test.verifyDVisibleIndividualHold();
        test.verifyEWeaponLaneOverridesTeamMotion();
        test.verifyFShapeAndShiftManeuvers();
        test.verifyIDynamicFiveSecondScene();
        test.verifyJDeterminism();
        System.out.println("TeamManeuver controlled tests A-F and I-J passed.");
    }

    private void verifyAVisibleDuoRotation() {
        var simulation = fixture(701L);
        simulation.forceTeamManeuver(Team.BLUE, TeamManeuverType.ROTATE_RIGHT);
        simulation.forceTeamManeuver(Team.RED, TeamManeuverType.HOLD_LINE);
        var group = simulation.modernGroupEngagement().group;
        var blueStart = group.blueCenter;
        var redStart = group.redCenter;
        var initial = group.blueManeuver;
        var innerSpeed = 0.0;
        var outerSpeed = 0.0;
        var samples = 0;
        for (var tick = 0; tick < 156; ++tick) {
            suppressDecisions(simulation);
            simulation.update(DT);
            var maneuver = group.blueManeuver;
            require(maneuver != null && maneuver.type == TeamManeuverType.ROTATE_RIGHT,
                    "A: the forced duo rotation must persist visibly for more than one second.");
            innerSpeed += maneuver.innerPlayer.velocity.hypotenuse();
            outerSpeed += maneuver.outerPlayer.velocity.hypotenuse();
            ++samples;
        }
        group.updateCenters();
        var forward = group.redCenter.minus(blueStart);
        forward = forward.div(forward.hypotenuse());
        var right = new Pos(forward.y(), -forward.x());
        var blueLateral = group.blueCenter.minus(blueStart).dot(right);
        var redLateral = Math.abs(group.redCenter.minus(redStart).dot(right));
        require(blueLateral > 0.30,
                "A: ROTATE_RIGHT must produce a clearly visible team-local lateral displacement (" +
                        blueLateral + ").");
        require(outerSpeed / samples > innerSpeed / samples + 0.02,
                "A: the outer player must move faster than the inner player (outer=" +
                        outerSpeed / samples + ", inner=" + innerSpeed / samples + ").");
        require(redLateral < blueLateral,
                "A: HOLD_LINE must not inherit the rotating team's tangent.");
        require(group.blueManeuver == initial,
                "A: TeamManeuver must be persistent, not recreated at every decision.");
    }

    private void verifyBOpposedIndependentRotations() {
        var simulation = fixture(702L);
        simulation.forceTeamManeuver(Team.BLUE, TeamManeuverType.ROTATE_RIGHT);
        simulation.forceTeamManeuver(Team.RED, TeamManeuverType.ROTATE_LEFT);
        var group = simulation.modernGroupEngagement().group;
        var blueStart = group.blueCenter;
        var redStart = group.redCenter;
        for (var tick = 0; tick < 156; ++tick) {
            suppressDecisions(simulation);
            simulation.update(DT);
        }
        group.updateCenters();
        var initialAxis = redStart.minus(blueStart);
        var initialDistance = initialAxis.hypotenuse();
        var forward = initialAxis.div(initialDistance);
        var right = new Pos(forward.y(), -forward.x());
        var blueLateral = group.blueCenter.minus(blueStart).dot(right);
        var redLateral = group.redCenter.minus(redStart).dot(right);
        require(Math.abs(blueLateral) > 0.30 && Math.abs(redLateral) > 0.30,
                "B: both teams must execute their own rotation vectors (blue=" + blueLateral +
                        ", red=" + redLateral + ").");
        require(Math.abs(group.redCenter.minus(group.blueCenter).hypotenuse() - initialDistance) > 0.02 ||
                        Math.abs(Math.abs(blueLateral) - Math.abs(redLateral)) > 0.015,
                "B: opposed maneuvers must not collapse into perfect four-player symmetry.");
    }

    private void verifyCRotationAgainstPressure() {
        var simulation = fixture(703L);
        simulation.forceTeamManeuver(Team.BLUE, TeamManeuverType.ROTATE_LEFT);
        simulation.forceTeamManeuver(Team.RED, TeamManeuverType.PRESS_FORWARD);
        var group = simulation.modernGroupEngagement().group;
        var blueStart = group.blueCenter;
        var redStart = group.redCenter;
        var initialDistance = redStart.minus(blueStart).hypotenuse();
        for (var tick = 0; tick < 132; ++tick) {
            suppressDecisions(simulation);
            simulation.update(DT);
        }
        group.updateCenters();
        var forward = redStart.minus(blueStart).div(initialDistance);
        var left = new Pos(-forward.y(), forward.x());
        require(group.blueCenter.minus(blueStart).dot(left) > 0.28,
                "C: the rotating duo must visibly contour the pressure.");
        require(group.redCenter.minus(group.blueCenter).hypotenuse() < initialDistance - 0.10,
                "C: PRESS_FORWARD must reduce range without replacing the rotation.");
    }

    private void verifyDVisibleIndividualHold() {
        var simulation = fixture(704L);
        simulation.forceTeamManeuver(Team.BLUE, TeamManeuverType.ROTATE_RIGHT);
        var group = simulation.modernGroupEngagement().group;
        var inner = group.blueManeuver.innerPlayer;
        var outer = group.blueManeuver.outerPlayer;
        inner.localManeuver = new LocalManeuver(LocalManeuverType.HOLD_POSITION, inner.pos,
                outer, inner.primaryMark, simulation.simulationTime, 0.30, 0.42,
                1, "controlled_visible_hold", false);
        var innerStart = inner.pos;
        var outerStart = outer.pos;
        for (var tick = 0; tick < 36; ++tick) {
            suppressDecisions(simulation);
            simulation.update(DT);
        }
        require(inner.pos.minus(innerStart).hypotenuse() < 0.075 &&
                        outer.pos.minus(outerStart).hypotenuse() > 0.09,
                "D: an intentional inner HOLD must be visible while the outer player circulates.");
        for (var tick = 0; tick < 96; ++tick) {
            suppressDecisions(simulation);
            simulation.update(DT);
        }
        require(inner.pos.minus(innerStart).hypotenuse() > 0.10,
                "D: after the hold, the inner player must naturally rejoin the maneuver.");
    }

    private void verifyEWeaponLaneOverridesTeamMotion() {
        var simulation = fixture(705L);
        simulation.forceTeamManeuver(Team.BLUE, TeamManeuverType.ROTATE_RIGHT);
        var group = simulation.modernGroupEngagement().group;
        var defender = simulation.blueTeam[0];
        var attacker = defender.primaryMark;
        defender.pos = new Pos(3.40, 3.30);
        attacker.pos = new Pos(4.90, 3.30);
        attacker.angle = Math.PI;
        attacker.weaponYaw = 0;
        attacker.state = CombatState.ATTACK_HOLD;
        var awareness = defender.opponentAwareness.get(attacker);
        awareness.visible = true;
        awareness.lastKnown = attacker.pos;
        awareness.observedBodyAngle = attacker.angle;
        awareness.observedWeaponAngle = attacker.weaponWorldAngle();
        awareness.observedProjectedRange = attacker.projectedThrustLength();
        awareness.observedState = attacker.state;
        awareness.observedStateTime = attacker.stateTime;
        awareness.observedAttackDirection = attacker.attackDirection;
        awareness.memoryTime = 2;
        defender.localManeuver = null;
        simulation.refreshLocalManeuver(defender);
        require(group.blueManeuver.type == TeamManeuverType.ROTATE_RIGHT &&
                        defender.localManeuver.type == LocalManeuverType.AVOID_PRIMARY_WEAPON_LINE,
                "E: the team rotation must persist while local weapon avoidance overrides it.");
    }

    private void verifyFShapeAndShiftManeuvers() {
        var shift = fixture(706L);
        var shiftGroup = shift.modernGroupEngagement().group;
        var shiftStart = shiftGroup.blueCenter;
        shift.forceTeamManeuver(Team.BLUE, TeamManeuverType.SHIFT_LEFT);
        for (var tick = 0; tick < 84; ++tick) {
            suppressDecisions(shift);
            shift.update(DT);
        }
        shiftGroup.updateCenters();
        require(Math.abs(shiftGroup.blueCenter.y() - shiftStart.y()) > 0.16,
                "F: SHIFT_LEFT must translate the duo without a global orbit (start=" +
                        shiftStart.y() + ", end=" + shiftGroup.blueCenter.y() + ").");

        var compress = fixture(707L);
        reset(compress.blueTeam[0], new Pos(2.80, 2.45), 0);
        reset(compress.blueTeam[1], new Pos(2.80, 4.45), 0);
        var compressStart = compress.blueTeam[0].pos.minus(compress.blueTeam[1].pos).hypotenuse();
        compress.forceTeamManeuver(Team.BLUE, TeamManeuverType.COMPRESS);
        for (var tick = 0; tick < 84; ++tick) {
            suppressDecisions(compress);
            compress.update(DT);
        }
        require(compress.blueTeam[0].pos.minus(compress.blueTeam[1].pos).hypotenuse() <
                        compressStart - 0.12,
                "F: COMPRESS must close a real partner gap.");

        var expand = fixture(708L);
        reset(expand.blueTeam[0], new Pos(2.80, 3.00), 0);
        reset(expand.blueTeam[1], new Pos(2.80, 4.00), 0);
        var expandStart = expand.blueTeam[0].pos.minus(expand.blueTeam[1].pos).hypotenuse();
        expand.forceTeamManeuver(Team.BLUE, TeamManeuverType.EXPAND);
        for (var tick = 0; tick < 84; ++tick) {
            suppressDecisions(expand);
            expand.update(DT);
        }
        require(expand.blueTeam[0].pos.minus(expand.blueTeam[1].pos).hypotenuse() >
                        expandStart + 0.10,
                "F: EXPAND must open two physical partner lines.");
    }

    private void verifyIDynamicFiveSecondScene() {
        var simulation = new DuelSimulation(AgentConfiguration.defaultBlue(),
                AgentConfiguration.defaultBlueTwo(), AgentConfiguration.defaultRed(),
                AgentConfiguration.defaultRedTwo(), SimulationScenario.MODERN_2V2, 709L);
        var minimumSpeed = Double.POSITIVE_INFINITY;
        var maximumSpeed = 0.0;
        for (var tick = 0; tick < 600; ++tick) {
            suppressDecisions(simulation);
            simulation.update(DT);
            for (var agent : simulation.agents) {
                minimumSpeed = Math.min(minimumSpeed, agent.velocity.hypotenuse());
                maximumSpeed = Math.max(maximumSpeed, agent.velocity.hypotenuse());
            }
        }
        var metrics = simulation.modernMetrics();
        require(metrics.teamManeuversStarted >= 2 && metrics.teamLateralDistance > 1.0 &&
                        maximumSpeed - minimumSpeed > 0.18 && metrics.formationAreaVariation > 0.20 &&
                        metrics.partnerDistanceVariation > 0.20,
                "I: five neutral seconds must contain lateral circulation, speed variation, and shape change.");
    }

    private void verifyJDeterminism() {
        var first = fixture(710L);
        var second = fixture(710L);
        for (var tick = 0; tick < 240; ++tick) {
            suppressDecisions(first);
            suppressDecisions(second);
            first.update(DT);
            second.update(DT);
            for (var n = 0; n < first.agents.length; ++n)
                require(first.agents[n].pos.equals(second.agents[n].pos),
                        "J: the same seed must reproduce every team-maneuver position.");
            var firstGroup = first.modernGroupEngagement().group;
            var secondGroup = second.modernGroupEngagement().group;
            require(firstGroup.blueManeuver.type == secondGroup.blueManeuver.type &&
                            firstGroup.redManeuver.type == secondGroup.redManeuver.type,
                    "J: the same seed must reproduce both TeamManeuver choices.");
        }
    }

    private DuelSimulation fixture(long seed) {
        var simulation = new DuelSimulation(AgentConfiguration.defaultBlue(),
                AgentConfiguration.defaultBlueTwo(), AgentConfiguration.defaultRed(),
                AgentConfiguration.defaultRedTwo(), SimulationScenario.MODERN_2V2, seed);
        reset(simulation.blueTeam[0], new Pos(2.80, 2.72), 0);
        reset(simulation.blueTeam[1], new Pos(2.80, 4.20), 0);
        reset(simulation.redTeam[0], new Pos(5.20, 2.72), Math.PI);
        reset(simulation.redTeam[1], new Pos(5.20, 4.20), Math.PI);
        var group = simulation.modernGroupEngagement().group;
        group.phase = GroupPhase.ROTATE;
        group.phaseStartedAt = group.rotationStartedAt = simulation.simulationTime;
        group.updateCenters();
        for (var agent : simulation.agents)
            agent.localManeuver = new LocalManeuver(LocalManeuverType.STEP_LEFT, agent.pos,
                    null, null, simulation.simulationTime, 2.0, 3.0, 0,
                    "controlled_team_component", false);
        return simulation;
    }

    private void suppressDecisions(DuelSimulation simulation) {
        for (var agent : simulation.agents) agent.decisionTime = 100;
    }

    private void reset(Musketeer agent, Pos position, double angle) {
        agent.pos = position;
        agent.previous = position;
        agent.velocity = new Pos();
        agent.angle = angle;
        agent.state = CombatState.NEUTRAL;
        agent.stateTime = 0;
        agent.localManeuver = null;
    }

    private void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
