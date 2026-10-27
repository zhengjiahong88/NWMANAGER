import java.util.List;

final class DecisionPointCatalog {
    List<DecisionPointDescriptor> entries() {
        return List.of(
                new DecisionPointDescriptor(DecisionPointType.TARGET_SELECTION,
                        "selectTarget / updateGroup3Attention",
                        "Sélection séparée, avec verrouillage, attention et perception avant l'action.",
                        List.of("cible actuelle", "meilleur adversaire perçu", "switch différé")),
                new DecisionPointDescriptor(DecisionPointType.LOCAL_TACTICAL_ROLE,
                        "updateLocalCoordination / updateTwoVsOneCoordination",
                        "Le rôle est attribué avant les décisions individuelles et limite leurs branches.",
                        List.of("FREE", "FIXER", "FLANKER", "SURVIVE", "COVER")),
                new DecisionPointDescriptor(DecisionPointType.DEFENSIVE_RESPONSE,
                        "chooseDecision / maintainPressureGuard / updateGroup3Defense",
                        "La défense est une branche prioritaire; elle peut empêcher la génération d'attaques.",
                        List.of("parade", "Chamber", "Double Block", "sortie de pression")),
                new DecisionPointDescriptor(DecisionPointType.WEAPON_ACTION,
                        "chooseDecision / chooseOpening / tryBeginPressureAttack",
                        "Plusieurs seuils et retours anticipés successifs, pas un score global unique.",
                        List.of("attaque", "Hold", "appât", "feinte", "attente")),
                new DecisionPointDescriptor(DecisionPointType.ATTACK_HEIGHT,
                        "chooseAttackDirection",
                        "La hauteur est choisie après l'intention d'attaquer.",
                        List.of("haute", "basse")),
                new DecisionPointDescriptor(DecisionPointType.HOLD_OR_RELEASE,
                        "maintainPressureAttack / chooseOffAxisHoldAction / updateState",
                        "Le maintien, réalignement, release et abandon sont évalués après la préparation.",
                        List.of("Hold", "réalignement", "release", "release imparfaite", "abandon")),
                new DecisionPointDescriptor(DecisionPointType.MOVEMENT,
                        "updateMovementIntent / chooseModernGroupDecision",
                        "Le déplacement est composé après les choix tactiques et d'arme.",
                        List.of("avance", "recul", "strafe", "dézonage", "destination tactique"))
        );
    }
}
