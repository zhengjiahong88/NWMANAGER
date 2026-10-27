record SimulationFeatures(
        boolean combatPhases,
        boolean engagementCycle,
        boolean pressure,
        boolean dynamicLocalCoordination,
        boolean spatialDezone,
        boolean targetSwitchDezone,
        boolean bait,
        boolean freeKill,
        boolean perfectChamber,
        boolean doubleBlock,
        boolean fiftyFifty,
        boolean attackManipulation,
        boolean synchronizedAttacks
) {
    static SimulationFeatures legacy2v2() {
        return new SimulationFeatures(true, true, true, true, true, true, true,
                true, true, true, true, true, true);
    }

    static SimulationFeatures modern2v2() {
        return new SimulationFeatures(false, false, false, false, false, false, false,
                false, false, false, false, false, false);
    }

    static SimulationFeatures controlled2v1() {
        return new SimulationFeatures(false, false, false, false, false, false, false,
                false, false, false, false, false, false);
    }

    static SimulationFeatures controlled2v2() {
        return controlled2v1();
    }

    static SimulationFeatures modern3v3() {
        return controlled2v1();
    }

    static SimulationFeatures forScenario(SimulationScenario scenario) {
        return switch (scenario) {
            case MODERN_2V2 -> modern2v2();
            case MODERN_3V3, CONTROLLED_3V3 -> modern3v3();
            case LEGACY_2V2 -> legacy2v2();
            case CONTROLLED_2V1 -> controlled2v1();
            case CONTROLLED_2V2 -> controlled2v2();
        };
    }
}
