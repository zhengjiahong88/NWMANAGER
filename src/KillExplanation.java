record KillExplanation(double time, Musketeer killer, Musketeer victim,
                       AttackOpportunityReason initiatingReason,
                       CombatState victimStateAtCommit,
                       CombatState victimStateAtImpact,
                       boolean victimFocusedOnOtherEnemy,
                       boolean victimGuardMatched,
                       Musketeer victimAlly, double victimAllyPunishEta,
                       double killerRecoveryEta, String coverFailureReason,
                       String finalCause) {
    String deterministicKey(UiLanguage language) {
        return "%.3f|%s|%s|%s|%s|%s|%s|%s|%s|%.4f|%.4f|%s|%s".formatted(
                time, name(killer, language), name(victim, language), initiatingReason,
                victimStateAtCommit, victimStateAtImpact, victimFocusedOnOtherEnemy,
                victimGuardMatched, name(victimAlly, language), victimAllyPunishEta,
                killerRecoveryEta, coverFailureReason, finalCause);
    }

    private String name(Musketeer musketeer, UiLanguage language) {
        return musketeer == null ? "NONE" : musketeer.name(language);
    }
}
