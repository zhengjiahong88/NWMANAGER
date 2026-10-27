final class ModernSupportPlanTest {
    private static final double DT = 1.0 / 120.0;

    public static void main(String[] args) {
        var test = new ModernSupportPlanTest();
        test.verifyBlockStunIsRevengeNotSupport();
        test.verifyRecoveryIsRevengeNotSupport();
        test.verifyNeutralGroupHasNoSupport();
        test.verifyDurableIsolationCanCreateSupport();
        test.verifyIsolationCancellationRestoresGroup();
        test.verifyDeathUsesPostEliminationMetric();
        System.out.println("Modern isolation-support tests passed.");
    }

    private void verifyBlockStunIsRevengeNotSupport() {
        var simulation = compact(201L);
        simulation.redTeam[1].state = CombatState.BLOCK_STUN;
        simulation.redTeam[1].stateTime = 0;
        observeAll(simulation);
        var opportunity = simulation.evaluateSupportOpportunity(simulation.blueTeam[1]);
        require(!opportunity.viable() &&
                        "not_durably_isolated".equals(opportunity.rejectionReason()),
                "BLOCK_STUN alone must no longer create a flank SupportPlan.");
    }

    private void verifyRecoveryIsRevengeNotSupport() {
        var simulation = compact(202L);
        simulation.redTeam[1].state = CombatState.RECOVERY;
        simulation.redTeam[1].stateTime = 0;
        observeAll(simulation);
        require(!simulation.evaluateSupportOpportunity(simulation.blueTeam[1]).viable(),
                "Ordinary recovery must remain a revenge window inside GROUP_2V2.");
    }

    private void verifyNeutralGroupHasNoSupport() {
        var simulation = compact(203L);
        for (var tick = 0; tick < 180; ++tick) simulation.update(DT);
        require(simulation.supportPlan() == null &&
                        simulation.modernMetrics().preEliminationIsolationTwoVsOne == 0,
                "A compact neutral group must not invent isolation support.");
    }

    private void verifyDurableIsolationCanCreateSupport() {
        var simulation = isolated(204L);
        var opportunity = simulation.evaluateSupportOpportunity(simulation.blueTeam[1]);
        require(opportunity.viable() &&
                        opportunity.reason() == SupportReason.SPATIAL_ADVANTAGE,
                "A durable separation must create a spatial isolation opportunity.");
        for (var tick = 0; tick < 90 &&
                simulation.modernMetrics().preEliminationIsolationTwoVsOne == 0; ++tick) {
            pinIsolationFixture(simulation);
            simulation.update(DT);
        }
        require(simulation.modernMetrics().preEliminationIsolationTwoVsOne == 1 &&
                        active(simulation, EngagementType.TWO_VS_ONE) == 1,
                "Isolation support must form an exceptional pre-elimination 2v1.");
    }

    private void verifyIsolationCancellationRestoresGroup() {
        var simulation = isolated(205L);
        for (var tick = 0; tick < 36 && simulation.supportPlan() == null; ++tick) {
            pinIsolationFixture(simulation);
            simulation.update(DT);
        }
        require(simulation.supportPlan() != null,
                "The controlled isolation must start a persistent SupportPlan.");
        simulation.blueTeam[1].state = CombatState.BLOCK_STUN;
        simulation.update(DT);
        require(simulation.supportPlan() == null &&
                        active(simulation, EngagementType.GROUP_2V2) == 1,
                "A dangerous departure must cancel support and restore GROUP_2V2.");
    }

    private void verifyDeathUsesPostEliminationMetric() {
        var simulation = compact(206L);
        simulation.redTeam[0].alive = false;
        simulation.redTeam[0].hitPoints = 0;
        simulation.redTeam[0].state = CombatState.DEAD;
        simulation.update(DT);
        var metrics = simulation.modernMetrics();
        require(metrics.postEliminationTwoVsOne == 1 &&
                        metrics.preEliminationIsolationTwoVsOne == 0,
                "Post-death and pre-elimination isolation 2v1 must remain separate.");
    }

    private DuelSimulation compact(long seed) {
        var simulation = modern(seed);
        reset(simulation.blueTeam[0], new Pos(3.00, 3.00), 0);
        reset(simulation.blueTeam[1], new Pos(3.05, 4.35), 0);
        reset(simulation.redTeam[0], new Pos(4.50, 3.00), Math.PI);
        reset(simulation.redTeam[1], new Pos(4.55, 4.35), Math.PI);
        observeAll(simulation);
        return simulation;
    }

    private DuelSimulation isolated(long seed) {
        var simulation = compact(seed);
        pinIsolationFixture(simulation);
        observeAll(simulation);
        return simulation;
    }

    private void pinIsolationFixture(DuelSimulation simulation) {
        reset(simulation.blueTeam[0], new Pos(3.20, 3.15), 0);
        reset(simulation.redTeam[0], new Pos(4.62, 3.15), Math.PI);
        reset(simulation.blueTeam[1], new Pos(3.35, 4.15), -0.30);
        reset(simulation.redTeam[1], new Pos(7.45, 6.35), Math.PI);
        observeAll(simulation);
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
