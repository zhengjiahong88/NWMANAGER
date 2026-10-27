import java.io.OutputStream;
import java.io.PrintStream;
import java.util.List;

public final class ControlledFlankTest {
    private static final double DT = 1.0 / 120.0;
    private static final int MAX_TICKS = 45 * 120;

    public static void main(String[] args) {
        new ControlledFlankTest().run();
    }

    private void run() {
        var originalOutput = System.out;
        try {
            System.setOut(new PrintStream(OutputStream.nullOutputStream()));
            verifyUnopposedFlankCanReachBackstab();
            verifyFixedDefenderAllowsCommit();
            verifyDefenderTurnRemovesBackstab();
            verifyInterceptionCancelsRearPlan();
            verifyFailedInterceptionKeepsProgress();
            verifyAlliedAttackLineBlocksWindow();
            verifyDeterminism();
        } finally {
            System.setOut(originalOutput);
        }
        System.out.println("Controlled flank tests passed.");
    }

    private void verifyUnopposedFlankCanReachBackstab() {
        var sideReached = false;
        var rearEntered = false;
        var windowCreated = false;
        var attempted = false;
        for (long seed = 1; seed <= 50; ++seed) {
            var simulation = controlled2v1(seed);
            run(simulation, MAX_TICKS, null);
            var log = simulation.tacticalLog();
            sideReached |= contains(log, "FLANK_SIDE_REACHED");
            rearEntered |= contains(log, "REAR_SECTOR_ENTERED");
            windowCreated |= contains(log, "BACKSTAB_WINDOW_CREATED");
            attempted |= contains(log, "BACKSTAB_PREPARED");
        }
        require(sideReached && rearEntered && windowCreated && attempted,
                "An unopposed controlled flank must sometimes create a real backstab attempt.");
    }

    private void verifyFixedDefenderAllowsCommit() {
        var simulation = controlled2v1(2_021L);
        var engagement = simulation.engagements().getFirst();
        run(simulation, MAX_TICKS, () -> keepDefenderFixed(engagement));
        require(contains(simulation.tacticalLog(), "REAR_SECTOR_STABILIZED") &&
                        contains(simulation.tacticalLog(), "BACKSTAB_PREPARED") &&
                        contains(simulation.tacticalLog(), "BACKSTAB_COMMITTED"),
                "A defender held by the fixer must permit stabilization, preparation and commit.");
    }

    private void verifyDefenderTurnRemovesBackstab() {
        var simulation = controlled2v1(77L);
        var engagement = simulation.engagements().getFirst();
        var turn = new boolean[1];
        run(simulation, MAX_TICKS, () -> {
            if (contains(simulation.tacticalLog(), "BACKSTAB_PREPARED"))
                turn[0] = true;
            if (turn[0]) {
                engagement.primaryTarget.angle =
                        engagement.flanker.pos.minus(engagement.primaryTarget.pos).radians();
                engagement.primaryTarget.target = engagement.flanker;
            } else keepDefenderFixed(engagement);
        });
        require(turn[0] && (contains(simulation.tacticalLog(), "BACKSTAB_LOST_DEFENDER_TURNED") ||
                        contains(simulation.tacticalLog(), "BACKSTAB_BLOCKED_AFTER_TURN")),
                "Turning toward A2 must remove the rear classification.");
    }

    private void verifyInterceptionCancelsRearPlan() {
        var simulation = controlled2v2(2_021L);
        var engagement = simulation.engagements().getFirst();
        var flanker = engagement.flanker;
        var interceptor = engagement.interceptor;
        flanker.flankPhase = FlankPhase.GAIN_REAR_ANGLE;
        flanker.rawRearDestination = flanker.smoothedRearDestination =
                engagement.primaryTarget.pos.plus(new Pos(engagement.primaryTarget.angle +
                        DuelSimulation.REAR_STABLE_ANGLE).times(1.65));
        flanker.tacticalDestination = flanker.smoothedRearDestination;
        engagement.fixer.pos = new Pos(1.2, 5.8);
        engagement.primaryTarget.pos = new Pos(6.8, 5.8);
        flanker.pos = new Pos(3.0, 2.0);
        flanker.angle = 0;
        interceptor.pos = new Pos(4.30, 2.0);
        interceptor.angle = Math.PI;
        interceptor.weaponYaw = 0;
        interceptor.velocity = new Pos(-0.7, 0);
        interceptor.state = CombatState.ATTACK_HOLD;
        interceptor.interceptionPhase = InterceptionPhase.PRESENT_THREAT;
        interceptor.target = flanker;
        observeExactly(flanker, interceptor);
        simulation.updateEngagementInterceptions(0.50);
        require(flanker.tacticalIntent != TacticalIntent.FLANK &&
                        flanker.flankPhase == FlankPhase.CONTESTED,
                "A credible B2 interception must cancel the rear plan.");
        var interruption = indexOf(simulation.tacticalLog(), "FLANK_INTERRUPTED_BY_B2");
        var lateCommit = indexOfAfter(simulation.tacticalLog(), "BACKSTAB_COMMITTED", interruption);
        require(interruption >= 0 && lateCommit < 0,
                "No rear commit may target B1 after the interception.");
    }

