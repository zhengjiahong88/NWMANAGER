import java.awt.*;
import java.util.Random;

final class DuelSimulation {
    static final double ARENA_WIDTH = 8.0, ARENA_HEIGHT = 7.0;
    static final int BLUE_TEAM = 0, RED_TEAM = 1, DRAW = 2;
    private static final double DECISION_INTERVAL = 0.10, HOLD_LIMIT = 0.75, BLOCK_MIN_TIME = 0.18;
    private static final double PERCEPTION_INTERVAL = 0.25;
    private static final double BACKWARD_SPEED_FACTOR = 0.62, BLOCK_STUN_TIME = 0.18;
    private static final double REAR_ATTACK_ANGLE = Math.toRadians(115);
    private static final double DOUBLE_BLOCK_WINDOW = 0.20, DOUBLE_BLOCK_MIN_DURATION = 0.26;
    private static final double DEZONE_BACKWARD_LIMIT = 2.55, TARGET_SWITCH_MAX_DISTANCE = 3.20;
    private static final double SIMPLE_CHAMBER_WINDOW = 0.025, PERFECT_CHAMBER_WINDOW = 0.004;
    private static final double CHAMBER_ATTEMPT_LIMIT = 0.65, SIMPLE_CHAMBER_HOLD = 0.24, WEAPON_HEIGHT = 1.15;
    private static final double RECHAMBER_BASE_FACTOR = 0.50, RECHAMBER_CHAIN_PENALTY = 0.10,
            MIN_RECHAMBER_FACTOR = 0.05;
    private static final int CHAMBER_BLOCK_BONUS = 8;
    private final Random random = new Random();
    private final AgentConfiguration[] blueConfigurations, redConfigurations;
    Musketeer[] blueTeam, redTeam, agents;
    int winningTeam = -1;

    DuelSimulation() {
        this(AgentConfiguration.defaultBlue(), AgentConfiguration.defaultBlueTwo(),
                AgentConfiguration.defaultRed(), AgentConfiguration.defaultRedTwo());
    }

    DuelSimulation(AgentConfiguration blueConfiguration, AgentConfiguration redConfiguration) {
        this(blueConfiguration, AgentConfiguration.defaultBlueTwo(), redConfiguration,
                AgentConfiguration.defaultRedTwo());
    }

    DuelSimulation(AgentConfiguration blueOne, AgentConfiguration blueTwo, AgentConfiguration redOne,
                   AgentConfiguration redTwo) {
        blueConfigurations = new AgentConfiguration[]{blueOne, blueTwo};
        redConfigurations = new AgentConfiguration[]{redOne, redTwo};
        reset();
    }

    void reset() {
        blueTeam = new Musketeer[]{
                createAgent(blueConfigurations[0], BLUE_TEAM, new Color(48, 112, 220), 1.65, 2.45, 0),
                createAgent(blueConfigurations[1], BLUE_TEAM, new Color(40, 151, 184), 1.65, 4.55, 0)
        };
        redTeam = new Musketeer[]{
                createAgent(redConfigurations[0], RED_TEAM, new Color(215, 62, 65), ARENA_WIDTH - 1.65, 2.45, Math.PI),
                createAgent(redConfigurations[1], RED_TEAM, new Color(221, 111, 48), ARENA_WIDTH - 1.65, 4.55, Math.PI)
        };
        agents = new Musketeer[]{blueTeam[0], blueTeam[1], redTeam[0], redTeam[1]};
        winningTeam = -1;
        for (var agent : agents) initializeAwareness(agent);
        for (var agent : agents) selectTarget(agent, true);
    }

    private Musketeer createAgent(AgentConfiguration configuration, int team, Color color,
                                   double x, double y, double angle) {
        return new Musketeer(configuration.name(), configuration.name(), team, color, configuration.stats(),
                configuration.tactic(), x, y, angle);
    }

    void update(double dt) {
        if (winningTeam >= 0) return;
        for (var agent : agents) snapshotMotion(agent);
        for (var agent : agents) updateAgent(agent, dt);
        separateAgents();
        for (var attacker : agents) resolveAttack(attacker);
        updateWinner();
    }

    private void updateAgent(Musketeer self, double dt) {
        if (self.hitMessageTime > 0) self.hitMessageTime -= dt;
        if (self.chamberMessageTime > 0) self.chamberMessageTime -= dt;
        if (self.baitTrapMessageTime > 0) self.baitTrapMessageTime -= dt;
        if (self.punishMessageTime > 0) self.punishMessageTime -= dt;
        if (self.baitAdvanceMessageTime > 0) self.baitAdvanceMessageTime -= dt;
        if (self.blockSparkTime > 0) self.blockSparkTime -= dt;
        if (self.backstabMessageTime > 0) self.backstabMessageTime -= dt;
        if (self.doubleBlockMessageTime > 0) self.doubleBlockMessageTime -= dt;
        if (self.backEscapeTime > 0) self.backEscapeTime -= dt;
        if (self.feintCooldown > 0) self.feintCooldown = Math.max(0, self.feintCooldown - dt);
        if (self.guardSwitchCooldown > 0) self.guardSwitchCooldown = Math.max(0, self.guardSwitchCooldown - dt);
        if (self.dezoneCooldown > 0) self.dezoneCooldown = Math.max(0, self.dezoneCooldown - dt);
        if (self.baitTime > 0) {
            self.baitTime -= dt;
            if (self.baitTime <= 0) self.baitOffset = 0;
        }
        if (self.dezoneTime > 0) {
            self.dezoneTime -= dt;
            if (self.dezoneTime <= 0) {
                if (self.dezoneSwitchTarget != null && !self.dezoneSwitchCommitted) commitTargetSwitchDezone(self);
                else endDezone(self);
            }
        }
        if (!self.alive) return;
        for (var awareness : self.opponentAwareness.values())
            if (awareness.memoryTime > 0) awareness.memoryTime = Math.max(0, awareness.memoryTime - dt);
        self.perceptionTime -= dt;
        if (self.perceptionTime <= 0) {
            updatePerception(self);
            self.perceptionTime = PERCEPTION_INTERVAL + random.nextDouble() * 0.08;
        }
        updateBackAwareness(self, dt);
        updateTargetSwitchDezone(self, dt);
        self.targetLockTime -= dt;
        var enemy = selectTarget(self, false);
        if (enemy == null) return;
        self.stateTime += dt;
        self.decisionTime -= dt;
        updateEngagementPlan(self, enemy, dt);
        updateState(self);
        if (self.decisionTime <= 0) {
            chooseDecision(self, enemy);
            self.decisionTime = DECISION_INTERVAL + random.nextDouble() * 0.035;
        }
        moveAndTurn(self, enemy, dt);
    }

    private void initializeAwareness(Musketeer self) {
        self.opponentAwareness.clear();
        for (var opponent : opponentsOf(self)) {
            var towardOpponent = Math.atan2(opponent.y - self.y, opponent.x - self.x);
            var visible = Math.abs(angleDifference(self.angle, towardOpponent)) <= Math.toRadians(115);
            self.opponentAwareness.put(opponent, new OpponentAwareness(opponent.x, opponent.y, visible));
        }
        self.perceptionTime = PERCEPTION_INTERVAL;
    }

    private void updatePerception(Musketeer self) {
        for (var opponent : opponentsOf(self)) {
            var awareness = self.opponentAwareness.get(opponent);
            if (awareness == null) {
                awareness = new OpponentAwareness(opponent.x, opponent.y, false);
                self.opponentAwareness.put(opponent, awareness);
            }
            if (!opponent.alive) {
                awareness.visible = false;
                awareness.memoryTime = 0;
                continue;
            }
            var distance = Math.hypot(opponent.x - self.x, opponent.y - self.y);
            var towardOpponent = Math.atan2(opponent.y - self.y, opponent.x - self.x);
            var relativeAngle = Math.abs(angleDifference(self.angle, towardOpponent));
            var detected = random.nextDouble() < detectionProbability(self, opponent, awareness,
                    distance, relativeAngle);
            awareness.visible = detected;
            if (!detected) continue;
            var angleFactor = perceptionAngleFactor(relativeAngle);
            var positionError = 0.01 + (100 - self.stats.distanceControl()) * 0.003 +
                    (1 - angleFactor) * 0.08;
            awareness.lastKnownX = opponent.x + random.nextGaussian() * positionError;
            awareness.lastKnownY = opponent.y + random.nextGaussian() * positionError;
            awareness.memoryTime = 0.55 +
                    (self.stats.reaction() + self.stats.decisionMaking()) / 200.0 * 1.30;
            observeCombatPattern(awareness, opponent);
        }
    }

    private void observeCombatPattern(OpponentAwareness awareness, Musketeer opponent) {
        if (opponent.attackSequence != awareness.lastObservedAttackSequence &&
                (opponent.state == CombatState.ATTACK_PREPARE || opponent.state == CombatState.ATTACK_HOLD ||
                        opponent.isAttacking())) {
            awareness.lastObservedAttackSequence = opponent.attackSequence;
            if (opponent.attackDirection == CombatDirection.UP) ++awareness.observedUpAttacks;
            else ++awareness.observedDownAttacks;
        }
        if (opponent.blockSequence == awareness.lastObservedBlockSequence || !opponent.isGuarding()) return;
        awareness.lastObservedBlockSequence = opponent.blockSequence;
        if (opponent.blockDirection == BlockDirection.UP) ++awareness.observedUpBlocks;
        else if (opponent.blockDirection == BlockDirection.DOWN) ++awareness.observedDownBlocks;
    }

    private void snapshotMotion(Musketeer agent) {
        agent.previousX = agent.x;
        agent.previousY = agent.y;
        agent.previousTipX = agent.tipX();
        agent.previousTipY = agent.tipY();
    }

    private double detectionProbability(Musketeer self, Musketeer opponent, OpponentAwareness awareness,
                                        double distance, double relativeAngle) {
        var distanceFactor = Math.clamp(1 - Math.max(0, distance - 1.5) / 4.0, 0.12, 1);
        var skillFactor = 0.45 + self.stats.peripheralAwareness() * 0.0055;
        var attentionFactor = 1.0;
        if (opponent != self.target && self.target != null && self.target.alive) {
            var targetDistance = Math.hypot(self.target.x - self.x, self.target.y - self.y);
            var targetPressures = self.target.state == CombatState.ATTACK_PREPARE ||
                    self.target.state == CombatState.ATTACK_HOLD || self.target.isAttacking();
            attentionFactor = targetDistance < 2.1 || targetPressures ? 0.45 : 0.72;
        }
        var probability = perceptionAngleFactor(relativeAngle) * distanceFactor * skillFactor * attentionFactor;
        if (awareness.visible) probability += 0.18;
        var opponentAttacks = opponent.state == CombatState.ATTACK_PREPARE ||
                opponent.state == CombatState.ATTACK_HOLD || opponent.state == CombatState.CHAMBER_HOLD ||
                opponent.isAttacking();
        if (opponentAttacks) probability += 0.28;
        else if (opponent.decision != Decision.WAIT) probability += 0.06;
        if (distance <= 1.6 && relativeAngle <= Math.toRadians(115)) probability = Math.max(0.82, probability);
        if (opponent == self.target && relativeAngle <= Math.toRadians(115))
            probability = Math.max(0.96, probability);
        return Math.clamp(probability, 0.02, 0.99);
    }

    private double perceptionAngleFactor(double relativeAngle) {
        var degrees = Math.toDegrees(relativeAngle);
        if (degrees <= 60) return 1 - degrees / 60 * 0.15;
        if (degrees <= 115) return 0.85 - (degrees - 60) / 55 * 0.55;
        return 0.30 - (degrees - 115) / 65 * 0.24;
    }

    private boolean isVisible(Musketeer self, Musketeer opponent) {
        var awareness = self.opponentAwareness.get(opponent);
        return awareness != null && awareness.visible;
    }

    private boolean isTracked(Musketeer self, Musketeer opponent) {
        var awareness = self.opponentAwareness.get(opponent);
        return awareness != null && (awareness.visible || awareness.memoryTime > 0);
    }

    private double knownX(Musketeer self, Musketeer opponent) {
        var awareness = self.opponentAwareness.get(opponent);
        return awareness == null || awareness.visible ? opponent.x : awareness.lastKnownX;
    }

    private double knownY(Musketeer self, Musketeer opponent) {
        var awareness = self.opponentAwareness.get(opponent);
        return awareness == null || awareness.visible ? opponent.y : awareness.lastKnownY;
    }

    private double knownDistance(Musketeer self, Musketeer opponent) {
        return Math.hypot(knownX(self, opponent) - self.x, knownY(self, opponent) - self.y);
    }

    private boolean isKnownRear(Musketeer self, Musketeer opponent) {
        var towardOpponent = Math.atan2(knownY(self, opponent) - self.y, knownX(self, opponent) - self.x);
        return Math.abs(angleDifference(self.angle, towardOpponent)) >= REAR_ATTACK_ANGLE;
    }

