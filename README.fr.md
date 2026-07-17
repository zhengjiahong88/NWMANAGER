# NWMANAGER

[中文說明](README.md)

NWMANAGER est un prototype observable de combat 2v2 à la baïonnette en vue de dessus. Deux Agents bleus affrontent deux Agents rouges. Chacun se déplace, choisit ou change de cible, réagit aux menaces immédiates, évite son coéquipier, prépare ses attaques, frappe, pare, subit des blessures et se replie. Le résultat n'est pas déterminé directement par un tirage fondé sur les statistiques : il dépend de la position réelle, de l'orientation, de la pointe de la baïonnette, de la direction d'attaque et de parade, du temps de réaction, de la zone touchée et des dégâts.

## Fonctionnalités actuelles

- Menu principal bilingue, écran Jouer et sélection du Match amical 2v2
- Création des deux équipes avec nom, tactique et onze statistiques modifiables pour chacun des quatre joueurs
- Validation des statistiques de 1 à 100 et lancement du combat dans la même fenêtre
- Déplacement libre en 2D avec inertie, trajectoires obliques, passages latéraux et sortie progressive des échanges
- IA défensive privilégiant la survie et la contre-attaque
- Préparation, maintien, phase active et récupération d'une attaque
- Cycle tactique `PROBE → COMMIT → PASS_THROUGH → DISENGAGE → RESET`
- Feinte vers une autre attaque ou vers une parade, parade préparée/maintenue, changement de garde et riposte
- Accel et drag intentionnels par manipulation continue du pitch et du yaw pendant l'estoc
- Attaques hautes et basses ; parades hautes, basses ou latérales
- Collision balayée entre l'ancienne et la nouvelle position de la pointe, plus collision corporelle souple
- Sélection et changement de cible, réaction prioritaire à une attaque latérale, soutien simple et évitement du coéquipier
- Victoire uniquement lorsque les deux membres d'une équipe sont éliminés ; match nul possible si les deux équipes tombent au même pas physique
- 100 points de vie, zones tête／torse／jambes et dégâts cumulés
- Erreurs d'exécution liées aux statistiques : mauvaise parade, coup à côté, erreur de distance ou mauvais choix
- Flèches de combat, texte de dégâts et poses de parade distinctes
- Panneaux affichant les statistiques, les PV, l'état, la décision, la direction, la distance, la cible et la dernière blessure
- Quatre fiches latérales, lignes pointillées rondes vers la cible actuelle et ligne carrée vers une cible de dézonage planifiée
- Pause, redémarrage et vitesses de simulation 0,25×, 0,5×, 1×, 2× et 4×
- Interface française par défaut et bouton permettant de basculer instantanément entre le français et le chinois
- Recul ralenti, orientations horizontale/verticale et étourdissement sur parade
- Angle latéral de baïonnette indépendant du corps et appât pouvant provoquer un estoc trop court ou une avance maîtrisée de contre-appât
- Statistique « Sortir de sa zone » et dézonage prolongé en parade, en maintien ou en posture neutre
- Dézonage de cible : rupture visible du vis-à-vis, traversée oblique, changement de cible puis stab du deuxième ennemi
- Dixième statistique « Chamber » de 1 à 100, chamber simple blocable et re-chamberable, plus chamber parfait extrêmement rare au-dessus de 90
- Onzième statistique « Double Block » de 1 à 100 : changement très difficile de parade haute/basse contre deux estocs rapprochés en 2v1
- Backstab géométrique impossible à parer ou à chamber, alerte de dos exposé et protection du dos dérivée des aptitudes existantes
- Perception périphérique imparfaite : le deuxième adversaire latéral devient plus difficile à voir avec la distance, puis sa dernière position n'est mémorisée que temporairement

## Déplacement et angles du personnage

Le moteur conserve maintenant une vélocité `velocityX/Y` au lieu d'ajouter directement chaque décision à la position. La vitesse de base dépend du jeu de jambes :

```text
vitesse de base = 1,05 + 0,006 × jeu de jambes
```

Les coefficients d'état sont :

```text
estoc actif : × 0,86
récupération : × 0,92
feinte : × 0,84
parade préparée ou maintenue : × 0,78
composante arrière : × 0,62 supplémentaire
```

L'accélération avec laquelle la vélocité rejoint le vecteur désiré vaut :

```text
A = 4,6 + 0,045 × jeu de jambes
A pendant l'estoc = A × 0,72
A pendant la récupération = A × 0,64
```

La variation de vélocité est limitée à `A × dt`. Sans entrée, une friction de `3,2 + 0,018 × jeu de jambes` par seconde ramène progressivement la vitesse à zéro. Un Agent ne peut donc plus inverser instantanément sa trajectoire. `movementHeading = atan2(velocityY, velocityX)` mémorise la vraie direction de course.

Chaque Agent possède quatre orientations ou directions distinctes :

- `movementHeading` : trajectoire réelle produite par l'inertie ;
- `angle` : orientation horizontale du corps ;
- `weaponYaw` : décalage gauche/droite de la baïonnette, limité à `±50°` ;
- `pitch`／`attackPitch` : inclinaison verticale, limitée à `±35°`.

Une inclinaison négative vise le sol et une inclinaison positive le ciel. L'arme se trouve à une hauteur de référence de `1,15 m`. L'IA calcule son inclinaison désirée à partir de la hauteur du coup et de la distance horizontale :

```text
pitch désiré = atan2(hauteur du coup - 1,15, distance horizontale)
```

La rotation du corps conserve `62 %` de sa vitesse pendant l'estoc, `68 %` en garde et `52 %` pendant la récupération. La baïonnette conserve respectivement `55 %` et `44 %` pendant l'estoc et la récupération. Pendant `PASS_THROUGH` et `DISENGAGE`, la course continue selon `movementHeading` tandis que le corps revient progressivement vers l'ennemi : les combattants peuvent donc se croiser sans marcher artificiellement à reculons.

## Perception périphérique du deuxième adversaire

L'IA ne connaît plus parfaitement les deux ennemis sur 360°. Chaque `Musketeer` possède une `IdentityHashMap<Musketeer, OpponentAwareness>` ; pour chaque adversaire, elle conserve `visible`, `lastKnownX/Y` et `memoryTime`. La perception est réévaluée toutes les :

```text
0,25 + U(0 ; 0,08) seconde
```

L'aptitude dérivée utilisée par cette lecture est :

```text
V = round(0,45 × réaction + 0,35 × décision + 0,20 × contrôle de distance)
```

Elle ne donne aucun bonus direct à la collision ou aux dégâts. La réaction aide à remarquer un mouvement, la décision répartit l'attention entre deux menaces et le contrôle de distance aide à situer une silhouette éloignée.

### Angle, distance et attention

Avec `α`, l'écart absolu entre l'orientation corporelle et la direction de l'adversaire, le coefficient angulaire continu vaut :

```text
0° ≤ α ≤ 60°    : A = 1,00 - 0,15 × α / 60
60° < α ≤ 115°  : A = 0,85 - 0,55 × (α - 60) / 55
115° < α ≤ 180° : A = 0,30 - 0,24 × (α - 115) / 65
```

Il passe donc de `1,00` droit devant à `0,30` à la limite du dos, puis `0,06` directement derrière. La distance et le niveau produisent :

```text
D = clamp(1 - max(0, distance - 1,5) / 4, 0,12, 1)
S = 0,45 + 0,0055 × V
```

Pour le deuxième adversaire, l'attention vaut `0,45` si la cible directe est à moins de `2,1 m` ou attaque déjà, sinon `0,72`. La cible actuellement suivie n'a pas cette pénalité. La base est :

```text
Pbase = A × D × S × attention
```

Être déjà visible ajoute `0,18`; attaquer ajoute `0,28`; se déplacer sans attaquer ajoute `0,06`. Un ennemi situé à moins de `1,6 m` et hors du dos conserve au moins `82 %`, tandis que la cible directe hors du dos conserve au moins `96 %`. Le résultat final est borné entre `2 % et 99 %`.

Un deuxième adversaire latéral à `1,5 m` reste donc généralement évident. À `4 m`, si l'Agent est occupé par un duel direct proche, plusieurs contrôles de perception peuvent échouer. Une attaque latérale augmente la visibilité grâce au mouvement, sans devenir une alerte omnisciente.

### Dernière position connue

Lors d'une détection, la dernière position est mémorisée avec :

```text
erreur de position σ = 0,01 + (100 - contrôle de distance) × 0,003 + (1 - A) × 0,08 m
durée de mémoire = 0,55 + (réaction + décision) / 200 × 1,30 seconde
```

Tant que `visible = true`, l'Agent utilise la position présente. Après la perte visuelle, `knownX()` et `knownY()` ne donnent que cette position légèrement fausse. L'Agent peut encore s'orienter vers son souvenir, mais ne lit plus l'attaque, la récupération, l'appât ou le dézonage actuels de cet ennemi. À l'expiration de `memoryTime`, il disparaît du choix de cible jusqu'à une nouvelle détection.

## Backstab et protection du dos

### Secteur arrière et résolution du coup

Le backstab ne remplace ni la collision ni la hauteur. La pointe doit d'abord entrer réellement dans le cercle du défenseur pendant la phase active, puis `HitZone.atHeight()` doit produire une tête, un torse ou des jambes valides. `DuelSimulation.checkHit()` mesure ensuite la position de l'attaquant par rapport à l'orientation corporelle du défenseur :

```text
θattaquant = atan2(attaquant.y - défenseur.y, attaquant.x - défenseur.x)
β = abs(normalizeAngle(θattaquant - défenseur.angle))
β ≥ 115° → backstab
```

Le calcul utilise `defender.angle`, l'orientation du corps, et non `weaponWorldAngle()`. Orienter la baïonnette à gauche ou à droite ne protège donc pas magiquement le dos. Lorsqu'un contact valide est un backstab, `checkHit()` saute entièrement les deux branches suivantes :

