import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.text.ParseException;

final class PlayerEditorPanel extends JPanel {
    private static final String[] FRENCH_STAT_LABELS = {
            "Jeu de jambes", "Contrôle de distance", "Précision", "Timing attaque", "Timing défense",
            "Réaction", "Agressivité", "Décision", "Dézonage", "Chamber", "Double Block", "Pression",
            "Volonté de 50/50", "Collectif"
    };
    private static final String[] CHINESE_STAT_LABELS = {
            "步法", "距離控制", "瞄準精度", "進攻時機", "防守時機",
            "反應速度", "攻擊性", "決策能力", "脫離傾向", "接招反擊", "雙重格擋", "施壓",
            "對刺傾向", "團隊協作"
    };
    private static final int[] TECHNICAL_STATS = {0, 1, 3, 4, 9};
    private static final int[] MENTAL_STATS = {2, 5, 7, 13};
    private static final int[] PERSONALITY_STATS = {6};

    private final JTextField nameField = new JTextField();
    private final JComboBox<Tactic> tacticField = new JComboBox<>(Tactic.values());
    private final JSpinner[] statFields = new JSpinner[FRENCH_STAT_LABELS.length];
    private final JLabel titleLabel = new JLabel("", SwingConstants.CENTER), nameLabel = new JLabel(),
            tacticLabel = new JLabel(), ratingLabel = new JLabel("", SwingConstants.CENTER),
            technicalLabel = new JLabel("", SwingConstants.CENTER), mentalLabel = new JLabel("", SwingConstants.CENTER),
            personalityLabel = new JLabel("", SwingConstants.CENTER), personalityHintLabel = new JLabel("", SwingConstants.CENTER);
    private final JLabel[] statLabels = new JLabel[FRENCH_STAT_LABELS.length];
    private final String frenchPlayerLabel, chinesePlayerLabel;
    private UiLanguage language = UiLanguage.FRENCH;

