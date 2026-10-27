import java.util.IdentityHashMap;
import java.util.ArrayList;

final class Group3v3Engagement {
    final Musketeer[] blueMembers, redMembers;
    final TeamFormation3 blueFormation, redFormation;
    final CoverNetwork blueCoverNetwork, redCoverNetwork;
    final Group3ApproachObservation approachObservation;
    TeamManeuver blueManeuver, redManeuver;
    Group3ApproachPlan blueApproachPlan, redApproachPlan;
    Group3ContactBand contactBand = Group3ContactBand.FAR_APPROACH;
    double contactBandChangedAt;
    int blueApproachSequence, redApproachSequence;
    TeamAttackPlan blueAttackPlan, redAttackPlan;
    TeamRevengeCandidate revengeCandidate;
    CounterCoverAssessment counterCoverAssessment;
    double exchangeStartedAt;
    double blueNextAttackPlanAt, redNextAttackPlanAt;
    double nextClusterEvaluationAt;
    final ArrayList<LocalEngagementCluster> localClusters = new ArrayList<>();
    final IdentityHashMap<Musketeer, Double> isolationTime = new IdentityHashMap<>();
    final IdentityHashMap<AttackOpportunity, OpposingCoverSummary> opposingCoverSummaries =
            new IdentityHashMap<>();
    final IdentityHashMap<AttackOpportunity, ClusterOpportunityEvidence> clusterEvidence =
            new IdentityHashMap<>();

    Group3v3Engagement(Musketeer[] blueMembers, Musketeer[] redMembers) {
        if (blueMembers.length != 3 || redMembers.length != 3)
            throw new IllegalArgumentException("GROUP_3V3 requires three members per team.");
        this.blueMembers = blueMembers.clone();
        this.redMembers = redMembers.clone();
        blueFormation = new TeamFormation3(Team.BLUE, blueMembers, average(redMembers));
        redFormation = new TeamFormation3(Team.RED, redMembers, average(blueMembers));
        blueCoverNetwork = new CoverNetwork(Team.BLUE, blueMembers);
        redCoverNetwork = new CoverNetwork(Team.RED, redMembers);
        approachObservation = new Group3ApproachObservation(0, blueFormation, redFormation);
    }

    void updateFormations(double dt) {
        blueFormation.update(redFormation.center, dt, false);
        redFormation.update(blueFormation.center, dt, false);
    }

    TeamFormation3 formation(Team team) {
        return team == Team.BLUE ? blueFormation : redFormation;
    }

    TeamFormation3 enemyFormation(Team team) {
        return team == Team.BLUE ? redFormation : blueFormation;
    }

    Musketeer[] members(Team team) {
        return team == Team.BLUE ? blueMembers : redMembers;
    }

    TeamManeuver maneuver(Team team) {
        return team == Team.BLUE ? blueManeuver : redManeuver;
    }

    void maneuver(Team team, TeamManeuver maneuver) {
        if (team == Team.BLUE) blueManeuver = maneuver;
        else redManeuver = maneuver;
    }

    Group3ApproachPlan approachPlan(Team team) {
        return team == Team.BLUE ? blueApproachPlan : redApproachPlan;
    }

    void approachPlan(Team team, Group3ApproachPlan plan) {
        if (team == Team.BLUE) blueApproachPlan = plan;
        else redApproachPlan = plan;
    }

    int nextApproachSequence(Team team) {
        return team == Team.BLUE ? ++blueApproachSequence : ++redApproachSequence;
    }

    CoverNetwork coverNetwork(Team team) {
        return team == Team.BLUE ? blueCoverNetwork : redCoverNetwork;
    }

    TeamAttackPlan attackPlan(Team team) {
        return team == Team.BLUE ? blueAttackPlan : redAttackPlan;
    }

    void attackPlan(Team team, TeamAttackPlan plan) {
        if (team == Team.BLUE) blueAttackPlan = plan;
        else redAttackPlan = plan;
    }

    double nextAttackPlanAt(Team team) {
        return team == Team.BLUE ? blueNextAttackPlanAt : redNextAttackPlanAt;
    }

    void nextAttackPlanAt(Team team, double time) {
        if (team == Team.BLUE) blueNextAttackPlanAt = time;
        else redNextAttackPlanAt = time;
    }

    boolean exchangeActive() {
        return revengeCandidate != null;
    }

    int living(Team team) {
        var count = 0;
        for (var member : members(team))
            if (member.alive) ++count;
        return count;
    }

    private static Pos average(Musketeer[] members) {
        var total = new Pos();
        for (var member : members) total = total.plus(member.pos);
        return total.div(members.length);
    }
}