1. interception en `CHAMBER_PREPARE` ;
2. parade en `BLOCKING`, même si sa hauteur correspond et si l'arme serait dans les 75° normalement autorisés.

Le texte `BACKSTAB !` apparaît pendant `1,15 s`, puis les dégâts ordinaires de la zone sont appliqués : tête 100, torse 100 dans 95 % des cas sinon 50, jambes 40. Le système garantit donc qu'une défense dirigée vers l'avant ne bloque pas un coup arrière ; il ne transforme pas une pointe hors du corps, une hauteur ratée ou un coup dans les jambes en élimination artificielle.

### Opportunité de `FREEKILL`, mais exécution faillible

`findFreeKillTarget()` recherche un adversaire visible qui expose son dos. Le mot freekill désigne ici une excellente occasion tactique, pas une suppression automatique. Les cinq conditions sont :

1. la cible est actuellement `visible` ;
2. `isRearAttack(candidate, self)` confirme que l'Agent se trouve à au moins 115° derrière elle ;
3. la cible est à moins de 100° de l'axe actuel de la baïonnette ;
4. sa distance estimée ne dépasse pas `UP.thrustLength + BODY_RADIUS + 0,10` ;
5. l'adversaire direct est estimé assez loin pour ne pas punir l'engagement.

Les distances de la cible arrière et de l'adversaire direct sont lues avec :

```text
distance estimée = distance réelle ou mémorisée
                 + N(0 ; (100 - contrôle de distance) × 0,006)
```

La zone dangereuse de l'adversaire direct vaut :

```text
projectedThrustLength + BODY_RADIUS + 0,55 m
+ 0,35 m si l'Agent le voit attaquer
- 0,30 m si l'Agent le voit en récupération ou BLOCK_STUN
```

L'occasion n'est jugée sûre que si sa distance estimée dépasse ce seuil. Un mauvais contrôle de distance peut donc faire croire que le dos est à portée alors qu'il ne l'est pas, ou que l'adversaire direct est inoffensif alors qu'il peut encore frapper.

La fréquence d'utilisation par décision est :

```text
F = (0,45 × décision + 0,35 × contrôle de distance + 0,20 × agressivité) / 100
P(utiliser) = min(0,24 ; (0,01 + 0,14 × F²) × multiplicateur tactique)

Défensive : ×0,75 ; Neutre : ×1,00 ; Agressive : ×1,25
```

Lorsqu'il s'engage, l'Agent choisit l'attaque haute au-delà de `1,42 m` perçu et l'attaque basse sinon. `configureFreeKillAim()` vise volontairement le torse, mais conserve toute l'erreur horizontale et verticale de la précision. Après la préparation, `FREE_KILL_BACKSTAB` libère immédiatement l'estoc sans avancer pour corriger une mauvaise estimation.

Les conséquences restent physiques :

- pointe hors du cercle ou hauteur hors du corps : raté complet ;
- hauteur dans les jambes : 40 dégâts seulement ;
- contact réellement arrière à la tête ou au torse : 100 dégâts ;
- cible qui se retourne avant l'impact : le bonus de freekill disparaît et les règles ordinaires reviennent ;
- adversaire direct mal évalué : il peut frapper l'Agent pendant sa préparation ou sa récupération.

Un excellent Agent transforme ainsi une vraie ouverture, à bonne distance et sans menace directe proche, en élimination fiable. Un Agent faible peut frapper à côté, trop court, dans les jambes ou se faire punir.

### Aptitude dérivée

La protection du dos reste une valeur dérivée et n'est pas la statistique « Double Block » ajoutée en onzième position. `AgentStats.backProtection()` la calcule à partir des quatre aptitudes qui correspondent réellement à l'action :

```text
B = round(0,50 × jeu de jambes
        + 0,22 × réaction
        + 0,18 × prise de décision
        + 0,10 × sortir de sa zone)
```

Le jeu de jambes reste le facteur principal, comme demandé : il contrôle déjà la vitesse de translation et de rotation. La réaction réduit le temps avant la réponse, la décision améliore le choix du côté et le dézonage prolonge la sortie de l'axe d'encerclement. La fiche affiche `Dos : B` ; ce nombre n'est ni une réduction de dégâts ni une probabilité de bloquer un backstab.

### Détection, réaction et décision

`backExposed` continue de représenter la géométrie réelle afin que le spectateur voie l'arc rouge, mais l'IA n'utilise pas ce drapeau omniscient. `updateBackAwareness()` ne choisit `rearThreat` que parmi les adversaires visibles ou encore mémorisés, avec leur position connue. Une nouvelle menace reconnue démarre un délai réaliste :

```text
délai arrière = 0,10 + (100 - réaction) × 0,0032 + U(0 ; 0,06) seconde
```

Une réaction de 100 produit `0,10–0,16 s`; une réaction de 1 produit environ `0,417–0,477 s`. Si l'ennemi arrière attaque déjà, vise l'Agent dans un cône de 34° et se trouve dans sa portée projetée augmentée de la marge corporelle, la sortie devient prioritaire dès la fin de ce délai. Si l'ennemi se trouve seulement derrière, à moins de `3,5 m`, chaque décision admissible peut anticiper :

```text
P(commencer la protection du dos | conditions) = B / 650
```

Cette probabilité règle la fréquence d'utilisation de la manœuvre, jamais la validité physique d'un backstab.

### Sortie oblique et protection contre l'encerclement

`beginBackEscape()` annule le dézonage courant et choisit une avancée oblique gauche ou droite. Il n'utilise pas le recul ralenti :

```text
durée = 0,40 + 0,0035 × jeu de jambes + 0,0015 × sortir de sa zone
forward = 0,55 ; sideways = ±0,85
norme du vecteur ≈ 1,012 × vitesse de base
```

`backEscapeScore()` projette les deux positions possibles pendant `0,80 s`. Le score ne considère que les ennemis suivis par la vue ou la mémoire ; un adversaire entièrement perdu ne fournit donc aucune information magique. Il favorise l'espace restant jusqu'au bord, la distance minimale connue et surtout un angle plus petit entre les deux ennemis vus depuis la position candidate : réduire cet angle signifie sortir latéralement de la ligne avant/arrière. La probabilité de retenir le meilleur côté vaut :

```text
P(côté le plus sûr) = 0,30 + 0,0065 × B
```

Elle atteint 95 % pour `B = 100`, mais un Agent faible peut encore choisir le mauvais côté. S'il ne reste qu'un seul ennemi derrière lui, l'Agent peut se tourner progressivement vers lui. S'il existe à la fois un ennemi frontal et un ennemi arrière, `backProtectionFacing()` oriente le corps vers la bissectrice de leurs directions ; lorsqu'ils sont presque exactement opposés à plus de 170°, il choisit la perpendiculaire correspondant au côté de sortie. La baïonnette continue de viser la cible frontale, de sorte qu'une parade avant peut rester active pendant le déplacement oblique.

`selectTarget()` participe à cette sécurité : tant qu'un ennemi frontal reste suivi, les candidats arrière ne remplacent pas la cible et la cible actuelle reste verrouillée pendant la sortie. L'Agent n'effectue donc pas un demi-tour qui exposerait simplement son dos à l'autre adversaire. Le demi-tour normal ne redevient autorisé que lorsqu'il n'existe plus de menace frontale connue.

## Dézonage et statistique « Sortir de sa zone »

Chaque Agent possède désormais une neuvième statistique, `Sortir de sa zone`. Elle représente sa volonté de quitter un face-à-face statique, d'emmener l'échange vers une autre partie de la carte et sa capacité à préférer une avancée oblique efficace à un simple recul qui se fait rattraper. Le dézonage n'est pas une téléportation : c'est une intention persistante, visible pendant laquelle l'Agent continue réellement à se déplacer.

Le dézonage possède maintenant deux variantes : le déplacement spatial décrit en premier, et la rupture de vis-à-vis vers une autre cible. Le dézonage spatial ne peut commencer que si l'adversaire est neutre, si la distance perçue est comprise entre `1,30 et 2,80 m`, si l'Agent ne se trouve pas près d'une limite et si `dezoneCooldown` est terminé. L'IA réévalue sa décision environ toutes les `0,10 à 0,135 seconde`. Pour une évaluation admissible :

```text
Z = Sortir de sa zone, J = Jeu de jambes, D = Prise de décision
seuil de dézonage = floor((Z + J + D) / 3)
P(commencer | conditions remplies) = seuil de dézonage / 1000
durée = 0,55 + 0,005 × Z + U(0 ; 0,45) seconde
cooldown initial = durée + U(0,80 ; 1,30) seconde
```

Le cooldown diminue déjà pendant la manœuvre. Après sa fin, il reste donc entre `0,80 et 1,30 s` avant qu'un nouveau dézonage puisse commencer. Cela empêche plusieurs sorties successives de repousser progressivement les combattants à plusieurs mètres.

Une valeur élevée augmente donc la fréquence et la durée du mouvement. Une fois le dézonage déclenché, le mode est tiré ainsi :

```text
P(recul pur | distance ≤ 2,20 m) = 0,45 - 0,0035 × Z
P(avance oblique | pas de recul) = 0,45 + 0,004 × Z
P(orbite | pas de recul) = 1 - P(avance oblique | pas de recul)
P(gauche) = P(droite) = 0,5
```

- Recul pur : disponible uniquement au début à `2,20 m` ou moins. L'Agent s'éloigne avec le facteur de vitesse `0,62`, mais dès que la distance atteint `2,55 m`, `moveAndTurn()` transforme ce recul en avance oblique gauche ou droite. Le dézonage ne peut donc plus créer seul une séparation de quatre mètres.
- Avance oblique gauche/droite : `0,70` vers l'avant et `0,70` sur le côté, soit environ `0,99 × vitesse de base`. L'Agent avance tout en changeant le côté de l'échange.
- Orbite gauche/droite : déplacement latéral à pleine vitesse afin de modifier l'angle autour de l'adversaire sans volontairement réduire la distance.

