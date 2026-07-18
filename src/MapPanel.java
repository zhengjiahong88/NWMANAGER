import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;

public final class MapPanel extends JPanel {
    private static final int PANEL_WIDTH = 1180, PANEL_HEIGHT = 820, INFO_WIDTH = 220, MARGIN = 28;
    private static final double[] SPEED_VALUES = {0.25, 0.5, 1, 2, 4};
    private static final String[] SPEED_LABELS = {"0.25×", "0.5×", "1×", "2×", "4×"};
    private final DuelSimulation simulation;
    private final JButton languageButton = new JButton(), menuButton = new JButton();
    private final JButton[] speedButtons = new JButton[SPEED_VALUES.length];
    private final Timer timer;
    private final transient MenuHandler menuHandler;
    private final String blueTeamName, redTeamName;
    private UiLanguage language = UiLanguage.FRENCH;
    private boolean paused;
    private double speed = 1;
    private long lastTick = System.nanoTime();

    MapPanel() {
        this(AgentConfiguration.defaultBlue(), AgentConfiguration.defaultBlueTwo(), AgentConfiguration.defaultRed(),
                AgentConfiguration.defaultRedTwo(), UiLanguage.FRENCH);
    }

    MapPanel(AgentConfiguration blue, AgentConfiguration red) {
        this(blue, AgentConfiguration.defaultBlueTwo(), red, AgentConfiguration.defaultRedTwo(), UiLanguage.FRENCH);
    }

    MapPanel(AgentConfiguration blue, AgentConfiguration red, UiLanguage language) {
        this(blue, AgentConfiguration.defaultBlueTwo(), red, AgentConfiguration.defaultRedTwo(), language);
    }

    MapPanel(AgentConfiguration blueOne, AgentConfiguration blueTwo, AgentConfiguration redOne,
             AgentConfiguration redTwo, UiLanguage language) {
        this(blueOne, blueTwo, redOne, redTwo, language, null);
    }

    MapPanel(AgentConfiguration blueOne, AgentConfiguration blueTwo, AgentConfiguration redOne,
             AgentConfiguration redTwo, UiLanguage language, MenuHandler menuHandler) {
        this(blueOne, blueTwo, redOne, redTwo, language, menuHandler,
                language.text("Équipe bleue", "藍隊"), language.text("Équipe rouge", "紅隊"));
    }

    MapPanel(TeamConfiguration blueTeam, TeamConfiguration redTeam, UiLanguage language, MenuHandler menuHandler) {
        this(blueTeam.firstPlayer(), blueTeam.secondPlayer(), redTeam.firstPlayer(), redTeam.secondPlayer(),
                language, menuHandler, blueTeam.name(), redTeam.name());
    }

    private MapPanel(AgentConfiguration blueOne, AgentConfiguration blueTwo, AgentConfiguration redOne,
                     AgentConfiguration redTwo, UiLanguage language, MenuHandler menuHandler,
                     String blueTeamName, String redTeamName) {
        this.language = language;
        this.menuHandler = menuHandler;
        this.blueTeamName = blueTeamName;
        this.redTeamName = redTeamName;
        simulation = new DuelSimulation(blueOne, blueTwo, redOne, redTwo);
        setPreferredSize(new Dimension(PANEL_WIDTH, PANEL_HEIGHT));
        setBackground(new Color(238, 214, 175));
        setFocusable(true);
        setLayout(null);
        languageButton.setBounds(PANEL_WIDTH - 126, 17, 98, 29);
        languageButton.setFocusable(false);
        languageButton.addActionListener(event -> toggleLanguage());
        add(languageButton);
        menuButton.setBounds(PANEL_WIDTH - 236, 17, 100, 29);
        menuButton.setFocusable(false);
        menuButton.setVisible(menuHandler != null);
        menuButton.addActionListener(event -> returnToMenu());
        add(menuButton);
        for (var n = 0; n < SPEED_VALUES.length; ++n) {
            var selectedSpeed = SPEED_VALUES[n];
            speedButtons[n] = new JButton(SPEED_LABELS[n]);
            speedButtons[n].setBounds(MARGIN + n * 52, 17, 48, 29);
            speedButtons[n].setMargin(new Insets(2, 3, 2, 3));
            speedButtons[n].setFocusable(false);
            speedButtons[n].addActionListener(event -> setSpeed(selectedSpeed));
            add(speedButtons[n]);
        }
        updateLanguageButton();
        setSpeed(1);
        bind("SPACE", "pause", () -> paused = !paused);
        bind("R", "reset", this::reset);
        bind("L", "language", this::toggleLanguage);
        bind("0", "speed025", () -> setSpeed(0.25));
        bind("5", "speed05", () -> setSpeed(0.5));
        bind("1", "speed1", () -> setSpeed(1));
        bind("2", "speed2", () -> setSpeed(2));
        bind("4", "speed4", () -> setSpeed(4));
        if (menuHandler != null) bind("ESCAPE", "menu", this::returnToMenu);
        timer = new Timer(16, this::tick);
        timer.start();
    }

