import javax.swing.*;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;

final class Experimental3v3SwingTransitionTest {
    private static final long SEED = 41L;

    public static void main(String[] args) throws Exception {
        var blue = new TeamConfiguration3("Blue", AgentConfiguration.defaultBlue(),
                AgentConfiguration.defaultBlueTwo(), AgentConfiguration.defaultBlueThree());
        var red = new TeamConfiguration3("Red", AgentConfiguration.defaultRed(),
                AgentConfiguration.defaultRedTwo(), AgentConfiguration.defaultRedThree());
        var simulation = new DuelSimulation(blue, red, SimulationScenario.MODERN_3V3, SEED);
        var panel = new MapPanel[1];
        var recorder = new TransitionDebugRecorder[1];
        var output = Path.of("out", "debug", "transition-swing-test-41.log");
        SwingUtilities.invokeAndWait(() -> {
            panel[0] = new MapPanel(simulation, UiLanguage.FRENCH, null, "Blue", "Red");
            panel[0].setSize(panel[0].getPreferredSize());
            recorder[0] = panel[0].enableTransitionDebug(SEED, 0.35, output);
            eliminate(simulation.blueTeam[2]);
            eliminate(simulation.redTeam[1]);
            eliminate(simulation.redTeam[2]);
        });

        Thread.sleep(800);
        var metrics = new SwingTickMetrics[1];
        SwingUtilities.invokeAndWait(() -> {
            var image = new BufferedImage(panel[0].getWidth(), panel[0].getHeight(),
                    BufferedImage.TYPE_INT_ARGB);
            var graphics = image.createGraphics();
            panel[0].paint(graphics);
            graphics.dispose();
            metrics[0] = panel[0].swingTickMetrics();
            panel[0].stopTimerForTest();
        });

        require(simulation.experimentalGroupPhase() == ExperimentalGroupPhase.HANDOFF_2V1,
                "The forced roster must enter the real 2v1 handoff.");
        require(metrics[0].edtTicks() >= 15 && metrics[0].repaintsRequested() == metrics[0].edtTicks(),
                "The Swing Timer and repaint requests must keep progressing after the handoff.");
        require(simulation.simulationTime > 0.20,
                "The simulation must continue after the elimination.");
        require(metrics[0].edtTicksOver200Ms() == 0,
                "No Swing callback may remain blocked for 200 ms.");
        var reportPath = recorder[0].await(5);
        var report = Files.readString(reportPath);
        require(report.contains("ticksAfterFirstDeath=") &&
                        report.contains("positionChangesAfterFirstDeath=") &&
                        report.contains("transitionApplications=1") &&
                        report.contains("duplicateTransitionApplications=0") &&
                        report.contains("deadReferencesAfterTransition=0") &&
                        report.contains("repaintsRequested=") &&
                        report.contains("consoleLinesEmitted=0") &&
                        report.contains("TRANSITION_REQUEST") &&
                        report.contains("TRANSITION_APPLIED"),
                "The asynchronous debug report must contain progress, EDT and transition evidence.");
        System.out.println("Experimental 3v3 Swing transition test passed: " + metrics[0]);
    }

    private static void eliminate(Musketeer agent) {
        agent.alive = false;
        agent.state = CombatState.DEAD;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
