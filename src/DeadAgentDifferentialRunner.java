import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.IdentityHashMap;

final class DeadAgentDifferentialRunner {
    private static final double DT = 1.0 / 120.0;

    DeadAgentDifferentialResult runDeadAgentScenario(long seed, DeadAgentExperimentMode mode) {
        return runDeadAgentScenario(seed, mode, -1, -1);
    }

    DeadAgentDifferentialResult runDeadAgentScenario(long seed, DeadAgentExperimentMode mode,
                                                      long captureStartTick, long captureEndTick) {
        if (mode == DeadAgentExperimentMode.COLLISION_REINTRODUCED)
            throw new UnsupportedOperationException(mode + " belongs to jalon 6.2c.");
        var simulation = fixture(seed);
        placeNearCenter(simulation);
        for (var tick = 0; tick < 60; ++tick) simulation.update(DT);
        var dead = simulation.blueTeam[1];
        if (!dead.alive) throw new IllegalStateException("B2 died before controlled tick 60.");
        simulation.configureDeadAgentExperiment(mode, dead);
        if (captureStartTick >= 0)
            simulation.configureDeadAgentDifferentialCapture(captureStartTick, captureEndTick);
        simulation.eliminateForDeadAgentTest(dead);
        var deathPosition = dead.pos;
        var deathBodyAngle = dead.angle;
        var deathWeaponAngle = dead.weaponWorldAngle();
        if (mode == DeadAgentExperimentMode.HIDDEN_RENDER_ONLY) dead.hiddenAfterDeath = true;
        else if (mode == DeadAgentExperimentMode.VISUAL_OFFSET_ONLY) dead.renderOffset = new Pos(5, 0);
        var snapshots = new ArrayList<SurvivorTickSnapshot>(2_000);
        var events = new ArrayList<CombatEventSnapshot>();
        events.add(new CombatEventSnapshot(60, simulation.simulationTime, "DEATH", id(simulation, dead),
                "NONE", "CONTROLLED_ELIMINATION", 100));
        var tracker = new CombatEventTracker(simulation);
        var transitionIndex = 0;
        var completionRecorded = false;
        var endTime = -1.0;
        for (var offset = 1; offset <= 2_000; ++offset) {
            var tick = 60L + offset;
            simulation.update(DT);
            render(simulation);
            snapshots.add(snapshot(simulation, dead, tick));
            tracker.capture(tick, simulation.simulationTime, events);
            var transitions = simulation.transitionTraces();
            while (transitionIndex < transitions.size()) {
                var transition = transitions.get(transitionIndex++);
                events.add(new CombatEventSnapshot(transition.tick(), transition.simulationTime(),
                        "TRANSITION", transition.controllerBefore(), transition.controllerAfter(),
                        transition.reason(), 0));
            }
            if (!completionRecorded && simulation.experimentalRoundComplete()) {
                completionRecorded = true;
                endTime = simulation.simulationTime;
                events.add(new CombatEventSnapshot(tick, simulation.simulationTime,
                        "MATCH_COMPLETE", simulation.experimentalRoundLeader() == null ? "NONE" :
                        simulation.experimentalRoundLeader().name(), "NONE", "ROUND_COMPLETE", 0));
            }
        }
        if (endTime < 0) endTime = simulation.simulationTime;
        return new DeadAgentDifferentialResult(runName(mode), seed, 60, 60 * DT, snapshots, events,
                simulation.transitionTraces(), simulation.experimentalRoundLeader(), endTime,
                simulation.experimentalRoundComplete(), dead.deadAgentAudit,
                new DeadAgentStateSnapshot(deathPosition, dead.pos, dead.velocity,
                        dead.desiredVelocity, deathBodyAngle, deathWeaponAngle, dead.angle,
                        dead.weaponWorldAngle(), dead.angularVelocity,
                        dead.state, dead.alive, dead.hiddenAfterDeath, dead.renderOffset),
                simulation.differentialStageSnapshots(), simulation.differentialRandomDrawCount(),
                simulation.differentialRandomStateHash());
    }

    java.util.List<DifferentialStageSnapshot> captureDeadAgentTick(long seed,
                                                                   DeadAgentExperimentMode mode,
                                                                   long tick) {
        var simulation = fixture(seed);
        placeNearCenter(simulation);
        for (var current = 0; current < 60; ++current) simulation.update(DT);
        var dead = simulation.blueTeam[1];
        simulation.configureDeadAgentExperiment(mode, dead);
        simulation.configureDeadAgentDifferentialCapture(tick, tick);
        simulation.eliminateForDeadAgentTest(dead);
        for (var current = 61L; current <= tick; ++current) simulation.update(DT);
        return simulation.differentialStageSnapshots();
    }

