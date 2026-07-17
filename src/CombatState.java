enum CombatState {
    NEUTRAL("Neutre", "待命"), ATTACK_PREPARE("Préparation", "準備攻擊"),
    ATTACK_HOLD("Attaque maintenue", "保持攻擊"), ATTACK_ACTIVE("Estoc", "刺擊"),
    CHAMBER_PREPARE("Interception chamber", "準備接招反擊"),
    CHAMBER_HOLD("Retour chamber", "保持接招反擊"), CHAMBER_ACTIVE("Retour chamber", "接招反擊刺擊"),
    FEINT("Transition de feinte", "變招過渡"), BLOCK_PREPARE("Préparation de parade", "準備格擋"),
    BLOCK_STUN("Étourdi par parade", "格擋僵直"),
    RECOVERY("Récupération", "攻擊硬直"), BLOCKING("Parade maintenue", "保持格擋"),
    DOUBLE_BLOCK("Double parade", "雙重格擋"), DEAD("Mort", "死亡");

    private final String frenchLabel, chineseLabel;

    CombatState(String frenchLabel, String chineseLabel) {
        this.frenchLabel = frenchLabel;
        this.chineseLabel = chineseLabel;
    }

    String label(UiLanguage language) {
        return language.text(frenchLabel, chineseLabel);
    }
}
