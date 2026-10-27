enum LocalRole {
    FREE("Libre", "自由"),
    FIXER("Fixateur", "牽制"),
    FLANKER("Débordeur", "側翼"),
    COVER("Couverture", "掩護");

    private final String frenchLabel, chineseLabel;

    LocalRole(String frenchLabel, String chineseLabel) {
        this.frenchLabel = frenchLabel;
        this.chineseLabel = chineseLabel;
    }

    String label(UiLanguage language) {
        return language.text(frenchLabel, chineseLabel);
    }
}
