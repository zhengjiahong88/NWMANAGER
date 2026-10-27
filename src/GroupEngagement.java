import java.util.IdentityHashMap;

final class GroupEngagement {
    final Musketeer[] blue, red;
    final IdentityHashMap<Musketeer, GroupAssignment> assignments = new IdentityHashMap<>();
    Pos center = new Pos(), blueCenter = new Pos(), redCenter = new Pos();
    GroupPhase phase = GroupPhase.APPROACH;
    RotationDirection rotation;
    double phaseStartedAt, rotationStartedAt, markChangedAt,
            desiredAngularVelocity = DuelSimulation.GROUP_DESIRED_ANGULAR_VELOCITY,
            circulationReferenceVelocity = DuelSimulation.GROUP_CIRCULATION_REFERENCE_VELOCITY,
            nextRotationEvaluationAt, rotationSegmentAngularDisplacement;
    Pos previousCenter = new Pos(), previousBlueCenter = new Pos(), previousRedCenter = new Pos();
    double previousBluePartnerDistance, previousRedPartnerDistance, previousFormationArea;
    final IdentityHashMap<Musketeer, Pos> previousPositions = new IdentityHashMap<>();
    final IdentityHashMap<Musketeer, Double> previousCenterAngles = new IdentityHashMap<>();
    Musketeer committedAttacker, committedTarget, revenger, vulnerableAttacker, counterCoverer;
    RevengeOpportunity revengeOpportunity;
    CounterCoverAssessment counterCoverAssessment;
    TeamAttackArbitration blueAttackArbitration, redAttackArbitration;
    TeamManeuver blueManeuver, redManeuver;

    GroupEngagement(Musketeer[] blue, Musketeer[] red, RotationDirection rotation,
                    double simulationTime) {
        this.blue = blue.clone();
        this.red = red.clone();
        this.rotation = rotation;
        phaseStartedAt = rotationStartedAt = markChangedAt = simulationTime;
        nextRotationEvaluationAt = simulationTime;
        updateCenters();
        previousCenter = center;
        previousBlueCenter = blueCenter;
        previousRedCenter = redCenter;
        previousBluePartnerDistance = blue[0].pos.minus(blue[1].pos).hypotenuse();
        previousRedPartnerDistance = red[0].pos.minus(red[1].pos).hypotenuse();
        previousFormationArea = formationArea();
        for (var agent : allAgents()) {
            previousPositions.put(agent, agent.pos);
            previousCenterAngles.put(agent, agent.pos.minus(center).radians());
        }
    }

    void updateCenters() {
        blueCenter = average(blue);
        redCenter = average(red);
        center = blueCenter.plus(redCenter).div(2);
    }

    private Pos average(Musketeer[] team) {
        var total = new Pos();
        var count = 0;
        for (var agent : team)
            if (agent.alive) {
                total = total.plus(agent.pos);
                ++count;
            }
        return count == 0 ? total : total.div(count);
    }

    Musketeer[] allAgents() {
        return new Musketeer[]{blue[0], blue[1], red[0], red[1]};
    }

    Musketeer[] team(Team team) {
        return team == Team.BLUE ? blue : red;
    }

    Musketeer[] opponents(Team team) {
        return team == Team.BLUE ? red : blue;
    }

    Pos teamCenter(Team team) {
        return team == Team.BLUE ? blueCenter : redCenter;
    }

    Pos enemyCenter(Team team) {
        return team == Team.BLUE ? redCenter : blueCenter;
    }

    TeamManeuver maneuver(Team team) {
        return team == Team.BLUE ? blueManeuver : redManeuver;
    }

    void maneuver(Team team, TeamManeuver maneuver) {
        if (team == Team.BLUE) blueManeuver = maneuver;
        else redManeuver = maneuver;
    }

    double formationArea() {
        var ordered = new Musketeer[]{blue[0], red[0], red[1], blue[1]};
        var twiceArea = 0.0;
        for (var n = 0; n < ordered.length; ++n) {
            var current = ordered[n].pos;
            var next = ordered[(n + 1) % ordered.length].pos;
            twiceArea += current.x() * next.y() - current.y() * next.x();
        }
        return Math.abs(twiceArea) / 2;
    }
}
