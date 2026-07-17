import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.text.ParseException;

final class SetupPanel extends JPanel {
    private final PlayerEditorPanel blueOneEditor = new PlayerEditorPanel("JOUEUR BLEU 1", "藍方玩家 1",
            new Color(48, 112, 220), AgentConfiguration.defaultBlue());
    private final PlayerEditorPanel blueTwoEditor = new PlayerEditorPanel("JOUEUR BLEU 2", "藍方玩家 2",
            new Color(40, 151, 184), AgentConfiguration.defaultBlueTwo());
    private final PlayerEditorPanel redOneEditor = new PlayerEditorPanel("JOUEUR ROUGE 1", "紅方玩家 1",
            new Color(215, 62, 65), AgentConfiguration.defaultRed());
    private final PlayerEditorPanel redTwoEditor = new PlayerEditorPanel("JOUEUR ROUGE 2", "紅方玩家 2",
            new Color(221, 111, 48), AgentConfiguration.defaultRedTwo());
    private final JLabel titleLabel = new JLabel(), subtitleLabel = new JLabel(), hintLabel = new JLabel(),
            stepLabel = new JLabel(), blueTeamNameLabel = new JLabel(), redTeamNameLabel = new JLabel();
    private final JTextField blueTeamNameField = new JTextField(), redTeamNameField = new JTextField();
    private final JButton languageButton = new JButton(), startButton = new JButton(), backButton = new JButton();
    private final JTabbedPane teamTabs = new JTabbedPane();
    private UiLanguage language;

