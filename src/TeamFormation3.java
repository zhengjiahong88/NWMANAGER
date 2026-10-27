import java.util.Arrays;
import java.util.Set;

final class TeamFormation3 {
    final Team team;
    final Musketeer[] members;
    Musketeer leftWing, centerPlayer, rightWing;
    Pos center = new Pos(), forward = new Pos(1, 0), lateral = new Pos(0, 1);
    double width, depth, cohesion, roleAge;
    int roleChanges, roleThrashing, livingMembers;

    TeamFormation3(Team team, Musketeer[] members, Pos enemyCenter) {
        if (members.length != 3) throw new IllegalArgumentException("TeamFormation3 requires three members.");
        this.team = team;
        this.members = members.clone();
        updateGeometry(enemyCenter);
        assignSortedRoles();
    }

    void update(Pos enemyCenter, double dt, boolean immediate) {
        roleAge += dt;
        updateGeometry(enemyCenter);
        var sorted = sortedLivingMembers();
        if (sorted.length == 0) return;
        if (sorted.length != livingMembers) immediate = true;
        livingMembers = sorted.length;
        if (sorted.length < 3) {
            if (immediate || rolesDiffer(sorted)) assignLivingRoles(sorted);
            return;
        }
        if (leftWing == null || centerPlayer == null || rightWing == null ||
                !leftWing.alive || !centerPlayer.alive || !rightWing.alive) immediate = true;
        if (!immediate && roleAge < minimumRoleDuration()) return;
        var leftChanged = sorted[0] != leftWing;
        var rightChanged = sorted[2] != rightWing;
        if (!immediate && (!leftChanged || projection(sorted[0]) > projection(leftWing) - 0.30) &&
                (!rightChanged || projection(sorted[2]) < projection(rightWing) + 0.30)) return;
        if (sorted[0] == leftWing && sorted[1] == centerPlayer && sorted[2] == rightWing) return;
        if (roleAge < 0.25) ++roleThrashing;
        leftWing = sorted[0];
        centerPlayer = sorted[1];
        rightWing = sorted[2];
        roleAge = 0;
        ++roleChanges;
    }

    FormationRole3 roleOf(Musketeer member) {
        if (livingMembers == 1) return FormationRole3.CENTER;
        if (livingMembers == 2) return member == leftWing ? FormationRole3.LEFT : FormationRole3.RIGHT;
        if (member == leftWing) return FormationRole3.LEFT;
        if (member == rightWing) return FormationRole3.RIGHT;
        return FormationRole3.CENTER;
    }

    void purgeDeadReferences(Set<Musketeer> alive) {
        if (!alive.contains(leftWing)) leftWing = null;
        if (!alive.contains(centerPlayer)) centerPlayer = null;
        if (!alive.contains(rightWing)) rightWing = null;
    }

    Musketeer member(FormationRole3 role) {
        return switch (role) {
            case LEFT -> leftWing;
            case CENTER -> centerPlayer;
            case RIGHT -> rightWing;
        };
    }

    private void updateGeometry(Pos enemyCenter) {
        var total = new Pos();
        var alive = 0;
        for (var member : members)
            if (member.alive) {
                total = total.plus(member.pos);
                ++alive;
            }
        if (alive > 0) center = total.div(alive);
        var towardEnemy = enemyCenter.minus(center);
        if (towardEnemy.hypotenuse() > 0.0001) forward = towardEnemy.div(towardEnemy.hypotenuse());
        lateral = new Pos(-forward.y(), forward.x());
        var minimumLateral = Double.POSITIVE_INFINITY;
        var maximumLateral = Double.NEGATIVE_INFINITY;
        var minimumForward = Double.POSITIVE_INFINITY;
        var maximumForward = Double.NEGATIVE_INFINITY;
        for (var member : members) {
            if (!member.alive) continue;
            var offset = member.pos.minus(center);
            minimumLateral = Math.min(minimumLateral, offset.dot(lateral));
            maximumLateral = Math.max(maximumLateral, offset.dot(lateral));
            minimumForward = Math.min(minimumForward, offset.dot(forward));
            maximumForward = Math.max(maximumForward, offset.dot(forward));
        }
        width = alive == 0 ? 0 : maximumLateral - minimumLateral;
        depth = alive == 0 ? 0 : maximumForward - minimumForward;
        if (alive <= 1) cohesion = 1;
        else if (alive == 2) {
            var living = sortedLivingMembers();
            cohesion = Math.clamp(1 - Math.abs(living[0].pos.minus(living[1].pos).hypotenuse() -
                    1.35) / 1.35, 0, 1);
        } else {
            var leftGap = leftWing == null || centerPlayer == null ? 1.35 :
                    leftWing.pos.minus(centerPlayer.pos).hypotenuse();
            var rightGap = rightWing == null || centerPlayer == null ? 1.35 :
                    rightWing.pos.minus(centerPlayer.pos).hypotenuse();
            cohesion = Math.clamp(1 - (Math.abs(leftGap - 1.35) +
                    Math.abs(rightGap - 1.35)) / 2.2, 0, 1);
        }
    }

    private void assignSortedRoles() {
        var sorted = sortedLivingMembers();
        livingMembers = sorted.length;
        assignLivingRoles(sorted);
    }

    private Musketeer[] sortedLivingMembers() {
        var count = 0;
        for (var member : members)
            if (member.alive) ++count;
        var sorted = new Musketeer[count];
        var index = 0;
        for (var member : members)
            if (member.alive) sorted[index++] = member;
        Arrays.sort(sorted, (first, second) -> Double.compare(projection(first), projection(second)));
        return sorted;
    }

    private void assignLivingRoles(Musketeer[] sorted) {
        if (sorted.length == 0) {
            leftWing = centerPlayer = rightWing = null;
            return;
        }
        var previousLeft = leftWing;
        leftWing = sorted[0];
        rightWing = sorted[sorted.length - 1];
        centerPlayer = sorted.length == 3 ? sorted[1] :
                sorted.length == 2 ? Math.abs(projection(sorted[0])) <=
                        Math.abs(projection(sorted[1])) ? sorted[0] : sorted[1] : sorted[0];
        if (previousLeft != null && previousLeft != leftWing) ++roleChanges;
        roleAge = 0;
    }

    private boolean rolesDiffer(Musketeer[] sorted) {
        return sorted.length == 1 ? centerPlayer != sorted[0] :
                leftWing != sorted[0] || rightWing != sorted[sorted.length - 1];
    }

    private double projection(Musketeer member) {
        return member.pos.minus(center).dot(lateral);
    }

    private double minimumRoleDuration() {
        var quality = 0.0;
        var alive = 0;
        for (var member : members)
            if (member.alive) {
                quality += member.stats.decisionMaking() + member.stats.teamworkAbility();
                ++alive;
            }
        quality /= Math.max(1, alive * 200.0);
        return 0.55 + quality * 0.35;
    }
}
