record ScenarioSetup(
        ScenarioFamily family,
        long seed,
        AgentSpawn blueOne,
        AgentSpawn blueTwo,
        AgentSpawn redOne,
        AgentSpawn redTwo,
        int rejectedPlacements,
        boolean baselineFallback
) {
    AgentSpawn[] spawns() {
        return new AgentSpawn[]{blueOne, blueTwo, redOne, redTwo};
    }
}
