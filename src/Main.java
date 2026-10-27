import javax.swing.*;
import java.nio.file.Path;

final class Main {
    private final JFrame window = new JFrame("NWMANAGER");

    public static void main(String[] args) {
        LaunchOptions options;
        try {
            options = parseArguments(args);
        } catch (IllegalArgumentException exception) {
            System.err.println(exception.getMessage());
            printUsage();
            return;
        }
        SwingUtilities.invokeLater(() -> new Main().start(options));
    }

    private static LaunchOptions parseArguments(String[] args) {
        if (args.length == 0)
            return new LaunchOptions(null, ScenarioFamily.BASELINE, System.nanoTime(), false);
        if (args[0].equalsIgnoreCase("debug-3v3-transition")) {
            if (args.length != 2)
                throw new IllegalArgumentException("debug-3v3-transition exige une seed.");
            return new LaunchOptions(SimulationScenario.MODERN_3V3, ScenarioFamily.BASELINE,
                    parseSeed(args[1]), true);
        }
        if (args[0].equalsIgnoreCase("legacy-2v2")) {
            if (args.length > 2)
                throw new IllegalArgumentException("legacy-2v2 accepte seulement une seed facultative.");
            return new LaunchOptions(SimulationScenario.LEGACY_2V2, ScenarioFamily.BASELINE,
                    args.length == 2 ? parseSeed(args[1]) : 2_021L, false);
        }
        if (args[0].equalsIgnoreCase("controlled-2v1")) {
            if (args.length != 1)
                throw new IllegalArgumentException("controlled-2v1 n’accepte aucun argument supplémentaire.");
            return new LaunchOptions(SimulationScenario.CONTROLLED_2V1, ScenarioFamily.BASELINE,
                    2_021L, false);
        }
        if (args[0].equalsIgnoreCase("modern-2v2")) {
            if (args.length != 2)
                throw new IllegalArgumentException("modern-2v2 exige une seed.");
            return new LaunchOptions(SimulationScenario.MODERN_2V2,
                    ScenarioFamily.BASELINE, parseSeed(args[1]), false);
        }
        if (args[0].equalsIgnoreCase("modern-3v3") || args[0].equalsIgnoreCase("controlled-3v3")) {
            if (args.length != 2)
                throw new IllegalArgumentException(args[0] + " exige une seed.");
            return new LaunchOptions(args[0].equalsIgnoreCase("modern-3v3")
                    ? SimulationScenario.MODERN_3V3 : SimulationScenario.CONTROLLED_3V3,
                    ScenarioFamily.BASELINE, parseSeed(args[1]), false);
        }
        if (!args[0].equalsIgnoreCase("controlled-2v2"))
            throw new IllegalArgumentException("Mode inconnu : " + args[0]);
        if (args.length > 3)
            throw new IllegalArgumentException("Trop d’arguments pour controlled-2v2.");
        var family = args.length >= 2 ? ScenarioFamily.parse(args[1]) : ScenarioFamily.BASELINE;
        var seed = args.length == 3 ? parseSeed(args[2]) : 2_021L;
        return new LaunchOptions(SimulationScenario.CONTROLLED_2V2, family, seed, false);
    }