    private Musketeer selectTarget(Musketeer self, boolean force) {
        if (!self.alive) return null;
        var opponents = opponentsOf(self);
        if (!force && self.dezoneSwitchTarget != null && !self.dezoneSwitchCommitted &&
                self.dezoneOriginalTarget != null && self.dezoneOriginalTarget.alive)
            return self.dezoneOriginalTarget;
        var frontOpponent = closestFrontOpponent(self);
        Musketeer immediateThreat = null;
        var greatestThreat = Double.NEGATIVE_INFINITY;
        for (var opponent : opponents) {
            if (!opponent.alive || !isVisible(self, opponent)) continue;
            var distance = Math.hypot(opponent.x - self.x, opponent.y - self.y);
            var attacking = opponent.state == CombatState.ATTACK_PREPARE ||
                    opponent.state == CombatState.ATTACK_HOLD || opponent.state == CombatState.CHAMBER_HOLD ||
                    opponent.isAttacking();
            var facing = Math.abs(angleDifference(opponent.weaponWorldAngle(),
                    Math.atan2(self.y - opponent.y, self.x - opponent.x))) < Math.toRadians(34);
            var threat = opponent.projectedThrustLength() + Musketeer.BODY_RADIUS + 0.28 - distance;
            if (attacking && !opponent.baitedAttack && facing && threat > 0 && threat > greatestThreat) {
                immediateThreat = opponent;
                greatestThreat = threat;
            }
        }

        var canReact = self.state == CombatState.NEUTRAL || self.isGuarding();
        if (immediateThreat != null && canReact && immediateThreat != self.target &&
                (frontOpponent == null || !isKnownRear(self, immediateThreat)))
            return changeTarget(self, immediateThreat, 0.55);
        if (!force && self.target != null && !self.target.alive && self.state != CombatState.NEUTRAL)
            return self.target;
        if (!force && self.target != null && self.target.alive && self.state != CombatState.NEUTRAL)
            return self.target;
        if (!force && self.backEscapeTime > 0 && self.target != null && self.target.alive &&
                isTracked(self, self.target)) return self.target;
        var currentTargetExposesFront = frontOpponent != null && self.target != null &&
                isTracked(self, self.target) && isKnownRear(self, self.target);
        if (!force && self.target != null && self.target.alive && isTracked(self, self.target) &&
                !currentTargetExposesFront && self.targetLockTime > 0)
            return self.target;

        Musketeer best = null;
        var bestScore = Double.POSITIVE_INFINITY;
        var teammate = teammateOf(self);
        for (var opponent : opponents) {
            if (!opponent.alive || !isTracked(self, opponent)) continue;
            if (frontOpponent != null && isKnownRear(self, opponent)) continue;
            var score = knownDistance(self, opponent);
            var visible = isVisible(self, opponent);
            if (visible && (opponent.state == CombatState.RECOVERY ||
                    opponent.state == CombatState.BLOCK_STUN)) score -= 0.65;
            if (visible && opponent.target == self) score -= 0.18;
            if (visible && teammate != null && opponent.target == teammate &&
                    (opponent.isAttacking() || opponent.state == CombatState.ATTACK_HOLD))
                score -= 0.35 + self.stats.decisionMaking() * 0.006;
            if (visible && teammate != null && teammate.alive && teammate.target == opponent)
                score -= self.stats.decisionMaking() * 0.0025;
            score += random.nextGaussian() * (101 - self.stats.decisionMaking()) * 0.004;
            if (score < bestScore) {
                best = opponent;
                bestScore = score;
            }
        }
        return best == self.target ? best : changeTarget(self, best,
                0.55 + self.stats.decisionMaking() * 0.008);
    }

    private void updateBackAwareness(Musketeer self, double dt) {
        Musketeer nearestRear = null;
        var nearestDistance = Double.POSITIVE_INFINITY;
        self.backExposed = false;
        for (var opponent : opponentsOf(self)) {
            if (!opponent.alive) continue;
            if (isRearAttack(self, opponent)) self.backExposed = true;
            if (!isTracked(self, opponent) || !isKnownRear(self, opponent)) continue;
            var distance = knownDistance(self, opponent);
            if (distance < nearestDistance) {
                nearestRear = opponent;
                nearestDistance = distance;
            }
        }
        if (nearestRear != null && nearestRear != self.rearThreat) {
            self.rearThreat = nearestRear;
            self.rearReactionTime = 0.10 + (100 - self.stats.reaction()) * 0.0032 +
                    random.nextDouble() * 0.06;
        } else if (nearestRear == null && self.backEscapeTime <= 0) {
            self.rearThreat = null;
            self.rearReactionTime = 0;
        }
        if (self.rearReactionTime > 0) self.rearReactionTime = Math.max(0, self.rearReactionTime - dt);
    }

    private Musketeer closestFrontOpponent(Musketeer self) {
        Musketeer closest = null;
        var closestDistance = Double.POSITIVE_INFINITY;
        for (var opponent : opponentsOf(self)) {
            if (!opponent.alive || !isTracked(self, opponent) || isKnownRear(self, opponent)) continue;
            var distance = knownDistance(self, opponent);
            if (distance < closestDistance) {
                closest = opponent;
                closestDistance = distance;
            }
        }
        return closest;
    }

    private boolean isRearAttack(Musketeer defender, Musketeer attacker) {
        var towardAttacker = Math.atan2(attacker.y - defender.y, attacker.x - defender.x);
        return Math.abs(angleDifference(defender.angle, towardAttacker)) >= REAR_ATTACK_ANGLE;
    }

    private Musketeer changeTarget(Musketeer self, Musketeer target, double lockTime) {
        self.target = target;
        self.targetLockTime = lockTime;
        self.observedBaitSequence = -1;
        self.threatSeen = false;
        self.reactionTime = 0;
        return target;
    }

    private Musketeer[] opponentsOf(Musketeer agent) {
        return agent.team == BLUE_TEAM ? redTeam : blueTeam;
    }

    private Musketeer teammateOf(Musketeer agent) {
        var team = agent.team == BLUE_TEAM ? blueTeam : redTeam;
        return team[0] == agent ? team[1] : team[0];
    }

    private void updateState(Musketeer self) {
        switch (self.state) {
            case ATTACK_PREPARE -> {
                if (self.stateTime >= self.prepareTime()) {
                    if (self.baitedAttack) startAttack(self, CombatState.ATTACK_ACTIVE);
                    else setState(self, CombatState.ATTACK_HOLD);
                }
            }
            case ATTACK_HOLD -> {
                if (self.stateTime >= HOLD_LIMIT && self.dezoneTime <= 0 &&
                        !self.punishingBait && !self.exploitingBait)
                    startAttack(self, CombatState.ATTACK_ACTIVE);
            }
            case FEINT -> {
                if (self.stateTime < self.feintDuration) break;
                var enemy = self.target;
                if (enemy == null || !enemy.alive) {
                    setState(self, CombatState.NEUTRAL);
                    break;
                }
                if (self.feintToBlock) beginBlock(self, enemy);
                else {
                    self.attackDirection = self.queuedAttackDirection;
                    configureAim(self, Math.hypot(enemy.x - self.x, enemy.y - self.y));
                    self.attackPitch = self.pitch;
                    self.baseAttackPitch = self.pitch;
                    self.decision = Decision.PREPARE_ATTACK;
                    setState(self, CombatState.ATTACK_PREPARE);
                }
            }
            case BLOCK_PREPARE -> {
                if (self.stateTime >= self.guardPrepareTime) {
                    self.guardSwitching = false;
                    setState(self, CombatState.BLOCKING);
                }
            }
            case CHAMBER_PREPARE -> {
                if (self.stateTime >= CHAMBER_ATTEMPT_LIMIT) setState(self, CombatState.NEUTRAL);
            }
            case CHAMBER_HOLD -> {
                if (self.stateTime >= SIMPLE_CHAMBER_HOLD) startAttack(self, CombatState.CHAMBER_ACTIVE);
            }
            case ATTACK_ACTIVE, CHAMBER_ACTIVE -> {
                if (self.stateTime >= self.activeTime()) setState(self, CombatState.RECOVERY);
            }
            case BLOCK_STUN -> {
                if (self.stateTime >= BLOCK_STUN_TIME) setState(self, CombatState.RECOVERY);
            }
            case RECOVERY -> {
                if (self.stateTime >= self.recoveryTime()) setState(self, CombatState.NEUTRAL);
            }
            case BLOCKING -> {
                if (self.stateTime >= Math.max(BLOCK_MIN_TIME, self.guardHoldTime) && self.dezoneTime <= 0)
                    setState(self, CombatState.NEUTRAL);
            }
            case DOUBLE_BLOCK -> {
                if (!self.doubleBlockSwitched && self.stateTime >= self.doubleBlockSwitchTime) {
                    self.blockDirection = self.doubleBlockSecondDirection;
                    self.doubleBlockSwitched = true;
                }
                if (self.stateTime >= Math.max(DOUBLE_BLOCK_MIN_DURATION, self.doubleBlockSwitchTime + 0.12))
                    setState(self, CombatState.NEUTRAL);
            }
            default -> { }
        }
    }

    private void updateEngagementPlan(Musketeer self, Musketeer enemy, double dt) {
        self.engagementTime -= dt;
        var distance = Math.hypot(enemy.x - self.x, enemy.y - self.y);
        switch (self.engagementPhase) {
            case PROBE -> {
                if (self.engagementTime > 0 || distance < 0.95) return;
                var commitment = Math.clamp(0.18 + self.stats.engagementAbility() * 0.005 +
                        (self.tactic == Tactic.AGGRESSIVE ? 0.18 : self.tactic == Tactic.DEFENSIVE ? -0.12 : 0),
                        0.12, 0.86);
                if (random.nextDouble() < commitment) beginEngagementPhase(self, EngagementPhase.COMMIT);
                else self.engagementTime = 0.35 + random.nextDouble() * 0.55;
            }
            case COMMIT -> {
                if (self.state == CombatState.RECOVERY || self.state == CombatState.BLOCK_STUN)
                    beginEngagementPhase(self, EngagementPhase.DISENGAGE);
                else if (self.isAttacking() && self.stateTime >= self.activeTime() * 0.30 || distance < 0.78)
                    beginEngagementPhase(self, EngagementPhase.PASS_THROUGH);
                else if (self.engagementTime <= 0 && self.state == CombatState.NEUTRAL)
                    beginEngagementPhase(self, EngagementPhase.PROBE);
            }
            case PASS_THROUGH -> {
                if (self.engagementTime <= 0 || self.state == CombatState.RECOVERY ||
                        self.state == CombatState.BLOCK_STUN)
                    beginEngagementPhase(self, EngagementPhase.DISENGAGE);
            }
            case DISENGAGE -> {
                if (self.engagementTime <= 0 || distance >= 2.20)
                    beginEngagementPhase(self, EngagementPhase.RESET);
            }
            case RESET -> {
                if (self.engagementTime <= 0) beginEngagementPhase(self, EngagementPhase.PROBE);
            }
        }
    }

    private void beginEngagementPhase(Musketeer self, EngagementPhase phase) {
        self.engagementPhase = phase;
        if (phase == EngagementPhase.COMMIT || phase == EngagementPhase.PASS_THROUGH) {
            var leftScore = movementSpace(self, -1);
            var rightScore = movementSpace(self, 1);
            var correctSide = leftScore >= rightScore ? -1 : 1;
            self.engagementSide = operationSucceeds(self.stats.engagementAbility()) ? correctSide : -correctSide;
        }
        self.engagementTime = switch (phase) {
            case PROBE -> 0.45 + random.nextDouble() * 0.75;
            case COMMIT -> 0.65 + random.nextDouble() * 0.55;
            case PASS_THROUGH -> 0.26 + random.nextDouble() * 0.25;
            case DISENGAGE -> 0.42 + random.nextDouble() * 0.38;
            case RESET -> 0.30 + random.nextDouble() * 0.48;
        };
    }

    private double movementSpace(Musketeer self, int side) {
        var sidewaysAngle = self.angle + side * Math.PI / 2;
        var candidateX = Math.clamp(self.x + Math.cos(sidewaysAngle) * 1.15,
                Musketeer.BODY_RADIUS, ARENA_WIDTH - Musketeer.BODY_RADIUS);
        var candidateY = Math.clamp(self.y + Math.sin(sidewaysAngle) * 1.15,
                Musketeer.BODY_RADIUS, ARENA_HEIGHT - Musketeer.BODY_RADIUS);
        return Math.min(Math.min(candidateX, ARENA_WIDTH - candidateX),
                Math.min(candidateY, ARENA_HEIGHT - candidateY));
    }

    private boolean applyForcedEngagementDecision(Musketeer self) {
        switch (self.engagementPhase) {
            case PASS_THROUGH -> self.decision = self.engagementSide < 0 ? Decision.PASS_LEFT : Decision.PASS_RIGHT;
            case DISENGAGE -> self.decision = Decision.DISENGAGE;
            case RESET -> self.decision = Decision.RESET_GUARD;
            default -> { return false; }
        }
        return true;
    }

