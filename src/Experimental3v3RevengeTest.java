final class Experimental3v3RevengeTest {
    private static final double DT = 1.0 / 120.0;

    public static void main(String[] args) {
        var test = new Experimental3v3RevengeTest();
        test.verifyAOnePhysicalRevengerIsSelected();
        test.verifyBCoverageAndCounterRiskAreRecorded();
        test.verifyCAtMostOneCounterCovererIsSelected();
        test.verifyDRevengeCannotCreateAnInfiniteChain();
        test.verifyENaturalExchangesAndDeterminism();
        System.out.println("Experimental GROUP_3V3 revenge tests A-E passed.");
    }

    private void verifyAOnePhysicalRevengerIsSelected() {
        var simulation = preparedFixture(4_101L);
        triggerBlockedAttack(simulation);
        var group = simulation.modernGroup3Engagement();
        require(group.revengeCandidate != null,
                "A: a blocked close attack must select one viable revenger.");
        var selected = group.revengeCandidate.revenger();
        var commits = 0;
        for (var agent : simulation.blueTeam)
            if (agent.groupIntent == GroupIntent.COMMIT_REVENGE) ++commits;
        require(commits == 1 && selected.groupIntent == GroupIntent.COMMIT_REVENGE,
                "A: exactly one teammate may commit the revenge.");
    }

    private void verifyBCoverageAndCounterRiskAreRecorded() {
        var simulation = preparedFixture(4_102L);
        triggerBlockedAttack(simulation);
        var candidate = simulation.modernGroup3Engagement().revengeCandidate;
        require(candidate != null && candidate.coverLinksPreserved() >= 0 &&
                        candidate.counterCoverRisk() >= 0 && candidate.counterCoverRisk() <= 1 &&
                        candidate.opportunity().lineSafety() >= 0 &&
                        candidate.opportunity().lineSafety() <= 1,
                "B: selection must expose preserved links, line safety and bounded counter risk.");
    }

    private void verifyCAtMostOneCounterCovererIsSelected() {
        var observed = false;
        for (var seed = 4_110L; seed < 4_140L && !observed; ++seed) {
            var simulation = preparedFixture(seed);
            triggerBlockedAttack(simulation);
            var group = simulation.modernGroup3Engagement();
            if (group.counterCoverAssessment == null) continue;
            observed = true;
            var commits = 0;
            for (var agent : simulation.redTeam)
                if (agent.groupIntent == GroupIntent.COUNTER_COVER) ++commits;
            require(commits <= 1 && group.counterCoverAssessment.viable(),
                    "C: a revenge may nominate at most one viable counter-coverer.");
        }
        require(observed, "C: controlled close lines must expose a counter-cover opportunity.");
    }

    private void verifyDRevengeCannotCreateAnInfiniteChain() {
        var simulation = preparedFixture(4_141L);
        triggerBlockedAttack(simulation);
        var group = simulation.modernGroup3Engagement();
        require(group.revengeCandidate != null, "D: fixture must start a revenge exchange.");
        var revenger = group.revengeCandidate.revenger();
        var blocker = group.revengeCandidate.opportunity().vulnerableAttacker();
        revenger.state = CombatState.BLOCK_STUN;
        simulation.onAttackBlocked(revenger, blocker);
        require(!group.exchangeActive() && simulation.group3Metrics().preventedRevengeChains == 1,
                "D: a blocked revenge must close the exchange instead of creating another revenge.");
    }

    private void verifyENaturalExchangesAndDeterminism() {
        var opportunities = 0;
        var commits = 0;
        var counterCommits = 0;
        for (var seed = 4_150L; seed < 4_170L; ++seed) {
            var simulation = fixture(seed);
            for (var tick = 0; tick < 3_600 && !simulation.experimentalRoundComplete(); ++tick)
                simulation.update(DT);
            opportunities += simulation.group3Metrics().revengeOpportunities;
            commits += simulation.group3Metrics().revengeCommits;
            counterCommits += simulation.group3Metrics().counterCoverCommits;
            require(simulation.group3Metrics().revengeCommits <=
                            simulation.group3Metrics().revengeOpportunities,
                    "E: natural combat cannot commit more revenges than evaluated exchanges.");
        }
        require(opportunities > 0 && commits > 0 && counterCommits > 0,
                "E: natural combat must evaluate and commit revenge exchanges " +
                        "(opportunities=" + opportunities + ", commits=" + commits +
                        ", counters=" + counterCommits + ").");
        var first = fixture(4_199L);
        var second = fixture(4_199L);
        for (var tick = 0; tick < 3_600 &&
                (!first.experimentalRoundComplete() || !second.experimentalRoundComplete()); ++tick) {
            first.update(DT);
            second.update(DT);
        }
        require(first.group3Metrics().revengeOpportunities ==
                        second.group3Metrics().revengeOpportunities &&
                        first.group3Metrics().revengeCommits == second.group3Metrics().revengeCommits &&
                        first.group3Metrics().counterCoverCommits ==
                                second.group3Metrics().counterCoverCommits &&
                        first.experimentalRoundLeader() == second.experimentalRoundLeader(),
                "E: same seed must reproduce revenge, counter-cover and phase result.");
    }

    private DuelSimulation preparedFixture(long seed) {
        var simulation = fixture(seed);
        simulation.disableGroup3AttacksForTest();
        for (var n = 0; n < 3; ++n) {
            simulation.blueTeam[n].pos = new Pos(3.0, 1.7 + n * 1.55);
            simulation.blueTeam[n].angle = 0;
            simulation.redTeam[n].pos = new Pos(4.62, 1.7 + n * 1.55);
            simulation.redTeam[n].angle = Math.PI;
        }
        for (var tick = 0; tick < 180; ++tick) simulation.update(DT);
        return simulation;
    }

    private void triggerBlockedAttack(DuelSimulation simulation) {
        var attacker = simulation.redTeam[1];
        var blocker = simulation.blueTeam[1];
        attacker.target = blocker;
        attacker.state = CombatState.BLOCK_STUN;
        attacker.stateTime = 0;
        blocker.state = CombatState.BLOCKING;
        blocker.stateTime = 0;
        simulation.onAttackBlocked(attacker, blocker);
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
