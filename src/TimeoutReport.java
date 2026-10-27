final class TimeoutReport {
    final long seed;
    final double simulationTime;
    final TimeoutCause cause;
    final String snapshot;

    private TimeoutReport(long seed, double simulationTime, TimeoutCause cause, String snapshot) {
        this.seed = seed;
        this.simulationTime = simulationTime;
        this.cause = cause;
        this.snapshot = snapshot;
    }

    static TimeoutReport capture(long seed, DuelSimulation simulation) {
        var cause = classify(simulation);
        var text = new StringBuilder().append("TIMEOUT_REPORT seed=").append(seed).
                append(" time=").append("%.3f".formatted(simulation.simulationTime)).
                append(" cause=").append(cause).append('\n');
        for (var agent : simulation.agents)
            text.append("agent=").append(agent.name(UiLanguage.FRENCH)).
                    append(" alive=").append(agent.alive).
                    append(" hp=").append(agent.hitPoints).
                    append(" pos=").append(agent.pos).
                    append(" velocity=").append(agent.velocity).
                    append(" angle=").append("%.3f".formatted(agent.angle)).
                    append(" state=").append(agent.state).
                    append(" stateTime=").append("%.3f".formatted(agent.stateTime)).
                    append(" target=").append(name(agent.target)).
                    append(" primaryMark=").append(name(agent.primaryMark)).
                    append(" groupIntent=").append(agent.groupIntent).
                    append(" tacticalIntent=").append(agent.tacticalIntent).
                    append(" flankPhase=").append(agent.flankPhase).append('\n');
        var engagement = activeEngagement(simulation);
        if (engagement != null) {
            text.append("engagement=").append(engagement.type).
                    append(" status=").append(engagement.status).append('\n');
            if (engagement.type == EngagementType.GROUP_2V2)
                text.append("groupPhase=").append(engagement.group.phase).
                        append(" phaseAge=").append("%.3f".formatted(
                                simulation.simulationTime - engagement.group.phaseStartedAt)).append('\n');
            else if (engagement.type == EngagementType.TWO_VS_ONE)
                appendTwoVsOne(text, simulation, engagement);
        }
        return new TimeoutReport(seed, simulation.simulationTime, cause, text.toString());
    }

    private static void appendTwoVsOne(StringBuilder text, DuelSimulation simulation,
                                       Engagement engagement) {
        var fixer = engagement.fixer;
        var flanker = engagement.flanker;
        var defender = engagement.primaryTarget;
        var separation = Math.abs(angleDifference(
                fixer.pos.minus(defender.pos).radians(),
                flanker.pos.minus(defender.pos).radians()));
        var frontAngle = Math.abs(angleDifference(defender.angle,
                fixer.pos.minus(defender.pos).radians()));
        var coordination = engagement.coordination;
        text.append("fixer=").append(name(fixer)).
                append(" flanker=").append(name(flanker)).
                append(" defender=").append(name(defender)).append('\n').
                append("fixerDestination=").append(fixer.tacticalDestination).
                append(" flankerDestination=").append(flanker.tacticalDestination).append('\n').
                append("fixerFrontAngleDeg=").append("%.2f".formatted(Math.toDegrees(frontAngle))).
                append(" attackersSeparationDeg=").append("%.2f".formatted(Math.toDegrees(separation))).
                append(" fixerDistance=").append("%.3f".formatted(fixer.pos.minus(defender.pos).hypotenuse())).
                append(" flankerDistance=").append("%.3f".formatted(flanker.pos.minus(defender.pos).hypotenuse())).
                append(" flankNoProgress=").append("%.3f".formatted(flanker.rearNoProgressTime)).append('\n').
                append("coordinationPhase=").append(coordination.phase).
                append(" coordinationPhaseAge=").append("%.3f".formatted(
                        simulation.simulationTime - coordination.phaseStartedAt)).
                append(" timeSinceAttack=").append("%.3f".formatted(
                        simulation.simulationTime - coordination.lastAttackAt)).
                append(" flankAborts=").append(coordination.abortedPlans).
                append(" coordinationPlans=").append(simulation.modernMetrics().coordinationPlansStarted).
                append('\n');
    }

    private static TimeoutCause classify(DuelSimulation simulation) {
        if (simulation.winningTeam != null) return TimeoutCause.MATCH_COMPLETED;
        for (var agent : simulation.agents)
            if (agent.alive && agent.state == CombatState.ATTACK_HOLD && agent.stateTime >= 8)
                return TimeoutCause.PERMANENT_ATTACK_HOLD;
        var engagement = activeEngagement(simulation);
        if (engagement == null) return physicalDeadlock(simulation) ?
                TimeoutCause.PHYSICAL_DEADLOCK : TimeoutCause.UNKNOWN;
        if (engagement.type == EngagementType.GROUP_2V2 &&
                engagement.group.phase == GroupPhase.ROTATE &&
                simulation.simulationTime - engagement.group.phaseStartedAt >= 12)
            return TimeoutCause.GROUP_ROTATION_NO_COMMIT;
        if (engagement.type == EngagementType.TWO_VS_ONE) {
            var coordination = engagement.coordination;
            if (coordination.abortedPlans >= 4)
                return TimeoutCause.REPEATED_COORDINATION_ABORT;
            if ((engagement.flanker.flankPhase == FlankPhase.APPROACH_SIDE ||
                    engagement.flanker.flankPhase == FlankPhase.GAIN_REAR_ANGLE) &&
                    engagement.flanker.rearNoProgressTime >= 3)
                return TimeoutCause.TWO_VS_ONE_NO_FLANK_PROGRESS;
            if (engagement.flanker.tacticalDestination != null &&
                    clearance(engagement.flanker.tacticalDestination) < 0)
                return TimeoutCause.UNREACHABLE_DESTINATION;
            if ((coordination.phase == TwoVsOneCoordinationPhase.BUILD_GEOMETRY ||
                    coordination.phase == TwoVsOneCoordinationPhase.RECOVER_COORDINATION) &&
                    simulation.simulationTime - coordination.lastAttackAt >= 12)
                return TimeoutCause.TWO_VS_ONE_ALIGNMENT_LOOP;
        }
        return physicalDeadlock(simulation) ? TimeoutCause.PHYSICAL_DEADLOCK : TimeoutCause.UNKNOWN;
    }

    private static boolean physicalDeadlock(DuelSimulation simulation) {
        var moving = 0.0;
        var living = 0;
        for (var agent : simulation.agents)
            if (agent.alive) {
                moving += agent.velocity.hypotenuse();
                ++living;
            }
        return living > 1 && moving / living < 0.06;
    }

    private static double clearance(Pos position) {
        return Math.min(Math.min(position.x() - Musketeer.BODY_RADIUS,
                        DuelSimulation.ARENA_WIDTH - Musketeer.BODY_RADIUS - position.x()),
                Math.min(position.y() - Musketeer.BODY_RADIUS,
                        DuelSimulation.ARENA_HEIGHT - Musketeer.BODY_RADIUS - position.y()));
    }

    private static Engagement activeEngagement(DuelSimulation simulation) {
        Engagement result = null;
        for (var engagement : simulation.engagements())
            if (engagement.status != EngagementStatus.BROKEN &&
                    engagement.status != EngagementStatus.FINISHED)
                result = engagement;
        return result;
    }

    private static String name(Musketeer agent) {
        return agent == null ? "NONE" : agent.name(UiLanguage.FRENCH);
    }

    private static double angleDifference(double from, double to) {
        return Math.atan2(Math.sin(to - from), Math.cos(to - from));
    }
}
