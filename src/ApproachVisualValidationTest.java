import javax.imageio.ImageIO;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;

final class ApproachVisualValidationTest {
    public static void main(String[] args) throws Exception {
        ApproachVisualValidationCapture.main(new String[0]);
        var test = new ApproachVisualValidationTest();
        test.verifyAArtifactsAreComplete();
        test.verifyBActiveApproachImprovesAllReferenceSeeds();
        System.out.println("GROUP_3V3 approach visual-validation milestone-4 tests A-B passed.");
    }

    private void verifyAArtifactsAreComplete() throws Exception {
        var root = Path.of("out", "approach-validation-3v3");
        for (var seed : new long[]{41, 73, 119}) {
            var directory = root.resolve("seed-" + seed);
            for (var name : new String[]{"old-contact-sheet.png", "new-contact-sheet.png",
                    "old-trajectories.png", "new-trajectories.png", "comparison-contact-sheet.png",
                    "comparison-trajectories.png", "old-approach.txt", "new-approach.txt",
                    "old-first-contact.txt", "new-first-contact.txt", "old-flat-front-audit.txt",
                    "new-flat-front-audit.txt", "approach.txt", "first-contact.txt",
                    "flat-front-audit.txt"})
                require(Files.size(directory.resolve(name)) > 0,
                        "A: missing or empty visual artifact " + directory.resolve(name));
            var contact = ImageIO.read(directory.resolve("comparison-contact-sheet.png").toFile());
            var trajectories = ImageIO.read(directory.resolve("comparison-trajectories.png").toFile());
            require(contact.getWidth() == 1_280 && contact.getHeight() == 928 &&
                            trajectories.getWidth() == 1_440 && trajectories.getHeight() == 644,
                    "A: comparison sheets must keep their inspectable full resolution.");
        }
    }

    private void verifyBActiveApproachImprovesAllReferenceSeeds() throws Exception {
        var lines = Files.readAllLines(Path.of("out", "approach-validation-3v3", "visual-summary.txt"));
        var rows = new HashMap<String, VisualRow>();
        for (var n = 1; n < lines.size(); ++n) {
            var values = lines.get(n).split(";");
            rows.put(values[0] + ":" + values[1], new VisualRow(Double.parseDouble(values[2]),
                    Double.parseDouble(values[4]), Double.parseDouble(values[5]),
                    Double.parseDouble(values[6]), Boolean.parseBoolean(values[7])));
        }
        for (var seed : new long[]{41, 73, 119}) {
            var baseline = rows.get(seed + ":baseline");
            var active = rows.get(seed + ":active");
            require(active.firstContact < baseline.firstContact &&
                            active.firstContactSpread > baseline.firstContactSpread &&
                            active.lateralDistance > baseline.lateralDistance * 4 &&
                            active.averageDepth > baseline.averageDepth * 8 &&
                            baseline.flat && !active.flat,
                    "B: seed " + seed + " must become faster, lateral, deep and non-flat.");
        }
    }

    private void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private record VisualRow(double firstContact, double firstContactSpread,
                             double lateralDistance, double averageDepth, boolean flat) {
    }
}