Plus Z est haut, moins l'Agent choisit le recul pur et plus il privilégie l'avance oblique parmi les options restantes. Une posture est également choisie pour toute la manœuvre. Avec `B = timing défensif`, `O = timing offensif` et `T = 100 + B + O` :

```text
P(posture neutre) = 100 / T
P(rester en parade) = B / T
P(rester en maintien) = O / T
```

En parade, le blocage reste actif jusqu'à la fin du dézonage. En maintien, l'Agent termine sa préparation puis reste en `ATTACK_HOLD` : la limite de maintien ordinaire ne libère pas automatiquement l'estoc au milieu du déplacement. Les règles normales de fin de parade ou de libération reprennent ensuite. Si l'Agent arrive près du bord, il interrompt la manœuvre et revient vers le centre.

L'adversaire peut suivre. Il doit être libre de décider, se trouver à plus de `1,45 m`, et le dézoneur ne doit pas être en maintien d'attaque. Pour chaque décision admissible :

```text
P(suivre) = (agressivité du poursuivant + décision du poursuivant) / 200
```

Avec les valeurs actuelles, l'Agent bleu possède Z = `74` : seuil `76/1000`, durée `0,92–1,37 s` et recul pur `19,1 %`. L'Agent rouge possède Z = `56` : seuil `58/1000`, durée `0,83–1,28 s` et recul pur `25,4 %`. Le bleu sort donc plus souvent de sa position et choisit plus fréquemment une coupe oblique vers l'avant.

### Dézonage de cible : quitter son vis-à-vis

Cette variante ne remplace pas le déplacement spatial. Elle permet à un Agent neutre de rompre son duel actuel pour stab l'autre adversaire, notamment celui déjà engagé par son coéquipier. `findTargetSwitchDezoneTarget()` exige un second ennemi actuellement visible, situé entre `0,90 et 3,20 m`. Ce candidat doit soit combattre le coéquipier, soit être en `RECOVERY`／`BLOCK_STUN`, soit montrer réellement son dos à l'Agent.

L'Agent évalue aussi la sécurité laissée par son vis-à-vis. Le contrôle de distance produit toujours une estimation imparfaite. Si l'ancien adversaire paraît encore capable de menacer immédiatement, une bonne prise de décision refuse le transfert ; une mauvaise décision peut accepter cette lecture dangereuse et offrir son dos.

L'aptitude dérivée de rupture de cible vaut :

```text
S = round(0,35 × sortir de sa zone
        + 0,25 × prise de décision
        + 0,20 × jeu de jambes
        + 0,10 × réaction
        + 0,10 × agressivité)
```

Pour chaque décision où un candidat admissible existe :

```text
P(commencer le transfert) = min(0,11 ; (0,012 + 0,065 × (S / 100)²) × tactique)

tactique Défensive = 0,65
tactique Neutre    = 1,00
tactique Agressive = 1,35
```

Une fois choisie, la traversée reste visible avant tout changement de cible :

```text
durée avant engagement = 0,72 - 0,0025 × S + U(0 ; 0,20) seconde
TARGET_SWITCH_LEFT  : forward = 0,55 ; sideways = -0,85
TARGET_SWITCH_RIGHT : forward = 0,55 ; sideways = +0,85
```

La direction correcte vers le nouvel ennemi est choisie avec `0,30 + 0,0068 × S`; un Agent faible peut partir du mauvais côté. Pendant cette première phase, `target` reste l'ancien vis-à-vis et la ligne de cible habituelle continue de le montrer. Une seconde ligne plus opaque terminée par un carré indique seulement la cible planifiée. Le texte `CHANGEMENT CIBLE`／`換目標脫離` et les décisions gauche/droite rendent l'intention observable.

La posture utilise les mêmes poids que le dézonage spatial : neutre, parade ou `ATTACK_HOLD`. L'Agent peut donc traverser en couvrant une direction ou en gardant un estoc préparé. À la fin du déplacement seulement, `commitTargetSwitchDezone()` :

1. vérifie que la nouvelle cible est encore vivante et visible ;
2. remplace réellement `target` et applique un verrouillage de `1,10 s` ;
3. transforme la parade en préparation d'attaque, ou réoriente l'estoc déjà maintenu ;
4. avance vers la nouvelle cible avec `DEZONE_SWITCH_ATTACK` ;
5. choisit, si le coéquipier attaque déjà cette cible et si la décision réussit, la hauteur opposée afin de créer une vraie menace haute + basse et une occasion de Double Block.

L'ancien vis-à-vis reste enregistré dans `dezoneOriginalTarget`. Tant qu'il reste visible et que le stab n'est pas parti, `updateTargetSwitchDezone()` abandonne la rupture si cet adversaire :

- lance une attaque orientée à moins de `34°` et entre dans sa portée projetée augmentée de `0,52 m` ; ou
- poursuit l'Agent, le cible encore et revient à moins de `1,42 m`.

L'abandon n'est pas instantané :

```text
délai d'abandon = 0,07 + (100 - réaction) × 0,0028 seconde
```

Après ce délai, l'Agent revient vers l'ancien adversaire. Il pare si une attaque frontale arrive, protège son dos si cet adversaire est désormais derrière, ou affiche l'abandon du changement de cible et recule. Si le délai est trop long, l'ancien vis-à-vis peut réellement le stab avant l'annulation. Une fois le nouvel estoc en phase active, il est trop tard pour annuler : montrer son dos devient alors une conséquence physique du choix tactique.

## Cycle d'engagement, feintes et manipulation de l'estoc

Le duel local n'est plus un simple choix « avancer ou reculer ». Chaque Agent conserve une `EngagementPhase` persistante qui organise son mouvement sur plusieurs décisions :

```text
PROBE → COMMIT → PASS_THROUGH → DISENGAGE → RESET → PROBE
```

- `PROBE` dure `0,45–1,20 s` : l'Agent teste l'angle avec `forward = 0,28` et `sideways = ±0,74`, ou prépare une garde anticipée.
- `COMMIT` dure `0,65–1,20 s` : il entre obliquement avec `forward = 0,88` et `sideways = ±0,52`, anticipe brièvement la vélocité adverse et cherche l'estoc.
- `PASS_THROUGH` dure `0,26–0,51 s` : il traverse avec `forward = 1` et `sideways = ±0,58` au lieu de s'arrêter face à face après le contact.
- `DISENGAGE` dure `0,42–0,80 s` : il conserve sa direction de course avec `forward = 0,94`, tourne progressivement le corps vers la menace et sort aussi dès que la distance atteint `2,20 m`.
- `RESET` dure `0,30–0,78 s` : un petit recul `-0,18` combiné à un pas latéral `±0,72` replace la garde avant la prochaine sonde.

Le côté gauche ou droit compare l'espace libre jusqu'aux limites. Sa bonne exécution dépend de :

```text
E = round(0,35 × jeu de jambes
        + 0,25 × contrôle de distance
        + 0,25 × décision
        + 0,15 × agressivité)

P(COMMIT à la fin d'une sonde)
= clamp(0,18 + 0,005 × E + tactique, 0,12, 0,86)

tactique agressive : +0,18 ; défensive : -0,12 ; neutre : 0
```

Les phases imposent un plan spatial, mais les urgences de survie, le backstab, le Double Block et les autres mécanismes prioritaires peuvent toujours l'interrompre.

### Mémoire des directions et feinte

`OpponentAwareness` mémorise séparément les nombres d'attaques hautes/basses et de gardes hautes/basses réellement observées. L'attaquant pénalise la répétition de sa dernière direction et tient compte de la garde présente ou habituelle de l'ennemi. Le choix haut/bas reste bruité par la décision : la distance contribue au score, mais ne fixe plus automatiquement la direction.

En `ATTACK_HOLD`, un Agent qui lit une garde adaptée ou remarque qu'il répète trop souvent le même côté peut passer par :

```text
ATTACK_HOLD → FEINT → ATTACK_PREPARE de la direction opposée
ATTACK_HOLD → FEINT → BLOCK_PREPARE si une menace arrive
```

L'aptitude de feinte vaut :

```text
F = round(0,35 × décision + 0,30 × timing offensif
        + 0,20 × agressivité + 0,15 × précision)
durée de feinte = 0,07 + 0,00125 × (100 - F) seconde
```

Un cooldown de `0,34–0,56 s` et une limite de deux feintes consécutives empêchent une boucle infinie.

### Parade préparée, garde maintenue et riposte

Une parade n'est plus active dès la décision. Elle suit :

```text
NEUTRAL → BLOCK_PREPARE → BLOCKING → NEUTRAL
```

```text
préparation = 0,035 + 0,00115 × (100 - timing défensif) seconde
maintien = 0,28 + 0,006 × timing défensif + U(0 ; 0,18) + tactique

tactique défensive : +0,25 s ; agressive : -0,08 s ; neutre : 0
```

Pendant `BLOCK_PREPARE`, la garde est visible mais n'intercepte pas encore. Une nouvelle attaque observée peut déclencher `SWITCH_GUARD` et repasser brièvement par la préparation dans l'autre direction. Après une parade, le défenseur peut rester couvert puis choisir `RIPOSTE` lorsque l'attaquant entre en récupération ; la riposte n'est donc pas une frappe gratuite et instantanée.

### Accel, drag et collision continue

La compétence de manipulation vaut :

```text
M = round(0,35 × timing offensif + 0,25 × précision
        + 0,20 × jeu de jambes + 0,20 × décision)
P(manipulation) = min(0,72 ; 0,08 + 0,0062 × M)
```

