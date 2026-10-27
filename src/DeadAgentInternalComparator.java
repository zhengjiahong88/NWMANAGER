import java.util.ArrayList;
import java.util.List;

final class DeadAgentInternalComparator {
    private static final double EPSILON = 1e-9;

    DeadAgentInternalComparison compare(List<DifferentialStageSnapshot> left,
                                        List<DifferentialStageSnapshot> right) {
        var comparisons = new ArrayList<DifferentialStageComparison>();
        FirstDifferentialContamination first = null;
        var size = Math.min(left.size(), right.size());
        for (var n = 0; n < size; ++n) {
            var a = left.get(n);
            var b = right.get(n);
            var collective = new ArrayList<DifferentialFieldDifference>();
            var agents = new ArrayList<DifferentialAgentDifference>();
            if (a.tick() != b.tick() || a.stage() != b.stage() ||
                    !a.checkpointAgentId().equals(b.checkpointAgentId()))
                collective.add(diff("CHECKPOINT_ORDER", "checkpoint", key(a), key(b)));
            compareStageSpecific(a, b, collective, agents);
            var identical = collective.isEmpty() && agents.isEmpty();
            comparisons.add(new DifferentialStageComparison(a.tick(), a.simulationTime(), a.sequence(),
                    a.stage(), identical, collective, agents));
            if (!identical && first == null) first = firstContamination(a, collective, agents);
        }
        if (left.size() != right.size() && first == null) {
            var snapshot = size < left.size() ? left.get(size) : right.get(size);
            var difference = diff("CHECKPOINT_ORDER", "snapshotCount",
                    Integer.toString(left.size()), Integer.toString(right.size()));
            first = new FirstDifferentialContamination(snapshot.tick(), snapshot.simulationTime(),
                    snapshot.stage(), difference.category(), difference.field(), List.of(),
                    difference.leftValue(), difference.rightValue(), List.of());
        }
        return new DeadAgentInternalComparison(first == null, first, comparisons);
    }

    private void compareStageSpecific(DifferentialStageSnapshot left,
                                      DifferentialStageSnapshot right,
                                      ArrayList<DifferentialFieldDifference> collective,
                                      ArrayList<DifferentialAgentDifference> agents) {
        switch (left.stage()) {
            case AFTER_DEATH_PROCESSING -> { }
            case AFTER_ATTENTION_UPDATE -> {
                compareRandom(left, right, collective);
                compareAgents(left, right, agents, true, false, false, false);
            }
            case AFTER_TARGET_SELECTION -> compareAgents(left, right, agents,
                    false, true, false, false);
            case AFTER_FORMATION_UPDATE -> compareFormations(left.collective(), right.collective(), collective);
            case AFTER_APPROACH_PLAN_UPDATE -> compareApproach(left.collective(), right.collective(), collective);
            case AFTER_COVER_NETWORK_UPDATE -> compareCover(left.collective(), right.collective(), collective);
            case AFTER_TEAM_MANEUVER_UPDATE -> compareManeuvers(left.collective(), right.collective(), collective);
            case AFTER_STEERING_COMPOSITION -> compareAgents(left, right, agents,
                    false, false, true, false);
            case AFTER_VELOCITY_INTEGRATION -> compareAgents(left, right, agents,
                    false, false, false, true);
            case AFTER_COMBAT_DECISIONS -> {
                compareRandom(left, right, collective);
                compareAgents(left, right, agents, false, true, false, false);
                compareCombat(left, right, agents);
            }
            case AFTER_PHYSICAL_RESOLUTION, END_OF_TICK ->
                    compareMotionAndCombat(left, right, agents);
        }
    }

    private void compareRandom(DifferentialStageSnapshot left, DifferentialStageSnapshot right,
                               ArrayList<DifferentialFieldDifference> differences) {
        if (left.randomDrawCount() != right.randomDrawCount())
            differences.add(diff("RNG_CONSUMPTION", "randomDrawCount",
                    Long.toString(left.randomDrawCount()), Long.toString(right.randomDrawCount())));
        if (left.randomStateHash() != right.randomStateHash())
            differences.add(diff("RNG_STATE", "randomStateHash",
                    Long.toUnsignedString(left.randomStateHash()), Long.toUnsignedString(right.randomStateHash())));
    }

