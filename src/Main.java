import javax.swing.*;

final class Main {
    private final JFrame window = new JFrame("NWMANAGER");

    static void main() {
        SwingUtilities.invokeLater(() -> new Main().start());
    }

    private void start() {
        window.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        window.setResizable(false);
        showMainMenu(UiLanguage.FRENCH);
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
            else if (action == MenuPanel.MenuAction.BACK) showMainMenu(selectedLanguage);
        }), "NWMANAGER - " + language.text("Jouer", "開始遊戲"));
    }

    private void showSetup(UiLanguage language) {
        show(new SetupPanel(language, this::showCombat, this::showPlayMenu),
                "NWMANAGER - " + language.text("Match amical", "友誼賽"));
    }

    private void showCombat(TeamConfiguration blueTeam, TeamConfiguration redTeam, UiLanguage language) {
        show(new MapPanel(blueTeam, redTeam, language, this::showMainMenu),
                "NWMANAGER - " + blueTeam.name() + " vs " + redTeam.name());
    }

    private void show(JPanel panel, String title) {
        window.setTitle(title);
        window.setContentPane(panel);
        window.pack();
        window.setLocationRelativeTo(null);
        window.setVisible(true);
        panel.requestFocusInWindow();
    }
}
