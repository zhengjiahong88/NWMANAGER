record AgentConfiguration(String name, AgentStats stats, Tactic tactic) {
    AgentConfiguration {
        name = name == null ? "" : name.strip();
        if (name.isEmpty()) throw new IllegalArgumentException("Le nom du joueur est obligatoire.");
        if (name.length() > 18) throw new IllegalArgumentException("Le nom est limité à 18 caractères.");
        if (stats == null) throw new IllegalArgumentException("Les statistiques du joueur sont obligatoires.");
        if (tactic == null) throw new IllegalArgumentException("La tactique du joueur est obligatoire.");
    }

    static AgentConfiguration defaultBlue() {
        return new AgentConfiguration("Agent bleu 1", new AgentStats(90, 90, 90, 90, 90, 90, 90, 90, 90, 90, 90, 90, 25, 75), Tactic.NEUTRAL);
    }

    static AgentConfiguration defaultBlueTwo() {
        return new AgentConfiguration("Agent bleu 2", new AgentStats(90, 90, 90, 90, 90, 90, 90, 90, 90, 90, 90, 90, 25, 75), Tactic.DEFENSIVE);
    }

    static AgentConfiguration defaultBlueThree() {
        return new AgentConfiguration("Agent bleu 3", new AgentStats(90, 90, 90, 90, 90, 90, 90, 90, 90, 90, 90, 90, 25, 75), Tactic.NEUTRAL);
    }

    static AgentConfiguration defaultRed() {
        return new AgentConfiguration("Agent rouge 1", new AgentStats(90, 90, 90, 90, 90, 90, 90, 90, 90, 90, 90, 90, 25, 75), Tactic.NEUTRAL);
    }

    static AgentConfiguration defaultRedTwo() {
        return new AgentConfiguration("Agent rouge 2", new AgentStats(90, 90, 90, 90, 90, 90, 90, 90, 90, 90, 90, 90, 25, 75), Tactic.AGGRESSIVE);
    }

    static AgentConfiguration defaultRedThree() {
        return new AgentConfiguration("Agent rouge 3", new AgentStats(90, 90, 90, 90, 90, 90, 90, 90, 90, 90, 90, 90, 25, 75), Tactic.DEFENSIVE);
    }
}
