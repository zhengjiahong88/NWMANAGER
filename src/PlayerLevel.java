enum PlayerLevel {
    TOP_PLAYER("Top Player", "頂尖選手"),
    CONTENDER("Contender", "競爭者"),
    MID_PLAYER("Mid Player", "中階選手"),
    LOW_PLAYER("Low Player", "低階選手");

    private final String frenchLabel, chineseLabel;

    PlayerLevel(String frenchLabel, String chineseLabel) {
        this.frenchLabel = frenchLabel;
        this.chineseLabel = chineseLabel;
    }

    String label(UiLanguage language) {
        return language.text(frenchLabel, chineseLabel);
    }

    static PlayerLevel forRating(int rating) {
        if (rating >= 90) return TOP_PLAYER;
        if (rating >= 80) return CONTENDER;
        if (rating >= 55) return MID_PLAYER;
        return LOW_PLAYER;
    }
}
