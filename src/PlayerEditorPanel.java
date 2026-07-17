import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.text.ParseException;

final class PlayerEditorPanel extends JPanel {
    private static final String[] FRENCH_STAT_LABELS = {
            "Jeu de jambes", "Contrôle de distance", "Précision", "Timing attaque", "Timing défense",
            "Réaction", "Agressivité", "Décision", "Sortir de sa zone", "Chamber", "Double Block"
    };
    private static final String[] CHINESE_STAT_LABELS = {
            "步法", "距離控制", "瞄準精度", "進攻時機", "防守時機",
            "反應速度", "攻擊性", "決策能力", "脫離原位", "接招反擊", "雙重格擋"
    };
    private final JTextField nameField = new JTextField();
    private final JComboBox<Tactic> tacticField = new JComboBox<>(Tactic.values());
    private final JSpinner[] statFields = new JSpinner[FRENCH_STAT_LABELS.length];
    private final JLabel titleLabel = new JLabel("", SwingConstants.CENTER), nameLabel = new JLabel(),
            tacticLabel = new JLabel();
    private final JLabel[] statLabels = new JLabel[FRENCH_STAT_LABELS.length];
    private final String frenchPlayerLabel, chinesePlayerLabel;
    private UiLanguage language = UiLanguage.FRENCH;

    PlayerEditorPanel(String frenchPlayerLabel, String chinesePlayerLabel, Color color,
                      AgentConfiguration configuration) {
        this.frenchPlayerLabel = frenchPlayerLabel;
        this.chinesePlayerLabel = chinesePlayerLabel;
        setLayout(new GridBagLayout());
        setBackground(new Color(255, 250, 239));
        setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(color, 2, true),
                new EmptyBorder(0, 18, 16, 18)));
        var constraints = new GridBagConstraints();
        constraints.gridx = 0;
        constraints.gridy = 0;
        constraints.gridwidth = 2;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.weightx = 1;
        constraints.insets = new Insets(0, -18, 18, -18);
        titleLabel.setOpaque(true);
        titleLabel.setBackground(color);
        titleLabel.setForeground(Color.WHITE);
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 20f));
        titleLabel.setBorder(new EmptyBorder(13, 8, 13, 8));
        add(titleLabel, constraints);

        constraints.gridy = 1;
        constraints.gridwidth = 1;
        constraints.weightx = 0;
        constraints.insets = new Insets(0, 0, 13, 12);
        add(nameLabel, constraints);
        nameField.setText(configuration.name());
        nameField.setColumns(14);
        constraints.gridx = 1;
        constraints.weightx = 1;
        constraints.insets = new Insets(0, 0, 13, 0);
        add(nameField, constraints);

        constraints.gridx = 0;
        constraints.gridy = 2;
        constraints.weightx = 0;
        constraints.insets = new Insets(0, 0, 13, 12);
        add(tacticLabel, constraints);
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
        constraints.gridx = 1;
        constraints.weightx = 1;
        constraints.insets = new Insets(0, 0, 13, 0);
        add(tacticField, constraints);

        var stats = configuration.stats();
        var values = new int[]{stats.footwork(), stats.distanceControl(), stats.aim(), stats.attackTiming(),
                stats.defenseTiming(), stats.reaction(), stats.aggression(), stats.decisionMaking(),
                stats.zoneExit(), stats.chamber(), stats.doubleBlock()};
        for (var n = 0; n < FRENCH_STAT_LABELS.length; ++n) {
            constraints.gridx = 0;
            constraints.gridy = n + 3;
            constraints.weightx = 1;
            constraints.insets = new Insets(0, 0, 9, 12);
            statLabels[n] = new JLabel();
            add(statLabels[n], constraints);
            statFields[n] = new JSpinner(new SpinnerNumberModel(values[n], 1, 100, 1));
            ((JSpinner.DefaultEditor) statFields[n].getEditor()).getTextField().setColumns(3);
            constraints.gridx = 1;
            constraints.weightx = 0;
            constraints.insets = new Insets(0, 0, 9, 0);
            add(statFields[n], constraints);
        }
        constraints.gridx = 0;
        constraints.gridy = FRENCH_STAT_LABELS.length + 3;
        constraints.gridwidth = 2;
        constraints.weighty = 1;
        constraints.fill = GridBagConstraints.BOTH;
        constraints.insets = new Insets(0, 0, 0, 0);
        add(Box.createGlue(), constraints);
        setLanguage(UiLanguage.FRENCH);
    }

    void setLanguage(UiLanguage language) {
        this.language = language;
        titleLabel.setText(language.text(frenchPlayerLabel, chinesePlayerLabel));
        nameLabel.setText(language.text("Nom du joueur", "玩家名稱"));
        tacticLabel.setText(language.text("Tactique", "戰術"));
        for (var n = 0; n < statLabels.length; ++n)
            statLabels[n].setText(language.text(FRENCH_STAT_LABELS[n], CHINESE_STAT_LABELS[n]));
        tacticField.repaint();
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
        return new AgentConfiguration(name, new AgentStats(value(0), value(1), value(2), value(3), value(4),
                value(5), value(6), value(7), value(8), value(9), value(10)),
                (Tactic) tacticField.getSelectedItem());
    }

    private int value(int index) {
        return (int) statFields[index].getValue();
    }
}
