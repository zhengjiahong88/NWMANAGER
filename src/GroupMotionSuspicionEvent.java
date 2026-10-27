record GroupMotionSuspicionEvent(GroupMotionSuspicion suspicion, double startedAt,
                                 double endedAt, double maximumEvidence) {
    String reportLine() {
        return "t=%.2f–%.2f s | %s | evidence=%.3f".formatted(
                startedAt, endedAt, suspicion, maximumEvidence);
    }
}
