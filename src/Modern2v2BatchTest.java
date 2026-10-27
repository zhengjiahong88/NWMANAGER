import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;

final class Modern2v2BatchTest {
    private static final double DT = 1.0 / 120.0, MAX_TIME = 60, STALEMATE_TIME = 15;

    public static void main(String[] args) {
        var test = new Modern2v2BatchTest();
        var metrics = new Metrics();
        var seedCount = args.length == 0 ? 100 : Integer.parseInt(args[0]);
        metrics.seedCount = seedCount;
        for (var seed = 1L; seed <= seedCount; ++seed) test.runMatch(seed, metrics);
        metrics.print();
        test.require(metrics.invalidPositions == 0 && metrics.nanValues == 0 &&
                        metrics.infiniteValues == 0 && metrics.arenaExits == 0,
                "The 100-seed batch produced invalid physical data.");
        test.require(metrics.historicalSystemActivations == 0,
                "A retired historical subsystem became active in the modern batch.");
        test.require(metrics.initialGroupEngagements == seedCount,
                "Every modern match must create exactly one initial GROUP_2V2.");
        test.require(metrics.attackCommits > 0 && metrics.revengeOpportunities > 0,
                "Normal groupfight seeds must produce attacks and revenge readings.");
        test.require(metrics.postEliminationTwoVsOne > 0,
                "Post-elimination 2v1 reconstruction must remain active.");
        test.require(metrics.engagementThrashing == 0 && metrics.targetThrashing == 0 &&
                        metrics.intentThrashing == 0 && metrics.primaryMarkThrashing == 0,
                "The groupfight produced structural, target, intent, or mark thrashing.");
        test.require(metrics.stationaryEpisodes > 0 && metrics.weaponLineAvoidances > 0,
                "The batch must measure real individual stops and weapon-line avoidance decisions.");
        test.require(metrics.collectiveRotationBiasContribution >
                        metrics.localManeuverContribution &&
                        metrics.localManeuverContribution > 0,
                "Persistent duo maneuvers must lead without suppressing local corrections.");
        test.require(metrics.matchesWithFirstDeathCausalChain == seedCount,
                "Every first death must produce a causal chain.");
        test.require(metrics.determinismFailures == 0,
                "Every seed must preserve its physical and explanatory replay.");
        System.out.println("Modern 2v2 batch test passed for seeds 1-" + seedCount + ".");
    }

