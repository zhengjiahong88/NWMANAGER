final class DeadAgentInternalAnalyzer {
    DeadAgentInternalAnalysis analyze(long seed) {
        var runner = new DeadAgentDifferentialRunner();
        var current = runner.runDeadAgentScenario(seed, DeadAgentExperimentMode.CURRENT_BUGGED_BEHAVIOR);
        var neutralized = runner.runDeadAgentScenario(seed, DeadAgentExperimentMode.LOGICALLY_NEUTRALIZED);
        var endComparison = new DeadAgentDifferentialComparator().compare(current, neutralized);
        var firstSurvivorTick = earliestEndTick(endComparison);
        var firstInternalTick = findFirstDifferentTick(runner, seed, 60, firstSurvivorTick);
        var currentCausal = runner.runDeadAgentScenario(seed,
                DeadAgentExperimentMode.CURRENT_BUGGED_BEHAVIOR,
                Math.max(60, firstInternalTick - 3), firstInternalTick + 3);
        var neutralizedCausal = runner.runDeadAgentScenario(seed,
                DeadAgentExperimentMode.LOGICALLY_NEUTRALIZED,
                Math.max(60, firstInternalTick - 3), firstInternalTick + 3);
        var internalComparator = new DeadAgentInternalComparator();
        var causal = internalComparator.compare(currentCausal.stageSnapshots,
                neutralizedCausal.stageSnapshots);
        var firstPropagatingTick = findFirstPropagatingTick(runner, seed,
                firstInternalTick, firstSurvivorTick);
        var currentPropagating = runner.captureDeadAgentTick(seed,
                DeadAgentExperimentMode.CURRENT_BUGGED_BEHAVIOR, firstPropagatingTick);
        var neutralizedPropagating = runner.captureDeadAgentTick(seed,
                DeadAgentExperimentMode.LOGICALLY_NEUTRALIZED, firstPropagatingTick);
        var currentSurvivor = runner.captureDeadAgentTick(seed,
                DeadAgentExperimentMode.CURRENT_BUGGED_BEHAVIOR, firstSurvivorTick);
        var neutralizedSurvivor = runner.captureDeadAgentTick(seed,
                DeadAgentExperimentMode.LOGICALLY_NEUTRALIZED, firstSurvivorTick);
        var propagation = scanPropagation(runner, seed, 60,
                Math.max(240, firstSurvivorTick + 20));
        var propagating = internalComparator.compare(currentPropagating, neutralizedPropagating);
        return new DeadAgentInternalAnalysis(seed, firstInternalTick, causal, firstPropagatingTick,
                propagating, firstEffectiveContamination(propagating), propagation,
                internalComparator.compare(currentSurvivor, neutralizedSurvivor), endComparison,
                currentCausal, neutralizedCausal);
    }

    private DifferentialPropagationTimeline scanPropagation(DeadAgentDifferentialRunner runner,
                                                             long seed, long start, long end) {
        var first = new java.util.LinkedHashMap<String, FirstDifferentialContamination>();
        var comparator = new DeadAgentInternalComparator();
        for (var windowStart = start; windowStart <= end; windowStart += 16) {
            var windowEnd = Math.min(end, windowStart + 15);
            var comparison = comparator.compare(runner.captureDeadAgentWindow(seed,
                    DeadAgentExperimentMode.CURRENT_BUGGED_BEHAVIOR, windowStart, windowEnd),
                    runner.captureDeadAgentWindow(seed, DeadAgentExperimentMode.LOGICALLY_NEUTRALIZED,
                            windowStart, windowEnd));
            for (var stage : comparison.stageComparisons()) {
                for (var difference : stage.collectiveDifferences())
                    recordPropagation(first, stage, null, difference);
                for (var agent : stage.agentDifferences())
                    for (var difference : agent.differences())
                        recordPropagation(first, stage, agent.agentId(), difference);
            }
        }
        return new DifferentialPropagationTimeline(first);
    }

    private void recordPropagation(java.util.Map<String, FirstDifferentialContamination> first,
                                   DifferentialStageComparison stage, String agent,
                                   DifferentialFieldDifference difference) {
        var system = propagationSystem(difference.category());
        if (system == null || first.containsKey(system)) return;
        var affected = agent == null ? difference.category().startsWith("COVER_LINK") ?
                coverLinkAgents(difference.field()) : java.util.List.<String>of() :
                java.util.List.of(agent);
        first.put(system, new FirstDifferentialContamination(stage.tick(), stage.simulationTime(),
                stage.stage(), difference.category(), difference.field(), affected,
                difference.leftValue(), difference.rightValue(), java.util.List.of()));
    }

    private String propagationSystem(String category) {
        if (category.startsWith("STEERING_")) return "STEERING";
        if (category.equals("VELOCITY")) return "VELOCITY";
        if (category.equals("POSITION")) return "POSITION";
        if (category.startsWith("ATTENTION_") || category.equals("AWARENESS_MEMORY")) return "ATTENTION";
        if (category.equals("TARGET_SELECTION")) return "TARGET";
        if (category.equals("ATTACK_OPPORTUNITY")) return "ATTACK_OPPORTUNITY";
        if (category.equals("COMBAT_STATE")) return "COMBAT_STATE";
        return null;
    }

    private long findFirstPropagatingTick(DeadAgentDifferentialRunner runner, long seed,
                                          long minimum, long maximum) {
        for (var start = minimum; start <= maximum; start += 16) {
            var end = Math.min(maximum, start + 15);
            var comparison = new DeadAgentInternalComparator().compare(
                    runner.captureDeadAgentWindow(seed, DeadAgentExperimentMode.CURRENT_BUGGED_BEHAVIOR,
                            start, end),
                    runner.captureDeadAgentWindow(seed, DeadAgentExperimentMode.LOGICALLY_NEUTRALIZED,
                            start, end));
            var first = firstEffectiveContamination(comparison);
            if (first != null) return first.tick();
        }
        throw new IllegalStateException("No propagating divergence found before tick " + maximum);
    }

    private boolean structuralCoverDifference(DifferentialFieldDifference difference) {
        return difference.category().equals("COVER_PARTICIPANTS") ||
                difference.field().contains(".rejected.") ||
                difference.category().equals("COVER_GLOBAL_METRIC") &&
                        (difference.field().endsWith("incomingCounts") ||
                                difference.field().endsWith("outgoingCounts"));
    }

    private FirstDifferentialContamination firstEffectiveContamination(
            DeadAgentInternalComparison comparison) {
        for (var stage : comparison.stageComparisons()) {
            var effective = new java.util.ArrayList<DifferentialFieldDifference>();
            var affected = new java.util.ArrayList<String>();
            for (var difference : stage.collectiveDifferences())
                if (!structuralCoverDifference(difference)) effective.add(difference);
            for (var agent : stage.agentDifferences()) {
                affected.add(agent.agentId());
                effective.addAll(agent.differences());
            }
            if (effective.isEmpty()) continue;
            var primary = effective.getFirst();
            if (affected.isEmpty() && primary.category().startsWith("COVER_LINK"))
                affected.addAll(coverLinkAgents(primary.field()));
            return new FirstDifferentialContamination(stage.tick(), stage.simulationTime(), stage.stage(),
                    primary.category(), primary.field(), affected, primary.leftValue(), primary.rightValue(),
                    effective.subList(1, effective.size()));
        }
        return null;
    }

    private java.util.List<String> coverLinkAgents(String field) {
        var parts = field.split("\\.");
        for (var part : parts)
            if (part.contains("|")) return java.util.List.of(part.split("\\|"));
        return java.util.List.of();
    }

    private long findFirstDifferentTick(DeadAgentDifferentialRunner runner, long seed,
                                        long minimum, long maximum) {
        var comparator = new DeadAgentInternalComparator();
        while (minimum < maximum) {
            var middle = (minimum + maximum) >>> 1;
            var current = runner.captureDeadAgentTick(seed,
                    DeadAgentExperimentMode.CURRENT_BUGGED_BEHAVIOR, middle);
            var neutralized = runner.captureDeadAgentTick(seed,
                    DeadAgentExperimentMode.LOGICALLY_NEUTRALIZED, middle);
            if (comparator.compare(current, neutralized).identical()) minimum = middle + 1;
            else maximum = middle;
        }
        return minimum;
    }

    private long earliestEndTick(DeadAgentDifferentialComparison comparison) {
        var earliest = Long.MAX_VALUE;
        var ticks = new Long[]{comparison.firstPositionDivergenceTick(),
                comparison.firstVelocityDivergenceTick(), comparison.firstBodyAngleDivergenceTick(),
                comparison.firstWeaponAngleDivergenceTick(), comparison.firstStateDivergenceTick(),
                comparison.firstTargetDivergenceTick(), comparison.firstAttentionDivergenceTick(),
                comparison.firstFormationDivergenceTick(), comparison.firstCoverDivergenceTick(),
                comparison.firstAttackOpportunityDivergenceTick(),
                comparison.firstCombatEventDivergenceTick(), comparison.firstTransitionDivergenceTick()};
        for (var tick : ticks)
            if (tick != null) earliest = Math.min(earliest, tick);
        if (earliest == Long.MAX_VALUE) throw new IllegalStateException("A and D1 did not diverge.");
        return earliest;
    }
}