    private static long parseSeed(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Seed invalide : " + value);
        }
    }

    private static void printUsage() {
        System.err.println("""
                Syntaxe :
                  java -cp out Main
                  java -cp out Main legacy-2v2 [seed]
                  java -cp out Main controlled-2v1
                  java -cp out Main modern-2v2 <seed>
                  java -cp out Main modern-3v3 <seed>
                  java -cp out Main controlled-3v3 <seed>
                  java -cp out Main debug-3v3-transition <seed>
                  java -cp out Main controlled-2v2 [baseline|jittered|jittered-baseline] [seed]
                """);
    }

    private void start(LaunchOptions options) {
        window.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        window.setResizable(false);
        if (options.scenario() != null) showControlled(UiLanguage.FRENCH, options);
        else showMainMenu(UiLanguage.FRENCH);
    }

    private void showMainMenu(UiLanguage language) {
        show(new MenuPanel(MenuPanel.Page.MAIN, language, (action, selectedLanguage) -> {
            if (action == MenuPanel.MenuAction.PLAY) showPlayMenu(selectedLanguage);
            else if (action == MenuPanel.MenuAction.QUIT) window.dispose();
        }), "NWMANAGER");
    }

    private void showPlayMenu(UiLanguage language) {
        show(new MenuPanel(MenuPanel.Page.PLAY, language, (action, selectedLanguage) -> {
            if (action == MenuPanel.MenuAction.FRIENDLY_MATCH) showSetup(selectedLanguage);
            else if (action == MenuPanel.MenuAction.EXPERIMENTAL_3V3) showSetup3(selectedLanguage);
            else if (action == MenuPanel.MenuAction.BACK) showMainMenu(selectedLanguage);
        }), "NWMANAGER - " + language.text("Jouer", "開始遊戲"));
    }

    private void showSetup3(UiLanguage language) {
        show(new SetupPanel(language, this::showCombat3, this::showPlayMenu, 3),
                "NWMANAGER - " + language.text("Match expérimental 3v3", "實驗 3v3 比賽"));
    }

    private void showSetup(UiLanguage language) {
        show(new SetupPanel(language, this::showCombat, this::showPlayMenu),
                "NWMANAGER - " + language.text("Match amical", "友誼賽"));
    }

    private void showCombat(TeamConfiguration blueTeam, TeamConfiguration redTeam, UiLanguage language) {
        show(new MapPanel(blueTeam, redTeam, language, this::showMainMenu),
                "NWMANAGER - " + blueTeam.name() + " vs " + redTeam.name());
    }

    private void showCombat3(TeamConfiguration3 blueTeam, TeamConfiguration3 redTeam, UiLanguage language) {
        show(new MapPanel(blueTeam, redTeam, language, this::showMainMenu),
                "NWMANAGER - 3v3 - " + blueTeam.name() + " vs " + redTeam.name());
    }

    private void showControlled(UiLanguage language, LaunchOptions options) {
        if (options.scenario() == SimulationScenario.MODERN_3V3 ||
                options.scenario() == SimulationScenario.CONTROLLED_3V3) {
            var blue = new TeamConfiguration3("Équipe A", AgentConfiguration.defaultBlue(),
                    AgentConfiguration.defaultBlueTwo(), AgentConfiguration.defaultBlueThree());
            var red = new TeamConfiguration3("Équipe B", AgentConfiguration.defaultRed(),
                    AgentConfiguration.defaultRedTwo(), AgentConfiguration.defaultRedThree());
            var panel = new MapPanel(blue, red, language, this::showMainMenu,
                    options.scenario(), options.seed());
            if (options.transitionDebug()) panel.enableTransitionDebug(options.seed(), 15,
                    Path.of("out", "debug", "transition-seed-" + options.seed() + ".log"));
            show(panel,
                    "NWMANAGER - " + options.scenario() + " seed " + options.seed());
            return;
        }
        show(new MapPanel(language, this::showMainMenu, options.scenario(), options.family(), options.seed()),
                "NWMANAGER - " + switch (options.scenario()) {
                    case LEGACY_2V2 -> "Legacy 2v2 seed " + options.seed();
                    case CONTROLLED_2V1 -> "Controlled 2v1";
                    case CONTROLLED_2V2 -> "Controlled 2v2 " + options.family() +
                            " seed " + options.seed();
                    case MODERN_2V2 -> "Modern 2v2 seed " + options.seed();
                    case MODERN_3V3, CONTROLLED_3V3 -> throw new IllegalStateException();
                });
    }

    private void show(JPanel panel, String title) {
        window.setTitle(title);
        window.setContentPane(panel);
        window.pack();
        window.setLocationRelativeTo(null);
        window.setVisible(true);
        panel.requestFocusInWindow();
    }

    private record LaunchOptions(SimulationScenario scenario, ScenarioFamily family, long seed,
                                 boolean transitionDebug) {
    }
}
