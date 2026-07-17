enum HitZone {
    HEAD("Tête", "頭部", 1.50, 1.80),
    TORSO("Torse", "軀幹", 0.65, 1.50),
    LEG("Jambes", "腿部", 0.00, 0.65);

    static final double BODY_HEIGHT = 1.80;
    private final String frenchLabel, chineseLabel;
    final double minimumHeight, maximumHeight;

    HitZone(String frenchLabel, String chineseLabel, double minimumHeight, double maximumHeight) {
        this.frenchLabel = frenchLabel;
        this.chineseLabel = chineseLabel;
        this.minimumHeight = minimumHeight;
        this.maximumHeight = maximumHeight;
    }

    String label(UiLanguage language) {
        return language.text(frenchLabel, chineseLabel);
    }

    double centerHeight() {
        return (minimumHeight + maximumHeight) / 2;
    }

    static HitZone atHeight(double height) {
        if (height < 0 || height > BODY_HEIGHT) return null;
        if (height >= HEAD.minimumHeight) return HEAD;
        return height >= TORSO.minimumHeight ? TORSO : LEG;
    }
}
