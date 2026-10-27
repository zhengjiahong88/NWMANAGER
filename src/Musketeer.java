import java.awt.*;
import java.awt.geom.Path2D;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.IdentityHashMap;

final class Musketeer {
    static final double
            BODY_RADIUS = 0.32,
            WEAPON_REST = 0.92,
            WEAPON_HEIGHT = 1.15,
            MAX_PITCH = Math.toRadians(35),
            MAX_WEAPON_YAW = Math.toRadians(50),
            BLOCK_SPARK_DURATION = 0.22;
    private static final Color
            NORMAL_WEAPON_COLOR = new Color(185, 190, 198),
            ATTACK_READY_COLOR = new Color(248, 205, 76),
            ACTIVE_ATTACK_COLOR = new Color(255, 224, 42),
            ATTACK_RECOVERY_COLOR = new Color(198, 126, 35),
            BLOCK_GLOW_COLOR = new Color(48, 220, 235);

    final Team team;
    final Color color;
    Pos pos, previous, velocity = new Pos(), desiredVelocity = new Pos(), previousTip, blockSpark;
    Pos renderOffset = new Pos();
    boolean hiddenAfterDeath;
    final DeadAgentAudit deadAgentAudit = new DeadAgentAudit();
    private DeathPose deathPose;
    SteeringContributionSnapshot lastGroup3Steering;
    double
            movementHeading, angle, angularVelocity, pitch, desiredPitch,
            attackPitch, baseAttackPitch, weaponYaw, desiredWeaponYaw,
            manipulationPitchOffset, manipulationYawOffset, baitOffset,
            stateTime, decisionTime, reactionTime, aimError, attackReachScale = 1, engagementTime,
            feintDuration, feintCooldown, guardPrepareTime, guardHoldTime, guardSwitchCooldown,
            pressureReceived, pressureGuardTime, pressureReleaseDelay, pressureTransferDelay,
            pressureFeintCooldown, pressureEscapeTime, pressureEscapeDelay, pressureReengageTime,
            localRoleTime, localSwitchCooldown, localHoldTransferTime, tacticalIntentTime,
            interceptionReevaluationTime, interceptionPhaseTime, threatResponseDelay;
    private final String name;
    final AgentStats stats;
    Tactic tactic;
    CombatState state = CombatState.NEUTRAL;
    CombatState targetStateAtAttackCommit = CombatState.NEUTRAL;
    Decision decision = Decision.WAIT;
    final MovementIntent movementIntent = new MovementIntent();
    CombatDirection attackDirection = CombatDirection.UP, queuedAttackDirection = CombatDirection.DOWN,
            lastCommittedAttackDirection;
    BlockDirection blockDirection = BlockDirection.UP;
    EngagementPhase engagementPhase = EngagementPhase.PROBE;
    OpeningChoice openingChoice = OpeningChoice.UNDECIDED;
    AttackManipulation attackManipulation = AttackManipulation.NEUTRAL;
    AttackIntent attackIntent = AttackIntent.HOLD;
    PressureRole pressureRole = PressureRole.NONE;
    PressureContext pressureContext = PressureContext.NONE;
    LocalRole localRole = LocalRole.FREE;
    TacticalIntent tacticalIntent = TacticalIntent.DUEL;
    GroupIntent groupIntent = GroupIntent.MARK;
    FlankPhase flankPhase = FlankPhase.APPROACH_SIDE;
    EngagementSlot engagementSlot;
    Engagement engagement;
    Musketeer plannedTarget;
    Musketeer primaryMark, secondaryThreat, coveredBy, covering;
    CoverAssessment coverAssessment;
    CoverPositionAssessment coverPositionAssessment;
    GroupSteeringDebug groupSteeringDebug;
    LocalManeuver localManeuver;
    LocalManeuverAssessment localManeuverAssessment;
    WeaponThreatLane primaryWeaponLane, secondaryWeaponLane;
    final MultiThreatAttention multiThreatAttention = new MultiThreatAttention();
    final AttentionAllocation attentionAllocation = new AttentionAllocation();
    CommitAssessment commitAssessment;
    AttackOpportunity attackOpportunity;
    AttackOpportunityRejection lastAttackOpportunityRejection =
            AttackOpportunityRejection.NO_IDENTIFIABLE_OPENING;
    RevengeOpportunity revengeOpportunity;
    CounterCoverAssessment counterCoverAssessment;
    Pos tacticalDestination, preferredGroupPosition, rawInterceptionPoint, committedInterceptionPoint,
            rawRearDestination, smoothedRearDestination;
    InterceptionPhase interceptionPhase;
    ThreatAssessment threatAssessment = new ThreatAssessment(0, false, false,
            Double.POSITIVE_INFINITY, ThreatResponse.IGNORE);
    BackstabOpportunity backstabOpportunity = BackstabOpportunity.unavailable();
    ThreatResponse threatResponse = ThreatResponse.IGNORE, pendingThreatResponse = ThreatResponse.IGNORE;
    Musketeer tacticalThreat;
    Musketeer group3DefensiveThreat;
    String lastIntentReason = "unassigned", lastInterceptionReason = "none", lastFlankReason = "not_started";
    double lastThreatLoggedScore = Double.NaN;
    double group3DefenseReadyAt;
    int group3ObservedThreatSequence = -1;
    HitZone intendedZone = HitZone.TORSO, lastHitZone;
    ChamberType chamberType = ChamberType.NONE, chamberMessageType = ChamberType.NONE;
    double attackHeight = HitZone.TORSO.centerHeight();
    int hitPoints = 100, lastDamage;
    Message
            doubleBlockMessage = new Message(new Color(145, 91, 181)),
            hitMessage = new Message(new Color(170, 35, 35)),
            chamberMessage = new Message(new Color(210, 128, 18)),
            baitMessage = new Message(new Color(184, 117, 28)),
            baitTrapMessage = new Message(new Color(190, 45, 45)),
            punishMessage = new Message(new Color(48, 137, 86)),
            baitAdvanceMessage = new Message(new Color(37, 111, 180)),
            dezoneMessage = new Message(new Color(48, 137, 86));
    int backWarningTimes;
    double chamberTimingError,
            blockSparkTime, backstabMessageTime,
            doubleBlockSwitchTime,
            dezoneCooldown, dezoneDangerTime, targetLockTime, rearReactionTime, backEscapeTime,
            backEscapeCooldown, movingIntoLineReasonUntil,
            freeKillPerceivedDistance, rearAngle, previousRearAngle, rearAngleGainRate,
            rearStableTime, rearNoProgressTime, flankFixerLostTime, flankRadiusOffset,
            controlledBackstabReleaseAngle, flankPhaseStartedAt;
    int baitSequence, observedBaitSequence, chamberChain, chamberMessageChain, attackSequence, blockSequence,
            directionStreak, engagementSide = 1, feintCount;
    DezoneMovement dezoneMovement = DezoneMovement.NONE;
    DezoneStance dezoneStance = DezoneStance.NEUTRAL;
    boolean alive = true, threatSeen, attackResolved, simpleChamberLethal, baitedAttack, punishingBait,
            exploitingBait, backExposed, freeKillAttempt, controlledBackstabAttempt,
            rearSectorStabilized, backstabWindowLogged, doubleBlockSwitched, dezoneSwitchCommitted,
            feintToBlock, feintCancel, guardSwitching, pressureMode, pressureCounter, pressureEscapeActive,
            backEscapeCountedSafe, targetFocusedOnOtherAtAttackCommit, modernStationaryEpisodeActive;
    int backEscapeDirection = 1, pressureSide = 1, localSide = 1, rotationSide = 1,
            lastLocalManeuverSide;
    BlockDirection doubleBlockSecondDirection = BlockDirection.DOWN;
    Musketeer target, rearThreat, doubleBlockThreat, dezoneOriginalTarget, dezoneSwitchTarget,
            pressureSource, pressureFixedTarget, pressureTransferTarget, forcedPressureSource, localObjective,
            openingOpponent, movingIntoLineTarget;
    final IdentityHashMap<Musketeer, OpponentAwareness> opponentAwareness = new IdentityHashMap<>();
    final IdentityHashMap<Musketeer, Double> pressureByOpponent = new IdentityHashMap<>();
    final IdentityHashMap<Musketeer, Double> localThreatByOpponent = new IdentityHashMap<>();