    private void runMatch(long seed, Metrics metrics) {
        var simulation = modern(seed);
        var targetThrashingBefore = metrics.targetThrashing;
        metrics.initialGroupEngagements += activeGroups(simulation);
        var previousHitPoints = totalHitPoints(simulation);
        var previousEngagement = activeEngagementSignature(simulation);
        var previousTargets = new Musketeer[simulation.agents.length];
        var previousIntents = new TacticalIntent[simulation.agents.length];
        var previousMarks = new Musketeer[simulation.agents.length];
        var structuralChanges = new ArrayDeque<Double>();
        @SuppressWarnings("unchecked")
        var targetChanges = (ArrayDeque<Double>[]) new ArrayDeque<?>[simulation.agents.length];
        @SuppressWarnings("unchecked")
        var intentChanges = (ArrayDeque<Double>[]) new ArrayDeque<?>[simulation.agents.length];
        @SuppressWarnings("unchecked")
        var markChanges = (ArrayDeque<Double>[]) new ArrayDeque<?>[simulation.agents.length];
        for (var n = 0; n < simulation.agents.length; ++n) {
            previousTargets[n] = simulation.agents[n].target;
            previousIntents[n] = simulation.agents[n].tacticalIntent;
            previousMarks[n] = simulation.agents[n].primaryMark;
            targetChanges[n] = new ArrayDeque<>();
            intentChanges[n] = new ArrayDeque<>();
            markChanges[n] = new ArrayDeque<>();
        }
        var lastUsefulProgress = 0.0;
        while (simulation.winningTeam == null && simulation.simulationTime < MAX_TIME) {
            simulation.update(DT);
            inspectPhysicalState(simulation, metrics);
            inspectRetiredSystems(simulation, metrics);
            var engagement = activeEngagementSignature(simulation);
            if (!engagement.equals(previousEngagement)) {
                lastUsefulProgress = simulation.simulationTime;
                previousEngagement = engagement;
                registerChange(structuralChanges, simulation.simulationTime);
                if (structuralChanges.size() > 3) ++metrics.engagementThrashing;
            }
            var hitPoints = totalHitPoints(simulation);
            if (hitPoints != previousHitPoints) {
                previousHitPoints = hitPoints;
                lastUsefulProgress = simulation.simulationTime;
            }
            if (hasActiveAttack(simulation)) lastUsefulProgress = simulation.simulationTime;
            for (var n = 0; n < simulation.agents.length; ++n) {
                var agent = simulation.agents[n];
                if (agent.target != previousTargets[n]) {
                    previousTargets[n] = agent.target;
                    registerChange(targetChanges[n], simulation.simulationTime);
                    if (targetChanges[n].size() > 5) ++metrics.targetThrashing;
                }
                if (agent.tacticalIntent != previousIntents[n]) {
                    previousIntents[n] = agent.tacticalIntent;
                    registerChange(intentChanges[n], simulation.simulationTime);
                    if (intentChanges[n].size() > 4) ++metrics.intentThrashing;
                }
                if (agent.primaryMark != previousMarks[n]) {
                    previousMarks[n] = agent.primaryMark;
                    registerChange(markChanges[n], simulation.simulationTime);
                    if (markChanges[n].size() > 3) ++metrics.primaryMarkThrashing;
                }
            }
        }
        if (simulation.winningTeam == null) {
            ++metrics.timeouts;
            metrics.timeoutSeeds.add(seed);
            var report = TimeoutReport.capture(seed, simulation);
            metrics.timeoutCauses.merge(report.cause, 1, Integer::sum);
            metrics.timeoutReports.add(report.snapshot);
            if (simulation.simulationTime - lastUsefulProgress >= STALEMATE_TIME) ++metrics.stalemates;
        } else {
            ++metrics.matchesCompleted;
            switch (simulation.winningTeam) {
                case BLUE -> ++metrics.blueWins;
                case RED -> ++metrics.redWins;
                case DRAW -> ++metrics.draws;
            }
        }
        var modern = simulation.modernMetrics();
        if (!simulation.killCausalChains().isEmpty()) ++metrics.matchesWithFirstDeathCausalChain;
        if (!deterministicSnapshot(simulation).equals(replaySnapshot(seed)))
            ++metrics.determinismFailures;
        metrics.groupEngagementsCreated += modern.groupEngagementsCreated;
        metrics.teamManeuversStarted += modern.teamManeuversStarted;
        merge(metrics.teamManeuversByType, modern.teamManeuversByType);
        mergeDoubles(metrics.teamManeuverDurationByType, modern.teamManeuverDurationByType);
        metrics.teamManeuverContribution += modern.teamManeuverContribution;
        metrics.teamLateralDistance += modern.teamLateralDistance;
        metrics.teamForwardDistance += modern.teamForwardDistance;
        metrics.innerOuterSpeedDifference += modern.innerOuterSpeedDifference;
        metrics.innerOuterSpeedSamples += modern.innerOuterSpeedSamples;
        metrics.intentionalHoldDuration += modern.intentionalHoldDuration;
        metrics.incidentalLowSpeedDuration += modern.incidentalLowSpeedDuration;
        metrics.formationAreaVariation += modern.formationAreaVariation;
        metrics.partnerDistanceVariation += modern.partnerDistanceVariation;
        metrics.groupRotations += modern.groupRotations;
        metrics.groupRotationDuration += modern.groupRotationDuration;
        metrics.requestedTangentialVelocitySum += modern.requestedTangentialVelocitySum;
        metrics.appliedTangentialVelocitySum += modern.appliedTangentialVelocitySum;
        metrics.actualTangentialVelocitySum += modern.actualTangentialVelocitySum;
        metrics.radialCorrectionSum += modern.radialCorrectionSum;
        metrics.formationCorrectionSum += modern.formationCorrectionSum;
        metrics.partnerCorrectionSum += modern.partnerCorrectionSum;
        metrics.avoidanceCorrectionSum += modern.avoidanceCorrectionSum;
        metrics.boundaryCorrectionSum += modern.boundaryCorrectionSum;
        metrics.backwardPenaltyLossSum += modern.backwardPenaltyLossSum;
        metrics.normalizationLossSum += modern.normalizationLossSum;
        metrics.inertiaLossSum += modern.inertiaLossSum;
        metrics.steeringSamples += modern.steeringSamples;
        metrics.actualTangentialVelocitySamples += modern.actualTangentialVelocitySamples;
        metrics.localManeuversStarted += modern.localManeuversStarted;
        metrics.localManeuversCompleted += modern.localManeuversCompleted;
        metrics.localManeuversInterrupted += modern.localManeuversInterrupted;
        metrics.holds += modern.holds;
        metrics.lateralSteps += modern.lateralSteps;
        metrics.diagonalAdvances += modern.diagonalAdvances;
        metrics.giveGroundManeuvers += modern.giveGroundManeuvers;
        metrics.weaponLineAvoidances += modern.weaponLineAvoidances;
        metrics.crossCoverManeuvers += modern.crossCoverManeuvers;
        metrics.attackLineCreations += modern.attackLineCreations;
        metrics.localDirectionReversals += modern.localDirectionReversals;
        metrics.maneuverDurationSum += modern.maneuverDurationSum;
        metrics.stationaryDuration += modern.stationaryDuration;
        metrics.stationaryEpisodes += modern.stationaryEpisodes;
        metrics.collectiveRotationBiasContribution += modern.collectiveRotationBiasContribution;
        metrics.localManeuverContribution += modern.localManeuverContribution;
        metrics.groupCorrectionContribution += modern.groupCorrectionContribution;
        metrics.weaponAvoidanceContribution += modern.weaponAvoidanceContribution;
        metrics.collisionAvoidanceContribution += modern.collisionAvoidanceContribution;
        metrics.movementBoundaryContribution += modern.movementBoundaryContribution;
        metrics.movementContributionSamples += modern.movementContributionSamples;
        merge(metrics.attackOpportunitiesByReason, modern.attackOpportunitiesByReason);
        merge(metrics.attackOpportunitiesAcceptedByReason,
                modern.attackOpportunitiesAcceptedByReason);
        merge(metrics.attackOpportunitiesRejectedByReason,
                modern.attackOpportunitiesRejectedByReason);
        merge(metrics.attackHitsByReason, modern.attackHitsByReason);
        merge(metrics.attackKillsByReason, modern.attackKillsByReason);
        merge(metrics.attackOpportunityRejections, modern.attackOpportunityRejections);
        metrics.attackWindowSum += modern.attackWindowSum;
        metrics.attackImpactEtaSum += modern.attackImpactEtaSum;
        metrics.acceptedAttackOpportunitySamples += modern.acceptedAttackOpportunitySamples;
        metrics.killsTargetFocusedOnAlly += modern.killsTargetFocusedOnAlly;
        metrics.killsTargetAttackCommitted += modern.killsTargetAttackCommitted;
        metrics.killsTargetBlockCommitted += modern.killsTargetBlockCommitted;
        metrics.killsTargetRecovering += modern.killsTargetRecovering;
        metrics.killsTargetOverextended += modern.killsTargetOverextended;
        metrics.killsLostAllyCover += modern.killsLostAllyCover;
        metrics.killsCrossAngle += modern.killsCrossAngle;
        metrics.killsRiskyInitiative += modern.killsRiskyInitiative;
        metrics.coverArrivalDelayOnKillSum += modern.coverArrivalDelayOnKillSum;
        metrics.attackWindowOnKillSum += modern.attackWindowOnKillSum;
        metrics.impactEtaOnKillSum += modern.impactEtaOnKillSum;
        metrics.killExplanationSamples += modern.killExplanationSamples;
        metrics.killAttackOpportunitySamples += modern.killAttackOpportunitySamples;
        metrics.coverArrivalDelayKillSamples += modern.coverArrivalDelayKillSamples;
        metrics.movingIntoLineOpportunities += modern.movingIntoLineOpportunities;
        metrics.movingIntoLineAccepted += modern.movingIntoLineAccepted;
        metrics.movingIntoLineRejected += modern.movingIntoLineRejected;
        metrics.movingIntoLineHits += modern.movingIntoLineHits;
        metrics.movingIntoLineKills += modern.movingIntoLineKills;
        metrics.movingIntoLineLowEvidence += modern.movingIntoLineLowEvidence;
        metrics.killsWithCompleteCausalChain += modern.killsWithCompleteCausalChain;
        metrics.killsWithNoObservedVictimReaction += modern.killsWithNoObservedVictimReaction;
        metrics.killsWithLateCover += modern.killsWithLateCover;
        metrics.killsWithNoCoverLine += modern.killsWithNoCoverLine;
        metrics.killsWithVictimWrongGuard += modern.killsWithVictimWrongGuard;
        metrics.killsWithVictimOccupied += modern.killsWithVictimOccupied;
        metrics.groupAngularDisplacement += modern.groupAngularDisplacement;
        metrics.teamCenterAngularDisplacement += modern.teamCenterAngularDisplacement;
        metrics.individualTangentialDistance += modern.individualTangentialDistance;
        metrics.rotationDirectionChanges += modern.rotationDirectionChanges;
        metrics.rotationsBelowVisibleThreshold += modern.rotationsBelowVisibleThreshold;
        metrics.partnerDistanceSum += modern.partnerDistanceSum;
        metrics.partnerDistanceSamples += modern.partnerDistanceSamples;
        metrics.crossThreatDistanceSum += modern.crossThreatDistanceSum;
        metrics.crossThreatDistanceSamples += modern.crossThreatDistanceSamples;
        metrics.physicalCoverSamples += modern.physicalCoverSamples;
        metrics.physicalCoveringSamples += modern.physicalCoveringSamples;
        metrics.coverAssessments += modern.coverAssessments;
        metrics.coveredAttackers += modern.coveredAttackers;
        metrics.uncoveredAttackers += modern.uncoveredAttackers;
        metrics.attackCommits += modern.attackCommits;
        metrics.attacksRejectedByRevengeRisk += modern.attackCommitsRejectedByRevengeRisk;
        metrics.revengeOpportunities += modern.revengeOpportunities;
        metrics.revengeCommits += modern.revengeCommits;
        metrics.revengeCancelled += modern.revengeCancelled;
        metrics.revengeRejectedDistance += modern.revengeRejectedDistance;
        metrics.revengeRejectedAlignment += modern.revengeRejectedAlignment;
        metrics.revengeRejectedLine += modern.revengeRejectedLine;
        metrics.revengeRejectedCounterThreat += modern.revengeRejectedCounterThreat;
        metrics.revengeRejectedWindow += modern.revengeRejectedWindow;
        metrics.revengeRejectedState += modern.revengeRejectedState;
        metrics.revengeHits += modern.revengeHits;
        metrics.revengeKills += modern.revengeKills;
        metrics.revengeMisses += modern.revengeMisses;
        metrics.revengeBlocked += modern.revengeBlocked;
        metrics.counterCoverOpportunities += modern.counterCoverOpportunities;
        metrics.counterCoverCommits += modern.counterCoverCommits;
        metrics.counterCoverThreatsEstablished += modern.counterCoverThreatsEstablished;
        metrics.counterCoverHits += modern.counterCoverHits;
        metrics.counterCoverKills += modern.counterCoverKills;
        metrics.recoverShapeTransitions += modern.recoverShapeTransitions;
        metrics.primaryMarkSwitches += modern.primaryMarkSwitches;
        metrics.preEliminationIsolationTwoVsOne += modern.preEliminationIsolationTwoVsOne;
        metrics.postEliminationTwoVsOne += modern.postEliminationTwoVsOne;
        metrics.fixerFrontAngleSum += modern.fixerFrontAngleSum;
        metrics.fixerDistanceSum += modern.fixerDistanceSum;
        metrics.fixerGeometrySamples += modern.fixerGeometrySamples;
        metrics.fixerFrontMaintainedTime += modern.fixerFrontMaintainedTime;
        metrics.fixerCrossedBehindDefender += modern.fixerCrossedBehindDefender;
        metrics.attackersSeparationAngleSum += modern.attackersSeparationAngleSum;
        metrics.attackersSeparationSamples += modern.attackersSeparationSamples;
        metrics.defenderThreatSwitches += modern.defenderThreatSwitches;
        metrics.backEscapes += modern.backEscapes;
        metrics.successfulBackEscapes += modern.successfulBackEscapes;
        metrics.backEscapesWithoutDetection += modern.backEscapesWithoutDetection;
        metrics.maximumConsecutiveBackEscapes = Math.max(metrics.maximumConsecutiveBackEscapes,
                modern.maximumConsecutiveBackEscapes);
        metrics.defenderSurvivalTime += modern.defenderSurvivalTime;
        metrics.coordinationPlansStarted += modern.coordinationPlansStarted;
        metrics.primaryThreatsEstablished += modern.primaryThreatsEstablished;
        metrics.secondaryReleaseWindowsCreated += modern.secondaryReleaseWindowsCreated;
        metrics.secondaryReleases += modern.secondaryReleases;
        metrics.doubleThreatsActive += modern.doubleThreatsActive;
        metrics.coordinatedHits += modern.coordinatedHits;
        metrics.coordinatedKills += modern.coordinatedKills;
        metrics.coordinationAborted += modern.coordinationAborted;
        metrics.coordinationRejectedLineBlocked += modern.coordinationRejectedLineBlocked;
        metrics.coordinationRejectedBadTiming += modern.coordinationRejectedBadTiming;
        metrics.coordinationRejectedFlankerNotReady += modern.coordinationRejectedFlankerNotReady;
        metrics.coordinationRejectedDefenderEscaped += modern.coordinationRejectedDefenderEscaped;
        metrics.coordinationRejectedFriendlyCollision += modern.coordinationRejectedFriendlyCollision;
        metrics.coordinatedImpactOffsetSum += modern.coordinatedImpactOffsetSum;
        metrics.attackerSeparationAtReleaseSum += modern.attackerSeparationAtReleaseSum;
        metrics.defenderFacingAtSecondaryReleaseSum += modern.defenderFacingAtSecondaryReleaseSum;
        metrics.coordinatedReleaseSamples += modern.coordinatedReleaseSamples;
        if (modern.revengeOpportunities > 0) ++metrics.matchesWithRevenge;
        if (modern.counterCoverThreatsEstablished > 0) ++metrics.matchesWithCounterCover;
        if (modern.preEliminationIsolationTwoVsOne == 0)
            ++metrics.matchesRemainingGroupUntilFirstDeath;
        if (modern.firstDeathCause != null)
            metrics.firstDeathCauses.merge(modern.firstDeathCause, 1, Integer::sum);
        metrics.preEliminationSupportOpportunities +=
                modern.preEliminationSupportOpportunities;
        metrics.preEliminationDurableFreedomAssessments +=
                modern.preEliminationDurableFreedomAssessments;
        metrics.preEliminationSupportPlansStarted +=
                modern.preEliminationSupportPlansStarted;
        metrics.preEliminationSupportPlansRejected +=
                modern.preEliminationSupportPlansRejected;
        metrics.preEliminationSupportPlansAborted +=
                modern.preEliminationSupportPlansAborted;
        metrics.preEliminationTwoVsOneFormed +=
                modern.preEliminationTwoVsOneFormed;
        metrics.preEliminationLocalAdvantagesReached +=
                modern.preEliminationLocalAdvantagesReached;
        metrics.postEliminationTwoVsOneCreated +=
                modern.postEliminationTwoVsOneCreated;
        metrics.supporterInterceptedBeforeJoining +=
                modern.supporterInterceptedBeforeJoining;
        metrics.supporterReachedFlankSide += modern.supporterReachedFlankSide;
        metrics.supporterReachedPrimaryDefender += modern.supporterReachedPrimaryDefender;
        metrics.supporterAttackedPrimaryDefender += modern.supporterAttackedPrimaryDefender;
        metrics.supportPlansExpired += modern.supportPlansExpired;
        metrics.supportPlansCancelledByThreat += modern.supportPlansCancelledByThreat;
        metrics.supportPlansCancelledByRecoveryEnd +=
                modern.supportPlansCancelledByRecoveryEnd;
        metrics.supportPlansCancelledByGeometry += modern.supportPlansCancelledByGeometry;
        if (modern.preEliminationTwoVsOneFormed > 0)
            ++metrics.matchesWithPreEliminationSupport;
        else ++metrics.matchesWithOnlyTwoDuelsUntilFirstDeath;
        if (metrics.targetThrashing > targetThrashingBefore)
            metrics.targetThrashingSeeds.add(seed);
        collectTacticalMetrics(simulation, metrics);
    }

