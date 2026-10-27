import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionEvent;

@SuppressWarnings("serial")
final class MenuPanel extends JPanel {
    enum Page { MAIN, PLAY }
    enum MenuAction { PLAY, FRIENDLY_MATCH, EXPERIMENTAL_3V3, BACK, QUIT }

    private final Page page;
    private final MenuHandler handler;
    private final JLabel sectionLabel = new JLabel(), titleLabel = new JLabel(), subtitleLabel = new JLabel(),
            modeTitleLabel = new JLabel(), modeDescriptionLabel = new JLabel(), footerLabel = new JLabel();
    private final JButton languageButton = new JButton(), primaryButton = new JButton(),
            experimentalButton = new JButton(), secondaryButton = new JButton();
    private UiLanguage language;

    MenuPanel(Page page, UiLanguage language, MenuHandler handler) {
        this.page = page;
        this.language = language;
        this.handler = handler;
        setPreferredSize(new Dimension(1000, 650));
        setLayout(new BorderLayout());
        setBorder(new EmptyBorder(24, 32, 20, 32));

        var topBar = new JPanel(new BorderLayout());
        topBar.setOpaque(false);
        var brand = new JLabel("NWMANAGER  /  2v2 + 3v3");
        brand.setFont(brand.getFont().deriveFont(Font.BOLD, 14f));
        brand.setForeground(new Color(69, 59, 49));
        topBar.add(brand, BorderLayout.WEST);
        languageButton.setPreferredSize(new Dimension(104, 31));
        languageButton.setFocusable(false);
        languageButton.addActionListener(event -> toggleLanguage());
        topBar.add(languageButton, BorderLayout.EAST);
        add(topBar, BorderLayout.NORTH);

        var card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(new Color(255, 251, 242, 242));
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(166, 139, 99), 1, true),
                new EmptyBorder(35, 48, 39, 48)));
        card.setPreferredSize(new Dimension(510, page == Page.MAIN ? 430 : 520));
        card.setMaximumSize(card.getPreferredSize());

        configureLabel(sectionLabel, Font.BOLD, 12f, new Color(151, 91, 43));
        configureLabel(titleLabel, Font.BOLD, 38f, new Color(50, 44, 38));
        configureLabel(subtitleLabel, Font.PLAIN, 14f, new Color(91, 79, 66));
        card.add(sectionLabel);
        card.add(Box.createVerticalStrut(8));
        card.add(titleLabel);
        card.add(Box.createVerticalStrut(9));
        card.add(subtitleLabel);
        card.add(Box.createVerticalStrut(28));

        if (page == Page.PLAY) {
            var modeCard = new JPanel();
            modeCard.setLayout(new BoxLayout(modeCard, BoxLayout.Y_AXIS));
            modeCard.setBackground(new Color(241, 225, 197));
            modeCard.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(new Color(190, 156, 107), 1, true),
                    new EmptyBorder(16, 20, 16, 20)));
            modeCard.setAlignmentX(Component.CENTER_ALIGNMENT);
            modeCard.setMaximumSize(new Dimension(400, 98));
            configureLabel(modeTitleLabel, Font.BOLD, 18f, new Color(55, 48, 42));
            configureLabel(modeDescriptionLabel, Font.PLAIN, 12f, new Color(91, 79, 66));
            modeCard.add(modeTitleLabel);
            modeCard.add(Box.createVerticalStrut(6));
            modeCard.add(modeDescriptionLabel);
            card.add(modeCard);
            card.add(Box.createVerticalStrut(22));
        } else card.add(Box.createVerticalGlue());

        configureButton(primaryButton, new Color(61, 115, 75), Color.WHITE, 400, 50);
        primaryButton.addActionListener(event -> handler.handle(
                page == Page.MAIN ? MenuAction.PLAY : MenuAction.FRIENDLY_MATCH, this.language));
        card.add(primaryButton);
        if (page == Page.PLAY) {
            card.add(Box.createVerticalStrut(10));
            configureButton(experimentalButton, new Color(63, 94, 139), Color.WHITE, 400, 46);
            experimentalButton.addActionListener(event ->
                    handler.handle(MenuAction.EXPERIMENTAL_3V3, this.language));
            card.add(experimentalButton);
        }
        card.add(Box.createVerticalStrut(12));
        configureButton(secondaryButton, new Color(225, 211, 188), new Color(69, 59, 49), 400, 42);
        secondaryButton.addActionListener(event -> handler.handle(
                page == Page.MAIN ? MenuAction.QUIT : MenuAction.BACK, this.language));
        card.add(secondaryButton);

        var center = new JPanel(new GridBagLayout());
        center.setOpaque(false);
        center.add(card);
        add(center, BorderLayout.CENTER);

        footerLabel.setHorizontalAlignment(SwingConstants.CENTER);
        footerLabel.setForeground(new Color(91, 79, 66));
        add(footerLabel, BorderLayout.SOUTH);
        bind("L", "language", this::toggleLanguage);
        if (page == Page.PLAY) bind("ESCAPE", "back", () -> handler.handle(MenuAction.BACK, this.language));
        updateLanguage();
    }

    private void configureLabel(JLabel label, int style, float size, Color color) {
        label.setAlignmentX(Component.CENTER_ALIGNMENT);
        label.setHorizontalAlignment(SwingConstants.CENTER);
        label.setFont(label.getFont().deriveFont(style, size));
        label.setForeground(color);
    }

    private void configureButton(JButton button, Color background, Color foreground, int width, int height) {
        button.setAlignmentX(Component.CENTER_ALIGNMENT);
        button.setMaximumSize(new Dimension(width, height));
        button.setPreferredSize(new Dimension(width, height));
        button.setFont(button.getFont().deriveFont(Font.BOLD, 16f));
        button.setBackground(background);
        button.setForeground(foreground);
        button.setFocusPainted(false);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    private void bind(String key, String name, Runnable action) {
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(key), name);
        getActionMap().put(name, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent event) {
                action.run();
            }
        });
    }

    @Override
    public void addNotify() {
        super.addNotify();
        getRootPane().setDefaultButton(primaryButton);
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        var g = (Graphics2D) graphics.create();
        g.setPaint(new GradientPaint(0, 0, new Color(244, 226, 194), getWidth(), getHeight(),
                new Color(214, 184, 141)));
        g.fillRect(0, 0, getWidth(), getHeight());
        g.setColor(new Color(123, 91, 55, 36));
        g.setStroke(new BasicStroke(2f));
        g.drawOval(-130, getHeight() - 310, 430, 430);
        g.drawOval(getWidth() - 250, -160, 410, 410);
        g.drawLine(0, getHeight() - 105, 330, getHeight() - 435);
        g.drawLine(getWidth() - 330, 340, getWidth(), 10);
        g.dispose();
    }

    private void toggleLanguage() {
        language = language.next();
        updateLanguage();
    }

    private void updateLanguage() {
        var main = page == Page.MAIN;
        sectionLabel.setText(language.text(main ? "SIMULATION TACTIQUE À LA BAÏONNETTE" : "MODE DE JEU",
                main ? "刺刀戰術模擬" : "遊戲模式"));
        titleLabel.setText(main ? "NWMANAGER" : language.text("JOUER", "開始遊戲"));
        subtitleLabel.setText(language.text(
                main ? "Dirigez vos Agents. Observez leurs décisions. Survivez." :
                        "Choisissez le type de rencontre à préparer.",
                main ? "管理你的 Agent、觀察決策，並存活下來。" : "選擇要準備的比賽類型。"));
        modeTitleLabel.setText(language.text("MATCH AMICAL 2v2", "2v2 友誼賽"));
        modeDescriptionLabel.setText(language.text("Créez deux équipes et lancez un combat sans enjeu.",
                "建立兩支隊伍，進行一場不影響戰績的比賽。"));
        primaryButton.setText(language.text(main ? "JOUER" : "PRÉPARER LE MATCH",
                main ? "開始遊戲" : "準備比賽"));
        experimentalButton.setText(language.text("MATCH EXPÉRIMENTAL 3v3", "實驗 3v3 比賽"));
        secondaryButton.setText(language.text(main ? "QUITTER" : "RETOUR",
                main ? "離開遊戲" : "返回"));
        languageButton.setText(language == UiLanguage.FRENCH ? "中文" : "Français");
        languageButton.setToolTipText(language.text("Changer la langue", "切換語言"));
        footerLabel.setText(language.text("L : langue  •  Entrée : confirmer",
                "L：切換語言  •  Enter：確認"));
    }

    @FunctionalInterface
    interface MenuHandler {
        void handle(MenuAction action, UiLanguage language);
    }
}
