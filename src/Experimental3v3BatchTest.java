import java.util.ArrayList;

final class Experimental3v3BatchTest {
    private static final double DT = 1.0 / 120.0;

    public static void main(String[] args) {
        new Experimental3v3BatchTest().run(args.length == 0 ? 100 : Integer.parseInt(args[0]));
    }

    private void run(int matches) {
        var completed = 0;
        var timeouts = 0;
        var invalidPositions = 0;
        var arenaExits = 0;
        var determinismFailures = 0;
        var group3v2 = 0;
        var group3v1 = 0;
        var handoff2v2 = 0;
        var handoff2v1 = 0;
        var handoff1v1 = 0;
        var clusters = 0;
        var isolations = 0;
        var clusterAttacks = 0;
        var clusterHits = 0;
        var blueWins = 0;
        var redWins = 0;
        var transitionRequests = 0;
        var transitionApplications = 0;
        var duplicateTransitionRequests = 0;
        var duplicateTransitionApplications = 0;
        var recursiveTransitionAttempts = 0;
        var multipleTransitionAttemptsSameTick = 0;
        var group3v3ControllerTicks = 0;
        var group3v2ControllerTicks = 0;
        var group3v1ControllerTicks = 0;
        var group2v2ControllerTicks = 0;
        var twoVsOneControllerTicks = 0;
        var duelControllerTicks = 0;
        var deadReferencePurges = 0;
        var deadReferencesAfterTransition = 0;
        var maximumTicksWithoutPositionChange = 0;
        var maximumTicksWithoutStateChange = 0;
        var maximumRepeatedTransitionStateTicks = 0;
        var logicalFreezeReports = 0;
        var transitionFailsafePauses = 0;
        var approachAudits = 0;
        var flatFrontConvergences = 0;
        var simultaneousSixPlayerContacts = 0;
        var coveredFirstContacts = 0;
        var uncoveredFirstContacts = 0;
        var firstContactSpreadSum = 0.0;
        var preContactLateralDistanceSum = 0.0;
        var timeToSpaceContestSum = 0.0;
        var timeToFirstContactSum = 0.0;
        var timeToFirstAttackSum = 0.0;
        var spaceContestSamples = 0;
        var firstAttackSamples = 0;
        var preContactDepthSum = 0.0;
        var preContactSamples = 0;
        long consoleLinesEmitted = 0, duplicateConsoleLinesSuppressed = 0;
        var timeoutDetails = new ArrayList<String>();
        for (var seed = 1L; seed <= matches; ++seed) {
            var simulation = fixture(seed);
            for (var tick = 0; tick < 14_400 && !simulation.experimentalRoundComplete(); ++tick)
                simulation.update(DT);
            if (simulation.experimentalRoundComplete()) {
                ++completed;
                if (simulation.experimentalRoundLeader() == Team.BLUE) ++blueWins;
                else if (simulation.experimentalRoundLeader() == Team.RED) ++redWins;
            } else {
                ++timeouts;
                timeoutDetails.add(seed + ":" + simulation.experimentalGroupPhase() +
                        ":blue=" + living(simulation.blueTeam) +
                        ":red=" + living(simulation.redTeam) + ":" + timeoutState(simulation));
            }
            var metrics = simulation.group3Metrics();
            group3v2 += metrics.group3v2Created;
            group3v1 += metrics.group3v1Created;
            handoff2v2 += metrics.handoffsTo2v2;
            handoff2v1 += metrics.handoffsTo2v1;
            handoff1v1 += metrics.handoffsTo1v1;
            clusters += metrics.clustersDetected;
            isolations += metrics.durableIsolations;
            clusterAttacks += metrics.clusterAttackCommits;
            clusterHits += metrics.clusterAttackHits;
            transitionRequests += metrics.transitionRequests;
            transitionApplications += metrics.transitionApplications;
            duplicateTransitionRequests += metrics.duplicateTransitionRequests;
            duplicateTransitionApplications += metrics.duplicateTransitionApplications;
            recursiveTransitionAttempts += metrics.recursiveTransitionAttempts;
            multipleTransitionAttemptsSameTick += metrics.multipleTransitionAttemptsSameTick;
            group3v3ControllerTicks += metrics.group3v3ControllerTicks;
            group3v2ControllerTicks += metrics.group3v2ControllerTicks;
            group3v1ControllerTicks += metrics.group3v1ControllerTicks;
            group2v2ControllerTicks += metrics.group2v2ControllerTicks;
            twoVsOneControllerTicks += metrics.twoVsOneControllerTicks;
            duelControllerTicks += metrics.duelControllerTicks;
            deadReferencePurges += metrics.deadReferencePurges;
            deadReferencesAfterTransition += metrics.deadReferencesAfterTransition;
            maximumTicksWithoutPositionChange = Math.max(maximumTicksWithoutPositionChange,
                    metrics.ticksWithoutAnyPositionChange);
            maximumTicksWithoutStateChange = Math.max(maximumTicksWithoutStateChange,
                    metrics.ticksWithoutAnyStateChange);
            maximumRepeatedTransitionStateTicks = Math.max(maximumRepeatedTransitionStateTicks,
                    metrics.repeatedTransitionStateTicks);
            logicalFreezeReports += metrics.logicalFreezeReports;
            transitionFailsafePauses += metrics.transitionFailsafePauses;
            approachAudits += metrics.approachAudits;
            firstContactSpreadSum += metrics.firstContactSpread;
            preContactLateralDistanceSum += metrics.preContactLateralDistance;
            if (metrics.timeToSpaceContest >= 0) {
                timeToSpaceContestSum += metrics.timeToSpaceContest;
                ++spaceContestSamples;
            }
            if (metrics.timeToFirstContact >= 0) timeToFirstContactSum += metrics.timeToFirstContact;
            if (metrics.timeToFirstAttack >= 0) {
                timeToFirstAttackSum += metrics.timeToFirstAttack;
                ++firstAttackSamples;
            }
            preContactDepthSum += metrics.preContactDepthSum;
            preContactSamples += metrics.preContactSamples;
            flatFrontConvergences += metrics.flatFrontConvergences;
            simultaneousSixPlayerContacts += metrics.simultaneousSixPlayerContacts;
            coveredFirstContacts += metrics.coveredFirstContacts;
            uncoveredFirstContacts += metrics.uncoveredFirstContacts;
            consoleLinesEmitted += simulation.consoleLinesEmitted();
            duplicateConsoleLinesSuppressed += simulation.duplicateConsoleLinesSuppressed();
            for (var agent : simulation.agents) {
                if (!Double.isFinite(agent.pos.x()) || !Double.isFinite(agent.pos.y()))
                    ++invalidPositions;
                if (agent.pos.x() < -0.001 || agent.pos.x() > DuelSimulation.ARENA_WIDTH + 0.001 ||
                        agent.pos.y() < -0.001 || agent.pos.y() > DuelSimulation.ARENA_HEIGHT + 0.001)
                    ++arenaExits;
            }
            if (seed <= Math.min(20, matches) && !deterministic(seed, simulation))
                ++determinismFailures;
        }
        System.out.println("Experimental3v3BatchTest seeds 1-" + matches);
        System.out.println("matchesCompleted=" + completed);
        System.out.println("timeouts=" + timeouts);
        System.out.println("timeoutDetails=" + timeoutDetails);
        System.out.println("blueWins=" + blueWins);
        System.out.println("redWins=" + redWins);
        System.out.println("clustersDetected=" + clusters);
        System.out.println("durableIsolations=" + isolations);
        System.out.println("clusterAttackCommits=" + clusterAttacks);
        System.out.println("clusterAttackHits=" + clusterHits);
        System.out.println("group3v2Created=" + group3v2);
        System.out.println("group3v1Created=" + group3v1);
        System.out.println("handoffsTo2v2=" + handoff2v2);
        System.out.println("handoffsTo2v1=" + handoff2v1);
        System.out.println("handoffsTo1v1=" + handoff1v1);
        System.out.println("transitionRequests=" + transitionRequests);
        System.out.println("transitionApplications=" + transitionApplications);
        System.out.println("duplicateTransitionRequests=" + duplicateTransitionRequests);
        System.out.println("duplicateTransitionApplications=" + duplicateTransitionApplications);
        System.out.println("recursiveTransitionAttempts=" + recursiveTransitionAttempts);
        System.out.println("multipleTransitionAttemptsSameTick=" + multipleTransitionAttemptsSameTick);
        System.out.println("controllerTicks=" + group3v3ControllerTicks + "|" +
                group3v2ControllerTicks + "|" + group3v1ControllerTicks + "|" +
                group2v2ControllerTicks + "|" + twoVsOneControllerTicks + "|" + duelControllerTicks);
        System.out.println("deadReferencePurges=" + deadReferencePurges);
        System.out.println("deadReferencesAfterTransition=" + deadReferencesAfterTransition);
        System.out.println("maximumTicksWithoutPositionChange=" + maximumTicksWithoutPositionChange);
        System.out.println("maximumTicksWithoutStateChange=" + maximumTicksWithoutStateChange);
        System.out.println("maximumRepeatedTransitionStateTicks=" + maximumRepeatedTransitionStateTicks);
        System.out.println("logicalFreezeReports=" + logicalFreezeReports);
        System.out.println("transitionFailsafePauses=" + transitionFailsafePauses);
        System.out.println("approachAudits=" + approachAudits);
        System.out.println("flatFrontConvergences=" + flatFrontConvergences);
        System.out.println("simultaneousSixPlayerContacts=" + simultaneousSixPlayerContacts);
        System.out.println("averageFirstContactSpread=" +
                (approachAudits == 0 ? 0 : firstContactSpreadSum / approachAudits));
        System.out.println("averagePreContactLateralDistance=" +
                (approachAudits == 0 ? 0 : preContactLateralDistanceSum / approachAudits));
        System.out.println("averagePreContactDepth=" +
                (preContactSamples == 0 ? 0 : preContactDepthSum / preContactSamples));
        System.out.println("averageTimeToSpaceContest=" +
                (spaceContestSamples == 0 ? 0 : timeToSpaceContestSum / spaceContestSamples));
        System.out.println("averageTimeToFirstContact=" +
                (approachAudits == 0 ? 0 : timeToFirstContactSum / approachAudits));
        System.out.println("averageTimeToFirstAttack=" +
                (firstAttackSamples == 0 ? 0 : timeToFirstAttackSum / firstAttackSamples));
        System.out.println("firstContactCoverage=" + coveredFirstContacts + "|" + uncoveredFirstContacts);
        System.out.println("consoleLinesEmitted=" + consoleLinesEmitted);
        System.out.println("duplicateConsoleLinesSuppressed=" + duplicateConsoleLinesSuppressed);
        System.out.println("invalidPositions=" + invalidPositions);
        System.out.println("arenaExits=" + arenaExits);
        System.out.println("determinismFailures=" + determinismFailures);
        require(completed == matches && timeouts == 0,
                "Every experimental 3v3 match must complete within 120 seconds.");
        require(clusters > 0 && isolations > 0 && clusterAttacks > 0,
                "Natural matches must exercise clusters, durable isolation and cluster attacks.");
        require(group3v2 > 0 && group3v1 > 0 && handoff2v2 > 0 && handoff2v1 > 0 && handoff1v1 > 0,
                "Natural batch must exercise every collective transition and handoff.");
        require(transitionRequests == transitionApplications && duplicateTransitionRequests == 0 &&
                        duplicateTransitionApplications == 0 && recursiveTransitionAttempts == 0 &&
                        multipleTransitionAttemptsSameTick == 0,
                "Every roster request must be applied exactly once without recursion or duplication.");
        require(group3v3ControllerTicks > 0 && group3v2ControllerTicks > 0 &&
                        group3v1ControllerTicks > 0 && group2v2ControllerTicks > 0 &&
                        twoVsOneControllerTicks > 0 && duelControllerTicks > 0,
                "Natural matches must exercise every active combat controller.");
        require(deadReferencePurges == transitionApplications && deadReferencesAfterTransition == 0,
                "Every applied roster transition must finish with a clean dead-reference audit.");
        require(maximumRepeatedTransitionStateTicks == 0 && logicalFreezeReports == 0 &&
                        transitionFailsafePauses == 0 && consoleLinesEmitted == 0,
                "Natural matches must not freeze, repeat transition state or spam the console.");
        require(invalidPositions == 0 && arenaExits == 0 && determinismFailures == 0,
                "Batch must remain finite, inside the arena and deterministic.");
        require(approachAudits == matches && coveredFirstContacts + uncoveredFirstContacts > 0,
                "Every match must preserve a first-contact audit and physical coverage evidence.");
        System.out.println("Experimental 3v3 batch passed.");
    }