    private boolean applyPlannedEngagement(Musketeer self, Musketeer enemy, double distance) {
        if (self.engagementPhase == EngagementPhase.COMMIT) {
            if (distance <= 2.05 && distance >= 1.10) beginAttack(self, enemy, distance);
            else self.decision = self.engagementSide < 0 ? Decision.COMMIT_LEFT : Decision.COMMIT_RIGHT;
            return true;
        }
        if (self.engagementPhase != EngagementPhase.PROBE || distance <= 1.25) return false;
        var guardChance = (self.stats.defenseTiming() + self.stats.decisionMaking()) / 2200.0;
        if (random.nextDouble() < guardChance) beginAnticipatoryBlock(self, enemy);
        else self.decision = self.engagementSide < 0 ? Decision.PROBE_LEFT : Decision.PROBE_RIGHT;
        return true;
    }

    private void chooseDecision(Musketeer self, Musketeer enemy) {
        var enemyVisible = isVisible(self, enemy);
        var dx = knownX(self, enemy) - self.x;
        var dy = knownY(self, enemy) - self.y;
        var distance = Math.hypot(dx, dy);
        var perceivedDistance = distance + random.nextGaussian() * (100 - self.stats.distanceControl()) * 0.006;
        var enemyAttack = enemyVisible && (enemy.state == CombatState.ATTACK_PREPARE ||
                enemy.state == CombatState.ATTACK_HOLD || enemy.state == CombatState.CHAMBER_HOLD ||
                enemy.isAttacking());
        var enemyThreat = enemyAttack && !enemy.baitedAttack;
        var enemyFacing = Math.abs(angleDifference(enemy.weaponWorldAngle(), Math.atan2(-dy, -dx))) < Math.toRadians(34);
        var immediateDanger = enemyThreat && enemyFacing &&
                perceivedDistance < enemy.projectedThrustLength() + Musketeer.BODY_RADIUS + 0.25;
        var angleToSelf = Math.atan2(-dy, -dx);
        var visibleBait = enemyVisible && enemy.baitTime > 0 &&
                Math.abs(angleDifference(enemy.weaponWorldAngle(), angleToSelf)) > Math.toRadians(18);
        var readableEnemyAttack = enemyVisible && enemy.chamberType == ChamberType.NONE && !enemy.baitedAttack &&
                (enemy.state == CombatState.ATTACK_HOLD || enemy.isAttacking() &&
                        Math.abs(enemy.weaponYaw) < Math.toRadians(12));

        if (immediateDanger && !self.threatSeen) {
            self.threatSeen = true;
            self.reactionTime = 0.10 + (100 - self.stats.reaction()) * 0.0045 + random.nextDouble() * 0.07;
        }
        if (!immediateDanger) self.threatSeen = false;
        if (self.reactionTime > 0) self.reactionTime -= DECISION_INTERVAL;
        if (enemyAttack) stopBait(self);

        var enemyBehind = isKnownRear(self, enemy);
        var rearDanger = isImmediateRearDanger(self);
        if (rearDanger && self.rearReactionTime <= 0 &&
                (self.state == CombatState.NEUTRAL || self.isGuarding())) {
            if (self.state == CombatState.NEUTRAL && immediateDanger && !enemyBehind) beginBlock(self, enemy);
            if (self.backEscapeTime <= 0) beginBackEscape(self);
            else setBackEscapeDecision(self);
            return;
        }

        if (immediateDanger && !enemyBehind && self.reactionTime <= 0 && self.state == CombatState.NEUTRAL) {
            var defenseCommitment = Math.clamp(self.stats.defenseTiming() + self.tactic.defenseCommitmentBonus,
                    0, 125);
            if (enemy.isAttacking() || perceivedDistance < 1.72 ||
                    random.nextInt(125) < defenseCommitment) {
                var chamberChance = Math.min(1, self.stats.chamber() / 100.0 *
                        self.tactic.chamberMultiplier(readableEnemyAttack));
                if (enemy.chamberType == ChamberType.SIMPLE && enemy.chamberChain > 0)
                    chamberChance *= Math.max(MIN_RECHAMBER_FACTOR,
                            RECHAMBER_BASE_FACTOR - RECHAMBER_CHAIN_PENALTY * enemy.chamberChain);
                if (random.nextDouble() < chamberChance) beginChamberAttempt(self, enemy);
                else beginBlock(self, enemy);
            } else self.decision = defensiveMovement(self, perceivedDistance);
            return;
        }
        if (self.freeKillAttempt && self.state != CombatState.RECOVERY &&
                self.state != CombatState.BLOCK_STUN) {
            self.decision = Decision.FREE_KILL_BACKSTAB;
            if (self.state == CombatState.ATTACK_HOLD) startAttack(self, CombatState.ATTACK_ACTIVE);
            return;
        }
        if (self.backEscapeTime > 0 &&
                (self.state == CombatState.NEUTRAL || self.isGuarding())) {
            setBackEscapeDecision(self);
            return;
        }
        if (!immediateDanger && self.rearThreat != null && self.rearReactionTime <= 0 &&
                self.state == CombatState.NEUTRAL &&
                knownDistance(self, self.rearThreat) < 3.5 &&
                random.nextDouble() < self.stats.backProtection() / 650.0) {
            beginBackEscape(self);
            return;
        }
        if (self.state == CombatState.NEUTRAL) {
            var freeKillTarget = findFreeKillTarget(self);
            if (freeKillTarget != null && random.nextDouble() < freeKillChoiceChance(self)) {
                beginFreeKillAttack(self, freeKillTarget);
                return;
            }
        }
        if (self.dezoneTime > 0 && nearBoundary(self)) {
            endDezone(self);
            self.decision = boundaryEscape(self);
            return;
        }
        if (self.state == CombatState.NEUTRAL && enemyVisible && enemy.baitedAttack) {
            beginBaitPunish(self, distance);
            return;
        }
        if (self.state == CombatState.NEUTRAL && self.dezoneCooldown <= 0 && !nearBoundary(self)) {
            var switchTarget = findTargetSwitchDezoneTarget(self, enemy);
            if (switchTarget != null && random.nextDouble() < targetSwitchDezoneChance(self)) {
                beginTargetSwitchDezone(self, enemy, switchTarget, distance);
                return;
            }
        }
        if (self.state == CombatState.ATTACK_HOLD) {
            if (self.dezoneSwitchCommitted) {
                self.decision = Decision.DEZONE_SWITCH_ATTACK;
                var facing = Math.abs(angleDifference(self.weaponWorldAngle(), Math.atan2(dy, dx)));
                var releaseSkill = Math.clamp(self.stats.attackTiming() + self.stats.decisionMaking() / 3 +
                        self.tactic.releaseBonus, 0, 140);
                if (perceivedDistance < self.projectedThrustLength() + Musketeer.BODY_RADIUS + 0.18 &&
                        facing < 0.30 && random.nextInt(140) < releaseSkill)
                    startAttack(self, CombatState.ATTACK_ACTIVE);
                return;
            }
            if (self.punishingBait) {
                self.decision = Decision.PUNISH_BAIT;
                if (enemy.state == CombatState.RECOVERY || !enemy.baitedAttack)
                    startAttack(self, CombatState.ATTACK_ACTIVE);
                return;
            }
            if (self.exploitingBait) {
                self.decision = Decision.EXPLOIT_BAIT;
                var facing = Math.abs(angleDifference(self.weaponWorldAngle(), Math.atan2(dy, dx)));
                if (distance <= self.projectedThrustLength() + Musketeer.BODY_RADIUS - 0.08 && facing < 0.30)
                    startAttack(self, CombatState.ATTACK_ACTIVE);
                return;
            }
            if (self.dezoneTime > 0) {
                setDezoneDecision(self);
                return;
            }
            var facing = Math.abs(angleDifference(self.weaponWorldAngle(), Math.atan2(dy, dx)));
            if (shouldFeintToBlock(self, enemy, enemyThreat, perceivedDistance)) {
                beginFeint(self, true);
                return;
            }
            if (shouldChangeAttack(self, enemy)) {
                beginFeint(self, false);
                return;
            }
            var goodChance = Math.clamp(self.stats.attackTiming() + self.stats.decisionMaking() / 3 +
                    self.tactic.releaseBonus, 0, 140);
            if (perceivedDistance < self.projectedThrustLength() + Musketeer.BODY_RADIUS + 0.18 &&
                    facing < 0.30 && random.nextInt(140) < goodChance) {
                self.decision = Decision.RELEASE_ATTACK;
                startAttack(self, CombatState.ATTACK_ACTIVE);
            } else if (perceivedDistance > self.tactic.holdAdvanceDistance) self.decision = Decision.APPROACH;
            else if (self.tactic == Tactic.DEFENSIVE && random.nextInt(100) < 45)
                self.decision = Decision.WAIT;
            else self.decision = randomStrafe();
            return;
        }
        if (self.state == CombatState.BLOCKING) {
            if (enemyAttack && !self.blockDirection.blocks(enemy.attackDirection) &&
                    self.guardSwitchCooldown <= 0 && operationSucceeds(self.stats.defenseTiming())) {
                switchGuard(self, enemy.attackDirection);
                return;
            }
            if (enemyVisible && enemy.state == CombatState.RECOVERY && self.stateTime >= BLOCK_MIN_TIME &&
                    random.nextInt(220) < self.stats.attackTiming() + self.stats.aggression()) {
                setState(self, CombatState.NEUTRAL);
                beginAttack(self, enemy, perceivedDistance);
                self.decision = Decision.RIPOSTE;
                return;
            }
            if (self.dezoneTime > 0) setDezoneDecision(self);
            else if (!immediateDanger && self.engagementPhase == EngagementPhase.PROBE)
                self.decision = self.engagementSide < 0 ? Decision.PROBE_LEFT : Decision.PROBE_RIGHT;
            else if (!immediateDanger && self.engagementPhase == EngagementPhase.RESET)
                self.decision = Decision.RESET_GUARD;
            else self.decision = Decision.BLOCK;
            return;
        }
        if (self.state == CombatState.BLOCK_PREPARE) {
            self.decision = self.guardSwitching ? Decision.SWITCH_GUARD : Decision.BLOCK;
            return;
        }
        if (self.state == CombatState.FEINT) return;
        if (self.state != CombatState.NEUTRAL) {
            if (self.dezoneSwitchTarget != null && !self.dezoneSwitchCommitted) setDezoneDecision(self);
            else if (self.dezoneSwitchCommitted) self.decision = Decision.DEZONE_SWITCH_ATTACK;
            else if (self.baitedAttack) self.decision = Decision.FALL_FOR_BAIT;
            else if (self.punishingBait) self.decision = Decision.PUNISH_BAIT;
            else if (self.exploitingBait) self.decision = Decision.EXPLOIT_BAIT;
            else if (self.state == CombatState.DOUBLE_BLOCK) self.decision = Decision.DOUBLE_BLOCK;
            else if (self.state == CombatState.BLOCKING && self.dezoneTime > 0) setDezoneDecision(self);
            else if (self.state == CombatState.BLOCKING) self.decision = Decision.BLOCK;
            else if (self.state == CombatState.CHAMBER_PREPARE || self.state == CombatState.CHAMBER_HOLD ||
                    self.state == CombatState.CHAMBER_ACTIVE) self.decision = Decision.CHAMBER;
            else if (self.state == CombatState.BLOCK_STUN) self.decision = Decision.WAIT;
            else if (applyForcedEngagementDecision(self)) { }
            else self.decision = Decision.RETREAT;
            return;
        }
        if (self.dezoneTime > 0) {
            setDezoneDecision(self);
            return;
        }
        if (self.baitTime > 0 && !enemyThreat) {
            self.decision = self.baitOffset < 0 ? Decision.BAIT_LEFT : Decision.BAIT_RIGHT;
            return;
        }
        if (applyForcedEngagementDecision(self)) return;
        if (perceivedDistance < 1.25) self.decision = defensiveMovement(self, perceivedDistance);
        else if (enemyThreat) self.decision = defensiveMovement(self, perceivedDistance);
        else if (nearBoundary(self)) self.decision = boundaryEscape(self);
        else if (visibleBait && self.observedBaitSequence != enemy.baitSequence &&
                distance > 1.38 && distance < 1.82) {
            self.observedBaitSequence = enemy.baitSequence;
            if (!operationSucceeds(self.stats.distanceControl())) beginBaitedAttack(self, enemy, distance);
            else if (random.nextInt(300) < Math.clamp(self.stats.decisionMaking() + self.stats.footwork() +
                    self.stats.aggression() + self.tactic.pressureBonus, 0, 300))
                beginBaitAdvanceAttack(self, enemy, distance);
            else self.decision = randomStrafe();
        }
        else if (enemyVisible && enemy.state == CombatState.RECOVERY && perceivedDistance < 2.02)
            beginAttack(self, perceivedDistance);
        else if (enemyVisible && enemy.state == CombatState.RECOVERY) self.decision = switch (self.tactic) {
            case DEFENSIVE -> random.nextInt(100) < 70 ? Decision.WAIT : randomStrafe();
            case NEUTRAL -> random.nextBoolean() ? randomStrafe() : Decision.WAIT;
            case AGGRESSIVE -> Decision.APPROACH;
        };
        else if (enemyVisible && enemy.dezoneTime > 0 && enemy.dezoneStance != DezoneStance.HOLD &&
                perceivedDistance > 1.45 &&
                random.nextInt(100) < Math.clamp((self.stats.aggression() + self.stats.decisionMaking()) / 2 +
                        self.tactic.followBonus, 0, 100))
            self.decision = Decision.APPROACH;
        else if (self.dezoneCooldown <= 0 && enemyVisible && enemy.state == CombatState.NEUTRAL &&
                perceivedDistance > 1.30 && perceivedDistance < 2.80 &&
                random.nextDouble() * 1000 < (self.stats.zoneExit() + self.stats.footwork() +
                        self.stats.decisionMaking()) / 3.0 * self.tactic.dezoneMultiplier)
            beginDezone(self, perceivedDistance);
        else if (enemyVisible && enemy.state == CombatState.NEUTRAL &&
                perceivedDistance > 1.50 && perceivedDistance < 2.05 &&
                random.nextDouble() * 1000 < (self.stats.decisionMaking() + self.stats.distanceControl()) / 4.0 *
                        self.tactic.baitMultiplier)
            beginBait(self);
        else if (applyPlannedEngagement(self, enemy, perceivedDistance)) { }
        else if (perceivedDistance > self.tactic.approachDistance) {
            if (self.tactic == Tactic.DEFENSIVE && random.nextInt(100) < 70)
                self.decision = random.nextInt(100) < 65 ? randomStrafe() : Decision.WAIT;
            else self.decision = Decision.APPROACH;
        }
        else if (perceivedDistance > 1.38 && perceivedDistance < 1.82 &&
                random.nextDouble() * 1000 < (self.stats.aggression() + self.stats.decisionMaking()) / 3.0 *
                        self.tactic.attackMultiplier)
            beginAttack(self, perceivedDistance);
        else self.decision = switch (self.tactic) {
            case DEFENSIVE -> random.nextInt(100) < 55 ? Decision.WAIT : randomStrafe();
            case NEUTRAL -> random.nextInt(100) < 78 ? randomStrafe() : Decision.WAIT;
            case AGGRESSIVE -> random.nextInt(100) < 90 ? randomStrafe() : Decision.WAIT;
        };
    }

