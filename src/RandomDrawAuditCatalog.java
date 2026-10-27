final class RandomDrawAuditCatalog {
    RandomDrawPurpose purposeFor(int line) {
        return switch (line) {
            case 345, 1169 -> RandomDrawPurpose.INITIAL_ROTATION_DIRECTION;
            case 1918 -> RandomDrawPurpose.GROUP3_PERCEPTION_REFRESH;
            case 1942 -> RandomDrawPurpose.GROUP3_PERCEPTION_DETECTION;
            case 1958, 1959 -> RandomDrawPurpose.GROUP3_PERCEPTION_POSITION_NOISE;
            case 1960, 1961 -> RandomDrawPurpose.GROUP3_PERCEPTION_VELOCITY_NOISE;
            case 1963, 1965 -> RandomDrawPurpose.GROUP3_PERCEPTION_ORIENTATION_NOISE;
            case 1967 -> RandomDrawPurpose.GROUP3_PERCEPTION_RANGE_NOISE;
            case 1969 -> RandomDrawPurpose.GROUP3_PERCEPTION_STATE_DETAIL;
            case 2100 -> RandomDrawPurpose.GROUP3_APPROACH_PATTERN;
            case 2104 -> RandomDrawPurpose.GROUP3_APPROACH_SIDE;
            case 2108, 2109, 2110 -> RandomDrawPurpose.GROUP3_APPROACH_DURATION;
            case 2119, 2120, 2121 -> RandomDrawPurpose.GROUP3_APPROACH_COMMITMENT;
            case 2130 -> RandomDrawPurpose.GROUP3_APPROACH_MEETING_POINT;
            case 2478 -> RandomDrawPurpose.GROUP3_ATTACK_TIMING;
            case 3016, 3020 -> RandomDrawPurpose.GROUP3_MANEUVER_SELECTION;
            case 3048 -> RandomDrawPurpose.GROUP3_MANEUVER_DIRECTION;
            case 4674 -> RandomDrawPurpose.LOCAL_COORDINATION_SIDE;
            case 5055, 8015 -> RandomDrawPurpose.PRESSURE_RELEASE_DELAY;
            case 5123 -> RandomDrawPurpose.DECISION_INTERVAL;
            case 5272, 8505 -> RandomDrawPurpose.PERCEPTION_DETECTION;
            case 5279 -> RandomDrawPurpose.PERCEPTION_POSITION_NOISE;
            case 5282, 5283 -> RandomDrawPurpose.PERCEPTION_VELOCITY_NOISE;
            case 5286, 5288 -> RandomDrawPurpose.PERCEPTION_ORIENTATION_NOISE;
            case 5290 -> RandomDrawPurpose.PERCEPTION_RANGE_NOISE;
            case 5489, 9697, 9721, 10824 -> RandomDrawPurpose.TACTICAL_SCORE_NOISE;
            case 5516 -> RandomDrawPurpose.TACTICAL_TIMING_NOISE;
            case 5567, 10057 -> RandomDrawPurpose.OPERATION_CHECK;
            case 5801 -> RandomDrawPurpose.TEAM_MANEUVER_SCORE_NOISE;
            case 5819 -> RandomDrawPurpose.TEAM_MANEUVER_WEIGHTED_SELECTION;
            case 5951 -> RandomDrawPurpose.GROUP3_MANEUVER_DIRECTION;
            case 7521 -> RandomDrawPurpose.ENGAGEMENT_PHASE_COMMIT;
            case 7522, 7557, 7558, 7559, 7560, 7561 -> RandomDrawPurpose.ENGAGEMENT_PHASE_DURATION;
            case 7577 -> RandomDrawPurpose.TEAMWORK_DIRECTION;
            case 7607, 8192, 8348 -> RandomDrawPurpose.BLOCK_DECISION;
            case 7686 -> RandomDrawPurpose.OPENING_ATTACK_DECISION;
            case 7755 -> RandomDrawPurpose.FIFTY_FIFTY_DECISION;
            case 7777, 8077, 8095, 8444, 8459, 8480, 8521, 8524, 8535, 8546 ->
                    RandomDrawPurpose.PRESSURE_DECISION;
            case 7889, 7965 -> RandomDrawPurpose.TACTICAL_SCORE_NOISE;
            case 8154, 8825, 8842 -> RandomDrawPurpose.DISTANCE_ESTIMATE;
            case 8171 -> RandomDrawPurpose.REACTION_DELAY;
            case 8201, 9904, 9908, 9956, 11449 -> RandomDrawPurpose.CHAMBER_DECISION;
            case 8231 -> RandomDrawPurpose.BACK_PROTECTION_DECISION;
            case 8240, 8261, 9024 -> RandomDrawPurpose.TARGET_SELECTION;
            case 8281, 8291, 8342, 8366, 9757 -> RandomDrawPurpose.ATTACK_RELEASE_DECISION;
            case 8452, 8453, 8465, 8470, 8475, 8476, 8484, 8485, 8486, 8918 ->
                    RandomDrawPurpose.IDLE_MOVEMENT_DECISION;
            case 8766, 8768, 8769, 8770, 8771, 8773, 8774, 8775 -> RandomDrawPurpose.BACKSTAB_AIM;
            case 8902, 8903, 8904, 8905, 8906, 8908, 8909 -> RandomDrawPurpose.FREE_KILL_AIM;
            case 8972 -> RandomDrawPurpose.BACK_ESCAPE_DIRECTION;
            case 9054, 9055, 9173, 9174 -> RandomDrawPurpose.DEZONE_DURATION;
            case 9058, 9175, 9178, 9179, 10091 -> RandomDrawPurpose.DEZONE_DIRECTION;
            case 9064, 9186, 9189 -> RandomDrawPurpose.DEZONE_STANCE;
            case 9732 -> RandomDrawPurpose.ATTACK_AIM_ZONE;
            case 9734 -> RandomDrawPurpose.ATTACK_AIM_ACCURACY;
            case 9735, 9736, 9737, 9738, 9740, 9741 -> RandomDrawPurpose.ATTACK_AIM_ERROR;
            case 9766 -> RandomDrawPurpose.ATTACK_MANIPULATION;
            case 9800, 9801 -> RandomDrawPurpose.GUARD_DIRECTION;
            case 9824 -> RandomDrawPurpose.GUARD_DURATION;
            case 9840, 9853 -> RandomDrawPurpose.FEINT_DECISION;
            case 9891 -> RandomDrawPurpose.FEINT_DURATION;
            case 9938, 9940 -> RandomDrawPurpose.ATTACK_AIM_ERROR;
            case 9967 -> RandomDrawPurpose.BAIT_DIRECTION;
            case 9968 -> RandomDrawPurpose.BAIT_DURATION;
            case 10069 -> RandomDrawPurpose.IDLE_MOVEMENT_DECISION;
            case 10203, 10204 -> RandomDrawPurpose.MOVEMENT_INTENT_NOISE;
            case 10207 -> RandomDrawPurpose.MOVEMENT_INTENT_DURATION;
            case 10964 -> RandomDrawPurpose.LOCAL_MANEUVER_DURATION;
            case 11465 -> RandomDrawPurpose.DAMAGE_ROLL;
            default -> null;
        };
    }
}