    private boolean deterministic(long seed, DuelSimulation reference) {
        var replay = fixture(seed);
        for (var tick = 0; tick < 14_400 && !replay.experimentalRoundComplete(); ++tick)
            replay.update(DT);
        var firstKill = reference.killExplanations().isEmpty() ? "NONE" :
                reference.killExplanations().getFirst().deterministicKey(UiLanguage.FRENCH);
        var replayFirstKill = replay.killExplanations().isEmpty() ? "NONE" :
                replay.killExplanations().getFirst().deterministicKey(UiLanguage.FRENCH);
        return replay.experimentalRoundComplete() == reference.experimentalRoundComplete() &&
                replay.experimentalRoundLeader() == reference.experimentalRoundLeader() &&
                replay.experimentalGroupPhase() == reference.experimentalGroupPhase() &&
                replay.group3Metrics().group3v2Created == reference.group3Metrics().group3v2Created &&
                replay.group3Metrics().group3v1Created == reference.group3Metrics().group3v1Created &&
                replay.group3Metrics().approachAudits == reference.group3Metrics().approachAudits &&
                replay.group3Metrics().firstContactSpread == reference.group3Metrics().firstContactSpread &&
                replay.group3Metrics().preContactLateralDistance ==
                        reference.group3Metrics().preContactLateralDistance &&
                firstKill.equals(replayFirstKill);
    }

