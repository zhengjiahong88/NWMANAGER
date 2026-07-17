enum Tactic {
    DEFENSIVE("Défensive", "防守", 0.45, 0.45, 1.35, 0.70, 25, -10, -25, -35, 1.95, 1.65),
    NEUTRAL("Neutre", "中立", 1.00, 1.00, 1.00, 1.00, 0, 0, 0, 0, 1.78, 1.45),
    AGGRESSIVE("Agressive", "進攻", 1.00, 1.80, 0.75, 1.35, -10, 15, 25, 35, 1.55, 1.25);

    private final String frenchLabel, chineseLabel;
    final double baseChamberMultiplier, attackMultiplier, dezoneMultiplier, baitMultiplier, approachDistance,
            holdAdvanceDistance;
    final int defenseCommitmentBonus, releaseBonus, followBonus, pressureBonus;

    Tactic(String frenchLabel, String chineseLabel, double baseChamberMultiplier, double attackMultiplier,
           double dezoneMultiplier, double baitMultiplier, int defenseCommitmentBonus, int releaseBonus,
           int followBonus, int pressureBonus, double approachDistance, double holdAdvanceDistance) {
        this.frenchLabel = frenchLabel;
        this.chineseLabel = chineseLabel;
        this.baseChamberMultiplier = baseChamberMultiplier;
        this.attackMultiplier = attackMultiplier;
        this.dezoneMultiplier = dezoneMultiplier;
        this.baitMultiplier = baitMultiplier;
        this.defenseCommitmentBonus = defenseCommitmentBonus;
        this.releaseBonus = releaseBonus;
        this.followBonus = followBonus;
        this.pressureBonus = pressureBonus;
        this.approachDistance = approachDistance;
        this.holdAdvanceDistance = holdAdvanceDistance;
    }

    double chamberMultiplier(boolean readableAttack) {
        return this == AGGRESSIVE && readableAttack ? 1.30 : baseChamberMultiplier;
    }

    String label(UiLanguage language) {
        return language.text(frenchLabel, chineseLabel);
    }

    @Override
    public String toString() {
        return frenchLabel;
    }
}
