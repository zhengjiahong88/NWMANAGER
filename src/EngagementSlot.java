enum EngagementSlot {
    FRONT(0),
    LEFT(-Math.PI / 2),
    RIGHT(Math.PI / 2),
    REAR_LEFT(-Math.PI * 3 / 4),
    REAR_RIGHT(Math.PI * 3 / 4);

    private final double angleOffset;

    EngagementSlot(double angleOffset) {
        this.angleOffset = angleOffset;
    }

    Pos destinationAround(Musketeer target, double preferredDistance) {
        return target.pos.plus(new Pos(target.angle + angleOffset).times(preferredDistance));
    }
}
