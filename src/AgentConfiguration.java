record AgentConfiguration(String name, AgentStats stats, Tactic tactic) {
    AgentConfiguration {
        name = name == null ? "" : name.strip();
        if (name.isEmpty()) throw new IllegalArgumentException("Le nom du joueur est obligatoire.");
        if (name.length() > 18) throw new IllegalArgumentException("Le nom est limité à 18 caractères.");
        if (stats == null) throw new IllegalArgumentException("Les statistiques du joueur sont obligatoires.");
        if (tactic == null) throw new IllegalArgumentException("La tactique du joueur est obligatoire.");
    }

    static AgentConfiguration defaultBlue() {
        return new AgentConfiguration("Agent bleu 1", new AgentStats(78, 81, 72, 70, 64, 64, 58, 76, 74, 74, 62),
                Tactic.NEUTRAL);
    }

    static AgentConfiguration defaultBlueTwo() {
        return new AgentConfiguration("Agent bleu 2", new AgentStats(68, 76, 75, 66, 72, 71, 63, 74, 81, 67, 78),
                Tactic.DEFENSIVE);
    }

    static AgentConfiguration defaultRed() {
        return new AgentConfiguration("Agent rouge 1", new AgentStats(55, 62, 84, 68, 82, 82, 72, 65, 56, 93, 74),
                Tactic.NEUTRAL);
    }

    static AgentConfiguration defaultRedTwo() {
        return new AgentConfiguration("Agent rouge 2", new AgentStats(74, 69, 70, 79, 61, 68, 81, 73, 70, 64, 58),
                Tactic.AGGRESSIVE);
    }
}
