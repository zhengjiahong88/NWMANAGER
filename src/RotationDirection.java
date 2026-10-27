enum RotationDirection {
    CLOCKWISE(-1),
    COUNTER_CLOCKWISE(1);

    final int sign;

    RotationDirection(int sign) {
        this.sign = sign;
    }

    RotationDirection opposite() {
        return this == CLOCKWISE ? COUNTER_CLOCKWISE : CLOCKWISE;
    }
}
