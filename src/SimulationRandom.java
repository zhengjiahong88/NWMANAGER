import java.util.Random;

final class SimulationRandom extends Random {
    private long drawCount, stateHash = 0xCBF29CE484222325L;

    SimulationRandom(long seed) {
        super(seed);
    }

    @Override
    protected int next(int bits) {
        var value = super.next(bits);
        ++drawCount;
        stateHash = (stateHash ^ value ^ bits * 0x9E3779B9L) * 0x100000001B3L;
        return value;
    }

    long drawCount() {
        return drawCount;
    }

    long stateHash() {
        return stateHash;
    }
}