    Musketeer(AgentConfiguration configuration, Team team, Color color, Pos pos, double angle) {
        this.team = team;
        this.color = color;
        this.pos = pos;
        this.angle = angle;
        name = configuration.name();
        stats = configuration.stats();
        tactic = configuration.tactic();
        previous = pos;
        movementHeading = angle;
        previousTip = tip();
    }

    void assignTacticalIntent(Engagement engagement, TacticalIntent intent, EngagementSlot slot,
                              Musketeer plannedTarget, String reason) {
        this.engagement = engagement;
        tacticalIntent = intent;
        engagementSlot = slot;
        this.plannedTarget = plannedTarget;
        lastIntentReason = reason;
        tacticalIntentTime = 0;
    }

    double getX() {
        return pos.x();
    }

    double getY() {
        return pos.y();
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

    Pos tip() {
        return pos.plus(new Pos(Math.cos(weaponWorldAngle()) * weaponLength() - Math.sin(weaponWorldAngle()) * weaponOffset(), Math.sin(weaponWorldAngle()) * weaponLength() + Math.cos(weaponWorldAngle()) * weaponOffset()));
    }

    double weaponWorldAngle() {
        return Math.atan2(Math.sin(angle + weaponYaw), Math.cos(angle + weaponYaw));
    }

    void captureDeathPose() {
        if (deathPose == null) deathPose = new DeathPose(pos, angle, weaponWorldAngle());
    }

    DeathPose deathPose() {
        return deathPose;
    }

    void freezeAtDeathPose() {
        if (alive || deathPose == null) return;
        pos = previous = deathPose.position();
        angle = deathPose.bodyAngle();
        weaponYaw = Math.atan2(Math.sin(deathPose.weaponAngle() - angle),
                Math.cos(deathPose.weaponAngle() - angle));
        desiredWeaponYaw = weaponYaw;
        velocity = desiredVelocity = new Pos();
        angularVelocity = 0;
        previousTip = tip();
    }

    void clearActiveIntentOnDeath() {
        target = plannedTarget = primaryMark = secondaryThreat = coveredBy = covering = null;
        tacticalThreat = group3DefensiveThreat = rearThreat = doubleBlockThreat = null;
        dezoneOriginalTarget = dezoneSwitchTarget = pressureSource = pressureFixedTarget = null;
        pressureTransferTarget = forcedPressureSource = localObjective = openingOpponent = null;
        movingIntoLineTarget = movementIntent.target = null;
        engagement = null;
        localManeuver = null;
        coverAssessment = null;
        coverPositionAssessment = null;
        groupSteeringDebug = null;
        localManeuverAssessment = null;
        primaryWeaponLane = secondaryWeaponLane = null;
        commitAssessment = null;
        attackOpportunity = null;
        revengeOpportunity = null;
        counterCoverAssessment = null;
        tacticalDestination = preferredGroupPosition = rawInterceptionPoint = null;
        committedInterceptionPoint = rawRearDestination = smoothedRearDestination = null;
        backstabOpportunity = BackstabOpportunity.unavailable();
        threatAssessment = new ThreatAssessment(0, false, false,
                Double.POSITIVE_INFINITY, ThreatResponse.IGNORE);
        threatResponse = pendingThreatResponse = ThreatResponse.IGNORE;
        attentionAllocation.primaryThreat = attentionAllocation.secondaryThreat = null;
        attentionAllocation.peripheralThreat = attentionAllocation.pendingPrimary = null;
        attentionAllocation.formerPrimary = null;
        multiThreatAttention.focusedThreat = multiThreatAttention.secondaryThreat = null;
        opponentAwareness.clear();
        pressureByOpponent.clear();
        localThreatByOpponent.clear();
        movementIntent.source = decision = Decision.WAIT;
        movementIntent.state = CombatState.DEAD;
        pressureMode = pressureCounter = pressureEscapeActive = false;
        controlledBackstabAttempt = freeKillAttempt = backExposed = false;
    }

    double pitchFor(double height, double distance) {
        return Math.clamp(Math.atan2(height - WEAPON_HEIGHT, Math.max(0.25, distance)), -MAX_PITCH, MAX_PITCH);
    }

    String name(UiLanguage language) {
        return language.text(name, name);
    }

    void paint(Graphics2D g, double scale, Pos origin, UiLanguage language) throws IOException {
        if (!alive && hiddenAfterDeath) return;
        var visualOrigin = origin.plus(renderOffset.times(scale));
        var transform = g.getTransform();
        var originalStroke = g.getStroke();
        Pos center = visualOrigin.plus(pos.times(scale));
        g.translate(center.x(), center.y());
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
        paintAttackSweep(g, scale, visualOrigin);
        paintDirectionBadge(g, scale, center.x(), center.y());
        paintCombatMessage(g, scale, center, language);
        paintBackWarning(g, scale, center.x(), center.y(), language);
        paintBlockSpark(g, scale, visualOrigin);
        g.setStroke(originalStroke);
    }

    private void paintAttackSweep(Graphics2D g, double scale, Pos origin) {
        if (!alive || !isAttacking()) return;
        g.setStroke(new BasicStroke((float) Math.max(1.4, scale * 0.022), BasicStroke.CAP_ROUND,
                BasicStroke.JOIN_ROUND));
        g.setColor(new Color(255, 202, 44, 92));
        Pos p = origin.plus(previousTip.times(scale)), po = origin.plus(tip().times(scale));
        g.drawLine((int) p.x(), (int) p.y(), (int) po.x(), (int) po.y());
    }

    private void paintVelocity(Graphics2D g, double scale) {
        var speed = Math.hypot(velocity.x(), velocity.y());
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

    private void paintCombatMessage(Graphics2D g, double scale, Pos center, UiLanguage language) {
        if (backstabMessageTime > 0) {
            var message = language.text("BACKSTAB !", "背刺！");
            g.setFont(g.getFont().deriveFont(Font.BOLD, 16f));
            g.setColor(new Color(205, 38, 38));
            g.drawString(message, (float) (center.x() - g.getFontMetrics().stringWidth(message) / 2.0),
                    (float) (center.y() - BODY_RADIUS * scale - 30));
            return;
        }
        if (doubleBlockMessage.isActive()) {
            doubleBlockMessage.paint(g, language.text("DOUBLE BLOCK !", "雙重格擋！"), center, scale);
            return;
        }
        if (hitMessage.isActive() && lastHitZone != null) {
            hitMessage.paint(g, lastHitZone.label(language) + " -" + lastDamage, center, scale);
            return;
        }
        if (baitTrapMessage.isActive()) {
            baitTrapMessage.paint(g, language.text("PIÉGÉ !", "中計！"), center, scale);
            return;
        }
        if (punishMessage.isActive()) {
            punishMessage.paint(g, language.text("PUNITION !", "懲罰！"), center, scale);
            return;
        }
        if (baitAdvanceMessage.isActive()) {
            baitAdvanceMessage.paint(g, language.text("CONTRE-APPÂT !", "識破前壓！"), center, scale);
            return;
        }
        if (chamberMessage.isActive()) {
            chamberMessage.paint(g, chamberMessageType == ChamberType.PERFECT ?
                    language.text("CHAMBER PARFAIT !", "完美接招反擊！") :
                    language.text(chamberMessageChain > 1 ? "CHAMBER ×" + chamberMessageChain + " !" : "CHAMBER !",
                            chamberMessageChain > 1 ? "連續接招反擊 ×" + chamberMessageChain + "！" : "接招反擊！"),
                    center, scale);
            return;
        }
        if (baitMessage.isActive()) {
            baitMessage.paint(g, language.text("APPÂT ", "誘敵 ") + (baitOffset < 0 ? "←" : "→"), center, scale);
            return;
        }
        if (dezoneMessage.isActive()) {
            dezoneMessage.paint(g, dezoneSwitchTarget == null ? language.text("DÉZONAGE", "脫離原位") :
                    language.text("CHANGEMENT CIBLE", "換目標脫離"), center, scale);
            return;
        }
        String message;
        Color messageColor;
        if (state == CombatState.FEINT) {
            message = language.text("FEINTE", "變招");
            messageColor = new Color(151, 91, 181);
        } else if (isBasicAttack() && state == CombatState.ATTACK_PREPARE) {
            message = attackIntent == AttackIntent.DIRECT_RELEASE ?
                    language.text("ESTOC DIRECT", "直接刺擊") : language.text("ARMER HOLD", "準備保持");
            messageColor = ATTACK_READY_COLOR;
        } else if (isBasicAttack() && state == CombatState.ATTACK_HOLD) {
            message = language.text("HOLD", "保持攻擊");
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
        g.drawString(message, (float) (center.x() - g.getFontMetrics().stringWidth(message) / 2.0),
                (float) (center.y() - BODY_RADIUS * scale - 28));
    }

    private void paintBlockSpark(Graphics2D g, double scale, Pos origin) {
        if (blockSparkTime <= 0) return;
        var remaining = Math.clamp(blockSparkTime / BLOCK_SPARK_DURATION, 0, 1);
        var progress = 1 - remaining;
        Pos center = origin.plus(blockSpark.times(scale));
        var radius = 7 + progress * 13;
        g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(new Color(255, 226, 73, (int) (remaining * 230)));
        for (var n = 0; n < 8; ++n) {
            var angle = n * Math.PI / 4;
            var inner = radius * 0.28;
            var outer = radius * (n % 2 == 0 ? 1 : 0.72);
            Pos p = center.plus(new Pos(angle).times(inner)), po = center.plus(new Pos(angle).times(outer));
            g.drawLine((int) p.x(), (int) p.y(), (int) po.x(), (int) po.y());
        }
        g.setColor(new Color(255, 250, 210, (int) (remaining * 245)));
        g.fillOval((int) center.x() - 3, (int) center.y() - 3, 6, 6);
    }

    private void paintBackWarning(Graphics2D g, double scale, double centerX, double centerY, UiLanguage language) {
        if (!backExposed || hitMessage.isActive() || backstabMessageTime > 0) return;
        var message = language.text("DOS EXPOSÉ", "背部暴露");
        g.setFont(g.getFont().deriveFont(Font.BOLD, 12f));
        g.setColor(new Color(205, 38, 38));
        var messageY = centerY + BODY_RADIUS * scale + 19;
        g.drawString(message, (float) (centerX - g.getFontMetrics().stringWidth(message) / 2.0), (float) messageY);
    }

    void recordMessage() {
        System.out.println(name + ":\n"
                + "doubleBlock: " + doubleBlockMessage.times + '\n'
                + "hit: " + hitMessage.times + '\n'
                + "chamber: " + chamberMessage.times + '\n'
                + "bait: " + baitMessage.times + '\n'
                + "baitTrap: " + baitTrapMessage.times + '\n'
                + "punish: " + punishMessage.times + '\n'
                + "baitAdvance: " + baitAdvanceMessage.times + '\n'
                + "dezone: " + dezoneMessage.times + '\n'
                + "backWarning: " + backWarningTimes);
    }
}
