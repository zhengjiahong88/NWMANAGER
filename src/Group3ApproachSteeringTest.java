final class Group3ApproachSteeringTest {
    private static final double DT = 1.0 / 120.0;

    public static void main(String[] args) {
        var test = new Group3ApproachSteeringTest();
        test.verifyAApproachMovesForwardAndLaterally();
        test.verifyBLeadCreatesFormationDepth();
        test.verifyCApproachDominatesFarSteering();
        test.verifyDContactRestoresExistingMovementPriorities();
        test.verifyEDeterministicInitialTrajectories();
        test.verifyFActualVelocityFollowsDistanceBand();
        test.verifyGDistantCoverGapDoesNotForceRecovery();
        System.out.println("GROUP_3V3 approach-steering milestone-2 tests A-G passed.");
    }

    private void verifyAApproachMovesForwardAndLaterally() {
        var simulation = fixture(7_201L);
        prepareControlledPlans(simulation, ApproachPattern.LEFT_LEAD, ApproachPattern.RIGHT_SWEEP);
        var group = simulation.modernGroup3Engagement();
        var blueStart = group.blueFormation.center;
        var blueForward = group.blueFormation.forward;
        var blueLateral = group.blueFormation.lateral;
        run(simulation, 0.90);
        var displacement = group.blueFormation.center.minus(blueStart);
        require(displacement.dot(blueForward) > 0.55,
                "A: FAR_APPROACH must visibly reduce the longitudinal distance.");
        require(Math.abs(displacement.dot(blueLateral)) > 0.16,
                "A: the plan must create visible lateral motion instead of a straight front.");
    }

    private void verifyBLeadCreatesFormationDepth() {
        var simulation = fixture(7_202L);
        prepareControlledPlans(simulation, ApproachPattern.LEFT_LEAD, ApproachPattern.RIGHT_LEAD);
        var group = simulation.modernGroup3Engagement();
        var plan = group.blueApproachPlan;
        var formation = group.blueFormation;
        var leader = formation.member(plan.leadRole);
        var trailing = formation.member(plan.trailingRole);
        var forward = formation.forward;
        var initialDifference = leader.pos.minus(trailing.pos).dot(forward);
        run(simulation, 0.90);
        var finalDifference = leader.pos.minus(trailing.pos).dot(forward);
        require(finalDifference - initialDifference > 0.16 && formation.depth > 0.16,
                "B: the temporary lead must emerge ahead of the trailing role through soft steering.");
    }

    private void verifyCApproachDominatesFarSteering() {
        var simulation = fixture(7_203L);
        prepareControlledPlans(simulation, ApproachPattern.OBLIQUE_PRESS_LEFT,
                ApproachPattern.OBLIQUE_PRESS_RIGHT);
        run(simulation, 0.45);
        var metrics = simulation.group3Metrics();
        require(metrics.approachSteeringSamples > 0 &&
                        metrics.approachContributionSum > metrics.teamManeuverContributionSum * 2.5 &&
                        metrics.approachContributionSum > metrics.formationContributionSum * 2.5,
                "C: approach steering must dominate maneuver and formation corrections while far away.");
    }

    private void verifyDContactRestoresExistingMovementPriorities() {
        var simulation = fixture(7_204L);
        prepareControlledPlans(simulation, ApproachPattern.LEFT_SWEEP, ApproachPattern.RIGHT_SWEEP);
        arrangeX(simulation, 2.70, 5.50);
        simulation.update(DT);
        var group = simulation.modernGroup3Engagement();
        var previousApproachSum = simulation.group3Metrics().approachContributionSum;
        require(group.contactBand == Group3ContactBand.CONTACT &&
                        group.blueApproachPlan == null && group.redApproachPlan == null,
                "D: contact must end approach guidance without replacing the combat controller.");
        run(simulation, 0.20);
        require(simulation.group3Metrics().approachContributionSum == previousApproachSum,
                "D: no approach velocity may remain once CONTACT restores existing priorities.");
    }

    private void verifyEDeterministicInitialTrajectories() {
        var first = fixture(7_205L);
        var second = fixture(7_205L);
        first.disableGroup3AttacksForTest();
        second.disableGroup3AttacksForTest();
        run(first, 1.10);
        run(second, 1.10);
        for (var n = 0; n < first.agents.length; ++n) {
            require(first.agents[n].pos.equals(second.agents[n].pos) &&
                            first.agents[n].velocity.equals(second.agents[n].velocity),
                    "E: identical seeds must reproduce every initial position and velocity.");
        }
    }

    private void verifyFActualVelocityFollowsDistanceBand() {
        var far = fixture(7_206L);
        prepareControlledPlans(far, ApproachPattern.OBLIQUE_PRESS_LEFT,
                ApproachPattern.OBLIQUE_PRESS_RIGHT);
        arrangeX(far, 0.90, 7.10);
        run(far, 0.45);
        var farFormation = far.modernGroup3Engagement().blueFormation;
        var farForward = teamVelocity(far.blueTeam).dot(farFormation.forward);
        var farLateral = Math.abs(teamVelocity(far.blueTeam).dot(farFormation.lateral));
        require(farForward >= 0.72 && farForward <= 1.16 &&
                        farLateral >= 0.22 && farLateral <= 0.70,
                "F: actual FAR velocity must follow the intended oblique range (forward=" +
                        farForward + ", lateral=" + farLateral + ").");

        var space = fixture(7_207L);
        prepareControlledPlans(space, ApproachPattern.OBLIQUE_PRESS_LEFT,
                ApproachPattern.OBLIQUE_PRESS_RIGHT);
        arrangeX(space, 2.20, 6.20);
        run(space, 0.45);
        var spaceFormation = space.modernGroup3Engagement().blueFormation;
        var spaceForward = teamVelocity(space.blueTeam).dot(spaceFormation.forward);
        var spaceLateral = Math.abs(teamVelocity(space.blueTeam).dot(spaceFormation.lateral));
        require(space.modernGroup3Engagement().contactBand == Group3ContactBand.SPACE_CONTEST &&
                        spaceForward >= 0.34 && spaceForward <= 0.92 &&
                        spaceLateral >= 0.30 && spaceLateral <= 0.86,
                "F: SPACE_CONTEST must reduce forward propulsion while preserving lateral motion " +
                        "(forward=" + spaceForward + ", lateral=" + spaceLateral + ").");
        require(spaceForward < farForward,
                "F: forward propulsion must decrease progressively before CONTACT (far=" +
                        farForward + ", space=" + spaceForward + ").");
    }

    private void verifyGDistantCoverGapDoesNotForceRecovery() {
        var simulation = fixture(7_208L);
        simulation.disableGroup3AttacksForTest();
        simulation.update(DT);
        var group = simulation.modernGroup3Engagement();
        group.blueFormation.leftWing.pos = group.blueFormation.leftWing.pos.plus(0, -0.55);
        group.maneuver(Team.BLUE, null);
        simulation.update(DT);
        require(group.contactBand == Group3ContactBand.FAR_APPROACH &&
                        group.blueApproachPlan != null &&
                        group.blueManeuver.type != TeamManeuverType.RECOVER_FORMATION,
                "G: an unexploitable distant cover gap must not cancel the oblique approach through recovery.");
    }

    private void prepareControlledPlans(DuelSimulation simulation, ApproachPattern bluePattern,
                                        ApproachPattern redPattern) {
        simulation.disableGroup3AttacksForTest();
        simulation.update(DT);
        var group = simulation.modernGroup3Engagement();
        group.approachPlan(Team.BLUE, controlledPlan(group, Team.BLUE, bluePattern));
        group.approachPlan(Team.RED, controlledPlan(group, Team.RED, redPattern));
        simulation.forceGroup3Maneuver(Team.BLUE, TeamManeuverType.HOLD_LINE);
        simulation.forceGroup3Maneuver(Team.RED, TeamManeuverType.HOLD_LINE);
    }

    private Group3ApproachPlan controlledPlan(Group3v3Engagement group, Team team,
                                               ApproachPattern pattern) {
        var formation = group.formation(team);
        var enemy = group.enemyFormation(team);
        var sign = switch (pattern) {
            case LEFT_LEAD, LEFT_SWEEP, OBLIQUE_PRESS_LEFT, COUNTER_ROTATE_LEFT -> -1;
            default -> 1;
        };
        var lead = sign < 0 ? FormationRole3.LEFT : FormationRole3.RIGHT;
        var trailing = sign < 0 ? FormationRole3.RIGHT : FormationRole3.LEFT;
        var meeting = formation.center.plus(enemy.center).div(2).
                plus(formation.lateral.times(sign * 0.90));
        return new Group3ApproachPlan(team, pattern, 0, 1.5, 3.2, 5.0,
                lead, FormationRole3.CENTER, trailing, sign, 0.94, 0.48, 0.52,
                formation.center, meeting, "controlled_steering_test");
    }

    private void arrangeX(DuelSimulation simulation, double blueX, double redX) {
        for (var agent : simulation.blueTeam) {
            agent.pos = new Pos(blueX, agent.pos.y());
            agent.velocity = new Pos();
        }
        for (var agent : simulation.redTeam) {
            agent.pos = new Pos(redX, agent.pos.y());
            agent.velocity = new Pos();
        }
    }

    private void run(DuelSimulation simulation, double seconds) {
        for (var n = 0; n < Math.round(seconds / DT); ++n) simulation.update(DT);
    }

    private Pos teamVelocity(Musketeer[] team) {
        var velocity = new Pos();
        for (var member : team) velocity = velocity.plus(member.velocity);
        return velocity.div(team.length);
    }

    private DuelSimulation fixture(long seed) {
        var blue = new TeamConfiguration3("Blue", AgentConfiguration.defaultBlue(),
                AgentConfiguration.defaultBlueTwo(), AgentConfiguration.defaultBlueThree());
        var red = new TeamConfiguration3("Red", AgentConfiguration.defaultRed(),
                AgentConfiguration.defaultRedTwo(), AgentConfiguration.defaultRedThree());
        return new DuelSimulation(blue, red, SimulationScenario.MODERN_3V3, seed);
    }

    private void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
