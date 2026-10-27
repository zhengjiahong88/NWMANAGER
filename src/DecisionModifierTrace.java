record DecisionModifierTrace(String name, double value, String source) {
    DecisionModifierTrace {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Modifier name is required.");
        if (source == null || source.isBlank()) throw new IllegalArgumentException("Modifier source is required.");
    }
}
