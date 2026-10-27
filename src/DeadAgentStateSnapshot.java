record DeadAgentStateSnapshot(
        Pos deathPosition,
        Pos finalPosition,
        Pos velocity,
        Pos desiredVelocity,
        double deathBodyAngle,
        double deathWeaponAngle,
        double bodyAngle,
        double weaponAngle,
        double angularVelocity,
        CombatState state,
        boolean alive,
        boolean hiddenAfterDeath,
        Pos renderOffset
) {
}
