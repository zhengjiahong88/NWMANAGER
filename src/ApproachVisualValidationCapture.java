import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;

final class ApproachVisualValidationCapture {
    private static final double DT = 1.0 / 120.0;
    private static final int CELL_WIDTH = 320, CELL_HEIGHT = 220;
    private static final double[] FRAME_TIMES = {0, 0.40, 0.80, 1.20, 1.60, 2.00, 2.40, 2.80};

    public static void main(String[] args) throws IOException {
        new ApproachVisualValidationCapture().captureAll(args.length == 0 ?
                new long[]{41, 73, 119} : new long[]{Long.parseLong(args[0])});
    }

    private void captureAll(long[] seeds) throws IOException {
        var root = Path.of("out", "approach-validation-3v3");
        Files.createDirectories(root);
        var summary = new StringBuilder("seed;mode;firstContact;firstAttack;spread;lateral;depth;flat;simultaneous6\n");
        for (var seed : seeds) {
            var directory = root.resolve("seed-" + seed);
            Files.createDirectories(directory);
            var baseline = capture(seed, true, directory, summary);
            var modern = capture(seed, false, directory, summary);
            ImageIO.write(combineVertical(baseline.contactSheet, modern.contactSheet,
                    "BASELINE — contribution d'approche désactivée",
                    "APPROCHE OBLIQUE ACTIVE"), "png",
                    directory.resolve("comparison-contact-sheet.png").toFile());
            ImageIO.write(combineHorizontal(baseline.trajectories, modern.trajectories,
                    "BASELINE", "APPROCHE ACTIVE"), "png",
                    directory.resolve("comparison-trajectories.png").toFile());
        }
        Files.writeString(root.resolve("visual-summary.txt"), summary);
        System.out.println("Approach visual validation written to " + root.toAbsolutePath());
    }

    private CaptureResult capture(long seed, boolean baseline, Path directory,
                                  StringBuilder summary) throws IOException {
        var simulation = fixture(seed);
        if (baseline) simulation.disableGroup3ApproachSteeringForVisualBaseline();
        var group = simulation.modernGroup3Engagement();
        var frames = new ArrayList<FrameSnapshot>();
        var paths = new ArrayList<ArrayList<Pos>>();
        for (var ignored : simulation.agents) paths.add(new ArrayList<>());
        var nextFrame = 0;
        var nextPathAt = 0.0;
        while (simulation.simulationTime <= 3.20 && !simulation.experimentalRoundComplete()) {
            if (nextFrame < FRAME_TIMES.length && simulation.simulationTime + DT * 0.5 >=
                    FRAME_TIMES[nextFrame]) {
                frames.add(snapshot(simulation, group));
                ++nextFrame;
            }
            if (simulation.simulationTime + DT * 0.5 >= nextPathAt) {
                for (var n = 0; n < simulation.agents.length; ++n)
                    paths.get(n).add(simulation.agents[n].pos);
                nextPathAt += 0.05;
            }
            simulation.update(DT);
        }
        while (nextFrame < FRAME_TIMES.length) {
            frames.add(snapshot(simulation, group));
            ++nextFrame;
        }
        while (group.approachObservation.flatFrontAudit == null &&
                !simulation.experimentalRoundComplete() && simulation.simulationTime < 8.0)
            simulation.update(DT);
        while (simulation.group3Metrics().timeToFirstAttack < 0 &&
                !simulation.experimentalRoundComplete() && simulation.simulationTime < 8.0)
            simulation.update(DT);
        var prefix = baseline ? "old" : "new";
        var contactSheet = paintContactSheet(frames);
        var trajectories = paintTrajectories(simulation, paths);
        ImageIO.write(contactSheet, "png", directory.resolve(prefix + "-contact-sheet.png").toFile());
        ImageIO.write(trajectories, "png", directory.resolve(prefix + "-trajectories.png").toFile());
        writeReports(directory, prefix, seed, baseline, simulation, group, summary);
        return new CaptureResult(contactSheet, trajectories);
    }

