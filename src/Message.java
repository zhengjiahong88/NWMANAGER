import java.awt.*;

class Message {
    final Color color;
    private double time;
    int times;
    Message(Color color) {
        this.color = color;
    }
    void paint(Graphics2D g, String message, Pos center, double scale) {
        if (time <= 0) return;
        g.setFont(g.getFont().deriveFont(Font.BOLD, 16f));
        g.setColor(color);
        g.drawString(message, (float) (center.x() - g.getFontMetrics().stringWidth(message) / 2.0), (float) (center.y() - Musketeer.BODY_RADIUS * scale - 30));
    }
    void setTime(double time) {
        this.time = time;
        ++times;
    }
    void setTime() {
        time = 0;
    }
    double getTime() {
        return time;
    }
    boolean isActive() {
        return time > 0;
    }
    boolean update(double dt) {
        if (time > 0) {
            time -= dt;
            return true;
        }
        return false;
    }
}
