record TransitionTrace(long transitionId, long tick, double simulationTime,
                       SimulationScenario scenario, String controllerBefore,
                       String controllerAfter, String phaseBefore, String requestedPhase,
                       String phaseAfter, int blueMaskBefore, int redMaskBefore,
                       int blueMaskAfter, int redMaskAfter, int blueAlive, int redAlive,
                       boolean applied, String reason) {
}
