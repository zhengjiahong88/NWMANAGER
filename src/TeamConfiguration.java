record TeamConfiguration(String name, AgentConfiguration firstPlayer, AgentConfiguration secondPlayer) {
    TeamConfiguration {
        name = name == null ? "" : name.strip();
        if (name.isEmpty()) throw new IllegalArgumentException("Le nom de l'équipe est obligatoire.");
        if (name.length() > 24) throw new IllegalArgumentException("Le nom de l'équipe est limité à 24 caractères.");
        if (firstPlayer == null || secondPlayer == null)
            throw new IllegalArgumentException("Une équipe doit contenir deux joueurs.");
    }
}
