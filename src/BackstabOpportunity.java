record BackstabOpportunity(
        boolean rearSector,
        boolean stable,
        boolean inRange,
        boolean weaponAligned,
        boolean fixerMaintainsPressure,
        boolean interceptorSafe,
        boolean attackLineClear,
        boolean hasRecoverySpace,
        double rearAngle,
        double distance,
        double estimatedWindowTime
) {
    static BackstabOpportunity unavailable() {
        return new BackstabOpportunity(false, false, false, false, false,
                true, false, false, 0, Double.POSITIVE_INFINITY, 0);
    }

    boolean valid() {
        return rearSector && stable && inRange && weaponAligned && fixerMaintainsPressure &&
                interceptorSafe && attackLineClear && hasRecoverySpace;
    }
}
