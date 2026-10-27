final class TeamManeuver {
    final Team team;
    final TeamManeuverType type;
    final double startedAt, minimumDuration, preferredDuration, maximumDuration;
    final Pos teamCenterAtStart, enemyCenterAtStart;
    final int directionSign;
    final double confidence;
    final String primaryReason;
    Musketeer innerPlayer, outerPlayer;

    TeamManeuver(Team team, TeamManeuverType type, double startedAt,
                 double minimumDuration, double preferredDuration, double maximumDuration,
                 Pos teamCenterAtStart, Pos enemyCenterAtStart, int directionSign,
                 Musketeer innerPlayer, Musketeer outerPlayer, double confidence,
                 String primaryReason) {
        this.team = team;
        this.type = type;
        this.startedAt = startedAt;
        this.minimumDuration = minimumDuration;
        this.preferredDuration = preferredDuration;
        this.maximumDuration = maximumDuration;
        this.teamCenterAtStart = teamCenterAtStart;
        this.enemyCenterAtStart = enemyCenterAtStart;
        this.directionSign = directionSign;
        this.innerPlayer = innerPlayer;
        this.outerPlayer = outerPlayer;
        this.confidence = confidence;
        this.primaryReason = primaryReason;
    }

    double age(double simulationTime) {
        return simulationTime - startedAt;
    }

    boolean rotating() {
        return type == TeamManeuverType.ROTATE_LEFT || type == TeamManeuverType.ROTATE_RIGHT;
    }
}
