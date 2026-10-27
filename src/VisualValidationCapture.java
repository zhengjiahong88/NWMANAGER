import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.EnumMap;

public final class VisualValidationCapture {
    private static final double DT = 1.0 / 120.0, MAX_FIRST_DEATH_TIME = 60;
    private static final int FRAME_COUNT = 16, FRAME_COLUMNS = 4,
            TILE_WIDTH = 400, TILE_HEIGHT = 300;

    public static void main(String[] args) throws IOException {
        if (args.length == 1 && args[0].equals("--representative")) {
            generateRepresentativeSet();
            return;
        }
        var seeds = args.length == 0 ? new long[]{41, 73, 119} : parseSeeds(args);
        for (var seed : seeds) generate(seed);
    }

    private static void generateRepresentativeSet() throws IOException {
        var selected = new LinkedHashSet<Long>();
        selected.add(41L);
        selected.add(73L);
        selected.add(119L);
        var causes = new EnumMap<FirstDeathCause, Long>(FirstDeathCause.class);
        var reasons = new EnumMap<AttackOpportunityReason, Long>(AttackOpportunityReason.class);
        var desiredCauses = new FirstDeathCause[]{FirstDeathCause.NORMAL_ATTACK,
                FirstDeathCause.REVENGE, FirstDeathCause.COUNTER_COVER};
        var desiredReasons = new AttackOpportunityReason[]{
                AttackOpportunityReason.TARGET_FOCUSED_ON_ALLY,
                AttackOpportunityReason.TARGET_ATTACK_COMMITTED,
                AttackOpportunityReason.TARGET_BLOCK_COMMITTED,
                AttackOpportunityReason.TARGET_MOVING_INTO_LINE,
                AttackOpportunityReason.RISKY_INITIATIVE};
        for (var seed = 1L; seed <= 500; ++seed) {
            var simulation = simulation(seed);
            while (simulation.simulationTime < MAX_FIRST_DEATH_TIME && aliveCount(simulation) == 4)
                simulation.update(DT);
            var metrics = simulation.modernMetrics();
            if (metrics.firstDeathCause != null) causes.putIfAbsent(metrics.firstDeathCause, seed);
            for (var reason : desiredReasons)
                if (metrics.attackOpportunitiesAcceptedByReason.getOrDefault(reason, 0) > 0)
                    reasons.putIfAbsent(reason, seed);
        }
        for (var cause : desiredCauses) if (causes.containsKey(cause)) selected.add(causes.get(cause));
        for (var reason : desiredReasons) if (reasons.containsKey(reason)) selected.add(reasons.get(reason));
        var index = new ArrayList<String>();
        index.add("Selection rule: first matching seed in 1..500; base seeds 41, 73, 119 always included.");
        for (var cause : desiredCauses)
            index.add("firstDeathCause " + cause + " = " +
                    (causes.containsKey(cause) ? "seed-" + causes.get(cause) : "INSUFFICIENT_EVIDENCE"));
        for (var reason : desiredReasons)
            index.add("acceptedReason " + reason + " = " +
                    (reasons.containsKey(reason) ? "seed-" + reasons.get(reason) :
                            "INSUFFICIENT_EVIDENCE"));
        var root = Path.of("out", "visual-validation");
        Files.createDirectories(root);
        write(root.resolve("representative-index.txt"), index);
        for (var seed : selected) generate(seed);
    }

    private static long[] parseSeeds(String[] args) {
        var seeds = new long[args.length];
        for (var n = 0; n < args.length; ++n) seeds[n] = Long.parseLong(args[n]);
        return seeds;
    }

    private static void generate(long seed) throws IOException {
        var directory = Path.of("out", "visual-validation", "seed-" + seed);
        Files.createDirectories(directory);
        var report = captureReport(seed, directory);
        paintContactSheet(seed, report.firstDeathTime, directory.resolve("contact-sheet.png"));
        write(directory.resolve("timeline.txt"), report.maneuverTimeline);
        write(directory.resolve("decisions.txt"), report.decisionTimeline);
        write(directory.resolve("first-kill.txt"), report.firstKill);
        write(directory.resolve("motion.txt"), motionReport(report.motionMetrics));
        var suspicionLines = new ArrayList<String>();
        suspicionLines.add("seed=" + seed);
        suspicionLines.add("diagnostic_only=true");
        suspicionLines.add("events=" + report.suspicions.size());
        for (var event : report.suspicions) suspicionLines.add(event.reportLine());
        if (report.suspicions.isEmpty()) suspicionLines.add("NONE");
        write(directory.resolve("suspicions.txt"), suspicionLines);
        System.out.println(directory.toAbsolutePath());
    }

