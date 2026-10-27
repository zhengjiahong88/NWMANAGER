final class Experimental3v3Test {
    private static final double DT = 1.0 / 120.0;

    public static void main(String[] args) {
        var test = new Experimental3v3Test();
        test.verifyASingleGroupAndNoAttacks();
        test.verifyBGeometricRolesAndHysteresis();
        test.verifyCIndependentManeuversAndRotationSpeeds();
        test.verifyDDeterminism();
        System.out.println("Experimental GROUP_3V3 milestone-1 tests A-D passed.");
    }

    private void verifyASingleGroupAndNoAttacks() {
        var simulation = fixture(3_101L);
        require(simulation.engagements().size() == 1 &&
                        simulation.engagements().getFirst().type == EngagementType.GROUP_3V3,
                "A: six players must start inside one GROUP_3V3, not three duels.");
        for (var tick = 0; tick < 600; ++tick) simulation.update(DT);
        for (var agent : simulation.agents)
            require(agent.alive && agent.hitPoints == 100 && agent.state == CombatState.NEUTRAL,
                    "A: milestone 1 must validate movement with every 3v3 attack disabled.");
        require(simulation.group3Metrics().groupEngagementsCreated == 1,
                "A: the scenario must not recreate its collective engagement.");
    }

    private void verifyBGeometricRolesAndHysteresis() {
        var simulation = fixture(3_102L);
        var formation = simulation.modernGroup3Engagement().blueFormation;
        require(formation.leftWing == simulation.blueTeam[0] &&
                        formation.centerPlayer == simulation.blueTeam[1] &&
                        formation.rightWing == simulation.blueTeam[2],
                "B: initial left/center/right roles must come from lateral geometry.");
        var originalLeft = formation.leftWing;
        simulation.blueTeam[0].pos = simulation.blueTeam[0].pos.plus(0, 0.12);
        simulation.blueTeam[1].pos = simulation.blueTeam[1].pos.plus(0, -0.12);
        formation.update(simulation.modernGroup3Engagement().redFormation.center, 0.20, false);
        require(formation.leftWing == originalLeft,
                "B: a short, marginal crossing must not thrash formation roles.");
        simulation.blueTeam[0].pos = simulation.blueTeam[0].pos.plus(0, 1.05);
        simulation.blueTeam[1].pos = simulation.blueTeam[1].pos.plus(0, -1.05);
        formation.update(simulation.modernGroup3Engagement().redFormation.center, 1.0, false);
        require(formation.leftWing == simulation.blueTeam[1] && formation.roleChanges == 1,
                "B: a sustained geometric crossing must eventually reassign roles.");
    }

    private void verifyCIndependentManeuversAndRotationSpeeds() {
        var simulation = fixture(3_103L);
        simulation.forceGroup3Maneuver(Team.BLUE, TeamManeuverType.ROTATE_RIGHT);
        simulation.forceGroup3Maneuver(Team.RED, TeamManeuverType.PRESS_FORWARD);
        var group = simulation.modernGroup3Engagement();
        var initialDistance = group.redFormation.center.minus(group.blueFormation.center).hypotenuse();
        var outerSpeed = 0.0;
        var centerSpeed = 0.0;
        var innerSpeed = 0.0;
        var samples = 0;
        for (var tick = 0; tick < 150; ++tick) {
            simulation.update(DT);
            var maneuver = group.blueManeuver;
            var lateral = group.blueFormation.lateral.times(maneuver.directionSign);
            if (tick < 30) continue;
            outerSpeed += maneuver.outerPlayer.velocity.dot(lateral);
            centerSpeed += group.blueFormation.centerPlayer.velocity.dot(lateral);
            innerSpeed += maneuver.innerPlayer.velocity.dot(lateral);
            ++samples;
        }
        require(group.blueManeuver.type == TeamManeuverType.ROTATE_RIGHT &&
                        group.redManeuver.type == TeamManeuverType.PRESS_FORWARD,
                "C: each team must retain its own persistent maneuver.");
        require(outerSpeed / samples > centerSpeed / samples + 0.04 &&
                        centerSpeed / samples > innerSpeed / samples + 0.04,
                "C: rotation must visibly differentiate outer, center and inner speeds (" +
                        outerSpeed / samples + "/" + centerSpeed / samples + "/" +
                        innerSpeed / samples + ").");
        require(group.redFormation.center.minus(group.blueFormation.center).hypotenuse() <
                        initialDistance - 0.08,
                "C: PRESS_FORWARD must reduce collective range independently of blue rotation.");
    }

    private void verifyDDeterminism() {
        var first = fixture(3_104L);
        var second = fixture(3_104L);
        for (var tick = 0; tick < 480; ++tick) {
            first.update(DT);
            second.update(DT);
        }
        for (var n = 0; n < first.agents.length; ++n)
            require(first.agents[n].pos.minus(second.agents[n].pos).hypotenuse() < 0.000_000_1,
                    "D: identical seeds must reproduce identical 3v3 trajectories.");
    }

    private DuelSimulation fixture(long seed) {
        var blue = new TeamConfiguration3("Blue", AgentConfiguration.defaultBlue(),
                AgentConfiguration.defaultBlueTwo(), AgentConfiguration.defaultBlueThree());
        var red = new TeamConfiguration3("Red", AgentConfiguration.defaultRed(),
                AgentConfiguration.defaultRedTwo(), AgentConfiguration.defaultRedThree());
        var simulation = new DuelSimulation(blue, red, SimulationScenario.CONTROLLED_3V3, seed);
        simulation.disableGroup3AttacksForTest();
        return simulation;
    }

    private void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
