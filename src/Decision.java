enum Decision {
    WAIT("Attendre", "等待"), APPROACH("Avancer", "前進"), RETREAT("Reculer", "後退"),
    RECENTER("Revenir au centre", "回到場中央"), STRAFE_LEFT("Contourner à gauche", "向左繞行"),
    STRAFE_RIGHT("Contourner à droite", "向右繞行"), PREPARE_ATTACK("Préparer l'attaque", "準備攻擊"),
    EDGE_ESCAPE_LEFT("Sortie du bord : gauche", "沿邊脫困：左"),
    EDGE_ESCAPE_RIGHT("Sortie du bord : droite", "沿邊脫困：右"),
    RELEASE_ATTACK("Libérer l'attaque", "釋放攻擊"), BLOCK("Parer", "格擋"),
    CHAMBER("Chamber", "接招反擊"), BAIT_LEFT("Appât à gauche", "向左誘敵"),
    BAIT_RIGHT("Appât à droite", "向右誘敵"), FALL_FOR_BAIT("Piégé : estoc trop tôt", "中計：過早刺擊"),
    PUNISH_BAIT("Punition de l'appât", "懲罰中計"), EXPLOIT_BAIT("Exploiter l'appât", "識破誘敵：前壓"),
    DEZONE_BACKWARD("Dézonage arrière", "後退脫離"),
    DEZONE_ADVANCE_LEFT("Dézonage avant gauche", "左前脫離"),
    DEZONE_ADVANCE_RIGHT("Dézonage avant droit", "右前脫離"),
    DEZONE_ORBIT_LEFT("Dézonage en orbite gauche", "向左繞行脫離"),
    DEZONE_ORBIT_RIGHT("Dézonage en orbite droite", "向右繞行脫離"),
    DEZONE_SWITCH_LEFT("Dézonage de cible : gauche", "換目標脫離：左"),
    DEZONE_SWITCH_RIGHT("Dézonage de cible : droite", "換目標脫離：右"),
    DEZONE_SWITCH_ATTACK("Stab après changement de cible", "換目標後刺擊"),
    DEZONE_SWITCH_ABORT("Abandonner le changement de cible", "中止換目標"),
    PROBE_LEFT("Observer en garde : gauche", "防守試探：左"),
    PROBE_RIGHT("Observer en garde : droite", "防守試探：右"),
    COMMIT_LEFT("Entrée oblique : gauche", "斜向進攻：左"),
    COMMIT_RIGHT("Entrée oblique : droite", "斜向進攻：右"),
    PASS_LEFT("Traverser : gauche", "穿越交鋒：左"),
    PASS_RIGHT("Traverser : droite", "穿越交鋒：右"),
    DISENGAGE("Sortir de l'échange", "脫離交鋒"), RESET_GUARD("Reconstruire la distance", "重整距離"),
    FEINT_ATTACK("Feinte d'attaque", "攻擊變招"), FEINT_TO_BLOCK("Feinte vers parade", "變招轉格擋"),
    SWITCH_GUARD("Changer la parade", "切換格擋方向"), RIPOSTE("Riposte préparée", "準備反擊"),
    PROTECT_BACK_LEFT("Protéger le dos : gauche", "護背：左前移"),
    PROTECT_BACK_RIGHT("Protéger le dos : droite", "護背：右前移"),
    FREE_KILL_BACKSTAB("Opportunité de backstab", "背刺機會"),
    DOUBLE_BLOCK("Double parade", "雙重格擋");

    private final String frenchLabel, chineseLabel;

    Decision(String frenchLabel, String chineseLabel) {
        this.frenchLabel = frenchLabel;
        this.chineseLabel = chineseLabel;
    }

    String label(UiLanguage language) {
        return language.text(frenchLabel, chineseLabel);
    }
}
