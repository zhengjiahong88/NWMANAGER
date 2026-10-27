public final class FinalAcceptanceTest {
    public static void main(String[] args) {
        ModernGeometryTest.main(new String[0]);
        GroupMotionAuditTest.main(new String[0]);
        AttackOpportunityTest.main(new String[0]);
        DecisionExplanationTest.main(new String[0]);
        try {
            AttributeBehaviorAudit.main(new String[0]);
        } catch (java.io.IOException exception) {
            throw new IllegalStateException(exception);
        }
        ModernGroup2v2Test.main(new String[0]);
        TwoVsOneCoordinationTest.main(new String[0]);
        Modern2v2Test.main(new String[0]);
        SimulationSmokeTest.main(new String[0]);
        Modern2v2BatchTest.main(new String[]{args.length == 0 ? "100" : args[0]});
        System.out.println("Final acceptance criteria 1-28 passed.");
    }
}
