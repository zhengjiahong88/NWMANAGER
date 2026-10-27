final class ModernGroup2v2Test {
    private static final double DT = 1.0 / 120.0;

    public static void main(String[] args) {
        var test = new ModernGroup2v2Test();
        test.verifyAStableGroup();
        test.verifyBCommitRejectedByRevengeRisk();
        test.verifyCAllyCoverImprovesCommit();
        test.verifyDBlockCreatesRevenge();
        test.verifyECounterCover();
        test.verifyFRevengeCancellation();
        test.verifyGPhysicalRevenge();
        test.verifyHMissCreatesRevenge();
        test.verifyINoArtificialRevenge();
        test.verifyJRecoverShape();
        test.verifyKDeathCreatesRealTwoVsOne();
        test.verifyLRealIsolation();
        test.verifyMPhysicalCoverPosition();
        System.out.println("Modern GROUP_2V2 controlled tests A-M passed.");
    }

    private void verifyAStableGroup() {
        var simulation = modern(101L);
        for (var tick = 0; tick < 240; ++tick) simulation.update(DT);
        require(active(simulation, EngagementType.GROUP_2V2) == 1 &&
                        active(simulation, EngagementType.DUEL) == 0 &&
                        active(simulation, EngagementType.TWO_VS_ONE) == 0,
                "A: a stable four-player situation must remain one GROUP_2V2.");
        require(simulation.modernMetrics().localManeuversStarted > 0 &&
                        simulation.modernMetrics().coverAssessments > 0 &&
                        simulation.supportPlan() == null,
                "A: the group must maneuver locally and assess cover without ordinary SupportPlan.");
        var group = simulation.modernGroupEngagement().group;
        reset(simulation.blueTeam[0], new Pos(3.0, 2.0), 0);
        reset(simulation.redTeam[1], new Pos(4.0, 2.0), Math.PI);
        reset(simulation.blueTeam[1], new Pos(3.0, 5.0), 0);
        reset(simulation.redTeam[0], new Pos(4.0, 5.0), Math.PI);
        for (var agent : simulation.agents) agent.decisionTime = 1;
        group.phase = GroupPhase.HOLD_SHAPE;
        group.markChangedAt = simulation.simulationTime - 2;
        observeAll(simulation);
        simulation.update(DT);
        require(simulation.modernMetrics().primaryMarkSwitches == 1 &&
                        simulation.blueTeam[0].primaryMark == simulation.redTeam[1],
                "A: a collective geometric crossing must switch both primary marks coherently.");
    }

    private void verifyBCommitRejectedByRevengeRisk() {
        var simulation = modern(102L);
        arrangeCommitFixture(simulation, false);
        var assessment = simulation.evaluateCommit(simulation.redTeam[0],
                simulation.blueTeam[0]);
        require(!assessment.viable() && assessment.revengeRisk() >= 0.42,
                "B: an uncovered attacker must be able to reject a dangerous revenge.");
    }

    private void verifyCAllyCoverImprovesCommit() {
        var uncovered = modern(103L);
        arrangeCommitFixture(uncovered, false);
        var weak = uncovered.evaluateCommit(uncovered.redTeam[0], uncovered.blueTeam[0]);
        var covered = modern(103L);
        arrangeCommitFixture(covered, true);
        var strong = covered.evaluateCommit(covered.redTeam[0], covered.blueTeam[0]);
        require(strong.allyCoverQuality() > weak.allyCoverQuality() &&
                        strong.score() > weak.score(),
                "C: B2's physical cover must improve B1's commit assessment.");
    }

    private void verifyDBlockCreatesRevenge() {
        var simulation = blockFixture(104L, false);
        simulation.onAttackBlocked(simulation.redTeam[0], simulation.blueTeam[0]);
        var group = simulation.modernGroupEngagement().group;
        require(group.revengeOpportunity != null && group.revengeOpportunity.viable() &&
                        group.revenger == simulation.blueTeam[1] &&
                        active(simulation, EngagementType.GROUP_2V2) == 1 &&
                        active(simulation, EngagementType.TWO_VS_ONE) == 0,
                "D: a block must create a revenge inside GROUP_2V2, not a 2v1.");
    }

    private void verifyECounterCover() {
        var simulation = blockFixture(105L, true);
        simulation.onAttackBlocked(simulation.redTeam[0], simulation.blueTeam[0]);
        var group = simulation.modernGroupEngagement().group;
        require(group.counterCoverAssessment != null &&
                        group.counterCoverAssessment.viable() &&
                        group.counterCoverer == simulation.redTeam[1],
                "E: the fourth player must establish a physical counter-cover threat.");
    }

    private void verifyFRevengeCancellation() {
        var simulation = blockFixture(106L, true);
        simulation.onAttackBlocked(simulation.redTeam[0], simulation.blueTeam[0]);
        for (var tick = 0; tick < 50 &&
                simulation.modernMetrics().revengeCancelled == 0; ++tick)
            simulation.update(DT);
        require(simulation.modernMetrics().revengeCancelled > 0,
                "F: a credible early counter-cover must be able to cancel revenge.");
    }

    private void verifyGPhysicalRevenge() {
        var simulation = blockFixture(108L, false);
        reset(simulation.blueTeam[1], new Pos(3.18, 3.82), -0.18);
        observeAll(simulation);
        simulation.onAttackBlocked(simulation.redTeam[0], simulation.blueTeam[0]);
        for (var tick = 0; tick < 180 &&
                simulation.modernMetrics().revengeHits == 0; ++tick)
            simulation.update(DT);
        require(simulation.modernMetrics().revengeCommits > 0 &&
                        simulation.modernMetrics().revengeHits > 0,
                "G: an uncovered revenge must travel through the ordinary physical attack.");
    }

    private void verifyHMissCreatesRevenge() {
        var simulation = blockFixture(109L, false);
        simulation.redTeam[0].state = CombatState.RECOVERY;
        simulation.redTeam[0].target = simulation.blueTeam[0];
        simulation.onAttackMissed(simulation.redTeam[0]);
        require(simulation.modernGroupEngagement().group.revengeOpportunity != null &&
                        simulation.modernGroupEngagement().group.revengeOpportunity.reason() ==
                                RevengeReason.ATTACK_MISSED,
                "H: a missed stab must create a classified revenge opportunity.");
    }

    private void verifyINoArtificialRevenge() {
        var simulation = blockFixture(110L, false);
        reset(simulation.blueTeam[1], new Pos(0.55, 6.35), 0);
        observeAll(simulation);
        simulation.onAttackBlocked(simulation.redTeam[0], simulation.blueTeam[0]);
        var opportunity = simulation.modernGroupEngagement().group.revengeOpportunity;
        require(opportunity != null && !opportunity.viable() &&
                        simulation.modernMetrics().revengeCommits == 0,
                "I: a distant partner must not receive an artificial revenge.");
    }

    private void verifyJRecoverShape() {
        var simulation = blockFixture(111L, false);
        reset(simulation.blueTeam[1], new Pos(0.55, 6.35), 0);
        observeAll(simulation);
        simulation.onAttackBlocked(simulation.redTeam[0], simulation.blueTeam[0]);
        for (var tick = 0; tick < 360; ++tick) simulation.update(DT);
        require(simulation.modernMetrics().recoverShapeTransitions > 0 &&
                        (active(simulation, EngagementType.GROUP_2V2) == 1 ||
                                simulation.winningTeam != null),
                "J: a surviving exchange must pass through RECOVER_SHAPE.");
    }

    private void verifyKDeathCreatesRealTwoVsOne() {
        var simulation = modern(112L);
        kill(simulation.redTeam[0]);
        simulation.update(DT);
        require(active(simulation, EngagementType.GROUP_2V2) == 0 &&
                        active(simulation, EngagementType.TWO_VS_ONE) == 1 &&
                        simulation.modernMetrics().postEliminationTwoVsOne == 1,
                "K: the first death must break the group and create the real 2v1.");
    }

    private void verifyLRealIsolation() {
        var simulation = modern(113L);
        reset(simulation.blueTeam[0], new Pos(3.20, 3.15), 0);
        reset(simulation.redTeam[0], new Pos(4.62, 3.15), Math.PI);
        reset(simulation.blueTeam[1], new Pos(3.35, 4.15), -0.30);
        reset(simulation.redTeam[1], new Pos(7.45, 6.35), Math.PI);
        observeAll(simulation);
        var opportunity = simulation.evaluateSupportOpportunity(simulation.blueTeam[1]);
        require(opportunity.viable() &&
                        opportunity.reason() == SupportReason.SPATIAL_ADVANTAGE,
                "L: only a durable physical isolation may create isolation support.");
        for (var tick = 0; tick < 90 &&
                simulation.modernMetrics().preEliminationIsolationTwoVsOne == 0; ++tick) {
            reset(simulation.redTeam[1], new Pos(7.45, 6.35), Math.PI);
            simulation.update(DT);
        }
        require(simulation.modernMetrics().preEliminationIsolationTwoVsOne > 0,
                "L: real isolation must exceptionally form a pre-elimination 2v1.");
    }

    private void verifyMPhysicalCoverPosition() {
        var simulation = modern(114L);
        var protectedAlly = simulation.blueTeam[0];
        var coverer = simulation.blueTeam[1];
        var enemy = simulation.redTeam[0];
        reset(protectedAlly, new Pos(3.00, 3.10), 0);
        reset(enemy, new Pos(4.46, 3.10), Math.PI);
        reset(coverer, new Pos(3.14, 4.16), enemy.pos.minus(new Pos(3.14, 4.16)).radians());
        observeAll(simulation);
        var correct = simulation.evaluateCoverPosition(protectedAlly, coverer, enemy);
        reset(coverer, new Pos(0.55, 6.30), 0);
        observeAll(simulation);
        var poor = simulation.evaluateCoverPosition(protectedAlly, coverer, enemy);
        require(correct.physicallyCovering() && correct.etaMargin() >= 0 &&
                        !poor.physicallyCovering() && poor.etaMargin() < correct.etaMargin(),
                "M: cover must depend on physical ETA and line geometry, not a symbolic role.");
    }

    private void arrangeCommitFixture(DuelSimulation simulation, boolean allyCover) {
        reset(simulation.blueTeam[0], new Pos(3.00, 3.10), 0);
        reset(simulation.blueTeam[1], new Pos(3.18, 4.24), -0.35);
        reset(simulation.redTeam[0], new Pos(4.48, 3.10), Math.PI);
        reset(simulation.redTeam[1], allyCover ?
                new Pos(4.34, 4.12) : new Pos(7.20, 6.15), Math.PI);
        observeAll(simulation);
    }

    private DuelSimulation blockFixture(long seed, boolean counterCover) {
        var simulation = modern(seed);
        reset(simulation.blueTeam[0], new Pos(3.00, 3.10), 0);
        reset(simulation.blueTeam[1], new Pos(3.20, 4.20), -0.30);
        reset(simulation.redTeam[0], new Pos(4.46, 3.10), Math.PI);
        reset(simulation.redTeam[1], counterCover ?
                new Pos(4.42, 4.18) : new Pos(7.25, 6.20), Math.PI);
        simulation.redTeam[0].state = CombatState.BLOCK_STUN;
        simulation.redTeam[0].stateTime = 0;
        simulation.redTeam[0].target = simulation.blueTeam[0];
        observeAll(simulation);
        return simulation;
    }

    private void observeAll(DuelSimulation simulation) {
        for (var observer : simulation.agents)
            for (var opponent : simulation.agents) {
                if (observer.team == opponent.team) continue;
                var awareness = observer.opponentAwareness.get(opponent);
                awareness.visible = true;
                awareness.lastKnown = opponent.pos;
                awareness.perceivedVelocity = opponent.velocity;
                awareness.observedBodyAngle = opponent.angle;
                awareness.observedWeaponAngle = opponent.weaponWorldAngle();
                awareness.observedProjectedRange = opponent.projectedThrustLength();
                awareness.observedState = opponent.state;
                awareness.observedStateTime = opponent.stateTime;
                awareness.observedAttackDirection = opponent.attackDirection;
                awareness.memoryTime = 2;
            }
    }

    private void reset(Musketeer agent, Pos position, double angle) {
        agent.pos = position;
        agent.previous = position;
        agent.velocity = new Pos();
        agent.angle = angle;
        agent.weaponYaw = agent.desiredWeaponYaw = 0;
        agent.state = CombatState.NEUTRAL;
        agent.stateTime = 0;
        agent.decisionTime = 0;
        agent.attackResolved = false;
    }

    private void kill(Musketeer agent) {
        agent.alive = false;
        agent.hitPoints = 0;
        agent.state = CombatState.DEAD;
    }

    private int active(DuelSimulation simulation, EngagementType type) {
        var count = 0;
        for (var engagement : simulation.engagements())
            if (engagement.type == type &&
                    engagement.status != EngagementStatus.BROKEN &&
                    engagement.status != EngagementStatus.FINISHED)
                ++count;
        return count;
    }

    private DuelSimulation modern(long seed) {
        return new DuelSimulation(AgentConfiguration.defaultBlue(),
                AgentConfiguration.defaultBlueTwo(), AgentConfiguration.defaultRed(),
                AgentConfiguration.defaultRedTwo(), SimulationScenario.MODERN_2V2, seed);
    }

    private void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
