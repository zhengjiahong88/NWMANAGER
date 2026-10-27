import java.util.ArrayList;
import java.util.List;

public final class DecisionExplanationTest {
    private static final double DT = 1.0 / 120.0;

    public static void main(String[] args) {
        verifyRevengeKillIsExplained();
        verifyUndetectedAttackIsExplicit();
        verifyDecisionAndKillDeterminism();
        System.out.println("Decision trace and kill explanation tests I-J passed.");
    }

    private static void verifyRevengeKillIsExplained() {
        var simulation = modern(301L);
        var blocker = simulation.blueTeam[0];
        var revenger = simulation.blueTeam[1];
        var attacker = simulation.redTeam[0];
        reset(blocker, new Pos(3.00, 3.10), 0);
        reset(revenger, new Pos(3.18, 3.82),
                attacker.pos.minus(new Pos(3.18, 3.82)).radians());
        reset(attacker, new Pos(4.46, 3.10), Math.PI);
        reset(simulation.redTeam[1], new Pos(7.25, 6.20), Math.PI);
        revenger.angle = attacker.pos.minus(revenger.pos).radians();
        attacker.state = CombatState.BLOCK_STUN;
        attacker.target = blocker;
        attacker.hitPoints = 40;
        observeAll(simulation);
        simulation.onAttackBlocked(attacker, blocker);
        for (var tick = 0; tick < 240 && simulation.lastKillExplanation() == null; ++tick)
            simulation.update(DT);
        var explanation = simulation.lastKillExplanation();
        require(explanation != null && explanation.killer() == revenger &&
                        explanation.victim() == attacker,
                "I: the physical revenge kill must produce the correct killer and victim.");
        require(explanation.initiatingReason() == AttackOpportunityReason.COUNTER_STAB_WINDOW &&
                        explanation.victimStateAtCommit() == CombatState.BLOCK_STUN &&
                        explanation.victimAlly() == simulation.redTeam[1] &&
                        !explanation.coverFailureReason().isBlank() &&
                        !explanation.finalCause().isBlank(),
                "I: the explanation must preserve opening, victim state, ally ETA and final cause.");
        require(simulation.decisionTraces().stream().anyMatch(trace ->
                        trace.category() == DecisionCategory.REVENGE &&
                                trace.action().equals("COMMIT_REVENGE")),
                "I: decision history must distinguish the revenge decision from its execution.");
        var chain = simulation.lastKillCausalChain();
        require(chain != null && chain.completeOrExplicit() && chain.attackDecision() != null &&
                        chain.physicalResolution().stream().anyMatch(trace ->
                                trace.action().equals("KILL")),
                "H: the causal chain must connect the attack decision to physical death.");
    }

    private static void verifyUndetectedAttackIsExplicit() {
        var simulation = modern(302L);
        var killer = simulation.redTeam[0];
        var victim = simulation.blueTeam[0];
        var awareness = victim.opponentAwareness.get(killer);
        awareness.visible = false;
        awareness.memoryTime = 0;
        require(simulation.defensiveFailureForTest(killer, victim, CombatState.NEUTRAL,
                        false, false).equals("attaque_non_detectee"),
                "I: an unperceived attack must be explicitly classified as undetected.");
        var chain = new KillCausalChain(null, null, null, null, null, null, List.of());
        require(chain.observed(chain.victimReaction()).equals("NONE_OBSERVED"),
                "I: an absent reaction must be written as NONE_OBSERVED.");
    }

    private static void verifyDecisionAndKillDeterminism() {
        var first = replay(417L);
        var second = replay(417L);
        require(first.snapshot.equals(second.snapshot),
                "J: equal seeds must finish with identical physical snapshots.");
        require(first.traces.equals(second.traces) && !first.traces.isEmpty(),
                "J: equal seeds must produce identical normalized decision traces.");
        require(first.kills.equals(second.kills) && !first.kills.isEmpty(),
                "J: equal seeds must produce identical kill explanations.");
        require(first.chains.equals(second.chains) && !first.chains.isEmpty(),
                "J: equal seeds must produce identical causal chains.");
        require(first.traces.size() <= 32,
                "J: the decision history must remain a bounded circular buffer.");
    }

    private static Replay replay(long seed) {
        var simulation = modern(seed);
        for (var tick = 0; tick < 60 * 120 && simulation.winningTeam == null; ++tick)
            simulation.update(DT);
        require(simulation.winningTeam != null,
                "J: deterministic explanation replay must complete.");
        var traces = new ArrayList<String>();
        for (var trace : simulation.decisionTraces())
            traces.add(trace.deterministicKey(UiLanguage.FRENCH));
        var kills = new ArrayList<String>();
        for (var kill : simulation.killExplanations())
            kills.add(kill.deterministicKey(UiLanguage.FRENCH));
        var chains = new ArrayList<String>();
        for (var chain : simulation.killCausalChains())
            chains.add(chain.deterministicKey(UiLanguage.FRENCH));
        var snapshot = new StringBuilder().append(simulation.winningTeam);
        for (var agent : simulation.agents)
            snapshot.append('|').append(agent.name(UiLanguage.FRENCH)).append(':')
                    .append(agent.alive).append(':').append(agent.hitPoints).append(':')
                    .append(agent.pos).append(':').append(agent.state);
        return new Replay(snapshot.toString(), List.copyOf(traces), List.copyOf(kills),
                List.copyOf(chains));
    }

    private static DuelSimulation modern(long seed) {
        return new DuelSimulation(AgentConfiguration.defaultBlue(),
                AgentConfiguration.defaultBlueTwo(), AgentConfiguration.defaultRed(),
                AgentConfiguration.defaultRedTwo(), SimulationScenario.MODERN_2V2, seed);
    }

    private static void observeAll(DuelSimulation simulation) {
        for (var observer : simulation.agents)
            for (var opponent : simulation.agents) {
                if (observer.team == opponent.team) continue;
                var awareness = observer.opponentAwareness.get(opponent);
                awareness.visible = true;
                awareness.lastKnown = opponent.pos;
                awareness.perceivedVelocity = opponent.velocity;
                awareness.observedBodyAngle = opponent.angle;
                awareness.observedWeaponAngle = opponent.weaponWorldAngle();
                awareness.observedProjectedRange = opponent.projectedThrustLength();
                awareness.observedState = opponent.state;
                awareness.observedStateTime = opponent.stateTime;
                awareness.observedAttackDirection = opponent.attackDirection;
                awareness.observedBlockDirection = opponent.blockDirection;
                awareness.memoryTime = 2;
            }
    }

    private static void reset(Musketeer agent, Pos position, double angle) {
        agent.pos = agent.previous = position;
        agent.velocity = new Pos();
        agent.angle = angle;
        agent.weaponYaw = agent.desiredWeaponYaw = 0;
        agent.state = CombatState.NEUTRAL;
        agent.stateTime = 0;
        agent.decisionTime = 0;
        agent.attackResolved = false;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    private record Replay(String snapshot, List<String> traces, List<String> kills,
                          List<String> chains) {
    }
}
