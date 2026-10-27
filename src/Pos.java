record Pos(double x, double y) {
    Pos(double angle) {
        this(Math.cos(angle), Math.sin(angle));
    }
    Pos() {
        this(0, 0);
    }
    Pos plus(double n) {
        return new Pos(x + n, y + n);
    }
    Pos plus(double a, double b) {
        return new Pos(x + a, y + b);
    }
    Pos plus(Pos p) {
        return new Pos(x + p.x, y + p.y);
    }
    Pos minus(double n) {
        return new Pos(x - n, y - n);
    }
    Pos minus(double a, double b) {
        return new Pos(x - a, y - b);
    }
    Pos minus(Pos p) {
        return new Pos(x - p.x, y - p.y);
    }
    Pos times(double n) {
        return new Pos(x * n, y * n);
    }
    Pos times(double a, double b) {
        return new Pos(x * a, y * b);
    }
    Pos times(Pos p) {
        return new Pos(x * p.x, y * p.y);
    }
    Pos div(double n) {
        return new Pos(x / n, y / n);
    }
    Pos div(double a, double b) {
        return new Pos(x / a, y / b);
    }
    Pos div(Pos p) {
        return new Pos(x / p.x, y / p.y);
    }
    double sum() {
        return x + y;
    }
    double dot(Pos p) {
        return x * p.x + y * p.y;
    }
    double hypotenuse() {
        return Math.hypot(x, y);
    }
    double radians() {
        return Math.atan2(y, x);
    }
}
