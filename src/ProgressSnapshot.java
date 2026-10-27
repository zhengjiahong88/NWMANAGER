record ProgressSnapshot(long tick, double simulationTime, ActiveCombatController controller,
                        RosterSnapshot roster, Pos[] positions, CombatState[] states) {
    ProgressSnapshot {
        positions = positions.clone();
        states = states.clone();
    }
}
