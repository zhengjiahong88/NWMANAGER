import java.awt.*;

enum CombatDirection {
    UP("Haute", "上段", "↑", new Color(232, 137, 37), 1.52, 0.36, 0.15, 0.48),
    DOWN("Basse", "下段", "↓", new Color(31, 158, 181), 1.30, 0.22, 0.09, 0.34);

    private final String frenchLabel, chineseLabel;
    final String symbol;
    final Color color;
    final double thrustLength, prepareTime, activeTime, recoveryTime;

    CombatDirection(String frenchLabel, String chineseLabel, String symbol, Color color, double thrustLength,
                    double prepareTime, double activeTime, double recoveryTime) {
        this.frenchLabel = frenchLabel;
        this.chineseLabel = chineseLabel;
        this.symbol = symbol;
        this.color = color;
        this.thrustLength = thrustLength;
        this.prepareTime = prepareTime;
        this.activeTime = activeTime;
        this.recoveryTime = recoveryTime;
    }

    CombatDirection opposite() {
        return this == UP ? DOWN : UP;
    }

    String label(UiLanguage language) {
        return language.text(frenchLabel, chineseLabel);
    }
}
