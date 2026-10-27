final class LocalManeuver {
    LocalManeuverType type;
    Pos destination;
    Musketeer referenceAgent, referencedThreat;
    double startedAt, minimumDuration, maximumDuration, initialScore;
    String primaryReason;
    boolean interruptible;

    LocalManeuver(LocalManeuverType type, Pos destination, Musketeer referenceAgent,
                  Musketeer referencedThreat, double startedAt, double minimumDuration,
                  double maximumDuration, double initialScore, String primaryReason,
                  boolean interruptible) {
        this.type = type;
        this.destination = destination;
        this.referenceAgent = referenceAgent;
        this.referencedThreat = referencedThreat;
        this.startedAt = startedAt;
        this.minimumDuration = minimumDuration;
        this.maximumDuration = maximumDuration;
        this.initialScore = initialScore;
        this.primaryReason = primaryReason;
        this.interruptible = interruptible;
    }

    double age(double simulationTime) {
        return simulationTime - startedAt;
    }
}
