final class DeadAgentDifferentialVisualIsolationTest {
    public static void main(String[] args) {
        new DeadAgentDifferentialVisualIsolationTest().run();
    }

    private void run() {
        var runner = new DeadAgentDifferentialRunner();
        var comparator = new DeadAgentDifferentialComparator();
        var current = runner.runDeadAgentScenario(41, DeadAgentExperimentMode.CURRENT_BUGGED_BEHAVIOR);
        var hidden = runner.runDeadAgentScenario(41, DeadAgentExperimentMode.HIDDEN_RENDER_ONLY);
        var offset = runner.runDeadAgentScenario(41, DeadAgentExperimentMode.VISUAL_OFFSET_ONLY);
        var currentHidden = comparator.compare(current, hidden);
        var currentOffset = comparator.compare(current, offset);
        System.out.println("A/B identical=" + currentHidden.identical());
        System.out.println("A/C identical=" + currentOffset.identical());
        System.out.println("A events=" + current.combatEvents.size() +
                " B events=" + hidden.combatEvents.size() + " C events=" + offset.combatEvents.size());
        System.out.println("A winner=" + current.winner + " B winner=" + hidden.winner +
                " C winner=" + offset.winner);
        System.out.println("A end=" + current.matchEndTime + " B end=" + hidden.matchEndTime +
                " C end=" + offset.matchEndTime);
        require(current.deathTick == hidden.deathTick && current.deathTick == offset.deathTick &&
                        current.deathTime == hidden.deathTime && current.deathTime == offset.deathTime,
                "The controlled death must occur at the same tick and time.");
        require(currentHidden.identical(), "A_CURRENT and B_HIDDEN_RENDER diverged: " +
                currentHidden.details());
        require(currentOffset.identical(), "A_CURRENT and C_VISUAL_OFFSET diverged: " +
                currentOffset.details());
        require(current.combatEvents.equals(hidden.combatEvents) &&
                        current.combatEvents.equals(offset.combatEvents),
                "Rendering variants must preserve combat events exactly.");
        require(current.winner == hidden.winner && current.winner == offset.winner &&
                        current.matchEndTime == hidden.matchEndTime &&
                        current.matchEndTime == offset.matchEndTime,
                "Rendering variants must preserve winner and completion time.");
        System.out.println("DeadAgentDifferentialVisualIsolationTest passed.");
    }

    private void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
