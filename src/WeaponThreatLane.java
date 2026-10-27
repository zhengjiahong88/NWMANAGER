record WeaponThreatLane(Musketeer attacker, Pos origin, Pos direction,
                        double projectedLength, double halfWidth,
                        double timeToActive, double activeRemaining,
                        double confidence, boolean currentlyDangerous) {
    Pos end() {
        return origin.plus(direction.times(projectedLength));
    }
}
