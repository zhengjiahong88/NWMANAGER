final class Group3ApproachPlan {
    final Team team;
    final ApproachPattern pattern;
    final double startedAt, minimumDuration, preferredDuration, maximumDuration;
    final FormationRole3 leadRole, supportRole, trailingRole;
    final int lateralSign;
    final double forwardCommitment, lateralCommitment, depthCommitment;
    final Pos initialTeamCenter, predictedMeetingPoint;
    final String primaryReason;

    Group3ApproachPlan(Team team, ApproachPattern pattern, double startedAt,
                       double minimumDuration, double preferredDuration, double maximumDuration,
                       FormationRole3 leadRole, FormationRole3 supportRole,
                       FormationRole3 trailingRole, int lateralSign, double forwardCommitment,
                       double lateralCommitment, double depthCommitment, Pos initialTeamCenter,
                       Pos predictedMeetingPoint, String primaryReason) {
        this.team = team;
        this.pattern = pattern;
        this.startedAt = startedAt;
        this.minimumDuration = minimumDuration;
        this.preferredDuration = preferredDuration;
        this.maximumDuration = maximumDuration;
        this.leadRole = leadRole;
        this.supportRole = supportRole;
        this.trailingRole = trailingRole;
        this.lateralSign = lateralSign;
        this.forwardCommitment = forwardCommitment;
        this.lateralCommitment = lateralCommitment;
        this.depthCommitment = depthCommitment;
        this.initialTeamCenter = initialTeamCenter;
        this.predictedMeetingPoint = predictedMeetingPoint;
        this.primaryReason = primaryReason;
    }

    double age(double simulationTime) {
        return simulationTime - startedAt;
    }
}
