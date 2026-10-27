final class TwoVsOneCoordinationTest {
    private static final double DT = 1.0 / 120.0;

    public static void main(String[] args) {
        verifyFixerPrimary();
        verifyFlankerPrimary();
        verifyDifferentHeights();
        verifyBlockedLineRejected();
        verifyDefenderEscapeAborts();
        System.out.println("TWO_VS_ONE coordination tests B-F passed.");
    }

    private static void verifyFixerPrimary() {
        var fixture = fixture(301L, true);
        fixture.simulation.evaluateTwoVsOneCoordination(fixture.engagement);
        var coordination = fixture.engagement.coordination;
        require(coordination.primaryAttacker == fixture.engagement.fixer,
                "B: the credible frontal fixer must be selectable as primary threat.");
        require(coordination.phase == TwoVsOneCoordinationPhase.SECONDARY_RELEASE_WINDOW,
                "B: the defender's physical reaction must open the flanker release window.");
    }

    private static void verifyFlankerPrimary() {
        var fixture = fixture(302L, false);
        var fixer = fixture.engagement.fixer;
        var flanker = fixture.engagement.flanker;
        var defender = fixture.engagement.primaryTarget;
        fixer.state = CombatState.NEUTRAL;
        flanker.state = CombatState.ATTACK_HOLD;
        fixture.simulation.evaluateTwoVsOneCoordination(fixture.engagement);
        require(fixture.engagement.coordination.primaryAttacker == flanker,
                "C: a ready rear flanker must be selectable as primary threat.");
        flanker.state = CombatState.ATTACK_ACTIVE;
        defender.target = flanker;
        defender.angle = flanker.pos.minus(defender.pos).radians();
        defender.state = CombatState.BLOCK_PREPARE;
        fixture.simulation.evaluateTwoVsOneCoordination(fixture.engagement);
        require(fixture.engagement.coordination.secondaryAttacker == fixer &&
                        fixture.engagement.coordination.phase ==
                                TwoVsOneCoordinationPhase.SECONDARY_RELEASE_WINDOW,
                "C: turning toward the flanker must open the frontal fixer response.");
    }

    private static void verifyDifferentHeights() {
        var fixture = fixture(303L, true);
        var coordination = fixture.engagement.coordination;
        fixture.simulation.evaluateTwoVsOneCoordination(fixture.engagement);
        for (var tick = 0; tick < 90 && fixture.simulation.modernMetrics().secondaryReleases == 0;
             ++tick) {
            holdGeometry(fixture);
            fixture.simulation.update(DT);
        }
        require(fixture.simulation.modernMetrics().secondaryReleases > 0,
                "D: a viable delayed window must produce a physical secondary release.");
        require(coordination.primaryAttacker.attackDirection !=
                        coordination.secondaryAttacker.attackDirection,
                "D: coordinated attackers should be able to use different heights.");
    }

    private static void verifyBlockedLineRejected() {
        var fixture = fixture(304L, true);
        var fixer = fixture.engagement.fixer;
        var flanker = fixture.engagement.flanker;
        var defender = fixture.engagement.primaryTarget;
        flanker.pos = fixer.pos.plus(defender.pos.minus(fixer.pos).times(0.50));
        flanker.angle = defender.pos.minus(flanker.pos).radians();
        flanker.tacticalIntent = TacticalIntent.DUEL;
        observe(fixture.simulation);
        var assessment = fixture.simulation.evaluateSecondaryRelease(fixer, flanker, defender);
        require(!assessment.viable() && assessment.rejectionReason().equals("line_blocked"),
                "E: an ally physically occupying the stab line must reject coordination.");
    }

    private static void verifyDefenderEscapeAborts() {
        var fixture = fixture(305L, true);
        fixture.simulation.evaluateTwoVsOneCoordination(fixture.engagement);
        require(fixture.engagement.coordination.phase ==
                        TwoVsOneCoordinationPhase.SECONDARY_RELEASE_WINDOW,
                "F: fixture must first create a secondary window.");
        fixture.engagement.primaryTarget.pos = new Pos(7.45, 6.35);
        observe(fixture.simulation);
        fixture.simulation.evaluateTwoVsOneCoordination(fixture.engagement);
        require(fixture.engagement.coordination.phase ==
                        TwoVsOneCoordinationPhase.RECOVER_COORDINATION &&
                        fixture.simulation.modernMetrics().coordinationRejectedDefenderEscaped > 0,
                "F: escaping the geometry must abort the coordinated release.");
    }

    private static Fixture fixture(long seed, boolean fixerArmed) {
        var simulation = new DuelSimulation(AgentConfiguration.defaultBlue(),
                AgentConfiguration.defaultBlueTwo(), AgentConfiguration.defaultRed(),
                AgentConfiguration.defaultRedTwo(), SimulationScenario.MODERN_2V2, seed);
        simulation.redTeam[0].alive = false;
        simulation.redTeam[0].hitPoints = 0;
        simulation.redTeam[0].state = CombatState.DEAD;
        simulation.update(DT);
        Engagement engagement = null;
        for (var candidate : simulation.engagements())
            if (candidate.type == EngagementType.TWO_VS_ONE &&
                    candidate.status != EngagementStatus.BROKEN &&
                    candidate.status != EngagementStatus.FINISHED) engagement = candidate;
        require(engagement != null, "A post-elimination TWO_VS_ONE must exist.");
        var defender = engagement.primaryTarget;
        var fixer = engagement.fixer;
        var flanker = engagement.flanker;
        defender.pos = new Pos(4.60, 3.50);
        defender.angle = Math.PI;
        defender.target = fixer;
        defender.state = CombatState.BLOCKING;
        fixer.pos = new Pos(3.16, 3.50);
        fixer.angle = 0;
        fixer.weaponYaw = 0;
        fixer.target = defender;
        fixer.state = fixerArmed ? CombatState.ATTACK_HOLD : CombatState.NEUTRAL;
        fixer.attackDirection = CombatDirection.UP;
        flanker.pos = new Pos(6.00, 3.50);
        flanker.angle = Math.PI;
        flanker.weaponYaw = 0;
        flanker.target = defender;
        flanker.state = CombatState.NEUTRAL;
        flanker.attackDirection = CombatDirection.DOWN;
        flanker.flankPhase = FlankPhase.STABILIZE_REAR;
        flanker.rearAngle = Math.PI;
        flanker.rearStableTime = 0.35;
        flanker.rearSectorStabilized = true;
        observe(simulation);
        return new Fixture(simulation, engagement, fixer.pos, flanker.pos, defender.pos);
    }

    private static void holdGeometry(Fixture fixture) {
        fixture.engagement.fixer.pos = fixture.fixerPosition;
        fixture.engagement.flanker.pos = fixture.flankerPosition;
        fixture.engagement.primaryTarget.pos = fixture.defenderPosition;
        fixture.engagement.primaryTarget.target = fixture.engagement.fixer;
        fixture.engagement.primaryTarget.angle = Math.PI;
        observe(fixture.simulation);
    }

    private static void observe(DuelSimulation simulation) {
        for (var observer : simulation.agents)
            for (var opponent : simulation.agents) {
                if (!observer.alive || !opponent.alive || observer.team == opponent.team) continue;
                var awareness = observer.opponentAwareness.get(opponent);
                awareness.visible = true;
                awareness.memoryTime = 2;
                awareness.lastKnown = opponent.pos;
                awareness.observedBodyAngle = opponent.angle;
                awareness.observedWeaponAngle = opponent.weaponWorldAngle();
                awareness.observedProjectedRange = opponent.projectedThrustLength();
                awareness.observedState = opponent.state;
                awareness.observedStateTime = opponent.stateTime;
            }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    private record Fixture(DuelSimulation simulation, Engagement engagement,
                           Pos fixerPosition, Pos flankerPosition, Pos defenderPosition) {
    }
}