À courte distance, et davantage avec une tactique agressive, l'IA favorise l'`ACCEL`; la tactique défensive favorise légèrement le `DRAG`. Une manipulation choisie ajoute progressivement :

```text
offset vertical = ±(7 + 0,13 × M) degrés
offset latéral = ±(4 + 0,075 × M) degrés
```

L'accel applique son changement tôt dans l'estoc et descend la pointe ; le drag retarde le changement et l'élève. Ce ne sont pas des bonus de dégâts : le yaw, le pitch, la longueur projetée et la trajectoire réelle de la pointe déterminent encore le contact.

Pour ne pas perdre un impact entre deux pas physiques, le moteur calcule la distance minimale entre le défenseur en mouvement et tout le segment parcouru par la pointe depuis le pas précédent. Les corps utilisent en parallèle une séparation souple : une correction de confort de `18 %` commence sous `2 × BODY_RADIUS`, une limite dure subsiste à `1,18 × BODY_RADIUS`, et seulement `28 %` de la vitesse de fermeture est amortie. Les Agents peuvent ainsi se frôler et se croiser sans se téléporter ni rester soudés.

## Angle du corps, baïonnette gauche/droite et appât

L'orientation horizontale du corps et celle de la baïonnette sont maintenant indépendantes. Le petit trait blanc représente le yaw du corps. L'arme possède un angle latéral relatif :

```text
angle mondial de la baïonnette = yaw du corps + décalage latéral
décalage latéral ∈ [-50°, +50°]
```

Une valeur négative dirige l'arme vers la gauche du personnage et une valeur positive vers sa droite. La pointe, les collisions, la détection de menace, la libération de l'estoc et l'orientation de la parade utilisent toutes l'angle réel de la baïonnette, jamais simplement celui du corps.

Sa vitesse latérale est :

```text
vitesse de rotation de l'arme = 3,4 + 0,018 × jeu de jambes (rad/s)
```

Pendant la phase active, l'arme conserve 55 % de cette vitesse et pendant la récupération 44 %. Il reste donc possible de travailler l'angle sans remettre instantanément un estoc dans l'axe après son départ. Pendant le maintien, la baïonnette doit être à moins de `0,30 rad`, soit environ 17,2°, de la cible réelle pour une libération normale. Sinon l'Agent continue à maintenir ou finit par libérer automatiquement un coup probablement hors axe.

Un Agent peut créer une ouverture tactique lorsque l'adversaire est neutre et que la distance perçue se trouve entre `1,50 et 2,05 m`. À chaque décision :

```text
seuil d'appât = floor((prise de décision + contrôle de distance) / 4)
P(commencer l'appât) = seuil d'appât / 1000
```

L'Agent déplace alors sa baïonnette vers un côté aléatoire :

```text
amplitude latérale ~ U(22°, 40°)
durée ~ U(0,35 ; 0,75) seconde
```

L'adversaire ne considère l'ouverture comme visible que si l'arme s'écarte de son axe de plus de 18°. Il ne peut tomber dans le piège que lorsque la distance réelle se trouve entre `1,38 et 1,82 m`. Chaque appât possède un numéro distinct : un même adversaire ne fait qu'une seule lecture par ouverture, même si l'IA prend plusieurs décisions pendant sa durée.

La réussite de cette lecture dépend directement du contrôle de distance `C` et reprend la courbe normale d'exécution :

```text
si C ≤ 10 : A(C) = 0,50
si C > 10 : A(C) = 0,50 + 0,499 × (C - 10) / 90

P(résister à l'appât) = A(C)
P(tomber dans l'appât) = 1 - A(C)
```

S'il échoue, la séquence d'estoc trop court décrite ci-dessous commence. S'il réussit, il effectue un second choix tactique fondé sur la prise de décision `D`, le jeu de jambes `J` et l'agressivité `G` :

```text
P(avancer et contre-attaquer | distance correctement lue)
= (D + J + G) / 300
```

La prise de décision représente la compréhension du piège, le jeu de jambes représente à la fois le choix et la vitesse réelle d'approche, et l'agressivité représente la volonté d'entrer à portée. Si l'Agent refuse cette option, il se décale et ne réévalue plus la même ouverture.

Avec les statistiques actuelles :

| Agent | Tomber dans le piège | Lire puis avancer | Lire puis se décaler |
| --- | ---: | ---: | ---: |
| Bleu | 10,63 % | environ 63,15 % | environ 26,21 % |
| Rouge | 21,17 % | environ 50,45 % | environ 28,38 % |

Lorsqu'il choisit d'avancer, l'Agent prépare un estoc bas rapide et vise le centre du torse. Il continue à avancer pendant la préparation et le maintien ; la limite ordinaire de maintien ne libère pas prématurément son attaque. Il ne frappe que lorsque les deux conditions physiques sont vraies :

```text
distance réelle ≤ portée horizontale + rayon corporel - 0,08 m
écart entre la baïonnette et la cible < 0,30 rad
```

Cette branche est marquée par `CONTRE-APPÂT !`. Elle constitue une menace normale, et non un `baitedAttack` volontairement trop court. L'appâteur annule donc son ouverture. Son délai avant de pouvoir réagir reste :

```text
temps de réaction = 0,10 + (100 - réaction) × 0,0045 + U(0 ; 0,07)
```

Une fois ce délai écoulé, il tente une parade. Son timing défensif utilise la courbe normale d'exécution pour choisir la parade basse correcte. Une réaction assez rapide et une bonne direction bloquent l'estoc ; une réaction trop lente, une parade haute ou une parade latérale laissent passer la pointe. La conclusion reste calculée par la collision réelle.

Si la première lecture de distance échoue, la séquence spéciale suivante commence :

```text
APPÂT visible
→ PIÉGÉ : ATTACK_PREPARE
→ estoc haut lancé trop tôt
→ RECOVERY sans possibilité de parer
→ PUNITION maintenue par l'appâteur
→ libération de la punition pendant RECOVERY
```

Le joueur piégé croit être à portée, mais son estoc n'utilise que :

```text
portée piégée = portée horizontale normale × 0,62
récupération piégée = récupération normale × 1,55
```

Il reste immobile pendant la préparation et l'estoc : l'attaque est donc visiblement trop courte, plutôt qu'annulée par un tirage caché. Les états `ATTACK_PREPARE`, `ATTACK_ACTIVE` et `RECOVERY` interdisent déjà la parade ; la récupération prolongée matérialise la fenêtre pendant laquelle il ne peut rien bloquer.

L'appâteur sait que cet estoc ne peut pas l'atteindre. Il ne gaspille donc pas cette fenêtre en parant : il replace sa baïonnette, prépare un estoc haut au torse et avance seulement jusqu'à conserver une marge de `0,08 m` au-delà de la pointe piégée et du rayon corporel. Il maintient ensuite son attaque tant que l'adversaire frappe, puis la libère dès que celui-ci entre en `RECOVERY`. La punition doit encore faire entrer la pointe réelle dans le cercle adverse.

```text
distance de sécurité = portée piégée + rayon du corps (0,32 m) + 0,08 m
```

Après avoir respecté cette limite pendant l'estoc adverse, une collision physique réussie inflige :

```text
dégâts de punition d'appât = 100
```

Le joueur piégé meurt donc en un coup et ne peut pas transformer son estoc raté en parade instantanée. Avec les valeurs actuelles, le bleu possède `C = 81`, soit `89,37 %` de résistance et `10,63 %` de risque de tomber. Le rouge possède `C = 62`, soit `78,83 %` de résistance et `21,17 %` de risque. Le contrôle de distance devient ainsi la statistique principale de défense contre l'appât.

## Déroulement d'une attaque

La machine à états principale suit cette séquence :

```text
NEUTRAL
→ ATTACK_PREPARE
→ ATTACK_HOLD
→ ATTACK_ACTIVE
→ RECOVERY
→ NEUTRAL
```

Deux branches supplémentaires existent pendant le maintien : `ATTACK_HOLD → FEINT → ATTACK_PREPARE` pour changer la direction, ou `ATTACK_HOLD → FEINT → BLOCK_PREPARE` pour abandonner l'attaque et se couvrir. Une parade suit `BLOCK_PREPARE → BLOCKING`; elle ne protège donc pas pendant son temps de préparation.

Une attaque ne peut infliger des dégâts que pendant `ATTACK_ACTIVE`. Dès qu'elle est parée ou qu'elle touche, elle est marquée comme résolue et ne peut pas infliger plusieurs fois des dégâts pendant le même estoc.

Pendant `RECOVERY`, l'Agent ne peut ni attaquer ni parer. Le moteur retire d'abord sa composante de vélocité qui se rapproche encore de la cible, puis impose un recul avec un léger décalage latéral `±0,28`. Il conserve `92 %` de sa vitesse maximale, seulement `64 %` de son accélération normale et le facteur arrière `0,62`. La récupération reste donc visiblement un éloignement, comme dans la version précédente, sans redevenir un recul parfaitement rectiligne et instantané.

L'inclinaison verticale modifie toutes les durées de l'attaque :

```text
facteur temporel = 1 + 0,35 × clamp(pitch d'attaque / 35°, -1, 1)
```

- Vers le sol à `-35°` : toutes les durées sont multipliées par `0,65`.
- À l'horizontale `0°` : les durées de base du tableau sont utilisées.
- Vers le ciel à `+35°` : toutes les durées sont multipliées par `1,35`.

La longueur visible et utile de la baïonnette dans le plan horizontal suit également sa projection tridimensionnelle :

```text
longueur horizontale = longueur originale × cos(pitch)
```

## Attaques hautes et basses

| Direction | Indication | Portée de base | Préparation de base | Actif de base | Récupération de base | Caractéristique |
| --- | --- | ---: | ---: | ---: | ---: | --- |
| Haute | `↑` orange | 1,52 m | 0,36 s | 0,15 s | 0,48 s | Portée plus longue, animation complète plus lente |
| Basse | `↓` cyan | 1,30 m | 0,22 s | 0,09 s | 0,34 s | Portée plus courte, mais préparation, frappe et récupération plus rapides |