    private void bind(String key, String name, Runnable action) {
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(key), name);
        getActionMap().put(name, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                action.run();
                repaint();
            }
        });
    }

    private void toggleLanguage() {
        language = language.next();
        updateLanguageButton();
        repaint();
    }

    private void updateLanguageButton() {
        languageButton.setText(language == UiLanguage.FRENCH ? "中文" : "Français");
        languageButton.setToolTipText(text("Changer la langue", "切換語言"));
        menuButton.setText(text("MENU", "主選單"));
        menuButton.setToolTipText(text("Retourner au menu principal", "返回主選單"));
    }

    private void returnToMenu() {
        if (menuHandler == null) return;
        timer.stop();
        menuHandler.open(language);
    }

    @Override
    public void removeNotify() {
        timer.stop();
        super.removeNotify();
    }

    private void setSpeed(double speed) {
        this.speed = speed;
        for (var n = 0; n < speedButtons.length; ++n) speedButtons[n].setEnabled(SPEED_VALUES[n] != speed);
        repaint();
    }

    private String text(String french, String chinese) {
        return language.text(french, chinese);
    }

    private void reset() {
        simulation.reset();
        paused = false;
        lastTick = System.nanoTime();
    }

    private void tick(ActionEvent event) {
        var now = System.nanoTime();
        var elapsed = Math.min(0.05, (now - lastTick) / 1_000_000_000.0) * speed;
        lastTick = now;
        if (!paused) {
            var steps = Math.max(1, (int) Math.ceil(elapsed / (1.0 / 120.0)));
            var dt = elapsed / steps;
            for (var n = 0; n < steps; ++n) simulation.update(dt);
        }
        repaint();
    }

    @FunctionalInterface
    interface MenuHandler {
        void open(UiLanguage language);
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        var g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        var arenaX = INFO_WIDTH + MARGIN * 2;
        var arenaY = MARGIN + 42;
        var arenaWidth = getWidth() - INFO_WIDTH * 2 - MARGIN * 4;
        var arenaHeight = getHeight() - MARGIN * 2 - 86;
        var scale = Math.min(arenaWidth / DuelSimulation.ARENA_WIDTH, arenaHeight / DuelSimulation.ARENA_HEIGHT);
        var drawWidth = DuelSimulation.ARENA_WIDTH * scale;
        var drawHeight = DuelSimulation.ARENA_HEIGHT * scale;
        var originX = arenaX + (arenaWidth - drawWidth) / 2;
        var originY = arenaY + (arenaHeight - drawHeight) / 2;

        paintArena(g, originX, originY, drawWidth, drawHeight, scale);
        paintTargetLines(g, scale, originX, originY);
        for (var agent : simulation.agents) agent.paint(g, scale, originX, originY, language);
        paintAgentInfo(g, simulation.blueTeam[0], MARGIN, 78);
        paintAgentInfo(g, simulation.blueTeam[1], MARGIN, 424);
        paintAgentInfo(g, simulation.redTeam[0], getWidth() - INFO_WIDTH - MARGIN, 78);
        paintAgentInfo(g, simulation.redTeam[1], getWidth() - INFO_WIDTH - MARGIN, 424);
        paintHeader(g);
        paintControls(g);
        g.dispose();
    }

    private void paintArena(Graphics2D g, double x, double y, double width, double height, double scale) {
        g.setColor(new Color(204, 181, 142));
        g.fillRoundRect((int) x, (int) y, (int) width, (int) height, 22, 22);
        g.setColor(new Color(245, 226, 191));
        g.fillRoundRect((int) (x + 7), (int) (y + 7), (int) (width - 14), (int) (height - 14), 16, 16);
        g.setColor(new Color(165, 139, 100));
        g.setStroke(new BasicStroke(2));
        g.drawRoundRect((int) x, (int) y, (int) width, (int) height, 22, 22);
        g.setColor(new Color(180, 153, 115, 55));
        for (var n = 1; n < DuelSimulation.ARENA_WIDTH; ++n)
            g.drawLine((int) (x + n * scale), (int) y, (int) (x + n * scale), (int) (y + height));
        for (var n = 1; n < DuelSimulation.ARENA_HEIGHT; ++n)
            g.drawLine((int) x, (int) (y + n * scale), (int) (x + width), (int) (y + n * scale));
    }

    private void paintTargetLines(Graphics2D g, double scale, double originX, double originY) {
        var originalStroke = g.getStroke();
        g.setStroke(new BasicStroke(1.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND,
                0, new float[]{6, 7}, 0));
        for (var agent : simulation.agents) {
            if (!agent.alive || agent.target == null || !agent.target.alive) continue;
            g.setColor(new Color(agent.color.getRed(), agent.color.getGreen(), agent.color.getBlue(), 72));
            var startX = (int) (originX + agent.x * scale);
            var startY = (int) (originY + agent.y * scale);
            var endX = (int) (originX + agent.target.x * scale);
            var endY = (int) (originY + agent.target.y * scale);
            g.drawLine(startX, startY, endX, endY);
            g.drawOval(endX - 5, endY - 5, 10, 10);
            if (agent.dezoneSwitchTarget == null || agent.dezoneSwitchCommitted ||
                    !agent.dezoneSwitchTarget.alive) continue;
            g.setColor(new Color(agent.color.getRed(), agent.color.getGreen(), agent.color.getBlue(), 145));
            var switchX = (int) (originX + agent.dezoneSwitchTarget.x * scale);
            var switchY = (int) (originY + agent.dezoneSwitchTarget.y * scale);
            g.drawLine(startX, startY, switchX, switchY);
            g.drawRect(switchX - 5, switchY - 5, 10, 10);
        }
        g.setStroke(originalStroke);
    }

    private void paintAgentInfo(Graphics2D g, Musketeer agent, int x, int y) {
        g.setColor(new Color(255, 250, 239, 225));
        g.fillRoundRect(x, y, INFO_WIDTH, 340, 18, 18);
        g.setColor(agent.color);
        g.fillRoundRect(x, y, INFO_WIDTH, 38, 18, 18);
        g.fillRect(x, y + 19, INFO_WIDTH, 19);
        g.setColor(Color.WHITE);
        g.setFont(getFont().deriveFont(Font.BOLD, 16f));
        g.drawString(agent.name(language), x + 13, y + 25);
        g.setColor(new Color(55, 48, 42));
        g.setFont(getFont().deriveFont(Font.BOLD, 12f));
        g.drawString(text("Vie : ", "生命：") + agent.hitPoints + " / 100", x + 13, y + 57);
        var tacticText = text("T : ", "戰術：") + agent.getTactic().label(language);
        g.setFont(getFont().deriveFont(Font.BOLD, 10f));
        g.drawString(tacticText, x + INFO_WIDTH - 13 - g.getFontMetrics().stringWidth(tacticText), y + 57);
        g.setColor(new Color(215, 205, 188));
        g.fillRoundRect(x + 13, y + 66, INFO_WIDTH - 26, 9, 9, 9);
        g.setColor(agent.hitPoints > 50 ? new Color(73, 151, 91) : new Color(202, 73, 58));
        g.fillRoundRect(x + 13, y + 66, (INFO_WIDTH - 26) * agent.hitPoints / 100, 9, 9, 9);
        g.setColor(new Color(55, 48, 42));
        g.setFont(getFont().deriveFont(Font.PLAIN, 11f));
        compactStat(g, text("Jambes", "步法"), agent.getStats().footwork(), x + 13, y + 96);
        compactStat(g, text("Distance", "距離"), agent.getStats().distanceControl(), x + 13, y + 112);
        compactStat(g, text("Précision", "瞄準"), agent.getStats().aim(), x + 13, y + 128);
        compactStat(g, text("Attaque", "進攻"), agent.getStats().attackTiming(), x + 13, y + 144);
        compactStat(g, text("Défense", "防守"), agent.getStats().defenseTiming(), x + 13, y + 160);
        compactStat(g, text("Double block", "雙重格擋"), agent.getStats().doubleBlock(), x + 13, y + 176);
        compactStat(g, text("Réaction", "反應"), agent.getStats().reaction(), x + 120, y + 96);
        compactStat(g, text("Agressivité", "攻擊性"), agent.getStats().aggression(), x + 120, y + 112);
        compactStat(g, text("Décision", "決策"), agent.getStats().decisionMaking(), x + 120, y + 128);
        compactStat(g, text("Dézonage", "脫離"), agent.getStats().zoneExit(), x + 120, y + 144);
        compactStat(g, text("Chamber", "接招"), agent.getStats().chamber(), x + 120, y + 160);

        g.setFont(getFont().deriveFont(Font.BOLD, 11f));
        var stateText = text("État : ", "狀態：") + agent.state.label(language) + " · " +
                agent.engagementPhase.label(language);
        g.drawString(fitText(g, stateText, INFO_WIDTH - 26), x + 13, y + 200);
        var decisionText = text("Décision : ", "決策：") + agent.decision.label(language);
        g.drawString(fitText(g, decisionText, INFO_WIDTH - 26), x + 13, y + 222);
        g.setFont(getFont().deriveFont(Font.PLAIN, 11f));
        var target = agent.alive && agent.target != null && agent.target.alive ? agent.target : null;
        g.drawString(fitText(g, text("Adversaire : ", "目標：") +
                (target == null ? "—" : target.name(language)), INFO_WIDTH - 26), x + 13, y + 244);
        var distanceText = target == null ? text("Distance : —", "距離：—") :
                text("Distance : %.2f m", "距離：%.2f 公尺").formatted(
                        Math.hypot(target.x - agent.x, target.y - agent.y));
        g.drawString(distanceText, x + 13, y + 264);
        var backProtectionText = text("Dos : ", "護背：") + agent.getStats().backProtection();
        g.setColor(agent.backExposed ? new Color(190, 45, 45) : new Color(55, 48, 42));
        g.drawString(backProtectionText,
                x + INFO_WIDTH - 13 - g.getFontMetrics().stringWidth(backProtectionText), y + 264);
        var hasDirection = agent.isGuarding() || agent.state == CombatState.FEINT ||
                agent.state == CombatState.ATTACK_PREPARE ||
                agent.state == CombatState.ATTACK_HOLD || agent.state == CombatState.CHAMBER_PREPARE ||
                agent.state == CombatState.CHAMBER_HOLD || agent.isAttacking() || agent.state == CombatState.RECOVERY;
        var directionText = "—";
        var directionColor = new Color(110, 105, 98);
        if (agent.isGuarding()) {
            directionText = (agent.state == CombatState.DOUBLE_BLOCK ? "⇅ " : agent.blockDirection.symbol + " ") +
                    agent.blockDirection.label(language);
            directionColor = agent.blockDirection.color;
        } else if (hasDirection) {
            directionText = agent.attackDirection.symbol + " " + agent.attackDirection.label(language);
            if (agent.attackManipulation != AttackManipulation.NEUTRAL && agent.isAttacking())
                directionText += " · " + agent.attackManipulation.label(language);
            directionColor = agent.attackDirection.color;
        }
        g.setColor(directionColor);
        g.drawString(text("Combat : ", "攻防：") + directionText, x + 13, y + 284);
        g.setColor(new Color(55, 48, 42));
        var attacking = agent.state == CombatState.FEINT || agent.state == CombatState.ATTACK_PREPARE ||
                agent.state == CombatState.ATTACK_HOLD ||
                agent.state == CombatState.CHAMBER_PREPARE || agent.state == CombatState.CHAMBER_HOLD ||
                agent.isAttacking() || agent.state == CombatState.RECOVERY;
        g.drawString(text("Zone / hauteur : ", "部位／高度：") + (attacking ?
                        agent.intendedZone.label(language) + "  %.2f m".formatted(agent.attackHeight) : "—"),
                x + 13, y + 304);
        g.drawString(text("Angles H/V/A : ", "角度 水／垂／刺：") + "%.0f°/%+.0f°/%+.0f°".formatted(
                        Math.toDegrees(agent.angle), Math.toDegrees(agent.pitch), Math.toDegrees(agent.weaponYaw)),
                x + 13, y + 320);
        g.setColor(agent.lastHitZone == null ? new Color(110, 105, 98) : new Color(170, 35, 35));
        g.drawString(text("Blessure : ", "受傷：") + (agent.lastHitZone == null ? "—" :
                agent.lastHitZone.label(language) + " -" + agent.lastDamage), x + 13, y + 335);
    }

    private void compactStat(Graphics2D g, String label, int value, int x, int y) {
        g.drawString(label + " " + value, x, y);
    }

    private String fitText(Graphics2D g, String value, int width) {
        if (g.getFontMetrics().stringWidth(value) <= width) return value;
        while (value.length() > 1 && g.getFontMetrics().stringWidth(value + "…") > width)
            value = value.substring(0, value.length() - 1);
        return value + "…";
    }

    private void paintHeader(Graphics2D g) {
        g.setColor(new Color(55, 48, 42));
        g.setFont(getFont().deriveFont(Font.BOLD, 25f));
        var title = simulation.winningTeam == null ? blueTeamName + "  VS  " + redTeamName : switch (simulation.winningTeam) {
            case BLUE -> blueTeamName + text(" gagne !", "勝利！");
            case RED -> redTeamName + text(" gagne !", "勝利！");
            case DRAW -> text("Match nul !", "平手！");
        };
        g.drawString(title, (getWidth() - g.getFontMetrics().stringWidth(title)) / 2, 39);
        g.setFont(getFont().deriveFont(Font.BOLD, 13f));
        var legend = text("↑ Haute : portée / lente   ↓ Basse : courte / rapide   " +
                        "↔ Latérale : erreur   ⇅ Double Block",
                "↑ 上段：長射程／慢   ↓ 下段：短射程／快   ↔ 側擋：失誤   ⇅ 雙重格擋");
        g.setColor(new Color(86, 76, 64));
        g.drawString(legend, (getWidth() - g.getFontMetrics().stringWidth(legend)) / 2, 61);
        if (paused) {
            g.setColor(new Color(20, 20, 20, 145));
            g.fillRoundRect(getWidth() / 2 - 88, getHeight() / 2 - 31, 176, 62, 16, 16);
            g.setColor(Color.WHITE);
            g.setFont(getFont().deriveFont(Font.BOLD, 24f));
            var pauseText = text("EN PAUSE", "已暫停");
            g.drawString(pauseText, (getWidth() - g.getFontMetrics().stringWidth(pauseText)) / 2, getHeight() / 2 + 9);
        }
    }

    private void paintControls(Graphics2D g) {
        var speedText = speed == 0.25 ? "0.25" : speed == 0.5 ? "0.5" : "%.0f".formatted(speed);
        var controls = text("Espace : pause   R : recommencer   L : langue   0 : 0,25×   5 : 0,5×   " +
                        "1 / 2 / 4 : vitesse   Actuel : %s×",
                "空白鍵：暫停   R：重新開始   L：語言   0：0.25 倍   5：0.5 倍   " +
                        "1／2／4：速度   目前：%s 倍").formatted(speedText);
        g.setColor(new Color(70, 62, 52));
        g.setFont(getFont().deriveFont(Font.PLAIN, 14f));
        g.drawString(controls, (getWidth() - g.getFontMetrics().stringWidth(controls)) / 2, getHeight() - 18);
    }
}
