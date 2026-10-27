import java.util.Map;

record DifferentialPropagationTimeline(Map<String, FirstDifferentialContamination> firstBySystem) {
    DifferentialPropagationTimeline {
        firstBySystem = java.util.Collections.unmodifiableMap(new java.util.TreeMap<>(firstBySystem));
    }
}
