import java.util.IdentityHashMap;

final class Group3ApproachObservation {
    final double startedAt;
    final Pos blueInitialCenter, redInitialCenter, blueInitialForward, redInitialForward,
            blueInitialLateral, redInitialLateral;
    Pos bluePreviousCenter, redPreviousCenter;
    final IdentityHashMap<Musketeer, FirstContactEvent> firstContacts = new IdentityHashMap<>();
    double firstContactAt = Double.NaN, lastFirstContactAt = Double.NaN;
    double preContactForwardDistance, preContactLateralDistance, preContactDepthSum,
            preContactWidthSum, maximumPreContactDepth, pathCorrelationSum,
            centerLineDeviationSum;
    int preContactSamples, pathCorrelationSamples;
    FormationRole3 blueLastLeadRole, redLastLeadRole;
    FlatFrontConvergenceAudit flatFrontAudit;

    Group3ApproachObservation(double startedAt, TeamFormation3 blue, TeamFormation3 red) {
        this.startedAt = startedAt;
        blueInitialCenter = bluePreviousCenter = blue.center;
        redInitialCenter = redPreviousCenter = red.center;
        blueInitialForward = blue.forward;
        redInitialForward = red.forward;
        blueInitialLateral = blue.lateral;
        redInitialLateral = red.lateral;
    }

    FormationRole3 lastLeadRole(Team team) {
        return team == Team.BLUE ? blueLastLeadRole : redLastLeadRole;
    }

    void lastLeadRole(Team team, FormationRole3 role) {
        if (team == Team.BLUE) blueLastLeadRole = role;
        else redLastLeadRole = role;
    }

    double firstContactSpread() {
        return Double.isNaN(firstContactAt) ? 0 : lastFirstContactAt - firstContactAt;
    }
}
