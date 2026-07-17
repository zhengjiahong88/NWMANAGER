import java.awt.*;
import java.awt.geom.Path2D;
import java.util.IdentityHashMap;

final class Musketeer {
    static final double BODY_RADIUS = 0.32, WEAPON_REST = 0.92;
    static final double MAX_PITCH = Math.toRadians(35), MAX_WEAPON_YAW = Math.toRadians(50);
    static final double BLOCK_SPARK_DURATION = 0.22;
    private static final Color NORMAL_WEAPON_COLOR = new Color(185, 190, 198),
            ATTACK_READY_COLOR = new Color(248, 205, 76), ACTIVE_ATTACK_COLOR = new Color(255, 224, 42),
            ATTACK_RECOVERY_COLOR = new Color(198, 126, 35), BLOCK_GLOW_COLOR = new Color(48, 220, 235);

    private final String frenchName, chineseName;
    final int team;
    final Color color;
    final AgentStats stats;
    final Tactic tactic;
    double x, y, previousX, previousY, velocityX, velocityY, movementHeading, angle, pitch, desiredPitch,
            attackPitch, baseAttackPitch, weaponYaw, desiredWeaponYaw, previousTipX, previousTipY,
            manipulationPitchOffset, manipulationYawOffset, baitOffset, baitTime;
    double stateTime, decisionTime, reactionTime, aimError, attackReachScale = 1, engagementTime,
            feintDuration, feintCooldown, guardPrepareTime, guardHoldTime, guardSwitchCooldown;
    CombatState state = CombatState.NEUTRAL;
    Decision decision = Decision.WAIT;
    CombatDirection attackDirection = CombatDirection.UP, queuedAttackDirection = CombatDirection.DOWN,
            lastCommittedAttackDirection;
    BlockDirection blockDirection = BlockDirection.UP;
    EngagementPhase engagementPhase = EngagementPhase.PROBE;
    AttackManipulation attackManipulation = AttackManipulation.NEUTRAL;
    HitZone intendedZone = HitZone.TORSO, lastHitZone;
    ChamberType chamberType = ChamberType.NONE, chamberMessageType = ChamberType.NONE;
    double attackHeight = HitZone.TORSO.centerHeight();
    int hitPoints = 100, lastDamage;
    double hitMessageTime, chamberMessageTime, chamberTimingError, baitTrapMessageTime, punishMessageTime,
            baitAdvanceMessageTime, blockSparkTime, blockSparkX, blockSparkY, backstabMessageTime,
            doubleBlockSwitchTime, doubleBlockMessageTime;
    double dezoneTime, dezoneCooldown, dezoneDangerTime, targetLockTime, rearReactionTime, backEscapeTime,
            perceptionTime, freeKillPerceivedDistance;
    int baitSequence, observedBaitSequence, chamberChain, chamberMessageChain, attackSequence, blockSequence,
            directionStreak, engagementSide = 1, feintCount;
    DezoneMovement dezoneMovement = DezoneMovement.NONE;
    DezoneStance dezoneStance = DezoneStance.NEUTRAL;
    boolean alive = true, threatSeen, attackResolved, simpleChamberLethal, baitedAttack, punishingBait,
            exploitingBait, backExposed, freeKillAttempt, doubleBlockSwitched, dezoneSwitchCommitted,
            feintToBlock, guardSwitching;
    int backEscapeDirection = 1;
    BlockDirection doubleBlockSecondDirection = BlockDirection.DOWN;
    Musketeer target, rearThreat, doubleBlockThreat, dezoneOriginalTarget, dezoneSwitchTarget;
    final IdentityHashMap<Musketeer, OpponentAwareness> opponentAwareness = new IdentityHashMap<>();

    Musketeer(String frenchName, String chineseName, int team, Color color, AgentStats stats, Tactic tactic,
              double x, double y, double angle) {
        this.frenchName = frenchName;
        this.chineseName = chineseName;
        this.team = team;
        this.color = color;
        this.stats = stats;
        this.tactic = tactic;
        this.x = x;
        this.y = y;
        this.angle = angle;
        previousX = x;
        previousY = y;
        movementHeading = angle;
        previousTipX = tipX();
        previousTipY = tipY();
    }