    PlayerEditorPanel(String frenchPlayerLabel, String chinesePlayerLabel, Color color,
                      AgentConfiguration configuration) {
        this.frenchPlayerLabel = frenchPlayerLabel;
        this.chinesePlayerLabel = chinesePlayerLabel;
        setLayout(new BorderLayout());
        setBackground(new Color(255, 250, 239));
        setBorder(BorderFactory.createLineBorder(color, 2, true));

        titleLabel.setOpaque(true);
        titleLabel.setBackground(color);
        titleLabel.setForeground(Color.WHITE);
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 19f));
        titleLabel.setBorder(new EmptyBorder(11, 8, 11, 8));
        add(titleLabel, BorderLayout.NORTH);

        var content = new JPanel(new BorderLayout(0, 10));
        content.setOpaque(false);
        content.setBorder(new EmptyBorder(12, 13, 13, 13));
        var identity = new JPanel(new GridBagLayout());
        identity.setOpaque(false);
        var constraints = new GridBagConstraints();
        constraints.gridy = 0;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.insets = new Insets(0, 0, 7, 8);
        identity.add(nameLabel, constraints);
        nameField.setText(configuration.name());
        nameField.setColumns(11);
        constraints.gridx = 1;
        constraints.weightx = 1;
        identity.add(nameField, constraints);
        constraints.gridx = 2;
        constraints.weightx = 0;
        identity.add(tacticLabel, constraints);
        tacticField.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                           boolean selected, boolean focused) {
                var component = super.getListCellRendererComponent(list, value, index, selected, focused);
                if (value instanceof Tactic tactic) setText(tactic.label(language));
                return component;
            }
        });
        tacticField.setSelectedItem(configuration.tactic());
        constraints.gridx = 3;
        constraints.insets = new Insets(0, 0, 7, 0);
        identity.add(tacticField, constraints);
        ratingLabel.setFont(ratingLabel.getFont().deriveFont(Font.BOLD, 12f));
        ratingLabel.setForeground(new Color(71, 61, 51));
        constraints.gridx = 0;
        constraints.gridy = 1;
        constraints.gridwidth = 4;
        constraints.insets = new Insets(1, 0, 0, 0);
        identity.add(ratingLabel, constraints);
        content.add(identity, BorderLayout.NORTH);

        var stats = configuration.stats();
        var values = new int[]{stats.footwork(), stats.distanceControl(), stats.aim(), stats.attackTiming(),
                stats.defenseTiming(), stats.reaction(), stats.aggression(), stats.decisionMaking(),
                stats.zoneExit(), stats.chamber(), stats.doubleBlock(), stats.pressure(), stats.fiftyFifty(),
                stats.teamwork()};
        for (var n = 0; n < statFields.length; ++n) {
            statLabels[n] = new JLabel();
            statFields[n] = new JSpinner(new SpinnerNumberModel(values[n], 1, 100, 1));
            ((JSpinner.DefaultEditor) statFields[n].getEditor()).getTextField().setColumns(2);
            statFields[n].addChangeListener(event -> updateRating());
        }

        var categories = new JPanel(new GridLayout(1, 3, 10, 0));
        categories.setOpaque(false);
        categories.add(createCategory(technicalLabel, TECHNICAL_STATS, color));
        categories.add(createCategory(mentalLabel, MENTAL_STATS, color));
        categories.add(createPersonalityCategory(color));
        content.add(categories, BorderLayout.CENTER);
        add(content, BorderLayout.CENTER);
        setLanguage(UiLanguage.FRENCH);
    }

    private JPanel createCategory(JLabel heading, int[] indexes, Color color) {
        var panel = new JPanel(new GridBagLayout());
        panel.setBackground(new Color(252, 246, 233));
        panel.setBorder(BorderFactory.createLineBorder(new Color(color.getRed(), color.getGreen(), color.getBlue(), 80)));
        heading.setOpaque(true);
        heading.setBackground(new Color(color.getRed(), color.getGreen(), color.getBlue(), 24));
        heading.setForeground(new Color(64, 55, 46));
        heading.setFont(heading.getFont().deriveFont(Font.BOLD, 11f));
        heading.setBorder(new EmptyBorder(7, 4, 7, 4));
        var constraints = new GridBagConstraints();
        constraints.gridx = 0;
        constraints.gridy = 0;
        constraints.gridwidth = 2;
        constraints.weightx = 1;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.insets = new Insets(0, 0, 7, 0);
        panel.add(heading, constraints);
        constraints.gridwidth = 1;
        for (var row = 0; row < indexes.length; ++row) {
            var index = indexes[row];
            constraints.gridx = 0;
            constraints.gridy = row + 1;
            constraints.weightx = 1;
            constraints.insets = new Insets(0, 8, 7, 5);
            panel.add(statLabels[index], constraints);
            constraints.gridx = 1;
            constraints.weightx = 0;
            constraints.insets = new Insets(0, 0, 7, 8);
            panel.add(statFields[index], constraints);
        }
        constraints.gridx = 0;
        constraints.gridy = indexes.length + 1;
        constraints.gridwidth = 2;
        constraints.weighty = 1;
        constraints.fill = GridBagConstraints.BOTH;
        constraints.insets = new Insets(0, 0, 0, 0);
        panel.add(Box.createGlue(), constraints);
        return panel;
    }

    private JPanel createPersonalityCategory(Color color) {
        var panel = createCategory(personalityLabel, PERSONALITY_STATS, color);
        personalityHintLabel.setFont(personalityHintLabel.getFont().deriveFont(Font.ITALIC, 9f));
        personalityHintLabel.setForeground(new Color(105, 91, 76));
        personalityHintLabel.setBorder(new EmptyBorder(4, 5, 7, 5));
        panel.add(personalityHintLabel, new GridBagConstraints(0, PERSONALITY_STATS.length + 2, 2, 1, 1, 0,
                GridBagConstraints.CENTER, GridBagConstraints.HORIZONTAL, new Insets(0, 0, 0, 0), 0, 0));
        return panel;
    }

    void setLanguage(UiLanguage language) {
        this.language = language;
        titleLabel.setText(language.text(frenchPlayerLabel, chinesePlayerLabel));
        nameLabel.setText(language.text("Nom", "姓名"));
        tacticLabel.setText(language.text("Tactique", "戰術"));
        technicalLabel.setText(language.text("TECHNIQUE", "技術能力"));
        mentalLabel.setText(language.text("MENTAL", "心理能力"));
        personalityLabel.setText(language.text("PERSONNALITÉ", "個人風格"));
        personalityHintLabel.setText(language.text("Préférences uniquement — hors niveau",
                "只改變選擇傾向，不計入等級"));
        for (var n = 0; n < statLabels.length; ++n)
            statLabels[n].setText(language.text(FRENCH_STAT_LABELS[n], CHINESE_STAT_LABELS[n]));
        tacticField.repaint();
        updateRating();
    }

    AgentConfiguration configuration() throws ParseException {
        for (var field : statFields) field.commitEdit();
        var name = nameField.getText().strip();
        var playerLabel = language.text(frenchPlayerLabel, chinesePlayerLabel);
        if (name.isEmpty())
            throw new IllegalArgumentException(language.text("Le nom de " + playerLabel + " est obligatoire.",
                    "請輸入" + playerLabel + "的名稱。"));
        if (name.length() > 18)
            throw new IllegalArgumentException(language.text(
                    "Le nom de " + playerLabel + " est limité à 18 caractères.",
                    playerLabel + "名稱不得超過 18 個字元。"));
        return new AgentConfiguration(name, currentStats(), (Tactic) tacticField.getSelectedItem());
    }

    private AgentStats currentStats() {
        return new AgentStats(value(0), value(1), value(2), value(3), value(4), value(5), value(6), value(7),
                50, value(9), 50, 50, 50, value(13));
    }

    private void updateRating() {
        if (statFields[0] == null) return;
        var stats = currentStats();
        var profile = RatingProfile.MODERN;
        ratingLabel.setText(language.text(
                "Niveau : %s %d  ·  Technique %d  ·  Mental %d".formatted(
                        stats.playerLevel(profile).label(language), stats.overallRating(profile),
                        stats.technicalRating(profile), stats.mentalRating(profile)),
                "等級：%s %d  ·  技術 %d  ·  心理 %d".formatted(
                        stats.playerLevel(profile).label(language), stats.overallRating(profile),
                        stats.technicalRating(profile), stats.mentalRating(profile))));
    }

    private int value(int index) {
        return (int) statFields[index].getValue();
    }
}