    private FrameSnapshot snapshot(DuelSimulation simulation, Group3v3Engagement group) {
        var agents = new AgentSnapshot[simulation.agents.length];
        for (var n = 0; n < agents.length; ++n) {
            var agent = simulation.agents[n];
            agents[n] = new AgentSnapshot(agent.pos, agent.angle, agent.state, agent.color,
                    agent.team, agent.alive, n % 3 + 1);
        }
        return new FrameSnapshot(simulation.simulationTime, group.contactBand,
                group.approachObservation.firstContacts.size(), agents,
                formationLine(group.blueFormation), formationLine(group.redFormation));
    }

    private Pos[] formationLine(TeamFormation3 formation) {
        return new Pos[]{formation.leftWing.pos, formation.centerPlayer.pos, formation.rightWing.pos};
    }

    private BufferedImage paintContactSheet(ArrayList<FrameSnapshot> frames) {
        var image = new BufferedImage(CELL_WIDTH * 4, CELL_HEIGHT * 2, BufferedImage.TYPE_INT_ARGB);
        var graphics = image.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        for (var n = 0; n < frames.size(); ++n)
            paintFrame(graphics, frames.get(n), n % 4 * CELL_WIDTH, n / 4 * CELL_HEIGHT);
        graphics.dispose();
        return image;
    }

