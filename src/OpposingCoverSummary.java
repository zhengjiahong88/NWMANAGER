import java.util.List;

record OpposingCoverSummary(List<CoverLink> relevantLinks, Musketeer fastestCoverer,
                            double fastestPunishEta, double secondPunishEta,
                            int credibleCoverers, double combinedRisk) {
    OpposingCoverSummary {
        relevantLinks = List.copyOf(relevantLinks);
    }

    static OpposingCoverSummary none() {
        return new OpposingCoverSummary(List.of(), null, Double.POSITIVE_INFINITY,
                Double.POSITIVE_INFINITY, 0, 0);
    }
}
