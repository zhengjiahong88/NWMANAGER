import java.awt.*;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.IdentityHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Collections;
import java.util.Set;

final class DuelSimulation {
    static final double ARENA_WIDTH = 8.0, ARENA_HEIGHT = 7.0;
    static final double GROUP_DESIRED_ANGULAR_VELOCITY = 0.39;
    static final double GROUP_CIRCULATION_REFERENCE_VELOCITY = 0.60;
    private static final double GROUP_ROTATION_REFERENCE_RADIUS = 1.17;
    private static final double
            DECISION_INTERVAL = 0.10,
            BLOCK_MIN_TIME = 0.18,
            BACKWARD_SPEED_FACTOR = 0.62,
            BLOCK_STUN_TIME = 0.30,
            DOUBLE_BLOCK_WINDOW = 0.20,
            DOUBLE_BLOCK_MIN_DURATION = 0.2,
            PRESSURE_NEAR_DISTANCE = 0.86,
            LOCAL_THREAT_RANGE = 3.35,
            LOCAL_OVERLOAD_THRESHOLD = 1.20,
            FRONT_LINE_ARM_DISTANCE = 2.75,
            OPENING_CONTACT_DISTANCE = 2.08,
            FIFTY_FIFTY_WINDOW = 0.12,
            REFORM_DISTANCE = 3.15,
            DEZONE_BACKWARD_LIMIT = 2.55,
            TARGET_SWITCH_MAX_DISTANCE = 3.20,
            SIMPLE_CHAMBER_WINDOW = 0.025,
            PERFECT_CHAMBER_WINDOW = 0.004,
            CHAMBER_ATTEMPT_LIMIT = 0.65,
            SIMPLE_CHAMBER_HOLD = 0.24,
            RECHAMBER_BASE_FACTOR = 0.50,
            RECHAMBER_CHAIN_PENALTY = 0.10,
            MIN_RECHAMBER_FACTOR = 0.05,
            INTERCEPTION_REEVALUATION_TIME = 0.20,
            INTERCEPTION_DESTINATION_SPEED = 1.85,
            INTERCEPTION_TIME_MARGIN = 0.18,
            THREAT_PROXIMITY_WEIGHT = 0.24,
            THREAT_CLOSING_WEIGHT = 0.14,
            THREAT_BODY_FACING_WEIGHT = 0.14,
            THREAT_WEAPON_ALIGNMENT_WEIGHT = 0.14,
            THREAT_READINESS_WEIGHT = 0.20,
            THREAT_LINE_WEIGHT = 0.14,
            THREAT_ADJUST_THRESHOLD = 0.35,
            THREAT_ATTENTION_THRESHOLD = 0.55,
            THREAT_ENGAGE_THRESHOLD = 0.65,
            THREAT_RESUME_THRESHOLD = 0.30,
            DUEL_LOCK_TIME = 0.80,
            INTERCEPTION_CONTEST_TIME = 0.18,
            REAR_DESTINATION_SPEED = 1.35,
            REAR_PREPARATION_MARGIN = 0.10,
            REAR_MIN_DISTANCE = 1.12,
            REAR_MAX_DISTANCE = 1.94,
            REAR_STABILITY_TIME = 0.20,
            REAR_NO_PROGRESS_ADJUST_TIME = 1.15,
            REAR_NO_PROGRESS_ABORT_TIME = 3.20;
    static final double
            REAR_ATTACK_ANGLE = Math.toRadians(115),
            REAR_ENTRY_ANGLE = Math.toRadians(130),
            REAR_STABLE_ANGLE = Math.toRadians(140),
            REAR_LOSS_ANGLE = Math.toRadians(122),
            BACKSTAB_COMMIT_ANGLE = Math.toRadians(132);
    private static final double
            MODERN_STRUCTURE_INTERVAL = 0.25,
            MODERN_ENGAGEMENT_MIN_TIME = 0.85,
            MODERN_DUEL_EXTREME_SEPARATION = 4.20,
            SUPPORT_PREPARATION_TIME = 0.22,
            SUPPORT_MIN_PERSISTENCE = 0.55,
            SUPPORT_MAX_DURATION = 3.20,
            SUPPORT_SAFETY_MARGIN = 0.12,
            GROUP_PARTNER_MIN_DISTANCE = 1.15,
            GROUP_PARTNER_MAX_DISTANCE = 1.78,
            GROUP_PARTNER_PREFERRED_DISTANCE = 1.38,
            GROUP_ROTATION_MIN_TIME = 2.40,
            GROUP_ROTATION_MAX_TIME = 3.80,
            GROUP_VISIBLE_ROTATION_ANGLE = Math.toRadians(20),
            GROUP_MARK_MIN_TIME = 1.15,
            GROUP_RECOVER_TIME = 0.72,
            ISOLATION_MIN_DISTANCE = 3.55,
            ISOLATION_MIN_ADVANTAGE = 0.72;
    private static final int CHAMBER_BLOCK_BONUS = 8;
    private SimulationRandom random;
    private final SimulationScenario scenario;
    private final ScenarioFamily scenarioFamily;
    private final long seed;
    private DeadAgentExperimentMode deadAgentExperimentMode =
            DeadAgentExperimentMode.CURRENT_BUGGED_BEHAVIOR;
    private Musketeer controlledDeadAgent;
    private boolean deadAgentDifferentialCaptureEnabled;
    private long differentialCaptureStartTick, differentialCaptureEndTick,
            differentialCaptureSequence;
    private DifferentialStageRingBuffer differentialStageSnapshots;
    private final SimulationFeatures features;
    private final ScenarioGenerator scenarioGenerator = new ScenarioGenerator();
    private final EngagementManager engagementManager = new EngagementManager();
    private final ArrayDeque<String> tacticalLog = new ArrayDeque<>();
    private final ArrayDeque<DecisionTrace> decisionTraces = new ArrayDeque<>();
    private final ArrayList<KillExplanation> killExplanations = new ArrayList<>();
    private final ArrayList<KillCausalChain> killCausalChains = new ArrayList<>();
    private final AgentConfiguration[] blueConfigurations, redConfigurations;
    Musketeer[] blueTeam, redTeam, agents;
    Team winningTeam;
    CombatPhase combatPhase;
    double combatPhaseTime, simulationTime;
    private ScenarioSetup scenarioSetup;
    private int controlledAliveMask;
    private boolean frontLineContact;
    private double modernStructureTime;
    private int modernAliveMask;
    private String lastModernReconstructionReason = "not_started";
    private final IdentityHashMap<Musketeer, FreedomAssessment> freedomAssessments =
            new IdentityHashMap<>();
    private final IdentityHashMap<Musketeer, Boolean> durableFreedomEpisodes =
            new IdentityHashMap<>();
    private final IdentityHashMap<Musketeer, SupportOpportunity> supportOpportunities =
            new IdentityHashMap<>();
    private final IdentityHashMap<Musketeer, String> supportOpportunityEpisodes =
            new IdentityHashMap<>();
    private ModernMatchMetrics modernMetrics = new ModernMatchMetrics();
    private SupportPlan supportPlan, lastSupportPlan;
    private Engagement modernGroupEngagement;
    private Engagement modernGroup3Engagement;
    private Group3v3Metrics group3Metrics = new Group3v3Metrics();
    private boolean experimentalRoundComplete;
    private boolean experimentalFirstEliminationObserved;
    private boolean experimentalAttacksEnabled = true;
    private boolean group3ApproachSteeringEnabled = true;
    private Team experimentalRoundLeader;
    private ExperimentalGroupPhase experimentalGroupPhase = ExperimentalGroupPhase.GROUP_3V3;
    private ActiveCombatController activeCombatController = ActiveCombatController.GROUP_3V3;
    private int experimentalAliveMask;
    private final ArrayList<TransitionTrace> transitionTraces = new ArrayList<>();
    private final HashSet<TransitionKey> appliedRosterTransitions = new HashSet<>();
    private final TransitionLogger transitionLogger = new TransitionLogger(false);
    private final ArrayDeque<RosterRequestObservation> rosterRequestObservations = new ArrayDeque<>();
    private RosterSnapshot processedRoster, pendingRosterTransition;
    private boolean transitionAppliedThisTick, transitionInProgress;
    private long experimentalTick, nextTransitionId;
    private DeadReferenceAudit lastDeadReferenceAudit = DeadReferenceAudit.cleanAudit();
    private ProgressSnapshot lastProgressSnapshot;
    private long ticksWithoutAnyPositionChange, ticksWithoutAnyStateChange,
            repeatedTransitionStateTicks;
    private double timeWithoutGlobalProgress;
    private boolean logicalFreezeActive, transitionFailsafePaused;
    private TransitionFailsafeReport transitionFailsafeReport;

    DuelSimulation() {
        this(AgentConfiguration.defaultBlue(), AgentConfiguration.defaultBlueTwo(),
                AgentConfiguration.defaultRed(), AgentConfiguration.defaultRedTwo());
    }

    DuelSimulation(AgentConfiguration blueConfiguration, AgentConfiguration redConfiguration) {
        this(blueConfiguration, AgentConfiguration.defaultBlueTwo(), redConfiguration,
                AgentConfiguration.defaultRedTwo());
    }

    DuelSimulation(AgentConfiguration blueOne, AgentConfiguration blueTwo, AgentConfiguration redOne,
                   AgentConfiguration redTwo) {
        this(blueOne, blueTwo, redOne, redTwo, SimulationScenario.MODERN_2V2,
                SimulationFeatures.modern2v2(), ScenarioFamily.BASELINE, System.nanoTime());
    }

    DuelSimulation(AgentConfiguration blueOne, AgentConfiguration blueTwo, AgentConfiguration redOne,
                   AgentConfiguration redTwo, SimulationScenario scenario, long seed) {
        this(blueOne, blueTwo, redOne, redTwo, scenario, SimulationFeatures.forScenario(scenario),
                ScenarioFamily.BASELINE, seed);
    }

    DuelSimulation(AgentConfiguration blueOne, AgentConfiguration blueTwo, AgentConfiguration redOne,
                   AgentConfiguration redTwo, SimulationScenario scenario, ScenarioFamily scenarioFamily,
                   long seed) {
        this(blueOne, blueTwo, redOne, redTwo, scenario, SimulationFeatures.forScenario(scenario),
                scenarioFamily, seed);
    }

    DuelSimulation(TeamConfiguration3 blue, TeamConfiguration3 red,
                   SimulationScenario scenario, long seed) {
        if (scenario != SimulationScenario.MODERN_3V3 && scenario != SimulationScenario.CONTROLLED_3V3)
            throw new IllegalArgumentException("TeamConfiguration3 requires a 3v3 scenario.");
        blueConfigurations = blue.players();
        redConfigurations = red.players();
        this.scenario = scenario;
        features = SimulationFeatures.forScenario(scenario);
        scenarioFamily = ScenarioFamily.BASELINE;
        this.seed = seed;
        reset();
    }

    DuelSimulation(AgentConfiguration blueOne, AgentConfiguration blueTwo, AgentConfiguration redOne,
                   AgentConfiguration redTwo, SimulationScenario scenario, SimulationFeatures features, long seed) {
        this(blueOne, blueTwo, redOne, redTwo, scenario, features, ScenarioFamily.BASELINE, seed);
    }

    private DuelSimulation(AgentConfiguration blueOne, AgentConfiguration blueTwo,
                           AgentConfiguration redOne, AgentConfiguration redTwo,
                           SimulationScenario scenario, SimulationFeatures features,
                           ScenarioFamily scenarioFamily, long seed) {
        blueConfigurations = new AgentConfiguration[]{blueOne, blueTwo};
        redConfigurations = new AgentConfiguration[]{redOne, redTwo};
        this.scenario = scenario;
        this.features = features;
        this.scenarioFamily = scenarioFamily;
        this.seed = seed;
        reset();
    }

    void reset() {
        random = new SimulationRandom(seed);
        engagementManager.reset();
        tacticalLog.clear();
        decisionTraces.clear();
        killExplanations.clear();
        killCausalChains.clear();
        simulationTime = 0;
        scenarioSetup = null;
        if (scenario == SimulationScenario.MODERN_3V3 ||
                scenario == SimulationScenario.CONTROLLED_3V3) {
            resetExperimental3v3();
            return;
        }
        if (scenario == SimulationScenario.MODERN_2V2) {
            resetModern2v2();
            return;
        }
        if (scenario == SimulationScenario.CONTROLLED_2V1) {
            resetControlled2v1();
            return;
        }
        if (scenario == SimulationScenario.CONTROLLED_2V2) {
            resetControlled2v2();
            return;
        }
        blueTeam = new Musketeer[]{
                new Musketeer(blueConfigurations[0], Team.BLUE, new Color(48, 112, 220), new Pos(1.65, 2.45), 0),
                new Musketeer(blueConfigurations[1], Team.BLUE, new Color(40, 151, 184), new Pos(1.65, 4.55), 0)
        };
        redTeam = new Musketeer[]{
                new Musketeer(redConfigurations[0], Team.RED, new Color(215, 62, 65), new Pos(ARENA_WIDTH - 1.65, 2.45), Math.PI),
                new Musketeer(redConfigurations[1], Team.RED, new Color(221, 111, 48), new Pos(ARENA_WIDTH - 1.65, 4.55), Math.PI)
        };
        agents = new Musketeer[]{blueTeam[0], blueTeam[1], redTeam[0], redTeam[1]};
        winningTeam = null;
        for (var agent : agents) initializeAwareness(agent);
        for (var agent : agents) selectTarget(agent, true);
        beginFrontLine();
        updateLocalCoordination(0);
    }

    private void resetModern2v2() {
        freedomAssessments.clear();
        durableFreedomEpisodes.clear();
        supportOpportunities.clear();
        supportOpportunityEpisodes.clear();
        modernMetrics = new ModernMatchMetrics();
        supportPlan = lastSupportPlan = null;
        modernGroupEngagement = null;
        blueTeam = new Musketeer[]{
                new Musketeer(blueConfigurations[0], Team.BLUE, new Color(48, 112, 220),
                        new Pos(1.65, 2.45), 0),
                new Musketeer(blueConfigurations[1], Team.BLUE, new Color(40, 151, 184),
                        new Pos(1.65, 4.55), 0)
        };
        redTeam = new Musketeer[]{
                new Musketeer(redConfigurations[0], Team.RED, new Color(215, 62, 65),
                        new Pos(ARENA_WIDTH - 1.65, 2.45), Math.PI),
                new Musketeer(redConfigurations[1], Team.RED, new Color(221, 111, 48),
                        new Pos(ARENA_WIDTH - 1.65, 4.55), Math.PI)
        };
        agents = new Musketeer[]{blueTeam[0], blueTeam[1], redTeam[0], redTeam[1]};
        winningTeam = null;
        combatPhase = CombatPhase.RESOLUTION;
        combatPhaseTime = 0;
        frontLineContact = false;
        modernStructureTime = MODERN_STRUCTURE_INTERVAL;
        modernAliveMask = aliveMask();
        lastModernReconstructionReason = "initial_geometric_pairing";
        for (var agent : agents) initializeAwareness(agent);
        createInitialModernGroup();
        logTactical("MODERN_RESET", "reason=" + lastModernReconstructionReason);
    }

    private void resetExperimental3v3() {
        group3Metrics = new Group3v3Metrics();
        experimentalRoundComplete = false;
        experimentalFirstEliminationObserved = false;
        experimentalRoundLeader = null;
        experimentalGroupPhase = ExperimentalGroupPhase.GROUP_3V3;
        activeCombatController = ActiveCombatController.GROUP_3V3;
        modernGroupEngagement = null;
        blueTeam = new Musketeer[]{
                new Musketeer(blueConfigurations[0], Team.BLUE, new Color(48, 112, 220),
                        new Pos(1.45, 1.80), 0),
                new Musketeer(blueConfigurations[1], Team.BLUE, new Color(40, 151, 184),
                        new Pos(1.45, 3.50), 0),
                new Musketeer(blueConfigurations[2], Team.BLUE, new Color(76, 95, 190),
                        new Pos(1.45, 5.20), 0)
        };
        redTeam = new Musketeer[]{
                new Musketeer(redConfigurations[0], Team.RED, new Color(215, 62, 65),
                        new Pos(ARENA_WIDTH - 1.45, 1.80), Math.PI),
                new Musketeer(redConfigurations[1], Team.RED, new Color(221, 111, 48),
                        new Pos(ARENA_WIDTH - 1.45, 3.50), Math.PI),
                new Musketeer(redConfigurations[2], Team.RED, new Color(184, 64, 112),
                        new Pos(ARENA_WIDTH - 1.45, 5.20), Math.PI)
        };
        agents = new Musketeer[]{blueTeam[0], blueTeam[1], blueTeam[2],
                redTeam[0], redTeam[1], redTeam[2]};
        winningTeam = null;
        combatPhase = CombatPhase.RESOLUTION;
        combatPhaseTime = 0;
        for (var agent : agents) initializeAwareness(agent);
        for (var n = 0; n < 3; ++n) {
            blueTeam[n].target = redTeam[n];
            redTeam[n].target = blueTeam[n];
            blueTeam[n].decision = redTeam[n].decision = Decision.WAIT;
        }
        modernGroup3Engagement = engagementManager.createGroup3v3(blueTeam, redTeam);
        modernGroup3Engagement.activate();
        experimentalAliveMask = aliveMask();
        processedRoster = captureRoster();
        pendingRosterTransition = null;
        transitionAppliedThisTick = transitionInProgress = false;
        experimentalTick = nextTransitionId = 0;
        transitionTraces.clear();
        appliedRosterTransitions.clear();
        transitionLogger.reset();
        rosterRequestObservations.clear();
        lastDeadReferenceAudit = DeadReferenceAudit.cleanAudit();
        lastProgressSnapshot = null;
        ticksWithoutAnyPositionChange = ticksWithoutAnyStateChange = repeatedTransitionStateTicks = 0;
        timeWithoutGlobalProgress = 0;
        logicalFreezeActive = transitionFailsafePaused = false;
        transitionFailsafeReport = null;
        for (var agent : agents) initializeGroup3Attention(agent);
        ++group3Metrics.groupEngagementsCreated;
        logTactical("GROUP_3V3_CREATED", "sixAlive=true attacksEnabled=" + experimentalAttacksEnabled);
    }

    private void createInitialModernGroup() {
        modernGroupEngagement = engagementManager.createGroup2v2(blueTeam, redTeam,
                random.nextBoolean() ? RotationDirection.CLOCKWISE :
                        RotationDirection.COUNTER_CLOCKWISE);
        modernGroupEngagement.activate();
        assignModernGroupMarks(modernGroupEngagement.group, false);
        ++modernMetrics.groupEngagementsCreated;
        logTactical("GROUP_2V2_CREATED", "rotation=" +
                modernGroupEngagement.group.rotation + " allFourAlive=true");
    }

    private void assignModernGroupMarks(GroupEngagement group, boolean switched) {
        var directCost = blueTeam[0].pos.minus(redTeam[0].pos).hypotenuse() +
                blueTeam[1].pos.minus(redTeam[1].pos).hypotenuse();
        var crossedCost = blueTeam[0].pos.minus(redTeam[1].pos).hypotenuse() +
                blueTeam[1].pos.minus(redTeam[0].pos).hypotenuse();
        if (directCost <= crossedCost + 0.000001) {
            assignGroupPair(group, blueTeam[0], redTeam[0], blueTeam[1], redTeam[1], -1);
            assignGroupPair(group, blueTeam[1], redTeam[1], blueTeam[0], redTeam[0], 1);
        } else {
            assignGroupPair(group, blueTeam[0], redTeam[1], blueTeam[1], redTeam[0], -1);
            assignGroupPair(group, blueTeam[1], redTeam[0], blueTeam[0], redTeam[1], 1);
        }
        group.markChangedAt = simulationTime;
        if (switched) {
            ++modernMetrics.primaryMarkSwitches;
            logTactical("PRIMARY_MARK_SWITCH", "reason=collective_geometry");
        }
    }

    private void assignGroupPair(GroupEngagement group, Musketeer blue, Musketeer red,
                                 Musketeer blueAlly, Musketeer redAlly, int side) {
        assignGroupAgent(group, blue, red, redAlly, blueAlly, side);
        assignGroupAgent(group, red, blue, blueAlly, redAlly, -side);
    }

    private void assignGroupAgent(GroupEngagement group, Musketeer agent, Musketeer mark,
                                  Musketeer secondary, Musketeer ally, int side) {
        group.assignments.put(agent, new GroupAssignment(agent, mark, secondary, ally));
        agent.primaryMark = mark;
        agent.secondaryThreat = secondary;
        agent.engagementSide = side;
        agent.groupIntent = GroupIntent.MARK;
        agent.assignTacticalIntent(modernGroupEngagement, TacticalIntent.GROUP, null, mark,
                "group_primary_mark");
        changeTarget(agent, mark, DUEL_LOCK_TIME);
    }

    private Engagement createModernDuel(Musketeer first, Musketeer second, int side) {
        var duel = engagementManager.createDuel(first, second);
        duel.activate();
        first.engagementSide = side;
        second.engagementSide = -side;
        first.localRole = second.localRole = LocalRole.FREE;
        changeTarget(first, second, DUEL_LOCK_TIME);
        changeTarget(second, first, DUEL_LOCK_TIME);
        logTactical("DUEL_CREATED", "agents=" + first.name(UiLanguage.FRENCH) +
                "," + second.name(UiLanguage.FRENCH) + " reason=modern_pairing");
        return duel;
    }

    private void resetControlled2v1() {
        var fixer = new Musketeer(blueConfigurations[0], Team.BLUE, new Color(48, 112, 220),
                new Pos(2.55, ARENA_HEIGHT / 2), 0);
        var flanker = new Musketeer(blueConfigurations[1], Team.BLUE, new Color(40, 151, 184),
                new Pos(3.15, ARENA_HEIGHT / 2 - 1.55), 0);
        var defender = new Musketeer(redConfigurations[0], Team.RED, new Color(215, 62, 65),
                new Pos(5.45, ARENA_HEIGHT / 2), Math.PI);
        var inactive = new Musketeer(redConfigurations[1], Team.RED, new Color(90, 90, 90),
                new Pos(ARENA_WIDTH + 2, ARENA_HEIGHT + 2), Math.PI);
        blueTeam = new Musketeer[]{fixer, flanker};
        redTeam = new Musketeer[]{defender, inactive};
        agents = new Musketeer[]{fixer, flanker, defender};
        eliminate(inactive);
        winningTeam = null;
        combatPhase = CombatPhase.RESOLUTION;
        combatPhaseTime = 0;
        defender.tactic = Tactic.DEFENSIVE;
        for (var agent : agents) initializeAwareness(agent);
        changeTarget(fixer, defender, Double.POSITIVE_INFINITY);
        changeTarget(flanker, defender, Double.POSITIVE_INFINITY);
        changeTarget(defender, fixer, 0.8);
        setLocalRole(fixer, LocalRole.FIXER, defender, Double.POSITIVE_INFINITY);
        setLocalRole(flanker, LocalRole.FLANKER, defender, Double.POSITIVE_INFINITY);
        fixer.localSide = -1;
        flanker.localSide = 1;
        engagementManager.createTwoVsOne(fixer, flanker, defender);
        initializeFlank(flanker);
        updateTacticalDestinations(0);
    }

    private void resetControlled2v2() {
        scenarioSetup = scenarioGenerator.create(scenarioFamily, seed);
        createControlledAgents(scenarioSetup);
        createControlledEngagements();
        controlledAliveMask = aliveMask();
        if (scenarioSetup.baselineFallback())
            logTactical("PLACEMENT_FALLBACK", "family=" + scenarioFamily + " seed=" + seed +
                    " rejected=" + scenarioSetup.rejectedPlacements());
    }

    private void createControlledAgents(ScenarioSetup setup) {
        var fixer = createControlledAgent(blueConfigurations[0], Team.BLUE, new Color(48, 112, 220),
                setup.blueOne());
        var flanker = createControlledAgent(blueConfigurations[1], Team.BLUE, new Color(40, 151, 184),
                setup.blueTwo());
        var defender = createControlledAgent(redConfigurations[0], Team.RED, new Color(215, 62, 65),
                setup.redOne());
        var interceptor = createControlledAgent(redConfigurations[1], Team.RED, new Color(221, 111, 48),
                setup.redTwo());
        blueTeam = new Musketeer[]{fixer, flanker};
        redTeam = new Musketeer[]{defender, interceptor};
        agents = new Musketeer[]{fixer, flanker, defender, interceptor};
        winningTeam = null;
        combatPhase = CombatPhase.RESOLUTION;
        combatPhaseTime = 0;
        defender.tactic = Tactic.DEFENSIVE;
        interceptor.tactic = Tactic.NEUTRAL;
        for (var agent : agents) initializeAwareness(agent);
    }

    private Musketeer createControlledAgent(AgentConfiguration configuration, Team team, Color color,
                                             AgentSpawn spawn) {
        var agent = new Musketeer(configuration, team, color, spawn.position(), spawn.bodyAngle());
        agent.weaponYaw = spawn.weaponYaw();
        agent.desiredWeaponYaw = spawn.weaponYaw();
        return agent;
    }

    private void createControlledEngagements() {
        var fixer = blueTeam[0];
        var flanker = blueTeam[1];
        var defender = redTeam[0];
        var interceptor = redTeam[1];
        changeTarget(fixer, defender, Double.POSITIVE_INFINITY);
        changeTarget(flanker, defender, Double.POSITIVE_INFINITY);
        changeTarget(defender, fixer, 0.8);
        changeTarget(interceptor, flanker, Double.POSITIVE_INFINITY);
        setLocalRole(fixer, LocalRole.FIXER, defender, Double.POSITIVE_INFINITY);
        setLocalRole(flanker, LocalRole.FLANKER, defender, Double.POSITIVE_INFINITY);
        fixer.localSide = -1;
        flanker.localSide = 1;
        var engagement = engagementManager.createTwoVsOne(fixer, flanker, defender);
        initializeFlank(flanker);
        engagement.assignInterceptor(interceptor);
        updateTacticalDestinations(0);
    }

    ScenarioFamily scenarioFamily() {
        return scenarioFamily;
    }

    SimulationScenario scenario() {
        return scenario;
    }

    SimulationFeatures features() {
        return features;
    }

    long seed() {
        return seed;
    }

    String lastModernReconstructionReason() {
        return lastModernReconstructionReason;
    }

    ModernMatchMetrics modernMetrics() {
        return modernMetrics.copy();
    }

    SupportPlan supportPlan() {
        return supportPlan;
    }

    SupportPlan lastSupportPlan() {
        return lastSupportPlan;
    }

    SupportOpportunity supportOpportunity(Musketeer agent) {
        return supportOpportunities.get(agent);
    }

    Engagement modernGroupEngagement() {
        return modernGroupEngagement;
    }

    Group3v3Engagement modernGroup3Engagement() {
        return activeGroup3v3();
    }

    Group3v3Metrics group3Metrics() {
        return group3Metrics;
    }

    boolean experimentalRoundComplete() {
        return experimentalRoundComplete;
    }

    Team experimentalRoundLeader() {
        return experimentalRoundLeader;
    }

    boolean experimentalFirstEliminationObserved() {
        return experimentalFirstEliminationObserved;
    }

    ExperimentalGroupPhase experimentalGroupPhase() {
        return experimentalGroupPhase;
    }

    ActiveCombatController activeCombatController() {
        return activeCombatController;
    }

    RosterSnapshot processedRoster() {
        return processedRoster;
    }

    List<TransitionTrace> transitionTraces() {
        return List.copyOf(transitionTraces);
    }

    DeadReferenceAudit lastDeadReferenceAudit() {
        return lastDeadReferenceAudit;
    }

    List<String> transitionLog() {
        return transitionLogger.lines();
    }

    long consoleLinesEmitted() {
        return transitionLogger.consoleLinesEmitted();
    }

    long duplicateConsoleLinesSuppressed() {
        return transitionLogger.duplicateConsoleLinesSuppressed();
    }

    ProgressWatchdogMetrics progressWatchdogMetrics() {
        return new ProgressWatchdogMetrics(ticksWithoutAnyPositionChange,
                ticksWithoutAnyStateChange, repeatedTransitionStateTicks,
                group3Metrics.logicalFreezeReports);
    }

    TransitionFailsafeReport transitionFailsafeReport() {
        return transitionFailsafeReport;
    }

    void requestRosterTransitionForTest(RosterSnapshot snapshot) {
        requestRosterTransition(snapshot);
    }

    void disableGroup3AttacksForTest() {
        experimentalAttacksEnabled = false;
    }

    void disableGroup3ApproachSteeringForVisualBaseline() {
        group3ApproachSteeringEnabled = false;
    }

    void eliminateForDeadAgentTest(Musketeer agent) {
        if (!agent.alive) throw new IllegalStateException("Agent is already dead.");
        eliminate(agent);
        captureDifferentialStage(DifferentialTickStage.AFTER_DEATH_PROCESSING);
    }

    void configureDeadAgentExperiment(DeadAgentExperimentMode mode, Musketeer controlledDeadAgent) {
        if (mode == DeadAgentExperimentMode.COLLISION_REINTRODUCED)
            throw new UnsupportedOperationException("D2 belongs to jalon 6.2c.");
        deadAgentExperimentMode = mode;
        this.controlledDeadAgent = controlledDeadAgent;
    }

    void configureDeadAgentDifferentialCapture(long startTick, long endTick) {
        if (startTick > endTick) throw new IllegalArgumentException("Invalid differential capture window.");
        deadAgentDifferentialCaptureEnabled = true;
        differentialCaptureStartTick = startTick;
        differentialCaptureEndTick = endTick;
        differentialCaptureSequence = 0;
        differentialStageSnapshots = new DifferentialStageRingBuffer(16);
    }

    List<DifferentialStageSnapshot> differentialStageSnapshots() {
        return differentialStageSnapshots == null ? List.of() : differentialStageSnapshots.flattened();
    }

    long differentialRandomDrawCount() {
        return random.drawCount();
    }

    long differentialRandomStateHash() {
        return random.stateHash();
    }

    private boolean isActiveForDeadAgentExperiment(Musketeer agent,
                                                    DeadAgentParticipationDomain domain) {
        if (!participates(agent, domain)) return false;
        if (deadAgentExperimentMode != DeadAgentExperimentMode.LOGICALLY_NEUTRALIZED) return true;
        return agent != controlledDeadAgent || agent.alive;
    }

    private boolean participates(Musketeer agent, DeadAgentParticipationDomain domain) {
        return agent.alive || domain.passiveAfterDeath();
    }

    private void eliminate(Musketeer agent) {
        if (!agent.alive) return;
        agent.captureDeathPose();
        agent.alive = false;
        setState(agent, CombatState.DEAD);
        agent.clearActiveIntentOnDeath();
        agent.freezeAtDeathPose();
        agent.deadAgentAudit.begin(agent.deathPose().position());
        purgeDeadReferences(aliveAgents());
    }

    private void freezeDeadAgents() {
        for (var agent : agents)
            if (!agent.alive) agent.freezeAtDeathPose();
    }

    private void captureDifferentialStage(DifferentialTickStage stage) {
        captureDifferentialStage(stage, null);
    }

    private void captureDifferentialStage(DifferentialTickStage stage, Musketeer checkpointAgent) {
        if (!deadAgentDifferentialCaptureEnabled || experimentalTick < differentialCaptureStartTick ||
                experimentalTick > differentialCaptureEndTick) return;
        if (checkpointAgent == controlledDeadAgent) return;
        var group = modernGroup3Engagement == null ? null : modernGroup3Engagement.group3;
        var survivors = new ArrayList<DifferentialAgentSnapshot>(5);
        for (var agent : agents)
            if (agent != controlledDeadAgent && agent.alive)
                survivors.add(buildDifferentialAgentSnapshot(agent, group));
        Collections.sort(survivors);
        differentialStageSnapshots.add(new DifferentialStageSnapshot(experimentalTick, simulationTime,
                ++differentialCaptureSequence, stage, stableAgentId(checkpointAgent), random.drawCount(),
                random.stateHash(), buildGroup3CollectiveSnapshot(group), survivors));
    }

    private Group3CollectiveSnapshot buildGroup3CollectiveSnapshot(Group3v3Engagement group) {
        if (group == null) return null;
        return new Group3CollectiveSnapshot(buildFormationSnapshot(group.blueFormation),
                buildFormationSnapshot(group.redFormation), maneuverName(group.blueManeuver),
                maneuverName(group.redManeuver), approachName(group.blueApproachPlan),
                approachName(group.redApproachPlan), buildCoverNetworkSnapshot(group.blueCoverNetwork),
                buildCoverNetworkSnapshot(group.redCoverNetwork));
    }

    private FormationSnapshot buildFormationSnapshot(TeamFormation3 formation) {
        var historical = new ArrayList<String>();
        var active = new ArrayList<String>();
        var lateral = new java.util.TreeMap<String, Double>();
        var forward = new java.util.TreeMap<String, Double>();
        for (var member : formation.members) {
            var id = stableAgentId(member);
            historical.add(id);
            if (!member.alive) continue;
            active.add(id);
            var offset = member.pos.minus(formation.center);
            lateral.put(id, offset.dot(formation.lateral));
            forward.put(id, offset.dot(formation.forward));
        }
        Collections.sort(historical);
        Collections.sort(active);
        return new FormationSnapshot(formation.center, formation.forward, formation.lateral,
                formation.width, formation.depth, formation.cohesion,
                stableAgentId(formation.leftWing), stableAgentId(formation.centerPlayer),
                stableAgentId(formation.rightWing), historical, active, lateral, forward);
    }

    private CoverNetworkSnapshot buildCoverNetworkSnapshot(CoverNetwork network) {
        var viable = new ArrayList<CanonicalCoverLinkSnapshot>();
        var rejected = new ArrayList<CanonicalCoverLinkSnapshot>();
        for (var link : network.links) {
            var snapshot = new CanonicalCoverLinkSnapshot(stableAgentId(link.protector()),
                    stableAgentId(link.protectedAlly()), stableAgentId(link.threateningEnemy()),
                    link.viable(), link.alignEta(), link.prepareEta(), link.contactEta(),
                    link.punishEta(), link.lineSafety(), link.perceptionConfidence(), link.quality(),
                    link.rejectionReason());
            (link.viable() ? viable : rejected).add(snapshot);
        }
        Collections.sort(viable);
        Collections.sort(rejected);
        var incoming = new java.util.TreeMap<String, Integer>();
        var outgoing = new java.util.TreeMap<String, Integer>();
        var active = new ArrayList<String>();
        for (var member : network.members) {
            if (!isActiveForDeadAgentExperiment(member, DeadAgentParticipationDomain.COVER)) continue;
            var id = stableAgentId(member);
            active.add(id);
            incoming.put(id, viableCoverLinkCount(network.incomingLinks.get(member)));
            outgoing.put(id, viableCoverLinkCount(network.outgoingLinks.get(member)));
        }
        Collections.sort(active);
        return new CoverNetworkSnapshot(network.connectivity, network.weakestMemberCoverage,
                viable, rejected, incoming, outgoing, active);
    }

    private int viableCoverLinkCount(List<CoverLink> links) {
        var count = 0;
        if (links != null)
            for (var link : links)
                if (link.viable()) ++count;
        return count;
    }

    private DifferentialAgentSnapshot buildDifferentialAgentSnapshot(Musketeer agent,
                                                                      Group3v3Engagement group) {
        var attention = agent.attentionAllocation;
        var awarenessEntries = new ArrayList<AwarenessEntrySnapshot>();
        for (var entry : agent.opponentAwareness.entrySet()) {
            var awareness = entry.getValue();
            awarenessEntries.add(new AwarenessEntrySnapshot(stableAgentId(entry.getKey()),
                    awareness.attentionLevel.name(), awareness.lastKnown, awareness.perceivedVelocity,
                    awareness.lastObservationTime, awareness.observations, awareness.detailConfidence,
                    awareness.stateDetailFresh));
        }
        Collections.sort(awarenessEntries);
        var formationRole = "NONE";
        var approachRole = "NONE";
        var maneuver = "NONE";
        var incoming = 0;
        var outgoing = 0;
        if (group != null) {
            var formation = group.formation(agent.team);
            var role = formation.roleOf(agent);
            formationRole = role.name();
            var plan = group.approachPlan(agent.team);
            if (plan != null) approachRole = role == plan.leadRole ? "LEAD" :
                    role == plan.supportRole ? "SUPPORT" : role == plan.trailingRole ? "TRAILING" : "NONE";
            maneuver = maneuverName(group.maneuver(agent.team));
            var network = group.coverNetwork(agent.team);
            incoming = viableCoverLinkCount(network.incomingLinks.get(agent));
            outgoing = viableCoverLinkCount(network.outgoingLinks.get(agent));
        }
        var opportunity = agent.attackOpportunity;
        var steering = agent.lastGroup3Steering == null ? null :
                new DifferentialSteeringSnapshot(agent.lastGroup3Steering.approachContribution(),
                        agent.lastGroup3Steering.teamManeuverContribution(),
                        agent.lastGroup3Steering.localManeuverContribution(),
                        agent.lastGroup3Steering.formationContribution(),
                        agent.lastGroup3Steering.coverContribution(),
                        agent.lastGroup3Steering.avoidanceContribution(),
                        agent.lastGroup3Steering.boundaryContribution(), null, null, null,
                        agent.desiredVelocity);
        return new DifferentialAgentSnapshot(stableAgentId(agent), agent.pos, agent.velocity,
                agent.desiredVelocity, agent.angle, agent.weaponWorldAngle(), agent.state.name(),
                agent.decision.name() + "/" + agent.groupIntent, formationRole, approachRole,
                maneuver, agent.localManeuver == null ? "NONE" : agent.localManeuver.type.name(),
                buildDifferentialAttentionSnapshot(agent), awarenessEntries,
                stableAgentId(agent.target), stableAgentId(agent.plannedTarget),
                stableAgentId(agent.movementIntent.target),
                agent.revengeOpportunity == null ? "NONE" :
                        stableAgentId(agent.revengeOpportunity.vulnerableAttacker()),
                agent.counterCoverAssessment == null ? "NONE" :
                        stableAgentId(agent.counterCoverAssessment.enemyRevenger()),
                opportunity == null ? "NONE" : stableAgentId(opportunity.target()), incoming, outgoing,
                opportunity == null || opportunity.primaryReason() == null ? "NONE" :
                        opportunity.primaryReason().name(), opportunity != null && opportunity.viable(), steering);
    }

    private DifferentialAttentionSnapshot buildDifferentialAttentionSnapshot(Musketeer agent) {
        var attention = agent.attentionAllocation;
        var primary = awareness(agent, attention.primaryThreat);
        var secondary = awareness(agent, attention.secondaryThreat);
        var peripheral = awareness(agent, attention.peripheralThreat);
        return new DifferentialAttentionSnapshot(stableAgentId(attention.primaryThreat),
                stableAgentId(attention.secondaryThreat), stableAgentId(attention.peripheralThreat),
                stableAgentId(attention.pendingPrimary), attention.primaryWeight,
                attention.secondaryWeight, attention.peripheralWeight,
                confidence(primary), confidence(secondary), confidence(peripheral), fresh(primary),
                fresh(secondary), fresh(peripheral), observations(primary), observations(secondary),
                observations(peripheral));
    }

    private OpponentAwareness awareness(Musketeer agent, Musketeer opponent) {
        return opponent == null ? null : agent.opponentAwareness.get(opponent);
    }

    private double confidence(OpponentAwareness awareness) {
        return awareness == null ? 0 : awareness.detailConfidence;
    }

    private boolean fresh(OpponentAwareness awareness) {
        return awareness != null && awareness.stateDetailFresh;
    }

    private long observations(OpponentAwareness awareness) {
        return awareness == null ? 0 : awareness.observations;
    }

    private String maneuverName(TeamManeuver maneuver) {
        return maneuver == null ? "NONE" : maneuver.type.name();
    }

    private String approachName(Group3ApproachPlan plan) {
        return plan == null ? "NONE" : plan.pattern.name();
    }

    String stableAgentId(Musketeer agent) {
        if (agent == null) return "NONE";
        for (var n = 0; n < blueTeam.length; ++n)
            if (blueTeam[n] == agent) return "B" + (n + 1);
        for (var n = 0; n < redTeam.length; ++n)
            if (redTeam[n] == agent) return "R" + (n + 1);
        return "UNKNOWN";
    }

    FreedomAssessment freedomAssessment(Musketeer agent) {
        return freedomAssessments.get(agent);
    }

    CommitAssessment evaluateCommit(Musketeer attacker, Musketeer target) {
        return assessCommit(attacker, target, attacker.pos.minus(target.pos).hypotenuse());
    }

    CoverAssessment evaluateCover(Musketeer protectedAlly, Musketeer coveringAgent,
                                  Musketeer punishableEnemy) {
        return assessCover(protectedAlly, coveringAgent, punishableEnemy);
    }

    CoverPositionAssessment evaluateCoverPosition(Musketeer protectedAlly,
                                                    Musketeer coveringAgent,
                                                    Musketeer punishableEnemy) {
        var group = activeModernGroup();
        var preferred = group == null ? coveringAgent.pos :
                preferredGroupCoverPosition(coveringAgent, group);
        return assessCoverPosition(protectedAlly, coveringAgent, punishableEnemy, preferred);
    }

    SecondaryReleaseAssessment evaluateSecondaryRelease(Musketeer primary,
                                                          Musketeer secondary,
                                                          Musketeer defender) {
        return assessSecondaryRelease(primary, secondary, defender);
    }

    AttackOpportunity evaluateAttackOpportunity(Musketeer attacker, Musketeer target) {
        return assessAttackOpportunity(attacker, target);
    }

    TeamAttackArbitration evaluateTeamAttackArbitration(Musketeer requestedAttacker) {
        var group = activeModernGroup();
        return group == null ? null : arbitrateTeamAttack(group, requestedAttacker,
                assessAttackOpportunity(requestedAttacker, requestedAttacker.primaryMark));
    }

    void evaluateTwoVsOneCoordination(Engagement engagement) {
        updateTwoVsOneCoordination(engagement);
    }

    WeaponThreatLane evaluateWeaponThreatLane(Musketeer observer, Musketeer attacker) {
        return weaponThreatLane(observer, attacker);
    }

    LocalManeuverAssessment evaluateLocalManeuver(Musketeer agent, LocalManeuverType type) {
        var group = activeModernGroup();
        var mark = agent.primaryMark;
        if (group == null || mark == null) return null;
        agent.primaryWeaponLane = weaponThreatLane(agent, mark);
        agent.secondaryWeaponLane = agent.secondaryThreat == null ? null :
                weaponThreatLane(agent, agent.secondaryThreat);
        return assessLocalManeuver(agent, group, mark, type);
    }

    void refreshLocalManeuver(Musketeer agent) {
        var group = activeModernGroup();
        var mark = agent.primaryMark;
        if (group == null || mark == null) return;
        agent.localManeuver = null;
        updateLocalManeuver(agent, group, mark);
    }

    SupportOpportunity evaluateSupportOpportunity(Musketeer supporter) {
        var group = activeModernGroup();
        if (group != null) return assessIsolationSupport(supporter, group);
        var duel = activeDuelOf(supporter);
        var teammate = teammateOf(supporter);
        var teammateDuel = teammate == null ? null : activeDuelOf(teammate);
        if (duel == null || teammateDuel == null || duel == teammateDuel)
            return SupportOpportunity.unavailable("invalid_duel");
        return assessSupportOpportunity(supporter, duel, teammate, teammateDuel);
    }

    ScenarioSetup scenarioSetup() {
        return scenarioSetup;
    }

    void update(double dt) {
        freezeDeadAgents();
        if (winningTeam != null) return;
        if (scenario == SimulationScenario.MODERN_3V3 ||
                scenario == SimulationScenario.CONTROLLED_3V3) {
            updateExperimental3v3(dt);
            return;
        }
        simulationTime += dt;
        for (var agent : agents) snapshotMotion(agent);
        engagementManager.update(dt);
        for (var agent : agents) agent.tacticalIntentTime += dt;
        updateTacticalDestinations(dt);
        if (scenario == SimulationScenario.MODERN_2V2) updateModernGroup(dt);
        if (features.combatPhases()) updateCombatPhase(dt);
        if (features.pressure()) {
            updatePressureRoles();
            updatePressure(dt);
        }
        if (features.dynamicLocalCoordination()) updateLocalCoordination(dt);
        for (var agent : agents) advanceAgent(agent, dt);
        updateTwoVsOneAttentionAndMetrics(dt);
        for (var agent : agents) prepareDecision(agent);
        if (scenario == SimulationScenario.MODERN_2V2 ||
                scenario == SimulationScenario.CONTROLLED_2V1 ||
                scenario == SimulationScenario.CONTROLLED_2V2) updateFlanks(dt);
        if (scenario == SimulationScenario.MODERN_2V2 ||
                scenario == SimulationScenario.CONTROLLED_2V2) updateEngagementInterceptions(dt);
        for (var agent : agents) decideAgent(agent);
        for (var agent : agents) moveAgent(agent, dt);
        separateAgents(dt);
        for (var attacker : agents) resolveAttack(attacker);
        freezeDeadAgents();
        updateWinner();
        if (winningTeam != null && scenario == SimulationScenario.MODERN_2V2) {
            if (supportPlan != null) finishSupportPlan(
                    !supportPlan.primaryDefender.alive &&
                            supportPlan.supporter.alive && supportPlan.fixer.alive ?
                            SupportPlanState.COMPLETED : SupportPlanState.ABORTED,
                    !supportPlan.primaryDefender.alive ?
                            "primary_defender_eliminated" : "elimination", false);
            engagementManager.update(0);
        }
        if (features.pressure()) updatePressureRoles();
        if (winningTeam == null) {
            if (features.dynamicLocalCoordination()) updateLocalCoordination(0);
            if (scenario == SimulationScenario.MODERN_2V2) {
                rebuildModernEngagementsAfterElimination();
                updateSupportPlan(dt);
                updateModernStructure(dt);
            } else if (scenario == SimulationScenario.CONTROLLED_2V2)
                rebuildControlledEngagementsAfterElimination();
            retargetAfterElimination();
            if (features.combatPhases()) updateCombatPhase(0);
        }
    }

    private void updateExperimental3v3(double dt) {
        freezeDeadAgents();
        if (dt <= 0 || experimentalRoundComplete || transitionFailsafePaused) return;
        transitionAppliedThisTick = false;
        ++experimentalTick;
        switch (activeCombatController) {
            case GROUP_3V3 -> ++group3Metrics.group3v3ControllerTicks;
            case GROUP_3V2 -> ++group3Metrics.group3v2ControllerTicks;
            case GROUP_3V1 -> ++group3Metrics.group3v1ControllerTicks;
            case GROUP_2V2 -> {
                ++group3Metrics.group2v2ControllerTicks;
                updateExperimentalHandoff(dt);
                updateProgressWatchdog(dt);
                return;
            }
            case TWO_VS_ONE -> {
                ++group3Metrics.twoVsOneControllerTicks;
                updateExperimentalHandoff(dt);
                updateProgressWatchdog(dt);
                return;
            }
            case DUEL -> {
                ++group3Metrics.duelControllerTicks;
                updateExperimentalHandoff(dt);
                updateProgressWatchdog(dt);
                return;
            }
            case COMPLETE -> {
                return;
            }
        }
        simulationTime += dt;
        for (var agent : agents) snapshotMotion(agent);
        engagementManager.update(dt);
        var group = activeGroup3v3();
        if (group == null) return;
        for (var agent : agents)
            if (agent.alive) {
                agent.stateTime += dt;
                updateState(agent);
            }
        for (var agent : agents) {
            if (isActiveForDeadAgentExperiment(agent, DeadAgentParticipationDomain.ATTENTION))
                updateGroup3Perception(agent, dt);
            if (isActiveForDeadAgentExperiment(agent, DeadAgentParticipationDomain.TARGET_SELECTION))
                updateGroup3Attention(agent);
        }
        captureDifferentialStage(DifferentialTickStage.AFTER_ATTENTION_UPDATE);
        captureDifferentialStage(DifferentialTickStage.AFTER_TARGET_SELECTION);
        var blueRoleChanges = group.blueFormation.roleChanges;
        var redRoleChanges = group.redFormation.roleChanges;
        group.updateFormations(dt);
        captureDifferentialStage(DifferentialTickStage.AFTER_FORMATION_UPDATE);
        group3Metrics.formationRoleChanges += group.blueFormation.roleChanges - blueRoleChanges +
                group.redFormation.roleChanges - redRoleChanges;
        group3Metrics.roleThrashing = group.blueFormation.roleThrashing + group.redFormation.roleThrashing;
        updateGroup3ContactBand(group);
        updateGroup3ApproachPlans(group);
        captureDifferentialStage(DifferentialTickStage.AFTER_APPROACH_PLAN_UPDATE);
        measureGroup3PreContactGeometry(group);
        updateGroup3CoverNetworks(group);
        captureDifferentialStage(DifferentialTickStage.AFTER_COVER_NETWORK_UPDATE);
        updateGroup3Clusters(group);
        updateGroup3Maneuver(group, Team.BLUE);
        updateGroup3Maneuver(group, Team.RED);
        captureDifferentialStage(DifferentialTickStage.AFTER_TEAM_MANEUVER_UPDATE);
        if (experimentalAttacksEnabled) {
            updateGroup3Exchange(group);
            updateGroup3HeldAttacks(group);
            if (!group.exchangeActive()) {
                updateGroup3AttackPlan(group, Team.BLUE);
                updateGroup3AttackPlan(group, Team.RED);
            }
            for (var agent : agents) updateGroup3Defense(agent);
        }
        captureDifferentialStage(DifferentialTickStage.AFTER_COMBAT_DECISIONS);
        observeGroup3FirstAttack();
        for (var agent : agents)
            if (isActiveForDeadAgentExperiment(agent, DeadAgentParticipationDomain.MOVEMENT))
                moveExperimental3v3Agent(agent, group, dt);
            else {
                agent.velocity = agent.desiredVelocity = new Pos();
                agent.angularVelocity = 0;
            }
        separateAgents(dt);
        for (var agent : agents)
            if (isActiveForDeadAgentExperiment(agent, DeadAgentParticipationDomain.BOUNDARY))
                keepInArena(agent);
        for (var agent : agents)
            if (agent.deadAgentAudit.active()) agent.deadAgentAudit.observe(agent.pos);
        measureGroup3FirstContacts(group);
        finishGroup3FlatFrontAuditIfReady(group, false);
        if (experimentalAttacksEnabled)
            for (var attacker : agents) resolveAttack(attacker);
        freezeDeadAgents();
        captureDifferentialStage(DifferentialTickStage.AFTER_PHYSICAL_RESOLUTION);
        var elimination = false;
        for (var agent : agents)
            if (!agent.alive) {
                elimination = true;
                break;
            }
        finishGroup3FlatFrontAuditIfReady(group, elimination);
        requestRosterTransition(captureRoster());
        applyPendingRosterTransition();
        measureGroup3(group, dt);
        updateProgressWatchdog(dt);
        captureDifferentialStage(DifferentialTickStage.END_OF_TICK);
    }

    private void rebuildExperimentalGroupAfterElimination(Group3v3Engagement group) {
        var blueAlive = livingAgents(blueTeam);
        var redAlive = livingAgents(redTeam);
        if (!experimentalFirstEliminationObserved) {
            experimentalFirstEliminationObserved = true;
            logTactical("GROUP3_FIRST_ELIMINATION", "blue=" + blueAlive.size() +
                    " red=" + redAlive.size() + " transition=pending_rebuild");
        }
        if (blueAlive.isEmpty() || redAlive.isEmpty()) {
            winningTeam = blueAlive.isEmpty() ? Team.RED : Team.BLUE;
            completeExperimentalMatch();
            return;
        }
        if (blueAlive.size() >= 3 || redAlive.size() >= 3) {
            var next = Math.min(blueAlive.size(), redAlive.size()) == 2 ?
                    ExperimentalGroupPhase.GROUP_3V2 : ExperimentalGroupPhase.GROUP_3V1;
            if (next != experimentalGroupPhase) {
                experimentalGroupPhase = next;
                activeCombatController = controllerForPhase(next);
                if (next == ExperimentalGroupPhase.GROUP_3V2) ++group3Metrics.group3v2Created;
                else ++group3Metrics.group3v1Created;
                logTactical("GROUP3_COLLECTIVE_TRANSITION", "phase=" + next + " blue=" +
                        blueAlive.size() + " red=" + redAlive.size() +
                        " splitIntoDuels=false");
            }
            refreshGroup3AttentionAfterTransition();
            group.blueFormation.update(group.redFormation.center, 0, true);
            group.redFormation.update(group.blueFormation.center, 0, true);
            rebindGroup3Maneuver(group, Team.BLUE);
            rebindGroup3Maneuver(group, Team.RED);
            updateGroup3CoverNetworks(group);
            updateGroup3Clusters(group);
            return;
        }
        if (blueAlive.size() == 2 && redAlive.size() == 2)
            beginExperimentalHandoff(ExperimentalGroupPhase.HANDOFF_2V2, blueAlive, redAlive);
        else if (blueAlive.size() == 2 && redAlive.size() == 1 ||
                redAlive.size() == 2 && blueAlive.size() == 1)
            beginExperimentalHandoff(ExperimentalGroupPhase.HANDOFF_2V1, blueAlive, redAlive);
        else beginExperimentalHandoff(ExperimentalGroupPhase.HANDOFF_1V1, blueAlive, redAlive);
    }

    private void refreshGroup3AttentionAfterTransition() {
        for (var agent : agents) {
            if (!agent.alive) continue;
            initializeGroup3Attention(agent);
        }
    }

    private void rebindGroup3Maneuver(Group3v3Engagement group, Team team) {
        var maneuver = group.maneuver(team);
        if (maneuver == null) return;
        var formation = group.formation(team);
        maneuver.outerPlayer = maneuver.directionSign > 0 ? formation.rightWing : formation.leftWing;
        maneuver.innerPlayer = maneuver.outerPlayer == formation.rightWing ?
                formation.leftWing : formation.rightWing;
    }

    private void beginExperimentalHandoff(ExperimentalGroupPhase phase, List<Musketeer> blueAlive,
                                          List<Musketeer> redAlive) {
        engagementManager.breakActive();
        clearModernTacticalState();
        blueTeam = blueAlive.toArray(Musketeer[]::new);
        redTeam = redAlive.toArray(Musketeer[]::new);
        experimentalGroupPhase = phase;
        activeCombatController = controllerForPhase(phase);
        modernGroup3Engagement = null;
        if (phase == ExperimentalGroupPhase.HANDOFF_2V2) {
            modernGroupEngagement = engagementManager.createGroup2v2(blueTeam, redTeam,
                    random.nextBoolean() ? RotationDirection.CLOCKWISE :
                            RotationDirection.COUNTER_CLOCKWISE);
            modernGroupEngagement.activate();
            assignModernGroupMarks(modernGroupEngagement.group, false);
            ++group3Metrics.handoffsTo2v2;
        } else if (phase == ExperimentalGroupPhase.HANDOFF_2V1) {
            modernGroupEngagement = null;
            if (blueAlive.size() == 2)
                createModernTwoVsOneAfterElimination(blueAlive, redAlive.getFirst(),
                        "group3_handoff_blue_superiority");
            else createModernTwoVsOneAfterElimination(redAlive, blueAlive.getFirst(),
                    "group3_handoff_red_superiority");
            ++group3Metrics.handoffsTo2v1;
        } else {
            modernGroupEngagement = null;
            createModernDuel(blueAlive.getFirst(), redAlive.getFirst(), 1);
            ++group3Metrics.handoffsTo1v1;
        }
        retargetAfterElimination();
        logTactical("GROUP3_ENGINE_HANDOFF", "phase=" + phase + " blue=" +
                blueAlive.size() + " red=" + redAlive.size());
    }

    private void updateExperimentalHandoff(double dt) {
        freezeDeadAgents();
        simulationTime += dt;
        for (var agent : agents) snapshotMotion(agent);
        engagementManager.update(dt);
        for (var agent : agents) agent.tacticalIntentTime += dt;
        updateTacticalDestinations(dt);
        if (activeCombatController == ActiveCombatController.GROUP_2V2) updateModernGroup(dt);
        for (var agent : agents) advanceAgent(agent, dt);
        for (var agent : agents) prepareDecision(agent);
        captureDifferentialStage(DifferentialTickStage.AFTER_ATTENTION_UPDATE);
        captureDifferentialStage(DifferentialTickStage.AFTER_TARGET_SELECTION);
        if (activeCombatController == ActiveCombatController.TWO_VS_ONE) updateFlanks(dt);
        for (var agent : agents) decideAgent(agent);
        captureDifferentialStage(DifferentialTickStage.AFTER_COMBAT_DECISIONS);
        for (var agent : agents) moveAgent(agent, dt);
        separateAgents(dt);
        for (var attacker : agents) resolveAttack(attacker);
        freezeDeadAgents();
        captureDifferentialStage(DifferentialTickStage.AFTER_PHYSICAL_RESOLUTION);
        updateWinner();
        if (winningTeam != null) {
            completeExperimentalMatch();
            captureDifferentialStage(DifferentialTickStage.END_OF_TICK);
            return;
        }
        requestRosterTransition(captureRoster());
        applyPendingRosterTransition();
        captureDifferentialStage(DifferentialTickStage.END_OF_TICK);
    }

    private Set<Musketeer> aliveAgents() {
        var alive = Collections.newSetFromMap(new IdentityHashMap<Musketeer, Boolean>());
        for (var agent : agents)
            if (agent.alive) alive.add(agent);
        return alive;
    }

    private void purgeDeadReferences(Set<Musketeer> alive) {
        for (var agent : agents) {
            if (!alive.contains(agent.target)) agent.target = agent.alive ? closestAliveOpponent(agent) : null;
            if (!alive.contains(agent.plannedTarget)) agent.plannedTarget = null;
            if (!alive.contains(agent.primaryMark)) agent.primaryMark = null;
            if (!alive.contains(agent.secondaryThreat)) agent.secondaryThreat = null;
            if (!alive.contains(agent.coveredBy)) agent.coveredBy = null;
            if (!alive.contains(agent.covering)) agent.covering = null;
            if (!alive.contains(agent.tacticalThreat)) agent.tacticalThreat = null;
            if (!alive.contains(agent.group3DefensiveThreat)) agent.group3DefensiveThreat = null;
            if (!alive.contains(agent.rearThreat)) agent.rearThreat = null;
            if (!alive.contains(agent.doubleBlockThreat)) agent.doubleBlockThreat = null;
            if (!alive.contains(agent.dezoneOriginalTarget)) agent.dezoneOriginalTarget = null;
            if (!alive.contains(agent.dezoneSwitchTarget)) agent.dezoneSwitchTarget = null;
            if (!alive.contains(agent.pressureSource)) agent.pressureSource = null;
            if (!alive.contains(agent.pressureFixedTarget)) agent.pressureFixedTarget = null;
            if (!alive.contains(agent.pressureTransferTarget)) agent.pressureTransferTarget = null;
            if (!alive.contains(agent.forcedPressureSource)) agent.forcedPressureSource = null;
            if (!alive.contains(agent.localObjective)) agent.localObjective = null;
            if (!alive.contains(agent.openingOpponent)) agent.openingOpponent = null;
            if (!alive.contains(agent.movingIntoLineTarget)) agent.movingIntoLineTarget = null;
            var attention = agent.attentionAllocation;
            if (!alive.contains(attention.primaryThreat)) attention.primaryThreat = null;
            if (!alive.contains(attention.secondaryThreat)) attention.secondaryThreat = null;
            if (!alive.contains(attention.peripheralThreat)) attention.peripheralThreat = null;
            if (!alive.contains(attention.pendingPrimary)) attention.pendingPrimary = null;
            if (!alive.contains(attention.formerPrimary)) attention.formerPrimary = null;
            if (!alive.contains(agent.multiThreatAttention.focusedThreat))
                agent.multiThreatAttention.focusedThreat = null;
            if (!alive.contains(agent.multiThreatAttention.secondaryThreat))
                agent.multiThreatAttention.secondaryThreat = null;
            if (!alive.contains(agent.movementIntent.target)) agent.movementIntent.target = null;
            if (agent.localManeuver != null &&
                    (!alive.contains(agent.localManeuver.referenceAgent) ||
                            !alive.contains(agent.localManeuver.referencedThreat))) agent.localManeuver = null;
            if (coverAssessmentHasDead(agent.coverAssessment, alive)) agent.coverAssessment = null;
            if (attackOpportunityHasDead(agent.attackOpportunity, alive)) agent.attackOpportunity = null;
            if (revengeOpportunityHasDead(agent.revengeOpportunity, alive)) agent.revengeOpportunity = null;
            if (counterCoverHasDead(agent.counterCoverAssessment, alive)) agent.counterCoverAssessment = null;
            if (agent.primaryWeaponLane != null && !alive.contains(agent.primaryWeaponLane.attacker()))
                agent.primaryWeaponLane = null;
            if (agent.secondaryWeaponLane != null && !alive.contains(agent.secondaryWeaponLane.attacker()))
                agent.secondaryWeaponLane = null;
            agent.opponentAwareness.keySet().removeIf(opponent -> !alive.contains(opponent));
            agent.pressureByOpponent.keySet().removeIf(opponent -> !alive.contains(opponent));
            agent.localThreatByOpponent.keySet().removeIf(opponent -> !alive.contains(opponent));
        }
        var group = activeCombatController == ActiveCombatController.GROUP_3V3 ||
                activeCombatController == ActiveCombatController.GROUP_3V2 ||
                activeCombatController == ActiveCombatController.GROUP_3V1 ? activeGroup3v3() : null;
        if (group != null) {
            group.blueFormation.purgeDeadReferences(alive);
            group.redFormation.purgeDeadReferences(alive);
            group.blueCoverNetwork.purgeDeadReferences(alive);
            group.redCoverNetwork.purgeDeadReferences(alive);
            group.localClusters.clear();
            group.isolationTime.keySet().removeIf(member -> !alive.contains(member));
            group.opposingCoverSummaries.clear();
            group.clusterEvidence.clear();
            group.blueAttackPlan = group.redAttackPlan = null;
            group.blueApproachPlan = group.redApproachPlan = null;
            if (revengeCandidateHasDead(group.revengeCandidate, alive)) group.revengeCandidate = null;
            if (counterCoverHasDead(group.counterCoverAssessment, alive)) group.counterCoverAssessment = null;
        }
        ++group3Metrics.deadReferencePurges;
        ++group3Metrics.deadReferencesRemoved;
    }

    private DeadReferenceAudit auditDeadReferences() {
        var alive = aliveAgents();
        var details = new ArrayList<String>();
        var activeTargets = 0;
        var attentionReferences = 0;
        var coverLinks = 0;
        var clusterMembers = 0;
        var formationMembers = 0;
        var planMembers = 0;
        for (var agent : agents) {
            if (!agent.alive) continue;
            var owner = agent.name(UiLanguage.FRENCH);
            activeTargets += countDeadReferences(details, owner + ":active", alive,
                    agent.target, agent.plannedTarget, agent.primaryMark, agent.secondaryThreat,
                    agent.tacticalThreat, agent.group3DefensiveThreat, agent.rearThreat,
                    agent.doubleBlockThreat, agent.dezoneOriginalTarget, agent.dezoneSwitchTarget,
                    agent.pressureSource, agent.pressureFixedTarget, agent.pressureTransferTarget,
                    agent.forcedPressureSource, agent.localObjective, agent.openingOpponent,
                    agent.movingIntoLineTarget, agent.movementIntent.target);
            attentionReferences += countDeadReferences(details, owner + ":attention", alive,
                    agent.attentionAllocation.primaryThreat, agent.attentionAllocation.secondaryThreat,
                    agent.attentionAllocation.peripheralThreat, agent.attentionAllocation.pendingPrimary,
                    agent.attentionAllocation.formerPrimary, agent.multiThreatAttention.focusedThreat,
                    agent.multiThreatAttention.secondaryThreat);
            for (var opponent : agent.opponentAwareness.keySet())
                if (!alive.contains(opponent)) {
                    ++attentionReferences;
                    details.add(owner + ":opponentAwareness=" + opponent.name(UiLanguage.FRENCH));
                }
            coverLinks += countDeadReferences(details, owner + ":cover", alive,
                    agent.coveredBy, agent.covering,
                    agent.coverAssessment == null ? null : agent.coverAssessment.protectedAlly(),
                    agent.coverAssessment == null ? null : agent.coverAssessment.coveringAgent(),
                    agent.coverAssessment == null ? null : agent.coverAssessment.punishableEnemy());
            planMembers += countDeadReferences(details, owner + ":attackOpportunity", alive,
                    attackOpportunityMembers(agent.attackOpportunity));
            planMembers += countDeadReferences(details, owner + ":revengeOpportunity", alive,
                    revengeOpportunityMembers(agent.revengeOpportunity));
            planMembers += countDeadReferences(details, owner + ":counterCover", alive,
                    counterCoverMembers(agent.counterCoverAssessment));
        }
        var group = activeCombatController == ActiveCombatController.GROUP_3V3 ||
                activeCombatController == ActiveCombatController.GROUP_3V2 ||
                activeCombatController == ActiveCombatController.GROUP_3V1 ? activeGroup3v3() : null;
        if (group != null) {
            for (var link : group.blueCoverNetwork.links)
                if (link.viable())
                    coverLinks += countDeadReferences(details, "blueCoverNetwork", alive,
                            link.protector(), link.protectedAlly(), link.threateningEnemy());
            for (var link : group.redCoverNetwork.links)
                if (link.viable())
                    coverLinks += countDeadReferences(details, "redCoverNetwork", alive,
                            link.protector(), link.protectedAlly(), link.threateningEnemy());
            for (var cluster : group.localClusters) {
                clusterMembers += countDeadReferences(details, "cluster:blue", alive,
                        cluster.blueMembers().toArray(Musketeer[]::new));
                clusterMembers += countDeadReferences(details, "cluster:red", alive,
                        cluster.redMembers().toArray(Musketeer[]::new));
            }
            formationMembers += countDeadReferences(details, "blueFormation:roles", alive,
                    group.blueFormation.leftWing, group.blueFormation.centerPlayer,
                    group.blueFormation.rightWing);
            formationMembers += countDeadReferences(details, "blueFormation:maneuver", alive,
                    group.blueManeuver == null ? null : group.blueManeuver.innerPlayer,
                    group.blueManeuver == null ? null : group.blueManeuver.outerPlayer);
            formationMembers += countDeadReferences(details, "redFormation:roles", alive,
                    group.redFormation.leftWing, group.redFormation.centerPlayer,
                    group.redFormation.rightWing);
            formationMembers += countDeadReferences(details, "redFormation:maneuver", alive,
                    group.redManeuver == null ? null : group.redManeuver.innerPlayer,
                    group.redManeuver == null ? null : group.redManeuver.outerPlayer);
            planMembers += countPlanDeadReferences(details, "blueAttackPlan", alive, group.blueAttackPlan);
            planMembers += countPlanDeadReferences(details, "redAttackPlan", alive, group.redAttackPlan);
            if (revengeCandidateHasDead(group.revengeCandidate, alive)) {
                ++planMembers;
                details.add("group:revengeCandidate");
            }
            if (counterCoverHasDead(group.counterCoverAssessment, alive)) {
                ++planMembers;
                details.add("group:counterCoverAssessment");
            }
        }
        return new DeadReferenceAudit(activeTargets, attentionReferences, coverLinks, clusterMembers,
                formationMembers, planMembers, details);
    }

    private int countDeadReferences(List<String> details, String owner, Set<Musketeer> alive,
                                    Musketeer... references) {
        var count = 0;
        for (var reference : references)
            if (reference != null && !alive.contains(reference)) {
                ++count;
                details.add(owner + "=" + reference.name(UiLanguage.FRENCH));
            }
        return count;
    }

    private Musketeer[] attackOpportunityMembers(AttackOpportunity opportunity) {
        return opportunity == null ? new Musketeer[0] : new Musketeer[]{opportunity.attacker(),
                opportunity.target(), opportunity.ally(), opportunity.opposingCoverer()};
    }

    private Musketeer[] revengeOpportunityMembers(RevengeOpportunity opportunity) {
        return opportunity == null ? new Musketeer[0] : new Musketeer[]{opportunity.revenger(),
                opportunity.vulnerableAttacker(), opportunity.protectedAlly(),
                opportunity.opposingCoverer()};
    }

    private Musketeer[] counterCoverMembers(CounterCoverAssessment assessment) {
        return assessment == null ? new Musketeer[0] : new Musketeer[]{assessment.counterCoverer(),
                assessment.enemyRevenger(), assessment.protectedAlly()};
    }

    private boolean coverAssessmentHasDead(CoverAssessment assessment, Set<Musketeer> alive) {
        return assessment != null && (dead(assessment.protectedAlly(), alive) ||
                dead(assessment.coveringAgent(), alive) || dead(assessment.punishableEnemy(), alive));
    }

    private boolean attackOpportunityHasDead(AttackOpportunity opportunity, Set<Musketeer> alive) {
        return opportunity != null && containsDead(attackOpportunityMembers(opportunity), alive);
    }

    private boolean revengeOpportunityHasDead(RevengeOpportunity opportunity, Set<Musketeer> alive) {
        return opportunity != null && containsDead(revengeOpportunityMembers(opportunity), alive);
    }

    private boolean counterCoverHasDead(CounterCoverAssessment assessment, Set<Musketeer> alive) {
        return assessment != null && containsDead(counterCoverMembers(assessment), alive);
    }

    private boolean revengeCandidateHasDead(TeamRevengeCandidate candidate, Set<Musketeer> alive) {
        return candidate != null && (dead(candidate.revenger(), alive) ||
                revengeOpportunityHasDead(candidate.opportunity(), alive));
    }

    private boolean containsDead(Musketeer[] references, Set<Musketeer> alive) {
        for (var reference : references)
            if (dead(reference, alive)) return true;
        return false;
    }

    private boolean dead(Musketeer reference, Set<Musketeer> alive) {
        return reference != null && !alive.contains(reference);
    }

    private int countPlanDeadReferences(List<String> details, String owner, Set<Musketeer> alive,
                                        TeamAttackPlan plan) {
        if (plan == null) return 0;
        var count = countDeadReferences(details, owner + ":attackers", alive,
                plan.selectedAttackers().toArray(Musketeer[]::new));
        count += countDeadReferences(details, owner + ":coverers", alive,
                plan.coveringMembers().toArray(Musketeer[]::new));
        for (var opportunity : plan.opportunities())
            count += countDeadReferences(details, owner + ":opportunity", alive,
                    attackOpportunityMembers(opportunity));
        return count;
    }

    private int auditTotal(DeadReferenceAudit audit) {
        return audit.activeDeadTargets() + audit.deadAttentionReferences() + audit.deadCoverLinks() +
                audit.deadClusterMembers() + audit.deadFormationMembers() + audit.deadPlanMembers();
    }

    private void updateProgressWatchdog(double dt) {
        var current = progressSnapshot();
        if (lastProgressSnapshot == null) {
            lastProgressSnapshot = current;
            return;
        }
        var rosterChanged = !current.roster().equals(lastProgressSnapshot.roster());
        var controllerChanged = current.controller() != lastProgressSnapshot.controller();
        var positionChanged = rosterChanged || positionsChanged(lastProgressSnapshot.positions(),
                current.positions());
        var stateChanged = rosterChanged || statesChanged(lastProgressSnapshot.states(), current.states());
        ticksWithoutAnyPositionChange = positionChanged ? 0 : ticksWithoutAnyPositionChange + 1;
        ticksWithoutAnyStateChange = stateChanged ? 0 : ticksWithoutAnyStateChange + 1;
        repeatedTransitionStateTicks = pendingRosterTransition != null && !controllerChanged && !rosterChanged ?
                repeatedTransitionStateTicks + 1 : 0;
        group3Metrics.ticksWithoutAnyPositionChange = Math.max(
                group3Metrics.ticksWithoutAnyPositionChange, (int) ticksWithoutAnyPositionChange);
        group3Metrics.ticksWithoutAnyStateChange = Math.max(
                group3Metrics.ticksWithoutAnyStateChange, (int) ticksWithoutAnyStateChange);
        group3Metrics.repeatedTransitionStateTicks = Math.max(
                group3Metrics.repeatedTransitionStateTicks, (int) repeatedTransitionStateTicks);
        if (positionChanged || stateChanged || controllerChanged || rosterChanged) {
            timeWithoutGlobalProgress = 0;
            logicalFreezeActive = false;
        } else timeWithoutGlobalProgress += dt;
        if (!logicalFreezeActive && timeWithoutGlobalProgress >= 5.0 &&
                activeCombatController != ActiveCombatController.COMPLETE) {
            logicalFreezeActive = true;
            ++group3Metrics.logicalFreezeReports;
            transitionLogger.emitOnce("LOGICAL_FREEZE:" + experimentalTick,
                    "LOGICAL_FREEZE tick=" + experimentalTick + " time=%.3f".formatted(simulationTime) +
                            " controller=" + activeCombatController + " roster=" + current.roster());
        }
        lastProgressSnapshot = current;
    }

    private ProgressSnapshot progressSnapshot() {
        var positions = new Pos[agents.length];
        var states = new CombatState[agents.length];
        for (var n = 0; n < agents.length; ++n) {
            positions[n] = agents[n].pos;
            states[n] = agents[n].state;
        }
        return new ProgressSnapshot(experimentalTick, simulationTime, activeCombatController,
                captureRoster(), positions, states);
    }

    private boolean positionsChanged(Pos[] before, Pos[] after) {
        if (before.length != after.length) return true;
        for (var n = 0; n < before.length; ++n)
            if (before[n].minus(after[n]).hypotenuse() > 0.000001) return true;
        return false;
    }

    private boolean statesChanged(CombatState[] before, CombatState[] after) {
        if (before.length != after.length) return true;
        for (var n = 0; n < before.length; ++n)
            if (before[n] != after[n]) return true;
        return false;
    }

    private void observeRosterRequest(RosterSnapshot snapshot) {
        while (!rosterRequestObservations.isEmpty() &&
                rosterRequestObservations.getFirst().tick() < experimentalTick - 60)
            rosterRequestObservations.removeFirst();
        rosterRequestObservations.addLast(new RosterRequestObservation(experimentalTick, snapshot));
        var repeated = 0;
        for (var observation : rosterRequestObservations)
            if (observation.roster().equals(snapshot)) ++repeated;
        if (repeated <= 2 || transitionFailsafePaused) return;
        transitionFailsafePaused = true;
        ++group3Metrics.transitionFailsafePauses;
        var engagements = new ArrayList<String>();
        for (var engagement : engagementManager.engagements())
            if (engagement.status != EngagementStatus.BROKEN &&
                    engagement.status != EngagementStatus.FINISHED)
                engagements.add(engagement.type + ":" + engagement.status);
        transitionFailsafeReport = new TransitionFailsafeReport(scenario, activeCombatController,
                snapshot, processedRoster, pendingRosterTransition,
                transitionTraces.isEmpty() ? null : transitionTraces.getLast(),
                lastDeadReferenceAudit, engagements,
                List.of("updateExperimental3v3", "requestRosterTransition",
                        "applyPendingRosterTransition"));
        transitionLogger.emitOnce("TRANSITION_FAILSAFE:" + snapshot,
                "TRANSITION_FAILSAFE tick=" + experimentalTick + " controller=" +
                        activeCombatController + " roster=" + snapshot + " processed=" +
                        processedRoster + " pending=" + pendingRosterTransition);
    }

    private void requestRosterTransition(RosterSnapshot snapshot) {
        if (snapshot.equals(processedRoster)) return;
        observeRosterRequest(snapshot);
        if (transitionFailsafePaused) return;
        if (snapshot.equals(pendingRosterTransition)) {
            ++group3Metrics.duplicateTransitionRequests;
            return;
        }
        pendingRosterTransition = snapshot;
        ++group3Metrics.transitionRequests;
    }

    private void applyPendingRosterTransition() {
        if (pendingRosterTransition == null) return;
        if (transitionInProgress) {
            ++group3Metrics.recursiveTransitionAttempts;
            return;
        }
        if (transitionAppliedThisTick) {
            ++group3Metrics.multipleTransitionAttemptsSameTick;
            return;
        }
        var snapshot = pendingRosterTransition;
        var before = processedRoster;
        var phaseBefore = experimentalGroupPhase;
        var controllerBefore = activeCombatController;
        var requestedPhase = phaseForRoster(snapshot);
        var requestedController = controllerForPhase(requestedPhase);
        var reason = transitionReason(before, snapshot);
        var transitionId = ++nextTransitionId;
        var transitionKey = new TransitionKey(controllerBefore, before, snapshot, requestedController);
        if (appliedRosterTransitions.contains(transitionKey)) {
            ++group3Metrics.duplicateTransitionApplications;
            pendingRosterTransition = null;
            throw new AssertionError("Duplicate roster transition application: " + transitionKey);
        }
        var requestTrace = transitionTrace(transitionId, before, snapshot, controllerBefore,
                controllerBefore, phaseBefore, requestedPhase, phaseBefore, false, reason);
        transitionTraces.add(requestTrace);
        transitionLogger.record(requestTrace);
        transitionInProgress = true;
        try {
            var group = activeGroup3v3();
            if (group != null && group.exchangeActive()) finishGroup3Exchange(group, "elimination");
            purgeDeadReferences(aliveAgents());
            if (group != null) rebuildExperimentalGroupAfterElimination(group);
            else rebuildExperimentalHandoffAfterElimination(snapshot);
            if (activeCombatController != requestedController)
                throw new IllegalStateException("Installed controller " + activeCombatController +
                        " differs from requested " + requestedController);
            processedRoster = snapshot;
            experimentalAliveMask = aliveMask();
            pendingRosterTransition = null;
            transitionAppliedThisTick = true;
            appliedRosterTransitions.add(transitionKey);
            ++group3Metrics.transitionApplications;
            lastDeadReferenceAudit = auditDeadReferences();
            group3Metrics.deadReferencesAfterTransition += auditTotal(lastDeadReferenceAudit);
            if (!lastDeadReferenceAudit.clean())
                throw new AssertionError("Dead references remain after transition: " +
                        lastDeadReferenceAudit.details());
            var appliedTrace = transitionTrace(transitionId, before, snapshot, controllerBefore,
                    activeCombatController, phaseBefore, requestedPhase, experimentalGroupPhase,
                    true, reason);
            transitionTraces.add(appliedTrace);
            transitionLogger.record(appliedTrace);
        } catch (RuntimeException exception) {
            pendingRosterTransition = null;
            transitionLogger.emitOnce("TRANSITION_ERROR:" + transitionId,
                    "TRANSITION_ERROR id=" + transitionId + " type=" +
                            exception.getClass().getSimpleName() + " message=" + exception.getMessage());
            throw new IllegalStateException("Roster transition " + transitionId + " failed: " +
                    before + " -> " + snapshot, exception);
        } finally {
            transitionInProgress = false;
        }
    }

    private void rebuildExperimentalHandoffAfterElimination(RosterSnapshot snapshot) {
        var blueAlive = livingAgents(blueTeam);
        var redAlive = livingAgents(redTeam);
        if (snapshot.blueAlive() == 0 || snapshot.redAlive() == 0) {
            winningTeam = snapshot.blueAlive() == 0 ? Team.RED : Team.BLUE;
            completeExperimentalMatch();
        } else if (snapshot.blueAlive() == 2 && snapshot.redAlive() == 1 ||
                snapshot.redAlive() == 2 && snapshot.blueAlive() == 1)
            beginExperimentalHandoff(ExperimentalGroupPhase.HANDOFF_2V1, blueAlive, redAlive);
        else if (snapshot.blueAlive() == 1 && snapshot.redAlive() == 1)
            beginExperimentalHandoff(ExperimentalGroupPhase.HANDOFF_1V1, blueAlive, redAlive);
        else throw new IllegalStateException("Unsupported handoff roster: " + snapshot);
    }

    private TransitionTrace transitionTrace(long id, RosterSnapshot before, RosterSnapshot after,
                                            ActiveCombatController controllerBefore,
                                            ActiveCombatController controllerAfter,
                                            ExperimentalGroupPhase phaseBefore,
                                            ExperimentalGroupPhase requestedPhase,
                                            ExperimentalGroupPhase phaseAfter, boolean applied,
                                            String reason) {
        return new TransitionTrace(id, experimentalTick, simulationTime, scenario,
                controllerBefore.name(), controllerAfter.name(),
                phaseBefore.name(), requestedPhase.name(), phaseAfter.name(),
                before.blueMask(), before.redMask(), after.blueMask(), after.redMask(),
                after.blueAlive(), after.redAlive(), applied, reason);
    }

    private ExperimentalGroupPhase phaseForRoster(RosterSnapshot roster) {
        if (roster.blueAlive() == 0 || roster.redAlive() == 0) return ExperimentalGroupPhase.COMPLETE;
        var maximum = Math.max(roster.blueAlive(), roster.redAlive());
        var minimum = Math.min(roster.blueAlive(), roster.redAlive());
        if (maximum == 3)
            return minimum == 3 ? ExperimentalGroupPhase.GROUP_3V3 :
                    minimum == 2 ? ExperimentalGroupPhase.GROUP_3V2 : ExperimentalGroupPhase.GROUP_3V1;
        if (maximum == 2 && minimum == 2) return ExperimentalGroupPhase.HANDOFF_2V2;
        if (maximum == 2) return ExperimentalGroupPhase.HANDOFF_2V1;
        return ExperimentalGroupPhase.HANDOFF_1V1;
    }

    private ActiveCombatController controllerForPhase(ExperimentalGroupPhase phase) {
        return switch (phase) {
            case GROUP_3V3 -> ActiveCombatController.GROUP_3V3;
            case GROUP_3V2 -> ActiveCombatController.GROUP_3V2;
            case GROUP_3V1 -> ActiveCombatController.GROUP_3V1;
            case HANDOFF_2V2 -> ActiveCombatController.GROUP_2V2;
            case HANDOFF_2V1 -> ActiveCombatController.TWO_VS_ONE;
            case HANDOFF_1V1 -> ActiveCombatController.DUEL;
            case COMPLETE -> ActiveCombatController.COMPLETE;
        };
    }

    private String transitionReason(RosterSnapshot before, RosterSnapshot after) {
        var blueChanged = before.blueMask() != after.blueMask();
        var redChanged = before.redMask() != after.redMask();
        if (blueChanged && redChanged) return "SIMULTANEOUS_ELIMINATIONS";
        return blueChanged ? "BLUE_AGENT_ELIMINATED" : "RED_AGENT_ELIMINATED";
    }

    private void completeExperimentalMatch() {
        experimentalRoundComplete = true;
        experimentalRoundLeader = winningTeam;
        experimentalGroupPhase = ExperimentalGroupPhase.COMPLETE;
        activeCombatController = ActiveCombatController.COMPLETE;
        ++group3Metrics.matchesCompleted;
        logTactical("GROUP3_MATCH_COMPLETE", "winner=" + winningTeam +
                " transitions_complete=true");
    }

    private Group3v3Engagement activeGroup3v3() {
        return modernGroup3Engagement != null &&
                modernGroup3Engagement.status != EngagementStatus.BROKEN &&
                modernGroup3Engagement.status != EngagementStatus.FINISHED ?
                modernGroup3Engagement.group3 : null;
    }

    private void initializeGroup3Attention(Musketeer observer) {
        var ranked = rankedGroup3Threats(observer);
        var attention = observer.attentionAllocation;
        attention.primaryThreat = ranked.isEmpty() ? null : ranked.get(0);
        attention.secondaryThreat = ranked.size() < 2 ? null : ranked.get(1);
        attention.peripheralThreat = ranked.size() < 3 ? null : ranked.get(2);
        attention.pendingPrimary = null;
        attention.formerPrimary = null;
        attention.lastShiftTime = simulationTime;
        attention.secondaryLastShiftTime = simulationTime;
        attention.shiftReason = "initial_geometry";
        updateAttentionWeights(observer);
        applyAttentionLevels(observer);
    }

    private void updateGroup3Attention(Musketeer observer) {
        if (!observer.alive && observer.deadAgentAudit.active()) {
            ++observer.deadAgentAudit.attentionParticipations;
            ++observer.deadAgentAudit.targetSelectionParticipations;
        }
        var ranked = rankedGroup3Threats(observer);
        if (ranked.isEmpty()) return;
        var attention = observer.attentionAllocation;
        if (attention.primaryThreat == null || !attention.primaryThreat.alive) {
            attention.primaryThreat = ranked.get(0);
            attention.pendingPrimary = null;
            attention.lastShiftTime = simulationTime;
        }
        if (attention.pendingPrimary != null && simulationTime >= attention.switchCompletesAt) {
            attention.formerPrimary = attention.primaryThreat;
            attention.primaryThreat = attention.pendingPrimary;
            attention.pendingPrimary = null;
            attention.lastShiftTime = simulationTime;
            attention.precisionPenaltyUntil = simulationTime + 0.16 +
                    (100 - observer.stats.decisionMaking()) * 0.0018;
            attention.lastCompletedShiftReason = attention.shiftReason;
            ++group3Metrics.primaryAttentionShifts;
            logTactical("ATTENTION_SHIFT_3V3", "observer=" + observer.name(UiLanguage.FRENCH) +
                    " primary=" + attention.primaryThreat.name(UiLanguage.FRENCH) +
                    " reason=" + attention.shiftReason);
        }
        var best = ranked.get(0);
        if (group3ClusterContaining(observer, best) != null)
            ++group3Metrics.clusterAttentionPriorities;
        if (best != attention.primaryThreat && attention.pendingPrimary == null) {
            var bestScore = group3ThreatSalience(observer, best);
            var currentScore = group3ThreatSalience(observer, attention.primaryThreat);
            var urgent = urgentAttentionShift(observer, best);
            var quality = (observer.stats.reaction() + observer.stats.decisionMaking()) / 200.0;
            var minimumLock = 0.90 - quality * 0.35;
            if (urgent || simulationTime - attention.lastShiftTime >= minimumLock &&
                    bestScore >= currentScore + 0.16) {
                attention.pendingPrimary = best;
                attention.pendingSince = simulationTime;
                attention.switchCompletesAt = simulationTime + (urgent ? 0.08 : 0.38 - quality * 0.24);
                attention.shiftReason = urgent ? "immediate_lateral_threat" : "higher_local_salience";
                ++group3Metrics.delayedAttentionShifts;
            }
        } else if (attention.pendingPrimary != null && best != attention.pendingPrimary &&
                !urgentAttentionShift(observer, attention.pendingPrimary)) {
            attention.pendingPrimary = null;
            attention.shiftReason = "pending_shift_cancelled";
        }
        var oldSecondary = attention.secondaryThreat;
        Musketeer candidateSecondary = null, candidatePeripheral = null;
        for (var opponent : ranked) {
            if (opponent == attention.primaryThreat) continue;
            if (candidateSecondary == null) candidateSecondary = opponent;
            else {
                candidatePeripheral = opponent;
                break;
            }
        }
        var secondary = candidateSecondary;
        var peripheral = candidatePeripheral;
        if (oldSecondary != null && oldSecondary.alive && oldSecondary != attention.primaryThreat &&
                candidateSecondary != oldSecondary &&
                (simulationTime - attention.secondaryLastShiftTime < 0.42 ||
                        group3ThreatSalience(observer, candidateSecondary) <
                                group3ThreatSalience(observer, oldSecondary) + 0.14)) {
            secondary = oldSecondary;
            for (var opponent : ranked)
                if (opponent != attention.primaryThreat && opponent != secondary) {
                    peripheral = opponent;
                    break;
                }
        }
        attention.secondaryThreat = secondary;
        attention.peripheralThreat = peripheral;
        if (oldSecondary != null && oldSecondary != secondary) {
            attention.secondaryLastShiftTime = simulationTime;
            ++group3Metrics.secondaryAttentionShifts;
        }
        updateAttentionWeights(observer);
        applyAttentionLevels(observer);
        group3Metrics.primaryWeightSum += attention.primaryWeight;
        group3Metrics.secondaryWeightSum += attention.secondaryWeight;
        group3Metrics.peripheralWeightSum += attention.peripheralWeight;
        ++group3Metrics.attentionSamples;
    }

    private ArrayList<Musketeer> rankedGroup3Threats(Musketeer observer) {
        var ranked = new ArrayList<Musketeer>(3);
        for (var opponent : opponentsOf(observer))
            if (opponent.alive) ranked.add(opponent);
        ranked.sort((first, second) -> Double.compare(group3ThreatSalience(observer, second),
                group3ThreatSalience(observer, first)));
        return ranked;
    }

    private double group3ThreatSalience(Musketeer observer, Musketeer opponent) {
        if (opponent == null || !opponent.alive) return -10;
        var offset = opponent.pos.minus(observer.pos);
        var distance = offset.hypotenuse();
        var relativeAngle = Math.abs(angleDifference(observer.angle, offset.radians()));
        var frontal = perceptionAngleFactor(relativeAngle);
        var towardObserver = observer.pos.minus(opponent.pos);
        var closing = towardObserver.hypotenuse() < 0.0001 ? 0 :
                opponent.velocity.dot(towardObserver.div(towardObserver.hypotenuse()));
        var awareness = observer.opponentAwareness.get(opponent);
        var observedAttack = awareness != null && awareness.stateDetailFresh &&
                (awareness.observedState == CombatState.ATTACK_PREPARE ||
                        awareness.observedState == CombatState.ATTACK_HOLD ||
                        awareness.observedState == CombatState.ATTACK_ACTIVE ||
                        awareness.observedState == CombatState.CHAMBER_ACTIVE);
        var attack = observedAttack ? 0.82 : awareness != null && awareness.motionAlert ? 0.48 : 0;
        var proximity = Math.clamp((3.9 - distance) / 3.0, 0, 1);
        var rearAlarm = relativeAngle > Math.toRadians(115) && distance < 2.0 ? 0.38 : 0;
        var cluster = group3ClusterContaining(observer, opponent);
        var clusterPriority = cluster == null ? 0 : 0.10 +
                cluster.isolationConfidence() * 0.16 +
                Math.clamp((1.0 - cluster.minimumImpactEta()) * 0.12, 0, 0.12);
        return proximity * 1.35 + frontal * 0.42 + Math.max(0, closing) * 0.35 + attack +
                rearAlarm + clusterPriority;
    }

    private boolean urgentAttentionShift(Musketeer observer, Musketeer opponent) {
        if (opponent == null) return false;
        var distance = opponent.pos.minus(observer.pos).hypotenuse();
        var awareness = observer.opponentAwareness.get(opponent);
        return distance < 1.32 || awareness != null && awareness.motionAlert && distance < 2.15 ||
                Math.abs(angleDifference(observer.angle, opponent.pos.minus(observer.pos).radians())) >
                        Math.toRadians(145) && distance < 1.65;
    }

    private void updateAttentionWeights(Musketeer observer) {
        var attention = observer.attentionAllocation;
        if (attention.secondaryThreat == null) {
            attention.primaryWeight = attention.primaryThreat == null ? 0 : 1;
            attention.secondaryWeight = attention.peripheralWeight = 0;
            return;
        }
        if (attention.peripheralThreat == null) {
            var difference = group3ThreatSalience(observer, attention.primaryThreat) -
                    group3ThreatSalience(observer, attention.secondaryThreat);
            attention.primaryWeight = Math.clamp(0.62 + difference * 0.08, 0.54, 0.74);
            attention.secondaryWeight = 1 - attention.primaryWeight;
            attention.peripheralWeight = 0;
            return;
        }
        var primaryScore = group3ThreatSalience(observer, attention.primaryThreat);
        var secondaryScore = group3ThreatSalience(observer, attention.secondaryThreat);
        attention.primaryWeight = Math.clamp(0.54 + (primaryScore - secondaryScore) * 0.075, 0.50, 0.66);
        if (attention.switching(simulationTime)) attention.primaryWeight = Math.min(attention.primaryWeight, 0.50);
        attention.peripheralWeight = Math.clamp(0.14 - (attention.primaryWeight - 0.54) * 0.25, 0.10, 0.17);
        attention.secondaryWeight = 1 - attention.primaryWeight - attention.peripheralWeight;
    }

    private void applyAttentionLevels(Musketeer observer) {
        for (var opponent : opponentsOf(observer)) {
            if (!opponent.alive) {
                observer.opponentAwareness.remove(opponent);
                continue;
            }
            var awareness = observer.opponentAwareness.computeIfAbsent(opponent,
                    ignored -> new OpponentAwareness(opponent.pos, false));
            var level = observer.attentionAllocation.levelOf(opponent);
            if (awareness.attentionLevel != level) {
                awareness.observationCooldown = 0;
                var confidenceCeiling = switch (level) {
                    case PRIMARY -> 1.0;
                    case SECONDARY -> 0.68;
                    case PERIPHERAL -> 0.30;
                    case NONE -> 0.08;
                };
                awareness.detailConfidence = Math.min(awareness.detailConfidence, confidenceCeiling);
                if (level == AttentionLevel.PERIPHERAL || level == AttentionLevel.NONE)
                    awareness.stateDetailFresh = false;
            }
            awareness.attentionLevel = level;
        }
    }

    private void updateGroup3Perception(Musketeer observer, double dt) {
        if (!observer.alive && observer.deadAgentAudit.active())
            ++observer.deadAgentAudit.attentionParticipations;
        for (var opponent : opponentsOf(observer)) {
            if (!opponent.alive) continue;
            var awareness = observer.opponentAwareness.get(opponent);
            if (awareness == null) {
                awareness = new OpponentAwareness(opponent.pos, false);
                observer.opponentAwareness.put(opponent, awareness);
            }
            awareness.memoryTime = Math.max(0, awareness.memoryTime - dt);
            awareness.observationCooldown -= dt;
            if (awareness.observationCooldown > 0) continue;
            var level = observer.attentionAllocation.levelOf(opponent);
            var quality = (observer.stats.reaction() + observer.stats.peripheralAwareness()) / 200.0;
            var interval = switch (level) {
                case PRIMARY -> 0.075 + (1 - quality) * 0.050;
                case SECONDARY -> 0.17 + (1 - quality) * 0.12;
                case PERIPHERAL -> 0.42 + (1 - quality) * 0.30;
                case NONE -> 0.80;
            };
            awareness.observationCooldown = interval * (0.88 + random.nextDouble() * 0.24);
            ++awareness.observationAttempts;
            switch (level) {
                case PRIMARY -> ++awareness.primaryObservationAttempts;
                case SECONDARY -> ++awareness.secondaryObservationAttempts;
                case PERIPHERAL -> ++awareness.peripheralObservationAttempts;
                case NONE -> { }
            }
            var offset = opponent.pos.minus(observer.pos);
            var distance = offset.hypotenuse();
            var relativeAngle = Math.abs(angleDifference(observer.angle, offset.radians()));
            var attentionFactor = switch (level) {
                case PRIMARY -> 1.0;
                case SECONDARY -> 0.68;
                case PERIPHERAL -> 0.34;
                case NONE -> 0.12;
            };
            var probability = perceptionAngleFactor(relativeAngle) *
                    Math.clamp(1 - Math.max(0, distance - 1.4) / 4.2, 0.12, 1) *
                    (0.48 + observer.stats.peripheralAwareness() * 0.0052) * attentionFactor;
            var obviousAttack = opponent.state == CombatState.ATTACK_PREPARE ||
                    opponent.state == CombatState.ATTACK_HOLD || opponent.isAttacking();
            if (obviousAttack) probability += level == AttentionLevel.PERIPHERAL ? 0.18 : 0.28;
            if (distance < 1.25) probability = Math.max(probability, level == AttentionLevel.PERIPHERAL ? 0.58 : 0.88);
            var detected = random.nextDouble() < Math.clamp(probability, 0.015, 0.99);
            awareness.visible = detected;
            awareness.stateDetailFresh = false;
            awareness.motionAlert = detected && obviousAttack;
            if (!detected) continue;
            ++awareness.observations;
            if (level == AttentionLevel.PERIPHERAL) ++group3Metrics.peripheralDetections;
            var levelError = switch (level) {
                case PRIMARY -> 1.0;
                case SECONDARY -> 2.25;
                case PERIPHERAL -> 4.60;
                case NONE -> 6.0;
            };
            if (simulationTime < observer.attentionAllocation.precisionPenaltyUntil) levelError *= 1.45;
            var positionError = (0.012 + (100 - observer.stats.distanceControl()) * 0.0032 +
                    (1 - perceptionAngleFactor(relativeAngle)) * 0.08) * levelError;
            awareness.lastKnown = opponent.pos.plus(random.nextGaussian() * positionError,
                    random.nextGaussian() * positionError);
            awareness.perceivedVelocity = opponent.velocity.plus(random.nextGaussian() * positionError * 1.8,
                    random.nextGaussian() * positionError * 1.8);
            var orientationError = positionError * (level == AttentionLevel.PRIMARY ? 1.5 : 2.4);
            awareness.observedBodyAngle = normalizeAngle(opponent.angle + random.nextGaussian() * orientationError);
            awareness.observedWeaponAngle = normalizeAngle(opponent.weaponWorldAngle() +
                    random.nextGaussian() * orientationError * 1.25);
            awareness.observedProjectedRange = Math.max(0.45, opponent.projectedThrustLength() +
                    random.nextGaussian() * positionError);
            var detailed = level == AttentionLevel.PRIMARY || level == AttentionLevel.SECONDARY &&
                    (obviousAttack || random.nextDouble() < 0.62);
            if (detailed) {
                awareness.observedState = opponent.state;
                awareness.observedStateTime = opponent.stateTime;
                awareness.observedAttackDirection = opponent.attackDirection;
                awareness.observedBlockDirection = opponent.blockDirection;
                awareness.stateDetailFresh = true;
                observeCombatPattern(awareness, opponent);
            }
            awareness.detailConfidence = switch (level) {
                case PRIMARY -> 0.78 + quality * 0.20;
                case SECONDARY -> 0.38 + quality * 0.28;
                case PERIPHERAL -> 0.12 + quality * 0.16;
                case NONE -> 0.06;
            };
            if (simulationTime < observer.attentionAllocation.precisionPenaltyUntil)
                awareness.detailConfidence *= 0.72;
            awareness.lastObservationTime = simulationTime;
            awareness.memoryTime = switch (level) {
                case PRIMARY -> 1.45;
                case SECONDARY -> 1.05;
                case PERIPHERAL -> 0.68;
                case NONE -> 0.35;
            };
        }
    }

    private void updateGroup3ContactBand(Group3v3Engagement group) {
        var blueCenter = group.blueFormation.center;
        var redCenter = group.redFormation.center;
        var centerDistance = blueCenter.minus(redCenter).hypotenuse();
        var minimumDistance = Double.POSITIVE_INFINITY;
        var potentialContacts = 0;
        for (var blue : group.blueMembers) {
            if (!blue.alive) continue;
            for (var red : group.redMembers) {
                if (!red.alive) continue;
                var distance = blue.pos.minus(red.pos).hypotenuse();
                minimumDistance = Math.min(minimumDistance, distance);
                if (distance <= 2.55) ++potentialContacts;
            }
        }
        var activeExchange = group.exchangeActive();
        for (var agent : agents)
            if (agent.alive && (agent.state == CombatState.ATTACK_PREPARE ||
                    agent.state == CombatState.ATTACK_HOLD || agent.state == CombatState.ATTACK_ACTIVE ||
                    agent.state == CombatState.BLOCK_PREPARE || agent.state == CombatState.BLOCKING ||
                    agent.state == CombatState.BLOCK_STUN)) {
                activeExchange = true;
                break;
            }
        var previous = group.contactBand;
        Group3ContactBand next;
        if (activeExchange) next = Group3ContactBand.ACTIVE_EXCHANGE;
        else next = switch (previous) {
            case FAR_APPROACH -> minimumDistance < 2.95 || potentialContacts > 0 ?
                    Group3ContactBand.CONTACT : centerDistance < 5.05 || minimumDistance < 4.45 ?
                    Group3ContactBand.SPACE_CONTEST : Group3ContactBand.FAR_APPROACH;
            case SPACE_CONTEST -> minimumDistance < 2.95 || potentialContacts > 0 ?
                    Group3ContactBand.CONTACT : centerDistance > 5.55 && minimumDistance > 4.85 ?
                    Group3ContactBand.FAR_APPROACH : Group3ContactBand.SPACE_CONTEST;
            case CONTACT -> minimumDistance > 3.32 && potentialContacts == 0 ?
                    centerDistance > 5.55 ? Group3ContactBand.FAR_APPROACH :
                            Group3ContactBand.SPACE_CONTEST : Group3ContactBand.CONTACT;
            case ACTIVE_EXCHANGE -> minimumDistance <= 3.32 || potentialContacts > 0 ?
                    Group3ContactBand.CONTACT : centerDistance > 5.55 ?
                    Group3ContactBand.FAR_APPROACH : Group3ContactBand.SPACE_CONTEST;
        };
        if (next == previous) return;
        group.contactBand = next;
        group.contactBandChangedAt = simulationTime;
        ++group3Metrics.contactBandChanges;
        if (next == Group3ContactBand.SPACE_CONTEST && group3Metrics.timeToSpaceContest < 0)
            group3Metrics.timeToSpaceContest = simulationTime - group.approachObservation.startedAt;
        logTactical("GROUP3_CONTACT_BAND", "from=" + previous + " to=" + next +
                " centerDistance=%.2f minimumDistance=%.2f contacts=%d".
                        formatted(centerDistance, minimumDistance, potentialContacts));
    }

    private void updateGroup3ApproachPlans(Group3v3Engagement group) {
        if (activeCombatController != ActiveCombatController.GROUP_3V3) return;
        if (group.contactBand == Group3ContactBand.CONTACT ||
                group.contactBand == Group3ContactBand.ACTIVE_EXCHANGE) {
            finishGroup3ApproachPlan(group, Team.BLUE, false, "contact_band");
            finishGroup3ApproachPlan(group, Team.RED, false, "contact_band");
            return;
        }
        updateGroup3ApproachPlan(group, Team.BLUE);
        updateGroup3ApproachPlan(group, Team.RED);
    }

    private void updateGroup3ApproachPlan(Group3v3Engagement group, Team team) {
        var current = group.approachPlan(team);
        var emergency = group3ApproachEmergency(group, team);
        if (current == null && emergency != null) return;
        if (current != null && emergency != null) {
            finishGroup3ApproachPlan(group, team, true, emergency);
            return;
        }
        if (current != null && current.age(simulationTime) <= current.maximumDuration) return;
        if (current != null) finishGroup3ApproachPlan(group, team, false, "maximum_duration");
        startGroup3ApproachPlan(group, team);
    }

    private String group3ApproachEmergency(Group3v3Engagement group, Team team) {
        var formation = group.formation(team);
        if (formation.cohesion < 0.25) return "formation_broken";
        var plan = group.approachPlan(team);
        if (plan != null) {
            var leader = formation.member(plan.leadRole);
            if (leader != null && arenaClearance(leader.pos) < 0.34) return "lead_role_at_boundary";
        }
        for (var member : group.members(team)) {
            if (!member.alive) continue;
            for (var enemy : group.members(team == Team.BLUE ? Team.RED : Team.BLUE))
                if (enemy.alive && enemy.isAttacking() && attackThreatEta(enemy, member) < 0.42)
                    return "imminent_weapon_threat";
        }
        for (var cluster : group.localClusters)
            if (!cluster.contested() && cluster.isolationConfidence() >= 0.66 &&
                    (team == Team.BLUE ? cluster.blueMembers() : cluster.redMembers()).size() == 1)
                return "durable_isolation";
        return null;
    }

    private void startGroup3ApproachPlan(Group3v3Engagement group, Team team) {
        var sequence = group.nextApproachSequence(team);
        var mixedSeed = seed ^ (team == Team.BLUE ? 0x6A09E667F3BCC909L : 0xBB67AE8584CAA73BL) ^
                sequence * 0x9E3779B97F4A7C15L;
        var planRandom = new Random(mixedSeed);
        var patterns = ApproachPattern.values();
        var pattern = patterns[planRandom.nextInt(patterns.length)];
        var lateralSign = switch (pattern) {
            case LEFT_LEAD, LEFT_SWEEP, OBLIQUE_PRESS_LEFT, COUNTER_ROTATE_LEFT -> -1;
            case RIGHT_LEAD, RIGHT_SWEEP, OBLIQUE_PRESS_RIGHT, COUNTER_ROTATE_RIGHT -> 1;
            case CENTER_DELAYED -> planRandom.nextBoolean() ? -1 : 1;
        };
        var leadRole = lateralSign < 0 ? FormationRole3.LEFT : FormationRole3.RIGHT;
        var trailingRole = lateralSign < 0 ? FormationRole3.RIGHT : FormationRole3.LEFT;
        var minimum = 1.2 + planRandom.nextDouble() * 0.6;
        var preferred = 2.0 + planRandom.nextDouble() * 1.4;
        var maximum = Math.max(preferred + 0.65, 4.0 + planRandom.nextDouble());
        var quality = 0.0;
        var participants = 0;
        for (var member : group.members(team))
            if (isActiveForDeadAgentExperiment(member, DeadAgentParticipationDomain.APPROACH)) {
                quality += member.stats.teamworkAbility() + member.stats.decisionMaking();
                ++participants;
            }
        quality /= Math.max(1, participants * 200.0);
        var forwardCommitment = 0.78 + quality * 0.15 + planRandom.nextDouble() * 0.12;
        var lateralCommitment = 0.28 + quality * 0.12 + planRandom.nextDouble() * 0.18;
        var depthCommitment = 0.35 + quality * 0.20 + planRandom.nextDouble() * 0.10;
        var formation = group.formation(team);
        var enemy = group.enemyFormation(team);
        var between = enemy.center.minus(formation.center);
        var forward = between.hypotenuse() < 0.0001 ? formation.forward :
                between.div(between.hypotenuse());
        var lateral = new Pos(-forward.y(), forward.x());
        var midpoint = formation.center.plus(enemy.center).div(2);
        var lateralOffset = Math.clamp(0.45 + lateralCommitment * 1.35 +
                planRandom.nextDouble() * 0.18, 0.45, 1.30);
        var rawMeeting = midpoint.plus(lateral.times(lateralSign * lateralOffset));
        var meeting = new Pos(Math.clamp(rawMeeting.x(), 0.75, ARENA_WIDTH - 0.75),
                Math.clamp(rawMeeting.y(), 0.75, ARENA_HEIGHT - 0.75));
        var plan = new Group3ApproachPlan(team, pattern, simulationTime, minimum, preferred, maximum,
                leadRole, FormationRole3.CENTER, trailingRole, lateralSign, forwardCommitment,
                lateralCommitment, depthCommitment, formation.center, meeting,
                group.contactBand == Group3ContactBand.FAR_APPROACH ?
                        "seeded_far_entry_geometry" : "seeded_space_contest_geometry");
        group.approachPlan(team, plan);
        var observation = group.approachObservation;
        var previousLead = observation.lastLeadRole(team);
        if (previousLead != null && previousLead != leadRole) ++group3Metrics.leadRoleChanges;
        observation.lastLeadRole(team, leadRole);
        ++group3Metrics.approachPlansStarted;
        group3Metrics.approachPlansByPattern.merge(pattern, 1, Integer::sum);
        logTactical("GROUP3_APPROACH_PLAN", "team=" + team + " pattern=" + pattern +
                " lead=" + leadRole + " lateralSign=" + lateralSign +
                " meeting=" + meeting + " minimum=%.2f preferred=%.2f maximum=%.2f".
                        formatted(minimum, preferred, maximum));
    }

    private void finishGroup3ApproachPlan(Group3v3Engagement group, Team team,
                                          boolean interrupted, String reason) {
        var plan = group.approachPlan(team);
        if (plan == null) return;
        group3Metrics.approachPlanDurationSum += plan.age(simulationTime);
        if (interrupted) ++group3Metrics.approachPlanInterruptions;
        group.approachPlan(team, null);
        logTactical("GROUP3_APPROACH_FINISHED", "team=" + team + " pattern=" + plan.pattern +
                " interrupted=" + interrupted + " reason=" + reason +
                " duration=%.2f".formatted(plan.age(simulationTime)));
    }

    private void updateGroup3CoverNetworks(Group3v3Engagement group) {
        updateGroup3CoverNetwork(group, Team.BLUE);
        updateGroup3CoverNetwork(group, Team.RED);
    }

    private void updateGroup3CoverNetwork(Group3v3Engagement group, Team team) {
        var network = group.coverNetwork(team);
        var previous = new HashSet<>(network.viableRelations);
        var evaluated = new ArrayList<CoverLink>(18);
        var members = group.members(team);
        var enemies = group.members(team == Team.BLUE ? Team.RED : Team.BLUE);
        for (var protector : members)
            for (var ally : members) {
                if (ally == protector) continue;
                for (var enemy : enemies) {
                    if (!isActiveForDeadAgentExperiment(protector, DeadAgentParticipationDomain.COVER) ||
                            !isActiveForDeadAgentExperiment(ally, DeadAgentParticipationDomain.COVER) ||
                            !isActiveForDeadAgentExperiment(enemy, DeadAgentParticipationDomain.COVER)) continue;
                    if (!protector.alive && protector.deadAgentAudit.active())
                        ++protector.deadAgentAudit.coverNetworkParticipations;
                    if (!ally.alive && ally.deadAgentAudit.active())
                        ++ally.deadAgentAudit.coverNetworkParticipations;
                    if (!enemy.alive && enemy.deadAgentAudit.active())
                        ++enemy.deadAgentAudit.coverNetworkParticipations;
                    evaluated.add(evaluateGroup3CoverLink(protector, ally, enemy, members));
                }
            }
        network.rebuild(evaluated, enemies);
        for (var relation : network.viableRelations)
            if (!previous.contains(relation)) ++group3Metrics.coverLinksCreated;
        for (var relation : previous)
            if (!network.viableRelations.contains(relation)) {
                ++group3Metrics.coverLinksBroken;
                ++group3Metrics.coverageBreaks;
            }
        group3Metrics.activeCoverLinksSum += network.activeLinks();
        group3Metrics.coverConnectivitySum += network.connectivity;
        group3Metrics.weakestCoverageSum += network.weakestMemberCoverage;
        for (var member : members) {
            if (!member.alive) continue;
            if (network.coverageOf(member) < 0.12) ++group3Metrics.membersWithoutCover;
            var active = 0;
            for (var link : network.outgoingLinks.get(member))
                if (link.viable()) ++active;
            if (group.formation(team).roleOf(member) == FormationRole3.CENTER)
                group3Metrics.centerCoverLinks += active;
            else group3Metrics.wingCoverLinks += active;
        }
        ++group3Metrics.coverSamples;
    }

    private void updateGroup3Clusters(Group3v3Engagement group) {
        if (simulationTime < group.nextClusterEvaluationAt) return;
        group.nextClusterEvaluationAt = simulationTime + 0.15;
        group.localClusters.clear();
        var living = new ArrayList<Musketeer>();
        for (var agent : agents)
            if (agent.alive) living.add(agent);
        var unvisited = new HashSet<>(living);
        while (!unvisited.isEmpty()) {
            var component = new ArrayList<Musketeer>();
            var queue = new ArrayDeque<Musketeer>();
            var first = unvisited.iterator().next();
            unvisited.remove(first);
            queue.add(first);
            while (!queue.isEmpty()) {
                var current = queue.removeFirst();
                component.add(current);
                var neighbors = new ArrayList<Musketeer>();
                for (var candidate : unvisited)
                    if (candidate.team != current.team && group3ClusterEdge(current, candidate))
                        neighbors.add(candidate);
                for (var neighbor : neighbors) {
                    unvisited.remove(neighbor);
                    queue.addLast(neighbor);
                }
            }
            var blue = new ArrayList<Musketeer>();
            var red = new ArrayList<Musketeer>();
            for (var member : component)
                (member.team == Team.BLUE ? blue : red).add(member);
            if (blue.isEmpty() || red.isEmpty()) continue;
            var center = new Pos();
            for (var member : component) center = center.plus(member.pos);
            center = center.div(component.size());
            var radius = 0.0;
            var minimumEta = Double.POSITIVE_INFINITY;
            for (var member : component) {
                radius = Math.max(radius, member.pos.minus(center).hypotenuse());
                for (var enemy : component)
                    if (enemy.team != member.team)
                        minimumEta = Math.min(minimumEta, attackThreatEta(member, enemy));
            }
            var overloaded = blue.size() == 1 && red.size() >= 2 ? blue.getFirst() :
                    red.size() == 1 && blue.size() >= 2 ? red.getFirst() : null;
            var isolation = overloaded == null ? 0 : group3IsolationConfidence(group, overloaded,
                    component, minimumEta);
            var previousIsolation = overloaded == null ? 0 :
                    group.isolationTime.getOrDefault(overloaded, 0.0);
            var currentIsolation = isolation >= 0.66 ? previousIsolation + 0.15 : 0;
            if (overloaded != null) group.isolationTime.put(overloaded, currentIsolation);
            if (previousIsolation < 0.35 && currentIsolation >= 0.35)
                ++group3Metrics.durableIsolations;
            var contested = overloaded != null && isolation < 0.66;
            var reason = overloaded == null ? "shared_interaction_lines" :
                    contested ? "temporary_two_vs_one_contested_by_support_eta" :
                            currentIsolation >= 0.35 ? "durable_isolation" :
                                    "temporary_two_vs_one_under_observation";
            group.localClusters.add(new LocalEngagementCluster(blue, red, center, radius,
                    minimumEta, isolation, contested, reason));
            ++group3Metrics.clustersDetected;
            if (overloaded != null) ++group3Metrics.temporaryTwoVsOneClusters;
            if (contested) ++group3Metrics.contestedClusters;
        }
    }

    private boolean group3ClusterEdge(Musketeer first, Musketeer second) {
        var distance = first.pos.minus(second.pos).hypotenuse();
        return distance <= 2.75 || Math.min(attackThreatEta(first, second),
                attackThreatEta(second, first)) <= 0.85;
    }

    private double group3IsolationConfidence(Group3v3Engagement group, Musketeer isolated,
                                              List<Musketeer> component, double enemyImpactEta) {
        var localEnemies = 0;
        for (var member : component)
            if (member.team != isolated.team) ++localEnemies;
        var supportEta = Double.POSITIVE_INFINITY;
        for (var ally : group.members(isolated.team)) {
            if (ally == isolated || !ally.alive) continue;
            var distance = ally.pos.minus(isolated.pos).hypotenuse();
            supportEta = Math.min(supportEta, Math.max(0, distance - 1.45) /
                    estimatedMovementSpeed(ally));
        }
        var coverage = group.coverNetwork(isolated.team).coverageOf(isolated);
        var numerical = Math.clamp((localEnemies - 1) / 2.0, 0, 1);
        var supportDelay = Double.isFinite(supportEta) ?
                Math.clamp((supportEta - enemyImpactEta + 0.18) / 1.15, 0, 1) : 1;
        return Math.clamp(numerical * 0.34 + (1 - coverage) * 0.34 +
                supportDelay * 0.32, 0, 1);
    }

    private LocalEngagementCluster group3ClusterContaining(Musketeer first, Musketeer second) {
        var group = activeGroup3v3();
        if (group == null) return null;
        for (var cluster : group.localClusters) {
            var firstPresent = cluster.blueMembers().contains(first) ||
                    cluster.redMembers().contains(first);
            var secondPresent = second == null || cluster.blueMembers().contains(second) ||
                    cluster.redMembers().contains(second);
            if (firstPresent && secondPresent) return cluster;
        }
        return null;
    }

    private CoverLink evaluateGroup3CoverLink(Musketeer protector, Musketeer ally,
                                               Musketeer enemy, Musketeer[] teammates) {
        if (!protector.alive || !ally.alive || !enemy.alive)
            return unavailableGroup3CoverLink(protector, ally, enemy, "dead_participant");
        if (protector.state == CombatState.RECOVERY || protector.state == CombatState.BLOCK_STUN)
            return unavailableGroup3CoverLink(protector, ally, enemy, "protector_unavailable");
        var awareness = protector.opponentAwareness.get(enemy);
        if (awareness == null || !awareness.visible && awareness.memoryTime <= 0)
            return unavailableGroup3CoverLink(protector, ally, enemy, "enemy_untracked");
        var perceivedEnemy = awareness.lastKnown;
        var confidence = Math.clamp(awareness.detailConfidence * 0.72 +
                protector.attentionAllocation.weightOf(enemy) * 0.46, 0.05, 1);
        var protectorOffset = perceivedEnemy.minus(protector.pos);
        var protectorDistance = protectorOffset.hypotenuse();
        var allyGap = ally.pos.minus(protector.pos).hypotenuse();
        var alignEta = Math.abs(angleDifference(protector.weaponWorldAngle(), protectorOffset.radians())) /
                estimatedTurnSpeed(protector);
        var prepareEta = protector.state == CombatState.ATTACK_HOLD ? 0 :
                protector.state == CombatState.ATTACK_PREPARE ?
                        Math.max(0, protector.prepareTime() - protector.stateTime) :
                        protector.attackDirection.prepareTime;
        var contactEta = Math.max(0, protectorDistance - protector.projectedThrustLength() -
                Musketeer.BODY_RADIUS) / estimatedMovementSpeed(protector);
        var punishEta = alignEta + prepareEta + contactEta;
        var enemyOffset = ally.pos.minus(perceivedEnemy);
        var enemyDistance = enemyOffset.hypotenuse();
        var enemyAlign = Math.abs(angleDifference(awareness.observedWeaponAngle, enemyOffset.radians())) /
                estimatedTurnSpeed(enemy);
        var enemyPrepare = awareness.stateDetailFresh &&
                (awareness.observedState == CombatState.ATTACK_HOLD ||
                        awareness.observedState == CombatState.ATTACK_ACTIVE) ? 0 :
                enemy.attackDirection.prepareTime;
        var enemyContact = Math.max(0, enemyDistance - awareness.observedProjectedRange -
                Musketeer.BODY_RADIUS) / estimatedMovementSpeed(enemy);
        var threatEta = enemyAlign + enemyPrepare + enemyContact;
        var clearance = Double.POSITIVE_INFINITY;
        for (var teammate : teammates)
            if (teammate != protector) clearance = Math.min(clearance,
                    distanceToSegment(teammate.pos, protector.pos, perceivedEnemy));
        var lineSafety = Math.clamp((clearance - Musketeer.BODY_RADIUS * 1.08) / 0.78, 0, 1);
        var timing = Math.clamp((threatEta + 0.42 - punishEta) / 1.20, 0, 1);
        var proximity = Math.clamp((3.25 - protectorDistance) / 2.25, 0, 1);
        var supportReach = Math.clamp((2.85 - allyGap) / 1.65, 0, 1);
        var quality = Math.clamp(timing * 0.38 + lineSafety * 0.23 + confidence * 0.20 +
                proximity * 0.07 + supportReach * 0.12 + (protector.stats.teamworkAbility() +
                protector.stats.decisionMaking()) / 200.0 * 0.08, 0, 1);
        var rejection = confidence < 0.15 ? "perception_too_uncertain" :
                lineSafety < 0.12 ? "line_blocked" :
                        allyGap > 2.85 ? "ally_too_far" :
                                punishEta > threatEta + 0.42 ? "punish_eta_too_late" :
                                quality < 0.34 ? "quality_too_low" : "NONE";
        return new CoverLink(protector, ally, enemy, rejection.equals("NONE"), alignEta,
                prepareEta, contactEta, punishEta, lineSafety, confidence, quality, rejection);
    }

    private CoverLink unavailableGroup3CoverLink(Musketeer protector, Musketeer ally,
                                                  Musketeer enemy, String reason) {
        return new CoverLink(protector, ally, enemy, false, Double.POSITIVE_INFINITY,
                Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY,
                0, 0, 0, reason);
    }

    private void updateGroup3AttackPlan(Group3v3Engagement group, Team team) {
        if (group.exchangeActive()) return;
        if (simulationTime < group.nextAttackPlanAt(team)) return;
        var members = group.members(team);
        var activeAttackers = new ArrayList<Musketeer>();
        for (var member : members)
            if (member.alive && (member.state == CombatState.ATTACK_PREPARE ||
                    member.state == CombatState.ATTACK_HOLD || member.isAttacking()))
                activeAttackers.add(member);
        var maximumAttackers = group.living(team) >= 3 ? 2 : 1;
        var availableSlots = maximumAttackers - activeAttackers.size();
        if (availableSlots <= 0) return;
        var opportunities = new ArrayList<AttackOpportunity>();
        for (var attacker : members) {
            if (!attacker.alive || attacker.state != CombatState.NEUTRAL) continue;
            AttackOpportunity best = null;
            for (var target : new Musketeer[]{attacker.attentionAllocation.primaryThreat,
                    attacker.attentionAllocation.secondaryThreat}) {
                if (target == null || !target.alive) continue;
                var opportunity = evaluateGroup3AttackOpportunity(attacker, target);
                ++group3Metrics.attackOpportunitiesEvaluated;
                if (opportunity.viable() && (best == null || opportunity.score() > best.score())) best = opportunity;
            }
            if (best != null) opportunities.add(best);
        }
        opportunities.sort((first, second) -> Double.compare(second.score(), first.score()));
        if (opportunities.isEmpty()) {
            group.attackPlan(team, TeamAttackPlan.hold(team, List.of(members),
                    "no_viable_perceived_opening", simulationTime));
            group.nextAttackPlanAt(team, simulationTime + 0.14);
            return;
        }
        var selected = new ArrayList<AttackOpportunity>(availableSlots);
        for (var candidate : opportunities) {
            var compatible = true;
            for (var existing : activeAttackers) {
                var existingOpportunity = existing.attackOpportunity;
                if (existingOpportunity != null && (!friendlyAttackLinesSafe(existingOpportunity, candidate) ||
                        existingOpportunity.target() == candidate.target() &&
                                group.living(candidate.target().team) > 1)) {
                    compatible = false;
                    break;
                }
            }
            if (!compatible) {
                ++group3Metrics.friendlyLineRejections;
                continue;
            }
            for (var accepted : selected)
                if (!friendlyAttackLinesSafe(accepted, candidate) || accepted.target() == candidate.target() &&
                        group.living(candidate.target().team) > 1) {
                    compatible = false;
                    break;
                }
            if (!compatible) {
                ++group3Metrics.friendlyLineRejections;
                continue;
            }
            var committed = new ArrayList<Musketeer>(activeAttackers);
            for (var accepted : selected) committed.add(accepted.attacker());
            committed.add(candidate.attacker());
            if (!hasRemainingGroup3Coverer(group, team, committed)) {
                ++group3Metrics.allCoverageAbandonedRejections;
                continue;
            }
            selected.add(candidate);
            if (selected.size() >= availableSlots) break;
        }
        if (selected.isEmpty()) {
            group.nextAttackPlanAt(team, simulationTime + 0.14);
            return;
        }
        if (activeAttackers.size() + opportunities.size() >= 3)
            ++group3Metrics.tripleAttackRejections;
        var selectedAttackers = new ArrayList<Musketeer>(selected.size());
        var coveringMembers = new ArrayList<Musketeer>();
        var lineSafety = 1.0;
        var recoveryRisk = 0.0;
        for (var opportunity : selected) {
            selectedAttackers.add(opportunity.attacker());
            lineSafety = Math.min(lineSafety, opportunity.lineSafety());
            recoveryRisk += opportunity.revengeRisk();
        }
        for (var member : members)
            if (member.alive && !activeAttackers.contains(member) &&
                    !selectedAttackers.contains(member)) coveringMembers.add(member);
        var plan = new TeamAttackPlan(team, selectedAttackers, selected, coveringMembers,
                lineSafety, recoveryRisk, selected.size() == 2 ?
                "two_distinct_lines_with_coverer" : "single_best_opening", simulationTime);
        group.attackPlan(team, plan);
        if (selected.size() == 1) ++group3Metrics.singleAttackerPlans;
        else ++group3Metrics.doubleAttackerPlans;
        for (var opportunity : selected) commitGroup3Attack(opportunity);
        var aggression = 0.0;
        for (var attacker : selectedAttackers) aggression += attacker.stats.aggression();
        aggression /= selectedAttackers.size();
        group.nextAttackPlanAt(team, simulationTime + 0.78 - aggression * 0.0028 +
                random.nextDouble() * 0.18);
        logTactical("TEAM_ATTACK_PLAN_3V3", "team=" + team + " attackers=" +
                selectedAttackers.size() + " covering=" + coveringMembers.size() +
                " reason=" + plan.reason());
    }

    private boolean hasRemainingGroup3Coverer(Group3v3Engagement group, Team team,
                                               List<Musketeer> committed) {
        if (group.living(team) <= 1) return true;
        for (var member : group.members(team)) {
            if (!member.alive || committed.contains(member) || member.state == CombatState.RECOVERY ||
                    member.state == CombatState.BLOCK_STUN) continue;
            for (var link : group.coverNetwork(team).outgoingLinks.get(member))
                if (link.viable()) return true;
        }
        return false;
    }

    AttackOpportunity evaluateGroup3AttackOpportunity(Musketeer attacker, Musketeer target) {
        if (attacker == null || target == null || !attacker.alive || !target.alive)
            return AttackOpportunity.unavailable(attacker, target,
                    AttackOpportunityRejection.NO_IDENTIFIABLE_OPENING);
        var awareness = attacker.opponentAwareness.get(target);
        if (awareness == null || !awareness.visible && awareness.memoryTime <= 0 ||
                awareness.attentionLevel == AttentionLevel.PERIPHERAL)
            return AttackOpportunity.unavailable(attacker, target,
                    AttackOpportunityRejection.LOW_CONFIDENCE_PERCEPTION);
        var perceivedTarget = awareness.lastKnown;
        var offset = perceivedTarget.minus(attacker.pos);
        var distance = offset.hypotenuse();
        var alignment = Math.abs(angleDifference(attacker.weaponWorldAngle(), offset.radians()));
        var alignEta = alignment / estimatedTurnSpeed(attacker);
        var prepareEta = attacker.attackDirection.prepareTime;
        var contactEta = Math.max(0, distance - attacker.projectedThrustLength() -
                Musketeer.BODY_RADIUS) / estimatedMovementSpeed(attacker);
        var impactEta = alignEta + prepareEta + contactEta + 0.04;
        var lineClearance = Double.POSITIVE_INFINITY;
        var group = activeGroup3v3();
        for (var ally : group.members(attacker.team))
            if (ally != attacker) lineClearance = Math.min(lineClearance,
                    distanceToSegment(ally.pos, attacker.pos, perceivedTarget));
        var lineSafety = Math.clamp((lineClearance - Musketeer.BODY_RADIUS * 1.25) / 0.82, 0, 1);
        var targetWeaponError = Math.abs(angleDifference(awareness.observedWeaponAngle,
                attacker.pos.minus(perceivedTarget).radians()));
        var focusedAway = targetWeaponError > Math.toRadians(38);
        var observedState = awareness.stateDetailFresh ? awareness.observedState : CombatState.NEUTRAL;
        var availableWindow = switch (observedState) {
            case RECOVERY -> Math.max(0.18, target.recoveryTime() - awareness.observedStateTime);
            case BLOCK_STUN -> Math.max(0.20, BLOCK_STUN_TIME - awareness.observedStateTime + 0.18);
            case ATTACK_PREPARE, ATTACK_ACTIVE, CHAMBER_PREPARE, CHAMBER_ACTIVE -> 0.54;
            case BLOCK_PREPARE, BLOCKING, DOUBLE_BLOCK -> 0.46;
            default -> focusedAway ? 0.64 : 0.48;
        };
        var opposingCover = summarizeGroup3OpposingCover(attacker, target, impactEta);
        var ownCover = group.coverNetwork(attacker.team).coverageOf(attacker);
        var confidence = awareness.detailConfidence *
                (simulationTime < attacker.attentionAllocation.precisionPenaltyUntil ? 0.72 : 1);
        var cluster = group3ClusterContaining(attacker, target);
        var targetLocalAllies = cluster == null ? 0 : target.team == Team.BLUE ?
                cluster.blueMembers().size() : cluster.redMembers().size();
        var targetLocalEnemies = cluster == null ? 0 : target.team == Team.BLUE ?
                cluster.redMembers().size() : cluster.blueMembers().size();
        var isolatedFromNetwork = cluster != null && !cluster.contested() &&
                cluster.isolationConfidence() >= 0.66 && targetLocalAllies == 1 &&
                targetLocalEnemies >= 2;
        var targetExposure = Math.clamp(availableWindow / 0.72, 0, 1) * 0.52 +
                Math.clamp(targetWeaponError / Math.toRadians(100), 0, 1) * 0.30 +
                (opposingCover.credibleCoverers() == 0 ? 0.18 : 0) +
                (isolatedFromNetwork ? 0.14 : 0);
        var revengeRisk = Math.clamp(opposingCover.combinedRisk() * (1 - ownCover * 0.42), 0, 1);
        var score = targetExposure * 0.31 + lineSafety * 0.21 + ownCover * 0.16 +
                (1 - opposingCover.combinedRisk()) * 0.17 + confidence * 0.15 +
                attacker.stats.aggression() * 0.0011 + attacker.stats.decisionMaking() * 0.0007;
        var threshold = 0.55 - attacker.stats.aggression() * 0.0012;
        var acceptsRisk = attacker.tactic == Tactic.AGGRESSIVE && attacker.stats.aggression() >= 84;
        var rejection = AttackOpportunityRejection.NONE;
        if (distance > attacker.projectedThrustLength() + Musketeer.BODY_RADIUS + 0.32)
            rejection = AttackOpportunityRejection.TARGET_OUT_OF_RANGE;
        else if (lineSafety < 0.14) rejection = AttackOpportunityRejection.LINE_BLOCKED_BY_ALLY;
        else if (confidence < 0.32) rejection = AttackOpportunityRejection.LOW_CONFIDENCE_PERCEPTION;
        else if (alignment > Math.toRadians(48)) rejection = AttackOpportunityRejection.WEAPON_NOT_ALIGNED;
        else if (!acceptsRisk && opposingCover.combinedRisk() > 0.62 && ownCover < 0.48)
            rejection = AttackOpportunityRejection.OPPOSING_COVER_TOO_FAST;
        else if (impactEta > availableWindow + 0.18)
            rejection = AttackOpportunityRejection.TARGET_WINDOW_TOO_SHORT;
        else if (score < threshold) rejection = AttackOpportunityRejection.LOW_DECISION_SCORE;
        var reason = observedState == CombatState.RECOVERY ? AttackOpportunityReason.TARGET_RECOVERING :
                observedState == CombatState.ATTACK_ACTIVE ? AttackOpportunityReason.TARGET_ATTACK_COMMITTED :
                        isolatedFromNetwork ?
                                AttackOpportunityReason.TARGET_ISOLATED_FROM_COVER_NETWORK :
                        focusedAway ? AttackOpportunityReason.TARGET_FOCUSED_ON_ALLY :
                                opposingCover.credibleCoverers() == 0 ?
                                        AttackOpportunityReason.TARGET_LOST_ALLY_COVER :
                                        AttackOpportunityReason.TARGET_LINE_OPEN;
        var opportunity = new AttackOpportunity(rejection == AttackOpportunityRejection.NONE,
                attacker, target, null, opposingCover.fastestCoverer(), reason, availableWindow,
                impactEta, targetExposure, lineSafety, ownCover,
                opposingCover.combinedRisk(), revengeRisk, 0, score, rejection,
                AttackOpportunityEvidence.insufficient());
        group.opposingCoverSummaries.put(opportunity, opposingCover);
        if (cluster != null) {
            group.clusterEvidence.put(opportunity, new ClusterOpportunityEvidence(
                    targetLocalAllies, targetLocalEnemies, cluster.isolationConfidence(),
                    cluster.minimumImpactEta(), cluster.contested(), cluster.reason()));
            if (isolatedFromNetwork) ++group3Metrics.clusterAttackOpportunities;
        }
        return opportunity;
    }

    private OpposingCoverSummary summarizeGroup3OpposingCover(Musketeer attacker, Musketeer target,
                                                               double impactEta) {
        var group = activeGroup3v3();
        if (group == null) return OpposingCoverSummary.none();
        var relevant = new ArrayList<CoverLink>();
        for (var link : group.coverNetwork(target.team).incomingLinks.get(target)) {
            if (!link.viable() || link.threateningEnemy() != attacker) continue;
            var awareness = attacker.opponentAwareness.get(link.protector());
            if (awareness == null || !awareness.visible && awareness.memoryTime <= 0) continue;
            relevant.add(link);
        }
        relevant.sort((first, second) -> Double.compare(first.punishEta(), second.punishEta()));
        if (relevant.isEmpty()) return OpposingCoverSummary.none();
        var fastest = relevant.getFirst();
        var secondEta = relevant.size() > 1 ? relevant.get(1).punishEta() : Double.POSITIVE_INFINITY;
        var firstRisk = Math.clamp((impactEta + attacker.recoveryTime() + 0.16 -
                fastest.punishEta()) / 1.10, 0, 1) * fastest.quality();
        var secondRisk = Double.isFinite(secondEta) ? Math.clamp((impactEta + attacker.recoveryTime() -
                secondEta) / 1.15, 0, 1) * relevant.get(1).quality() * 0.35 : 0;
        return new OpposingCoverSummary(relevant, fastest.protector(), fastest.punishEta(),
                secondEta, relevant.size(), Math.clamp(firstRisk + secondRisk, 0, 1));
    }

    private boolean friendlyAttackLinesSafe(AttackOpportunity first, AttackOpportunity second) {
        var clearance = Math.min(
                distanceToSegment(first.attacker().pos, second.attacker().pos, second.target().pos),
                distanceToSegment(second.attacker().pos, first.attacker().pos, first.target().pos));
        return clearance > Musketeer.BODY_RADIUS * 1.32;
    }

    private Musketeer remainingGroup3Coverer(Musketeer[] members, Musketeer first, Musketeer second) {
        for (var member : members)
            if (member != first && member != second && member.alive) return member;
        return null;
    }

    private void commitGroup3Attack(AttackOpportunity opportunity) {
        var attacker = opportunity.attacker();
        attacker.target = opportunity.target();
        attacker.attackOpportunity = opportunity;
        attacker.groupIntent = GroupIntent.COMMIT_ATTACK;
        beginAttack(attacker, opportunity.target(), opportunity.target().pos.minus(attacker.pos).hypotenuse());
        attacker.attackIntent = AttackIntent.DIRECT_RELEASE;
        attacker.decision = Decision.PREPARE_DIRECT_ATTACK;
        ++group3Metrics.attackCommits;
        if (opportunity.primaryReason() ==
                AttackOpportunityReason.TARGET_ISOLATED_FROM_COVER_NETWORK)
            ++group3Metrics.clusterAttackCommits;
        traceDecision(attacker, DecisionCategory.ATTACK, "GROUP3_STAB",
                opportunity.primaryReason().name(), opportunity.target(), null,
                opportunity.score(), opportunity.availableWindow(), opportunity.impactEta(),
                opportunity.revengeRisk(), "team_attack_plan");
    }

    private void createGroup3RevengeOpportunity(Group3v3Engagement group, Musketeer attacker,
                                                 Musketeer protectedAlly, RevengeReason reason) {
        if (group.exchangeActive() || attacker == null || protectedAlly == null ||
                !attacker.alive || !protectedAlly.alive) {
            if (group.exchangeActive()) ++group3Metrics.preventedRevengeChains;
            return;
        }
        TeamRevengeCandidate selected = null;
        for (var revenger : group.members(protectedAlly.team)) {
            if (revenger == protectedAlly || !revenger.alive) continue;
            var candidate = assessGroup3RevengeCandidate(group, revenger, attacker,
                    protectedAlly, reason);
            ++group3Metrics.revengeCandidatesEvaluated;
            if (!candidate.opportunity().viable()) {
                ++group3Metrics.revengeRejected;
                continue;
            }
            if (selected == null || candidate.opportunity().score() > selected.opportunity().score())
                selected = candidate;
        }
        ++group3Metrics.revengeOpportunities;
        if (selected == null) {
            logTactical("GROUP3_REVENGE_REJECTED", "attacker=" +
                    attacker.name(UiLanguage.FRENCH) + " protected=" +
                    protectedAlly.name(UiLanguage.FRENCH) + " reason=no_viable_candidate");
            return;
        }
        group.revengeCandidate = selected;
        group.exchangeStartedAt = simulationTime;
        var opportunity = selected.opportunity();
        var revenger = selected.revenger();
        revenger.revengeOpportunity = opportunity;
        revenger.attackOpportunity = null;
        revenger.groupIntent = GroupIntent.COMMIT_REVENGE;
        revenger.target = attacker;
        revenger.plannedTarget = attacker;
        protectedAlly.groupIntent = GroupIntent.COVER;
        attacker.groupIntent = GroupIntent.WAIT;
        beginAttack(revenger, attacker, attacker.pos.minus(revenger.pos).hypotenuse());
        revenger.attackIntent = AttackIntent.DIRECT_RELEASE;
        revenger.decision = Decision.RIPOSTE;
        ++group3Metrics.revengeCommits;
        logTactical("GROUP3_REVENGE_SELECTED", "revenger=" + revenger.name(UiLanguage.FRENCH) +
                " target=" + attacker.name(UiLanguage.FRENCH) + " reason=" + reason +
                " preservedLinks=" + selected.coverLinksPreserved() + " counterRisk=" +
                "%.2f".formatted(selected.counterCoverRisk()) + " score=" +
                "%.2f".formatted(opportunity.score()));
        traceDecision(revenger, DecisionCategory.REVENGE, "COMMIT_REVENGE", reason.name(),
                attacker, null, opportunity.score(), opportunity.vulnerabilityRemaining(),
                opportunity.timeToAlign() + opportunity.timeToPrepare() + opportunity.timeToContact(),
                selected.counterCoverRisk(), "other_team_candidates_hold_cover");
        group.counterCoverAssessment = selectGroup3CounterCover(group, revenger, attacker,
                opportunity.timeToAlign() + opportunity.timeToPrepare() + opportunity.timeToContact());
        var counter = group.counterCoverAssessment;
        if (counter == null || !counter.viable()) return;
        var counterCoverer = counter.counterCoverer();
        counterCoverer.counterCoverAssessment = counter;
        counterCoverer.attackOpportunity = null;
        counterCoverer.groupIntent = GroupIntent.COUNTER_COVER;
        counterCoverer.target = revenger;
        counterCoverer.plannedTarget = revenger;
        beginAttack(counterCoverer, revenger, revenger.pos.minus(counterCoverer.pos).hypotenuse());
        counterCoverer.attackIntent = AttackIntent.HOLD;
        counterCoverer.decision = Decision.PREPARE_HOLD_ATTACK;
        ++group3Metrics.counterCoverCommits;
        logTactical("GROUP3_COUNTER_COVER_SELECTED", "coverer=" +
                counterCoverer.name(UiLanguage.FRENCH) + " protected=" +
                attacker.name(UiLanguage.FRENCH) + " target=" +
                revenger.name(UiLanguage.FRENCH) + " threatEta=" +
                "%.2f".formatted(counter.threatEta()) + " score=" +
                "%.2f".formatted(counter.score()));
        traceDecision(counterCoverer, DecisionCategory.COUNTER_COVER, "ESTABLISH_COUNTER_COVER",
                "protect_vulnerable_attacker", revenger, null, counter.score(),
                opportunity.vulnerabilityRemaining(), counter.threatEta(), 0,
                "other_counter_candidates_keep_cover");
    }

    private TeamRevengeCandidate assessGroup3RevengeCandidate(Group3v3Engagement group,
                                                               Musketeer revenger,
                                                               Musketeer attacker,
                                                               Musketeer protectedAlly,
                                                               RevengeReason reason) {
        var awareness = revenger.opponentAwareness.get(attacker);
        var unavailable = revenger.state == CombatState.RECOVERY ||
                revenger.state == CombatState.BLOCK_STUN || revenger.isAttacking() ||
                awareness == null || awareness.attentionLevel == AttentionLevel.PERIPHERAL ||
                awareness.detailConfidence < 0.24;
        var vulnerability = attacker.state == CombatState.BLOCK_STUN ?
                Math.max(0, BLOCK_STUN_TIME - attacker.stateTime) + attacker.recoveryTime() :
                attacker.state == CombatState.RECOVERY ?
                        Math.max(0, attacker.recoveryTime() - attacker.stateTime) :
                        attacker.recoveryTime();
        var perceivedTarget = awareness == null ? attacker.pos : awareness.lastKnown;
        var distance = perceivedTarget.minus(revenger.pos).hypotenuse();
        var timeToAlign = Math.abs(angleDifference(revenger.weaponWorldAngle(),
                perceivedTarget.minus(revenger.pos).radians())) / estimatedTurnSpeed(revenger);
        var timeToPrepare = revenger.state == CombatState.ATTACK_HOLD ? 0 :
                revenger.state == CombatState.ATTACK_PREPARE ?
                        Math.max(0, revenger.prepareTime() - revenger.stateTime) :
                        revenger.attackDirection.prepareTime;
        var timeToContact = Math.max(0, distance - revenger.projectedThrustLength() -
                Musketeer.BODY_RADIUS) / estimatedMovementSpeed(revenger);
        var impactEta = timeToAlign + timeToPrepare + timeToContact;
        var clearance = Double.POSITIVE_INFINITY;
        for (var ally : group.members(revenger.team))
            if (ally != revenger) clearance = Math.min(clearance,
                    distanceToSegment(ally.pos, revenger.pos, perceivedTarget));
        var lineSafety = Math.clamp((clearance - Musketeer.BODY_RADIUS * 1.28) / 0.82, 0, 1);
        var preserved = 0;
        for (var link : group.coverNetwork(revenger.team).links)
            if (link.viable() && link.protector() != revenger) ++preserved;
        var bestCounterEta = Double.POSITIVE_INFINITY;
        Musketeer bestCounter = null;
        for (var candidate : group.members(attacker.team)) {
            if (candidate == attacker || !candidate.alive) continue;
            var counter = assessGroup3CounterCover(candidate, revenger, attacker, impactEta);
            if (counter.threatEta() < bestCounterEta) {
                bestCounterEta = counter.threatEta();
                bestCounter = candidate;
            }
        }
        var counterRisk = Double.isFinite(bestCounterEta) ?
                Math.clamp((impactEta + 0.24 - bestCounterEta) / 0.92, 0, 1) : 0;
        var temporal = Math.clamp((vulnerability - impactEta + 0.18) / 0.88, 0, 1);
        var skill = (revenger.stats.teamworkAbility() + revenger.stats.decisionMaking() +
                revenger.stats.attackTiming()) / 300.0;
        var preservation = Math.clamp(preserved / 4.0, 0, 1);
        var score = Math.clamp(temporal * 0.36 + lineSafety * 0.20 + skill * 0.17 +
                preservation * 0.12 + (1 - counterRisk) * 0.15, 0, 1);
        var rejection = unavailable ? "candidate_unavailable_or_unperceived" :
                distance > 3.15 ? "revenger_too_far" :
                        lineSafety < 0.14 ? "ally_blocks_stab_line" :
                                impactEta >= vulnerability + 0.16 ? "vulnerability_too_short" :
                                        counterRisk > 0.72 && preserved == 0 ?
                                                "counter_cover_too_fast" :
                                                score < 0.43 ? "revenge_score" : "";
        var opportunity = new RevengeOpportunity(rejection.isEmpty(), revenger, attacker,
                protectedAlly, bestCounter, reason, vulnerability, timeToAlign, timeToPrepare,
                timeToContact, bestCounterEta, lineSafety, score, rejection);
        return new TeamRevengeCandidate(revenger, opportunity, preserved, counterRisk);
    }

    private CounterCoverAssessment selectGroup3CounterCover(Group3v3Engagement group,
                                                              Musketeer revenger,
                                                              Musketeer vulnerableAttacker,
                                                              double revengeImpactEta) {
        CounterCoverAssessment selected = null;
        for (var candidate : group.members(vulnerableAttacker.team)) {
            if (candidate == vulnerableAttacker || !candidate.alive) continue;
            var assessment = assessGroup3CounterCover(candidate, revenger,
                    vulnerableAttacker, revengeImpactEta);
            ++group3Metrics.counterCoverCandidatesEvaluated;
            if (!assessment.viable()) continue;
            if (selected == null || assessment.score() > selected.score()) selected = assessment;
        }
        ++group3Metrics.counterCoverOpportunities;
        return selected;
    }

    private CounterCoverAssessment assessGroup3CounterCover(Musketeer coverer, Musketeer revenger,
                                                              Musketeer protectedAlly,
                                                              double revengeImpactEta) {
        var awareness = coverer.opponentAwareness.get(revenger);
        if (awareness == null || awareness.attentionLevel == AttentionLevel.PERIPHERAL ||
                awareness.detailConfidence < 0.22 || coverer.state == CombatState.RECOVERY ||
                coverer.state == CombatState.BLOCK_STUN || coverer.isAttacking())
            return new CounterCoverAssessment(false, coverer, revenger, protectedAlly,
                    Double.POSITIVE_INFINITY, revengeImpactEta, 1, 0,
                    "coverer_unavailable_or_unperceived");
        var perceivedTarget = awareness.lastKnown;
        var distance = perceivedTarget.minus(coverer.pos).hypotenuse();
        var alignEta = Math.abs(angleDifference(coverer.weaponWorldAngle(),
                perceivedTarget.minus(coverer.pos).radians())) / estimatedTurnSpeed(coverer);
        var prepareEta = coverer.attackDirection.prepareTime;
        var contactEta = Math.max(0, distance - coverer.projectedThrustLength() -
                Musketeer.BODY_RADIUS) / estimatedMovementSpeed(coverer);
        var threatEta = alignEta + prepareEta + contactEta;
        var clearance = distanceToSegment(protectedAlly.pos, coverer.pos, perceivedTarget);
        var approachRisk = Math.clamp(1 - clearance / 1.15, 0, 1);
        var skill = (coverer.stats.teamworkAbility() + coverer.stats.decisionMaking()) / 200.0;
        var temporal = Math.clamp((revengeImpactEta - threatEta + 0.38) / 0.92, 0, 1);
        var score = Math.clamp(temporal * 0.50 + skill * 0.27 +
                (1 - approachRisk) * 0.16 + awareness.detailConfidence * 0.07, 0, 1);
        var rejection = threatEta > revengeImpactEta + 0.32 ? "counter_too_late" :
                approachRisk > 0.88 ? "protected_ally_blocks_line" :
                        score < 0.40 ? "counter_score" : "";
        return new CounterCoverAssessment(rejection.isEmpty(), coverer, revenger,
                protectedAlly, threatEta, revengeImpactEta, approachRisk, score, rejection);
    }

    private void updateGroup3Exchange(Group3v3Engagement group) {
        if (!group.exchangeActive()) return;
        var opportunity = group.revengeCandidate.opportunity();
        var revenger = group.revengeCandidate.revenger();
        var vulnerable = opportunity.vulnerableAttacker();
        if (!revenger.alive || !vulnerable.alive || simulationTime - group.exchangeStartedAt > 2.40) {
            if (revenger.alive && vulnerable.alive) ++group3Metrics.revengeCancelled;
            finishGroup3Exchange(group, "exchange_expired_or_participant_lost");
            return;
        }
        var counter = group.counterCoverAssessment;
        if (counter != null && counter.viable()) {
            var coverer = counter.counterCoverer();
            if (coverer.alive && coverer.state == CombatState.ATTACK_HOLD &&
                    (revenger.state == CombatState.ATTACK_ACTIVE ||
                            revenger.state == CombatState.ATTACK_PREPARE)) {
                var offset = revenger.pos.minus(coverer.pos);
                if (offset.hypotenuse() <= coverer.projectedThrustLength() +
                        Musketeer.BODY_RADIUS + 0.24 && Math.abs(angleDifference(
                        coverer.weaponWorldAngle(), offset.radians())) < Math.toRadians(28)) {
                    coverer.attackIntent = AttackIntent.DIRECT_RELEASE;
                    startAttack(coverer, CombatState.ATTACK_ACTIVE);
                }
            }
        }
        if (revenger.state == CombatState.NEUTRAL && simulationTime - group.exchangeStartedAt > 0.30)
            finishGroup3Exchange(group, "revenge_action_finished");
    }

    private void updateGroup3HeldAttacks(Group3v3Engagement group) {
        for (var holder : agents) {
            if (!holder.alive || holder.state != CombatState.ATTACK_HOLD ||
                    holder.groupIntent == GroupIntent.COUNTER_COVER) continue;
            var target = holder.target;
            if (target == null || !target.alive) {
                setState(holder, CombatState.NEUTRAL);
                holder.groupIntent = GroupIntent.MARK;
                continue;
            }
            var offset = target.pos.minus(holder.pos);
            var distance = offset.hypotenuse();
            var aligned = Math.abs(angleDifference(holder.weaponWorldAngle(), offset.radians())) <
                    Math.toRadians(26);
            var withinReach = distance <= holder.projectedThrustLength() +
                    Musketeer.BODY_RADIUS + 0.22;
            var guardMatches = target.isGuarding() &&
                    target.blockDirection.blocks(holder.attackDirection);
            if (withinReach && aligned && !guardMatches) {
                holder.attackIntent = AttackIntent.DIRECT_RELEASE;
                holder.decision = Decision.RELEASE_ATTACK;
                startAttack(holder, CombatState.ATTACK_ACTIVE);
                logTactical("GROUP3_HOLD_RELEASE", "attacker=" +
                        holder.name(UiLanguage.FRENCH) + " target=" +
                        target.name(UiLanguage.FRENCH) + " reason=open_guard_line");
                continue;
            }
            if (!guardMatches || holder.stateTime < 0.34 +
                    (100 - holder.stats.decisionMaking()) * 0.003) {
                holder.decision = aligned ? Decision.HOLD_ATTACK : Decision.REALIGN_HOLD;
                continue;
            }
            Musketeer firstHolder = null;
            for (var teammate : group.members(holder.team))
                if (teammate.alive && teammate.state == CombatState.ATTACK_HOLD &&
                        teammate.target == target) {
                    firstHolder = teammate;
                    break;
                }
            if (firstHolder != null && firstHolder != holder &&
                    firstHolder.attackDirection == holder.attackDirection) {
                holder.attackDirection = holder.attackDirection.opposite();
                configureAim(holder, distance);
                holder.attackPitch = holder.pitch;
                holder.baseAttackPitch = holder.pitch;
                holder.attackIntent = AttackIntent.DIRECT_RELEASE;
                holder.decision = Decision.FEINT_ATTACK;
                setState(holder, CombatState.ATTACK_PREPARE);
                logTactical("GROUP3_HOLD_HEIGHT_CHANGE", "attacker=" +
                        holder.name(UiLanguage.FRENCH) + " target=" +
                        target.name(UiLanguage.FRENCH) + " reason=second_line_against_guard");
            } else holder.decision = aligned ? Decision.HOLD_ATTACK : Decision.REALIGN_HOLD;
        }
    }

    private void finishGroup3Exchange(Group3v3Engagement group, String reason) {
        if (!group.exchangeActive()) return;
        var opportunity = group.revengeCandidate.opportunity();
        var participants = new Musketeer[]{group.revengeCandidate.revenger(),
                opportunity.vulnerableAttacker(), opportunity.protectedAlly(),
                group.counterCoverAssessment == null ? null :
                        group.counterCoverAssessment.counterCoverer()};
        for (var agent : participants) {
            if (agent == null || !agent.alive) continue;
            if ((agent.groupIntent == GroupIntent.COMMIT_REVENGE ||
                    agent.groupIntent == GroupIntent.COUNTER_COVER) &&
                    agent.state == CombatState.ATTACK_ACTIVE) continue;
            if (agent.groupIntent == GroupIntent.COUNTER_COVER &&
                    (agent.state == CombatState.ATTACK_PREPARE ||
                            agent.state == CombatState.ATTACK_HOLD)) setState(agent, CombatState.NEUTRAL);
            agent.groupIntent = GroupIntent.MARK;
            agent.revengeOpportunity = null;
            agent.counterCoverAssessment = null;
        }
        group.revengeCandidate = null;
        group.counterCoverAssessment = null;
        group.nextAttackPlanAt(Team.BLUE, Math.max(group.nextAttackPlanAt(Team.BLUE),
                simulationTime + 0.24));
        group.nextAttackPlanAt(Team.RED, Math.max(group.nextAttackPlanAt(Team.RED),
                simulationTime + 0.24));
        logTactical("GROUP3_EXCHANGE_FINISHED", "reason=" + reason);
    }

    private void clearGroup3SecondaryAttack(Musketeer attacker) {
        attacker.groupIntent = GroupIntent.MARK;
        attacker.revengeOpportunity = null;
        attacker.counterCoverAssessment = null;
    }

    private void updateGroup3Defense(Musketeer defender) {
        if (!defender.alive) return;
        Musketeer threat = null;
        var bestEta = Double.POSITIVE_INFINITY;
        for (var opponent : opponentsOf(defender)) {
            if (!opponent.alive) continue;
            var awareness = defender.opponentAwareness.get(opponent);
            if (awareness == null) continue;
            var observedAttack = awareness.motionAlert || awareness.stateDetailFresh &&
                    (awareness.observedState == CombatState.ATTACK_PREPARE ||
                            awareness.observedState == CombatState.ATTACK_ACTIVE);
            if (!observedAttack) continue;
            var perceivedOffset = awareness.lastKnown.minus(defender.pos);
            var weaponError = Math.abs(angleDifference(awareness.observedWeaponAngle,
                    defender.pos.minus(awareness.lastKnown).radians()));
            if (weaponError > Math.toRadians(42)) continue;
            var eta = Math.max(0, perceivedOffset.hypotenuse() - awareness.observedProjectedRange -
                    Musketeer.BODY_RADIUS) / 1.35;
            if (eta >= bestEta) continue;
            bestEta = eta;
            threat = opponent;
        }
        if (threat == null) return;
        var awareness = defender.opponentAwareness.get(threat);
        if (defender.group3DefensiveThreat != threat ||
                defender.group3ObservedThreatSequence != threat.attackSequence) {
            defender.group3DefensiveThreat = threat;
            defender.group3ObservedThreatSequence = threat.attackSequence;
            var baseDelay = 0.36 - defender.stats.reaction() * 0.0024;
            var attentionDelay = switch (awareness.attentionLevel) {
                case PRIMARY -> 0.0;
                case SECONDARY -> 0.09;
                case PERIPHERAL -> 0.22;
                case NONE -> 0.30;
            };
            if (!awareness.stateDetailFresh) attentionDelay += 0.10;
            defender.group3DefenseReadyAt = simulationTime + Math.max(0.07, baseDelay + attentionDelay);
            traceDecision(defender, DecisionCategory.TARGET_ATTENTION, "THREAT_DETECTED",
                    awareness.attentionLevel.name(), threat, null, awareness.detailConfidence,
                    0, bestEta, 0, "block_pending");
        }
        if (simulationTime < defender.group3DefenseReadyAt || defender.isGuarding() ||
                defender.state != CombatState.NEUTRAL) return;
        beginObservedBlock(defender, threat);
    }

    private void updateGroup3Maneuver(Group3v3Engagement group, Team team) {
        var current = group.maneuver(team);
        if (current != null && current.age(simulationTime) < current.preferredDuration) return;
        var formation = group.formation(team);
        var enemy = group.enemyFormation(team);
        var coverNetwork = group.coverNetwork(team);
        var centerDistance = enemy.center.minus(formation.center).hypotenuse();
        var distantApproach = group3ApproachSteeringEnabled &&
                group.contactBand == Group3ContactBand.FAR_APPROACH;
        var spaceContest = group3ApproachSteeringEnabled &&
                group.contactBand == Group3ContactBand.SPACE_CONTEST;
        TeamManeuverType type;
        var clearance = Math.min(arenaClearance(formation.leftWing.pos),
                arenaClearance(formation.rightWing.pos));
        if (formation.cohesion < (distantApproach ? 0.25 : spaceContest ? 0.36 : 0.48) ||
                coverNetwork.weakestMemberCoverage < (distantApproach ? 0.035 : spaceContest ? 0.06 : 0.10))
            type = TeamManeuverType.RECOVER_FORMATION;
        else if (coverNetwork.connectivity < (distantApproach ? 0.05 : spaceContest ? 0.09 : 0.16))
            type = TeamManeuverType.COMPRESS;
        else if (formation.width > 3.25) type = TeamManeuverType.COMPRESS;
        else if (formation.width < 2.25 && centerDistance < 3.55) type = TeamManeuverType.EXPAND;
        else if (clearance < 0.62) type = random.nextBoolean() ?
                TeamManeuverType.SHIFT_LEFT : TeamManeuverType.SHIFT_RIGHT;
        else if (centerDistance > 3.45) type = TeamManeuverType.PRESS_FORWARD;
        else {
            var roll = random.nextDouble();
            type = roll < 0.38 ? TeamManeuverType.ROTATE_LEFT :
                    roll < 0.76 ? TeamManeuverType.ROTATE_RIGHT :
                            roll < 0.88 ? TeamManeuverType.HOLD_LINE :
                                    roll < 0.95 ? TeamManeuverType.GIVE_GROUND :
                                            TeamManeuverType.CROSS_COVER;
        }
        startGroup3Maneuver(group, team, type, "three_player_geometry");
    }

    private void startGroup3Maneuver(Group3v3Engagement group, Team team,
                                     TeamManeuverType type, String reason) {
        var formation = group.formation(team);
        var members = group.members(team);
        var quality = 0.0;
        var participants = 0;
        for (var member : members)
            if (isActiveForDeadAgentExperiment(member, DeadAgentParticipationDomain.TEAM_MANEUVER)) {
                quality += member.stats.decisionMaking() + member.stats.teamworkAbility();
                ++participants;
            }
        quality /= Math.max(1, participants * 200.0);
        var minimum = 0.72 + quality * 0.38;
        var preferred = 1.45 + quality * 1.05;
        var maximum = 3.10 + quality * 0.90;
        var direction = switch (type) {
            case ROTATE_LEFT, SHIFT_LEFT -> 1;
            case ROTATE_RIGHT, SHIFT_RIGHT -> -1;
            default -> random.nextBoolean() ? 1 : -1;
        };
        var outer = direction > 0 ? formation.rightWing : formation.leftWing;
        var inner = outer == formation.rightWing ? formation.leftWing : formation.rightWing;
        var maneuver = new TeamManeuver(team, type, simulationTime, minimum, preferred, maximum,
                formation.center, group.enemyFormation(team).center, direction, inner, outer,
                formation.cohesion, reason);
        group.maneuver(team, maneuver);
        ++group3Metrics.teamManeuversStarted;
        group3Metrics.maneuversByType.merge(type, 1, Integer::sum);
        logTactical("TEAM_MANEUVER_3V3", "team=" + team + " type=" + type +
                " left=" + formation.leftWing.name(UiLanguage.FRENCH) +
                " center=" + formation.centerPlayer.name(UiLanguage.FRENCH) +
                " right=" + formation.rightWing.name(UiLanguage.FRENCH));
    }

    void forceGroup3Maneuver(Team team, TeamManeuverType type) {
        var group = activeGroup3v3();
        if (group == null) throw new IllegalStateException("No active GROUP_3V3.");
        startGroup3Maneuver(group, team, type, "controlled_test");
    }

    private void moveExperimental3v3Agent(Musketeer self, Group3v3Engagement group, double dt) {
        if (!self.alive && self.deadAgentAudit.active()) ++self.deadAgentAudit.movementUpdates;
        var formation = group.formation(self.team);
        var maneuver = group.maneuver(self.team);
        var committedTarget = self.target != null && self.target.alive &&
                (self.state == CombatState.ATTACK_PREPARE || self.state == CombatState.ATTACK_HOLD ||
                        self.isAttacking() || self.state == CombatState.RECOVERY) ? self.target : null;
        var defensiveTarget = self.isGuarding() && self.group3DefensiveThreat != null &&
                self.group3DefensiveThreat.alive ? self.group3DefensiveThreat : null;
        var opponent = committedTarget != null ? committedTarget : defensiveTarget != null ? defensiveTarget :
                self.attentionAllocation.primaryThreat != null && self.attentionAllocation.primaryThreat.alive ?
                        self.attentionAllocation.primaryThreat : closestAliveOpponent(self);
        var plan = group.approachPlan(self.team);
        var approachActive = group3ApproachSteeringEnabled && plan != null &&
                (group.contactBand == Group3ContactBand.FAR_APPROACH ||
                group.contactBand == Group3ContactBand.SPACE_CONTEST);
        var approach = approachActive ? group3ApproachVelocity(self, formation, plan,
                group.contactBand) : new Pos();
        var maneuverWeight = !approachActive ? 1.0 : switch (group.contactBand) {
            case FAR_APPROACH -> 0.18;
            case SPACE_CONTEST -> 0.42;
            case CONTACT, ACTIVE_EXCHANGE -> 1.0;
        };
        var formationWeight = !approachActive ? 1.0 : switch (group.contactBand) {
            case FAR_APPROACH -> 0.32;
            case SPACE_CONTEST -> 0.65;
            case CONTACT, ACTIVE_EXCHANGE -> 1.0;
        };
        var coverWeight = !approachActive ? 1.0 : switch (group.contactBand) {
            case FAR_APPROACH -> 0.28;
            case SPACE_CONTEST -> 0.62;
            case CONTACT, ACTIVE_EXCHANGE -> 1.0;
        };
        var localWeight = !approachActive ? 1.0 : switch (group.contactBand) {
            case FAR_APPROACH -> 0.55;
            case SPACE_CONTEST -> 0.82;
            case CONTACT, ACTIVE_EXCHANGE -> 1.0;
        };
        var teamManeuver = group3ManeuverVelocity(self, formation, maneuver).times(maneuverWeight);
        var formationSteering = group3FormationSteering(self, group,
                approachActive ? plan : null).times(formationWeight);
        var coverSteering = group3CoverSteering(self, group);
        if (maneuver != null && maneuver.rotating()) coverSteering = coverSteering.times(0.18);
        coverSteering = coverSteering.times(coverWeight);
        var localManeuver = group3LocalOpponentSteering(self, opponent).times(localWeight);
        var avoidance = (group3ApproachSteeringEnabled ? group3AllyAvoidance(self, group) : new Pos()).
                times(approachActive &&
                group.contactBand == Group3ContactBand.FAR_APPROACH ?
                0.72 : 1.0);
        var composition = new Group3SteeringComposition(approach, teamManeuver, localManeuver,
                formationSteering, coverSteering, avoidance, group3BoundarySteering(self));
        self.lastGroup3Steering = new SteeringContributionSnapshot(
                composition.approachContribution(), composition.teamManeuverContribution(),
                composition.localManeuverContribution(), composition.formationContribution(),
                composition.coverContribution(), composition.avoidanceContribution(),
                composition.boundaryContribution());
        var desired = composition.desiredVelocity();
        measureGroup3Steering(composition);
        if (opponent != null) {
            var offset = opponent.pos.minus(self.pos);
            var distance = offset.hypotenuse();
            if (distance > 0.0001) {
                if (!self.alive && self.deadAgentAudit.active()) {
                    ++self.deadAgentAudit.bodyRotationUpdates;
                    ++self.deadAgentAudit.weaponRotationUpdates;
                }
                var desiredAngle = offset.radians();
                var maximumTurn = (2.2 + self.stats.footwork() * 0.014) * dt;
                self.angle += Math.clamp(angleDifference(self.angle, desiredAngle), -maximumTurn, maximumTurn);
                self.desiredWeaponYaw = 0;
                self.weaponYaw += Math.clamp(-self.weaponYaw, -maximumTurn, maximumTurn);
                self.target = opponent;
            }
        }
        if (self.state == CombatState.RECOVERY && opponent != null) {
            var away = self.pos.minus(opponent.pos);
            if (away.hypotenuse() > 0.0001)
                desired = desired.plus(away.div(away.hypotenuse()).times(0.38));
        } else if (self.state == CombatState.ATTACK_PREPARE || self.isAttacking()) desired = desired.times(0.68);
        else if (self.isGuarding()) desired = desired.times(0.82);
        var maximum = 1.22 + self.stats.footwork() * 0.0035;
        if (desired.hypotenuse() > maximum) desired = desired.div(desired.hypotenuse()).times(maximum);
        if (desired.dot(new Pos(self.angle)) < -0.02) desired = desired.times(BACKWARD_SPEED_FACTOR);
        self.desiredVelocity = desired;
        captureDifferentialStage(DifferentialTickStage.AFTER_STEERING_COMPOSITION, self);
        integrateVelocity(self, desired, movementAcceleration(self), dt);
        moveWithVelocity(self, dt);
        if (self.state == CombatState.NEUTRAL) self.decision = Decision.WAIT;
    }

    private Pos group3ApproachVelocity(Musketeer self, TeamFormation3 formation,
                                       Group3ApproachPlan plan, Group3ContactBand band) {
        if (plan == null || band == Group3ContactBand.CONTACT ||
                band == Group3ContactBand.ACTIVE_EXCHANGE) return new Pos();
        var patternForward = switch (plan.pattern) {
            case LEFT_SWEEP, RIGHT_SWEEP -> 0.92;
            case COUNTER_ROTATE_LEFT, COUNTER_ROTATE_RIGHT -> 0.86;
            case CENTER_DELAYED -> 0.90;
            default -> 1.0;
        };
        var patternLateral = switch (plan.pattern) {
            case LEFT_SWEEP, RIGHT_SWEEP -> 1.16;
            case COUNTER_ROTATE_LEFT, COUNTER_ROTATE_RIGHT -> 1.10;
            case LEFT_LEAD, RIGHT_LEAD -> 0.92;
            default -> 1.0;
        };
        double forwardSpeed, lateralSpeed;
        if (band == Group3ContactBand.FAR_APPROACH) {
            forwardSpeed = Math.clamp(plan.forwardCommitment * patternForward, 0.78, 1.05);
            lateralSpeed = Math.clamp(plan.lateralCommitment * patternLateral, 0.28, 0.58);
        } else {
            forwardSpeed = Math.clamp(plan.forwardCommitment * patternForward * 0.58, 0.42, 0.78);
            lateralSpeed = Math.clamp(plan.lateralCommitment * patternLateral * 1.35, 0.42, 0.72);
        }
        var meetingLateralDistance = plan.predictedMeetingPoint.minus(formation.center).
                dot(formation.lateral) * plan.lateralSign;
        lateralSpeed *= Math.clamp(meetingLateralDistance / 0.90, 0.72, 1.12);
        var role = formation.roleOf(self);
        var forwardFactor = role == plan.leadRole ? 1.12 : role == plan.trailingRole ? 0.88 : 1.0;
        var lateralFactor = role == plan.leadRole ? 1.08 : role == plan.trailingRole ? 0.94 : 0.98;
        return formation.forward.times(forwardSpeed * forwardFactor).
                plus(formation.lateral.times(plan.lateralSign * lateralSpeed * lateralFactor));
    }

    private Pos group3FormationSteering(Musketeer self, Group3v3Engagement group,
                                         Group3ApproachPlan plan) {
        if (!self.alive && self.deadAgentAudit.active())
            ++self.deadAgentAudit.formationParticipations;
        var formation = group.formation(self.team);
        var role = formation.roleOf(self);
        var teamSize = group.living(self.team);
        var roleOffset = switch (role) {
            case LEFT -> teamSize == 2 ? -0.68 : -1.35;
            case CENTER -> 0.0;
            case RIGHT -> teamSize == 2 ? 0.68 : 1.35;
        };
        var depthOffset = role == FormationRole3.CENTER ? -0.10 : 0.06;
        if (plan != null) {
            if (role == plan.leadRole) depthOffset = plan.depthCommitment;
            else if (role == plan.supportRole) depthOffset = plan.depthCommitment * 0.22;
            else if (role == plan.trailingRole) depthOffset = -plan.depthCommitment * 0.68;
        }
        var preferred = formation.center.plus(formation.lateral.times(roleOffset)).
                plus(formation.forward.times(depthOffset));
        var correction = preferred.minus(self.pos);
        if (correction.hypotenuse() <= 0.0001) return new Pos();
        return correction.times(Math.min(0.42, 0.34 / correction.hypotenuse()));
    }

    private Pos group3LocalOpponentSteering(Musketeer self, Musketeer opponent) {
        if (opponent == null) return new Pos();
        var offset = opponent.pos.minus(self.pos);
        var distance = offset.hypotenuse();
        if (distance <= 0.0001) return new Pos();
        var direction = offset.div(distance);
        if (distance < 1.28) return direction.times(-(1.28 - distance) * 0.72);
        return distance > 2.05 ? direction.times(0.12) : new Pos();
    }

    private Pos group3AllyAvoidance(Musketeer self, Group3v3Engagement group) {
        if (!self.alive && self.deadAgentAudit.active())
            ++self.deadAgentAudit.avoidanceParticipations;
        var avoidance = new Pos();
        for (var ally : group.members(self.team)) {
            if (!ally.alive || ally == self) continue;
            var offset = ally.pos.minus(self.pos);
            var distance = offset.hypotenuse();
            if (distance < 0.0001) avoidance = avoidance.plus(new Pos(self.angle + Math.PI));
            else if (distance < 1.15)
                avoidance = avoidance.minus(offset.div(distance).times((1.15 - distance) * 2.2));
        }
        return avoidance;
    }

    private void measureGroup3Steering(Group3SteeringComposition composition) {
        group3Metrics.approachContributionSum += composition.approachContribution().hypotenuse();
        group3Metrics.teamManeuverContributionSum += composition.teamManeuverContribution().hypotenuse();
        group3Metrics.localManeuverContributionSum += composition.localManeuverContribution().hypotenuse();
        group3Metrics.formationContributionSum += composition.formationContribution().hypotenuse();
        group3Metrics.coverContributionSum += composition.coverContribution().hypotenuse();
        group3Metrics.avoidanceContributionSum += composition.avoidanceContribution().hypotenuse();
        group3Metrics.boundaryContributionSum += composition.boundaryContribution().hypotenuse();
        ++group3Metrics.approachSteeringSamples;
    }

    private Pos group3CoverSteering(Musketeer self, Group3v3Engagement group) {
        var network = group.coverNetwork(self.team);
        var formation = group.formation(self.team);
        var ownCoverage = network.coverageOf(self);
        var steering = new Pos();
        if (ownCoverage < 0.18) {
            var towardCenter = formation.center.minus(self.pos);
            if (towardCenter.hypotenuse() > 0.0001)
                steering = steering.plus(towardCenter.div(towardCenter.hypotenuse()).times(0.11));
        }
        if (formation.centerPlayer == self && network.weakestMember != null &&
                network.weakestMember != self && network.weakestMemberCoverage < 0.24) {
            var towardWeakest = network.weakestMember.pos.minus(self.pos);
            if (towardWeakest.hypotenuse() > 0.0001)
                steering = steering.plus(towardWeakest.div(towardWeakest.hypotenuse()).times(0.09));
        }
        var cluster = group3ClusterContaining(self, null);
        if (cluster != null && cluster.isolationConfidence() >= 0.66) {
            var allies = self.team == Team.BLUE ? cluster.blueMembers().size() :
                    cluster.redMembers().size();
            var enemies = self.team == Team.BLUE ? cluster.redMembers().size() :
                    cluster.blueMembers().size();
            if (allies == 1 && enemies >= 2) {
                var towardFormation = formation.center.minus(self.pos);
                if (towardFormation.hypotenuse() > 0.0001)
                    steering = steering.plus(towardFormation.div(towardFormation.hypotenuse()).
                            times(0.16));
            }
        }
        return steering;
    }

    private Pos group3ManeuverVelocity(Musketeer self, TeamFormation3 formation,
                                       TeamManeuver maneuver) {
        if (maneuver == null) return new Pos();
        var footwork = 0.82 + self.stats.footwork() * 0.002;
        var lateral = formation.lateral.times(maneuver.directionSign);
        var role = formation.roleOf(self);
        return switch (maneuver.type) {
            case HOLD_LINE -> new Pos();
            case ROTATE_LEFT, ROTATE_RIGHT -> {
                var lateralSpeed = role == FormationRole3.CENTER ? 0.56 :
                        self == maneuver.outerPlayer ? 0.84 : 0.28;
                var forwardSpeed = role == FormationRole3.CENTER ? 0.14 :
                        self == maneuver.outerPlayer ? 0.10 : 0.20;
                yield lateral.times(lateralSpeed * footwork).
                        plus(formation.forward.times(forwardSpeed * footwork));
            }
            case PRESS_FORWARD -> formation.forward.times(0.54 * footwork).
                    plus(lateral.times(role == FormationRole3.CENTER ? 0.04 : 0.11));
            case GIVE_GROUND -> formation.forward.times(-0.32 * footwork).
                    plus(lateral.times(0.24));
            case SHIFT_LEFT, SHIFT_RIGHT -> lateral.times(0.55 * footwork).
                    plus(formation.forward.times(0.06));
            case COMPRESS -> normalizedOr(formation.center.minus(self.pos), formation.forward).
                    times(role == FormationRole3.CENTER ? 0.08 : 0.42 * footwork);
            case EXPAND -> role == FormationRole3.CENTER ? formation.forward.times(0.08) :
                    normalizedOr(self.pos.minus(formation.center), lateral).times(0.40 * footwork);
            case CROSS_COVER -> lateral.times((role == FormationRole3.CENTER ? 0.18 : 0.34) * footwork).
                    plus(formation.forward.times(0.16));
            case RECOVER_FORMATION -> normalizedOr(formation.center.minus(self.pos), formation.forward).
                    times(role == FormationRole3.CENTER ? 0.10 : 0.48 * footwork);
        };
    }

    private Pos group3BoundarySteering(Musketeer self) {
        var margin = 0.72;
        var x = self.pos.x() < margin ? (margin - self.pos.x()) * 1.25 :
                self.pos.x() > ARENA_WIDTH - margin ? (ARENA_WIDTH - margin - self.pos.x()) * 1.25 : 0;
        var y = self.pos.y() < margin ? (margin - self.pos.y()) * 1.25 :
                self.pos.y() > ARENA_HEIGHT - margin ? (ARENA_HEIGHT - margin - self.pos.y()) * 1.25 : 0;
        return new Pos(x, y);
    }

    private void measureGroup3PreContactGeometry(Group3v3Engagement group) {
        var observation = group.approachObservation;
        var blueCenter = group.blueFormation.center;
        var redCenter = group.redFormation.center;
        var blueDelta = blueCenter.minus(observation.bluePreviousCenter);
        var redDelta = redCenter.minus(observation.redPreviousCenter);
        observation.bluePreviousCenter = blueCenter;
        observation.redPreviousCenter = redCenter;
        if (!Double.isNaN(observation.firstContactAt) || observation.flatFrontAudit != null) return;
        observation.preContactForwardDistance += Math.max(0,
                blueDelta.dot(observation.blueInitialForward)) * 0.5;
        observation.preContactForwardDistance += Math.max(0,
                redDelta.dot(observation.redInitialForward)) * 0.5;
        observation.preContactLateralDistance += Math.abs(
                blueDelta.dot(observation.blueInitialLateral)) * 0.5;
        observation.preContactLateralDistance += Math.abs(
                redDelta.dot(observation.redInitialLateral)) * 0.5;
        var averageDepth = (group.blueFormation.depth + group.redFormation.depth) * 0.5;
        observation.preContactDepthSum += averageDepth;
        observation.preContactWidthSum += (group.blueFormation.width + group.redFormation.width) * 0.5;
        observation.maximumPreContactDepth = Math.max(observation.maximumPreContactDepth,
                Math.max(group.blueFormation.depth, group.redFormation.depth));
        ++observation.preContactSamples;
        if (blueDelta.hypotenuse() > 0.0001 && redDelta.hypotenuse() > 0.0001) {
            observation.pathCorrelationSum += -blueDelta.dot(redDelta) /
                    (blueDelta.hypotenuse() * redDelta.hypotenuse());
            ++observation.pathCorrelationSamples;
        }
        observation.centerLineDeviationSum += (Math.abs(blueCenter.minus(
                observation.blueInitialCenter).dot(observation.blueInitialLateral)) +
                Math.abs(redCenter.minus(observation.redInitialCenter).
                        dot(observation.redInitialLateral))) * 0.5;
    }

    private void observeGroup3FirstAttack() {
        if (group3Metrics.timeToFirstAttack >= 0) return;
        for (var agent : agents)
            if (agent.alive && (agent.state == CombatState.ATTACK_PREPARE ||
                    agent.state == CombatState.ATTACK_HOLD || agent.isAttacking())) {
                group3Metrics.timeToFirstAttack = simulationTime;
                return;
            }
    }

    private void measureGroup3FirstContacts(Group3v3Engagement group) {
        var observation = group.approachObservation;
        if (observation.flatFrontAudit != null) return;
        for (var agent : agents) {
            if (!agent.alive || observation.firstContacts.containsKey(agent)) continue;
            Musketeer closest = null;
            var minimumDistance = Double.POSITIVE_INFINITY;
            for (var opponent : group.members(agent.team == Team.BLUE ? Team.RED : Team.BLUE)) {
                if (!opponent.alive) continue;
                var distance = opponent.pos.minus(agent.pos).hypotenuse();
                if (distance >= minimumDistance) continue;
                minimumDistance = distance;
                closest = opponent;
            }
            if (closest == null || minimumDistance > 2.95) continue;
            var formation = group.formation(agent.team);
            var covered = group.coverNetwork(agent.team).coverageOf(agent) >= 0.18;
            var event = new FirstContactEvent(agent, closest, simulationTime,
                    formation.roleOf(agent), minimumDistance, covered);
            observation.firstContacts.put(agent, event);
            if (Double.isNaN(observation.firstContactAt)) {
                observation.firstContactAt = simulationTime;
                group3Metrics.timeToFirstContact = simulationTime - observation.startedAt;
            }
            observation.lastFirstContactAt = simulationTime;
            group3Metrics.firstContactByRole.merge(event.role(), 1, Integer::sum);
            if (covered) ++group3Metrics.coveredFirstContacts;
            else ++group3Metrics.uncoveredFirstContacts;
        }
    }

    private void finishGroup3FlatFrontAuditIfReady(Group3v3Engagement group, boolean elimination) {
        var observation = group.approachObservation;
        if (observation.flatFrontAudit != null || Double.isNaN(observation.firstContactAt)) return;
        if (!elimination && observation.firstContacts.size() < 6 &&
                simulationTime - observation.firstContactAt < 1.50) return;
        var spread = observation.firstContactSpread();
        var averageDepth = observation.preContactSamples == 0 ? 0 :
                observation.preContactDepthSum / observation.preContactSamples;
        var correlation = observation.pathCorrelationSamples == 0 ? 0 :
                observation.pathCorrelationSum / observation.pathCorrelationSamples;
        var centerDeviation = observation.preContactSamples == 0 ? 0 :
                observation.centerLineDeviationSum / observation.preContactSamples;
        var approachDuration = observation.firstContactAt - observation.startedAt;
        var lowLateral = observation.preContactLateralDistance < 0.55;
        var lowDepth = averageDepth < 0.28;
        var compressedContacts = observation.firstContacts.size() >= 4 && spread < 0.18;
        var highlyCorrelated = correlation > 0.88;
        var lowCenterDeviation = centerDeviation < 0.32;
        var slow = approachDuration > 4.20;
        var suspiciousSignals = (lowLateral ? 1 : 0) + (lowDepth ? 1 : 0) +
                (compressedContacts ? 1 : 0) + (highlyCorrelated ? 1 : 0) +
                (lowCenterDeviation ? 1 : 0) + (slow ? 1 : 0);
        var reason = new StringBuilder();
        appendFlatFrontReason(reason, lowLateral, "low_lateral");
        appendFlatFrontReason(reason, lowDepth, "low_depth");
        appendFlatFrontReason(reason, compressedContacts, "compressed_contacts");
        appendFlatFrontReason(reason, highlyCorrelated, "correlated_paths");
        appendFlatFrontReason(reason, lowCenterDeviation, "centered_paths");
        appendFlatFrontReason(reason, slow, "slow_approach");
        var suspicious = suspiciousSignals >= 3;
        observation.flatFrontAudit = new FlatFrontConvergenceAudit(suspicious,
                observation.preContactLateralDistance, averageDepth, spread, correlation,
                centerDeviation, approachDuration, reason.isEmpty() ? "organic_geometry" : reason.toString());
        ++group3Metrics.approachAudits;
        group3Metrics.preContactForwardDistance = observation.preContactForwardDistance;
        group3Metrics.preContactLateralDistance = observation.preContactLateralDistance;
        group3Metrics.preContactWidthSum = observation.preContactWidthSum;
        group3Metrics.preContactDepthSum = observation.preContactDepthSum;
        group3Metrics.preContactSamples = observation.preContactSamples;
        group3Metrics.maximumPreContactDepth = observation.maximumPreContactDepth;
        group3Metrics.firstContactSpread = spread;
        group3Metrics.formationRoleChangesBeforeContact = group.blueFormation.roleChanges +
                group.redFormation.roleChanges;
        var simultaneous = maximumSimultaneousFirstContacts(observation);
        if (simultaneous >= 3) ++group3Metrics.simultaneousThreePlayerContacts;
        if (simultaneous == 6) ++group3Metrics.simultaneousSixPlayerContacts;
        if (suspicious) ++group3Metrics.flatFrontConvergences;
        if (lowLateral) ++group3Metrics.lowLateralApproaches;
        if (lowDepth) ++group3Metrics.lowDepthApproaches;
        if (slow) ++group3Metrics.slowApproaches;
        if (highlyCorrelated) ++group3Metrics.highlyCorrelatedTeamPaths;
    }

    private void appendFlatFrontReason(StringBuilder reason, boolean present, String value) {
        if (!present) return;
        if (!reason.isEmpty()) reason.append('+');
        reason.append(value);
    }

    private int maximumSimultaneousFirstContacts(Group3ApproachObservation observation) {
        var times = new double[observation.firstContacts.size()];
        var index = 0;
        for (var event : observation.firstContacts.values()) times[index++] = event.time();
        java.util.Arrays.sort(times);
        var maximum = 0;
        for (var start = 0; start < times.length; ++start) {
            var end = start;
            while (end < times.length && times[end] - times[start] <= 0.12) ++end;
            maximum = Math.max(maximum, end - start);
        }
        return maximum;
    }

    private void measureGroup3(Group3v3Engagement group, double dt) {
        for (var formation : new TeamFormation3[]{group.blueFormation, group.redFormation}) {
            group3Metrics.formationWidthSum += formation.width;
            group3Metrics.formationDepthSum += formation.depth;
            group3Metrics.cohesionSum += formation.cohesion;
            ++group3Metrics.formationSamples;
            var maneuver = group.maneuver(formation.team);
            if (maneuver == null || !maneuver.rotating()) continue;
            var lateral = formation.lateral.times(maneuver.directionSign);
            group3Metrics.outerInnerLateralDifference += maneuver.outerPlayer.velocity.dot(lateral) -
                    maneuver.innerPlayer.velocity.dot(lateral);
            ++group3Metrics.rotationSamples;
        }
    }

    private void updateTwoVsOneAttentionAndMetrics(double dt) {
        for (var engagement : engagementManager.engagements()) {
            if (engagement.type != EngagementType.TWO_VS_ONE ||
                    engagement.status == EngagementStatus.BROKEN ||
                    engagement.status == EngagementStatus.FINISHED) continue;
            var defender = engagement.primaryTarget;
            var fixer = engagement.fixer;
            var flanker = engagement.flanker;
            if (!defender.alive || !fixer.alive || !flanker.alive) continue;
            modernMetrics.defenderSurvivalTime += dt;
            var frontAngle = Math.abs(angleDifference(defender.angle,
                    fixer.pos.minus(defender.pos).radians()));
            var fixerDistance = fixer.pos.minus(defender.pos).hypotenuse();
            modernMetrics.fixerFrontAngleSum += frontAngle;
            modernMetrics.fixerDistanceSum += fixerDistance;
            ++modernMetrics.fixerGeometrySamples;
            if (frontAngle <= Math.toRadians(70) && fixerDistance >= 1.05 && fixerDistance <= 2.05)
                modernMetrics.fixerFrontMaintainedTime += dt;
            var behind = frontAngle >= Math.toRadians(110);
            if (behind && !engagement.fixerBehindDefender)
                ++modernMetrics.fixerCrossedBehindDefender;
            engagement.fixerBehindDefender = behind;
            var fixerAngle = known(defender, fixer).minus(defender.pos).radians();
            if (isTracked(defender, flanker)) {
                var flankerAngle = known(defender, flanker).minus(defender.pos).radians();
                modernMetrics.attackersSeparationAngleSum +=
                        Math.abs(angleDifference(fixerAngle, flankerAngle));
                ++modernMetrics.attackersSeparationSamples;
            }
            updateMultiThreatAttention(defender, fixer, flanker, dt);
            updateTwoVsOneCoordination(engagement);
            if (defender.backEscapeTime > 0 && !defender.backEscapeCountedSafe &&
                    !isImmediateRearDanger(defender)) {
                defender.backEscapeCountedSafe = true;
                ++modernMetrics.successfulBackEscapes;
            }
        }
    }

    private void updateTwoVsOneCoordination(Engagement engagement) {
        if (scenario != SimulationScenario.MODERN_2V2) return;
        var coordination = engagement.coordination;
        if (coordination == null) return;
        var fixer = engagement.fixer;
        var flanker = engagement.flanker;
        var defender = engagement.primaryTarget;
        coordination.flankReadiness = flankReadiness(flanker, defender);
        if (flanker.flankPhase == FlankPhase.ABORTED ||
                flanker.flankPhase == FlankPhase.CONTESTED ||
                flanker.flankPhase == FlankPhase.COMPLETED) {
            if (coordination.phase != TwoVsOneCoordinationPhase.BUILD_GEOMETRY &&
                    coordination.phase != TwoVsOneCoordinationPhase.RECOVER_COORDINATION)
                abortCoordination(coordination, "flanker_not_ready");
            return;
        }
        if (coordination.phase == TwoVsOneCoordinationPhase.RECOVER_COORDINATION) {
            if (simulationTime - coordination.phaseStartedAt >= 0.55)
                changeCoordinationPhase(coordination,
                        TwoVsOneCoordinationPhase.BUILD_GEOMETRY, "recovered");
            return;
        }
        if (coordination.phase == TwoVsOneCoordinationPhase.BUILD_GEOMETRY) {
            var flankerReady = coordination.flankReadiness == FlankReadiness.FLANK_THREAT_READY ||
                    coordination.flankReadiness == FlankReadiness.FLANK_COMMITTED;
            var fixerReady = attackerGeometryCredible(fixer, defender, flanker);
            if (!flankerReady && !fixerReady) return;
            var defenderFocusedOnFixer = defender.target == fixer &&
                    Math.abs(angleDifference(defender.angle,
                            fixer.pos.minus(defender.pos).radians())) <= Math.toRadians(72);
            var fixerScore = primaryThreatScore(fixer, defender, flanker) +
                    (defenderFocusedOnFixer ? 0.12 : 0);
            var flankerScore = primaryThreatScore(flanker, defender, fixer) +
                    (flankerReady ? 0.28 : 0) + (defenderFocusedOnFixer ? 0.16 : 0);
            coordination.primaryAttacker = flankerReady && flankerScore > fixerScore + 0.10 ?
                    flanker : fixer;
            coordination.secondaryAttacker = coordination.primaryAttacker == fixer ? flanker : fixer;
            coordination.lastProgressAt = simulationTime;
            coordination.secondaryReleased = false;
            ++modernMetrics.coordinationPlansStarted;
            changeCoordinationPhase(coordination,
                    TwoVsOneCoordinationPhase.PRIMARY_THREAT_READY,
                    coordination.primaryAttacker == fixer ? "fixer_primary" : "flanker_primary");
        }
        var primary = coordination.primaryAttacker;
        var secondary = coordination.secondaryAttacker;
        if (primary == null || secondary == null || !primary.alive || !secondary.alive) {
            abortCoordination(coordination, "attacker_unavailable");
            return;
        }
        var previouslyCredible = coordination.primaryThreatCredible;
        coordination.primaryThreatCredible = credibleAttackThreat(primary, defender, secondary);
        coordination.defenderReacting = defenderReactingTo(defender, primary);
        if (coordination.phase == TwoVsOneCoordinationPhase.PRIMARY_THREAT_READY &&
                coordination.primaryThreatCredible) {
            if (!previouslyCredible) ++modernMetrics.primaryThreatsEstablished;
            coordination.lastProgressAt = simulationTime;
            if (primary.isAttacking())
                changeCoordinationPhase(coordination,
                        TwoVsOneCoordinationPhase.PRIMARY_COMMITTED, "primary_active");
        }
        if ((coordination.phase == TwoVsOneCoordinationPhase.PRIMARY_THREAT_READY ||
                coordination.phase == TwoVsOneCoordinationPhase.PRIMARY_COMMITTED) &&
                coordination.primaryThreatCredible && coordination.defenderReacting) {
            var assessment = assessSecondaryRelease(primary, secondary, defender);
            coordination.secondaryAssessment = assessment;
            coordination.secondaryLineSafe = assessment.lineSafety() >= 0.14;
            if (assessment.viable()) {
                var delay = Math.clamp(assessment.primaryImpactEta() +
                        assessment.desiredImpactOffset() - assessment.secondaryImpactEta(), 0, 0.42);
                coordination.releaseWindowStart = simulationTime + delay;
                coordination.releaseWindowEnd = coordination.releaseWindowStart +
                        Math.max(0.23, secondaryPreparationRemaining(secondary) + 0.24);
                prepareSecondaryThreat(engagement, secondary, defender);
                ++modernMetrics.secondaryReleaseWindowsCreated;
                changeCoordinationPhase(coordination,
                        TwoVsOneCoordinationPhase.SECONDARY_RELEASE_WINDOW,
                        "defender_reaction_observed");
                if (secondary.isAttacking()) registerCoordinatedRelease(engagement, secondary);
            } else recordCoordinationRejection(coordination, assessment.rejectionReason());
        }
        if (coordination.phase == TwoVsOneCoordinationPhase.SECONDARY_RELEASE_WINDOW) {
            if (!defenderReactingTo(defender, primary) ||
                    defender.pos.minus(secondary.pos).hypotenuse() >
                            secondary.projectedThrustLength() + Musketeer.BODY_RADIUS + 0.82) {
                ++modernMetrics.coordinationRejectedDefenderEscaped;
                abortCoordination(coordination, "defender_escaped");
            } else if (simulationTime > coordination.releaseWindowEnd)
                abortCoordination(coordination, "secondary_window_expired");
            else if (secondary.isAttacking()) {
                coordination.lastAttackAt = simulationTime;
                ++modernMetrics.doubleThreatsActive;
                changeCoordinationPhase(coordination,
                        TwoVsOneCoordinationPhase.DOUBLE_THREAT_ACTIVE,
                        "secondary_attack_active");
            }
        }
        if (coordination.phase == TwoVsOneCoordinationPhase.DOUBLE_THREAT_ACTIVE &&
                !primary.isAttacking() && !secondary.isAttacking())
            changeCoordinationPhase(coordination,
                    TwoVsOneCoordinationPhase.RECOVER_COORDINATION, "attacks_resolved");
        if (simulationTime - coordination.phaseStartedAt > 2.60 &&
                coordination.phase != TwoVsOneCoordinationPhase.DOUBLE_THREAT_ACTIVE)
            abortCoordination(coordination, "preparation_timeout");
    }

    private FlankReadiness flankReadiness(Musketeer flanker, Musketeer defender) {
        if (flanker.flankPhase == FlankPhase.COMMIT_BACKSTAB || flanker.isAttacking())
            return FlankReadiness.FLANK_COMMITTED;
        var distance = flanker.pos.minus(defender.pos).hypotenuse();
        var exploitable = flanker.rearAngle >= Math.toRadians(82) &&
                distance <= flanker.projectedThrustLength() + Musketeer.BODY_RADIUS + 0.62;
        if (exploitable || flanker.flankPhase == FlankPhase.PREPARE_BACKSTAB ||
                flanker.flankPhase == FlankPhase.STABILIZE_REAR)
            return FlankReadiness.FLANK_THREAT_READY;
        if (flanker.flankPhase == FlankPhase.APPROACH_SIDE ||
                flanker.flankPhase == FlankPhase.GAIN_REAR_ANGLE)
            return FlankReadiness.FLANK_APPROACHING;
        return FlankReadiness.FLANK_NOT_READY;
    }

    private boolean attackerGeometryCredible(Musketeer attacker, Musketeer defender,
                                               Musketeer ally) {
        var distance = attacker.pos.minus(defender.pos).hypotenuse();
        var alignment = Math.abs(angleDifference(attacker.weaponWorldAngle(),
                defender.pos.minus(attacker.pos).radians()));
        return distance <= attacker.projectedThrustLength() + Musketeer.BODY_RADIUS + 0.52 &&
                alignment <= Math.toRadians(34) &&
                distanceToSegment(ally.pos, attacker.pos, defender.pos) >=
                        Musketeer.BODY_RADIUS * 1.35;
    }

    private double primaryThreatScore(Musketeer attacker, Musketeer defender, Musketeer ally) {
        var distance = attacker.pos.minus(defender.pos).hypotenuse();
        var range = attacker.projectedThrustLength() + Musketeer.BODY_RADIUS + 0.52;
        var alignment = Math.abs(angleDifference(attacker.weaponWorldAngle(),
                defender.pos.minus(attacker.pos).radians()));
        var armed = attacker.state == CombatState.ATTACK_HOLD ? 0.42 :
                attacker.state == CombatState.ATTACK_PREPARE ? 0.28 :
                        attacker.isAttacking() ? 0.48 : 0;
        var line = distanceToSegment(ally.pos, attacker.pos, defender.pos) >=
                Musketeer.BODY_RADIUS * 1.35 ? 0.18 : 0;
        return Math.clamp(1 - Math.abs(distance - 1.48) / Math.max(0.4, range), 0, 1) * 0.28 +
                Math.clamp(1 - alignment / Math.toRadians(45), 0, 1) * 0.24 + armed + line;
    }

    private boolean credibleAttackThreat(Musketeer attacker, Musketeer defender,
                                         Musketeer ally) {
        var armed = attacker.state == CombatState.ATTACK_HOLD || attacker.isAttacking() ||
                attacker.state == CombatState.ATTACK_PREPARE &&
                        attacker.prepareTime() - attacker.stateTime <= 0.16;
        return armed && attackerGeometryCredible(attacker, defender, ally) &&
                isVisible(defender, attacker);
    }

    private boolean defenderReactingTo(Musketeer defender, Musketeer attacker) {
        var facing = Math.abs(angleDifference(defender.angle,
                attacker.pos.minus(defender.pos).radians()));
        var guarding = defender.state == CombatState.BLOCK_PREPARE ||
                defender.state == CombatState.BLOCKING ||
                defender.state == CombatState.DOUBLE_BLOCK ||
                defender.state == CombatState.BLOCK_STUN;
        var attacking = (defender.isAttacking() ||
                defender.state == CombatState.ATTACK_PREPARE) && defender.target == attacker;
        return isVisible(defender, attacker) &&
                (defender.target == attacker && facing <= Math.toRadians(58) ||
                        guarding && facing <= Math.toRadians(76) || attacking);
    }

    private SecondaryReleaseAssessment assessSecondaryRelease(Musketeer primary,
                                                                Musketeer secondary,
                                                                Musketeer defender) {
        var primaryEta = coordinatedImpactEta(primary, defender);
        var secondaryEta = coordinatedImpactEta(secondary, defender);
        var attention = Math.clamp(1 - Math.abs(angleDifference(defender.angle,
                primary.pos.minus(defender.pos).radians())) / Math.toRadians(95), 0, 1);
        if (defender.target == primary) attention = Math.min(1, attention + 0.22);
        if (defender.isGuarding()) attention = Math.min(1, attention + 0.16);
        var primaryClearance = distanceToSegment(secondary.pos, primary.pos, defender.pos);
        var secondaryClearance = distanceToSegment(primary.pos, secondary.pos, defender.pos);
        var lineSafety = Math.clamp((Math.min(primaryClearance, secondaryClearance) -
                Musketeer.BODY_RADIUS * 1.25) / 0.72, 0, 1);
        var allyDistance = primary.pos.minus(secondary.pos).hypotenuse();
        var collisionRisk = Math.clamp((1.02 - allyDistance) / 0.52, 0, 1);
        var separation = Math.abs(angleDifference(primary.pos.minus(defender.pos).radians(),
                secondary.pos.minus(defender.pos).radians()));
        var heightDifference = primary.attackDirection != secondary.attackDirection ? 1 : 0.25;
        var desiredOffset = 0.12 + Math.clamp((separation - Math.toRadians(35)) /
                Math.toRadians(100), 0, 1) * 0.18;
        var timing = Math.clamp(1 - Math.abs(secondaryEta - primaryEta - desiredOffset) / 0.55,
                0, 1);
        var score = Math.clamp(attention * 0.27 +
                Math.clamp(separation / Math.toRadians(120), 0, 1) * 0.20 +
                heightDifference * 0.12 + lineSafety * 0.20 + timing * 0.21 -
                collisionRisk * 0.34, 0, 1);
        var secondaryFlankReadiness = flankReadiness(secondary, defender);
        var physicalFlankReady = secondary.tacticalIntent != TacticalIntent.FLANK ||
                secondary.engagement != null &&
                        assessBackstabOpportunity(secondary.engagement).valid() ||
                secondary.state == CombatState.ATTACK_PREPARE ||
                secondary.state == CombatState.ATTACK_HOLD || secondary.isAttacking();
        var rejection = secondary.tacticalIntent == TacticalIntent.FLANK &&
                (secondaryFlankReadiness != FlankReadiness.FLANK_THREAT_READY &&
                        secondaryFlankReadiness != FlankReadiness.FLANK_COMMITTED ||
                        !physicalFlankReady) ? "flanker_not_ready" :
                lineSafety < 0.14 ? "line_blocked" :
                        collisionRisk > 0.58 ? "friendly_collision" :
                                timing < 0.24 ? "bad_timing" :
                                        score < 0.44 ? "bad_timing" : "";
        return new SecondaryReleaseAssessment(rejection.isEmpty(), primary, secondary, defender,
                primaryEta, secondaryEta, desiredOffset, attention, lineSafety,
                collisionRisk, score, rejection);
    }

    private double coordinatedImpactEta(Musketeer attacker, Musketeer defender) {
        var distanceEta = Math.max(0, attacker.pos.minus(defender.pos).hypotenuse() -
                attacker.projectedThrustLength() - Musketeer.BODY_RADIUS) /
                estimatedMovementSpeed(attacker);
        var turnEta = Math.abs(angleDifference(attacker.weaponWorldAngle(),
                defender.pos.minus(attacker.pos).radians())) / estimatedTurnSpeed(attacker);
        var attackEta = attacker.isAttacking() ?
                Math.max(0, attacker.activeTime() * 0.5 - attacker.stateTime) :
                attacker.state == CombatState.ATTACK_HOLD ? 0.06 :
                        attacker.state == CombatState.ATTACK_PREPARE ?
                                Math.max(0, attacker.prepareTime() - attacker.stateTime) + 0.06 :
                                attacker.attackDirection.prepareTime + 0.06;
        return distanceEta + turnEta + attackEta;
    }

    private double secondaryPreparationRemaining(Musketeer attacker) {
        return attacker.state == CombatState.ATTACK_PREPARE ?
                Math.max(0, attacker.prepareTime() - attacker.stateTime) :
                attacker.state == CombatState.ATTACK_HOLD || attacker.isAttacking() ? 0 :
                        attacker.attackDirection.prepareTime;
    }

    private void prepareSecondaryThreat(Engagement engagement, Musketeer secondary,
                                        Musketeer defender) {
        if (secondary.state != CombatState.NEUTRAL) return;
        if (secondary == engagement.flanker) {
            secondary.backstabOpportunity = assessBackstabOpportunity(engagement);
            if (secondary.backstabOpportunity.valid()) {
                changeFlankPhase(secondary, FlankPhase.PREPARE_BACKSTAB,
                        "coordinated_secondary_window");
                beginBackstab(secondary, defender);
            }
            return;
        }
        var distance = secondary.pos.minus(defender.pos).hypotenuse();
        beginAttack(secondary, defender, distance);
        secondary.attackIntent = AttackIntent.HOLD;
        secondary.decision = Decision.PREPARE_HOLD_ATTACK;
    }

    private void changeCoordinationPhase(TwoVsOneCoordination coordination,
                                         TwoVsOneCoordinationPhase phase, String reason) {
        if (coordination.phase == phase) return;
        var previous = coordination.phase;
        coordination.phase = phase;
        coordination.phaseStartedAt = simulationTime;
        logTactical("TWO_VS_ONE_COORDINATION", "from=" + previous + " to=" + phase +
                " primary=" + (coordination.primaryAttacker == null ? "NONE" :
                coordination.primaryAttacker.name(UiLanguage.FRENCH)) + " reason=" + reason);
        traceDecision(coordination.primaryAttacker, DecisionCategory.TWO_VS_ONE_COORDINATION,
                phase.name(), reason, coordination.defender, null, 0, 0, 0, 0,
                previous.name());
    }

    private void abortCoordination(TwoVsOneCoordination coordination, String reason) {
        if (coordination.phase == TwoVsOneCoordinationPhase.BUILD_GEOMETRY) return;
        ++coordination.abortedPlans;
        ++modernMetrics.coordinationAborted;
        changeCoordinationPhase(coordination,
                TwoVsOneCoordinationPhase.RECOVER_COORDINATION, reason);
    }

    private void recordCoordinationRejection(TwoVsOneCoordination coordination, String reason) {
        if (reason.equals(coordination.lastRejection) &&
                simulationTime - coordination.lastRejectionAt < 0.50) return;
        coordination.lastRejection = reason;
        coordination.lastRejectionAt = simulationTime;
        switch (reason) {
            case "line_blocked" -> ++modernMetrics.coordinationRejectedLineBlocked;
            case "friendly_collision" -> ++modernMetrics.coordinationRejectedFriendlyCollision;
            case "flanker_not_ready" -> ++modernMetrics.coordinationRejectedFlankerNotReady;
            default -> ++modernMetrics.coordinationRejectedBadTiming;
        }
    }

    private void updateMultiThreatAttention(Musketeer defender, Musketeer fixer,
                                            Musketeer flanker, double dt) {
        var attention = defender.multiThreatAttention;
        attention.focusLockRemaining = Math.max(0, attention.focusLockRemaining - dt);
        attention.switchDelay = Math.max(0, attention.switchDelay - dt);
        var best = threatAttentionScore(defender, fixer) >= threatAttentionScore(defender, flanker) ?
                fixer : flanker;
        if (!isTracked(defender, best)) best = best == fixer ? flanker : fixer;
        if (!isTracked(defender, best)) best = null;
        var currentScore = threatAttentionScore(defender, attention.focusedThreat);
        var bestScore = threatAttentionScore(defender, best);
        var currentLost = attention.focusedThreat == null ||
                !isTracked(defender, attention.focusedThreat);
        if (best != null && best != attention.focusedThreat && attention.switchDelay <= 0 &&
                (currentLost || attention.focusLockRemaining <= 0 && bestScore > currentScore + 0.28)) {
            attention.focusedThreat = best;
            attention.focusLockRemaining = 0.58 +
                    (100 - defender.stats.reaction()) * 0.005;
            attention.switchDelay = 0.22 +
                    (100 - defender.stats.decisionMaking()) * 0.003;
            ++modernMetrics.defenderThreatSwitches;
            logTactical("DEFENDER_THREAT_SWITCH", "defender=" +
                    defender.name(UiLanguage.FRENCH) + " focus=" + best.name(UiLanguage.FRENCH));
            traceDecision(defender, DecisionCategory.TARGET_ATTENTION, "FOCUS_TARGET",
                    currentLost ? "previous_target_lost" : "higher_physical_threat", best,
                    null, bestScore, 0, 0, 0,
                    attention.secondaryThreat == null ? "NONE" :
                            attention.secondaryThreat.name(UiLanguage.FRENCH));
        }
        attention.secondaryThreat = attention.focusedThreat == fixer && isTracked(defender, flanker) ?
                flanker : attention.focusedThreat == flanker && isTracked(defender, fixer) ? fixer : null;
        attention.uncertainty = attention.secondaryThreat == null ? 1 :
                isVisible(defender, attention.secondaryThreat) ? 0 : 0.45;
    }

    private double threatAttentionScore(Musketeer defender, Musketeer threat) {
        if (threat == null || !threat.alive || !isTracked(defender, threat))
            return Double.NEGATIVE_INFINITY;
        var distance = knownDistance(defender, threat);
        var active = threat.isAttacking() ? 1.2 :
                threat.state == CombatState.ATTACK_HOLD ? 0.72 :
                        threat.state == CombatState.ATTACK_PREPARE ||
                                threat.state == CombatState.CHAMBER_PREPARE ? 0.48 : 0;
        var effectiveRange = threat.projectedThrustLength() + Musketeer.BODY_RADIUS + 0.95;
        var rangeFactor = Math.clamp((effectiveRange - distance) / 1.35, 0, 1);
        return active * (0.28 + rangeFactor * 0.72) +
                Math.clamp((2.75 - distance) / 2.2, 0, 1) +
                (isVisible(defender, threat) ? 0.18 : 0);
    }

    private void updateTacticalDestinations(double dt) {
        for (var engagement : engagementManager.engagements()) {
            if (engagement.status == EngagementStatus.BROKEN ||
                    engagement.status == EngagementStatus.FINISHED ||
                    engagement.type != EngagementType.TWO_VS_ONE) continue;
            updateFixerDestination(engagement, dt);
            if (engagement.flanker.flankPhase == FlankPhase.APPROACH_SIDE)
                engagement.flanker.tacticalDestination = engagement.flankerDestination();
            else if (engagement.flanker.flankPhase != FlankPhase.CONTESTED &&
                    engagement.flanker.flankPhase != FlankPhase.ABORTED &&
                    engagement.flanker.flankPhase != FlankPhase.COMPLETED)
                updateRearDestination(engagement, dt);
            if (engagement.interceptor == null) continue;
            var interceptor = engagement.interceptor;
            interceptor.interceptionReevaluationTime -= dt;
            if (interceptor.rawInterceptionPoint == null || interceptor.interceptionReevaluationTime <= 0) {
                interceptor.rawInterceptionPoint = calculateInterceptionPoint(engagement);
                interceptor.interceptionReevaluationTime = INTERCEPTION_REEVALUATION_TIME;
            }
            if (interceptor.tacticalDestination == null || dt <= 0)
                interceptor.tacticalDestination = interceptor.rawInterceptionPoint;
            else {
                var adjustment = interceptor.rawInterceptionPoint.minus(interceptor.tacticalDestination);
                var distance = adjustment.hypotenuse();
                var maximum = INTERCEPTION_DESTINATION_SPEED * dt;
                if (distance <= maximum) interceptor.tacticalDestination = interceptor.rawInterceptionPoint;
                else interceptor.tacticalDestination = interceptor.tacticalDestination.
                        plus(adjustment.div(distance).times(maximum));
            }
            updateInterceptionPhase(engagement, dt);
        }
    }

    private void updateFixerDestination(Engagement engagement, double dt) {
        var fixer = engagement.fixer;
        var defender = engagement.primaryTarget;
        if (fixer == null || defender == null || !fixer.alive || !defender.alive) return;
        var awareness = fixer.opponentAwareness.get(defender);
        var defenderPosition = awareness == null ? defender.pos : known(fixer, defender);
        var defenderAngle = awareness == null || awareness.visible || awareness.memoryTime <= 0 ?
                defender.angle : awareness.observedBodyAngle;
        var destination = defenderPosition.plus(new Pos(defenderAngle).times(1.42));
        var margin = Musketeer.BODY_RADIUS + 0.08;
        destination = new Pos(Math.clamp(destination.x(), margin, ARENA_WIDTH - margin),
                Math.clamp(destination.y(), margin, ARENA_HEIGHT - margin));
        if (fixer.tacticalDestination == null || dt <= 0) fixer.tacticalDestination = destination;
        else {
            var adjustment = destination.minus(fixer.tacticalDestination);
            var maximum = 1.45 * dt;
            fixer.tacticalDestination = adjustment.hypotenuse() <= maximum ? destination :
                    fixer.tacticalDestination.plus(adjustment.div(adjustment.hypotenuse()).times(maximum));
        }
    }

    private void initializeFlank(Musketeer flanker) {
        flanker.flankPhase = FlankPhase.APPROACH_SIDE;
        flanker.rawRearDestination = null;
        flanker.smoothedRearDestination = null;
        flanker.backstabOpportunity = BackstabOpportunity.unavailable();
        flanker.rearAngle = flanker.previousRearAngle = 0;
        flanker.rearAngleGainRate = 0;
        flanker.rearStableTime = flanker.rearNoProgressTime = flanker.flankFixerLostTime = 0;
        flanker.flankRadiusOffset = 0;
        flanker.flankPhaseStartedAt = simulationTime;
        flanker.controlledBackstabAttempt = false;
        flanker.rearSectorStabilized = false;
        flanker.backstabWindowLogged = false;
        flanker.lastFlankReason = "flank_created";
    }

    private void updateRearDestination(Engagement engagement, double dt) {
        var flanker = engagement.flanker;
        var defender = engagement.primaryTarget;
        var preparationRadius = Math.clamp(
                flanker.projectedThrustLength() + Musketeer.BODY_RADIUS -
                        REAR_PREPARATION_MARGIN + flanker.flankRadiusOffset,
                REAR_MIN_DISTANCE + 0.20, REAR_MAX_DISTANCE - 0.08);
        var side = flanker.localSide < 0 ? -1 : 1;
        flanker.rawRearDestination = defender.pos.plus(
                new Pos(defender.angle + side * REAR_STABLE_ANGLE).times(preparationRadius));
        if (flanker.smoothedRearDestination == null || dt <= 0)
            flanker.smoothedRearDestination = flanker.rawRearDestination;
        else {
            var adjustment = flanker.rawRearDestination.minus(flanker.smoothedRearDestination);
            var distance = adjustment.hypotenuse();
            var maximum = REAR_DESTINATION_SPEED * dt;
            flanker.smoothedRearDestination = distance <= maximum ? flanker.rawRearDestination :
                    flanker.smoothedRearDestination.plus(adjustment.div(distance).times(maximum));
        }
        flanker.tacticalDestination = flanker.smoothedRearDestination;
    }

    private void updateFlanks(double dt) {
        for (var engagement : engagementManager.engagements()) {
            if (engagement.type != EngagementType.TWO_VS_ONE ||
                    engagement.status == EngagementStatus.BROKEN ||
                    engagement.status == EngagementStatus.FINISHED) continue;
            var flanker = engagement.flanker;
            var defender = engagement.primaryTarget;
            if (!flanker.alive) {
                changeFlankPhase(flanker, FlankPhase.ABORTED, "flanker_died");
                continue;
            }
            if (!defender.alive) {
                changeFlankPhase(flanker, FlankPhase.COMPLETED, "defender_died_before_backstab");
                continue;
            }
            if (!engagement.fixer.alive) {
                changeFlankPhase(flanker, FlankPhase.ABORTED, "fixer_lost");
                continue;
            }
            if (flanker.flankPhase == FlankPhase.CONTESTED ||
                    flanker.flankPhase == FlankPhase.ABORTED ||
                    flanker.flankPhase == FlankPhase.COMPLETED) continue;
            if (flanker.tacticalIntent != TacticalIntent.FLANK ||
                    flanker.threatResponse == ThreatResponse.ENGAGE) {
                changeFlankPhase(flanker, FlankPhase.CONTESTED, "credible_interceptor_threat");
                continue;
            }

            var rearAngle = Math.abs(angleDifference(defender.angle,
                    flanker.pos.minus(defender.pos).radians()));
            var previousRearAngle = flanker.rearAngle;
            var gainRate = dt <= 0 || previousRearAngle == 0 && flanker.previousRearAngle == 0 ?
                    0 : (rearAngle - previousRearAngle) / dt;
            flanker.rearAngleGainRate = flanker.rearAngleGainRate * 0.82 + gainRate * 0.18;
            flanker.previousRearAngle = previousRearAngle;
            flanker.rearAngle = rearAngle;
            var fixerPressure = fixerMaintainsPressure(engagement);
            flanker.flankFixerLostTime = fixerPressure ? 0 : flanker.flankFixerLostTime + dt;

            if (flanker.flankPhase == FlankPhase.APPROACH_SIDE) {
                if ((flanker.pos.minus(engagement.flankerDestination()).hypotenuse() <= 0.58 ||
                        rearAngle >= Math.toRadians(82)) &&
                        fixerPressure && legalRearDestination(engagement)) {
                    logTactical("FLANK_SIDE_REACHED", flankFields(flanker, defender));
                    changeFlankPhase(flanker, FlankPhase.GAIN_REAR_ANGLE,
                            engagement.flankerSlot.name().toLowerCase() + "_slot_reached");
                    updateRearDestination(engagement, 0);
                } else if (simulationTime - flanker.flankPhaseStartedAt >= 4.50)
                    changeFlankPhase(flanker, FlankPhase.ABORTED, "side_approach_stalled");
                continue;
            }

            if (flanker.flankFixerLostTime >= 1.10) {
                changeFlankPhase(flanker, FlankPhase.ABORTED, "fixer_lost");
                continue;
            }
            if (!legalRearDestination(engagement)) {
                changeFlankPhase(flanker, FlankPhase.ABORTED, "rear_path_blocked_by_boundary");
                continue;
            }

            if (flanker.flankPhase == FlankPhase.GAIN_REAR_ANGLE) {
                updateRearProgress(flanker, defender, dt);
                if (rearAngle >= REAR_ENTRY_ANGLE &&
                        simulationTime - flanker.flankPhaseStartedAt >= 0.45) {
                    logTactical("REAR_SECTOR_ENTERED", flankFields(flanker, defender));
                    changeFlankPhase(flanker, FlankPhase.STABILIZE_REAR, "rear_entry_angle_reached");
                } else if (flanker.rearNoProgressTime >= REAR_NO_PROGRESS_ABORT_TIME)
                    changeFlankPhase(flanker, FlankPhase.ABORTED, "no_angular_progress");
                continue;
            }

            flanker.backstabOpportunity = assessBackstabOpportunity(engagement);
            if (rearAngle < REAR_LOSS_ANGLE) {
                if (flanker.flankPhase == FlankPhase.PREPARE_BACKSTAB)
                    loseBackstabWindow(flanker, defender, "defender_turned");
                changeFlankPhase(flanker, FlankPhase.GAIN_REAR_ANGLE, "rear_sector_lost");
                continue;
            }
            if (flanker.flankPhase == FlankPhase.STABILIZE_REAR) {
                if (rearAngle >= REAR_STABLE_ANGLE && flanker.backstabOpportunity.inRange() &&
                        fixerPressure && flanker.backstabOpportunity.interceptorSafe())
                    flanker.rearStableTime += dt;
                else flanker.rearStableTime = Math.max(0, flanker.rearStableTime - dt * 1.6);
                if (flanker.rearStableTime >= REAR_STABILITY_TIME &&
                        !flanker.rearSectorStabilized) {
                    logTactical("REAR_SECTOR_STABILIZED", flankFields(flanker, defender) +
                            " stableTime=" + "%.2f".formatted(flanker.rearStableTime));
                    flanker.rearSectorStabilized = true;
                }
                flanker.backstabOpportunity = assessBackstabOpportunity(engagement);
                if (flanker.rearSectorStabilized && flanker.backstabOpportunity.valid() &&
                        simulationTime - flanker.flankPhaseStartedAt >= 0.28 &&
                        !flanker.backstabWindowLogged) {
                    logTactical("BACKSTAB_WINDOW_CREATED", opportunityFields(flanker, defender));
                    flanker.backstabWindowLogged = true;
                    changeFlankPhase(flanker, FlankPhase.PREPARE_BACKSTAB,
                            "physical_backstab_window");
                }
            }
        }
    }

    private void updateRearProgress(Musketeer flanker, Musketeer defender, double dt) {
        if (flanker.rearAngleGainRate > Math.toRadians(3))
            flanker.rearNoProgressTime = Math.max(0, flanker.rearNoProgressTime - dt * 1.8);
        else flanker.rearNoProgressTime += dt;
        if (flanker.rearNoProgressTime >= REAR_NO_PROGRESS_ADJUST_TIME &&
                flanker.flankRadiusOffset == 0) {
            flanker.flankRadiusOffset = flanker.pos.minus(defender.pos).hypotenuse() < 1.55 ? 0.20 : -0.14;
            logTactical("REAR_PROGRESS", flankFields(flanker, defender) +
                    " adjustment=radius reason=angular_stall");
        }
    }

    private boolean legalRearDestination(Engagement engagement) {
        var defender = engagement.primaryTarget;
        var flanker = engagement.flanker;
        var radius = Math.clamp(flanker.projectedThrustLength() + Musketeer.BODY_RADIUS -
                REAR_PREPARATION_MARGIN, REAR_MIN_DISTANCE + 0.20, REAR_MAX_DISTANCE - 0.08);
        var destination = defender.pos.plus(new Pos(defender.angle +
                (flanker.localSide < 0 ? -1 : 1) * REAR_STABLE_ANGLE).times(radius));
        var margin = Musketeer.BODY_RADIUS + 0.12;
        return destination.x() >= margin && destination.x() <= ARENA_WIDTH - margin &&
                destination.y() >= margin && destination.y() <= ARENA_HEIGHT - margin;
    }

    private boolean fixerMaintainsPressure(Engagement engagement) {
        var fixer = engagement.fixer;
        var defender = engagement.primaryTarget;
        if (!fixer.alive || fixer.target != defender) return false;
        var distance = fixer.pos.minus(defender.pos).hypotenuse();
        if (distance > fixer.projectedThrustLength() + Musketeer.BODY_RADIUS + 0.72 ||
                fixer.state == CombatState.RECOVERY && fixer.stateTime > 0.28) return false;
        var defenderFacesFixer = Math.abs(angleDifference(defender.angle,
                fixer.pos.minus(defender.pos).radians())) <= Math.toRadians(100);
        var armed = fixer.state == CombatState.ATTACK_PREPARE ||
                fixer.state == CombatState.ATTACK_HOLD || fixer.isAttacking();
        return defender.target == fixer && (defenderFacesFixer || armed);
    }

    private void changeFlankPhase(Musketeer flanker, FlankPhase phase, String reason) {
        if (flanker.flankPhase == phase) return;
        var previous = flanker.flankPhase;
        flanker.flankPhase = phase;
        flanker.flankPhaseStartedAt = simulationTime;
        flanker.lastFlankReason = reason;
        if (phase == FlankPhase.GAIN_REAR_ANGLE) {
            flanker.rearStableTime = 0;
            flanker.rearSectorStabilized = false;
            flanker.backstabOpportunity = BackstabOpportunity.unavailable();
        }
        logTactical("FLANK_PHASE", "agent=" + flanker.name(UiLanguage.FRENCH) +
                " from=" + previous + " to=" + phase + " reason=" + reason);
        if (phase == FlankPhase.CONTESTED)
            logTactical(scenario == SimulationScenario.MODERN_2V2 ?
                            "FLANK_INTERRUPTED_BY_INTERCEPTOR" : "FLANK_INTERRUPTED_BY_B2",
                    "agent=" + flanker.name(UiLanguage.FRENCH) +
                    " reason=" + reason + " threat=" + "%.2f".formatted(flanker.threatAssessment.score()));
        else if (phase == FlankPhase.ABORTED) {
            if (flanker.engagement != null && flanker.engagement.primaryTarget != null) {
                flanker.assignTacticalIntent(flanker.engagement, TacticalIntent.DUEL, null,
                        flanker.engagement.primaryTarget, "flank_aborted_" + reason);
                flanker.tacticalDestination = null;
            }
            logTactical(reason.equals("fixer_lost") ? "FLANK_ABORTED_FIXER_LOST" :
                    reason.equals("no_angular_progress") ? "FLANK_ABORTED_NO_PROGRESS" :
                            reason.equals("flanker_died") ? "FLANKER_DIED" : "FLANK_ABORTED",
                    "agent=" + flanker.name(UiLanguage.FRENCH) + " reason=" + reason);
        }
        else if (phase == FlankPhase.COMPLETED && reason.equals("defender_died_before_backstab"))
            logTactical("DEFENDER_DIED_BEFORE_BACKSTAB",
                    "agent=" + flanker.name(UiLanguage.FRENCH));
    }

    private String flankFields(Musketeer flanker, Musketeer defender) {
        return "agent=" + flanker.name(UiLanguage.FRENCH) +
                " target=" + defender.name(UiLanguage.FRENCH) +
                " rearAngle=" + "%.1f".formatted(Math.toDegrees(flanker.rearAngle)) +
                " gainRate=" + "%.1f".formatted(Math.toDegrees(flanker.rearAngleGainRate)) +
                " distance=" + "%.2f".formatted(flanker.pos.minus(defender.pos).hypotenuse());
    }

    private Pos calculateInterceptionPoint(Engagement engagement) {
        return assessInterception(engagement.flanker, engagement.interceptor,
                engagement.flankerDestination(), actionLockRemaining(engagement.interceptor)).point();
    }

    private InterceptionAssessment assessInterception(Musketeer supporter, Musketeer interceptor,
                                                       Pos destination, double actionLock) {
        var path = destination.minus(supporter.pos);
        var supporterSpeed = estimatedMovementSpeed(supporter);
        var interceptorSpeed = estimatedMovementSpeed(interceptor);
        Pos best = supporter.pos.plus(path.times(0.75));
        var bestSupporterEta = supporter.pos.minus(best).hypotenuse() / supporterSpeed;
        var bestInterceptorEta = actionLock +
                interceptor.pos.minus(best).hypotenuse() / interceptorSpeed;
        var bestMargin = bestInterceptorEta - bestSupporterEta;
        for (var fraction : new double[]{0.25, 0.50, 0.75}) {
            var candidate = supporter.pos.plus(path.times(fraction));
            var supporterEta = supporter.pos.minus(candidate).hypotenuse() / supporterSpeed;
            var interceptorEta = actionLock +
                    interceptor.pos.minus(candidate).hypotenuse() / interceptorSpeed;
            var margin = interceptorEta - supporterEta;
            if (margin <= INTERCEPTION_TIME_MARGIN)
                return new InterceptionAssessment(candidate, supporterEta, interceptorEta, margin);
            if (margin < bestMargin) {
                best = candidate;
                bestSupporterEta = supporterEta;
                bestInterceptorEta = interceptorEta;
                bestMargin = margin;
            }
        }
        return new InterceptionAssessment(best, bestSupporterEta, bestInterceptorEta, bestMargin);
    }

    private double actionLockRemaining(Musketeer agent) {
        if (agent.state == CombatState.BLOCK_STUN)
            return Math.max(0, BLOCK_STUN_TIME - agent.stateTime);
        if (agent.state == CombatState.RECOVERY)
            return Math.max(0, agent.recoveryTime() - agent.stateTime);
        if (agent.isAttacking())
            return Math.max(0, agent.activeTime() - agent.stateTime) + agent.recoveryTime();
        return 0;
    }

    private double estimatedMovementSpeed(Musketeer agent) {
        var speed = 1.05 + agent.stats.footwork() * 0.006;
        if (agent.isAttacking()) speed *= 0.86;
        else if (agent.state == CombatState.RECOVERY) speed *= 0.92;
        else if (agent.state == CombatState.FEINT) speed *= 0.84;
        else if (agent.isGuarding()) speed *= 0.78;
        return Math.max(0.35, speed);
    }

    private void updateInterceptionPhase(Engagement engagement, double dt) {
        var interceptor = engagement.interceptor;
        if (interceptor.interceptionPhase == null) return;
        interceptor.interceptionPhaseTime += dt;
        if (interceptor.interceptionPhase == InterceptionPhase.FAILED ||
                interceptor.interceptionPhase == InterceptionPhase.ENGAGE_FLANKER) return;
        var flanker = engagement.flanker;
        var slotDistance = flanker.pos.minus(engagement.flankerDestination()).hypotenuse();
        var flankerDistance = interceptor.pos.minus(flanker.pos).hypotenuse();
        var slot = engagement.flankerDestination();
        var path = slot.minus(flanker.pos);
        var pathDistance = distanceToSegment(interceptor.pos, flanker.pos, slot);
        var passedInterception = interceptor.committedInterceptionPoint != null &&
                flanker.pos.minus(interceptor.committedInterceptionPoint).dot(
                        slot.minus(interceptor.committedInterceptionPoint)) > 0.08;
        var attackedPrimary = flanker.target == engagement.primaryTarget &&
                (flanker.isAttacking() || flanker.state == CombatState.RECOVERY);
        var usefulArrivalTime = interceptor.tacticalDestination == null ? Double.POSITIVE_INFINITY :
                interceptor.pos.minus(interceptor.tacticalDestination).hypotenuse() /
                        estimatedMovementSpeed(interceptor);
        var flankArrivalTime = slotDistance / estimatedMovementSpeed(flanker);
        var actionLockTime = interceptor.state == CombatState.BLOCK_STUN ?
                Math.max(0, BLOCK_STUN_TIME - interceptor.stateTime) :
                interceptor.state == CombatState.RECOVERY ?
                        Math.max(0, interceptor.recoveryTime() - interceptor.stateTime) : 0;
        if (slotDistance <= 0.38 && flankerDistance > 1.65) {
            changeInterceptionPhase(interceptor, InterceptionPhase.FAILED, "flanker_reached_slot");
            return;
        }
        if (passedInterception) {
            changeInterceptionPhase(interceptor, InterceptionPhase.FAILED, "flanker_passed_interception_line");
            return;
        }
        if (attackedPrimary && flanker.threatResponse != ThreatResponse.ENGAGE) {
            changeInterceptionPhase(interceptor, InterceptionPhase.FAILED, "flanker_attacked_primary");
            return;
        }
        if (usefulArrivalTime > flankArrivalTime + 0.55 && slotDistance < 0.85) {
            changeInterceptionPhase(interceptor, InterceptionPhase.FAILED, "interceptor_too_late");
            return;
        }
        if (actionLockTime > flankArrivalTime + 0.18 && slotDistance < 1.10) {
            changeInterceptionPhase(interceptor, InterceptionPhase.FAILED, "combat_action_blocked_plan");
            return;
        }
        if (interceptor.interceptionPhase == InterceptionPhase.APPROACH_INTERCEPT_POINT &&
                interceptor.pos.minus(interceptor.tacticalDestination).hypotenuse() <= 0.45 &&
                pathDistance <= 0.48) {
            interceptor.committedInterceptionPoint = interceptor.tacticalDestination;
            changeInterceptionPhase(interceptor, InterceptionPhase.BLOCK_FLANK_PATH, "path_physically_blocked");
        } else if (interceptor.interceptionPhase == InterceptionPhase.BLOCK_FLANK_PATH &&
                interceptor.interceptionPhaseTime >= 0.20 &&
                flankerDistance <= 1.85 &&
                isTracked(flanker, interceptor) &&
                Math.abs(angleDifference(interceptor.angle,
                        flanker.pos.minus(interceptor.pos).radians())) <= Math.toRadians(75) &&
                Math.abs(angleDifference(interceptor.weaponWorldAngle(),
                        flanker.pos.minus(interceptor.pos).radians())) <= Math.toRadians(48) &&
                flankerDistance <= interceptor.projectedThrustLength() +
                        Musketeer.BODY_RADIUS + 0.72)
            changeInterceptionPhase(interceptor, InterceptionPhase.PRESENT_THREAT, "credible_weapon_threat");
    }

    private void changeInterceptionPhase(Musketeer interceptor, InterceptionPhase phase, String reason) {
        if (interceptor.interceptionPhase == phase) return;
        interceptor.interceptionPhase = phase;
        interceptor.interceptionPhaseTime = 0;
        interceptor.lastInterceptionReason = reason;
        logTactical(phase == InterceptionPhase.FAILED ? "INTERCEPTION_FAILED" : "INTERCEPTION_PHASE",
                "interceptor=" + interceptor.name(UiLanguage.FRENCH) +
                        (interceptor.plannedTarget == null ? "" :
                                " flanker=" + interceptor.plannedTarget.name(UiLanguage.FRENCH)) +
                        " phase=" + phase + " reason=" + reason);
    }

    List<Engagement> engagements() {
        return engagementManager.engagements();
    }

    List<String> tacticalLog() {
        return List.copyOf(tacticalLog);
    }

    List<DecisionTrace> decisionTraces() {
        return List.copyOf(decisionTraces);
    }

    List<KillExplanation> killExplanations() {
        return List.copyOf(killExplanations);
    }

    KillExplanation lastKillExplanation() {
        return killExplanations.isEmpty() ? null : killExplanations.getLast();
    }

    List<KillCausalChain> killCausalChains() {
        return List.copyOf(killCausalChains);
    }

    KillCausalChain lastKillCausalChain() {
        return killCausalChains.isEmpty() ? null : killCausalChains.getLast();
    }

    ThreatAssessment assessThreat(Musketeer observer, Musketeer source) {
        var awareness = observer.opponentAwareness.get(source);
        if (awareness == null || !source.alive || !awareness.visible && awareness.memoryTime <= 0)
            return new ThreatAssessment(0, false, false, Double.POSITIVE_INFINITY, ThreatResponse.IGNORE);
        var visible = awareness.visible;
        var sourcePosition = awareness.lastKnown;
        var offset = observer.pos.minus(sourcePosition);
        var distance = offset.hypotenuse();
        var proximity = Math.clamp((3.0 - distance) / 2.15, 0, 1);
        if (!visible) {
            var memoryConfidence = Math.clamp(awareness.memoryTime / 1.85, 0, 1);
            var score = proximity * (THREAT_PROXIMITY_WEIGHT + 0.16) * memoryConfidence;
            return new ThreatAssessment(score, false, false, Double.POSITIVE_INFINITY,
                    recommendedThreatResponse(score));
        }

        var direction = distance > 0.0001 ? offset.div(distance) : new Pos(awareness.observedBodyAngle);
        var relativeVelocity = awareness.perceivedVelocity.minus(observer.velocity);
        var closingMetersPerSecond = Math.max(0, relativeVelocity.dot(direction));
        var closing = Math.clamp(closingMetersPerSecond / 1.65, 0, 1);
        var bodyFacing = directionalAlignment(awareness.observedBodyAngle, direction.radians(), Math.toRadians(95));
        var weaponAlignment = directionalAlignment(awareness.observedWeaponAngle, direction.radians(),
                Math.toRadians(62));
        var readiness = switch (awareness.observedState) {
            case ATTACK_ACTIVE, CHAMBER_ACTIVE -> 1.0;
            case ATTACK_HOLD, CHAMBER_HOLD -> 0.88;
            case ATTACK_PREPARE, CHAMBER_PREPARE -> 0.68;
            case FEINT -> 0.48;
            case NEUTRAL -> 0.20;
            case BLOCK_PREPARE, BLOCKING, DOUBLE_BLOCK -> 0.15;
            case RECOVERY, BLOCK_STUN, DEAD -> 0;
        };
        var projectedRange = awareness.observedProjectedRange + Musketeer.BODY_RADIUS + 0.32;
        var rangeFactor = Math.clamp(1 - Math.max(0, distance - projectedRange) / 1.25, 0, 1);
        var lineOfAttack = weaponAlignment * rangeFactor *
                (hasObservableAttackLine(observer, source, sourcePosition) ? 1 : 0.35);
        var vulnerability = observer.state == CombatState.RECOVERY || observer.isAttacking() ? 1 :
                observer.state == CombatState.ATTACK_HOLD ? 0.62 : observer.isGuarding() ? 0.05 : 0.30;
        var slot = observer.engagement != null && observer.engagement.type == EngagementType.TWO_VS_ONE ?
                observer.engagement.flankerDestination() : observer.pos;
        var sourceToSlot = distanceToSegment(sourcePosition, observer.pos, slot);
        var flankBlocked = Math.clamp((1.15 - sourceToSlot) / 1.15, 0, 1);
        var escapeDirection = observer.pos.minus(sourcePosition);
        var escapeRoom = escapeDirection.hypotenuse() < 0.0001 ? 0 :
                distanceToBoundary(observer.pos, escapeDirection.div(escapeDirection.hypotenuse()));
        var escapePossibility = Math.clamp(escapeRoom / 1.6, 0, 1);
        var score = THREAT_PROXIMITY_WEIGHT * proximity +
                THREAT_CLOSING_WEIGHT * closing +
                THREAT_BODY_FACING_WEIGHT * bodyFacing +
                THREAT_WEAPON_ALIGNMENT_WEIGHT * weaponAlignment +
                THREAT_READINESS_WEIGHT * readiness +
                THREAT_LINE_WEIGHT * lineOfAttack +
                0.05 * vulnerability + 0.05 * flankBlocked - 0.04 * escapePossibility;
        score = Math.clamp(score, 0, 1);
        var estimatedImpactTime = closingMetersPerSecond > 0.05 ?
                Math.max(0, distance - projectedRange) / closingMetersPerSecond :
                distance <= projectedRange && weaponAlignment >= 0.72 ? 0 : Double.POSITIVE_INFINITY;
        var immediate = lineOfAttack >= 0.68 && readiness >= 0.68 &&
                (distance <= projectedRange + 0.20 || estimatedImpactTime <= 0.32);
        return new ThreatAssessment(score, true, immediate, estimatedImpactTime,
                recommendedThreatResponse(score, immediate));
    }

    private ThreatResponse recommendedThreatResponse(double score) {
        return recommendedThreatResponse(score, false);
    }

    private ThreatResponse recommendedThreatResponse(double score, boolean immediate) {
        if (score >= THREAT_ENGAGE_THRESHOLD && immediate) return ThreatResponse.ENGAGE;
        if (score >= THREAT_ATTENTION_THRESHOLD) return ThreatResponse.DEFENSIVE_ATTENTION;
        if (score >= THREAT_ADJUST_THRESHOLD) return ThreatResponse.ADJUST_PATH;
        return ThreatResponse.IGNORE;
    }

    private double directionalAlignment(double angle, double target, double maximumDifference) {
        return Math.clamp(1 - Math.abs(angleDifference(angle, target)) / maximumDifference, 0, 1);
    }

    private boolean hasObservableAttackLine(Musketeer observer, Musketeer source, Pos sourcePosition) {
        for (var agent : agents) {
            if (agent == observer || agent == source || !agent.alive) continue;
            if (agent.team != observer.team && !isTracked(observer, agent)) continue;
            var perceivedPosition = agent.team == observer.team ? agent.pos : known(observer, agent);
            if (distanceToSegment(perceivedPosition, sourcePosition, observer.pos) <
                    Musketeer.BODY_RADIUS * 1.35)
                return false;
        }
        return true;
    }

    private double distanceToSegment(Pos point, Pos start, Pos end) {
        var segment = end.minus(start);
        var lengthSquared = segment.dot(segment);
        if (lengthSquared < 0.000001) return point.minus(start).hypotenuse();
        var fraction = Math.clamp(point.minus(start).dot(segment) / lengthSquared, 0, 1);
        return point.minus(start.plus(segment.times(fraction))).hypotenuse();
    }

    private double distanceToBoundary(Pos point, Pos direction) {
        var distance = Double.POSITIVE_INFINITY;
        if (direction.x() > 0.0001) distance = Math.min(distance, (ARENA_WIDTH - point.x()) / direction.x());
        else if (direction.x() < -0.0001) distance = Math.min(distance, -point.x() / direction.x());
        if (direction.y() > 0.0001) distance = Math.min(distance, (ARENA_HEIGHT - point.y()) / direction.y());
        else if (direction.y() < -0.0001) distance = Math.min(distance, -point.y() / direction.y());
        return Double.isFinite(distance) ? Math.max(0, distance) : 0;
    }

    void updateEngagementInterceptions(double dt) {
        for (var engagement : engagementManager.engagements()) {
            if (engagement.type != EngagementType.TWO_VS_ONE ||
                    engagement.status == EngagementStatus.BROKEN ||
                    engagement.status == EngagementStatus.FINISHED ||
                    engagement.interceptor == null) continue;
            var flanker = engagement.flanker;
            var interceptor = engagement.interceptor;
            if (!flanker.alive || !interceptor.alive) continue;
            var previousRecommendation = flanker.threatAssessment.recommendedResponse();
            flanker.tacticalThreat = interceptor;
            flanker.threatAssessment = assessThreat(flanker, interceptor);
            if (previousRecommendation != flanker.threatAssessment.recommendedResponse() ||
                    Double.isNaN(flanker.lastThreatLoggedScore) ||
                    Math.abs(flanker.lastThreatLoggedScore - flanker.threatAssessment.score()) >= 0.08) {
                logTactical("THREAT_ASSESSMENT", "observer=" + flanker.name(UiLanguage.FRENCH) +
                        " source=" + interceptor.name(UiLanguage.FRENCH) +
                        " score=" + "%.2f".formatted(flanker.threatAssessment.score()) +
                        " response=" + flanker.threatAssessment.recommendedResponse());
                flanker.lastThreatLoggedScore = flanker.threatAssessment.score();
            }
            updateThreatResponse(flanker, dt);
            if (flanker.threatResponse == ThreatResponse.ENGAGE)
                contestEngagement(engagement);
            if (engagement.status != EngagementStatus.CONTESTED) continue;
            if (hasEstablishedFlankerEngagement(engagement)) {
                if (interceptor.interceptionPhase != InterceptionPhase.ENGAGE_FLANKER)
                    changeInterceptionPhase(interceptor, InterceptionPhase.ENGAGE_FLANKER,
                            "mutual_engagement_established");
                engagement.contestedTime += dt;
            } else engagement.contestedTime = 0;
            if (canCreateInterceptionDuels(engagement)) {
                createInterceptionDuels(engagement);
                return;
            }
        }
    }

    void updateThreatResponse(Musketeer flanker, double dt) {
        var recommended = flanker.threatAssessment.recommendedResponse();
        if (flanker.threatResponse == ThreatResponse.ENGAGE &&
                flanker.threatAssessment.score() > THREAT_RESUME_THRESHOLD)
            recommended = ThreatResponse.ENGAGE;
        if (recommended != flanker.pendingThreatResponse) {
            flanker.pendingThreatResponse = recommended;
            var escalating = recommended.ordinal() > flanker.threatResponse.ordinal();
            flanker.threatResponseDelay = (escalating ? 0.06 : 0.14) +
                    (100 - flanker.stats.reaction()) * 0.0022 +
                    (100 - flanker.stats.decisionMaking()) * 0.0011;
        }
        if (recommended == flanker.threatResponse) {
            flanker.threatResponseDelay = 0;
            return;
        }
        flanker.threatResponseDelay = Math.max(0, flanker.threatResponseDelay - dt);
        if (flanker.threatResponseDelay > 0) return;
        var previous = flanker.threatResponse;
        flanker.threatResponse = recommended;
        flanker.tacticalIntentTime = 0;
        flanker.lastIntentReason = switch (recommended) {
            case IGNORE -> "observable_threat_cleared";
            case ADJUST_PATH -> "flank_path_contested";
            case DEFENSIVE_ATTENTION -> "credible_interceptor_attention";
            case ENGAGE -> "credible_immediate_threat";
        };
        logTactical(recommended == ThreatResponse.ADJUST_PATH ||
                        recommended == ThreatResponse.DEFENSIVE_ATTENTION ? "FLANK_CONTESTED" : "THREAT_RESPONSE",
                "agent=" + flanker.name(UiLanguage.FRENCH) +
                        (flanker.tacticalThreat == null ? "" :
                                " source=" + flanker.tacticalThreat.name(UiLanguage.FRENCH)) +
                        " from=" + previous + " to=" + recommended +
                        " score=" + "%.2f".formatted(flanker.threatAssessment.score()));
    }

    private void contestEngagement(Engagement engagement) {
        var flanker = engagement.flanker;
        var interceptor = engagement.interceptor;
        if (scenario == SimulationScenario.MODERN_2V2 && supportPlan != null &&
                supportPlan.engagement == engagement &&
                supportPlan.state != SupportPlanState.INTERCEPTED) {
            if (Double.isNaN(supportPlan.localAdvantageAt))
                ++modernMetrics.supporterInterceptedBeforeJoining;
            supportPlan.changeState(SupportPlanState.INTERCEPTED, simulationTime);
            logTactical("SUPPORT_PLAN_INTERCEPTED", "supporter=" +
                    flanker.name(UiLanguage.FRENCH) +
                    " interceptor=" + interceptor.name(UiLanguage.FRENCH) +
                    " phase=" + interceptor.interceptionPhase);
        }
        if (engagement.status != EngagementStatus.CONTESTED) {
            var oldStatus = engagement.status;
            engagement.status = EngagementStatus.CONTESTED;
            engagement.contestedTime = 0;
            logTactical("ENGAGEMENT_STATUS", "from=TWO_VS_ONE_" + oldStatus +
                    " to=TWO_VS_ONE_CONTESTED");
        }
        if (flanker.tacticalIntent != TacticalIntent.DUEL) {
            var previous = flanker.tacticalIntent;
            changeFlankPhase(flanker, FlankPhase.CONTESTED, "credible_interceptor_threat");
            flanker.assignTacticalIntent(engagement, TacticalIntent.DUEL, null, interceptor,
                    "credible_immediate_threat");
            changeTarget(flanker, interceptor, DUEL_LOCK_TIME);
            logTactical("INTENT_CHANGE", "agent=" + flanker.name(UiLanguage.FRENCH) +
                    " from=" + previous + " to=DUEL target=" + interceptor.name(UiLanguage.FRENCH) +
                    " reason=credible_immediate_threat score=" +
                    "%.2f".formatted(flanker.threatAssessment.score()));
        }
        changeTarget(interceptor, flanker, Math.max(interceptor.targetLockTime, DUEL_LOCK_TIME));
    }

    private boolean hasEstablishedFlankerEngagement(Engagement engagement) {
        var flanker = engagement.flanker;
        var interceptor = engagement.interceptor;
        if (interceptor.interceptionPhase != InterceptionPhase.PRESENT_THREAT &&
                interceptor.interceptionPhase != InterceptionPhase.ENGAGE_FLANKER ||
                !engagement.fixer.alive || !flanker.alive || !engagement.primaryTarget.alive ||
                !interceptor.alive || flanker.threatResponse != ThreatResponse.ENGAGE ||
                flanker.target != interceptor || interceptor.target != flanker) return false;
        var mutuallyFacing = Math.abs(angleDifference(flanker.angle,
                interceptor.pos.minus(flanker.pos).radians())) <= Math.toRadians(85) &&
                Math.abs(angleDifference(interceptor.angle,
                        flanker.pos.minus(interceptor.pos).radians())) <= Math.toRadians(85);
        var primaryOffset = engagement.primaryTarget.pos.minus(flanker.pos);
        var freeAttackOnPrimary = primaryOffset.hypotenuse() <=
                flanker.projectedThrustLength() + Musketeer.BODY_RADIUS + 0.20 &&
                Math.abs(angleDifference(flanker.weaponWorldAngle(), primaryOffset.radians())) <
                        Math.toRadians(30);
        return mutuallyFacing && !freeAttackOnPrimary;
    }

    private boolean canCreateInterceptionDuels(Engagement engagement) {
        return engagement.interceptor.interceptionPhase == InterceptionPhase.ENGAGE_FLANKER &&
                engagement.contestedTime >= INTERCEPTION_CONTEST_TIME &&
                hasEstablishedFlankerEngagement(engagement);
    }

    private void createInterceptionDuels(Engagement engagement) {
        engagement.status = EngagementStatus.BROKEN;
        logTactical("ENGAGEMENT_BROKEN", "reason=flanker_intercepted");
        clearLocalRole(engagement.fixer);
        clearLocalRole(engagement.flanker);
        var first = engagementManager.createDuel(engagement.fixer, engagement.primaryTarget);
        var second = engagementManager.createDuel(engagement.flanker, engagement.interceptor);
        first.activate();
        second.activate();
        changeTarget(first.duelistOne, first.duelistTwo, DUEL_LOCK_TIME);
        changeTarget(first.duelistTwo, first.duelistOne, DUEL_LOCK_TIME);
        changeTarget(second.duelistOne, second.duelistTwo, DUEL_LOCK_TIME);
        changeTarget(second.duelistTwo, second.duelistOne, DUEL_LOCK_TIME);
        logTactical("DUEL_CREATED", "agents=" + first.duelistOne.name(UiLanguage.FRENCH) +
                "," + first.duelistTwo.name(UiLanguage.FRENCH));
        logTactical("DUEL_CREATED", "agents=" + second.duelistOne.name(UiLanguage.FRENCH) +
                "," + second.duelistTwo.name(UiLanguage.FRENCH));
        if (scenario == SimulationScenario.MODERN_2V2) {
            modernStructureTime = MODERN_ENGAGEMENT_MIN_TIME;
            lastModernReconstructionReason = "successful_interception";
            if (supportPlan != null)
                finishSupportPlan(SupportPlanState.INTERCEPTED,
                        "interception_created_two_duels", false);
        }
    }

    private void logTactical(String event, String fields) {
        if (tacticalLog.size() == 4_096) tacticalLog.removeFirst();
        tacticalLog.addLast("[%.2f] %s %s".formatted(simulationTime, event, fields));
    }

    private void traceDecision(Musketeer agent, DecisionCategory category, String action,
                               String reason, Musketeer target, Pos destination, double score,
                               double availableWindow, double impactEta, double revengeRisk,
                               String rejectedAlternative) {
        if (decisionTraces.size() >= 32) decisionTraces.removeFirst();
        decisionTraces.addLast(new DecisionTrace(simulationTime, agent, category, action,
                reason, target, destination, score, availableWindow, impactEta,
                revengeRisk, rejectedAlternative));
    }

    private void updateCombatPhase(double dt) {
        combatPhaseTime += dt;
        switch (combatPhase) {
            case FRONT_LINE -> {
                if (frontLineDisrupted()) {
                    beginResolution();
                    return;
                }
                for (var agent : agents) {
                    if (!agent.alive || agent.openingOpponent == null || !agent.openingOpponent.alive) continue;
                    if (agent.openingOpponent.pos.minus(agent.pos).hypotenuse() <= OPENING_CONTACT_DISTANCE)
                        frontLineContact = true;
                }
                if (hasOpeningStab()) beginResolution();
                else if (frontLineContact && allOpeningChoices(OpeningChoice.ROTATE)) beginRotation();
                else if (frontLineContact && combatPhaseTime >= 6.0) {
                    for (var agent : agents)
                        if (agent.alive && agent.openingChoice == OpeningChoice.UNDECIDED)
                            agent.openingChoice = OpeningChoice.ROTATE;
                    beginRotation();
                }
            }
            case ROTATION -> {
                if (rotationDisrupted()) beginResolution();
            }
            case RESOLUTION -> {
                if (canReformFrontLine()) beginFrontLine();
            }
        }
    }

    private boolean frontLineDisrupted() {
        if (countAlive(blueTeam) != 2 || countAlive(redTeam) != 2) return true;
        for (var agent : agents)
            if (agent.state == CombatState.BLOCK_STUN || agent.state == CombatState.RECOVERY ||
                    agent.hitPoints < 100) return true;
        return false;
    }

    private boolean allOpeningChoices(OpeningChoice choice) {
        for (var agent : agents)
            if (agent.alive && agent.openingChoice != choice) return false;
        return true;
    }

    private boolean hasOpeningStab() {
        for (var agent : agents)
            if (agent.alive && agent.openingChoice == OpeningChoice.STAB) return true;
        return false;
    }

    private boolean rotationDisrupted() {
        if (countAlive(blueTeam) != 2 || countAlive(redTeam) != 2) return true;
        for (var agent : agents) {
            if (agent.state == CombatState.BLOCK_STUN || agent.state == CombatState.RECOVERY ||
                    agent.isAttacking() || agent.backEscapeTime > 0 || agent.hitPoints < 100) return true;
            if (combatPhaseTime >= 0.25 && agent.localRole != LocalRole.FREE) return true;
        }
        return false;
    }

    private void beginFrontLine() {
        combatPhase = CombatPhase.FRONT_LINE;
        combatPhaseTime = 0;
        frontLineContact = false;
        var rotationDirection = chooseRotationDirection();
        assignFrontLinePairs(blueTeam, redTeam);
        assignFrontLinePairs(redTeam, blueTeam);
        for (var agent : agents) {
            agent.openingChoice = OpeningChoice.UNDECIDED;
            agent.rotationSide = agent.team == Team.BLUE ? rotationDirection : -rotationDirection;
            agent.localHoldTransferTime = 0;
            clearLocalRole(agent);
            abandonPressurePlan(agent);
            endDezone(agent);
            beginEngagementPhase(agent, EngagementPhase.PROBE);
        }
    }

    private int chooseRotationDirection() {
        double total = 0;
        var living = 0;
        for (var agent : agents) {
            if (!agent.alive) continue;
            total += agent.getY();
            ++living;
        }
        if (living == 0) return 1;
        var average = total / living;
        if (average < ARENA_HEIGHT / 2 - 0.35) return 1;
        if (average > ARENA_HEIGHT / 2 + 0.35) return -1;
        return random.nextBoolean() ? -1 : 1;
    }

    private void assignFrontLinePairs(Musketeer[] team, Musketeer[] opponents) {
        var first = team[0].getY() <= team[1].getY() ? team[0] : team[1];
        var second = first == team[0] ? team[1] : team[0];
        var firstOpponent = opponents[0].getY() <= opponents[1].getY() ? opponents[0] : opponents[1];
        var secondOpponent = firstOpponent == opponents[0] ? opponents[1] : opponents[0];
        configureFrontLineAgent(first, firstOpponent);
        configureFrontLineAgent(second, secondOpponent);
    }

    private void configureFrontLineAgent(Musketeer agent, Musketeer opponent) {
        agent.openingOpponent = opponent;
        changeTarget(agent, opponent, 1.20);
    }

    private void beginRotation() {
        combatPhase = CombatPhase.ROTATION;
        combatPhaseTime = 0;
        for (var agent : agents) {
            if (!agent.alive) continue;
            if (agent.openingChoice == OpeningChoice.UNDECIDED) agent.openingChoice = OpeningChoice.ROTATE;
            beginEngagementPhase(agent, EngagementPhase.PROBE);
        }
    }

    private void beginResolution() {
        if (combatPhase == CombatPhase.RESOLUTION) return;
        combatPhase = CombatPhase.RESOLUTION;
        combatPhaseTime = 0;
        for (var agent : agents)
            if (agent.alive) beginEngagementPhase(agent, EngagementPhase.PROBE);
    }

    private boolean canReformFrontLine() {
        if (combatPhaseTime < 1.40 || countAlive(blueTeam) != 2 || countAlive(redTeam) != 2 ||
                minimumOpposingDistance() < REFORM_DISTANCE ||
                Math.abs(blueTeam[0].getX() - blueTeam[1].getX()) > 0.78 ||
                Math.abs(redTeam[0].getX() - redTeam[1].getX()) > 0.78) return false;
        for (var agent : agents)
            if (!agent.alive || agent.state != CombatState.NEUTRAL || agent.pressureReceived >= 0.08 ||
                    agent.rearThreat != null) return false;
        return true;
    }

    private double minimumOpposingDistance() {
        var minimum = Double.POSITIVE_INFINITY;
        for (var blue : blueTeam)
            if (blue.alive)
                for (var red : redTeam)
                    if (red.alive) minimum = Math.min(minimum, red.pos.minus(blue.pos).hypotenuse());
        return minimum;
    }

    private void updatePressureRoles() {
        for (var agent : agents) agent.pressureRole = PressureRole.NONE;
        var blueAlive = countAlive(blueTeam);
        var redAlive = countAlive(redTeam);
        if (blueAlive == 2 && redAlive == 1) assignPressureRoles(blueTeam, livingAgent(redTeam));
        else if (redAlive == 2 && blueAlive == 1) assignPressureRoles(redTeam, livingAgent(blueTeam));
        for (var agent : agents)
            if (agent.pressureContext == PressureContext.OUTNUMBERING &&
                    agent.pressureRole == PressureRole.NONE) abandonPressurePlan(agent);
    }

    private int countAlive(Musketeer[] team) {
        int count = 0;
        for (var agent : team) if (agent.alive) ++count;
        return count;
    }

    private Musketeer livingAgent(Musketeer[] team) {
        for (var agent : team) if (agent.alive) return agent;
        return null;
    }

    private void assignPressureRoles(Musketeer[] team, Musketeer target) {
        if (target == null) return;
        var trigger = team[0].stats.pressureAbility() >= team[1].stats.pressureAbility() ? team[0] : team[1];
        var anchor = team[0] == trigger ? team[1] : team[0];
        trigger.pressureRole = PressureRole.TRIGGER;
        anchor.pressureRole = PressureRole.ANCHOR;
        trigger.pressureSide = 1;
        anchor.pressureSide = -1;
        trigger.pressureFixedTarget = target;
        anchor.pressureFixedTarget = target;
    }

    private void updateLocalCoordination(double dt) {
        for (var agent : agents) {
            if (agent.localRoleTime > 0) agent.localRoleTime = Math.max(0, agent.localRoleTime - dt);
            agent.localThreatByOpponent.clear();
            if (!agent.alive) {
                clearLocalRole(agent);
                continue;
            }
            for (var opponent : opponentsOf(agent))
                if (opponent.alive) agent.localThreatByOpponent.put(opponent, localThreat(agent, opponent));
        }
        if (combatPhase == CombatPhase.FRONT_LINE ||
                combatPhase == CombatPhase.ROTATION && combatPhaseTime < 0.65) {
            for (var agent : agents) clearLocalRole(agent);
            return;
        }
        assignLocalRoles(blueTeam, redTeam);
        assignLocalRoles(redTeam, blueTeam);
    }

    private double localThreat(Musketeer attacker, Musketeer defender) {
        if (!attacker.alive || !defender.alive || !isTracked(attacker, defender)) return 0;
        var offset = known(attacker, defender).minus(attacker.pos);
        var distance = offset.hypotenuse();
        if (distance > LOCAL_THREAT_RANGE || distance < 0.0001) return 0;
        var reach = attacker.projectedThrustLength() + Musketeer.BODY_RADIUS;
        var distanceQuality = Math.clamp(1 - Math.max(0, distance - reach) / 2.15, 0.08, 1);
        var alignment = Math.clamp(1 - Math.abs(angleDifference(attacker.weaponWorldAngle(), offset.radians())) /
                Math.toRadians(105), 0.12, 1);
        var state = switch (attacker.state) {
            case ATTACK_HOLD, CHAMBER_HOLD -> 1.18;
            case ATTACK_ACTIVE, CHAMBER_ACTIVE -> 1.26;
            case ATTACK_PREPARE, CHAMBER_PREPARE, FEINT -> 1.02;
            case BLOCK_PREPARE, BLOCKING, DOUBLE_BLOCK -> 0.72;
            case RECOVERY -> 0.28;
            case BLOCK_STUN -> 0.10;
            default -> 0.66;
        };
        var direction = offset.div(distance);
        var closingSpeed = attacker.velocity.dot(direction);
        var movement = Math.clamp(0.82 + closingSpeed * 0.18, 0.55, 1.16);
        var attention = attacker.target == defender ? 1.14 : 0.86;
        var perception = isVisible(attacker, defender) ? 1.0 : 0.62;
        return Math.clamp(distanceQuality * (0.35 + alignment * 0.65) * state * movement * attention *
                perception, 0, 1.4);
    }

    private void assignLocalRoles(Musketeer[] team, Musketeer[] opponents) {
        Musketeer first = null, second = null, enemyOne = null, enemyTwo = null;
        var alliesAlive = 0;
        for (var agent : team) {
            if (!agent.alive) continue;
            if (first == null) first = agent;
            else if (second == null) second = agent;
            ++alliesAlive;
        }
        var enemiesAlive = 0;
        for (var opponent : opponents) {
            if (!opponent.alive) continue;
            if (enemyOne == null) enemyOne = opponent;
            else if (enemyTwo == null) enemyTwo = opponent;
            ++enemiesAlive;
        }
        if (alliesAlive != 2 || enemiesAlive == 0) {
            for (var agent : team) clearLocalRole(agent);
            return;
        }
        if (enemiesAlive == 1) {
            var fixer = first.pressureRole == PressureRole.ANCHOR ? first :
                    second.pressureRole == PressureRole.ANCHOR ? second :
                            localFixerValue(first, enemyOne) >= localFixerValue(second, enemyOne) ? first : second;
            var flanker = fixer == first ? second : first;
            setLocalRole(fixer, LocalRole.FIXER, enemyOne, 0.32);
            setLocalRole(flanker, LocalRole.FLANKER, enemyOne, 0.32);
            setLocalFlankSide(flanker, enemyOne);
            fixer.localSide = -flanker.localSide;
            return;
        }
        if (enemiesAlive != 2) {
            clearLocalRole(first);
            clearLocalRole(second);
            return;
        }

        var uncovered = uncoveredEnemy(first, second, enemyOne, enemyTwo);
        if (uncovered != null) {
            assignCoveragePlan(first, second, uncovered, uncovered == enemyOne ? enemyTwo : enemyOne);
            return;
        }
        if (localPlanActive(first, second)) return;

        var firstScore = localOverloadScore(first, second, enemyOne, enemyTwo);
        var secondScore = localOverloadScore(first, second, enemyTwo, enemyOne);
        var target = firstScore >= secondScore ? enemyOne : enemyTwo;
        var teamwork = pairTeamwork(first, second);
        if (Math.max(firstScore, secondScore) >= LOCAL_OVERLOAD_THRESHOLD + (1 - teamwork) * 0.42) {
            assignOverloadPlan(first, second, target);
            return;
        }
        setLocalRole(first, LocalRole.FREE, null, 0.18);
        setLocalRole(second, LocalRole.FREE, null, 0.18);
    }

    private Musketeer uncoveredEnemy(Musketeer first, Musketeer second, Musketeer enemyOne,
                                      Musketeer enemyTwo) {
        var shared = first.target == second.target ? first.target :
                first.localObjective == second.localObjective ? first.localObjective : null;
        if (shared == null || shared != enemyOne && shared != enemyTwo) return null;
        var uncovered = shared == enemyOne ? enemyTwo : enemyOne;
        var danger = Math.max(uncovered.localThreatByOpponent.getOrDefault(first, 0.0),
                uncovered.localThreatByOpponent.getOrDefault(second, 0.0));
        var nearest = Math.min(uncovered.pos.minus(first.pos).hypotenuse(),
                uncovered.pos.minus(second.pos).hypotenuse());
        var teamwork = pairTeamwork(first, second);
        return danger >= 0.50 - teamwork * 0.24 || nearest <= 2.35 + teamwork * 0.42 ? uncovered : null;
    }

    private void assignCoveragePlan(Musketeer first, Musketeer second, Musketeer uncovered,
                                    Musketeer fixedTarget) {
        var firstValue = first.localThreatByOpponent.getOrDefault(uncovered, 0.0) +
                first.stats.decisionMaking() * 0.003 + first.stats.footwork() * 0.002 +
                first.stats.teamworkAbility() * 0.004 -
                localCommitmentPenalty(first);
        var secondValue = second.localThreatByOpponent.getOrDefault(uncovered, 0.0) +
                second.stats.decisionMaking() * 0.003 + second.stats.footwork() * 0.002 +
                second.stats.teamworkAbility() * 0.004 -
                localCommitmentPenalty(second);
        var cover = firstValue >= secondValue ? first : second;
        var fixer = cover == first ? second : first;
        setLocalRole(cover, LocalRole.COVER, uncovered, 0.46);
        setLocalRole(fixer, LocalRole.FIXER, fixedTarget, 0.46);
        setLocalFlankSide(cover, uncovered);
        fixer.localSide = -cover.localSide;
    }

    private double localCommitmentPenalty(Musketeer agent) {
        return agent.isAttacking() || agent.state == CombatState.RECOVERY ? 0.65 :
                agent.state == CombatState.ATTACK_PREPARE || agent.state == CombatState.ATTACK_HOLD ? 0.28 : 0;
    }

    private boolean localPlanActive(Musketeer first, Musketeer second) {
        return first.localRoleTime > 0 && second.localRoleTime > 0 &&
                first.localObjective != null && first.localObjective.alive &&
                second.localObjective != null && second.localObjective.alive;
    }

    private double localOverloadScore(Musketeer first, Musketeer second, Musketeer target,
                                      Musketeer otherEnemy) {
        var firstThreat = first.localThreatByOpponent.getOrDefault(target, 0.0);
        var secondThreat = second.localThreatByOpponent.getOrDefault(target, 0.0);
        if (firstThreat < 0.24 || secondThreat < 0.24) return Double.NEGATIVE_INFINITY;
        var teamwork = pairTeamwork(first, second);
        var supportRange = 2.05 + teamwork * 0.55;
        if (target.pos.minus(first.pos).hypotenuse() > supportRange ||
                target.pos.minus(second.pos).hypotenuse() > supportRange) return Double.NEGATIVE_INFINITY;
        var otherDanger = Math.max(otherEnemy.localThreatByOpponent.getOrDefault(first, 0.0),
                otherEnemy.localThreatByOpponent.getOrDefault(second, 0.0));
        var otherDistance = Math.min(otherEnemy.pos.minus(first.pos).hypotenuse(),
                otherEnemy.pos.minus(second.pos).hypotenuse());
        var separation = Math.clamp((otherDistance - 1.35) / 1.75, 0, 1);
        var vulnerability = switch (target.state) {
            case BLOCK_STUN, RECOVERY -> 0.42;
            case BLOCK_PREPARE, BLOCKING, DOUBLE_BLOCK -> 0.18;
            default -> target.target != first && target.target != second ? 0.16 : 0;
        };
        var sharedFocus = first.target == target && second.target == target ? 0.16 : 0;
        if (otherDanger > 0.58 + teamwork * 0.24 && vulnerability < 0.40)
            return Double.NEGATIVE_INFINITY;
        return firstThreat + secondThreat + separation * 0.42 + vulnerability + sharedFocus +
                teamwork * 0.48 - otherDanger * (0.52 + teamwork * 0.38);
    }

    private void assignOverloadPlan(Musketeer first, Musketeer second, Musketeer target) {
        var firstPlan = localFixerValue(first, target) + localFlankerValue(second, target);
        var secondPlan = localFixerValue(second, target) + localFlankerValue(first, target);
        var fixer = firstPlan >= secondPlan ? first : second;
        var flanker = fixer == first ? second : first;
        var duration = 0.30 + Math.min(fixer.stats.decisionMaking(), flanker.stats.decisionMaking()) * 0.002 +
                Math.min(fixer.stats.teamworkAbility(), flanker.stats.teamworkAbility()) * 0.003;
        setLocalRole(fixer, LocalRole.FIXER, target, duration);
        setLocalRole(flanker, LocalRole.FLANKER, target, duration);
        setLocalFlankSide(flanker, target);
        fixer.localSide = -flanker.localSide;
    }

    private double localFixerValue(Musketeer agent, Musketeer target) {
        var towardAgent = agent.pos.minus(target.pos).radians();
        var front = 1 - Math.abs(angleDifference(target.angle, towardAgent)) / Math.PI;
        var attention = target.target == agent ? 0.48 : 0;
        var armed = agent.state == CombatState.ATTACK_HOLD || agent.state == CombatState.ATTACK_PREPARE ? 0.34 : 0;
        return agent.stats.pressureAbility() * 0.006 + agent.stats.decisionMaking() * 0.003 +
                agent.stats.teamworkAbility() * 0.002 + front * 0.45 + attention + armed;
    }

    private double localFlankerValue(Musketeer agent, Musketeer target) {
        var towardAgent = agent.pos.minus(target.pos).radians();
        var side = Math.abs(angleDifference(target.angle, towardAgent)) / Math.PI;
        return agent.stats.footwork() * 0.005 + agent.stats.zoneExit() * 0.003 +
                agent.stats.decisionMaking() * 0.0025 + agent.stats.teamworkAbility() * 0.0025 +
                side * 0.72 + (target.target == agent ? 0 : 0.24);
    }

    private double pairTeamwork(Musketeer first, Musketeer second) {
        return Math.min(first.stats.teamworkAbility(), second.stats.teamworkAbility()) / 100.0;
    }

    private void setLocalFlankSide(Musketeer agent, Musketeer target) {
        var relative = angleDifference(target.angle, agent.pos.minus(target.pos).radians());
        if (Math.abs(relative) < 0.05)
            agent.localSide = movementSpace(agent, -1) >= movementSpace(agent, 1) ? -1 : 1;
        else agent.localSide = relative < 0 ? -1 : 1;
    }

    private void setLocalRole(Musketeer agent, LocalRole role, Musketeer objective, double duration) {
        agent.localRole = role;
        agent.localObjective = objective;
        agent.localRoleTime = duration;
    }

    private void clearLocalRole(Musketeer agent) {
        agent.localRole = LocalRole.FREE;
        agent.localObjective = null;
        agent.localRoleTime = 0;
        agent.localHoldTransferTime = 0;
    }

    private void updatePressure(double dt) {
        for (var defender : agents) {
            defender.pressureSource = null;
            defender.pressureReceived = 0;
            if (!defender.alive) {
                defender.pressureByOpponent.clear();
                continue;
            }
            double strongest = 0, second = 0;
            for (var attacker : opponentsOf(defender)) {
                var previous = defender.pressureByOpponent.getOrDefault(attacker, 0.0);
                var signal = pressureSignal(attacker, defender);
                var current = signal > 0 ? previous + (signal - previous) * Math.min(1, dt * 4.6) :
                        Math.max(0, previous - dt * (0.72 + defender.stats.reaction() * 0.006));
                defender.pressureByOpponent.put(attacker, current);
                if (current > strongest) {
                    second = strongest;
                    strongest = current;
                    defender.pressureSource = attacker;
                } else if (current > second) second = current;
            }
            defender.pressureReceived = Math.clamp(strongest + second * 0.55, 0, 1.4);
            updateForcedPressureRelease(defender);
        }
    }

    private double pressureSignal(Musketeer attacker, Musketeer defender) {
        if (!attacker.alive || !isVisible(defender, attacker)) return 0;
        var stateFactor = switch (attacker.state) {
            case ATTACK_PREPARE -> 0.58;
            case ATTACK_HOLD -> 1.0;
            case FEINT -> 0.76;
            case ATTACK_ACTIVE, CHAMBER_ACTIVE -> 1.08;
            default -> 0;
        };
        if (stateFactor == 0 || attacker.target != defender && attacker.pressureFixedTarget != defender) return 0;
        var offset = defender.pos.minus(attacker.pos);
        var distance = offset.hypotenuse();
        var maximum = attacker.projectedThrustLength() + Musketeer.BODY_RADIUS + 0.35;
        if (distance < Musketeer.BODY_RADIUS * 1.65 || distance > maximum) return 0;
        var toward = offset.radians();
        var weaponAlignment = 1 - Math.abs(angleDifference(attacker.weaponWorldAngle(), toward)) / Math.toRadians(55);
        var bodyAlignment = 1 - Math.abs(angleDifference(attacker.angle, toward)) / Math.toRadians(95);
        if (weaponAlignment <= 0 || bodyAlignment <= 0) return 0;
        var direction = offset.div(distance);
        var closingSpeed = attacker.velocity.dot(direction);
        var lateralSpeed = Math.abs(attacker.velocity.dot(new Pos(-direction.y(), direction.x())));
        var stepQuality = Math.clamp(0.76 + closingSpeed * 0.20 + lateralSpeed * 0.07, 0.48, 1.12);
        var distanceQuality = Math.clamp(1 - Math.abs(distance - PRESSURE_NEAR_DISTANCE) /
                Math.max(0.35, maximum - PRESSURE_NEAR_DISTANCE), 0.18, 1);
        var weaponFactor = attacker.attackDirection == CombatDirection.UP ? 1.06 : 0.96;
        return Math.clamp(attacker.stats.pressureAbility() / 100.0 * stateFactor * distanceQuality *
                Math.sqrt(weaponAlignment * bodyAlignment) * stepQuality * weaponFactor, 0, 1.15);
    }

    private void updateForcedPressureRelease(Musketeer defender) {
        var source = defender.pressureSource;
        if (defender.state != CombatState.ATTACK_HOLD || source == null ||
                defender.pressureReceived < pressureThreshold(defender)) {
            if (defender.pressureReceived < pressureThreshold(defender) * 0.55)
                defender.forcedPressureSource = null;
            return;
        }
        if (defender.forcedPressureSource != null) return;
        defender.forcedPressureSource = source;
        defender.pressureReleaseDelay = Math.max(0.055,
                0.23 - defender.stats.reaction() * 0.00155 + random.nextDouble() * 0.035);
    }

    private double pressureThreshold(Musketeer agent) {
        var composure = (agent.stats.decisionMaking() * 0.55 + agent.stats.distanceControl() * 0.45) / 100.0;
        return 0.42 + composure * 0.24;
    }

    private void advanceAgent(Musketeer self, double dt) {
        self.hitMessage.update(dt);
        self.chamberMessage.update(dt);
        self.baitTrapMessage.update(dt);
        self.punishMessage.update(dt);
        self.baitAdvanceMessage.update(dt);
        if (self.blockSparkTime > 0) self.blockSparkTime -= dt;
        if (self.backstabMessageTime > 0) self.backstabMessageTime -= dt;
        self.doubleBlockMessage.update(dt);
        if (self.backEscapeTime > 0) self.backEscapeTime -= dt;
        if (self.backEscapeCooldown > 0)
            self.backEscapeCooldown = Math.max(0, self.backEscapeCooldown - dt);
        if (self.feintCooldown > 0) self.feintCooldown = Math.max(0, self.feintCooldown - dt);
        if (self.guardSwitchCooldown > 0) self.guardSwitchCooldown = Math.max(0, self.guardSwitchCooldown - dt);
        if (self.pressureGuardTime > 0) self.pressureGuardTime = Math.max(0, self.pressureGuardTime - dt);
        if (self.pressureReleaseDelay > 0) self.pressureReleaseDelay = Math.max(0, self.pressureReleaseDelay - dt);
        if (self.pressureTransferDelay > 0) self.pressureTransferDelay = Math.max(0, self.pressureTransferDelay - dt);
        if (self.pressureFeintCooldown > 0) self.pressureFeintCooldown = Math.max(0, self.pressureFeintCooldown - dt);
        if (self.pressureEscapeTime > 0) self.pressureEscapeTime = Math.max(0, self.pressureEscapeTime - dt);
        if (self.pressureEscapeDelay > 0) self.pressureEscapeDelay = Math.max(0, self.pressureEscapeDelay - dt);
        if (self.pressureReengageTime > 0) self.pressureReengageTime = Math.max(0, self.pressureReengageTime - dt);
        if (self.localSwitchCooldown > 0) self.localSwitchCooldown = Math.max(0, self.localSwitchCooldown - dt);
        if (self.localHoldTransferTime > 0)
            self.localHoldTransferTime = Math.max(0, self.localHoldTransferTime - dt);
        if (self.dezoneCooldown > 0) self.dezoneCooldown = Math.max(0, self.dezoneCooldown - dt);
        self.baitMessage.update(dt);
        if (features.spatialDezone() && self.dezoneMessage.update(dt) && self.dezoneMessage.getTime() <= 0) {
            if (self.dezoneSwitchTarget != null && !self.dezoneSwitchCommitted) commitTargetSwitchDezone(self);
            else endDezone(self);
        }
        if (!self.alive) return;
        for (var awareness : self.opponentAwareness.values())
            if (awareness.memoryTime > 0) awareness.memoryTime = Math.max(0, awareness.memoryTime - dt);
        updateBackAwareness(self, dt);
        if (features.targetSwitchDezone()) updateTargetSwitchDezone(self, dt);
        self.targetLockTime -= dt;
        var enemy = selectTarget(self, false);
        if (enemy == null) {
            updatePerception(self);
            enemy = selectTarget(self, false);
            if (enemy == null) return;
        }
        self.stateTime += dt;
        self.decisionTime -= dt;
        if (features.engagementCycle()) updateEngagementPlan(self, enemy, dt);
        updateState(self);
    }

    private void prepareDecision(Musketeer self) {
        if (!self.alive || self.decisionTime > 0) return;
        updatePerception(self);
        adoptLocalObjective(self);
    }

    private void decideAgent(Musketeer self) {
        if (!self.alive || self.decisionTime > 0) return;
        var enemy = selectTarget(self, false);
        if (enemy == null) return;
        if (!chooseModernGroupDecision(self) && !applyThreatDecision(self))
            chooseDecision(self, enemy);
        self.decisionTime = DECISION_INTERVAL + random.nextDouble() * 0.035;
    }

    private boolean chooseModernGroupDecision(Musketeer self) {
        var group = activeModernGroup();
        if (group == null) return false;
        if (self.groupIntent == GroupIntent.COMMIT_REVENGE &&
                group.revengeOpportunity != null && group.revengeOpportunity.revenger() == self) {
            var opportunity = group.revengeOpportunity;
            var counter = group.counterCoverAssessment;
            var revengeImpactEta = opportunity.timeToAlign() +
                    opportunity.timeToPrepare() + opportunity.timeToContact();
            var counterThreatens = counter != null && counter.viable() &&
                    counter.threatEta() + 0.04 < revengeImpactEta &&
                    counter.score() >= 0.48 &&
                    counter.counterCoverer().state != CombatState.BLOCK_STUN &&
                    counter.counterCoverer().state != CombatState.RECOVERY;
            if (counterThreatens && !self.isAttacking()) {
                ++modernMetrics.revengeCancelled;
                self.groupIntent = GroupIntent.RECOVER_FORMATION;
                self.decision = Decision.BLOCK;
                if (counter.counterCoverer().isAttacking())
                    beginObservedBlock(self, counter.counterCoverer());
                else setState(self, CombatState.NEUTRAL);
                beginGroupRecovery(group, "counter_cover_threat");
                logTactical("REVENGE_CANCELLED", "revenger=" +
                        self.name(UiLanguage.FRENCH) + " reason=counter_cover_threat");
                return true;
            }
            if (self.state == CombatState.NEUTRAL || self.isGuarding()) {
                var target = opportunity.vulnerableAttacker();
                changeTarget(self, target, 0.72);
                beginAttack(self, target, target.pos.minus(self.pos).hypotenuse());
                self.attackIntent = AttackIntent.DIRECT_RELEASE;
                self.decision = Decision.RIPOSTE;
                ++modernMetrics.revengeCommits;
                logTactical("REVENGE_COMMIT", "revenger=" + self.name(UiLanguage.FRENCH) +
                        " target=" + target.name(UiLanguage.FRENCH));
                traceDecision(self, DecisionCategory.REVENGE, "COMMIT_REVENGE",
                        opportunity.reason().name(), target, null, opportunity.score(),
                        opportunity.vulnerabilityRemaining(), revengeImpactEta,
                        Double.isFinite(opportunity.opposingThreatEta()) ?
                                Math.clamp((revengeImpactEta + 0.22 -
                                        opportunity.opposingThreatEta()) / 0.95, 0, 1) : 0,
                        "NONE");
            } else if (self.state == CombatState.ATTACK_HOLD) {
                var target = opportunity.vulnerableAttacker();
                var offset = target.pos.minus(self.pos);
                var aligned = Math.abs(angleDifference(self.weaponWorldAngle(),
                        offset.radians())) <= Math.toRadians(24);
                if (aligned && offset.hypotenuse() <= self.projectedThrustLength() +
                        Musketeer.BODY_RADIUS + 0.24) {
                    self.attackIntent = AttackIntent.DIRECT_RELEASE;
                    startAttack(self, CombatState.ATTACK_ACTIVE);
                } else if (target.state != CombatState.BLOCK_STUN &&
                        target.state != CombatState.RECOVERY) {
                    ++modernMetrics.revengeCancelled;
                    self.groupIntent = GroupIntent.RECOVER_FORMATION;
                    beginGroupRecovery(group, "revenge_window_closed");
                } else self.decision = Decision.APPROACH;
            }
            return true;
        }
        if (self.groupIntent == GroupIntent.COUNTER_COVER &&
                group.counterCoverAssessment != null &&
                group.counterCoverAssessment.counterCoverer() == self) {
            var assessment = group.counterCoverAssessment;
            var target = assessment.enemyRevenger();
            if (!target.alive) {
                self.groupIntent = GroupIntent.RECOVER_FORMATION;
                return false;
            }
            changeTarget(self, target, 0.48);
            if (assessment.viable() && self.state == CombatState.NEUTRAL &&
                    target.pos.minus(self.pos).hypotenuse() <=
                            self.projectedThrustLength() + Musketeer.BODY_RADIUS + 0.42) {
                beginAttack(self, target, target.pos.minus(self.pos).hypotenuse());
                self.attackIntent = AttackIntent.HOLD;
                ++modernMetrics.counterCoverCommits;
                logTactical("COUNTER_COVER", "coverer=" + self.name(UiLanguage.FRENCH) +
                        " protected=" + assessment.protectedAlly().name(UiLanguage.FRENCH) +
                        " target=" + target.name(UiLanguage.FRENCH));
                traceDecision(self, DecisionCategory.COUNTER_COVER, "COMMIT_COUNTER_COVER",
                        "protect_recovering_ally", target, null, assessment.score(), 0,
                        assessment.threatEta(), 0, "NONE");
            } else self.decision = Decision.APPROACH;
            return true;
        }
        return false;
    }

    private boolean applyThreatDecision(Musketeer self) {
        if (scenario != SimulationScenario.MODERN_2V2 &&
                scenario != SimulationScenario.CONTROLLED_2V2 ||
                self.tacticalThreat == null ||
                !self.tacticalThreat.alive || self.threatResponse.ordinal() <
                        ThreatResponse.DEFENSIVE_ATTENTION.ordinal()) return false;
        var threat = self.tacticalThreat;
        var assessment = self.threatAssessment;
        if (self.threatResponse == ThreatResponse.ENGAGE) return false;
        if (!assessment.visible() || !assessment.immediate() ||
                self.state != CombatState.NEUTRAL && !self.isGuarding()) return false;
        if (self.isGuarding()) {
            var observedDirection = self.opponentAwareness.get(threat).observedAttackDirection;
            if (!self.blockDirection.blocks(observedDirection) && self.guardSwitchCooldown <= 0)
                switchGuard(self, observedDirection);
            else self.decision = Decision.BLOCK;
        } else beginObservedBlock(self, threat);
        return true;
    }

    private void moveAgent(Musketeer self, double dt) {
        if (!self.alive) return;
        var enemy = selectTarget(self, false);
        if (enemy == null) return;
        moveAndTurn(self, enemy, dt);
    }

    private void initializeAwareness(Musketeer self) {
        self.opponentAwareness.clear();
        for (var opponent : opponentsOf(self)) {
            double towardOpponent = opponent.pos.minus(self.pos).radians();
            var visible = Math.abs(angleDifference(self.angle, towardOpponent)) <= Math.toRadians(115);
            var awareness = new OpponentAwareness(opponent.pos, visible);
            if (visible) {
                awareness.perceivedVelocity = opponent.velocity;
                awareness.observedBodyAngle = opponent.angle;
                awareness.observedWeaponAngle = opponent.weaponWorldAngle();
                awareness.observedProjectedRange = opponent.projectedThrustLength();
                awareness.observedState = opponent.state;
                awareness.observedStateTime = opponent.stateTime;
                awareness.observedAttackDirection = opponent.attackDirection;
                awareness.observedBlockDirection = opponent.blockDirection;
            }
            self.opponentAwareness.put(opponent, awareness);
        }
    }

    private void updatePerception(Musketeer self) {
        for (var opponent : opponentsOf(self)) {
            var awareness = self.opponentAwareness.computeIfAbsent(opponent, o -> new OpponentAwareness(o.pos, false));
            if (!opponent.alive) {
                awareness.visible = false;
                awareness.memoryTime = 0;
                continue;
            }
            double distance = opponent.pos.minus(self.pos).hypotenuse();
            var towardOpponent = opponent.pos.minus(self.pos).radians();
            var relativeAngle = Math.abs(angleDifference(self.angle, towardOpponent));
            var detected = random.nextDouble() < detectionProbability(self, opponent, awareness,
                    distance, relativeAngle);
            awareness.visible = detected;
            if (!detected) continue;
            var angleFactor = perceptionAngleFactor(relativeAngle);
            var positionError = 0.01 + (100 - self.stats.distanceControl()) * 0.003 +
                    (1 - angleFactor) * 0.08;
            awareness.lastKnown = opponent.pos.plus(random.nextGaussian() * positionError, random.nextGaussian() * positionError);
            var readingError = (101 - self.stats.distanceControl()) / 100.0;
            var velocityError = 0.025 + readingError * 0.28;
            awareness.perceivedVelocity = opponent.velocity.plus(random.nextGaussian() * velocityError,
                    random.nextGaussian() * velocityError);
            var orientationError = 0.012 + readingError * 0.16 + (1 - angleFactor) * 0.10;
            awareness.observedBodyAngle = normalizeAngle(opponent.angle +
                    random.nextGaussian() * orientationError);
            awareness.observedWeaponAngle = normalizeAngle(opponent.weaponWorldAngle() +
                    random.nextGaussian() * orientationError * 1.18);
            awareness.observedProjectedRange = Math.max(0.45, opponent.projectedThrustLength() +
                    random.nextGaussian() * positionError * 0.55);
            awareness.observedState = opponent.state;
            awareness.observedStateTime = opponent.stateTime;
            awareness.observedAttackDirection = opponent.attackDirection;
            awareness.observedBlockDirection = opponent.blockDirection;
            awareness.memoryTime = 0.55 + (self.stats.reaction() + self.stats.decisionMaking()) / 200.0 * 1.30;
            observeCombatPattern(awareness, opponent);
        }
    }

    private void observeCombatPattern(OpponentAwareness awareness, Musketeer opponent) {
        if (opponent.attackSequence != awareness.lastObservedAttackSequence &&
                (opponent.state == CombatState.ATTACK_PREPARE || opponent.state == CombatState.ATTACK_HOLD ||
                        opponent.isAttacking())) {
            awareness.lastObservedAttackSequence = opponent.attackSequence;
            if (opponent.attackDirection == CombatDirection.UP) ++awareness.observedUpAttacks;
            else ++awareness.observedDownAttacks;
        }
        if (opponent.blockSequence == awareness.lastObservedBlockSequence || !opponent.isGuarding()) return;
        awareness.lastObservedBlockSequence = opponent.blockSequence;
        if (opponent.blockDirection == BlockDirection.UP) ++awareness.observedUpBlocks;
        else if (opponent.blockDirection == BlockDirection.DOWN) ++awareness.observedDownBlocks;
    }

    private void snapshotMotion(Musketeer agent) {
        agent.previous = agent.pos;
        agent.previousTip = agent.tip();
    }

    private double detectionProbability(Musketeer self, Musketeer opponent, OpponentAwareness awareness,
                                        double distance, double relativeAngle) {
        var distanceFactor = Math.clamp(1 - Math.max(0, distance - 1.5) / 4.0, 0.12, 1);
        var skillFactor = 0.45 + self.stats.peripheralAwareness() * 0.0055;
        var attentionFactor = 1.0;
        if (opponent != self.target && self.target != null && self.target.alive) {
            double targetDistance = self.target.pos.minus(self.pos).hypotenuse();
            var targetPressures = self.target.state == CombatState.ATTACK_PREPARE ||
                    self.target.state == CombatState.ATTACK_HOLD || self.target.isAttacking();
            attentionFactor = targetDistance < 2.1 || targetPressures ? 0.45 : 0.72;
        }
        var probability = perceptionAngleFactor(relativeAngle) * distanceFactor * skillFactor * attentionFactor;
        if (awareness.visible) probability += 0.18;
        var opponentAttacks = opponent.state == CombatState.ATTACK_PREPARE ||
                opponent.state == CombatState.ATTACK_HOLD || opponent.state == CombatState.CHAMBER_HOLD ||
                opponent.isAttacking();
        if (opponentAttacks) probability += 0.28;
        else if (opponent.decision != Decision.WAIT) probability += 0.06;
        if (distance <= 1.6 && relativeAngle <= Math.toRadians(115)) probability = Math.max(0.82, probability);
        if (opponent == self.target && relativeAngle <= Math.toRadians(115))
            probability = Math.max(0.96, probability);
        return Math.clamp(probability, 0.02, 0.99);
    }

    private double perceptionAngleFactor(double relativeAngle) {
        var degrees = Math.toDegrees(relativeAngle);
        if (degrees <= 60) return 1 - degrees / 60 * 0.15;
        if (degrees <= 115) return 0.85 - (degrees - 60) / 55 * 0.55;
        return 0.30 - (degrees - 115) / 65 * 0.24;
    }

    private boolean isVisible(Musketeer self, Musketeer opponent) {
        var awareness = self.opponentAwareness.get(opponent);
        return awareness != null && awareness.visible;
    }

    private boolean isTracked(Musketeer self, Musketeer opponent) {
        var awareness = self.opponentAwareness.get(opponent);
        return awareness != null && (awareness.visible || awareness.memoryTime > 0);
    }

    private Pos known(Musketeer self, Musketeer opponent) {
        var awareness = self.opponentAwareness.get(opponent);
        return awareness == null || awareness.visible ? opponent.pos : awareness.lastKnown;
    }

    private double knownDistance(Musketeer self, Musketeer opponent) {
        return known(self, opponent).minus(self.pos).hypotenuse();
    }

    private boolean isKnownRear(Musketeer self, Musketeer opponent) {
        return Math.abs(angleDifference(self.angle, known(self, opponent).minus(self.pos).radians())) >= REAR_ATTACK_ANGLE;
    }

    private Musketeer selectTarget(Musketeer self, boolean force) {
        if (!self.alive) return null;
        var opponents = opponentsOf(self);
        if (!force && self.dezoneSwitchTarget != null && !self.dezoneSwitchCommitted &&
                self.dezoneOriginalTarget != null && self.dezoneOriginalTarget.alive)
            return self.dezoneOriginalTarget;
        var frontOpponent = closestFrontOpponent(self);
        Musketeer immediateThreat = null;
        var greatestThreat = Double.NEGATIVE_INFINITY;
        for (var opponent : opponents) {
            if (!opponent.alive || !isVisible(self, opponent)) continue;
            var distance = opponent.pos.minus(self.pos).hypotenuse();
//            var distance = Math.hypot(opponent.getX() - self.getX(), opponent.getY() - self.getY());
            var attacking = opponent.state == CombatState.ATTACK_PREPARE ||
                    opponent.state == CombatState.ATTACK_HOLD || opponent.state == CombatState.CHAMBER_HOLD ||
                    opponent.isAttacking();
            var facing = Math.abs(angleDifference(opponent.weaponWorldAngle(),
                    Math.atan2(self.getY() - opponent.getY(), self.getX() - opponent.getX()))) < Math.toRadians(34);
            var threat = opponent.projectedThrustLength() + Musketeer.BODY_RADIUS + 0.28 - distance;
            if (attacking && !opponent.baitedAttack && facing && threat > 0 && threat > greatestThreat) {
                immediateThreat = opponent;
                greatestThreat = threat;
            }
        }

        var canReact = self.state == CombatState.NEUTRAL || self.isGuarding();
        var protectedFlankTarget = self.tacticalIntent == TacticalIntent.FLANK &&
                self.tacticalThreat == immediateThreat &&
                self.threatResponse != ThreatResponse.ENGAGE;
        var survivorFocusLocked = self.tacticalIntent == TacticalIntent.SURVIVE &&
                self.multiThreatAttention.focusLockRemaining > 0 &&
                self.multiThreatAttention.focusedThreat != null &&
                isTracked(self, self.multiThreatAttention.focusedThreat);
        if (immediateThreat != null && immediateThreat != self.target &&
                scenario == SimulationScenario.MODERN_2V2 && self.engagement != null &&
                self.engagement.type == EngagementType.GROUP_2V2 && self.targetLockTime > 0 &&
                self.target != null && self.target.alive && isTracked(self, self.target)) {
            var currentThreatEta = perceivedThreatEta(self, self.target, self);
            var newThreatEta = perceivedThreatEta(self, immediateThreat, self);
            if (currentThreatEta <= newThreatEta + 0.12) return self.target;
        }
        if (immediateThreat != null && !protectedFlankTarget && !survivorFocusLocked &&
                canReact && immediateThreat != self.target &&
                (frontOpponent == null || !isKnownRear(self, immediateThreat))) {
            if (self.tacticalIntent == TacticalIntent.SURVIVE) {
                self.multiThreatAttention.focusedThreat = immediateThreat;
                self.multiThreatAttention.focusLockRemaining = 0.72 +
                        (100 - self.stats.reaction()) * 0.005;
                self.multiThreatAttention.switchDelay = 0.28;
            }
            return changeTarget(self, immediateThreat, 0.55);
        }
        if (!force && self.tacticalIntent == TacticalIntent.SURVIVE &&
                self.engagement != null && self.engagement.type == EngagementType.TWO_VS_ONE) {
            var focused = self.multiThreatAttention.focusedThreat;
            if (focused != null && focused.alive && isTracked(self, focused))
                return focused == self.target ? self.target : changeTarget(self, focused,
                        self.multiThreatAttention.focusLockRemaining);
        }
        if (!force && scenario == SimulationScenario.MODERN_2V2 &&
                self.engagement != null &&
                self.engagement.status != EngagementStatus.BROKEN &&
                self.engagement.status != EngagementStatus.FINISHED &&
                self.plannedTarget != null && self.plannedTarget.alive) {
            if (self.target != null && self.target.alive && self.target != self.plannedTarget &&
                    (self.targetLockTime > 0 ||
                            self.state != CombatState.NEUTRAL && !self.isGuarding()))
                return self.target;
            return self.target == self.plannedTarget ? self.target :
                    changeTarget(self, self.plannedTarget, DUEL_LOCK_TIME);
        }
        if (!force && scenario == SimulationScenario.CONTROLLED_2V2 &&
                self.tacticalIntent == TacticalIntent.DUEL &&
                self.engagement != null && self.engagement.type == EngagementType.DUEL &&
                self.engagement.status != EngagementStatus.FINISHED &&
                self.plannedTarget != null && self.plannedTarget.alive)
            return self.plannedTarget == self.target ? self.target :
                    changeTarget(self, self.plannedTarget, DUEL_LOCK_TIME);
        if (!force && combatPhase == CombatPhase.FRONT_LINE && self.openingOpponent != null &&
                self.openingOpponent.alive) return self.openingOpponent;
        if (!force && self.target != null && !self.target.alive && self.state != CombatState.NEUTRAL)
            return self.target;
        if (!force && self.target != null && self.target.alive && self.state != CombatState.NEUTRAL)
            return self.target;
        if (!force && self.backEscapeTime > 0 && self.target != null && self.target.alive &&
                isTracked(self, self.target)) return self.target;
        var currentTargetExposesFront = frontOpponent != null && self.target != null &&
                isTracked(self, self.target) && isKnownRear(self, self.target);
        if (!force && self.target != null && self.target.alive && isTracked(self, self.target) &&
                !currentTargetExposesFront && self.targetLockTime > 0)
            return self.target;

        Musketeer best = null;
        var bestScore = Double.POSITIVE_INFINITY;
        var teammate = teammateOf(self);
        for (var opponent : opponents) {
            if (!opponent.alive || !isTracked(self, opponent)) continue;
            if (frontOpponent != null && isKnownRear(self, opponent)) continue;
            var score = knownDistance(self, opponent);
            var visible = isVisible(self, opponent);
            if (visible && (opponent.state == CombatState.RECOVERY ||
                    opponent.state == CombatState.BLOCK_STUN)) score -= 0.65;
            if (visible && opponent.target == self) score -= 0.18;
            if (visible && teammate != null && opponent.target == teammate &&
                    (opponent.isAttacking() || opponent.state == CombatState.ATTACK_HOLD))
                score -= 0.18 + self.stats.decisionMaking() * 0.003 +
                        self.stats.teamworkAbility() * 0.003;
            if (visible && teammate != null && teammate.alive && teammate.target == opponent)
                score -= self.stats.decisionMaking() * 0.0015 + self.stats.teamworkAbility() * 0.0035;
            if (opponent == self.localObjective) score -= switch (self.localRole) {
                case FIXER -> 0.82;
                case FLANKER -> 1.08;
                case COVER -> 1.24;
                case FREE -> 0;
            };
            else if (self.localRole != LocalRole.FREE) score += 0.28;
            score += random.nextGaussian() * (101 - self.stats.decisionMaking()) * 0.004;
            if (score < bestScore) {
                best = opponent;
                bestScore = score;
            }
        }
        return best == self.target ? best : changeTarget(self, best,
                0.55 + self.stats.decisionMaking() * 0.008);
    }

    private void updateBackAwareness(Musketeer self, double dt) {
        Musketeer nearestRear = null;
        var nearestDistance = Double.POSITIVE_INFINITY;
        self.backExposed = false;
        for (var opponent : opponentsOf(self)) {
            if (!opponent.alive) continue;
            if (isRearAttack(self, opponent)) self.backExposed = true;
            if (!isTracked(self, opponent) || !isKnownRear(self, opponent)) continue;
            var distance = knownDistance(self, opponent);
            if (distance < nearestDistance) {
                nearestRear = opponent;
                nearestDistance = distance;
            }
        }
        if (nearestRear != null && nearestRear != self.rearThreat) {
            self.rearThreat = nearestRear;
            self.rearReactionTime = 0.10 + (100 - self.stats.reaction()) * 0.0032 +
                    random.nextDouble() * 0.06;
        } else if (nearestRear == null && self.backEscapeTime <= 0) {
            self.rearThreat = null;
            self.rearReactionTime = 0;
            modernMetrics.consecutiveBackEscapes = 0;
        }
        if (self.rearReactionTime > 0) self.rearReactionTime = Math.max(0, self.rearReactionTime - dt);
    }

    private Musketeer closestFrontOpponent(Musketeer self) {
        Musketeer closest = null;
        var closestDistance = Double.POSITIVE_INFINITY;
        for (var opponent : opponentsOf(self)) {
            if (!opponent.alive || !isTracked(self, opponent) || isKnownRear(self, opponent)) continue;
            var distance = knownDistance(self, opponent);
            if (distance < closestDistance) {
                closest = opponent;
                closestDistance = distance;
            }
        }
        return closest;
    }

    private boolean isRearAttack(Musketeer defender, Musketeer attacker) {
        var towardAttacker = Math.atan2(attacker.getY() - defender.getY(), attacker.getX() - defender.getX());
        return Math.abs(angleDifference(defender.angle, towardAttacker)) >= REAR_ATTACK_ANGLE;
    }

    private Musketeer changeTarget(Musketeer self, Musketeer target, double lockTime) {
        self.target = target;
        self.targetLockTime = lockTime;
        self.observedBaitSequence = -1;
        self.threatSeen = false;
        self.reactionTime = 0;
        return target;
    }

    private void adoptLocalObjective(Musketeer self) {
        var objective = self.localObjective;
        if (self.localRole == LocalRole.FREE || objective == null || !objective.alive ||
                objective == self.target || self.localSwitchCooldown > 0 || !isTracked(self, objective)) return;
        var current = self.target;
        if (current != null && current.alive && isImmediateAttackThreat(self, current)) return;
        if (self.state == CombatState.ATTACK_HOLD) {
            if (self.pressureMode || self.pressureCounter || self.dezoneSwitchTarget != null ||
                    self.baitedAttack || self.punishingBait || self.exploitingBait || self.freeKillAttempt) return;
            var offset = known(self, objective).minus(self.pos);
            var distance = offset.hypotenuse();
            var turn = Math.abs(angleDifference(self.weaponWorldAngle(), offset.radians()));
            if (distance > LOCAL_THREAT_RANGE || turn > Math.toRadians(110)) return;
            var ability = self.stats.teamworkAbility();
            if (random.nextDouble() >= Math.min(0.92, 0.08 + ability * 0.0082)) {
                self.localSwitchCooldown = 0.16;
                return;
            }
            changeTarget(self, objective, 0.48);
            configureAim(self, distance);
            self.attackPitch = self.pitch;
            self.baseAttackPitch = self.pitch;
            self.localHoldTransferTime = 0.18 + turn / (3.4 + self.stats.footwork() * 0.018) * 0.45;
            self.localSwitchCooldown = 0.62;
            self.decision = Decision.LOCAL_HOLD_TRANSFER;
            return;
        }
        if (self.state != CombatState.NEUTRAL && !self.isGuarding()) return;
        changeTarget(self, objective, 0.42);
        self.localSwitchCooldown = 0.34;
        if (self.isGuarding() && objective.isAttacking() &&
                !self.blockDirection.blocks(objective.attackDirection) && self.guardSwitchCooldown <= 0)
            switchGuard(self, objective.attackDirection);
    }

    private boolean isImmediateAttackThreat(Musketeer defender, Musketeer attacker) {
        if (!attacker.alive || !isVisible(defender, attacker) || attacker.baitedAttack ||
                attacker.state != CombatState.ATTACK_PREPARE && attacker.state != CombatState.ATTACK_HOLD &&
                        attacker.state != CombatState.CHAMBER_HOLD && !attacker.isAttacking()) return false;
        var offset = defender.pos.minus(attacker.pos);
        return offset.hypotenuse() <= attacker.projectedThrustLength() + Musketeer.BODY_RADIUS + 0.30 &&
                Math.abs(angleDifference(attacker.weaponWorldAngle(), offset.radians())) < Math.toRadians(38);
    }

    private void retargetAfterElimination() {
        for (var agent : agents) {
            if (!agent.alive || agent.target != null && agent.target.alive) continue;
            updatePerception(agent);
            var next = selectTarget(agent, true);
            if (next == null) next = closestAliveOpponent(agent);
            if (next == null) continue;
            changeTarget(agent, next, 0.34);
            if (agent.state == CombatState.ATTACK_HOLD) {
                var distance = next.pos.minus(agent.pos).hypotenuse();
                configureAim(agent, distance);
                agent.attackPitch = agent.pitch;
                agent.baseAttackPitch = agent.pitch;
                agent.localHoldTransferTime = 0.10 + (100 - agent.stats.reaction()) * 0.0015;
                agent.decision = Decision.LOCAL_HOLD_TRANSFER;
            }
        }
    }

    private void rebuildControlledEngagementsAfterElimination() {
        var aliveMask = aliveMask();
        if (aliveMask == controlledAliveMask) return;
        controlledAliveMask = aliveMask;
        for (var engagement : engagementManager.engagements())
            if (engagement.status != EngagementStatus.FINISHED)
                engagement.status = EngagementStatus.BROKEN;
        for (var agent : agents) {
            if (!agent.alive) continue;
            clearLocalRole(agent);
            agent.engagement = null;
            agent.engagementSlot = null;
            agent.plannedTarget = null;
            agent.tacticalDestination = null;
            agent.rawInterceptionPoint = null;
            agent.interceptionPhase = null;
            agent.tacticalThreat = null;
            agent.threatAssessment = new ThreatAssessment(0, false, false,
                    Double.POSITIVE_INFINITY, ThreatResponse.IGNORE);
            agent.threatResponse = agent.pendingThreatResponse = ThreatResponse.IGNORE;
            agent.threatResponseDelay = 0;
        }
        var blueAlive = livingAgents(blueTeam);
        var redAlive = livingAgents(redTeam);
        if (blueAlive.size() == 2 && redAlive.size() == 1) {
            createControlledTwoVsOne(blueAlive.get(0), blueAlive.get(1), redAlive.get(0));
            logTactical("ENGAGEMENT_REBUILT", "type=TWO_VS_ONE attackers=BLUE reason=elimination");
        } else if (redAlive.size() == 2 && blueAlive.size() == 1) {
            createControlledTwoVsOne(redAlive.get(0), redAlive.get(1), blueAlive.get(0));
            logTactical("ENGAGEMENT_REBUILT", "type=TWO_VS_ONE attackers=RED reason=elimination");
        } else if (blueAlive.size() == 1 && redAlive.size() == 1) {
            var duel = engagementManager.createDuel(blueAlive.get(0), redAlive.get(0));
            changeTarget(duel.duelistOne, duel.duelistTwo, DUEL_LOCK_TIME);
            changeTarget(duel.duelistTwo, duel.duelistOne, DUEL_LOCK_TIME);
            logTactical("DUEL_CREATED", "agents=" + duel.duelistOne.name(UiLanguage.FRENCH) +
                    "," + duel.duelistTwo.name(UiLanguage.FRENCH) + " reason=elimination");
        }
    }

    private void rebuildModernEngagementsAfterElimination() {
        var aliveMask = aliveMask();
        if (aliveMask == modernAliveMask) return;
        modernAliveMask = aliveMask;
        if (supportPlan != null) finishSupportPlan(
                !supportPlan.primaryDefender.alive &&
                        supportPlan.supporter.alive && supportPlan.fixer.alive ?
                        SupportPlanState.COMPLETED : SupportPlanState.ABORTED,
                !supportPlan.primaryDefender.alive ?
                        "primary_defender_eliminated" : "elimination", false);
        var endingGroup = activeModernGroup();
        if (endingGroup != null) interruptTeamManeuvers(endingGroup, "elimination");
        engagementManager.breakActive();
        clearModernTacticalState();
        var blueAlive = livingAgents(blueTeam);
        var redAlive = livingAgents(redTeam);
        if (blueAlive.size() == 2 && redAlive.size() == 1)
            createModernTwoVsOneAfterElimination(blueAlive, redAlive.getFirst(), "blue_superiority");
        else if (redAlive.size() == 2 && blueAlive.size() == 1)
            createModernTwoVsOneAfterElimination(redAlive, blueAlive.getFirst(), "red_superiority");
        else if (blueAlive.size() == 1 && redAlive.size() == 1)
            createModernDuel(blueAlive.getFirst(), redAlive.getFirst(), 1);
        lastModernReconstructionReason = "elimination";
        modernStructureTime = MODERN_ENGAGEMENT_MIN_TIME;
        logTactical("ENGAGEMENT_REBUILT", "scenario=MODERN_2V2 reason=elimination");
    }

    private GroupEngagement activeModernGroup() {
        return modernGroupEngagement != null &&
                modernGroupEngagement.status != EngagementStatus.BROKEN &&
                modernGroupEngagement.status != EngagementStatus.FINISHED ?
                modernGroupEngagement.group : null;
    }

    private void updateModernGroup(double dt) {
        var group = activeModernGroup();
        if (group == null) return;
        group.updateCenters();
        measureGroupGeometry(group, dt);
        var centerDistance = group.blueCenter.minus(group.redCenter).hypotenuse();
        if (group.phase == GroupPhase.APPROACH && centerDistance <= 3.15)
            changeGroupPhase(group, GroupPhase.HOLD_SHAPE, "teams_in_collective_range");
        if (group.phase == GroupPhase.HOLD_SHAPE &&
                simulationTime - group.phaseStartedAt >= 0.36)
            changeGroupPhase(group, GroupPhase.ROTATE, "shape_established");
        if (group.phase == GroupPhase.ROTATE &&
                simulationTime - group.rotationStartedAt >= rotationPersistence(group) &&
                safeToChangeRotation(group)) {
            var reason = rotationChangeReason(group);
            if (reason != null) changeRotationDirection(group, reason);
            else group.nextRotationEvaluationAt = simulationTime + rotationPersistence(group);
        }
        if (group.phase == GroupPhase.RECOVER_SHAPE &&
                simulationTime - group.phaseStartedAt >= GROUP_RECOVER_TIME &&
                (groupShapeQuality(group) >= 0.46 ||
                        simulationTime - group.phaseStartedAt >= 1.80)) {
            clearGroupExchange(group);
            changeGroupPhase(group, GroupPhase.ROTATE, "formation_recovered");
        }
        if ((group.phase == GroupPhase.ATTACK_COMMITTED ||
                group.phase == GroupPhase.REVENGE_WINDOW ||
                group.phase == GroupPhase.COUNTER_COVER) &&
                simulationTime - group.phaseStartedAt >= 2.40)
            beginGroupRecovery(group, "exchange_no_longer_progressing");
        updateGroupAssignments(group);
        updateTeamManeuvers(group);
        updateGroupCoverage(group);
    }

    private void updateTeamManeuvers(GroupEngagement group) {
        if (group.phase == GroupPhase.ATTACK_COMMITTED || group.phase == GroupPhase.REVENGE_WINDOW ||
                group.phase == GroupPhase.COUNTER_COVER || group.phase == GroupPhase.BROKEN) {
            interruptTeamManeuvers(group, "urgent_group_exchange");
            return;
        }
        for (var team : new Team[]{Team.BLUE, Team.RED}) {
            var current = group.maneuver(team);
            var interrupted = current != null && teamManeuverInterrupted(group, current);
            if (current != null && !interrupted &&
                    current.age(simulationTime) < current.minimumDuration) continue;
            if (current != null && !interrupted &&
                    current.age(simulationTime) < current.preferredDuration) continue;
            var selected = interrupted ? emergencyTeamManeuver(group, team) :
                    chooseTeamManeuver(group, team, current);
            if (current != null && !interrupted &&
                    current.age(simulationTime) < current.maximumDuration &&
                    selected.type() == current.type) continue;
            startTeamManeuver(group, team, selected);
        }
    }

    private TeamManeuverAssessment emergencyTeamManeuver(GroupEngagement group, Team team) {
        var members = group.team(team);
        var partnerDistance = members[0].pos.minus(members[1].pos).hypotenuse();
        if (partnerDistance > 2.15)
            return assessTeamManeuver(group, team, TeamManeuverType.RECOVER_FORMATION);
        for (var opponent : group.opponents(team))
            if (opponent.alive && distanceToSegment(opponent.pos, members[0].pos, members[1].pos) <
                    Musketeer.BODY_RADIUS * 1.45)
                return assessTeamManeuver(group, team, TeamManeuverType.COMPRESS);
        return assessTeamManeuver(group, team, TeamManeuverType.RECOVER_FORMATION);
    }

    private void interruptTeamManeuvers(GroupEngagement group, String reason) {
        for (var team : new Team[]{Team.BLUE, Team.RED}) {
            var maneuver = group.maneuver(team);
            if (maneuver == null) continue;
            finishTeamManeuver(maneuver, reason);
            group.maneuver(team, null);
        }
    }

    private boolean teamManeuverInterrupted(GroupEngagement group, TeamManeuver maneuver) {
        var members = group.team(maneuver.team);
        if (!members[0].alive || !members[1].alive || activeModernGroup() != group) return true;
        if (group.phase == GroupPhase.ATTACK_COMMITTED || group.phase == GroupPhase.REVENGE_WINDOW ||
                group.phase == GroupPhase.COUNTER_COVER || group.phase == GroupPhase.BROKEN) return true;
        if (members[0].pos.minus(members[1].pos).hypotenuse() > 2.35 &&
                maneuver.type != TeamManeuverType.RECOVER_FORMATION) return true;
        if ((arenaClearance(members[0].pos) < 0.24 || arenaClearance(members[1].pos) < 0.24) &&
                maneuver.type != TeamManeuverType.RECOVER_FORMATION &&
                maneuver.type != TeamManeuverType.GIVE_GROUND)
            return true;
        for (var member : members)
            if (member.state == CombatState.BLOCK_STUN || member.groupIntent == GroupIntent.COMMIT_REVENGE ||
                    member.groupIntent == GroupIntent.COUNTER_COVER) {
                if (maneuver.type != TeamManeuverType.RECOVER_FORMATION) return true;
            }
        var opponents = group.opponents(maneuver.team);
        for (var opponent : opponents)
            if (opponent.alive && distanceToSegment(opponent.pos, members[0].pos, members[1].pos) <
                    Musketeer.BODY_RADIUS * 1.45 && maneuver.type != TeamManeuverType.COMPRESS &&
                    maneuver.type != TeamManeuverType.RECOVER_FORMATION) return true;
        return false;
    }

    private TeamManeuverAssessment chooseTeamManeuver(GroupEngagement group, Team team,
                                                       TeamManeuver current) {
        var viable = new ArrayList<TeamManeuverAssessment>();
        var bestScore = Double.NEGATIVE_INFINITY;
        for (var type : TeamManeuverType.values()) {
            var assessment = assessTeamManeuver(group, team, type);
            if (!assessment.viable()) continue;
            var score = assessment.score();
            if (current != null && type == current.type)
                score += current.age(simulationTime) >= current.maximumDuration ? -0.18 : 0.10;
            score += (random.nextDouble() - 0.5) * teamDecisionNoise(group.team(team));
            var scored = new TeamManeuverAssessment(type, true, assessment.coverPreservation(),
                    assessment.lateralSpace(), assessment.forwardPressure(), assessment.enemyThreat(),
                    assessment.boundarySafety(), assessment.formationQuality(),
                    assessment.attackLinePotential(), score, assessment.primaryReason(),
                    assessment.rejectionReason());
            viable.add(scored);
            bestScore = Math.max(bestScore, score);
        }
        if (viable.isEmpty()) return assessTeamManeuver(group, team, TeamManeuverType.HOLD_LINE);
        var members = group.team(team);
        var decisionQuality = (members[0].stats.decisionMaking() + members[1].stats.decisionMaking() +
                members[0].stats.teamworkAbility() + members[1].stats.teamworkAbility()) / 400.0;
        var choiceWindow = 0.10 + (1 - decisionQuality) * 0.18;
        var totalWeight = 0.0;
        for (var assessment : viable)
            if (assessment.score() >= bestScore - choiceWindow)
                totalWeight += Math.exp((assessment.score() - bestScore) / 0.10);
        var choice = random.nextDouble() * totalWeight;
        for (var assessment : viable) {
            if (assessment.score() < bestScore - choiceWindow) continue;
            choice -= Math.exp((assessment.score() - bestScore) / 0.10);
            if (choice <= 0) return assessment;
        }
        return viable.getFirst();
    }

    TeamManeuverAssessment evaluateTeamManeuver(Team team, TeamManeuverType type) {
        var group = activeModernGroup();
        if (group == null) return new TeamManeuverAssessment(type, false, 0, 0, 0, 0,
                0, 0, 0, Double.NEGATIVE_INFINITY, "no_group", "no_active_group");
        group.updateCenters();
        return assessTeamManeuver(group, team, type);
    }

    private TeamManeuverAssessment assessTeamManeuver(GroupEngagement group, Team team,
                                                       TeamManeuverType type) {
        var members = group.team(team);
        var opponents = group.opponents(team);
        var teamCenter = group.teamCenter(team);
        var enemyCenter = group.enemyCenter(team);
        var forwardOffset = enemyCenter.minus(teamCenter);
        var centerDistance = forwardOffset.hypotenuse();
        var forward = centerDistance < 0.0001 ? new Pos(members[0].angle) :
                forwardOffset.div(centerDistance);
        var left = new Pos(-forward.y(), forward.x());
        var direction = switch (type) {
            case ROTATE_LEFT, SHIFT_LEFT -> left;
            case ROTATE_RIGHT, SHIFT_RIGHT -> left.times(-1);
            case PRESS_FORWARD -> forward;
            case GIVE_GROUND -> forward.times(-1).plus(left.times(team == Team.BLUE ? 0.45 : -0.45));
            default -> new Pos();
        };
        var projectedCenter = teamCenter.plus(direction.times(0.85));
        var lateralSpace = Math.clamp(Math.min(arenaClearance(members[0].pos.plus(direction.times(0.85))),
                arenaClearance(members[1].pos.plus(direction.times(0.85)))) / 1.05, 0, 1);
        var boundarySafety = Math.clamp(arenaClearance(projectedCenter) / 1.10, 0, 1);
        var partnerDistance = members[0].pos.minus(members[1].pos).hypotenuse();
        var formationQuality = Math.clamp(1 - Math.abs(partnerDistance - 1.48) / 1.20, 0, 1);
        var coverPreservation = Math.clamp(1 - Math.abs(partnerDistance - 1.42) / 1.05, 0, 1);
        var enemyThreat = 0.0;
        for (var opponent : opponents) {
            var nearest = Math.min(opponent.pos.minus(members[0].pos).hypotenuse(),
                    opponent.pos.minus(members[1].pos).hypotenuse());
            enemyThreat += Math.clamp((2.15 - nearest) / 1.25, 0, 1) *
                    (opponent.state == CombatState.ATTACK_HOLD || opponent.isAttacking() ? 0.65 : 0.35);
        }
        enemyThreat = Math.clamp(enemyThreat, 0, 1);
        var forwardPressure = Math.clamp((3.0 - centerDistance) / 1.65, 0, 1);
        var attackLinePotential = 0.0;
        for (var member : members) {
            var mark = member.primaryMark == null ? opponents[0] : member.primaryMark;
            var ally = member == members[0] ? members[1] : members[0];
            attackLinePotential += Math.clamp((distanceToSegment(ally.pos, member.pos, mark.pos) -
                    Musketeer.BODY_RADIUS) / 0.80, 0, 1) * 0.5;
        }
        var averageAggression = (members[0].stats.aggression() + members[1].stats.aggression()) / 200.0;
        var averageTeamwork = (members[0].stats.teamworkAbility() +
                members[1].stats.teamworkAbility()) / 200.0;
        var score = coverPreservation * 0.20 + lateralSpace * 0.18 + boundarySafety * 0.14 +
                formationQuality * 0.16 + attackLinePotential * 0.14;
        var reason = "balanced_team_geometry";
        switch (type) {
            case HOLD_LINE -> {
                score += 0.22 + formationQuality * 0.22 - enemyThreat * 0.10;
                reason = "stable_covered_line";
            }
            case ROTATE_LEFT, ROTATE_RIGHT -> {
                score += 0.28 + lateralSpace * 0.18 + attackLinePotential * 0.10;
                reason = "lateral_space_for_cross_angle";
            }
            case PRESS_FORWARD -> {
                score += Math.clamp((centerDistance - 1.72) / 1.55, 0, 1) * 0.54 +
                        averageAggression * 0.14 - enemyThreat * 0.12;
                reason = "opponents_yielding_range";
            }
            case GIVE_GROUND -> {
                score += enemyThreat * 0.52 + Math.clamp((1.72 - centerDistance) / 0.72, 0, 1) * 0.22;
                reason = "cede_under_weapon_pressure";
            }
            case SHIFT_LEFT, SHIFT_RIGHT -> {
                score += lateralSpace * 0.30 + (1 - boundarySafety) * 0.24;
                reason = "parallel_line_reposition";
            }
            case COMPRESS -> {
                score += 0.12 + Math.clamp((partnerDistance - 1.38) / 0.65, 0, 1) * 0.52 +
                        enemyThreat * 0.18;
                reason = "close_cover_gap";
            }
            case EXPAND -> {
                score += 0.08 + Math.clamp((1.48 - partnerDistance) / 0.62, 0, 1) * 0.40 +
                        attackLinePotential * 0.14;
                reason = "open_two_attack_lines";
            }
            case CROSS_COVER -> {
                score += 0.08 + averageTeamwork * 0.28 + enemyThreat * 0.18;
                reason = "cover_secondary_line";
            }
            case RECOVER_FORMATION -> {
                score += (1 - formationQuality) * 0.58;
                reason = "restore_partner_geometry";
            }
        }
        var viable = lateralSpace > 0.08 && boundarySafety > 0.06;
        if ((type == TeamManeuverType.COMPRESS && partnerDistance < 1.22) ||
                (type == TeamManeuverType.EXPAND && partnerDistance > 1.82)) viable = false;
        return new TeamManeuverAssessment(type, viable, coverPreservation, lateralSpace,
                forwardPressure, enemyThreat, boundarySafety, formationQuality, attackLinePotential,
                score, reason, viable ? "" : "unsafe_team_destination");
    }

    private double teamDecisionNoise(Musketeer[] members) {
        var quality = (members[0].stats.decisionMaking() + members[1].stats.decisionMaking() +
                members[0].stats.teamworkAbility() + members[1].stats.teamworkAbility()) / 400.0;
        return 0.22 - quality * 0.14;
    }

    private void startTeamManeuver(GroupEngagement group, Team team,
                                   TeamManeuverAssessment assessment) {
        var previous = group.maneuver(team);
        if (previous != null) finishTeamManeuver(previous, "reassessed_team_geometry");
        var members = group.team(team);
        var persistence = (members[0].stats.decisionMaking() + members[1].stats.decisionMaking() +
                members[0].stats.teamworkAbility() + members[1].stats.teamworkAbility()) / 400.0;
        var minimum = 0.70 + persistence * 0.40;
        var preferred = 1.40 + persistence * 1.10;
        var maximum = 3.00 + persistence;
        var sign = switch (assessment.type()) {
            case ROTATE_LEFT, SHIFT_LEFT -> 1;
            case ROTATE_RIGHT, SHIFT_RIGHT -> -1;
            default -> random.nextBoolean() ? 1 : -1;
        };
        var maneuver = new TeamManeuver(team, assessment.type(), simulationTime, minimum,
                preferred, maximum, group.teamCenter(team), group.enemyCenter(team), sign,
                null, null, Math.clamp(assessment.score(), 0, 1), assessment.primaryReason());
        refreshTeamManeuverRoles(group, maneuver);
        group.maneuver(team, maneuver);
        ++modernMetrics.teamManeuversStarted;
        modernMetrics.teamManeuversByType.merge(maneuver.type, 1, Integer::sum);
        for (var member : members) {
            var role = member == maneuver.outerPlayer ? "outer" : "inner";
            traceDecision(member, DecisionCategory.TEAM_MANEUVER, maneuver.type.name(),
                    maneuver.primaryReason + ":" + role, null, group.teamCenter(team),
                    assessment.score(), maneuver.preferredDuration, 0, 0, "");
        }
        logTactical("TEAM_MANEUVER_STARTED", "team=" + team + " type=" + maneuver.type +
                " inner=" + maneuver.innerPlayer.name(UiLanguage.FRENCH) +
                " outer=" + maneuver.outerPlayer.name(UiLanguage.FRENCH) +
                " reason=" + maneuver.primaryReason);
    }

    void forceTeamManeuver(Team team, TeamManeuverType type) {
        var group = activeModernGroup();
        if (group == null) throw new IllegalStateException("No active GROUP_2V2.");
        group.updateCenters();
        startTeamManeuver(group, team, assessTeamManeuver(group, team, type));
    }

    private void refreshTeamManeuverRoles(GroupEngagement group, TeamManeuver maneuver) {
        var members = group.team(maneuver.team);
        if (!maneuver.rotating()) {
            maneuver.innerPlayer = members[0];
            maneuver.outerPlayer = members[1];
            return;
        }
        var firstDistance = members[0].pos.minus(group.center).hypotenuse();
        var secondDistance = members[1].pos.minus(group.center).hypotenuse();
        Musketeer inner;
        if (Math.abs(firstDistance - secondDistance) > 0.05)
            inner = firstDistance < secondDistance ? members[0] : members[1];
        else {
            var forward = group.enemyCenter(maneuver.team).minus(group.teamCenter(maneuver.team));
            forward = forward.hypotenuse() < 0.0001 ? new Pos(members[0].angle) :
                    forward.div(forward.hypotenuse());
            var lateral = new Pos(-forward.y(), forward.x()).times(maneuver.directionSign);
            inner = members[0].pos.minus(group.teamCenter(maneuver.team)).dot(lateral) <
                    members[1].pos.minus(group.teamCenter(maneuver.team)).dot(lateral) ?
                    members[0] : members[1];
        }
        maneuver.innerPlayer = inner;
        maneuver.outerPlayer = inner == members[0] ? members[1] : members[0];
    }

    private void finishTeamManeuver(TeamManeuver maneuver, String reason) {
        var duration = Math.max(0, maneuver.age(simulationTime));
        modernMetrics.teamManeuverDurationByType.merge(maneuver.type, duration, Double::sum);
        logTactical("TEAM_MANEUVER_FINISHED", "team=" + maneuver.team + " type=" +
                maneuver.type + " duration=%.2f reason=%s".formatted(duration, reason));
    }

    private double rotationPersistence(GroupEngagement group) {
        var averageDecision = 0.0;
        for (var agent : agents)
            averageDecision += agent.stats.decisionMaking() + agent.stats.teamworkAbility();
        averageDecision /= agents.length * 200.0;
        return GROUP_ROTATION_MIN_TIME +
                (GROUP_ROTATION_MAX_TIME - GROUP_ROTATION_MIN_TIME) * averageDecision;
    }

    private boolean safeToChangeRotation(GroupEngagement group) {
        if (simulationTime < group.nextRotationEvaluationAt) return false;
        for (var agent : agents)
            if (agent.alive && (agent.isAttacking() ||
                    agent.state == CombatState.ATTACK_PREPARE ||
                    agent.state == CombatState.ATTACK_HOLD ||
                    agent.state == CombatState.BLOCK_STUN))
                return false;
        return true;
    }

    private String rotationChangeReason(GroupEngagement group) {
        var currentSpace = rotationSpace(group, group.rotation);
        var oppositeSpace = rotationSpace(group, group.rotation.opposite());
        if (currentSpace < 0.58 && oppositeSpace > currentSpace + 0.16) return "arena_edge";
        for (var team : new Musketeer[][]{group.blue, group.red}) {
            var distance = team[0].pos.minus(team[1].pos).hypotenuse();
            if (distance < 0.86 && oppositeSpace > currentSpace + 0.08)
                return "teammate_collision";
        }
        return null;
    }

    private double rotationSpace(GroupEngagement group, RotationDirection direction) {
        var minimum = Double.POSITIVE_INFINITY;
        for (var agent : group.allAgents()) {
            if (!agent.alive) continue;
            var radial = agent.pos.minus(group.center);
            if (radial.hypotenuse() < 0.0001) continue;
            var tangent = new Pos(-radial.y(), radial.x()).div(radial.hypotenuse()).times(direction.sign);
            minimum = Math.min(minimum, arenaClearance(agent.pos.plus(tangent.times(0.95))));
        }
        return minimum;
    }

    private void changeRotationDirection(GroupEngagement group, String reason) {
        finishRotationSegment(group);
        group.rotation = group.rotation.opposite();
        group.rotationStartedAt = simulationTime;
        group.nextRotationEvaluationAt = simulationTime + rotationPersistence(group);
        ++modernMetrics.rotationDirectionChanges;
        logTactical("ROTATION_DIRECTION_CHANGED", "rotation=" + group.rotation +
                " reason=" + reason);
    }

    private void finishRotationSegment(GroupEngagement group) {
        if (group.rotationSegmentAngularDisplacement < GROUP_VISIBLE_ROTATION_ANGLE)
            ++modernMetrics.rotationsBelowVisibleThreshold;
        group.rotationSegmentAngularDisplacement = 0;
    }

    private void measureGroupGeometry(GroupEngagement group, double dt) {
        if (dt <= 0) return;
        var rotating = group.phase == GroupPhase.ROTATE;
        if (rotating) modernMetrics.groupRotationDuration += dt;
        var bluePreviousAngle = group.previousBlueCenter.minus(group.previousCenter).radians();
        var redPreviousAngle = group.previousRedCenter.minus(group.previousCenter).radians();
        var blueAngle = group.blueCenter.minus(group.center).radians();
        var redAngle = group.redCenter.minus(group.center).radians();
        var teamAngularDelta = (Math.abs(angleDifference(bluePreviousAngle, blueAngle)) +
                Math.abs(angleDifference(redPreviousAngle, redAngle))) / 2;
        if (rotating) {
            modernMetrics.teamCenterAngularDisplacement += teamAngularDelta;
            modernMetrics.groupAngularDisplacement += teamAngularDelta;
            group.rotationSegmentAngularDisplacement += teamAngularDelta;
        }
        for (var agent : group.allAgents()) {
            var previous = group.previousPositions.get(agent);
            var previousAngle = group.previousCenterAngles.get(agent);
            if (agent.alive && previous != null && previousAngle != null) {
                var currentRadial = agent.pos.minus(group.center);
                var angularDelta = Math.abs(angleDifference(previousAngle, currentRadial.radians()));
                if (rotating) {
                    modernMetrics.individualTangentialDistance +=
                            Math.min(agent.pos.minus(previous).hypotenuse(), angularDelta * currentRadial.hypotenuse());
                    if (agent.velocity.hypotenuse() < 0.12) {
                        modernMetrics.stationaryDuration += dt;
                        if (agent.localManeuver != null &&
                                agent.localManeuver.type == LocalManeuverType.HOLD_POSITION)
                            modernMetrics.intentionalHoldDuration += dt;
                        else modernMetrics.incidentalLowSpeedDuration += dt;
                        if (!agent.modernStationaryEpisodeActive) {
                            agent.modernStationaryEpisodeActive = true;
                            ++modernMetrics.stationaryEpisodes;
                        }
                    } else agent.modernStationaryEpisodeActive = false;
                    var tangent = currentRadial.hypotenuse() < 0.0001 ? new Pos() :
                            new Pos(-currentRadial.y(), currentRadial.x()).
                                    div(currentRadial.hypotenuse()).times(group.rotation.sign);
                    modernMetrics.actualTangentialVelocitySum += Math.max(0, agent.velocity.dot(tangent));
                    modernMetrics.actualTangentialVelocitySamples++;
                }
                var teamCenter = group.teamCenter(agent.team);
                var enemyCenter = group.enemyCenter(agent.team);
                var forward = enemyCenter.minus(teamCenter);
                if (forward.hypotenuse() > 0.0001) {
                    forward = forward.div(forward.hypotenuse());
                    var maneuver = group.maneuver(agent.team);
                    var lateral = new Pos(-forward.y(), forward.x()).times(
                            maneuver == null ? 1 : maneuver.directionSign);
                    var displacement = agent.pos.minus(previous);
                    modernMetrics.teamLateralDistance += Math.abs(displacement.dot(lateral));
                    modernMetrics.teamForwardDistance += displacement.dot(forward);
                }
            } else agent.modernStationaryEpisodeActive = false;
            group.previousPositions.put(agent, agent.pos);
            group.previousCenterAngles.put(agent, agent.pos.minus(group.center).radians());
        }
        for (var team : new Musketeer[][]{group.blue, group.red}) {
            if (!team[0].alive || !team[1].alive) continue;
            modernMetrics.partnerDistanceSum += team[0].pos.minus(team[1].pos).hypotenuse();
            ++modernMetrics.partnerDistanceSamples;
            var maneuver = group.maneuver(team[0].team);
            if (maneuver != null && maneuver.rotating() && maneuver.innerPlayer != null &&
                    maneuver.outerPlayer != null && ordinaryRotationSample(maneuver.innerPlayer) &&
                    ordinaryRotationSample(maneuver.outerPlayer)) {
                var forward = group.enemyCenter(team[0].team).minus(group.teamCenter(team[0].team));
                forward = normalizedOr(forward, new Pos(team[0].angle));
                var lateral = new Pos(-forward.y(), forward.x()).times(maneuver.directionSign);
                modernMetrics.innerOuterSpeedDifference +=
                        maneuver.outerPlayer.velocity.dot(lateral) -
                                maneuver.innerPlayer.velocity.dot(lateral);
                ++modernMetrics.innerOuterSpeedSamples;
            }
        }
        for (var agent : group.allAgents()) {
            if (!agent.alive || agent.secondaryThreat == null || !agent.secondaryThreat.alive) continue;
            modernMetrics.crossThreatDistanceSum += agent.pos.minus(agent.secondaryThreat.pos).hypotenuse();
            ++modernMetrics.crossThreatDistanceSamples;
        }
        var bluePartnerDistance = group.blue[0].pos.minus(group.blue[1].pos).hypotenuse();
        var redPartnerDistance = group.red[0].pos.minus(group.red[1].pos).hypotenuse();
        modernMetrics.partnerDistanceVariation += Math.abs(bluePartnerDistance -
                group.previousBluePartnerDistance) + Math.abs(redPartnerDistance -
                group.previousRedPartnerDistance);
        var formationArea = group.formationArea();
        modernMetrics.formationAreaVariation += Math.abs(formationArea - group.previousFormationArea);
        group.previousBluePartnerDistance = bluePartnerDistance;
        group.previousRedPartnerDistance = redPartnerDistance;
        group.previousFormationArea = formationArea;
        group.previousCenter = group.center;
        group.previousBlueCenter = group.blueCenter;
        group.previousRedCenter = group.redCenter;
    }

    private boolean ordinaryRotationSample(Musketeer agent) {
        if (arenaClearance(agent.pos) < 0.40 || agent.isAttacking() ||
                agent.state == CombatState.RECOVERY || agent.state == CombatState.BLOCK_STUN)
            return false;
        return agent.localManeuver == null || agent.localManeuver.type != LocalManeuverType.HOLD_POSITION &&
                agent.localManeuver.type != LocalManeuverType.AVOID_PRIMARY_WEAPON_LINE &&
                agent.localManeuver.type != LocalManeuverType.AVOID_SECONDARY_WEAPON_LINE;
    }

    private void updateGroupAssignments(GroupEngagement group) {
        if (simulationTime - group.markChangedAt < GROUP_MARK_MIN_TIME ||
                group.phase == GroupPhase.ATTACK_COMMITTED ||
                group.phase == GroupPhase.REVENGE_WINDOW ||
                group.phase == GroupPhase.COUNTER_COVER) return;
        var directCost = groupMarkingCost(blueTeam[0], redTeam[0], blueTeam[1], redTeam[1]);
        var crossedCost = groupMarkingCost(blueTeam[0], redTeam[1], blueTeam[1], redTeam[0]);
        var currentDirect = blueTeam[0].primaryMark == redTeam[0];
        if (currentDirect && crossedCost + 0.72 < directCost ||
                !currentDirect && directCost + 0.72 < crossedCost)
            assignModernGroupMarks(group, true);
    }

    private double groupMarkingCost(Musketeer blueOne, Musketeer redOne,
                                    Musketeer blueTwo, Musketeer redTwo) {
        return groupPairingCost(blueOne, redOne) + groupPairingCost(blueTwo, redTwo);
    }

    private double groupPairingCost(Musketeer first, Musketeer second) {
        var distance = first.pos.minus(second.pos).hypotenuse();
        var turn = Math.abs(angleDifference(first.angle,
                second.pos.minus(first.pos).radians()));
        var rearPenalty = turn > Math.toRadians(125) ? 1.15 : 0;
        return distance + turn * 0.34 + rearPenalty;
    }

    private void updateGroupCoverage(GroupEngagement group) {
        for (var agent : agents) {
            if (!agent.alive) continue;
            var ally = teammateOf(agent);
            var punishable = ally == null ? null : ally.primaryMark;
            agent.preferredGroupPosition = preferredGroupCoverPosition(agent, group);
            agent.coverPositionAssessment = assessCoverPosition(ally, agent, punishable,
                    agent.preferredGroupPosition);
            var assessment = assessCover(ally, agent, punishable);
            agent.coverAssessment = assessment;
            agent.covering = assessment.covered() ? ally : null;
            if (ally != null) ally.coveredBy = assessment.covered() ? agent : null;
            ++modernMetrics.coverAssessments;
            ++modernMetrics.physicalCoverSamples;
            if (agent.coverPositionAssessment.physicallyCovering())
                ++modernMetrics.physicalCoveringSamples;
        }
    }

    private Pos preferredGroupCoverPosition(Musketeer self, GroupEngagement group) {
        var teamCenter = self.team == Team.BLUE ? group.blueCenter : group.redCenter;
        var enemyCenter = self.team == Team.BLUE ? group.redCenter : group.blueCenter;
        var front = enemyCenter.minus(teamCenter);
        front = front.hypotenuse() < 0.0001 ? new Pos(self.angle) : front.div(front.hypotenuse());
        var lateral = new Pos(-front.y(), front.x());
        var side = self.engagementSide == 0 ? 1 : self.engagementSide;
        var spacing = preferredPartnerSpacing(self, group);
        var maneuver = group.maneuver(self.team);
        var directionSign = maneuver == null ? side : maneuver.directionSign;
        var oblique = front.times(side * directionSign * 0.13);
        var preferred = teamCenter.plus(lateral.times(side * spacing / 2)).plus(oblique);
        if (self.secondaryThreat != null && self.secondaryThreat.alive) {
            var reach = self.projectedThrustLength() + Musketeer.BODY_RADIUS + 0.42;
            var toSecondary = self.secondaryThreat.pos.minus(preferred);
            if (toSecondary.hypotenuse() > reach)
                preferred = preferred.plus(toSecondary.div(toSecondary.hypotenuse()).times(
                        Math.min(0.28, toSecondary.hypotenuse() - reach)));
        }
        var margin = Musketeer.BODY_RADIUS + 0.08;
        return new Pos(Math.clamp(preferred.x(), margin, ARENA_WIDTH - margin),
                Math.clamp(preferred.y(), margin, ARENA_HEIGHT - margin));
    }

    private double preferredPartnerSpacing(Musketeer self, GroupEngagement group) {
        var ally = teammateOf(self);
        if (ally == null || !ally.alive) return GROUP_PARTNER_PREFERRED_DISTANCE;
        var spacing = 1.34;
        var enemyBetween = self.primaryMark == null ? Double.POSITIVE_INFINITY :
                distanceToSegment(self.primaryMark.pos, self.pos, ally.pos);
        if (enemyBetween < Musketeer.BODY_RADIUS * 1.85) spacing -= 0.14;
        if (self.secondaryThreat != null && distanceToSegment(ally.pos, self.pos,
                self.secondaryThreat.pos) < Musketeer.BODY_RADIUS * 1.55) spacing += 0.20;
        var opponents = self.team == Team.BLUE ? group.red : group.blue;
        var enemySpacing = opponents[0].pos.minus(opponents[1].pos).hypotenuse();
        if (enemySpacing < 1.12) spacing += 0.12;
        return Math.clamp(spacing, GROUP_PARTNER_MIN_DISTANCE, GROUP_PARTNER_MAX_DISTANCE);
    }

    private CoverPositionAssessment assessCoverPosition(Musketeer protectedAlly,
                                                          Musketeer coveringAgent,
                                                          Musketeer punishableEnemy,
                                                          Pos preferredPosition) {
        if (protectedAlly == null || coveringAgent == null || punishableEnemy == null ||
                !protectedAlly.alive || !coveringAgent.alive || !punishableEnemy.alive)
            return CoverPositionAssessment.unavailable(preferredPosition, "invalid_participant");
        if (!isTracked(coveringAgent, punishableEnemy))
            return CoverPositionAssessment.unavailable(preferredPosition, "enemy_untracked");
        var revengeEta = attackThreatEta(coveringAgent, punishableEnemy);
        var expectedWindow = punishableEnemy.recoveryTime() + BLOCK_STUN_TIME;
        var etaMargin = expectedWindow + 0.12 - revengeEta;
        var lineClearance = distanceToSegment(protectedAlly.pos, coveringAgent.pos,
                punishableEnemy.pos);
        var lineSafety = Math.clamp((lineClearance - Musketeer.BODY_RADIUS * 1.35) / 0.82,
                0, 1);
        var reason = etaMargin < 0 ? "revenge_eta_too_late" :
                lineSafety < 0.14 ? "ally_blocks_stab_line" : "physical_cover_available";
        return new CoverPositionAssessment(etaMargin >= 0 && lineSafety >= 0.14,
                revengeEta, expectedWindow, etaMargin, lineSafety, preferredPosition, reason);
    }

    private CoverAssessment assessCover(Musketeer protectedAlly, Musketeer coveringAgent,
                                        Musketeer punishableEnemy) {
        if (protectedAlly == null || coveringAgent == null || punishableEnemy == null ||
                !protectedAlly.alive || !coveringAgent.alive || !punishableEnemy.alive)
            return CoverAssessment.unavailable(protectedAlly, coveringAgent, "invalid_participant");
        if (!isTracked(coveringAgent, punishableEnemy))
            return CoverAssessment.unavailable(protectedAlly, coveringAgent, "enemy_untracked");
        var distance = coveringAgent.pos.minus(punishableEnemy.pos).hypotenuse();
        var alignment = Math.abs(angleDifference(coveringAgent.weaponWorldAngle(),
                punishableEnemy.pos.minus(coveringAgent.pos).radians()));
        var prepare = coveringAgent.state == CombatState.ATTACK_HOLD ? 0 :
                coveringAgent.state == CombatState.ATTACK_PREPARE ?
                        Math.max(0, coveringAgent.prepareTime() - coveringAgent.stateTime) :
                        coveringAgent.attackDirection.prepareTime;
        var turn = alignment / estimatedTurnSpeed(coveringAgent);
        var closing = Math.max(0, distance - coveringAgent.projectedThrustLength() -
                Musketeer.BODY_RADIUS) / estimatedMovementSpeed(coveringAgent);
        var punishEta = prepare + turn + closing;
        var lineClearance = distanceToSegment(protectedAlly.pos, coveringAgent.pos,
                punishableEnemy.pos);
        var lineSafety = Math.clamp((lineClearance - Musketeer.BODY_RADIUS * 1.45) / 0.85,
                0, 1);
        var opposing = teammateOf(punishableEnemy);
        var counterEta = opposing == null ? Double.POSITIVE_INFINITY :
                attackThreatEta(opposing, coveringAgent);
        var recoveryWindow = punishableEnemy.recoveryTime() + BLOCK_STUN_TIME;
        var score = Math.clamp(0.44 * (1 - Math.clamp(punishEta / 1.25, 0, 1)) +
                0.22 * lineSafety +
                0.18 * Math.clamp((counterEta - punishEta + 0.30) / 1.15, 0, 1) +
                0.16 * (coveringAgent.stats.teamworkAbility() +
                        coveringAgent.stats.decisionMaking()) / 200.0, 0, 1);
        var physical = assessCoverPosition(protectedAlly, coveringAgent, punishableEnemy,
                coveringAgent.preferredGroupPosition == null ? coveringAgent.pos :
                        coveringAgent.preferredGroupPosition);
        var covered = physical.physicallyCovering() && score >= 0.43;
        return new CoverAssessment(covered, protectedAlly, coveringAgent,
                punishableEnemy, punishEta, recoveryWindow, counterEta, lineSafety, score,
                covered ? "physical_punish_available" : physical.rejectionReason());
    }

    private double attackThreatEta(Musketeer attacker, Musketeer target) {
        if (attacker == null || target == null || !attacker.alive || !target.alive)
            return Double.POSITIVE_INFINITY;
        var distance = attacker.pos.minus(target.pos).hypotenuse();
        var turn = Math.abs(angleDifference(attacker.weaponWorldAngle(),
                target.pos.minus(attacker.pos).radians())) / estimatedTurnSpeed(attacker);
        var prepare = attacker.state == CombatState.ATTACK_HOLD ? 0 :
                attacker.state == CombatState.ATTACK_PREPARE ?
                        Math.max(0, attacker.prepareTime() - attacker.stateTime) :
                        attacker.attackDirection.prepareTime;
        var close = Math.max(0, distance - attacker.projectedThrustLength() -
                Musketeer.BODY_RADIUS) / estimatedMovementSpeed(attacker);
        return prepare + turn + close;
    }

    private double groupShapeQuality(GroupEngagement group) {
        var blueSpacing = blueTeam[0].pos.minus(blueTeam[1].pos).hypotenuse();
        var redSpacing = redTeam[0].pos.minus(redTeam[1].pos).hypotenuse();
        var blueQuality = 1 - Math.abs(blueSpacing - GROUP_PARTNER_PREFERRED_DISTANCE) / 2;
        var redQuality = 1 - Math.abs(redSpacing - GROUP_PARTNER_PREFERRED_DISTANCE) / 2;
        return Math.clamp((blueQuality + redQuality) / 2, 0, 1);
    }

    private void changeGroupPhase(GroupEngagement group, GroupPhase phase, String reason) {
        if (group.phase == phase) return;
        var previous = group.phase;
        if (previous == GroupPhase.ROTATE) finishRotationSegment(group);
        group.phase = phase;
        group.phaseStartedAt = simulationTime;
        if (phase == GroupPhase.ROTATE) {
            group.rotationStartedAt = simulationTime;
            group.nextRotationEvaluationAt = simulationTime + rotationPersistence(group);
            group.rotationSegmentAngularDisplacement = 0;
            ++modernMetrics.groupRotations;
        } else if (phase == GroupPhase.RECOVER_SHAPE)
            ++modernMetrics.recoverShapeTransitions;
        logTactical("GROUP_PHASE", "from=" + previous + " to=" + phase +
                " rotation=" + group.rotation + " reason=" + reason);
    }

    private void beginGroupRecovery(GroupEngagement group, String reason) {
        for (var agent : agents)
            if (agent.alive) agent.groupIntent = GroupIntent.RECOVER_FORMATION;
        changeGroupPhase(group, GroupPhase.RECOVER_SHAPE, reason);
    }

    private void clearGroupExchange(GroupEngagement group) {
        group.committedAttacker = group.committedTarget = group.revenger =
                group.vulnerableAttacker = group.counterCoverer = null;
        group.revengeOpportunity = null;
        group.counterCoverAssessment = null;
        for (var agent : agents) {
            if (!agent.alive) continue;
            agent.groupIntent = GroupIntent.MARK;
            agent.revengeOpportunity = null;
            agent.counterCoverAssessment = null;
            agent.attackOpportunity = null;
            agent.plannedTarget = agent.primaryMark;
        }
    }

    void onAttackBlocked(Musketeer attacker, Musketeer blocker) {
        var group3 = activeGroup3v3();
        if (group3 != null) {
            ++group3Metrics.attackBlocks;
            traceAttackExecution(attacker, blocker, "BLOCKED");
            logTactical("GROUP3_ATTACK_BLOCKED", "attacker=" + attacker.name(UiLanguage.FRENCH) +
                    " blocker=" + blocker.name(UiLanguage.FRENCH));
            if (attacker.groupIntent == GroupIntent.COMMIT_REVENGE) {
                ++group3Metrics.revengeBlocks;
                ++group3Metrics.preventedRevengeChains;
                finishGroup3Exchange(group3, "revenge_blocked_no_new_chain");
                clearGroup3SecondaryAttack(attacker);
            } else if (attacker.groupIntent == GroupIntent.COUNTER_COVER) {
                ++group3Metrics.counterCoverBlocks;
                ++group3Metrics.preventedRevengeChains;
                finishGroup3Exchange(group3, "counter_cover_blocked_no_new_chain");
                clearGroup3SecondaryAttack(attacker);
            } else createGroup3RevengeOpportunity(group3, attacker, blocker,
                    RevengeReason.ATTACK_BLOCKED);
            return;
        }
        var group = activeModernGroup();
        if (group == null) return;
        traceAttackExecution(attacker, blocker, "BLOCKED");
        logTactical("ATTACK_BLOCKED", "attacker=" + attacker.name(UiLanguage.FRENCH) +
                " blocker=" + blocker.name(UiLanguage.FRENCH));
        if (attacker.groupIntent == GroupIntent.COMMIT_REVENGE) {
            ++modernMetrics.revengeBlocked;
            beginGroupRecovery(group, "revenge_blocked");
            return;
        }
        if (attacker.groupIntent == GroupIntent.COUNTER_COVER) {
            beginGroupRecovery(group, "counter_cover_blocked");
            return;
        }
        createRevengeOpportunity(group, teammateOf(blocker), attacker, blocker,
                RevengeReason.ATTACK_BLOCKED);
    }

    void onAttackMissed(Musketeer attacker) {
        var group3 = activeGroup3v3();
        if (group3 != null) {
            ++group3Metrics.attackMisses;
            traceAttackExecution(attacker, attacker.target, "MISSED");
            if (attacker.groupIntent == GroupIntent.COMMIT_REVENGE) {
                ++group3Metrics.revengeMisses;
                ++group3Metrics.preventedRevengeChains;
                finishGroup3Exchange(group3, "revenge_missed_no_new_chain");
                clearGroup3SecondaryAttack(attacker);
            } else if (attacker.groupIntent == GroupIntent.COUNTER_COVER) {
                ++group3Metrics.counterCoverMisses;
                ++group3Metrics.preventedRevengeChains;
                finishGroup3Exchange(group3, "counter_cover_missed_no_new_chain");
                clearGroup3SecondaryAttack(attacker);
            } else if (attacker.target != null && attacker.target.alive &&
                    attacker.target.team != attacker.team)
                createGroup3RevengeOpportunity(group3, attacker, attacker.target,
                        RevengeReason.ATTACK_MISSED);
            return;
        }
        var group = activeModernGroup();
        if (group == null) return;
        traceAttackExecution(attacker, attacker.target, "MISSED");
        if (attacker.groupIntent == GroupIntent.COUNTER_COVER) return;
        if (attacker.groupIntent == GroupIntent.COMMIT_REVENGE) {
            ++modernMetrics.revengeMisses;
            beginGroupRecovery(group, "revenge_missed");
            return;
        }
        var protectedAlly = attacker.target;
        if (protectedAlly == null || !protectedAlly.alive ||
                protectedAlly.team == attacker.team) {
            beginGroupRecovery(group, "attack_missed_without_target");
            return;
        }
        logTactical("ATTACK_MISSED", "attacker=" + attacker.name(UiLanguage.FRENCH) +
                " target=" + protectedAlly.name(UiLanguage.FRENCH));
        createRevengeOpportunity(group, teammateOf(protectedAlly), attacker, protectedAlly,
                RevengeReason.ATTACK_MISSED);
    }

    private void createRevengeOpportunity(GroupEngagement group, Musketeer revenger,
                                          Musketeer attacker, Musketeer protectedAlly,
                                          RevengeReason reason) {
        if (revenger == null || !revenger.alive || !attacker.alive ||
                revenger.state == CombatState.BLOCK_STUN ||
                revenger.state == CombatState.RECOVERY) {
            ++modernMetrics.revengeRejectedState;
            logTactical("REVENGE_REJECTED", "category=state reason=no_available_revenger");
            beginGroupRecovery(group, "no_available_revenger");
            return;
        }
        var opposingCoverer = teammateOf(attacker);
        var vulnerability = attacker.state == CombatState.BLOCK_STUN ?
                Math.max(0, BLOCK_STUN_TIME - attacker.stateTime) + attacker.recoveryTime() :
                attacker.state == CombatState.RECOVERY ?
                        Math.max(0, attacker.recoveryTime() - attacker.stateTime) :
                        attacker.recoveryTime();
        var distance = revenger.pos.minus(attacker.pos).hypotenuse();
        var timeToAlign = Math.abs(angleDifference(revenger.weaponWorldAngle(),
                attacker.pos.minus(revenger.pos).radians())) / estimatedTurnSpeed(revenger);
        var timeToPrepare = revenger.state == CombatState.ATTACK_HOLD ? 0 :
                revenger.state == CombatState.ATTACK_PREPARE ?
                        Math.max(0, revenger.prepareTime() - revenger.stateTime) :
                        revenger.attackDirection.prepareTime;
        var timeToContact = Math.max(0, distance - revenger.projectedThrustLength() -
                Musketeer.BODY_RADIUS) / estimatedMovementSpeed(revenger);
        var opposingEta = opposingCoverer == null ? Double.POSITIVE_INFINITY :
                attackThreatEta(opposingCoverer, revenger);
        var allyClearance = distanceToSegment(protectedAlly.pos, revenger.pos, attacker.pos);
        var lineSafety = Math.clamp((allyClearance - Musketeer.BODY_RADIUS * 1.35) / 0.82,
                0, 1);
        var impactEta = timeToAlign + timeToPrepare + timeToContact;
        modernMetrics.revengeDistanceSum += distance;
        modernMetrics.revengeEtaSum += impactEta;
        modernMetrics.revengeVulnerabilityWindowSum += vulnerability;
        modernMetrics.revengeEtaMarginSum += vulnerability - impactEta;
        ++modernMetrics.revengeGeometrySamples;
        var temporal = Math.clamp((vulnerability - impactEta + 0.20) / 0.88, 0, 1);
        var counterSafety = Math.clamp((opposingEta - impactEta + 0.22) / 0.95, 0, 1);
        var skill = (revenger.stats.teamworkAbility() + revenger.stats.decisionMaking() +
                revenger.stats.attackTiming()) / 300.0;
        var score = Math.clamp(temporal * 0.42 + lineSafety * 0.23 +
                counterSafety * 0.18 + skill * 0.17, 0, 1);
        var rejection = "";
        if (distance > 3.05) rejection = "revenger_too_far";
        else if (lineSafety < 0.14) rejection = "ally_blocks_stab_line";
        else if (impactEta >= vulnerability + 0.18) rejection = "vulnerability_too_short";
        else if (score < 0.43) rejection = "revenge_score";
        if (!rejection.isEmpty()) recordRevengeRejection(rejection, timeToAlign,
                timeToPrepare, timeToContact, opposingEta, impactEta);
        var opportunity = new RevengeOpportunity(rejection.isEmpty(), revenger, attacker,
                protectedAlly, opposingCoverer, reason, vulnerability, timeToAlign,
                timeToPrepare, timeToContact, opposingEta, lineSafety, score, rejection);
        revenger.revengeOpportunity = opportunity;
        group.revengeOpportunity = opportunity;
        ++modernMetrics.revengeOpportunities;
        logTactical("REVENGE_OPPORTUNITY", "revenger=" +
                revenger.name(UiLanguage.FRENCH) +
                " target=" + attacker.name(UiLanguage.FRENCH) +
                " reason=" + reason +
                " window=" + "%.2f".formatted(vulnerability) +
                " contactETA=" + "%.2f".formatted(impactEta) +
                " counterCoverer=" + (opposingCoverer == null ? "NONE" :
                opposingCoverer.name(UiLanguage.FRENCH)) +
                " counterThreatETA=" + "%.2f".formatted(opposingEta) +
                " accepted=" + opportunity.viable() +
                (rejection.isEmpty() ? "" : " rejection=" + rejection));
        traceDecision(revenger, DecisionCategory.REVENGE,
                opportunity.viable() ? "ACCEPT_REVENGE" : "REFUSE_REVENGE",
                reason.name(), attacker, null, opportunity.score(), vulnerability,
                impactEta, Double.isFinite(opposingEta) ?
                        Math.clamp((impactEta + 0.22 - opposingEta) / 0.95, 0, 1) : 0,
                rejection.isEmpty() ? "NONE" : rejection);
        if (!opportunity.viable()) {
            beginGroupRecovery(group, "revenge_rejected_" + rejection);
            return;
        }
        group.revenger = revenger;
        group.vulnerableAttacker = attacker;
        revenger.groupIntent = GroupIntent.COMMIT_REVENGE;
        revenger.plannedTarget = attacker;
        protectedAlly.groupIntent = GroupIntent.COVER;
        attacker.groupIntent = GroupIntent.WAIT;
        changeGroupPhase(group, GroupPhase.REVENGE_WINDOW, reason.name().toLowerCase());
        var counter = assessCounterCover(opposingCoverer, revenger, attacker, impactEta);
        group.counterCoverAssessment = counter;
        if (opposingCoverer != null) opposingCoverer.counterCoverAssessment = counter;
        ++modernMetrics.counterCoverOpportunities;
        if (counter.viable()) {
            group.counterCoverer = opposingCoverer;
            opposingCoverer.groupIntent = GroupIntent.COUNTER_COVER;
            opposingCoverer.plannedTarget = revenger;
            ++modernMetrics.counterCoverThreatsEstablished;
            changeGroupPhase(group, GroupPhase.COUNTER_COVER, "counter_threat_available");
        }
    }

    private void recordRevengeRejection(String rejection, double alignEta, double prepareEta,
                                        double contactEta, double opposingEta, double impactEta) {
        String category;
        if (rejection.equals("revenger_too_far")) {
            ++modernMetrics.revengeRejectedDistance;
            category = "distance";
        } else if (rejection.equals("ally_blocks_stab_line")) {
            ++modernMetrics.revengeRejectedLine;
            category = "line";
        } else if (rejection.equals("vulnerability_too_short")) {
            if (alignEta > prepareEta + contactEta && alignEta > 0.22) {
                ++modernMetrics.revengeRejectedAlignment;
                category = "alignment";
            } else {
                ++modernMetrics.revengeRejectedWindow;
                category = "window";
            }
        } else if (opposingEta <= impactEta + 0.22) {
            ++modernMetrics.revengeRejectedCounterThreat;
            category = "counter_threat";
        } else {
            ++modernMetrics.revengeRejectedWindow;
            category = "window";
        }
        logTactical("REVENGE_REJECTED", "category=" + category + " reason=" + rejection);
    }

    private CounterCoverAssessment assessCounterCover(Musketeer coverer, Musketeer revenger,
                                                      Musketeer protectedAlly,
                                                      double revengeImpactEta) {
        if (coverer == null || !coverer.alive || !isTracked(coverer, revenger))
            return new CounterCoverAssessment(false, coverer, revenger, protectedAlly,
                    Double.POSITIVE_INFINITY, revengeImpactEta, 1, 0,
                    "coverer_unavailable");
        var threatEta = attackThreatEta(coverer, revenger);
        var routeClearance = distanceToSegment(protectedAlly.pos, coverer.pos, revenger.pos);
        var approachRisk = Math.clamp(1 - routeClearance / 1.15, 0, 1);
        var skill = (coverer.stats.teamworkAbility() +
                coverer.stats.decisionMaking()) / 200.0;
        var temporal = Math.clamp((revengeImpactEta - threatEta + 0.38) / 0.92, 0, 1);
        var score = Math.clamp(temporal * 0.52 + skill * 0.28 +
                (1 - approachRisk) * 0.20, 0, 1);
        var rejection = threatEta > revengeImpactEta + 0.30 ? "counter_too_late" :
                score < 0.40 ? "counter_score" : "";
        return new CounterCoverAssessment(rejection.isEmpty(), coverer, revenger,
                protectedAlly, threatEta, revengeImpactEta, approachRisk, score, rejection);
    }

    private void onModernAttackHit(Musketeer attacker, Musketeer defender,
                                   HitZone zone, boolean backstab) {
        var engagement = attacker.engagement;
        var coordination = engagement == null ? null : engagement.coordination;
        if (coordination != null && coordination.defender == defender &&
                (coordination.phase == TwoVsOneCoordinationPhase.DOUBLE_THREAT_ACTIVE ||
                        coordination.secondaryReleased &&
                                simulationTime - coordination.lastAttackAt <= 0.55)) {
            ++modernMetrics.coordinatedHits;
            if (!defender.alive) ++modernMetrics.coordinatedKills;
        }
        if (activeGroup3v3() != null) {
            ++group3Metrics.attackHits;
            if (!defender.alive) ++group3Metrics.attackKills;
            if (attacker.attackOpportunity != null && attacker.attackOpportunity.target() == defender &&
                    attacker.attackOpportunity.primaryReason() ==
                            AttackOpportunityReason.TARGET_ISOLATED_FROM_COVER_NETWORK) {
                ++group3Metrics.clusterAttackHits;
                if (!defender.alive) ++group3Metrics.clusterAttackKills;
            }
            if (attacker.groupIntent == GroupIntent.COMMIT_REVENGE) {
                ++group3Metrics.revengeHits;
                if (!defender.alive) ++group3Metrics.revengeKills;
            } else if (attacker.groupIntent == GroupIntent.COUNTER_COVER) {
                ++group3Metrics.counterCoverHits;
                if (!defender.alive) ++group3Metrics.counterCoverKills;
            }
            traceAttackExecution(attacker, defender, defender.alive ? "HIT" : "KILL");
            logTactical("GROUP3_ATTACK_HIT", "attacker=" + attacker.name(UiLanguage.FRENCH) +
                    " target=" + defender.name(UiLanguage.FRENCH) + " zone=" + zone +
                    " damage=" + defender.lastDamage + " lethal=" + !defender.alive);
            if (defender.alive && (attacker.groupIntent == GroupIntent.COMMIT_REVENGE ||
                    attacker.groupIntent == GroupIntent.COUNTER_COVER)) {
                if (activeGroup3v3().exchangeActive())
                    finishGroup3Exchange(activeGroup3v3(),
                            attacker.groupIntent == GroupIntent.COMMIT_REVENGE ?
                                    "revenge_hit_non_lethal" : "counter_cover_hit_non_lethal");
                clearGroup3SecondaryAttack(attacker);
            }
            return;
        }
        var group = activeModernGroup();
        if (group == null) return;
        traceAttackExecution(attacker, defender, defender.alive ? "HIT" : "KILL");
        if (attacker.groupIntent == GroupIntent.COMMIT_REVENGE) {
            ++modernMetrics.revengeHits;
            if (!defender.alive) ++modernMetrics.revengeKills;
            logTactical("REVENGE_HIT", "revenger=" + attacker.name(UiLanguage.FRENCH) +
                    " target=" + defender.name(UiLanguage.FRENCH) +
                    " zone=" + zone + " damage=" + defender.lastDamage);
        } else if (attacker.groupIntent == GroupIntent.COUNTER_COVER) {
            ++modernMetrics.counterCoverHits;
            if (!defender.alive) ++modernMetrics.counterCoverKills;
            logTactical("COUNTER_COVER_HIT", "coverer=" +
                    attacker.name(UiLanguage.FRENCH) +
                    " target=" + defender.name(UiLanguage.FRENCH) +
                    " zone=" + zone + " damage=" + defender.lastDamage);
        } else if (attacker.groupIntent == GroupIntent.COMMIT_ATTACK &&
                attacker.attackOpportunity != null &&
                attacker.attackOpportunity.target() == defender) {
            var reason = attacker.attackOpportunity.primaryReason();
            modernMetrics.attackHitsByReason.merge(reason, 1, Integer::sum);
            if (reason == AttackOpportunityReason.TARGET_MOVING_INTO_LINE)
                ++modernMetrics.movingIntoLineHits;
            if (!defender.alive) {
                modernMetrics.attackKillsByReason.merge(reason, 1, Integer::sum);
                if (reason == AttackOpportunityReason.TARGET_MOVING_INTO_LINE)
                    ++modernMetrics.movingIntoLineKills;
            }
        }
        if (modernMetrics.firstDeathCause == null && !defender.alive)
            modernMetrics.firstDeathCause = backstab ? FirstDeathCause.BACKSTAB :
                    attacker.chamberType != ChamberType.NONE ? FirstDeathCause.CHAMBER :
                            attacker.groupIntent == GroupIntent.COMMIT_REVENGE ?
                                    FirstDeathCause.REVENGE :
                                    attacker.groupIntent == GroupIntent.COUNTER_COVER ?
                                            FirstDeathCause.COUNTER_COVER :
                                            FirstDeathCause.NORMAL_ATTACK;
        if (defender.alive) beginGroupRecovery(group, "hit_without_elimination");
    }

    private void traceAttackExecution(Musketeer attacker, Musketeer target, String action) {
        var opportunity = attacker.attackOpportunity != null &&
                attacker.attackOpportunity.target() == target ? attacker.attackOpportunity : null;
        var reason = opportunity != null ? opportunity.primaryReason().name() :
                attacker.groupIntent == GroupIntent.COMMIT_REVENGE ? "REVENGE" :
                        attacker.groupIntent == GroupIntent.COUNTER_COVER ? "COUNTER_COVER" :
                                attacker.chamberType != ChamberType.NONE ? "CHAMBER" :
                                        "PHYSICAL_ATTACK";
        traceDecision(attacker, DecisionCategory.ATTACK, action, reason, target, null,
                opportunity == null ? 0 : opportunity.score(),
                opportunity == null ? 0 : opportunity.availableWindow(),
                opportunity == null ? 0 : opportunity.impactEta(),
                opportunity == null ? 0 : opportunity.revengeRisk(), "NONE");
    }

    private void createModernTwoVsOneAfterElimination(List<Musketeer> attackers, Musketeer defender,
                                                       String reason) {
        var fixer = defender.target != null && defender.target.alive &&
                attackers.contains(defender.target) ? defender.target :
                attackers.get(0).pos.minus(defender.pos).hypotenuse() <=
                        attackers.get(1).pos.minus(defender.pos).hypotenuse() ?
                        attackers.get(0) : attackers.get(1);
        var flanker = attackers.get(0) == fixer ? attackers.get(1) : attackers.get(0);
        createModernTwoVsOne(fixer, flanker, defender, null, reason);
        ++modernMetrics.postEliminationTwoVsOneCreated;
        ++modernMetrics.postEliminationTwoVsOne;
        logTactical("POST_ELIMINATION_TWO_VS_ONE", "fixer=" +
                fixer.name(UiLanguage.FRENCH) +
                " flanker=" + flanker.name(UiLanguage.FRENCH) +
                " defender=" + defender.name(UiLanguage.FRENCH) +
                " reason=" + reason);
    }

    private void updateModernStructure(double dt) {
        modernStructureTime -= dt;
        if (modernStructureTime > 0) return;
        modernStructureTime = MODERN_STRUCTURE_INTERVAL;
        if (supportPlan != null) return;
        for (var engagement : engagementManager.engagements())
            if (engagement.type == EngagementType.TWO_VS_ONE &&
                    engagement.status != EngagementStatus.BROKEN &&
                    engagement.status != EngagementStatus.FINISHED) return;

        var group = activeModernGroup();
        if (group == null) return;
        Musketeer selected = null;
        SupportOpportunity selectedOpportunity = null;
        for (var agent : agents) {
            if (!agent.alive) continue;
            var opportunity = assessIsolationSupport(agent, group);
            supportOpportunities.put(agent, opportunity);
            if (!opportunity.viable() || selectedOpportunity != null &&
                    opportunity.score() <= selectedOpportunity.score()) continue;
            selected = agent;
            selectedOpportunity = opportunity;
        }
        for (var agent : agents) {
            var opportunity = supportOpportunities.get(agent);
            if (opportunity == null || opportunity.reason() == null) {
                supportOpportunityEpisodes.remove(agent);
                continue;
            }
            registerSupportOpportunity(agent, opportunity, agent == selected);
        }
        if (selected != null) startIsolationSupportPlan(selected, selectedOpportunity);
    }

    private SupportOpportunity assessIsolationSupport(Musketeer supporter,
                                                       GroupEngagement group) {
        var formerOpponent = supporter.primaryMark;
        var teammate = teammateOf(supporter);
        var primaryDefender = teammate == null ? null : teammate.primaryMark;
        if (formerOpponent == null || teammate == null || primaryDefender == null ||
                !formerOpponent.alive || !teammate.alive || !primaryDefender.alive)
            return SupportOpportunity.unavailable("invalid_group_assignment");
        if (supporter.isAttacking() || supporter.state == CombatState.RECOVERY ||
                supporter.state == CombatState.BLOCK_STUN)
            return SupportOpportunity.unavailable("supporter_action_locked");
        var formerDistance = supporter.pos.minus(formerOpponent.pos).hypotenuse();
        if (formerDistance < ISOLATION_MIN_DISTANCE)
            return SupportOpportunity.unavailable("not_durably_isolated");
        if (teammate.pos.minus(primaryDefender.pos).hypotenuse() > 2.85)
            return SupportOpportunity.unavailable("partner_fight_too_far");
        var awareness = supporter.opponentAwareness.get(formerOpponent);
        if (awareness != null && observedAttackStillThreatens(supporter, awareness))
            return SupportOpportunity.unavailable("immediate_attack_threat");
        var left = assessModernFlankSide(EngagementSlot.LEFT, supporter, teammate,
                primaryDefender, formerOpponent);
        var right = assessModernFlankSide(EngagementSlot.RIGHT, supporter, teammate,
                primaryDefender, formerOpponent);
        var side = left.score() <= right.score() ? left : right;
        var destination = side.slot().destinationAround(primaryDefender, 1.34);
        var interception = assessInterception(supporter, formerOpponent, destination, 0);
        var returnEta = formerOpponent.pos.minus(group.center).hypotenuse() /
                estimatedMovementSpeed(formerOpponent);
        var advantage = Math.min(interception.interceptorEta(), returnEta) -
                side.travelTime();
        var routeClearance = distanceToSegment(formerOpponent.pos, supporter.pos, destination);
        var leaveRisk = Math.clamp(1 - routeClearance / 1.65, 0, 1);
        var skill = (supporter.stats.teamworkAbility() +
                supporter.stats.decisionMaking()) / 200.0;
        var supportValue = Math.clamp(0.42 +
                (2.85 - teammate.pos.minus(primaryDefender.pos).hypotenuse()) * 0.16 +
                skill * 0.24, 0, 1);
        var score = Math.clamp(0.34 * supportValue +
                0.34 * Math.clamp(advantage / 1.35, 0, 1) +
                0.18 * Math.clamp(1 - side.score() / 3.2, 0, 1) +
                0.14 * skill - 0.32 * leaveRisk, 0, 1);
        var rejection = "";
        if (advantage < ISOLATION_MIN_ADVANTAGE) rejection = "isolation_advantage_too_short";
        else if (leaveRisk > 0.48) rejection = "isolation_route_unsafe";
        else if (score < 0.44) rejection = "isolation_score_too_low";
        return new SupportOpportunity(rejection.isEmpty(), score,
                Math.max(0, advantage), side.travelTime(), returnEta,
                interception.interceptorEta(), leaveRisk, supportValue,
                SupportReason.SPATIAL_ADVANTAGE, rejection, side, destination,
                interception.point());
    }

    private void startIsolationSupportPlan(Musketeer supporter,
                                           SupportOpportunity opportunity) {
        var fixer = teammateOf(supporter);
        var formerOpponent = supporter.primaryMark;
        var defender = fixer == null ? null : fixer.primaryMark;
        if (fixer == null || formerOpponent == null || defender == null) return;
        var duration = Math.clamp(Math.max(opportunity.availableWindow() + 1.55,
                        opportunity.timeToSupport() + 0.95),
                SUPPORT_MIN_PERSISTENCE, SUPPORT_MAX_DURATION);
        supportPlan = new SupportPlan(supporter, fixer, defender, formerOpponent,
                opportunity, simulationTime, simulationTime + duration);
        supporter.tacticalDestination = opportunity.supportDestination();
        ++modernMetrics.preEliminationSupportPlansStarted;
        logTactical("ISOLATION_SUPPORT_PLAN_STARTED", "supporter=" +
                supporter.name(UiLanguage.FRENCH) + " fixer=" +
                fixer.name(UiLanguage.FRENCH) + " defender=" +
                defender.name(UiLanguage.FRENCH) + " isolated=" +
                formerOpponent.name(UiLanguage.FRENCH) + " side=" +
                opportunity.flankSide().slot());
    }

    private SupportOpportunity assessSupportOpportunity(Musketeer supporter, Engagement duel,
                                                        Musketeer teammate,
                                                        Engagement teammateDuel) {
        var formerOpponent = duelOpponent(duel, supporter);
        var primaryDefender = duelOpponent(teammateDuel, teammate);
        if (formerOpponent == null || primaryDefender == null || !formerOpponent.alive ||
                !primaryDefender.alive)
            return SupportOpportunity.unavailable("invalid_duel");
        if (supporter.isAttacking() || supporter.state == CombatState.RECOVERY ||
                supporter.state == CombatState.BLOCK_STUN)
            return SupportOpportunity.unavailable("supporter_action_locked");
        var freedom = assessFreedom(supporter, duel, teammate, teammateDuel);
        freedomAssessments.put(supporter, freedom);
        if (freedom.free() && !durableFreedomEpisodes.getOrDefault(supporter, false))
            ++modernMetrics.preEliminationDurableFreedomAssessments;
        durableFreedomEpisodes.put(supporter, freedom.free());
        var awareness = supporter.opponentAwareness.get(formerOpponent);
        if (awareness == null || !awareness.visible && awareness.memoryTime <= 0)
            return SupportOpportunity.unavailable("former_opponent_untracked");

        var observedPosition = awareness.lastKnown;
        var formerDistance = observedPosition.minus(supporter.pos).hypotenuse();
        var towardSupporter = supporter.pos.minus(observedPosition).radians();
        var bodyError = Math.abs(angleDifference(awareness.observedBodyAngle, towardSupporter));
        var weaponError = Math.abs(angleDifference(awareness.observedWeaponAngle, towardSupporter));
        if (observedAttackStillThreatens(supporter, awareness))
            return SupportOpportunity.unavailable("immediate_attack_threat");
        var stateRemaining = 0.0;
        SupportReason reason = null;
        if (awareness.observedState == CombatState.BLOCK_STUN) {
            stateRemaining = Math.max(0, BLOCK_STUN_TIME - awareness.observedStateTime) +
                    formerOpponent.recoveryTime() * 0.72;
            reason = SupportReason.OPPONENT_BLOCK_STUNNED;
        } else if (awareness.observedState == CombatState.RECOVERY) {
            stateRemaining = Math.max(0, formerOpponent.recoveryTime() -
                    awareness.observedStateTime);
            reason = SupportReason.OPPONENT_RECOVERING;
        } else if ((awareness.observedState == CombatState.ATTACK_ACTIVE ||
                awareness.observedState == CombatState.CHAMBER_ACTIVE) &&
                !observedAttackStillThreatens(supporter, awareness)) {
            stateRemaining = Math.max(0, formerOpponent.activeTime() -
                    awareness.observedStateTime) + formerOpponent.recoveryTime() * 0.72;
            reason = SupportReason.OPPONENT_MISSED_ATTACK;
        } else if (freedom.free())
            reason = SupportReason.SPATIAL_ADVANTAGE;
        else if (formerDistance >= 2.45 && bodyError >= Math.toRadians(68))
            reason = SupportReason.OPPONENT_OUT_OF_POSITION;
        else if (formerDistance >= 2.05 && bodyError >= Math.toRadians(118))
            reason = SupportReason.OPPONENT_LOST_CONTACT;
        else if (opponentBlockedByCombat(formerOpponent, supporter, teammate, primaryDefender))
            reason = SupportReason.OPPONENT_BLOCKED_BY_ALLY;
        if (reason == null) return SupportOpportunity.unavailable("no_concrete_opening");

        var left = assessModernFlankSide(EngagementSlot.LEFT, supporter, teammate,
                primaryDefender, formerOpponent);
        var right = assessModernFlankSide(EngagementSlot.RIGHT, supporter, teammate,
                primaryDefender, formerOpponent);
        var side = left.score() <= right.score() ? left : right;
        var destination = side.slot().destinationAround(primaryDefender, 1.34);
        var actionLock = Math.max(stateRemaining,
                observedActionLockRemaining(formerOpponent, awareness));
        var interception = assessInterception(supporter, formerOpponent, destination, actionLock);
        var turnDelay = weaponError / estimatedTurnSpeed(formerOpponent);
        var closingDelay = Math.max(0, formerDistance - awareness.observedProjectedRange -
                Musketeer.BODY_RADIUS - 0.24) / estimatedMovementSpeed(formerOpponent);
        var availableWindow = Math.max(0, stateRemaining + turnDelay * 0.62 +
                closingDelay * 0.70 - SUPPORT_SAFETY_MARGIN);
        if (freedom.free())
            availableWindow = Math.max(availableWindow,
                    Math.max(0, freedom.timeForOpponentToContest() -
                            freedom.timeToSupport()));
        var pathClearance = distanceToSegment(observedPosition, supporter.pos, destination);
        var routeThreat = Math.clamp(1 - pathClearance /
                (awareness.observedProjectedRange + Musketeer.BODY_RADIUS + 0.28), 0, 1);
        var facingRisk = Math.clamp(1 - weaponError / Math.toRadians(80), 0, 1);
        var recoveryRisk = Math.clamp(1 - availableWindow / 1.10, 0, 1);
        var departure = destination.minus(supporter.pos);
        var backTurnRisk = departure.hypotenuse() <= 0.0001 ? 0 :
                Math.clamp((Math.abs(angleDifference(supporter.angle, departure.radians())) -
                        Math.toRadians(85)) / Math.toRadians(95), 0, 1);
        var interceptionRisk = Math.clamp(
                (INTERCEPTION_TIME_MARGIN - interception.margin() + 0.35) / 0.90, 0, 1);
        var leaveRisk = Math.clamp(routeThreat * 0.24 +
                facingRisk * 0.16 + recoveryRisk * 0.18 +
                backTurnRisk * 0.10 + interceptionRisk * 0.12, 0, 1);
        var teammateTrouble = switch (teammate.state) {
            case BLOCK_STUN -> 1.0;
            case BLOCK_PREPARE, BLOCKING -> 0.72;
            case RECOVERY -> 0.62;
            default -> 0.28;
        };
        var defenderOccupied = primaryDefender.target == teammate ? 0.82 : 0.34;
        var defenderFacing = Math.abs(angleDifference(primaryDefender.angle,
                teammate.pos.minus(primaryDefender.pos).radians())) <= Math.toRadians(65) ?
                0.82 : 0.30;
        var localDistance = teammate.pos.minus(primaryDefender.pos).hypotenuse();
        var contactValue = Math.clamp(1 - Math.abs(localDistance - 1.48) / 2.2, 0, 1);
        var healthNeed = Math.clamp((primaryDefender.hitPoints - teammate.hitPoints + 35) / 100.0,
                0, 1);
        var skill = (supporter.stats.teamworkAbility() +
                supporter.stats.decisionMaking()) / 200.0;
        var supportValue = Math.clamp(teammateTrouble * 0.22 + defenderOccupied * 0.22 +
                defenderFacing * 0.16 + contactValue * 0.18 + healthNeed * 0.10 +
                skill * 0.12, 0, 1);
        var temporalValue = Math.clamp((availableWindow - SUPPORT_PREPARATION_TIME) / 0.85 +
                interception.margin() * 0.22, 0, 1);
        var geometryValue = Math.clamp(1 - side.score() / 3.2, 0, 1);
        var score = Math.clamp(supportValue * 0.42 + temporalValue * 0.25 +
                geometryValue * 0.15 + skill * 0.18 - leaveRisk * 0.40 -
                interceptionRisk * 0.10, 0, 1);
        var threshold = 0.43 - skill * 0.06;
        var rejection = "";
        if (availableWindow < SUPPORT_PREPARATION_TIME + 0.055)
            rejection = "window_too_short";
        else if (side.travelTime() > 2.65) rejection = "partner_fight_too_far";
        else if (leaveRisk > 0.68) rejection = "leave_risk_too_high";
        else if (interception.interceptorEta() <= SUPPORT_PREPARATION_TIME * 0.82)
            rejection = "interceptor_already_controls_path";
        else if (score < threshold) rejection = "support_score_too_low";
        return new SupportOpportunity(rejection.isEmpty(), score, availableWindow,
                side.travelTime(), stateRemaining, interception.interceptorEta(), leaveRisk,
                supportValue, reason, rejection, side, destination, interception.point());
    }

    private boolean observedAttackStillThreatens(Musketeer supporter, OpponentAwareness awareness) {
        if (awareness.observedState != CombatState.ATTACK_ACTIVE &&
                awareness.observedState != CombatState.CHAMBER_ACTIVE) return false;
        var offset = supporter.pos.minus(awareness.lastKnown);
        return offset.hypotenuse() <= awareness.observedProjectedRange +
                Musketeer.BODY_RADIUS + 0.26 &&
                Math.abs(angleDifference(awareness.observedWeaponAngle, offset.radians())) <=
                        Math.toRadians(32);
    }

    private double observedActionLockRemaining(Musketeer opponent, OpponentAwareness awareness) {
        return switch (awareness.observedState) {
            case BLOCK_STUN -> Math.max(0, BLOCK_STUN_TIME - awareness.observedStateTime) +
                    opponent.recoveryTime();
            case RECOVERY -> Math.max(0, opponent.recoveryTime() - awareness.observedStateTime);
            case ATTACK_ACTIVE, CHAMBER_ACTIVE ->
                    Math.max(0, opponent.activeTime() - awareness.observedStateTime) +
                            opponent.recoveryTime();
            default -> 0;
        };
    }

    private double estimatedTurnSpeed(Musketeer agent) {
        return 2.35 + agent.stats.footwork() * 0.018;
    }

    private boolean opponentBlockedByCombat(Musketeer opponent, Musketeer supporter,
                                             Musketeer teammate, Musketeer defender) {
        var routeClearance = Math.min(
                distanceToSegment(teammate.pos, opponent.pos, supporter.pos),
                distanceToSegment(defender.pos, opponent.pos, supporter.pos));
        return routeClearance <= Musketeer.BODY_RADIUS * 1.65 &&
                opponent.pos.minus(supporter.pos).hypotenuse() >= 2.0;
    }

    private void registerSupportOpportunity(Musketeer supporter, SupportOpportunity opportunity,
                                            boolean accepted) {
        var formerOpponent = duelOpponent(activeDuelOf(supporter), supporter);
        if (formerOpponent == null && activeModernGroup() != null)
            formerOpponent = supporter.primaryMark;
        var episode = (formerOpponent == null ? "none" : indexOfAgent(formerOpponent)) + ":" +
                opportunity.reason() + ":" +
                (formerOpponent == null ? -1 : formerOpponent.attackSequence) + ":" +
                (formerOpponent == null ? -1 : formerOpponent.blockSequence);
        if (episode.equals(supportOpportunityEpisodes.get(supporter))) return;
        supportOpportunityEpisodes.put(supporter, episode);
        ++modernMetrics.preEliminationSupportOpportunities;
        if (!accepted) ++modernMetrics.preEliminationSupportPlansRejected;
        var rejection = accepted ? "" : opportunity.viable() ?
                "simultaneous_lower_priority" : opportunity.rejectionReason();
        logTactical("SUPPORT_OPPORTUNITY", "supporter=" +
                supporter.name(UiLanguage.FRENCH) +
                " formerOpponent=" + (formerOpponent == null ? "NONE" :
                formerOpponent.name(UiLanguage.FRENCH)) +
                " reason=" + opportunity.reason() +
                " window=" + "%.2f".formatted(opportunity.availableWindow()) +
                " timeToSupport=" + "%.2f".formatted(opportunity.timeToSupport()) +
                " timeToRecover=" + "%.2f".formatted(opportunity.timeForOpponentToRecover()) +
                " timeToIntercept=" +
                "%.2f".formatted(opportunity.timeForOpponentToIntercept()) +
                " leaveRisk=" + "%.2f".formatted(opportunity.leaveRisk()) +
                " supportValue=" + "%.2f".formatted(opportunity.supportValue()) +
                " score=" + "%.2f".formatted(opportunity.score()) +
                " accepted=" + accepted +
                (rejection.isEmpty() ? "" : " rejection=" + rejection));
    }

    private void startSupportPlan(Musketeer supporter, SupportOpportunity opportunity) {
        var duel = activeDuelOf(supporter);
        var fixer = teammateOf(supporter);
        var supportDuel = activeDuelOf(fixer);
        var formerOpponent = duelOpponent(duel, supporter);
        var defender = duelOpponent(supportDuel, fixer);
        if (duel == null || supportDuel == null || formerOpponent == null || defender == null) return;
        var duration = Math.clamp(Math.max(opportunity.availableWindow() + 1.55,
                        opportunity.timeToSupport() + 0.95),
                SUPPORT_MIN_PERSISTENCE, SUPPORT_MAX_DURATION);
        supportPlan = new SupportPlan(supporter, fixer, defender, formerOpponent,
                opportunity, simulationTime, simulationTime + duration);
        supporter.tacticalDestination = opportunity.supportDestination();
        ++modernMetrics.preEliminationSupportPlansStarted;
        lastModernReconstructionReason = "temporary_support_preparation";
        logTactical("SUPPORT_PLAN_STARTED", "supporter=" +
                supporter.name(UiLanguage.FRENCH) +
                " fixer=" + fixer.name(UiLanguage.FRENCH) +
                " defender=" + defender.name(UiLanguage.FRENCH) +
                " interceptor=" + formerOpponent.name(UiLanguage.FRENCH) +
                " side=" + opportunity.flankSide().slot() +
                " state=" + supportPlan.state +
                " allFourAlive=" + allFourAlive() +
                " window=" + "%.2f".formatted(opportunity.availableWindow()) +
                " etaSupport=" + "%.2f".formatted(opportunity.timeToSupport()) +
                " etaIntercept=" +
                "%.2f".formatted(opportunity.timeForOpponentToIntercept()) +
                " reason=" + opportunity.reason());
    }

    private void updateSupportPlan(double dt) {
        if (supportPlan == null) return;
        var plan = supportPlan;
        if (!plan.supporter.alive || !plan.fixer.alive || !plan.primaryDefender.alive ||
                !plan.formerOpponent.alive) {
            finishSupportPlan(SupportPlanState.ABORTED, "elimination", false);
            return;
        }
        if (plan.state == SupportPlanState.PREPARING_TO_LEAVE) {
            if (isImmediateAttackThreat(plan.supporter, plan.formerOpponent) ||
                    plan.supporter.state == CombatState.BLOCK_STUN ||
                    plan.supporter.state == CombatState.RECOVERY) {
                finishSupportPlan(SupportPlanState.ABORTED, "departure_became_dangerous", true);
                return;
            }
            if (supportWindowEndedEarly(plan)) {
                finishSupportPlan(SupportPlanState.ABORTED, "former_opponent_recovered_early",
                        false);
                return;
            }
            if (simulationTime - plan.startedAt >= SUPPORT_PREPARATION_TIME)
                formPreEliminationTwoVsOne(plan);
            return;
        }
        var minimumPersistenceReached =
                simulationTime - plan.startedAt >= SUPPORT_MIN_PERSISTENCE;
        if (simulationTime >= plan.expiresAt && minimumPersistenceReached) {
            finishSupportPlan(SupportPlanState.ABORTED, "support_window_expired", true);
            return;
        }
        if (plan.engagement == null || plan.engagement.status == EngagementStatus.FINISHED) {
            finishSupportPlan(SupportPlanState.ABORTED, "engagement_invalid", true);
            return;
        }
        var geometryInvalid = plan.fixer.pos.minus(plan.primaryDefender.pos).hypotenuse() > 3.85 ||
                plan.supportDestination.x() < Musketeer.BODY_RADIUS ||
                plan.supportDestination.x() > ARENA_WIDTH - Musketeer.BODY_RADIUS ||
                plan.supportDestination.y() < Musketeer.BODY_RADIUS ||
                plan.supportDestination.y() > ARENA_HEIGHT - Musketeer.BODY_RADIUS;
        if (geometryInvalid && minimumPersistenceReached) {
            finishSupportPlan(SupportPlanState.ABORTED, "support_geometry_lost", true);
            return;
        }
        if ((plan.supporter.state == CombatState.BLOCK_STUN ||
                plan.supporter.state == CombatState.RECOVERY) && minimumPersistenceReached) {
            finishSupportPlan(SupportPlanState.ABORTED, "supporter_disabled_by_threat", true);
            return;
        }
        updateSupportProgress(plan);
    }

    private boolean supportWindowEndedEarly(SupportPlan plan) {
        return switch (plan.reason) {
            case OPPONENT_BLOCK_STUNNED ->
                    plan.formerOpponent.state != CombatState.BLOCK_STUN &&
                            plan.formerOpponent.state != CombatState.RECOVERY;
            case OPPONENT_RECOVERING ->
                    plan.formerOpponent.state != CombatState.RECOVERY;
            case OPPONENT_MISSED_ATTACK ->
                    plan.formerOpponent.state == CombatState.NEUTRAL;
            default -> false;
        };
    }

    private void formPreEliminationTwoVsOne(SupportPlan plan) {
        if (!allFourAlive()) {
            finishSupportPlan(SupportPlanState.ABORTED, "elimination", false);
            return;
        }
        createModernTwoVsOne(plan.fixer, plan.supporter, plan.primaryDefender,
                plan.formerOpponent, plan.reason.name().toLowerCase(), plan.flankSide);
        plan.engagement = plan.supporter.engagement;
        plan.changeState(SupportPlanState.MOVING_TO_SUPPORT, simulationTime);
        ++modernMetrics.preEliminationTwoVsOneFormed;
        ++modernMetrics.preEliminationIsolationTwoVsOne;
        lastModernReconstructionReason = "pre_elimination_support";
        logTactical("PRE_ELIMINATION_TWO_VS_ONE", "fixer=" +
                plan.fixer.name(UiLanguage.FRENCH) +
                " flanker=" + plan.supporter.name(UiLanguage.FRENCH) +
                " defender=" + plan.primaryDefender.name(UiLanguage.FRENCH) +
                " interceptor=" + plan.formerOpponent.name(UiLanguage.FRENCH) +
                " side=" + plan.flankSide);
    }

    private void updateSupportProgress(SupportPlan plan) {
        var flankDistance = plan.supporter.pos.minus(
                plan.engagement.flankerDestination()).hypotenuse();
        if (!plan.reachedFlankSide && (flankDistance <= 0.55 ||
                plan.supporter.flankPhase != FlankPhase.APPROACH_SIDE)) {
            plan.reachedFlankSide = true;
            ++modernMetrics.supporterReachedFlankSide;
        }
        var defenderDistance = plan.supporter.pos.minus(plan.primaryDefender.pos).hypotenuse();
        if (!plan.reachedPrimaryDefender && defenderDistance <=
                plan.supporter.projectedThrustLength() + Musketeer.BODY_RADIUS + 0.62) {
            plan.reachedPrimaryDefender = true;
            ++modernMetrics.supporterReachedPrimaryDefender;
        }
        if (!plan.attackedPrimaryDefender && plan.supporter.target == plan.primaryDefender &&
                (plan.supporter.state == CombatState.ATTACK_PREPARE ||
                        plan.supporter.state == CombatState.ATTACK_HOLD ||
                        plan.supporter.isAttacking())) {
            plan.attackedPrimaryDefender = true;
            ++modernMetrics.supporterAttackedPrimaryDefender;
        }
        if (plan.state != SupportPlanState.MOVING_TO_SUPPORT ||
                !plan.reachedFlankSide || !plan.reachedPrimaryDefender ||
                !fixerMaintainsPressure(plan.engagement) ||
                plan.supporter.threatResponse == ThreatResponse.ENGAGE ||
                plan.formerOpponent.interceptionPhase == InterceptionPhase.PRESENT_THREAT ||
                plan.formerOpponent.interceptionPhase == InterceptionPhase.ENGAGE_FLANKER) return;
        plan.changeState(SupportPlanState.TWO_VS_ONE_ACTIVE, simulationTime);
        plan.localAdvantageAt = simulationTime;
        ++modernMetrics.preEliminationLocalAdvantagesReached;
        logTactical("LOCAL_ADVANTAGE_ACTIVE", "fixer=" +
                plan.fixer.name(UiLanguage.FRENCH) +
                " flanker=" + plan.supporter.name(UiLanguage.FRENCH) +
                " defender=" + plan.primaryDefender.name(UiLanguage.FRENCH) +
                " durationBeforeInterception=" +
                "%.2f".formatted(simulationTime - plan.startedAt));
    }

    private void finishSupportPlan(SupportPlanState state, String reason, boolean restoreDuels) {
        if (supportPlan == null) return;
        var plan = supportPlan;
        plan.changeState(state, simulationTime);
        plan.finalReason = reason;
        if (state == SupportPlanState.ABORTED) {
            ++modernMetrics.preEliminationSupportPlansAborted;
            if (reason.equals("support_window_expired")) ++modernMetrics.supportPlansExpired;
            else if (reason.equals("departure_became_dangerous") ||
                    reason.equals("supporter_disabled_by_threat"))
                ++modernMetrics.supportPlansCancelledByThreat;
            else if (reason.equals("former_opponent_recovered_early"))
                ++modernMetrics.supportPlansCancelledByRecoveryEnd;
            else if (reason.equals("support_geometry_lost") ||
                    reason.equals("engagement_invalid"))
                ++modernMetrics.supportPlansCancelledByGeometry;
            logTactical("SUPPORT_PLAN_ABORTED", "supporter=" +
                    plan.supporter.name(UiLanguage.FRENCH) +
                    " reason=" + reason + " state=" + state);
        } else if (state == SupportPlanState.COMPLETED)
            logTactical("SUPPORT_PLAN_COMPLETED", "supporter=" +
                    plan.supporter.name(UiLanguage.FRENCH) +
                    " reason=" + reason);
        if (restoreDuels && allFourAlive()) {
            engagementManager.breakActive();
            clearModernTacticalState();
            createInitialModernGroup();
            modernStructureTime = MODERN_ENGAGEMENT_MIN_TIME;
            lastModernReconstructionReason = "support_plan_" + reason;
        } else plan.supporter.tacticalDestination = null;
        lastSupportPlan = plan;
        supportPlan = null;
    }

    private boolean allFourAlive() {
        for (var agent : agents)
            if (!agent.alive) return false;
        return agents.length == 4;
    }

    private int indexOfAgent(Musketeer agent) {
        for (var n = 0; n < agents.length; ++n)
            if (agents[n] == agent) return n;
        return -1;
    }

    private FreedomAssessment assessFreedom(Musketeer agent, Engagement duel, Musketeer teammate,
                                             Engagement teammateDuel) {
        var opponent = duelOpponent(duel, agent);
        var supportTarget = duelOpponent(teammateDuel, teammate);
        if (opponent == null || supportTarget == null || !supportTarget.alive)
            return FreedomAssessment.engaged("invalid_duel");
        if (!opponent.alive)
            return new FreedomAssessment(true, 1, 0, Double.POSITIVE_INFINITY,
                    "opponent_eliminated");
        var ownDistance = agent.pos.minus(opponent.pos).hypotenuse();
        var supportDistance = agent.pos.minus(supportTarget.pos).hypotenuse();
        var timeToSupport = supportDistance / estimatedMovementSpeed(agent);
        var timeToContest = opponent.pos.minus(supportTarget.pos).hypotenuse() /
                estimatedMovementSpeed(opponent);
        var opponentLocked = opponent.state == CombatState.BLOCK_STUN ||
                opponent.state == CombatState.RECOVERY && opponent.stateTime < opponent.recoveryTime() * 0.72;
        var separated = ownDistance >= 3.05 ||
                ownDistance >= 2.20 && opponentLocked ||
                ownDistance >= MODERN_DUEL_EXTREME_SEPARATION;
        var advantage = timeToContest - timeToSupport;
        var confidence = Math.clamp((ownDistance - 2.20) * 0.28 + advantage * 0.34 +
                (opponentLocked ? 0.24 : 0) +
                agent.stats.decisionMaking() * 0.0015 + agent.stats.teamworkAbility() * 0.0015, 0, 1);
        var free = separated && supportDistance <= 3.35 &&
                timeToSupport + 0.30 < timeToContest && confidence >= 0.52;
        return new FreedomAssessment(free, confidence, timeToSupport, timeToContest,
                free ? opponentLocked ? "opponent_action_locked" : "local_support_advantage" :
                        "duel_still_contested");
    }

    private void createModernTwoVsOne(Musketeer fixer, Musketeer flanker, Musketeer defender,
                                      Musketeer interceptor, String reason) {
        createModernTwoVsOne(fixer, flanker, defender, interceptor, reason, null);
    }

    private void createModernTwoVsOne(Musketeer fixer, Musketeer flanker, Musketeer defender,
                                      Musketeer interceptor, String reason,
                                      EngagementSlot selectedSlot) {
        engagementManager.breakActive();
        modernGroupEngagement = null;
        clearModernTacticalState();
        var side = selectedSlot == null ?
                chooseModernFlankSide(flanker, fixer, defender, interceptor) :
                assessModernFlankSide(selectedSlot, flanker, fixer, defender, interceptor);
        var engagement = engagementManager.createTwoVsOne(fixer, flanker, defender, side.slot());
        engagement.activate();
        fixer.localRole = flanker.localRole = defender.localRole = LocalRole.FREE;
        flanker.localSide = side.slot() == EngagementSlot.LEFT ? -1 : 1;
        fixer.localSide = -flanker.localSide;
        changeTarget(fixer, defender, DUEL_LOCK_TIME);
        changeTarget(flanker, defender, DUEL_LOCK_TIME);
        changeTarget(defender, fixer, DUEL_LOCK_TIME);
        defender.multiThreatAttention.focusedThreat = fixer;
        defender.multiThreatAttention.secondaryThreat = null;
        defender.multiThreatAttention.focusLockRemaining = 0.45;
        defender.multiThreatAttention.switchDelay = 0.22;
        initializeFlank(flanker);
        if (interceptor != null) {
            interceptor.localRole = LocalRole.FREE;
            engagement.assignInterceptor(interceptor);
            changeTarget(interceptor, flanker, DUEL_LOCK_TIME);
        }
        updateTacticalDestinations(0);
        modernStructureTime = MODERN_ENGAGEMENT_MIN_TIME;
        lastModernReconstructionReason = reason;
        logTactical("TWO_VS_ONE_CREATED", "fixer=" + fixer.name(UiLanguage.FRENCH) +
                " flanker=" + flanker.name(UiLanguage.FRENCH) +
                " target=" + defender.name(UiLanguage.FRENCH) +
                (interceptor == null ? "" : " interceptor=" +
                        interceptor.name(UiLanguage.FRENCH)) +
                " slot=" + side.slot() + " reason=" + reason +
                " score=" + "%.2f".formatted(side.score()));
    }

    private FlankSideAssessment chooseModernFlankSide(Musketeer flanker, Musketeer fixer,
                                                       Musketeer defender, Musketeer interceptor) {
        var left = assessModernFlankSide(EngagementSlot.LEFT, flanker, fixer, defender, interceptor);
        var right = assessModernFlankSide(EngagementSlot.RIGHT, flanker, fixer, defender, interceptor);
        return left.score() <= right.score() ? left : right;
    }

    private FlankSideAssessment assessModernFlankSide(EngagementSlot slot, Musketeer flanker,
                                                       Musketeer fixer, Musketeer defender,
                                                       Musketeer interceptor) {
        var destination = slot.destinationAround(defender, 1.34);
        var travelDistance = flanker.pos.minus(destination).hypotenuse();
        var travelTime = travelDistance / estimatedMovementSpeed(flanker);
        var freeSpace = arenaClearance(destination);
        var interceptionRisk = interceptor == null ? 0 :
                Math.clamp(1 - interceptor.pos.minus(destination).hypotenuse() / 3.2, 0, 1);
        var fixerBlock = Math.clamp(1 - distanceToSegment(fixer.pos, flanker.pos, destination) /
                1.10, 0, 1);
        var score = travelTime + interceptionRisk * 0.85 + fixerBlock * 0.72 -
                Math.clamp(freeSpace, -0.5, 1.5) * 0.32;
        return new FlankSideAssessment(slot, travelTime, freeSpace, interceptionRisk, score);
    }

    private Engagement activeDuelOf(Musketeer agent) {
        for (var engagement : engagementManager.engagements())
            if (engagement.type == EngagementType.DUEL &&
                    engagement.status != EngagementStatus.BROKEN &&
                    engagement.status != EngagementStatus.FINISHED &&
                    (engagement.duelistOne == agent || engagement.duelistTwo == agent))
                return engagement;
        return null;
    }

    private Musketeer duelOpponent(Engagement duel, Musketeer agent) {
        if (duel == null) return null;
        if (duel.duelistOne == agent) return duel.duelistTwo;
        return duel.duelistTwo == agent ? duel.duelistOne : null;
    }

    private void clearModernTacticalState() {
        for (var agent : agents) {
            if (!agent.alive) continue;
            agent.engagement = null;
            agent.engagementSlot = null;
            agent.plannedTarget = null;
            agent.tacticalDestination = null;
            agent.rawInterceptionPoint = null;
            agent.committedInterceptionPoint = null;
            agent.interceptionPhase = null;
            agent.tacticalThreat = null;
            agent.localRole = LocalRole.FREE;
            agent.primaryMark = null;
            agent.secondaryThreat = null;
            agent.coveredBy = null;
            agent.covering = null;
            agent.groupIntent = GroupIntent.MARK;
            agent.coverAssessment = null;
            agent.coverPositionAssessment = null;
            agent.preferredGroupPosition = null;
            agent.commitAssessment = null;
            agent.revengeOpportunity = null;
            agent.counterCoverAssessment = null;
            agent.threatAssessment = new ThreatAssessment(0, false, false,
                    Double.POSITIVE_INFINITY, ThreatResponse.IGNORE);
            agent.threatResponse = agent.pendingThreatResponse = ThreatResponse.IGNORE;
            agent.threatResponseDelay = 0;
            agent.multiThreatAttention.focusedThreat = null;
            agent.multiThreatAttention.secondaryThreat = null;
            agent.multiThreatAttention.focusLockRemaining = 0;
            agent.multiThreatAttention.switchDelay = 0;
            agent.multiThreatAttention.uncertainty = 1;
        }
    }

    private void createControlledTwoVsOne(Musketeer fixer, Musketeer flanker, Musketeer defender) {
        changeTarget(fixer, defender, Double.POSITIVE_INFINITY);
        changeTarget(flanker, defender, Double.POSITIVE_INFINITY);
        changeTarget(defender, fixer, DUEL_LOCK_TIME);
        defender.multiThreatAttention.focusedThreat = fixer;
        defender.multiThreatAttention.secondaryThreat = null;
        setLocalRole(fixer, LocalRole.FIXER, defender, Double.POSITIVE_INFINITY);
        setLocalRole(flanker, LocalRole.FLANKER, defender, Double.POSITIVE_INFINITY);
        fixer.localSide = -1;
        flanker.localSide = 1;
        engagementManager.createTwoVsOne(fixer, flanker, defender);
        initializeFlank(flanker);
        updateTacticalDestinations(0);
    }

    private List<Musketeer> livingAgents(Musketeer[] team) {
        var living = new ArrayList<Musketeer>();
        for (var agent : team)
            if (agent.alive) living.add(agent);
        return living;
    }

    private int aliveMask() {
        var mask = 0;
        for (var n = 0; n < agents.length; ++n)
            if (agents[n].alive) mask |= 1 << n;
        return mask;
    }

    private RosterSnapshot captureRoster() {
        var blueMask = 0;
        var redMask = 0;
        var blueIndex = 0;
        var redIndex = 0;
        for (var agent : agents)
            if (agent.team == Team.BLUE) {
                if (agent.alive) blueMask |= 1 << blueIndex;
                ++blueIndex;
            } else {
                if (agent.alive) redMask |= 1 << redIndex;
                ++redIndex;
            }
        return new RosterSnapshot(blueMask, redMask, Integer.bitCount(blueMask),
                Integer.bitCount(redMask));
    }

    private Musketeer closestAliveOpponent(Musketeer self) {
        if (!self.alive && self.deadAgentAudit.active())
            ++self.deadAgentAudit.targetSelectionParticipations;
        Musketeer closest = null;
        var closestDistance = Double.POSITIVE_INFINITY;
        for (var opponent : opponentsOf(self)) {
            if (!opponent.alive) continue;
            var distance = opponent.pos.minus(self.pos).hypotenuse();
            if (distance >= closestDistance) continue;
            closest = opponent;
            closestDistance = distance;
        }
        return closest;
    }

    private Musketeer[] opponentsOf(Musketeer agent) {
        return agent.team == Team.BLUE ? redTeam : blueTeam;
    }

    private Musketeer teammateOf(Musketeer agent) {
        var team = agent.team == Team.BLUE ? blueTeam : redTeam;
        for (var teammate : team)
            if (teammate != agent && teammate.alive) return teammate;
        return null;
    }

    private void updateState(Musketeer self) {
        switch (self.state) {
            case ATTACK_PREPARE -> {
                if (self.stateTime >= self.prepareTime()) {
                    if (self.attackIntent == AttackIntent.DIRECT_RELEASE)
                        startAttack(self, CombatState.ATTACK_ACTIVE);
                    else setState(self, CombatState.ATTACK_HOLD);
                }
            }
            case ATTACK_HOLD -> { }
            case FEINT -> {
                if (self.stateTime < self.feintDuration) break;
                var enemy = self.target;
                if (enemy == null || !enemy.alive) {
                    setState(self, CombatState.NEUTRAL);
                    break;
                }
                if (self.feintCancel) setState(self, CombatState.NEUTRAL);
                else if (self.feintToBlock) beginBlock(self, enemy);
                else {
                    self.attackDirection = self.queuedAttackDirection;
                    configureAim(self, Math.hypot(enemy.getX() - self.getX(), enemy.getY() - self.getY()));
                    self.attackPitch = self.pitch;
                    self.baseAttackPitch = self.pitch;
                    self.attackIntent = AttackIntent.HOLD;
                    self.decision = Decision.PREPARE_HOLD_ATTACK;
                    setState(self, CombatState.ATTACK_PREPARE);
                }
            }
            case BLOCK_PREPARE -> {
                if (self.stateTime >= self.guardPrepareTime) {
                    self.guardSwitching = false;
                    setState(self, CombatState.BLOCKING);
                }
            }
            case CHAMBER_PREPARE -> {
                if (self.stateTime >= CHAMBER_ATTEMPT_LIMIT) setState(self, CombatState.NEUTRAL);
            }
            case CHAMBER_HOLD -> {
                if (self.stateTime >= SIMPLE_CHAMBER_HOLD) startAttack(self, CombatState.CHAMBER_ACTIVE);
            }
            case ATTACK_ACTIVE -> {
                if (ordinaryDirectTargetEscaped(self) || self.stateTime >= self.activeTime()) {
                    setState(self, CombatState.RECOVERY);
                    if (!self.attackResolved) onAttackMissed(self);
                }
            }
            case CHAMBER_ACTIVE -> {
                if (self.stateTime >= self.activeTime()) setState(self, CombatState.RECOVERY);
            }
            case BLOCK_STUN -> {
                if (self.stateTime >= BLOCK_STUN_TIME) setState(self, CombatState.RECOVERY);
            }
            case RECOVERY -> {
                if (self.stateTime >= self.recoveryTime()) setState(self, CombatState.NEUTRAL);
            }
            case BLOCKING -> {
                if (self.stateTime >= Math.max(BLOCK_MIN_TIME, self.guardHoldTime) &&
                        self.dezoneMessage.getTime() <= 0 && self.pressureGuardTime <= 0)
                    setState(self, CombatState.NEUTRAL);
            }
            case DOUBLE_BLOCK -> {
                if (!self.doubleBlockSwitched && self.stateTime >= self.doubleBlockSwitchTime) {
                    self.blockDirection = self.doubleBlockSecondDirection;
                    self.doubleBlockSwitched = true;
                }
                if (self.stateTime >= Math.max(DOUBLE_BLOCK_MIN_DURATION, self.doubleBlockSwitchTime + 0.12))
                    setState(self, CombatState.NEUTRAL);
            }
            default -> { }
        }
    }

    private void updateEngagementPlan(Musketeer self, Musketeer enemy, double dt) {
        if (combatPhase == CombatPhase.FRONT_LINE) return;
        if (self.pressureRole != PressureRole.NONE || self.pressureMode || self.pressureCounter) return;
        self.engagementTime -= dt;
        var distance = Math.hypot(enemy.getX() - self.getX(), enemy.getY() - self.getY());
        switch (self.engagementPhase) {
            case PROBE -> {
                if (self.engagementTime > 0 || distance < 0.95) return;
                var commitment = Math.clamp(0.18 + self.stats.engagementAbility() * 0.005 +
                                (self.tactic == Tactic.AGGRESSIVE ? 0.18 : self.tactic == Tactic.DEFENSIVE ? -0.12 : 0),
                        0.12, 0.86);
                if (random.nextDouble() < commitment) beginEngagementPhase(self, EngagementPhase.COMMIT);
                else self.engagementTime = 0.35 + random.nextDouble() * 0.55;
            }
            case COMMIT -> {
                if (self.state == CombatState.RECOVERY || self.state == CombatState.BLOCK_STUN)
                    beginEngagementPhase(self, EngagementPhase.DISENGAGE);
                else if (self.isAttacking() && self.stateTime >= self.activeTime() * 0.30 || distance < 0.78)
                    beginEngagementPhase(self, EngagementPhase.PASS_THROUGH);
                else if (self.engagementTime <= 0 && self.state == CombatState.NEUTRAL)
                    beginEngagementPhase(self, EngagementPhase.PROBE);
            }
            case PASS_THROUGH -> {
                if (self.engagementTime <= 0 || self.state == CombatState.RECOVERY ||
                        self.state == CombatState.BLOCK_STUN)
                    beginEngagementPhase(self, EngagementPhase.DISENGAGE);
            }
            case DISENGAGE -> {
                if (self.engagementTime <= 0 || distance >= 2.20)
                    beginEngagementPhase(self, EngagementPhase.RESET);
            }
            case RESET -> {
                if (self.engagementTime <= 0) beginEngagementPhase(self, EngagementPhase.PROBE);
            }
        }
    }

    private void beginEngagementPhase(Musketeer self, EngagementPhase phase) {
        self.engagementPhase = phase;
        if (phase == EngagementPhase.COMMIT || phase == EngagementPhase.PASS_THROUGH) {
            var leftScore = movementSpace(self, -1);
            var rightScore = movementSpace(self, 1);
            var correctSide = leftScore >= rightScore ? -1 : 1;
            var preferredSide = operationSucceeds(self.stats.engagementAbility()) ? correctSide : -correctSide;
            self.engagementSide = collectiveMovementSide(self, preferredSide);
        }
        self.engagementTime = switch (phase) {
            case PROBE -> 0.45 + random.nextDouble() * 0.75;
            case COMMIT -> 0.65 + random.nextDouble() * 0.55;
            case PASS_THROUGH -> 0.26 + random.nextDouble() * 0.25;
            case DISENGAGE -> 0.42 + random.nextDouble() * 0.38;
            case RESET -> 0.30 + random.nextDouble() * 0.48;
        };
    }

    private double movementSpace(Musketeer self, int side) {
        var sidewaysAngle = self.angle + side * Math.PI / 2;
        var candidateX = Math.clamp(self.getX() + Math.cos(sidewaysAngle) * 1.15,
                Musketeer.BODY_RADIUS, ARENA_WIDTH - Musketeer.BODY_RADIUS);
        var candidateY = Math.clamp(self.getY() + Math.sin(sidewaysAngle) * 1.15,
                Musketeer.BODY_RADIUS, ARENA_HEIGHT - Musketeer.BODY_RADIUS);
        return Math.min(Math.min(candidateX, ARENA_WIDTH - candidateX),
                Math.min(candidateY, ARENA_HEIGHT - candidateY));
    }

    private int collectiveMovementSide(Musketeer self, int preferredSide) {
        var teammate = teammateOf(self);
        if (teammate == null || !teammate.alive || random.nextInt(100) >= self.stats.teamworkAbility())
            return preferredSide;
        var orbit = teammate.movementIntent.orbitWeight;
        var side = 0;
        if (Math.abs(orbit) >= 0.20) side = (int) Math.signum(orbit);
        else if (teammate.engagementPhase == EngagementPhase.COMMIT ||
                teammate.engagementPhase == EngagementPhase.PASS_THROUGH) side = teammate.engagementSide;
        if (side == 0) return preferredSide;
        if (Math.abs(angleDifference(self.angle, teammate.angle)) > Math.PI / 2) side = -side;
        return movementSpace(self, side) >= 0.55 ? side : preferredSide;
    }

    private boolean applyForcedEngagementDecision(Musketeer self) {
        switch (self.engagementPhase) {
            case PASS_THROUGH -> self.decision = self.engagementSide < 0 ? Decision.PASS_LEFT : Decision.PASS_RIGHT;
            case DISENGAGE -> self.decision = Decision.DISENGAGE;
            case RESET -> self.decision = Decision.RESET_GUARD;
            default -> { return false; }
        }
        return true;
    }

    private boolean applyPlannedEngagement(Musketeer self, Musketeer enemy, double distance) {
        if (self.engagementPhase == EngagementPhase.COMMIT) {
            if (distance <= 2.05 && distance >= 1.10) beginAttack(self, enemy, distance);
            else self.decision = self.engagementSide < 0 ? Decision.COMMIT_LEFT : Decision.COMMIT_RIGHT;
            return true;
        }
        if (self.engagementPhase != EngagementPhase.PROBE || distance <= 1.25) return false;
        var guardChance = (self.stats.defenseTiming() + self.stats.decisionMaking()) / 2200.0;
        if (random.nextDouble() < guardChance) beginAnticipatoryBlock(self, enemy);
        else self.decision = self.engagementSide < 0 ? Decision.PROBE_LEFT : Decision.PROBE_RIGHT;
        return true;
    }

    private boolean applyFrontLineDecision(Musketeer self) {
        if (combatPhase != CombatPhase.FRONT_LINE || !self.alive || self.openingOpponent == null ||
                !self.openingOpponent.alive || self.openingOpponent.isAttacking()) return false;
        var enemy = self.openingOpponent;
        if (self.target != enemy && self.state == CombatState.NEUTRAL) changeTarget(self, enemy, 0.48);
        var distance = enemy.pos.minus(self.pos).hypotenuse();
        if (self.openingChoice == OpeningChoice.ROTATE) {
            self.decision = Decision.OPENING_ROTATE;
            return self.state == CombatState.NEUTRAL || self.state == CombatState.ATTACK_PREPARE ||
                    self.state == CombatState.ATTACK_HOLD || self.isGuarding();
        }
        if (self.openingChoice == OpeningChoice.STAB) {
            self.decision = Decision.OPENING_STAB;
            return self.state != CombatState.NEUTRAL;
        }
        if (self.state == CombatState.NEUTRAL) {
            if (distance > FRONT_LINE_ARM_DISTANCE) self.decision = Decision.FRONT_LINE_ADVANCE;
            else {
                beginAttack(self, enemy, distance);
                self.attackIntent = AttackIntent.HOLD;
                self.decision = Decision.FRONT_LINE_HOLD;
            }
            return true;
        }
        if (self.state == CombatState.ATTACK_PREPARE) {
            self.attackIntent = AttackIntent.HOLD;
            self.decision = Decision.FRONT_LINE_HOLD;
            return true;
        }
        if (self.state != CombatState.ATTACK_HOLD) return false;
        if (distance > OPENING_CONTACT_DISTANCE) {
            self.decision = Decision.FRONT_LINE_HOLD;
            return true;
        }
        chooseOpening(self, enemy, distance);
        return true;
    }

    private void chooseOpening(Musketeer self, Musketeer enemy, double distance) {
        if (isFiftyFiftySituation(self, enemy, distance)) {
            if (acceptsFiftyFifty(self, enemy)) chooseOpeningStab(self);
            else chooseOpeningRotation(self);
            return;
        }
        var attackScore = self.stats.attackTiming() * 0.26 + self.stats.reaction() * 0.18 +
                self.stats.aim() * 0.16 + self.stats.distanceControl() * 0.16 +
                self.stats.aggression() * 0.12 + self.stats.decisionMaking() * 0.12;
        var rotationScore = self.stats.footwork() * 0.28 + self.stats.decisionMaking() * 0.24 +
                self.stats.zoneExit() * 0.20 + self.stats.defenseTiming() * 0.16 +
                self.stats.distanceControl() * 0.12;
        if (self.tactic == Tactic.AGGRESSIVE) attackScore += 12;
        else if (self.tactic == Tactic.DEFENSIVE) {
            attackScore -= 10;
            rotationScore += 12;
        }
        if (enemy.isGuarding()) {
            attackScore -= 28;
            rotationScore += 22;
        } else if (enemy.state == CombatState.ATTACK_HOLD) {
            attackScore += 4;
            rotationScore += 6;
        }
        var teammate = teammateOf(self);
        if (teammate != null && teammate.alive) {
            if (teammate.pos.minus(self.pos).hypotenuse() <= 1.65) rotationScore += 8;
            if (teammate.openingChoice == OpeningChoice.ROTATE) rotationScore += 24;
            else if (teammate.openingChoice == OpeningChoice.STAB) attackScore += 18;
        }
        var selfTime = openingActionTime(self, enemy, distance);
        var enemyDistance = self.pos.minus(enemy.pos).hypotenuse();
        var enemyTime = enemy.isGuarding() ? 0 : openingActionTime(enemy, self, enemyDistance);
        attackScore += Math.clamp((enemyTime - selfTime) * 65, -18, 18);
        var stabChance = 1 / (1 + Math.exp((rotationScore - attackScore) / 13.0));
        stabChance = Math.clamp(stabChance, 0.12, 0.88);
        if (random.nextDouble() < stabChance) chooseOpeningStab(self);
        else chooseOpeningRotation(self);
    }

    private void chooseOpeningStab(Musketeer self) {
        self.openingChoice = OpeningChoice.STAB;
        self.attackIntent = AttackIntent.DIRECT_RELEASE;
        self.decision = Decision.OPENING_STAB;
        startAttack(self, CombatState.ATTACK_ACTIVE);
    }

    private void chooseOpeningRotation(Musketeer self) {
        self.openingChoice = OpeningChoice.ROTATE;
        self.decision = Decision.OPENING_ROTATE;
    }

    private double openingActionTime(Musketeer self, Musketeer enemy, double distance) {
        var prepare = self.state == CombatState.ATTACK_HOLD ? 0 :
                self.state == CombatState.ATTACK_PREPARE ? Math.max(0, self.prepareTime() - self.stateTime) :
                        self.attackDirection.prepareTime;
        var turn = Math.abs(angleDifference(self.weaponWorldAngle(), enemy.pos.minus(self.pos).radians())) /
                (3.4 + self.stats.footwork() * 0.018);
        var deficit = Math.max(0, distance - self.projectedThrustLength() - Musketeer.BODY_RADIUS);
        var closing = Math.max(0.35, self.velocity.minus(enemy.velocity).dot(
                enemy.pos.minus(self.pos).div(Math.max(0.001, distance))));
        return prepare + turn + deficit / closing;
    }

    private boolean isFiftyFiftySituation(Musketeer self, Musketeer enemy, double distance) {
        if (!features.fiftyFifty()) return false;
        if (enemy == null || !enemy.alive || enemy.target != self || enemy.baitedAttack ||
                enemy.chamberType != ChamberType.NONE || self.baitedAttack || self.punishingBait ||
                self.exploitingBait || self.freeKillAttempt || self.pressureMode || self.pressureCounter ||
                self.dezoneSwitchCommitted || self.state != CombatState.ATTACK_HOLD) return false;
        var enemyArmed = enemy.state == CombatState.ATTACK_PREPARE || enemy.state == CombatState.ATTACK_HOLD ||
                enemy.isAttacking();
        if (!enemyArmed || enemy.isAttacking() && enemy.stateTime > enemy.activeTime() * 0.65) return false;
        if (combatPhase == CombatPhase.FRONT_LINE && self.openingOpponent == enemy &&
                distance <= OPENING_CONTACT_DISTANCE + 0.08) return true;
        if (!isVisible(self, enemy)) return false;
        var towardEnemy = enemy.pos.minus(self.pos).radians();
        var towardSelf = self.pos.minus(enemy.pos).radians();
        if (Math.abs(angleDifference(self.weaponWorldAngle(), towardEnemy)) > Math.toRadians(34) ||
                Math.abs(angleDifference(enemy.weaponWorldAngle(), towardSelf)) > Math.toRadians(34) ||
                distance > Math.min(self.projectedThrustLength(), enemy.projectedThrustLength()) +
                        Musketeer.BODY_RADIUS + 0.48) return false;
        return Math.abs(estimatedFiftyFiftyImpact(self, enemy) -
                estimatedFiftyFiftyImpact(enemy, self)) <= FIFTY_FIFTY_WINDOW;
    }

    private double estimatedFiftyFiftyImpact(Musketeer attacker, Musketeer target) {
        var actionTime = switch (attacker.state) {
            case ATTACK_HOLD -> attacker.activeTime() / 2;
            case ATTACK_PREPARE -> Math.max(0, attacker.prepareTime() - attacker.stateTime) +
                    attacker.activeTime() / 2;
            case ATTACK_ACTIVE -> Math.max(0, attacker.activeTime() / 2 - attacker.stateTime);
            default -> Double.POSITIVE_INFINITY;
        };
        var turnTime = Math.abs(angleDifference(attacker.weaponWorldAngle(),
                target.pos.minus(attacker.pos).radians())) / (3.4 + attacker.stats.footwork() * 0.018);
        return Math.max(actionTime, turnTime);
    }

    private boolean acceptsFiftyFifty(Musketeer self, Musketeer enemy) {
        var awareness = self.opponentAwareness.get(enemy);
        if (awareness == null) return false;
        if (awareness.evaluatedFiftyFiftySequence != enemy.attackSequence) {
            awareness.evaluatedFiftyFiftySequence = enemy.attackSequence;
            var willingness = (self.stats.fiftyFifty() - 1) / 99.0;
            awareness.acceptsFiftyFifty = random.nextDouble() < willingness * willingness;
        }
        return awareness.acceptsFiftyFifty;
    }

    private boolean applyRotationDecision(Musketeer self, Musketeer enemy, boolean enemyVisible,
                                          double distance) {
        if (combatPhase != CombatPhase.ROTATION || !enemyVisible) return false;
        if (distance < 1.25) {
            self.decision = defensiveMovement(self, distance);
            return true;
        }
        var teammate = teammateOf(self);
        var formationReady = teammate != null && teammate.alive &&
                teammate.pos.minus(self.pos).hypotenuse() <= 2.35;
        var enemyOccupied = enemy.target != self || enemy.isGuarding() || enemy.state == CombatState.RECOVERY ||
                enemy.state == CombatState.BLOCK_STUN;
        var flankAngle = Math.abs(angleDifference(enemy.angle, self.pos.minus(enemy.pos).radians()));
        var opening = enemyOccupied || flankAngle >= Math.toRadians(62);
        var attackChance = (self.stats.attackTiming() + self.stats.decisionMaking() +
                self.stats.aggression()) / 4800.0 * self.tactic.attackMultiplier;
        if (combatPhaseTime >= 0.45 && formationReady && opening && distance <= 1.92 &&
                random.nextDouble() < attackChance) {
            beginAttack(self, enemy, distance);
            return true;
        }
        self.decision = self.rotationSide < 0 ? Decision.ROTATION_LEFT : Decision.ROTATION_RIGHT;
        return true;
    }

    private boolean applyOutnumberingPressure(Musketeer self) {
        if (self.pressureRole == PressureRole.NONE || self.pressureFixedTarget == null ||
                !self.pressureFixedTarget.alive) return false;
        var target = self.pressureFixedTarget;
        var teammate = teammateOf(self);
        self.target = target;
        self.targetLockTime = 0.45;
        var distance = target.pos.minus(self.pos).hypotenuse();
        var teammateNear = teammate != null && teammate.alive &&
                target.pos.minus(teammate.pos).hypotenuse() <= 1.18;
        var bothNear = distance <= 1.12 && teammateNear;
        if (self.state == CombatState.BLOCK_PREPARE) {
            self.pressureGuardTime = Math.max(self.pressureGuardTime, 0.22);
            setPressureClosingDecision(self, distance);
            return true;
        }
        if (self.state == CombatState.BLOCKING) {
            if (self.pressureRole == PressureRole.TRIGGER && bothNear && self.stateTime >= BLOCK_MIN_TIME) {
                setState(self, CombatState.NEUTRAL);
                beginPressureAttack(self, target, distance);
                return true;
            }
            self.pressureGuardTime = Math.max(self.pressureGuardTime, 0.22);
            setPressureClosingDecision(self, distance);
            return true;
        }
        if (self.state != CombatState.NEUTRAL) return false;
        if (self.pressureRole == PressureRole.TRIGGER && bothNear) beginPressureAttack(self, target, distance);
        else {
            beginBlock(self, target);
            self.pressureGuardTime = 0.28;
            setPressureClosingDecision(self, distance);
        }
        return true;
    }

    private void setPressureClosingDecision(Musketeer self, double distance) {
        self.decision = distance > PRESSURE_NEAR_DISTANCE ?
                self.pressureSide < 0 ? Decision.PRESSURE_CLOSE_LEFT : Decision.PRESSURE_CLOSE_RIGHT :
                Decision.PRESSURE_BLOCK;
    }

    private boolean applyPressureReaction(Musketeer self) {
        var source = self.pressureSource;
        if (self.state != CombatState.NEUTRAL || source == null || !source.alive ||
                self.pressureReceived < pressureThreshold(self) || isRearAttack(self, source)) return false;
        self.target = source;
        self.targetLockTime = 0.42;
        endDezone(self);
        beginBlock(self, source);
        self.pressureGuardTime = 0.15 + (100 - self.stats.reaction()) * 0.0015;
        self.pressureEscapeDelay = 0.045 + (100 - self.stats.reaction()) * 0.0022;
        self.pressureEscapeTime = 0;
        self.pressureEscapeActive = false;
        self.decision = Decision.PRESSURE_BLOCK;
        return true;
    }

    private boolean maintainPressureGuard(Musketeer self) {
        if (self.pressureRole != PressureRole.NONE) return applyOutnumberingPressure(self);
        var source = self.pressureSource;
        if (source == null || !source.alive || self.pressureReceived < pressureThreshold(self) * 0.55) return false;
        self.target = source;
        self.targetLockTime = 0.35;
        self.pressureGuardTime = Math.max(self.pressureGuardTime,
                0.14 + (100 - self.stats.reaction()) * 0.0014);
        if (!self.blockDirection.blocks(source.attackDirection) && self.guardSwitchCooldown <= 0)
            switchGuard(self, source.attackDirection);
        if (self.pressureEscapeDelay > 0) self.decision = Decision.PRESSURE_BLOCK;
        else if (self.pressureEscapeTime <= 0 || !isPressureEscape(self.decision) ||
                pressureEscapeBlocked(self)) choosePressureEscape(self, source);
        return true;
    }

    private boolean isPressureEscape(Decision decision) {
        return decision == Decision.PRESSURE_ESCAPE_LEFT || decision == Decision.PRESSURE_ESCAPE_RIGHT ||
                decision == Decision.PRESSURE_JOIN_TEAM || decision == Decision.PRESSURE_RETREAT;
    }

    private boolean pressureEscapeBlocked(Musketeer self) {
        if (self.decision == Decision.PRESSURE_JOIN_TEAM) {
            var teammate = teammateOf(self);
            return teammate == null || !teammate.alive;
        }
        if (self.decision == Decision.PRESSURE_ESCAPE_LEFT) return movementSpace(self, -1) < 0.16;
        if (self.decision == Decision.PRESSURE_ESCAPE_RIGHT) return movementSpace(self, 1) < 0.16;
        if (self.decision != Decision.PRESSURE_RETREAT) return false;
        var candidate = self.pos.minus(new Pos(self.angle).times(0.85));
        return arenaClearance(candidate) < 0.16;
    }

    private void choosePressureEscape(Musketeer self, Musketeer source) {
        var left = pressureSideScore(self, source, -1);
        var right = pressureSideScore(self, source, 1);
        var retreat = pressureRetreatScore(self, source);
        var join = pressureJoinScore(self, source);
        self.decision = left >= right ? Decision.PRESSURE_ESCAPE_LEFT : Decision.PRESSURE_ESCAPE_RIGHT;
        var best = Math.max(left, right);
        if (retreat > best) {
            best = retreat;
            self.decision = Decision.PRESSURE_RETREAT;
        }
        if (join > best) self.decision = Decision.PRESSURE_JOIN_TEAM;
        self.pressureEscapeTime = 0.32 + self.stats.decisionMaking() * 0.0025 +
                self.stats.footwork() * 0.002 + random.nextDouble() * 0.08;
        self.pressureEscapeActive = true;
        endDezone(self);
    }

    private double pressureSideScore(Musketeer self, Musketeer source, int side) {
        var candidate = self.pos.plus(new Pos(self.angle + side * Math.PI / 2).times(0.90));
        var distanceGain = candidate.minus(source.pos).hypotenuse() - self.pos.minus(source.pos).hypotenuse();
        var teammate = teammateOf(self);
        var teamGain = teammate == null || !teammate.alive ? 0 :
                self.pos.minus(teammate.pos).hypotenuse() - candidate.minus(teammate.pos).hypotenuse();
        return 1.10 + arenaClearance(candidate) * 0.72 + distanceGain * 1.25 + teamGain * 0.38 -
                pressureFlankRisk(self, source, candidate) * 2.25 + self.stats.zoneExit() * 0.004 +
                self.stats.footwork() * 0.002 + pressureDecisionNoise(self);
    }

    private double pressureRetreatScore(Musketeer self, Musketeer source) {
        var candidate = self.pos.minus(new Pos(self.angle).times(0.85));
        var distanceGain = candidate.minus(source.pos).hypotenuse() - self.pos.minus(source.pos).hypotenuse();
        return 0.82 + self.pressureReceived * 0.75 + arenaClearance(candidate) * 0.68 +
                distanceGain * 1.45 - pressureFlankRisk(self, source, candidate) * 2.85 +
                self.stats.zoneExit() * 0.0035 + pressureDecisionNoise(self);
    }

    private double pressureJoinScore(Musketeer self, Musketeer source) {
        var teammate = teammateOf(self);
        if (teammate == null || !teammate.alive) return Double.NEGATIVE_INFINITY;
        var goal = pressureJoinPoint(self, teammate, source);
        var towardGoal = goal.minus(self.pos);
        var distance = towardGoal.hypotenuse();
        if (distance < 0.15) return Double.NEGATIVE_INFINITY;
        var candidate = self.pos.plus(towardGoal.times(Math.min(0.90, distance) / distance));
        var sourceGain = candidate.minus(source.pos).hypotenuse() - self.pos.minus(source.pos).hypotenuse();
        var sourceOffset = candidate.minus(source.pos);
        var sourceReach = source.projectedThrustLength() + Musketeer.BODY_RADIUS + 0.35;
        var sourcePathRisk = Math.clamp(1 - sourceOffset.hypotenuse() / sourceReach, 0, 1) *
                Math.clamp(1 - Math.abs(angleDifference(source.weaponWorldAngle(), sourceOffset.radians())) /
                        Math.toRadians(55), 0, 1);
        return 1.55 + Math.clamp(2.8 - distance, -0.8, 1.4) + sourceGain * 0.65 +
                arenaClearance(candidate) * 0.52 - sourcePathRisk * 2.55 -
                pressureFlankRisk(self, source, candidate) * 2.45 +
                self.stats.decisionMaking() * 0.0045 + pressureDecisionNoise(self);
    }

    private Pos pressureJoinPoint(Musketeer self, Musketeer teammate, Musketeer source) {
        var away = teammate.pos.minus(source.pos);
        var distance = away.hypotenuse();
        if (distance < 0.0001) away = teammate.pos.minus(self.pos);
        distance = away.hypotenuse();
        if (distance < 0.0001) away = new Pos(self.angle + Math.PI);
        else away = away.div(distance);
        return teammate.pos.plus(away.times(0.82));
    }

    private double pressureFlankRisk(Musketeer self, Musketeer source, Pos candidate) {
        var facing = source.pos.minus(candidate).radians();
        var risk = 0.0;
        for (var opponent : opponentsOf(self)) {
            if (opponent == source || !opponent.alive || !isTracked(self, opponent)) continue;
            var offset = known(self, opponent).minus(candidate);
            var distance = offset.hypotenuse();
            var reach = opponent.projectedThrustLength() + Musketeer.BODY_RADIUS + 0.55;
            var proximity = Math.clamp(1 - Math.max(0, distance - reach) / 1.35, 0, 1);
            var relative = Math.abs(angleDifference(facing, offset.radians()));
            var flank = Math.clamp(1 - Math.abs(relative - Math.PI / 2) / Math.toRadians(72), 0.12, 1);
            var weapon = isVisible(self, opponent) ? Math.clamp(1 - Math.abs(angleDifference(
                    opponent.weaponWorldAngle(), candidate.minus(opponent.pos).radians())) / Math.toRadians(90),
                    0.10, 1) : 0.32;
            var posture = opponent.state == CombatState.ATTACK_HOLD || opponent.isAttacking() ? 1.30 :
                    opponent.state == CombatState.ATTACK_PREPARE ? 1.05 : 0.62;
            risk += proximity * flank * weapon * posture;
        }
        return risk;
    }

    private double pressureDecisionNoise(Musketeer self) {
        return random.nextGaussian() * (101 - self.stats.decisionMaking()) * 0.012;
    }

    private double arenaClearance(Pos position) {
        return Math.min(Math.min(position.x() - Musketeer.BODY_RADIUS,
                        ARENA_WIDTH - Musketeer.BODY_RADIUS - position.x()),
                Math.min(position.y() - Musketeer.BODY_RADIUS,
                        ARENA_HEIGHT - Musketeer.BODY_RADIUS - position.y()));
    }

    private boolean maintainPressureAttack(Musketeer self) {
        if (self.forcedPressureSource != null && self.forcedPressureSource.alive) {
            self.target = self.forcedPressureSource;
            self.targetLockTime = 0.35;
            self.decision = Decision.PRESSURE_SPAM;
            if (self.pressureReleaseDelay <= 0) startAttack(self, CombatState.ATTACK_ACTIVE);
            return true;
        }
        var teammate = teammateOf(self);
        if (self.pressureContext == PressureContext.OUTNUMBERING &&
                (self.pressureRole == PressureRole.NONE || teammate == null || !teammate.alive)) {
            abandonPressurePlan(self);
            return false;
        }
        if (self.pressureCounter) {
            self.decision = self.attackDirection == CombatDirection.UP ?
                    Decision.PRESSURE_COUNTER_HIGH : Decision.PRESSURE_COUNTER_LOW;
            var teammateCommitted = teammate != null && teammate.alive && teammate.pressureCounter &&
                    (teammate.isAttacking() || teammate.state == CombatState.ATTACK_HOLD);
            if (teammateCommitted && self.attackDirection == CombatDirection.UP && self.stateTime >= 0.08) {
                startAttack(self, CombatState.ATTACK_ACTIVE);
                if (teammate.state == CombatState.ATTACK_HOLD) startAttack(teammate, CombatState.ATTACK_ACTIVE);
            }
            if (self.stateTime >= pressurePlanLimit(self)) {
                abandonPressurePlan(self);
                return false;
            }
            return true;
        }
        if (!self.pressureMode) return false;
        if (self.pressureContext == PressureContext.NONE || self.pressureFixedTarget == null ||
                !self.pressureFixedTarget.alive) {
            abandonPressurePlan(self);
            return false;
        }
        var fixedTarget = self.pressureFixedTarget;
        if (self.pressureRole == PressureRole.TRIGGER && fixedTarget.state == CombatState.ATTACK_HOLD &&
                self.stateTime >= 0.14 && self.pressureFeintCooldown <= 0) {
            fixedTarget.forcedPressureSource = self;
            fixedTarget.pressureReleaseDelay = Math.max(0.05,
                    0.22 - fixedTarget.stats.reaction() * 0.0015 + random.nextDouble() * 0.03);
            self.pressureFeintCooldown = 0.55;
            beginFeint(self, true);
            self.decision = Decision.PRESSURE_FEINT;
            return true;
        }
        if (self.pressureRole == PressureRole.TRIGGER && fixedTarget.isGuarding() && self.stateTime >= 0.24 &&
                teammate != null && teammate.alive && fixedTarget.pos.minus(teammate.pos).hypotenuse() <= 1.18) {
            beginCoordinatedPressureCounter(self, fixedTarget);
            return true;
        }
        if (self.pressureTransferTarget == null && fixedTarget.isGuarding()) {
            var transfer = pressureTransferTarget(self, fixedTarget);
            if (transfer != null) {
                self.pressureTransferTarget = transfer;
                self.target = transfer;
                var turn = Math.abs(angleDifference(self.weaponWorldAngle(), transfer.pos.minus(self.pos).radians()));
                self.pressureTransferDelay = Math.max(0.055, 0.22 - self.stats.reaction() * 0.00145) +
                        turn / (3.4 + self.stats.footwork() * 0.018) * 0.28;
            }
        }
        if (self.pressureTransferTarget != null && self.pressureTransferTarget.alive) {
            self.target = self.pressureTransferTarget;
            self.decision = Decision.PRESSURE_TRANSFER;
            if (self.pressureTransferDelay <= 0) startAttack(self, CombatState.ATTACK_ACTIVE);
            return true;
        }
        if (self.stateTime >= pressurePlanLimit(self)) {
            abandonPressurePlan(self);
            return false;
        }
        self.target = fixedTarget;
        self.decision = Decision.PRESSURE_HOLD;
        return true;
    }

    private double pressurePlanLimit(Musketeer self) {
        return self.pressureContext == PressureContext.INDIVIDUAL ? 0.65 :
                1.20 + self.stats.decisionMaking() * 0.004;
    }

    private Musketeer pressureTransferTarget(Musketeer self, Musketeer fixedTarget) {
        Musketeer best = null;
        var bestDistance = Double.POSITIVE_INFINITY;
        for (var opponent : opponentsOf(self)) {
            if (opponent == fixedTarget || !opponent.alive || !isVisible(self, opponent)) continue;
            var offset = opponent.pos.minus(self.pos);
            var distance = offset.hypotenuse();
            if (distance > self.projectedThrustLength() + Musketeer.BODY_RADIUS + 0.38 ||
                    Math.abs(angleDifference(self.weaponWorldAngle(), offset.radians())) > Math.toRadians(100))
                continue;
            if (distance < bestDistance) {
                best = opponent;
                bestDistance = distance;
            }
        }
        return best;
    }

    private boolean tryBeginPressureAttack(Musketeer self, Musketeer enemy, double distance) {
        if (self.pressureRole != PressureRole.NONE || countAlive(opponentsOf(self)) < 2 || !isVisible(self, enemy) ||
                distance < 0.78 || distance > 1.82 || enemy.isAttacking() ||
                random.nextDouble() >= 0.018 + self.stats.pressureAbility() * 0.00045) return false;
        beginPressureAttack(self, enemy, distance);
        return true;
    }

    private boolean tryReestablishPressureHold(Musketeer self, Musketeer enemy, double distance) {
        if (self.state != CombatState.NEUTRAL || self.pressureReengageTime <= 0 || !isVisible(self, enemy) ||
                enemy.isAttacking()) return false;
        if (distance > 2.05) {
            self.decision = Decision.APPROACH;
            return true;
        }
        if (distance < 0.76) {
            self.decision = Decision.PRESSURE_RETREAT;
            return true;
        }
        var ability = (self.stats.attackTiming() + self.stats.decisionMaking() + self.stats.distanceControl() +
                self.stats.aggression()) / 4.0;
        if (random.nextDouble() < 0.06 + ability * 0.0052) {
            beginPressureAttack(self, enemy, distance);
            return true;
        }
        self.decision = movementSpace(self, -1) >= movementSpace(self, 1) ?
                Decision.PRESSURE_ESCAPE_LEFT : Decision.PRESSURE_ESCAPE_RIGHT;
        return true;
    }

    private void beginPressureAttack(Musketeer self, Musketeer target, double distance) {
        self.target = target;
        self.targetLockTime = 0.62;
        endDezone(self);
        beginEngagementPhase(self, EngagementPhase.PROBE);
        beginAttack(self, target, distance);
        self.attackIntent = AttackIntent.HOLD;
        self.decision = Decision.PRESSURE_HOLD;
        self.pressureMode = true;
        self.pressureContext = self.pressureRole == PressureRole.NONE ?
                PressureContext.INDIVIDUAL : PressureContext.OUTNUMBERING;
        self.pressureFixedTarget = target;
        self.pressureTransferTarget = null;
        self.pressureTransferDelay = 0;
        self.pressureReengageTime = 0;
    }

    private void beginCoordinatedPressureCounter(Musketeer initiator, Musketeer target) {
        var teammate = teammateOf(initiator);
        if (teammate == null || !teammate.alive || initiator.pressureCounter || teammate.pressureCounter) return;
        var high = initiator.pressureRole == PressureRole.ANCHOR ? initiator : teammate;
        var low = high == initiator ? teammate : initiator;
        beginPressureCounterAttack(high, target, CombatDirection.UP, Decision.PRESSURE_COUNTER_HIGH);
        beginPressureCounterAttack(low, target, CombatDirection.DOWN, Decision.PRESSURE_COUNTER_LOW);
    }

    private void beginPressureCounterAttack(Musketeer self, Musketeer target, CombatDirection direction,
                                            Decision decision) {
        setState(self, CombatState.NEUTRAL);
        self.target = target;
        self.targetLockTime = 0.65;
        stopBait(self);
        endDezone(self);
        beginEngagementPhase(self, EngagementPhase.PROBE);
        self.attackDirection = direction;
        configureAim(self, target.pos.minus(self.pos).hypotenuse());
        self.attackPitch = self.pitch;
        self.baseAttackPitch = self.pitch;
        self.attackIntent = AttackIntent.HOLD;
        setState(self, CombatState.ATTACK_PREPARE);
        self.pressureCounter = true;
        self.pressureContext = PressureContext.OUTNUMBERING;
        self.pressureFixedTarget = target;
        self.decision = decision;
    }

    private void chooseDecision(Musketeer self, Musketeer enemy) {
        var enemyVisible = isVisible(self, enemy);
        Pos d = known(self, enemy).minus(self.pos);
        var distance = d.hypotenuse();
        var perceivedDistance = distance + random.nextGaussian() * (100 - self.stats.distanceControl()) * 0.006;
        var enemyAttack = enemyVisible && (enemy.state == CombatState.ATTACK_PREPARE ||
                enemy.state == CombatState.ATTACK_HOLD || enemy.state == CombatState.CHAMBER_HOLD ||
                enemy.isAttacking());
        var enemyThreat = enemyAttack && !enemy.baitedAttack;
        var angleToSelf = Math.atan2(-d.y(), -d.x());
        var enemyFacing = Math.abs(angleDifference(enemy.weaponWorldAngle(), angleToSelf)) < Math.toRadians(34);
        var immediateDanger = enemyThreat && enemyFacing &&
                perceivedDistance < enemy.projectedThrustLength() + Musketeer.BODY_RADIUS + 0.25;
        var visibleBait = enemyVisible && enemy.baitMessage.getTime() > 0 &&
                Math.abs(angleDifference(enemy.weaponWorldAngle(), angleToSelf)) > Math.toRadians(18);
        var readableEnemyAttack = enemyVisible && enemy.chamberType == ChamberType.NONE && !enemy.baitedAttack &&
                (enemy.state == CombatState.ATTACK_HOLD || enemy.isAttacking() &&
                        Math.abs(enemy.weaponYaw) < Math.toRadians(12));

        if (immediateDanger && !self.threatSeen) {
            self.threatSeen = true;
            self.reactionTime = 0.10 + (100 - self.stats.reaction()) * 0.0045 + random.nextDouble() * 0.07;
        }
        if (!immediateDanger) self.threatSeen = false;
        if (self.reactionTime > 0) self.reactionTime -= DECISION_INTERVAL;
        if (enemyAttack) stopBait(self);

        var enemyBehind = isKnownRear(self, enemy);
        var rearDanger = isImmediateRearDanger(self);
        if (rearDanger && self.rearReactionTime <= 0 &&
                (self.state == CombatState.NEUTRAL || self.isGuarding())) {
            if (self.state == CombatState.NEUTRAL && immediateDanger && !enemyBehind) beginBlock(self, enemy);
            if (self.backEscapeTime > 0) setBackEscapeDecision(self);
            else if (self.backEscapeCooldown <= 0) beginBackEscape(self);
            else self.decision = defensiveMovement(self, perceivedDistance);
            return;
        }

        if (immediateDanger && !enemyBehind && self.reactionTime <= 0 && self.state == CombatState.NEUTRAL) {
            var defenseCommitment = Math.clamp(self.stats.defenseTiming() + self.tactic.defenseCommitmentBonus,
                    0, 125);
            if (enemy.isAttacking() || perceivedDistance < 1.72 ||
                    random.nextInt(125) < defenseCommitment) {
                var chamberWillingness = (self.stats.aggression() - 1) / 99.0;
                var chamberJudgment = (self.stats.decisionMaking() + self.stats.reaction()) / 200.0;
                var chamberChance = Math.clamp((0.025 + chamberWillingness * 0.12 +
                        chamberJudgment * 0.10 + (readableEnemyAttack ? 0.12 : 0)) *
                        self.tactic.chamberMultiplier(readableEnemyAttack), 0.01, 0.55);
                if (enemy.chamberType == ChamberType.SIMPLE && enemy.chamberChain > 0)
                    chamberChance *= Math.max(MIN_RECHAMBER_FACTOR,
                            RECHAMBER_BASE_FACTOR - RECHAMBER_CHAIN_PENALTY * enemy.chamberChain);
                if (random.nextDouble() < chamberChance) beginChamberAttempt(self, enemy);
                else beginBlock(self, enemy);
            } else self.decision = defensiveMovement(self, perceivedDistance);
            return;
        }
        if (scenario == SimulationScenario.MODERN_2V2 && supportPlan != null &&
                supportPlan.supporter == self &&
                supportPlan.state == SupportPlanState.PREPARING_TO_LEAVE) {
            self.decision = supportPlan.flankSide == EngagementSlot.LEFT ?
                    Decision.LOCAL_FLANK_LEFT : Decision.LOCAL_FLANK_RIGHT;
            return;
        }
        if (applyFixerDecision(self, enemy) || applyFlankDecision(self, enemy)) return;
        if (features.combatPhases() && applyFrontLineDecision(self)) return;
        if (features.freeKill() && self.freeKillAttempt && self.state != CombatState.RECOVERY &&
                self.state != CombatState.BLOCK_STUN) {
            self.decision = Decision.FREE_KILL_BACKSTAB;
            if (self.state == CombatState.ATTACK_HOLD) startAttack(self, CombatState.ATTACK_ACTIVE);
            return;
        }
        if (self.backEscapeTime > 0 &&
                (self.state == CombatState.NEUTRAL || self.isGuarding())) {
            setBackEscapeDecision(self);
            return;
        }
        if (features.pressure() && (applyOutnumberingPressure(self) || applyPressureReaction(self))) return;
        if (!immediateDanger && self.rearThreat != null && self.rearReactionTime <= 0 &&
                self.state == CombatState.NEUTRAL &&
                self.backEscapeCooldown <= 0 &&
                knownDistance(self, self.rearThreat) < 3.5 &&
                random.nextDouble() < self.stats.backProtection() / 650.0) {
            beginBackEscape(self);
            return;
        }
        if (features.pressure() && tryReestablishPressureHold(self, enemy, perceivedDistance)) return;
        if (self.state == CombatState.NEUTRAL && tryExploitModernAttackOpportunity(
                self, enemy, enemyVisible, perceivedDistance)) return;
        if (features.freeKill() && self.state == CombatState.NEUTRAL) {
            var freeKillTarget = findFreeKillTarget(self);
            if (freeKillTarget != null && random.nextDouble() < freeKillChoiceChance(self)) {
                beginFreeKillAttack(self, freeKillTarget);
                return;
            }
        }
        if (features.spatialDezone() && self.dezoneMessage.getTime() > 0 && nearBoundary(self)) {
            endDezone(self);
            self.decision = boundaryEscape(self);
            return;
        }
        if (features.bait() && self.state == CombatState.NEUTRAL && enemyVisible && enemy.baitedAttack) {
            beginBaitPunish(self, distance);
            return;
        }
        if (features.pressure() && self.state == CombatState.NEUTRAL &&
                tryBeginPressureAttack(self, enemy, perceivedDistance)) return;
        if (self.state == CombatState.NEUTRAL && applyLocalRoleDecision(self, enemy, enemyVisible,
                perceivedDistance)) return;
        if (features.targetSwitchDezone() && self.state == CombatState.NEUTRAL &&
                self.dezoneCooldown <= 0 && !nearBoundary(self)) {
            var switchTarget = findTargetSwitchDezoneTarget(self, enemy);
            if (switchTarget != null && random.nextDouble() < targetSwitchDezoneChance(self)) {
                beginTargetSwitchDezone(self, enemy, switchTarget, distance);
                return;
            }
        }
        if (self.state == CombatState.ATTACK_HOLD) {
            if (features.pressure() && maintainPressureAttack(self)) return;
            double v = Math.atan2(d.y(), d.x());
            if (combatPhase == CombatPhase.ROTATION && self.openingChoice == OpeningChoice.ROTATE &&
                    combatPhaseTime < 0.55) {
                self.decision = self.rotationSide < 0 ? Decision.ROTATION_LEFT : Decision.ROTATION_RIGHT;
                return;
            }
            if (self.localHoldTransferTime > 0) {
                self.decision = Decision.LOCAL_HOLD_TRANSFER;
                var facing = Math.abs(angleDifference(self.weaponWorldAngle(), v));
                var withinRange = perceivedDistance <
                        self.projectedThrustLength() + Musketeer.BODY_RADIUS + 0.16;
                var releaseSkill = Math.clamp(self.stats.attackTiming() + self.stats.decisionMaking() / 3 +
                        self.tactic.releaseBonus, 0, 140);
                if (withinRange && facing < 0.30 && random.nextInt(140) < releaseSkill)
                    startAttack(self, CombatState.ATTACK_ACTIVE);
                return;
            }
            if (self.dezoneSwitchCommitted) {
                self.decision = Decision.DEZONE_SWITCH_ATTACK;
                var facing = Math.abs(angleDifference(self.weaponWorldAngle(), v));
                var releaseSkill = Math.clamp(self.stats.attackTiming() + self.stats.decisionMaking() / 3 +
                        self.tactic.releaseBonus, 0, 140);
                if (perceivedDistance < self.projectedThrustLength() + Musketeer.BODY_RADIUS + 0.18 &&
                        facing < 0.30 && random.nextInt(140) < releaseSkill)
                    startAttack(self, CombatState.ATTACK_ACTIVE);
                return;
            }
            if (self.punishingBait) {
                self.decision = Decision.PUNISH_BAIT;
                if (enemy.state == CombatState.RECOVERY || !enemy.baitedAttack)
                    startAttack(self, CombatState.ATTACK_ACTIVE);
                return;
            }
            if (self.exploitingBait) {
                self.decision = Decision.EXPLOIT_BAIT;
                var facing = Math.abs(angleDifference(self.weaponWorldAngle(), v));
                if (distance <= self.projectedThrustLength() + Musketeer.BODY_RADIUS - 0.08 && facing < 0.30)
                    startAttack(self, CombatState.ATTACK_ACTIVE);
                return;
            }
            if (features.spatialDezone() && self.dezoneMessage.getTime() > 0) {
                setDezoneDecision(self);
                return;
            }
            if (isFiftyFiftySituation(self, enemy, perceivedDistance)) {
                if (acceptsFiftyFifty(self, enemy)) {
                    if (combatPhase == CombatPhase.FRONT_LINE) self.openingChoice = OpeningChoice.STAB;
                    self.attackIntent = AttackIntent.DIRECT_RELEASE;
                    self.decision = Decision.ACCEPT_FIFTY_FIFTY;
                    startAttack(self, CombatState.ATTACK_ACTIVE);
                } else {
                    if (combatPhase == CombatPhase.FRONT_LINE) self.openingChoice = OpeningChoice.ROTATE;
                    beginFeint(self, true);
                    self.decision = Decision.REFUSE_FIFTY_FIFTY;
                }
                return;
            }
            var facing = Math.abs(angleDifference(self.weaponWorldAngle(), v));
            if (shouldFeintToBlock(self, enemy, enemyThreat, perceivedDistance)) {
                beginFeint(self, true);
                return;
            }
            if (shouldChangeAttack(self, enemy)) {
                beginFeint(self, false);
                return;
            }
            var withinReleaseDistance = perceivedDistance <
                    self.projectedThrustLength() + Musketeer.BODY_RADIUS + 0.18;
            if (facing >= 0.30) {
                chooseOffAxisHoldAction(self, withinReleaseDistance);
                return;
            }
            var goodChance = Math.clamp(self.stats.attackTiming() + self.stats.decisionMaking() / 3 +
                    self.tactic.releaseBonus, 0, 140);
            if (withinReleaseDistance && random.nextInt(140) < goodChance) {
                self.decision = Decision.RELEASE_ATTACK;
                startAttack(self, CombatState.ATTACK_ACTIVE);
            } else if (perceivedDistance > self.tactic.holdAdvanceDistance) self.decision = Decision.APPROACH;
            else if (combatPhase == CombatPhase.ROTATION)
                self.decision = self.rotationSide < 0 ? Decision.ROTATION_LEFT : Decision.ROTATION_RIGHT;
            else if (self.tactic == Tactic.DEFENSIVE && random.nextInt(100) < 45)
                self.decision = Decision.HOLD_ATTACK;
            else self.decision = randomStrafe(self);
            return;
        }
        if (self.state == CombatState.BLOCKING) {
            if (features.pressure() && maintainPressureGuard(self)) return;
            if (enemyAttack && !self.blockDirection.blocks(enemy.attackDirection) &&
                    self.guardSwitchCooldown <= 0 && operationSucceeds(self.stats.defenseTiming())) {
                switchGuard(self, enemy.attackDirection);
                return;
            }
            if (enemyVisible && enemy.state == CombatState.RECOVERY && self.stateTime >= BLOCK_MIN_TIME) {
                var riposteReach = CombatDirection.UP.thrustLength + Musketeer.BODY_RADIUS + 0.12;
                if (perceivedDistance > riposteReach) {
                    self.decision = Decision.APPROACH;
                    return;
                }
                if (random.nextInt(220) < self.stats.attackTiming() + self.stats.aggression()) {
                    setState(self, CombatState.NEUTRAL);
                    beginAttack(self, enemy, perceivedDistance);
                    self.decision = Decision.RIPOSTE;
                    return;
                }
            }
            if (features.spatialDezone() && self.dezoneMessage.getTime() > 0) setDezoneDecision(self);
            else if (features.combatPhases() && !immediateDanger && combatPhase == CombatPhase.ROTATION)
                self.decision = self.rotationSide < 0 ? Decision.ROTATION_LEFT : Decision.ROTATION_RIGHT;
            else if (features.engagementCycle() && !immediateDanger &&
                    self.engagementPhase == EngagementPhase.PROBE)
                self.decision = self.engagementSide < 0 ? Decision.PROBE_LEFT : Decision.PROBE_RIGHT;
            else if (features.engagementCycle() && !immediateDanger &&
                    self.engagementPhase == EngagementPhase.RESET)
                self.decision = Decision.RESET_GUARD;
            else self.decision = Decision.BLOCK;
            return;
        }
        if (self.state == CombatState.BLOCK_PREPARE) {
            if (features.pressure() && self.pressureRole != PressureRole.NONE) {
                applyOutnumberingPressure(self);
                return;
            }
            self.decision = self.pressureGuardTime > 0 ? Decision.PRESSURE_BLOCK :
                    self.guardSwitching ? Decision.SWITCH_GUARD : Decision.BLOCK;
            return;
        }
        if (self.state == CombatState.FEINT) return;
        if (self.state != CombatState.NEUTRAL) {
            if (self.dezoneSwitchTarget != null && !self.dezoneSwitchCommitted) setDezoneDecision(self);
            else if (self.dezoneSwitchCommitted) self.decision = Decision.DEZONE_SWITCH_ATTACK;
            else if (self.baitedAttack) self.decision = Decision.FALL_FOR_BAIT;
            else if (self.punishingBait) self.decision = Decision.PUNISH_BAIT;
            else if (self.exploitingBait) self.decision = Decision.EXPLOIT_BAIT;
            else if (self.state == CombatState.DOUBLE_BLOCK) self.decision = Decision.DOUBLE_BLOCK;
            else if (self.pressureCounter && self.state == CombatState.ATTACK_PREPARE)
                self.decision = self.attackDirection == CombatDirection.UP ?
                        Decision.PRESSURE_COUNTER_HIGH : Decision.PRESSURE_COUNTER_LOW;
            else if (self.pressureMode && self.state == CombatState.ATTACK_PREPARE)
                self.decision = Decision.PRESSURE_HOLD;
            else if (self.state == CombatState.ATTACK_PREPARE)
                self.decision = self.attackIntent == AttackIntent.DIRECT_RELEASE ?
                        Decision.PREPARE_DIRECT_ATTACK : Decision.PREPARE_HOLD_ATTACK;
            else if (self.state == CombatState.BLOCKING && self.dezoneMessage.getTime() > 0) setDezoneDecision(self);
            else if (self.state == CombatState.BLOCKING) self.decision = Decision.BLOCK;
            else if (self.state == CombatState.CHAMBER_PREPARE || self.state == CombatState.CHAMBER_HOLD ||
                    self.state == CombatState.CHAMBER_ACTIVE) self.decision = Decision.CHAMBER;
            else if (self.state == CombatState.ATTACK_ACTIVE && self.pressureCounter)
                self.decision = self.attackDirection == CombatDirection.UP ?
                        Decision.PRESSURE_COUNTER_HIGH : Decision.PRESSURE_COUNTER_LOW;
            else if (self.state == CombatState.ATTACK_ACTIVE && self.pressureMode)
                self.decision = self.pressureTransferTarget == null ?
                        Decision.RELEASE_ATTACK : Decision.PRESSURE_TRANSFER;
            else if (self.state == CombatState.ATTACK_ACTIVE) self.decision = Decision.RELEASE_ATTACK;
            else if (self.state == CombatState.BLOCK_STUN) self.decision = Decision.WAIT;
            else if (features.engagementCycle() && applyForcedEngagementDecision(self)) { }
            else self.decision = Decision.RETREAT;
            return;
        }
        if (features.spatialDezone() && self.dezoneMessage.getTime() > 0) {
            setDezoneDecision(self);
            return;
        }
        if (features.bait() && self.baitMessage.getTime() > 0 && !enemyThreat) {
            self.decision = self.baitOffset < 0 ? Decision.BAIT_LEFT : Decision.BAIT_RIGHT;
            return;
        }
        if (features.engagementCycle() && applyForcedEngagementDecision(self)) return;
        if (perceivedDistance < 1.25) self.decision = defensiveMovement(self, perceivedDistance);
        else if (enemyThreat) self.decision = defensiveMovement(self, perceivedDistance);
        else if (nearBoundary(self)) self.decision = boundaryEscape(self);
        else if (features.combatPhases() &&
                applyRotationDecision(self, enemy, enemyVisible, perceivedDistance)) { }
        else if (features.bait() && visibleBait && self.observedBaitSequence != enemy.baitSequence &&
                distance > 1.38 && distance < 1.82) {
            self.observedBaitSequence = enemy.baitSequence;
            if (!operationSucceeds(self.stats.distanceControl())) beginBaitedAttack(self, enemy, distance);
            else if (random.nextInt(300) < Math.clamp(self.stats.decisionMaking() + self.stats.footwork() +
                    self.stats.aggression() + self.tactic.pressureBonus, 0, 300))
                beginBaitAdvanceAttack(self, enemy, distance);
            else self.decision = randomStrafe(self);
        }
        else if (enemyVisible && enemy.state == CombatState.RECOVERY && perceivedDistance < 2.02)
            beginAttack(self, perceivedDistance);
        else if (enemyVisible && enemy.state == CombatState.RECOVERY) self.decision = switch (self.tactic) {
            case DEFENSIVE -> random.nextInt(100) < 70 ? Decision.WAIT : randomStrafe(self);
            case NEUTRAL -> random.nextBoolean() ? randomStrafe(self) : Decision.WAIT;
            case AGGRESSIVE -> Decision.APPROACH;
        };
        else if (features.spatialDezone() && enemyVisible && enemy.dezoneMessage.getTime() > 0 &&
                enemy.dezoneStance != DezoneStance.HOLD &&
                perceivedDistance > 1.45 &&
                random.nextInt(100) < Math.clamp((self.stats.aggression() + self.stats.decisionMaking()) / 2 +
                        self.tactic.followBonus, 0, 100))
            self.decision = Decision.APPROACH;
        else if (features.spatialDezone() && self.dezoneCooldown <= 0 &&
                enemyVisible && enemy.state == CombatState.NEUTRAL &&
                perceivedDistance > 1.30 && perceivedDistance < 2.80 &&
                random.nextDouble() * 1000 < (self.stats.zoneExit() + self.stats.footwork() +
                        self.stats.decisionMaking()) / 3.0 * self.tactic.dezoneMultiplier)
            beginDezone(self, perceivedDistance);
        else if (features.bait() && enemyVisible && enemy.state == CombatState.NEUTRAL &&
                perceivedDistance > 1.50 && perceivedDistance < 2.05 &&
                random.nextDouble() * 1000 < (self.stats.decisionMaking() + self.stats.distanceControl()) / 4.0 *
                        self.tactic.baitMultiplier)
            beginBait(self);
        else if (features.engagementCycle() && applyPlannedEngagement(self, enemy, perceivedDistance)) { }
        else if (perceivedDistance > self.tactic.approachDistance) {
            if (self.tactic == Tactic.DEFENSIVE && random.nextInt(100) < 70)
                self.decision = random.nextInt(100) < 65 ? randomStrafe(self) : Decision.WAIT;
            else self.decision = Decision.APPROACH;
        }
        else if (perceivedDistance > 1.38 && perceivedDistance < 1.82 &&
                random.nextDouble() * 1000 < (self.stats.aggression() + self.stats.decisionMaking()) / 3.0 *
                        self.tactic.attackMultiplier)
            beginAttack(self, perceivedDistance);
        else self.decision = switch (self.tactic) {
                case DEFENSIVE -> random.nextInt(100) < 55 ? Decision.WAIT : randomStrafe(self);
                case NEUTRAL -> random.nextInt(100) < 78 ? randomStrafe(self) : Decision.WAIT;
                case AGGRESSIVE -> random.nextInt(100) < 90 ? randomStrafe(self) : Decision.WAIT;
            };
    }

    private boolean tryExploitModernAttackOpportunity(Musketeer self, Musketeer enemy,
                                                       boolean enemyVisible, double distance) {
        var group = activeModernGroup();
        if (scenario != SimulationScenario.MODERN_2V2 || group == null ||
                self.tacticalIntent != TacticalIntent.GROUP || self.groupIntent != GroupIntent.MARK ||
                group.phase != GroupPhase.APPROACH && group.phase != GroupPhase.HOLD_SHAPE &&
                        group.phase != GroupPhase.ROTATE || !enemyVisible) return false;
        var opportunity = assessAttackOpportunity(self, enemy);
        if (!opportunity.viable()) return false;
        var urgency = Math.clamp((0.72 - opportunity.availableWindow()) / 0.72, 0, 1);
        var noticeChance = Math.clamp(0.05 + self.stats.reaction() * 0.0010 +
                self.stats.decisionMaking() * 0.0011 + self.stats.attackTiming() * 0.0008 +
                urgency * 0.08 + (opportunity.primaryReason() ==
                AttackOpportunityReason.RISKY_INITIATIVE ? self.stats.aggression() * 0.0012 : 0),
                0.08, 0.48);
        if (random.nextDouble() >= noticeChance) return false;
        beginAttack(self, enemy, distance);
        return true;
    }

    private boolean applyLocalRoleDecision(Musketeer self, Musketeer enemy, boolean enemyVisible,
                                           double distance) {
        if (self.localRole == LocalRole.FREE || self.localObjective != enemy || !enemyVisible ||
                self.pressureRole != PressureRole.NONE) return false;
        var teammate = teammateOf(self);
        var opening = enemy.target != self || enemy.state == CombatState.RECOVERY ||
                enemy.state == CombatState.BLOCK_STUN || enemy.isGuarding();
        return switch (self.localRole) {
            case FIXER -> {
                if (distance < 0.76) self.decision = Decision.RETREAT;
                else if (features.pressure() && distance <= 1.90 && !enemy.isAttacking() &&
                        random.nextDouble() < 0.04 + self.stats.pressureAbility() * 0.0012)
                    beginPressureAttack(self, enemy, distance);
                else if (!features.pressure() && distance >= 1.18 && distance <= 1.90 &&
                        !enemy.isAttacking() && random.nextDouble() <
                        0.025 + (self.stats.attackTiming() + self.stats.decisionMaking()) * 0.00045) {
                    beginAttack(self, enemy, distance);
                    self.attackIntent = AttackIntent.HOLD;
                    self.decision = Decision.PREPARE_HOLD_ATTACK;
                }
                else self.decision = Decision.LOCAL_FIX;
                yield true;
            }
            case FLANKER -> {
                if (opening && distance >= 1.18 && distance <= 1.92 &&
                        random.nextDouble() < 0.06 + (self.stats.attackTiming() +
                                self.stats.decisionMaking()) * 0.00055 +
                                self.stats.teamworkAbility() * 0.0015)
                    beginAttack(self, enemy, distance);
                else self.decision = self.localSide < 0 ?
                        Decision.LOCAL_FLANK_LEFT : Decision.LOCAL_FLANK_RIGHT;
                yield true;
            }
            case COVER -> {
                var protectsTeammate = teammate != null && teammate.alive && enemy.target == teammate;
                if (protectsTeammate && opening && distance >= 1.12 && distance <= 1.94 &&
                        random.nextDouble() < 0.05 + (self.stats.attackTiming() +
                                self.stats.decisionMaking()) * 0.00045 +
                                self.stats.teamworkAbility() * 0.0014)
                    beginAttack(self, enemy, distance);
                else self.decision = Decision.LOCAL_COVER;
                yield true;
            }
            case FREE -> false;
        };
    }

    private boolean applyFlankDecision(Musketeer flanker, Musketeer defender) {
        if (scenario != SimulationScenario.MODERN_2V2 &&
                scenario != SimulationScenario.CONTROLLED_2V1 &&
                scenario != SimulationScenario.CONTROLLED_2V2 ||
                flanker.tacticalIntent != TacticalIntent.FLANK ||
                flanker.engagement == null ||
                flanker.engagement.type != EngagementType.TWO_VS_ONE ||
                flanker.engagement.primaryTarget != defender) return false;
        if (flanker.flankPhase == FlankPhase.CONTESTED ||
                flanker.flankPhase == FlankPhase.ABORTED ||
                flanker.flankPhase == FlankPhase.COMPLETED) return false;
        if (flanker.flankPhase == FlankPhase.APPROACH_SIDE ||
                flanker.flankPhase == FlankPhase.GAIN_REAR_ANGLE ||
                flanker.flankPhase == FlankPhase.STABILIZE_REAR) {
            flanker.decision = flanker.localSide < 0 ?
                    Decision.LOCAL_FLANK_LEFT : Decision.LOCAL_FLANK_RIGHT;
            return true;
        }
        flanker.backstabOpportunity = assessBackstabOpportunity(flanker.engagement);
        if (flanker.flankPhase == FlankPhase.PREPARE_BACKSTAB) {
            if (flanker.state == CombatState.NEUTRAL) {
                if (!flanker.backstabOpportunity.valid()) {
                    flanker.decision = Decision.LOCAL_FLANK_RIGHT;
                    return true;
                }
                beginBackstab(flanker, defender);
                return true;
            }
            if (flanker.state == CombatState.ATTACK_PREPARE) {
                flanker.decision = Decision.PREPARE_HOLD_ATTACK;
                return true;
            }
            if (flanker.state == CombatState.ATTACK_HOLD) {
                if (canCommitBackstab(flanker, defender) &&
                        coordinatedReleaseAllowed(flanker.engagement, flanker)) {
                    registerCoordinatedRelease(flanker.engagement, flanker);
                    flanker.controlledBackstabReleaseAngle = flanker.rearAngle;
                    flanker.decision = Decision.RELEASE_ATTACK;
                    changeFlankPhase(flanker, FlankPhase.COMMIT_BACKSTAB, "backstab_released");
                    logTactical("BACKSTAB_COMMITTED", opportunityFields(flanker, defender) +
                            " rearAngleAtRelease=" +
                            "%.1f".formatted(Math.toDegrees(flanker.controlledBackstabReleaseAngle)));
                    startAttack(flanker, CombatState.ATTACK_ACTIVE);
                } else if (flanker.rearAngle < REAR_LOSS_ANGLE ||
                        !flanker.backstabOpportunity.fixerMaintainsPressure() ||
                        !flanker.backstabOpportunity.interceptorSafe() ||
                        !flanker.backstabOpportunity.attackLineClear()) {
                    loseBackstabWindow(flanker, defender,
                            flanker.rearAngle < REAR_LOSS_ANGLE ? "defender_turned" :
                                    !flanker.backstabOpportunity.fixerMaintainsPressure() ? "fixer_lost" :
                                            !flanker.backstabOpportunity.interceptorSafe() ?
                                                    "interceptor_threat" : "ally_line_blocked");
                    changeFlankPhase(flanker, FlankPhase.GAIN_REAR_ANGLE, "backstab_window_lost");
                } else flanker.decision = Decision.HOLD_ATTACK;
                return true;
            }
            if (flanker.state == CombatState.RECOVERY || flanker.state == CombatState.BLOCK_STUN)
                return true;
        }
        if (flanker.flankPhase == FlankPhase.COMMIT_BACKSTAB) {
            flanker.decision = flanker.isAttacking() ? Decision.RELEASE_ATTACK : Decision.WAIT;
            return true;
        }
        return false;
    }

    private boolean applyFixerDecision(Musketeer fixer, Musketeer defender) {
        if (fixer.tacticalIntent != TacticalIntent.FIX || fixer.engagement == null ||
                fixer.engagement.type != EngagementType.TWO_VS_ONE ||
                fixer.engagement.primaryTarget != defender || !defender.alive) return false;
        var flankPhase = fixer.engagement.flanker.flankPhase;
        if (flankPhase == FlankPhase.CONTESTED || flankPhase == FlankPhase.ABORTED ||
                flankPhase == FlankPhase.COMPLETED) return false;
        var distance = fixer.pos.minus(defender.pos).hypotenuse();
        var inPressureRange = distance <= fixer.projectedThrustLength() +
                Musketeer.BODY_RADIUS + 0.52;
        if (fixer.state == CombatState.NEUTRAL && inPressureRange) {
            beginAttack(fixer, defender, distance);
            fixer.attackIntent = AttackIntent.HOLD;
            fixer.decision = Decision.PREPARE_HOLD_ATTACK;
            return true;
        }
        if (fixer.state == CombatState.ATTACK_PREPARE) {
            fixer.decision = Decision.PREPARE_HOLD_ATTACK;
            return true;
        }
        if (fixer.state == CombatState.ATTACK_HOLD) {
            var coordination = fixer.engagement.coordination;
            var coordinatedRelease = scenario == SimulationScenario.MODERN_2V2 &&
                    coordinatedReleaseAllowed(fixer.engagement, fixer);
            var defenderDistracted = defender.target != fixer ||
                    Math.abs(angleDifference(defender.angle,
                            fixer.pos.minus(defender.pos).radians())) > Math.toRadians(78);
            if (inPressureRange && (coordinatedRelease || defenderDistracted)) {
                registerCoordinatedRelease(fixer.engagement, fixer);
                fixer.decision = Decision.RELEASE_ATTACK;
                startAttack(fixer, CombatState.ATTACK_ACTIVE);
            } else fixer.decision = Decision.HOLD_ATTACK;
            return true;
        }
        return fixer.isAttacking() || fixer.state == CombatState.RECOVERY ||
                fixer.state == CombatState.BLOCK_STUN;
    }

    private boolean coordinatedReleaseAllowed(Engagement engagement, Musketeer attacker) {
        if (scenario != SimulationScenario.MODERN_2V2) return true;
        var coordination = engagement.coordination;
        if (coordination == null || coordination.primaryAttacker == null) return true;
        if (coordination.primaryAttacker == attacker) {
            if (coordination.secondaryAttacker != null &&
                    coordination.secondaryAttacker.tacticalIntent == TacticalIntent.FLANK &&
                    coordination.flankReadiness != FlankReadiness.FLANK_THREAT_READY &&
                    coordination.flankReadiness != FlankReadiness.FLANK_COMMITTED)
                return false;
            return coordination.phase == TwoVsOneCoordinationPhase.PRIMARY_THREAT_READY ||
                    coordination.phase == TwoVsOneCoordinationPhase.PRIMARY_COMMITTED ||
                    coordination.phase == TwoVsOneCoordinationPhase.SECONDARY_RELEASE_WINDOW;
        }
        return coordination.secondaryAttacker == attacker &&
                coordination.phase == TwoVsOneCoordinationPhase.SECONDARY_RELEASE_WINDOW &&
                simulationTime >= coordination.releaseWindowStart &&
                simulationTime <= coordination.releaseWindowEnd &&
                coordination.secondaryAssessment != null &&
                coordination.secondaryAssessment.viable();
    }

    private void registerCoordinatedRelease(Engagement engagement, Musketeer attacker) {
        var coordination = engagement.coordination;
        if (coordination == null || coordination.secondaryAttacker != attacker ||
                coordination.phase != TwoVsOneCoordinationPhase.SECONDARY_RELEASE_WINDOW ||
                coordination.secondaryReleased) return;
        var primary = coordination.primaryAttacker;
        if (primary != null && !attacker.isAttacking()) {
            attacker.attackDirection = primary.attackDirection.opposite();
            configureAim(attacker, attacker.pos.minus(coordination.defender.pos).hypotenuse());
        }
        coordination.secondaryReleased = true;
        ++modernMetrics.secondaryReleases;
        var primaryEta = coordination.secondaryAssessment.primaryImpactEta();
        var secondaryEta = coordinatedImpactEta(attacker, coordination.defender);
        modernMetrics.coordinatedImpactOffsetSum += secondaryEta - primaryEta;
        modernMetrics.attackerSeparationAtReleaseSum += Math.abs(angleDifference(
                primary.pos.minus(coordination.defender.pos).radians(),
                attacker.pos.minus(coordination.defender.pos).radians()));
        modernMetrics.defenderFacingAtSecondaryReleaseSum += Math.abs(angleDifference(
                coordination.defender.angle,
                attacker.pos.minus(coordination.defender.pos).radians()));
        ++modernMetrics.coordinatedReleaseSamples;
        coordination.lastAttackAt = simulationTime;
        logTactical("SECONDARY_RELEASE", "primary=" + primary.name(UiLanguage.FRENCH) +
                " secondary=" + attacker.name(UiLanguage.FRENCH) +
                " offset=" + "%.3f".formatted(secondaryEta - primaryEta) +
                " direction=" + attacker.attackDirection);
    }

    private BackstabOpportunity assessBackstabOpportunity(Engagement engagement) {
        var flanker = engagement.flanker;
        var defender = engagement.primaryTarget;
        var distance = flanker.pos.minus(defender.pos).hypotenuse();
        var maximumRange = flanker.projectedThrustLength() + Musketeer.BODY_RADIUS + 0.08;
        var weaponAlignment = Math.abs(angleDifference(flanker.weaponWorldAngle(),
                defender.pos.minus(flanker.pos).radians()));
        var fixerPressure = fixerMaintainsPressure(engagement);
        var interceptorSafe = engagement.interceptor == null || !engagement.interceptor.alive ||
                engagement.interceptor.interceptionPhase == InterceptionPhase.FAILED ||
                flanker.threatResponse != ThreatResponse.ENGAGE;
        var lineClear = distanceToSegment(engagement.fixer.pos, flanker.pos, defender.pos) >
                Musketeer.BODY_RADIUS * 1.42;
        var clearance = arenaClearance(flanker.pos);
        var angularLoss = Math.max(Math.toRadians(4), -flanker.rearAngleGainRate);
        var estimatedWindow = Math.max(0, flanker.rearAngle - REAR_ATTACK_ANGLE) / angularLoss;
        return new BackstabOpportunity(
                flanker.rearAngle >= REAR_STABLE_ANGLE,
                flanker.rearStableTime >= REAR_STABILITY_TIME,
                distance >= REAR_MIN_DISTANCE && distance <= maximumRange &&
                        distance <= REAR_MAX_DISTANCE,
                weaponAlignment <= Math.toRadians(28),
                fixerPressure,
                interceptorSafe,
                lineClear,
                clearance >= 0.18,
                flanker.rearAngle,
                distance,
                estimatedWindow);
    }

    private void beginBackstab(Musketeer flanker, Musketeer defender) {
        stopBait(flanker);
        changeTarget(flanker, defender, 1.0);
        var distance = defender.pos.minus(flanker.pos).hypotenuse();
        flanker.attackDirection = chooseAttackDirection(flanker, defender, distance);
        configureBackstabAim(flanker, distance);
        flanker.attackPitch = flanker.pitch;
        flanker.baseAttackPitch = flanker.pitch;
        flanker.attackIntent = AttackIntent.HOLD;
        flanker.controlledBackstabAttempt = true;
        flanker.decision = Decision.PREPARE_HOLD_ATTACK;
        setState(flanker, CombatState.ATTACK_PREPARE);
        logTactical("BACKSTAB_PREPARED", opportunityFields(flanker, defender) + " zone=TORSO");
    }

    private void configureBackstabAim(Musketeer flanker, double distance) {
        flanker.attackReachScale = 1;
        flanker.baitedAttack = false;
        flanker.punishingBait = false;
        flanker.exploitingBait = false;
        flanker.intendedZone = HitZone.TORSO;
        var accurate = operationSucceeds(flanker.stats.aim());
        flanker.attackHeight = HitZone.TORSO.centerHeight() +
                random.nextGaussian() * (0.025 + (100 - flanker.stats.aim()) * 0.0045);
        if (accurate)
            flanker.aimError = random.nextGaussian() * (101 - flanker.stats.aim()) * 0.0009;
        else if (random.nextBoolean()) {
            flanker.attackHeight -= 0.45 + random.nextDouble() * 0.75;
            flanker.aimError = random.nextGaussian() * 0.10;
        } else {
            flanker.attackHeight += random.nextGaussian() * 0.55;
            flanker.aimError = (random.nextBoolean() ? 1 : -1) *
                    (0.22 + random.nextDouble() * 0.38);
        }
        flanker.desiredPitch = flanker.pitchFor(flanker.attackHeight, distance);
        flanker.attackResolved = false;
    }

    private boolean canCommitBackstab(Musketeer flanker, Musketeer defender) {
        var opportunity = flanker.backstabOpportunity;
        var alignment = Math.abs(angleDifference(flanker.weaponWorldAngle(),
                defender.pos.minus(flanker.pos).radians()));
        return flanker.rearAngle >= BACKSTAB_COMMIT_ANGLE &&
                opportunity.inRange() && alignment <= Math.toRadians(24) &&
                opportunity.fixerMaintainsPressure() && opportunity.interceptorSafe() &&
                opportunity.attackLineClear() && opportunity.hasRecoverySpace();
    }

    private void loseBackstabWindow(Musketeer flanker, Musketeer defender, String reason) {
        var event = "BACKSTAB_WINDOW_LOST";
        if (reason.equals("defender_turned")) event = "BACKSTAB_LOST_DEFENDER_TURNED";
        else if (reason.equals("interceptor_threat"))
            event = scenario == SimulationScenario.MODERN_2V2 ?
                    "FLANK_INTERRUPTED_BY_INTERCEPTOR" : "FLANK_INTERRUPTED_BY_B2";
        logTactical(event, opportunityFields(flanker, defender) + " reason=" + reason);
        flanker.controlledBackstabAttempt = false;
        flanker.backstabWindowLogged = false;
        if (flanker.state == CombatState.ATTACK_PREPARE ||
                flanker.state == CombatState.ATTACK_HOLD)
            setState(flanker, CombatState.NEUTRAL);
    }

    private String opportunityFields(Musketeer flanker, Musketeer defender) {
        var opportunity = flanker.backstabOpportunity;
        return flankFields(flanker, defender) +
                " stable=" + opportunity.stable() +
                " inRange=" + opportunity.inRange() +
                " aligned=" + opportunity.weaponAligned() +
                " fixerPressure=" + opportunity.fixerMaintainsPressure() +
                " interceptorSafe=" + opportunity.interceptorSafe() +
                " lineClear=" + opportunity.attackLineClear();
    }

    private Musketeer findFreeKillTarget(Musketeer self) {
        Musketeer best = null;
        var bestDistance = Double.POSITIVE_INFINITY;
        for (var candidate : opponentsOf(self)) {
            if (!candidate.alive || !isVisible(self, candidate) || !isRearAttack(candidate, self)) continue;
            var towardCandidate = Math.atan2(candidate.getY() - self.getY(), candidate.getX() - self.getX());
            if (Math.abs(angleDifference(self.weaponWorldAngle(), towardCandidate)) > Math.toRadians(100))
                continue;
            var perceivedDistance = Math.max(0, Math.hypot(candidate.getX() - self.getX(), candidate.getY() - self.getY()) +
                    random.nextGaussian() * (100 - self.stats.distanceControl()) * 0.006);
            if (perceivedDistance > CombatDirection.UP.thrustLength + Musketeer.BODY_RADIUS + 0.10 ||
                    !believesDirectOpponentIsSafe(self, candidate)) continue;
            if (perceivedDistance < bestDistance) {
                best = candidate;
                bestDistance = perceivedDistance;
            }
        }
        self.freeKillPerceivedDistance = best == null ? 0 : bestDistance;
        return best;
    }

    private boolean believesDirectOpponentIsSafe(Musketeer self, Musketeer backstabTarget) {
        var directOpponent = self.target != backstabTarget && self.target != null && self.target.alive &&
                isTracked(self, self.target) ? self.target : closestTrackedOpponentExcept(self, backstabTarget);
        if (directOpponent == null) return true;
        var perceivedDistance = Math.max(0, knownDistance(self, directOpponent) +
                random.nextGaussian() * (100 - self.stats.distanceControl()) * 0.006);
        var dangerDistance = directOpponent.projectedThrustLength() + Musketeer.BODY_RADIUS + 0.55 +
                self.stats.teamworkAbility() * 0.0025;
        if (isVisible(self, directOpponent) && (directOpponent.state == CombatState.ATTACK_PREPARE ||
                directOpponent.state == CombatState.ATTACK_HOLD || directOpponent.isAttacking()))
            dangerDistance += 0.35;
        else if (isVisible(self, directOpponent) &&
                (directOpponent.state == CombatState.RECOVERY || directOpponent.state == CombatState.BLOCK_STUN))
            dangerDistance -= 0.30;
        return perceivedDistance > dangerDistance;
    }

    private Musketeer closestTrackedOpponentExcept(Musketeer self, Musketeer excluded) {
        Musketeer closest = null;
        var closestDistance = Double.POSITIVE_INFINITY;
        for (var opponent : opponentsOf(self)) {
            if (!opponent.alive || opponent == excluded || !isTracked(self, opponent)) continue;
            var distance = knownDistance(self, opponent);
            if (distance < closestDistance) {
                closest = opponent;
                closestDistance = distance;
            }
        }
        return closest;
    }

    private double freeKillChoiceChance(Musketeer self) {
        var readingSkill = (self.stats.decisionMaking() * 0.30 + self.stats.distanceControl() * 0.25 +
                self.stats.aggression() * 0.15 + self.stats.teamworkAbility() * 0.30) / 100.0;
        var tacticMultiplier = switch (self.tactic) {
            case DEFENSIVE -> 0.75;
            case NEUTRAL -> 1.0;
            case AGGRESSIVE -> 1.25;
        };
        return Math.min(0.24, (0.01 + 0.14 * readingSkill * readingSkill) * tacticMultiplier);
    }

    private void beginFreeKillAttack(Musketeer self, Musketeer target) {
        stopBait(self);
        endDezone(self);
        self.backEscapeTime = 0;
        changeTarget(self, target, 1.0);
        self.attackDirection = self.freeKillPerceivedDistance > 1.42 ?
                CombatDirection.UP : CombatDirection.DOWN;
        configureFreeKillAim(self);
        self.freeKillAttempt = true;
        self.attackPitch = self.pitch;
        self.attackIntent = AttackIntent.DIRECT_RELEASE;
        self.decision = Decision.FREE_KILL_BACKSTAB;
        setState(self, CombatState.ATTACK_PREPARE);
    }

    private void configureFreeKillAim(Musketeer self) {
        self.attackReachScale = 1;
        self.baitedAttack = false;
        self.punishingBait = false;
        self.exploitingBait = false;
        self.intendedZone = HitZone.TORSO;
        var accurate = operationSucceeds(self.stats.aim());
        self.attackHeight = HitZone.TORSO.centerHeight() +
                random.nextGaussian() * (0.025 + (100 - self.stats.aim()) * 0.0045);
        if (accurate) self.aimError = random.nextGaussian() * (101 - self.stats.aim()) * 0.0009;
        else if (random.nextBoolean()) {
            self.attackHeight -= 0.45 + random.nextDouble() * 0.75;
            self.aimError = random.nextGaussian() * 0.10;
        } else {
            self.attackHeight += random.nextGaussian() * 0.55;
            self.aimError = (random.nextBoolean() ? 1 : -1) * (0.22 + random.nextDouble() * 0.38);
        }
        self.desiredPitch = self.pitchFor(self.attackHeight, self.freeKillPerceivedDistance);
        self.attackResolved = false;
    }

    private Decision defensiveMovement(Musketeer self, double distance) {
        if (nearBoundary(self) && distance > 1.28) return boundaryEscape(self);
        return switch (self.tactic) {
            case DEFENSIVE -> distance < 1.35 || random.nextInt(100) < 60 ? Decision.RETREAT : randomStrafe(self);
            case NEUTRAL -> distance < 1.55 ? Decision.RETREAT : randomStrafe(self);
            case AGGRESSIVE -> distance < 1.30 ? Decision.RETREAT : randomStrafe(self);
        };
    }

    private boolean nearBoundary(Musketeer self) {
        return self.getX() < 0.78 || self.getX() > ARENA_WIDTH - 0.78 || self.getY() < 0.78 || self.getY() > ARENA_HEIGHT - 0.78;
    }

    private Decision boundaryEscape(Musketeer self) {
        return movementSpace(self, -1) >= movementSpace(self, 1) ?
                Decision.EDGE_ESCAPE_LEFT : Decision.EDGE_ESCAPE_RIGHT;
    }

    private boolean isImmediateRearDanger(Musketeer self) {
        var threat = self.rearThreat;
        if (threat == null || !threat.alive || !isVisible(self, threat) || !isRearAttack(self, threat)) return false;
        var attacking = threat.state == CombatState.ATTACK_PREPARE || threat.state == CombatState.ATTACK_HOLD ||
                threat.state == CombatState.CHAMBER_HOLD || threat.isAttacking();
        var towardSelf = Math.atan2(self.getY() - threat.getY(), self.getX() - threat.getX());
        return attacking && !threat.baitedAttack &&
                Math.abs(angleDifference(threat.weaponWorldAngle(), towardSelf)) < Math.toRadians(34) &&
                Math.hypot(threat.getX() - self.getX(), threat.getY() - self.getY()) <
                        threat.projectedThrustLength() + Musketeer.BODY_RADIUS + 0.45;
    }

    private void beginBackEscape(Musketeer self) {
        if (self.rearThreat == null || !self.rearThreat.alive || !isTracked(self, self.rearThreat)) {
            ++modernMetrics.backEscapesWithoutDetection;
            return;
        }
        stopBait(self);
        endDezone(self);
        self.backEscapeDirection = chooseBackEscapeDirection(self);
        self.backEscapeTime = 0.40 + self.stats.footwork() * 0.004 + self.stats.reaction() * 0.001;
        self.backEscapeCooldown = self.backEscapeTime + 0.34;
        self.backEscapeCountedSafe = false;
        ++modernMetrics.backEscapes;
        ++modernMetrics.consecutiveBackEscapes;
        modernMetrics.maximumConsecutiveBackEscapes = Math.max(
                modernMetrics.maximumConsecutiveBackEscapes,
                modernMetrics.consecutiveBackEscapes);
        setBackEscapeDecision(self);
    }

    private void setBackEscapeDecision(Musketeer self) {
        self.decision = self.backEscapeDirection < 0 ?
                Decision.PROTECT_BACK_LEFT : Decision.PROTECT_BACK_RIGHT;
    }

    private int chooseBackEscapeDirection(Musketeer self) {
        var bestDirection = backEscapeScore(self, -1) > backEscapeScore(self, 1) ? -1 : 1;
        var correctChoiceChance = 0.30 + self.stats.backProtection() * 0.0065;
        return random.nextDouble() < correctChoiceChance ? bestDirection : -bestDirection;
    }

    private double backEscapeScore(Musketeer self, int direction) {
        var horizon = 0.80;
        var candidateX = Math.clamp(self.getX() +
                        (Math.cos(self.angle) * 0.55 - Math.sin(self.angle) * direction * 0.85) * horizon,
                Musketeer.BODY_RADIUS, ARENA_WIDTH - Musketeer.BODY_RADIUS);
        var candidateY = Math.clamp(self.getY() +
                        (Math.sin(self.angle) * 0.55 + Math.cos(self.angle) * direction * 0.85) * horizon,
                Musketeer.BODY_RADIUS, ARENA_HEIGHT - Musketeer.BODY_RADIUS);
        var boundarySpace = Math.min(Math.min(candidateX, ARENA_WIDTH - candidateX),
                Math.min(candidateY, ARENA_HEIGHT - candidateY));
        var minimumDistance = Double.POSITIVE_INFINITY;
        Musketeer first = null, second = null;
        for (var opponent : opponentsOf(self)) {
            if (!opponent.alive || !isTracked(self, opponent)) continue;
            double distance = known(self, opponent).minus(candidateX, candidateY).hypotenuse();
            minimumDistance = Math.min(minimumDistance, distance);
            if (first == null) first = opponent;
            else second = opponent;
        }
        var separation = 0.0;
        if (first != null && second != null) {
            var firstAngle = Math.atan2(known(self, first).y() - candidateY, known(self, first).x() - candidateX);
            var secondAngle = Math.atan2(known(self, second).y() - candidateY, known(self, second).x() - candidateX);
            separation = Math.abs(angleDifference(firstAngle, secondAngle));
        }
        if (!Double.isFinite(minimumDistance)) minimumDistance = 0;
        return minimumDistance * 0.30 + boundarySpace * 0.18 - separation * 1.40;
    }

    private Musketeer findTargetSwitchDezoneTarget(Musketeer self, Musketeer currentTarget) {
        if (currentTarget == null || !currentTarget.alive) return null;
        var teammate = teammateOf(self);
        if (teammate == null || !teammate.alive) return null;
        Musketeer best = null;
        var bestScore = Double.POSITIVE_INFINITY;
        for (var candidate : opponentsOf(self)) {
            if (candidate == currentTarget || !candidate.alive || !isVisible(self, candidate)) continue;
            var distance = Math.hypot(candidate.getX() - self.getX(), candidate.getY() - self.getY());
            if (distance < 0.90 || distance > TARGET_SWITCH_MAX_DISTANCE) continue;
            var supportsTeammate = teammate.target == candidate || candidate.target == teammate;
            var opening = candidate.state == CombatState.RECOVERY || candidate.state == CombatState.BLOCK_STUN ||
                    isRearAttack(candidate, self);
            if (!supportsTeammate && !opening) continue;
            if (!believesDirectOpponentIsSafe(self, candidate) &&
                    operationSucceeds(self.stats.decisionMaking())) continue;
            var targetAngle = Math.atan2(candidate.getY() - self.getY(), candidate.getX() - self.getX());
            var score = distance + Math.abs(angleDifference(self.angle, targetAngle)) * 0.22;
            if (supportsTeammate) score -= 0.55;
            if (opening) score -= 0.45;
            score += random.nextGaussian() * (101 - self.stats.decisionMaking()) * 0.003;
            if (score < bestScore) {
                best = candidate;
                bestScore = score;
            }
        }
        return best;
    }

    private double targetSwitchDezoneChance(Musketeer self) {
        var ability = (self.stats.targetSwitchDezoneAbility() * 0.65 +
                self.stats.teamworkAbility() * 0.35) / 100.0;
        var willingness = (self.stats.zoneExit() - 1) / 99.0;
        var tacticMultiplier = switch (self.tactic) {
            case DEFENSIVE -> 0.65;
            case NEUTRAL -> 1.0;
            case AGGRESSIVE -> 1.35;
        };
        return Math.min(0.14, (0.008 + 0.058 * ability * ability) *
                (0.45 + willingness * 1.10) * tacticMultiplier);
    }

    private void beginTargetSwitchDezone(Musketeer self, Musketeer currentTarget, Musketeer switchTarget,
                                         double currentDistance) {
        stopBait(self);
        var ability = self.stats.targetSwitchDezoneAbility();
        self.dezoneOriginalTarget = currentTarget;
        self.dezoneSwitchTarget = switchTarget;
        self.dezoneSwitchCommitted = false;
        self.dezoneDangerTime = 0;
        self.dezoneMessage.setTime(0.72 - 0.0025 * ability + random.nextDouble() * 0.20);
        self.dezoneCooldown = self.dezoneMessage.getTime() + 0.85 + random.nextDouble() * 0.45;
        var switchAngle = Math.atan2(switchTarget.getY() - self.getY(), switchTarget.getX() - self.getX());
        var right = angleDifference(self.angle, switchAngle) >= 0;
        if (random.nextDouble() >= 0.30 + ability * 0.0068) right = !right;
        self.dezoneMovement = right ? DezoneMovement.TARGET_SWITCH_RIGHT :
                DezoneMovement.TARGET_SWITCH_LEFT;

        var defenseWeight = self.stats.defenseTiming() + (self.tactic == Tactic.DEFENSIVE ? 40 : 0);
        var attackWeight = self.stats.attackTiming() + (self.tactic == Tactic.AGGRESSIVE ? 40 : 0);
        var stanceRoll = random.nextInt(100 + defenseWeight + attackWeight);
        if (stanceRoll < defenseWeight) {
            self.dezoneStance = DezoneStance.BLOCK;
            beginBlock(self, currentTarget);
        } else if (stanceRoll < defenseWeight + attackWeight) {
            self.dezoneStance = DezoneStance.HOLD;
            beginAttack(self, currentDistance);
            self.attackIntent = AttackIntent.HOLD;
        } else self.dezoneStance = DezoneStance.NEUTRAL;
        setDezoneDecision(self);
    }

    private void updateTargetSwitchDezone(Musketeer self, double dt) {
        var switchTarget = self.dezoneSwitchTarget;
        if (switchTarget == null) return;
        var originalTarget = self.dezoneOriginalTarget;
        if (!switchTarget.alive && self.dezoneSwitchCommitted && self.isAttacking()) {
            clearTargetSwitchDezone(self);
            return;
        }
        if (!switchTarget.alive || !self.dezoneSwitchCommitted && !isVisible(self, switchTarget)) {
            cancelTargetSwitchDezone(self, false);
            return;
        }
        if (originalTarget == null || !originalTarget.alive) {
            self.dezoneDangerTime = 0;
            return;
        }
        if (!isVisible(self, originalTarget)) {
            self.dezoneDangerTime = 0;
            return;
        }
        var distance = Math.hypot(originalTarget.getX() - self.getX(), originalTarget.getY() - self.getY());
        var attacking = originalTarget.state == CombatState.ATTACK_PREPARE ||
                originalTarget.state == CombatState.ATTACK_HOLD ||
                originalTarget.state == CombatState.CHAMBER_HOLD || originalTarget.isAttacking();
        var towardSelf = Math.atan2(self.getY() - originalTarget.getY(), self.getX() - originalTarget.getX());
        var dangerousAttack = attacking && !originalTarget.baitedAttack &&
                Math.abs(angleDifference(originalTarget.weaponWorldAngle(), towardSelf)) < Math.toRadians(34) &&
                distance < originalTarget.projectedThrustLength() + Musketeer.BODY_RADIUS + 0.52;
        var pursuit = originalTarget.target == self && distance < 1.42 &&
                originalTarget.state != CombatState.RECOVERY && originalTarget.state != CombatState.BLOCK_STUN;
        if (!dangerousAttack && !pursuit) {
            self.dezoneDangerTime = 0;
            return;
        }
        self.dezoneDangerTime += dt;
        var responseDelay = 0.07 + (100 - self.stats.reaction()) * 0.0028;
        if (self.dezoneDangerTime < responseDelay || self.isAttacking()) return;
        cancelTargetSwitchDezone(self, true);
    }

    private void commitTargetSwitchDezone(Musketeer self) {
        var switchTarget = self.dezoneSwitchTarget;
        if (switchTarget == null || !switchTarget.alive || !isVisible(self, switchTarget)) {
            cancelTargetSwitchDezone(self, false);
            return;
        }
        self.dezoneSwitchCommitted = true;
        clearDezoneMovement(self);
        changeTarget(self, switchTarget, 1.10);
        var distance = Math.hypot(switchTarget.getX() - self.getX(), switchTarget.getY() - self.getY());
        configureTargetSwitchAttack(self, switchTarget, distance);
        if (self.state != CombatState.ATTACK_PREPARE && self.state != CombatState.ATTACK_HOLD) {
            if (self.isGuarding()) setState(self, CombatState.NEUTRAL);
            self.attackIntent = AttackIntent.DIRECT_RELEASE;
            setState(self, CombatState.ATTACK_PREPARE);
        }
        self.decision = Decision.DEZONE_SWITCH_ATTACK;
    }

    private void configureTargetSwitchAttack(Musketeer self, Musketeer switchTarget, double distance) {
        var direction = distance > 1.48 ? CombatDirection.UP : CombatDirection.DOWN;
        var teammate = teammateOf(self);
        if (teammate != null && teammate.alive && teammate.target == switchTarget &&
                (teammate.state == CombatState.ATTACK_PREPARE || teammate.state == CombatState.ATTACK_HOLD ||
                        teammate.isAttacking()) && operationSucceeds(self.stats.decisionMaking()))
            direction = teammate.attackDirection.opposite();
        self.attackDirection = direction;
        configureAim(self, distance);
        self.attackPitch = self.pitch;
        self.baseAttackPitch = self.pitch;
    }

    private void cancelTargetSwitchDezone(Musketeer self, boolean threatened) {
        var originalTarget = self.dezoneOriginalTarget;
        if (self.state == CombatState.ATTACK_PREPARE || self.state == CombatState.ATTACK_HOLD)
            setState(self, CombatState.NEUTRAL);
        clearDezoneMovement(self);
        clearTargetSwitchDezone(self);
        if (originalTarget == null || !originalTarget.alive) {
            self.decision = Decision.WAIT;
            self.decisionTime = 0;
            return;
        }
        changeTarget(self, originalTarget, 0.55);
        if (!threatened) {
            self.decision = Decision.WAIT;
            return;
        }
        if (isKnownRear(self, originalTarget)) beginBackEscape(self);
        else if (originalTarget.state == CombatState.ATTACK_PREPARE ||
                originalTarget.state == CombatState.ATTACK_HOLD || originalTarget.isAttacking())
            beginBlock(self, originalTarget);
        else self.decision = Decision.DEZONE_SWITCH_ABORT;
    }

    private void beginDezone(Musketeer self, double distance) {
        stopBait(self);
        self.dezoneMessage.setTime(0.55 + 0.005 * self.stats.zoneExit() + random.nextDouble() * 0.45);
        self.dezoneCooldown = self.dezoneMessage.getTime() + 0.80 + random.nextDouble() * 0.50;
        if (distance <= 2.20 && random.nextDouble() < 0.45 - 0.0035 * self.stats.zoneExit())
            self.dezoneMovement = DezoneMovement.BACKWARD;
        else {
            var left = random.nextBoolean();
            if (random.nextDouble() < 0.45 + 0.004 * self.stats.zoneExit())
                self.dezoneMovement = left ? DezoneMovement.ADVANCE_LEFT : DezoneMovement.ADVANCE_RIGHT;
            else self.dezoneMovement = left ? DezoneMovement.ORBIT_LEFT : DezoneMovement.ORBIT_RIGHT;
        }

        var defenseWeight = self.stats.defenseTiming() + (self.tactic == Tactic.DEFENSIVE ? 40 : 0);
        var attackWeight = self.stats.attackTiming() + (self.tactic == Tactic.AGGRESSIVE ? 40 : 0);
        var stanceRoll = random.nextInt(100 + defenseWeight + attackWeight);
        if (stanceRoll < defenseWeight) {
            self.dezoneStance = DezoneStance.BLOCK;
            self.blockDirection = random.nextBoolean() ? BlockDirection.UP : BlockDirection.DOWN;
            setState(self, CombatState.BLOCKING);
        } else if (stanceRoll < defenseWeight + attackWeight) {
            self.dezoneStance = DezoneStance.HOLD;
            beginAttack(self, distance);
            self.attackIntent = AttackIntent.HOLD;
        } else self.dezoneStance = DezoneStance.NEUTRAL;
        setDezoneDecision(self);
    }

    private void setDezoneDecision(Musketeer self) {
        self.decision = switch (self.dezoneMovement) {
            case BACKWARD -> Decision.DEZONE_BACKWARD;
            case ADVANCE_LEFT -> Decision.DEZONE_ADVANCE_LEFT;
            case ADVANCE_RIGHT -> Decision.DEZONE_ADVANCE_RIGHT;
            case ORBIT_LEFT -> Decision.DEZONE_ORBIT_LEFT;
            case ORBIT_RIGHT -> Decision.DEZONE_ORBIT_RIGHT;
            case TARGET_SWITCH_LEFT -> Decision.DEZONE_SWITCH_LEFT;
            case TARGET_SWITCH_RIGHT -> Decision.DEZONE_SWITCH_RIGHT;
            case NONE -> Decision.WAIT;
        };
    }

    private void endDezone(Musketeer self) {
        clearDezoneMovement(self);
        clearTargetSwitchDezone(self);
    }

    private void clearDezoneMovement(Musketeer self) {
        self.dezoneMessage.setTime();
        self.dezoneMovement = DezoneMovement.NONE;
        self.dezoneStance = DezoneStance.NEUTRAL;
    }

    private void clearTargetSwitchDezone(Musketeer self) {
        self.dezoneOriginalTarget = null;
        self.dezoneSwitchTarget = null;
        self.dezoneSwitchCommitted = false;
        self.dezoneDangerTime = 0;
    }

    private void beginAttack(Musketeer self, double distance) {
        beginAttack(self, self.target, distance);
    }

    private void beginAttack(Musketeer self, Musketeer enemy, double distance) {
        if ((scenario == SimulationScenario.MODERN_2V2 ||
                experimentalGroupPhase == ExperimentalGroupPhase.HANDOFF_2V2) &&
                activeModernGroup() != null &&
                self.groupIntent != GroupIntent.COMMIT_REVENGE &&
                self.groupIntent != GroupIntent.COUNTER_COVER &&
                !approveModernGroupCommit(self, enemy, distance))
            return;
        stopBait(self);
        self.attackDirection = chooseAttackDirection(self, enemy, distance);
        configureAim(self, distance);
        self.attackPitch = self.pitch;
        self.baseAttackPitch = self.pitch;
        self.attackIntent = (scenario == SimulationScenario.MODERN_2V2 ||
                experimentalGroupPhase == ExperimentalGroupPhase.HANDOFF_2V2) &&
                self.groupIntent == GroupIntent.COMMIT_ATTACK && self.attackOpportunity != null &&
                self.attackOpportunity.viable() ? AttackIntent.DIRECT_RELEASE :
                chooseAttackIntent(self, enemy, distance);
        self.decision = self.attackIntent == AttackIntent.DIRECT_RELEASE ?
                Decision.PREPARE_DIRECT_ATTACK : Decision.PREPARE_HOLD_ATTACK;
        setState(self, CombatState.ATTACK_PREPARE);
    }

    private boolean approveModernGroupCommit(Musketeer attacker, Musketeer target,
                                             double distance) {
        var group = activeModernGroup();
        if (group == null || target == null) return true;
        if (attacker.groupIntent != GroupIntent.MARK ||
                group.phase != GroupPhase.APPROACH && group.phase != GroupPhase.HOLD_SHAPE &&
                        group.phase != GroupPhase.ROTATE) {
            attacker.lastAttackOpportunityRejection = AttackOpportunityRejection.ALLY_NEEDS_COVER;
            modernMetrics.attackOpportunityRejections.merge(
                    AttackOpportunityRejection.ALLY_NEEDS_COVER, 1, Integer::sum);
            attacker.decision = Decision.WAIT;
            traceDecision(attacker, DecisionCategory.ATTACK_REJECTION, "REFUSE_STAB",
                    AttackOpportunityRejection.ALLY_NEEDS_COVER.name(), target, null,
                    0, 0, Double.POSITIVE_INFINITY, 1, group.phase.name());
            return false;
        }
        attacker.commitAssessment = assessCommit(attacker, target, distance);
        var opportunity = assessAttackOpportunity(attacker, target);
        attacker.attackOpportunity = opportunity;
        recordAttackOpportunity(opportunity);
        var arbitration = arbitrateTeamAttack(group, attacker, opportunity);
        if (!opportunity.viable() || arbitration.selectedAttacker() != attacker) {
            var rejection = !opportunity.viable() ? opportunity.rejectionReason() :
                    AttackOpportunityRejection.ALLY_SELECTED_TO_ATTACK;
            attacker.lastAttackOpportunityRejection = rejection;
            recordAttackOpportunityRejection(opportunity.primaryReason(), rejection);
            attacker.groupIntent = GroupIntent.MARK;
            attacker.decision = Decision.WAIT;
            if (rejection == AttackOpportunityRejection.REVENGE_RISK_TOO_HIGH)
                ++modernMetrics.attackCommitsRejectedByRevengeRisk;
            logTactical("ATTACK_OPPORTUNITY_REJECTED", "attacker=" + attacker.name(UiLanguage.FRENCH) +
                    " target=" + target.name(UiLanguage.FRENCH) +
                    " reason=" + opportunity.primaryReason() +
                    " window=" + "%.2f".formatted(opportunity.availableWindow()) +
                    " impactEta=" + "%.2f".formatted(opportunity.impactEta()) +
                    " revengeRisk=" + "%.2f".formatted(opportunity.revengeRisk()) +
                    " rejection=" + rejection);
            traceDecision(attacker, DecisionCategory.ATTACK_REJECTION, "REFUSE_STAB",
                    opportunity.primaryReason().name(), target, null, opportunity.score(),
                    opportunity.availableWindow(), opportunity.impactEta(),
                    opportunity.revengeRisk(), rejection.name());
            return false;
        }
        attacker.lastAttackOpportunityRejection = AttackOpportunityRejection.NONE;
        attacker.groupIntent = GroupIntent.COMMIT_ATTACK;
        group.committedAttacker = attacker;
        group.committedTarget = target;
        ++modernMetrics.attackCommits;
        recordAcceptedAttackOpportunity(opportunity);
        if (opportunity.allyCoverQuality() >= 0.43) ++modernMetrics.coveredAttackers;
        else ++modernMetrics.uncoveredAttackers;
        changeGroupPhase(group, GroupPhase.ATTACK_COMMITTED,
                "attack_opportunity_" + opportunity.primaryReason().name().toLowerCase());
        logTactical("ATTACK_OPPORTUNITY_ACCEPTED", "attacker=" + attacker.name(UiLanguage.FRENCH) +
                " target=" + target.name(UiLanguage.FRENCH) +
                " reason=" + opportunity.primaryReason() +
                " window=" + "%.2f".formatted(opportunity.availableWindow()) +
                " impactEta=" + "%.2f".formatted(opportunity.impactEta()) +
                " cover=" + "%.2f".formatted(opportunity.allyCoverQuality()) +
                " revengeRisk=" + "%.2f".formatted(opportunity.revengeRisk()) +
                " arbitration=" + arbitration.reason());
        traceDecision(attacker, DecisionCategory.ATTACK, "STAB", opportunity.primaryReason().name(),
                target, null, opportunity.score(), opportunity.availableWindow(),
                opportunity.impactEta(), opportunity.revengeRisk(), arbitration.reason());
        return true;
    }

    private AttackOpportunity assessAttackOpportunity(Musketeer attacker, Musketeer target) {
        if (attacker == null || target == null || !attacker.alive || !target.alive ||
                !isTracked(attacker, target))
            return AttackOpportunity.unavailable(attacker, target,
                    AttackOpportunityRejection.LOW_CONFIDENCE_PERCEPTION);
        var awareness = attacker.opponentAwareness.get(target);
        var perceivedTarget = known(attacker, target);
        var offset = perceivedTarget.minus(attacker.pos);
        var distance = offset.hypotenuse();
        var towardTarget = distance < 0.0001 ? new Pos(attacker.weaponWorldAngle()) :
                offset.div(distance);
        var alignment = Math.abs(angleDifference(attacker.weaponWorldAngle(), towardTarget.radians()));
        var alignEta = alignment / estimatedTurnSpeed(attacker);
        var prepareEta = attacker.state == CombatState.ATTACK_HOLD ? 0 :
                attacker.state == CombatState.ATTACK_PREPARE ?
                        Math.max(0, attacker.prepareTime() - attacker.stateTime) :
                        attacker.attackDirection.prepareTime;
        var contactEta = Math.max(0, distance - attacker.projectedThrustLength() -
                Musketeer.BODY_RADIUS) / estimatedMovementSpeed(attacker);
        var impactEta = alignEta + prepareEta + contactEta + 0.04;
        var ally = teammateOf(attacker);
        var opposingCoverer = teammateOf(target);
        var observedState = awareness.observedState;
        var observedDirection = awareness.observedAttackDirection;
        var targetStateWindow = switch (observedState) {
            case BLOCK_STUN -> Math.max(0, BLOCK_STUN_TIME - awareness.observedStateTime) + 0.20;
            case RECOVERY -> Math.max(0, observedDirection.recoveryTime - awareness.observedStateTime);
            case ATTACK_ACTIVE, CHAMBER_ACTIVE ->
                    Math.max(0, observedDirection.activeTime - awareness.observedStateTime) +
                            observedDirection.recoveryTime;
            case ATTACK_PREPARE, CHAMBER_PREPARE ->
                    Math.max(0, observedDirection.prepareTime - awareness.observedStateTime) +
                            observedDirection.activeTime + 0.18;
            case ATTACK_HOLD, CHAMBER_HOLD -> 0.30;
            case BLOCK_PREPARE, BLOCKING, DOUBLE_BLOCK -> 0.34;
            default -> 0.28;
        };
        var targetWeaponToAttacker = Math.abs(angleDifference(awareness.observedWeaponAngle,
                attacker.pos.minus(perceivedTarget).radians()));
        var targetBodyToAttacker = Math.abs(angleDifference(awareness.observedBodyAngle,
                attacker.pos.minus(perceivedTarget).radians()));
        var focusedOnAlly = false;
        var targetReturnEta = targetWeaponToAttacker / 3.5;
        if (ally != null && ally.alive) {
            var towardAlly = ally.pos.minus(perceivedTarget).radians();
            focusedOnAlly = Math.abs(angleDifference(awareness.observedWeaponAngle, towardAlly)) <=
                    Math.toRadians(30) && targetWeaponToAttacker >= Math.toRadians(34);
        }
        var lineClearance = ally == null ? 1.2 :
                distanceToSegment(ally.pos, attacker.pos, perceivedTarget);
        var lineSafety = Math.clamp((lineClearance - Musketeer.BODY_RADIUS * 1.32) / 0.80, 0, 1);
        var ownCover = ally == null ? null : assessCover(attacker, ally, target);
        var allyCoverQuality = ownCover == null ? 0 : ownCover.score();
        var opposingEta = perceivedThreatEta(attacker, opposingCoverer, attacker);
        var opposingCoverQuality = Double.isFinite(opposingEta) ?
                Math.clamp((attacker.recoveryTime() + impactEta + 0.24 - opposingEta) / 1.05, 0, 1) : 0;
        var perceivedOpposingEta = opposingEta + Math.max(0, 68 - attacker.stats.decisionMaking()) * 0.006;
        var revengeRisk = Math.clamp(opposingCoverQuality *
                (0.72 + attacker.recoveryTime() * 0.34) * (1 - allyCoverQuality * 0.42), 0, 1);
        var counterAttackEta = perceivedThreatEta(attacker, target, attacker);
        var counterAttackRisk = Math.clamp((impactEta + 0.12 - counterAttackEta) / 0.72, 0, 1);
        var targetAllyDistance = opposingCoverer == null ? Double.POSITIVE_INFINITY :
                known(attacker, opposingCoverer).minus(perceivedTarget).hypotenuse();
        var targetOverextended = targetAllyDistance > 1.92;
        var targetLostCover = !Double.isFinite(opposingEta) ||
                perceivedOpposingEta > impactEta + attacker.recoveryTime() + 0.18;
        var confidence = awareness.visible ? 0.62 +
                (attacker.stats.distanceControl() + attacker.stats.decisionMaking()) * 0.0019 :
                Math.clamp(awareness.memoryTime / 1.8, 0.08, 0.46);
        var laneDirection = new Pos(attacker.weaponWorldAngle());
        var laneOrigin = attacker.pos.plus(laneDirection.times(Musketeer.WEAPON_REST * 0.55));
        var laneEnd = attacker.pos.plus(laneDirection.times(
                attacker.projectedThrustLength() + Musketeer.BODY_RADIUS));
        var observationInterval = 0.10;
        var previousTarget = perceivedTarget.minus(
                awareness.perceivedVelocity.times(observationInterval));
        var predictionHorizon = Math.clamp(impactEta, 0.12, 0.65);
        var predictedTarget = perceivedTarget.plus(
                awareness.perceivedVelocity.times(predictionHorizon));
        var previousDistanceToLane = distanceToSegment(previousTarget, laneOrigin, laneEnd);
        var currentDistanceToLane = distanceToSegment(perceivedTarget, laneOrigin, laneEnd);
        var predictedDistanceToLane = distanceToSegment(predictedTarget, laneOrigin, laneEnd);
        var laneRadius = Musketeer.BODY_RADIUS * 0.58 + 0.03;
        var laneEntrySpeed = (currentDistanceToLane - predictedDistanceToLane) /
                predictionHorizon;
        var timeUntilLaneEntry = laneEntrySpeed <= 0 ? Double.POSITIVE_INFINITY :
                Math.max(0, currentDistanceToLane - laneRadius) / laneEntrySpeed;
        var timeInsideLane = predictedDistanceToLane > laneRadius ? 0 :
                Math.max(0, predictionHorizon - timeUntilLaneEntry);
        var laneContactGain = Math.max(0, currentDistanceToLane - laneRadius) -
                Math.max(0, predictedDistanceToLane - laneRadius);
        var coverAllowsLaneAttack = !Double.isFinite(perceivedOpposingEta) ||
                perceivedOpposingEta >= impactEta + attacker.recoveryTime() - 0.08 ||
                allyCoverQuality >= 0.48;
        var rawMovingIntoLine = previousDistanceToLane >= laneRadius + 0.035 &&
                predictedDistanceToLane <= laneRadius + 0.01 && laneEntrySpeed >= 0.14 &&
                awareness.perceivedVelocity.hypotenuse() >= 0.16 && laneContactGain >= 0.035 &&
                timeInsideLane >= 0.06 &&
                timeUntilLaneEntry <= Math.max(0.08, targetStateWindow - prepareEta + 0.08) &&
                lineSafety >= 0.14 && coverAllowsLaneAttack;
        if (rawMovingIntoLine) {
            attacker.movingIntoLineTarget = target;
            attacker.movingIntoLineReasonUntil = simulationTime + 0.22;
        }
        var retainedMovingIntoLine = attacker.movingIntoLineTarget == target &&
                simulationTime <= attacker.movingIntoLineReasonUntil &&
                predictedDistanceToLane <= laneRadius + 0.08 && laneEntrySpeed >= 0.08 &&
                awareness.perceivedVelocity.hypotenuse() >= 0.10 && laneContactGain >= 0.015 &&
                lineSafety >= 0.14 && coverAllowsLaneAttack;
        var movingIntoLine = rawMovingIntoLine || retainedMovingIntoLine;
        var allyAngle = ally == null ? 0 : Math.abs(angleDifference(
                perceivedTarget.minus(attacker.pos).radians(),
                perceivedTarget.minus(ally.pos).radians()));
        var crossAngle = ally != null && allyAngle >= Math.toRadians(28) && lineSafety >= 0.28;
        AttackOpportunityReason reason;
        if (observedState == CombatState.BLOCK_STUN) reason = AttackOpportunityReason.TARGET_BLOCK_STUNNED;
        else if (observedState == CombatState.RECOVERY) reason = AttackOpportunityReason.TARGET_RECOVERING;
        else if (observedState == CombatState.ATTACK_ACTIVE ||
                observedState == CombatState.CHAMBER_ACTIVE)
            reason = AttackOpportunityReason.TARGET_ATTACK_COMMITTED;
        else if (observedState == CombatState.ATTACK_PREPARE ||
                observedState == CombatState.CHAMBER_PREPARE)
            reason = AttackOpportunityReason.TARGET_ATTACK_PREPARING;
        else if (focusedOnAlly) reason = AttackOpportunityReason.TARGET_FOCUSED_ON_ALLY;
        else if (observedState == CombatState.BLOCK_PREPARE)
            reason = AttackOpportunityReason.TARGET_SWITCHING_GUARD;
        else if ((observedState == CombatState.BLOCKING || observedState == CombatState.DOUBLE_BLOCK) &&
                !awareness.observedBlockDirection.blocks(attacker.attackDirection))
            reason = AttackOpportunityReason.TARGET_WRONG_GUARD;
        else if (observedState == CombatState.BLOCKING || observedState == CombatState.DOUBLE_BLOCK)
            reason = AttackOpportunityReason.TARGET_BLOCK_COMMITTED;
        else if (targetWeaponToAttacker >= Math.toRadians(42))
            reason = AttackOpportunityReason.TARGET_ATTACK_MISALIGNED;
        else if (targetOverextended) reason = AttackOpportunityReason.TARGET_OVEREXTENDED;
        else if (targetLostCover) reason = AttackOpportunityReason.TARGET_LOST_ALLY_COVER;
        else if (movingIntoLine) reason = AttackOpportunityReason.TARGET_MOVING_INTO_LINE;
        else if (crossAngle) reason = AttackOpportunityReason.CROSS_ANGLE_CREATED;
        else if (lineSafety >= 0.55) reason = AttackOpportunityReason.TARGET_LINE_OPEN;
        else reason = AttackOpportunityReason.RISKY_INITIATIVE;
        var availableWindow = Math.max(targetStateWindow,
                focusedOnAlly ? targetReturnEta : 0);
        if (targetLostCover) availableWindow = Math.max(availableWindow,
                perceivedOpposingEta - impactEta);
        if (reason == AttackOpportunityReason.TARGET_LINE_OPEN)
            availableWindow = Math.max(availableWindow, 0.48);
        else if (reason == AttackOpportunityReason.CROSS_ANGLE_CREATED)
            availableWindow = Math.max(availableWindow, 0.43);
        else if (reason == AttackOpportunityReason.TARGET_MOVING_INTO_LINE)
            availableWindow = Math.max(availableWindow, 0.40);
        else if (reason == AttackOpportunityReason.RISKY_INITIATIVE)
            availableWindow = Math.max(availableWindow, 0.34);
        var acceptsDangerousInitiative = attacker.tactic == Tactic.AGGRESSIVE &&
                attacker.stats.aggression() >= 80 || attacker.stats.decisionMaking() < 55 &&
                attacker.stats.aggression() >= 65;
        if (acceptsDangerousInitiative && (reason == AttackOpportunityReason.TARGET_LINE_OPEN ||
                reason == AttackOpportunityReason.CROSS_ANGLE_CREATED))
            reason = AttackOpportunityReason.RISKY_INITIATIVE;
        var targetExposure = Math.clamp(availableWindow / 0.72, 0, 1) * 0.46 +
                Math.clamp(targetWeaponToAttacker / Math.toRadians(90), 0, 1) * 0.28 +
                (targetOverextended ? 0.26 : 0);
        var score = targetExposure * 0.28 + lineSafety * 0.18 + allyCoverQuality * 0.18 +
                (1 - opposingCoverQuality) * 0.14 + (1 - counterAttackRisk) * 0.10 +
                confidence * 0.12 - revengeRisk * 0.24;
        score += attacker.stats.aggression() * 0.0012 +
                attacker.stats.decisionMaking() * 0.0008;
        if (reason == AttackOpportunityReason.RISKY_INITIATIVE)
            score += attacker.stats.aggression() * 0.0018 - 0.14;
        var threshold = 0.48 - attacker.stats.aggression() * 0.0010 -
                Math.max(0, 62 - attacker.stats.decisionMaking()) * 0.0012;
        var rejection = AttackOpportunityRejection.NONE;
        if (distance > attacker.projectedThrustLength() + Musketeer.BODY_RADIUS + 0.30)
            rejection = AttackOpportunityRejection.TARGET_OUT_OF_RANGE;
        else if (lineSafety < 0.14) rejection = AttackOpportunityRejection.LINE_BLOCKED_BY_ALLY;
        else if (confidence < 0.34) rejection = AttackOpportunityRejection.LOW_CONFIDENCE_PERCEPTION;
        else if (alignment > Math.toRadians(58) && alignEta > availableWindow + 0.08)
            rejection = AttackOpportunityRejection.WEAPON_NOT_ALIGNED;
        else if (!acceptsDangerousInitiative && counterAttackEta + 0.05 < impactEta)
            rejection = AttackOpportunityRejection.ATTACKER_ALREADY_THREATENED;
        else if (!acceptsDangerousInitiative && Double.isFinite(perceivedOpposingEta) &&
                perceivedOpposingEta < impactEta + attacker.recoveryTime() - 0.08 &&
                allyCoverQuality < 0.48)
            rejection = AttackOpportunityRejection.OPPOSING_COVER_TOO_FAST;
        else if (!acceptsDangerousInitiative && revengeRisk >= 0.56 && allyCoverQuality +
                attacker.stats.aggression() * 0.0015 < revengeRisk)
            rejection = AttackOpportunityRejection.REVENGE_RISK_TOO_HIGH;
        else if (reason != AttackOpportunityReason.RISKY_INITIATIVE &&
                impactEta > availableWindow + 0.14)
            rejection = AttackOpportunityRejection.TARGET_WINDOW_TOO_SHORT;
        else if (score < threshold) rejection = AttackOpportunityRejection.LOW_DECISION_SCORE;
        var viable = rejection == AttackOpportunityRejection.NONE;
        var targetToAlly = ally == null ? Double.NaN : ally.pos.minus(perceivedTarget).radians();
        var evidence = new AttackOpportunityEvidence(reason, targetBodyToAttacker,
                ally == null ? Double.NaN : Math.abs(angleDifference(
                        awareness.observedBodyAngle, targetToAlly)), targetWeaponToAttacker,
                ally == null ? Double.NaN : Math.abs(angleDifference(
                        awareness.observedWeaponAngle, targetToAlly)), observedState,
                targetStateWindow, laneEntrySpeed, perceivedOpposingEta,
                attacker.recoveryTime(), lineClearance, confidence, previousDistanceToLane,
                predictedDistanceToLane, laneEntrySpeed, timeUntilLaneEntry, timeInsideLane,
                reason == AttackOpportunityReason.TARGET_MOVING_INTO_LINE &&
                        (laneContactGain < 0.06 || timeInsideLane < 0.09 ||
                                confidence < 0.48 || availableWindow < 0.10));
        return new AttackOpportunity(viable, attacker, target, ally, opposingCoverer, reason,
                availableWindow, impactEta, targetExposure, lineSafety, allyCoverQuality,
                opposingCoverQuality, revengeRisk, counterAttackRisk, score, rejection, evidence);
    }

    private double perceivedThreatEta(Musketeer observer, Musketeer threat, Musketeer target) {
        if (threat == null || target == null || !threat.alive || !target.alive ||
                !isTracked(observer, threat)) return Double.POSITIVE_INFINITY;
        var awareness = observer.opponentAwareness.get(threat);
        var threatPosition = known(observer, threat);
        var targetPosition = target.team == observer.team ? target.pos : known(observer, target);
        var distance = targetPosition.minus(threatPosition).hypotenuse();
        var alignment = Math.abs(angleDifference(awareness.observedWeaponAngle,
                targetPosition.minus(threatPosition).radians()));
        var turn = alignment / 3.5;
        var prepare = awareness.observedState == CombatState.ATTACK_HOLD ||
                awareness.observedState == CombatState.CHAMBER_HOLD ? 0 :
                awareness.observedState == CombatState.ATTACK_PREPARE ||
                        awareness.observedState == CombatState.CHAMBER_PREPARE ?
                        Math.max(0, awareness.observedAttackDirection.prepareTime -
                                awareness.observedStateTime) :
                        awareness.observedAttackDirection.prepareTime;
        var contact = Math.max(0, distance - awareness.observedProjectedRange -
                Musketeer.BODY_RADIUS) / 1.42;
        return turn + prepare + contact;
    }

    private TeamAttackArbitration arbitrateTeamAttack(GroupEngagement group,
                                                       Musketeer requestedAttacker,
                                                       AttackOpportunity requested) {
        if (group.committedAttacker != null && group.committedAttacker.alive &&
                group.committedAttacker != requestedAttacker)
            return new TeamAttackArbitration(group.committedAttacker, requestedAttacker,
                    group.committedAttacker.attackOpportunity, false,
                    "ally_already_committed");
        var ally = teammateOf(requestedAttacker);
        var allyOpportunity = ally == null || !ally.alive || ally.decisionTime > 0 ||
                ally.primaryMark == null || ally.state != CombatState.NEUTRAL && !ally.isGuarding() ? null :
                assessAttackOpportunity(ally, ally.primaryMark);
        var selected = requested;
        var selectedAttacker = requestedAttacker;
        var supporting = ally;
        var reason = "requested_opening_is_best";
        if (allyOpportunity != null && allyOpportunity.viable() &&
                (!requested.viable() || arbitrationValue(allyOpportunity) >
                        arbitrationValue(requested) + 0.06)) {
            selected = allyOpportunity;
            selectedAttacker = ally;
            supporting = requestedAttacker;
            reason = "ally_has_shorter_safer_window";
        }
        var arbitration = new TeamAttackArbitration(selectedAttacker, supporting,
                selected, false, reason);
        if (requestedAttacker.team == Team.BLUE) group.blueAttackArbitration = arbitration;
        else group.redAttackArbitration = arbitration;
        return arbitration;
    }

    private double arbitrationValue(AttackOpportunity opportunity) {
        var urgency = Math.clamp((0.75 - opportunity.availableWindow()) / 0.75, 0, 1);
        return opportunity.score() + urgency * 0.12 + opportunity.lineSafety() * 0.10 +
                opportunity.allyCoverQuality() * 0.10 - opportunity.revengeRisk() * 0.16;
    }

    private void recordAttackOpportunity(AttackOpportunity opportunity) {
        if (opportunity.primaryReason() != null) {
            modernMetrics.attackOpportunitiesByReason.merge(
                    opportunity.primaryReason(), 1, Integer::sum);
            if (opportunity.primaryReason() == AttackOpportunityReason.TARGET_MOVING_INTO_LINE) {
                ++modernMetrics.movingIntoLineOpportunities;
                if (opportunity.evidence().movingIntoLineLowEvidence())
                    ++modernMetrics.movingIntoLineLowEvidence;
            }
        }
    }

    private void recordAcceptedAttackOpportunity(AttackOpportunity opportunity) {
        modernMetrics.attackOpportunitiesAcceptedByReason.merge(
                opportunity.primaryReason(), 1, Integer::sum);
        if (opportunity.primaryReason() == AttackOpportunityReason.TARGET_MOVING_INTO_LINE)
            ++modernMetrics.movingIntoLineAccepted;
        modernMetrics.attackWindowSum += opportunity.availableWindow();
        modernMetrics.attackImpactEtaSum += opportunity.impactEta();
        ++modernMetrics.acceptedAttackOpportunitySamples;
    }

    private void recordAttackOpportunityRejection(AttackOpportunityReason reason,
                                                   AttackOpportunityRejection rejection) {
        if (reason != null) {
            modernMetrics.attackOpportunitiesRejectedByReason.merge(reason, 1, Integer::sum);
            if (reason == AttackOpportunityReason.TARGET_MOVING_INTO_LINE)
                ++modernMetrics.movingIntoLineRejected;
        }
        modernMetrics.attackOpportunityRejections.merge(rejection, 1, Integer::sum);
    }

    private CommitAssessment assessCommit(Musketeer attacker, Musketeer target,
                                          double distance) {
        var idealDistance = attacker.projectedThrustLength() + Musketeer.BODY_RADIUS - 0.10;
        var distanceQuality = Math.clamp(1 - Math.abs(distance - idealDistance) / 0.78, 0, 1);
        var alignment = Math.abs(angleDifference(attacker.weaponWorldAngle(),
                target.pos.minus(attacker.pos).radians()));
        var alignmentQuality = Math.clamp(1 - alignment / Math.toRadians(48), 0, 1);
        var hitOpportunity = distanceQuality * 0.58 + alignmentQuality * 0.42;
        var blockRisk = target.isGuarding() ?
                (target.blockDirection.blocks(attacker.attackDirection) ? 0.90 : 0.52) :
                Math.clamp((target.stats.defenseTiming() + target.stats.reaction()) / 230.0,
                        0.16, 0.78);
        var enemyPartner = teammateOf(target);
        var revengeCover = enemyPartner == null ? null :
                assessCover(target, enemyPartner, attacker);
        var revengeRisk = revengeCover == null ? 0 :
                Math.clamp(revengeCover.score() *
                        (0.62 + attacker.recoveryTime() * 0.48), 0, 1);
        var ally = teammateOf(attacker);
        var ownCover = ally == null ? null : assessCover(attacker, ally, target);
        var allyCoverQuality = ownCover == null ? 0 : ownCover.score();
        var recoverySafety = Math.clamp(0.56 + allyCoverQuality * 0.62 -
                revengeRisk * 0.74, 0, 1);
        var skill = (attacker.stats.decisionMaking() +
                attacker.stats.teamworkAbility()) / 200.0;
        var aggression = attacker.stats.aggression() / 100.0;
        var score = Math.clamp(hitOpportunity * 0.42 + recoverySafety * 0.24 +
                allyCoverQuality * 0.20 + aggression * 0.14 -
                blockRisk * 0.18 - revengeRisk * (0.36 - aggression * 0.14), 0, 1);
        var threshold = 0.43 - aggression * 0.10 - (skill - 0.5) * 0.08;
        var rejection = "";
        if (distance > attacker.projectedThrustLength() + Musketeer.BODY_RADIUS + 0.26)
            rejection = "outside_commit_range";
        else if (revengeRisk >= 0.42 &&
                allyCoverQuality + aggression * 0.18 < revengeRisk + 0.08)
            rejection = "revenge_risk";
        else if (score < threshold) rejection = "commit_score";
        return new CommitAssessment(rejection.isEmpty(), hitOpportunity, blockRisk,
                revengeRisk, allyCoverQuality, recoverySafety, score, rejection);
    }

    private AttackIntent chooseAttackIntent(Musketeer self, Musketeer enemy, double distance) {
        if (enemy == null || !enemy.alive) return AttackIntent.HOLD;
        if (distance > self.projectedThrustLength() + Musketeer.BODY_RADIUS + 0.20) return AttackIntent.HOLD;
        var idealDistance = self.projectedThrustLength() + Musketeer.BODY_RADIUS - 0.12;
        var distanceQuality = Math.clamp(1 - Math.abs(distance - idealDistance) / 0.62, 0, 1);
        var facingQuality = Math.clamp(1 - Math.abs(angleDifference(self.weaponWorldAngle(),
                enemy.pos.minus(self.pos).radians())) / Math.toRadians(42), 0, 1);
        var directScore = 0.35 + distanceQuality * 1.50 + facingQuality * 0.65 +
                self.stats.attackTiming() * 0.008 + self.stats.aggression() * 0.004;
        var holdScore = 0.30 + self.stats.pressureAbility() * 0.009 +
                self.stats.decisionMaking() * 0.005 + self.stats.distanceControl() * 0.004;
        if (enemy.state == CombatState.RECOVERY && distanceQuality >= 0.45) directScore += 0.85;
        else if (enemy.state == CombatState.BLOCK_STUN) directScore += 1.10;
        if (enemy.isGuarding()) {
            holdScore += 0.68;
            directScore -= enemy.blockDirection.blocks(self.attackDirection) ? 0.92 : 0.24;
        }
        if (distance > self.projectedThrustLength() + Musketeer.BODY_RADIUS + 0.05) {
            holdScore += 0.48;
            directScore -= 0.42;
        }
        if (self.dezoneMessage.getTime() > 0) holdScore += 1.0;
        if (self.tactic == Tactic.AGGRESSIVE) directScore += 0.24;
        else if (self.tactic == Tactic.DEFENSIVE) holdScore += 0.20;
        directScore -= enemy.stats.chamber() / 100.0 * 0.42;
        for (var opponent : opponentsOf(self)) {
            if (opponent == enemy || !opponent.alive || !isVisible(self, opponent)) continue;
            var offset = opponent.pos.minus(self.pos);
            if (offset.hypotenuse() <= self.projectedThrustLength() + Musketeer.BODY_RADIUS + 0.45 &&
                    Math.abs(angleDifference(self.weaponWorldAngle(), offset.radians())) <= Math.toRadians(105)) {
                holdScore += 0.55;
                break;
            }
        }
        var noise = random.nextGaussian() * (101 - self.stats.decisionMaking()) * 0.010;
        return directScore + noise >= holdScore ? AttackIntent.DIRECT_RELEASE : AttackIntent.HOLD;
    }

    private CombatDirection chooseAttackDirection(Musketeer self, Musketeer enemy, double distance) {
        var upScore = (distance - 1.18) * 1.15;
        var downScore = (1.82 - distance) * 0.95;
        var awareness = enemy == null ? null : self.opponentAwareness.get(enemy);
        if (enemy != null && isVisible(self, enemy) && enemy.isGuarding()) {
            if (enemy.blockDirection == BlockDirection.UP) downScore += 1.05;
            else if (enemy.blockDirection == BlockDirection.DOWN) upScore += 1.05;
        }
        if (awareness != null) {
            var observedBlocks = awareness.observedUpBlocks + awareness.observedDownBlocks;
            if (observedBlocks > 0) {
                upScore += awareness.observedDownBlocks / (double) observedBlocks * 0.72;
                downScore += awareness.observedUpBlocks / (double) observedBlocks * 0.72;
            }
        }
        if (self.lastCommittedAttackDirection != null && self.directionStreak >= 2) {
            var mixup = self.stats.decisionMaking() / 100.0 * Math.min(1.0, self.directionStreak / 3.0);
            if (self.lastCommittedAttackDirection == CombatDirection.UP) upScore -= mixup;
            else downScore -= mixup;
        }
        var decisionNoise = random.nextGaussian() * (101 - self.stats.decisionMaking()) * 0.012;
        return upScore + decisionNoise >= downScore ? CombatDirection.UP : CombatDirection.DOWN;
    }

    private void configureAim(Musketeer self, double distance) {
        self.attackReachScale = 1;
        self.baitedAttack = false;
        self.punishingBait = false;
        self.exploitingBait = false;
        var accurate = operationSucceeds(self.stats.aim());
        self.intendedZone = self.attackDirection == CombatDirection.UP &&
                random.nextInt(100) < 12 + self.stats.aim() / 5 ? HitZone.HEAD : HitZone.TORSO;
        self.attackHeight = self.intendedZone.centerHeight() +
                random.nextGaussian() * (0.025 + (100 - self.stats.aim()) * 0.0045);
        if (accurate) self.aimError = random.nextGaussian() * (101 - self.stats.aim()) * 0.0009;
        else if (random.nextBoolean()) {
            self.attackHeight -= 0.45 + random.nextDouble() * 0.75;
            self.aimError = random.nextGaussian() * 0.10;
        } else {
            self.attackHeight += random.nextGaussian() * 0.55;
            self.aimError = (random.nextBoolean() ? 1 : -1) * (0.22 + random.nextDouble() * 0.38);
        }
        self.desiredPitch = self.pitchFor(self.attackHeight, distance);
        configureAttackManipulation(self, distance);
        self.attackResolved = false;
    }

    private void configureAttackManipulation(Musketeer self, double distance) {
        if (!features.attackManipulation()) {
            self.attackManipulation = AttackManipulation.NEUTRAL;
            self.manipulationPitchOffset = 0;
            self.manipulationYawOffset = 0;
            return;
        }
        var ability = self.stats.manipulationAbility();
        var chance = Math.min(0.72, 0.08 + ability * 0.0062);
        if (random.nextDouble() >= chance) {
            self.attackManipulation = AttackManipulation.NEUTRAL;
            self.manipulationPitchOffset = 0;
            self.manipulationYawOffset = 0;
            return;
        }
        var accelBias = distance < 1.50 ? 0.64 : 0.42;
        if (self.tactic == Tactic.AGGRESSIVE) accelBias += 0.14;
        else if (self.tactic == Tactic.DEFENSIVE) accelBias -= 0.12;
        self.attackManipulation = random.nextDouble() < accelBias ?
                AttackManipulation.ACCEL : AttackManipulation.DRAG;
        var degrees = 7 + ability * 0.13;
        self.manipulationPitchOffset = Math.toRadians(
                self.attackManipulation == AttackManipulation.ACCEL ? -degrees : degrees);
        self.manipulationYawOffset = self.engagementSide * Math.toRadians(4 + ability * 0.075) *
                (self.attackManipulation == AttackManipulation.ACCEL ? -1 : 1);
    }

    private void beginBlock(Musketeer self, Musketeer enemy) {
        var chamberBonus = enemy.chamberType == ChamberType.SIMPLE && enemy.chamberChain > 0 ?
                enemy.chamberChain * CHAMBER_BLOCK_BONUS : 0;
        beginBlock(self, enemy.attackDirection, chamberBonus);
        traceDecision(self, DecisionCategory.BLOCK, "BLOCK_" + self.blockDirection,
                "immediate_weapon_threat", enemy, null,
                self.stats.defenseTiming() / 100.0, 0, self.guardPrepareTime, 0, "NONE");
    }

    private void beginObservedBlock(Musketeer self, Musketeer enemy) {
        var awareness = self.opponentAwareness.get(enemy);
        beginBlock(self, awareness == null ? enemy.attackDirection : awareness.observedAttackDirection, 0);
        traceDecision(self, DecisionCategory.BLOCK, "BLOCK_" + self.blockDirection,
                "observed_weapon_threat", enemy, null,
                self.stats.defenseTiming() / 100.0, 0, self.guardPrepareTime, 0, "NONE");
    }

    private void beginBlock(Musketeer self, CombatDirection perceivedDirection, int chamberBonus) {
        stopBait(self);
        if (self.dezoneMessage.getTime() > 0) self.dezoneStance = DezoneStance.BLOCK;
        self.decision = Decision.BLOCK;
        var blockSkill = Math.min(100, self.stats.defenseTiming() + chamberBonus);
        BlockDirection direction;
        if (operationSucceeds(blockSkill)) direction = BlockDirection.matching(perceivedDirection);
        else if (perceivedDirection == CombatDirection.UP)
            direction = random.nextBoolean() ? BlockDirection.DOWN : BlockDirection.SIDE;
        else direction = random.nextBoolean() ? BlockDirection.UP : BlockDirection.SIDE;
        prepareGuard(self, direction, false);
    }

    private void beginAnticipatoryBlock(Musketeer self, Musketeer enemy) {
        stopBait(self);
        var awareness = self.opponentAwareness.get(enemy);
        CombatDirection predicted;
        if (awareness == null || awareness.observedUpAttacks == awareness.observedDownAttacks)
            predicted = enemy.attackDirection;
        else predicted = awareness.observedUpAttacks > awareness.observedDownAttacks ?
                CombatDirection.UP : CombatDirection.DOWN;
        self.decision = Decision.BLOCK;
        prepareGuard(self, BlockDirection.matching(predicted), false);
        traceDecision(self, DecisionCategory.BLOCK, "ANTICIPATE_" + self.blockDirection,
                "attack_pattern", enemy, null, self.stats.defenseTiming() / 100.0,
                0, self.guardPrepareTime, 0, "NONE");
    }

    private void prepareGuard(Musketeer self, BlockDirection direction, boolean switching) {
        self.blockDirection = direction;
        self.guardSwitching = switching;
        self.guardPrepareTime = 0.035 + (100 - self.stats.defenseTiming()) * 0.00115;
        self.guardHoldTime = 0.28 + self.stats.defenseTiming() * 0.006 + random.nextDouble() * 0.18 +
                (self.tactic == Tactic.DEFENSIVE ? 0.25 : self.tactic == Tactic.AGGRESSIVE ? -0.08 : 0);
        ++self.blockSequence;
        setState(self, CombatState.BLOCK_PREPARE);
    }

    private void switchGuard(Musketeer self, CombatDirection attackDirection) {
        self.decision = Decision.SWITCH_GUARD;
        self.guardSwitchCooldown = 0.16 + (100 - self.stats.reaction()) * 0.0016;
        prepareGuard(self, BlockDirection.matching(attackDirection), true);
    }

    private boolean shouldFeintToBlock(Musketeer self, Musketeer enemy, boolean enemyThreat, double distance) {
        if (!enemyThreat || self.feintCooldown > 0 || self.feintCount >= 2 || self.stateTime < 0.08 ||
                distance > enemy.projectedThrustLength() + Musketeer.BODY_RADIUS + 0.35) return false;
        var defense = (self.stats.defenseTiming() + self.stats.reaction() + self.stats.decisionMaking()) / 3.0;
        return random.nextDouble() < 0.04 + defense * 0.0015;
    }

    private boolean shouldChangeAttack(Musketeer self, Musketeer enemy) {
        if (self.feintCooldown > 0 || self.feintCount >= 2 || self.stateTime < 0.10 || enemy == null ||
                !isVisible(self, enemy)) return false;
        var guardReadsAttack = enemy.isGuarding() && enemy.blockDirection.blocks(self.attackDirection);
        var repetition = self.directionStreak >= 2;
        if (!guardReadsAttack && !repetition) return false;
        var chance = 0.025 + self.stats.feintAbility() * 0.00155;
        if (guardReadsAttack) chance += 0.07;
        if (self.tactic == Tactic.AGGRESSIVE) chance *= 1.20;
        else if (self.tactic == Tactic.DEFENSIVE) chance *= 0.75;
        return random.nextDouble() < chance;
    }

    private void chooseOffAxisHoldAction(Musketeer self, boolean withinReleaseDistance) {
        var realignScore = self.stats.aim() + self.stats.decisionMaking();
        var releaseScore = withinReleaseDistance ? self.stats.aggression() + self.stats.attackTiming() :
                Double.NEGATIVE_INFINITY;
        var cancelScore = self.stats.defenseTiming() + self.stats.reaction();
        if (self.rearThreat != null && self.rearThreat.alive) cancelScore += 100;
        if (self.feintCount >= 2) cancelScore += 45;
        if (self.tactic == Tactic.AGGRESSIVE) releaseScore += 30;
        else if (self.tactic == Tactic.DEFENSIVE) cancelScore += 30;
        if (realignScore >= releaseScore && realignScore >= cancelScore) {
            self.decision = Decision.REALIGN_HOLD;
            var correction = Math.toRadians(2 + self.stats.aim() * 0.05);
            self.aimError -= Math.clamp(self.aimError, -correction, correction);
        } else if (releaseScore >= cancelScore) {
            self.decision = Decision.RELEASE_IMPERFECT;
            startAttack(self, CombatState.ATTACK_ACTIVE);
        } else beginHoldCancel(self);
    }

    private void beginFeint(Musketeer self, boolean toBlock) {
        self.feintToBlock = toBlock;
        self.feintCancel = false;
        prepareFeint(self, toBlock ? Decision.FEINT_TO_BLOCK : Decision.FEINT_ATTACK);
    }

    private void beginHoldCancel(Musketeer self) {
        self.feintToBlock = false;
        self.feintCancel = true;
        prepareFeint(self, Decision.CANCEL_HOLD);
    }

    private void prepareFeint(Musketeer self, Decision decision) {
        ++self.feintCount;
        self.queuedAttackDirection = self.attackDirection.opposite();
        self.feintDuration = 0.07 + (100 - self.stats.feintAbility()) * 0.00125;
        self.feintCooldown = 0.34 + random.nextDouble() * 0.22;
        self.attackManipulation = AttackManipulation.NEUTRAL;
        self.manipulationPitchOffset = 0;
        self.manipulationYawOffset = 0;
        self.decision = decision;
        setState(self, CombatState.FEINT);
    }

    private void beginChamberAttempt(Musketeer self, Musketeer enemy) {
        stopBait(self);
        endDezone(self);
        self.attackDirection = enemy.attackDirection;
        self.chamberType = features.perfectChamber() && self.stats.chamber() > 90 &&
                random.nextInt(100) < self.stats.chamber() - 90 ?
                ChamberType.PERFECT : ChamberType.SIMPLE;
        var timingSkill = self.stats.chamber() * 0.50 + self.stats.attackTiming() * 0.20 +
                self.stats.reaction() * 0.15 + self.stats.decisionMaking() * 0.15;
        self.chamberTimingError = random.nextGaussian() * Math.max(0.012, 0.060 - 0.00045 * timingSkill);
        self.attackPitch = self.pitch;
        self.baseAttackPitch = self.pitch;
        self.attackManipulation = AttackManipulation.NEUTRAL;
        self.attackResolved = false;
        self.decision = Decision.CHAMBER;
        setState(self, CombatState.CHAMBER_PREPARE);
    }

    private void beginChamberReturn(Musketeer defender, Musketeer attacker) {
        var nextChain = attacker.chamberChain + 1;
        var distance = Math.hypot(attacker.getX() - defender.getX(), attacker.getY() - defender.getY());
        stopBait(defender);
        endDezone(defender);
        defender.attackDirection = attacker.attackDirection;
        defender.attackReachScale = 1;
        defender.baitedAttack = false;
        defender.punishingBait = false;
        defender.exploitingBait = false;
        defender.simpleChamberLethal = false;
        defender.chamberChain = nextChain;
        defender.chamberMessageType = defender.chamberType;
        defender.chamberMessageChain = nextChain;
        defender.chamberMessage.setTime(1.1);
        defender.decision = Decision.CHAMBER;
        defender.attackResolved = false;

        if (defender.chamberType == ChamberType.PERFECT) configureCertainTorsoAim(defender, distance);
        else {
            defender.intendedZone = HitZone.TORSO;
            defender.attackHeight = HitZone.TORSO.centerHeight() + random.nextGaussian() *
                    (0.06 + (100 - defender.stats.footwork()) * 0.002);
            defender.aimError = random.nextGaussian() *
                    (0.05 + (100 - defender.stats.footwork()) * 0.002);
            defender.desiredPitch = defender.pitchFor(defender.attackHeight, distance);
        }
        defender.attackPitch = defender.pitch;

        attacker.decision = Decision.WAIT;
        if (defender.chamberType == ChamberType.PERFECT) {
            setState(attacker, CombatState.BLOCK_STUN);
            startAttack(defender, CombatState.CHAMBER_ACTIVE);
        } else {
            var idealDistance = defender.projectedThrustLength() + Musketeer.BODY_RADIUS - 0.10;
            var distanceQuality = Math.max(0, 1 - Math.abs(distance - idealDistance) / 0.45);
            var facingQuality = Math.max(0, 1 - Math.abs(angleDifference(defender.weaponWorldAngle(),
                    Math.atan2(attacker.getY() - defender.getY(), attacker.getX() - defender.getX()))) / 0.40);
            var returnQuality = defender.stats.chamber() * 0.55 + defender.stats.footwork() * 0.45;
            defender.simpleChamberLethal = random.nextDouble() <
                    returnQuality / 500.0 * distanceQuality * facingQuality;
            setState(attacker, CombatState.NEUTRAL);
            attacker.decisionTime = 0;
            attacker.threatSeen = false;
            setState(defender, CombatState.CHAMBER_HOLD);
        }
    }

    private void beginBait(Musketeer self) {
        ++self.baitSequence;
        self.baitOffset = (random.nextBoolean() ? 1 : -1) * Math.toRadians(22 + random.nextDouble() * 18);
        self.baitMessage.setTime(0.35 + random.nextDouble() * 0.40);
        self.decision = self.baitOffset < 0 ? Decision.BAIT_LEFT : Decision.BAIT_RIGHT;
    }

    private void stopBait(Musketeer self) {
        self.baitMessage.setTime();
        self.baitOffset = 0;
    }

    private void beginBaitedAttack(Musketeer self, Musketeer baiter, double distance) {
        stopBait(self);
        stopBait(baiter);
        endDezone(self);
        self.attackDirection = CombatDirection.UP;
        configureCertainTorsoAim(self, distance);
        self.attackReachScale = 0.62;
        self.baitedAttack = true;
        self.baitTrapMessage.setTime(1.35);
        self.decision = Decision.FALL_FOR_BAIT;
        self.attackPitch = self.pitch;
        self.attackIntent = AttackIntent.DIRECT_RELEASE;
        setState(self, CombatState.ATTACK_PREPARE);
    }

    private void beginBaitPunish(Musketeer self, double distance) {
        stopBait(self);
        endDezone(self);
        self.attackDirection = CombatDirection.UP;
        configureCertainTorsoAim(self, distance);
        self.punishingBait = true;
        self.punishMessage.setTime(1.35);
        self.decision = Decision.PUNISH_BAIT;
        self.attackPitch = self.pitch;
        self.attackIntent = AttackIntent.HOLD;
        setState(self, CombatState.ATTACK_PREPARE);
    }

    private void beginBaitAdvanceAttack(Musketeer self, Musketeer baiter, double distance) {
        stopBait(self);
        stopBait(baiter);
        endDezone(self);
        self.attackDirection = CombatDirection.DOWN;
        configureCertainTorsoAim(self, distance);
        self.exploitingBait = true;
        self.baitAdvanceMessage.setTime(1.35);
        self.decision = Decision.EXPLOIT_BAIT;
        self.attackPitch = self.pitch;
        self.attackIntent = AttackIntent.HOLD;
        setState(self, CombatState.ATTACK_PREPARE);
    }

    private void configureCertainTorsoAim(Musketeer self, double distance) {
        self.intendedZone = HitZone.TORSO;
        self.attackHeight = HitZone.TORSO.centerHeight();
        self.aimError = 0;
        self.desiredPitch = self.pitchFor(self.attackHeight, distance);
        self.attackManipulation = AttackManipulation.NEUTRAL;
        self.manipulationPitchOffset = 0;
        self.manipulationYawOffset = 0;
        self.attackResolved = false;
    }

    private void startAttack(Musketeer self, CombatState state) {
        if (state == CombatState.ATTACK_ACTIVE && ordinaryDirectTargetEscaped(self)) {
            self.attackIntent = AttackIntent.HOLD;
            self.decision = Decision.APPROACH;
            setState(self, CombatState.ATTACK_HOLD);
            return;
        }
        self.attackPitch = self.pitch;
        self.baseAttackPitch = self.pitch;
        if (state == CombatState.ATTACK_ACTIVE || state == CombatState.CHAMBER_ACTIVE) {
            if (self.attackDirection == self.lastCommittedAttackDirection) ++self.directionStreak;
            else self.directionStreak = 1;
            self.lastCommittedAttackDirection = self.attackDirection;
            if (self.engagementPhase == EngagementPhase.PROBE)
                beginEngagementPhase(self, EngagementPhase.COMMIT);
        }
        setState(self, state);
    }

    private boolean ordinaryDirectTargetEscaped(Musketeer self) {
        var target = self.target;
        return self.attackIntent == AttackIntent.DIRECT_RELEASE && !self.baitedAttack && !self.freeKillAttempt &&
                target != null && target.alive && target.pos.minus(self.pos).hypotenuse() >
                self.projectedThrustLength() + Musketeer.BODY_RADIUS + 0.45;
    }

    private boolean operationSucceeds(int skill) {
        return random.nextDouble() < operationAccuracy(skill);
    }

    double operationAccuracy(int skill) {
        skill = Math.clamp(skill, 1, 100);
        if (skill < 55) return 0.25 + (skill - 1) / 53.0 * 0.41;
        if (skill < 80) return 0.68 + (skill - 55) / 24.0 * 0.19;
        if (skill < 90) return 0.89 + (skill - 80) / 9.0 * 0.06;
        return 0.965 + (skill - 90) / 10.0 * 0.032;
    }

    private Decision randomStrafe(Musketeer self) {
        return collectiveMovementSide(self, random.nextBoolean() ? -1 : 1) < 0 ?
                Decision.STRAFE_LEFT : Decision.STRAFE_RIGHT;
    }

    private void moveAndTurn(Musketeer self, Musketeer enemy, double dt) {
        if (self.state == CombatState.BLOCK_STUN) {
            self.velocity = new Pos();
            keepInArena(self);
            return;
        }
        var observedEnemy = self.state == CombatState.DOUBLE_BLOCK && self.doubleBlockThreat != null &&
                self.doubleBlockThreat.alive ? self.doubleBlockThreat :
                self.target != null && self.target.alive && (self.pressureMode || self.pressureCounter ||
                        self.pressureRole != PressureRole.NONE || self.forcedPressureSource != null) ? self.target : enemy;
        var enemyPos = known(self, observedEnemy);
        if (self.engagementPhase == EngagementPhase.COMMIT && isVisible(self, observedEnemy)) {
            var leadTime = 0.06 + self.stats.decisionMaking() * 0.0014;
            enemyPos = enemyPos.plus(observedEnemy.velocity.times(leadTime));
        }
        var distance = enemyPos.minus(self.pos).hypotenuse();
        if (self.dezoneSwitchTarget == null && self.dezoneMovement == DezoneMovement.BACKWARD &&
                distance >= DEZONE_BACKWARD_LIMIT) {
            self.dezoneMovement = random.nextBoolean() ?
                    DezoneMovement.ADVANCE_LEFT : DezoneMovement.ADVANCE_RIGHT;
            setDezoneDecision(self);
        }
        if (self.isGuarding())
            self.desiredPitch = switch (self.blockDirection) {
                case UP -> Math.toRadians(24);
                case DOWN -> Math.toRadians(-20);
                case SIDE -> 0;
            };
        else if (self.state == CombatState.RECOVERY) self.desiredPitch = self.attackPitch;
        else if (self.state == CombatState.ATTACK_PREPARE || self.state == CombatState.ATTACK_HOLD ||
                self.state == CombatState.CHAMBER_PREPARE || self.state == CombatState.CHAMBER_HOLD ||
                self.isAttacking())
            self.desiredPitch = self.pitchFor(self.attackHeight, distance);
        else self.desiredPitch = self.pitchFor(HitZone.TORSO.centerHeight(), distance);
        var pitchSpeed = 1.2 + self.stats.footwork() * 0.012;
        self.pitch += Math.clamp(self.desiredPitch - self.pitch, -pitchSpeed * dt, pitchSpeed * dt);
        self.pitch = Math.clamp(self.pitch, -Musketeer.MAX_PITCH, Musketeer.MAX_PITCH);
        updateAttackManipulation(self, dt);

        var enemyAngle = Math.atan2(enemyPos.y() - self.getY(), enemyPos.x() - self.getX());
        updateMovementIntent(self, observedEnemy, dt);
        var speed = 1.05 + self.stats.footwork() * 0.006;
        if (self.isAttacking()) speed *= 0.86;
        else if (self.state == CombatState.RECOVERY) speed *= 0.92;
        else if (self.state == CombatState.FEINT) speed *= 0.84;
        else if (self.isGuarding()) speed *= 0.78;
        var desiredVelocity = steeringVelocity(self, observedEnemy, enemyPos, speed);
        var targetAngle = bodyFacing(self, observedEnemy, enemyAngle, desiredVelocity);
        if (self.threatResponse == ThreatResponse.DEFENSIVE_ATTENTION && self.tacticalThreat != null &&
                self.tacticalThreat.alive && isTracked(self, self.tacticalThreat)) {
            var threatAngle = known(self, self.tacticalThreat).minus(self.pos).radians();
            var attentionBlend = Math.clamp((self.threatAssessment.score() - THREAT_ATTENTION_THRESHOLD) /
                    (THREAT_ENGAGE_THRESHOLD - THREAT_ATTENTION_THRESHOLD), 0.25, 0.72);
            targetAngle += angleDifference(targetAngle, threatAngle) * attentionBlend;
        }
        var turnSpeed = 2.4 + self.stats.footwork() * 0.018;
        if (self.isAttacking()) turnSpeed *= 0.62;
        else if (self.state == CombatState.RECOVERY) turnSpeed *= 0.52;
        else if (self.isGuarding()) turnSpeed *= 0.68;
        self.angle += Math.clamp(angleDifference(self.angle, targetAngle), -turnSpeed * dt, turnSpeed * dt);
        self.angle = normalizeAngle(self.angle);

        var weaponTargetAngle = enemyAngle + self.aimError + (self.baitMessage.getTime() > 0 ? self.baitOffset : 0);
        if (self.isGuarding()) weaponTargetAngle = enemyAngle;
        else if (self.isAttacking()) weaponTargetAngle += self.manipulationYawOffset * manipulationCurve(self);
        if (self.threatResponse == ThreatResponse.DEFENSIVE_ATTENTION && self.tacticalThreat != null &&
                self.tacticalThreat.alive && isTracked(self, self.tacticalThreat)) {
            var threatAngle = known(self, self.tacticalThreat).minus(self.pos).radians();
            weaponTargetAngle += angleDifference(weaponTargetAngle, threatAngle) * 0.62;
        }
        self.desiredWeaponYaw = Math.clamp(angleDifference(self.angle, weaponTargetAngle),
                -Musketeer.MAX_WEAPON_YAW, Musketeer.MAX_WEAPON_YAW);
        var weaponTurnSpeed = 3.4 + self.stats.footwork() * 0.018;
        if (self.isAttacking()) weaponTurnSpeed *= 0.55;
        else if (self.state == CombatState.RECOVERY) weaponTurnSpeed *= 0.44;
        self.weaponYaw += Math.clamp(self.desiredWeaponYaw - self.weaponYaw,
                -weaponTurnSpeed * dt, weaponTurnSpeed * dt);
        self.weaponYaw = Math.clamp(self.weaponYaw, -Musketeer.MAX_WEAPON_YAW, Musketeer.MAX_WEAPON_YAW);
        self.desiredVelocity = desiredVelocity;
        captureDifferentialStage(DifferentialTickStage.AFTER_STEERING_COMPOSITION, self);
        integrateVelocity(self, desiredVelocity, movementAcceleration(self), dt);
        moveWithVelocity(self, dt);
        keepInArena(self);
    }

    private void updateMovementIntent(Musketeer self, Musketeer enemy, double dt) {
        var intent = self.movementIntent;
        intent.remaining = Math.max(0, intent.remaining - dt);
        var changedTarget = intent.target != enemy;
        var changedUrgentState = intent.state != self.state && urgentMovement(self);
        if (!changedTarget && !changedUrgentState && intent.source == self.decision && intent.remaining > 0) return;
        if (!changedTarget && intent.remaining > 0 && !urgentMovement(self)) return;
        configureMovementIntent(self, enemy);
    }

    private boolean urgentMovement(Musketeer self) {
        if (self.state == CombatState.RECOVERY || self.backEscapeTime > 0) return true;
        return switch (self.decision) {
            case RETREAT, BLOCK, RECENTER, EDGE_ESCAPE_LEFT, EDGE_ESCAPE_RIGHT,
                 RELEASE_ATTACK, RELEASE_IMPERFECT, RIPOSTE, CHAMBER,
                 PROTECT_BACK_LEFT, PROTECT_BACK_RIGHT, PRESSURE_ESCAPE_LEFT,
                 PRESSURE_ESCAPE_RIGHT, PRESSURE_JOIN_TEAM, PRESSURE_RETREAT,
                 DEZONE_SWITCH_ABORT, FREE_KILL_BACKSTAB -> true;
            default -> false;
        };
    }

    private void configureMovementIntent(Musketeer self, Musketeer enemy) {
        var intent = self.movementIntent;
        intent.source = self.decision;
        intent.state = self.state;
        intent.target = enemy;
        intent.side = self.engagementSide == 0 ? 1 : self.engagementSide;
        intent.preferredDistance = self.tactic.holdAdvanceDistance;
        intent.rangeGain = 0.85;
        intent.radialBias = 0;
        intent.orbitWeight = 0;
        intent.momentumWeight = 0;
        intent.cohesionWeight = 0.08 + self.stats.teamworkAbility() * 0.0065;
        intent.threatAvoidance = 0.42;
        intent.facingMovementBlend = 0.28;
        intent.speedFactor = 0.82;
        if (self.state == CombatState.RECOVERY) {
            intent.preferredDistance = 2.25;
            intent.radialBias = -0.35;
            intent.orbitWeight = self.engagementSide * 0.24;
            intent.threatAvoidance = 0.82;
            intent.speedFactor = 0.92;
        } else configureMovementProfile(self, intent);
        var controlError = (101 - self.stats.footwork()) / 100.0;
        intent.radialBias += random.nextGaussian() * (0.025 + controlError * 0.08);
        intent.orbitWeight += random.nextGaussian() * (0.035 + controlError * 0.12);
        var coherence = (self.stats.decisionMaking() + self.stats.footwork()) / 200.0;
        intent.remaining = urgentMovement(self) ? 0.16 + coherence * 0.12 :
                0.34 + coherence * 0.32 + random.nextDouble() * 0.12;
    }

    private void configureMovementProfile(Musketeer self, MovementIntent intent) {
        switch (self.decision) {
            case APPROACH, FRONT_LINE_ADVANCE -> setClosingIntent(intent, 1.48, 0.16, 0, 0.96);
            case PREPARE_DIRECT_ATTACK, RELEASE_ATTACK, RELEASE_IMPERFECT, DEZONE_SWITCH_ATTACK,
                 RIPOSTE, FREE_KILL_BACKSTAB, OPENING_STAB, PUNISH_BAIT, EXPLOIT_BAIT,
                 PRESSURE_TRANSFER, PRESSURE_SPAM, PRESSURE_COUNTER_HIGH, PRESSURE_COUNTER_LOW ->
                    setClosingIntent(intent, 1.18, 0.30, self.engagementSide * 0.08, 1);
            case PREPARE_HOLD_ATTACK, FRONT_LINE_HOLD, HOLD_ATTACK, REALIGN_HOLD,
                 LOCAL_HOLD_TRANSFER, PRESSURE_HOLD, PRESSURE_FEINT ->
                    setClosingIntent(intent, 1.30, 0.04, movementSide(self) * 0.16, 0.78);
            case LOCAL_FIX, PRESSURE_BLOCK ->
                    setClosingIntent(intent, 1.20, -0.02, movementSide(self) * 0.18, 0.72);
            case LOCAL_COVER, RESCUE_TEAMMATE -> {
                setClosingIntent(intent, 1.34, 0.06, movementSide(self) * 0.30, 0.88);
                intent.cohesionWeight = Math.max(intent.cohesionWeight, 0.72);
            }
            case LOCAL_FLANK_LEFT, COMMIT_LEFT, PRESSURE_CLOSE_LEFT ->
                    setClosingIntent(intent, 1.12, 0.14, -0.64, 0.98);
            case LOCAL_FLANK_RIGHT, COMMIT_RIGHT, PRESSURE_CLOSE_RIGHT ->
                    setClosingIntent(intent, 1.12, 0.14, 0.64, 0.98);
            case STRAFE_LEFT, BAIT_LEFT, DEZONE_ADVANCE_LEFT, DEZONE_ORBIT_LEFT,
                 DEZONE_SWITCH_LEFT, PROBE_LEFT, ROTATION_LEFT -> setOrbitIntent(intent, -1);
            case STRAFE_RIGHT, BAIT_RIGHT, DEZONE_ADVANCE_RIGHT, DEZONE_ORBIT_RIGHT,
                 DEZONE_SWITCH_RIGHT, PROBE_RIGHT, ROTATION_RIGHT -> setOrbitIntent(intent, 1);
            case OPENING_ROTATE -> setOrbitIntent(intent, self.rotationSide);
            case PASS_LEFT -> setPassingIntent(intent, -1);
            case PASS_RIGHT -> setPassingIntent(intent, 1);
            case DISENGAGE -> {
                intent.preferredDistance = 2.05;
                intent.radialBias = -0.10;
                intent.orbitWeight = self.engagementSide * 0.20;
                intent.momentumWeight = 0.78;
                intent.facingMovementBlend = 0.58;
                intent.speedFactor = 1;
            }
            case RESET_GUARD -> {
                intent.preferredDistance = 1.82;
                intent.orbitWeight = self.engagementSide * 0.48;
                intent.threatAvoidance = 0.62;
            }
            case RETREAT, BLOCK, DEZONE_BACKWARD, DEZONE_SWITCH_ABORT, PRESSURE_RETREAT -> {
                intent.preferredDistance = 2.02;
                intent.radialBias = -0.18;
                intent.orbitWeight = movementSide(self) * 0.12;
                intent.threatAvoidance = 0.70;
                intent.speedFactor = 0.90;
            }
            case EDGE_ESCAPE_LEFT, PRESSURE_ESCAPE_LEFT -> setEscapeIntent(intent, -1);
            case EDGE_ESCAPE_RIGHT, PRESSURE_ESCAPE_RIGHT -> setEscapeIntent(intent, 1);
            case PROTECT_BACK_LEFT -> setBackProtectionIntent(intent, -1);
            case PROTECT_BACK_RIGHT -> setBackProtectionIntent(intent, 1);
            case CHAMBER -> setClosingIntent(intent, 0.92, self.isAttacking() ? 0.30 : 0, 0, 0.92);
            case PRESSURE_JOIN_TEAM, RECENTER -> {
                intent.preferredDistance = 1.55;
                intent.cohesionWeight = 1;
                intent.threatAvoidance = 0.72;
                intent.facingMovementBlend = 0.38;
                intent.speedFactor = 1;
            }
            case WAIT, FEINT_ATTACK, FEINT_TO_BLOCK, SWITCH_GUARD, DOUBLE_BLOCK,
                 FALL_FOR_BAIT, ACCEPT_FIFTY_FIFTY, REFUSE_FIFTY_FIFTY, CANCEL_HOLD -> {
                intent.orbitWeight = movementSide(self) * 0.10;
                intent.threatAvoidance = 0.52;
                intent.speedFactor = 0.56;
            }
        }
    }

    private void setClosingIntent(MovementIntent intent, double distance, double bias, double orbit,
                                  double speedFactor) {
        intent.preferredDistance = distance;
        intent.radialBias = bias;
        intent.orbitWeight = orbit;
        intent.speedFactor = speedFactor;
    }

    private void setOrbitIntent(MovementIntent intent, int side) {
        intent.side = side;
        intent.preferredDistance = 1.42;
        intent.orbitWeight = side * 0.78;
        intent.cohesionWeight = Math.max(intent.cohesionWeight, 0.30);
        intent.facingMovementBlend = 0.42;
        intent.speedFactor = 0.94;
    }

    private void setPassingIntent(MovementIntent intent, int side) {
        intent.side = side;
        intent.preferredDistance = 0.58;
        intent.radialBias = 0.30;
        intent.orbitWeight = side * 0.52;
        intent.momentumWeight = 0.34;
        intent.cohesionWeight = 0.08;
        intent.facingMovementBlend = 0.64;
        intent.speedFactor = 1;
    }

    private void setEscapeIntent(MovementIntent intent, int side) {
        intent.side = side;
        intent.preferredDistance = 1.88;
        intent.radialBias = -0.12;
        intent.orbitWeight = side * 0.82;
        intent.threatAvoidance = 0.88;
        intent.facingMovementBlend = 0.55;
        intent.speedFactor = 1;
    }

    private void setBackProtectionIntent(MovementIntent intent, int side) {
        setEscapeIntent(intent, side);
        intent.preferredDistance = 1.52;
        intent.radialBias = 0.10;
        intent.threatAvoidance = 1;
    }

    private int movementSide(Musketeer self) {
        return switch (self.decision) {
            case STRAFE_LEFT, BAIT_LEFT, DEZONE_ADVANCE_LEFT, DEZONE_ORBIT_LEFT,
                 DEZONE_SWITCH_LEFT, LOCAL_FLANK_LEFT, PROBE_LEFT, COMMIT_LEFT, PASS_LEFT,
                 ROTATION_LEFT, EDGE_ESCAPE_LEFT, PROTECT_BACK_LEFT, PRESSURE_CLOSE_LEFT,
                 PRESSURE_ESCAPE_LEFT -> -1;
            case STRAFE_RIGHT, BAIT_RIGHT, DEZONE_ADVANCE_RIGHT, DEZONE_ORBIT_RIGHT,
                 DEZONE_SWITCH_RIGHT, LOCAL_FLANK_RIGHT, PROBE_RIGHT, COMMIT_RIGHT, PASS_RIGHT,
                 ROTATION_RIGHT, EDGE_ESCAPE_RIGHT, PROTECT_BACK_RIGHT, PRESSURE_CLOSE_RIGHT,
                 PRESSURE_ESCAPE_RIGHT -> 1;
            default -> self.engagementSide == 0 ? 1 : self.engagementSide;
        };
    }

    private Pos steeringVelocity(Musketeer self, Musketeer enemy, Pos enemyPos, double speed) {
        if (self.engagement != null && self.engagement.type == EngagementType.TWO_VS_ONE &&
                self.backEscapeTime <= 0 && self.state != CombatState.RECOVERY) {
            if (self.tacticalIntent == TacticalIntent.FIX)
                return modernFixerVelocity(self, self.engagement.primaryTarget, speed);
            if (self.tacticalIntent == TacticalIntent.SURVIVE)
                return modernOutnumberedVelocity(self, speed);
        }
        if (scenario == SimulationScenario.MODERN_2V2 &&
                self.tacticalIntent == TacticalIntent.GROUP &&
                self.engagement == modernGroupEngagement && activeModernGroup() != null)
            return modernGroupVelocity(self, activeModernGroup(), speed);
        if (self.tacticalIntent == TacticalIntent.INTERCEPT && self.tacticalDestination != null &&
                self.interceptionPhase != InterceptionPhase.FAILED &&
                self.state != CombatState.RECOVERY && self.backEscapeTime <= 0)
            return interceptionVelocity(self, enemy, speed);
        if (scenario == SimulationScenario.MODERN_2V2 &&
                self.tacticalIntent == TacticalIntent.DUEL &&
                self.engagement != null && self.engagement.type == EngagementType.DUEL)
            return modernDuelVelocity(self, enemy, speed);
        if (self.tacticalIntent == TacticalIntent.FLANK &&
                self.flankPhase != FlankPhase.APPROACH_SIDE &&
                self.flankPhase != FlankPhase.CONTESTED &&
                self.flankPhase != FlankPhase.ABORTED &&
                self.flankPhase != FlankPhase.COMPLETED &&
                self.engagement != null && self.engagement.primaryTarget == enemy)
            return rearFlankVelocity(self, enemy, speed);
        var intent = self.movementIntent;
        var offset = enemyPos.minus(self.pos);
        var distance = offset.hypotenuse();
        var towardEnemy = distance > 0.0001 ? offset.div(distance) : new Pos(self.angle);
        var tangent = new Pos(-towardEnemy.y(), towardEnemy.x());
        var radial = Math.clamp((distance - intent.preferredDistance) * intent.rangeGain +
                intent.radialBias, -1, 1);
        var steering = towardEnemy.times(radial).plus(tangent.times(intent.orbitWeight));
        if (intent.momentumWeight > 0)
            steering = steering.plus(new Pos(self.movementHeading).times(intent.momentumWeight));
        steering = steering.plus(teamSteering(self, enemy).times(intent.cohesionWeight));
        steering = steering.plus(threatSteering(self, enemy).times(intent.threatAvoidance));
        if (self.tacticalIntent == TacticalIntent.FLANK && self.tacticalDestination != null) {
            var flankOffset = self.tacticalDestination.minus(self.pos);
            var flankDistance = flankOffset.hypotenuse();
            if (flankDistance > 0.0001) {
                var flankWeight = Math.clamp(flankDistance / 0.75, 0.22, 1);
                steering = steering.times(0.28).plus(flankOffset.div(flankDistance).times(flankWeight));
            }
        }
        if (self.tacticalIntent == TacticalIntent.FLANK && self.tacticalThreat != null &&
                self.tacticalThreat.alive && isTracked(self, self.tacticalThreat) &&
                self.threatResponse.ordinal() >= ThreatResponse.ADJUST_PATH.ordinal()) {
            var threatOffset = self.pos.minus(known(self, self.tacticalThreat));
            var threatDistance = threatOffset.hypotenuse();
            if (threatDistance > 0.0001) {
                var weight = Math.clamp((self.threatAssessment.score() - THREAT_ADJUST_THRESHOLD) /
                        (THREAT_ENGAGE_THRESHOLD - THREAT_ADJUST_THRESHOLD), 0.16, 0.86);
                var avoid = threatOffset.div(threatDistance);
                var flankPath = self.tacticalDestination == null ? new Pos(self.angle) :
                        self.tacticalDestination.minus(self.pos);
                if (flankPath.hypotenuse() > 0.0001) {
                    var pathDirection = flankPath.div(flankPath.hypotenuse());
                    var side = Math.signum(pathDirection.x() * avoid.y() - pathDirection.y() * avoid.x());
                    avoid = avoid.plus(new Pos(-pathDirection.y(), pathDirection.x()).
                            times(side == 0 ? self.localSide : side).times(0.42));
                }
                steering = steering.plus(avoid.times(weight));
            }
        }
        steering = steering.plus(boundarySteering(self).times(1.15));
        if (self.decision == Decision.RECENTER)
            steering = steering.plus(new Pos(ARENA_WIDTH, ARENA_HEIGHT).div(2).minus(self.pos).times(0.65));
        else if (self.decision == Decision.PRESSURE_JOIN_TEAM) {
            var teammate = teammateOf(self);
            if (teammate != null && teammate.alive) {
                var source = self.pressureSource != null && self.pressureSource.alive ? self.pressureSource : enemy;
                steering = steering.plus(pressureJoinPoint(self, teammate, source).minus(self.pos).times(0.90));
            }
        }
        var magnitude = steering.hypotenuse();
        if (magnitude > 1) steering = steering.div(magnitude);
        var threatSlowdown = self.tacticalIntent == TacticalIntent.FLANK &&
                self.threatResponse.ordinal() >= ThreatResponse.ADJUST_PATH.ordinal() ?
                1 - 0.22 * self.threatAssessment.score() : 1;
        var desired = steering.times(speed * intent.speedFactor * threatSlowdown);
        if (desired.dot(new Pos(self.angle)) < -0.02) desired = desired.times(BACKWARD_SPEED_FACTOR);
        return desired;
    }

    private Pos modernFixerVelocity(Musketeer fixer, Musketeer defender, double speed) {
        var destination = fixer.tacticalDestination == null ?
                defender.pos.plus(new Pos(defender.angle).times(1.42)) : fixer.tacticalDestination;
        var route = destination.minus(fixer.pos);
        var distance = fixer.pos.minus(defender.pos).hypotenuse();
        var towardDefender = defender.pos.minus(fixer.pos);
        towardDefender = towardDefender.hypotenuse() < 0.0001 ? new Pos(fixer.angle) :
                towardDefender.div(towardDefender.hypotenuse());
        var desired = route.hypotenuse() < 0.0001 ? new Pos() :
                route.div(route.hypotenuse()).times(Math.min(speed * 0.88, route.hypotenuse() * 1.35));
        if (distance < 1.18) desired = desired.minus(towardDefender.times((1.18 - distance) * 1.25));
        else if (distance > 1.82) desired = desired.plus(towardDefender.times(
                Math.min(0.48, (distance - 1.82) * 0.52)));
        desired = desired.plus(allyAvoidance(fixer).times(0.72)).
                plus(boundarySteering(fixer).times(1.05));
        if (desired.hypotenuse() > speed * 0.88)
            desired = desired.div(desired.hypotenuse()).times(speed * 0.88);
        if (desired.dot(new Pos(fixer.angle)) < -0.02)
            desired = desired.times(BACKWARD_SPEED_FACTOR);
        return desired;
    }

    private Pos modernOutnumberedVelocity(Musketeer defender, double speed) {
        var attention = defender.multiThreatAttention;
        var focus = attention.focusedThreat;
        if (focus == null || !focus.alive || !isTracked(defender, focus)) return new Pos();
        var focusPosition = known(defender, focus);
        var away = defender.pos.minus(focusPosition);
        away = away.hypotenuse() < 0.0001 ? new Pos(defender.angle + Math.PI) :
                away.div(away.hypotenuse());
        var lateral = new Pos(-away.y(), away.x());
        var side = defender.engagementSide == 0 ? 1 : defender.engagementSide;
        var secondary = attention.secondaryThreat;
        if (secondary != null && secondary.alive && isTracked(defender, secondary)) {
            var leftCandidate = defender.pos.plus(away.times(0.22)).plus(lateral.times(0.55));
            var rightCandidate = defender.pos.plus(away.times(0.22)).minus(lateral.times(0.55));
            side = outnumberedCandidateScore(defender, leftCandidate, focus, secondary) <=
                    outnumberedCandidateScore(defender, rightCandidate, focus, secondary) ? 1 : -1;
        }
        var desired = away.times(0.24).plus(lateral.times(side * 0.64));
        desired = desired.plus(boundarySteering(defender).times(1.25));
        if (desired.hypotenuse() > 1) desired = desired.div(desired.hypotenuse());
        desired = desired.times(speed * (defender.isGuarding() ? 0.72 : 0.82));
        if (desired.dot(new Pos(defender.angle)) < -0.02)
            desired = desired.times(BACKWARD_SPEED_FACTOR);
        return desired;
    }

    private double outnumberedCandidateScore(Musketeer defender, Pos candidate,
                                               Musketeer first, Musketeer second) {
        var firstAngle = known(defender, first).minus(candidate).radians();
        var secondAngle = known(defender, second).minus(candidate).radians();
        var separation = Math.abs(angleDifference(firstAngle, secondAngle));
        return separation - Math.clamp(arenaClearance(candidate), 0, 1.2) * 0.18;
    }

    private Pos modernGroupVelocity(Musketeer self, GroupEngagement group, double speed) {
        var mark = self.primaryMark != null && self.primaryMark.alive ?
                self.primaryMark : closestAliveOpponent(self);
        if (mark == null) return new Pos();
        var perceivedMark = known(self, mark);
        var toMark = perceivedMark.minus(self.pos);
        var markDistance = toMark.hypotenuse();
        var towardMark = markDistance > 0.0001 ? toMark.div(markDistance) : new Pos(self.angle);
        var teamCenter = group.teamCenter(self.team);
        var enemyCenter = group.enemyCenter(self.team);
        var teamForward = enemyCenter.minus(teamCenter);
        teamForward = teamForward.hypotenuse() < 0.0001 ? new Pos(self.angle) :
                teamForward.div(teamForward.hypotenuse());
        var maneuver = group.maneuver(self.team);
        var teamLateral = new Pos(-teamForward.y(), teamForward.x()).times(
                maneuver == null ? 1 : maneuver.directionSign);
        updateLocalManeuver(self, group, mark);
        var localVelocity = localManeuverVelocity(self, speed);
        var localWeaponAvoidance = self.localManeuver != null &&
                (self.localManeuver.type == LocalManeuverType.AVOID_PRIMARY_WEAPON_LINE ||
                        self.localManeuver.type == LocalManeuverType.AVOID_SECONDARY_WEAPON_LINE) ?
                localVelocity : new Pos();
        var ordinaryLocalVelocity = localVelocity.minus(localWeaponAvoidance);
        var composedLocalVelocity = ordinaryLocalVelocity.times(0.58).plus(localWeaponAvoidance);
        var teamManeuverVelocity = teamManeuverVelocity(self, group, teamForward, teamLateral);
        var tangentRequested = teamManeuverVelocity.dot(teamLateral);
        var preferred = self.preferredGroupPosition == null ?
                preferredGroupCoverPosition(self, group) : self.preferredGroupPosition;
        var positionCorrection = preferred.minus(self.pos);
        var formationCorrection = positionCorrection.hypotenuse() <= 0.0001 ? new Pos() :
                positionCorrection.times(Math.min(0.30, 0.24 / positionCorrection.hypotenuse()));
        var partnerCorrection = new Pos();
        var ally = teammateOf(self);
        if (ally != null && ally.alive) {
            var toAlly = ally.pos.minus(self.pos);
            var allyDistance = toAlly.hypotenuse();
            var allyDirection = allyDistance > 0.0001 ? toAlly.div(allyDistance) :
                    new Pos(self.angle + Math.PI);
            var preferredSpacing = preferredPartnerSpacing(self, group);
            if (allyDistance < preferredSpacing - 0.12)
                partnerCorrection = partnerCorrection.minus(allyDirection.times(
                        (preferredSpacing - 0.12 - allyDistance) * 1.35));
            else if (allyDistance > preferredSpacing + 0.12)
                partnerCorrection = partnerCorrection.plus(allyDirection.times(
                        Math.min(0.52, (allyDistance - preferredSpacing - 0.12) * 0.64)));
            var enemyBetween = distanceToSegment(perceivedMark, self.pos, ally.pos);
            if (enemyBetween < Musketeer.BODY_RADIUS * 1.75)
                partnerCorrection = partnerCorrection.plus(allyDirection.times(0.22));
        }
        var weaponAvoidance = perceivedWeaponAvoidance(self).times(0.72);
        var collisionAvoidance = allyAvoidance(self).times(0.78);
        var avoidanceCorrection = collisionAvoidance.plus(weaponAvoidance);
        var boundaryCorrection = boundarySteering(self).times(0.94);
        var intentionalHold = self.localManeuver != null &&
                self.localManeuver.type == LocalManeuverType.HOLD_POSITION &&
                !laneContains(self.primaryWeaponLane, self.pos) &&
                !laneContains(self.secondaryWeaponLane, self.pos) && arenaClearance(self.pos) >= 0.38;
        var desired = intentionalHold ? avoidanceCorrection.plus(boundaryCorrection) :
                composedLocalVelocity.plus(teamManeuverVelocity).plus(formationCorrection).
                        plus(partnerCorrection).plus(avoidanceCorrection).plus(boundaryCorrection);
        var ordinaryRotation = maneuver != null && maneuver.rotating() && !intentionalHold &&
                localWeaponAvoidance.hypotenuse() < 0.01 && weaponAvoidance.hypotenuse() < 0.08 &&
                collisionAvoidance.hypotenuse() < 0.16 && boundaryCorrection.hypotenuse() < 0.08;
        if (ordinaryRotation) {
            var lateralProjection = desired.dot(teamLateral);
            var targetProjection = self == maneuver.outerPlayer ? 0.62 : 0.42;
            if (self == maneuver.outerPlayer && lateralProjection < targetProjection ||
                    self == maneuver.innerPlayer && lateralProjection > targetProjection)
                desired = desired.plus(teamLateral.times(targetProjection - lateralProjection));
        }
        if (self.groupIntent == GroupIntent.COMMIT_REVENGE &&
                group.revengeOpportunity != null) {
            var target = group.revengeOpportunity.vulnerableAttacker();
            var route = target.pos.minus(self.pos);
            if (route.hypotenuse() > 0.0001)
                desired = desired.times(0.28).plus(route.div(route.hypotenuse()).times(speed * 0.72));
        } else if (self.groupIntent == GroupIntent.COUNTER_COVER &&
                group.counterCoverAssessment != null) {
            var target = group.counterCoverAssessment.enemyRevenger();
            var route = target.pos.minus(self.pos);
            if (route.hypotenuse() > 0.0001)
                desired = desired.times(0.36).plus(route.div(route.hypotenuse()).times(speed * 0.64));
        } else if (self.groupIntent == GroupIntent.RECOVER_FORMATION) {
            var recoveryTeamCenter = self.team == Team.BLUE ? group.blueCenter : group.redCenter;
            var recovery = recoveryTeamCenter.plus(group.center.minus(recoveryTeamCenter).times(0.18)).
                    minus(self.pos);
            if (recovery.hypotenuse() > 0.0001)
                desired = desired.times(0.44).plus(recovery.div(recovery.hypotenuse()).times(speed * 0.56));
        }
        var phaseSpeed = group.phase == GroupPhase.APPROACH ? 0.94 :
                group.phase == GroupPhase.RECOVER_SHAPE ? 0.86 : 0.82;
        var maximum = speed * phaseSpeed;
        var beforeLimit = desired.hypotenuse();
        desired = limitPreservingTangent(desired, teamLateral, maximum);
        var afterLimit = desired.hypotenuse();
        var beforeBackward = desired.hypotenuse();
        if (desired.dot(new Pos(self.angle)) < -0.02) {
            var preservedTangent = teamLateral.times(desired.dot(teamLateral));
            desired = preservedTangent.plus(desired.minus(preservedTangent).
                    times(BACKWARD_SPEED_FACTOR));
        }
        var appliedTangent = desired.dot(teamLateral);
        if (group.phase == GroupPhase.ROTATE) {
            modernMetrics.requestedTangentialVelocitySum += tangentRequested;
            modernMetrics.appliedTangentialVelocitySum += appliedTangent;
            modernMetrics.radialCorrectionSum += Math.abs(composedLocalVelocity.dot(towardMark));
            modernMetrics.formationCorrectionSum += formationCorrection.hypotenuse();
            modernMetrics.partnerCorrectionSum += partnerCorrection.hypotenuse();
            modernMetrics.avoidanceCorrectionSum += avoidanceCorrection.hypotenuse();
            modernMetrics.boundaryCorrectionSum += boundaryCorrection.hypotenuse();
            modernMetrics.normalizationLossSum += Math.max(0, beforeLimit - afterLimit);
            modernMetrics.backwardPenaltyLossSum += Math.max(0, beforeBackward - desired.hypotenuse());
            modernMetrics.inertiaLossSum += Math.max(0, appliedTangent -
                    Math.max(0, self.velocity.dot(teamLateral)));
            modernMetrics.collectiveRotationBiasContribution += teamManeuverVelocity.hypotenuse();
            modernMetrics.teamManeuverContribution += teamManeuverVelocity.hypotenuse();
            modernMetrics.localManeuverContribution += ordinaryLocalVelocity.times(0.58).hypotenuse();
            modernMetrics.groupCorrectionContribution += formationCorrection.plus(partnerCorrection).hypotenuse();
            modernMetrics.weaponAvoidanceContribution +=
                    localWeaponAvoidance.plus(weaponAvoidance).hypotenuse();
            modernMetrics.collisionAvoidanceContribution += collisionAvoidance.hypotenuse();
            modernMetrics.movementBoundaryContribution += boundaryCorrection.hypotenuse();
            ++modernMetrics.movementContributionSamples;
            ++modernMetrics.steeringSamples;
        }
        self.groupSteeringDebug = new GroupSteeringDebug(tangentRequested,
                Math.abs(composedLocalVelocity.dot(towardMark)), formationCorrection.hypotenuse(),
                partnerCorrection.hypotenuse(), avoidanceCorrection.hypotenuse(),
                boundaryCorrection.hypotenuse(), appliedTangent,
                desired.dot(towardMark), desired.hypotenuse(), self.velocity.hypotenuse(),
                maneuver == null ? null : maneuver.type,
                maneuver == null ? "NONE" : self == maneuver.outerPlayer ? "OUTER" : "INNER",
                teamManeuverVelocity.hypotenuse());
        return desired;
    }

    private Pos teamManeuverVelocity(Musketeer self, GroupEngagement group, Pos forward,
                                     Pos lateral) {
        var maneuver = group.maneuver(self.team);
        if (maneuver == null || self.isAttacking() || self.state == CombatState.RECOVERY ||
                self.state == CombatState.BLOCK_STUN || self.groupIntent == GroupIntent.COMMIT_REVENGE ||
                self.groupIntent == GroupIntent.COUNTER_COVER || self.localManeuver != null &&
                self.localManeuver.type == LocalManeuverType.HOLD_POSITION) return new Pos();
        var ally = teammateOf(self);
        var footworkScale = 0.82 + self.stats.footwork() * 0.002;
        return switch (maneuver.type) {
            case HOLD_LINE -> new Pos();
            case ROTATE_LEFT, ROTATE_RIGHT -> {
                var outer = self == maneuver.outerPlayer;
                var lateralSpeed = (outer ? 0.76 : 0.37) * footworkScale;
                var forwardSpeed = (outer ? 0.12 : 0.15) * footworkScale;
                yield lateral.times(lateralSpeed).plus(forward.times(forwardSpeed));
            }
            case PRESS_FORWARD -> forward.times(0.56 * footworkScale).
                    plus(lateral.times(0.14 * footworkScale));
            case GIVE_GROUND -> forward.times(-0.34 * footworkScale).
                    plus(lateral.times(0.28 * footworkScale));
            case SHIFT_LEFT, SHIFT_RIGHT -> lateral.times(0.58 * footworkScale).
                    plus(forward.times(0.08 * footworkScale));
            case COMPRESS -> ally == null ? new Pos() :
                    normalizedOr(ally.pos.minus(self.pos), lateral).times(0.45 * footworkScale).
                            plus(lateral.times(0.10));
            case EXPAND -> ally == null ? new Pos() :
                    normalizedOr(self.pos.minus(ally.pos), lateral).times(0.42 * footworkScale).
                            plus(forward.times(0.08));
            case CROSS_COVER -> {
                var objective = self.secondaryThreat == null ? group.enemyCenter(self.team) :
                        known(self, self.secondaryThreat);
                yield normalizedOr(objective.minus(self.pos), lateral).times(0.40 * footworkScale).
                        plus(lateral.times(0.20 * footworkScale));
            }
            case RECOVER_FORMATION -> {
                var preferred = preferredGroupCoverPosition(self, group);
                yield normalizedOr(preferred.minus(self.pos), forward).times(0.52 * footworkScale);
            }
        };
    }

    private Pos normalizedOr(Pos vector, Pos fallback) {
        return vector.hypotenuse() < 0.0001 ? fallback : vector.div(vector.hypotenuse());
    }

    private void updateLocalManeuver(Musketeer self, GroupEngagement group, Musketeer mark) {
        self.primaryWeaponLane = weaponThreatLane(self, mark);
        self.secondaryWeaponLane = self.secondaryThreat == null || !self.secondaryThreat.alive ? null :
                weaponThreatLane(self, self.secondaryThreat);
        if (self.isAttacking() || self.state == CombatState.RECOVERY ||
                self.state == CombatState.BLOCK_STUN || self.groupIntent == GroupIntent.COMMIT_REVENGE ||
                self.groupIntent == GroupIntent.COUNTER_COVER) return;
        var current = self.localManeuver;
        if (current != null) {
            var age = current.age(simulationTime);
            var completed = age >= current.maximumDuration || age >= current.minimumDuration &&
                    current.destination.minus(self.pos).hypotenuse() < 0.10;
            var interrupted = current.interruptible && localManeuverInterrupted(self, current);
            if (!completed && !interrupted) return;
            modernMetrics.maneuverDurationSum += age;
            if (completed) ++modernMetrics.localManeuversCompleted;
            else ++modernMetrics.localManeuversInterrupted;
        }
        var selected = chooseLocalManeuver(self, group, mark);
        var previousSide = current == null ? 0 : localManeuverSide(current.type);
        var selectedSide = localManeuverSide(selected.type());
        if (previousSide != 0 && selectedSide != 0 && previousSide != selectedSide)
            ++modernMetrics.localDirectionReversals;
        else if (self.lastLocalManeuverSide != 0 && selectedSide != 0 &&
                self.lastLocalManeuverSide != selectedSide)
            ++modernMetrics.localDirectionReversals;
        if (selectedSide != 0) self.lastLocalManeuverSide = selectedSide;
        var duration = maneuverDuration(selected.type(), self);
        self.localManeuver = new LocalManeuver(selected.type(), selected.destination(), teammateOf(self),
                selected.type() == LocalManeuverType.AVOID_SECONDARY_WEAPON_LINE ?
                        self.secondaryThreat : mark, simulationTime, duration[0], duration[1],
                selected.score(), selected.primaryReason(), true);
        self.localManeuverAssessment = selected;
        ++modernMetrics.localManeuversStarted;
        traceDecision(self, DecisionCategory.LOCAL_MOVEMENT, selected.type().name(),
                selected.primaryReason(), selected.type() ==
                        LocalManeuverType.AVOID_SECONDARY_WEAPON_LINE ? self.secondaryThreat : mark,
                selected.destination(), selected.score(), 0, 0, 0,
                selected.rejectionReason());
        switch (selected.type()) {
            case HOLD_POSITION -> ++modernMetrics.holds;
            case STEP_LEFT, STEP_RIGHT -> ++modernMetrics.lateralSteps;
            case ADVANCE_DIAGONAL_LEFT, ADVANCE_DIAGONAL_RIGHT -> ++modernMetrics.diagonalAdvances;
            case GIVE_GROUND_LEFT, GIVE_GROUND_RIGHT -> ++modernMetrics.giveGroundManeuvers;
            case AVOID_PRIMARY_WEAPON_LINE, AVOID_SECONDARY_WEAPON_LINE ->
                    ++modernMetrics.weaponLineAvoidances;
            case CROSS_COVER_LEFT, CROSS_COVER_RIGHT -> ++modernMetrics.crossCoverManeuvers;
            case CREATE_ATTACK_LINE -> ++modernMetrics.attackLineCreations;
            default -> { }
        }
    }

    private boolean localManeuverInterrupted(Musketeer self, LocalManeuver maneuver) {
        if (arenaClearance(self.pos) < 0.38) return true;
        for (var agent : agents)
            if (agent != self && agent.alive && agent.pos.minus(self.pos).hypotenuse() < 0.69 &&
                    agent.pos.minus(maneuver.destination).hypotenuse() <
                            agent.pos.minus(self.pos).hypotenuse()) return true;
        if (maneuver.age(simulationTime) < Math.min(0.12, maneuver.minimumDuration)) return false;
        var future = self.pos.plus(self.velocity.times(0.18));
        var avoiding = maneuver.type == LocalManeuverType.AVOID_PRIMARY_WEAPON_LINE ||
                maneuver.type == LocalManeuverType.AVOID_SECONDARY_WEAPON_LINE;
        return !avoiding &&
                laneContains(self.primaryWeaponLane, future) ||
                !avoiding &&
                        laneContains(self.secondaryWeaponLane, future);
    }

    private LocalManeuverAssessment chooseLocalManeuver(Musketeer self, GroupEngagement group,
                                                         Musketeer mark) {
        var candidates = new LocalManeuverType[]{LocalManeuverType.HOLD_POSITION,
                LocalManeuverType.STEP_LEFT, LocalManeuverType.STEP_RIGHT,
                LocalManeuverType.ADVANCE_DIAGONAL_LEFT, LocalManeuverType.ADVANCE_DIAGONAL_RIGHT,
                LocalManeuverType.GIVE_GROUND_LEFT, LocalManeuverType.GIVE_GROUND_RIGHT,
                LocalManeuverType.CLOSE_DISTANCE, LocalManeuverType.OPEN_DISTANCE,
                LocalManeuverType.REJOIN_ALLY, LocalManeuverType.CROSS_COVER_LEFT,
                LocalManeuverType.CROSS_COVER_RIGHT, LocalManeuverType.AVOID_PRIMARY_WEAPON_LINE,
                LocalManeuverType.AVOID_SECONDARY_WEAPON_LINE, LocalManeuverType.CREATE_ATTACK_LINE,
                LocalManeuverType.DENY_ENEMY_ATTACK_LINE, LocalManeuverType.SCREEN_ALLY,
                LocalManeuverType.PRESSURE_PRIMARY_MARK, LocalManeuverType.RECOVER_TEAM_SHAPE};
        LocalManeuverAssessment best = null;
        for (var type : candidates) {
            var assessment = assessLocalManeuver(self, group, mark, type);
            if (assessment.viable() && (best == null || assessment.score() > best.score()))
                best = assessment;
        }
        return best == null ? assessLocalManeuver(self, group, mark,
                LocalManeuverType.HOLD_POSITION) : best;
    }

    private LocalManeuverAssessment assessLocalManeuver(Musketeer self, GroupEngagement group,
                                                         Musketeer mark, LocalManeuverType type) {
        var knownMark = known(self, mark);
        var offset = knownMark.minus(self.pos);
        var distance = offset.hypotenuse();
        var forward = distance < 0.0001 ? new Pos(self.angle) : offset.div(distance);
        var left = new Pos(-forward.y(), forward.x());
        var ally = teammateOf(self);
        var destination = localManeuverDestination(self, group, type, forward, left, ally);
        var boundarySafety = Math.clamp(arenaClearance(destination) / 0.85, 0, 1);
        var primarySafety = laneSafety(self.primaryWeaponLane, destination);
        var secondarySafety = laneSafety(self.secondaryWeaponLane, destination);
        var weaponSafety = Math.min(primarySafety, secondarySafety);
        var allyDistance = ally == null ? GROUP_PARTNER_PREFERRED_DISTANCE :
                ally.pos.minus(destination).hypotenuse();
        var cohesion = Math.clamp(1 - Math.abs(allyDistance - GROUP_PARTNER_PREFERRED_DISTANCE) / 1.25,
                0, 1);
        var allyClearance = ally == null ? 1 : distanceToSegment(ally.pos, destination, knownMark);
        var attackLine = Math.clamp((allyClearance - Musketeer.BODY_RADIUS * 1.25) / 0.72, 0, 1);
        var spacing = Math.clamp((knownMark.minus(destination).hypotenuse() - 0.88) / 0.70, 0, 1);
        var cover = ally == null ? 0 : Math.clamp(1 - Math.abs(allyDistance - 1.35) / 1.10, 0, 1);
        var movementCost = Math.clamp(destination.minus(self.pos).hypotenuse() / 0.78, 0, 1);
        var score = weaponSafety * 0.30 + cover * 0.20 + attackLine * 0.14 + cohesion * 0.15 +
                spacing * 0.08 + boundarySafety * 0.10 - movementCost * 0.08;
        var reason = "local_geometry";
        if (type == LocalManeuverType.HOLD_POSITION && cover > 0.72 && weaponSafety > 0.62) {
            score += 0.22;
            reason = "ally_already_covered";
        }
        if ((type == LocalManeuverType.AVOID_PRIMARY_WEAPON_LINE &&
                laneContains(self.primaryWeaponLane, self.pos)) ||
                (type == LocalManeuverType.AVOID_SECONDARY_WEAPON_LINE &&
                        laneContains(self.secondaryWeaponLane, self.pos))) {
            score += 0.62;
            reason = "avoid_credible_weapon_lane";
        }
        if (type == LocalManeuverType.REJOIN_ALLY && allyDistance > 1.72) {
            score += 0.38;
            reason = "ally_too_far";
        }
        if (type == LocalManeuverType.OPEN_DISTANCE && distance < 1.30) score += 0.36;
        if (type == LocalManeuverType.CLOSE_DISTANCE && distance > 2.05) score += 0.34;
        if (type == LocalManeuverType.CREATE_ATTACK_LINE && allyClearance < Musketeer.BODY_RADIUS * 1.45) {
            score += 0.48;
            reason = "ally_blocks_attack_line";
        }
        if ((type == LocalManeuverType.CROSS_COVER_LEFT ||
                type == LocalManeuverType.CROSS_COVER_RIGHT) && self.secondaryThreat != null) {
            score += self.stats.teamworkAbility() * 0.0025;
            reason = "cross_cover_secondary_threat";
        }
        if (type == LocalManeuverType.PRESSURE_PRIMARY_MARK)
            score += self.stats.aggression() * 0.0018;
        if (type == LocalManeuverType.STEP_LEFT || type == LocalManeuverType.STEP_RIGHT)
            score += 0.10 + self.stats.footwork() * 0.0008;
        var preferredLateral = (self.preferredGroupPosition == null ? self.pos :
                self.preferredGroupPosition).minus(self.pos).dot(left);
        if (type == LocalManeuverType.STEP_LEFT)
            score += Math.max(0, preferredLateral) * 0.45;
        else if (type == LocalManeuverType.STEP_RIGHT)
            score += Math.max(0, -preferredLateral) * 0.45;
        var candidateSide = localManeuverSide(type);
        if (candidateSide != 0 && self.lastLocalManeuverSide != 0)
            score += candidateSide == self.lastLocalManeuverSide ? -0.08 : 0.06;
        if (type == LocalManeuverType.ADVANCE_DIAGONAL_LEFT ||
                type == LocalManeuverType.ADVANCE_DIAGONAL_RIGHT)
            score += 0.14 + self.stats.aggression() * 0.0008;
        if (type == LocalManeuverType.GIVE_GROUND_LEFT ||
                type == LocalManeuverType.GIVE_GROUND_RIGHT)
            score += 0.08 + (distance < 1.62 ? 0.22 : 0);
        if (self.localManeuver != null && self.localManeuver.type == type)
            score += type == LocalManeuverType.HOLD_POSITION ? -0.16 : 0.05;
        score += random.nextGaussian() * (101 - self.stats.decisionMaking()) * 0.0015;
        var bodyBlocked = false;
        for (var agent : agents)
            if (agent != self && agent.alive && agent.pos.minus(destination).hypotenuse() < 0.62) {
                bodyBlocked = true;
                break;
            }
        var viable = boundarySafety > 0.08 && (!bodyBlocked || type == LocalManeuverType.HOLD_POSITION) &&
                (type != LocalManeuverType.AVOID_SECONDARY_WEAPON_LINE || self.secondaryWeaponLane != null);
        return new LocalManeuverAssessment(type, viable, destination, weaponSafety, cover,
                attackLine, cohesion, spacing, boundarySafety, movementCost, score, reason,
                viable ? "none" : bodyBlocked ? "destination_occupied" : "boundary_risk");
    }

    private Pos localManeuverDestination(Musketeer self, GroupEngagement group,
                                          LocalManeuverType type, Pos forward, Pos left,
                                          Musketeer ally) {
        var destination = switch (type) {
            case HOLD_POSITION -> self.pos;
            case STEP_LEFT -> self.pos.plus(left.times(0.34));
            case STEP_RIGHT -> self.pos.minus(left.times(0.34));
            case ADVANCE_DIAGONAL_LEFT -> self.pos.plus(forward.times(0.24)).plus(left.times(0.40));
            case ADVANCE_DIAGONAL_RIGHT -> self.pos.plus(forward.times(0.24)).minus(left.times(0.40));
            case GIVE_GROUND_LEFT -> self.pos.minus(forward.times(0.20)).plus(left.times(0.42));
            case GIVE_GROUND_RIGHT -> self.pos.minus(forward.times(0.20)).minus(left.times(0.42));
            case CLOSE_DISTANCE, PRESSURE_PRIMARY_MARK -> self.pos.plus(forward.times(0.38));
            case OPEN_DISTANCE -> self.pos.minus(forward.times(0.36));
            case FOLLOW_ALLY, REJOIN_ALLY -> ally == null ? self.pos :
                    ally.pos.plus(self.pos.minus(ally.pos).times(0.58));
            case CROSS_COVER_LEFT -> self.pos.plus(left.times(0.46)).plus(forward.times(0.10));
            case CROSS_COVER_RIGHT -> self.pos.minus(left.times(0.46)).plus(forward.times(0.10));
            case AVOID_PRIMARY_WEAPON_LINE, AVOID_SECONDARY_WEAPON_LINE -> {
                var lane = type == LocalManeuverType.AVOID_PRIMARY_WEAPON_LINE ?
                        self.primaryWeaponLane : self.secondaryWeaponLane;
                var laneLeft = lane == null ? left : new Pos(-lane.direction().y(), lane.direction().x());
                var first = self.pos.plus(laneLeft.times(0.48));
                var second = self.pos.minus(laneLeft.times(0.48));
                yield laneSafety(lane, first) + arenaClearance(first) * 0.12 >=
                        laneSafety(lane, second) + arenaClearance(second) * 0.12 ? first : second;
            }
            case CREATE_ATTACK_LINE -> {
                if (ally == null) yield self.pos.plus(left.times(0.36));
                var allySide = Math.signum(ally.pos.minus(self.pos).dot(left));
                yield self.pos.plus(left.times(allySide >= 0 ? -0.44 : 0.44));
            }
            case DENY_ENEMY_ATTACK_LINE, SCREEN_ALLY -> ally == null ? self.pos :
                    self.pos.plus(ally.pos.minus(self.pos).times(0.24));
            case RECOVER_TEAM_SHAPE -> self.preferredGroupPosition == null ?
                    preferredGroupCoverPosition(self, group) : self.preferredGroupPosition;
        };
        var margin = Musketeer.BODY_RADIUS + 0.08;
        return new Pos(Math.clamp(destination.x(), margin, ARENA_WIDTH - margin),
                Math.clamp(destination.y(), margin, ARENA_HEIGHT - margin));
    }

    private Pos localManeuverVelocity(Musketeer self, double speed) {
        var maneuver = self.localManeuver;
        if (maneuver == null || maneuver.type == LocalManeuverType.HOLD_POSITION) return new Pos();
        var route = maneuver.destination.minus(self.pos);
        if (route.hypotenuse() < 0.025) return new Pos();
        var factor = switch (maneuver.type) {
            case STEP_LEFT, STEP_RIGHT, AVOID_PRIMARY_WEAPON_LINE,
                 AVOID_SECONDARY_WEAPON_LINE -> 0.48;
            case ADVANCE_DIAGONAL_LEFT, ADVANCE_DIAGONAL_RIGHT, GIVE_GROUND_LEFT,
                 GIVE_GROUND_RIGHT, CROSS_COVER_LEFT, CROSS_COVER_RIGHT,
                 CREATE_ATTACK_LINE, DENY_ENEMY_ATTACK_LINE -> 0.58;
            case REJOIN_ALLY, FOLLOW_ALLY, RECOVER_TEAM_SHAPE -> 0.68;
            default -> 0.52;
        };
        return route.div(route.hypotenuse()).times(Math.min(speed * factor, route.hypotenuse() * 2.6));
    }

    private WeaponThreatLane weaponThreatLane(Musketeer observer, Musketeer attacker) {
        if (attacker == null || !attacker.alive || !isTracked(observer, attacker)) return null;
        var awareness = observer.opponentAwareness.get(attacker);
        var confidence = awareness.visible ? 0.62 +
                (observer.stats.distanceControl() + observer.stats.reaction()) * 0.0019 :
                Math.clamp(awareness.memoryTime / 1.8, 0.08, 0.48);
        var timeToActive = Double.POSITIVE_INFINITY;
        var activeRemaining = 0.0;
        if (awareness.observedState == CombatState.ATTACK_ACTIVE ||
                awareness.observedState == CombatState.CHAMBER_ACTIVE) {
            timeToActive = 0;
            activeRemaining = Math.max(0, awareness.observedAttackDirection.activeTime -
                    awareness.observedStateTime);
        } else if (awareness.observedState == CombatState.ATTACK_PREPARE ||
                awareness.observedState == CombatState.CHAMBER_PREPARE)
            timeToActive = Math.max(0, awareness.observedAttackDirection.prepareTime -
                    awareness.observedStateTime);
        else if (awareness.observedState == CombatState.ATTACK_HOLD ||
                awareness.observedState == CombatState.CHAMBER_HOLD) timeToActive = 0.04;
        var direction = new Pos(awareness.observedWeaponAngle);
        var origin = awareness.lastKnown;
        var lead = awareness.perceivedVelocity.times(Math.min(0.32,
                Double.isFinite(timeToActive) ? timeToActive : 0));
        origin = origin.plus(lead);
        var length = awareness.observedProjectedRange + Musketeer.BODY_RADIUS +
                awareness.perceivedVelocity.dot(direction) * 0.18;
        var halfWidth = 0.18 + (1 - confidence) * 0.28;
        return new WeaponThreatLane(attacker, origin, direction, Math.max(0.55, length),
                halfWidth, timeToActive, activeRemaining, confidence,
                timeToActive <= 0.34 && confidence >= 0.34);
    }

    private boolean laneContains(WeaponThreatLane lane, Pos point) {
        return lane != null && lane.currentlyDangerous() &&
                distanceToSegment(point, lane.origin(), lane.end()) <=
                        lane.halfWidth() + Musketeer.BODY_RADIUS * 0.72;
    }

    private double laneSafety(WeaponThreatLane lane, Pos point) {
        if (lane == null || !lane.currentlyDangerous()) return 1;
        var clearance = distanceToSegment(point, lane.origin(), lane.end()) - lane.halfWidth();
        return Math.clamp(clearance / 0.62, 0, 1) * lane.confidence() + 1 - lane.confidence();
    }

    private Pos perceivedWeaponAvoidance(Musketeer self) {
        var total = new Pos();
        for (var lane : new WeaponThreatLane[]{self.primaryWeaponLane, self.secondaryWeaponLane}) {
            if (!laneContains(lane, self.pos)) continue;
            var lateral = new Pos(-lane.direction().y(), lane.direction().x());
            var first = self.pos.plus(lateral.times(0.35));
            var second = self.pos.minus(lateral.times(0.35));
            total = total.plus(lateral.times(laneSafety(lane, first) >= laneSafety(lane, second) ? 1 : -1).
                    times(0.44 * lane.confidence()));
        }
        return total;
    }

    private double[] maneuverDuration(LocalManeuverType type, Musketeer self) {
        var range = switch (type) {
            case HOLD_POSITION, STEP_LEFT, STEP_RIGHT -> new double[]{0.18, 0.42};
            case ADVANCE_DIAGONAL_LEFT, ADVANCE_DIAGONAL_RIGHT,
                 GIVE_GROUND_LEFT, GIVE_GROUND_RIGHT -> new double[]{0.30, 0.70};
            case FOLLOW_ALLY, REJOIN_ALLY, CROSS_COVER_LEFT, CROSS_COVER_RIGHT,
                 SCREEN_ALLY, RECOVER_TEAM_SHAPE -> new double[]{0.35, 0.85};
            default -> new double[]{0.25, 0.65};
        };
        var persistence = (self.stats.decisionMaking() + self.stats.footwork()) / 200.0;
        var maximum = range[0] + (range[1] - range[0]) * (0.42 + persistence * 0.42) +
                random.nextDouble() * 0.06;
        return new double[]{range[0], Math.min(range[1], maximum)};
    }

    private int localManeuverSide(LocalManeuverType type) {
        return switch (type) {
            case STEP_LEFT, ADVANCE_DIAGONAL_LEFT, GIVE_GROUND_LEFT, CROSS_COVER_LEFT -> -1;
            case STEP_RIGHT, ADVANCE_DIAGONAL_RIGHT, GIVE_GROUND_RIGHT, CROSS_COVER_RIGHT -> 1;
            default -> 0;
        };
    }

    private Pos limitPreservingTangent(Pos velocity, Pos tangent, double maximum) {
        if (velocity.hypotenuse() <= maximum) return velocity;
        var tangentAmount = Math.clamp(velocity.dot(tangent), -maximum, maximum);
        var tangentVelocity = tangent.times(tangentAmount);
        var remainder = velocity.minus(tangentVelocity);
        var remainingMagnitude = Math.sqrt(Math.max(0, maximum * maximum -
                tangentAmount * tangentAmount));
        if (remainder.hypotenuse() > remainingMagnitude && remainder.hypotenuse() > 0.0001)
            remainder = remainder.div(remainder.hypotenuse()).times(remainingMagnitude);
        return tangentVelocity.plus(remainder);
    }

    private Pos modernDuelVelocity(Musketeer self, Musketeer opponent, double speed) {
        var offset = opponent.pos.minus(self.pos);
        var distance = offset.hypotenuse();
        var toward = distance > 0.0001 ? offset.div(distance) : new Pos(self.angle);
        var tangent = new Pos(-toward.y(), toward.x()).times(self.engagementSide);
        var preferredDistance = self.state == CombatState.RECOVERY ? 2.12 :
                self.isGuarding() ? 1.55 :
                        self.state == CombatState.ATTACK_HOLD ? 1.36 : 1.48;
        var radial = Math.clamp((distance - preferredDistance) * 1.05, -0.82, 0.88);
        var tangentWeight = self.state == CombatState.RECOVERY ? 0.28 :
                self.isGuarding() ? 0.22 : self.state == CombatState.ATTACK_HOLD ? 0.14 : 0.26;
        var steering = toward.times(radial).plus(tangent.times(tangentWeight));
        if (supportPlan != null && supportPlan.supporter == self &&
                supportPlan.state == SupportPlanState.PREPARING_TO_LEAVE) {
            var departure = supportPlan.supportDestination.minus(self.pos);
            if (departure.hypotenuse() > 0.0001)
                steering = steering.times(0.38).
                        plus(departure.div(departure.hypotenuse()).times(0.62));
        }
        steering = steering.plus(allyAvoidance(self).times(1.25));
        steering = steering.plus(threatSteering(self, opponent).times(0.52));
        steering = steering.plus(boundarySteering(self).times(1.25));
        var magnitude = steering.hypotenuse();
        if (magnitude > 1) steering = steering.div(magnitude);
        var desired = steering.times(speed * (self.state == CombatState.RECOVERY ? 0.92 : 0.88));
        if (desired.dot(new Pos(self.angle)) < -0.02)
            desired = desired.times(BACKWARD_SPEED_FACTOR);
        return desired;
    }

    private Pos rearFlankVelocity(Musketeer flanker, Musketeer defender, double speed) {
        var radialOffset = flanker.pos.minus(defender.pos);
        var distance = radialOffset.hypotenuse();
        var radial = distance > 0.0001 ? radialOffset.div(distance) :
                new Pos(defender.angle + Math.PI / 2);
        var side = flanker.localSide < 0 ? -1 : 1;
        var tangent = new Pos(-radial.y(), radial.x()).times(side);
        var desiredRadius = Math.clamp(flanker.projectedThrustLength() + Musketeer.BODY_RADIUS -
                REAR_PREPARATION_MARGIN + flanker.flankRadiusOffset,
                REAR_MIN_DISTANCE + 0.20, REAR_MAX_DISTANCE - 0.08);
        var radialCorrection = Math.clamp((desiredRadius - distance) * 1.45, -0.72, 0.72);
        var tangentialWeight = flanker.flankPhase == FlankPhase.GAIN_REAR_ANGLE ? 1.0 :
                flanker.flankPhase == FlankPhase.STABILIZE_REAR ? 0.48 : 0.22;
        var steering = tangent.times(tangentialWeight).plus(radial.times(radialCorrection));
        if (flanker.smoothedRearDestination != null) {
            var destinationOffset = flanker.smoothedRearDestination.minus(flanker.pos);
            var destinationDistance = destinationOffset.hypotenuse();
            if (destinationDistance > 0.0001)
                steering = steering.plus(destinationOffset.div(destinationDistance).times(
                        flanker.flankPhase == FlankPhase.GAIN_REAR_ANGLE ? 0.24 : 0.72));
        }
        steering = steering.plus(allyAvoidance(flanker).times(1.35));
        steering = steering.plus(threatSteering(flanker, defender).times(
                flanker.threatResponse.ordinal() >= ThreatResponse.ADJUST_PATH.ordinal() ? 1.15 : 0.48));
        steering = steering.plus(boundarySteering(flanker).times(1.45));
        var magnitude = steering.hypotenuse();
        if (magnitude > 1) steering = steering.div(magnitude);
        var phaseSpeed = switch (flanker.flankPhase) {
            case GAIN_REAR_ANGLE -> 1.0;
            case STABILIZE_REAR -> 0.62;
            case PREPARE_BACKSTAB -> 0.42;
            case COMMIT_BACKSTAB -> 0.30;
            default -> 0.72;
        };
        var desired = steering.times(speed * phaseSpeed);
        if (desired.dot(new Pos(flanker.angle)) < -0.02)
            desired = desired.times(BACKWARD_SPEED_FACTOR);
        return desired;
    }

    private Pos interceptionVelocity(Musketeer self, Musketeer flanker, double speed) {
        var offset = self.tacticalDestination.minus(self.pos);
        var distance = offset.hypotenuse();
        var steering = distance > 0.0001 ? offset.div(distance) : new Pos();
        if (distance < 0.32) steering = steering.times(distance / 0.32);
        if (self.velocity.hypotenuse() > 0.0001)
            steering = steering.plus(self.velocity.div(self.velocity.hypotenuse()).times(0.12));
        steering = steering.plus(allyAvoidance(self).times(1.25));
        steering = steering.plus(threatSteering(self, flanker).times(0.38));
        steering = steering.plus(boundarySteering(self).times(1.15));
        var magnitude = steering.hypotenuse();
        if (magnitude > 1) steering = steering.div(magnitude);
        var phaseSpeed = switch (self.interceptionPhase) {
            case APPROACH_INTERCEPT_POINT -> 1.0;
            case BLOCK_FLANK_PATH -> 0.78;
            case PRESENT_THREAT -> 0.58;
            case ENGAGE_FLANKER, FAILED -> 0.88;
        };
        var desired = steering.times(speed * phaseSpeed);
        if (desired.dot(new Pos(self.angle)) < -0.02) desired = desired.times(BACKWARD_SPEED_FACTOR);
        return desired;
    }

    private Pos allyAvoidance(Musketeer self) {
        var teammate = teammateOf(self);
        if (teammate == null || !teammate.alive) return new Pos();
        var offset = teammate.pos.minus(self.pos);
        var distance = offset.hypotenuse();
        if (distance < 0.0001) return new Pos(self.angle + Math.PI);
        return distance < 1.15 ? offset.div(distance).times(-(1.15 - distance) * 2.2) : new Pos();
    }

    private Pos teamSteering(Musketeer self, Musketeer enemy) {
        var teammate = teammateOf(self);
        if (teammate == null || !teammate.alive) return new Pos();
        var offset = teammate.pos.minus(self.pos);
        var distance = offset.hypotenuse();
        if (distance < 0.0001) return new Pos(self.angle + Math.PI);
        if (distance < 1.05) return offset.div(distance).times(-(1.05 - distance) * 2.2);
        var teamwork = self.stats.teamworkAbility() / 100.0;
        var urgency = teammateSupportUrgency(self, teammate);
        var maximumSupportDistance = 2.65 - teamwork * 0.75 - urgency * 0.18;
        var supportTarget = teammate.target != null && teammate.target.alive ? teammate.target : enemy;
        var supportPoint = teammate.pos;
        if (supportTarget != null) {
            var towardThreat = supportTarget.pos.minus(teammate.pos);
            var threatDistance = towardThreat.hypotenuse();
            if (threatDistance > 0.0001) {
                var direction = towardThreat.div(threatDistance);
                supportPoint = supportPoint.plus(new Pos(-direction.y(), direction.x()).times(teamSlot(self) * 0.68));
            }
        }
        var towardSupport = supportPoint.minus(self.pos);
        var supportDistance = towardSupport.hypotenuse();
        if (supportDistance < 0.0001) return new Pos();
        if (distance > maximumSupportDistance)
            return towardSupport.div(supportDistance).times(Math.min(0.95,
                    0.20 + (distance - maximumSupportDistance) * 0.58 + urgency * 0.42));
        if (supportDistance > 0.58 && enemy != null && teammate.target == enemy)
            return towardSupport.div(supportDistance).times((0.10 + teamwork * 0.18) *
                    Math.clamp((supportDistance - 0.58) / 0.85, 0, 1));
        return new Pos();
    }

    private int teamSlot(Musketeer self) {
        var team = self.team == Team.BLUE ? blueTeam : redTeam;
        return team[0] == self ? -1 : 1;
    }

    private double teammateSupportUrgency(Musketeer self, Musketeer teammate) {
        var urgency = Math.clamp(teammate.pressureReceived * 0.55, 0, 0.55);
        if (teammate.isGuarding()) urgency += 0.12;
        for (var opponent : opponentsOf(self)) {
            if (!opponent.alive || opponent.target != teammate) continue;
            var distance = opponent.pos.minus(teammate.pos).hypotenuse();
            if (distance > 2.55) continue;
            var attack = opponent.isAttacking() ? 0.55 :
                    opponent.state == CombatState.ATTACK_HOLD ? 0.38 :
                            opponent.state == CombatState.ATTACK_PREPARE ? 0.24 : 0.10;
            urgency = Math.max(urgency, attack * Math.clamp((2.55 - distance) / 1.55, 0.20, 1));
        }
        return Math.clamp(urgency, 0, 1);
    }

    private Pos threatSteering(Musketeer self, Musketeer selectedEnemy) {
        var steering = new Pos();
        for (var opponent : opponentsOf(self)) {
            if (!opponent.alive || opponent == selectedEnemy || !isTracked(self, opponent)) continue;
            var offset = known(self, opponent).minus(self.pos);
            var distance = offset.hypotenuse();
            if (distance < 0.0001 || distance > 2.65) continue;
            var danger = (2.65 - distance) / 2.65;
            if (opponent.state == CombatState.ATTACK_HOLD || opponent.isAttacking()) danger *= 1.55;
            steering = steering.minus(offset.div(distance).times(danger));
        }
        return steering;
    }

    private Pos boundarySteering(Musketeer self) {
        var margin = 1.08;
        var horizontal = self.getX() < margin ? (margin - self.getX()) / margin :
                self.getX() > ARENA_WIDTH - margin ? -(self.getX() - ARENA_WIDTH + margin) / margin : 0;
        var vertical = self.getY() < margin ? (margin - self.getY()) / margin :
                self.getY() > ARENA_HEIGHT - margin ? -(self.getY() - ARENA_HEIGHT + margin) / margin : 0;
        return new Pos(horizontal, vertical);
    }

    private double bodyFacing(Musketeer self, Musketeer selectedEnemy, double enemyAngle, Pos desiredVelocity) {
        if (self.backEscapeTime > 0) return backProtectionFacing(self, enemyAngle);
        var threatDirection = new Pos(enemyAngle);
        for (var opponent : opponentsOf(self)) {
            if (!opponent.alive || opponent == selectedEnemy || !isTracked(self, opponent)) continue;
            var offset = known(self, opponent).minus(self.pos);
            var distance = offset.hypotenuse();
            if (distance < 0.0001 || distance > 3.10) continue;
            var weight = Math.clamp((3.10 - distance) / 2.35, 0.10, 0.70);
            if (opponent.state == CombatState.ATTACK_HOLD || opponent.isAttacking()) weight *= 1.35;
            threatDirection = threatDirection.plus(offset.div(distance).times(weight));
        }
        var tacticalAngle = threatDirection.radians();
        tacticalAngle = enemyAngle + Math.clamp(angleDifference(enemyAngle, tacticalAngle),
                -Math.toRadians(38), Math.toRadians(38));
        var movementBlend = self.movementIntent.facingMovementBlend;
        if (self.isAttacking()) movementBlend = Math.min(movementBlend, 0.12);
        else if (self.isGuarding() || self.state == CombatState.ATTACK_HOLD ||
                self.state == CombatState.CHAMBER_HOLD) movementBlend = Math.min(movementBlend, 0.22);
        if (desiredVelocity.hypotenuse() <= 0.08) return tacticalAngle;
        var facing = new Pos(tacticalAngle).times(1 - movementBlend).
                plus(desiredVelocity.div(desiredVelocity.hypotenuse()).times(movementBlend));
        return facing.hypotenuse() > 0.0001 ? facing.radians() : tacticalAngle;
    }

    private void updateAttackManipulation(Musketeer self, double dt) {
        if (!self.isAttacking()) return;
        var targetPitch = Math.clamp(self.baseAttackPitch +
                self.manipulationPitchOffset * manipulationCurve(self), -Musketeer.MAX_PITCH, Musketeer.MAX_PITCH);
        var speed = 2.2 + self.stats.manipulationAbility() * 0.018;
        self.attackPitch += Math.clamp(targetPitch - self.attackPitch, -speed * dt, speed * dt);
    }

    private double manipulationCurve(Musketeer self) {
        if (!self.isAttacking() || self.attackManipulation == AttackManipulation.NEUTRAL) return 0;
        var progress = Math.clamp(self.stateTime / Math.max(0.001, self.activeTime()), 0, 1);
        return self.attackManipulation == AttackManipulation.ACCEL ? Math.min(1, progress / 0.32) :
                Math.clamp((progress - 0.18) / 0.68, 0, 1);
    }

    private double movementAcceleration(Musketeer self) {
        var acceleration = 4.6 + self.stats.footwork() * 0.045;
        if (self.isAttacking()) acceleration *= 0.72;
        else if (self.state == CombatState.RECOVERY) acceleration *= 0.64;
        return acceleration;
    }

    private void integrateVelocity(Musketeer self, Pos desired, double acceleration, double dt) {
        self.desiredVelocity = desired;
        if (!self.alive && self.deadAgentAudit.active()) ++self.deadAgentAudit.velocityIntegrations;
        Pos deltaPos = desired.minus(self.velocity);
        var delta = Math.hypot(deltaPos.x(), deltaPos.y());
        var maximum = acceleration * dt;
        if (delta > maximum && delta > 0.0001) deltaPos = deltaPos.times(maximum / delta);
        self.velocity = self.velocity.plus(deltaPos);
        if (Math.hypot(desired.x(), desired.y()) < 0.001) {
            var speed = Math.hypot(self.velocity.x(), self.velocity.y());
            var remaining = Math.max(0, speed - (3.2 + self.stats.footwork() * 0.018) * dt);
            if (speed > 0.0001) self.velocity = self.velocity.times(remaining / speed);
        }
        captureDifferentialStage(DifferentialTickStage.AFTER_VELOCITY_INTEGRATION, self);
    }

    private void moveWithVelocity(Musketeer self, double dt) {
        self.pos = self.pos.plus(self.velocity.times(dt));
        if (Math.hypot(self.velocity.x(), self.velocity.y()) > 0.08)
            self.movementHeading = Math.atan2(self.velocity.y(), self.velocity.x());
    }

    private double backProtectionFacing(Musketeer self, double enemyAngle) {
        var rear = self.rearThreat;
        if (rear == null || !rear.alive) return enemyAngle;
        Musketeer other = null;
        var closestDistance = Double.POSITIVE_INFINITY;
        for (var opponent : opponentsOf(self)) {
            if (!opponent.alive || opponent == rear || !isTracked(self, opponent)) continue;
            var distance = knownDistance(self, opponent);
            if (distance < closestDistance) {
                other = opponent;
                closestDistance = distance;
            }
        }
        if (other == null) return known(self, rear).minus(self.pos).radians();
        var frontAngle = known(self, other).minus(self.pos).radians();
        var rearAngle = known(self, rear).minus(self.pos).radians();
        var separation = angleDifference(frontAngle, rearAngle);
        if (Math.abs(separation) > Math.toRadians(170))
            return normalizeAngle(frontAngle + self.backEscapeDirection * Math.PI / 2);
        return normalizeAngle(frontAngle + separation / 2);
    }

    private void keepInArena(Musketeer agent) {
        if (!agent.alive && agent.deadAgentAudit.active()) ++agent.deadAgentAudit.boundaryCorrections;
        double oldX = agent.getX();
        double oldY = agent.getY();

        double clampedX = Math.clamp(
                oldX,
                Musketeer.BODY_RADIUS,
                ARENA_WIDTH - Musketeer.BODY_RADIUS
        );

        double clampedY = Math.clamp(
                oldY,
                Musketeer.BODY_RADIUS,
                ARENA_HEIGHT - Musketeer.BODY_RADIUS
        );

        double velocityX =
                clampedX != oldX ? 0 : agent.velocity.x();

        double velocityY =
                clampedY != oldY ? 0 : agent.velocity.y();

        agent.velocity = new Pos(velocityX, velocityY);
        agent.pos = new Pos(clampedX, clampedY);
    }

    private void separateAgents(double dt) {
        for (var firstIndex = 0; firstIndex < agents.length; ++firstIndex) {
            var first = agents[firstIndex];
            if (!first.alive) continue;
            for (var secondIndex = firstIndex + 1; secondIndex < agents.length; ++secondIndex) {
                var second = agents[secondIndex];
                if (!second.alive) continue;
                var d = second.pos.minus(first.pos);
                var distance = d.hypotenuse();
                var comfortDistance = Musketeer.BODY_RADIUS * 2;
                if (distance >= comfortDistance) continue;
                var hardDistance = Musketeer.BODY_RADIUS * 1.18;
                if (distance < 0.0001) {
                    d = new Pos(1, 0);
                    distance = 0.0001;
                }
                var normal = d.div(distance);
                var overlap = comfortDistance - distance;
                var closingSpeed = second.velocity.minus(first.velocity).dot(normal);
                var desiredSeparationSpeed = Math.min(0.45, overlap * (4.2 + dt * 8));
                if (closingSpeed < desiredSeparationSpeed) {
                    var impulse = (desiredSeparationSpeed - closingSpeed) / 2;
                    first.velocity = first.velocity.minus(normal.times(impulse));
                    second.velocity = second.velocity.plus(normal.times(impulse));
                }
                if (distance < hardDistance) {
                    var separation = normal.times((hardDistance - distance) / 2);
                    first.pos = first.pos.minus(separation);
                    second.pos = second.pos.plus(separation);
                }
                keepInArena(first);
                keepInArena(second);
            }
        }
    }

    private void resolveAttack(Musketeer attacker) {
        if (!attacker.alive || !attacker.isAttacking() || attacker.attackResolved) return;
        Musketeer nearest = null;
        var nearestDistance = Double.POSITIVE_INFINITY;
        for (var defender : opponentsOf(attacker)) {
            if (!defender.alive) continue;
            var distance = sweptTipDistance(attacker, defender);
            if (distance <= Musketeer.BODY_RADIUS && distance < nearestDistance) {
                nearest = defender;
                nearestDistance = distance;
            }
        }
        if (nearest != null) checkHit(attacker, nearest);
    }

    private void checkHit(Musketeer attacker, Musketeer defender) {
        if (!attacker.alive || !defender.alive || !attacker.isAttacking() || attacker.attackResolved) return;
        var distance = sweptTipDistance(attacker, defender);
        if (distance > Musketeer.BODY_RADIUS) return;
        attacker.attackResolved = true;
        var hitZone = HitZone.atHeight(attacker.attackHeight);
        if (hitZone == null) {
            if (attacker.controlledBackstabAttempt)
                completeBackstab(attacker, defender, false,
                        "BACKSTAB_MISSED", "invalid_hit_height");
            setState(attacker, CombatState.RECOVERY);
            return;
        }
        var backstab = isRearAttack(defender, attacker);
        if (!backstab && defender.target == attacker && defender.state == CombatState.CHAMBER_PREPARE &&
                defender.attackDirection == attacker.attackDirection) {
            var towardAttacker = Math.atan2(attacker.getY() - defender.getY(), attacker.getX() - defender.getX());
            var chamberWindow = defender.chamberType == ChamberType.PERFECT ?
                    PERFECT_CHAMBER_WINDOW : SIMPLE_CHAMBER_WINDOW;
            if (Math.abs(angleDifference(defender.weaponWorldAngle(), towardAttacker)) < Math.toRadians(75) &&
                    Math.abs(defender.chamberTimingError) <= chamberWindow &&
                    (defender.chamberType != ChamberType.PERFECT || defender.stats.chamber() > 90)) {
                beginChamberReturn(defender, attacker);
                return;
            }
        }
        var doubleBlock = defender.state == CombatState.DOUBLE_BLOCK && defender.doubleBlockSwitched &&
                defender.doubleBlockThreat == attacker;
        if (!backstab && (defender.state == CombatState.BLOCKING || doubleBlock) &&
                defender.blockDirection.blocks(attacker.attackDirection)) {
            var towardAttacker = Math.atan2(attacker.getY() - defender.getY(), attacker.getX() - defender.getX());
            if (Math.abs(angleDifference(defender.weaponWorldAngle(), towardAttacker)) < Math.toRadians(75)) {
                defender.blockSparkTime = Musketeer.BLOCK_SPARK_DURATION;
                defender.blockSpark = attacker.tip();
                attacker.decision = Decision.WAIT;
                if (attacker.controlledBackstabAttempt)
                    completeBackstab(attacker, defender, false,
                            "BACKSTAB_BLOCKED_AFTER_TURN", "defender_turned_and_blocked");
                setState(attacker, CombatState.BLOCK_STUN);
                onAttackBlocked(attacker, defender);
                if (doubleBlock) defender.doubleBlockMessage.setTime(1.15);
                else {
                    if (features.doubleBlock()) tryBeginDoubleBlock(defender, attacker);
                    if (features.synchronizedAttacks() && defender.pressureRole != PressureRole.NONE)
                        beginCoordinatedPressureCounter(defender, attacker);
                }
                return;
            }
        }
        if (backstab) {
            defender.backstabMessageTime = 1.15;
            ++defender.backWarningTimes;
        }
        var victimStateAtImpact = defender.state;
        var victimGuardMatched = defender.isGuarding() &&
                defender.blockDirection.blocks(attacker.attackDirection);
        applyDamage(attacker, defender, hitZone, backstab);
        if (!defender.alive)
            explainKill(attacker, defender, victimStateAtImpact, victimGuardMatched, backstab);
        onModernAttackHit(attacker, defender, hitZone, backstab);
        if (!defender.alive) attachKillCausalChain(lastKillExplanation());
        if (attacker.controlledBackstabAttempt)
            completeBackstab(attacker, defender, backstab,
                    backstab ? "BACKSTAB_HIT" : "BACKSTAB_LOST_DEFENDER_TURNED",
                    backstab ? "physical_rear_hit" : "rear_angle_lost_before_impact");
        setState(attacker, CombatState.RECOVERY);
    }

    private void completeBackstab(Musketeer flanker, Musketeer defender, boolean completed,
                                  String event, String reason) {
        var impactRearAngle = Math.abs(angleDifference(defender.angle,
                flanker.pos.minus(defender.pos).radians()));
        logTactical(event, "agent=" + flanker.name(UiLanguage.FRENCH) +
                " target=" + defender.name(UiLanguage.FRENCH) +
                " rearAngleAtImpact=" + "%.1f".formatted(Math.toDegrees(impactRearAngle)) +
                " zone=" + (defender.lastHitZone == null ? "NONE" : defender.lastHitZone) +
                " damage=" + defender.lastDamage + " reason=" + reason);
        flanker.controlledBackstabAttempt = false;
        flanker.backstabWindowLogged = false;
        changeFlankPhase(flanker, completed || !defender.alive ?
                FlankPhase.COMPLETED : FlankPhase.GAIN_REAR_ANGLE, reason);
    }

    private double sweptTipDistance(Musketeer attacker, Musketeer defender) {
        Pos
                start = attacker.previousTip.minus(defender.previous),
                end = attacker.tip().minus(defender.pos),
                segment = end.minus(start),
                lengthSquaredPos = segment.times(segment);
        ;
        var lengthSquared = lengthSquaredPos.x() + lengthSquaredPos.y();
        if (lengthSquared < 0.0000001) return end.hypotenuse();
        var position = Math.clamp(-(start.times(segment).sum()) / lengthSquared, 0, 1);
        return start.plus(segment.times(position)).hypotenuse();
    }

    private void tryBeginDoubleBlock(Musketeer defender, Musketeer firstAttacker) {
        Musketeer secondAttacker = null;
        for (var opponent : opponentsOf(defender)) {
            if (opponent == firstAttacker || !opponent.alive || !opponent.isAttacking() ||
                    opponent.attackResolved || opponent.target != defender ||
                    opponent.attackDirection != firstAttacker.attackDirection.opposite() ||
                    !isVisible(defender, opponent) || isRearAttack(defender, opponent)) continue;
            var towardDefender = Math.atan2(defender.getY() - opponent.getY(), defender.getX() - opponent.getX());
            var distance = defender.pos.minus(opponent.pos).hypotenuse();
            var timeUntilPeak = Math.max(0, opponent.activeTime() / 2 - opponent.stateTime);
            if (Math.abs(angleDifference(opponent.weaponWorldAngle(), towardDefender)) > Math.toRadians(34) ||
                    distance > opponent.projectedThrustLength() + Musketeer.BODY_RADIUS + 0.35 ||
                    opponent.stateTime > opponent.activeTime() * 0.65 || timeUntilPeak > DOUBLE_BLOCK_WINDOW)
                continue;
            secondAttacker = opponent;
            break;
        }
        if (secondAttacker == null) return;
        var ability = defender.stats.doubleBlockAbility();
        if (random.nextDouble() >= Math.pow(ability / 100.0, 3)) return;
        endDezone(defender);
        defender.doubleBlockThreat = secondAttacker;
        defender.doubleBlockSecondDirection = BlockDirection.matching(secondAttacker.attackDirection);
        defender.doubleBlockSwitchTime = 0.19 - 0.00125 * ability;
        defender.doubleBlockSwitched = false;
        defender.decision = Decision.DOUBLE_BLOCK;
        setState(defender, CombatState.DOUBLE_BLOCK);
    }

    private void applyDamage(Musketeer attacker, Musketeer defender, HitZone hitZone, boolean backstab) {
        var cleanFreeKill = backstab && attacker.freeKillAttempt && hitZone != HitZone.LEG;
        var damage = cleanFreeKill || attacker.punishingBait ||
                attacker.chamberType == ChamberType.PERFECT ? 100 : switch (hitZone) {
            case HEAD -> 100;
            case TORSO -> attacker.chamberType == ChamberType.SIMPLE ?
                    (attacker.simpleChamberLethal ? 100 : 50) : random.nextInt(100) < 95 ? 100 : 50;
            case LEG -> 40;
        };
        defender.lastHitZone = hitZone;
        defender.lastDamage = damage;
        defender.hitMessage.setTime(1.15);
        defender.hitPoints = Math.max(0, defender.hitPoints - damage);
        if (defender.dezoneMessage.getTime() > 0 || defender.dezoneSwitchTarget != null) endDezone(defender);
        if (defender.hitPoints > 0) return;
        classifyFlankDeath(attacker, defender);
        stopBait(defender);
        eliminate(defender);
    }

    private void explainKill(Musketeer killer, Musketeer victim,
                             CombatState victimStateAtImpact, boolean victimGuardMatched,
                             boolean backstab) {
        var victimAlly = activeGroup3v3() == null ? teammateOf(victim) :
                bestGroup3Coverer(victim, killer);
        var allyPunishEta = victimAlly == null || !victimAlly.alive ?
                Double.POSITIVE_INFINITY : attackThreatEta(victimAlly, killer);
        var recoveryEta = killer.recoveryTime();
        var opportunity = killer.attackOpportunity != null &&
                killer.attackOpportunity.target() == victim ? killer.attackOpportunity : null;
        var reason = opportunity == null ? inferredKillReason(killer, victim,
                victimStateAtImpact, backstab) : opportunity.primaryReason();
        var coverFailure = explainCoverFailure(victim, victimAlly, killer,
                allyPunishEta, recoveryEta);
        var finalCause = explainDefensiveFailure(killer, victim, victimStateAtImpact,
                victimGuardMatched, backstab);
        var explanation = new KillExplanation(simulationTime, killer, victim, reason,
                killer.targetStateAtAttackCommit, victimStateAtImpact,
                killer.targetFocusedOnOtherAtAttackCommit, victimGuardMatched, victimAlly,
                allyPunishEta, recoveryEta, coverFailure, finalCause);
        killExplanations.add(explanation);
        if (killExplanations.size() > 12) killExplanations.removeFirst();
        recordKillExplanationMetrics(explanation, opportunity);
        logTactical("KILL_EXPLANATION", "killer=" + killer.name(UiLanguage.FRENCH) +
                " victim=" + victim.name(UiLanguage.FRENCH) + " opening=" + reason +
                " commitState=" + explanation.victimStateAtCommit() +
                " impactState=" + victimStateAtImpact + " allyPunishEta=" +
                "%.2f".formatted(allyPunishEta) + " recoveryEta=" +
                "%.2f".formatted(recoveryEta) + " coverFailure=" + coverFailure +
                " finalCause=" + finalCause);
    }

    private Musketeer bestGroup3Coverer(Musketeer victim, Musketeer killer) {
        var group = activeGroup3v3();
        if (group == null) return null;
        CoverLink best = null;
        for (var link : group.coverNetwork(victim.team).incomingLinks.get(victim))
            if (link.threateningEnemy() == killer &&
                    (best == null || link.punishEta() < best.punishEta())) best = link;
        return best == null ? null : best.protector();
    }

    private String explainDefensiveFailure(Musketeer killer, Musketeer victim,
                                           CombatState impactState, boolean guardMatched,
                                           boolean backstab) {
        if (backstab) return "contact_arriere_impossible_a_bloquer";
        var awareness = victim.opponentAwareness.get(killer);
        if (awareness == null || !awareness.visible && awareness.memoryTime <= 0)
            return "attaque_non_detectee";
        if (killer.targetFocusedOnOtherAtAttackCommit) return "attention_dirigee_vers_autre_ennemi";
        if (impactState == CombatState.RECOVERY || impactState == CombatState.BLOCK_STUN)
            return "victime_en_recuperation";
        if (impactState == CombatState.ATTACK_PREPARE || impactState == CombatState.ATTACK_HOLD ||
                impactState == CombatState.ATTACK_ACTIVE || impactState == CombatState.CHAMBER_PREPARE ||
                impactState == CombatState.CHAMBER_HOLD || impactState == CombatState.CHAMBER_ACTIVE)
            return "victime_occupee_par_son_attaque";
        if (impactState == CombatState.BLOCK_PREPARE) return "changement_de_garde_commence_trop_tard";
        if (victim.isGuarding() && !victim.blockDirection.blocks(killer.attackDirection))
            return "garde_deja_engagee_dans_autre_direction";
        if (guardMatched) return "erreur_execution_defensive_garde_correcte";
        if (victim.reactionTime > 0 || victim.threatResponseDelay > 0) return "reaction_trop_lente";
        var lateral = Math.abs(victim.velocity.dot(new Pos(-Math.sin(victim.angle),
                Math.cos(victim.angle))));
        return lateral > 0.08 ? "sortie_laterale_insuffisante" : "erreur_execution_defensive";
    }

    String defensiveFailureForTest(Musketeer killer, Musketeer victim,
                                   CombatState impactState, boolean guardMatched,
                                   boolean backstab) {
        return explainDefensiveFailure(killer, victim, impactState, guardMatched, backstab);
    }

    private String explainCoverFailure(Musketeer victim, Musketeer ally, Musketeer killer,
                                       double punishEta, double recoveryEta) {
        if (ally == null || !ally.alive) return "aucun_allie_survivant";
        var awareness = ally.opponentAwareness.get(killer);
        if (awareness == null || !awareness.visible && awareness.memoryTime <= 0)
            return "opportunite_de_couverture_non_percue";
        if (ally.state == CombatState.RECOVERY || ally.state == CombatState.BLOCK_STUN)
            return "allie_en_recuperation";
        if (ally.isAttacking() || ally.state == CombatState.ATTACK_PREPARE ||
                ally.state == CombatState.ATTACK_HOLD) return "allie_deja_engage";
        if (distanceToSegment(victim.pos, ally.pos, killer.pos) < Musketeer.BODY_RADIUS * 0.92)
            return "ligne_de_couverture_bloquee";
        var distance = ally.pos.minus(killer.pos).hypotenuse();
        if (distance > ally.projectedThrustLength() + Musketeer.BODY_RADIUS + 0.42)
            return "allie_trop_loin";
        var alignment = Math.abs(angleDifference(ally.weaponWorldAngle(),
                killer.pos.minus(ally.pos).radians()));
        if (alignment > Math.toRadians(48)) return "allie_mal_aligne";
        if (!Double.isFinite(punishEta)) return "aucune_ligne_physique_de_punition";
        if (punishEta > recoveryEta)
            return "punition_possible_seulement_apres_recuperation_du_tueur";
        if (punishEta > 0) return "punition_possible_mais_apres_impact";
        return "punition_possible_sans_prevenir_impact";
    }

    private void attachKillCausalChain(KillExplanation kill) {
        if (kill == null) return;
        DecisionTrace attack = null, teamManeuver = null, movement = null, reaction = null, cover = null;
        for (var trace : decisionTraces) {
            if (trace.time() > kill.time()) continue;
            if (trace.agent() == kill.killer() && trace.target() == kill.victim() &&
                    (trace.category() == DecisionCategory.ATTACK ||
                            trace.category() == DecisionCategory.REVENGE ||
                            trace.category() == DecisionCategory.COUNTER_COVER) &&
                    !trace.action().equals("HIT") && !trace.action().equals("KILL") &&
                    !trace.action().equals("MISSED") && !trace.action().equals("BLOCKED")) attack = trace;
        }
        var attackTime = attack == null ? kill.time() - 1.5 : attack.time();
        var resolution = new ArrayList<DecisionTrace>();
        for (var trace : decisionTraces)
            if (trace.time() >= attackTime - 3.0 && trace.time() <= attackTime + 0.0001 &&
                    trace.agent() == kill.killer()) {
                if (trace.category() == DecisionCategory.TEAM_MANEUVER) teamManeuver = trace;
                else if (trace.category() == DecisionCategory.LOCAL_MOVEMENT) movement = trace;
            }
        for (var trace : decisionTraces) {
            if (trace.time() < attackTime - 0.0001 || trace.time() > kill.time() + 0.0001) continue;
            if (trace.agent() == kill.victim() &&
                    (trace.category() == DecisionCategory.BLOCK ||
                            trace.category() == DecisionCategory.LOCAL_MOVEMENT ||
                            trace.category() == DecisionCategory.TARGET_ATTENTION)) reaction = trace;
            if (kill.victimAlly() != null && trace.agent() == kill.victimAlly() &&
                    (activeGroup3v3() == null || trace.target() == kill.killer()) &&
                    (trace.category() == DecisionCategory.COUNTER_COVER ||
                            trace.category() == DecisionCategory.BLOCK ||
                            trace.category() == DecisionCategory.LOCAL_MOVEMENT)) cover = trace;
            if (trace.agent() == kill.killer() && trace.target() == kill.victim() &&
                    trace.category() == DecisionCategory.ATTACK) resolution.add(trace);
        }
        var chain = new KillCausalChain(kill, teamManeuver, movement, attack, reaction, cover,
                List.copyOf(resolution));
        killCausalChains.add(chain);
        if (killCausalChains.size() > 12) killCausalChains.removeFirst();
        if (chain.completeOrExplicit()) ++modernMetrics.killsWithCompleteCausalChain;
        if (chain.victimReaction() == null) ++modernMetrics.killsWithNoObservedVictimReaction;
        if (kill.coverFailureReason().contains("apres")) ++modernMetrics.killsWithLateCover;
        if (kill.coverFailureReason().contains("ligne")) ++modernMetrics.killsWithNoCoverLine;
        if (kill.finalCause().contains("garde_deja")) ++modernMetrics.killsWithVictimWrongGuard;
        if (kill.finalCause().contains("occupee")) ++modernMetrics.killsWithVictimOccupied;
    }

    private AttackOpportunityReason inferredKillReason(Musketeer killer, Musketeer victim,
                                                        CombatState victimStateAtImpact,
                                                        boolean backstab) {
        if (killer.groupIntent == GroupIntent.COMMIT_REVENGE ||
                killer.groupIntent == GroupIntent.COUNTER_COVER ||
                killer.chamberType != ChamberType.NONE)
            return AttackOpportunityReason.COUNTER_STAB_WINDOW;
        if (backstab || killer.targetFocusedOnOtherAtAttackCommit)
            return AttackOpportunityReason.TARGET_FOCUSED_ON_ALLY;
        if (victimStateAtImpact == CombatState.RECOVERY ||
                victimStateAtImpact == CombatState.BLOCK_STUN)
            return AttackOpportunityReason.TARGET_RECOVERING;
        if (victimStateAtImpact == CombatState.ATTACK_ACTIVE ||
                victimStateAtImpact == CombatState.CHAMBER_ACTIVE)
            return AttackOpportunityReason.TARGET_ATTACK_COMMITTED;
        return AttackOpportunityReason.RISKY_INITIATIVE;
    }

    private void recordKillExplanationMetrics(KillExplanation explanation,
                                              AttackOpportunity opportunity) {
        switch (explanation.initiatingReason()) {
            case TARGET_FOCUSED_ON_ALLY -> ++modernMetrics.killsTargetFocusedOnAlly;
            case TARGET_ATTACK_PREPARING, TARGET_ATTACK_COMMITTED,
                 TARGET_ATTACK_MISALIGNED, COUNTER_STAB_WINDOW ->
                    ++modernMetrics.killsTargetAttackCommitted;
            case TARGET_BLOCK_COMMITTED, TARGET_WRONG_GUARD, TARGET_SWITCHING_GUARD,
                 TARGET_BLOCK_STUNNED -> ++modernMetrics.killsTargetBlockCommitted;
            case TARGET_RECOVERING -> ++modernMetrics.killsTargetRecovering;
            case TARGET_OVEREXTENDED -> ++modernMetrics.killsTargetOverextended;
            case TARGET_LOST_ALLY_COVER, OPPOSING_COVERER_OCCUPIED ->
                    ++modernMetrics.killsLostAllyCover;
            case CROSS_ANGLE_CREATED, TARGET_LINE_OPEN, TARGET_MOVING_INTO_LINE ->
                    ++modernMetrics.killsCrossAngle;
            case RISKY_INITIATIVE -> ++modernMetrics.killsRiskyInitiative;
        }
        if (Double.isFinite(explanation.victimAllyPunishEta())) {
            modernMetrics.coverArrivalDelayOnKillSum +=
                    explanation.victimAllyPunishEta() - explanation.killerRecoveryEta();
            ++modernMetrics.coverArrivalDelayKillSamples;
        }
        if (opportunity != null) {
            modernMetrics.attackWindowOnKillSum += opportunity.availableWindow();
            modernMetrics.impactEtaOnKillSum += opportunity.impactEta();
            ++modernMetrics.killAttackOpportunitySamples;
        }
        ++modernMetrics.killExplanationSamples;
    }

    private void classifyFlankDeath(Musketeer attacker, Musketeer defeated) {
        var engagement = defeated.engagement;
        if (engagement == null || engagement.type != EngagementType.TWO_VS_ONE) return;
        if (defeated == engagement.flanker) {
            changeFlankPhase(defeated, FlankPhase.ABORTED, "flanker_died");
            return;
        }
        if (defeated != engagement.primaryTarget || attacker.controlledBackstabAttempt ||
                engagement.flanker == null || !engagement.flanker.alive) return;
        changeFlankPhase(engagement.flanker, FlankPhase.COMPLETED,
                "defender_died_before_backstab");
    }

    private void updateWinner() {
        var blueAlive = teamAlive(blueTeam);
        var redAlive = teamAlive(redTeam);
        if (!blueAlive && !redAlive) {
            winningTeam = Team.DRAW;
            if (scenario == SimulationScenario.LEGACY_2V2)
                for (var agent : agents) agent.recordMessage();
        }
        else if (!blueAlive) {
            winningTeam = Team.RED;
            if (scenario == SimulationScenario.LEGACY_2V2)
                for (var agent : agents) agent.recordMessage();
        }
        else if (!redAlive) {
            winningTeam = Team.BLUE;
            if (scenario == SimulationScenario.LEGACY_2V2)
                for (var agent : agents) agent.recordMessage();
        }
    }

    private boolean teamAlive(Musketeer[] team) {
        for (var agent : team)
            if (agent.alive) return true;
        return false;
    }

    private void abandonPressurePlan(Musketeer agent) {
        agent.pressureMode = false;
        agent.pressureContext = PressureContext.NONE;
        agent.pressureCounter = false;
        agent.pressureFixedTarget = null;
        agent.pressureTransferTarget = null;
        agent.pressureTransferDelay = 0;
        agent.pressureFeintCooldown = 0;
    }

    private void setState(Musketeer agent, CombatState state) {
        var previousState = agent.state;
        if (state == CombatState.RECOVERY && previousState == CombatState.ATTACK_ACTIVE &&
                agent.controlledBackstabAttempt && agent.target != null)
            completeBackstab(agent, agent.target, false,
                    isRearAttack(agent.target, agent) ? "BACKSTAB_MISSED" :
                            "BACKSTAB_LOST_DEFENDER_TURNED",
                    isRearAttack(agent.target, agent) ? "tip_collision_missed" :
                            "defender_turned_before_impact");
        var escapedPressure = state == CombatState.NEUTRAL && previousState == CombatState.BLOCKING &&
                agent.pressureEscapeActive;
        agent.state = state;
        agent.stateTime = 0;
        if ((state == CombatState.ATTACK_PREPARE || state == CombatState.CHAMBER_PREPARE) &&
                state != previousState && agent.target != null) {
            agent.targetStateAtAttackCommit = agent.target.state;
            agent.targetFocusedOnOtherAtAttackCommit = agent.target.target != null &&
                    agent.target.target != agent;
        }
        if ((state == CombatState.ATTACK_PREPARE || state == CombatState.CHAMBER_PREPARE) &&
                state != previousState) ++agent.attackSequence;
        if (state == CombatState.BLOCK_STUN || state == CombatState.RECOVERY || state == CombatState.DEAD)
            endDezone(agent);
        if (state == CombatState.RECOVERY && agent.target != null && agent.target.alive) {
            Pos d = agent.target.pos.minus(agent.pos);
            var distance = d.hypotenuse();
            if (distance > 0.0001) {
                double closingSpeed = agent.velocity.times(d).div(distance).sum();

                if (closingSpeed > 0) agent.velocity = agent.velocity.minus(d.div(distance).times(closingSpeed));
            }
        }
        if (state == CombatState.NEUTRAL || state == CombatState.DEAD) {
            agent.doubleBlockThreat = null;
            agent.doubleBlockSwitched = false;
            agent.doubleBlockSwitchTime = 0;
            agent.feintCancel = false;
            abandonPressurePlan(agent);
            agent.forcedPressureSource = null;
            agent.pressureReleaseDelay = 0;
        }
        if (state == CombatState.NEUTRAL) {
            agent.aimError = 0;
            agent.chamberType = ChamberType.NONE;
            agent.simpleChamberLethal = false;
            agent.chamberChain = 0;
            agent.attackReachScale = 1;
            agent.baitedAttack = false;
            agent.punishingBait = false;
            agent.exploitingBait = false;
            agent.freeKillAttempt = false;
            agent.freeKillPerceivedDistance = 0;
            agent.attackManipulation = AttackManipulation.NEUTRAL;
            agent.manipulationPitchOffset = 0;
            agent.manipulationYawOffset = 0;
            agent.feintCount = 0;
            agent.feintToBlock = false;
            agent.guardSwitching = false;
            agent.attackIntent = AttackIntent.HOLD;
            agent.pressureEscapeTime = 0;
            agent.pressureEscapeDelay = 0;
            agent.pressureEscapeActive = false;
            if (escapedPressure) agent.pressureReengageTime = 0.48 +
                    agent.stats.decisionMaking() * 0.002 + agent.stats.attackTiming() * 0.0015;
        } else if (state == CombatState.DEAD) {
            agent.velocity = new Pos();
            agent.desiredVelocity = new Pos();
            agent.attackIntent = AttackIntent.HOLD;
            agent.pressureEscapeTime = 0;
            agent.pressureEscapeDelay = 0;
            agent.pressureReengageTime = 0;
            agent.pressureEscapeActive = false;
        }
    }

    private double angleDifference(double from, double to) {
        return normalizeAngle(to - from);
    }

    private double normalizeAngle(double angle) {
        while (angle > Math.PI) angle -= Math.PI * 2;
        while (angle < -Math.PI) angle += Math.PI * 2;
        return angle;
    }
}