    SetupPanel(UiLanguage language, StartHandler startHandler, BackHandler backHandler) {
        this.language = language;
        setPreferredSize(new Dimension(880, 790));
        setLayout(new BorderLayout(24, 14));
        setBackground(new Color(238, 214, 175));
        setBorder(new EmptyBorder(24, 28, 22, 28));

        var header = new JPanel();
        header.setOpaque(false);
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        titleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 27f));
        titleLabel.setForeground(new Color(55, 48, 42));
        subtitleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        subtitleLabel.setForeground(new Color(86, 76, 64));
        stepLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        stepLabel.setFont(stepLabel.getFont().deriveFont(Font.BOLD, 11f));
        stepLabel.setForeground(new Color(151, 91, 43));
        header.add(stepLabel);
        header.add(Box.createVerticalStrut(5));
        header.add(titleLabel);
        header.add(Box.createVerticalStrut(7));
        header.add(subtitleLabel);

        var headerArea = new JPanel(new BorderLayout());
        headerArea.setOpaque(false);
        headerArea.add(Box.createHorizontalStrut(98), BorderLayout.WEST);
        headerArea.add(header, BorderLayout.CENTER);
        languageButton.setPreferredSize(new Dimension(98, 29));
        languageButton.setFocusable(false);
        languageButton.addActionListener(event -> toggleLanguage());
        headerArea.add(languageButton, BorderLayout.EAST);
        add(headerArea, BorderLayout.NORTH);

        teamTabs.setFont(teamTabs.getFont().deriveFont(Font.BOLD, 14f));
        blueTeamNameField.setText(language.text("Mousquetaires bleus", "藍色火槍隊"));
        redTeamNameField.setText(language.text("Garde rouge", "紅色近衛隊"));
        teamTabs.addTab("", createTeamPanel(blueTeamNameLabel, blueTeamNameField, blueOneEditor, blueTwoEditor));
        teamTabs.addTab("", createTeamPanel(redTeamNameLabel, redTeamNameField, redOneEditor, redTwoEditor));
        add(teamTabs, BorderLayout.CENTER);

        var footer = new JPanel();
        footer.setOpaque(false);
        footer.setLayout(new BoxLayout(footer, BoxLayout.Y_AXIS));
        var buttonRow = new JPanel();
        buttonRow.setOpaque(false);
        buttonRow.setLayout(new BoxLayout(buttonRow, BoxLayout.X_AXIS));
        backButton.setFont(backButton.getFont().deriveFont(Font.BOLD, 15f));
        backButton.setForeground(new Color(69, 59, 49));
        backButton.setBackground(new Color(225, 211, 188));
        backButton.setFocusPainted(false);
        backButton.setPreferredSize(new Dimension(170, 46));
        backButton.setMaximumSize(new Dimension(170, 46));
        backButton.addActionListener(event -> backHandler.back(this.language));
        startButton.setFont(startButton.getFont().deriveFont(Font.BOLD, 19f));
        startButton.setForeground(Color.WHITE);
        startButton.setBackground(new Color(66, 126, 80));
        startButton.setFocusPainted(false);
        startButton.setPreferredSize(new Dimension(250, 46));
        startButton.setMaximumSize(new Dimension(250, 46));
        startButton.addActionListener(event -> start(startHandler));
        buttonRow.add(Box.createHorizontalGlue());
        buttonRow.add(backButton);
        buttonRow.add(Box.createHorizontalStrut(14));
        buttonRow.add(startButton);
        buttonRow.add(Box.createHorizontalGlue());
        hintLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        hintLabel.setForeground(new Color(86, 76, 64));
        footer.add(buttonRow);
        footer.add(Box.createVerticalStrut(8));
        footer.add(hintLabel);
        add(footer, BorderLayout.SOUTH);
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("L"), "language");
        getActionMap().put("language", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent event) {
                toggleLanguage();
            }
        });
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("ESCAPE"), "back");
        getActionMap().put("back", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent event) {
                backHandler.back(SetupPanel.this.language);
            }
        });
        updateLanguage();
    }

    private JPanel createTeamPanel(JLabel teamNameLabel, JTextField teamNameField,
                                   PlayerEditorPanel first, PlayerEditorPanel second) {
        var panel = new JPanel(new BorderLayout(0, 13));
        panel.setBackground(new Color(238, 214, 175));
        panel.setBorder(new EmptyBorder(14, 0, 0, 0));
        var teamIdentity = new JPanel(new BorderLayout(12, 0));
        teamIdentity.setOpaque(false);
        teamIdentity.setBorder(new EmptyBorder(0, 5, 0, 5));
        teamNameLabel.setFont(teamNameLabel.getFont().deriveFont(Font.BOLD, 13f));
        teamIdentity.add(teamNameLabel, BorderLayout.WEST);
        teamNameField.setFont(teamNameField.getFont().deriveFont(Font.BOLD, 14f));
        teamIdentity.add(teamNameField, BorderLayout.CENTER);
        panel.add(teamIdentity, BorderLayout.NORTH);
        var players = new JPanel(new GridLayout(1, 2, 22, 0));
        players.setOpaque(false);
        players.add(first);
        players.add(second);
        panel.add(players, BorderLayout.CENTER);
        return panel;
    }

    @Override
    public void addNotify() {
        super.addNotify();
        getRootPane().setDefaultButton(startButton);
    }

    private void toggleLanguage() {
        language = language.next();
        updateLanguage();
    }

    private void updateLanguage() {
        stepLabel.setText(language.text("JOUER  ›  MATCH AMICAL  ›  CRÉATION DES ÉQUIPES",
                "開始遊戲  ›  友誼賽  ›  建立隊伍"));
        titleLabel.setText(language.text("CRÉATION DES ÉQUIPES", "建立兩支隊伍"));
        subtitleLabel.setText(language.text(
                "Nommez chaque équipe, puis configurez ses deux joueurs.",
                "為兩支隊伍命名，並設定各自的兩名玩家。"));
        languageButton.setText(language == UiLanguage.FRENCH ? "中文" : "Français");
        languageButton.setToolTipText(language.text("Changer la langue", "切換語言"));
        startButton.setText(language.text("JOUER LE MATCH", "開始比賽"));
        backButton.setText(language.text("RETOUR", "返回"));
        hintLabel.setText(language.text(
                "L : langue  •  Échap : retour  •  R recommencera ensuite le même match.",
                "L：切換語言  •  Esc：返回  •  進入比賽後按 R 可重新開始同一場比賽。"));
        teamTabs.setTitleAt(0, language.text("ÉQUIPE BLEUE", "藍隊"));
        teamTabs.setTitleAt(1, language.text("ÉQUIPE ROUGE", "紅隊"));
        blueTeamNameLabel.setText(language.text("Nom de l'équipe bleue", "藍隊名稱"));
        redTeamNameLabel.setText(language.text("Nom de l'équipe rouge", "紅隊名稱"));
        teamTabs.setForegroundAt(0, new Color(35, 91, 185));
        teamTabs.setForegroundAt(1, new Color(184, 45, 48));
        blueOneEditor.setLanguage(language);
        blueTwoEditor.setLanguage(language);
        redOneEditor.setLanguage(language);
        redTwoEditor.setLanguage(language);
    }

    private void start(StartHandler startHandler) {
        try {
            var blueTeam = teamConfiguration(blueTeamNameField, blueOneEditor, blueTwoEditor,
                    language.text("bleue", "藍隊"));
            var redTeam = teamConfiguration(redTeamNameField, redOneEditor, redTwoEditor,
                    language.text("rouge", "紅隊"));
            startHandler.start(blueTeam, redTeam, language);
        } catch (IllegalArgumentException exception) {
            JOptionPane.showMessageDialog(this, exception.getMessage(),
                    language.text("Valeur invalide", "輸入錯誤"), JOptionPane.ERROR_MESSAGE);
        } catch (ParseException exception) {
            JOptionPane.showMessageDialog(this, language.text(
                            "Une statistique contient une valeur invalide.", "能力值格式錯誤，請輸入 1 到 100 的整數。"),
                    language.text("Valeur invalide", "輸入錯誤"), JOptionPane.ERROR_MESSAGE);
        }
    }

    private TeamConfiguration teamConfiguration(JTextField nameField, PlayerEditorPanel first,
                                                PlayerEditorPanel second, String teamLabel) throws ParseException {
        var name = nameField.getText().strip();
        if (name.isEmpty())
            throw new IllegalArgumentException(language.text("Le nom de l'équipe " + teamLabel + " est obligatoire.",
                    "請輸入" + teamLabel + "名稱。"));
        if (name.length() > 24)
            throw new IllegalArgumentException(language.text("Le nom de l'équipe " + teamLabel +
                    " est limité à 24 caractères.", teamLabel + "名稱不得超過 24 個字元。"));
        return new TeamConfiguration(name, first.configuration(), second.configuration());
    }

    @FunctionalInterface
    interface StartHandler {
        void start(TeamConfiguration blueTeam, TeamConfiguration redTeam, UiLanguage language);
    }

    @FunctionalInterface
    interface BackHandler {
        void back(UiLanguage language);
    }
}
