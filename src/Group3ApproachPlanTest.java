final class Group3ApproachPlanTest {
    private static final double DT = 1.0 / 120.0;

    public static void main(String[] args) {
        var test = new Group3ApproachPlanTest();
        test.verifyAPlansContainLateralMeetingPointAndDistinctRoles();
        test.verifyBPlansPersistBeyondFastEvaluationIntervals();
        test.verifyCSelectionIsDeterministicAndIndependent();
        test.verifyDContactBandsUseHysteresis();
        test.verifyEContactEndsApproachWithoutCreatingDuels();
        System.out.println("GROUP_3V3 approach-plan milestone-1 tests A-E passed.");
    }

    private void verifyAPlansContainLateralMeetingPointAndDistinctRoles() {
        var simulation = fixture(6_101L);
        simulation.disableGroup3AttacksForTest();
        simulation.update(DT);
        var group = simulation.modernGroup3Engagement();
        require(group.contactBand == Group3ContactBand.FAR_APPROACH,
                "A: the initial separated geometry must be FAR_APPROACH.");
        for (var team : Team.values()) {
            var plan = group.approachPlan(team);
            var formation = group.formation(team);
            var enemy = group.enemyFormation(team);
            var midpoint = formation.center.plus(enemy.center).div(2);
            require(plan != null && plan.leadRole != plan.supportRole &&
                            plan.leadRole != plan.trailingRole && plan.supportRole != plan.trailingRole,
                    "A: every plan must assign distinct temporary lead, support and trailing roles.");
            require(plan.predictedMeetingPoint.minus(midpoint).hypotenuse() >= 0.45 &&
                            plan.minimumDuration >= 1.2 && plan.maximumDuration >= 4.0,
                    "A: the meeting point must be visibly offset and the plan persistent.");
        }
    }

    private void verifyBPlansPersistBeyondFastEvaluationIntervals() {
        var simulation = fixture(6_102L);
        simulation.disableGroup3AttacksForTest();
        simulation.update(DT);
        var group = simulation.modernGroup3Engagement();
        var bluePlan = group.blueApproachPlan;
        var redPlan = group.redApproachPlan;
        for (var tick = 0; tick < 90; ++tick) simulation.update(DT);
        require(group.blueApproachPlan == bluePlan && group.redApproachPlan == redPlan,
                "B: plans must not be recalculated on 0.15-second perception intervals.");
        require(simulation.group3Metrics().approachPlansStarted == 2,
                "B: one persistent opening plan per team is sufficient.");
    }

    private void verifyCSelectionIsDeterministicAndIndependent() {
        var first = fixture(6_103L);
        var second = fixture(6_103L);
        first.disableGroup3AttacksForTest();
        second.disableGroup3AttacksForTest();
        first.update(DT);
        second.update(DT);
        var firstGroup = first.modernGroup3Engagement();
        var secondGroup = second.modernGroup3Engagement();
        require(samePlan(firstGroup.blueApproachPlan, secondGroup.blueApproachPlan) &&
                        samePlan(firstGroup.redApproachPlan, secondGroup.redApproachPlan),
                "C: the same seed must reproduce patterns, roles, durations and meeting points.");
        var blue = firstGroup.blueApproachPlan;
        var red = firstGroup.redApproachPlan;
        require(blue.pattern != red.pattern || blue.lateralSign != red.lateralSign ||
                        Math.abs(blue.preferredDuration - red.preferredDuration) > 0.000001,
                "C: both teams must make independent seeded choices instead of mirrored plans.");
    }

    private void verifyDContactBandsUseHysteresis() {
        var simulation = fixture(6_104L);
        simulation.disableGroup3AttacksForTest();
        arrangeX(simulation, 0.8, 7.2);
        simulation.update(DT);
        var group = simulation.modernGroup3Engagement();
        require(group.contactBand == Group3ContactBand.FAR_APPROACH,
                "D: widely separated teams must remain in FAR_APPROACH.");
        arrangeX(simulation, 2.0, 6.0);
        simulation.update(DT);
        require(group.contactBand == Group3ContactBand.SPACE_CONTEST,
                "D: medium center distance must enter SPACE_CONTEST.");
        arrangeX(simulation, 2.7, 5.5);
        simulation.update(DT);
        require(group.contactBand == Group3ContactBand.CONTACT,
                "D: an opposition below 3.1 m must enter CONTACT.");
        arrangeX(simulation, 2.6, 5.75);
        simulation.update(DT);
        require(group.contactBand == Group3ContactBand.CONTACT,
                "D: a small separation around the threshold must not immediately leave CONTACT.");
    }

    private void verifyEContactEndsApproachWithoutCreatingDuels() {
        var simulation = fixture(6_105L);
        simulation.disableGroup3AttacksForTest();
        simulation.update(DT);
        var group = simulation.modernGroup3Engagement();
        require(group.blueApproachPlan != null && group.redApproachPlan != null,
                "E: opening plans must exist before contact.");
        arrangeX(simulation, 2.7, 5.5);
        simulation.update(DT);
        require(group.blueApproachPlan == null && group.redApproachPlan == null &&
                        simulation.experimentalGroupPhase() == ExperimentalGroupPhase.GROUP_3V3,
                "E: contact ends approach guidance but keeps all six Agents in GROUP_3V3 (band=" +
                        group.contactBand + ", bluePlan=" + group.blueApproachPlan + ", redPlan=" +
                        group.redApproachPlan + ", phase=" + simulation.experimentalGroupPhase() + ").");
        for (var engagement : simulation.engagements())
            require(engagement.type != EngagementType.DUEL,
                    "E: the first contact must never create a fixed duel.");
    }

    private boolean samePlan(Group3ApproachPlan first, Group3ApproachPlan second) {
        return first.pattern == second.pattern && first.leadRole == second.leadRole &&
                first.supportRole == second.supportRole && first.trailingRole == second.trailingRole &&
                first.lateralSign == second.lateralSign &&
                first.predictedMeetingPoint.equals(second.predictedMeetingPoint) &&
                first.minimumDuration == second.minimumDuration &&
                first.preferredDuration == second.preferredDuration &&
                first.maximumDuration == second.maximumDuration;
    }

    private void arrangeX(DuelSimulation simulation, double blueX, double redX) {
        for (var agent : simulation.blueTeam) agent.pos = new Pos(blueX, agent.pos.y());
        for (var agent : simulation.redTeam) agent.pos = new Pos(redX, agent.pos.y());
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