    private Musketeer findFreeKillTarget(Musketeer self) {
        Musketeer best = null;
        var bestDistance = Double.POSITIVE_INFINITY;
        for (var candidate : opponentsOf(self)) {
            if (!candidate.alive || !isVisible(self, candidate) || !isRearAttack(candidate, self)) continue;
            var towardCandidate = Math.atan2(candidate.y - self.y, candidate.x - self.x);
            if (Math.abs(angleDifference(self.weaponWorldAngle(), towardCandidate)) > Math.toRadians(100))
                continue;
            var perceivedDistance = Math.max(0, Math.hypot(candidate.x - self.x, candidate.y - self.y) +
                    random.nextGaussian() * (100 - self.stats.distanceControl()) * 0.006);
            if (perceivedDistance > CombatDirection.UP.thrustLength + Musketeer.BODY_RADIUS + 0.10 ||
                    !believesDirectOpponentIsSafe(self, candidate)) continue;
            if (perceivedDistance < bestDistance) {
                best = candidate;
                bestDistance = perceivedDistance;
            }
        }
        self.freeKillPerceivedDistance = best == null ? 0 : bestDistance;
        return best;
    }

    private boolean believesDirectOpponentIsSafe(Musketeer self, Musketeer backstabTarget) {
        var directOpponent = self.target != backstabTarget && self.target != null && self.target.alive &&
                isTracked(self, self.target) ? self.target : closestTrackedOpponentExcept(self, backstabTarget);
        if (directOpponent == null) return true;
        var perceivedDistance = Math.max(0, knownDistance(self, directOpponent) +
                random.nextGaussian() * (100 - self.stats.distanceControl()) * 0.006);
        var dangerDistance = directOpponent.projectedThrustLength() + Musketeer.BODY_RADIUS + 0.55;
        if (isVisible(self, directOpponent) && (directOpponent.state == CombatState.ATTACK_PREPARE ||
                directOpponent.state == CombatState.ATTACK_HOLD || directOpponent.isAttacking()))
            dangerDistance += 0.35;
        else if (isVisible(self, directOpponent) &&
                (directOpponent.state == CombatState.RECOVERY || directOpponent.state == CombatState.BLOCK_STUN))
            dangerDistance -= 0.30;
        return perceivedDistance > dangerDistance;
    }

    private Musketeer closestTrackedOpponentExcept(Musketeer self, Musketeer excluded) {
        Musketeer closest = null;
        var closestDistance = Double.POSITIVE_INFINITY;
        for (var opponent : opponentsOf(self)) {
            if (!opponent.alive || opponent == excluded || !isTracked(self, opponent)) continue;
            var distance = knownDistance(self, opponent);
            if (distance < closestDistance) {
                closest = opponent;
                closestDistance = distance;
            }
        }
        return closest;
    }

    private double freeKillChoiceChance(Musketeer self) {
        var readingSkill = (self.stats.decisionMaking() * 0.45 + self.stats.distanceControl() * 0.35 +
                self.stats.aggression() * 0.20) / 100.0;
        var tacticMultiplier = switch (self.tactic) {
            case DEFENSIVE -> 0.75;
            case NEUTRAL -> 1.0;
            case AGGRESSIVE -> 1.25;
        };
        return Math.min(0.24, (0.01 + 0.14 * readingSkill * readingSkill) * tacticMultiplier);
    }

    private void beginFreeKillAttack(Musketeer self, Musketeer target) {
        stopBait(self);
        endDezone(self);
        self.backEscapeTime = 0;
        changeTarget(self, target, 1.0);
        self.attackDirection = self.freeKillPerceivedDistance > 1.42 ?
                CombatDirection.UP : CombatDirection.DOWN;
        configureFreeKillAim(self);
        self.freeKillAttempt = true;
        self.attackPitch = self.pitch;
        self.decision = Decision.FREE_KILL_BACKSTAB;
        setState(self, CombatState.ATTACK_PREPARE);
    }

    private void configureFreeKillAim(Musketeer self) {
        self.attackReachScale = 1;
        self.baitedAttack = false;
        self.punishingBait = false;
        self.exploitingBait = false;
        self.intendedZone = HitZone.TORSO;
        var accurate = operationSucceeds(self.stats.aim());
        self.attackHeight = HitZone.TORSO.centerHeight() +
                random.nextGaussian() * (0.025 + (100 - self.stats.aim()) * 0.0045);
        if (accurate) self.aimError = random.nextGaussian() * (101 - self.stats.aim()) * 0.0009;
        else if (random.nextBoolean()) {
            self.attackHeight -= 0.45 + random.nextDouble() * 0.75;
            self.aimError = random.nextGaussian() * 0.10;
        } else {
            self.attackHeight += random.nextGaussian() * 0.55;
            self.aimError = (random.nextBoolean() ? 1 : -1) * (0.22 + random.nextDouble() * 0.38);
        }
        self.desiredPitch = Math.clamp(Math.atan2(self.attackHeight - WEAPON_HEIGHT,
                Math.max(0.25, self.freeKillPerceivedDistance)), -Musketeer.MAX_PITCH, Musketeer.MAX_PITCH);
        self.attackResolved = false;
    }

    private Decision defensiveMovement(Musketeer self, double distance) {
        if (nearBoundary(self) && distance > 1.28) return boundaryEscape(self);
        return switch (self.tactic) {
            case DEFENSIVE -> distance < 1.35 || random.nextInt(100) < 60 ? Decision.RETREAT : randomStrafe();
            case NEUTRAL -> distance < 1.55 ? Decision.RETREAT : randomStrafe();
            case AGGRESSIVE -> distance < 1.30 ? Decision.RETREAT : randomStrafe();
        };
    }

    private boolean nearBoundary(Musketeer self) {
        return self.x < 0.78 || self.x > ARENA_WIDTH - 0.78 || self.y < 0.78 || self.y > ARENA_HEIGHT - 0.78;
    }

    private Decision boundaryEscape(Musketeer self) {
        return movementSpace(self, -1) >= movementSpace(self, 1) ?
                Decision.EDGE_ESCAPE_LEFT : Decision.EDGE_ESCAPE_RIGHT;
    }

    private boolean isImmediateRearDanger(Musketeer self) {
        var threat = self.rearThreat;
        if (threat == null || !threat.alive || !isVisible(self, threat) || !isRearAttack(self, threat)) return false;
        var attacking = threat.state == CombatState.ATTACK_PREPARE || threat.state == CombatState.ATTACK_HOLD ||
                threat.state == CombatState.CHAMBER_HOLD || threat.isAttacking();
        var towardSelf = Math.atan2(self.y - threat.y, self.x - threat.x);
        return attacking && !threat.baitedAttack &&
                Math.abs(angleDifference(threat.weaponWorldAngle(), towardSelf)) < Math.toRadians(34) &&
                Math.hypot(threat.x - self.x, threat.y - self.y) <
                        threat.projectedThrustLength() + Musketeer.BODY_RADIUS + 0.45;
    }

    private void beginBackEscape(Musketeer self) {
        stopBait(self);
        endDezone(self);
        self.backEscapeDirection = chooseBackEscapeDirection(self);
        self.backEscapeTime = 0.40 + self.stats.footwork() * 0.0035 + self.stats.zoneExit() * 0.0015;
        setBackEscapeDecision(self);
    }

    private void setBackEscapeDecision(Musketeer self) {
        self.decision = self.backEscapeDirection < 0 ?
                Decision.PROTECT_BACK_LEFT : Decision.PROTECT_BACK_RIGHT;
    }

    private int chooseBackEscapeDirection(Musketeer self) {
        var bestDirection = backEscapeScore(self, -1) > backEscapeScore(self, 1) ? -1 : 1;
        var correctChoiceChance = 0.30 + self.stats.backProtection() * 0.0065;
        return random.nextDouble() < correctChoiceChance ? bestDirection : -bestDirection;
    }

    private double backEscapeScore(Musketeer self, int direction) {
        var horizon = 0.80;
        var candidateX = Math.clamp(self.x +
                (Math.cos(self.angle) * 0.55 - Math.sin(self.angle) * direction * 0.85) * horizon,
                Musketeer.BODY_RADIUS, ARENA_WIDTH - Musketeer.BODY_RADIUS);
        var candidateY = Math.clamp(self.y +
                (Math.sin(self.angle) * 0.55 + Math.cos(self.angle) * direction * 0.85) * horizon,
                Musketeer.BODY_RADIUS, ARENA_HEIGHT - Musketeer.BODY_RADIUS);
        var boundarySpace = Math.min(Math.min(candidateX, ARENA_WIDTH - candidateX),
                Math.min(candidateY, ARENA_HEIGHT - candidateY));
        var minimumDistance = Double.POSITIVE_INFINITY;
        Musketeer first = null, second = null;
        for (var opponent : opponentsOf(self)) {
            if (!opponent.alive || !isTracked(self, opponent)) continue;
            var distance = Math.hypot(knownX(self, opponent) - candidateX,
                    knownY(self, opponent) - candidateY);
            minimumDistance = Math.min(minimumDistance, distance);
            if (first == null) first = opponent;
            else second = opponent;
        }
        var separation = 0.0;
        if (first != null && second != null) {
            var firstAngle = Math.atan2(knownY(self, first) - candidateY, knownX(self, first) - candidateX);
            var secondAngle = Math.atan2(knownY(self, second) - candidateY, knownX(self, second) - candidateX);
            separation = Math.abs(angleDifference(firstAngle, secondAngle));
        }
        if (!Double.isFinite(minimumDistance)) minimumDistance = 0;
        return minimumDistance * 0.30 + boundarySpace * 0.18 - separation * 1.40;
    }

    private Musketeer findTargetSwitchDezoneTarget(Musketeer self, Musketeer currentTarget) {
        if (currentTarget == null || !currentTarget.alive) return null;
        var teammate = teammateOf(self);
        if (teammate == null || !teammate.alive) return null;
        Musketeer best = null;
        var bestScore = Double.POSITIVE_INFINITY;
        for (var candidate : opponentsOf(self)) {
            if (candidate == currentTarget || !candidate.alive || !isVisible(self, candidate)) continue;
            var distance = Math.hypot(candidate.x - self.x, candidate.y - self.y);
            if (distance < 0.90 || distance > TARGET_SWITCH_MAX_DISTANCE) continue;
            var supportsTeammate = teammate.target == candidate || candidate.target == teammate;
            var opening = candidate.state == CombatState.RECOVERY || candidate.state == CombatState.BLOCK_STUN ||
                    isRearAttack(candidate, self);
            if (!supportsTeammate && !opening) continue;
            if (!believesDirectOpponentIsSafe(self, candidate) &&
                    operationSucceeds(self.stats.decisionMaking())) continue;
            var targetAngle = Math.atan2(candidate.y - self.y, candidate.x - self.x);
            var score = distance + Math.abs(angleDifference(self.angle, targetAngle)) * 0.22;
            if (supportsTeammate) score -= 0.55;
            if (opening) score -= 0.45;
            score += random.nextGaussian() * (101 - self.stats.decisionMaking()) * 0.003;
            if (score < bestScore) {
                best = candidate;
                bestScore = score;
            }
        }
        return best;
    }

