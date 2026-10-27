import java.util.List;

record TeamAttackPlan(Team team, List<Musketeer> selectedAttackers,
                      List<AttackOpportunity> opportunities,
                      List<Musketeer> coveringMembers,
                      double combinedLineSafety, double combinedRecoveryRisk,
                      String reason, double createdAt) {
    TeamAttackPlan {
        selectedAttackers = List.copyOf(selectedAttackers);
        opportunities = List.copyOf(opportunities);
        coveringMembers = List.copyOf(coveringMembers);
    }

    static TeamAttackPlan hold(Team team, List<Musketeer> coveringMembers,
                               String reason, double createdAt) {
        return new TeamAttackPlan(team, List.of(), List.of(), coveringMembers,
                1, 0, reason, createdAt);
    }
}