    private static CaptureReport captureReport(long seed, Path directory) throws IOException {
        var simulation = simulation(seed);
        var audit = new GroupMotionAudit();
        var maneuverTimeline = new ArrayList<String>();
        maneuverTimeline.add("time | team | TeamManeuver | inner | outer | plannedDuration | actualDuration | reason | outcome");
        maneuverTimeline.add("time | agent | LocalManeuver | plannedDuration | actualDuration | reason | interruption");
        var decisionTimeline = new ArrayList<String>();
        decisionTimeline.add("time | attacker | target | action | AttackOpportunityReason | window | impactEta | revengeRisk | opposingCoverQuality | physicalResult");
        decisionTimeline.add("# values come from immutable AttackOpportunityEvidence; missing evidence is never reconstructed after impact.");
        var activeManeuvers = new IdentityHashMap<Musketeer, ObservedManeuver>();
        var activeTeamManeuvers = new java.util.EnumMap<Team, ObservedTeamManeuver>(Team.class);
        var recordedTraces = new LinkedHashSet<String>();
        var trajectory = trajectoryCanvas();
        var graphics = trajectory.createGraphics();
        prepareTrajectoryGraphics(graphics, trajectory);
        var scale = 70.0;
        var origin = new Pos(80, 60);
        var previous = new Pos[simulation.agents.length];
        for (var n = 0; n < previous.length; ++n) previous[n] = simulation.agents[n].pos;
        var nextMarker = 0.5;
        while (simulation.simulationTime < MAX_FIRST_DEATH_TIME && aliveCount(simulation) == 4) {
            simulation.update(DT);
            audit.observe(simulation, DT);
            recordManeuvers(simulation, activeManeuvers, maneuverTimeline, false);
            recordTeamManeuvers(simulation, activeTeamManeuvers, maneuverTimeline, false);
            recordDecisions(simulation, recordedTraces, decisionTimeline);
            for (var n = 0; n < simulation.agents.length; ++n) {
                var agent = simulation.agents[n];
                graphics.setColor(new Color(agent.color.getRed(), agent.color.getGreen(),
                        agent.color.getBlue(), 155));
                var from = origin.plus(previous[n].times(scale));
                var to = origin.plus(agent.pos.times(scale));
                graphics.drawLine((int) from.x(), (int) from.y(), (int) to.x(), (int) to.y());
                previous[n] = agent.pos;
            }
            if (simulation.simulationTime + DT / 2 >= nextMarker) {
                for (var agent : simulation.agents) {
                    var marker = origin.plus(agent.pos.times(scale));
                    graphics.setColor(agent.color);
                    graphics.fillOval((int) marker.x() - 3, (int) marker.y() - 3, 6, 6);
                }
                nextMarker += 0.5;
            }
        }
        audit.finish(simulation.simulationTime);
        recordManeuvers(simulation, activeManeuvers, maneuverTimeline, true);
        recordTeamManeuvers(simulation, activeTeamManeuvers, maneuverTimeline, true);
        recordDecisions(simulation, recordedTraces, decisionTimeline);
        finishTrajectoryGraphics(graphics, simulation, seed, scale, origin);
        ImageIO.write(trajectory, "png", directory.resolve("trajectories.png").toFile());
        return new CaptureReport(simulation.simulationTime, maneuverTimeline, decisionTimeline,
                firstKillReport(seed, simulation), audit.metrics(), audit.events());
    }

    private static void recordManeuvers(DuelSimulation simulation,
                                        IdentityHashMap<Musketeer, ObservedManeuver> active,
                                        ArrayList<String> lines, boolean finish) {
        for (var agent : simulation.agents) {
            var observed = active.get(agent);
            var current = finish ? null : agent.localManeuver;
            if (observed != null && observed.maneuver != current) {
                var age = Math.max(0, simulation.simulationTime - observed.maneuver.startedAt);
                var reached = observed.maneuver.destination.minus(agent.pos).hypotenuse() < 0.10;
                var completed = age >= observed.maneuver.maximumDuration ||
                        age >= observed.maneuver.minimumDuration && reached;
                var outcome = finish ? "ACTIVE_AT_FIRST_DEATH" : completed ? "NONE" : "INTERRUPTED";
                lines.add("%.3f | %s | %s | %.3f–%.3f | %.3f | %s | %s".formatted(
                        observed.maneuver.startedAt, agent.name(UiLanguage.FRENCH),
                        observed.maneuver.type, observed.maneuver.minimumDuration,
                        observed.maneuver.maximumDuration, age,
                        observed.maneuver.primaryReason, outcome));
                active.remove(agent);
            }
            if (current != null && (observed == null || observed.maneuver != current))
                active.put(agent, new ObservedManeuver(current));
        }
    }

