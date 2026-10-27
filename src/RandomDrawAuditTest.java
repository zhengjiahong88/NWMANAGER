import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.regex.Pattern;

final class RandomDrawAuditTest {
    private static final Pattern RANDOM_CALL = Pattern.compile(
            "\\b(?:random|planRandom)\\.(?:nextDouble|nextGaussian|nextBoolean|nextInt|nextLong|nextFloat)\\b");

    public static void main(String[] args) throws IOException {
        new RandomDrawAuditTest().verifyInventory();
    }

    private void verifyInventory() throws IOException {
        var lines = Files.readAllLines(Path.of("src", "DuelSimulation.java"));
        var catalog = new RandomDrawAuditCatalog();
        var counts = new EnumMap<RandomDrawPurpose, Integer>(RandomDrawPurpose.class);
        var calls = 0;
        for (var n = 0; n < lines.size(); ++n) {
            var matcher = RANDOM_CALL.matcher(lines.get(n));
            var occurrences = 0;
            while (matcher.find()) ++occurrences;
            if (occurrences == 0) continue;
            var purpose = catalog.purposeFor(n + 1);
            require(purpose != null, "Unclassified RNG call at DuelSimulation.java:" + (n + 1) +
                    " -> " + lines.get(n).trim());
            calls += occurrences;
            counts.merge(purpose, occurrences, Integer::sum);
        }
        require(calls == 157, "Expected 157 audited RNG calls, found " + calls);
        require(counts.size() == RandomDrawPurpose.values().length,
                "Unused/invented RandomDrawPurpose detected: used=" + counts.size() +
                        " declared=" + RandomDrawPurpose.values().length);
        var output = Path.of("out", "dead-agent-differential", "6.2b.3", "random-source-audit.txt");
        Files.createDirectories(output.getParent());
        Files.writeString(output, "calls=" + calls + "\nsourceLines=153\npurposes=" +
                counts.size() + "\ncounts=" + counts + '\n');
        System.out.println("RNG source audit: calls=" + calls + " lines=153 purposes=" + counts.size());
        System.out.println("purposeCounts=" + counts);
        System.out.println("RandomDrawAuditTest passed.");
    }

    private void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