    private double targetSwitchDezoneChance(Musketeer self) {
        var ability = self.stats.targetSwitchDezoneAbility() / 100.0;
        var tacticMultiplier = switch (self.tactic) {
            case DEFENSIVE -> 0.65;
            case NEUTRAL -> 1.0;
            case AGGRESSIVE -> 1.35;
        };
        return Math.min(0.11, (0.012 + 0.065 * ability * ability) * tacticMultiplier);
    }

    private void beginTargetSwitchDezone(Musketeer self, Musketeer currentTarget, Musketeer switchTarget,
                                         double currentDistance) {
        stopBait(self);
        var ability = self.stats.targetSwitchDezoneAbility();
        self.dezoneOriginalTarget = currentTarget;
        self.dezoneSwitchTarget = switchTarget;
        self.dezoneSwitchCommitted = false;
        self.dezoneDangerTime = 0;
        self.dezoneTime = 0.72 - 0.0025 * ability + random.nextDouble() * 0.20;
        self.dezoneCooldown = self.dezoneTime + 0.85 + random.nextDouble() * 0.45;
        var switchAngle = Math.atan2(switchTarget.y - self.y, switchTarget.x - self.x);
        var right = angleDifference(self.angle, switchAngle) >= 0;
        if (random.nextDouble() >= 0.30 + ability * 0.0068) right = !right;
        self.dezoneMovement = right ? DezoneMovement.TARGET_SWITCH_RIGHT :
                DezoneMovement.TARGET_SWITCH_LEFT;

        var defenseWeight = self.stats.defenseTiming() + (self.tactic == Tactic.DEFENSIVE ? 40 : 0);
        var attackWeight = self.stats.attackTiming() + (self.tactic == Tactic.AGGRESSIVE ? 40 : 0);
        var stanceRoll = random.nextInt(100 + defenseWeight + attackWeight);
        if (stanceRoll < defenseWeight) {
            self.dezoneStance = DezoneStance.BLOCK;
            beginBlock(self, currentTarget);
        } else if (stanceRoll < defenseWeight + attackWeight) {
            self.dezoneStance = DezoneStance.HOLD;
            beginAttack(self, currentDistance);
        } else self.dezoneStance = DezoneStance.NEUTRAL;
        setDezoneDecision(self);
    }

    private void updateTargetSwitchDezone(Musketeer self, double dt) {
        var switchTarget = self.dezoneSwitchTarget;
        if (switchTarget == null) return;
        var originalTarget = self.dezoneOriginalTarget;
        if (!switchTarget.alive && self.dezoneSwitchCommitted && self.isAttacking()) {
            clearTargetSwitchDezone(self);
            return;
        }
        if (!switchTarget.alive || !self.dezoneSwitchCommitted && !isVisible(self, switchTarget)) {
            cancelTargetSwitchDezone(self, false);
            return;
        }
        if (originalTarget == null || !originalTarget.alive) {
            self.dezoneDangerTime = 0;
            return;
        }
        if (!isVisible(self, originalTarget)) {
            self.dezoneDangerTime = 0;
            return;
        }
        var distance = Math.hypot(originalTarget.x - self.x, originalTarget.y - self.y);
        var attacking = originalTarget.state == CombatState.ATTACK_PREPARE ||
                originalTarget.state == CombatState.ATTACK_HOLD ||
                originalTarget.state == CombatState.CHAMBER_HOLD || originalTarget.isAttacking();
        var towardSelf = Math.atan2(self.y - originalTarget.y, self.x - originalTarget.x);
        var dangerousAttack = attacking && !originalTarget.baitedAttack &&
                Math.abs(angleDifference(originalTarget.weaponWorldAngle(), towardSelf)) < Math.toRadians(34) &&
                distance < originalTarget.projectedThrustLength() + Musketeer.BODY_RADIUS + 0.52;
        var pursuit = originalTarget.target == self && distance < 1.42 &&
                originalTarget.state != CombatState.RECOVERY && originalTarget.state != CombatState.BLOCK_STUN;
        if (!dangerousAttack && !pursuit) {
            self.dezoneDangerTime = 0;
            return;
        }
        self.dezoneDangerTime += dt;
        var responseDelay = 0.07 + (100 - self.stats.reaction()) * 0.0028;
        if (self.dezoneDangerTime < responseDelay || self.isAttacking()) return;
        cancelTargetSwitchDezone(self, true);
    }

    private void commitTargetSwitchDezone(Musketeer self) {
        var switchTarget = self.dezoneSwitchTarget;
        if (switchTarget == null || !switchTarget.alive || !isVisible(self, switchTarget)) {
            cancelTargetSwitchDezone(self, false);
            return;
        }
        self.dezoneSwitchCommitted = true;
        clearDezoneMovement(self);
        changeTarget(self, switchTarget, 1.10);
        var distance = Math.hypot(switchTarget.x - self.x, switchTarget.y - self.y);
        configureTargetSwitchAttack(self, switchTarget, distance);
        if (self.state != CombatState.ATTACK_PREPARE && self.state != CombatState.ATTACK_HOLD) {
            if (self.isGuarding()) setState(self, CombatState.NEUTRAL);
            setState(self, CombatState.ATTACK_PREPARE);
        }
        self.decision = Decision.DEZONE_SWITCH_ATTACK;
    }

    private void configureTargetSwitchAttack(Musketeer self, Musketeer switchTarget, double distance) {
        var direction = distance > 1.48 ? CombatDirection.UP : CombatDirection.DOWN;
        var teammate = teammateOf(self);
        if (teammate != null && teammate.alive && teammate.target == switchTarget &&
                (teammate.state == CombatState.ATTACK_PREPARE || teammate.state == CombatState.ATTACK_HOLD ||
                        teammate.isAttacking()) && operationSucceeds(self.stats.decisionMaking()))
            direction = teammate.attackDirection.opposite();
        self.attackDirection = direction;
        configureAim(self, distance);
        self.attackPitch = self.pitch;
        self.baseAttackPitch = self.pitch;
    }

    private void cancelTargetSwitchDezone(Musketeer self, boolean threatened) {
        var originalTarget = self.dezoneOriginalTarget;
        if (self.state == CombatState.ATTACK_PREPARE || self.state == CombatState.ATTACK_HOLD)
            setState(self, CombatState.NEUTRAL);
        clearDezoneMovement(self);
        clearTargetSwitchDezone(self);
        if (originalTarget == null || !originalTarget.alive) {
            self.decision = Decision.WAIT;
            self.decisionTime = 0;
            return;
        }
        changeTarget(self, originalTarget, 0.55);
        if (!threatened) {
            self.decision = Decision.WAIT;
            return;
        }
        if (isKnownRear(self, originalTarget)) beginBackEscape(self);
        else if (originalTarget.state == CombatState.ATTACK_PREPARE ||
                originalTarget.state == CombatState.ATTACK_HOLD || originalTarget.isAttacking())
            beginBlock(self, originalTarget);
        else self.decision = Decision.DEZONE_SWITCH_ABORT;
    }

    private void beginDezone(Musketeer self, double distance) {
        stopBait(self);
        self.dezoneTime = 0.55 + 0.005 * self.stats.zoneExit() + random.nextDouble() * 0.45;
        self.dezoneCooldown = self.dezoneTime + 0.80 + random.nextDouble() * 0.50;
        if (distance <= 2.20 && random.nextDouble() < 0.45 - 0.0035 * self.stats.zoneExit())
            self.dezoneMovement = DezoneMovement.BACKWARD;
        else {
            var left = random.nextBoolean();
            if (random.nextDouble() < 0.45 + 0.004 * self.stats.zoneExit())
                self.dezoneMovement = left ? DezoneMovement.ADVANCE_LEFT : DezoneMovement.ADVANCE_RIGHT;
            else self.dezoneMovement = left ? DezoneMovement.ORBIT_LEFT : DezoneMovement.ORBIT_RIGHT;
        }

        var defenseWeight = self.stats.defenseTiming() + (self.tactic == Tactic.DEFENSIVE ? 40 : 0);
        var attackWeight = self.stats.attackTiming() + (self.tactic == Tactic.AGGRESSIVE ? 40 : 0);
        var stanceRoll = random.nextInt(100 + defenseWeight + attackWeight);
        if (stanceRoll < defenseWeight) {
            self.dezoneStance = DezoneStance.BLOCK;
            self.blockDirection = random.nextBoolean() ? BlockDirection.UP : BlockDirection.DOWN;
            setState(self, CombatState.BLOCKING);
        } else if (stanceRoll < defenseWeight + attackWeight) {
            self.dezoneStance = DezoneStance.HOLD;
            beginAttack(self, distance);
        } else self.dezoneStance = DezoneStance.NEUTRAL;
        setDezoneDecision(self);
    }

    private void setDezoneDecision(Musketeer self) {
        self.decision = switch (self.dezoneMovement) {
            case BACKWARD -> Decision.DEZONE_BACKWARD;
            case ADVANCE_LEFT -> Decision.DEZONE_ADVANCE_LEFT;
            case ADVANCE_RIGHT -> Decision.DEZONE_ADVANCE_RIGHT;
            case ORBIT_LEFT -> Decision.DEZONE_ORBIT_LEFT;
            case ORBIT_RIGHT -> Decision.DEZONE_ORBIT_RIGHT;
            case TARGET_SWITCH_LEFT -> Decision.DEZONE_SWITCH_LEFT;
            case TARGET_SWITCH_RIGHT -> Decision.DEZONE_SWITCH_RIGHT;
            case NONE -> Decision.WAIT;
        };
    }

    private void endDezone(Musketeer self) {
        clearDezoneMovement(self);
        clearTargetSwitchDezone(self);
    }

    private void clearDezoneMovement(Musketeer self) {
        self.dezoneTime = 0;
        self.dezoneMovement = DezoneMovement.NONE;
        self.dezoneStance = DezoneStance.NEUTRAL;
    }

    private void clearTargetSwitchDezone(Musketeer self) {
        self.dezoneOriginalTarget = null;
        self.dezoneSwitchTarget = null;
        self.dezoneSwitchCommitted = false;
        self.dezoneDangerTime = 0;
    }

    private void beginAttack(Musketeer self, double distance) {
        beginAttack(self, self.target, distance);
    }

    private void beginAttack(Musketeer self, Musketeer enemy, double distance) {
        stopBait(self);
        self.decision = Decision.PREPARE_ATTACK;
        self.attackDirection = chooseAttackDirection(self, enemy, distance);
        configureAim(self, distance);
        self.attackPitch = self.pitch;
        self.baseAttackPitch = self.pitch;
        setState(self, CombatState.ATTACK_PREPARE);
    }

    private CombatDirection chooseAttackDirection(Musketeer self, Musketeer enemy, double distance) {
        var upScore = (distance - 1.18) * 1.15;
        var downScore = (1.82 - distance) * 0.95;
        var awareness = enemy == null ? null : self.opponentAwareness.get(enemy);
        if (enemy != null && isVisible(self, enemy) && enemy.isGuarding()) {
            if (enemy.blockDirection == BlockDirection.UP) downScore += 1.05;
            else if (enemy.blockDirection == BlockDirection.DOWN) upScore += 1.05;
        }
        if (awareness != null) {
            var observedBlocks = awareness.observedUpBlocks + awareness.observedDownBlocks;
            if (observedBlocks > 0) {
                upScore += awareness.observedDownBlocks / (double) observedBlocks * 0.72;
                downScore += awareness.observedUpBlocks / (double) observedBlocks * 0.72;
            }
        }
        if (self.lastCommittedAttackDirection != null && self.directionStreak >= 2) {
            var mixup = self.stats.decisionMaking() / 100.0 * Math.min(1.0, self.directionStreak / 3.0);
            if (self.lastCommittedAttackDirection == CombatDirection.UP) upScore -= mixup;
            else downScore -= mixup;
        }
        var decisionNoise = random.nextGaussian() * (101 - self.stats.decisionMaking()) * 0.012;
        return upScore + decisionNoise >= downScore ? CombatDirection.UP : CombatDirection.DOWN;
    }

