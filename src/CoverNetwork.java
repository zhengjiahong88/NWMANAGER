import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

final class CoverNetwork {
    final Team team;
    final Musketeer[] members;
    final ArrayList<CoverLink> links = new ArrayList<>();
    final IdentityHashMap<Musketeer, List<CoverLink>> incomingLinks = new IdentityHashMap<>(),
            outgoingLinks = new IdentityHashMap<>();
    final IdentityHashMap<Musketeer, Double> coverageByMember = new IdentityHashMap<>();
    final HashSet<CoverRelation> viableRelations = new HashSet<>();
    double connectivity, weakestMemberCoverage;
    Musketeer weakestMember;

    CoverNetwork(Team team, Musketeer[] members) {
        this.team = team;
        this.members = members.clone();
        for (var member : members) {
            incomingLinks.put(member, new ArrayList<>());
            outgoingLinks.put(member, new ArrayList<>());
            coverageByMember.put(member, 0.0);
        }
    }

    void rebuild(List<CoverLink> evaluatedLinks, Musketeer[] enemies) {
        links.clear();
        links.addAll(evaluatedLinks);
        viableRelations.clear();
        for (var member : members) {
            incomingLinks.get(member).clear();
            outgoingLinks.get(member).clear();
        }
        var viable = 0;
        for (var link : links) {
            incomingLinks.get(link.protectedAlly()).add(link);
            outgoingLinks.get(link.protector()).add(link);
            if (!link.viable()) continue;
            ++viable;
            viableRelations.add(new CoverRelation(link.protector(), link.protectedAlly(),
                    link.threateningEnemy()));
        }
        var livingMembers = 0;
        var livingEnemies = 0;
        for (var member : members)
            if (member.alive) ++livingMembers;
        for (var enemy : enemies)
            if (enemy.alive) ++livingEnemies;
        var possibleLinks = livingMembers * Math.max(0, livingMembers - 1) * livingEnemies;
        connectivity = possibleLinks == 0 ? 0 : viable / (double) possibleLinks;
        weakestMember = null;
        weakestMemberCoverage = Double.POSITIVE_INFINITY;
        for (var member : members) {
            if (!member.alive) {
                coverageByMember.put(member, 0.0);
                continue;
            }
            var coverage = 0.0;
            for (var enemy : enemies) {
                if (!enemy.alive) continue;
                var best = 0.0;
                for (var link : incomingLinks.get(member))
                    if (link.threateningEnemy() == enemy && link.viable()) best = Math.max(best, link.quality());
                coverage += best;
            }
            coverage /= Math.max(1, livingEnemies);
            coverageByMember.put(member, coverage);
            if (coverage < weakestMemberCoverage) {
                weakestMemberCoverage = coverage;
                weakestMember = member;
            }
        }
    }

    double coverageOf(Musketeer member) {
        return coverageByMember.getOrDefault(member, 0.0);
    }

    int activeLinks() {
        return viableRelations.size();
    }

    void purgeDeadReferences(Set<Musketeer> alive) {
        links.removeIf(link -> !alive.contains(link.protector()) ||
                !alive.contains(link.protectedAlly()) || !alive.contains(link.threateningEnemy()));
        viableRelations.removeIf(relation -> !alive.contains(relation.protector()) ||
                !alive.contains(relation.protectedAlly()) || !alive.contains(relation.threateningEnemy()));
        for (var entries : incomingLinks.values())
            entries.removeIf(link -> !alive.contains(link.protector()) ||
                    !alive.contains(link.protectedAlly()) || !alive.contains(link.threateningEnemy()));
        for (var entries : outgoingLinks.values())
            entries.removeIf(link -> !alive.contains(link.protector()) ||
                    !alive.contains(link.protectedAlly()) || !alive.contains(link.threateningEnemy()));
        if (!alive.contains(weakestMember)) weakestMember = null;
    }

    CoverLink bestLink(Musketeer protector, Musketeer protectedAlly) {
        CoverLink best = null;
        for (var link : outgoingLinks.get(protector))
            if (link.protectedAlly() == protectedAlly && (best == null || link.quality() > best.quality()))
                best = link;
        return best;
    }
}