    private String replaySnapshot(long seed) {
        var replay = modern(seed);
        while (replay.winningTeam == null && replay.simulationTime < MAX_TIME) replay.update(DT);
        return deterministicSnapshot(replay);
    }

    private String deterministicSnapshot(DuelSimulation simulation) {
        var snapshot = new StringBuilder().append(simulation.winningTeam);
        for (var agent : simulation.agents)
            snapshot.append('|').append(agent.name(UiLanguage.FRENCH)).append(':')
                    .append(agent.alive).append(':').append(agent.hitPoints).append(':')
                    .append(agent.pos).append(':').append(agent.state);
        for (var chain : simulation.killCausalChains())
            snapshot.append("|CHAIN:").append(chain.deterministicKey(UiLanguage.FRENCH));
        return snapshot.toString();
    }

    private void registerChange(ArrayDeque<Double> changes, double time) {
        changes.addLast(time);
        while (!changes.isEmpty() && changes.getFirst() < time - 1) changes.removeFirst();
    }

    private void inspectPhysicalState(DuelSimulation simulation, Metrics metrics) {
        for (var agent : simulation.agents) {
            var coordinates = new double[]{agent.pos.x(), agent.pos.y(), agent.velocity.x(),
                    agent.velocity.y(), agent.angle, agent.weaponYaw};
            for (var value : coordinates) {
                if (Double.isNaN(value)) ++metrics.nanValues;
                else if (!Double.isFinite(value)) ++metrics.infiniteValues;
            }
            if (!Double.isFinite(agent.pos.x()) || !Double.isFinite(agent.pos.y()))
                ++metrics.invalidPositions;
            else if (agent.alive && (agent.pos.x() < 0 || agent.pos.x() > DuelSimulation.ARENA_WIDTH ||
                    agent.pos.y() < 0 || agent.pos.y() > DuelSimulation.ARENA_HEIGHT))
                ++metrics.arenaExits;
        }
    }

