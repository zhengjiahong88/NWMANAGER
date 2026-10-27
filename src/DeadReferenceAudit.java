import java.util.List;

record DeadReferenceAudit(int activeDeadTargets, int deadAttentionReferences,
                          int deadCoverLinks, int deadClusterMembers,
                          int deadFormationMembers, int deadPlanMembers,
                          List<String> details) {
    DeadReferenceAudit {
        details = List.copyOf(details);
    }

    boolean clean() {
        return activeDeadTargets == 0 && deadAttentionReferences == 0 && deadCoverLinks == 0 &&
                deadClusterMembers == 0 && deadFormationMembers == 0 && deadPlanMembers == 0;
    }

    static DeadReferenceAudit cleanAudit() {
        return new DeadReferenceAudit(0, 0, 0, 0, 0, 0, List.of());
    }
}
