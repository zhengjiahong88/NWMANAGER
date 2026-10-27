final class SimulationSmokeTest {
    public static void main(String[] args) {
        new SimulationSmokeTest().run();
    }

    private void run() {
        verifyModernScenario();
        verifyLegacyScenario();
        verifyControlledScenario();
        verifyControlled2v2Scenario();
        verifyScenarioPlacementFamilies();
        verifyBaselineAliasRegression();
        verifyInterceptionCandidateSelection();
        verifyPhysicalInterceptionPhases();
        verifyLowThreatKeepsFlank();
        verifyMediumThreatContestsPath();
        verifyVisibilityAloneDoesNotEngage();
        verifyThreatUsesPerceivedData();
        verifyHighThreatCreatesDuels();
        verifyDuelTargetsRemainStable();
        verifyThreatHysteresis();
        verifyFailedInterception();
        verifyControlledEliminations();
        verifyDeterministicSeed();
        System.out.println("Simulation smoke tests passed.");
    }

    private void verifyModernScenario() {
        var simulation = new DuelSimulation();
        require(simulation.scenario() == SimulationScenario.MODERN_2V2,
                "The default simulation must use the modern 2v2 kernel.");
        require(simulation.features().equals(SimulationFeatures.modern2v2()),
                "Modern 2v2 must disable the retired legacy systems.");
        require(simulation.agents.length == 4, "Modern 2v2 must contain four active Agents.");
        require(simulation.combatPhase == CombatPhase.RESOLUTION,
                "Modern 2v2 must bypass the legacy front-line opening.");
        require(simulation.engagements().stream()
                        .filter(engagement -> engagement.status == EngagementStatus.ACTIVE &&
                                engagement.type == EngagementType.GROUP_2V2).count() == 1,
                "Modern 2v2 must start with one authoritative group engagement.");
        for (var agent : simulation.agents) {
            require(agent.localRole == LocalRole.FREE,
                    "Modern agents must not receive scripted local roles.");
            require(agent.tacticalIntent == TacticalIntent.GROUP,
                    "Every modern agent must start with a group intent.");
            require(agent.primaryMark != null && agent.secondaryThreat != null,
                    "Every modern agent must receive primary and secondary group threats.");
            require(agent.plannedTarget != null && agent.target == agent.plannedTarget,
                    "The primary group mark must be authoritative at reset.");
        }
    }

    private void verifyLegacyScenario() {
        var simulation = legacy2v2(2_021L);
        require(simulation.scenario() == SimulationScenario.LEGACY_2V2,
                "Legacy 2v2 must remain explicitly selectable.");
        require(simulation.features().equals(SimulationFeatures.legacy2v2()),
                "Legacy 2v2 must retain every historical subsystem.");
        require(simulation.agents.length == 4, "Legacy 2v2 must contain four active Agents.");
        require(simulation.combatPhase == CombatPhase.FRONT_LINE,
                "Legacy 2v2 must preserve the historical front-line opening.");
    }

    private void verifyControlledScenario() {
        var simulation = controlled(2_021L);
        require(simulation.agents.length == 3, "Controlled 2v1 must contain exactly three active Agents.");
        require(simulation.blueTeam[0].localRole == LocalRole.FIXER,
                "A1 must start as FIXER.");
        require(simulation.blueTeam[1].localRole == LocalRole.FLANKER,
                "A2 must start as FLANKER.");
        require(simulation.blueTeam[1].localSide == 1,
                "A2 must reserve the right flank.");
        verifyTwoVsOneEngagement(simulation);
        require(!simulation.redTeam[1].alive && simulation.redTeam[1].state == CombatState.DEAD,
                "The fourth Agent must remain inactive.");
        for (var tick = 0; tick < 240 && simulation.winningTeam == null; ++tick) {
            simulation.update(1.0 / 120.0);
            require(simulation.combatPhase == CombatPhase.RESOLUTION,
                    "Legacy combat phases must remain disabled.");
            for (var agent : simulation.agents) {
                require(agent.pressureRole == PressureRole.NONE && !agent.pressureMode,
                        "Legacy pressure must remain disabled.");
                require(agent.state != CombatState.DOUBLE_BLOCK,
                        "Double Block must remain disabled.");
                require(agent.attackManipulation == AttackManipulation.NEUTRAL,
                        "Accel and drag must remain disabled.");
                require(agent.chamberType != ChamberType.PERFECT,
                        "Perfect Chamber must remain disabled.");
            }
        }
        require(simulation.blueTeam[0].localRole == LocalRole.FIXER,
                "The FIXER role must not expire during the controlled engagement.");
        require(simulation.blueTeam[1].localRole == LocalRole.FLANKER,
                "The FLANKER role must not expire during the controlled engagement.");
    }

    private void verifyControlled2v2Scenario() {
        var simulation = controlled2v2(2_021L);
        require(simulation.agents.length == 4, "Controlled 2v2 must contain four active Agents.");
        for (var agent : simulation.agents)
            require(agent.alive, "Every controlled 2v2 Agent must start alive.");
        verifyTwoVsOneEngagement(simulation);
        var engagement = simulation.engagements().getFirst();
        require(engagement.interceptor == simulation.redTeam[1],
                "B2 must be the assigned interceptor.");
        require(simulation.redTeam[1].tacticalIntent == TacticalIntent.INTERCEPT &&
                        simulation.redTeam[1].plannedTarget == simulation.blueTeam[1],
                "B2 must start with INTERCEPT against A2.");
        require(simulation.redTeam[1].target == simulation.blueTeam[1],
                "B2's physical combat target must initially be A2.");
        require(simulation.redTeam[1].tacticalDestination != null,
                "B2 must expose an interception point.");
        var interceptor = simulation.redTeam[1];
        var initialRawPoint = interceptor.rawInterceptionPoint;
        var previousDestination = interceptor.tacticalDestination;
        var initialDistance = interceptor.pos.minus(interceptor.tacticalDestination).hypotenuse();
        var minimumDistance = initialDistance;
        var pointRecalculated = false;
        var interceptionPhaseObserved = interceptor.interceptionPhase != null;
        for (var tick = 0; tick < 1_200 && simulation.winningTeam == null; ++tick) {
            simulation.update(1.0 / 120.0);
            interceptionPhaseObserved |= interceptor.interceptionPhase != null;
            if (!interceptor.alive || interceptor.tacticalDestination == null ||
                    engagement.status == EngagementStatus.BROKEN) break;
            minimumDistance = Math.min(minimumDistance,
                    interceptor.pos.minus(interceptor.tacticalDestination).hypotenuse());
            require(interceptor.tacticalDestination.minus(previousDestination).hypotenuse() <=
                            1.85 / 120.0 + 0.000001,
                    "The smoothed interception point must obey its speed limit.");
            if (interceptor.rawInterceptionPoint.minus(initialRawPoint).hypotenuse() > 0.04)
                pointRecalculated = true;
            previousDestination = interceptor.tacticalDestination;
            require(simulation.combatPhase == CombatPhase.RESOLUTION,
                    "Controlled 2v2 must not reactivate legacy combat phases.");
            for (var agent : simulation.agents)
                require(agent.pressureRole == PressureRole.NONE && !agent.pressureMode &&
                                agent.state != CombatState.DOUBLE_BLOCK &&
                                agent.attackManipulation == AttackManipulation.NEUTRAL &&
                                agent.chamberType != ChamberType.PERFECT,
                        "Controlled 2v2 must keep advanced legacy systems disabled.");
        }
        require(minimumDistance <= initialDistance - 0.30,
                "B2 must physically approach its interception destination.");
        require(pointRecalculated,
                "The raw interception point must react to the moving situation.");
        require(interceptionPhaseObserved,
                "B2 must expose a readable interception phase.");
    }

    private void verifyScenarioPlacementFamilies() {
        var generator = new ScenarioGenerator();
        var baseline = generator.create(ScenarioFamily.BASELINE, 2_021L);
        require(baseline.blueOne().equals(new AgentSpawn(new Pos(2.55, 3.50), 0, 0)) &&
                        baseline.blueTwo().equals(new AgentSpawn(new Pos(3.15, 1.95), 0, 0)) &&
                        baseline.redOne().equals(new AgentSpawn(new Pos(5.45, 3.50), Math.PI, 0)) &&
                        baseline.redTwo().equals(new AgentSpawn(new Pos(5.25, 1.35), Math.PI, 0)),
                "BASELINE must preserve the four historical spawns exactly.");
        require(generator.isLegal(baseline), "The historical BASELINE must be legal.");
        var first = generator.create(ScenarioFamily.JITTERED_BASELINE, 42L);
        var second = generator.create(ScenarioFamily.JITTERED_BASELINE, 42L);
        var other = generator.create(ScenarioFamily.JITTERED_BASELINE, 41L);
        require(first.equals(second) && generator.isLegal(first),
                "The same jittered seed must produce the same legal setup.");
        require(!first.spawns()[0].equals(other.spawns()[0]) ||
                        !first.spawns()[1].equals(other.spawns()[1]) ||
                        !first.spawns()[2].equals(other.spawns()[2]) ||
                        !first.spawns()[3].equals(other.spawns()[3]),
                "Different jittered seeds must produce different spawns.");
    }

    private void verifyBaselineAliasRegression() {
        var alias = controlled2v2(2_021L);
        var explicit = controlled2v2(ScenarioFamily.BASELINE, 2_021L);
        require(alias.scenarioSetup().equals(explicit.scenarioSetup()),
                "The historical alias must use the explicit BASELINE setup.");
        for (var tick = 0; tick < 900; ++tick) {
            compareCompleteState(alias, explicit,
                    "Historical controlled-2v2 and explicit baseline must remain identical.");
            alias.update(1.0 / 120.0);
            explicit.update(1.0 / 120.0);
        }
        compareCompleteState(alias, explicit,
                "Historical controlled-2v2 and explicit baseline must end identically.");

        var reset = controlled2v2(ScenarioFamily.JITTERED_BASELINE, 42L);
        for (var tick = 0; tick < 240; ++tick) reset.update(1.0 / 120.0);
        reset.reset();
        var fresh = controlled2v2(ScenarioFamily.JITTERED_BASELINE, 42L);
        for (var tick = 0; tick < 600; ++tick) {
            compareCompleteState(reset, fresh,
                    "R must restore the same family, seed, placement and combat RNG.");
            reset.update(1.0 / 120.0);
            fresh.update(1.0 / 120.0);
        }
    }

    private void compareCompleteState(DuelSimulation first, DuelSimulation second, String message) {
        require(first.scenarioFamily() == second.scenarioFamily() &&
                        first.seed() == second.seed() &&
                        first.scenarioSetup().equals(second.scenarioSetup()) &&
                        first.agents.length == second.agents.length,
                message);
        for (var n = 0; n < first.agents.length; ++n) {
            var left = first.agents[n];
            var right = second.agents[n];
            require(left.pos.equals(right.pos) && left.velocity.equals(right.velocity) &&
                            same(left.angle, right.angle) && same(left.weaponYaw, right.weaponYaw) &&
                            left.state == right.state && left.decision == right.decision &&
                            left.alive == right.alive && left.tacticalIntent == right.tacticalIntent &&
                            left.interceptionPhase == right.interceptionPhase &&
                            left.flankPhase == right.flankPhase &&
                            left.controlledBackstabAttempt == right.controlledBackstabAttempt &&
                            same(left.rearAngle, right.rearAngle) &&
                            same(left.rearAngleGainRate, right.rearAngleGainRate) &&
                            same(left.rearStableTime, right.rearStableTime) &&
                            equal(left.rawRearDestination, right.rawRearDestination) &&
                            equal(left.smoothedRearDestination, right.smoothedRearDestination) &&
                            left.backstabOpportunity.equals(right.backstabOpportunity) &&
                            same(left.threatAssessment.score(), right.threatAssessment.score()) &&
                            targetIndex(first, left.target) == targetIndex(second, right.target),
                    message);
        }
        require(first.engagements().size() == second.engagements().size(), message);
        for (var n = 0; n < first.engagements().size(); ++n) {
            var left = first.engagements().get(n);
            var right = second.engagements().get(n);
            require(left.type == right.type && left.status == right.status &&
                            targetIndex(first, left.primaryTarget) == targetIndex(second, right.primaryTarget) &&
                            targetIndex(first, left.fixer) == targetIndex(second, right.fixer) &&
                            targetIndex(first, left.flanker) == targetIndex(second, right.flanker) &&
                            targetIndex(first, left.duelistOne) == targetIndex(second, right.duelistOne) &&
                            targetIndex(first, left.duelistTwo) == targetIndex(second, right.duelistTwo),
                    message);
        }
        require(first.tacticalLog().equals(second.tacticalLog()) &&
                        first.winningTeam == second.winningTeam, message);
    }

    private boolean same(double first, double second) {
        return Double.doubleToLongBits(first) == Double.doubleToLongBits(second);
    }

    private boolean equal(Pos first, Pos second) {
        return first == null ? second == null : first.equals(second);
    }

    private void verifyTwoVsOneEngagement(DuelSimulation simulation) {
        require(simulation.engagements().size() == 1,
                "The controlled scenario must own one explicit engagement.");
        var engagement = simulation.engagements().getFirst();
        require(engagement.type == EngagementType.TWO_VS_ONE &&
                        engagement.primaryTarget == simulation.redTeam[0],
                "The engagement must describe A1 and A2 against B1.");
        require(engagement.fixer == simulation.blueTeam[0] &&
                        engagement.fixerSlot == EngagementSlot.FRONT &&
                        simulation.blueTeam[0].tacticalIntent == TacticalIntent.FIX,
                "A1 must own FIX and FRONT.");
        require(engagement.flanker == simulation.blueTeam[1] &&
                        engagement.flankerSlot == EngagementSlot.RIGHT &&
                        simulation.blueTeam[1].tacticalIntent == TacticalIntent.FLANK,
                "A2 must own FLANK and RIGHT.");
        require(simulation.redTeam[0].tacticalIntent == TacticalIntent.SURVIVE,
                "B1 must own the explicit SURVIVE intent.");
        var defenderToFixer = simulation.blueTeam[0].tacticalDestination.
                minus(engagement.primaryTarget.pos).radians();
        require(Math.abs(Math.atan2(Math.sin(defenderToFixer - engagement.primaryTarget.angle),
                        Math.cos(defenderToFixer - engagement.primaryTarget.angle))) < Math.toRadians(5) &&
                        simulation.blueTeam[1].tacticalDestination.equals(engagement.flankerDestination()),
                "FIXER must own a frontal physical destination and FLANKER its reserved side.");
    }

    private void verifyInterceptionCandidateSelection() {
        var close = controlled2v2(4_218L);
        var closeEngagement = close.engagements().getFirst();
        var closePath = closeEngagement.flankerDestination().minus(closeEngagement.flanker.pos);
        closeEngagement.interceptor.pos = closeEngagement.flanker.pos.plus(closePath.times(0.25));
        closeEngagement.interceptor.rawInterceptionPoint = null;
        close.update(1.0 / 120.0);
        var closeFraction = pathFraction(closeEngagement, closeEngagement.interceptor.rawInterceptionPoint);

        var far = controlled2v2(4_218L);
        var farEngagement = far.engagements().getFirst();
        farEngagement.interceptor.pos = new Pos(7.25, 0.65);
        farEngagement.interceptor.rawInterceptionPoint = null;
        far.update(1.0 / 120.0);
        var farFraction = pathFraction(farEngagement, farEngagement.interceptor.rawInterceptionPoint);

        require(closeFraction <= 0.26,
                "A nearby B2 must be allowed to select the early 25% interception point.");
        require(farFraction > closeFraction,
                "A distant B2 must select a later, more reachable interception point.");
    }

    private void verifyPhysicalInterceptionPhases() {
        var simulation = controlled2v2(7_100L);
        var engagement = simulation.engagements().getFirst();
        var flanker = engagement.flanker;
        var interceptor = engagement.interceptor;
        interceptor.pos = interceptor.tacticalDestination;
        simulation.update(1.0 / 120.0);
        require(interceptor.interceptionPhase == InterceptionPhase.BLOCK_FLANK_PATH,
                "B2 must enter BLOCK_FLANK_PATH only after physically reaching the flank segment.");
        interceptor.pos = flanker.pos.plus(1.30, 0);
        interceptor.angle = Math.PI;
        interceptor.weaponYaw = 0;
        interceptor.interceptionPhaseTime = 0.21;
        observeExactly(flanker, interceptor);
        simulation.update(1.0 / 120.0);
        require(interceptor.interceptionPhase == InterceptionPhase.PRESENT_THREAT,
                "Aligned, reachable and perceived B2 pressure must enter PRESENT_THREAT.");
    }

    private double pathFraction(Engagement engagement, Pos point) {
        var path = engagement.flankerDestination().minus(engagement.flanker.pos);
        return point.minus(engagement.flanker.pos).dot(path) / path.dot(path);
    }

    private void verifyLowThreatKeepsFlank() {
        var simulation = controlled2v2(7_101L);
        var flanker = simulation.blueTeam[1];
        var interceptor = simulation.redTeam[1];
        interceptor.pos = new Pos(7.45, 6.45);
        interceptor.velocity = new Pos();
        interceptor.angle = flanker.pos.minus(interceptor.pos).radians();
        observeExactly(flanker, interceptor);
        simulation.updateEngagementInterceptions(0.50);
        require(flanker.threatResponse == ThreatResponse.IGNORE,
                "A distant B2 must remain a low observable threat.");
        require(flanker.tacticalIntent == TacticalIntent.FLANK &&
                        flanker.target == simulation.redTeam[0],
                "A2 must keep its flank and B1 target under low threat.");
        require(simulation.engagements().size() == 1,
                "Low threat must not create duels.");
    }

    private void verifyMediumThreatContestsPath() {
        var simulation = controlled2v2(7_102L);
        var flanker = simulation.blueTeam[1];
        var interceptor = simulation.redTeam[1];
        interceptor.pos = flanker.pos.plus(2.30, 0);
        interceptor.velocity = new Pos();
        interceptor.angle = Math.PI;
        interceptor.state = CombatState.NEUTRAL;
        observeExactly(flanker, interceptor);
        simulation.updateEngagementInterceptions(0.50);
        require(flanker.threatResponse == ThreatResponse.ADJUST_PATH,
                "A partially obstructing B2 must produce path adjustment.");
        require(flanker.tacticalIntent == TacticalIntent.FLANK &&
                        flanker.target == simulation.redTeam[0] &&
                        flanker.tacticalDestination != null,
                "Medium threat must deform the flank without permanently changing target.");
        require(simulation.engagements().size() == 1,
                "Medium threat must not create permanent duels.");
        var start = flanker.pos;
        var slotDirection = flanker.tacticalDestination.minus(start);
        flanker.decisionTime = 1;
        simulation.update(0.10);
        var displacement = flanker.pos.minus(start);
        require(displacement.hypotenuse() > 0 &&
                        Math.abs(slotDirection.x() * displacement.y() -
                                slotDirection.y() * displacement.x()) > 0.00001,
                "ADJUST_PATH must physically deform A2's movement toward its flank slot.");
    }

    private void verifyVisibilityAloneDoesNotEngage() {
        var simulation = controlled2v2(7_106L);
        var flanker = simulation.blueTeam[1];
        var interceptor = simulation.redTeam[1];
        interceptor.pos = flanker.pos.plus(1.05, 0);
        interceptor.velocity = new Pos();
        interceptor.angle = Math.PI;
        interceptor.weaponYaw = 0;
        interceptor.state = CombatState.NEUTRAL;
        observeExactly(flanker, interceptor);
        simulation.updateEngagementInterceptions(0.50);
        require(!flanker.threatAssessment.immediate() &&
                        flanker.threatResponse != ThreatResponse.ENGAGE &&
                        flanker.target == simulation.redTeam[0],
                "Visibility and proximity alone must never switch A2 from B1 to B2.");
    }

    private void verifyThreatUsesPerceivedData() {
        var simulation = controlled2v2(7_112L);
        var flanker = simulation.blueTeam[1];
        var interceptor = simulation.redTeam[1];
        interceptor.state = CombatState.ATTACK_HOLD;
        interceptor.angle = Math.PI;
        observeExactly(flanker, interceptor);
        var before = simulation.assessThreat(flanker, interceptor);
        interceptor.pos = new Pos(7.8, 6.8);
        interceptor.velocity = new Pos(1.7, 0.9);
        interceptor.angle = Math.PI / 2;
        interceptor.weaponYaw = Math.toRadians(45);
        interceptor.state = CombatState.RECOVERY;
        var after = simulation.assessThreat(flanker, interceptor);
        require(before.equals(after),
                "Threat assessment must consume A2's stored observation, not B2's hidden live data.");
    }

    private void verifyHighThreatCreatesDuels() {
        var simulation = intercepted2v2(7_103L);
        var engagement = simulation.engagements().getFirst();
        var flanker = simulation.blueTeam[1];
        var interceptor = simulation.redTeam[1];
        require(flanker.threatResponse == ThreatResponse.ENGAGE &&
                        flanker.target == interceptor,
                "A credible immediate B2 threat must suspend A2's flank.");
        require(engagement.status == EngagementStatus.BROKEN &&
                        simulation.engagements().size() == 3,
                "A successful physical interception must break the 2v1 and create two duels.");
        require(simulation.engagements().stream().filter(e -> e.type == EngagementType.DUEL).count() == 2,
                "The transition must produce exactly A1-B1 and A2-B2 duels.");
    }

    private void verifyDuelTargetsRemainStable() {
        var simulation = intercepted2v2(7_107L);
        for (var agent : simulation.agents) agent.targetLockTime = 0;
        simulation.redTeam[1].state = CombatState.NEUTRAL;
        simulation.redTeam[1].pos = simulation.blueTeam[0].pos.plus(0.7, 0);
        for (var tick = 0; tick < 180 && simulation.winningTeam == null; ++tick)
            simulation.update(1.0 / 120.0);
        for (var engagement : simulation.engagements()) {
            if (engagement.type != EngagementType.DUEL ||
                    engagement.status == EngagementStatus.FINISHED) continue;
            require(engagement.duelistOne.target == engagement.duelistTwo &&
                            engagement.duelistTwo.target == engagement.duelistOne,
                    "Living duelists must retain their planned opponent after the 0.8 second lock.");
        }
    }

    private void verifyThreatHysteresis() {
        var simulation = controlled2v2(7_104L);
        var flanker = simulation.blueTeam[1];
        flanker.threatResponse = ThreatResponse.ENGAGE;
        flanker.pendingThreatResponse = ThreatResponse.ENGAGE;
        flanker.threatAssessment = assessment(0.64);
        simulation.updateThreatResponse(flanker, 0.50);
        require(flanker.threatResponse == ThreatResponse.ENGAGE,
                "A score just below 0.65 must not immediately abandon an established engagement.");
        flanker.threatAssessment = assessment(0.31);
        simulation.updateThreatResponse(flanker, 0.50);
        require(flanker.threatResponse == ThreatResponse.ENGAGE,
                "The engagement must remain stable above the 0.30 resume threshold.");
        flanker.threatAssessment = assessment(0.29);
        simulation.updateThreatResponse(flanker, 0.50);
        require(flanker.threatResponse == ThreatResponse.IGNORE,
                "A2 may release the interception only below the lower hysteresis threshold.");
    }

    private void verifyFailedInterception() {
        var simulation = controlled2v2(7_105L);
        var engagement = simulation.engagements().getFirst();
        engagement.flanker.pos = engagement.flankerDestination();
        engagement.interceptor.pos = new Pos(7.6, 0.4);
        simulation.update(1.0 / 120.0);
        require(engagement.interceptor.interceptionPhase == InterceptionPhase.FAILED &&
                        engagement.status != EngagementStatus.BROKEN,
                "A late B2 must fail naturally while the original 2v1 remains.");
        require(engagement.interceptor.lastInterceptionReason.equals("flanker_reached_slot"),
                "The failed interception must preserve its physical reason.");
    }

    private void verifyControlledEliminations() {
        var b2Dead = controlled2v2(7_108L);
        kill(b2Dead.redTeam[1]);
        b2Dead.update(1.0 / 120.0);
        var blueAttack = latestTwoVsOne(b2Dead);
        require(blueAttack.fixer == b2Dead.blueTeam[0] &&
                        blueAttack.flanker == b2Dead.blueTeam[1] &&
                        blueAttack.primaryTarget == b2Dead.redTeam[0] &&
                        b2Dead.redTeam[0].tacticalIntent == TacticalIntent.SURVIVE,
                "B2's death must rebuild A1 FIX + A2 FLANK against B1 SURVIVE.");

        var a2Dead = controlled2v2(7_109L);
        kill(a2Dead.blueTeam[1]);
        a2Dead.update(1.0 / 120.0);
        var redAttack = latestTwoVsOne(a2Dead);
        require(redAttack.fixer == a2Dead.redTeam[0] &&
                        redAttack.flanker == a2Dead.redTeam[1] &&
                        redAttack.primaryTarget == a2Dead.blueTeam[0] &&
                        a2Dead.blueTeam[0].tacticalIntent == TacticalIntent.SURVIVE,
                "A2's death must rebuild B1 FIX + B2 FLANK against A1 SURVIVE.");

        var b1Dead = controlled2v2(7_110L);
        kill(b1Dead.redTeam[0]);
        b1Dead.update(1.0 / 120.0);
        require(latestTwoVsOne(b1Dead).primaryTarget == b1Dead.redTeam[1],
                "B1's death must make A1 and A2 reevaluate B2.");

        var a1Dead = controlled2v2(7_111L);
        kill(a1Dead.blueTeam[0]);
        a1Dead.update(1.0 / 120.0);
        require(latestTwoVsOne(a1Dead).primaryTarget == a1Dead.blueTeam[1] &&
                        a1Dead.blueTeam[1].tacticalIntent == TacticalIntent.SURVIVE,
                "A1's death must make A2 abandon its flank and survive against both Reds.");
    }

    private Engagement latestTwoVsOne(DuelSimulation simulation) {
        for (var n = simulation.engagements().size() - 1; n >= 0; --n) {
            var engagement = simulation.engagements().get(n);
            if (engagement.type == EngagementType.TWO_VS_ONE &&
                    engagement.status != EngagementStatus.BROKEN &&
                    engagement.status != EngagementStatus.FINISHED) return engagement;
        }
        throw new IllegalStateException("No active controlled 2v1 engagement.");
    }

    private void kill(Musketeer agent) {
        agent.alive = false;
        agent.state = CombatState.DEAD;
        agent.hitPoints = 0;
    }

    private ThreatAssessment assessment(double score) {
        var response = score >= 0.65 ? ThreatResponse.ENGAGE :
                score >= 0.55 ? ThreatResponse.DEFENSIVE_ATTENTION :
                        score >= 0.35 ? ThreatResponse.ADJUST_PATH : ThreatResponse.IGNORE;
        return new ThreatAssessment(score, true, score >= 0.65, 0.2, response);
    }

    private void observeExactly(Musketeer observer, Musketeer source) {
        var awareness = observer.opponentAwareness.get(source);
        awareness.visible = true;
        awareness.lastKnown = source.pos;
        awareness.perceivedVelocity = source.velocity;
        awareness.observedBodyAngle = source.angle;
        awareness.observedWeaponAngle = source.weaponWorldAngle();
        awareness.observedProjectedRange = source.projectedThrustLength();
        awareness.observedState = source.state;
        awareness.observedAttackDirection = source.attackDirection;
        awareness.memoryTime = 1.5;
    }

    private DuelSimulation intercepted2v2(long seed) {
        var simulation = controlled2v2(seed);
        var engagement = simulation.engagements().getFirst();
        var flanker = engagement.flanker;
        var interceptor = engagement.interceptor;
        engagement.fixer.pos = new Pos(1.2, 5.8);
        engagement.primaryTarget.pos = new Pos(6.8, 5.8);
        flanker.pos = new Pos(3.0, 2.0);
        flanker.angle = 0;
        interceptor.pos = new Pos(4.30, 2.0);
        interceptor.angle = Math.PI;
        interceptor.weaponYaw = 0;
        interceptor.velocity = new Pos(-0.7, 0);
        interceptor.state = CombatState.ATTACK_HOLD;
        interceptor.interceptionPhase = InterceptionPhase.PRESENT_THREAT;
        observeExactly(flanker, interceptor);
        simulation.updateEngagementInterceptions(0.50);
        return simulation;
    }

    private void verifyDeterministicSeed() {
        var first = controlled(9_817L);
        var second = controlled(9_817L);
        for (var tick = 0; tick < 600; ++tick) {
            first.update(1.0 / 120.0);
            second.update(1.0 / 120.0);
            for (var n = 0; n < first.agents.length; ++n) {
                var left = first.agents[n];
                var right = second.agents[n];
                require(left.pos.equals(right.pos) && left.state == right.state &&
                                left.decision == right.decision && left.alive == right.alive &&
                                left.attackDirection == right.attackDirection &&
                                targetIndex(first, left.target) == targetIndex(second, right.target),
                        "Equal seeds must produce the same simulation state.");
            }
            require(first.winningTeam == second.winningTeam,
                    "Equal seeds must produce the same winner.");
        }
        var first2v2 = controlled2v2(9_818L);
        var second2v2 = controlled2v2(9_818L);
        for (var tick = 0; tick < 600; ++tick) {
            first2v2.update(1.0 / 120.0);
            second2v2.update(1.0 / 120.0);
            for (var n = 0; n < first2v2.agents.length; ++n) {
                var left = first2v2.agents[n];
                var right = second2v2.agents[n];
                require(left.pos.equals(right.pos) && left.state == right.state &&
                                left.decision == right.decision && left.alive == right.alive &&
                                left.tacticalIntent == right.tacticalIntent &&
                                left.threatResponse == right.threatResponse &&
                                targetIndex(first2v2, left.target) == targetIndex(second2v2, right.target),
                        "Equal controlled 2v2 seeds must preserve the same complete Agent state.");
            }
            require(first2v2.winningTeam == second2v2.winningTeam,
                    "Equal controlled 2v2 seeds must produce the same result.");
        }
        require(first2v2.tacticalLog().equals(second2v2.tacticalLog()),
                "Equal controlled 2v2 seeds must produce identical tactical transitions.");
    }

    private DuelSimulation controlled(long seed) {
        return new DuelSimulation(AgentConfiguration.defaultBlue(), AgentConfiguration.defaultBlueTwo(),
                AgentConfiguration.defaultRed(), AgentConfiguration.defaultRedTwo(),
                SimulationScenario.CONTROLLED_2V1, seed);
    }

    private DuelSimulation legacy2v2(long seed) {
        return new DuelSimulation(AgentConfiguration.defaultBlue(), AgentConfiguration.defaultBlueTwo(),
                AgentConfiguration.defaultRed(), AgentConfiguration.defaultRedTwo(),
                SimulationScenario.LEGACY_2V2, seed);
    }

    private DuelSimulation controlled2v2(long seed) {
        return controlled2v2(ScenarioFamily.BASELINE, seed);
    }

    private DuelSimulation controlled2v2(ScenarioFamily family, long seed) {
        return new DuelSimulation(AgentConfiguration.defaultBlue(), AgentConfiguration.defaultBlueTwo(),
                AgentConfiguration.defaultRed(), AgentConfiguration.defaultRedTwo(),
                SimulationScenario.CONTROLLED_2V2, family, seed);
    }

    private int targetIndex(DuelSimulation simulation, Musketeer target) {
        if (target == null) return -1;
        for (var n = 0; n < simulation.agents.length; ++n)
            if (simulation.agents[n] == target) return n;
        return -2;
    }

    private void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