    private void configureAim(Musketeer self, double distance) {
        self.attackReachScale = 1;
        self.baitedAttack = false;
        self.punishingBait = false;
        self.exploitingBait = false;
        var accurate = operationSucceeds(self.stats.aim());
        self.intendedZone = self.attackDirection == CombatDirection.UP &&
                random.nextInt(100) < 12 + self.stats.aim() / 5 ? HitZone.HEAD : HitZone.TORSO;
        self.attackHeight = self.intendedZone.centerHeight() +
                random.nextGaussian() * (0.025 + (100 - self.stats.aim()) * 0.0045);
        if (accurate) self.aimError = random.nextGaussian() * (101 - self.stats.aim()) * 0.0009;
        else if (random.nextBoolean()) {
            self.attackHeight -= 0.45 + random.nextDouble() * 0.75;
            self.aimError = random.nextGaussian() * 0.10;
        } else {
            self.attackHeight += random.nextGaussian() * 0.55;
            self.aimError = (random.nextBoolean() ? 1 : -1) * (0.22 + random.nextDouble() * 0.38);
        }
        self.desiredPitch = Math.clamp(Math.atan2(self.attackHeight - WEAPON_HEIGHT, Math.max(0.25, distance)),
                -Musketeer.MAX_PITCH, Musketeer.MAX_PITCH);
        configureAttackManipulation(self, distance);
        self.attackResolved = false;
    }

    private void configureAttackManipulation(Musketeer self, double distance) {
        var ability = self.stats.manipulationAbility();
        var chance = Math.min(0.72, 0.08 + ability * 0.0062);
        if (random.nextDouble() >= chance) {
            self.attackManipulation = AttackManipulation.NEUTRAL;
            self.manipulationPitchOffset = 0;
            self.manipulationYawOffset = 0;
            return;
        }
        var accelBias = distance < 1.50 ? 0.64 : 0.42;
        if (self.tactic == Tactic.AGGRESSIVE) accelBias += 0.14;
        else if (self.tactic == Tactic.DEFENSIVE) accelBias -= 0.12;
        self.attackManipulation = random.nextDouble() < accelBias ?
                AttackManipulation.ACCEL : AttackManipulation.DRAG;
        var degrees = 7 + ability * 0.13;
        self.manipulationPitchOffset = Math.toRadians(
                self.attackManipulation == AttackManipulation.ACCEL ? -degrees : degrees);
        self.manipulationYawOffset = self.engagementSide * Math.toRadians(4 + ability * 0.075) *
                (self.attackManipulation == AttackManipulation.ACCEL ? -1 : 1);
    }

    private void beginBlock(Musketeer self, Musketeer enemy) {
        stopBait(self);
        if (self.dezoneTime > 0) self.dezoneStance = DezoneStance.BLOCK;
        self.decision = Decision.BLOCK;
        var blockSkill = self.stats.defenseTiming();
        if (enemy.chamberType == ChamberType.SIMPLE && enemy.chamberChain > 0)
            blockSkill = Math.min(100, blockSkill + enemy.chamberChain * CHAMBER_BLOCK_BONUS);
        BlockDirection direction;
        if (operationSucceeds(blockSkill)) direction = BlockDirection.matching(enemy.attackDirection);
        else if (enemy.attackDirection == CombatDirection.UP)
            direction = random.nextBoolean() ? BlockDirection.DOWN : BlockDirection.SIDE;
        else direction = random.nextBoolean() ? BlockDirection.UP : BlockDirection.SIDE;
        prepareGuard(self, direction, false);
    }

    private void beginAnticipatoryBlock(Musketeer self, Musketeer enemy) {
        stopBait(self);
        var awareness = self.opponentAwareness.get(enemy);
        CombatDirection predicted;
        if (awareness == null || awareness.observedUpAttacks == awareness.observedDownAttacks)
            predicted = enemy.attackDirection;
        else predicted = awareness.observedUpAttacks > awareness.observedDownAttacks ?
                CombatDirection.UP : CombatDirection.DOWN;
        self.decision = Decision.BLOCK;
        prepareGuard(self, BlockDirection.matching(predicted), false);
    }

    private void prepareGuard(Musketeer self, BlockDirection direction, boolean switching) {
        self.blockDirection = direction;
        self.guardSwitching = switching;
        self.guardPrepareTime = 0.035 + (100 - self.stats.defenseTiming()) * 0.00115;
        self.guardHoldTime = 0.28 + self.stats.defenseTiming() * 0.006 + random.nextDouble() * 0.18 +
                (self.tactic == Tactic.DEFENSIVE ? 0.25 : self.tactic == Tactic.AGGRESSIVE ? -0.08 : 0);
        ++self.blockSequence;
        setState(self, CombatState.BLOCK_PREPARE);
    }

    private void switchGuard(Musketeer self, CombatDirection attackDirection) {
        self.decision = Decision.SWITCH_GUARD;
        self.guardSwitchCooldown = 0.16 + (100 - self.stats.reaction()) * 0.0016;
        prepareGuard(self, BlockDirection.matching(attackDirection), true);
    }

    private boolean shouldFeintToBlock(Musketeer self, Musketeer enemy, boolean enemyThreat, double distance) {
        if (!enemyThreat || self.feintCooldown > 0 || self.feintCount >= 2 || self.stateTime < 0.08 ||
                distance > enemy.projectedThrustLength() + Musketeer.BODY_RADIUS + 0.35) return false;
        var defense = (self.stats.defenseTiming() + self.stats.reaction() + self.stats.decisionMaking()) / 3.0;
        return random.nextDouble() < 0.04 + defense * 0.0015;
    }

    private boolean shouldChangeAttack(Musketeer self, Musketeer enemy) {
        if (self.feintCooldown > 0 || self.feintCount >= 2 || self.stateTime < 0.10 || enemy == null ||
                !isVisible(self, enemy)) return false;
        var guardReadsAttack = enemy.isGuarding() && enemy.blockDirection.blocks(self.attackDirection);
        var repetition = self.directionStreak >= 2;
        if (!guardReadsAttack && !repetition) return false;
        var chance = 0.025 + self.stats.feintAbility() * 0.00155;
        if (guardReadsAttack) chance += 0.07;
        if (self.tactic == Tactic.AGGRESSIVE) chance *= 1.20;
        else if (self.tactic == Tactic.DEFENSIVE) chance *= 0.75;
        return random.nextDouble() < chance;
    }

    private void beginFeint(Musketeer self, boolean toBlock) {
        ++self.feintCount;
        self.feintToBlock = toBlock;
        self.queuedAttackDirection = self.attackDirection.opposite();
        self.feintDuration = 0.07 + (100 - self.stats.feintAbility()) * 0.00125;
        self.feintCooldown = 0.34 + random.nextDouble() * 0.22;
        self.attackManipulation = AttackManipulation.NEUTRAL;
        self.manipulationPitchOffset = 0;
        self.manipulationYawOffset = 0;
        self.decision = toBlock ? Decision.FEINT_TO_BLOCK : Decision.FEINT_ATTACK;
        setState(self, CombatState.FEINT);
    }

    private void beginChamberAttempt(Musketeer self, Musketeer enemy) {
        stopBait(self);
        endDezone(self);
        self.attackDirection = enemy.attackDirection;
        self.chamberType = self.stats.chamber() > 90 && random.nextInt(100) < self.stats.chamber() - 90 ?
                ChamberType.PERFECT : ChamberType.SIMPLE;
        var timingSkill = (self.stats.attackTiming() + self.stats.reaction() + self.stats.decisionMaking()) / 3.0;
        self.chamberTimingError = random.nextGaussian() * Math.max(0.012, 0.060 - 0.00045 * timingSkill);
        self.attackPitch = self.pitch;
        self.baseAttackPitch = self.pitch;
        self.attackManipulation = AttackManipulation.NEUTRAL;
        self.attackResolved = false;
        self.decision = Decision.CHAMBER;
        setState(self, CombatState.CHAMBER_PREPARE);
    }

    private void beginChamberReturn(Musketeer defender, Musketeer attacker) {
        var nextChain = attacker.chamberChain + 1;
        var distance = Math.hypot(attacker.x - defender.x, attacker.y - defender.y);
        stopBait(defender);
        endDezone(defender);
        defender.attackDirection = attacker.attackDirection;
        defender.attackReachScale = 1;
        defender.baitedAttack = false;
        defender.punishingBait = false;
        defender.exploitingBait = false;
        defender.simpleChamberLethal = false;
        defender.chamberChain = nextChain;
        defender.chamberMessageType = defender.chamberType;
        defender.chamberMessageChain = nextChain;
        defender.chamberMessageTime = 1.1;
        defender.decision = Decision.CHAMBER;
        defender.attackResolved = false;

        if (defender.chamberType == ChamberType.PERFECT) configureCertainTorsoAim(defender, distance);
        else {
            defender.intendedZone = HitZone.TORSO;
            defender.attackHeight = HitZone.TORSO.centerHeight() + random.nextGaussian() *
                    (0.06 + (100 - defender.stats.footwork()) * 0.002);
            defender.aimError = random.nextGaussian() *
                    (0.05 + (100 - defender.stats.footwork()) * 0.002);
            defender.desiredPitch = Math.clamp(Math.atan2(defender.attackHeight - WEAPON_HEIGHT,
                    Math.max(0.25, distance)), -Musketeer.MAX_PITCH, Musketeer.MAX_PITCH);
        }
        defender.attackPitch = defender.pitch;

        attacker.decision = Decision.WAIT;
        if (defender.chamberType == ChamberType.PERFECT) {
            setState(attacker, CombatState.BLOCK_STUN);
            startAttack(defender, CombatState.CHAMBER_ACTIVE);
        } else {
            var idealDistance = defender.projectedThrustLength() + Musketeer.BODY_RADIUS - 0.10;
            var distanceQuality = Math.max(0, 1 - Math.abs(distance - idealDistance) / 0.45);
            var facingQuality = Math.max(0, 1 - Math.abs(angleDifference(defender.weaponWorldAngle(),
                    Math.atan2(attacker.y - defender.y, attacker.x - defender.x))) / 0.40);
            defender.simpleChamberLethal = random.nextDouble() <
                    defender.stats.footwork() / 500.0 * distanceQuality * facingQuality;
            setState(attacker, CombatState.NEUTRAL);
            attacker.decisionTime = 0;
            attacker.threatSeen = false;
            setState(defender, CombatState.CHAMBER_HOLD);
        }
    }

    private void beginBait(Musketeer self) {
        ++self.baitSequence;
        self.baitOffset = (random.nextBoolean() ? 1 : -1) * Math.toRadians(22 + random.nextDouble() * 18);
        self.baitTime = 0.35 + random.nextDouble() * 0.40;
        self.decision = self.baitOffset < 0 ? Decision.BAIT_LEFT : Decision.BAIT_RIGHT;
    }

    private void stopBait(Musketeer self) {
        self.baitTime = 0;
        self.baitOffset = 0;
    }

    private void beginBaitedAttack(Musketeer self, Musketeer baiter, double distance) {
        stopBait(self);
        stopBait(baiter);
        endDezone(self);
        self.attackDirection = CombatDirection.UP;
        configureCertainTorsoAim(self, distance);
        self.attackReachScale = 0.62;
        self.baitedAttack = true;
        self.baitTrapMessageTime = 1.35;
        self.decision = Decision.FALL_FOR_BAIT;
        self.attackPitch = self.pitch;
        setState(self, CombatState.ATTACK_PREPARE);
    }

    private void beginBaitPunish(Musketeer self, double distance) {
        stopBait(self);
        endDezone(self);
        self.attackDirection = CombatDirection.UP;
        configureCertainTorsoAim(self, distance);
        self.punishingBait = true;
        self.punishMessageTime = 1.35;
        self.decision = Decision.PUNISH_BAIT;
        self.attackPitch = self.pitch;
        setState(self, CombatState.ATTACK_PREPARE);
    }

    private void beginBaitAdvanceAttack(Musketeer self, Musketeer baiter, double distance) {
        stopBait(self);
        stopBait(baiter);
        endDezone(self);
        self.attackDirection = CombatDirection.DOWN;
        configureCertainTorsoAim(self, distance);
        self.exploitingBait = true;
        self.baitAdvanceMessageTime = 1.35;
        self.decision = Decision.EXPLOIT_BAIT;
        self.attackPitch = self.pitch;
        setState(self, CombatState.ATTACK_PREPARE);
    }

    private void configureCertainTorsoAim(Musketeer self, double distance) {
        self.intendedZone = HitZone.TORSO;
        self.attackHeight = HitZone.TORSO.centerHeight();
        self.aimError = 0;
        self.desiredPitch = Math.clamp(Math.atan2(self.attackHeight - WEAPON_HEIGHT, Math.max(0.25, distance)),
                -Musketeer.MAX_PITCH, Musketeer.MAX_PITCH);
        self.attackManipulation = AttackManipulation.NEUTRAL;
        self.manipulationPitchOffset = 0;
        self.manipulationYawOffset = 0;
        self.attackResolved = false;
    }

