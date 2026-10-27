final class TimeoutReplayTest {
    private static final long[] SEEDS = {182, 208, 258, 311, 418};

    public static void main(String[] args) {
        for (var seed : SEEDS) {
            var first = replay(seed);
            var second = replay(seed);
            require(first.cause == second.cause && first.snapshot.equals(second.snapshot),
                    "G: timeout replay must remain deterministic for seed " + seed);
            System.out.print(first.snapshot);
        }
        System.out.println("Timeout replay test G passed.");
    }

    private static TimeoutReport replay(long seed) {
        var simulation = new DuelSimulation(AgentConfiguration.defaultBlue(),
                AgentConfiguration.defaultBlueTwo(), AgentConfiguration.defaultRed(),
                AgentConfiguration.defaultRedTwo(), SimulationScenario.MODERN_2V2, seed);
        while (simulation.winningTeam == null && simulation.simulationTime < 180)
            simulation.update(1.0 / 120.0);
        return TimeoutReport.capture(seed, simulation);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
