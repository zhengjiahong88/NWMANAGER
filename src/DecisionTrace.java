record DecisionTrace(double time, Musketeer agent, DecisionCategory category,
                     String action, String primaryReason, Musketeer target,
                     Pos destination, double score, double availableWindow,
                     double impactEta, double revengeRisk,
                     String rejectedAlternative) {
    String deterministicKey(UiLanguage language) {
        return "%.3f|%s|%s|%s|%s|%s|%s|%.4f|%.4f|%.4f|%.4f|%s".formatted(
                time, name(agent, language), category, action, primaryReason,
                name(target, language), destination, score, availableWindow,
                impactEta, revengeRisk, rejectedAlternative);
    }

    private String name(Musketeer musketeer, UiLanguage language) {
        return musketeer == null ? "NONE" : musketeer.name(language);
    }
}