    private void inspectRetiredSystems(DuelSimulation simulation, Metrics metrics) {
        for (var agent : simulation.agents)
            if (agent.pressureMode || agent.pressureRole != PressureRole.NONE ||
                    agent.baitedAttack || agent.punishingBait || agent.exploitingBait ||
                    agent.freeKillAttempt || agent.state == CombatState.DOUBLE_BLOCK ||
                    agent.attackManipulation != AttackManipulation.NEUTRAL ||
                    agent.chamberType == ChamberType.PERFECT)
                ++metrics.historicalSystemActivations;
    }

    private void collectTacticalMetrics(DuelSimulation simulation, Metrics metrics) {
        for (var entry : simulation.tacticalLog()) {
            if (entry.contains("PRE_ELIMINATION_TWO_VS_ONE"))
                ++metrics.interceptionsAttempted;
            if (entry.contains("SUPPORT_OPPORTUNITY") && entry.contains("accepted=false") &&
                    entry.contains("rejection=")) {
                var rejection = entry.substring(entry.indexOf("rejection=") + 10);
                var separator = rejection.indexOf(' ');
                if (separator >= 0) rejection = rejection.substring(0, separator);
                metrics.supportRejections.merge(rejection, 1, Integer::sum);
            }
            if (entry.contains("INTERCEPTION_FAILED")) ++metrics.interceptionsFailed;
            if (entry.contains("ENGAGEMENT_BROKEN reason=flanker_intercepted")) {
                ++metrics.interceptionsSucceeded;
                ++metrics.duelSplitsCreated;
            }
            if (entry.contains("FLANK_SIDE_REACHED")) ++metrics.flankSideReached;
            if (entry.contains("REAR_SECTOR_ENTERED")) ++metrics.rearSectorEntered;
            if (entry.contains("BACKSTAB_WINDOW_CREATED")) ++metrics.backstabWindowsCreated;
            if (entry.contains("BACKSTAB_COMMITTED")) ++metrics.backstabsCommitted;
            if (entry.contains("BACKSTAB_HIT")) ++metrics.backstabsHit;
        }
    }

    private int activeDuels(DuelSimulation simulation) {
        var count = 0;
        for (var engagement : simulation.engagements())
            if (active(engagement) && engagement.type == EngagementType.DUEL) ++count;
        return count;
    }

    private <E extends Enum<E>> void merge(EnumMap<E, Integer> destination,
                                            EnumMap<E, Integer> source) {
        for (var entry : source.entrySet())
            destination.merge(entry.getKey(), entry.getValue(), Integer::sum);
    }

    private <E extends Enum<E>> void mergeDoubles(EnumMap<E, Double> destination,
                                                   EnumMap<E, Double> source) {
        for (var entry : source.entrySet())
            destination.merge(entry.getKey(), entry.getValue(), Double::sum);
    }

    private int activeGroups(DuelSimulation simulation) {
        var count = 0;
        for (var engagement : simulation.engagements())
            if (active(engagement) && engagement.type == EngagementType.GROUP_2V2) ++count;
        return count;
    }