    private void paintFrame(Graphics2D graphics, FrameSnapshot frame, int cellX, int cellY) {
        graphics.setColor(new Color(247, 230, 199));
        graphics.fillRect(cellX, cellY, CELL_WIDTH, CELL_HEIGHT);
        var scale = Math.min((CELL_WIDTH - 24.0) / DuelSimulation.ARENA_WIDTH,
                (CELL_HEIGHT - 38.0) / DuelSimulation.ARENA_HEIGHT);
        var origin = new Pos(cellX + (CELL_WIDTH - DuelSimulation.ARENA_WIDTH * scale) / 2,
                cellY + 28 + (CELL_HEIGHT - 32 - DuelSimulation.ARENA_HEIGHT * scale) / 2);
        graphics.setColor(new Color(167, 137, 98));
        graphics.setStroke(new BasicStroke(1.2f));
        graphics.drawRect((int) origin.x(), (int) origin.y(),
                (int) (DuelSimulation.ARENA_WIDTH * scale),
                (int) (DuelSimulation.ARENA_HEIGHT * scale));
        paintFormation(graphics, frame.blueFormation, origin, scale, new Color(34, 96, 190, 105));
        paintFormation(graphics, frame.redFormation, origin, scale, new Color(190, 52, 58, 105));
        for (var agent : frame.agents) paintAgent(graphics, agent, origin, scale);
        graphics.setColor(new Color(42, 37, 32));
        graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 11));
        graphics.drawString("t=%.2f s  %s  contacts=%d".formatted(frame.time,
                frame.band, frame.firstContacts), cellX + 8, cellY + 16);
    }

    private void paintFormation(Graphics2D graphics, Pos[] line, Pos origin, double scale, Color color) {
        graphics.setColor(color);
        graphics.setStroke(new BasicStroke(2f));
        for (var n = 1; n < line.length; ++n) {
            var start = origin.plus(line[n - 1].times(scale));
            var end = origin.plus(line[n].times(scale));
            graphics.drawLine((int) start.x(), (int) start.y(), (int) end.x(), (int) end.y());
        }
    }

    private void paintAgent(Graphics2D graphics, AgentSnapshot agent, Pos origin, double scale) {
        var point = origin.plus(agent.pos.times(scale));
        graphics.setColor(agent.color);
        if (agent.alive) graphics.fillOval((int) point.x() - 7, (int) point.y() - 7, 14, 14);
        else {
            graphics.setStroke(new BasicStroke(2.5f));
            graphics.drawLine((int) point.x() - 7, (int) point.y() - 7,
                    (int) point.x() + 7, (int) point.y() + 7);
            graphics.drawLine((int) point.x() - 7, (int) point.y() + 7,
                    (int) point.x() + 7, (int) point.y() - 7);
        }
        graphics.setColor(weaponColor(agent.state));
        graphics.setStroke(new BasicStroke(agent.state == CombatState.ATTACK_ACTIVE ||
                agent.state == CombatState.BLOCKING ? 3f : 1.8f));
        var tip = point.plus(new Pos(agent.angle).times(22));
        graphics.drawLine((int) point.x(), (int) point.y(), (int) tip.x(), (int) tip.y());
        graphics.setColor(new Color(35, 31, 28));
        graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 9));
        graphics.drawString((agent.team == Team.BLUE ? "B" : "R") + agent.number,
                (int) point.x() + 7, (int) point.y() - 7);
    }

    private Color weaponColor(CombatState state) {
        if (state == CombatState.ATTACK_ACTIVE || state == CombatState.ATTACK_PREPARE ||
                state == CombatState.ATTACK_HOLD)
            return new Color(238, 182, 24);
        if (state == CombatState.BLOCKING || state == CombatState.BLOCK_PREPARE)
            return new Color(30, 184, 204);
        return new Color(92, 93, 96);
    }

    private BufferedImage paintTrajectories(DuelSimulation simulation,
                                             ArrayList<ArrayList<Pos>> paths) {
        var image = new BufferedImage(720, 620, BufferedImage.TYPE_INT_ARGB);
        var graphics = image.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setColor(new Color(247, 230, 199));
        graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
        var origin = new Pos(38, 34);
        var scale = Math.min(644.0 / DuelSimulation.ARENA_WIDTH, 548.0 / DuelSimulation.ARENA_HEIGHT);
        graphics.setColor(new Color(167, 137, 98));
        graphics.drawRect((int) origin.x(), (int) origin.y(),
                (int) (DuelSimulation.ARENA_WIDTH * scale),
                (int) (DuelSimulation.ARENA_HEIGHT * scale));
        for (var n = 0; n < paths.size(); ++n) {
            graphics.setColor(simulation.agents[n].color);
            graphics.setStroke(new BasicStroke(2.6f));
            var path = paths.get(n);
            for (var point = 1; point < path.size(); ++point) {
                var start = origin.plus(path.get(point - 1).times(scale));
                var end = origin.plus(path.get(point).times(scale));
                graphics.drawLine((int) start.x(), (int) start.y(), (int) end.x(), (int) end.y());
            }
        }
        graphics.dispose();
        return image;
    }

    private BufferedImage combineVertical(BufferedImage first, BufferedImage second,
                                          String firstLabel, String secondLabel) {
        var labelHeight = 24;
        var image = new BufferedImage(Math.max(first.getWidth(), second.getWidth()),
                first.getHeight() + second.getHeight() + labelHeight * 2, BufferedImage.TYPE_INT_ARGB);
        var graphics = image.createGraphics();
        graphics.setColor(new Color(39, 35, 31));
        graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
        graphics.setColor(new Color(245, 236, 220));
        graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
        graphics.drawString(firstLabel, 10, 17);
        graphics.drawImage(first, 0, labelHeight, null);
        graphics.drawString(secondLabel, 10, labelHeight + first.getHeight() + 17);
        graphics.drawImage(second, 0, labelHeight * 2 + first.getHeight(), null);
        graphics.dispose();
        return image;
    }

    private BufferedImage combineHorizontal(BufferedImage first, BufferedImage second,
                                            String firstLabel, String secondLabel) {
        var labelHeight = 24;
        var image = new BufferedImage(first.getWidth() + second.getWidth(),
                Math.max(first.getHeight(), second.getHeight()) + labelHeight, BufferedImage.TYPE_INT_ARGB);
        var graphics = image.createGraphics();
        graphics.setColor(new Color(39, 35, 31));
        graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
        graphics.setColor(new Color(245, 236, 220));
        graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
        graphics.drawString(firstLabel, 10, 17);
        graphics.drawString(secondLabel, first.getWidth() + 10, 17);
        graphics.drawImage(first, 0, labelHeight, null);
        graphics.drawImage(second, first.getWidth(), labelHeight, null);
        graphics.dispose();
        return image;
    }

    private void writeReports(Path directory, String prefix, long seed, boolean baseline,
                              DuelSimulation simulation, Group3v3Engagement group,
                              StringBuilder summary) throws IOException {
        var observation = group.approachObservation;
        var metrics = simulation.group3Metrics();
        var audit = observation.flatFrontAudit;
        var approach = "seed=%d%nmode=%s%ntimeToSpaceContest=%.3f%ntimeToFirstContact=%.3f%n".
                formatted(seed, baseline ? "BASELINE" : "APPROACH_ACTIVE",
                        metrics.timeToSpaceContest, metrics.timeToFirstContact) +
                "timeToFirstAttack=%.3f%npreContactForwardDistance=%.3f%n".
                        formatted(metrics.timeToFirstAttack, metrics.preContactForwardDistance) +
                "preContactLateralDistance=%.3f%naveragePreContactWidth=%.3f%n".
                        formatted(metrics.preContactLateralDistance, metrics.preContactSamples == 0 ? 0 :
                                metrics.preContactWidthSum / metrics.preContactSamples) +
                "averagePreContactDepth=%.3f%nmaximumPreContactDepth=%.3f%n".
                        formatted(metrics.preContactSamples == 0 ? 0 :
                                metrics.preContactDepthSum / metrics.preContactSamples,
                                metrics.maximumPreContactDepth);
        var planHistory = new StringBuilder(approach).append("planHistory=\n");
        for (var event : simulation.tacticalLog())
            if (event.contains("GROUP3_APPROACH_PLAN") ||
                    event.contains("GROUP3_APPROACH_FINISHED") ||
                    event.contains("GROUP3_CONTACT_BAND")) planHistory.append(event).append('\n');
        Files.writeString(directory.resolve(prefix + "-approach.txt"), planHistory);
        if (!baseline) Files.writeString(directory.resolve("approach.txt"), planHistory);
        var contacts = new ArrayList<>(observation.firstContacts.values());
        contacts.sort(Comparator.comparingDouble(FirstContactEvent::time).
                thenComparing(event -> event.agent().name(UiLanguage.FRENCH)));
        var firstContact = new StringBuilder("agent;opponent;time;role;distance;covered\n");
        for (var event : contacts)
            firstContact.append("%s;%s;%.3f;%s;%.3f;%s%n".formatted(
                    event.agent().name(UiLanguage.FRENCH), event.opponent().name(UiLanguage.FRENCH),
                    event.time(), event.role(), event.distance(), event.covered()));
        Files.writeString(directory.resolve(prefix + "-first-contact.txt"), firstContact);
        var auditText = audit == null ? "audit=NOT_FINALIZED\n" : audit + "\n";
        Files.writeString(directory.resolve(prefix + "-flat-front-audit.txt"), auditText);
        if (!baseline) {
            Files.writeString(directory.resolve("first-contact.txt"), firstContact);
            Files.writeString(directory.resolve("flat-front-audit.txt"), auditText);
        }
        summary.append("%d;%s;%.3f;%.3f;%.3f;%.3f;%.3f;%s;%d%n".formatted(seed,
                baseline ? "baseline" : "active", metrics.timeToFirstContact,
                metrics.timeToFirstAttack, metrics.firstContactSpread,
                metrics.preContactLateralDistance,
                metrics.preContactSamples == 0 ? 0 : metrics.preContactDepthSum / metrics.preContactSamples,
                audit != null && audit.suspicious(), metrics.simultaneousSixPlayerContacts));
    }

    private DuelSimulation fixture(long seed) {
        var blue = new TeamConfiguration3("Blue", AgentConfiguration.defaultBlue(),
                AgentConfiguration.defaultBlueTwo(), AgentConfiguration.defaultBlueThree());
        var red = new TeamConfiguration3("Red", AgentConfiguration.defaultRed(),
                AgentConfiguration.defaultRedTwo(), AgentConfiguration.defaultRedThree());
        return new DuelSimulation(blue, red, SimulationScenario.MODERN_3V3, seed);
    }

    private record CaptureResult(BufferedImage contactSheet, BufferedImage trajectories) {
    }

    private record FrameSnapshot(double time, Group3ContactBand band, int firstContacts,
                                 AgentSnapshot[] agents, Pos[] blueFormation, Pos[] redFormation) {
    }

    private record AgentSnapshot(Pos pos, double angle, CombatState state, Color color,
                                 Team team, boolean alive, int number) {
    }
}
