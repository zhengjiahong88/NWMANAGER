final class OpponentAwareness {
    Pos lastKnown, perceivedVelocity = new Pos();
    double memoryTime, observedBodyAngle, observedWeaponAngle, observedProjectedRange, observedStateTime;
    CombatState observedState = CombatState.NEUTRAL;
    CombatDirection observedAttackDirection = CombatDirection.UP;
    BlockDirection observedBlockDirection = BlockDirection.UP;
    int observedUpAttacks, observedDownAttacks, observedUpBlocks, observedDownBlocks,
            lastObservedAttackSequence = -1, lastObservedBlockSequence = -1,
            evaluatedFiftyFiftySequence = -1, observationAttempts, observations,
            primaryObservationAttempts, secondaryObservationAttempts, peripheralObservationAttempts;
    double observationCooldown, detailConfidence, lastObservationTime;
    AttentionLevel attentionLevel = AttentionLevel.NONE;
    boolean visible, acceptsFiftyFifty, stateDetailFresh, motionAlert;

    OpponentAwareness(Pos pos, boolean visible) {
        lastKnown = pos;
        this.visible = visible;
        memoryTime = visible ? 1 : 0;
    }
}
