enum EngagementPhase {
    PROBE("Observation", "試探"), COMMIT("Engagement", "進入交戰"),
    PASS_THROUGH("Traversée", "穿越交鋒"), DISENGAGE("Sortie", "脫離交鋒"), RESET("Reconstruction", "重整距離");

    private final String frenchLabel, chineseLabel;

    EngagementPhase(String frenchLabel, String chineseLabel) {
        this.frenchLabel = frenchLabel;
        this.chineseLabel = chineseLabel;
    }

    String label(UiLanguage language) {
        return language.text(frenchLabel, chineseLabel);
    }
}
