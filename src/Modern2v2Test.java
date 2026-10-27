import java.util.IdentityHashMap;

final class Modern2v2Test {
    private static final double DT = 1.0 / 120.0;

    public static void main(String[] args) {
        var test = new Modern2v2Test();
        test.verifyModernRatingIgnoresInactiveStats();
        test.verifySingleInitialGroup();
        test.verifyEliminationReconstruction();
        test.verifyDeterminism();
        System.out.println("Modern 2v2 tests passed.");
    }

    private void verifyModernRatingIgnoresInactiveStats() {
        var base = new AgentStats(82, 84, 79, 86, 83, 80, 10, 81,
                1, 85, 1, 1, 1, 88);
        var changedInactive = new AgentStats(82, 84, 79, 86, 83, 80, 100, 81,
                100, 85, 100, 100, 100, 88);
        require(base.overallRating(RatingProfile.MODERN) ==
                        changedInactive.overallRating(RatingProfile.MODERN),
                "Modern ratings must ignore inactive historical attributes.");
        require(base.overallRating(RatingProfile.LEGACY) !=
                        changedInactive.overallRating(RatingProfile.LEGACY),
                "Legacy ratings must preserve the historical formula.");
    }

    private void verifySingleInitialGroup() {
        var simulation = modern(31L);
        require(activeGroups(simulation) == 1 && activeDuels(simulation) == 0,
                "Four modern survivors must belong to one GROUP_2V2.");
        verifyEngagementIntegrity(simulation);
        for (var tick = 0; tick < 180; ++tick) simulation.update(DT);
        require(activeGroups(simulation) == 1 && activeDuels(simulation) == 0,
                "Ordinary rotation must not split the group into two duels.");
        for (var agent : simulation.agents)
            require(agent.primaryMark != null && agent.secondaryThreat != null &&
                            agent.tacticalIntent == TacticalIntent.GROUP,
                    "Every group member must retain collective assignments.");
    }

    private void verifyEliminationReconstruction() {
        var twoVsOne = modern(61L);
        kill(twoVsOne.redTeam[0]);
        twoVsOne.update(DT);
        require(activeTwoVsOne(twoVsOne) != null && activeGroups(twoVsOne) == 0,
                "A death must break GROUP_2V2 and create the real 2v1.");
        verifyEngagementIntegrity(twoVsOne);

        var oneVsOne = modern(62L);
        kill(oneVsOne.blueTeam[1]);
        kill(oneVsOne.redTeam[1]);
        oneVsOne.update(DT);
        require(activeDuels(oneVsOne) == 1 && activeGroups(oneVsOne) == 0,
                "One survivor per team must rebuild one duel.");
        verifyEngagementIntegrity(oneVsOne);
    }

    private void verifyDeterminism() {
        var first = modern(9_818L);
        var second = modern(9_818L);
        for (var tick = 0; tick < 1_200; ++tick) {
            first.update(DT);
            second.update(DT);
            for (var n = 0; n < first.agents.length; ++n) {
                var left = first.agents[n];
                var right = second.agents[n];
                require(left.pos.equals(right.pos) && left.velocity.equals(right.velocity) &&
                                left.state == right.state && left.decision == right.decision &&
                                left.alive == right.alive &&
                                left.tacticalIntent == right.tacticalIntent &&
                                left.groupIntent == right.groupIntent &&
                                indexOf(first, left.primaryMark) ==
                                        indexOf(second, right.primaryMark) &&
                                indexOf(first, left.target) == indexOf(second, right.target),
                        "Equal seeds and time steps must produce equal group states.");
            }
            require(first.winningTeam == second.winningTeam,
                    "Equal seeds must produce the same result.");
            if (first.winningTeam != null) break;
        }
        require(first.tacticalLog().equals(second.tacticalLog()),
                "Equal seeds must produce identical tactical transitions.");
    }

    private void verifyEngagementIntegrity(DuelSimulation simulation) {
        var membership = new IdentityHashMap<Musketeer, Integer>();
        for (var engagement : simulation.engagements()) {
            if (!active(engagement)) continue;
            switch (engagement.type) {
                case GROUP_2V2 -> {
                    for (var agent : engagement.group.blue) addMembership(membership, agent);
                    for (var agent : engagement.group.red) addMembership(membership, agent);
                }
                case DUEL -> {
                    addMembership(membership, engagement.duelistOne);
                    addMembership(membership, engagement.duelistTwo);
                }
                case TWO_VS_ONE -> {
                    addMembership(membership, engagement.fixer);
                    addMembership(membership, engagement.flanker);
                    addMembership(membership, engagement.primaryTarget);
                    if (engagement.interceptor != null)
                        addMembership(membership, engagement.interceptor);
                }
            }
        }
        for (var count : membership.values())
            require(count == 1, "An Agent belongs to multiple active Engagements.");
    }

    private void addMembership(IdentityHashMap<Musketeer, Integer> membership,
                               Musketeer agent) {
        if (agent.alive) membership.put(agent, membership.getOrDefault(agent, 0) + 1);
    }

    private int activeGroups(DuelSimulation simulation) {
        return count(simulation, EngagementType.GROUP_2V2);
    }

    private int activeDuels(DuelSimulation simulation) {
        return count(simulation, EngagementType.DUEL);
    }

    private int count(DuelSimulation simulation, EngagementType type) {
        var count = 0;
        for (var engagement : simulation.engagements())
            if (active(engagement) && engagement.type == type) ++count;
        return count;
    }

    private Engagement activeTwoVsOne(DuelSimulation simulation) {
        for (var engagement : simulation.engagements())
            if (active(engagement) && engagement.type == EngagementType.TWO_VS_ONE)
                return engagement;
        return null;
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

    private void kill(Musketeer agent) {
        agent.alive = false;
        agent.hitPoints = 0;
        agent.state = CombatState.DEAD;
    }

    private DuelSimulation modern(long seed) {
        return new DuelSimulation(AgentConfiguration.defaultBlue(),
                AgentConfiguration.defaultBlueTwo(), AgentConfiguration.defaultRed(),
                AgentConfiguration.defaultRedTwo(), SimulationScenario.MODERN_2V2, seed);
    }

    private void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
