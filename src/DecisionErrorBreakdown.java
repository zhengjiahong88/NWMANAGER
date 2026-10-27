record DecisionErrorBreakdown(double perceptionPositionError,
                              double perceptionVelocityError,
                              double perceptionOrientationError,
                              double perceptionDistanceError,
                              double evaluationNoise,
                              double tacticalMisjudgment,
                              double selectionRegret,
                              double releaseTimingError,
                              double aimError,
                              double movementExecutionError) {
    static DecisionErrorBreakdown unrecorded() {
        return new DecisionErrorBreakdown(Double.NaN, Double.NaN, Double.NaN, Double.NaN,
                Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN);
    }

    static String report(double value) {
        return Double.isNaN(value) ? "NONE_RECORDED" : "%.6f".formatted(value);
    }
}
