import java.io.OutputStream;
import java.io.PrintStream;
import java.util.ArrayDeque;

public final class Controlled2v2PlacementTest {
    private static final double DT = 1.0 / 120.0;
    private static final int MAX_TICKS = 45 * 120;

    public static void main(String[] args) {
        new Controlled2v2PlacementTest().run();
    }

    private void run() {
        var metrics = new Metrics();
        var originalOutput = System.out;
        try {
            System.setOut(new PrintStream(OutputStream.nullOutputStream()));
            for (long seed = 1; seed <= 50; ++seed)
                runSeed(seed, metrics);
        } finally {
            System.setOut(originalOutput);
        }
        metrics.print();
    }

    private void runSeed(long seed, Metrics metrics) {
        var generator = new ScenarioGenerator();
        var simulation = new DuelSimulation(
                AgentConfiguration.defaultBlue(),
                AgentConfiguration.defaultBlueTwo(),
                AgentConfiguration.defaultRed(),
                AgentConfiguration.defaultRedTwo(),
                SimulationScenario.CONTROLLED_2V2,
                ScenarioFamily.JITTERED_BASELINE,
                seed);
        var setup = simulation.scenarioSetup();
        if (!generator.isLegal(setup))
            throw new IllegalStateException("Placement illégal pour la seed " + seed);

        ++metrics.completed;
        metrics.rejectedPlacements += setup.rejectedPlacements();
        if (setup.baselineFallback())
            ++metrics.baselineFallbacks;

        var agents = simulation.agents;
        var a1 = agents[0];
        var a2 = agents[1];
        var b1 = agents[2];
        var b2 = agents[3];
        var targetSwitches = new ArrayDeque<Integer>();
        var intentSwitches = new ArrayDeque<Integer>();
        Musketeer lastTarget = a2.target;
        TacticalIntent lastIntent = a2.tacticalIntent;
        Pos previousDestination = null;
        int blueStackTicks = 0, redStackTicks = 0;
        boolean targetThrashing = false, intentThrashing = false;

        for (int tick = 0; tick < MAX_TICKS && simulation.winningTeam == null; ++tick) {
            simulation.update(DT);
            verifyPhysicalState(seed, agents);

            blueStackTicks = stackingTicks(a1, a2, blueStackTicks);
            redStackTicks = stackingTicks(b1, b2, redStackTicks);
            if (blueStackTicks > 120 || redStackTicks > 120)
                throw new IllegalStateException("Empilement allié prolongé pour la seed " + seed);

            if (a2.target != lastTarget) {
                lastTarget = a2.target;
                targetSwitches.addLast(tick);
                trimWindow(targetSwitches, tick);
                if (targetSwitches.size() >= 4)
                    targetThrashing = true;
            }
            if (a2.tacticalIntent != lastIntent) {
                lastIntent = a2.tacticalIntent;
                intentSwitches.addLast(tick);
                trimWindow(intentSwitches, tick);
                if (intentSwitches.size() >= 4)
                    intentThrashing = true;
            }

            var destination = b2.tacticalIntent == TacticalIntent.INTERCEPT ? b2.tacticalDestination : null;
            if (destination != null && previousDestination != null
                    && destination.minus(previousDestination).hypotenuse() > 1.85 * DT + 1e-7)
                throw new IllegalStateException("Destination d'interception instable pour la seed " + seed);
            previousDestination = destination;

        }

        if (targetThrashing)
            ++metrics.targetThrashing;
        if (intentThrashing)
            ++metrics.intentThrashing;
        var log = simulation.tacticalLog();
        if (log.stream().anyMatch(line -> line.contains("ENGAGEMENT_BROKEN reason=flanker_intercepted")))
            ++metrics.interceptionsSucceeded;
        if (log.stream().anyMatch(line -> line.contains("INTERCEPTION_FAILED")))
            ++metrics.interceptionsFailed;
        if (log.stream().filter(line -> line.contains("DUEL_CREATED")).count() >= 2)
            ++metrics.duelSplits;
        metrics.sideSlotsReached += count(log, "FLANK_SIDE_REACHED");
        metrics.rearSectorsEntered += count(log, "REAR_SECTOR_ENTERED");
        metrics.rearSectorsStabilized += count(log, "REAR_SECTOR_STABILIZED");
        metrics.backstabWindowsCreated += count(log, "BACKSTAB_WINDOW_CREATED");
        metrics.backstabsPrepared += count(log, "BACKSTAB_PREPARED");
        metrics.backstabsCommitted += count(log, "BACKSTAB_COMMITTED");
        metrics.backstabsHit += count(log, "BACKSTAB_HIT");
        metrics.backstabsMissed += count(log, "BACKSTAB_MISSED");
        metrics.backstabsLostByTurn += count(log, "BACKSTAB_LOST_DEFENDER_TURNED");
        metrics.flanksInterruptedByB2 += count(log, "FLANK_INTERRUPTED_BY_B2");
        metrics.flanksAbortedFixerLost += count(log, "FLANK_ABORTED_FIXER_LOST");
        metrics.flanksAbortedNoProgress += count(log, "FLANK_ABORTED_NO_PROGRESS");
        metrics.flankersDied += count(log, "FLANKER_DIED");
        metrics.defendersDiedBeforeBackstab += count(log, "DEFENDER_DIED_BEFORE_BACKSTAB");

        var winner = simulation.winningTeam;
        if (winner == null) {
            ++metrics.stalemates;
            return;
        }
        switch (winner) {
            case BLUE -> ++metrics.blueWins;
            case RED -> ++metrics.redWins;
            case DRAW -> ++metrics.draws;
        }
    }