    double weaponLength() {
        var weaponPitch = isAttacking() || state == CombatState.ATTACK_PREPARE || state == CombatState.ATTACK_HOLD ||
                state == CombatState.CHAMBER_PREPARE || state == CombatState.CHAMBER_HOLD ||
                state == CombatState.RECOVERY ? attackPitch : pitch;
        var restLength = WEAPON_REST * Math.cos(weaponPitch);
        if (isAttacking()) {
            var progress = Math.min(1.0, stateTime / activeTime());
            return restLength + (projectedThrustLength() - restLength) * Math.sin(progress * Math.PI);
        }
        return state == CombatState.ATTACK_PREPARE || state == CombatState.CHAMBER_PREPARE ?
                0.76 * Math.cos(weaponPitch) : restLength;
    }

    boolean isAttacking() {
        return state == CombatState.ATTACK_ACTIVE || state == CombatState.CHAMBER_ACTIVE;
    }

    boolean isBlocking() {
        return state == CombatState.BLOCKING || state == CombatState.DOUBLE_BLOCK;
    }

    boolean isGuarding() {
        return state == CombatState.BLOCK_PREPARE || isBlocking();
    }

    double projectedThrustLength() {
        return attackDirection.thrustLength * Math.cos(attackPitch) * attackReachScale;
    }

    double prepareTime() {
        return attackDirection.prepareTime * timingFactor();
    }

    double activeTime() {
        return attackDirection.activeTime * timingFactor();
    }

    double recoveryTime() {
        return attackDirection.recoveryTime * timingFactor() * (baitedAttack ? 1.55 : 1);
    }

    private double timingFactor() {
        return 1 + 0.35 * Math.clamp(attackPitch / MAX_PITCH, -1, 1);
    }

    double weaponOffset() {
        if (state != CombatState.ATTACK_PREPARE && state != CombatState.ATTACK_HOLD &&
                state != CombatState.CHAMBER_PREPARE && state != CombatState.CHAMBER_HOLD &&
                !isAttacking() && state != CombatState.RECOVERY) return 0;
        return attackDirection == CombatDirection.UP ? -0.08 : 0.08;
    }

    double tipX() {
        return x + Math.cos(weaponWorldAngle()) * weaponLength() - Math.sin(weaponWorldAngle()) * weaponOffset();
    }

    double tipY() {
        return y + Math.sin(weaponWorldAngle()) * weaponLength() + Math.cos(weaponWorldAngle()) * weaponOffset();
    }

    double weaponWorldAngle() {
        return Math.atan2(Math.sin(angle + weaponYaw), Math.cos(angle + weaponYaw));
    }

    String name(UiLanguage language) {
        return language.text(frenchName, chineseName);
    }

    void paint(Graphics2D g, double scale, double originX, double originY, UiLanguage language) {
        var transform = g.getTransform();
        var originalStroke = g.getStroke();
        g.translate(originX + x * scale, originY + y * scale);
        var centerTransform = g.getTransform();
        paintVelocity(g, scale);
        g.setTransform(centerTransform);
        g.rotate(angle + weaponYaw);
        paintAttackRange(g, scale);
        g.setTransform(centerTransform);
        g.rotate(angle);
        paintBackExposure(g, scale);
        var radius = BODY_RADIUS * scale;

        var bodyTransform = g.getTransform();
        g.rotate(weaponYaw);
        paintWeapon(g, scale);
        g.setTransform(bodyTransform);

        g.setColor(alive ? color : new Color(90, 90, 90));
        g.fillOval((int) -radius, (int) -radius, (int) (radius * 2), (int) (radius * 2));
        g.setColor(Color.WHITE);
        g.drawLine(0, 0, (int) (radius * 0.9), 0);
        g.setColor(new Color(35, 35, 35));
        var arc = new Path2D.Double();
        for (var degrees = -55; degrees <= 55; degrees += 2) {
            var angle = Math.toRadians(degrees);
            var arcX = Math.cos(angle) * radius;
            var arcY = Math.sin(angle) * radius;
            if (degrees == -55) arc.moveTo(arcX, arcY);
            else arc.lineTo(arcX, arcY);
        }
        g.draw(arc);
        g.setColor(new Color(255, 238, 205, 210));
        g.fillOval((int) (radius * 0.25), (int) (-radius * 0.28), (int) (radius * 0.52), (int) (radius * 0.52));
        g.setColor(new Color(35, 35, 35, 150));
        g.drawLine((int) (-radius * 0.42), (int) (-radius * 0.30), (int) (-radius * 0.42), (int) (radius * 0.30));
        g.setTransform(transform);
        paintAttackSweep(g, scale, originX, originY);
        paintDirectionBadge(g, scale, originX + x * scale, originY + y * scale);
        paintBasicCombatMessage(g, scale, originX + x * scale, originY + y * scale, language);
        paintDoubleBlockMessage(g, scale, originX + x * scale, originY + y * scale, language);
        paintHitMessage(g, scale, originX + x * scale, originY + y * scale, language);
        paintChamberMessage(g, scale, originX + x * scale, originY + y * scale, language);
        paintBaitMessage(g, scale, originX + x * scale, originY + y * scale, language);
        paintBaitOutcomeMessage(g, scale, originX + x * scale, originY + y * scale, language);
        paintDezoneMessage(g, scale, originX + x * scale, originY + y * scale, language);
        paintBackWarning(g, scale, originX + x * scale, originY + y * scale, language);
        paintBlockSpark(g, scale, originX, originY);
        g.setStroke(originalStroke);
    }