    private void compareFormations(Group3CollectiveSnapshot left, Group3CollectiveSnapshot right,
                                   ArrayList<DifferentialFieldDifference> differences) {
        if (left == null || right == null) return;
        compareFormation("blue", left.blueFormation(), right.blueFormation(), differences);
        compareFormation("red", left.redFormation(), right.redFormation(), differences);
    }

    private void compareFormation(String team, FormationSnapshot left, FormationSnapshot right,
                                  ArrayList<DifferentialFieldDifference> differences) {
        comparePos("FORMATION_CENTER", team + ".center", left.center(), right.center(), differences);
        comparePos("FORMATION_GEOMETRY", team + ".forward", left.forward(), right.forward(), differences);
        comparePos("FORMATION_GEOMETRY", team + ".lateral", left.lateral(), right.lateral(), differences);
        compareDouble("FORMATION_GEOMETRY", team + ".width", left.width(), right.width(), differences);
        compareDouble("FORMATION_GEOMETRY", team + ".depth", left.depth(), right.depth(), differences);
        compareDouble("FORMATION_GEOMETRY", team + ".cohesion", left.cohesion(), right.cohesion(), differences);
        compareObject("FORMATION_PARTICIPANTS", team + ".activeParticipants",
                left.activeParticipants(), right.activeParticipants(), differences);
        compareObject("FORMATION_ROLE", team + ".roles",
                List.of(left.leftRoleAgent(), left.centerRoleAgent(), left.rightRoleAgent()),
                List.of(right.leftRoleAgent(), right.centerRoleAgent(), right.rightRoleAgent()), differences);
        compareObject("FORMATION_GEOMETRY", team + ".lateralProjections",
                left.lateralProjections(), right.lateralProjections(), differences);
        compareObject("FORMATION_GEOMETRY", team + ".forwardProjections",
                left.forwardProjections(), right.forwardProjections(), differences);
    }

    private void compareApproach(Group3CollectiveSnapshot left, Group3CollectiveSnapshot right,
                                 ArrayList<DifferentialFieldDifference> differences) {
        if (left == null || right == null) return;
        compareObject("APPROACH_PLAN", "blueApproachPattern", left.blueApproachPattern(),
                right.blueApproachPattern(), differences);
        compareObject("APPROACH_PLAN", "redApproachPattern", left.redApproachPattern(),
                right.redApproachPattern(), differences);
    }

    private void compareManeuvers(Group3CollectiveSnapshot left, Group3CollectiveSnapshot right,
                                  ArrayList<DifferentialFieldDifference> differences) {
        if (left == null || right == null) return;
        compareObject("TEAM_MANEUVER", "blueTeamManeuver", left.blueTeamManeuver(),
                right.blueTeamManeuver(), differences);
        compareObject("TEAM_MANEUVER", "redTeamManeuver", left.redTeamManeuver(),
                right.redTeamManeuver(), differences);
    }

    private void compareCover(Group3CollectiveSnapshot left, Group3CollectiveSnapshot right,
                              ArrayList<DifferentialFieldDifference> differences) {
        if (left == null || right == null) return;
        compareCoverNetwork("blue", left.blueCoverNetwork(), right.blueCoverNetwork(), differences);
        compareCoverNetwork("red", left.redCoverNetwork(), right.redCoverNetwork(), differences);
    }

    private void compareCoverNetwork(String team, CoverNetworkSnapshot left, CoverNetworkSnapshot right,
                                     ArrayList<DifferentialFieldDifference> differences) {
        compareObject("COVER_PARTICIPANTS", team + ".activeParticipants",
                left.activeParticipants(), right.activeParticipants(), differences);
        compareCoverLinks(team + ".viable", left.viableLinks(), right.viableLinks(), differences);
        compareCoverLinks(team + ".rejected", left.relevantRejectedLinks(),
                right.relevantRejectedLinks(), differences);
        compareDouble("COVER_GLOBAL_METRIC", team + ".connectivity",
                left.connectivity(), right.connectivity(), differences);
        compareDouble("COVER_GLOBAL_METRIC", team + ".weakestMemberCoverage",
                left.weakestMemberCoverage(), right.weakestMemberCoverage(), differences);
        compareObject("COVER_GLOBAL_METRIC", team + ".incomingCounts",
                left.incomingViableCounts(), right.incomingViableCounts(), differences);
        compareObject("COVER_GLOBAL_METRIC", team + ".outgoingCounts",
                left.outgoingViableCounts(), right.outgoingViableCounts(), differences);
    }

