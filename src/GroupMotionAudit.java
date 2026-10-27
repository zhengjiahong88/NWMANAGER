import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.List;

final class GroupMotionAudit {
    private static final double MOVING_SPEED = 0.12;

    private final Pos[] previousVelocities = new Pos[4];
    private final boolean[] previousMoving = new boolean[4];
    private final LocalManeuver[] previousManeuvers = new LocalManeuver[4];
    private final EnumMap<GroupMotionSuspicion, ActiveSuspicion> active =
            new EnumMap<>(GroupMotionSuspicion.class);
    private final ArrayList<GroupMotionSuspicionEvent> events = new ArrayList<>();
    private final ArrayDeque<AreaSample> recentAreas = new ArrayDeque<>();
    private double individualCorrelationSum, teamCorrelationSum, localDirectionOppositionTime;
    private double minimumFormationArea = Double.POSITIVE_INFINITY, maximumFormationArea;
    private double minimumPartnerDistance = Double.POSITIVE_INFINITY, maximumPartnerDistance;
    private int individualCorrelationSamples, teamCorrelationSamples,
            simultaneousDirectionChanges, simultaneousStops, identicalManeuverStarts;

    void observe(DuelSimulation simulation, double dt) {
        if (aliveCount(simulation) != 4) {
            closeAll(simulation.simulationTime);
            return;
        }
        var agents = simulation.agents;
        var pairCorrelation = 0.0;
        var pairSamples = 0;
        for (var first = 0; first < agents.length; ++first)
            for (var second = first + 1; second < agents.length; ++second) {
                var correlation = correlation(agents[first].velocity, agents[second].velocity);
                if (!Double.isFinite(correlation)) continue;
                pairCorrelation += correlation;
                ++pairSamples;
            }
        var individualCorrelation = pairSamples == 0 ? Double.NaN : pairCorrelation / pairSamples;
        if (Double.isFinite(individualCorrelation)) {
            individualCorrelationSum += individualCorrelation;
            ++individualCorrelationSamples;
        }
        var blueCorrelation = correlation(agents[0].velocity, agents[1].velocity);
        var redCorrelation = correlation(agents[2].velocity, agents[3].velocity);
        var teamCorrelation = finiteMean(blueCorrelation, redCorrelation);
        if (Double.isFinite(teamCorrelation)) {
            teamCorrelationSum += teamCorrelation;
            ++teamCorrelationSamples;
        }
        var directionChanges = 0;
        var stops = 0;
        var maneuverStarts = new IdentityHashMap<LocalManeuver, Boolean>();
        var startsByType = new EnumMap<LocalManeuverType, Integer>(LocalManeuverType.class);
        var minimumSpeed = Double.POSITIVE_INFINITY;
        for (var n = 0; n < agents.length; ++n) {
            var speed = agents[n].velocity.hypotenuse();
            var moving = speed >= MOVING_SPEED;
            minimumSpeed = Math.min(minimumSpeed, speed);
            if (moving && previousMoving[n] && previousVelocities[n] != null &&
                    Math.abs(angleDifference(previousVelocities[n].radians(),
                            agents[n].velocity.radians())) >= Math.toRadians(35)) ++directionChanges;
            if (!moving && previousMoving[n]) ++stops;
            var maneuver = agents[n].localManeuver;
            if (maneuver != null && maneuver != previousManeuvers[n] &&
                    maneuverStarts.put(maneuver, Boolean.TRUE) == null)
                startsByType.merge(maneuver.type, 1, Integer::sum);
            previousVelocities[n] = agents[n].velocity;
            previousMoving[n] = moving;
            previousManeuvers[n] = maneuver;
        }
        if (directionChanges >= 3) ++simultaneousDirectionChanges;
        if (stops >= 2) ++simultaneousStops;
        var maximumIdenticalStarts = 0;
        for (var count : startsByType.values()) maximumIdenticalStarts = Math.max(maximumIdenticalStarts, count);
        if (maximumIdenticalStarts >= 2) ++identicalManeuverStarts;
        if (opposed(agents[0].velocity, agents[1].velocity) ||
                opposed(agents[2].velocity, agents[3].velocity)) localDirectionOppositionTime += dt;
        var area = formationArea(agents);
        minimumFormationArea = Math.min(minimumFormationArea, area);
        maximumFormationArea = Math.max(maximumFormationArea, area);
        var partnerDistance = (agents[0].pos.minus(agents[1].pos).hypotenuse() +
                agents[2].pos.minus(agents[3].pos).hypotenuse()) / 2;
        minimumPartnerDistance = Math.min(minimumPartnerDistance, partnerDistance);
        maximumPartnerDistance = Math.max(maximumPartnerDistance, partnerDistance);
        recentAreas.addLast(new AreaSample(simulation.simulationTime, area));
        while (!recentAreas.isEmpty() && recentAreas.getFirst().time < simulation.simulationTime - 1)
            recentAreas.removeFirst();
        var fixedFormation = recentAreas.size() >= 60 && minimumSpeed >= MOVING_SPEED &&
                recentAreaVariation() < 0.015;
        updateSuspicion(GroupMotionSuspicion.FOUR_AGENT_DIRECTION_SYNC,
                minimumSpeed >= 0.16 && individualCorrelation >= 0.92,
                simulation.simulationTime, individualCorrelation);
        updateSuspicion(GroupMotionSuspicion.TEAM_DIRECTION_SYNC,
                teamCorrelation >= 0.96, simulation.simulationTime, teamCorrelation);
        updateSuspicion(GroupMotionSuspicion.SIMULTANEOUS_STOP_SYNC,
                stops >= 2, simulation.simulationTime, stops / 4.0);
        updateSuspicion(GroupMotionSuspicion.IDENTICAL_MANEUVER_SYNC,
                maximumIdenticalStarts >= 2, simulation.simulationTime,
                maximumIdenticalStarts / 4.0);
        updateSuspicion(GroupMotionSuspicion.FIXED_FORMATION_MOTION,
                fixedFormation, simulation.simulationTime,
                fixedFormation ? 1 - recentAreaVariation() / 0.015 : 0);
    }