La distance donne toujours un avantage de score au coup haut lorsqu'il faut profiter de sa portée et au coup bas à courte distance pour sa vitesse. Elle ne fixe cependant plus le résultat. L'IA ajoute la garde actuelle ou mémorisée de l'adversaire, pénalise ses propres répétitions et applique un bruit lié à la décision. Elle peut donc employer volontairement le même côté pour conditionner l'ennemi, puis feinter vers l'opposé. Le torse est la cible normale et une attaque haute peut parfois tenter la tête. Les jambes ne sont jamais choisies volontairement : elles ne sont touchées qu'à la suite d'une erreur de hauteur, d'un coup trop long ou mal ciblé.

## Directions de parade

| Parade | Indication | Résultat |
| --- | --- | --- |
| Haute | `↑` orange | Arrête uniquement une attaque haute |
| Basse | `↓` cyan | Arrête uniquement une attaque basse |
| Latérale | `↔` violet | Représente actuellement une erreur et n'arrête pas les estocs hauts ou bas |

La direction doit correspondre et le défenseur doit également faire face à l'attaquant. `BLOCK_PREPARE` annonce la future garde sans la rendre active ; seul `BLOCKING` ou la phase correspondante du Double Block peut intercepter. Le simple fait d'avoir choisi la parade ne garantit donc pas la sécurité : une préparation trop lente, une mauvaise hauteur, une parade latérale ou une mauvaise orientation laisse passer le coup.

## Étourdissement sur parade

Lorsqu'un estoc est correctement bloqué, l'attaquant ne récupère pas immédiatement :

```text
ATTACK_ACTIVE → BLOCK_STUN (0,18 s) → RECOVERY
```

Pendant les 0,18 seconde de `BLOCK_STUN`, il ne peut ni se déplacer, ni pivoter, ni attaquer, ni bloquer. Le défenseur ne reçoit pas un estoc automatique : sa garde possède sa propre durée variable. S'il lit la fin de cette garde et la récupération adverse, il peut choisir une `RIPOSTE`; sinon il reste couvert ou reprend son placement.

Une parade normale n'accorde aucun chamber automatique. La mécanique suivante intercepte désormais le coup sans entrer dans l'état `BLOCKING`.

## Statistique Chamber et interception sans parade

La dixième statistique `Chamber`, comprise entre 1 et 100, représente d'abord la tendance de l'Agent à employer cette technique face à une menace. Lorsque l'IA a fini son délai de réaction, se trouve encore en état `NEUTRAL` et a décidé qu'une défense directe est nécessaire :

```text
P(choisir un chamber contre une attaque ordinaire | défense directe) = Chamber / 100
```

Une valeur de 1 produit donc environ 1 % de tentatives et une valeur de 100 en produit à chaque attaque ordinaire. Cette statistique ne devient pas une probabilité de réussite et n'est plus réutilisée intégralement pendant une chaîne. Si le chamber n'est pas choisi, l'Agent tente la parade normale.

Les valeurs initiales sont `74` pour le bleu et `93` pour le rouge. Sur une défense directe admissible, le bleu choisit donc le chamber dans 74 % des cas. Le rouge le choisit dans 93 % des cas, mais seulement 3 % de ces tentatives sont sélectionnées comme parfaites avant même le test de timing.

`beginChamberAttempt()` copie la direction haute ou basse du coup adverse et place l'Agent dans `CHAMBER_PREPARE`. Il essaie alors de récupérer le coup avec sa propre attaque. Il ne se trouve pas en parade : une mauvaise direction, une mauvaise orientation ou un timing raté laisse l'estoc adverse infliger ses dégâts normalement.

### Difficulté temporelle commune

La qualité d'exécution dépend des attributs déjà liés à l'instant de l'action :

```text
T = (timing offensif + réaction + prise de décision) / 3
σ = max(0,012 ; 0,060 - 0,00045 × T)
erreur temporelle ~ N(0, σ)
```

La pointe adverse doit réellement entrer dans le cercle corporel. À cet instant, les deux directions d'attaque doivent être identiques et la baïonnette du chamber doit faire face à l'attaquant à moins de `75°`. `CHAMBER_PREPARE` expire après `0,65 s` si aucun contact n'arrive. Une tentative ratée ne se transforme jamais gratuitement en parade.

### Chamber simple

Le chamber simple réussit si :

```text
abs(erreur temporelle) ≤ 0,025 s
```

Après l'interception, il conserve le coup pendant `0,24 s` dans `CHAMBER_HOLD`, puis libère `CHAMBER_ACTIVE`. L'attaquant original revient à `NEUTRAL` et réévalue immédiatement la menace. Avec une bonne réaction et un bon placement, il peut donc bloquer ce retour ou tenter de le re-chamber. Un re-chamber reproduit exactement le même processus dans l'autre sens ; `chamberChain` compte 2, 3, etc., et l'interface affiche `CHAMBER ×2`, `CHAMBER ×3` jusqu'à ce qu'un Agent bloque, rate, esquive ou soit touché.

Une chaîne devenant rapidement trop risquée, la statistique complète n'est plus appliquée à chaque retour. Pour un retour simple de rang `n` :

```text
facteur de re-chamber(n) = max(0,05 ; 0,50 - 0,10 × n)
P(re-chamber) = Chamber / 100 × facteur de re-chamber(n)
P(choisir la parade) = 1 - P(re-chamber)
```

Les facteurs des rangs 1, 2, 3 et 4 valent respectivement 40 %, 30 %, 20 % et 10 %. À partir du rang 5, ils restent à 5 %. Même un Agent avec Chamber 100 préfère donc la parade dans 60 % des premiers retours, 90 % des quatrièmes et 95 % des chaînes plus longues.

Le retour répété devient également plus lisible, ce qui améliore le choix de la bonne hauteur de parade :

```text
compétence de parade contre la chaîne = min(100 ; timing défensif + 8 × n)
```

Ce bonus ne concerne que la parade d'un chamber simple. Une attaque ordinaire conserve le timing défensif normal. Le chamber parfait verrouille toujours l'adversaire : il ne lui laisse aucune occasion de bloquer ou de re-chamber.

Le retour simple vise normalement le torse, mais le jeu de jambes introduit l'imprécision du replacement :

```text
σhauteur = 0,06 + (100 - jeu de jambes) × 0,002 m
σyaw = 0,05 + (100 - jeu de jambes) × 0,002 rad
```

Une touche simple au torse inflige normalement `50 dégâts`. Elle ne devient létale que si le replacement réel est exceptionnellement favorable :

```text
distance idéale = portée horizontale projetée + rayon du corps - 0,10
qualité distance = max(0 ; 1 - abs(distance - distance idéale) / 0,45)
qualité face = max(0 ; 1 - abs(erreur d'orientation) / 0,40)
P(torse létal) = jeu de jambes / 500 × qualité distance × qualité face
```

Même avec 100 en jeu de jambes et deux qualités parfaites, la limite reste 20 %. Le footwork réel fait donc que la position idéale de one-shot reste rare en duel. Une erreur montant jusqu'à la petite tête inflige néanmoins 100 ; une erreur tombant dans les jambes inflige 40.

### Chamber parfait

Le type parfait est inaccessible à `Chamber ≤ 90`. Au-dessus de 90, la sélection reste volontairement rare :

```text
P(parfait | chamber choisi) = (Chamber - 90) / 100
```

Avec 91, elle vaut 1 % ; avec 93, 3 % ; même avec 100, seulement 10 %. Le succès exige ensuite :

```text
Chamber > 90 et abs(erreur temporelle) ≤ 0,004 s
```

Cette fenêtre totale de 8 ms est beaucoup plus difficile que celle du chamber simple. En cas de succès, l'attaquant original entre dans `BLOCK_STUN` et ne peut ni bloquer ni re-chamber. Le retour part immédiatement en `CHAMBER_ACTIVE`, vise le torse et inflige 100 dégâts si la collision horizontale réelle aboutit. Les trois filtres — niveau supérieur à 90, sélection rare et fenêtre de ±4 ms — empêchent le chamber parfait de devenir un one-shot régulier.

## Erreurs d'exécution et statistiques

Après avoir choisi une action, l'agent peut encore mal l'exécuter. La courbe de réussite de base est :

```text
Statistique 1–10 : 50 %
Statistique 11–100 : 50 % + 49,9 % × (statistique - 10) / 90
```

| Statistique | Exécution correcte |
| ---: | ---: |
| 1–10 | 50,0 % |
| 25 | environ 58,3 % |
| 50 | environ 72,2 % |
| 75 | environ 86,0 % |
| 100 | 99,9 % |

Cette courbe s'applique actuellement aux opérations discrètes importantes :

- Défense : choix de la bonne hauteur. En cas d'erreur, l'agent peut choisir l'autre hauteur ou une parade latérale.
- Prise de décision : choix d'une attaque adaptée à la distance et volonté d'exploiter un appât correctement lu.
- Précision : elle agit à la fois sur l'angle horizontal et la hauteur verticale. Une réussite produit une petite déviation ; un échec peut faire passer l'arme à côté, au-dessus de la tête ou trop bas dans les jambes.
- Contrôle de distance : lecture unique d'un appât visible ; un échec déclenche un estoc prématuré et trop court.

Les autres aptitudes produisent des erreurs continues :

