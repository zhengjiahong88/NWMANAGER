import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.IOException;
import java.nio.file.Path;

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
    private boolean paused, debugVisible;
    private double speed = 1;
    private long lastTick = System.nanoTime(), edtTicks, edtTickTotalNanos, edtTickMaximumNanos,
            edtTicksOver16Ms, edtTicksOver50Ms, edtTicksOver200Ms, repaintsRequested;
    private TransitionDebugRecorder transitionDebugRecorder;

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

    MapPanel(TeamConfiguration3 blueTeam, TeamConfiguration3 redTeam, UiLanguage language, MenuHandler menuHandler) {
        this(blueTeam, redTeam, language, menuHandler, SimulationScenario.MODERN_3V3, System.nanoTime());
    }

    MapPanel(TeamConfiguration3 blueTeam, TeamConfiguration3 redTeam, UiLanguage language, MenuHandler menuHandler,
             SimulationScenario scenario, long seed) {
        this(new DuelSimulation(blueTeam, redTeam, scenario, seed), language, menuHandler,
                blueTeam.name(), redTeam.name());
    }

    MapPanel(UiLanguage language, MenuHandler menuHandler, SimulationScenario scenario, long seed) {
        this(language, menuHandler, scenario, ScenarioFamily.BASELINE, seed);
    }

    MapPanel(UiLanguage language, MenuHandler menuHandler, SimulationScenario scenario,
             ScenarioFamily scenarioFamily, long seed) {
        this(AgentConfiguration.defaultBlue(), AgentConfiguration.defaultBlueTwo(),
                AgentConfiguration.defaultRed(), AgentConfiguration.defaultRedTwo(), language, menuHandler,
                "Équipe A", "Équipe B", scenario, scenarioFamily, seed);
    }

    private MapPanel(AgentConfiguration blueOne, AgentConfiguration blueTwo, AgentConfiguration redOne,
                      AgentConfiguration redTwo, UiLanguage language, MenuHandler menuHandler,
                      String blueTeamName, String redTeamName) {
        this(blueOne, blueTwo, redOne, redTwo, language, menuHandler, blueTeamName, redTeamName,
                SimulationScenario.MODERN_2V2, ScenarioFamily.BASELINE, System.nanoTime());
    }

    private MapPanel(AgentConfiguration blueOne, AgentConfiguration blueTwo, AgentConfiguration redOne,
                     AgentConfiguration redTwo, UiLanguage language, MenuHandler menuHandler,
                     String blueTeamName, String redTeamName, SimulationScenario scenario,
                     ScenarioFamily scenarioFamily, long seed) {
        this(new DuelSimulation(blueOne, blueTwo, redOne, redTwo, scenario, scenarioFamily, seed),
                language, menuHandler, blueTeamName, redTeamName);
    }

    MapPanel(DuelSimulation simulation, UiLanguage language, MenuHandler menuHandler,
             String blueTeamName, String redTeamName) {
        this.language = language;
        this.menuHandler = menuHandler;
        this.blueTeamName = blueTeamName;
        this.redTeamName = redTeamName;
        this.simulation = simulation;
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
        bind("D", "debug", () -> debugVisible = !debugVisible);
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
        var tickStarted = System.nanoTime();
        var now = System.nanoTime();
        var elapsed = Math.min(0.05, (now - lastTick) / 1_000_000_000.0) * speed;
        lastTick = now;
        if (!paused) {
            var steps = Math.max(1, (int) Math.ceil(elapsed / (1.0 / 120.0)));
            var dt = elapsed / steps;
            for (var n = 0; n < steps; ++n) simulation.update(dt);
        }
        repaint();
        ++repaintsRequested;
        var tickNanos = System.nanoTime() - tickStarted;
        ++edtTicks;
        edtTickTotalNanos += tickNanos;
        edtTickMaximumNanos = Math.max(edtTickMaximumNanos, tickNanos);
        if (tickNanos > 16_000_000) ++edtTicksOver16Ms;
        if (tickNanos > 50_000_000) ++edtTicksOver50Ms;
        if (tickNanos > 200_000_000) ++edtTicksOver200Ms;
        if (transitionDebugRecorder != null)
            transitionDebugRecorder.observe(simulation, swingTickMetrics());
    }

    SwingTickMetrics swingTickMetrics() {
        return new SwingTickMetrics(edtTicks,
                edtTicks == 0 ? 0 : edtTickTotalNanos / 1_000_000.0 / edtTicks,
                edtTickMaximumNanos / 1_000_000.0, edtTicksOver16Ms, edtTicksOver50Ms,
                edtTicksOver200Ms, repaintsRequested);
    }

    void stopTimerForTest() {
        timer.stop();
    }

    TransitionDebugRecorder enableTransitionDebug(long seed, double secondsAfterFirstDeath, Path output) {
        transitionDebugRecorder = new TransitionDebugRecorder(seed, secondsAfterFirstDeath, output);
        return transitionDebugRecorder;
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
        Pos arena = new Pos(INFO_WIDTH + MARGIN * 2, MARGIN + 42);
        var arenaWidth = getWidth() - INFO_WIDTH * 2 - MARGIN * 4;
        var arenaHeight = getHeight() - MARGIN * 2 - 86;
        var scale = Math.min(arenaWidth / DuelSimulation.ARENA_WIDTH, arenaHeight / DuelSimulation.ARENA_HEIGHT);
        var drawWidth = DuelSimulation.ARENA_WIDTH * scale;
        var drawHeight = DuelSimulation.ARENA_HEIGHT * scale;
        Pos origin = arena.plus(new Pos(arenaWidth - drawWidth, arenaHeight - drawHeight).div(2D));

        paintArena(g, origin.x(), origin.y(), drawWidth, drawHeight, scale);
        paintTargetLines(g, scale, origin);
        if (debugVisible) paintTacticalDebug(g, scale, origin);
        for (var agent : simulation.agents) {
            try {
                agent.paint(g, scale, origin, language);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        if (simulation.blueTeam.length == 3 && simulation.redTeam.length == 3) {
            for (var n = 0; n < 3; ++n) {
                paintCompactAgentInfo(g, simulation.blueTeam[n], MARGIN, 82 + n * 142);
                paintCompactAgentInfo(g, simulation.redTeam[n], getWidth() - INFO_WIDTH - MARGIN, 82 + n * 142);
            }
        } else {
            paintSurvivingTeamInfo(g, simulation.blueTeam, MARGIN);
            paintSurvivingTeamInfo(g, simulation.redTeam, getWidth() - INFO_WIDTH - MARGIN);
        }
        paintHeader(g);
        paintControls(g);
        g.dispose();
    }

    private void paintSurvivingTeamInfo(Graphics2D g, Musketeer[] team, int x) {
        if (team.length == 1) {
            paintAgentInfo(g, team[0], x, 250);
            return;
        }
        for (var n = 0; n < team.length; ++n)
            paintAgentInfo(g, team[n], x, 78 + n * 352);
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

    private void paintTargetLines(Graphics2D g, double scale, Pos origin) {
        if ((simulation.scenario() == SimulationScenario.MODERN_3V3 ||
                simulation.scenario() == SimulationScenario.CONTROLLED_3V3) && !debugVisible) return;
        var originalStroke = g.getStroke();
        g.setStroke(new BasicStroke(1.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND,
                0, new float[]{6, 7}, 0));
        for (var agent : simulation.agents) {
            if (!agent.alive || agent.target == null || !agent.target.alive) continue;
            var roleColor = switch (agent.localRole) {
                case FIXER -> new Color(224, 151, 37, 112);
                case FLANKER -> new Color(142, 80, 190, 112);
                case COVER -> new Color(45, 151, 94, 112);
                case FREE -> new Color(agent.color.getRed(), agent.color.getGreen(), agent.color.getBlue(), 72);
            };
            g.setColor(roleColor);
            Pos start = origin.plus(agent.pos.times(scale)), end = origin.plus(agent.target.pos.times(scale));
            g.drawLine((int) start.x(), (int) start.y(), (int) end.x(), (int) end.y());
            g.drawOval((int) end.x() - 5, (int) end.y() - 5, 10, 10);
            if (agent.dezoneSwitchTarget == null || agent.dezoneSwitchCommitted ||
                    !agent.dezoneSwitchTarget.alive) continue;
            g.setColor(new Color(agent.color.getRed(), agent.color.getGreen(), agent.color.getBlue(), 145));
            Pos switchPos = origin.plus(agent.dezoneSwitchTarget.pos.times(scale));
            g.drawLine((int) start.x(), (int) start.y(), (int) switchPos.x(), (int) switchPos.y());
            g.drawRect((int) switchPos.x() - 5, (int) switchPos.y() - 5, 10, 10);
        }
        g.setStroke(originalStroke);
    }

    private void paintTacticalDebug(Graphics2D g, double scale, Pos origin) {
        var originalStroke = g.getStroke();
        var originalFont = g.getFont();
        g.setFont(getFont().deriveFont(Font.BOLD, 10f));
        var setup = simulation.scenarioSetup();
        g.setColor(new Color(38, 34, 30));
        var debugX = (int) origin.x() + 8;
        var headerY = (int) origin.y() + 14;
        g.drawString("SCENARIO: " + simulation.scenario() + " | SEED: " + simulation.seed(),
                debugX, headerY);
        if (simulation.scenario() == SimulationScenario.MODERN_2V2)
            g.drawString("LAST RECONSTRUCTION: " + simulation.lastModernReconstructionReason(),
                    debugX, headerY + 12);
        var group3 = simulation.modernGroup3Engagement();
        if (group3 != null) {
            g.drawString(simulation.experimentalGroupPhase() + " — attaques physiques actives",
                    debugX, headerY += 24);
            for (var team : new Team[]{Team.BLUE, Team.RED}) {
                var formation = group3.formation(team);
                var maneuver = group3.maneuver(team);
                g.drawString(team + " roles=" + agentCode(formation.leftWing) + "/" +
                                agentCode(formation.centerPlayer) + "/" + agentCode(formation.rightWing) +
                                " width=" + "%.2f".formatted(formation.width) +
                                " cohesion=" + "%.2f".formatted(formation.cohesion) +
                                " maneuver=" + (maneuver == null ? "NONE" : maneuver.type),
                        debugX, headerY += 12);
                paintTeamFormation3(g, scale, origin, formation);
                var network = group3.coverNetwork(team);
                g.drawString(team + " cover links=" + network.activeLinks() + "/18" +
                                " connectivity=" + "%.2f".formatted(network.connectivity) +
                                " weakest=" + agentCode(network.weakestMember) +
                                " %.2f".formatted(network.weakestMemberCoverage),
                        debugX, headerY += 12);
                paintCoverNetwork3(g, scale, origin, network);
            }
            for (var agent : simulation.agents) {
                var attention = agent.attentionAllocation;
                var pending = attention.pendingPrimary == null ? "-" : agentCode(attention.pendingPrimary) +
                        "@%.2f".formatted(Math.max(0, attention.switchCompletesAt - simulation.simulationTime));
                g.drawString(agentCode(agent) + " attention P=" + agentCode(attention.primaryThreat) +
                                " %.2f".formatted(attention.primaryWeight) +
                                " S=" + agentCode(attention.secondaryThreat) +
                                " %.2f".formatted(attention.secondaryWeight) +
                                " R=" + agentCode(attention.peripheralThreat) +
                                " %.2f".formatted(attention.peripheralWeight) +
                                " pending=" + pending,
                        debugX, headerY += 12);
            }
            for (var team : new Team[]{Team.BLUE, Team.RED}) {
                var plan = group3.attackPlan(team);
                if (plan == null) continue;
                var detail = new StringBuilder(team + " attackPlan=" + plan.reason() + " attackers=");
                for (var opportunity : plan.opportunities())
                    detail.append(agentCode(opportunity.attacker())).append("->")
                            .append(agentCode(opportunity.target())).append(' ');
                detail.append("coverers=");
                for (var coverer : plan.coveringMembers()) detail.append(agentCode(coverer)).append(' ');
                detail.append("risk=").append("%.2f".formatted(plan.combinedRecoveryRisk()));
                g.drawString(detail.toString(), debugX, headerY += 12);
            }
            if (group3.revengeCandidate != null) {
                var candidate = group3.revengeCandidate;
                var opportunity = candidate.opportunity();
                g.drawString("REVENGE " + agentCode(candidate.revenger()) + "->" +
                                agentCode(opportunity.vulnerableAttacker()) + " links=" +
                                candidate.coverLinksPreserved() + " counterRisk=" +
                                "%.2f".formatted(candidate.counterCoverRisk()) + " score=" +
                                "%.2f".formatted(opportunity.score()),
                        debugX, headerY += 12);
                var counter = group3.counterCoverAssessment;
                if (counter != null && counter.viable())
                    g.drawString("COUNTER-COVER " + agentCode(counter.counterCoverer()) + "->" +
                                    agentCode(counter.enemyRevenger()) + " eta=" +
                                    "%.2f".formatted(counter.threatEta()) + " score=" +
                                    "%.2f".formatted(counter.score()),
                            debugX, headerY += 12);
            }
            for (var cluster : group3.localClusters) {
                var members = new StringBuilder();
                for (var member : cluster.blueMembers()) members.append(agentCode(member)).append(' ');
                members.append("/ ");
                for (var member : cluster.redMembers()) members.append(agentCode(member)).append(' ');
                g.drawString("CLUSTER " + members + " eta=" +
                                "%.2f".formatted(cluster.minimumImpactEta()) + " isolation=" +
                                "%.2f".formatted(cluster.isolationConfidence()) + " " +
                                cluster.reason(), debugX, headerY += 12);
            }
        }
        var groupEngagement = simulation.modernGroupEngagement();
        if (groupEngagement != null &&
                groupEngagement.status != EngagementStatus.BROKEN &&
                groupEngagement.status != EngagementStatus.FINISHED) {
            var group = groupEngagement.group;
            g.drawString("GROUP_2V2 phase=" + group.phase +
                            " center=" + positionText(group.center),
                    debugX, headerY += 24);
            for (var team : new Team[]{Team.BLUE, Team.RED}) {
                var maneuver = group.maneuver(team);
                if (maneuver == null) continue;
                g.drawString(team + " " + maneuver.type +
                                " inner=" + maneuver.innerPlayer.name(language) +
                                " outer=" + maneuver.outerPlayer.name(language) +
                                " age=" + "%.2fs".formatted(maneuver.age(simulation.simulationTime)),
                        debugX, headerY += 12);
            }
            if (group.revengeOpportunity != null) {
                var opportunity = group.revengeOpportunity;
                g.drawString("REVENGE OPPORTUNITY revenger=" +
                                opportunity.revenger().name(language) +
                                " target=" + opportunity.vulnerableAttacker().name(language) +
                                " reason=" + opportunity.reason() +
                                " window=" +
                                "%.2fs".formatted(opportunity.vulnerabilityRemaining()),
                        debugX, headerY += 12);
                g.drawString("align=" + "%.2fs".formatted(opportunity.timeToAlign()) +
                                " prepare=" + "%.2fs".formatted(opportunity.timeToPrepare()) +
                                " contact=" + "%.2fs".formatted(opportunity.timeToContact()) +
                                " counterETA=" +
                                "%.2fs".formatted(opportunity.opposingThreatEta()) +
                                " score=" + "%.2f".formatted(opportunity.score()) +
                                (opportunity.rejectionReason().isEmpty() ? "" :
                                        " reject=" + opportunity.rejectionReason()),
                        debugX, headerY += 12);
            }
            for (var arbitration : new TeamAttackArbitration[]{
                    group.blueAttackArbitration, group.redAttackArbitration}) {
                if (arbitration == null || arbitration.selectedOpportunity() == null) continue;
                var opportunity = arbitration.selectedOpportunity();
                g.drawString("ATTACK SELECT " +
                                arbitration.selectedAttacker().name(language) + " -> " +
                                opportunity.target().name(language) + " | " +
                                opportunity.primaryReason() + " | window=" +
                                "%.2fs".formatted(opportunity.availableWindow()) + " | " +
                                arbitration.reason(),
                        debugX, headerY += 12);
            }
        }
        var kill = simulation.lastKillExplanation();
        if (kill != null) {
            g.setColor(new Color(128, 32, 32));
            g.drawString(text("MORT EXPLIQUÉE ", "擊殺解釋 ") +
                            kill.killer().name(language) + " -> " + kill.victim().name(language) +
                            " | " + kill.initiatingReason() + " | " + kill.finalCause(),
                    debugX, headerY += 18);
            g.drawString(text("état ", "狀態 ") + kill.victimStateAtCommit() + " -> " +
                            kill.victimStateAtImpact() + " | " + kill.coverFailureReason() +
                            " | coverETA=" + "%.2fs".formatted(kill.victimAllyPunishEta()) +
                            " recovery=" + "%.2fs".formatted(kill.killerRecoveryEta()),
                    debugX, headerY += 12);
            var chain = simulation.lastKillCausalChain();
            if (chain != null)
                g.drawString("CHAIN movement=" + chain.observed(chain.initiatingMovement()) +
                                " attack=" + chain.observed(chain.attackDecision()) +
                                " reaction=" + chain.observed(chain.victimReaction()) +
                                " cover=" + chain.observed(chain.allyCoverDecision()),
                        debugX, headerY += 12);
        }
        var traces = simulation.decisionTraces();
        for (var n = Math.max(0, traces.size() - 5); n < traces.size(); ++n) {
            var trace = traces.get(n);
            g.setColor(trace.category() == DecisionCategory.ATTACK_REJECTION ?
                    new Color(146, 67, 38) : new Color(38, 34, 30));
            g.drawString("TRACE " + trace.category() + " | " +
                            (trace.agent() == null ? "TEAM" : trace.agent().name(language)) +
                            " " + trace.action() + " | " + trace.primaryReason() +
                            (trace.target() == null ? "" : " -> " + trace.target().name(language)) +
                            (trace.rejectedAlternative().equals("NONE") ? "" :
                                    " | rejet=" + trace.rejectedAlternative()),
                    debugX, headerY += 12);
        }
        var supportPlan = simulation.supportPlan();
        if (supportPlan != null) {
            g.drawString("SUPPORT PLAN: SUPPORTER=" + supportPlan.supporter.name(language) +
                            " FIXER=" + supportPlan.fixer.name(language) +
                            " DEFENDER=" + supportPlan.primaryDefender.name(language) +
                            " INTERCEPTOR=" + supportPlan.formerOpponent.name(language),
                    debugX, headerY += 24);
            g.drawString("STATE=" + supportPlan.state + " SIDE=" + supportPlan.flankSide +
                            " WINDOW=" + "%.2fs".formatted(supportPlan.availableWindow) +
                            " ETA_SUPPORT=" + "%.2fs".formatted(supportPlan.timeToSupport) +
                            " ETA_INTERCEPT=" + "%.2fs".formatted(supportPlan.timeToIntercept),
                    debugX, headerY += 12);
            paintSupportPlanDebug(g, scale, origin, supportPlan);
        }
        if (setup != null) {
            g.setColor(new Color(38, 34, 30));
            headerY += simulation.scenario() == SimulationScenario.MODERN_2V2 ? 24 : 12;
            g.drawString("FAMILY: " + setup.family(), debugX, headerY);
            g.drawString(spawnText("A1", setup.blueOne()) + " | " + spawnText("A2", setup.blueTwo()),
                    debugX, headerY + 12);
            g.drawString(spawnText("B1", setup.redOne()) + " | " + spawnText("B2", setup.redTwo()),
                    debugX, headerY + 24);
            if (setup.rejectedPlacements() > 0 || setup.baselineFallback())
                g.drawString("REJECTED: " + setup.rejectedPlacements() +
                                (setup.baselineFallback() ? " | BASELINE FALLBACK" : ""),
                        debugX, headerY + 36);
        }
        for (var engagement : simulation.engagements()) {
            if (engagement.type == EngagementType.DUEL) {
                var first = origin.plus(engagement.duelistOne.pos.times(scale));
                var second = origin.plus(engagement.duelistTwo.pos.times(scale));
                g.setColor(new Color(42, 91, 150, 135));
                g.setStroke(new BasicStroke(1.7f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND,
                        0, new float[]{4, 5}, 0));
                g.drawLine((int) first.x(), (int) first.y(), (int) second.x(), (int) second.y());
                continue;
            }
            if (engagement.type == EngagementType.GROUP_2V2) {
                paintGroupDebug(g, scale, origin, engagement.group);
                continue;
            }
            if (engagement.type == EngagementType.GROUP_3V3) continue;
            var front = origin.plus(engagement.fixerDestination().times(scale));
            var flank = origin.plus(engagement.flankerDestination().times(scale));
            paintDebugMarker(g, front, new Color(224, 151, 37), "FRONT", false);
            paintDebugMarker(g, flank, new Color(142, 80, 190),
                    engagement.flankerSlot.name(), false);
            var flanker = origin.plus(engagement.flanker.pos.times(scale));
            g.setColor(new Color(142, 80, 190, 150));
            g.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND,
                    0, new float[]{5, 5}, 0));
            g.drawLine((int) flanker.x(), (int) flanker.y(), (int) flank.x(), (int) flank.y());
            var flankingAgent = engagement.flanker;
            if (flankingAgent.rawRearDestination != null)
                paintDebugMarker(g, origin.plus(flankingAgent.rawRearDestination.times(scale)),
                        new Color(143, 42, 73), "REAR RAW", false);
            if (flankingAgent.smoothedRearDestination != null) {
                var rearSmooth = origin.plus(flankingAgent.smoothedRearDestination.times(scale));
                paintDebugMarker(g, rearSmooth, new Color(221, 76, 137), "REAR SMOOTH", true);
                g.setColor(new Color(221, 76, 137, 145));
                g.drawLine((int) flanker.x(), (int) flanker.y(),
                        (int) rearSmooth.x(), (int) rearSmooth.y());
                var defender = origin.plus(engagement.primaryTarget.pos.times(scale));
                var rearRadius = flankingAgent.rawRearDestination.
                        minus(engagement.primaryTarget.pos).hypotenuse() * scale;
                var start = (int) Math.toDegrees(engagement.primaryTarget.angle +
                        (flankingAgent.localSide < 0 ? -1 : 1) * DuelSimulation.REAR_ENTRY_ANGLE);
                var extent = (int) Math.toDegrees((flankingAgent.localSide < 0 ? -1 : 1) *
                        (Math.PI - DuelSimulation.REAR_ENTRY_ANGLE));
                g.drawArc((int) (defender.x() - rearRadius), (int) (defender.y() - rearRadius),
                        (int) (rearRadius * 2), (int) (rearRadius * 2), start, extent);
            }
            if (engagement.coordination != null) {
                var coordination = engagement.coordination;
                g.setColor(new Color(52, 42, 79));
                g.drawString("2V1 " + coordination.phase + " | flank=" +
                                coordination.flankReadiness + " | primary=" +
                                (coordination.primaryAttacker == null ? "NONE" :
                                        coordination.primaryAttacker.name(language)),
                        (int) origin.x() + 8, (int) origin.y() + 120);
                if (coordination.secondaryAssessment != null)
                    g.drawString("SECONDARY score=" +
                                    "%.2f".formatted(coordination.secondaryAssessment.score()) +
                                    " line=" +
                                    "%.2f".formatted(coordination.secondaryAssessment.lineSafety()) +
                                    " window=" +
                                    "%.2f".formatted(Math.max(0,
                                            coordination.releaseWindowEnd - simulation.simulationTime)),
                            (int) origin.x() + 8, (int) origin.y() + 132);
            }
            if (engagement.interceptor == null) continue;
            var interceptor = engagement.interceptor;
            if (interceptor.rawInterceptionPoint != null)
                paintDebugMarker(g, origin.plus(interceptor.rawInterceptionPoint.times(scale)),
                        new Color(220, 72, 62), "RAW", false);
            if (interceptor.tacticalDestination != null) {
                var destination = origin.plus(interceptor.tacticalDestination.times(scale));
                paintDebugMarker(g, destination, new Color(28, 151, 184), "SMOOTH", true);
                var interceptorPosition = origin.plus(interceptor.pos.times(scale));
                g.setColor(new Color(28, 151, 184, 170));
                g.drawLine((int) interceptorPosition.x(), (int) interceptorPosition.y(),
                        (int) destination.x(), (int) destination.y());
            }
        }
        g.setStroke(originalStroke);
        for (var agent : simulation.agents) {
            if (!agent.alive) continue;
            if (agent.tacticalIntent == TacticalIntent.GROUP) {
                var detail = agentCode(agent) + " " + agent.groupIntent +
                        " | mark=" + agentCode(agent.primaryMark) +
                        " second=" + agentCode(agent.secondaryThreat);
                if (agent.coverAssessment != null)
                    detail += " | cover=" +
                            "%.2f".formatted(agent.coverAssessment.score());
                if (agent.commitAssessment != null)
                    detail += " | commit=" +
                            "%.2f".formatted(agent.commitAssessment.score()) +
                            " revengeRisk=" +
                            "%.2f".formatted(agent.commitAssessment.revengeRisk());
                if (agent.attackOpportunity != null) {
                    var opportunity = agent.attackOpportunity;
                    detail += " | stab=" + opportunity.primaryReason() +
                            " window=" + "%.2fs".formatted(opportunity.availableWindow()) +
                            " impact=" + "%.2fs".formatted(opportunity.impactEta());
                }
                if (agent.attackOpportunity != null &&
                        agent.lastAttackOpportunityRejection != AttackOpportunityRejection.NONE)
                    detail += " | refus=" + agent.lastAttackOpportunityRejection;
                g.setColor(new Color(38, 34, 30));
                g.drawString(detail, debugX, headerY += 12);
                if (agent.attackOpportunity != null) {
                    var evidence = agent.attackOpportunity.evidence();
                    g.drawString("EVIDENCE body attacker/ally=" +
                                    angle(evidence.targetFacingErrorToAttacker()) + "/" +
                                    angle(evidence.targetFacingErrorToAlly()) +
                                    " weapon=" + angle(evidence.targetWeaponErrorToAttacker()) +
                                    "/" + angle(evidence.targetWeaponErrorToAlly()) +
                                    " state=" + evidence.observedTargetState() +
                                    " confidence=" +
                                    "%.2f".formatted(evidence.perceptionConfidence()),
                            debugX, headerY += 12);
                    g.drawString("LANE previous/predicted=" +
                                    "%.2f/%.2fm".formatted(
                                            evidence.previousDistanceToThreatLane(),
                                            evidence.predictedDistanceToThreatLane()) +
                                    " entry=" + "%.2fm/s".formatted(evidence.laneEntrySpeed()) +
                                    " inside=" + "%.2fs".formatted(evidence.timeInsideLane()) +
                                    " punish=" + "%.2fs".formatted(evidence.opposingCoverEta()) +
                                    " recovery=" +
                                    "%.2fs".formatted(evidence.attackerRecoveryEta()),
                            debugX, headerY += 12);
                }
                continue;
            }
            var position = origin.plus(agent.pos.times(scale)).plus(0, -Musketeer.BODY_RADIUS * scale - 12);
            var detail = agent.tacticalIntent + " | t=" + "%.2f".formatted(agent.tacticalIntentTime) + "s";
            detail += " | tactical=" + agentName(agent.plannedTarget) +
                    " | current=" + agentName(agent.target);
            if (agent.tacticalThreat != null) detail += " | THREAT " +
                    "%.2f".formatted(agent.threatAssessment.score()) + " " +
                    agent.threatAssessment.recommendedResponse() + "/" + agent.threatResponse;
            if (agent.interceptionPhase != null) detail += " · " + agent.interceptionPhase;
            if (agent.tacticalIntent == TacticalIntent.FLANK)
                detail += " | " + agent.flankPhase +
                        " rear=" + "%.1f°".formatted(Math.toDegrees(agent.rearAngle));
            g.setColor(new Color(38, 34, 30));
            g.drawString(detail, (int) position.x() - g.getFontMetrics().stringWidth(detail) / 2,
                    (int) position.y());
        }
        var debugY = (int) (origin.y() + DuelSimulation.ARENA_HEIGHT * scale - 34);
        g.setColor(new Color(38, 34, 30));
        g.drawString("ENGAGE >= 0.65 | RESUME <= 0.30", (int) origin.x() + 8, debugY);
        var engagementIndex = 0;
        for (var engagement : simulation.engagements()) {
            if (engagement.type == EngagementType.GROUP_2V2 || engagement.type == EngagementType.GROUP_3V3) continue;
            ++engagementIndex;
            var relation = switch (engagement.type) {
                case DUEL -> "ENGAGEMENT " + engagementIndex + ": DUEL " +
                        engagement.duelistOne.name(language) + " - " +
                        engagement.duelistTwo.name(language) + " [" + engagement.status + "]";
                case GROUP_2V2 -> "ENGAGEMENT " + engagementIndex + ": GROUP_2V2 [" +
                        engagement.status + "] | phase=" + engagement.group.phase +
                        " | rotation=" + engagement.group.rotation +
                        " | center=" + positionText(engagement.group.center);
                case GROUP_3V3 -> "ENGAGEMENT " + engagementIndex + ": GROUP_3V3 [" +
                        engagement.status + "]";
                case TWO_VS_ONE -> "ENGAGEMENT " + engagementIndex +
                        ": TWO_VS_ONE [" + engagement.status + "]" +
                        " | FIX=" + engagement.fixer.name(language) +
                        " | FLANK=" + engagement.flanker.name(language) +
                        " " + engagement.flankerSlot +
                        " | TARGET=" + engagement.primaryTarget.name(language) +
                        " | INTERCEPT=" + agentName(engagement.interceptor);
            };
            g.drawString(relation, (int) origin.x() + 8, debugY += 12);
            if (engagement.type == EngagementType.TWO_VS_ONE) {
                var flankingAgent = engagement.flanker;
                var opportunity = flankingAgent.backstabOpportunity;
                g.drawString("FLANK " + flankingAgent.flankPhase +
                                " | rear=" + "%.1f°".formatted(Math.toDegrees(flankingAgent.rearAngle)) +
                                " | entry=" + "%.0f°".formatted(Math.toDegrees(DuelSimulation.REAR_ENTRY_ANGLE)) +
                                " | physical=" + "%.0f°".formatted(Math.toDegrees(DuelSimulation.REAR_ATTACK_ANGLE)) +
                                " | gain=" + "%.1f°/s".formatted(
                                        Math.toDegrees(flankingAgent.rearAngleGainRate)),
                        (int) origin.x() + 8, debugY += 12);
                g.drawString("stable=" + "%.2fs".formatted(flankingAgent.rearStableTime) +
                                " | radius=" + (flankingAgent.rawRearDestination == null ? "n/a" :
                                "%.2fm".formatted(flankingAgent.rawRearDestination.
                                        minus(engagement.primaryTarget.pos).hypotenuse())) +
                                " | window=" + opportunity.valid() +
                                " | fix=" + opportunity.fixerMaintainsPressure() +
                                " | B2safe=" + opportunity.interceptorSafe() +
                                " | line=" + opportunity.attackLineClear() +
                                " | range=" + opportunity.inRange() +
                                " | aim=" + opportunity.weaponAligned() +
                                " | reason=" + flankingAgent.lastFlankReason,
                        (int) origin.x() + 8, debugY += 12);
            }
            if (engagement.interceptor != null &&
                    engagement.interceptor.interceptionPhase == InterceptionPhase.FAILED)
                g.drawString("FAILED: " + engagement.interceptor.lastInterceptionReason,
                        (int) origin.x() + 8, debugY += 12);
        }
        if (simulation.scenario() == SimulationScenario.MODERN_2V2)
            for (var agent : simulation.agents) {
                if (!agent.alive) continue;
                var freedom = simulation.freedomAssessment(agent);
                if (freedom != null)
                    g.drawString("FREEDOM " + agent.name(language) +
                                    " free=" + freedom.free() +
                                    " confidence=" + "%.2f".formatted(freedom.confidence()) +
                                    " support=" + "%.2fs".formatted(freedom.timeToSupport()) +
                                    " contest=" +
                                    "%.2fs".formatted(freedom.timeForOpponentToContest()) +
                                    " reason=" + freedom.reason(),
                            (int) origin.x() + 8, debugY += 12);
                var opportunity = simulation.supportOpportunity(agent);
                if (opportunity != null && opportunity.reason() != null)
                    g.drawString("SUPPORT " + agent.name(language) +
                                    " viable=" + opportunity.viable() +
                                    " reason=" + opportunity.reason() +
                                    " window=" + "%.2fs".formatted(opportunity.availableWindow()) +
                                    " support=" + "%.2fs".formatted(opportunity.timeToSupport()) +
                                    " recover=" +
                                    "%.2fs".formatted(opportunity.timeForOpponentToRecover()) +
                                    " intercept=" +
                                    "%.2fs".formatted(opportunity.timeForOpponentToIntercept()) +
                                    " leaveRisk=" + "%.2f".formatted(opportunity.leaveRisk()) +
                                    " value=" + "%.2f".formatted(opportunity.supportValue()) +
                                    " score=" + "%.2f".formatted(opportunity.score()) +
                                    " rejection=" + opportunity.rejectionReason(),
                            (int) origin.x() + 8, debugY += 12);
            }
        g.setFont(originalFont);
    }

    private String angle(double radians) {
        return Double.isFinite(radians) ? "%.0f°".formatted(Math.toDegrees(radians)) : "NONE";
    }

    private void paintGroupDebug(Graphics2D g, double scale, Pos origin,
                                 GroupEngagement group) {
        paintDebugMarker(g, origin.plus(group.center.times(scale)),
                new Color(48, 48, 48), "GROUP CENTER", true);
        paintTeamLink(g, scale, origin, group.blue);
        paintTeamLink(g, scale, origin, group.red);
        for (var agent : simulation.agents) {
            if (!agent.alive || agent.localManeuver == null) continue;
            var start = origin.plus(agent.pos.times(scale));
            var destination = origin.plus(agent.localManeuver.destination.times(scale));
            g.setColor(new Color(agent.color.getRed(), agent.color.getGreen(),
                    agent.color.getBlue(), 150));
            g.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND,
                    BasicStroke.JOIN_ROUND, 0, new float[]{4, 4}, 0));
            g.drawLine((int) start.x(), (int) start.y(),
                    (int) destination.x(), (int) destination.y());
            g.setFont(g.getFont().deriveFont(Font.BOLD, 10f));
            g.drawString(agent.localManeuver.type.name().replace('_', ' '),
                    (int) start.x() + 10, (int) start.y() - 20);
            g.setFont(g.getFont().deriveFont(Font.PLAIN, 9f));
            g.drawString(agent.localManeuver.primaryReason,
                    (int) start.x() + 10, (int) start.y() - 9);
            paintWeaponThreatLane(g, scale, origin, agent.primaryWeaponLane,
                    new Color(235, 112, 42, 105));
            paintWeaponThreatLane(g, scale, origin, agent.secondaryWeaponLane,
                    new Color(154, 74, 190, 80));
        }
        for (var agent : simulation.agents)
            if (agent.alive && agent.groupSteeringDebug != null) {
                var debug = agent.groupSteeringDebug;
                g.setColor(new Color(38, 34, 30));
                g.drawString("GROUP STEERING " + agent.name(language) +
                                " team=" + debug.teamManeuver() + "/" + debug.teamRole() +
                                " contribution=" + "%.3f".formatted(debug.teamManeuverContribution()) +
                                " radial=" + "%.3f".formatted(debug.radialCorrection()) +
                                " formation=" + "%.3f".formatted(debug.formationCorrection()) +
                                " avoid=" + "%.3f".formatted(debug.avoidanceCorrection()),
                        (int) origin.x() + 8, (int) origin.y() + 96);
                g.drawString("tangentFinal=" +
                                "%.3f".formatted(debug.finalTangentialProjection()) +
                                " radialFinal=" + "%.3f".formatted(debug.finalRadialProjection()) +
                                " desired=" + "%.3f".formatted(debug.finalDesiredVelocity()) +
                                " actual=" + "%.3f".formatted(debug.actualVelocity()),
                        (int) origin.x() + 8, (int) origin.y() + 108);
                break;
            }
        for (var agent : simulation.agents) {
            if (!agent.alive || agent.primaryMark == null) continue;
            var start = origin.plus(agent.pos.times(scale));
            var mark = origin.plus(agent.primaryMark.pos.times(scale));
            g.setColor(new Color(agent.color.getRed(), agent.color.getGreen(),
                    agent.color.getBlue(), 125));
            g.setStroke(new BasicStroke(1.4f, BasicStroke.CAP_ROUND,
                    BasicStroke.JOIN_ROUND, 0, new float[]{5, 5}, 0));
            g.drawLine((int) start.x(), (int) start.y(), (int) mark.x(), (int) mark.y());
            if (agent.secondaryThreat == null) continue;
            var secondary = origin.plus(agent.secondaryThreat.pos.times(scale));
            g.setColor(new Color(120, 95, 45, 105));
            g.setStroke(new BasicStroke(1f, BasicStroke.CAP_ROUND,
                    BasicStroke.JOIN_ROUND, 0, new float[]{2, 5}, 0));
            g.drawLine((int) start.x(), (int) start.y(),
                    (int) secondary.x(), (int) secondary.y());
        }
        if (group.revengeOpportunity != null) {
            var opportunity = group.revengeOpportunity;
            var start = origin.plus(opportunity.revenger().pos.times(scale));
            var target = origin.plus(opportunity.vulnerableAttacker().pos.times(scale));
            g.setColor(new Color(220, 116, 28, 190));
            g.setStroke(new BasicStroke(2.4f));
            g.drawLine((int) start.x(), (int) start.y(),
                    (int) target.x(), (int) target.y());
        }
        if (group.counterCoverAssessment == null ||
                group.counterCoverAssessment.counterCoverer() == null) return;
        var assessment = group.counterCoverAssessment;
        var start = origin.plus(assessment.counterCoverer().pos.times(scale));
        var target = origin.plus(assessment.enemyRevenger().pos.times(scale));
        g.setColor(new Color(190, 48, 72, 180));
        g.setStroke(new BasicStroke(2f));
        g.drawLine((int) start.x(), (int) start.y(), (int) target.x(), (int) target.y());
    }

    private void paintWeaponThreatLane(Graphics2D g, double scale, Pos origin,
                                       WeaponThreatLane lane, Color color) {
        if (lane == null || !lane.currentlyDangerous()) return;
        var start = origin.plus(lane.origin().times(scale));
        var end = origin.plus(lane.end().times(scale));
        g.setColor(color);
        g.setStroke(new BasicStroke((float) Math.max(2, lane.halfWidth() * scale * 2),
                BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.drawLine((int) start.x(), (int) start.y(), (int) end.x(), (int) end.y());
    }

    private void paintTeamLink(Graphics2D g, double scale, Pos origin, Musketeer[] team) {
        var first = origin.plus(team[0].pos.times(scale));
        var second = origin.plus(team[1].pos.times(scale));
        g.setColor(new Color(team[0].color.getRed(), team[0].color.getGreen(),
                team[0].color.getBlue(), 150));
        g.setStroke(new BasicStroke(2f));
        g.drawLine((int) first.x(), (int) first.y(), (int) second.x(), (int) second.y());
    }

    private void paintTeamFormation3(Graphics2D g, double scale, Pos origin, TeamFormation3 formation) {
        var left = origin.plus(formation.leftWing.pos.times(scale));
        var center = origin.plus(formation.centerPlayer.pos.times(scale));
        var right = origin.plus(formation.rightWing.pos.times(scale));
        g.setColor(new Color(formation.leftWing.color.getRed(), formation.leftWing.color.getGreen(),
                formation.leftWing.color.getBlue(), 145));
        g.setStroke(new BasicStroke(2f));
        g.drawLine((int) left.x(), (int) left.y(), (int) center.x(), (int) center.y());
        g.drawLine((int) center.x(), (int) center.y(), (int) right.x(), (int) right.y());
    }

    private String positionText(Pos position) {
        return "(%.2f, %.2f)".formatted(position.x(), position.y());
    }

    private String agentCode(Musketeer agent) {
        if (agent == null) return "-";
        for (var n = 0; n < simulation.blueTeam.length; ++n)
            if (simulation.blueTeam[n] == agent) return "A" + (n + 1);
        for (var n = 0; n < simulation.redTeam.length; ++n)
            if (simulation.redTeam[n] == agent) return "B" + (n + 1);
        return "?";
    }

    private void paintCompactAgentInfo(Graphics2D g, Musketeer agent, int x, int y) {
        g.setColor(new Color(255, 250, 239, 225));
        g.fillRoundRect(x, y, INFO_WIDTH, 136, 16, 16);
        g.setColor(agent.color);
        g.fillRoundRect(x, y, INFO_WIDTH, 34, 16, 16);
        g.fillRect(x, y + 17, INFO_WIDTH, 17);
        g.setColor(Color.WHITE);
        g.setFont(getFont().deriveFont(Font.BOLD, 14f));
        g.drawString(agent.name(language), x + 12, y + 23);
        g.setColor(new Color(55, 48, 42));
        g.setFont(getFont().deriveFont(Font.BOLD, 11f));
        g.drawString(text("Vie : ", "生命：") + agent.hitPoints + " / 100", x + 12, y + 52);
        var formation = simulation.modernGroup3Engagement().formation(agent.team);
        g.drawString(text("Rôle : ", "位置：") + formation.roleOf(agent), x + 12, y + 70);
        var maneuver = simulation.modernGroup3Engagement().maneuver(agent.team);
        g.drawString(text("Manœuvre : ", "隊形動作：") + (maneuver == null ? "NONE" : maneuver.type),
                x + 12, y + 88);
        g.setFont(getFont().deriveFont(Font.PLAIN, 10f));
        var attention = agent.attentionAllocation;
        g.drawString("P:" + agentCode(attention.primaryThreat) + "  S:" +
                agentCode(attention.secondaryThreat) + "  R:" + agentCode(attention.peripheralThreat),
                x + 12, y + 106);
        var coverage = simulation.modernGroup3Engagement().coverNetwork(agent.team).coverageOf(agent);
        g.drawString(text("Couv. ", "掩護 ") + "%.0f%%".formatted(coverage * 100) +
                text(" · attaques ON", " · 攻擊開啟"), x + 12, y + 124);
    }

    private void paintCoverNetwork3(Graphics2D g, double scale, Pos origin, CoverNetwork network) {
        for (var protectedAlly : network.members) {
            CoverLink best = null;
            for (var link : network.incomingLinks.get(protectedAlly))
                if (link.viable() && (best == null || link.quality() > best.quality())) best = link;
            if (best == null) continue;
            var protector = origin.plus(best.protector().pos.times(scale));
            var enemy = origin.plus(best.threateningEnemy().pos.times(scale));
            g.setColor(new Color(46, 157, 101, 150));
            g.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND,
                    0, new float[]{5, 4}, 0));
            g.drawLine((int) protector.x(), (int) protector.y(), (int) enemy.x(), (int) enemy.y());
        }
    }

    private void paintSupportPlanDebug(Graphics2D g, double scale, Pos origin, SupportPlan plan) {
        var supporter = origin.plus(plan.supporter.pos.times(scale));
        var destination = origin.plus(plan.supportDestination.times(scale));
        g.setColor(new Color(235, 137, 28, 185));
        g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND,
                0, new float[]{7, 4}, 0));
        g.drawLine((int) supporter.x(), (int) supporter.y(),
                (int) destination.x(), (int) destination.y());
        paintDebugMarker(g, destination, new Color(235, 137, 28),
                "SUPPORT " + plan.flankSide, true);
        var path = plan.supportDestination.minus(plan.supporter.pos);
        for (var fraction : new double[]{0.25, 0.50, 0.75}) {
            var candidate = origin.plus(plan.supporter.pos.plus(path.times(fraction)).times(scale));
            paintDebugMarker(g, candidate, new Color(202, 83, 48),
                    "%.0f%%".formatted(fraction * 100), false);
        }
        var opportunity = simulation.supportOpportunity(plan.supporter);
        if (opportunity == null || opportunity.interceptionPoint() == null) return;
        var interception = origin.plus(opportunity.interceptionPoint().times(scale));
        paintDebugMarker(g, interception, new Color(194, 39, 45), "BEST INTERCEPT", true);
        var formerOpponent = origin.plus(plan.formerOpponent.pos.times(scale));
        g.setColor(new Color(194, 39, 45, 160));
        g.drawLine((int) formerOpponent.x(), (int) formerOpponent.y(),
                (int) interception.x(), (int) interception.y());
    }

    private String agentName(Musketeer agent) {
        return agent == null ? "NONE" : agent.name(language);
    }

    private String spawnText(String label, AgentSpawn spawn) {
        return "%s SPAWN: (%.2f, %.2f)".formatted(label, spawn.position().x(), spawn.position().y());
    }

    private void paintDebugMarker(Graphics2D g, Pos point, Color color, String label, boolean cross) {
        g.setColor(color);
        if (cross) {
            g.drawLine((int) point.x() - 6, (int) point.y(), (int) point.x() + 6, (int) point.y());
            g.drawLine((int) point.x(), (int) point.y() - 6, (int) point.x(), (int) point.y() + 6);
        } else g.drawOval((int) point.x() - 5, (int) point.y() - 5, 10, 10);
        g.drawString(label, (int) point.x() + 8, (int) point.y() - 7);
    }

    private void paintAgentInfo(Graphics2D g, Musketeer agent, int x, int y) {
        g.setColor(new Color(255, 250, 239, 225));
        g.fillRoundRect(x, y, INFO_WIDTH, 348, 18, 18);
        g.setColor(agent.color);
        g.fillRoundRect(x, y, INFO_WIDTH, 38, 18, 18);
        g.fillRect(x, y + 19, INFO_WIDTH, 19);
        g.setColor(Color.WHITE);
        g.setFont(getFont().deriveFont(Font.BOLD, 16f));
        g.drawString(agent.name(language), x + 13, y + 25);
        g.setColor(new Color(55, 48, 42));
        g.setFont(getFont().deriveFont(Font.BOLD, 12f));
        g.drawString(text("Vie : ", "生命：") + agent.hitPoints + " / 100", x + 13, y + 57);
        var tacticText = text("T : ", "戰術：") + agent.tactic.label(language);
        g.setFont(getFont().deriveFont(Font.BOLD, 10f));
        g.drawString(tacticText, x + INFO_WIDTH - 13 - g.getFontMetrics().stringWidth(tacticText), y + 57);
        g.setColor(new Color(215, 205, 188));
        g.fillRoundRect(x + 13, y + 66, INFO_WIDTH - 26, 9, 9, 9);
        g.setColor(agent.hitPoints > 50 ? new Color(73, 151, 91) : new Color(202, 73, 58));
        g.fillRoundRect(x + 13, y + 66, (INFO_WIDTH - 26) * agent.hitPoints / 100, 9, 9, 9);
        g.setColor(new Color(55, 48, 42));
        g.setFont(getFont().deriveFont(Font.BOLD, 9f));
        var ratingProfile = simulation.scenario() == SimulationScenario.LEGACY_2V2 ?
                RatingProfile.LEGACY : RatingProfile.MODERN;
        var ratingText = text("Niveau : ", "等級：") +
                agent.stats.playerLevel(ratingProfile).label(language) + " " +
                agent.stats.overallRating(ratingProfile) + " · T" +
                agent.stats.technicalRating(ratingProfile) + "/M" +
                agent.stats.mentalRating(ratingProfile);
        ratingText = fitText(g, ratingText, INFO_WIDTH - 26);
        g.drawString(ratingText, x + (INFO_WIDTH - g.getFontMetrics().stringWidth(ratingText)) / 2, y + 88);
        g.setFont(getFont().deriveFont(Font.PLAIN, 11f));
        compactStat(g, text("Jambes", "步法"), agent.stats.footwork(), x + 13, y + 96);
        compactStat(g, text("Distance", "距離"), agent.stats.distanceControl(), x + 13, y + 112);
        compactStat(g, text("Précision", "瞄準"), agent.stats.aim(), x + 13, y + 128);
        compactStat(g, text("Attaque", "進攻"), agent.stats.attackTiming(), x + 13, y + 144);
        compactStat(g, text("Défense", "防守"), agent.stats.defenseTiming(), x + 13, y + 160);
        compactStat(g, text("Réaction", "反應"), agent.stats.reaction(), x + 120, y + 96);
        compactStat(g, text("Agressivité", "攻擊性"), agent.stats.aggression(), x + 120, y + 112);
        compactStat(g, text("Décision", "決策"), agent.stats.decisionMaking(), x + 120, y + 128);
        compactStat(g, text("Chamber", "接招"), agent.stats.chamber(), x + 13, y + 176);
        compactStat(g, text("Collectif", "協作"), agent.stats.teamwork(), x + 120, y + 144);
        if (simulation.scenario() == SimulationScenario.LEGACY_2V2) {
            compactStat(g, text("Double block", "雙重格擋"), agent.stats.doubleBlock(), x + 13, y + 192);
            compactStat(g, text("Dézonage", "脫離"), agent.stats.zoneExit(), x + 120, y + 160);
            compactStat(g, text("Pression", "施壓"), agent.stats.pressure(), x + 120, y + 176);
            compactStat(g, text("50/50", "對刺"), agent.stats.fiftyFifty(), x + 120, y + 192);
        }

        g.setFont(getFont().deriveFont(Font.BOLD, 11f));
        var pressureText = agent.pressureReceived < 0.05 ? "" :
                text(" · pression ", " · 受壓 ") + Math.round(agent.pressureReceived * 100) + "%";
        var localRoleText = agent.localRole == LocalRole.FREE ? "" : " · " + agent.localRole.label(language);
        var modern = simulation.scenario() == SimulationScenario.MODERN_2V2;
        var engagementText = agent.engagement == null ? "" :
                " · " + agent.tacticalIntent + " · " +
                        agent.engagement.type + "/" + agent.engagement.status;
        var stateText = text("État : ", "狀態：") + agent.state.label(language) +
                (modern ? engagementText :
                        (pressureText.isEmpty() ? " · " + agent.engagementPhase.label(language) : pressureText) +
                                localRoleText);
        g.drawString(fitText(g, stateText, INFO_WIDTH - 26), x + 13, y + 208);
        var decisionText = text("Décision : ", "決策：") + agent.decision.label(language);
        g.drawString(fitText(g, decisionText, INFO_WIDTH - 26), x + 13, y + 230);
        g.setFont(getFont().deriveFont(Font.PLAIN, 11f));
        var target = agent.alive && agent.target != null && agent.target.alive ? agent.target : null;
        var tacticalTarget = agent.plannedTarget != null && agent.plannedTarget.alive ?
                agent.plannedTarget : null;
        var targetText = modern ?
                text("Cible : ", "目標：") +
                        (tacticalTarget == null ? "—" : tacticalTarget.name(language)) +
                        (target == tacticalTarget ? "" :
                                text(" · actuelle ", " · 當前 ") +
                                        (target == null ? "—" : target.name(language))) :
                text("Adversaire : ", "對手：") +
                        (target == null ? "—" : target.name(language));
        g.drawString(fitText(g, targetText, INFO_WIDTH - 26), x + 13, y + 252);
        var distanceText = target == null ? text("Distance : —", "距離：—") :
                text("Distance : %.2f m", "距離：%.2f 公尺").formatted(Math.hypot(target.getX() - agent.getX(), target.getY() - agent.getY()));
        g.drawString(distanceText, x + 13, y + 272);
        var backProtectionText = text("Dos : ", "護背：") + agent.stats.backProtection();
        g.setColor(agent.backExposed ? new Color(190, 45, 45) : new Color(55, 48, 42));
        g.drawString(backProtectionText,
                x + INFO_WIDTH - 13 - g.getFontMetrics().stringWidth(backProtectionText), y + 272);
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
        g.drawString(text("Combat : ", "攻防：") + directionText, x + 13, y + 292);
        g.setColor(new Color(55, 48, 42));
        var attacking = agent.state == CombatState.FEINT || agent.state == CombatState.ATTACK_PREPARE ||
                agent.state == CombatState.ATTACK_HOLD ||
                agent.state == CombatState.CHAMBER_PREPARE || agent.state == CombatState.CHAMBER_HOLD ||
                agent.isAttacking() || agent.state == CombatState.RECOVERY;
        g.drawString(text("Zone / hauteur : ", "部位／高度：") + (attacking ?
                        agent.intendedZone.label(language) + "  %.2f m".formatted(agent.attackHeight) : "—"),
                x + 13, y + 312);
        g.drawString(text("Angles H/V/A : ", "角度 水／垂／刺：") + "%.0f°/%+.0f°/%+.0f°".formatted(
                        Math.toDegrees(agent.angle), Math.toDegrees(agent.pitch), Math.toDegrees(agent.weaponYaw)),
                x + 13, y + 328);
        g.setColor(agent.lastHitZone == null ? new Color(110, 105, 98) : new Color(170, 35, 35));
        g.drawString(text("Blessure : ", "受傷：") + (agent.lastHitZone == null ? "—" :
                agent.lastHitZone.label(language) + " -" + agent.lastDamage), x + 13, y + 343);
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
        var title = simulation.experimentalRoundComplete() ? switch (simulation.experimentalRoundLeader()) {
            case BLUE -> blueTeamName + text(" gagne la phase", "贏得本階段");
            case RED -> redTeamName + text(" gagne la phase", "贏得本階段");
            case DRAW -> text("Phase sans avantage", "本階段平手");
        } : simulation.winningTeam == null ? blueTeamName + "  VS  " + redTeamName : switch (simulation.winningTeam) {
            case BLUE -> blueTeamName + text(" gagne !", "勝利！");
            case RED -> redTeamName + text(" gagne !", "勝利！");
            case DRAW -> text("Match nul !", "平手！");
        };
        g.drawString(title, (getWidth() - g.getFontMetrics().stringWidth(title)) / 2, 39);
        g.setFont(getFont().deriveFont(Font.BOLD, 13f));
        var modern = simulation.scenario() == SimulationScenario.MODERN_2V2;
        var group3 = simulation.scenario() == SimulationScenario.MODERN_3V3 ||
                simulation.scenario() == SimulationScenario.CONTROLLED_3V3;
        var phase = modern ? "MODERN_2V2" : group3 ?
                "MODERN_3V3 · " + simulation.experimentalGroupPhase() :
                text("Phase : ", "階段：") + simulation.combatPhase.label(language);
        var legend = phase + "   ·   " + (modern || group3 ?
                text("↑ Haute : portée / lente   ↓ Basse : courte / rapide   ↔ Latérale : erreur",
                        "↑ 上段：長射程／慢   ↓ 下段：短射程／快   ↔ 側擋：失誤") :
                text("↑ Haute : portée / lente   ↓ Basse : courte / rapide   " +
                                "↔ Latérale : erreur   ⇅ Double Block",
                        "↑ 上段：長射程／慢   ↓ 下段：短射程／快   ↔ 側擋：失誤   ⇅ 雙重格擋"));
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
        var controls = text("Espace : pause   R : recommencer   L : langue   D : debug   0 : 0,25×   5 : 0,5×   " +
                         "1 / 2 / 4 : vitesse   Actuel : %s×",
                "空白鍵：暫停   R：重新開始   L：語言   D：除錯   0：0.25 倍   5：0.5 倍   " +
                         "1／2／4：速度   目前：%s 倍").formatted(speedText);
        g.setColor(new Color(70, 62, 52));
        g.setFont(getFont().deriveFont(Font.PLAIN, 14f));
        g.drawString(controls, (getWidth() - g.getFontMetrics().stringWidth(controls)) / 2, getHeight() - 18);
    }
}
