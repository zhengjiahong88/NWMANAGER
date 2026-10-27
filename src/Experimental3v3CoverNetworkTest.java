final class Experimental3v3CoverNetworkTest {
    private static final double DT = 1.0 / 120.0;

    public static void main(String[] args) {
        var test = new Experimental3v3CoverNetworkTest();
        test.verifyANetworkContainsPhysicalLinks();
        test.verifyBSeparatedWingLosesLinksLocally();
        test.verifyCCoverageLossChangesTheNextManeuver();
        test.verifyDLinksRespectPerceptionQuality();
        test.verifyEDeterminism();
        System.out.println("Experimental GROUP_3V3 cover-network tests A-E passed.");
    }

    private void verifyANetworkContainsPhysicalLinks() {
        var simulation = fixture(3_301L);
        for (var tick = 0; tick < 180; ++tick) simulation.update(DT);
        var group = simulation.modernGroup3Engagement();
        for (var team : new Team[]{Team.BLUE, Team.RED}) {
            var network = group.coverNetwork(team);
            require(network.links.size() == 18,
                    "A: each team must evaluate 3 protectors × 2 allies × 3 enemies.");
            require(network.activeLinks() > 0 && network.connectivity > 0,
                    "A: coherent formations must create viable physical cover links: " + network.links);
            require(network.weakestMember != null && network.weakestMemberCoverage >= 0 &&
                            network.weakestMemberCoverage <= 1,
                    "A: the network must expose its weakest covered member.");
        }
    }

    private void verifyBSeparatedWingLosesLinksLocally() {
        var simulation = fixture(3_302L);
        for (var tick = 0; tick < 180; ++tick) simulation.update(DT);
        var group = simulation.modernGroup3Engagement();
        var formation = group.blueFormation;
        var network = group.blueCoverNetwork;
        var separated = formation.rightWing;
        var stable = formation.leftWing;
        var separatedBefore = viableIncoming(network, separated);
        var stableBefore = viableIncoming(network, stable);
        separated.pos = new Pos(0.55, 6.40);
        simulation.update(DT);
        var separatedAfter = viableIncoming(network, separated);
        var stableAfter = viableIncoming(network, stable);
        require(separatedAfter < separatedBefore,
                "B: moving one wing away must break links directed toward that wing (" +
                        separatedBefore + " -> " + separatedAfter + ").");
        require(stableAfter > 0 && stableAfter >= stableBefore - 2,
                "B: links on the opposite wing must not all disappear with the separated wing.");
    }

    private void verifyCCoverageLossChangesTheNextManeuver() {
        var simulation = fixture(3_303L);
        for (var tick = 0; tick < 120; ++tick) simulation.update(DT);
        var group = simulation.modernGroup3Engagement();
        group.blueFormation.rightWing.pos = new Pos(0.45, 6.45);
        group.blueManeuver = null;
        simulation.update(DT);
        require(group.blueManeuver.type == TeamManeuverType.RECOVER_FORMATION ||
                        group.blueManeuver.type == TeamManeuverType.COMPRESS,
                "C: severe network loss must influence the next maneuver assessment.");
        require(group.blueCoverNetwork.weakestMemberCoverage < 0.16,
                "C: the maneuver response must correspond to a measured weak member.");
    }

    private void verifyDLinksRespectPerceptionQuality() {
        var simulation = fixture(3_304L);
        for (var tick = 0; tick < 240; ++tick) simulation.update(DT);
        var observer = simulation.blueTeam[0];
        var network = simulation.modernGroup3Engagement().blueCoverNetwork;
        var primary = observer.attentionAllocation.primaryThreat;
        var peripheral = observer.attentionAllocation.peripheralThreat;
        var primaryConfidence = bestConfidence(network, observer, primary);
        var peripheralConfidence = bestConfidence(network, observer, peripheral);
        require(primaryConfidence > peripheralConfidence,
                "D: cover links based on the primary threat must be more certain than peripheral links (" +
                        primaryConfidence + "/" + peripheralConfidence + ").");
    }

    private void verifyEDeterminism() {
        var first = fixture(3_305L);
        var second = fixture(3_305L);
        for (var tick = 0; tick < 600; ++tick) {
            first.update(DT);
            second.update(DT);
        }
        var firstNetwork = first.modernGroup3Engagement().blueCoverNetwork;
        var secondNetwork = second.modernGroup3Engagement().blueCoverNetwork;
        require(firstNetwork.activeLinks() == secondNetwork.activeLinks() &&
                        Math.abs(firstNetwork.connectivity - secondNetwork.connectivity) < 0.000_000_1 &&
                        Math.abs(firstNetwork.weakestMemberCoverage -
                                secondNetwork.weakestMemberCoverage) < 0.000_000_1,
                "E: same seed must reproduce the same viable blue cover relations.");
        require(first.group3Metrics().coverLinksCreated == second.group3Metrics().coverLinksCreated &&
                        first.group3Metrics().coverLinksBroken == second.group3Metrics().coverLinksBroken,
                "E: same seed must reproduce cover-network transitions.");
    }

    private int viableIncoming(CoverNetwork network, Musketeer member) {
        var count = 0;
        for (var link : network.incomingLinks.get(member))
            if (link.viable()) ++count;
        return count;
    }

    private double bestConfidence(CoverNetwork network, Musketeer protector, Musketeer enemy) {
        var confidence = 0.0;
        for (var link : network.outgoingLinks.get(protector))
            if (link.threateningEnemy() == enemy) confidence = Math.max(confidence, link.perceptionConfidence());
        return confidence;
    }

    private DuelSimulation fixture(long seed) {
        var blue = new TeamConfiguration3("Blue", AgentConfiguration.defaultBlue(),
                AgentConfiguration.defaultBlueTwo(), AgentConfiguration.defaultBlueThree());
        var red = new TeamConfiguration3("Red", AgentConfiguration.defaultRed(),
                AgentConfiguration.defaultRedTwo(), AgentConfiguration.defaultRedThree());
        var simulation = new DuelSimulation(blue, red, SimulationScenario.CONTROLLED_3V3, seed);
        simulation.disableGroup3AttacksForTest();
        return simulation;
    }

    private void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
