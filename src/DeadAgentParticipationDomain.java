enum DeadAgentParticipationDomain {
    MOVEMENT,
    VELOCITY_INTEGRATION,
    BODY_ROTATION,
    WEAPON_ROTATION,
    AVOIDANCE,
    BOUNDARY,
    FORMATION,
    COVER,
    ATTENTION,
    TARGET_SELECTION,
    ATTACK_DECISION,
    REVENGE,
    COUNTER_COVER,
    TEAM_MANEUVER,
    APPROACH,
    STEERING,
    RENDER,
    ROSTER_HISTORY,
    DEATH_EVENT,
    RESULT_ACCOUNTING;

    boolean passiveAfterDeath() {
        return this == RENDER || this == ROSTER_HISTORY || this == DEATH_EVENT ||
                this == RESULT_ACCOUNTING;
    }
}
