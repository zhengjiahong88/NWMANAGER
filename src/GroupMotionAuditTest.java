public final class GroupMotionAuditTest {
    private static final double DT = 1.0 / 120.0, DURATION = 8;

    public static void main(String[] args) {
        verifyIndependentMovementWithoutAttacks();
        verifyDeterministicDiagnostics();
        System.out.println("Group motion audit test A passed.");
    }

    private static void verifyIndependentMovementWithoutAttacks() {
        var simulation = simulation(601L);
        var audit = new GroupMotionAudit();
        while (simulation.simulationTime < DURATION) {
            disableAttacks(simulation);
            simulation.update(DT);
            audit.observe(simulation, DT);
        }
        audit.finish(simulation.simulationTime);
        var metrics = audit.metrics();
        require(simulation.modernMetrics().stationaryEpisodes > 0,
                "A: at least one individual stop must remain observable.");
        require(metrics.localDirectionOppositionTime() > 0.10,
                "A: partners must sometimes move in locally opposing directions.");
        require(metrics.formationAreaVariation() > 0.05 &&
                        metrics.partnerDistanceVariation() > 0.03,
                "A: the formation must deform instead of moving as a rigid plate.");
        require(metrics.individualVelocityCorrelation() < 0.90,
                "A: four-agent movement must not remain permanently synchronized.");
        var synchronizedTime = 0.0;
        for (var event : audit.events())
            if (event.suspicion() == GroupMotionSuspicion.FOUR_AGENT_DIRECTION_SYNC)
                synchronizedTime += event.endedAt() - event.startedAt();
        require(synchronizedTime < DURATION * 0.60,
                "A: diagnostic synchronization must remain temporary, not permanent.");
    }

    private static void verifyDeterministicDiagnostics() {
        var first = replay(602L);
        var second = replay(602L);
        require(first.metrics().equals(second.metrics()) && first.events().equals(second.events()),
                "A: identical seeds must produce identical movement diagnostics.");
    }

    private static AuditResult replay(long seed) {
        var simulation = simulation(seed);
        var audit = new GroupMotionAudit();
        while (simulation.simulationTime < 5 && aliveCount(simulation) == 4) {
            simulation.update(DT);
            audit.observe(simulation, DT);
        }
        audit.finish(simulation.simulationTime);
        return new AuditResult(audit.metrics(), audit.events());
    }

    private static void disableAttacks(DuelSimulation simulation) {
        for (var agent : simulation.agents) {
            agent.state = CombatState.BLOCKING;
            agent.stateTime = 0;
            agent.decisionTime = 20;
        }
    }

    private static DuelSimulation simulation(long seed) {
        return new DuelSimulation(AgentConfiguration.defaultBlue(),
                AgentConfiguration.defaultBlueTwo(), AgentConfiguration.defaultRed(),
                AgentConfiguration.defaultRedTwo(), SimulationScenario.MODERN_2V2, seed);
    }

    private static int aliveCount(DuelSimulation simulation) {
        var count = 0;
        for (var agent : simulation.agents) if (agent.alive) ++count;
        return count;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    private record AuditResult(GroupMotionMetrics metrics,
                               java.util.List<GroupMotionSuspicionEvent> events) {
    }
}