    void finish(double time) {
        closeAll(time);
    }

    GroupMotionMetrics metrics() {
        return new GroupMotionMetrics(
                individualCorrelationSum / Math.max(1, individualCorrelationSamples),
                teamCorrelationSum / Math.max(1, teamCorrelationSamples),
                simultaneousDirectionChanges, simultaneousStops, identicalManeuverStarts,
                localDirectionOppositionTime,
                finiteVariation(minimumFormationArea, maximumFormationArea),
                finiteVariation(minimumPartnerDistance, maximumPartnerDistance));
    }

    List<GroupMotionSuspicionEvent> events() {
        return List.copyOf(events);
    }

    private void updateSuspicion(GroupMotionSuspicion suspicion, boolean detected,
                                 double time, double evidence) {
        var current = active.get(suspicion);
        if (detected) {
            if (current == null) active.put(suspicion, new ActiveSuspicion(time, evidence));
            else current.maximumEvidence = Math.max(current.maximumEvidence, evidence);
        } else if (current != null) {
            events.add(new GroupMotionSuspicionEvent(suspicion, current.startedAt,
                    time, current.maximumEvidence));
            active.remove(suspicion);
        }
    }

    private void closeAll(double time) {
        for (var entry : active.entrySet())
            events.add(new GroupMotionSuspicionEvent(entry.getKey(), entry.getValue().startedAt,
                    time, entry.getValue().maximumEvidence));
        active.clear();
    }

    private double recentAreaVariation() {
        var minimum = Double.POSITIVE_INFINITY;
        var maximum = 0.0;
        for (var sample : recentAreas) {
            minimum = Math.min(minimum, sample.area);
            maximum = Math.max(maximum, sample.area);
        }
        return finiteVariation(minimum, maximum);
    }

    private double formationArea(Musketeer[] agents) {
        var center = new Pos();
        for (var agent : agents) center = center.plus(agent.pos);
        center = center.div(agents.length);
        var ordered = agents.clone();
        var finalCenter = center;
        java.util.Arrays.sort(ordered, java.util.Comparator.comparingDouble(
                agent -> agent.pos.minus(finalCenter).radians()));
        var twiceArea = 0.0;
        for (var n = 0; n < ordered.length; ++n) {
            var first = ordered[n].pos;
            var second = ordered[(n + 1) % ordered.length].pos;
            twiceArea += first.x() * second.y() - first.y() * second.x();
        }
        return Math.abs(twiceArea) / 2;
    }

    private int aliveCount(DuelSimulation simulation) {
        var count = 0;
        for (var agent : simulation.agents) if (agent.alive) ++count;
        return count;
    }

    private boolean opposed(Pos first, Pos second) {
        var correlation = correlation(first, second);
        return Double.isFinite(correlation) && correlation <= -0.25;
    }

    private double correlation(Pos first, Pos second) {
        var magnitude = first.hypotenuse() * second.hypotenuse();
        return magnitude < MOVING_SPEED * MOVING_SPEED ? Double.NaN :
                Math.clamp(first.dot(second) / magnitude, -1, 1);
    }

    private double finiteMean(double first, double second) {
        if (Double.isFinite(first) && Double.isFinite(second)) return (first + second) / 2;
        if (Double.isFinite(first)) return first;
        return second;
    }

    private double finiteVariation(double minimum, double maximum) {
        return Double.isFinite(minimum) ? maximum - minimum : 0;
    }

    private double angleDifference(double first, double second) {
        return Math.atan2(Math.sin(second - first), Math.cos(second - first));
    }

    private static final class ActiveSuspicion {
        final double startedAt;
        double maximumEvidence;

        ActiveSuspicion(double startedAt, double maximumEvidence) {
            this.startedAt = startedAt;
            this.maximumEvidence = maximumEvidence;
        }
    }

    private record AreaSample(double time, double area) {
    }
}
