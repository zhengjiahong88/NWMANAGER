final class DeadAgentCheckpointNonIntrusionTest {
    public static void main(String[] args) {
        new DeadAgentCheckpointNonIntrusionTest().verifyCaptureDoesNotAffectSimulation();
    }

    private void verifyCaptureDoesNotAffectSimulation() {
        var runner = new DeadAgentDifferentialRunner();
        var withoutCapture = runner.runDeadAgentScenario(41,
                DeadAgentExperimentMode.CURRENT_BUGGED_BEHAVIOR);
        var withCapture = runner.runDeadAgentScenario(41,
                DeadAgentExperimentMode.CURRENT_BUGGED_BEHAVIOR, 198, 206);
        var comparison = new DeadAgentDifferentialComparator().compare(withoutCapture, withCapture);
        require(comparison.identical(), "capture changed the simulation: " + comparison.details());
        require(!withCapture.stageSnapshots.isEmpty(), "capture produced no internal snapshots");
        require(withoutCapture.randomDrawCount == withCapture.randomDrawCount &&
                        withoutCapture.randomStateHash == withCapture.randomStateHash,
                "capture changed the RNG stream");
        require(withoutCapture.combatEvents.equals(withCapture.combatEvents),
                "capture changed combat events");
        System.out.println("Checkpoint capture non-intrusion: snapshots=" +
                withCapture.stageSnapshots.size() + " RNG=" + withCapture.randomDrawCount +
                " final states identical=true");
        System.out.println("DeadAgentCheckpointNonIntrusionTest passed.");
    }

    private void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
