import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class EngagementManager {
    private final ArrayList<Engagement> engagements = new ArrayList<>();
    private double simulationTime;

    void reset() {
        engagements.clear();
        simulationTime = 0;
    }

    void update(double dt) {
        simulationTime += dt;
        for (var engagement : engagements) {
            if (engagement.status == EngagementStatus.BROKEN ||
                    engagement.status == EngagementStatus.FINISHED) continue;
            var finished = switch (engagement.type) {
                case GROUP_2V2 -> living(engagement.group.blue) < 2 ||
                        living(engagement.group.red) < 2;
                case GROUP_3V3 -> living(engagement.group3.blueMembers) == 0 ||
                        living(engagement.group3.redMembers) == 0;
                case DUEL -> !engagement.duelistOne.alive || !engagement.duelistTwo.alive;
                case TWO_VS_ONE -> !engagement.primaryTarget.alive ||
                        engagement.fixer != null && !engagement.fixer.alive &&
                                engagement.flanker != null && !engagement.flanker.alive;
            };
            if (finished)
                engagement.status = EngagementStatus.FINISHED;
            else engagement.activate();
        }
    }

    Engagement createTwoVsOne(Musketeer fixer, Musketeer flanker, Musketeer defender) {
        return createTwoVsOne(fixer, flanker, defender, EngagementSlot.RIGHT);
    }

    Engagement createTwoVsOne(Musketeer fixer, Musketeer flanker, Musketeer defender,
                              EngagementSlot flankSlot) {
        var engagement = new Engagement(EngagementType.TWO_VS_ONE, defender, fixer, flanker,
                EngagementSlot.FRONT, flankSlot, simulationTime);
        engagements.add(engagement);
        fixer.assignTacticalIntent(engagement, TacticalIntent.FIX, EngagementSlot.FRONT, defender,
                "two_vs_one_assignment");
        flanker.assignTacticalIntent(engagement, TacticalIntent.FLANK, flankSlot, defender,
                "two_vs_one_assignment");
        defender.assignTacticalIntent(engagement, TacticalIntent.SURVIVE, null, fixer,
                "outnumbered_defender");
        return engagement;
    }

    void breakActive() {
        for (var engagement : engagements)
            if (engagement.status == EngagementStatus.FORMING ||
                    engagement.status == EngagementStatus.ACTIVE ||
                    engagement.status == EngagementStatus.CONTESTED)
                engagement.status = EngagementStatus.BROKEN;
    }

    Engagement createDuel(Musketeer first, Musketeer second) {
        var engagement = new Engagement(first, second, simulationTime);
        engagements.add(engagement);
        first.assignTacticalIntent(engagement, TacticalIntent.DUEL, null, second, "duel_assignment");
        second.assignTacticalIntent(engagement, TacticalIntent.DUEL, null, first, "duel_assignment");
        return engagement;
    }

    Engagement createGroup2v2(Musketeer[] blue, Musketeer[] red,
                              RotationDirection rotation) {
        var group = new GroupEngagement(blue, red, rotation, simulationTime);
        var engagement = new Engagement(group, simulationTime);
        engagements.add(engagement);
        return engagement;
    }

    Engagement createGroup3v3(Musketeer[] blue, Musketeer[] red) {
        var engagement = new Engagement(new Group3v3Engagement(blue, red), simulationTime);
        engagements.add(engagement);
        return engagement;
    }

    private int living(Musketeer[] team) {
        var count = 0;
        for (var agent : team)
            if (agent.alive) ++count;
        return count;
    }

    List<Engagement> engagements() {
        return Collections.unmodifiableList(engagements);
    }
}
