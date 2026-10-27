enum ScenarioFamily {
    BASELINE,
    JITTERED_BASELINE;

    static ScenarioFamily parse(String value) {
        return switch (value.toLowerCase()) {
            case "baseline" -> BASELINE;
            case "jittered", "jittered-baseline" -> JITTERED_BASELINE;
            default -> throw new IllegalArgumentException("Famille inconnue : " + value);
        };
    }
}
