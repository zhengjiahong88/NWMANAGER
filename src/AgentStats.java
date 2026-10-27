record AgentStats(int footwork, int distanceControl, int aim, int attackTiming,
                  int defenseTiming, int reaction, int aggression, int decisionMaking, int zoneExit, int chamber,
                  int doubleBlock, int pressure, int fiftyFifty, int teamwork) {
    AgentStats {
        if (footwork < 1 || footwork > 100 || distanceControl < 1 || distanceControl > 100 ||
                aim < 1 || aim > 100 || attackTiming < 1 || attackTiming > 100 ||
                defenseTiming < 1 || defenseTiming > 100 || reaction < 1 || reaction > 100 ||
                aggression < 1 || aggression > 100 || decisionMaking < 1 || decisionMaking > 100 ||
                zoneExit < 1 || zoneExit > 100 || chamber < 1 || chamber > 100 ||
                doubleBlock < 1 || doubleBlock > 100 || pressure < 1 || pressure > 100 ||
                fiftyFifty < 1 || fiftyFifty > 100 || teamwork < 1 || teamwork > 100)
            throw new IllegalArgumentException("Chaque statistique doit être comprise entre 1 et 100.");
    }

    int backProtection() {
        return (int) Math.round(footwork * 0.55 + reaction * 0.25 + decisionMaking * 0.20);
    }

    int peripheralAwareness() {
        return (int) Math.round(reaction * 0.45 + decisionMaking * 0.35 + distanceControl * 0.20);
    }

    int doubleBlockAbility() {
        return (int) Math.round(doubleBlock * 0.50 + defenseTiming * 0.225 + reaction * 0.15 +
                decisionMaking * 0.075 + footwork * 0.05);
    }

    int targetSwitchDezoneAbility() {
        return (int) Math.round(decisionMaking * 0.35 + footwork * 0.30 + reaction * 0.20 +
                distanceControl * 0.15);
    }

    int engagementAbility() {
        return (int) Math.round(footwork * 0.40 + distanceControl * 0.25 + decisionMaking * 0.25 +
                reaction * 0.10);
    }

    int feintAbility() {
        return (int) Math.round(decisionMaking * 0.40 + attackTiming * 0.35 + aim * 0.25);
    }

    int manipulationAbility() {
        return (int) Math.round(attackTiming * 0.35 + aim * 0.25 + footwork * 0.20 + decisionMaking * 0.20);
    }

    int pressureAbility() {
        return (int) Math.round(pressure * 0.50 + decisionMaking * 0.20 + footwork * 0.15 +
                distanceControl * 0.10 + aim * 0.05);
    }

    int teamworkAbility() {
        return (int) Math.round(teamwork * 0.55 + decisionMaking * 0.25 + footwork * 0.15 +
                reaction * 0.05);
    }

    int technicalRating() {
        return technicalRating(RatingProfile.LEGACY);
    }

    int technicalRating(RatingProfile profile) {
        if (profile == RatingProfile.MODERN)
            return (int) Math.round((footwork + distanceControl + attackTiming + defenseTiming + chamber) / 5.0);
        return (int) Math.round((footwork + distanceControl + attackTiming + defenseTiming + chamber +
                doubleBlock) / 6.0);
    }

    int mentalRating() {
        return mentalRating(RatingProfile.LEGACY);
    }

    int mentalRating(RatingProfile profile) {
        if (profile == RatingProfile.MODERN)
            return (int) Math.round((aim + reaction + decisionMaking + teamwork) / 4.0);
        return (int) Math.round((aim + reaction + decisionMaking + pressure + teamwork) / 5.0);
    }

    int overallRating() {
        return overallRating(RatingProfile.LEGACY);
    }

    int overallRating(RatingProfile profile) {
        var technicalMinimum = profile == RatingProfile.MODERN ?
                Math.min(Math.min(footwork, distanceControl),
                        Math.min(Math.min(attackTiming, defenseTiming), chamber)) :
                Math.min(Math.min(Math.min(footwork, distanceControl),
                        Math.min(attackTiming, defenseTiming)), Math.min(chamber, doubleBlock));
        var weaknessPenalty = Math.max(0, 70 - technicalMinimum) * 0.15;
        return (int) Math.round(Math.clamp(technicalRating(profile) * 0.65 +
                mentalRating(profile) * 0.35 -
                weaknessPenalty, 1, 100));
    }

    PlayerLevel playerLevel() {
        return playerLevel(RatingProfile.LEGACY);
    }

    PlayerLevel playerLevel(RatingProfile profile) {
        var overall = overallRating(profile);
        var technical = technicalRating(profile);
        var mental = mentalRating(profile);
        if (overall >= 90 && technical >= 90 && mental >= 85 &&
                footwork >= 80 && distanceControl >= 80 && attackTiming >= 80 && defenseTiming >= 80 &&
                chamber >= 80 && (profile == RatingProfile.MODERN || doubleBlock >= 80))
            return PlayerLevel.TOP_PLAYER;
        if (overall >= 80 && technical >= 80 && mental >= 75 &&
                footwork >= 65 && distanceControl >= 65 && attackTiming >= 65 && defenseTiming >= 65 &&
                chamber >= 65 && (profile == RatingProfile.MODERN || doubleBlock >= 65))
            return PlayerLevel.CONTENDER;
        return PlayerLevel.forRating(Math.min(overall, 79));
    }
}
