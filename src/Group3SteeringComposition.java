record Group3SteeringComposition(
        Pos approachContribution,
        Pos teamManeuverContribution,
        Pos localManeuverContribution,
        Pos formationContribution,
        Pos coverContribution,
        Pos avoidanceContribution,
        Pos boundaryContribution
) {
    Pos desiredVelocity() {
        return approachContribution.plus(teamManeuverContribution).plus(localManeuverContribution).
                plus(formationContribution).plus(coverContribution).plus(avoidanceContribution).
                plus(boundaryContribution);
    }
}