    private static void recordTeamManeuvers(DuelSimulation simulation,
                                             java.util.EnumMap<Team, ObservedTeamManeuver> active,
                                             ArrayList<String> lines, boolean finish) {
        var engagement = simulation.modernGroupEngagement();
        var group = engagement == null ? null : engagement.group;
        for (var team : new Team[]{Team.BLUE, Team.RED}) {
            var observed = active.get(team);
            var current = finish || group == null ? null : group.maneuver(team);
            if (observed != null && observed.maneuver != current) {
                var maneuver = observed.maneuver;
                var age = Math.max(0, simulation.simulationTime - maneuver.startedAt);
                var outcome = finish ? "ACTIVE_AT_FIRST_DEATH" :
                        age >= maneuver.minimumDuration ? "COMPLETED_OR_REASSESSED" : "INTERRUPTED";
                lines.add("%.3f | %s | %s | %s | %s | %.3f-%.3f-%.3f | %.3f | %s | %s".formatted(
                        maneuver.startedAt, team, maneuver.type, name(maneuver.innerPlayer),
                        name(maneuver.outerPlayer), maneuver.minimumDuration,
                        maneuver.preferredDuration, maneuver.maximumDuration, age,
                        maneuver.primaryReason, outcome));
                active.remove(team);
            }
            if (current != null && (observed == null || observed.maneuver != current))
                active.put(team, new ObservedTeamManeuver(current));
        }
    }

    private static void recordDecisions(DuelSimulation simulation, LinkedHashSet<String> recorded,
                                        ArrayList<String> lines) {
        for (var trace : simulation.decisionTraces()) {
            if (trace.category() != DecisionCategory.ATTACK &&
                    trace.category() != DecisionCategory.ATTACK_REJECTION) continue;
            var key = trace.deterministicKey(UiLanguage.FRENCH);
            if (!recorded.add(key)) continue;
            var physicalResult = switch (trace.action()) {
                case "BLOCKED", "MISSED", "HIT", "KILL" -> trace.action();
                default -> "PENDING_OR_REFUSED";
            };
            var opportunity = trace.agent() == null ? null : trace.agent().attackOpportunity;
            var opposingCover = opportunity == null ? "INSUFFICIENT_EVIDENCE" :
                    "%.3f".formatted(opportunity.evidence().opposingCoverEta());
            lines.add("%.3f | %s | %s | %s | %s | %.3f | %.3f | %.3f | %s | %s".formatted(
                    trace.time(), name(trace.agent()), name(trace.target()), trace.action(),
                    trace.primaryReason(), trace.availableWindow(), trace.impactEta(),
                    trace.revengeRisk(), opposingCover, physicalResult));
            if (opportunity != null && (trace.action().equals("STAB") ||
                    trace.action().equals("REFUSE_STAB"))) {
                var evidence = opportunity.evidence();
                lines.add("  EVIDENCE body(attacker/ally)=%.3f/%.3f weapon=%.3f/%.3f state=%s remaining=%.3f lane(previous/predicted)=%.3f/%.3f entrySpeed=%.3f entryEta=%.3f inside=%.3f clearance=%.3f confidence=%.3f lowEvidence=%s".formatted(
                        evidence.targetFacingErrorToAttacker(), evidence.targetFacingErrorToAlly(),
                        evidence.targetWeaponErrorToAttacker(), evidence.targetWeaponErrorToAlly(),
                        evidence.observedTargetState(), evidence.observedStateRemaining(),
                        evidence.previousDistanceToThreatLane(),
                        evidence.predictedDistanceToThreatLane(), evidence.laneEntrySpeed(),
                        evidence.timeUntilLaneEntry(), evidence.timeInsideLane(),
                        evidence.attackLineClearance(), evidence.perceptionConfidence(),
                        evidence.movingIntoLineLowEvidence()));
            }
        }
    }