    java.util.List<DifferentialStageSnapshot> captureDeadAgentWindow(long seed,
                                                                     DeadAgentExperimentMode mode,
                                                                     long startTick,
                                                                     long endTick) {
        var simulation = fixture(seed);
        placeNearCenter(simulation);
        for (var current = 0; current < 60; ++current) simulation.update(DT);
        var dead = simulation.blueTeam[1];
        simulation.configureDeadAgentExperiment(mode, dead);
        simulation.configureDeadAgentDifferentialCapture(startTick, endTick);
        simulation.eliminateForDeadAgentTest(dead);
        for (var current = 61L; current <= endTick; ++current) simulation.update(DT);
        return simulation.differentialStageSnapshots();
    }

    private SurvivorTickSnapshot snapshot(DuelSimulation simulation, Musketeer controlledDead, long tick) {
        var agents = new ArrayList<AgentTickSnapshot>(5);
        var group = simulation.modernGroup3Engagement();
        for (var agent : simulation.agents) {
            if (agent == controlledDead) continue;
            var formationRole = "NONE";
            var approachRole = "NONE";
            var maneuver = "NONE";
            var incoming = 0;
            var outgoing = 0;
            if (group != null && agent.alive) {
                var formation = group.formation(agent.team);
                var role = formation.roleOf(agent);
                formationRole = role.name();
                var plan = group.approachPlan(agent.team);
                if (plan != null) approachRole = role == plan.leadRole ? "LEAD" :
                        role == plan.supportRole ? "SUPPORT" : role == plan.trailingRole ?
                                "TRAILING" : "NONE";
                var teamManeuver = group.maneuver(agent.team);
                maneuver = teamManeuver == null ? "NONE" : teamManeuver.type.name();
                var network = group.coverNetwork(agent.team);
                var incomingLinks = network.incomingLinks.get(agent);
                var outgoingLinks = network.outgoingLinks.get(agent);
                if (incomingLinks != null)
                    for (var link : incomingLinks)
                        if (link.viable()) ++incoming;
                if (outgoingLinks != null)
                    for (var link : outgoingLinks)
                        if (link.viable()) ++outgoing;
            }
            var attention = agent.attentionAllocation;
            var opportunity = agent.attackOpportunity;
            agents.add(new AgentTickSnapshot(id(simulation, agent), agent.pos, agent.velocity,
                    agent.angle, agent.weaponWorldAngle(), agent.state,
                    agent.decision.name() + "/" + agent.groupIntent,
                    id(simulation, agent.target), id(simulation, attention.primaryThreat),
                    id(simulation, attention.secondaryThreat), id(simulation, attention.peripheralThreat),
                    formationRole, approachRole, maneuver,
                    agent.localManeuver == null ? "NONE" : agent.localManeuver.type.name(),
                    incoming, outgoing, opportunity == null || opportunity.primaryReason() == null ?
                    "NONE" : opportunity.primaryReason().name(),
                    opportunity != null && opportunity.viable(), agent.lastGroup3Steering));
        }
        return new SurvivorTickSnapshot(tick, simulation.simulationTime, agents,
                simulation.activeCombatController().name(), simulation.experimentalGroupPhase().name());
    }

    private void render(DuelSimulation simulation) {
        var image = new BufferedImage(800, 700, BufferedImage.TYPE_INT_ARGB);
        var graphics = image.createGraphics();
        try {
            for (var agent : simulation.agents)
                agent.paint(graphics, 70, new Pos(80, 80), UiLanguage.FRENCH);
        } catch (IOException exception) {
            throw new IllegalStateException("Differential render failed.", exception);
        } finally {
            graphics.dispose();
        }
    }

    private String id(DuelSimulation simulation, Musketeer agent) {
        if (agent == null) return "NONE";
        for (var n = 0; n < simulation.blueTeam.length; ++n)
            if (simulation.blueTeam[n] == agent) return "B" + (n + 1);
        for (var n = 0; n < simulation.redTeam.length; ++n)
            if (simulation.redTeam[n] == agent) return "R" + (n + 1);
        return "UNKNOWN";
    }