    private void startAttack(Musketeer self, CombatState state) {
        self.attackPitch = self.pitch;
        self.baseAttackPitch = self.pitch;
        if (state == CombatState.ATTACK_ACTIVE || state == CombatState.CHAMBER_ACTIVE) {
            if (self.attackDirection == self.lastCommittedAttackDirection) ++self.directionStreak;
            else self.directionStreak = 1;
            self.lastCommittedAttackDirection = self.attackDirection;
            if (self.engagementPhase == EngagementPhase.PROBE)
                beginEngagementPhase(self, EngagementPhase.COMMIT);
        }
        setState(self, state);
    }

    private boolean operationSucceeds(int skill) {
        return random.nextDouble() < operationAccuracy(skill);
    }

    double operationAccuracy(int skill) {
        return skill <= 10 ? 0.5 : 0.5 + 0.499 * (skill - 10) / 90.0;
    }

    private Decision randomStrafe() {
        return random.nextBoolean() ? Decision.STRAFE_LEFT : Decision.STRAFE_RIGHT;
    }

    private void moveAndTurn(Musketeer self, Musketeer enemy, double dt) {
        if (self.state == CombatState.BLOCK_STUN) {
            integrateVelocity(self, 0, 0, 4.8, dt);
            moveWithVelocity(self, dt);
            return;
        }
        var observedEnemy = self.state == CombatState.DOUBLE_BLOCK && self.doubleBlockThreat != null &&
                self.doubleBlockThreat.alive ? self.doubleBlockThreat : enemy;
        var enemyX = knownX(self, observedEnemy);
        var enemyY = knownY(self, observedEnemy);
        if (self.engagementPhase == EngagementPhase.COMMIT && isVisible(self, observedEnemy)) {
            var leadTime = 0.06 + self.stats.decisionMaking() * 0.0014;
            enemyX += observedEnemy.velocityX * leadTime;
            enemyY += observedEnemy.velocityY * leadTime;
        }
        var distance = Math.hypot(enemyX - self.x, enemyY - self.y);
        if (self.dezoneSwitchTarget == null && self.dezoneMovement == DezoneMovement.BACKWARD &&
                distance >= DEZONE_BACKWARD_LIMIT) {
            self.dezoneMovement = random.nextBoolean() ?
                    DezoneMovement.ADVANCE_LEFT : DezoneMovement.ADVANCE_RIGHT;
            setDezoneDecision(self);
        }
        if (self.isGuarding())
            self.desiredPitch = switch (self.blockDirection) {
                case UP -> Math.toRadians(24);
                case DOWN -> Math.toRadians(-20);
                case SIDE -> 0;
            };
        else if (self.state == CombatState.RECOVERY) self.desiredPitch = self.attackPitch;
        else if (self.state == CombatState.ATTACK_PREPARE || self.state == CombatState.ATTACK_HOLD ||
                self.state == CombatState.CHAMBER_PREPARE || self.state == CombatState.CHAMBER_HOLD ||
                self.isAttacking())
            self.desiredPitch = Math.clamp(Math.atan2(self.attackHeight - WEAPON_HEIGHT, Math.max(0.25, distance)),
                    -Musketeer.MAX_PITCH, Musketeer.MAX_PITCH);
        else self.desiredPitch = Math.atan2(HitZone.TORSO.centerHeight() - WEAPON_HEIGHT, Math.max(0.25, distance));
        var pitchSpeed = 1.2 + self.stats.footwork() * 0.012;
        self.pitch += Math.clamp(self.desiredPitch - self.pitch, -pitchSpeed * dt, pitchSpeed * dt);
        self.pitch = Math.clamp(self.pitch, -Musketeer.MAX_PITCH, Musketeer.MAX_PITCH);
        updateAttackManipulation(self, dt);

        var enemyAngle = Math.atan2(enemyY - self.y, enemyX - self.x);
        var targetAngle = self.backEscapeTime > 0 ? backProtectionFacing(self, enemyAngle) : enemyAngle;
        if (self.engagementPhase == EngagementPhase.PASS_THROUGH)
            targetAngle += self.engagementSide * Math.toRadians(18);
        var turnSpeed = 2.4 + self.stats.footwork() * 0.018;
        if (self.isAttacking()) turnSpeed *= 0.62;
        else if (self.state == CombatState.RECOVERY) turnSpeed *= 0.52;
        else if (self.isGuarding()) turnSpeed *= 0.68;
        self.angle += Math.clamp(angleDifference(self.angle, targetAngle), -turnSpeed * dt, turnSpeed * dt);
        self.angle = normalizeAngle(self.angle);

        var weaponTargetAngle = enemyAngle + self.aimError + (self.baitTime > 0 ? self.baitOffset : 0);
        if (self.isGuarding()) weaponTargetAngle = enemyAngle;
        else if (self.isAttacking()) weaponTargetAngle += self.manipulationYawOffset * manipulationCurve(self);
        self.desiredWeaponYaw = Math.clamp(angleDifference(self.angle, weaponTargetAngle),
                -Musketeer.MAX_WEAPON_YAW, Musketeer.MAX_WEAPON_YAW);
        var weaponTurnSpeed = 3.4 + self.stats.footwork() * 0.018;
        if (self.isAttacking()) weaponTurnSpeed *= 0.55;
        else if (self.state == CombatState.RECOVERY) weaponTurnSpeed *= 0.44;
        self.weaponYaw += Math.clamp(self.desiredWeaponYaw - self.weaponYaw,
                -weaponTurnSpeed * dt, weaponTurnSpeed * dt);
        self.weaponYaw = Math.clamp(self.weaponYaw, -Musketeer.MAX_WEAPON_YAW, Musketeer.MAX_WEAPON_YAW);

        var speed = 1.05 + self.stats.footwork() * 0.006;
        if (self.isAttacking()) speed *= 0.86;
        else if (self.state == CombatState.RECOVERY) speed *= 0.92;
        else if (self.state == CombatState.FEINT) speed *= 0.84;
        else if (self.isGuarding()) speed *= 0.78;
        double forward = 0, sideways = 0;
        if (self.decision == Decision.RECENTER) {
            var centerX = ARENA_WIDTH / 2 - self.x;
            var centerY = ARENA_HEIGHT / 2 - self.y;
            var centerDistance = Math.hypot(centerX, centerY);
            if (centerDistance > 0.0001)
                integrateVelocity(self, centerX / centerDistance * speed, centerY / centerDistance * speed,
                        movementAcceleration(self), dt);
            else integrateVelocity(self, 0, 0, movementAcceleration(self), dt);
            moveWithVelocity(self, dt);
            avoidTeammate(self, speed, dt);
            keepInArena(self);
            return;
        }
        if (self.state == CombatState.RECOVERY) {
            forward = -1;
            sideways = self.engagementSide * 0.28;
        } else if (self.dezoneTime > 0 && !self.isAttacking())
            switch (self.dezoneMovement) {
                case BACKWARD -> forward = -1;
                case ADVANCE_LEFT -> { forward = 0.70; sideways = -0.70; }
                case ADVANCE_RIGHT -> { forward = 0.70; sideways = 0.70; }
                case ORBIT_LEFT -> sideways = -1;
                case ORBIT_RIGHT -> sideways = 1;
                case TARGET_SWITCH_LEFT -> { forward = 0.55; sideways = -0.85; }
                case TARGET_SWITCH_RIGHT -> { forward = 0.55; sideways = 0.85; }
                default -> { }
            }
        else
            switch (self.decision) {
                case APPROACH, PREPARE_ATTACK, RELEASE_ATTACK, DEZONE_SWITCH_ATTACK, RIPOSTE,
                        FREE_KILL_BACKSTAB -> forward = 1;
                case CHAMBER -> {
                    if (self.isAttacking()) forward = 1;
                }
                case PUNISH_BAIT -> {
                    var safePunishDistance = enemy.projectedThrustLength() + Musketeer.BODY_RADIUS + 0.08;
                    if (self.isAttacking() || distance > safePunishDistance) forward = 1;
                }
                case EXPLOIT_BAIT -> forward = 1;
                case RETREAT, BLOCK, DEZONE_SWITCH_ABORT -> forward = -1;
                case STRAFE_LEFT -> sideways = -1;
                case STRAFE_RIGHT -> sideways = 1;
                case EDGE_ESCAPE_LEFT -> { forward = 0.24; sideways = -0.92; }
                case EDGE_ESCAPE_RIGHT -> { forward = 0.24; sideways = 0.92; }
                case PROBE_LEFT -> { forward = 0.28; sideways = -0.74; }
                case PROBE_RIGHT -> { forward = 0.28; sideways = 0.74; }
                case COMMIT_LEFT -> { forward = 0.88; sideways = -0.52; }
                case COMMIT_RIGHT -> { forward = 0.88; sideways = 0.52; }
                case PASS_LEFT -> { forward = 1; sideways = -0.58; }
                case PASS_RIGHT -> { forward = 1; sideways = 0.58; }
                case DISENGAGE -> { forward = 0.94; sideways = self.engagementSide * 0.18; }
                case RESET_GUARD -> { forward = -0.18; sideways = self.engagementSide * 0.72; }
                case PROTECT_BACK_LEFT -> { forward = 0.55; sideways = -0.85; }
                case PROTECT_BACK_RIGHT -> { forward = 0.55; sideways = 0.85; }
                default -> { }
            }
        if (forward < 0) speed *= BACKWARD_SPEED_FACTOR;
        var vectorLength = Math.hypot(forward, sideways);
        if (vectorLength > 1) {
            forward /= vectorLength;
            sideways /= vectorLength;
        }
        var preserveHeading = self.state != CombatState.RECOVERY && (self.engagementPhase == EngagementPhase.PASS_THROUGH ||
                self.engagementPhase == EngagementPhase.DISENGAGE) && Math.hypot(self.velocityX, self.velocityY) > 0.12;
        var movementBasis = preserveHeading ? self.movementHeading : self.angle;
        var desiredVelocityX = (Math.cos(movementBasis) * forward - Math.sin(movementBasis) * sideways) * speed;
        var desiredVelocityY = (Math.sin(movementBasis) * forward + Math.cos(movementBasis) * sideways) * speed;
        integrateVelocity(self, desiredVelocityX, desiredVelocityY, movementAcceleration(self), dt);
        moveWithVelocity(self, dt);
        avoidTeammate(self, speed, dt);
        keepInArena(self);
    }

    private void updateAttackManipulation(Musketeer self, double dt) {
        if (!self.isAttacking()) return;
        var targetPitch = Math.clamp(self.baseAttackPitch +
                self.manipulationPitchOffset * manipulationCurve(self), -Musketeer.MAX_PITCH, Musketeer.MAX_PITCH);
        var speed = 2.2 + self.stats.manipulationAbility() * 0.018;
        self.attackPitch += Math.clamp(targetPitch - self.attackPitch, -speed * dt, speed * dt);
    }

    private double manipulationCurve(Musketeer self) {
        if (!self.isAttacking() || self.attackManipulation == AttackManipulation.NEUTRAL) return 0;
        var progress = Math.clamp(self.stateTime / Math.max(0.001, self.activeTime()), 0, 1);
        return self.attackManipulation == AttackManipulation.ACCEL ? Math.min(1, progress / 0.32) :
                Math.clamp((progress - 0.18) / 0.68, 0, 1);
    }

    private double movementAcceleration(Musketeer self) {
        var acceleration = 4.6 + self.stats.footwork() * 0.045;
        if (self.isAttacking()) acceleration *= 0.72;
        else if (self.state == CombatState.RECOVERY) acceleration *= 0.64;
        return acceleration;
    }

    private void integrateVelocity(Musketeer self, double desiredX, double desiredY,
                                   double acceleration, double dt) {
        var deltaX = desiredX - self.velocityX;
        var deltaY = desiredY - self.velocityY;
        var delta = Math.hypot(deltaX, deltaY);
        var maximum = acceleration * dt;
        if (delta > maximum && delta > 0.0001) {
            deltaX *= maximum / delta;
            deltaY *= maximum / delta;
        }
        self.velocityX += deltaX;
        self.velocityY += deltaY;
        if (Math.hypot(desiredX, desiredY) < 0.001) {
            var speed = Math.hypot(self.velocityX, self.velocityY);
            var remaining = Math.max(0, speed - (3.2 + self.stats.footwork() * 0.018) * dt);
            if (speed > 0.0001) {
                self.velocityX *= remaining / speed;
                self.velocityY *= remaining / speed;
            }
        }
    }

    private void moveWithVelocity(Musketeer self, double dt) {
        self.x += self.velocityX * dt;
        self.y += self.velocityY * dt;
        if (Math.hypot(self.velocityX, self.velocityY) > 0.08)
            self.movementHeading = Math.atan2(self.velocityY, self.velocityX);
    }