    private void compareCoverLinks(String field, List<CanonicalCoverLinkSnapshot> left,
                                   List<CanonicalCoverLinkSnapshot> right,
                                   ArrayList<DifferentialFieldDifference> differences) {
        var leftByKey = new java.util.TreeMap<String, CanonicalCoverLinkSnapshot>();
        var rightByKey = new java.util.TreeMap<String, CanonicalCoverLinkSnapshot>();
        for (var link : left) leftByKey.put(linkKey(link), link);
        for (var link : right) rightByKey.put(linkKey(link), link);
        var keys = new java.util.TreeSet<String>(leftByKey.keySet());
        keys.addAll(rightByKey.keySet());
        for (var key : keys) {
            var a = leftByKey.get(key);
            var b = rightByKey.get(key);
            if (a == null || b == null) {
                differences.add(diff("COVER_LINK_EXISTENCE", field + "." + key,
                        a == null ? "ABSENT" : "PRESENT", b == null ? "ABSENT" : "PRESENT"));
                continue;
            }
            if (a.viable() != b.viable()) differences.add(diff("COVER_LINK_VIABILITY", field + "." + key,
                    Boolean.toString(a.viable()), Boolean.toString(b.viable())));
            compareDouble("COVER_LINK_ETA", field + "." + key + ".alignEta",
                    a.alignEta(), b.alignEta(), differences);
            compareDouble("COVER_LINK_ETA", field + "." + key + ".prepareEta",
                    a.prepareEta(), b.prepareEta(), differences);
            compareDouble("COVER_LINK_ETA", field + "." + key + ".contactEta",
                    a.contactEta(), b.contactEta(), differences);
            compareDouble("COVER_LINK_ETA", field + "." + key + ".punishEta",
                    a.punishEta(), b.punishEta(), differences);
            compareDouble("COVER_LINK_QUALITY", field + "." + key + ".quality",
                    a.quality(), b.quality(), differences);
            compareObject("COVER_LINK_REJECTION", field + "." + key + ".rejectionReason",
                    a.rejectionReason(), b.rejectionReason(), differences);
        }
    }

    private void compareAgents(DifferentialStageSnapshot left, DifferentialStageSnapshot right,
                               ArrayList<DifferentialAgentDifference> differences,
                               boolean attention, boolean targets, boolean steering, boolean velocity) {
        var count = Math.min(left.survivors().size(), right.survivors().size());
        for (var n = 0; n < count; ++n) {
            var a = left.survivors().get(n);
            var b = right.survivors().get(n);
            var fields = new ArrayList<DifferentialFieldDifference>();
            if (attention) {
                compareObject("ATTENTION_ASSIGNMENT", "attention.assignments",
                        List.of(a.attention().primaryThreatId(), a.attention().secondaryThreatId(),
                                a.attention().peripheralThreatId(), a.attention().pendingPrimaryId()),
                        List.of(b.attention().primaryThreatId(), b.attention().secondaryThreatId(),
                                b.attention().peripheralThreatId(), b.attention().pendingPrimaryId()), fields);
                compareAttention(a.attention(), b.attention(), fields);
                compareAwarenessEntries(a.awarenessEntries(), b.awarenessEntries(), fields);
            }
            if (targets) compareObject("TARGET_SELECTION", "targets",
                    targetValues(a), targetValues(b), fields);
            if (steering) compareSteering(a.steering(), b.steering(), fields);
            if (velocity) comparePos("VELOCITY", "velocity", a.velocity(), b.velocity(), fields);
            if (!fields.isEmpty()) differences.add(new DifferentialAgentDifference(a.agentId(), fields));
        }
    }