    private void verifyFailedInterceptionKeepsProgress() {
        var simulation = controlled2v2(2_021L);
        var engagement = simulation.engagements().getFirst();
        engagement.flanker.pos = engagement.flankerDestination();
        engagement.interceptor.pos = engagement.flanker.pos.plus(2.2, 0);
        keepDefenderFixed(engagement);
        simulation.update(DT);
        require(engagement.interceptor.interceptionPhase == InterceptionPhase.FAILED &&
                        engagement.flanker.flankPhase != FlankPhase.APPROACH_SIDE,
                "A failed interception must continue from A2's real flank position.");
        run(simulation, MAX_TICKS, () -> {
            keepDefenderFixed(engagement);
            engagement.interceptor.pos = new Pos(7.35, 6.45);
            engagement.interceptor.velocity = new Pos();
            engagement.interceptor.state = CombatState.RECOVERY;
            engagement.interceptor.stateTime = 0;
        });
        require(contains(simulation.tacticalLog(), "REAR_SECTOR_ENTERED") &&
                        contains(simulation.tacticalLog(), "BACKSTAB_WINDOW_CREATED"),
                "After B2 fails, A2 must be able to continue toward a backstab window.");
    }

    private void verifyAlliedAttackLineBlocksWindow() {
        var simulation = controlled2v1(91L);
        var engagement = simulation.engagements().getFirst();
        var flanker = engagement.flanker;
        var defender = engagement.primaryTarget;
        flanker.pos = defender.pos.plus(new Pos(defender.angle +
                DuelSimulation.REAR_STABLE_ANGLE).times(1.62));
        flanker.angle = defender.pos.minus(flanker.pos).radians();
        flanker.weaponYaw = 0;
        flanker.flankPhase = FlankPhase.STABILIZE_REAR;
        flanker.rearStableTime = 0.30;
        engagement.fixer.pos = flanker.pos.plus(defender.pos).div(2);
        engagement.fixer.target = defender;
        defender.target = engagement.fixer;
        defender.angle = engagement.fixer.pos.minus(defender.pos).radians();
        simulation.update(DT);
        require(!flanker.backstabOpportunity.attackLineClear() &&
                        !contains(simulation.tacticalLog(), "BACKSTAB_PREPARED"),
                "A1 on the thrust segment must block the backstab window.");
    }

    private void verifyDeterminism() {
        var first = controlled2v1(42L);
        var second = controlled2v1(42L);
        for (var tick = 0; tick < 1_800; ++tick) {
            first.update(DT);
            second.update(DT);
            var left = first.blueTeam[1];
            var right = second.blueTeam[1];
            require(left.pos.equals(right.pos) && left.velocity.equals(right.velocity) &&
                            left.flankPhase == right.flankPhase &&
                            equal(left.rawRearDestination, right.rawRearDestination) &&
                            equal(left.smoothedRearDestination, right.smoothedRearDestination) &&
                            left.backstabOpportunity.equals(right.backstabOpportunity) &&
                            Double.doubleToLongBits(left.rearAngle) ==
                                    Double.doubleToLongBits(right.rearAngle),
                    "The controlled flank must remain deterministic.");
        }
        require(first.tacticalLog().equals(second.tacticalLog()),
                "Deterministic flanks must produce identical logs.");
    }

    private DuelSimulation controlled2v1(long seed) {
        return new DuelSimulation(
                AgentConfiguration.defaultBlue(),
                AgentConfiguration.defaultBlueTwo(),
                AgentConfiguration.defaultRed(),
                AgentConfiguration.defaultRedTwo(),
                SimulationScenario.CONTROLLED_2V1,
                seed);
    }

    private DuelSimulation controlled2v2(long seed) {
        return new DuelSimulation(
                AgentConfiguration.defaultBlue(),
                AgentConfiguration.defaultBlueTwo(),
                AgentConfiguration.defaultRed(),
                AgentConfiguration.defaultRedTwo(),
                SimulationScenario.CONTROLLED_2V2,
                seed);
    }

    private void run(DuelSimulation simulation, int ticks, Runnable fixture) {
        for (var tick = 0; tick < ticks && simulation.winningTeam == null; ++tick) {
            if (fixture != null) fixture.run();
            simulation.update(DT);
        }
    }

    private void keepDefenderFixed(Engagement engagement) {
        var defender = engagement.primaryTarget;
        var fixer = engagement.fixer;
        defender.pos = new Pos(5.45, 3.50);
        defender.velocity = new Pos();
        fixer.pos = new Pos(3.75, 3.50);
        fixer.velocity = new Pos();
        fixer.target = defender;
        fixer.angle = 0;
        fixer.state = CombatState.ATTACK_HOLD;
        fixer.stateTime = 0;
        fixer.attackIntent = AttackIntent.HOLD;
        defender.target = engagement.fixer;
        defender.state = CombatState.BLOCKING;
        defender.stateTime = 0;
        defender.angle = engagement.fixer.pos.minus(defender.pos).radians();
    }

    private void observeExactly(Musketeer observer, Musketeer opponent) {
        var awareness = observer.opponentAwareness.get(opponent);
        awareness.visible = true;
        awareness.lastKnown = opponent.pos;
        awareness.perceivedVelocity = opponent.velocity;
        awareness.observedBodyAngle = opponent.angle;
        awareness.observedWeaponAngle = opponent.weaponWorldAngle();
        awareness.observedProjectedRange = opponent.projectedThrustLength();
        awareness.observedState = opponent.state;
        awareness.observedAttackDirection = opponent.attackDirection;
        awareness.memoryTime = 2;
    }

    private boolean contains(List<String> log, String event) {
        return indexOf(log, event) >= 0;
    }

    private int indexOf(List<String> log, String event) {
        return indexOfAfter(log, event, -1);
    }

    private int indexOfAfter(List<String> log, String event, int start) {
        for (var n = start + 1; n < log.size(); ++n)
            if (log.get(n).contains(event)) return n;
        return -1;
    }

    private boolean equal(Pos first, Pos second) {
        return first == null ? second == null : first.equals(second);
    }

    private void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
