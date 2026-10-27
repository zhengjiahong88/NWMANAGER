import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;

public final class AttributeBehaviorAudit {
    public static void main(String[] args) throws IOException {
        var lines = new ArrayList<String>();
        double reactionLow = reactionDelay(20), reactionHigh = reactionDelay(80);
        lines.add("reaction 20 estimatedDelay=%.3f".formatted(reactionLow));
        lines.add("reaction 80 estimatedDelay=%.3f".formatted(reactionHigh));
        var decisionLow = opportunityWith(Stat.DECISION, 20, 701L);
        var decisionHigh = opportunityWith(Stat.DECISION, 80, 701L);
        lines.add("decision 20 score=%.3f reason=%s rejection=%s".formatted(
                decisionLow.score(), decisionLow.primaryReason(), decisionLow.rejectionReason()));
        lines.add("decision 80 score=%.3f reason=%s rejection=%s".formatted(
                decisionHigh.score(), decisionHigh.primaryReason(), decisionHigh.rejectionReason()));
        var teamworkLow = coverWith(20, 702L);
        var teamworkHigh = coverWith(80, 702L);
        lines.add("collectif 20 coverScore=%.3f".formatted(teamworkLow));
        lines.add("collectif 80 coverScore=%.3f".formatted(teamworkHigh));
        var distanceLow = opportunityWith(Stat.DISTANCE, 20, 703L);
        var distanceHigh = opportunityWith(Stat.DISTANCE, 80, 703L);
        lines.add("distance 20 perceptionSigma=%.3f confidence=%.3f".formatted(
                perceptionSigma(20), distanceLow.evidence().perceptionConfidence()));
        lines.add("distance 80 perceptionSigma=%.3f confidence=%.3f".formatted(
                perceptionSigma(80), distanceHigh.evidence().perceptionConfidence()));
        require(reactionHigh < reactionLow, "Higher reaction must shorten observed response delay.");
        require(decisionHigh.score() > decisionLow.score(),
                "Higher decision must improve the same physical opportunity assessment.");
        require(teamworkHigh > teamworkLow,
                "Higher collectif must improve the same physical cover assessment.");
        require(perceptionSigma(80) < perceptionSigma(20) &&
                        distanceHigh.evidence().perceptionConfidence() >
                                distanceLow.evidence().perceptionConfidence(),
                "Higher distance control must reduce error and improve confidence.");
        var output = Path.of("out", "visual-validation", "attribute-comparison.txt");
        Files.createDirectories(output.getParent());
        Files.write(output, lines, StandardCharsets.UTF_8);
        System.out.println("Controlled attribute behavior audit passed: " + output.toAbsolutePath());
    }

    private static double reactionDelay(int reaction) {
        return 0.10 + (100 - reaction) * 0.0045 + 0.035;
    }

    private static double perceptionSigma(int distanceControl) {
        return (100 - distanceControl) * 0.006;
    }

    private static AttackOpportunity opportunityWith(Stat stat, int value, long seed) {
        var configuration = configuration("Audit", stat, value);
        var simulation = new DuelSimulation(configuration, AgentConfiguration.defaultBlueTwo(),
                AgentConfiguration.defaultRed(), AgentConfiguration.defaultRedTwo(),
                SimulationScenario.MODERN_2V2, seed);
        var attacker = simulation.blueTeam[0];
        var target = simulation.redTeam[0];
        reset(attacker, new Pos(3.00, 3.05), 0);
        reset(simulation.blueTeam[1], new Pos(3.08, 4.20), -0.25);
        reset(target, new Pos(4.40, 3.05), Math.PI);
        reset(simulation.redTeam[1], new Pos(4.45, 4.22), Math.PI);
        attacker.primaryMark = attacker.target = target;
        observeAll(simulation);
        return simulation.evaluateAttackOpportunity(attacker, target);
    }

    private static double coverWith(int teamwork, long seed) {
        var coverConfiguration = configuration("Cover", Stat.TEAMWORK, teamwork);
        var simulation = new DuelSimulation(AgentConfiguration.defaultBlue(), coverConfiguration,
                AgentConfiguration.defaultRed(), AgentConfiguration.defaultRedTwo(),
                SimulationScenario.MODERN_2V2, seed);
        var protectedAlly = simulation.blueTeam[0];
        var coverer = simulation.blueTeam[1];
        var enemy = simulation.redTeam[0];
        reset(protectedAlly, new Pos(3.00, 3.10), 0);
        reset(coverer, new Pos(3.14, 4.16), -0.35);
        reset(enemy, new Pos(4.46, 3.10), Math.PI);
        observeAll(simulation);
        return simulation.evaluateCover(protectedAlly, coverer, enemy).score();
    }

    private static AgentConfiguration configuration(String name, Stat stat, int value) {
        var base = AgentConfiguration.defaultBlue().stats();
        return new AgentConfiguration(name, new AgentStats(
                base.footwork(), stat == Stat.DISTANCE ? value : base.distanceControl(),
                base.aim(), base.attackTiming(), base.defenseTiming(),
                stat == Stat.REACTION ? value : base.reaction(), base.aggression(),
                stat == Stat.DECISION ? value : base.decisionMaking(), base.zoneExit(),
                base.chamber(), base.doubleBlock(), base.pressure(), base.fiftyFifty(),
                stat == Stat.TEAMWORK ? value : base.teamwork()), Tactic.NEUTRAL);
    }

    private static void observeAll(DuelSimulation simulation) {
        for (var observer : simulation.agents)
            for (var opponent : simulation.agents) {
                if (observer.team == opponent.team) continue;
                var awareness = observer.opponentAwareness.get(opponent);
                awareness.visible = true;
                awareness.lastKnown = opponent.pos;
                awareness.perceivedVelocity = opponent.velocity;
                awareness.observedBodyAngle = opponent.angle;
                awareness.observedWeaponAngle = opponent.weaponWorldAngle();
                awareness.observedProjectedRange = opponent.projectedThrustLength();
                awareness.observedState = opponent.state;
                awareness.observedStateTime = opponent.stateTime;
                awareness.observedAttackDirection = opponent.attackDirection;
                awareness.observedBlockDirection = opponent.blockDirection;
                awareness.memoryTime = 2;
            }
    }

    private static void reset(Musketeer agent, Pos position, double angle) {
        agent.pos = agent.previous = position;
        agent.velocity = new Pos();
        agent.angle = angle;
        agent.weaponYaw = agent.desiredWeaponYaw = 0;
        agent.state = CombatState.NEUTRAL;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    private enum Stat { REACTION, DECISION, TEAMWORK, DISTANCE }
}
