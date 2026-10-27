final class Experimental3v3TransitionTest {
    private static final double DT = 1.0 / 120.0;

    public static void main(String[] args) {
        var test = new Experimental3v3TransitionTest();
        test.verifyATemporaryTwoVsOneRemainsACluster();
        test.verifyBDurableIsolationRequiresLostCoverAndSupportDelay();
        test.verifyCThreeVsTwoRemainsCollective();
        test.verifyDThreeVsOneRemainsCollective();
        test.verifyEHandoffsUseExistingEngagementTypes();
        test.verifyFTransitionDeterminism();
        test.verifyGNaturalMatchesReachTransitions();
        test.verifyHFirstDeathIsOneTransactionalTransition();
        test.verifyISimultaneousDeathsUseOneRosterSnapshot();
        test.verifyJProcessedRosterPreventsRepeatedApplication();
        test.verifyKControllerHandoffsAreIrreversible();
        test.verifyLDeadReferencesArePurgedAndAudited();
        test.verifyMBoundedLoggerSuppressesDuplicates();
        test.verifyNWatchdogAllowsOneLegitimateHold();
        test.verifyORepeatedRosterRequestsTriggerFailsafe();
        System.out.println("Experimental GROUP_3V3 cluster/transition tests A-O passed.");
    }

    private void verifyATemporaryTwoVsOneRemainsACluster() {
        var simulation = fixture(5_101L);
        simulation.disableGroup3AttacksForTest();
        arrangeLocalTwoVsOne(simulation, false);
        for (var tick = 0; tick < 40; ++tick) simulation.update(DT);
        var group = simulation.modernGroup3Engagement();
        var observed = false;
        for (var cluster : group.localClusters)
            if (cluster.blueMembers().size() == 2 && cluster.redMembers().size() == 1) {
                observed = true;
                require(cluster.contested() || cluster.isolationConfidence() < 0.66,
                        "A: nearby support must keep a temporary 2v1 contested (isolation=" +
                                cluster.isolationConfidence() + ", reason=" + cluster.reason() + ").");
            }
        require(observed && activeEngagements(simulation, EngagementType.GROUP_3V3) == 1 &&
                        activeEngagements(simulation, EngagementType.TWO_VS_ONE) == 0,
                "A: a local 2v1 is descriptive and must not create a permanent engagement.");
    }

    private void verifyBDurableIsolationRequiresLostCoverAndSupportDelay() {
        var simulation = fixture(5_102L);
        simulation.disableGroup3AttacksForTest();
        arrangeLocalTwoVsOne(simulation, true);
        for (var tick = 0; tick < 100; ++tick) simulation.update(DT);
        require(simulation.group3Metrics().durableIsolations > 0,
                "B: persistent numerical pressure without cover must become a durable isolation.");
        require(activeEngagements(simulation, EngagementType.TWO_VS_ONE) == 0,
                "B: even durable isolation remains inside GROUP_3V3 before an elimination.");
        var opportunity = simulation.evaluateGroup3AttackOpportunity(simulation.blueTeam[0],
                simulation.redTeam[0]);
        var evidence = simulation.modernGroup3Engagement().clusterEvidence.get(opportunity);
        require(evidence != null && evidence.localAllies() == 1 && evidence.localEnemies() >= 2 &&
                        evidence.isolationConfidence() >= 0.66 &&
                        opportunity.primaryReason() ==
                                AttackOpportunityReason.TARGET_ISOLATED_FROM_COVER_NETWORK,
                "B: a cluster-isolation attack must retain frozen physical evidence.");
    }

    private void verifyCThreeVsTwoRemainsCollective() {
        var simulation = fixture(5_103L);
        eliminate(simulation.redTeam[2]);
        simulation.update(DT);
        require(simulation.experimentalGroupPhase() == ExperimentalGroupPhase.GROUP_3V2 &&
                        simulation.modernGroup3Engagement() != null &&
                        activeEngagements(simulation, EngagementType.DUEL) == 0 &&
                        activeEngagements(simulation, EngagementType.TWO_VS_ONE) == 0,
                "C: 3v2 must remain one collective group instead of 2v1 + 1v1.");
        for (var agent : simulation.blueTeam)
            require(!agent.opponentAwareness.containsKey(simulation.redTeam[2]),
                    "C: transition must remove dead awareness references.");
    }

    private void verifyDThreeVsOneRemainsCollective() {
        var simulation = fixture(5_104L);
        eliminate(simulation.redTeam[1]);
        eliminate(simulation.redTeam[2]);
        simulation.update(DT);
        require(simulation.experimentalGroupPhase() == ExperimentalGroupPhase.GROUP_3V1 &&
                        simulation.modernGroup3Engagement() != null &&
                        activeEngagements(simulation, EngagementType.TWO_VS_ONE) == 0,
                "D: 3v1 must use the collective experimental group, not TWO_VS_ONE.");
    }

    private void verifyEHandoffsUseExistingEngagementTypes() {
        var twoVsTwo = fixture(5_105L);
        eliminate(twoVsTwo.blueTeam[2]);
        eliminate(twoVsTwo.redTeam[2]);
        twoVsTwo.update(DT);
        require(twoVsTwo.experimentalGroupPhase() == ExperimentalGroupPhase.HANDOFF_2V2 &&
                        twoVsTwo.modernGroupEngagement() != null &&
                        activeEngagements(twoVsTwo, EngagementType.GROUP_2V2) == 1,
                "E: 2v2 survivors must be transferred to the existing GROUP_2V2 engine.");

        var twoVsOne = fixture(5_106L);
        eliminate(twoVsOne.blueTeam[2]);
        eliminate(twoVsOne.redTeam[1]);
        eliminate(twoVsOne.redTeam[2]);
        twoVsOne.update(DT);
        require(twoVsOne.experimentalGroupPhase() == ExperimentalGroupPhase.HANDOFF_2V1 &&
                        activeEngagements(twoVsOne, EngagementType.TWO_VS_ONE) == 1,
                "E: 2v1 survivors must use the validated TWO_VS_ONE engagement.");

        var duel = fixture(5_107L);
        eliminate(duel.blueTeam[1]);
        eliminate(duel.blueTeam[2]);
        eliminate(duel.redTeam[1]);
        eliminate(duel.redTeam[2]);
        duel.update(DT);
        require(duel.experimentalGroupPhase() == ExperimentalGroupPhase.HANDOFF_1V1 &&
                        activeEngagements(duel, EngagementType.DUEL) == 1,
                "E: 1v1 survivors must use the existing DUEL engagement.");
    }

    private void verifyFTransitionDeterminism() {
        var first = fixture(5_108L);
        var second = fixture(5_108L);
        eliminate(first.redTeam[2]);
        eliminate(second.redTeam[2]);
        for (var tick = 0; tick < 240; ++tick) {
            first.update(DT);
            second.update(DT);
        }
        require(first.experimentalGroupPhase() == second.experimentalGroupPhase() &&
                        first.group3Metrics().clustersDetected ==
                                second.group3Metrics().clustersDetected &&
                        first.group3Metrics().group3v2Created ==
                                second.group3Metrics().group3v2Created,
                "F: equal seeds and eliminations must reproduce clusters and transition phase.");
    }

    private void verifyGNaturalMatchesReachTransitions() {
        var completed = 0;
        var collectiveTransitions = 0;
        var engineHandoffs = 0;
        for (var seed = 5_200L; seed < 5_210L; ++seed) {
            var simulation = fixture(seed);
            for (var tick = 0; tick < 14_400 && !simulation.experimentalRoundComplete(); ++tick)
                simulation.update(DT);
            if (simulation.experimentalRoundComplete()) ++completed;
            collectiveTransitions += simulation.group3Metrics().group3v2Created +
                    simulation.group3Metrics().group3v1Created;
            engineHandoffs += simulation.group3Metrics().handoffsTo2v2 +
                    simulation.group3Metrics().handoffsTo2v1 +
                    simulation.group3Metrics().handoffsTo1v1;
            for (var agent : simulation.agents)
                require(Double.isFinite(agent.pos.x()) && Double.isFinite(agent.pos.y()),
                        "G: transitions must never create invalid positions.");
        }
        require(completed >= 8 && collectiveTransitions > 0 && engineHandoffs > 0,
                "G: natural matches must complete through collective transitions and existing engines " +
                        "(completed=" + completed + ", collective=" + collectiveTransitions +
                        ", handoffs=" + engineHandoffs + ").");
    }

    private void verifyHFirstDeathIsOneTransactionalTransition() {
        var simulation = fixture(5_211L);
        eliminate(simulation.redTeam[2]);
        simulation.update(DT);
        var metrics = simulation.group3Metrics();
        var traces = simulation.transitionTraces();
        require(metrics.transitionRequests == 1 && metrics.transitionApplications == 1 &&
                        metrics.duplicateTransitionApplications == 0 && traces.size() == 2,
                "H: one roster change must create one request and one applied conclusion.");
        require(traces.getFirst().transitionId() == traces.getLast().transitionId() &&
                        !traces.getFirst().applied() && traces.getLast().applied() &&
                        traces.getLast().requestedPhase().equals("GROUP_3V2") &&
                        traces.getLast().phaseAfter().equals("GROUP_3V2") &&
                        simulation.processedRoster().equals(new RosterSnapshot(0b111, 0b011, 3, 2)),
                "H: request and conclusion must share an id and install the processed 111|011 roster.");
    }

    private void verifyISimultaneousDeathsUseOneRosterSnapshot() {
        var simulation = fixture(5_212L);
        eliminate(simulation.blueTeam[2]);
        eliminate(simulation.redTeam[2]);
        simulation.update(DT);
        var metrics = simulation.group3Metrics();
        var applied = simulation.transitionTraces().getLast();
        require(metrics.transitionRequests == 1 && metrics.transitionApplications == 1 &&
                        applied.applied() && applied.reason().equals("SIMULTANEOUS_ELIMINATIONS") &&
                        applied.blueMaskAfter() == 0b011 && applied.redMaskAfter() == 0b011 &&
                        simulation.experimentalGroupPhase() == ExperimentalGroupPhase.HANDOFF_2V2,
                "I: simultaneous deaths must install one final 2v2 roster without an intermediate state.");
    }

    private void verifyJProcessedRosterPreventsRepeatedApplication() {
        var simulation = fixture(5_213L);
        eliminate(simulation.redTeam[2]);
        simulation.update(DT);
        for (var tick = 0; tick < 1_000; ++tick) simulation.update(DT);
        var metrics = simulation.group3Metrics();
        require(metrics.transitionRequests == 1 && metrics.transitionApplications == 1 &&
                        metrics.recursiveTransitionAttempts == 0 &&
                        metrics.multipleTransitionAttemptsSameTick == 0,
                "J: an unchanged processed roster must never be reconstructed on later ticks.");
    }

    private void verifyKControllerHandoffsAreIrreversible() {
        var simulation = fixture(5_214L);
        eliminate(simulation.blueTeam[2]);
        eliminate(simulation.redTeam[2]);
        simulation.update(DT);
        require(simulation.activeCombatController() == ActiveCombatController.GROUP_2V2 &&
                        simulation.scenario() == SimulationScenario.CONTROLLED_3V3,
                "K: the initial scenario must remain 3v3 while GROUP_2V2 becomes the active controller.");
        var groupTicks = simulation.group3Metrics().group3v3ControllerTicks +
                simulation.group3Metrics().group3v2ControllerTicks +
                simulation.group3Metrics().group3v1ControllerTicks;
        simulation.update(DT);
        require(simulation.group3Metrics().group2v2ControllerTicks == 1 &&
                        groupTicks == simulation.group3Metrics().group3v3ControllerTicks +
                                simulation.group3Metrics().group3v2ControllerTicks +
                                simulation.group3Metrics().group3v1ControllerTicks,
                "K: GROUP_2V2 must receive the next tick without re-entering a 3v3 controller.");

        eliminate(simulation.redTeam[1]);
        simulation.update(DT);
        require(simulation.activeCombatController() == ActiveCombatController.TWO_VS_ONE,
                "K: a 2v2 elimination must hand control to TWO_VS_ONE.");
        var group2v2Ticks = simulation.group3Metrics().group2v2ControllerTicks;
        simulation.update(DT);
        require(simulation.group3Metrics().twoVsOneControllerTicks == 1 &&
                        simulation.group3Metrics().group2v2ControllerTicks == group2v2Ticks,
                "K: TWO_VS_ONE must advance without returning to GROUP_2V2 or GROUP_3V3.");

        eliminate(simulation.blueTeam[1]);
        simulation.update(DT);
        require(simulation.activeCombatController() == ActiveCombatController.DUEL,
                "K: eliminating one attacker from 2v1 must hand control to DUEL.");
        var twoVsOneTicks = simulation.group3Metrics().twoVsOneControllerTicks;
        simulation.update(DT);
        require(simulation.group3Metrics().duelControllerTicks == 1 &&
                        simulation.group3Metrics().twoVsOneControllerTicks == twoVsOneTicks &&
                        simulation.transitionTraces().getLast().controllerAfter().equals("DUEL"),
                "K: DUEL must remain the sole controller after the final handoff.");
    }

    private void verifyLDeadReferencesArePurgedAndAudited() {
        var simulation = fixture(5_215L);
        simulation.disableGroup3AttacksForTest();
        var survivor = simulation.blueTeam[0];
        var dead = simulation.redTeam[2];
        survivor.target = dead;
        survivor.plannedTarget = dead;
        survivor.primaryMark = dead;
        survivor.coveredBy = dead;
        survivor.tacticalThreat = dead;
        survivor.pressureSource = dead;
        survivor.movementIntent.target = dead;
        survivor.attentionAllocation.primaryThreat = dead;
        survivor.attentionAllocation.pendingPrimary = dead;
        survivor.multiThreatAttention.focusedThreat = dead;
        survivor.opponentAwareness.put(dead, new OpponentAwareness(dead.pos, true));
        eliminate(dead);
        simulation.update(DT);
        var audit = simulation.lastDeadReferenceAudit();
        require(audit.clean() && audit.details().isEmpty() &&
                        simulation.group3Metrics().deadReferencePurges == 1 &&
                        simulation.group3Metrics().deadReferencesAfterTransition == 0,
                "L: every active reference to an eliminated Agent must be purged and audit cleanly.");
        require(survivor.target != dead && survivor.plannedTarget == null &&
                        survivor.primaryMark == null && survivor.coveredBy == null &&
                        survivor.tacticalThreat == null && survivor.pressureSource == null &&
                        survivor.movementIntent.target == null &&
                        !survivor.opponentAwareness.containsKey(dead),
                "L: target, plan, cover, pressure, movement and awareness references must be removed.");
    }

    private void verifyMBoundedLoggerSuppressesDuplicates() {
        var logger = new TransitionLogger(false);
        for (var n = 0; n < 1_000; ++n) logger.emitOnce("same_error", "same error");
        require(logger.lines().size() == 1 && logger.duplicateConsoleLinesSuppressed() == 999 &&
                        logger.consoleLinesEmitted() == 0,
                "M: one invalid condition must not emit thousands of identical console lines.");
        for (var n = 0; n < 300; ++n) logger.emitOnce("unique_" + n, "line " + n);
        require(logger.lines().size() == 256 && logger.storedLinesDropped() == 45,
                "M: the structured transition log must remain bounded to 256 lines.");
    }

    private void verifyNWatchdogAllowsOneLegitimateHold() {
        var simulation = fixture(5_216L);
        simulation.disableGroup3AttacksForTest();
        var holder = simulation.blueTeam[0];
        holder.target = simulation.redTeam[0];
        holder.state = CombatState.ATTACK_HOLD;
        holder.stateTime = holder.prepareTime();
        for (var tick = 0; tick < 720; ++tick) simulation.update(DT);
        var watchdog = simulation.progressWatchdogMetrics();
        require(watchdog.logicalFreezeReports() == 0 &&
                        simulation.group3Metrics().repeatedTransitionStateTicks == 0,
                "N: one indefinite Hold is legitimate while other Agents or states keep progressing.");

        var transitioned = fixture(5_217L);
        eliminate(transitioned.redTeam[2]);
        transitioned.update(DT);
        require(transitioned.transitionLog().size() == 2 &&
                        transitioned.consoleLinesEmitted() == 0 &&
                        transitioned.transitionLog().getFirst().startsWith("TRANSITION_REQUEST") &&
                        transitioned.transitionLog().getLast().startsWith("TRANSITION_APPLIED"),
                "N: a transition must produce one structured request and one conclusion without console output.");
    }

    private void verifyORepeatedRosterRequestsTriggerFailsafe() {
        var simulation = fixture(5_218L);
        var repeatedRoster = new RosterSnapshot(0b111, 0b011, 3, 2);
        simulation.requestRosterTransitionForTest(repeatedRoster);
        simulation.requestRosterTransitionForTest(repeatedRoster);
        simulation.requestRosterTransitionForTest(repeatedRoster);
        var report = simulation.transitionFailsafeReport();
        require(report != null && report.roster().equals(repeatedRoster) &&
                        report.processedRoster().equals(new RosterSnapshot(0b111, 0b111, 3, 3)) &&
                        simulation.group3Metrics().transitionFailsafePauses == 1 &&
                        simulation.transitionLog().size() == 1 &&
                        simulation.transitionLog().getFirst().startsWith("TRANSITION_FAILSAFE"),
                "O: three requests for the same unprocessed roster must pause once and emit one report.");
        simulation.requestRosterTransitionForTest(repeatedRoster);
        require(simulation.group3Metrics().transitionFailsafePauses == 1 &&
                        simulation.transitionLog().size() == 1,
                "O: an active failsafe must suppress repeated reports and log lines.");
    }

    private void arrangeLocalTwoVsOne(DuelSimulation simulation, boolean isolate) {
        simulation.blueTeam[0].pos = new Pos(3.0, 3.0);
        simulation.blueTeam[1].pos = new Pos(3.0, 4.0);
        simulation.redTeam[0].pos = new Pos(4.35, 3.5);
        simulation.blueTeam[2].pos = new Pos(0.55, 6.35);
        simulation.redTeam[1].pos = isolate ? new Pos(7.35, 0.55) : new Pos(6.25, 3.5);
        simulation.redTeam[2].pos = new Pos(7.35, 6.35);
    }

    private int activeEngagements(DuelSimulation simulation, EngagementType type) {
        var count = 0;
        for (var engagement : simulation.engagements())
            if (engagement.type == type && engagement.status != EngagementStatus.BROKEN &&
                    engagement.status != EngagementStatus.FINISHED) ++count;
        return count;
    }

    private void eliminate(Musketeer agent) {
        agent.alive = false;
        agent.state = CombatState.DEAD;
    }

    private DuelSimulation fixture(long seed) {
        var blue = new TeamConfiguration3("Blue", AgentConfiguration.defaultBlue(),
                AgentConfiguration.defaultBlueTwo(), AgentConfiguration.defaultBlueThree());
        var red = new TeamConfiguration3("Red", AgentConfiguration.defaultRed(),
                AgentConfiguration.defaultRedTwo(), AgentConfiguration.defaultRedThree());
        return new DuelSimulation(blue, red, SimulationScenario.CONTROLLED_3V3, seed);
    }

    private void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
