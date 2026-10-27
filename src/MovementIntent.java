final class MovementIntent {
    Decision source = Decision.WAIT;
    CombatState state = CombatState.NEUTRAL;
    Musketeer target;
    double remaining, preferredDistance = 1.55, rangeGain = 0.85, radialBias,
            orbitWeight, momentumWeight, cohesionWeight = 0.18, threatAvoidance = 0.42,
            facingMovementBlend = 0.18, speedFactor = 0.82;
    int side = 1;
}
