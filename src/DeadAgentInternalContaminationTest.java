final class DeadAgentInternalContaminationTest {
    public static void main(String[] args) {
        new DeadAgentInternalContaminationTest().verifyFirstContamination();
    }

    private void verifyFirstContamination() {
        var firstRun = new DeadAgentInternalAnalyzer().analyze(41);
        var secondRun = new DeadAgentInternalAnalyzer().analyze(41);
        var first = firstRun.causalWindow().firstContamination();
        var repeated = secondRun.causalWindow().firstContamination();
        require(!firstRun.endOfTickComparison().identical(), "A and D1 must diverge");
        require(first != null, "first contamination was not identified");
        require(first.tick() <= 204, "first contamination tick=" + first.tick());
        require(first.equals(repeated), "first contamination is not deterministic");
        require(first.tick() == 61, "reproducible first contamination moved from tick 61 to " + first.tick());
        System.out.println("Exploratory first contamination=" + first);
        require(first.stage() == DifferentialTickStage.AFTER_COVER_NETWORK_UPDATE,
                "first stage=" + first.stage());
        require(first.primaryCategory().equals("COVER_PARTICIPANTS"),
                "first category=" + first.primaryCategory());
        require(firstRun.firstPropagatingTick() == 62,
                "first propagating tick=" + firstRun.firstPropagatingTick());
        require(firstRun.firstPropagatingContamination().primaryCategory().equals("COVER_LINK_QUALITY"),
                "first propagating category=" +
                        firstRun.firstPropagatingContamination().primaryCategory());
        new DeadAgentDifferentialReportWriter().write(firstRun);
        System.out.println("First contamination: tick=" + first.tick() + " stage=" + first.stage() +
                " category=" + first.primaryCategory() + " field=" + first.primaryField() +
                " affected=" + first.affectedAgents());
        System.out.println("First propagating divergence: tick=" + firstRun.firstPropagatingTick() +
                " data=" + firstRun.firstPropagatingContamination());
        System.out.println("Propagation timeline=" + firstRun.propagationTimeline().firstBySystem());
        System.out.println("Checkpoint at earliest end-result divergence=" +
                firstRun.firstSurvivorWindow().firstContamination());
        System.out.println("DeadAgentInternalContaminationTest passed.");
    }

    private void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