    private void compareAttention(DifferentialAttentionSnapshot left,
                                  DifferentialAttentionSnapshot right,
                                  ArrayList<DifferentialFieldDifference> differences) {
        compareDouble("ATTENTION_WEIGHT", "primaryWeight", left.primaryWeight(),
                right.primaryWeight(), differences);
        compareDouble("ATTENTION_WEIGHT", "secondaryWeight", left.secondaryWeight(),
                right.secondaryWeight(), differences);
        compareDouble("ATTENTION_WEIGHT", "peripheralWeight", left.peripheralWeight(),
                right.peripheralWeight(), differences);
        compareDouble("ATTENTION_CONFIDENCE", "primaryConfidence", left.primaryConfidence(),
                right.primaryConfidence(), differences);
        compareDouble("ATTENTION_CONFIDENCE", "secondaryConfidence", left.secondaryConfidence(),
                right.secondaryConfidence(), differences);
        compareDouble("ATTENTION_CONFIDENCE", "peripheralConfidence", left.peripheralConfidence(),
                right.peripheralConfidence(), differences);
        compareObject("ATTENTION_CONFIDENCE", "freshStates",
                List.of(left.primaryStateFresh(), left.secondaryStateFresh(), left.peripheralStateFresh()),
                List.of(right.primaryStateFresh(), right.secondaryStateFresh(), right.peripheralStateFresh()),
                differences);
        compareObject("ATTENTION_CONFIDENCE", "observationCounts",
                List.of(left.primaryObservationCount(), left.secondaryObservationCount(),
                        left.peripheralObservationCount()),
                List.of(right.primaryObservationCount(), right.secondaryObservationCount(),
                        right.peripheralObservationCount()), differences);
    }

    private void compareAwarenessEntries(List<AwarenessEntrySnapshot> left,
                                         List<AwarenessEntrySnapshot> right,
                                         ArrayList<DifferentialFieldDifference> differences) {
        if (left.size() != right.size()) {
            differences.add(diff("AWARENESS_MEMORY", "entryCount",
                    Integer.toString(left.size()), Integer.toString(right.size())));
            return;
        }
        for (var n = 0; n < left.size(); ++n) {
            var a = left.get(n);
            var b = right.get(n);
            if (!a.opponentId().equals(b.opponentId()) ||
                    !a.attentionLevel().equals(b.attentionLevel()) ||
                    !sameNullable(a.lastKnownPosition(), b.lastKnownPosition()) ||
                    !sameNullable(a.lastKnownVelocity(), b.lastKnownVelocity()) ||
                    !equal(a.observationTime(), b.observationTime()) ||
                    a.observationCount() != b.observationCount() ||
                    !equal(a.confidence(), b.confidence()) ||
                    a.stateDetailFresh() != b.stateDetailFresh()) {
                differences.add(diff("AWARENESS_MEMORY", "awareness." + a.opponentId(),
                        a.toString(), b.toString()));
            }
        }
    }

    private boolean sameNullable(Pos left, Pos right) {
        return left == null || right == null ? left == right :
                equal(left.x(), right.x()) && equal(left.y(), right.y());
    }

    private void compareSteering(DifferentialSteeringSnapshot left, DifferentialSteeringSnapshot right,
                                 ArrayList<DifferentialFieldDifference> differences) {
        if (left == null || right == null) {
            compareObject("STEERING_FINAL", "steering", left, right, differences);
            return;
        }
        comparePos("STEERING_APPROACH", "approachContribution", left.approachContribution(),
                right.approachContribution(), differences);
        comparePos("STEERING_TEAM_MANEUVER", "teamManeuverContribution",
                left.teamManeuverContribution(), right.teamManeuverContribution(), differences);
        comparePos("STEERING_LOCAL", "localManeuverContribution", left.localManeuverContribution(),
                right.localManeuverContribution(), differences);
        comparePos("STEERING_FORMATION", "formationContribution", left.formationContribution(),
                right.formationContribution(), differences);
        comparePos("STEERING_COVER", "coverContribution", left.coverContribution(),
                right.coverContribution(), differences);
        comparePos("STEERING_AVOIDANCE", "avoidanceContribution", left.avoidanceContribution(),
                right.avoidanceContribution(), differences);
        comparePos("STEERING_BOUNDARY", "boundaryContribution", left.boundaryContribution(),
                right.boundaryContribution(), differences);
        comparePos("STEERING_FINAL", "finalDesiredVelocity", left.finalDesiredVelocity(),
                right.finalDesiredVelocity(), differences);
    }

    private void compareCombat(DifferentialStageSnapshot left, DifferentialStageSnapshot right,
                               ArrayList<DifferentialAgentDifference> differences) {
        compareEach(left, right, differences, true);
    }

    private void compareMotionAndCombat(DifferentialStageSnapshot left, DifferentialStageSnapshot right,
                                        ArrayList<DifferentialAgentDifference> differences) {
        compareEach(left, right, differences, false);
    }

