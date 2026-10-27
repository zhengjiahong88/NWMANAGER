final class Experimental3v3AttentionTest {
    private static final double DT = 1.0 / 120.0;

    public static void main(String[] args) {
        var test = new Experimental3v3AttentionTest();
        test.verifyAThreeNormalizedAttentionLevels();
        test.verifyBObservationCadenceAndConfidence();
        test.verifyCDelayedPrimaryShift();
        test.verifyDPeripheralStateIsNotReadExactly();
        test.verifyEDeterminism();
        System.out.println("Experimental GROUP_3V3 attention tests A-E passed.");
    }

    private void verifyAThreeNormalizedAttentionLevels() {
        var simulation = fixture(3_201L);
        for (var observer : simulation.agents) {
            var attention = observer.attentionAllocation;
            require(attention.primaryThreat != null && attention.secondaryThreat != null &&
                            attention.peripheralThreat != null &&
                            attention.primaryThreat != attention.secondaryThreat &&
                            attention.primaryThreat != attention.peripheralThreat &&
                            attention.secondaryThreat != attention.peripheralThreat,
                    "A: every Agent must allocate three distinct opponent levels.");
            require(Math.abs(attention.primaryWeight + attention.secondaryWeight +
                            attention.peripheralWeight - 1) < 0.000_000_1,
                    "A: attention weights must remain normalized.");
            require(attention.primaryWeight > attention.secondaryWeight &&
                            attention.secondaryWeight > attention.peripheralWeight,
                    "A: primary, secondary and peripheral weights must be ordered.");
        }
    }

    private void verifyBObservationCadenceAndConfidence() {
        var simulation = fixture(3_202L);
        simulation.forceGroup3Maneuver(Team.BLUE, TeamManeuverType.HOLD_LINE);
        simulation.forceGroup3Maneuver(Team.RED, TeamManeuverType.HOLD_LINE);
        var observer = simulation.blueTeam[0];
        observer.attentionAllocation.lastShiftTime = 1_000;
        for (var tick = 0; tick < 480; ++tick) simulation.update(DT);
        var primaryAttempts = 0;
        var secondaryAttempts = 0;
        var peripheralAttempts = 0;
        for (var awareness : observer.opponentAwareness.values()) {
            primaryAttempts += awareness.primaryObservationAttempts;
            secondaryAttempts += awareness.secondaryObservationAttempts;
            peripheralAttempts += awareness.peripheralObservationAttempts;
        }
        var attention = observer.attentionAllocation;
        var primary = observer.opponentAwareness.get(attention.primaryThreat);
        var secondary = observer.opponentAwareness.get(attention.secondaryThreat);
        var peripheral = observer.opponentAwareness.get(attention.peripheralThreat);
        require(primaryAttempts > secondaryAttempts && secondaryAttempts > peripheralAttempts,
                "B: observation cadence must decrease with attention level (" +
                        primaryAttempts + "/" + secondaryAttempts + "/" + peripheralAttempts + ").");
        require(primary.detailConfidence > secondary.detailConfidence &&
                        secondary.detailConfidence > peripheral.detailConfidence,
                "B: detail confidence must decrease from primary to peripheral.");
    }

    private void verifyCDelayedPrimaryShift() {
        var simulation = fixture(3_203L);
        var observer = simulation.blueTeam[1];
        var attention = observer.attentionAllocation;
        var former = attention.primaryThreat;
        var challenger = attention.peripheralThreat;
        observer.pos = new Pos(2.0, 3.5);
        former.pos = new Pos(5.4, 3.5);
        challenger.pos = new Pos(3.42, 3.5);
        attention.lastShiftTime = simulation.simulationTime - 2;
        simulation.update(DT);
        require(attention.primaryThreat == former && attention.pendingPrimary == challenger,
                "C: a salient challenger must first become pending, not replace the target instantly.");
        require(attention.switchCompletesAt > simulation.simulationTime,
                "C: reorientation must have a positive delay.");
        while (simulation.simulationTime <= attention.switchCompletesAt + DT) simulation.update(DT);
        require(attention.primaryThreat == challenger && attention.formerPrimary == former,
                "C: the primary threat must change only after the reorientation delay.");
        require(attention.precisionPenaltyUntil > attention.lastShiftTime,
                "C: a completed shift must leave temporary uncertainty.");
    }

    private void verifyDPeripheralStateIsNotReadExactly() {
        var simulation = fixture(3_204L);
        var observer = simulation.blueTeam[0];
        var attention = observer.attentionAllocation;
        var primary = attention.primaryThreat;
        var peripheral = attention.peripheralThreat;
        observer.pos = new Pos(1.5, 2.0);
        primary.pos = new Pos(3.0, 2.0);
        peripheral.pos = new Pos(6.4, 5.8);
        attention.lastShiftTime = 1_000;
        var awareness = observer.opponentAwareness.get(peripheral);
        awareness.observedState = CombatState.NEUTRAL;
        awareness.observationCooldown = 0;
        peripheral.state = CombatState.ATTACK_HOLD;
        simulation.update(DT);
        require(attention.peripheralThreat == peripheral && !awareness.stateDetailFresh &&
                        awareness.observedState == CombatState.NEUTRAL,
                "D: a peripheral attack may create an alert but must not expose its exact state.");
        require(simulation.group3Metrics().peripheralStateReads == 0,
                "D: the forbidden peripheral exact-state counter must remain zero.");
    }

    private void verifyEDeterminism() {
        var first = fixture(3_205L);
        var second = fixture(3_205L);
        for (var tick = 0; tick < 600; ++tick) {
            first.update(DT);
            second.update(DT);
        }
        for (var n = 0; n < first.agents.length; ++n) {
            var firstAttention = first.agents[n].attentionAllocation;
            var secondAttention = second.agents[n].attentionAllocation;
            require(code(first, firstAttention.primaryThreat).equals(code(second, secondAttention.primaryThreat)) &&
                            first.agents[n].pos.minus(second.agents[n].pos).hypotenuse() < 0.000_000_1,
                    "E: same seed must reproduce attention and movement.");
        }
    }

    private String code(DuelSimulation simulation, Musketeer agent) {
        for (var n = 0; n < simulation.agents.length; ++n)
            if (simulation.agents[n] == agent) return Integer.toString(n);
        return "-";
    }

    private DuelSimulation fixture(long seed) {
        var blue = new TeamConfiguration3("Blue", AgentConfiguration.defaultBlue(),
                AgentConfiguration.defaultBlueTwo(), AgentConfiguration.defaultBlueThree());
        var red = new TeamConfiguration3("Red", AgentConfiguration.defaultRed(),
                AgentConfiguration.defaultRedTwo(), AgentConfiguration.defaultRedThree());
        var simulation = new DuelSimulation(blue, red, SimulationScenario.CONTROLLED_3V3, seed);
        simulation.disableGroup3AttacksForTest();
        return simulation;
    }

    private void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