- Le contrôle de distance modifie aussi l'erreur continue sur la distance perçue.
- La réaction détermine le délai entre la détection et la réponse et fournit 45 % de la perception périphérique dérivée.
- Le jeu de jambes modifie la vitesse de déplacement et de rotation, le choix d'avancer après avoir lu un appât, et constitue 50 % de la protection du dos dérivée.
- Le timing offensif influence le moment où l'attaque maintenue est libérée.
- L'agressivité influence la fréquence des attaques d'essai et la volonté d'avancer sur un appât identifié.
- Sortir de sa zone détermine la fréquence et la durée du dézonage, puis la répartition entre recul, avance oblique et orbite.
- Chamber détermine la fréquence du choix d'interception ; le timing offensif, la réaction et la décision déterminent séparément l'erreur temporelle de son exécution.
- La réaction, la décision et Sortir de sa zone complètent respectivement 22 %, 18 % et 10 % de la protection du dos.
- La décision et le contrôle de distance complètent respectivement 35 % et 20 % de la perception périphérique ; avec la réaction, ces trois valeurs déterminent si le deuxième adversaire est vu et combien de temps sa position reste mémorisée.
- La décision, le contrôle de distance et l'agressivité déterminent séparément la fréquence d'utilisation d'une occasion de freekill ; la précision reste responsable de l'exécution réelle.

Une précision de 80 ne signifie donc pas « 80 % de chances de toucher ». La pointe réelle de la baïonnette doit toujours entrer en collision avec l'adversaire.

## Points de vie, zones touchées et dégâts

Chaque agent commence avec `100 PV`. Le corps utilise une hauteur continue de `0 à 1,80 m` et la taille de chaque zone détermine directement sa fenêtre verticale :

| Zone | Intervalle | Taille verticale | Sélection | Dégâts |
| --- | ---: | ---: | --- | --- |
| Jambes | 0–0,65 m | 0,65 m | Jamais visées volontairement ; erreur trop basse ou coup trop long | 40 dégâts fixes |
| Torse | 0,65–1,50 m | 0,85 m | Cible normale, la plus grande et la plus simple | 95 % : 100 dégâts ; 5 % : 50 dégâts |
| Tête | 1,50–1,80 m | 0,30 m | Tentative possible avec une attaque haute, fenêtre la plus petite | 100 dégâts fixes |

La tête ne mesure que 0,30 m dans ce modèle. À erreur égale, une tentative peut donc retomber sur le torse ou passer au-dessus. Le torse mesure 0,85 m et constitue de loin la cible la plus facile. Une touche aux jambes révèle une erreur verticale importante ; elle n'est pas une décision offensive normale.

Les dégâts sont cumulatifs. Un agent déjà blessé peut donc être achevé par un nouveau coup à la jambe ou un coup de 50 au torse. Il passe à `DEAD` uniquement lorsque ses PV atteignent zéro.

La vue de dessus ajoute une hauteur continue pour représenter l'axe vertical invisible. Le calcul complet est :

1. La pointe doit réellement entrer dans le cercle de collision adverse.
2. Pendant la préparation, l'agent choisit le torse ou tente parfois la tête avec une attaque haute ; il ne choisit jamais les jambes.
3. Une erreur verticale continue, déterminée par la précision, est ajoutée au centre de la cible voulue.
4. Une hauteur finale dans `0–0,65 m`, `0,65–1,50 m` ou `1,50–1,80 m` touche respectivement les jambes, le torse ou la tête.
5. Une hauteur inférieure à 0 ou supérieure à 1,80 m rate verticalement, même si la trajectoire 2D traverse le corps.
6. Les dégâts ne sont calculés que si le contact horizontal et la hauteur sont tous les deux valides.

## IA défensive

Les agents suivent une stratégie où la survie passe avant l'attaque :

1. Vérifier si un adversaire actuellement visible les vise et se trouve dans la portée de menace de son attaque.
2. Face à une menace directe, choisir selon la statistique Chamber entre une interception risquée et la parade normale.
3. Reculer ou se décaler lorsque l'espace le permet, puis revenir au centre près des limites.
4. Éviter d'entrer inutilement à portée lorsqu'une attaque adverse est en préparation.
5. Chercher une contre-attaque après un coup adverse manqué.
6. Si l'adversaire en récupération est déjà trop loin, attendre ou tourner autour de lui au lieu de le poursuivre sans arrêt.
7. Exploiter un dos visible uniquement si l'adversaire direct semble assez loin, sans annuler les erreurs de distance ou de précision.
8. Ne lancer que de rares attaques d'essai lorsque les deux agents sont au repos et en sécurité.

La priorité à la survie ne rend pas la défense parfaite. Un agent faible peut comprendre la bonne réponse, mais réagir trop tard, choisir la mauvaise hauteur, effectuer une parade latérale, frapper à côté ou mal évaluer la distance.

## Tactiques individuelles

La préparation propose `Défensive`, `Neutre` ou `Agressive` séparément pour chaque joueur. La tactique ne modifie ni les statistiques brutes ni les dégâts : elle pondère les choix de l'IA. `Neutre` conserve exactement les multiplicateurs de base.

| Modificateur | Défensive | Neutre | Agressive |
| --- | ---: | ---: | ---: |
| Chamber contre une attaque ordinaire | ×0,45 | ×1,00 | ×1,00 ; ×1,30 si le coup est lisible |
| Utilisation d'une occasion de backstab | ×0,75 | ×1,00 | ×1,25 |
| Attaque d'essai | ×0,45 | ×1,00 | ×1,80 |
| Déclenchement du dézonage | ×1,35 | ×1,00 | ×0,75 |
| Déclenchement de l'appât | ×0,70 | ×1,00 | ×1,35 |
| Engagement en défense directe | +25 | 0 | -10 |
| Seuil de libération d'un estoc maintenu | -10 | 0 | +15 |
| Poursuite d'un dézonage adverse | -25 | 0 | +25 |
| Avance après lecture d'un appât | -35 | 0 | +35 |
| Distance déclenchant l'approche | 1,95 m | 1,78 m | 1,55 m |
| Avance pendant un maintien | au-delà de 1,65 m | au-delà de 1,45 m | au-delà de 1,25 m |

### Défensive

La tactique défensive multiplie la volonté de Chamber initiale par `0,45`, ce qui laisse beaucoup plus de décisions à la parade normale. Le bonus `+25` d'engagement fait choisir plus tôt entre parade et déplacement face à une menace préparée ; il n'invente pas de compétence, car la direction correcte reste déterminée par le véritable timing défensif.

Elle produit 55 % d'attente dans les décisions calmes, davantage de recul ou de déplacement latéral, 35 % de dézonages supplémentaires et 55 % d'attaques d'essai en moins. Lors d'un dézonage, le poids de la posture `BLOCK` reçoit `+40`. Un adversaire en `RECOVERY` à moins de 2,02 m reste une faille valable : l'Agent défensif peut alors contre-attaquer au lieu de rester passif sans raison.

### Agressive

La tactique agressive approche dès 1,55 m, continue d'avancer avec un estoc maintenu jusqu'à 1,25 m, multiplie les attaques d'essai par `1,80` et les appâts par `1,35`. Elle poursuit systématiquement un adversaire trop loin mais en récupération, reçoit `+25` pour suivre son dézonage et `+35` pour avancer après avoir reconnu un appât. Le poids de la posture `HOLD` pendant son propre dézonage reçoit également `+40`.

Un « coup trop simple » possède une définition mesurable : ce n'est ni un appât ni un retour de Chamber, et l'adversaire se trouve en `ATTACK_HOLD`, ou son estoc est déjà actif avec moins de 12° de décalage latéral. Uniquement dans ce cas :

```text
P(Chamber agressif | coup lisible) = min(1 ; Chamber / 100 × 1,30)
```

Les pénalités de re-chamber 40 %, 30 %, 20 %, 10 % puis 5 % sont appliquées ensuite. La tactique agressive exploite donc davantage les attaques évidentes sans réintroduire les chaînes de dizaines de Chambers.

## Informations visuelles

- Trait blanc dans le cercle : orientation de l'agent.
- Baïonnette grise : posture neutre.
- Baïonnette jaune pâle pulsante et arc pointillé frontal : préparation ou maintien d'un estoc ordinaire.
- Baïonnette jaune vif épaissie : phase active d'un estoc ordinaire ; elle peut réellement infliger des dégâts à cet instant.
- Baïonnette orange sombre : récupération, puis retour automatique au gris.
- Large halo cyan autour de l'arme de parade : parade active ; la couleur centrale orange, cyan ou violette conserve sa direction.
- Étincelle jaune et blanche au contact : parade réussie, visible pendant 0,22 seconde.
- Textes `PRÊT`, `ATTAQUE`, `RÉCUP.` et `PARADE` au-dessus de l'Agent ; leur version chinoise est « 準備 », « 攻擊 », « 恢復 » et « 格擋 ».
- `↑` orange : attaque ou parade haute.
- `↓` cyan : attaque ou parade basse.
- `↔` violet : parade latérale erronée.
- Petit cercle à l'avant du corps : repère visuel de la tête.
- Petit trait à l'arrière : repère de la direction des jambes.
- Arc rouge de 115° à 245° derrière le corps et `DOS EXPOSÉ` : au moins un ennemi se trouve actuellement dans le secteur impossible à parer.
- `BACKSTAB !` rouge : une pointe valide vient de toucher dans ce secteur arrière.
- `Opportunité de backstab`／「背刺機會」 dans la décision : l'Agent vient de choisir l'ouverture freekill ; la baïonnette conserve les couleurs ordinaires car la collision reste normale.
- Texte rouge flottant : dernière zone touchée et dégâts.
- Panneaux latéraux : PV, statistiques, état, décision, direction, distance, protection du dos dérivée, cible voulue, hauteur réelle et blessure récente.
- À droite des PV : tactique active (`T : Défensive`, `T : Neutre` ou `T : Agressive`).
- Ligne compacte « Angles H/V/A » : yaw horizontal, pitch vertical et angle latéral de l'arme ; une valeur verticale négative vise le sol, une valeur positive le ciel, tandis que le troisième nombre montre le décalage gauche/droite de la baïonnette.
- Lignes pointillées colorées : cible ennemie actuellement verrouillée par chaque Agent.
- `APPÂT ←/→` orange : l'Agent sort volontairement sa baïonnette de l'axe pour provoquer une attaque.
- `CONTRE-APPÂT !` bleu : l'Agent a lu la distance, avance jusqu'à portée réelle et prépare un estoc bas.
- `PIÉGÉ !` rouge : l'Agent a mal lu la distance et s'engage dans un estoc trop court impossible à convertir en parade.
- `PUNITION !` verte : l'appâteur prépare ou libère la contre-attaque mortelle pendant la récupération adverse.
- `DÉZONAGE` vert : l'Agent est en train de changer durablement de position ; sa décision précise recul, avance oblique gauche/droite ou orbite gauche/droite.
- `✕` rouge : attaquant bloqué et entièrement verrouillé pendant 0,18 seconde.
- `CHAMBER !` violet : chamber simple réussi ; `CHAMBER ×N` indique une chaîne de re-chambers.
- `CHAMBER PARFAIT !` doré : chamber à ±4 ms, retour immédiat et adversaire verrouillé.
- Boutons en haut à gauche : sélection directe de 0,25×, 0,5×, 1×, 2× ou 4× ; le bouton actif est désactivé pour indiquer la vitesse courante.
- Bouton en haut à droite : `中文` dans l'interface française et `Français` dans l'interface chinoise ; toute l'interface change immédiatement.

