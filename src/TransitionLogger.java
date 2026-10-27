import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;

final class TransitionLogger {
    private static final int MAX_LINES = 256;
    private final ArrayDeque<String> lines = new ArrayDeque<>(), keys = new ArrayDeque<>();
    private final HashSet<String> emittedKeys = new HashSet<>();
    private final boolean consoleEnabled;
    private long consoleLinesEmitted, duplicateConsoleLinesSuppressed, storedLinesDropped;

    TransitionLogger(boolean consoleEnabled) {
        this.consoleEnabled = consoleEnabled;
    }

    void record(TransitionTrace trace) {
        var type = trace.applied() ? "TRANSITION_APPLIED" : "TRANSITION_REQUEST";
        emitOnce(trace.transitionId() + ":" + type, type + " id=" + trace.transitionId() +
                " tick=" + trace.tick() + " time=%.3f".formatted(trace.simulationTime()) +
                " controller=" + trace.controllerBefore() + "->" + trace.controllerAfter() +
                " phase=" + trace.phaseBefore() + "->" + trace.requestedPhase() + "->" +
                trace.phaseAfter() + " mask=" + Integer.toBinaryString(trace.blueMaskBefore()) +
                "|" + Integer.toBinaryString(trace.redMaskBefore()) + "->" +
                Integer.toBinaryString(trace.blueMaskAfter()) + "|" +
                Integer.toBinaryString(trace.redMaskAfter()) + " reason=" + trace.reason());
    }

    void emitOnce(String key, String line) {
        if (!emittedKeys.add(key)) {
            ++duplicateConsoleLinesSuppressed;
            return;
        }
        if (lines.size() == MAX_LINES) {
            lines.removeFirst();
            emittedKeys.remove(keys.removeFirst());
            ++storedLinesDropped;
        }
        lines.addLast(line);
        keys.addLast(key);
        if (consoleEnabled) {
            System.out.println(line);
            ++consoleLinesEmitted;
        }
    }

    List<String> lines() {
        return List.copyOf(lines);
    }

    long consoleLinesEmitted() {
        return consoleLinesEmitted;
    }

    long duplicateConsoleLinesSuppressed() {
        return duplicateConsoleLinesSuppressed;
    }

    long storedLinesDropped() {
        return storedLinesDropped;
    }

    void reset() {
        lines.clear();
        keys.clear();
        emittedKeys.clear();
        consoleLinesEmitted = duplicateConsoleLinesSuppressed = storedLinesDropped = 0;
    }
}