    private void paintAttackSweep(Graphics2D g, double scale, double originX, double originY) {
        if (!alive || !isAttacking()) return;
        g.setStroke(new BasicStroke((float) Math.max(1.4, scale * 0.022), BasicStroke.CAP_ROUND,
                BasicStroke.JOIN_ROUND));
        g.setColor(new Color(255, 202, 44, 92));
        g.drawLine((int) (originX + previousTipX * scale), (int) (originY + previousTipY * scale),
                (int) (originX + tipX() * scale), (int) (originY + tipY() * scale));
    }

    private void paintVelocity(Graphics2D g, double scale) {
        var speed = Math.hypot(velocityX, velocityY);
        if (!alive || speed < 0.12) return;
        g.rotate(movementHeading);
        g.setStroke(new BasicStroke((float) Math.max(1.2, scale * 0.018), BasicStroke.CAP_ROUND,
                BasicStroke.JOIN_ROUND));
        g.setColor(new Color(57, 132, 92, 105));
        var length = (int) (Math.min(0.62, speed * 0.32) * scale);
        g.drawLine(0, 0, length, 0);
        g.drawLine(length, 0, length - 7, -5);
        g.drawLine(length, 0, length - 7, 5);
    }

    private void paintAttackRange(Graphics2D g, double scale) {
        if (!alive || !isBasicAttack() || state != CombatState.ATTACK_PREPARE && state != CombatState.ATTACK_HOLD)
            return;
        var radius = (projectedThrustLength() + BODY_RADIUS) * scale;
        var pulse = 0.5 + 0.5 * Math.sin(stateTime * 10);
        g.setColor(new Color(246, 195, 42, 30 + (int) (pulse * 28)));
        g.setStroke(new BasicStroke(1.25f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND,
                0, new float[]{5, 7}, 0));
        g.drawOval((int) -radius, (int) -radius, (int) (radius * 2), (int) (radius * 2));
    }