    private String runName(DeadAgentExperimentMode mode) {
        return switch (mode) {
            case CURRENT_BUGGED_BEHAVIOR -> "A_CURRENT";
            case HIDDEN_RENDER_ONLY -> "B_HIDDEN_RENDER";
            case VISUAL_OFFSET_ONLY -> "C_VISUAL_OFFSET";
            case LOGICALLY_NEUTRALIZED -> "D1_NEUTRALIZED";
            case COLLISION_REINTRODUCED -> "D2_COLLISION_REINTRODUCED";
        };
    }

    private void placeNearCenter(DuelSimulation simulation) {
        for (var n = 0; n < 3; ++n) {
            simulation.blueTeam[n].pos = new Pos(2.30, 2.30 + n * 1.20);
            simulation.redTeam[n].pos = new Pos(5.70, 2.30 + n * 1.20);
            simulation.blueTeam[n].velocity = simulation.redTeam[n].velocity = new Pos();
        }
    }

    private DuelSimulation fixture(long seed) {
        var blue = new TeamConfiguration3("Blue", AgentConfiguration.defaultBlue(),
                AgentConfiguration.defaultBlueTwo(), AgentConfiguration.defaultBlueThree());
        var red = new TeamConfiguration3("Red", AgentConfiguration.defaultRed(),
                AgentConfiguration.defaultRedTwo(), AgentConfiguration.defaultRedThree());
        return new DuelSimulation(blue, red, SimulationScenario.CONTROLLED_3V3, seed);
    }

    private final class CombatEventTracker {
        private final DuelSimulation simulation;
        private final IdentityHashMap<Musketeer, CombatState> states = new IdentityHashMap<>();
        private final IdentityHashMap<Musketeer, GroupIntent> intents = new IdentityHashMap<>();
        private final IdentityHashMap<Musketeer, Integer> hitPoints = new IdentityHashMap<>();
        private final IdentityHashMap<Musketeer, Boolean> alive = new IdentityHashMap<>();

        CombatEventTracker(DuelSimulation simulation) {
            this.simulation = simulation;
            for (var agent : simulation.agents) remember(agent);
        }

        void capture(long tick, double time, ArrayList<CombatEventSnapshot> events) {
            for (var agent : simulation.agents) {
                var previousState = states.get(agent);
                var previousHitPoints = hitPoints.get(agent);
                var previousAlive = alive.get(agent);
                var previousIntent = intents.get(agent);
                if (agent.state != previousState) {
                    var type = switch (agent.state) {
                        case ATTACK_PREPARE -> "ATTACK_PREPARE";
                        case ATTACK_ACTIVE -> "ATTACK_RELEASE";
                        case BLOCK_PREPARE -> "BLOCK_PREPARE";
                        case BLOCK_STUN -> "BLOCK_SUCCESS";
                        case RECOVERY -> previousState == CombatState.ATTACK_ACTIVE ? "ATTACK_MISS" : null;
                        default -> null;
                    };
                    if (type != null) events.add(new CombatEventSnapshot(tick, time, type,
                            id(simulation, agent), id(simulation, agent.target), agent.decision.name(), 0));
                }
                if (agent.hitPoints < previousHitPoints)
                    events.add(new CombatEventSnapshot(tick, time, "ATTACK_HIT", "UNKNOWN",
                            id(simulation, agent), agent.lastHitZone == null ? "UNKNOWN" :
                            agent.lastHitZone.name(), previousHitPoints - agent.hitPoints));
                if (previousAlive && !agent.alive)
                    events.add(new CombatEventSnapshot(tick, time, "DEATH", "UNKNOWN",
                            id(simulation, agent), "COMBAT_DEATH", previousHitPoints));
                if (agent.groupIntent != previousIntent &&
                        (agent.groupIntent == GroupIntent.COMMIT_REVENGE ||
                                agent.groupIntent == GroupIntent.COUNTER_COVER))
                    events.add(new CombatEventSnapshot(tick, time,
                            agent.groupIntent == GroupIntent.COMMIT_REVENGE ?
                                    "REVENGE_COMMIT" : "COUNTER_COVER_COMMIT",
                            id(simulation, agent), id(simulation, agent.target),
                            agent.groupIntent.name(), 0));
                remember(agent);
            }
        }

        private void remember(Musketeer agent) {
            states.put(agent, agent.state);
            intents.put(agent, agent.groupIntent);
            hitPoints.put(agent, agent.hitPoints);
            alive.put(agent, agent.alive);
        }
    }
}
