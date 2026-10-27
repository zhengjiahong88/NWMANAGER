final class Group3FirstContactAuditTest {
    private static final double DT = 1.0 / 120.0;

    public static void main(String[] args) {
        var test = new Group3FirstContactAuditTest();
        test.verifyAContactsAreRecordedPerAgentAndStaggered();
        test.verifyBFlatSixPlayerContactIsDiagnosed();
        test.verifyCContactKeepsOneGroupfightController();
        test.verifyDAuditIsDeterministic();
        System.out.println("GROUP_3V3 first-contact/audit milestone-3 tests A-D passed.");
    }

    private void verifyAContactsAreRecordedPerAgentAndStaggered() {
        var simulation = fixture(8_301L);
        simulation.disableGroup3AttacksForTest();
        simulation.update(DT);
        arrangeStaggered(simulation, 1);
        simulation.update(DT);
        var group = simulation.modernGroup3Engagement();
        require(group.approachObservation.firstContacts.size() == 2,
                "A: the first wing opposition must create one event for each physical participant.");
        run(simulation, 0.25);
        arrangeStaggered(simulation, 2);
        simulation.update(DT);
        require(group.approachObservation.firstContacts.size() == 4,
                "A: center contacts must be added without rewriting the wing events.");
        run(simulation, 0.25);
        arrangeStaggered(simulation, 3);
        simulation.update(DT);
        var audit = group.approachObservation.flatFrontAudit;
        require(group.approachObservation.firstContacts.size() == 6 && audit != null &&
                        audit.firstContactSpread() >= 0.45 && !audit.suspicious(),
                "A: staggered wing/center/rear contact must produce a wide, non-flat spread.");
        require(simulation.group3Metrics().simultaneousSixPlayerContacts == 0,
                "A: staggered contacts must not be counted as a simultaneous six-player collision.");
    }

    private void verifyBFlatSixPlayerContactIsDiagnosed() {
        var simulation = fixture(8_302L);
        simulation.disableGroup3AttacksForTest();
        simulation.update(DT);
        arrangeFlat(simulation, 2.0, 4.80);
        simulation.update(DT);
        var group = simulation.modernGroup3Engagement();
        var observation = group.approachObservation;
        var metrics = simulation.group3Metrics();
        require(observation.firstContacts.size() == 6 && observation.flatFrontAudit != null &&
                        observation.flatFrontAudit.suspicious(),
                "B: a simultaneous flat six-player convergence must be diagnosed as suspicious.");
        require(observation.flatFrontAudit.firstContactSpread() <= 0.000001 &&
                        metrics.simultaneousThreePlayerContacts == 1 &&
                        metrics.simultaneousSixPlayerContacts == 1,
                "B: the simultaneous windows and zero spread must be measured explicitly.");
        require(metrics.coveredFirstContacts + metrics.uncoveredFirstContacts == 6 &&
                        metrics.firstContactByRole.getOrDefault(FormationRole3.LEFT, 0) == 2 &&
                        metrics.firstContactByRole.getOrDefault(FormationRole3.CENTER, 0) == 2 &&
                        metrics.firstContactByRole.getOrDefault(FormationRole3.RIGHT, 0) == 2,
                "B: every event must retain coverage and geometric role evidence.");
    }

    private void verifyCContactKeepsOneGroupfightController() {
        var simulation = fixture(8_303L);
        simulation.disableGroup3AttacksForTest();
        simulation.update(DT);
        arrangeFlat(simulation, 2.0, 4.80);
        simulation.update(DT);
        require(simulation.experimentalGroupPhase() == ExperimentalGroupPhase.GROUP_3V3,
                "C: first-contact observation must not replace the GROUP_3V3 controller.");
        for (var engagement : simulation.engagements())
            require(engagement.type != EngagementType.DUEL,
                    "C: measurement must never create fixed duels.");
    }

    private void verifyDAuditIsDeterministic() {
        var first = fixture(8_304L);
        var second = fixture(8_304L);
        first.disableGroup3AttacksForTest();
        second.disableGroup3AttacksForTest();
        run(first, 6.0);
        run(second, 6.0);
        var firstObservation = first.modernGroup3Engagement().approachObservation;
        var secondObservation = second.modernGroup3Engagement().approachObservation;
        require(firstObservation.firstContacts.equals(secondObservation.firstContacts) ||
                        sameContactEvidence(firstObservation, secondObservation),
                "D: equal seeds must reproduce first-contact times, roles and distances.");
        require(firstObservation.flatFrontAudit != null &&
                        firstObservation.flatFrontAudit.equals(secondObservation.flatFrontAudit),
                "D: the diagnostic audit must be exactly deterministic.");
    }

    private boolean sameContactEvidence(Group3ApproachObservation first,
                                        Group3ApproachObservation second) {
        if (first.firstContacts.size() != second.firstContacts.size()) return false;
        for (var firstEvent : first.firstContacts.values()) {
            FirstContactEvent matched = null;
            for (var secondEvent : second.firstContacts.values())
                if (firstEvent.agent().team == secondEvent.agent().team &&
                        firstEvent.agent().name(UiLanguage.FRENCH).
                                equals(secondEvent.agent().name(UiLanguage.FRENCH))) {
                    matched = secondEvent;
                    break;
                }
            if (matched == null || firstEvent.time() != matched.time() ||
                    firstEvent.role() != matched.role() || firstEvent.distance() != matched.distance() ||
                    firstEvent.covered() != matched.covered()) return false;
        }
        return true;
    }

    private void arrangeStaggered(DuelSimulation simulation, int contactRows) {
        for (var n = 0; n < 3; ++n) {
            simulation.blueTeam[n].pos = new Pos(1.0, 1.80 + n * 1.70);
            simulation.redTeam[n].pos = new Pos(n < contactRows ? 3.80 : 6.50,
                    1.80 + n * 1.70);
            simulation.blueTeam[n].velocity = simulation.redTeam[n].velocity = new Pos();
        }
    }

    private void arrangeFlat(DuelSimulation simulation, double blueX, double redX) {
        for (var n = 0; n < 3; ++n) {
            simulation.blueTeam[n].pos = new Pos(blueX, 1.80 + n * 1.70);
            simulation.redTeam[n].pos = new Pos(redX, 1.80 + n * 1.70);
            simulation.blueTeam[n].velocity = simulation.redTeam[n].velocity = new Pos();
        }
    }

    private void run(DuelSimulation simulation, double seconds) {
        for (var n = 0; n < Math.round(seconds / DT); ++n) simulation.update(DT);
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
