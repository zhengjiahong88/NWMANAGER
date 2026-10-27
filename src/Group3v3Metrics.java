import java.util.EnumMap;

final class Group3v3Metrics {
    int groupEngagementsCreated, formationRoleChanges, roleThrashing, teamManeuversStarted,
            primaryAttentionShifts, delayedAttentionShifts, secondaryAttentionShifts,
            peripheralDetections, peripheralStateReads, coverLinksCreated, coverLinksBroken,
            membersWithoutCover, centerCoverLinks, wingCoverLinks, coverageBreaks,
            attackOpportunitiesEvaluated, singleAttackerPlans, doubleAttackerPlans,
            tripleAttackerPlans, tripleAttackRejections, friendlyLineRejections,
            allCoverageAbandonedRejections, attackCommits, attackBlocks, attackMisses,
            attackHits, attackKills, revengeCandidatesEvaluated, revengeOpportunities,
            revengeCommits, revengeRejected, revengeCancelled, revengeBlocks, revengeMisses,
            revengeHits, revengeKills, counterCoverCandidatesEvaluated,
            counterCoverOpportunities, counterCoverCommits, counterCoverBlocks,
            counterCoverMisses, counterCoverHits, counterCoverKills,
            preventedRevengeChains, clustersDetected, temporaryTwoVsOneClusters,
            contestedClusters, durableIsolations, group3v2Created, group3v1Created,
            handoffsTo2v2, handoffsTo2v1, handoffsTo1v1, deadReferencesRemoved,
            clusterAttentionPriorities, clusterAttackOpportunities, clusterAttackCommits,
            clusterAttackHits, clusterAttackKills, roundsStoppedAtFirstElimination,
            matchesCompleted, transitionRequests, transitionApplications,
            duplicateTransitionRequests, duplicateTransitionApplications,
            recursiveTransitionAttempts, multipleTransitionAttemptsSameTick,
            group3v3ControllerTicks, group3v2ControllerTicks, group3v1ControllerTicks,
            group2v2ControllerTicks, twoVsOneControllerTicks, duelControllerTicks,
            deadReferencePurges, deadReferencesAfterTransition,
            ticksWithoutAnyPositionChange, ticksWithoutAnyStateChange,
            repeatedTransitionStateTicks, logicalFreezeReports, transitionFailsafePauses;
    double formationWidthSum, formationDepthSum, cohesionSum, formationSamples,
            outerInnerLateralDifference, rotationSamples, primaryWeightSum,
            secondaryWeightSum, peripheralWeightSum, attentionSamples, activeCoverLinksSum,
            coverConnectivitySum, weakestCoverageSum, coverSamples;
    final EnumMap<TeamManeuverType, Integer> maneuversByType =
            new EnumMap<>(TeamManeuverType.class);
    int approachPlansStarted, approachPlanInterruptions, contactBandChanges;
    double approachPlanDurationSum, approachContributionSum, teamManeuverContributionSum,
            localManeuverContributionSum, formationContributionSum, coverContributionSum,
            avoidanceContributionSum, boundaryContributionSum;
    int approachSteeringSamples;
    double timeToSpaceContest = -1, timeToFirstContact = -1, timeToFirstAttack = -1,
            preContactForwardDistance, preContactLateralDistance, preContactWidthSum,
            preContactDepthSum, maximumPreContactDepth, firstContactSpread;
    int preContactSamples, formationRoleChangesBeforeContact, leadRoleChanges, approachAudits,
            coveredFirstContacts, uncoveredFirstContacts, simultaneousThreePlayerContacts,
            simultaneousSixPlayerContacts, flatFrontConvergences, lowLateralApproaches,
            lowDepthApproaches, slowApproaches, highlyCorrelatedTeamPaths;
    final EnumMap<FormationRole3, Integer> firstContactByRole =
            new EnumMap<>(FormationRole3.class);
    final EnumMap<ApproachPattern, Integer> approachPlansByPattern =
            new EnumMap<>(ApproachPattern.class);
}
