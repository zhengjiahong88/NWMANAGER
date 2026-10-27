import java.util.List;

final class DeadAgentDifferentialResult {
    final String runName;
    final long seed, deathTick;
    final double deathTime;
    final List<SurvivorTickSnapshot> snapshots;
    final List<CombatEventSnapshot> combatEvents;
    final List<TransitionTrace> transitions;
    final Team winner;
    final double matchEndTime;
    final boolean matchCompleted;
    final DeadAgentAudit deadAgentAudit;
    final DeadAgentStateSnapshot deadAgentState;
    final List<DifferentialStageSnapshot> stageSnapshots;
    final long randomDrawCount, randomStateHash;

    DeadAgentDifferentialResult(String runName, long seed, long deathTick, double deathTime,
                                List<SurvivorTickSnapshot> snapshots,
                                List<CombatEventSnapshot> combatEvents,
                                List<TransitionTrace> transitions, Team winner,
                                double matchEndTime, boolean matchCompleted,
                                DeadAgentAudit deadAgentAudit,
                                DeadAgentStateSnapshot deadAgentState,
                                List<DifferentialStageSnapshot> stageSnapshots,
                                long randomDrawCount, long randomStateHash) {
        this.runName = runName;
        this.seed = seed;
        this.deathTick = deathTick;
        this.deathTime = deathTime;
        this.snapshots = List.copyOf(snapshots);
        this.combatEvents = List.copyOf(combatEvents);
        this.transitions = List.copyOf(transitions);
        this.winner = winner;
        this.matchEndTime = matchEndTime;
        this.matchCompleted = matchCompleted;
        this.deadAgentAudit = deadAgentAudit;
        this.deadAgentState = deadAgentState;
        this.stageSnapshots = List.copyOf(stageSnapshots);
        this.randomDrawCount = randomDrawCount;
        this.randomStateHash = randomStateHash;
    }
}
