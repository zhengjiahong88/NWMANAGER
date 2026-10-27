public final class AttackOpportunityTest {
    private static final double DT = 1.0 / 120.0;

    public static void main(String[] args) {
        verifyTargetFocusedOnAlly();
        verifyInsufficientFacingDifferenceIsNotFocusedOnAlly();
        verifyTargetAttackCommitted();
        verifyRealLaneEntry();
        verifyMovementNoiseDoesNotCreateLaneEntry();
        verifyMovingIntoLineHysteresis();
        verifyOpposingCoverRejectsAttack();
        verifyRiskyDecisionCanBeRevengedPhysically();
        System.out.println("Attack opportunity evidence tests B-H passed.");
    }

    private static void verifyTargetFocusedOnAlly() {
        var simulation = modern(201L);
        var a1 = simulation.blueTeam[0];
        var a2 = simulation.blueTeam[1];
        var b1 = simulation.redTeam[0];
        reset(a1, new Pos(3.00, 3.00), 0);
        reset(a2, new Pos(3.00, 4.10), b1.pos.minus(new Pos(3.00, 4.10)).radians());
        reset(b1, new Pos(4.45, 3.05), a1.pos.minus(new Pos(4.45, 3.05)).radians());
        reset(simulation.redTeam[1], new Pos(7.30, 6.20), Math.PI);
        a2.primaryMark = a2.target = b1;
        observeAll(simulation);
        var opportunity = simulation.evaluateAttackOpportunity(a2, b1);
        require(opportunity.primaryReason() == AttackOpportunityReason.TARGET_FOCUSED_ON_ALLY,
                "E: A2 must identify B1's physical focus on A1.");
        require(opportunity.evidence().targetWeaponErrorToAlly() <
                        opportunity.evidence().targetWeaponErrorToAttacker(),
                "B: frozen evidence must show B1's weapon closer to A1 than A2.");
    }

    private static void verifyInsufficientFacingDifferenceIsNotFocusedOnAlly() {
        var simulation = modern(205L);
        var a1 = simulation.blueTeam[0];
        var a2 = simulation.blueTeam[1];
        var b1 = simulation.redTeam[0];
        reset(a1, new Pos(3.00, 3.00), 0);
        reset(a2, new Pos(3.00, 4.00), 0);
        reset(b1, new Pos(4.45, 3.50), Math.PI);
        reset(simulation.redTeam[1], new Pos(4.55, 4.75), Math.PI);
        var midpoint = a1.pos.plus(a2.pos).div(2);
        b1.angle = midpoint.minus(b1.pos).radians();
        a2.primaryMark = a2.target = b1;
        observeAll(simulation);
        var opportunity = simulation.evaluateAttackOpportunity(a2, b1);
        require(opportunity.primaryReason() != AttackOpportunityReason.TARGET_FOCUSED_ON_ALLY,
                "C: a nearly symmetric facing must not be labeled focused on ally.");
    }

    private static void verifyTargetAttackCommitted() {
        var simulation = modern(202L);
        var a1 = simulation.blueTeam[0];
        var a2 = simulation.blueTeam[1];
        var b1 = simulation.redTeam[0];
        reset(a1, new Pos(3.00, 3.00), 0);
        reset(a2, new Pos(3.05, 4.05), b1.pos.minus(new Pos(3.05, 4.05)).radians());
        reset(b1, new Pos(4.45, 3.05), a1.pos.minus(new Pos(4.45, 3.05)).radians());
        reset(simulation.redTeam[1], new Pos(7.30, 6.20), Math.PI);
        b1.state = CombatState.ATTACK_ACTIVE;
        b1.stateTime = 0.02;
        b1.target = a1;
        a2.primaryMark = a2.target = b1;
        observeAll(simulation);
        var opportunity = simulation.evaluateAttackOpportunity(a2, b1);
        require(opportunity.primaryReason() == AttackOpportunityReason.TARGET_ATTACK_COMMITTED,
                "F: a stab physically committed toward A1 must open A2's counter-stab window.");
    }

    private static void verifyOpposingCoverRejectsAttack() {
        var simulation = modern(203L);
        var a1 = simulation.blueTeam[0];
        var a2 = simulation.blueTeam[1];
        var b1 = simulation.redTeam[0];
        var b2 = simulation.redTeam[1];
        reset(a1, new Pos(1.05, 1.00), 0);
        reset(a2, new Pos(3.05, 4.05), b1.pos.minus(new Pos(3.05, 4.05)).radians());
        reset(b1, new Pos(4.45, 3.05), a1.pos.minus(new Pos(4.45, 3.05)).radians());
        reset(b2, new Pos(4.35, 4.30), a2.pos.minus(new Pos(4.35, 4.30)).radians());
        b1.state = CombatState.ATTACK_ACTIVE;
        b1.stateTime = 0.02;
        b1.target = a1;
        a2.primaryMark = a2.target = b1;
        observeAll(simulation);
        var opportunity = simulation.evaluateAttackOpportunity(a2, b1);
        require(opportunity.primaryReason() == AttackOpportunityReason.TARGET_ATTACK_COMMITTED &&
                        !opportunity.viable() && opportunity.rejectionReason() ==
                        AttackOpportunityRejection.OPPOSING_COVER_TOO_FAST,
                "G: B2's physical punish ETA must reject the same opening: " + opportunity);
        require(opportunity.evidence().opposingCoverEta() < opportunity.impactEta() +
                        opportunity.evidence().attackerRecoveryEta(),
                "G: frozen cover ETA must explain why the fourth player can punish.");
    }

    private static void verifyRealLaneEntry() {
        var fixture = movingFixture(206L, new Pos(0, -1.20));
        var opportunity = fixture.simulation.evaluateAttackOpportunity(fixture.attacker, fixture.target);
        require(opportunity.primaryReason() == AttackOpportunityReason.TARGET_MOVING_INTO_LINE,
                "D: a clear crossing into the future bayonet corridor must be identified: " +
                        opportunity);
        require(opportunity.evidence().previousDistanceToThreatLane() >
                        opportunity.evidence().predictedDistanceToThreatLane() &&
                        opportunity.evidence().laneEntrySpeed() > 0.14 &&
                        opportunity.evidence().timeInsideLane() >= 0.06,
                "D: moving-into-line evidence must contain a real geometric entry.");
    }

    private static void verifyMovementNoiseDoesNotCreateLaneEntry() {
        var fixture = movingFixture(207L, new Pos(0, -0.05));
        var opportunity = fixture.simulation.evaluateAttackOpportunity(fixture.attacker, fixture.target);
        require(opportunity.primaryReason() != AttackOpportunityReason.TARGET_MOVING_INTO_LINE,
                "E: a few centimeters of movement must not create a lane-entry opportunity.");
    }

    private static void verifyMovingIntoLineHysteresis() {
        var fixture = movingFixture(208L, new Pos(0, -1.20));
        var first = fixture.simulation.evaluateAttackOpportunity(fixture.attacker, fixture.target);
        require(first.primaryReason() == AttackOpportunityReason.TARGET_MOVING_INTO_LINE,
                "F: fixture must enter the lane before hysteresis can be tested.");
        var awareness = fixture.attacker.opponentAwareness.get(fixture.target);
        awareness.perceivedVelocity = new Pos(0, -0.14);
        fixture.simulation.simulationTime += 0.10;
        var retained = fixture.simulation.evaluateAttackOpportunity(fixture.attacker, fixture.target);
        require(retained.primaryReason() == AttackOpportunityReason.TARGET_MOVING_INTO_LINE,
                "F: the exit threshold must retain a recently proven lane entry.");
        fixture.simulation.simulationTime += 0.30;
        var expired = fixture.simulation.evaluateAttackOpportunity(fixture.attacker, fixture.target);
        require(expired.primaryReason() != AttackOpportunityReason.TARGET_MOVING_INTO_LINE,
                "F: hysteresis must expire instead of becoming a permanent justification.");
    }

    private static MovingFixture movingFixture(long seed, Pos velocity) {
        var simulation = modern(seed);
        var attacker = simulation.blueTeam[0];
        var ally = simulation.blueTeam[1];
        var target = simulation.redTeam[0];
        var coverer = simulation.redTeam[1];
        reset(attacker, new Pos(3.00, 3.00), 0);
        attacker.state = CombatState.ATTACK_HOLD;
        attacker.stateTime = attacker.prepareTime();
        reset(ally, new Pos(2.85, 4.15), target.pos.minus(new Pos(2.85, 4.15)).radians());
        reset(target, new Pos(4.36, 3.28), attacker.pos.minus(new Pos(4.36, 3.28)).radians());
        reset(coverer, new Pos(4.55, 4.72), attacker.pos.minus(new Pos(4.55, 4.72)).radians());
        target.velocity = velocity;
        attacker.primaryMark = attacker.target = target;
        observeAll(simulation);
        return new MovingFixture(simulation, attacker, target);
    }

    private static void verifyRiskyDecisionCanBeRevengedPhysically() {
        var risky = new AgentConfiguration("Risky B1",
                new AgentStats(70, 70, 70, 70, 65, 65, 95, 35,
                        70, 35, 35, 70, 80, 45), Tactic.AGGRESSIVE);
        var simulation = new DuelSimulation(AgentConfiguration.defaultBlue(),
                AgentConfiguration.defaultBlueTwo(), risky,
                AgentConfiguration.defaultRedTwo(), SimulationScenario.MODERN_2V2, 204L);
        var a1 = simulation.blueTeam[0];
        var a2 = simulation.blueTeam[1];
        var b1 = simulation.redTeam[0];
        var b2 = simulation.redTeam[1];
        reset(a1, new Pos(4.45, 3.05), Math.PI);
        reset(a2, new Pos(3.25, 4.02), b1.pos.minus(new Pos(3.25, 4.02)).radians());
        reset(b1, new Pos(3.00, 3.05), 0);
        reset(b2, new Pos(6.95, 6.10), Math.PI);
        a2.angle = b1.pos.minus(a2.pos).radians();
        b1.primaryMark = b1.target = a1;
        observeAll(simulation);
        var opportunity = simulation.evaluateAttackOpportunity(b1, a1);
        require(opportunity.viable() && opportunity.primaryReason() ==
                        AttackOpportunityReason.RISKY_INITIATIVE,
                "H: an aggressive low-decision agent must be able to accept a named risky initiative.");
        b1.attackOpportunity = opportunity;
        b1.groupIntent = GroupIntent.COMMIT_ATTACK;
        b1.state = CombatState.RECOVERY;
        b1.stateTime = 0;
        simulation.onAttackMissed(b1);
        var revenge = simulation.modernGroupEngagement().group.revengeOpportunity;
        for (var tick = 0; tick < 240 && simulation.modernMetrics().revengeHits == 0; ++tick)
            simulation.update(DT);
        require(simulation.modernMetrics().revengeCommits > 0 &&
                        simulation.modernMetrics().revengeHits > 0,
                "H: the dangerous physical miss must be punishable by a real revenge stab: commits=" +
                        simulation.modernMetrics().revengeCommits + " hits=" +
                        simulation.modernMetrics().revengeHits + " opportunity=" +
                        revenge + " winner=" + simulation.winningTeam);
    }

    private static DuelSimulation modern(long seed) {
        return new DuelSimulation(AgentConfiguration.defaultBlue(),
                AgentConfiguration.defaultBlueTwo(), AgentConfiguration.defaultRed(),
                AgentConfiguration.defaultRedTwo(), SimulationScenario.MODERN_2V2, seed);
    }

    private static void observeAll(DuelSimulation simulation) {
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
                awareness.observedBlockDirection = opponent.blockDirection;
                awareness.memoryTime = 2;
            }
    }

    private static void reset(Musketeer agent, Pos position, double angle) {
        agent.pos = agent.previous = position;
        agent.velocity = new Pos();
        agent.angle = angle;
        agent.weaponYaw = agent.desiredWeaponYaw = 0;
        agent.state = CombatState.NEUTRAL;
        agent.stateTime = 0;
        agent.decisionTime = 0;
        agent.attackResolved = false;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    private record MovingFixture(DuelSimulation simulation, Musketeer attacker, Musketeer target) {
    }
}