    private static ArrayList<String> firstKillReport(long seed, DuelSimulation simulation) {
        var lines = new ArrayList<String>();
        lines.add("seed=" + seed);
        lines.add("firstDeathTime=%.3f".formatted(simulation.simulationTime));
        lines.add("firstDeathCause=" + simulation.modernMetrics().firstDeathCause);
        var kill = simulation.lastKillExplanation();
        if (kill == null) {
            lines.add("KillExplanation=INSUFFICIENT_EVIDENCE");
            return lines;
        }
        lines.add("killer=" + name(kill.killer()));
        lines.add("victim=" + name(kill.victim()));
        lines.add("initiatingReason=" + kill.initiatingReason());
        lines.add("victimStateAtCommit=" + kill.victimStateAtCommit());
        lines.add("victimStateAtImpact=" + kill.victimStateAtImpact());
        lines.add("victimFocusedOnOtherEnemy=" + kill.victimFocusedOnOtherEnemy());
        lines.add("victimGuardMatched=" + kill.victimGuardMatched());
        lines.add("victimAlly=" + name(kill.victimAlly()));
        lines.add("victimAllyPunishEta=%.3f".formatted(kill.victimAllyPunishEta()));
        lines.add("killerRecoveryEta=%.3f".formatted(kill.killerRecoveryEta()));
        lines.add("coverFailureReason=" + kill.coverFailureReason());
        lines.add("finalCause=" + kill.finalCause());
        var chain = simulation.lastKillCausalChain();
        if (chain == null) lines.add("KillCausalChain=INSUFFICIENT_EVIDENCE");
        else {
            lines.add("teamManeuver=" + chain.observed(chain.teamManeuver()));
            lines.add("initiatingMovement=" + chain.observed(chain.initiatingMovement()));
            lines.add("attackDecision=" + chain.observed(chain.attackDecision()));
            lines.add("victimReaction=" + chain.observed(chain.victimReaction()));
            lines.add("allyCoverDecision=" + chain.observed(chain.allyCoverDecision()));
            if (chain.physicalResolution().isEmpty()) lines.add("physicalResolution=NONE_OBSERVED");
            else for (var trace : chain.physicalResolution())
                lines.add("physicalResolution=" + chain.observed(trace));
        }
        return lines;
    }

    private static ArrayList<String> motionReport(GroupMotionMetrics metrics) {
        var lines = new ArrayList<String>();
        lines.add("individualVelocityCorrelation=%.5f".formatted(
                metrics.individualVelocityCorrelation()));
        lines.add("teamVelocityCorrelation=%.5f".formatted(metrics.teamVelocityCorrelation()));
        lines.add("simultaneousDirectionChanges=" + metrics.simultaneousDirectionChanges());
        lines.add("simultaneousStops=" + metrics.simultaneousStops());
        lines.add("identicalManeuverStarts=" + metrics.identicalManeuverStarts());
        lines.add("localDirectionOppositionTime=%.5f".formatted(
                metrics.localDirectionOppositionTime()));
        lines.add("formationAreaVariation=%.5f".formatted(metrics.formationAreaVariation()));
        lines.add("partnerDistanceVariation=%.5f".formatted(metrics.partnerDistanceVariation()));
        return lines;
    }

    private static void paintContactSheet(long seed, double firstDeathTime, Path output)
            throws IOException {
        var simulation = simulation(seed);
        var image = new BufferedImage(TILE_WIDTH * FRAME_COLUMNS,
                TILE_HEIGHT * (FRAME_COUNT / FRAME_COLUMNS), BufferedImage.TYPE_INT_ARGB);
        var graphics = image.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
        for (var n = 0; n < FRAME_COUNT; ++n) {
            var frameTime = firstDeathTime * n / (FRAME_COUNT - 1);
            while (simulation.simulationTime + DT / 2 < frameTime &&
                    aliveCount(simulation) == 4) simulation.update(DT);
            paintFrame(graphics, simulation, n % FRAME_COLUMNS * TILE_WIDTH,
                    n / FRAME_COLUMNS * TILE_HEIGHT, seed);
        }
        graphics.dispose();
        ImageIO.write(image, "png", output.toFile());
    }