    private void verifyPhysicalState(long seed, Musketeer[] agents) {
        for (var agent : agents) {
            if (!finite(agent.pos) || !finite(agent.velocity)
                    || !Double.isFinite(agent.angle) || !Double.isFinite(agent.weaponYaw)
                    || !Double.isFinite(agent.threatAssessment.score()))
                throw new IllegalStateException("NaN ou infini pour la seed " + seed);
            if (agent.pos.x() < Musketeer.BODY_RADIUS - 1e-7
                    || agent.pos.x() > DuelSimulation.ARENA_WIDTH - Musketeer.BODY_RADIUS + 1e-7
                    || agent.pos.y() < Musketeer.BODY_RADIUS - 1e-7
                    || agent.pos.y() > DuelSimulation.ARENA_HEIGHT - Musketeer.BODY_RADIUS + 1e-7)
                throw new IllegalStateException("Agent hors arène pour la seed " + seed);
        }
    }

    private int stackingTicks(Musketeer first, Musketeer second, int ticks) {
        return first.alive && second.alive
                && first.pos.minus(second.pos).hypotenuse() < Musketeer.BODY_RADIUS * 1.18 ? ticks + 1 : 0;
    }

    private boolean finite(Pos position) {
        return Double.isFinite(position.x()) && Double.isFinite(position.y());
    }

    private void trimWindow(ArrayDeque<Integer> switches, int tick) {
        while (!switches.isEmpty() && switches.getFirst() < tick - 120)
            switches.removeFirst();
    }

    private int count(java.util.List<String> log, String event) {
        return (int) log.stream().filter(line -> line.contains(event)).count();
    }

    private static final class Metrics {
        int completed;
        int stalemates;
        int interceptionsSucceeded;
        int interceptionsFailed;
        int duelSplits;
        int sideSlotsReached;
        int rearSectorsEntered;
        int rearSectorsStabilized;
        int backstabWindowsCreated;
        int backstabsPrepared;
        int backstabsCommitted;
        int backstabsHit;
        int backstabsMissed;
        int backstabsLostByTurn;
        int flanksInterruptedByB2;
        int flanksAbortedFixerLost;
        int flanksAbortedNoProgress;
        int flankersDied;
        int defendersDiedBeforeBackstab;
        int targetThrashing;
        int intentThrashing;
        int rejectedPlacements;
        int baselineFallbacks;
        int blueWins;
        int redWins;
        int draws;

        void print() {
            System.out.println("JITTERED_BASELINE seeds 1-50");
            System.out.println("completed=" + completed);
            System.out.println("stalemates=" + stalemates);
            System.out.println("interceptionsSucceeded=" + interceptionsSucceeded);
            System.out.println("interceptionsFailed=" + interceptionsFailed);
            System.out.println("duelSplits=" + duelSplits);
            System.out.println("sideSlotsReached=" + sideSlotsReached);
            System.out.println("rearSectorsEntered=" + rearSectorsEntered);
            System.out.println("rearSectorsStabilized=" + rearSectorsStabilized);
            System.out.println("backstabWindowsCreated=" + backstabWindowsCreated);
            System.out.println("backstabsPrepared=" + backstabsPrepared);
            System.out.println("backstabsCommitted=" + backstabsCommitted);
            System.out.println("backstabsHit=" + backstabsHit);
            System.out.println("backstabsMissed=" + backstabsMissed);
            System.out.println("backstabsLostByTurn=" + backstabsLostByTurn);
            System.out.println("flanksInterruptedByB2=" + flanksInterruptedByB2);
            System.out.println("flanksAbortedFixerLost=" + flanksAbortedFixerLost);
            System.out.println("flanksAbortedNoProgress=" + flanksAbortedNoProgress);
            System.out.println("flankersDied=" + flankersDied);
            System.out.println("defendersDiedBeforeBackstab=" + defendersDiedBeforeBackstab);
            System.out.println("targetThrashing=" + targetThrashing);
            System.out.println("intentThrashing=" + intentThrashing);
            System.out.println("rejectedPlacements=" + rejectedPlacements);
            System.out.println("baselineFallbacks=" + baselineFallbacks);
            System.out.println("blueWins=" + blueWins);
            System.out.println("redWins=" + redWins);
            System.out.println("draws=" + draws);
        }
    }
}