    private String activeEngagementSignature(DuelSimulation simulation) {
        var result = new StringBuilder();
        for (var engagement : simulation.engagements()) {
            if (!active(engagement)) continue;
            result.append(engagement.type).append(':');
            if (engagement.type == EngagementType.DUEL)
                result.append(indexOf(simulation, engagement.duelistOne)).append('-')
                        .append(indexOf(simulation, engagement.duelistTwo));
            else if (engagement.type == EngagementType.GROUP_2V2)
                result.append("all-four");
            else result.append(indexOf(simulation, engagement.fixer)).append('-')
                        .append(indexOf(simulation, engagement.flanker)).append('-')
                        .append(indexOf(simulation, engagement.primaryTarget)).append('-')
                        .append(indexOf(simulation, engagement.interceptor)).append('-')
                        .append(engagement.flankerSlot);
            result.append(';');
        }
        return result.toString();
    }

    private boolean hasActiveAttack(DuelSimulation simulation) {
        for (var agent : simulation.agents)
            if (agent.alive && agent.state == CombatState.ATTACK_ACTIVE) return true;
        return false;
    }

    private int totalHitPoints(DuelSimulation simulation) {
        var total = 0;
        for (var agent : simulation.agents) total += agent.hitPoints;
        return total;
    }

    private boolean active(Engagement engagement) {
        return engagement.status != EngagementStatus.BROKEN &&
                engagement.status != EngagementStatus.FINISHED;
    }

    private int indexOf(DuelSimulation simulation, Musketeer agent) {
        if (agent == null) return -1;
        for (var n = 0; n < simulation.agents.length; ++n)
            if (simulation.agents[n] == agent) return n;
        return -2;
    }

    private DuelSimulation modern(long seed) {
        return new DuelSimulation(AgentConfiguration.defaultBlue(), AgentConfiguration.defaultBlueTwo(),
                AgentConfiguration.defaultRed(), AgentConfiguration.defaultRedTwo(),
                SimulationScenario.MODERN_2V2, seed);
    }