    private DuelSimulation fixture(long seed) {
        var blue = new TeamConfiguration3("Blue", AgentConfiguration.defaultBlue(),
                AgentConfiguration.defaultBlueTwo(), AgentConfiguration.defaultBlueThree());
        var red = new TeamConfiguration3("Red", AgentConfiguration.defaultRed(),
                AgentConfiguration.defaultRedTwo(), AgentConfiguration.defaultRedThree());
        return new DuelSimulation(blue, red, SimulationScenario.CONTROLLED_3V3, seed);
    }

    private int living(Musketeer[] team) {
        var count = 0;
        for (var agent : team)
            if (agent.alive) ++count;
        return count;
    }

    private String timeoutState(DuelSimulation simulation) {
        var state = new StringBuilder();
        for (var agent : simulation.agents) {
            if (!agent.alive) continue;
            if (!state.isEmpty()) state.append('|');
            state.append(agent.name(UiLanguage.FRENCH)).append('/').append(agent.state).
                    append('/').append(agent.decision).append("/target=").
                    append(agent.target == null ? "NONE" : agent.target.name(UiLanguage.FRENCH)).
                    append("/distance=").append(agent.target == null ? "NONE" :
                            "%.2f".formatted(agent.target.pos.minus(agent.pos).hypotenuse()));
        }
        return state.toString();
    }

    private void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