### Lecture de la portée et des actions ordinaires

`Musketeer.isBasicAttack()` exclut les Chambers, les attaques piégées et les réponses spéciales à l'appât. Ces actions spéciales conservent donc leurs couleurs et leurs messages actuels. Pour un estoc ordinaire, `paintWeapon()` superpose une ligne transparente large puis un noyau plus fin : pendant la phase active, leurs épaisseurs valent respectivement 3,8 et 1,55 fois l'épaisseur normale. Le jaune reste ainsi lisible même lorsque les combattants sont éloignés.

Pendant `ATTACK_PREPARE` et `ATTACK_HOLD`, `paintAttackRange()` dessine uniquement devant la baïonnette un arc de `±55°`. Son rayon représente la distance maximale entre les centres permettant un contact :

```text
rayon visuel = longueur projetée de l'estoc + rayon du corps
```

Un adversaire dont le centre reste au-delà de cet arc ne peut pas être touché par une libération immédiate. Être à l'intérieur ne garantit pas la touche : l'angle horizontal, la hauteur, la direction de parade et le timing réel restent vérifiés par la simulation. L'arc disparaît après la préparation afin de ne pas encombrer durablement le 2v2.

Lorsqu'une parade valide intercepte la pointe, `checkHit()` enregistre sa position dans `blockSparkX/Y` et démarre `blockSparkTime` à `0,22 s`. `paintBlockSpark()` dessine huit rayons jaune-blanc qui grandissent et s'effacent au point exact du contact.

## Double Block : double parade haute/basse en 2v1

Le Double Block répond au cas précis où deux ennemis visent le même Agent avec deux hauteurs opposées. Ce n'est ni une invulnérabilité temporaire ni une parade à 360°. Le moteur doit d'abord résoudre physiquement une première parade normale. Seulement à cet instant, `tryBeginDoubleBlock()` cherche un deuxième estoc qui arrivera presque immédiatement.

### Onzième statistique et aptitude effective

La valeur modifiable `doubleBlock`, comprise entre 1 et 100, pèse pour moitié dans une aptitude effective `E`. Les aptitudes déjà présentes représentent l'exécution autour de cette technique :

```text
E = round(0,50 × Double Block
        + 0,225 × timing défensif
        + 0,15 × réaction
        + 0,075 × décision
        + 0,05 × jeu de jambes)
```

- `Double Block` mesure la maîtrise spécifique du changement haut/bas ;
- le timing défensif détermine la qualité de la première parade et pèse ensuite 22,5 % dans l'enchaînement ;
- la réaction aide à lire le second impact ;
- la décision aide à identifier lequel des deux estocs doit être pris en deuxième ;
- le jeu de jambes aide à conserver l'orientation et la position pendant les deux contacts.

Le 50 % demandé est donc strictement conservé. Les quatre autres statistiques ne remplacent pas la nouvelle caractéristique et ne peuvent pas, seules, créer un spécialiste.

### Conditions géométriques obligatoires

Une première parade réussie n'ouvre une tentative que si un autre ennemi vérifie simultanément toutes les conditions suivantes :

1. il est vivant, a réellement commencé `ATTACK_ACTIVE` ou `CHAMBER_ACTIVE`, et son estoc n'a pas déjà été résolu ;
2. sa `target` est le même défenseur ;
3. son attaque est opposée à la première : haute après basse ou basse après haute ;
4. le défenseur le voit actuellement selon `OpponentAwareness.visible` ; une dernière position mémorisée ne suffit pas ;
5. il se trouve devant le défenseur : une attaque arrière reste un backstab impossible à parer ;
6. sa baïonnette pointe vers le défenseur avec au plus `34°` d'erreur ;
7. sa distance centrale ne dépasse pas sa portée projetée, le rayon corporel et une marge d'anticipation de `0,35 m` ;
8. l'estoc n'a pas dépassé 65 % de sa phase active et le temps estimé avant son extension maximale ne dépasse pas `0,20 s`.

Il faut donc deux attaques visibles et réellement engagées. Deux adversaires seulement proches, deux attaques de même hauteur, une feinte encore en préparation, une attaque latérale non détectée ou un coup dans le dos ne déclenchent jamais `DOUBLE_BLOCK`.

### Probabilité de tentative

Lorsque toutes les conditions physiques sont réunies, l'Agent reconnaît et tente la séquence avec :

```text
P(tenter le Double Block | deux menaces valides) = (E / 100)³
```

Le cube rend volontairement la technique très difficile :

| `E` | Probabilité de tentative |
| ---: | ---: |
| 25 | 1,56 % |
| 50 | 12,50 % |
| 75 | 42,19 % |
| 90 | 72,90 % |
| 100 | 100 % |

Cette probabilité ne garantit pas la seconde parade. Elle autorise seulement l'entrée dans l'état `DOUBLE_BLOCK`. La première parade doit déjà avoir choisi la bonne direction avec les règles ordinaires de `defenseTiming`; une mauvaise première parade ne donne donc aucun tirage de Double Block.

### Changement de hauteur et échec physique

Après le premier contact, le défenseur garde brièvement sa première direction, mémorise le deuxième attaquant dans `doubleBlockThreat`, puis change vers `doubleBlockSecondDirection`. Le délai vaut :

```text
temps de changement = 0,19 - 0,00125 × E seconde
durée de l'état = max(0,26 ; temps de changement + 0,12) seconde
```

| `E` | Changement haut/bas |
| ---: | ---: |
| 25 | 0,15875 s |
| 50 | 0,12750 s |
| 75 | 0,09625 s |
| 90 | 0,07750 s |
| 100 | 0,06500 s |

`updateState()` ne remplace `blockDirection` qu'après ce délai. Pendant `DOUBLE_BLOCK`, `moveAndTurn()` oriente le corps et la baïonnette vers le second attaquant visible. Lorsque sa pointe entre dans le cercle corporel, `checkHit()` exige encore :

```text
doubleBlockSwitched == true
doubleBlockThreat == attaquant du second impact
blockDirection.blocks(direction du second estoc)
écart de la baïonnette défensive vers cet attaquant < 75°
attaque non arrière
```

Si le deuxième coup arrive avant la fin du changement, si l'Agent s'est mal orienté ou si un autre attaquant touche à sa place, les dégâts sont appliqués normalement. Le Double Block n'accorde aucune réduction de dégâts et ne modifie pas les règles de tête, torse ou jambes.

### Lecture à l'écran

L'état et la décision affichent `Double parade` en français et `雙重格擋` en chinois. Le symbole violet `⇅` remplace momentanément la flèche unique, la baïonnette prend successivement la couleur de la direction courante et un succès affiche `DOUBLE BLOCK !`／`雙重格擋！`. Chaque contact conserve aussi l'étincelle de parade à la position exacte de la pointe. La fiche latérale affiche la statistique brute « Double Block » ; l'aptitude effective `E` reste un calcul interne expliqué ci-dessus.

## Parcours de l'interface : menu, match amical et équipes

`Main` conserve désormais un seul `JFrame` et remplace son panneau central à chaque étape. `show()` applique le nouveau titre, appelle `setContentPane()`, `pack()` et recentre la fenêtre. Le parcours complet est :

```text
Menu principal
→ JOUER
→ MATCH AMICAL 2v2
→ CRÉATION DES ÉQUIPES
→ JOUER LE MATCH
→ Combat 2v2
```

Les deux premiers écrans utilisent `MenuPanel`. Son enum `Page` choisit entre `MAIN` et `PLAY`, tandis que `MenuAction` renvoie `PLAY`, `FRIENDLY_MATCH`, `BACK` ou `QUIT` à `Main`. Cette séparation évite que les boutons créent eux-mêmes des fenêtres ou connaissent le moteur de combat. Le menu mesure `1000 × 650`, possède un fond dessiné, une carte centrale, la touche Entrée pour confirmer, `L` pour la langue et Échap pour revenir depuis l'écran des modes.

Après « PRÉPARER LE MATCH », `SetupPanel` affiche un onglet « ÉQUIPE BLEUE » et un onglet « ÉQUIPE ROUGE ». Chacun contient un nom d'équipe limité à 24 caractères et deux `PlayerEditorPanel`, soit quatre joueurs configurables :

- nom du joueur, obligatoire et limité à 18 caractères afin de rester lisible dans le panneau de combat ;
- tactique individuelle : Défensive, Neutre ou Agressive ;
- jeu de jambes ;
- contrôle de distance ;
- précision ;
- timing d'attaque ;
- timing de défense ;
- réaction ;
- agressivité ;
- décision ;
- sortir de sa zone ;
- chamber ;
- Double Block.

