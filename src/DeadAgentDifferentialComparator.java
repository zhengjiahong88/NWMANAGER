import java.util.ArrayList;
import java.util.LinkedHashSet;

final class DeadAgentDifferentialComparator {
    private static final double EPSILON = 1e-9;

    DeadAgentDifferentialComparison compare(DeadAgentDifferentialResult left,
                                             DeadAgentDifferentialResult right) {
        Long position = null, velocity = null, body = null, weapon = null, state = null,
                target = null, attention = null, formation = null, cover = null, opportunity = null;
        var details = new ArrayList<String>();
        var affected = new LinkedHashSet<String>();
        var snapshots = Math.min(left.snapshots.size(), right.snapshots.size());
        for (var n = 0; n < snapshots; ++n) {
            var first = left.snapshots.get(n);
            var second = right.snapshots.get(n);
            if (!first.activeController().equals(second.activeController()) ||
                    !first.groupPhase().equals(second.groupPhase()))
                addDetail(details, first.tick(), "controller_or_phase", "GROUP");
            var count = Math.min(first.agents().size(), second.agents().size());
            for (var agentIndex = 0; agentIndex < count; ++agentIndex) {
                var a = first.agents().get(agentIndex);
                var b = second.agents().get(agentIndex);
                if (!a.agentId().equals(b.agentId())) {
                    addDetail(details, first.tick(), "agent_order", a.agentId() + "/" + b.agentId());
                    affected.add(a.agentId());
                    continue;
                }
                if (!same(a.position(), b.position())) {
                    if (position == null) position = first.tick();
                    affected.add(a.agentId());
                    addDetail(details, first.tick(), "position", a.agentId());
                }
                if (!same(a.velocity(), b.velocity())) {
                    if (velocity == null) velocity = first.tick();
                    affected.add(a.agentId());
                    addDetail(details, first.tick(), "velocity", a.agentId());
                }
                if (!same(a.bodyAngle(), b.bodyAngle())) {
                    if (body == null) body = first.tick();
                    affected.add(a.agentId());
                    addDetail(details, first.tick(), "body_angle", a.agentId());
                }
                if (!same(a.weaponAngle(), b.weaponAngle())) {
                    if (weapon == null) weapon = first.tick();
                    affected.add(a.agentId());
                    addDetail(details, first.tick(), "weapon_angle", a.agentId());
                }
                if (a.combatState() != b.combatState() || !a.actionState().equals(b.actionState()) ||
                        !a.localManeuver().equals(b.localManeuver())) {
                    if (state == null) state = first.tick();
                    affected.add(a.agentId());
                    addDetail(details, first.tick(), "state", a.agentId());
                }
                if (!a.targetId().equals(b.targetId())) {
                    if (target == null) target = first.tick();
                    affected.add(a.agentId());
                    addDetail(details, first.tick(), "target", a.agentId());
                }
                if (!a.primaryThreatId().equals(b.primaryThreatId()) ||
                        !a.secondaryThreatId().equals(b.secondaryThreatId()) ||
                        !a.peripheralThreatId().equals(b.peripheralThreatId())) {
                    if (attention == null) attention = first.tick();
                    affected.add(a.agentId());
                    addDetail(details, first.tick(), "attention", a.agentId());
                }
                if (!a.formationRole().equals(b.formationRole()) ||
                        !a.approachRole().equals(b.approachRole()) ||
                        !a.teamManeuver().equals(b.teamManeuver())) {
                    if (formation == null) formation = first.tick();
                    affected.add(a.agentId());
                    addDetail(details, first.tick(), "formation", a.agentId());
                }
                if (a.viableIncomingCoverLinks() != b.viableIncomingCoverLinks() ||
                        a.viableOutgoingCoverLinks() != b.viableOutgoingCoverLinks()) {
                    if (cover == null) cover = first.tick();
                    affected.add(a.agentId());
                    addDetail(details, first.tick(), "cover", a.agentId());
                }
                if (!a.attackOpportunityReason().equals(b.attackOpportunityReason()) ||
                        a.attackOpportunityViable() != b.attackOpportunityViable()) {
                    if (opportunity == null) opportunity = first.tick();
                    affected.add(a.agentId());
                    addDetail(details, first.tick(), "attack_opportunity", a.agentId());
                }
            }
        }
        Long combat = null, transition = null;
        var combatCount = Math.min(left.combatEvents.size(), right.combatEvents.size());
        for (var n = 0; n < combatCount; ++n)
            if (!left.combatEvents.get(n).equals(right.combatEvents.get(n))) {
                combat = Math.min(left.combatEvents.get(n).tick(), right.combatEvents.get(n).tick());
                addDetail(details, combat, "combat_event", "EVENT");
                break;
            }
        if (combat == null && left.combatEvents.size() != right.combatEvents.size())
            combat = eventTick(left, right, combatCount);
        var transitionCount = Math.min(left.transitions.size(), right.transitions.size());
        for (var n = 0; n < transitionCount; ++n)
            if (!left.transitions.get(n).equals(right.transitions.get(n))) {
                transition = Math.min(left.transitions.get(n).tick(), right.transitions.get(n).tick());
                addDetail(details, transition, "transition", "TRANSITION");
                break;
            }
        if (transition == null && left.transitions.size() != right.transitions.size())
            transition = transitionTick(left, right, transitionCount);
        var winnerChanged = left.winner != right.winner;
        var completionChanged = left.matchCompleted != right.matchCompleted;
        var endDifference = Math.abs(left.matchEndTime - right.matchEndTime);
        var identical = details.isEmpty() && left.snapshots.size() == right.snapshots.size() &&
                left.combatEvents.size() == right.combatEvents.size() &&
                left.transitions.size() == right.transitions.size() && !winnerChanged &&
                !completionChanged && endDifference <= EPSILON;
        return new DeadAgentDifferentialComparison(left.runName, right.runName, identical,
                position, velocity, body, weapon, state, target, attention, formation, cover,
                opportunity, combat, transition, new ArrayList<>(affected), winnerChanged,
                completionChanged, endDifference, details);
    }

    private boolean same(Pos first, Pos second) {
        return first.minus(second).hypotenuse() <= EPSILON;
    }

    private boolean same(double first, double second) {
        return Math.abs(first - second) <= EPSILON;
    }

    private void addDetail(ArrayList<String> details, long tick, String system, String agent) {
        if (details.size() < 80) details.add("tick=" + tick + " system=" + system + " agent=" + agent);
    }

    private long eventTick(DeadAgentDifferentialResult left, DeadAgentDifferentialResult right, int index) {
        return index < left.combatEvents.size() ? left.combatEvents.get(index).tick() :
                right.combatEvents.get(index).tick();
    }

    private long transitionTick(DeadAgentDifferentialResult left, DeadAgentDifferentialResult right,
                                int index) {
        return index < left.transitions.size() ? left.transitions.get(index).tick() :
                right.transitions.get(index).tick();
    }
}
