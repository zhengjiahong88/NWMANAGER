import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;

final class VisualValidation3v3Capture {
    private static final double DT = 1.0 / 120.0;
    private static final int CELL_WIDTH = 300, CELL_HEIGHT = 200;

    public static void main(String[] args) throws IOException {
        var seeds = args.length == 0 ? new long[]{41, 73, 119} : new long[]{Long.parseLong(args[0])};
        for (var seed : seeds) capture(seed);
        System.out.println("3v3 milestone-6 visual captures written to out/visual-validation-3v3/.");
    }

    private static void capture(long seed) throws IOException {
        var directory = Path.of("out", "visual-validation-3v3", "seed-" + seed);
        Files.createDirectories(directory);
        var simulation = fixture(seed);
        simulation.forceGroup3Maneuver(Team.BLUE, TeamManeuverType.ROTATE_RIGHT);
        simulation.forceGroup3Maneuver(Team.RED, TeamManeuverType.PRESS_FORWARD);
        var contact = new BufferedImage(CELL_WIDTH * 4, CELL_HEIGHT * 4, BufferedImage.TYPE_INT_ARGB);
        var contactGraphics = contact.createGraphics();
        contactGraphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        var paths = new ArrayList<Pos[]>();
        for (var agent : simulation.agents) paths.add(new Pos[16]);
        var timeline = new StringBuilder("time;team;left;center;right;width;depth;cohesion;maneuver\n");
        var attentionTimeline = new StringBuilder(
                "time;observer;primary;secondary;peripheral;weights;pending;confidence;attempts\n");
        var coverTimeline = new StringBuilder(
                "time;team;active;connectivity;weakest;weakestCoverage;viableLinks\n");
        for (var frame = 0; frame < 16; ++frame) {
            if (frame > 0 && !simulation.experimentalFirstEliminationObserved())
                for (var tick = 0; tick < 120; ++tick) simulation.update(DT);
            paintFrame(contactGraphics, simulation, frame % 4 * CELL_WIDTH, frame / 4 * CELL_HEIGHT,
                    "t=%.1fs".formatted(simulation.simulationTime));
            for (var n = 0; n < simulation.agents.length; ++n) paths.get(n)[frame] = simulation.agents[n].pos;
            appendFormation(timeline, simulation, Team.BLUE);
            appendFormation(timeline, simulation, Team.RED);
            appendAttention(attentionTimeline, simulation);
            appendCoverNetwork(coverTimeline, simulation);
        }
        while (!simulation.experimentalFirstEliminationObserved() && simulation.simulationTime < 30)
            simulation.update(DT);
        if (simulation.experimentalFirstEliminationObserved())
            paintFrame(contactGraphics, simulation, 3 * CELL_WIDTH, 3 * CELL_HEIGHT,
                    "t=%.1fs — 1re élimination".formatted(simulation.simulationTime));
        contactGraphics.dispose();
        var firstKill = simulation.lastKillExplanation();
        var firstChain = simulation.lastKillCausalChain();
        var firstKillText = firstKill == null ? "NONE_OBSERVED\n" :
                firstKill.deterministicKey(UiLanguage.FRENCH) + "\ncausal=" +
                        (firstChain == null ? "NONE_OBSERVED" :
                                firstChain.deterministicKey(UiLanguage.FRENCH)) + "\n";
        var transitions = new BufferedImage(CELL_WIDTH * 4, CELL_HEIGHT * 2,
                BufferedImage.TYPE_INT_ARGB);
        var transitionGraphics = transitions.createGraphics();
        transitionGraphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
        var frame = 0;
        var capturedPhase = simulation.experimentalGroupPhase();
        var capturedAt = simulation.simulationTime;
        paintFrame(transitionGraphics, simulation, 0, 0, "t=%.1fs — %s".formatted(
                simulation.simulationTime, capturedPhase));
        ++frame;
        while (frame < 8 && !simulation.experimentalRoundComplete() &&
                simulation.simulationTime < 120) {
            simulation.update(DT);
            if (simulation.experimentalGroupPhase() == capturedPhase &&
                    simulation.simulationTime - capturedAt < 1.5) continue;
            capturedPhase = simulation.experimentalGroupPhase();
            capturedAt = simulation.simulationTime;
            paintFrame(transitionGraphics, simulation, frame % 4 * CELL_WIDTH,
                    frame / 4 * CELL_HEIGHT, "t=%.1fs — %s".formatted(
                            simulation.simulationTime, simulation.experimentalGroupPhase()));
            ++frame;
        }
        while (frame < 8) {
            paintFrame(transitionGraphics, simulation, frame % 4 * CELL_WIDTH,
                    frame / 4 * CELL_HEIGHT, "t=%.1fs — %s".formatted(
                            simulation.simulationTime, simulation.experimentalGroupPhase()));
            ++frame;
        }
        transitionGraphics.dispose();
        while (!simulation.experimentalRoundComplete() && simulation.simulationTime < 120)
            simulation.update(DT);
        ImageIO.write(contact, "png", directory.resolve("contact-sheet.png").toFile());
        ImageIO.write(transitions, "png", directory.resolve("transition-sheet.png").toFile());
        ImageIO.write(paintTrajectories(simulation, paths), "png", directory.resolve("trajectories.png").toFile());
        Files.writeString(directory.resolve("formations.txt"), timeline);
        Files.writeString(directory.resolve("attention.txt"), attentionTimeline);
        Files.writeString(directory.resolve("cover-network.txt"), coverTimeline);
        var attacks = new StringBuilder();
        for (var event : simulation.tacticalLog())
            if (event.contains("ATTACK") || event.contains("THREAT_DETECTED") ||
                    event.contains("REVENGE") || event.contains("COUNTER_COVER") ||
                    event.contains("EXCHANGE") || event.contains("KILL") ||
                    event.contains("PHASE_COMPLETE")) attacks.append(event).append('\n');
        Files.writeString(directory.resolve("attacks.txt"), attacks);
        Files.writeString(directory.resolve("first-kill.txt"), firstKillText);
        var transitionLog = new StringBuilder();
        for (var event : simulation.tacticalLog())
            if (event.contains("FIRST_ELIMINATION") || event.contains("COLLECTIVE_TRANSITION") ||
                    event.contains("ENGINE_HANDOFF") || event.contains("MATCH_COMPLETE"))
                transitionLog.append(event).append('\n');
        Files.writeString(directory.resolve("transitions.txt"), transitionLog);
        Files.writeString(directory.resolve("match-result.txt"), "time=%.3f\nphase=%s\nwinner=%s\n".formatted(
                simulation.simulationTime, simulation.experimentalGroupPhase(),
                simulation.experimentalRoundLeader()));
    }