    private void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    private static final class Metrics {
        int matchesCompleted, stalemates, timeouts, blueWins, redWins, draws;
        int seedCount;
        int teamManeuversStarted, innerOuterSpeedSamples;
        final EnumMap<TeamManeuverType, Integer> teamManeuversByType =
                new EnumMap<>(TeamManeuverType.class);
        final EnumMap<TeamManeuverType, Double> teamManeuverDurationByType =
                new EnumMap<>(TeamManeuverType.class);
        int initialGroupEngagements, groupEngagementsCreated, groupRotations,
                coverAssessments, coveredAttackers, uncoveredAttackers, attackCommits,
                attacksRejectedByRevengeRisk, revengeOpportunities, revengeCommits,
                revengeCancelled, revengeHits, revengeKills, revengeMisses, revengeBlocked,
                counterCoverOpportunities, counterCoverCommits,
                counterCoverThreatsEstablished, counterCoverHits, counterCoverKills,
                recoverShapeTransitions, primaryMarkSwitches,
                preEliminationIsolationTwoVsOne, postEliminationTwoVsOne,
                matchesWithRevenge, matchesWithCounterCover,
                matchesRemainingGroupUntilFirstDeath;
        double groupRotationDuration, groupAngularDisplacement, teamCenterAngularDisplacement,
                individualTangentialDistance, partnerDistanceSum, crossThreatDistanceSum,
                fixerFrontAngleSum, fixerDistanceSum, fixerFrontMaintainedTime,
                attackersSeparationAngleSum, defenderSurvivalTime,
                requestedTangentialVelocitySum, appliedTangentialVelocitySum,
                actualTangentialVelocitySum, radialCorrectionSum, formationCorrectionSum,
                partnerCorrectionSum, avoidanceCorrectionSum, boundaryCorrectionSum,
                backwardPenaltyLossSum, normalizationLossSum, inertiaLossSum,
                coordinatedImpactOffsetSum, attackerSeparationAtReleaseSum,
                defenderFacingAtSecondaryReleaseSum;
        int rotationDirectionChanges, rotationsBelowVisibleThreshold, partnerDistanceSamples,
                crossThreatDistanceSamples, physicalCoverSamples, physicalCoveringSamples,
                revengeRejectedDistance, revengeRejectedAlignment, revengeRejectedLine,
                revengeRejectedCounterThreat, revengeRejectedWindow, revengeRejectedState,
                fixerGeometrySamples, fixerCrossedBehindDefender, attackersSeparationSamples,
                defenderThreatSwitches, backEscapes, successfulBackEscapes,
                backEscapesWithoutDetection, maximumConsecutiveBackEscapes;
        int steeringSamples, actualTangentialVelocitySamples, coordinationPlansStarted,
                primaryThreatsEstablished, secondaryReleaseWindowsCreated, secondaryReleases,
                doubleThreatsActive, coordinatedHits, coordinatedKills, coordinationAborted,
                coordinationRejectedLineBlocked, coordinationRejectedBadTiming,
                coordinationRejectedFlankerNotReady, coordinationRejectedDefenderEscaped,
                coordinationRejectedFriendlyCollision, coordinatedReleaseSamples;
        int localManeuversStarted, localManeuversCompleted, localManeuversInterrupted,
                holds, lateralSteps, diagonalAdvances, giveGroundManeuvers,
                weaponLineAvoidances, crossCoverManeuvers, attackLineCreations,
                localDirectionReversals, movementContributionSamples;
        double maneuverDurationSum, stationaryDuration, collectiveRotationBiasContribution,
                localManeuverContribution, groupCorrectionContribution,
                weaponAvoidanceContribution, collisionAvoidanceContribution,
                movementBoundaryContribution, teamManeuverContribution,
                teamLateralDistance, teamForwardDistance, innerOuterSpeedDifference,
                intentionalHoldDuration, incidentalLowSpeedDuration,
                formationAreaVariation, partnerDistanceVariation;
        int stationaryEpisodes;
        int initialDuelsCreated, interceptionsAttempted, interceptionsSucceeded,
                interceptionsFailed, duelSplitsCreated;
        int preEliminationSupportOpportunities, preEliminationDurableFreedomAssessments,
                preEliminationSupportPlansStarted, preEliminationSupportPlansRejected,
                preEliminationSupportPlansAborted, preEliminationTwoVsOneFormed,
                preEliminationLocalAdvantagesReached, postEliminationTwoVsOneCreated,
                supporterInterceptedBeforeJoining, supporterReachedFlankSide,
                supporterReachedPrimaryDefender, supporterAttackedPrimaryDefender,
                supportPlansExpired, supportPlansCancelledByThreat,
                supportPlansCancelledByRecoveryEnd, supportPlansCancelledByGeometry,
                matchesWithOnlyTwoDuelsUntilFirstDeath, matchesWithPreEliminationSupport;
        int flankSideReached, rearSectorEntered, backstabWindowsCreated, backstabsCommitted,
                backstabsHit;
        int engagementThrashing, targetThrashing, intentThrashing, primaryMarkThrashing;
        int invalidPositions, nanValues, infiniteValues, arenaExits, historicalSystemActivations;
        int matchesWithFirstDeathCausalChain, determinismFailures;
        final LinkedHashMap<String, Integer> supportRejections = new LinkedHashMap<>();
        final EnumMap<FirstDeathCause, Integer> firstDeathCauses =
                new EnumMap<>(FirstDeathCause.class);
        final ArrayList<Long> targetThrashingSeeds = new ArrayList<>();
        final ArrayList<Long> timeoutSeeds = new ArrayList<>();
        final EnumMap<TimeoutCause, Integer> timeoutCauses = new EnumMap<>(TimeoutCause.class);
        final EnumMap<AttackOpportunityReason, Integer> attackOpportunitiesByReason =
                new EnumMap<>(AttackOpportunityReason.class);
        final EnumMap<AttackOpportunityReason, Integer> attackOpportunitiesAcceptedByReason =
                new EnumMap<>(AttackOpportunityReason.class);
        final EnumMap<AttackOpportunityReason, Integer> attackOpportunitiesRejectedByReason =
                new EnumMap<>(AttackOpportunityReason.class);
        final EnumMap<AttackOpportunityReason, Integer> attackHitsByReason =
                new EnumMap<>(AttackOpportunityReason.class);
        final EnumMap<AttackOpportunityReason, Integer> attackKillsByReason =
                new EnumMap<>(AttackOpportunityReason.class);
        final EnumMap<AttackOpportunityRejection, Integer> attackOpportunityRejections =
                new EnumMap<>(AttackOpportunityRejection.class);
        double attackWindowSum, attackImpactEtaSum;
        int acceptedAttackOpportunitySamples, killsTargetFocusedOnAlly,
                killsTargetAttackCommitted, killsTargetBlockCommitted,
                killsTargetRecovering, killsTargetOverextended, killsLostAllyCover,
                killsCrossAngle, killsRiskyInitiative, killExplanationSamples,
                killAttackOpportunitySamples, coverArrivalDelayKillSamples;
        int movingIntoLineOpportunities, movingIntoLineAccepted, movingIntoLineRejected,
                movingIntoLineHits, movingIntoLineKills, movingIntoLineLowEvidence;
        int killsWithCompleteCausalChain, killsWithNoObservedVictimReaction,
                killsWithLateCover, killsWithNoCoverLine, killsWithVictimWrongGuard,
                killsWithVictimOccupied;
        double coverArrivalDelayOnKillSum, attackWindowOnKillSum, impactEtaOnKillSum;
        final ArrayList<String> timeoutReports = new ArrayList<>();

        void print() {
            var steeringCount = Math.max(1, steeringSamples);
            var releaseCount = Math.max(1, coordinatedReleaseSamples);
            System.out.printf("""
                    desiredAngularVelocity=%.3f
                    circulationReferenceVelocity=%.3f
                    actualAngularVelocity=%.3f
                    averageTangentialRequested=%.3f
                    averageTangentialApplied=%.3f
                    averageTangentialActual=%.3f
                    steeringLosses={radial=%.3f, formation=%.3f, partner=%.3f, avoidance=%.3f, boundary=%.3f, backward=%.3f, normalization=%.3f, inertia=%.3f}
                    coordinationPlansStarted=%d
                    primaryThreatsEstablished=%d
                    secondaryReleaseWindowsCreated=%d
                    secondaryReleases=%d
                    doubleThreatsActive=%d
                    coordinatedHits=%d
                    coordinatedKills=%d
                    averageImpactOffset=%.3f
                    averageAttackerSeparationAtReleaseDeg=%.2f
                    averageDefenderFacingAtSecondaryReleaseDeg=%.2f
                    coordinationAborted=%d
                    coordinationRejections={line=%d, timing=%d, flank=%d, escape=%d, collision=%d}
                    timeoutCauses=%s
                    """, DuelSimulation.GROUP_DESIRED_ANGULAR_VELOCITY,
                    DuelSimulation.GROUP_CIRCULATION_REFERENCE_VELOCITY,
                    groupRotationDuration == 0 ? 0 : groupAngularDisplacement / groupRotationDuration,
                    requestedTangentialVelocitySum / steeringCount,
                    appliedTangentialVelocitySum / steeringCount,
                    actualTangentialVelocitySamples == 0 ? 0 :
                            actualTangentialVelocitySum / actualTangentialVelocitySamples,
                    radialCorrectionSum / steeringCount, formationCorrectionSum / steeringCount,
                    partnerCorrectionSum / steeringCount, avoidanceCorrectionSum / steeringCount,
                    boundaryCorrectionSum / steeringCount, backwardPenaltyLossSum / steeringCount,
                    normalizationLossSum / steeringCount, inertiaLossSum / steeringCount,
                    coordinationPlansStarted, primaryThreatsEstablished,
                    secondaryReleaseWindowsCreated, secondaryReleases, doubleThreatsActive,
                    coordinatedHits, coordinatedKills, coordinatedImpactOffsetSum / releaseCount,
                    Math.toDegrees(attackerSeparationAtReleaseSum / releaseCount),
                    Math.toDegrees(defenderFacingAtSecondaryReleaseSum / releaseCount),
                    coordinationAborted, coordinationRejectedLineBlocked,
                    coordinationRejectedBadTiming, coordinationRejectedFlankerNotReady,
                    coordinationRejectedDefenderEscaped,
                    coordinationRejectedFriendlyCollision, timeoutCauses);
            var movementCount = Math.max(1, movementContributionSamples);
            var contributionTotal = Math.max(0.000001, collectiveRotationBiasContribution +
                    localManeuverContribution + groupCorrectionContribution +
                    weaponAvoidanceContribution + collisionAvoidanceContribution +
                    movementBoundaryContribution);
            System.out.printf("""
                    teamManeuvers={started=%d, byType=%s, durationByType=%s}
                    teamDynamics={lateralDistance=%.3f, forwardDistance=%.3f, innerOuterSpeedDifference=%.3f, intentionalHold=%.3f, incidentalLowSpeed=%.3f, formationAreaVariation=%.3f, partnerDistanceVariation=%.3f}
                    localManeuvers={started=%d, completed=%d, interrupted=%d, holds=%d, lateral=%d, diagonal=%d, giveGround=%d, weaponAvoid=%d, crossCover=%d, attackLine=%d, reversals=%d}
                    averageManeuverDuration=%.3f
                    averageStationaryDuration=%.3f (episodes=%d, total=%.3f)
                    movementContributions={collectiveBias=%.3f, local=%.3f, groupCorrection=%.3f, weaponAvoidance=%.3f, collisionAvoidance=%.3f, boundary=%.3f}
                    movementContributionShares={collectiveBias=%.3f, local=%.3f, groupCorrection=%.3f, weaponAvoidance=%.3f, collisionAndBoundary=%.3f}
                    """, teamManeuversStarted, teamManeuversByType,
                    teamManeuverDurationByType, teamLateralDistance, teamForwardDistance,
                    innerOuterSpeedDifference / Math.max(1, innerOuterSpeedSamples),
                    intentionalHoldDuration, incidentalLowSpeedDuration,
                    formationAreaVariation, partnerDistanceVariation,
                    localManeuversStarted, localManeuversCompleted,
                    localManeuversInterrupted, holds, lateralSteps, diagonalAdvances,
                    giveGroundManeuvers, weaponLineAvoidances, crossCoverManeuvers,
                    attackLineCreations, localDirectionReversals,
                    maneuverDurationSum / Math.max(1, localManeuversCompleted + localManeuversInterrupted),
                    stationaryDuration / Math.max(1, stationaryEpisodes), stationaryEpisodes,
                    stationaryDuration, collectiveRotationBiasContribution / movementCount,
                    localManeuverContribution / movementCount,
                    groupCorrectionContribution / movementCount,
                    weaponAvoidanceContribution / movementCount,
                    collisionAvoidanceContribution / movementCount,
                    movementBoundaryContribution / movementCount,
                    collectiveRotationBiasContribution / contributionTotal,
                    localManeuverContribution / contributionTotal,
                    groupCorrectionContribution / contributionTotal,
                    weaponAvoidanceContribution / contributionTotal,
                    (collisionAvoidanceContribution + movementBoundaryContribution) /
                            contributionTotal);
            System.out.println("attackOpportunitiesByReason=" + attackOpportunitiesByReason);
            System.out.println("attackOpportunitiesAcceptedByReason=" +
                    attackOpportunitiesAcceptedByReason);
            System.out.println("attackOpportunitiesRejectedByReason=" +
                    attackOpportunitiesRejectedByReason);
            System.out.println("attackOpportunityRejections=" + attackOpportunityRejections);
            System.out.println("attackHitsByReason=" + attackHitsByReason);
            System.out.println("attackKillsByReason=" + attackKillsByReason);
            System.out.println("movingIntoLine={detected=" + movingIntoLineOpportunities +
                    ", accepted=" + movingIntoLineAccepted + ", rejected=" +
                    movingIntoLineRejected + ", hits=" + movingIntoLineHits +
                    ", kills=" + movingIntoLineKills + ", lowEvidence=" +
                    movingIntoLineLowEvidence + "}");
            System.out.println("causalChains={complete=" + killsWithCompleteCausalChain +
                    ", noVictimReaction=" + killsWithNoObservedVictimReaction +
                    ", lateCover=" + killsWithLateCover + ", noCoverLine=" +
                    killsWithNoCoverLine + ", victimWrongGuard=" +
                    killsWithVictimWrongGuard + ", victimOccupied=" +
                    killsWithVictimOccupied + "}");
            System.out.printf("averageAttackWindow=%.3f%n",
                    attackWindowSum / Math.max(1, acceptedAttackOpportunitySamples));
            System.out.printf("averageOpportunityImpactEta=%.3f%n",
                    attackImpactEtaSum / Math.max(1, acceptedAttackOpportunitySamples));
            System.out.println("killExplanations={focusedOnAlly=" + killsTargetFocusedOnAlly +
                    ", attackCommitted=" + killsTargetAttackCommitted +
                    ", blockCommitted=" + killsTargetBlockCommitted +
                    ", recovering=" + killsTargetRecovering +
                    ", overextended=" + killsTargetOverextended +
                    ", lostCover=" + killsLostAllyCover + ", crossAngle=" +
                    killsCrossAngle + ", risky=" + killsRiskyInitiative + "}");
            System.out.printf("averageCoverArrivalDelayOnKill=%.3f%n",
                    coverArrivalDelayOnKillSum / Math.max(1, coverArrivalDelayKillSamples));
            System.out.printf("averageAttackWindowOnKill=%.3f%n",
                    attackWindowOnKillSum / Math.max(1, killAttackOpportunitySamples));
            System.out.printf("averageImpactEtaOnKill=%.3f%n",
                    impactEtaOnKillSum / Math.max(1, killAttackOpportunitySamples));
            System.out.printf("""
                    rotationAngularDisplacementDeg=%.2f
                    teamCenterAngularDisplacementDeg=%.2f
                    individualTangentialDistance=%.2f
                    rotationDirectionChanges=%d
                    rotationsBelowVisibleThreshold=%d
                    averagePartnerDistance=%.3f
                    averageCrossThreatDistance=%.3f
                    physicalCoverRate=%.3f
                    revengeRejections={distance=%d, alignment=%d, line=%d, counter=%d, window=%d, state=%d}
                    averageFixerFrontAngleDeg=%.2f
                    averageFixerDistance=%.3f
                    fixerFrontMaintainedTime=%.2f
                    fixerCrossedBehindDefender=%d
                    averageAttackersSeparationDeg=%.2f
                    defenderThreatSwitches=%d
                    backEscapes=%d
                    successfulBackEscapes=%d
                    backEscapesWithoutDetection=%d
                    maximumConsecutiveBackEscapes=%d
                    defenderSurvivalTime=%.2f
                    """, Math.toDegrees(groupAngularDisplacement),
                    Math.toDegrees(teamCenterAngularDisplacement), individualTangentialDistance,
                    rotationDirectionChanges, rotationsBelowVisibleThreshold,
                    partnerDistanceSamples == 0 ? 0 : partnerDistanceSum / partnerDistanceSamples,
                    crossThreatDistanceSamples == 0 ? 0 :
                            crossThreatDistanceSum / crossThreatDistanceSamples,
                    physicalCoverSamples == 0 ? 0 :
                            (double) physicalCoveringSamples / physicalCoverSamples,
                    revengeRejectedDistance, revengeRejectedAlignment, revengeRejectedLine,
                    revengeRejectedCounterThreat, revengeRejectedWindow, revengeRejectedState,
                    fixerGeometrySamples == 0 ? 0 :
                            Math.toDegrees(fixerFrontAngleSum / fixerGeometrySamples),
                    fixerGeometrySamples == 0 ? 0 : fixerDistanceSum / fixerGeometrySamples,
                    fixerFrontMaintainedTime, fixerCrossedBehindDefender,
                    attackersSeparationSamples == 0 ? 0 :
                            Math.toDegrees(attackersSeparationAngleSum / attackersSeparationSamples),
                    defenderThreatSwitches, backEscapes, successfulBackEscapes,
                    backEscapesWithoutDetection, maximumConsecutiveBackEscapes,
                    defenderSurvivalTime);
            System.out.println("""
                    Modern2v2BatchTest seeds 1-%d
                    matchesCompleted=%d
                    stalemates=%d
                    timeouts=%d
                    blueWins=%d
                    redWins=%d
                    draws=%d
                    initialGroupEngagements=%d
                    groupEngagementsCreated=%d
                    groupRotations=%d
                    averageRotationDuration=%.3f
                    coverAssessments=%d
                    coveredAttackers=%d
                    uncoveredAttackers=%d
                    attackCommits=%d
                    attacksRejectedByRevengeRisk=%d
                    revengeOpportunities=%d
                    revengeCommits=%d
                    revengeCancelled=%d
                    revengeHits=%d
                    revengeKills=%d
                    revengeMisses=%d
                    revengeBlocked=%d
                    counterCoverOpportunities=%d
                    counterCoverCommits=%d
                    counterCoverThreatsEstablished=%d
                    counterCoverHits=%d
                    counterCoverKills=%d
                    recoverShapeTransitions=%d
                    primaryMarkSwitches=%d
                    preEliminationIsolationTwoVsOne=%d
                    postEliminationTwoVsOne=%d
                    matchesWithRevenge=%d
                    matchesWithCounterCover=%d
                    matchesRemainingGroupUntilFirstDeath=%d
                    firstDeathCauses=%s
                    initialDuelsCreated=%d
                    preEliminationSupportOpportunities=%d
                    preEliminationDurableFreedomAssessments=%d
                    preEliminationSupportPlansStarted=%d
                    preEliminationSupportPlansRejected=%d
                    preEliminationSupportPlansAborted=%d
                    preEliminationTwoVsOneFormed=%d
                    preEliminationLocalAdvantagesReached=%d
                    postEliminationTwoVsOneCreated=%d
                    supporterInterceptedBeforeJoining=%d
                    supporterReachedFlankSide=%d
                    supporterReachedPrimaryDefender=%d
                    supporterAttackedPrimaryDefender=%d
                    supportPlansExpired=%d
                    supportPlansCancelledByThreat=%d
                    supportPlansCancelledByRecoveryEnd=%d
                    supportPlansCancelledByGeometry=%d
                    matchesWithOnlyTwoDuelsUntilFirstDeath=%d
                    matchesWithPreEliminationSupport=%d
                    interceptionsAttempted=%d
                    interceptionsSucceeded=%d
                    interceptionsFailed=%d
                    duelSplitsCreated=%d
                    flankSideReached=%d
                    rearSectorEntered=%d
                    backstabWindowsCreated=%d
                    backstabsCommitted=%d
                    backstabsHit=%d
                    engagementThrashing=%d
                    targetThrashing=%d
                    intentThrashing=%d
                    primaryMarkThrashing=%d
                    invalidPositions=%d
                    nanValues=%d
                    infiniteValues=%d
                    arenaExits=%d
                    historicalSystemActivations=%d
                    matchesWithFirstDeathCausalChain=%d
                    determinismFailures=%d
                    supportRejections=%s
                    targetThrashingSeeds=%s
                    timeoutSeeds=%s
                    """.formatted(seedCount, matchesCompleted, stalemates, timeouts,
                    blueWins, redWins, draws,
                    initialGroupEngagements, groupEngagementsCreated, groupRotations,
                    groupRotations == 0 ? 0 : groupRotationDuration / groupRotations,
                    coverAssessments, coveredAttackers, uncoveredAttackers, attackCommits,
                    attacksRejectedByRevengeRisk, revengeOpportunities, revengeCommits,
                    revengeCancelled, revengeHits, revengeKills, revengeMisses, revengeBlocked,
                    counterCoverOpportunities, counterCoverCommits,
                    counterCoverThreatsEstablished, counterCoverHits, counterCoverKills,
                    recoverShapeTransitions, primaryMarkSwitches,
                    preEliminationIsolationTwoVsOne, postEliminationTwoVsOne,
                    matchesWithRevenge, matchesWithCounterCover,
                    matchesRemainingGroupUntilFirstDeath, firstDeathCauses,
                    initialDuelsCreated, preEliminationSupportOpportunities,
                    preEliminationDurableFreedomAssessments, preEliminationSupportPlansStarted,
                    preEliminationSupportPlansRejected, preEliminationSupportPlansAborted,
                    preEliminationTwoVsOneFormed, preEliminationLocalAdvantagesReached,
                    postEliminationTwoVsOneCreated, supporterInterceptedBeforeJoining,
                    supporterReachedFlankSide, supporterReachedPrimaryDefender,
                    supporterAttackedPrimaryDefender, supportPlansExpired,
                    supportPlansCancelledByThreat, supportPlansCancelledByRecoveryEnd,
                    supportPlansCancelledByGeometry, matchesWithOnlyTwoDuelsUntilFirstDeath,
                    matchesWithPreEliminationSupport, interceptionsAttempted,
                    interceptionsSucceeded, interceptionsFailed, duelSplitsCreated,
                    flankSideReached, rearSectorEntered, backstabWindowsCreated,
                    backstabsCommitted, backstabsHit, engagementThrashing, targetThrashing,
                    intentThrashing, primaryMarkThrashing, invalidPositions, nanValues,
                    infiniteValues, arenaExits,
                    historicalSystemActivations, matchesWithFirstDeathCausalChain,
                    determinismFailures, supportRejections, targetThrashingSeeds,
                    timeoutSeeds));
            for (var report : timeoutReports) System.out.print(report);
        }
    }
}
