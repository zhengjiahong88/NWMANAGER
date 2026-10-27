import javax.swing.*;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;

final class Debug3v3TransitionSeedsTest {
    private static final long[] SEEDS = {41L, 73L, 119L};

    public static void main(String[] args) throws Exception {
        var panels = new MapPanel[SEEDS.length];
        var recorders = new TransitionDebugRecorder[SEEDS.length];
        SwingUtilities.invokeAndWait(() -> {
            for (var n = 0; n < SEEDS.length; ++n) {
                var seed = SEEDS[n];
                var blue = new TeamConfiguration3("Blue", AgentConfiguration.defaultBlue(),
                        AgentConfiguration.defaultBlueTwo(), AgentConfiguration.defaultBlueThree());
                var red = new TeamConfiguration3("Red", AgentConfiguration.defaultRed(),
                        AgentConfiguration.defaultRedTwo(), AgentConfiguration.defaultRedThree());
                panels[n] = new MapPanel(new DuelSimulation(blue, red, SimulationScenario.MODERN_3V3,
                        seed), UiLanguage.FRENCH, null, "Blue", "Red");
                panels[n].setSize(panels[n].getPreferredSize());
                recorders[n] = panels[n].enableTransitionDebug(seed, 15,
                        Path.of("out", "debug", "transition-seed-" + seed + ".log"));
            }
        });

        var deadline = System.nanoTime() + 120_000_000_000L;
        while (!allReportsComplete(recorders) && System.nanoTime() < deadline) {
            SwingUtilities.invokeAndWait(() -> {
                for (var panel : panels) {
                    var image = new BufferedImage(panel.getWidth(), panel.getHeight(),
                            BufferedImage.TYPE_INT_ARGB);
                    var graphics = image.createGraphics();
                    panel.paint(graphics);
                    graphics.dispose();
                }
            });
            Thread.sleep(250);
        }
        SwingUtilities.invokeAndWait(() -> {
            for (var panel : panels) panel.stopTimerForTest();
        });
        require(allReportsComplete(recorders),
                "Seeds 41, 73 and 119 must all produce a report within 120 real seconds.");
        for (var n = 0; n < SEEDS.length; ++n) {
            var report = Files.readString(recorders[n].await(5));
            require(report.contains("seed=" + SEEDS[n]) &&
                            positive(report, "ticksAfterFirstDeath") &&
                            positive(report, "positionChangesAfterFirstDeath") &&
                            positive(report, "repaintsRequested") &&
                            report.contains("duplicateTransitionApplications=0") &&
                            report.contains("recursiveTransitionAttempts=0") &&
                            report.contains("deadReferencesAfterTransition=0") &&
                            report.contains("logicalFreezeReports=0") &&
                            report.contains("consoleLinesEmitted=0"),
                    "Seed " + SEEDS[n] + " must remain animated and transactionally clean.");
        }
        System.out.println("Swing debug seeds 41, 73 and 119 passed at 1x for 15 seconds after first death.");
    }

    private static boolean allReportsComplete(TransitionDebugRecorder[] recorders) {
        for (var recorder : recorders)
            if (recorder == null || recorder.future() == null || !recorder.future().isDone()) return false;
        return true;
    }

    private static boolean positive(String report, String field) {
        for (var line : report.lines().toList())
            if (line.startsWith(field + "=")) return Long.parseLong(line.substring(field.length() + 1)) > 0;
        return false;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
