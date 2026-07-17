final class OpponentAwareness {
    double lastKnownX, lastKnownY, memoryTime;
    int observedUpAttacks, observedDownAttacks, observedUpBlocks, observedDownBlocks,
            lastObservedAttackSequence = -1, lastObservedBlockSequence = -1;
    boolean visible;

    OpponentAwareness(double x, double y, boolean visible) {
        lastKnownX = x;
        lastKnownY = y;
        this.visible = visible;
        memoryTime = visible ? 1 : 0;
    }
}