    private static void paintFrame(Graphics2D g, DuelSimulation simulation, int x, int y, String label) {
        g.setColor(new Color(247, 230, 199));
        g.fillRect(x, y, CELL_WIDTH, CELL_HEIGHT);
        var scale = Math.min((CELL_WIDTH - 28.0) / DuelSimulation.ARENA_WIDTH,
                (CELL_HEIGHT - 32.0) / DuelSimulation.ARENA_HEIGHT);
        var origin = new Pos(x + (CELL_WIDTH - DuelSimulation.ARENA_WIDTH * scale) / 2,
                y + 22 + (CELL_HEIGHT - 24 - DuelSimulation.ARENA_HEIGHT * scale) / 2);
        g.setColor(new Color(170, 143, 103));
        g.drawRect((int) origin.x(), (int) origin.y(), (int) (DuelSimulation.ARENA_WIDTH * scale),
                (int) (DuelSimulation.ARENA_HEIGHT * scale));
        var group = simulation.modernGroup3Engagement();
        if (group != null) {
            paintFormation(g, group.blueFormation, origin, scale);
            paintFormation(g, group.redFormation, origin, scale);
            paintCoverLinks(g, group.blueCoverNetwork, origin, scale);
            paintCoverLinks(g, group.redCoverNetwork, origin, scale);
        }
        for (var agent : simulation.agents) {
            var point = origin.plus(agent.pos.times(scale));
            g.setColor(agent.color);
            if (agent.alive) g.fillOval((int) point.x() - 6, (int) point.y() - 6, 12, 12);
            else {
                g.setStroke(new BasicStroke(2.5f));
                g.drawLine((int) point.x() - 7, (int) point.y() - 7,
                        (int) point.x() + 7, (int) point.y() + 7);
                g.drawLine((int) point.x() - 7, (int) point.y() + 7,
                        (int) point.x() + 7, (int) point.y() - 7);
            }
            var tip = point.plus(new Pos(agent.angle).times(16));
            g.drawLine((int) point.x(), (int) point.y(), (int) tip.x(), (int) tip.y());
        }
        g.setColor(new Color(45, 39, 34));
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 10));
        g.drawString(label, x + 8, y + 14);
    }

    private static void paintFormation(Graphics2D g, TeamFormation3 formation, Pos origin, double scale) {
        var left = origin.plus(formation.leftWing.pos.times(scale));
        var center = origin.plus(formation.centerPlayer.pos.times(scale));
        var right = origin.plus(formation.rightWing.pos.times(scale));
        g.setColor(new Color(formation.leftWing.color.getRed(), formation.leftWing.color.getGreen(),
                formation.leftWing.color.getBlue(), 120));
        g.drawLine((int) left.x(), (int) left.y(), (int) center.x(), (int) center.y());
        g.drawLine((int) center.x(), (int) center.y(), (int) right.x(), (int) right.y());
    }

    private static void paintCoverLinks(Graphics2D g, CoverNetwork network, Pos origin, double scale) {
        for (var protectedAlly : network.members) {
            CoverLink best = null;
            for (var link : network.incomingLinks.get(protectedAlly))
                if (link.viable() && (best == null || link.quality() > best.quality())) best = link;
            if (best == null) continue;
            var start = origin.plus(best.protector().pos.times(scale));
            var end = origin.plus(best.threateningEnemy().pos.times(scale));
            g.setColor(new Color(42, 151, 92, 105));
            g.setStroke(new BasicStroke(1.2f));
            g.drawLine((int) start.x(), (int) start.y(), (int) end.x(), (int) end.y());
        }
    }

    private static BufferedImage paintTrajectories(DuelSimulation simulation, ArrayList<Pos[]> paths) {
        var image = new BufferedImage(900, 760, BufferedImage.TYPE_INT_ARGB);
        var g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(247, 230, 199));
        g.fillRect(0, 0, image.getWidth(), image.getHeight());
        var origin = new Pos(42, 46);
        var scale = Math.min(816.0 / DuelSimulation.ARENA_WIDTH, 668.0 / DuelSimulation.ARENA_HEIGHT);
        g.setColor(new Color(170, 143, 103));
        g.drawRect((int) origin.x(), (int) origin.y(), (int) (DuelSimulation.ARENA_WIDTH * scale),
                (int) (DuelSimulation.ARENA_HEIGHT * scale));
        for (var n = 0; n < paths.size(); ++n) {
            g.setColor(simulation.agents[n].color);
            g.setStroke(new BasicStroke(3f));
            var path = paths.get(n);
            for (var frame = 1; frame < path.length; ++frame) {
                var start = origin.plus(path[frame - 1].times(scale));
                var end = origin.plus(path[frame].times(scale));
                g.drawLine((int) start.x(), (int) start.y(), (int) end.x(), (int) end.y());
            }
        }
        g.dispose();
        return image;
    }

    private static void appendFormation(StringBuilder timeline, DuelSimulation simulation, Team team) {
        var group = simulation.modernGroup3Engagement();
        var formation = group.formation(team);
        var maneuver = group.maneuver(team);
        timeline.append("%.2f;%s;%s;%s;%s;%.3f;%.3f;%.3f;%s%n".formatted(
                simulation.simulationTime, team, formation.leftWing.name(UiLanguage.FRENCH),
                formation.centerPlayer.name(UiLanguage.FRENCH), formation.rightWing.name(UiLanguage.FRENCH),
                formation.width, formation.depth, formation.cohesion,
                maneuver == null ? "NONE" : maneuver.type));
    }

    private static void appendAttention(StringBuilder timeline, DuelSimulation simulation) {
        for (var observer : simulation.agents) {
            var allocation = observer.attentionAllocation;
            var primary = observer.opponentAwareness.get(allocation.primaryThreat);
            var secondary = observer.opponentAwareness.get(allocation.secondaryThreat);
            var peripheral = observer.opponentAwareness.get(allocation.peripheralThreat);
            timeline.append("%.2f;%s;%s;%s;%s;%.3f/%.3f/%.3f;%s;%.3f/%.3f/%.3f;%d/%d/%d%n".formatted(
                    simulation.simulationTime, observer.name(UiLanguage.FRENCH),
                    allocation.primaryThreat == null ? "NONE" :
                            allocation.primaryThreat.name(UiLanguage.FRENCH),
                    allocation.secondaryThreat == null ? "NONE" :
                            allocation.secondaryThreat.name(UiLanguage.FRENCH),
                    allocation.peripheralThreat == null ? "NONE" :
                            allocation.peripheralThreat.name(UiLanguage.FRENCH),
                    allocation.primaryWeight, allocation.secondaryWeight, allocation.peripheralWeight,
                    allocation.pendingPrimary == null ? "NONE" :
                            allocation.pendingPrimary.name(UiLanguage.FRENCH),
                    primary == null ? 0 : primary.detailConfidence,
                    secondary == null ? 0 : secondary.detailConfidence,
                    peripheral == null ? 0 : peripheral.detailConfidence,
                    primary == null ? 0 : primary.observationAttempts,
                    secondary == null ? 0 : secondary.observationAttempts,
                    peripheral == null ? 0 : peripheral.observationAttempts));
        }
    }

    private static void appendCoverNetwork(StringBuilder timeline, DuelSimulation simulation) {
        var group = simulation.modernGroup3Engagement();
        for (var team : new Team[]{Team.BLUE, Team.RED}) {
            var network = group.coverNetwork(team);
            var links = new StringBuilder();
            for (var link : network.links) {
                if (!link.viable()) continue;
                if (!links.isEmpty()) links.append('|');
                links.append(link.protector().name(UiLanguage.FRENCH)).append("->")
                        .append(link.protectedAlly().name(UiLanguage.FRENCH)).append("/")
                        .append(link.threateningEnemy().name(UiLanguage.FRENCH)).append(" q=")
                        .append("%.2f".formatted(link.quality())).append(" eta=")
                        .append("%.2f".formatted(link.punishEta()));
            }
            timeline.append("%.2f;%s;%d;%.3f;%s;%.3f;%s%n".formatted(
                    simulation.simulationTime, team, network.activeLinks(), network.connectivity,
                    network.weakestMember == null ? "NONE" :
                            network.weakestMember.name(UiLanguage.FRENCH),
                    network.weakestMemberCoverage, links));
        }
    }

    private static DuelSimulation fixture(long seed) {
        var blue = new TeamConfiguration3("Blue", AgentConfiguration.defaultBlue(),
                AgentConfiguration.defaultBlueTwo(), AgentConfiguration.defaultBlueThree());
        var red = new TeamConfiguration3("Red", AgentConfiguration.defaultRed(),
                AgentConfiguration.defaultRedTwo(), AgentConfiguration.defaultRedThree());
        return new DuelSimulation(blue, red, SimulationScenario.CONTROLLED_3V3, seed);
    }
}
