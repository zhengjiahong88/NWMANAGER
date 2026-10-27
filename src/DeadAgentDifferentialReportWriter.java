import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

final class DeadAgentDifferentialReportWriter {
    void write(DeadAgentInternalAnalysis analysis) {
        var directory = Path.of("out", "dead-agent-differential", "seed-" + analysis.seed(), "6.2b.2");
        try {
            Files.createDirectories(directory);
            write(directory.resolve("first-contamination.txt"), firstContamination(analysis));
            write(directory.resolve("tick-window.txt"), tickWindow(analysis.causalWindow()));
            write(directory.resolve("formation-differences.txt"), categories(analysis, "FORMATION"));
            write(directory.resolve("cover-differences.txt"), categories(analysis, "COVER"));
            write(directory.resolve("attention-differences.txt"), categories(analysis,
                    "ATTENTION", "AWARENESS", "RNG"));
            write(directory.resolve("target-differences.txt"), categories(analysis, "TARGET"));
            write(directory.resolve("steering-differences.txt"), categories(analysis, "STEERING"));
            write(directory.resolve("combat-event-differences.txt"), combatEvents(analysis));
            write(directory.resolve("A-stage-snapshots.txt"), snapshots(analysis.current().stageSnapshots));
            write(directory.resolve("D1-stage-snapshots.txt"), snapshots(analysis.neutralized().stageSnapshots));
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot write dead-Agent differential reports.", exception);
        }
    }

    private String firstContamination(DeadAgentInternalAnalysis analysis) {
        var first = analysis.causalWindow().firstContamination();
        var end = analysis.endOfTickComparison();
        var builder = new StringBuilder();
        builder.append("CONTROLLED DEATH\n")
                .append("tick=").append(analysis.current().deathTick).append('\n')
                .append("time=").append(format(analysis.current().deathTime)).append('\n')
                .append("agent=B2\nposition=").append(analysis.current().deadAgentState.deathPosition())
                .append("\n\nFIRST INTERNAL CONTAMINATION\n")
                .append("tick=").append(first.tick()).append('\n')
                .append("time=").append(format(first.simulationTime())).append('\n')
                .append("stage=").append(first.stage()).append('\n')
                .append("category=").append(first.primaryCategory()).append('\n')
                .append("field=").append(first.primaryField()).append('\n')
                .append("affectedAgents=").append(first.affectedAgents()).append("\n\nA_CURRENT:\n")
                .append(first.leftValue()).append("\n\nD1_NEUTRALIZED:\n")
                .append(first.rightValue()).append("\n\nSIMULTANEOUS DIFFERENCES:\n");
        for (var difference : first.simultaneousDifferences()) builder.append(difference).append('\n');
        builder.append("\nPROPAGATION\n")
                .append("first propagating divergence=")
                .append(analysis.firstPropagatingContamination()).append('\n')
                .append("checkpoint propagation=")
                .append(analysis.propagationTimeline().firstBySystem()).append('\n')
                .append("checkpoint at earliest end-result divergence=")
                .append(analysis.firstSurvivorWindow().firstContamination()).append('\n')
                .append("first velocity divergence=").append(value(end.firstVelocityDivergenceTick())).append('\n')
                .append("first position divergence=").append(value(end.firstPositionDivergenceTick())).append('\n')
                .append("first attention divergence=").append(value(end.firstAttentionDivergenceTick())).append('\n')
                .append("first target divergence=").append(value(end.firstTargetDivergenceTick())).append('\n')
                .append("first attack opportunity divergence=")
                .append(value(end.firstAttackOpportunityDivergenceTick())).append('\n')
                .append("first combat event divergence=")
                .append(value(end.firstCombatEventDivergenceTick())).append('\n')
                .append("first transition divergence=").append(value(end.firstTransitionDivergenceTick())).append('\n');
        return builder.toString();
    }

    private String tickWindow(DeadAgentInternalComparison comparison) {
        var builder = new StringBuilder();
        for (var stage : comparison.stageComparisons()) {
            builder.append("tick=").append(stage.tick()).append(" sequence=").append(stage.sequence())
                    .append(" stage=").append(stage.stage()).append(' ')
                    .append(stage.identical() ? "IDENTICAL" : "DIFFERENT").append('\n');
            if (!stage.identical()) {
                for (var difference : stage.collectiveDifferences()) builder.append("  ").append(difference).append('\n');
                for (var agent : stage.agentDifferences()) builder.append("  ").append(agent).append('\n');
            }
        }
        return builder.toString();
    }

    private String categories(DeadAgentInternalAnalysis analysis, String... prefixes) {
        var builder = new StringBuilder();
        appendCategories(builder, analysis.causalWindow().stageComparisons(), prefixes);
        appendCategories(builder, analysis.propagatingWindow().stageComparisons(), prefixes);
        appendCategories(builder, analysis.firstSurvivorWindow().stageComparisons(), prefixes);
        for (var entry : analysis.propagationTimeline().firstBySystem().entrySet())
            if (matches(entry.getValue().primaryCategory(), prefixes))
                builder.append("FIRST ").append(entry.getKey()).append(' ')
                        .append(entry.getValue()).append('\n');
        return builder.isEmpty() ? "NONE_OBSERVED\n" : builder.toString();
    }

    private void appendCategories(StringBuilder builder, List<DifferentialStageComparison> comparisons,
                                  String... prefixes) {
        for (var stage : comparisons) {
            for (var difference : stage.collectiveDifferences())
                if (matches(difference.category(), prefixes))
                    builder.append(stage.tick()).append('/').append(stage.stage()).append(' ')
                            .append(difference).append('\n');
            for (var agent : stage.agentDifferences())
                for (var difference : agent.differences())
                    if (matches(difference.category(), prefixes))
                        builder.append(stage.tick()).append('/').append(stage.stage()).append(" agent=")
                                .append(agent.agentId()).append(' ').append(difference).append('\n');
        }
    }

    private boolean matches(String category, String... prefixes) {
        for (var prefix : prefixes)
            if (category.startsWith(prefix)) return true;
        return false;
    }

    private String combatEvents(DeadAgentInternalAnalysis analysis) {
        return "firstCombatEventDivergenceTick=" +
                value(analysis.endOfTickComparison().firstCombatEventDivergenceTick()) +
                "\n\nA_CURRENT\n" + analysis.current().combatEvents +
                "\n\nD1_NEUTRALIZED\n" + analysis.neutralized().combatEvents + '\n';
    }

    private String snapshots(List<DifferentialStageSnapshot> snapshots) {
        var builder = new StringBuilder();
        for (var snapshot : snapshots) builder.append(snapshot).append("\n\n");
        return builder.toString();
    }

    private String value(Long tick) {
        return tick == null ? "NONE_OBSERVED" : tick.toString();
    }

    private String format(double value) {
        return "%.9f".formatted(value);
    }

    private void write(Path path, String content) throws IOException {
        Files.writeString(path, content, StandardCharsets.UTF_8);
    }
}
