record TeamConfiguration3(String name, AgentConfiguration firstPlayer,
                          AgentConfiguration secondPlayer, AgentConfiguration thirdPlayer) {
    TeamConfiguration3 {
        name = name == null ? "" : name.strip();
        if (name.isEmpty()) throw new IllegalArgumentException("Le nom de l'équipe est obligatoire.");
        if (name.length() > 24)
            throw new IllegalArgumentException("Le nom de l'équipe est limité à 24 caractères.");
        if (firstPlayer == null || secondPlayer == null || thirdPlayer == null)
            throw new IllegalArgumentException("Une équipe 3v3 doit contenir trois joueurs.");
    }

    AgentConfiguration[] players() {
        return new AgentConfiguration[]{firstPlayer, secondPlayer, thirdPlayer};
    }
}
