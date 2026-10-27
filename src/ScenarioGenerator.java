import java.util.Random;

final class ScenarioGenerator {
    private static final double
            POSITION_JITTER = 0.35,
            BODY_ANGLE_JITTER = Math.toRadians(12),
            WEAPON_YAW_JITTER = Math.toRadians(6),
            INITIAL_BODY_SEPARATION = Musketeer.BODY_RADIUS * 2 + 0.08,
            FLANK_SLOT_DISTANCE = 1.34;
    private static final int MAX_ATTEMPTS = 32;
    private static final long JITTERED_SEED_MASK = 0x6A09E667F3BCC909L;

    ScenarioSetup create(ScenarioFamily family, long seed) {
        return switch (family) {
            case BASELINE -> createBaseline(seed);
            case JITTERED_BASELINE -> createJitteredBaseline(seed);
        };
    }

    ScenarioSetup createBaseline(long seed) {
        return new ScenarioSetup(ScenarioFamily.BASELINE, seed,
                new AgentSpawn(new Pos(2.55, DuelSimulation.ARENA_HEIGHT / 2), 0, 0),
                new AgentSpawn(new Pos(3.15, DuelSimulation.ARENA_HEIGHT / 2 - 1.55), 0, 0),
                new AgentSpawn(new Pos(5.45, DuelSimulation.ARENA_HEIGHT / 2), Math.PI, 0),
                new AgentSpawn(new Pos(5.25, DuelSimulation.ARENA_HEIGHT / 2 - 2.15), Math.PI, 0),
                0, false);
    }

    private ScenarioSetup createJitteredBaseline(long seed) {
        var random = new Random(seed ^ JITTERED_SEED_MASK);
        var baseline = createBaseline(seed);
        for (var attempt = 0; attempt < MAX_ATTEMPTS; ++attempt) {
            var scale = attempt < 16 ? 1.0 : attempt < 24 ? 0.75 : 0.50;
            var candidate = new ScenarioSetup(ScenarioFamily.JITTERED_BASELINE, seed,
                    jitter(baseline.blueOne(), random, scale),
                    jitter(baseline.blueTwo(), random, scale),
                    jitter(baseline.redOne(), random, scale),
                    jitter(baseline.redTwo(), random, scale),
                    attempt, false);
            if (isLegal(candidate)) return candidate;
        }
        return new ScenarioSetup(ScenarioFamily.JITTERED_BASELINE, seed,
                baseline.blueOne(), baseline.blueTwo(), baseline.redOne(), baseline.redTwo(),
                MAX_ATTEMPTS, true);
    }

    private AgentSpawn jitter(AgentSpawn spawn, Random random, double scale) {
        var position = spawn.position().plus(symmetric(random, POSITION_JITTER * scale),
                symmetric(random, POSITION_JITTER * scale));
        return new AgentSpawn(position,
                normalizeAngle(spawn.bodyAngle() + symmetric(random, BODY_ANGLE_JITTER * scale)),
                Math.clamp(spawn.weaponYaw() + symmetric(random, WEAPON_YAW_JITTER * scale),
                        -Musketeer.MAX_WEAPON_YAW, Musketeer.MAX_WEAPON_YAW));
    }

    private double symmetric(Random random, double amplitude) {
        return (random.nextDouble() * 2 - 1) * amplitude;
    }

    boolean isLegal(ScenarioSetup setup) {
        var spawns = setup.spawns();
        for (var spawn : spawns)
            if (!insideArena(spawn.position()) || !finite(spawn)) return false;
        for (var first = 0; first < spawns.length; ++first)
            for (var second = first + 1; second < spawns.length; ++second)
                if (spawns[first].position().minus(spawns[second].position()).hypotenuse() <
                        INITIAL_BODY_SEPARATION) return false;
        if (offensiveContact(setup.blueOne(), setup.redOne()) ||
                offensiveContact(setup.blueOne(), setup.redTwo()) ||
                offensiveContact(setup.blueTwo(), setup.redOne()) ||
                offensiveContact(setup.blueTwo(), setup.redTwo())) return false;
        var blueAverage = (setup.blueOne().position().x() + setup.blueTwo().position().x()) / 2;
        var redAverage = (setup.redOne().position().x() + setup.redTwo().position().x()) / 2;
        if (redAverage - blueAverage < 1.20) return false;
        var flankSlot = setup.redOne().position().plus(
                new Pos(setup.redOne().bodyAngle() + Math.PI / 2).times(FLANK_SLOT_DISTANCE));
        if (setup.blueTwo().position().minus(flankSlot).hypotenuse() < 0.55) return false;
        var encounter = setup.redOne().position().minus(setup.blueTwo().position());
        if (setup.redTwo().position().minus(setup.blueTwo().position()).dot(encounter) <= 0) return false;
        var path = flankSlot.minus(setup.blueTwo().position());
        if (distanceToSegment(setup.redTwo().position(), setup.blueTwo().position(), flankSlot) > 3.20)
            return false;
        for (var fraction : new double[]{0.25, 0.50, 0.75})
            if (setup.redTwo().position().minus(
                    setup.blueTwo().position().plus(path.times(fraction))).hypotenuse() < 0.18) return false;
        return true;
    }

    private boolean insideArena(Pos position) {
        return position.x() >= Musketeer.BODY_RADIUS &&
                position.x() <= DuelSimulation.ARENA_WIDTH - Musketeer.BODY_RADIUS &&
                position.y() >= Musketeer.BODY_RADIUS &&
                position.y() <= DuelSimulation.ARENA_HEIGHT - Musketeer.BODY_RADIUS;
    }

    private boolean finite(AgentSpawn spawn) {
        return Double.isFinite(spawn.position().x()) && Double.isFinite(spawn.position().y()) &&
                Double.isFinite(spawn.bodyAngle()) && Double.isFinite(spawn.weaponYaw());
    }

    private boolean offensiveContact(AgentSpawn attacker, AgentSpawn defender) {
        var weaponAngle = attacker.bodyAngle() + attacker.weaponYaw();
        var tip = attacker.position().plus(new Pos(weaponAngle).times(Musketeer.WEAPON_REST));
        return tip.minus(defender.position()).hypotenuse() <= Musketeer.BODY_RADIUS;
    }

    private double distanceToSegment(Pos point, Pos start, Pos end) {
        var segment = end.minus(start);
        var lengthSquared = segment.dot(segment);
        if (lengthSquared < 0.000001) return point.minus(start).hypotenuse();
        var fraction = Math.clamp(point.minus(start).dot(segment) / lengthSquared, 0, 1);
        return point.minus(start.plus(segment.times(fraction))).hypotenuse();
    }

    private double normalizeAngle(double angle) {
        return Math.atan2(Math.sin(angle), Math.cos(angle));
    }
}
