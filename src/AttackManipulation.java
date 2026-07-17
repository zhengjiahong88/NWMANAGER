enum AttackManipulation {
    NEUTRAL("Neutre", "一般"), ACCEL("Accel", "加速刺擊"), DRAG("Drag", "延遲刺擊");

    private final String frenchLabel, chineseLabel;

    AttackManipulation(String frenchLabel, String chineseLabel) {
        this.frenchLabel = frenchLabel;
        this.chineseLabel = chineseLabel;
    }

    String label(UiLanguage language) {
        return language.text(frenchLabel, chineseLabel);
    }
}
