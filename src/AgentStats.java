record AgentStats(int footwork, int distanceControl, int aim, int attackTiming,
                  int defenseTiming, int reaction, int aggression, int decisionMaking, int zoneExit, int chamber,
                  int doubleBlock) {
    AgentStats {
        if (footwork < 1 || footwork > 100 || distanceControl < 1 || distanceControl > 100 ||
                aim < 1 || aim > 100 || attackTiming < 1 || attackTiming > 100 ||
                defenseTiming < 1 || defenseTiming > 100 || reaction < 1 || reaction > 100 ||
                aggression < 1 || aggression > 100 || decisionMaking < 1 || decisionMaking > 100 ||
                zoneExit < 1 || zoneExit > 100 || chamber < 1 || chamber > 100 ||
                doubleBlock < 1 || doubleBlock > 100)
            throw new IllegalArgumentException("Chaque statistique doit être comprise entre 1 et 100.");
    }

    int backProtection() {
        return (int) Math.round(footwork * 0.50 + reaction * 0.22 + decisionMaking * 0.18 + zoneExit * 0.10);
    }

    int peripheralAwareness() {
        return (int) Math.round(reaction * 0.45 + decisionMaking * 0.35 + distanceControl * 0.20);
    }

    int doubleBlockAbility() {
        return (int) Math.round(doubleBlock * 0.50 + defenseTiming * 0.225 + reaction * 0.15 +
                decisionMaking * 0.075 + footwork * 0.05);
    }

    int targetSwitchDezoneAbility() {
        return (int) Math.round(zoneExit * 0.35 + decisionMaking * 0.25 + footwork * 0.20 +
                reaction * 0.10 + aggression * 0.10);
    }

    int engagementAbility() {
        return (int) Math.round(footwork * 0.35 + distanceControl * 0.25 + decisionMaking * 0.25 +
                aggression * 0.15);
    }

    int feintAbility() {
        return (int) Math.round(decisionMaking * 0.35 + attackTiming * 0.30 + aggression * 0.20 + aim * 0.15);
    }

    int manipulationAbility() {
        return (int) Math.round(attackTiming * 0.35 + aim * 0.25 + footwork * 0.20 + decisionMaking * 0.20);
    }
}
