import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

final class TransitionDebugRecorder {
    private final long seed, durationNanos;
    private final Path output;
    private long firstDeathWallNanos, ticksAfterFirstDeath, positionChangesAfterFirstDeath,
            stateChangesAfterFirstDeath, attacksAfterFirstDeath;
    private double firstDeathSimulationTime;
    private int aliveAtFirstDeath, subsequentDeaths;
    private Pos[] previousPositions;
    private CombatState[] previousStates;
    private CompletableFuture<Path> writeFuture;

    TransitionDebugRecorder(long seed, double secondsAfterFirstDeath, Path output) {
        this.seed = seed;
        durationNanos = (long) (secondsAfterFirstDeath * 1_000_000_000L);
        this.output = output;
    }

    void observe(DuelSimulation simulation, SwingTickMetrics swingMetrics) {
        if (writeFuture != null) return;
        if (firstDeathWallNanos == 0) {
            if (!simulation.experimentalFirstEliminationObserved()) return;
            firstDeathWallNanos = System.nanoTime();
            firstDeathSimulationTime = simulation.simulationTime;
            aliveAtFirstDeath = simulation.processedRoster().blueAlive() +
                    simulation.processedRoster().redAlive();
            snapshotAgents(simulation);
            return;
        }
        ++ticksAfterFirstDeath;
        var positionChanged = false;
        var stateChanged = false;
        for (var n = 0; n < simulation.agents.length; ++n) {
            var agent = simulation.agents[n];
            if (previousPositions[n].minus(agent.pos).hypotenuse() > 0.000001) positionChanged = true;
            if (previousStates[n] != agent.state) {
                stateChanged = true;
                if ((agent.state == CombatState.ATTACK_PREPARE ||
                        agent.state == CombatState.ATTACK_ACTIVE) &&
                        previousStates[n] != CombatState.ATTACK_PREPARE &&
                        previousStates[n] != CombatState.ATTACK_ACTIVE) ++attacksAfterFirstDeath;
            }
            previousPositions[n] = agent.pos;
            previousStates[n] = agent.state;
        }
        if (positionChanged) ++positionChangesAfterFirstDeath;
        if (stateChanged) ++stateChangesAfterFirstDeath;
        var roster = simulation.processedRoster();
        subsequentDeaths = Math.max(subsequentDeaths, aliveAtFirstDeath -
                roster.blueAlive() - roster.redAlive());
        if (System.nanoTime() - firstDeathWallNanos < durationNanos) return;
        var report = report(simulation, swingMetrics);
        writeFuture = CompletableFuture.supplyAsync(() -> write(report));
    }

    CompletableFuture<Path> future() {
        return writeFuture;
    }

    Path await(long timeoutSeconds) throws Exception {
        if (writeFuture == null) throw new IllegalStateException("Debug report is not scheduled yet.");
        return writeFuture.get(timeoutSeconds, TimeUnit.SECONDS);
    }

    private void snapshotAgents(DuelSimulation simulation) {
        previousPositions = new Pos[simulation.agents.length];
        previousStates = new CombatState[simulation.agents.length];
        for (var n = 0; n < simulation.agents.length; ++n) {
            previousPositions[n] = simulation.agents[n].pos;
            previousStates[n] = simulation.agents[n].state;
        }
    }

    private String report(DuelSimulation simulation, SwingTickMetrics swing) {
        var metrics = simulation.group3Metrics();
        var watchdog = simulation.progressWatchdogMetrics();
        var text = new StringBuilder();
        text.append("NWMANAGER debug-3v3-transition\n").append("seed=").append(seed).
                append("\nscenario=").append(simulation.scenario()).
                append("\ncontroller=").append(simulation.activeCombatController()).
                append("\nphase=").append(simulation.experimentalGroupPhase()).
                append("\nfirstDeathSimulationTime=").append(firstDeathSimulationTime).
                append("\nticksAfterFirstDeath=").append(ticksAfterFirstDeath).
                append("\npositionChangesAfterFirstDeath=").append(positionChangesAfterFirstDeath).
                append("\nstateChangesAfterFirstDeath=").append(stateChangesAfterFirstDeath).
                append("\nattacksAfterFirstDeath=").append(attacksAfterFirstDeath).
                append("\nsubsequentDeaths=").append(subsequentDeaths).
                append("\nmatchCompleted=").append(simulation.experimentalRoundComplete()).
                append("\nwinner=").append(simulation.experimentalRoundLeader()).
                append("\ntransitionRequests=").append(metrics.transitionRequests).
                append("\ntransitionApplications=").append(metrics.transitionApplications).
                append("\nduplicateTransitionRequests=").append(metrics.duplicateTransitionRequests).
                append("\nduplicateTransitionApplications=").append(metrics.duplicateTransitionApplications).
                append("\nrecursiveTransitionAttempts=").append(metrics.recursiveTransitionAttempts).
                append("\nmultipleTransitionAttemptsSameTick=").append(metrics.multipleTransitionAttemptsSameTick).
                append("\ndeadReferencesAfterTransition=").append(metrics.deadReferencesAfterTransition).
                append("\nlogicalFreezeReports=").append(watchdog.logicalFreezeReports()).
                append("\nrepeatedTransitionStateTicks=").append(watchdog.repeatedTransitionStateTicks()).
                append("\nedtTicks=").append(swing.edtTicks()).
                append("\nedtTickAverageMs=").append(swing.edtTickAverageMs()).
                append("\nedtTickMaximumMs=").append(swing.edtTickMaximumMs()).
                append("\nedtTicksOver16Ms=").append(swing.edtTicksOver16Ms()).
                append("\nedtTicksOver50Ms=").append(swing.edtTicksOver50Ms()).
                append("\nedtTicksOver200Ms=").append(swing.edtTicksOver200Ms()).
                append("\nrepaintsRequested=").append(swing.repaintsRequested()).
                append("\nconsoleLinesEmitted=").append(simulation.consoleLinesEmitted()).
                append("\nduplicateConsoleLinesSuppressed=").
                append(simulation.duplicateConsoleLinesSuppressed()).append("\n\nTRANSITIONS\n");
        for (var line : simulation.transitionLog()) text.append(line).append('\n');
        return text.toString();
    }

    private Path write(String report) {
        try {
            Files.createDirectories(output.getParent());
            Files.writeString(output, report, StandardCharsets.UTF_8);
            return output;
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to write transition debug report: " + output, exception);
        }
    }
}