    private void compareEach(DifferentialStageSnapshot left, DifferentialStageSnapshot right,
                             ArrayList<DifferentialAgentDifference> differences, boolean combatOnly) {
        for (var n = 0; n < Math.min(left.survivors().size(), right.survivors().size()); ++n) {
            var a = left.survivors().get(n);
            var b = right.survivors().get(n);
            var fields = new ArrayList<DifferentialFieldDifference>();
            compareObject("COMBAT_STATE", "combatState", a.combatState(), b.combatState(), fields);
            compareObject("COMBAT_STATE", "actionState", a.actionState(), b.actionState(), fields);
            compareObject("ATTACK_OPPORTUNITY", "attackOpportunity",
                    List.of(a.bestAttackOpportunityTargetId(), a.attackOpportunityReason(),
                            a.attackOpportunityViable()),
                    List.of(b.bestAttackOpportunityTargetId(), b.attackOpportunityReason(),
                            b.attackOpportunityViable()), fields);
            if (!combatOnly) {
                comparePos("POSITION", "position", a.position(), b.position(), fields);
                comparePos("VELOCITY", "velocity", a.velocity(), b.velocity(), fields);
                compareDouble("BODY_ANGLE", "bodyAngle", a.bodyAngle(), b.bodyAngle(), fields);
                compareDouble("WEAPON_ANGLE", "weaponAngle", a.weaponAngle(), b.weaponAngle(), fields);
            }
            if (!fields.isEmpty()) mergeAgentDifference(differences, a.agentId(), fields);
        }
    }

    private void mergeAgentDifference(ArrayList<DifferentialAgentDifference> differences, String id,
                                      ArrayList<DifferentialFieldDifference> fields) {
        for (var n = 0; n < differences.size(); ++n)
            if (differences.get(n).agentId().equals(id)) {
                var merged = new ArrayList<>(differences.get(n).differences());
                merged.addAll(fields);
                differences.set(n, new DifferentialAgentDifference(id, merged));
                return;
            }
        differences.add(new DifferentialAgentDifference(id, fields));
    }

    private FirstDifferentialContamination firstContamination(DifferentialStageSnapshot snapshot,
                                                               List<DifferentialFieldDifference> collective,
                                                               List<DifferentialAgentDifference> agents) {
        var all = new ArrayList<DifferentialFieldDifference>(collective);
        var affected = new ArrayList<String>();
        for (var agent : agents) {
            affected.add(agent.agentId());
            all.addAll(agent.differences());
        }
        var primary = all.getFirst();
        return new FirstDifferentialContamination(snapshot.tick(), snapshot.simulationTime(),
                snapshot.stage(), primary.category(), primary.field(), affected,
                primary.leftValue(), primary.rightValue(), all.subList(1, all.size()));
    }

    private List<Object> targetValues(DifferentialAgentSnapshot snapshot) {
        return List.of(snapshot.targetId(), snapshot.plannedTargetId(), snapshot.movementTargetId(),
                snapshot.revengeTargetId(), snapshot.counterCoverTargetId(),
                snapshot.bestAttackOpportunityTargetId());
    }

    private String linkKey(CanonicalCoverLinkSnapshot link) {
        return link.protectorId() + "|" + link.protectedId() + "|" + link.enemyId();
    }

    private String key(DifferentialStageSnapshot snapshot) {
        return snapshot.tick() + "/" + snapshot.stage() + "/" + snapshot.checkpointAgentId();
    }

    private void comparePos(String category, String field, Pos left, Pos right,
                            ArrayList<DifferentialFieldDifference> differences) {
        if (left == null || right == null) {
            compareObject(category, field, left, right, differences);
            return;
        }
        if (!equal(left.x(), right.x()) || !equal(left.y(), right.y()))
            differences.add(diff(category, field, left.toString(), right.toString()));
    }

    private void compareDouble(String category, String field, double left, double right,
                               ArrayList<DifferentialFieldDifference> differences) {
        if (!equal(left, right)) differences.add(diff(category, field,
                Double.toString(left), Double.toString(right)));
    }

    private void compareObject(String category, String field, Object left, Object right,
                               ArrayList<DifferentialFieldDifference> differences) {
        if (!java.util.Objects.equals(left, right)) differences.add(diff(category, field,
                String.valueOf(left), String.valueOf(right)));
    }

    private boolean equal(double left, double right) {
        return left == right || Double.isNaN(left) && Double.isNaN(right) ||
                Math.abs(left - right) <= EPSILON;
    }

    private DifferentialFieldDifference diff(String category, String field,
                                             String left, String right) {
        return new DifferentialFieldDifference(category, field, left, right);
    }
}
