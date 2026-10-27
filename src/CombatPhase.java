enum CombatPhase {
    FRONT_LINE("Front line", "正面列陣"),
    ROTATION("Rotation", "團隊繞行"),
    RESOLUTION("Résolution", "破局決勝");

    private final String frenchLabel, chineseLabel;

    CombatPhase(String frenchLabel, String chineseLabel) {
        this.frenchLabel = frenchLabel;
        this.chineseLabel = chineseLabel;
    }

    String label(UiLanguage language) {
        return language.text(frenchLabel, chineseLabel);
    }
}