    private void paintWeapon(Graphics2D g, double scale) {
        var baseWidth = (float) Math.max(2, scale * 0.035);
        if (isGuarding()) {
            g.setStroke(new BasicStroke(baseWidth * 3.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.setColor(new Color(BLOCK_GLOW_COLOR.getRed(), BLOCK_GLOW_COLOR.getGreen(),
                    BLOCK_GLOW_COLOR.getBlue(), state == CombatState.BLOCK_PREPARE ? 62 : 118));
            drawBlockWeapon(g, scale);
            g.setStroke(new BasicStroke(baseWidth * 1.35f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.setColor(blockDirection.color);
            drawBlockWeapon(g, scale);
            return;
        }

        var offset = (int) (weaponOffset() * scale);
        var start = (int) (0.60 * scale);
        var end = (int) (weaponLength() * scale);
        g.setStroke(new BasicStroke(baseWidth, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(new Color(55, 45, 35));
        g.drawLine(0, offset, end, offset);
        if (isBasicAttack() && (state == CombatState.ATTACK_PREPARE || state == CombatState.ATTACK_HOLD ||
                state == CombatState.ATTACK_ACTIVE || state == CombatState.RECOVERY)) {
            var alpha = state == CombatState.ATTACK_ACTIVE ? 165 : state == CombatState.RECOVERY ? 62 :
                    70 + (int) ((0.5 + 0.5 * Math.sin(stateTime * 10)) * 58);
            var glow = state == CombatState.RECOVERY ? ATTACK_RECOVERY_COLOR : ACTIVE_ATTACK_COLOR;
            g.setStroke(new BasicStroke(baseWidth * (state == CombatState.ATTACK_ACTIVE ? 3.8f : 2.7f),
                    BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.setColor(new Color(glow.getRed(), glow.getGreen(), glow.getBlue(), alpha));
            g.drawLine(start, offset, end, offset);
        }
        g.setStroke(new BasicStroke(baseWidth * (state == CombatState.ATTACK_ACTIVE && isBasicAttack() ? 1.55f : 1),
                BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(weaponColor());
        g.drawLine(start, offset, end, offset);
    }

    private void drawBlockWeapon(Graphics2D g, double scale) {
        if (blockDirection == BlockDirection.UP)
            g.drawLine((int) (0.12 * scale), (int) (0.48 * scale),
                    (int) (0.60 * scale), (int) (-0.43 * scale));
        else if (blockDirection == BlockDirection.DOWN)
            g.drawLine((int) (0.12 * scale), (int) (-0.48 * scale),
                    (int) (0.60 * scale), (int) (0.43 * scale));
        else g.drawLine((int) (0.38 * scale), (int) (-0.52 * scale),
                    (int) (0.38 * scale), (int) (0.52 * scale));
    }

    private Color weaponColor() {
        if (!isBasicAttack()) return state == CombatState.NEUTRAL ? NORMAL_WEAPON_COLOR : attackDirection.color;
        return switch (state) {
            case ATTACK_PREPARE, ATTACK_HOLD, FEINT -> ATTACK_READY_COLOR;
            case ATTACK_ACTIVE -> ACTIVE_ATTACK_COLOR;
            case RECOVERY -> ATTACK_RECOVERY_COLOR;
            default -> NORMAL_WEAPON_COLOR;
        };
    }

    private boolean isBasicAttack() {
        return chamberType == ChamberType.NONE && !baitedAttack && !punishingBait && !exploitingBait;
    }

    private void paintBackExposure(Graphics2D g, double scale) {
        if (!alive || !backExposed) return;
        var radius = (BODY_RADIUS + 0.10) * scale;
        var arc = new Path2D.Double();
        for (var degrees = 115; degrees <= 245; degrees += 3) {
            var angle = Math.toRadians(degrees);
            var arcX = Math.cos(angle) * radius;
            var arcY = Math.sin(angle) * radius;
            if (degrees == 115) arc.moveTo(arcX, arcY);
            else arc.lineTo(arcX, arcY);
        }
        g.setStroke(new BasicStroke((float) Math.max(2.5, scale * 0.045),
                BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(new Color(215, 43, 43, 175));
        g.draw(arc);
    }

    private void paintDirectionBadge(Graphics2D g, double scale, double centerX, double centerY) {
        String symbol;
        Color badgeColor;
        if (state == CombatState.DOUBLE_BLOCK) {
            symbol = "⇅";
            badgeColor = new Color(145, 91, 181);
        } else if (isGuarding()) {
            symbol = blockDirection.symbol;
            badgeColor = blockDirection.color;
        } else if (state == CombatState.FEINT || state == CombatState.ATTACK_PREPARE || state == CombatState.ATTACK_HOLD ||
                state == CombatState.CHAMBER_PREPARE || state == CombatState.CHAMBER_HOLD || isAttacking() ||
                state == CombatState.RECOVERY) {
            symbol = attackDirection.symbol;
            badgeColor = attackDirection.color;
        } else if (state == CombatState.BLOCK_STUN) {
            symbol = "✕";
            badgeColor = new Color(190, 45, 45);
        } else return;
        g.setColor(badgeColor);
        g.setFont(g.getFont().deriveFont(Font.BOLD, (float) Math.max(16, scale * 0.28)));
        var metrics = g.getFontMetrics();
        g.drawString(symbol, (float) (centerX - metrics.stringWidth(symbol) / 2.0),
                (float) (centerY - BODY_RADIUS * scale - 7));
    }

    private void paintBasicCombatMessage(Graphics2D g, double scale, double centerX, double centerY,
                                         UiLanguage language) {
        if (chamberMessageTime > 0 || baitTime > 0 || baitTrapMessageTime > 0 || punishMessageTime > 0 ||
                baitAdvanceMessageTime > 0 || dezoneTime > 0 || backstabMessageTime > 0 ||
                doubleBlockMessageTime > 0) return;
        String message;
        Color messageColor;
        if (state == CombatState.FEINT) {
            message = language.text("FEINTE", "變招");
            messageColor = new Color(151, 91, 181);
        } else if (isBasicAttack() && (state == CombatState.ATTACK_PREPARE || state == CombatState.ATTACK_HOLD)) {
            message = language.text("PRÊT", "準備");
            messageColor = ATTACK_READY_COLOR;
        } else if (isBasicAttack() && state == CombatState.ATTACK_ACTIVE) {
            message = language.text("ATTAQUE", "攻擊") + (attackManipulation == AttackManipulation.NEUTRAL ? "" :
                    " · " + attackManipulation.label(language).toUpperCase());
            messageColor = ACTIVE_ATTACK_COLOR;
        } else if (state == CombatState.DOUBLE_BLOCK) {
            message = language.text("DOUBLE PARADE", "雙重格擋");
            messageColor = new Color(145, 91, 181);
        } else if (isGuarding()) {
            message = state == CombatState.BLOCK_PREPARE ? language.text("PARADE…", "準備格擋…") :
                    language.text("PARADE", "格擋");
            messageColor = BLOCK_GLOW_COLOR;
        } else if (isBasicAttack() && state == CombatState.RECOVERY) {
            message = language.text("RÉCUP.", "恢復");
            messageColor = ATTACK_RECOVERY_COLOR;
        } else return;
        g.setFont(g.getFont().deriveFont(Font.BOLD, 13f));
        g.setColor(messageColor);
        g.drawString(message, (float) (centerX - g.getFontMetrics().stringWidth(message) / 2.0),
                (float) (centerY - BODY_RADIUS * scale - 28));
    }

    private void paintDoubleBlockMessage(Graphics2D g, double scale, double centerX, double centerY,
                                         UiLanguage language) {
        if (doubleBlockMessageTime <= 0) return;
        var message = language.text("DOUBLE BLOCK !", "雙重格擋！");
        g.setFont(g.getFont().deriveFont(Font.BOLD, 16f));
        g.setColor(new Color(145, 91, 181));
        g.drawString(message, (float) (centerX - g.getFontMetrics().stringWidth(message) / 2.0),
                (float) (centerY - BODY_RADIUS * scale - 30));
    }

    private void paintBlockSpark(Graphics2D g, double scale, double originX, double originY) {
        if (blockSparkTime <= 0) return;
        var remaining = Math.clamp(blockSparkTime / BLOCK_SPARK_DURATION, 0, 1);
        var progress = 1 - remaining;
        var centerX = originX + blockSparkX * scale;
        var centerY = originY + blockSparkY * scale;
        var radius = 7 + progress * 13;
        g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(new Color(255, 226, 73, (int) (remaining * 230)));
        for (var n = 0; n < 8; ++n) {
            var angle = n * Math.PI / 4;
            var inner = radius * 0.28;
            var outer = radius * (n % 2 == 0 ? 1 : 0.72);
            g.drawLine((int) (centerX + Math.cos(angle) * inner), (int) (centerY + Math.sin(angle) * inner),
                    (int) (centerX + Math.cos(angle) * outer), (int) (centerY + Math.sin(angle) * outer));
        }
        g.setColor(new Color(255, 250, 210, (int) (remaining * 245)));
        g.fillOval((int) centerX - 3, (int) centerY - 3, 6, 6);
    }

    private void paintHitMessage(Graphics2D g, double scale, double centerX, double centerY, UiLanguage language) {
        if (hitMessageTime <= 0 || lastHitZone == null) return;
        var message = lastHitZone.label(language) + " -" + lastDamage;
        g.setFont(g.getFont().deriveFont(Font.BOLD, 14f));
        g.setColor(new Color(170, 35, 35));
        g.drawString(message, (float) (centerX - g.getFontMetrics().stringWidth(message) / 2.0),
                (float) (centerY + BODY_RADIUS * scale + 22));
    }

    private void paintChamberMessage(Graphics2D g, double scale, double centerX, double centerY, UiLanguage language) {
        if (chamberMessageTime <= 0 || backstabMessageTime > 0 || doubleBlockMessageTime > 0) return;
        var message = chamberMessageType == ChamberType.PERFECT ?
                language.text("CHAMBER PARFAIT !", "完美接招反擊！") :
                language.text(chamberMessageChain > 1 ? "CHAMBER ×" + chamberMessageChain + " !" : "CHAMBER !",
                        chamberMessageChain > 1 ? "連續接招反擊 ×" + chamberMessageChain + "！" : "接招反擊！");
        g.setFont(g.getFont().deriveFont(Font.BOLD, 16f));
        g.setColor(chamberMessageType == ChamberType.PERFECT ?
                new Color(210, 128, 18) : new Color(126, 55, 171));
        g.drawString(message, (float) (centerX - g.getFontMetrics().stringWidth(message) / 2.0),
                (float) (centerY - BODY_RADIUS * scale - 30));
    }

    private void paintBaitMessage(Graphics2D g, double scale, double centerX, double centerY, UiLanguage language) {
        if (baitTime <= 0 || chamberMessageTime > 0 || baitTrapMessageTime > 0 || punishMessageTime > 0 ||
                baitAdvanceMessageTime > 0 || backstabMessageTime > 0 || doubleBlockMessageTime > 0) return;
        var arrow = baitOffset < 0 ? "←" : "→";
        var message = language.text("APPÂT ", "誘敵 ") + arrow;
        g.setFont(g.getFont().deriveFont(Font.BOLD, 14f));
        g.setColor(new Color(184, 117, 28));
        g.drawString(message, (float) (centerX - g.getFontMetrics().stringWidth(message) / 2.0),
                (float) (centerY - BODY_RADIUS * scale - 30));
    }

    private void paintBaitOutcomeMessage(Graphics2D g, double scale, double centerX, double centerY,
                                         UiLanguage language) {
        if (backstabMessageTime > 0 || doubleBlockMessageTime > 0 ||
                baitTrapMessageTime <= 0 && punishMessageTime <= 0 && baitAdvanceMessageTime <= 0) return;
        String message;
        Color messageColor;
        if (baitTrapMessageTime > 0) {
            message = language.text("PIÉGÉ !", "中計！");
            messageColor = new Color(190, 45, 45);
        } else if (punishMessageTime > 0) {
            message = language.text("PUNITION !", "懲罰！");
            messageColor = new Color(48, 137, 86);
        } else {
            message = language.text("CONTRE-APPÂT !", "識破前壓！");
            messageColor = new Color(37, 111, 180);
        }
        g.setFont(g.getFont().deriveFont(Font.BOLD, 16f));
        g.setColor(messageColor);
        g.drawString(message, (float) (centerX - g.getFontMetrics().stringWidth(message) / 2.0),
                (float) (centerY - BODY_RADIUS * scale - 30));
    }

    private void paintDezoneMessage(Graphics2D g, double scale, double centerX, double centerY, UiLanguage language) {
        if (dezoneTime <= 0 || chamberMessageTime > 0 || baitTime > 0 ||
                baitTrapMessageTime > 0 || punishMessageTime > 0 || baitAdvanceMessageTime > 0 ||
                backstabMessageTime > 0 || doubleBlockMessageTime > 0) return;
        var message = dezoneSwitchTarget == null ? language.text("DÉZONAGE", "脫離原位") :
                language.text("CHANGEMENT CIBLE", "換目標脫離");
        g.setFont(g.getFont().deriveFont(Font.BOLD, 14f));
        g.setColor(new Color(48, 137, 86));
        g.drawString(message, (float) (centerX - g.getFontMetrics().stringWidth(message) / 2.0),
                (float) (centerY - BODY_RADIUS * scale - 30));
    }

    private void paintBackWarning(Graphics2D g, double scale, double centerX, double centerY, UiLanguage language) {
        String message;
        if (backstabMessageTime > 0) message = language.text("BACKSTAB !", "背刺！");
        else if (backExposed && hitMessageTime <= 0) message = language.text("DOS EXPOSÉ", "背部暴露");
        else return;
        g.setFont(g.getFont().deriveFont(Font.BOLD, backstabMessageTime > 0 ? 16f : 12f));
        g.setColor(new Color(205, 38, 38));
        var messageY = backstabMessageTime > 0 ? centerY - BODY_RADIUS * scale - 30 :
                centerY + BODY_RADIUS * scale + 19;
        g.drawString(message, (float) (centerX - g.getFontMetrics().stringWidth(message) / 2.0), (float) messageY);
    }
}
