import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

final class DeadAgentInternalSeedSummaryTest {
    public static void main(String[] args) throws IOException {
        new DeadAgentInternalSeedSummaryTest().verifySeeds();
    }

    private void verifySeeds() throws IOException {
        var builder = new StringBuilder();
        for (var seed : new long[]{41, 73, 119}) {
            var analysis = new DeadAgentInternalAnalyzer().analyze(seed);
            var first = analysis.causalWindow().firstContamination();
            require(first != null && !analysis.endOfTickComparison().identical(),
                    "seed " + seed + " did not expose a divergence");
            new DeadAgentDifferentialReportWriter().write(analysis);
            builder.append("seed=").append(seed)
                    .append(" firstTick=").append(first.tick())
                    .append(" firstStage=").append(first.stage())
                    .append(" firstCategory=").append(first.primaryCategory())
                    .append(" firstAffected=").append(firstAffected(analysis))
                    .append(" firstPropagating=").append(analysis.firstPropagatingContamination())
                    .append(" firstCombatEvent=")
                    .append(analysis.endOfTickComparison().firstCombatEventDivergenceTick())
                    .append('\n');
        }
        var output = Path.of("out", "dead-agent-differential", "6.2b.2-seed-summary.txt");
        Files.createDirectories(output.getParent());
        Files.writeString(output, builder.toString(), StandardCharsets.UTF_8);
        System.out.print(builder);
        System.out.println("DeadAgentInternalSeedSummaryTest passed.");
    }

    private java.util.List<String> firstAffected(DeadAgentInternalAnalysis analysis) {
        if (!analysis.firstPropagatingContamination().affectedAgents().isEmpty())
            return analysis.firstPropagatingContamination().affectedAgents();
        FirstDifferentialContamination earliest = null;
        for (var contamination : analysis.propagationTimeline().firstBySystem().values())
            if (!contamination.affectedAgents().isEmpty() &&
                    (earliest == null || contamination.tick() < earliest.tick())) earliest = contamination;
        return earliest == null ? java.util.List.of() : earliest.affectedAgents();
    }

    private void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
