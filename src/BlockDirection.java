import java.awt.*;

enum BlockDirection {
    UP("Haute", "上段", "↑", new Color(232, 137, 37)),
    DOWN("Basse", "下段", "↓", new Color(31, 158, 181)),
    SIDE("Latérale", "側面", "↔", new Color(145, 91, 181));

    private final String frenchLabel, chineseLabel;
    final String symbol;
    final Color color;

    BlockDirection(String frenchLabel, String chineseLabel, String symbol, Color color) {
        this.frenchLabel = frenchLabel;
        this.chineseLabel = chineseLabel;
        this.symbol = symbol;
        this.color = color;
    }

    boolean blocks(CombatDirection attack) {
        return this == UP && attack == CombatDirection.UP || this == DOWN && attack == CombatDirection.DOWN;
    }

    static BlockDirection matching(CombatDirection attack) {
        return attack == CombatDirection.UP ? UP : DOWN;
    }

    String label(UiLanguage language) {
        return language.text(frenchLabel, chineseLabel);
    }
}
