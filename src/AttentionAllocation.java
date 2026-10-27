final class AttentionAllocation {
    Musketeer primaryThreat, secondaryThreat, peripheralThreat, pendingPrimary, formerPrimary;
    double primaryWeight = 0.55, secondaryWeight = 0.30, peripheralWeight = 0.15,
            lastShiftTime, secondaryLastShiftTime, pendingSince, switchCompletesAt, precisionPenaltyUntil;
    String shiftReason = "initial_geometry", lastCompletedShiftReason = "none";

    AttentionLevel levelOf(Musketeer opponent) {
        if (opponent == primaryThreat) return AttentionLevel.PRIMARY;
        if (opponent == secondaryThreat) return AttentionLevel.SECONDARY;
        if (opponent == peripheralThreat) return AttentionLevel.PERIPHERAL;
        return AttentionLevel.NONE;
    }

    double weightOf(Musketeer opponent) {
        return switch (levelOf(opponent)) {
            case PRIMARY -> primaryWeight;
            case SECONDARY -> secondaryWeight;
            case PERIPHERAL -> peripheralWeight;
            case NONE -> 0;
        };
    }

    boolean switching(double simulationTime) {
        return pendingPrimary != null && simulationTime < switchCompletesAt;
    }
}