    private static void paintFrame(Graphics2D graphics, DuelSimulation simulation,
                                   int tileX, int tileY, long seed) throws IOException {
        graphics.setColor(new Color(238, 214, 175));
        graphics.fillRect(tileX, tileY, TILE_WIDTH, TILE_HEIGHT);
        graphics.setColor(new Color(88, 67, 45));
        graphics.drawRect(tileX, tileY, TILE_WIDTH - 1, TILE_HEIGHT - 1);
        var scale = 34.0;
        var origin = new Pos(tileX + 55, tileY + 38);
        graphics.setColor(new Color(245, 226, 191));
        graphics.fillRoundRect((int) origin.x(), (int) origin.y(),
                (int) (DuelSimulation.ARENA_WIDTH * scale),
                (int) (DuelSimulation.ARENA_HEIGHT * scale), 14, 14);
        graphics.setColor(new Color(165, 139, 100));
        graphics.drawRoundRect((int) origin.x(), (int) origin.y(),
                (int) (DuelSimulation.ARENA_WIDTH * scale),
                (int) (DuelSimulation.ARENA_HEIGHT * scale), 14, 14);
        for (var agent : simulation.agents) {
            if (agent.alive && agent.localManeuver != null) {
                var start = origin.plus(agent.pos.times(scale));
                var end = origin.plus(agent.localManeuver.destination.times(scale));
                graphics.setColor(new Color(55, 48, 42, 80));
                graphics.drawLine((int) start.x(), (int) start.y(),
                        (int) end.x(), (int) end.y());
            }
            agent.paint(graphics, scale, origin, UiLanguage.FRENCH);
        }
        graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        graphics.setColor(new Color(38, 34, 30));
        graphics.drawString("seed=" + seed + "  t=" +
                "%.2fs".formatted(simulation.simulationTime), tileX + 10, tileY + 18);
        var text = new StringBuilder();
        for (var agent : simulation.agents) {
            if (!text.isEmpty()) text.append(" | ");
            text.append(agent.name(UiLanguage.FRENCH)).append(' ').append(agent.state);
        }
        graphics.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 9));
        graphics.drawString(text.toString(), tileX + 10, tileY + TILE_HEIGHT - 10);
    }

    private static BufferedImage trajectoryCanvas() {
        return new BufferedImage(720, 620, BufferedImage.TYPE_INT_ARGB);
    }

    private static void prepareTrajectoryGraphics(Graphics2D graphics, BufferedImage image) {
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setColor(new Color(238, 214, 175));
        graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
        graphics.setColor(new Color(245, 226, 191));
        graphics.fillRoundRect(80, 60, (int) (DuelSimulation.ARENA_WIDTH * 70),
                (int) (DuelSimulation.ARENA_HEIGHT * 70), 18, 18);
    }

    private static void finishTrajectoryGraphics(Graphics2D graphics, DuelSimulation simulation,
                                                 long seed, double scale, Pos origin) {
        graphics.setColor(new Color(165, 139, 100));
        graphics.drawRoundRect((int) origin.x(), (int) origin.y(),
                (int) (DuelSimulation.ARENA_WIDTH * scale),
                (int) (DuelSimulation.ARENA_HEIGHT * scale), 18, 18);
        graphics.setColor(new Color(38, 34, 30));
        graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        graphics.drawString("Trajectoires à 1× jusqu'à la première mort — %.2fs — seed %d"
                .formatted(simulation.simulationTime, seed), 80, 34);
        graphics.dispose();
    }

    private static DuelSimulation simulation(long seed) {
        return new DuelSimulation(AgentConfiguration.defaultBlue(),
                AgentConfiguration.defaultBlueTwo(), AgentConfiguration.defaultRed(),
                AgentConfiguration.defaultRedTwo(), SimulationScenario.MODERN_2V2, seed);
    }

    private static int aliveCount(DuelSimulation simulation) {
        var count = 0;
        for (var agent : simulation.agents) if (agent.alive) ++count;
        return count;
    }

    private static String name(Musketeer agent) {
        return agent == null ? "NONE" : agent.name(UiLanguage.FRENCH);
    }

    private static void write(Path path, ArrayList<String> lines) throws IOException {
        Files.write(path, lines, StandardCharsets.UTF_8);
    }

    private record ObservedManeuver(LocalManeuver maneuver) {
    }

    private record ObservedTeamManeuver(TeamManeuver maneuver) {
    }

    private record CaptureReport(double firstDeathTime,
                                 ArrayList<String> maneuverTimeline,
                                 ArrayList<String> decisionTimeline,
                                 ArrayList<String> firstKill,
                                 GroupMotionMetrics motionMetrics,
                                 java.util.List<GroupMotionSuspicionEvent> suspicions) {
    }
}