    private double backProtectionFacing(Musketeer self, double enemyAngle) {
        var rear = self.rearThreat;
        if (rear == null || !rear.alive) return enemyAngle;
        Musketeer other = null;
        var closestDistance = Double.POSITIVE_INFINITY;
        for (var opponent : opponentsOf(self)) {
            if (!opponent.alive || opponent == rear || !isTracked(self, opponent)) continue;
            var distance = knownDistance(self, opponent);
            if (distance < closestDistance) {
                other = opponent;
                closestDistance = distance;
            }
        }
        if (other == null) return Math.atan2(knownY(self, rear) - self.y, knownX(self, rear) - self.x);
        var frontAngle = Math.atan2(knownY(self, other) - self.y, knownX(self, other) - self.x);
        var rearAngle = Math.atan2(knownY(self, rear) - self.y, knownX(self, rear) - self.x);
        var separation = angleDifference(frontAngle, rearAngle);
        if (Math.abs(separation) > Math.toRadians(170))
            return normalizeAngle(frontAngle + self.backEscapeDirection * Math.PI / 2);
        return normalizeAngle(frontAngle + separation / 2);
    }

    private void avoidTeammate(Musketeer self, double speed, double dt) {
        var teammate = teammateOf(self);
        if (teammate == null || !teammate.alive) return;
        var dx = teammate.x - self.x;
        var dy = teammate.y - self.y;
        var distance = Math.hypot(dx, dy);
        if (distance >= 1.05 || distance < 0.0001) return;
        var strength = (1.05 - distance) / 1.05 * speed * dt;
        self.x -= dx / distance * strength;
        self.y -= dy / distance * strength;
    }

    private void keepInArena(Musketeer agent) {
        var clampedX = Math.clamp(agent.x, Musketeer.BODY_RADIUS, ARENA_WIDTH - Musketeer.BODY_RADIUS);
        var clampedY = Math.clamp(agent.y, Musketeer.BODY_RADIUS, ARENA_HEIGHT - Musketeer.BODY_RADIUS);
        if (clampedX != agent.x) agent.velocityX = 0;
        if (clampedY != agent.y) agent.velocityY = 0;
        agent.x = clampedX;
        agent.y = clampedY;
    }

    private void separateAgents() {
        for (var firstIndex = 0; firstIndex < agents.length; ++firstIndex) {
            var first = agents[firstIndex];
            if (!first.alive) continue;
            for (var secondIndex = firstIndex + 1; secondIndex < agents.length; ++secondIndex) {
                var second = agents[secondIndex];
                if (!second.alive) continue;
                var dx = second.x - first.x;
                var dy = second.y - first.y;
                var distance = Math.hypot(dx, dy);
                var comfortDistance = Musketeer.BODY_RADIUS * 2;
                if (distance >= comfortDistance) continue;
                var hardDistance = Musketeer.BODY_RADIUS * 1.18;
                double correction;
                if (distance < 0.0001) {
                    dx = 1;
                    dy = 0;
                    distance = 1;
                    correction = hardDistance;
                } else {
                    correction = (comfortDistance - distance) * 0.18;
                    if (distance < hardDistance) correction = Math.max(correction, hardDistance - distance);
                }
                var push = correction / 2;
                first.x -= dx / distance * push;
                first.y -= dy / distance * push;
                second.x += dx / distance * push;
                second.y += dy / distance * push;
                var normalX = dx / distance;
                var normalY = dy / distance;
                var closingSpeed = (second.velocityX - first.velocityX) * normalX +
                        (second.velocityY - first.velocityY) * normalY;
                if (closingSpeed < 0) {
                    var impulse = -closingSpeed * 0.28;
                    first.velocityX -= normalX * impulse;
                    first.velocityY -= normalY * impulse;
                    second.velocityX += normalX * impulse;
                    second.velocityY += normalY * impulse;
                }
                keepInArena(first);
                keepInArena(second);
            }
        }
    }

    private void resolveAttack(Musketeer attacker) {
        if (!attacker.alive || !attacker.isAttacking() || attacker.attackResolved) return;
        Musketeer nearest = null;
        var nearestDistance = Double.POSITIVE_INFINITY;
        for (var defender : opponentsOf(attacker)) {
            if (!defender.alive) continue;
            var distance = sweptTipDistance(attacker, defender);
            if (distance <= Musketeer.BODY_RADIUS && distance < nearestDistance) {
                nearest = defender;
                nearestDistance = distance;
            }
        }
        if (nearest != null) checkHit(attacker, nearest);
    }

    private void checkHit(Musketeer attacker, Musketeer defender) {
        if (!attacker.alive || !defender.alive || !attacker.isAttacking() || attacker.attackResolved) return;
        var distance = sweptTipDistance(attacker, defender);
        if (distance > Musketeer.BODY_RADIUS) return;
        attacker.attackResolved = true;
        var hitZone = HitZone.atHeight(attacker.attackHeight);
        if (hitZone == null) {
            setState(attacker, CombatState.RECOVERY);
            return;
        }
        var backstab = isRearAttack(defender, attacker);
        if (!backstab && defender.target == attacker && defender.state == CombatState.CHAMBER_PREPARE &&
                defender.attackDirection == attacker.attackDirection) {
            var towardAttacker = Math.atan2(attacker.y - defender.y, attacker.x - defender.x);
            var chamberWindow = defender.chamberType == ChamberType.PERFECT ?
                    PERFECT_CHAMBER_WINDOW : SIMPLE_CHAMBER_WINDOW;
            if (Math.abs(angleDifference(defender.weaponWorldAngle(), towardAttacker)) < Math.toRadians(75) &&
                    Math.abs(defender.chamberTimingError) <= chamberWindow &&
                    (defender.chamberType != ChamberType.PERFECT || defender.stats.chamber() > 90)) {
                beginChamberReturn(defender, attacker);
                return;
            }
        }
        var doubleBlock = defender.state == CombatState.DOUBLE_BLOCK && defender.doubleBlockSwitched &&
                defender.doubleBlockThreat == attacker;
        if (!backstab && (defender.state == CombatState.BLOCKING || doubleBlock) &&
                defender.blockDirection.blocks(attacker.attackDirection)) {
            var towardAttacker = Math.atan2(attacker.y - defender.y, attacker.x - defender.x);
            if (Math.abs(angleDifference(defender.weaponWorldAngle(), towardAttacker)) < Math.toRadians(75)) {
                defender.blockSparkTime = Musketeer.BLOCK_SPARK_DURATION;
                defender.blockSparkX = attacker.tipX();
                defender.blockSparkY = attacker.tipY();
                attacker.decision = Decision.WAIT;
                setState(attacker, CombatState.BLOCK_STUN);
                if (doubleBlock) defender.doubleBlockMessageTime = 1.15;
                else tryBeginDoubleBlock(defender, attacker);
                return;
            }
        }
        if (backstab) defender.backstabMessageTime = 1.15;
        applyDamage(attacker, defender, hitZone, backstab);
        setState(attacker, CombatState.RECOVERY);
    }

    private double sweptTipDistance(Musketeer attacker, Musketeer defender) {
        var startX = attacker.previousTipX - defender.previousX;
        var startY = attacker.previousTipY - defender.previousY;
        var endX = attacker.tipX() - defender.x;
        var endY = attacker.tipY() - defender.y;
        var segmentX = endX - startX;
        var segmentY = endY - startY;
        var lengthSquared = segmentX * segmentX + segmentY * segmentY;
        if (lengthSquared < 0.0000001) return Math.hypot(endX, endY);
        var position = Math.clamp(-(startX * segmentX + startY * segmentY) / lengthSquared, 0, 1);
        return Math.hypot(startX + segmentX * position, startY + segmentY * position);
    }

    private void tryBeginDoubleBlock(Musketeer defender, Musketeer firstAttacker) {
        Musketeer secondAttacker = null;
        for (var opponent : opponentsOf(defender)) {
            if (opponent == firstAttacker || !opponent.alive || !opponent.isAttacking() ||
                    opponent.attackResolved || opponent.target != defender ||
                    opponent.attackDirection != firstAttacker.attackDirection.opposite() ||
                    !isVisible(defender, opponent) || isRearAttack(defender, opponent)) continue;
            var towardDefender = Math.atan2(defender.y - opponent.y, defender.x - opponent.x);
            var distance = Math.hypot(defender.x - opponent.x, defender.y - opponent.y);
            var timeUntilPeak = Math.max(0, opponent.activeTime() / 2 - opponent.stateTime);
            if (Math.abs(angleDifference(opponent.weaponWorldAngle(), towardDefender)) > Math.toRadians(34) ||
                    distance > opponent.projectedThrustLength() + Musketeer.BODY_RADIUS + 0.35 ||
                    opponent.stateTime > opponent.activeTime() * 0.65 || timeUntilPeak > DOUBLE_BLOCK_WINDOW)
                continue;
            secondAttacker = opponent;
            break;
        }
        if (secondAttacker == null) return;
        var ability = defender.stats.doubleBlockAbility();
        if (random.nextDouble() >= Math.pow(ability / 100.0, 3)) return;
        endDezone(defender);
        defender.doubleBlockThreat = secondAttacker;
        defender.doubleBlockSecondDirection = BlockDirection.matching(secondAttacker.attackDirection);
        defender.doubleBlockSwitchTime = 0.19 - 0.00125 * ability;
        defender.doubleBlockSwitched = false;
        defender.decision = Decision.DOUBLE_BLOCK;
        setState(defender, CombatState.DOUBLE_BLOCK);
    }

    private void applyDamage(Musketeer attacker, Musketeer defender, HitZone hitZone, boolean backstab) {
        var cleanFreeKill = backstab && attacker.freeKillAttempt && hitZone != HitZone.LEG;
        var damage = cleanFreeKill || attacker.punishingBait ||
                attacker.chamberType == ChamberType.PERFECT ? 100 : switch (hitZone) {
            case HEAD -> 100;
            case TORSO -> attacker.chamberType == ChamberType.SIMPLE ?
                    (attacker.simpleChamberLethal ? 100 : 50) : random.nextInt(100) < 95 ? 100 : 50;
            case LEG -> 40;
        };
        defender.lastHitZone = hitZone;
        defender.lastDamage = damage;
        defender.hitMessageTime = 1.15;
        defender.hitPoints = Math.max(0, defender.hitPoints - damage);
        if (defender.dezoneTime > 0 || defender.dezoneSwitchTarget != null) endDezone(defender);
        if (defender.hitPoints > 0) return;
        defender.alive = false;
        defender.target = null;
        defender.rearThreat = null;
        defender.backExposed = false;
        defender.backEscapeTime = 0;
        defender.freeKillAttempt = false;
        stopBait(defender);
        setState(defender, CombatState.DEAD);
    }

    private void updateWinner() {
        var blueAlive = teamAlive(blueTeam);
        var redAlive = teamAlive(redTeam);
        if (!blueAlive && !redAlive) winningTeam = DRAW;
        else if (!blueAlive) winningTeam = RED_TEAM;
        else if (!redAlive) winningTeam = BLUE_TEAM;
    }

    private boolean teamAlive(Musketeer[] team) {
        for (var agent : team)
            if (agent.alive) return true;
        return false;
    }

    private void setState(Musketeer agent, CombatState state) {
        var previousState = agent.state;
        agent.state = state;
        agent.stateTime = 0;
        if ((state == CombatState.ATTACK_PREPARE || state == CombatState.CHAMBER_PREPARE) &&
                state != previousState) ++agent.attackSequence;
        if (state == CombatState.BLOCK_STUN || state == CombatState.RECOVERY || state == CombatState.DEAD)
            endDezone(agent);
        if (state == CombatState.RECOVERY && agent.target != null && agent.target.alive) {
            var dx = agent.target.x - agent.x;
            var dy = agent.target.y - agent.y;
            var distance = Math.hypot(dx, dy);
            if (distance > 0.0001) {
                var closingSpeed = agent.velocityX * dx / distance + agent.velocityY * dy / distance;
                if (closingSpeed > 0) {
                    agent.velocityX -= dx / distance * closingSpeed;
                    agent.velocityY -= dy / distance * closingSpeed;
                }
            }
        }
        if (state == CombatState.NEUTRAL || state == CombatState.DEAD) {
            agent.doubleBlockThreat = null;
            agent.doubleBlockSwitched = false;
            agent.doubleBlockSwitchTime = 0;
        }
        if (state == CombatState.NEUTRAL) {
            agent.aimError = 0;
            agent.chamberType = ChamberType.NONE;
            agent.simpleChamberLethal = false;
            agent.chamberChain = 0;
            agent.attackReachScale = 1;
            agent.baitedAttack = false;
            agent.punishingBait = false;
            agent.exploitingBait = false;
            agent.freeKillAttempt = false;
            agent.freeKillPerceivedDistance = 0;
            agent.attackManipulation = AttackManipulation.NEUTRAL;
            agent.manipulationPitchOffset = 0;
            agent.manipulationYawOffset = 0;
            agent.feintCount = 0;
            agent.feintToBlock = false;
            agent.guardSwitching = false;
        } else if (state == CombatState.DEAD) {
            agent.velocityX = 0;
            agent.velocityY = 0;
        }
    }

    private double angleDifference(double from, double to) {
        return normalizeAngle(to - from);
    }

    private double normalizeAngle(double angle) {
        while (angle > Math.PI) angle -= Math.PI * 2;
        while (angle < -Math.PI) angle += Math.PI * 2;
        return angle;
    }
}
