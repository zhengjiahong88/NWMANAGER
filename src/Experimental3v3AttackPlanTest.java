final class Experimental3v3AttackPlanTest {
    private static final double DT = 1.0 / 120.0;

    public static void main(String[] args) {
        var test = new Experimental3v3AttackPlanTest();
        test.verifyAOpposingCoverSummaryUsesPerceivedLinks();
        test.verifyBAtMostTwoAttackersAndOneCoverer();
        test.verifyCPhysicalResolutionAcrossSeeds();
        test.verifyDRecoveryMovesAway();
        test.verifyEDeterminism();
        test.verifyFTwoHoldsCreateDifferentHeights();
        System.out.println("Experimental GROUP_3V3 attack-plan tests A-F passed.");
    }

    private void verifyAOpposingCoverSummaryUsesPerceivedLinks() {
        var simulation = fixture(3_401L);
        arrangeCloseLines(simulation);
        simulation.disableGroup3AttacksForTest();
        for (var tick = 0; tick < 180; ++tick) simulation.update(DT);
        var attacker = simulation.blueTeam[0];
        var target = attacker.attentionAllocation.primaryThreat;
        var opportunity = simulation.evaluateGroup3AttackOpportunity(attacker, target);
        var summary = simulation.modernGroup3Engagement().opposingCoverSummaries.get(opportunity);
        require(summary != null,
                "A: every evaluated 3v3 opportunity must retain an opposing-cover summary.");
        require(summary.credibleCoverers() == summary.relevantLinks().size() &&
                        summary.combinedRisk() >= 0 && summary.combinedRisk() <= 1,
                "A: opposing cover must be a bounded summary of perceived physical links.");
        for (var link : summary.relevantLinks())
            require(link.protectedAlly() == target && link.threateningEnemy() == attacker && link.viable(),
                    "A: unrelated or non-viable links must not enter the opposing summary.");
    }

    private void verifyBAtMostTwoAttackersAndOneCoverer() {
        var doublePlanObserved = false;
        for (var seed = 3_410L; seed < 3_430L && !doublePlanObserved; ++seed) {
            var simulation = fixture(seed);
            arrangeCloseLines(simulation);
            for (var tick = 0; tick < 900 && !simulation.experimentalFirstEliminationObserved(); ++tick) {
                simulation.update(DT);
                var group = simulation.modernGroup3Engagement();
                for (var team : new Team[]{Team.BLUE, Team.RED}) {
                    var plan = group.attackPlan(team);
                    if (plan == null) continue;
                    require(plan.selectedAttackers().size() <= 2,
                            "B: a 3v3 plan must never select three attackers.");
                    if (plan.selectedAttackers().size() == 2) {
                        require(plan.coveringMembers().size() == 1 &&
                                        plan.opportunities().get(0).target() !=
                                                plan.opportunities().get(1).target(),
                                "B: a double plan requires distinct targets and one remaining coverer.");
                        doublePlanObserved = true;
                    }
                }
            }
            require(simulation.group3Metrics().tripleAttackerPlans == 0,
                    "B: tripleAttackerPlans must remain exactly zero.");
        }
        require(doublePlanObserved,
                "B: controlled close formations must allow at least one safe double attack.");
    }

    private void verifyCPhysicalResolutionAcrossSeeds() {
        var completed = 0;
        var hits = 0;
        var blocks = 0;
        for (var seed = 3_450L; seed < 3_470L; ++seed) {
            var simulation = fixture(seed);
            for (var tick = 0; tick < 3_600 && !simulation.experimentalFirstEliminationObserved(); ++tick)
                simulation.update(DT);
            if (simulation.experimentalFirstEliminationObserved()) ++completed;
            hits += simulation.group3Metrics().attackHits;
            blocks += simulation.group3Metrics().attackBlocks;
            require(simulation.group3Metrics().tripleAttackerPlans == 0,
                    "C: no natural seed may produce a triple attack plan.");
        }
        require(completed >= 16 && hits > 0 && blocks > 0,
                "C: attacks must resolve physically into hits, blocks and first eliminations (completed=" +
                        completed + ", hits=" + hits + ", blocks=" + blocks + ").");
    }

    private void verifyDRecoveryMovesAway() {
        var simulation = fixture(3_480L);
        simulation.disableGroup3AttacksForTest();
        var attacker = simulation.blueTeam[1];
        var target = simulation.redTeam[1];
        attacker.target = target;
        attacker.state = CombatState.RECOVERY;
        attacker.stateTime = 0;
        attacker.velocity = new Pos();
        var direction = target.pos.minus(attacker.pos);
        direction = direction.div(direction.hypotenuse());
        simulation.update(DT);
        require(attacker.velocity.dot(direction) < 0,
                "D: attack recovery must create velocity away from the target.");
    }

    private void verifyEDeterminism() {
        var first = fixture(3_490L);
        var second = fixture(3_490L);
        for (var tick = 0; tick < 3_600 &&
                (!first.experimentalFirstEliminationObserved() ||
                        !second.experimentalFirstEliminationObserved()); ++tick) {
            first.update(DT);
            second.update(DT);
        }
        require(first.experimentalFirstEliminationObserved() ==
                        second.experimentalFirstEliminationObserved() &&
                        first.group3Metrics().attackCommits == second.group3Metrics().attackCommits &&
                        first.group3Metrics().attackHits == second.group3Metrics().attackHits &&
                        first.group3Metrics().attackKills == second.group3Metrics().attackKills,
                "E: same seed must reproduce plans, impacts and the first elimination.");
    }

    private void verifyFTwoHoldsCreateDifferentHeights() {
        var simulation = fixture(3_491L);
        var target = simulation.redTeam[1];
        target.state = CombatState.BLOCKING;
        target.blockDirection = BlockDirection.UP;
        for (var n = 0; n < 2; ++n) {
            var holder = simulation.blueTeam[n];
            holder.target = target;
            holder.state = CombatState.ATTACK_HOLD;
            holder.stateTime = 0.80;
            holder.attackDirection = CombatDirection.UP;
            holder.attackIntent = AttackIntent.HOLD;
        }
        simulation.update(DT);
        require(simulation.blueTeam[0].attackDirection != simulation.blueTeam[1].attackDirection &&
                        simulation.blueTeam[1].state == CombatState.ATTACK_PREPARE,
                "F: the second Hold must change height while the first keeps the guard occupied.");
    }

    private void arrangeCloseLines(DuelSimulation simulation) {
        for (var n = 0; n < 3; ++n) {
            simulation.blueTeam[n].pos = new Pos(3.0, 1.8 + n * 1.7);
            simulation.blueTeam[n].angle = 0;
            simulation.redTeam[n].pos = new Pos(4.72, 1.8 + n * 1.7);
            simulation.redTeam[n].angle = Math.PI;
        }
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