Chaque statistique utilise un `JSpinner` construit avec :

```java
new SpinnerNumberModel(valeurParDéfaut, 1, 100, 1)
```

La valeur ne peut donc pas sortir de l'intervalle 1–100. Lorsque « JOUER LE MATCH » ou « 開始比賽 » est pressé, même avec la touche Entrée, le programme valide d'abord le texte encore en cours d'édition dans chaque spinner. Une erreur garde l'écran ouvert et affiche sa cause dans la langue active. Chaque joueur valide produit un objet immuable :

```java
record AgentConfiguration(String name, AgentStats stats, Tactic tactic)
```

Les deux joueurs sont ensuite regroupés avec le nom de leur équipe :

```java
record TeamConfiguration(String name, AgentConfiguration firstPlayer, AgentConfiguration secondPlayer)
```

`Main.showCombat()` installe alors `MapPanel` dans la même fenêtre. Son titre reprend les deux noms d'équipe et les configurations sont transmises par les constructeurs :

```text
SetupPanel
→ TeamConfiguration bleue + TeamConfiguration rouge + UiLanguage
→ MapPanel(équipe bleue, équipe rouge, langue, retour menu)
→ DuelSimulation(configurations bleues[2], configurations rouges[2])
→ blueTeam[2] + redTeam[2]
```

`SetupPanel.updateLanguage()` traduit le fil d'Ariane, le titre, les noms d'équipe, les onglets, les boutons, l'aide et les erreurs. Il appelle ensuite `PlayerEditorPanel.setLanguage()` pour traduire les noms des joueurs, « Nom du joueur », « Tactique » et les onze caractéristiques. Le renderer du `JComboBox<Tactic>` utilise `Tactic.label(language)` : Défensive, Neutre et Agressive deviennent donc 防守, 中立 et 進攻 sans modifier la tactique sélectionnée ni les textes déjà saisis.

La langue active voyage dans chaque callback de navigation. Elle est donc conservée entre le menu principal, le choix du mode, la création des équipes et le combat.

`MapPanel` affiche les deux noms créés dans l'en-tête du combat et réutilise le nom du vainqueur dans le message final. `DuelSimulation` conserve seulement les quatre configurations de joueurs. La touche `R` restaure les PV, les positions, les cibles et les états, mais recrée les mêmes noms, statistiques et tactiques. Le bouton `MENU` ou Échap arrête le `Timer` du combat puis rappelle `Main.showMainMenu(language)`. `MapPanel.removeNotify()` arrête également le timer lorsque le panneau quitte la fenêtre, afin qu'une ancienne partie ne continue jamais en arrière-plan.

Les quatre fiches par défaut sont centralisées dans `AgentConfiguration.defaultBlue()`, `defaultBlueTwo()`, `defaultRed()` et `defaultRedTwo()`.

## Sélection de cible et victoire 2v2

Chaque `Musketeer` conserve son numéro d'équipe et une référence `target`. `DuelSimulation.selectTarget()` compare seulement les ennemis visibles ou encore mémorisés. Une attaque frontale ou latérale déjà détectée, orientée vers l'Agent et à portée projetée devient une menace immédiate : en posture neutre ou en parade, l'Agent peut abandonner sa cible précédente. Une attaque latérale éloignée qui n'a pas été perçue ne déclenche aucune réaction magique. Un ennemi arrière reconnu est traité par la protection du dos et ne force pas un demi-tour tant qu'une cible frontale connue existe.

Hors urgence, le score de cible part de la distance visible ou de la dernière position mémorisée. Les bonus liés à la récupération, à l'attaque du coéquipier et à la cible pressée par l'allié ne sont appliqués que si l'ennemi est actuellement visible : l'IA ne lit pas son état derrière son dos. Une erreur aléatoire proportionnelle à `101 - decisionMaking` empêche les faibles décideurs de choisir systématiquement la meilleure cible. `findFreeKillTarget()` constitue l'exception volontaire au refus de tourner vers une cible arrière : il vise un ennemi dont l'Agent voit le dos, et non un ennemi situé derrière l'Agent lui-même.

Après un changement ordinaire, la cible reste verrouillée pendant `0,55 + 0,008 × décision` seconde afin d'éviter un basculement permanent. La mort de la cible annule cette attente : un Agent neutre choisit un ennemi encore visible ou mémorisé, mais peut rester temporairement sans cible si le second combattant n'a jamais été perçu. Un Agent engagé termine d'abord son action pour ne pas faire pivoter magiquement l'estoc en cours. Les lignes pointillées et la distance des fiches sont des informations de spectateur ; leur précision n'est pas donnée telle quelle à l'IA.

`avoidTeammate()` repousse progressivement un Agent lorsque son allié se trouve à moins de 1,05 m. `separateAgents()` vérifie ensuite toutes les paires vivantes avec la collision souple décrite plus haut : elle corrige doucement la zone de confort et ne force la séparation complète qu'à la limite dure. Les estocs ne testent que les deux adversaires, donc il n'y a pas de dégâts alliés dans cette version ; `resolveAttack()` choisit l'ennemi dont le corps est le plus proche du segment balayé par la pointe, qui peut être différent de la cible voulue.

Un premier mort ne termine plus la partie. `updateWinner()` est appelé après tous les impacts du pas physique et fixe `winningTeam` uniquement lorsqu'aucun membre d'une équipe ne vit encore. Si les quatre Agents tombent dans le même pas, le résultat est `DRAW`.

## Commandes

| Touche | Action |
| --- | --- |
| `Espace` | Pause / reprise |
| `R` | Recommencer le combat 2v2 et restaurer 100 PV |
| `L` | Basculer entre le français et le chinois sur tous les écrans |
| `Entrée` | Confirmer l'action principale du menu ou lancer le match depuis la création des équipes |
| `Échap` | Revenir à l'écran précédent ; depuis le combat, retourner au menu principal |
| `0` | Vitesse 0,25× |
| `5` | Vitesse 0,5× |
| `1` | Vitesse 1× |
| `2` | Vitesse 2× |
| `4` | Vitesse 4× |

## Lancement

Le projet est configuré avec le JDK 25. Il est possible d'exécuter `Main` dans IntelliJ IDEA ou d'utiliser PowerShell depuis la racine :

```powershell
javac -encoding UTF-8 -d .\out .\src\*.java
java -cp .\out Main
```

Le menu principal apparaît en premier. Choisissez `JOUER`, puis `MATCH AMICAL 2v2`, nommez les deux équipes et configurez les quatre joueurs. Le bouton `中文`／`Français` traduit chaque écran, les tactiques et les messages d'erreur. « JOUER LE MATCH » installe la page de combat dans la même fenêtre et dans la même langue.

## Architecture

| Fichier | Rôle |
| --- | --- |
| `Main.java` | Contrôleur de la fenêtre unique et navigation menu → mode → équipes → combat |
| `MenuPanel.java` | Menu principal et écran Jouer / Match amical bilingues |
| `SetupPanel.java` | Création des deux équipes, quatre joueurs, retour, langue et validation du lancement |
| `PlayerEditorPanel.java` | Libellés bilingues, nom, tactique et onze sélecteurs 1–100 pour un joueur |
| `AgentConfiguration.java` | Configuration immuable nom + statistiques + tactique et quatre fiches par défaut |
| `TeamConfiguration.java` | Nom d'équipe et ses deux configurations de joueurs validées |
| `Tactic.java` | Défensive, Neutre, Agressive et modificateurs de décision associés |
| `MapPanel.java` | Arène, quatre fiches, lignes de cible, PV, commandes et retour sûr au menu |
| `DuelSimulation.java` | Perception, mémoire, choix de cible, freekill, protection du dos, double parade, IA, collisions, dégâts et victoire collective |
| `Musketeer.java` | Équipe, cible, table de perception, données instantanées, double parade, PV, baïonnette, blessures et rendu |
| `OpponentAwareness.java` | Visibilité, dernière position connue, durée de mémoire et historique observé des attaques/parades d'un ennemi |
| `AgentStats.java` | Les onze statistiques et les aptitudes dérivées de dos, perception, Double Block, engagement, feinte et manipulation |
| `EngagementPhase.java` | Sonde, engagement, traversée, sortie et remise en garde du cycle spatial |
| `AttackManipulation.java` | Estoc neutre, accel ou drag actuellement exécuté |
| `ChamberType.java` | Type `NONE`, `SIMPLE` ou `PERFECT` du chamber en cours |
| `DezoneMovement.java` | Recul, avances obliques et orbites gauche/droite du dézonage |
| `DezoneStance.java` | Posture neutre, parade ou maintien pendant le dézonage |
| `CombatState.java` | États de combat |
| `CombatDirection.java` | Valeurs, symboles et couleurs des attaques hautes et basses |
| `BlockDirection.java` | Parades hautes, basses et latérales |
| `HitZone.java` | Tête, torse et jambes |
| `Decision.java` | Action actuellement choisie |
| `UiLanguage.java` | Langues française et chinoise de l'interface |

`DuelSimulation` ne dépend pas de `MapPanel`. Le même moteur peut donc servir plus tard à simuler rapidement de nombreux combats sans interface graphique.

## Limites actuelles

Le prototype gère maintenant un combat fixe 2v2 et une seule famille d'attaque à l'estoc, avec feintes, accel et drag. Il possède une sélection de cible individuelle, un soutien simple et l'évitement du coéquipier, mais pas encore de formation, d'ordre de capitaine, de tactique collective ni d'effectif arbitraire. Les coups de pied, les attaques de taille, la progression, le recrutement, les ligues et les tournois ne sont pas encore implémentés. Les zones ont désormais des tailles et une hauteur continues, mais il n'existe pas encore de squelette 3D complet ni d'animations des membres.
