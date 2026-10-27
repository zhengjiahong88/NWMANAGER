# NWMANAGER

NWMANAGER est un prototype Java Swing de gestion et de simulation de groupfight à la baïonnette.
Le joueur crée deux équipes, règle les caractéristiques et la tactique de leurs Agents, puis observe
un combat physique en 2v2 ou en 3v3. Les Agents se déplacent librement dans un plan 2D, orientent leur
corps et leur baïonnette, perçoivent imparfaitement plusieurs menaces et prennent eux-mêmes leurs
décisions d'attaque, de parade, de déplacement et de coopération.

Le 3v3 n'est plus limité à son premier prototype de déplacement. Il comprend maintenant l'approche
dynamique, l'attention limitée, les réseaux de couverture, les plans d'attaque, revenge,
contre-couverture, les clusters descriptifs et les transitions transactionnelles vers 3v2, 3v1,
2v2, 2v1 et 1v1. Le chantier en cours est le jalon 6.3 consacré à la neutralisation permanente des
joueurs morts ; son noyau 6.3.1 est intégré, tandis que les validations spécialisées et les nouveaux
batchs restent à terminer.

[README en chinois traditionnel](README.md)

## 1. Lancer le jeu

Depuis Windows, le plus simple est de double-cliquer sur `run.bat`. En ligne de commande :

```powershell
javac -encoding UTF-8 -d .\out .\src\*.java
java -cp .\out Main
```

`run.bat` compile tous les fichiers Java en UTF-8 puis lance `Main.main`. Deux raccourcis
supplémentaires existent :

```text
run-controlled-2v1.bat
run-controlled-2v2.bat
```

Chemin de l'interface :

```text
Menu principal → Jouer → Match amical
→ création et modification des quatre joueurs
→ Start → combat MODERN_2V2

Menu principal → Jouer → Match expérimental 3v3
→ création et modification des six joueurs
→ Start → groupfight MODERN_3V3 avec transitions 3v2, 3v1, 2v2, 2v1 et 1v1
```

L'interface bascule entre français et chinois traditionnel. Les vitesses disponibles sont
`0,25×`, `0,5×`, `1×`, `2×` et `4×`.

### Modes de ligne de commande

```powershell
java -cp .\out Main
java -cp .\out Main legacy-2v2 [seed]
java -cp .\out Main controlled-2v1
java -cp .\out Main modern-2v2 <seed>
java -cp .\out Main controlled-2v2 [baseline|jittered|jittered-baseline] [seed]
java -cp .\out Main modern-3v3 <seed>
java -cp .\out Main controlled-3v3 <seed>
java -cp .\out Main debug-3v3-transition <seed>
```

- Sans argument, le menu permet de préparer un match amical `MODERN_2V2` ou un match
  `MODERN_3V3`.
- `controlled-2v1` ouvre la scène reproductible de fixation et de flank.
- `controlled-2v2` ouvre une géométrie contrôlée ; la famille choisit le placement de départ.
- `modern-2v2` et `modern-3v3` lancent directement le moteur moderne avec une seed imposée.
- `legacy-2v2` conserve l'ancien moteur uniquement pour comparaison historique.
- `debug-3v3-transition` utilise le vrai panneau Swing et enregistre quinze secondes après la
  première mort dans `out/debug/transition-seed-<seed>.log`.

### Commandes pendant un combat

| Commande | Effet |
|---|---|
| `Espace` | pause ou reprise |
| `R` | recommencer le match avec la même configuration |
| `L` | basculer français/chinois traditionnel |
| `D` | afficher ou masquer les diagnostics |
| `0` | vitesse `0,25×` |
| `5` | vitesse `0,5×` |
| `1`, `2`, `4` | vitesse correspondante |

Le moteur avance avec des sous-pas physiques d'au plus `1/120 s`. Modifier la vitesse d'affichage
change donc le nombre de sous-pas exécutés, pas les règles de collision.

## 2. État actuel du moteur

| Domaine | État actuel |
|---|---|
| Duel et 2v1 | moteur physique actif et conservé |
| 2v2 moderne | groupfight collectif actif jusqu'à la première mort |
| 3v3 moderne | groupfight complet, puis transitions d'effectif transactionnelles |
| Interface | menu, création d'équipes, français/chinois, vitesses et debug |
| Joueurs morts | noyau permanent `DeathPose` 6.3.1 intégré |
| RNG sémantique | infrastructure et audit présents, non branchés au RNG de production |
| Validation 6.3 | isolation, smoke test et transitions validés ; tests spécialisés et batchs à venir |

Avec quatre survivants, le match utilise un seul `EngagementType.GROUP_2V2`. Ce n'est ni deux duels
indépendants, ni une orbite des quatre joueurs autour d'un centre commun. Chaque équipe possède sa
propre `TeamManeuver` persistante. Exemple réel de la seed 41 :

```text
Bleus : COMPRESS
Rouges : ROTATE_RIGHT
```

Après la première mort, le `GROUP_2V2` est rompu et `EngagementManager` crée le vrai
`TWO_VS_ONE`. Sa logique `FIX/FLANK/SURVIVE`, ses doubles menaces, doubles parades, backstabs et
sorties défensives n'ont pas été recalibrés par cette refonte.

Systèmes conservés : `AttackOpportunity`, preuve figée, arbitrage d'attaque, `WeaponThreatLane`,
revenge, contre-couverture, perception imparfaite, collisions, parades physiques, `DecisionTrace`,
`KillExplanation`, `KillCausalChain` et déterminisme.

Les résultats de batch présentés plus bas sont les résultats historiques obtenus avant le correctif
permanent 6.3. Ils restent utiles comme référence, mais ne doivent pas être présentés comme une
acceptation post-6.3 tant que les campagnes 100 et 500 n'ont pas été rejouées.

## 3. Architecture du code

- `DuelSimulation` exécute la simulation, la perception, les décisions, le mouvement et les impacts.
- `GroupEngagement` contient les quatre Agents, les centres de chaque duo, les assignations et une
  `TeamManeuver` bleue et rouge.
- `TeamManeuverType`, `TeamManeuver` et `TeamManeuverAssessment` décrivent le choix tactique du duo.
- `LocalManeuver` décrit une correction individuelle persistante.
- `Musketeer` contient `Pos`, vitesse, angle du corps, orientation de baïonnette, état et attributs.
- `EngagementManager` crée ou rompt `GROUP_2V2`, `DUEL` et `TWO_VS_ONE`.
- `MapPanel` dessine l'arène et les diagnostics ; il ne décide jamais du résultat.
- `ModernMatchMetrics` et `GroupMotionAudit` observent le moteur sans influencer l'IA.
- `TeamFormation3`, `CoverNetwork`, `AttentionAllocation` et `TeamAttackPlan` portent les systèmes
  collectifs propres au 3v3.
- `RosterSnapshot`, `ActiveCombatController` et `TransitionTrace` sécurisent les changements
  d'effectif après une élimination.
- `DeathPose`, `DeadAgentAudit` et `DeadAgentParticipationDomain` définissent l'invariant permanent
  des joueurs morts.
- `SimulationRandom` reste le RNG de production. `SemanticRandomTape` et les clés sémantiques sont
  pour l'instant des outils d'audit et de replay expérimental séparés.

Les coordonnées restent regroupées dans `Pos`. Les additions, soustractions, produits scalaires et
normalisations ne manipulent donc pas deux variables `x/y` séparées dans la logique tactique.

## Guide fonctionnel complet

Cette section décrit le jeu tel qu'il existe dans le code actuel. Les sections numérotées suivantes
conservent ensuite le détail chronologique des refontes 2v2 et 3v3.

### Les 14 caractéristiques d'un Agent

Toutes les caractéristiques de `AgentStats` sont des entiers compris entre `1` et `100`. Une valeur
n'est pas une probabilité directe de toucher. Elle modifie la qualité d'une perception, un délai, une
erreur, un score de décision, une vitesse ou la probabilité de choisir une option. L'impact final reste
soumis aux positions, angles, vitesses, collisions et états physiques.

| Caractéristique | Sens dans le jeu | Effet mathématique ou algorithmique principal |
|---|---|---|
| Jeu de jambes | qualité du déplacement, rotation, accélération et protection du dos | intervient dans les vitesses, `backProtection`, engagement, dézonage ciblé, pression et collectif |
| Contrôle de distance | capacité à estimer la portée et à entrer au bon moment | réduit l'erreur de distance et intervient dans engagement, pression et attention périphérique |
| Précision | qualité de l'orientation de la pointe et de la zone visée | réduit les erreurs angulaires/verticales ; participe aux feintes et manipulations |
| Timing attaque | choix et exécution de la fenêtre de libération | influence préparation, release, feinte, manipulation et exploitation d'une récupération |
| Timing défense | choix de la parade et de son changement de hauteur | influence parade, Chamber et Double Block |
| Réaction | délai entre perception d'une menace et réponse | réduit les délais de réaction ; participe à l'attention, la protection du dos et la Double Block |
| Agressivité | préférence pour avancer et proposer le combat | change la fréquence des choix offensifs, pas leur qualité d'exécution |
| Décision | cohérence du choix parmi les options disponibles | réduit le bruit tactique et participe à presque toutes les capacités composites |
| Dézonage | préférence à sortir de son vis-à-vis ou changer de cible | modifie la fréquence et la durée des dézonages ; la qualité du changement dépend aussi des autres stats |
| Chamber | aptitude et préférence à intercepter directement un stab | augmente les tentatives viables et la précision du timing, surtout au-dessus de `90` pour le parfait |
| Double Block | spécialisation de la double parade haut/bas | fournit 50 % de la capacité composite de Double Block |
| Pression | capacité à imposer un Hold, une feinte ou un angle de menace | fournit 50 % de la capacité composite de pression |
| Volonté de 50/50 | préférence pour accepter un échange simultané risqué | proche de `100` : accepte souvent ; proche de `1` : refuse et privilégie la parade |
| Collectif | volonté et qualité de couverture mutuelle | rapproche les partenaires, améliore les choix d'équipe et les manœuvres coordonnées |

Capacités dérivées utilisées par le moteur :

```text
protectionDos = 0,55 × jeuJambes
              + 0,25 × réaction
              + 0,20 × décision

attentionPériphérique = 0,45 × réaction
                      + 0,35 × décision
                      + 0,20 × contrôleDistance

doubleBlockAbility = 0,50 × doubleBlock
                   + 0,225 × timingDéfense
                   + 0,15 × réaction
                   + 0,075 × décision
                   + 0,05 × jeuJambes

changementCibleDézonage = 0,35 × décision
                        + 0,30 × jeuJambes
                        + 0,20 × réaction
                        + 0,15 × contrôleDistance

engagementAbility = 0,40 × jeuJambes
                  + 0,25 × contrôleDistance
                  + 0,25 × décision
                  + 0,10 × réaction

feintAbility = 0,40 × décision
             + 0,35 × timingAttaque
             + 0,25 × précision

manipulationAbility = 0,35 × timingAttaque
                    + 0,25 × précision
                    + 0,20 × jeuJambes
                    + 0,20 × décision

pressureAbility = 0,50 × pression
                + 0,20 × décision
                + 0,15 × jeuJambes
                + 0,10 × contrôleDistance
                + 0,05 × précision

teamworkAbility = 0,55 × collectif
                + 0,25 × décision
                + 0,15 × jeuJambes
                + 0,05 × réaction
```

Chaque résultat est arrondi à l'entier le plus proche.

### Technique, Mental, niveau global et personnalité

Le profil moderne affiché dans l'interface calcule :

```text
Technique = moyenne(jeuJambes, contrôleDistance, timingAttaque, timingDéfense, chamber)
Mental    = moyenne(précision, réaction, décision, collectif)

minimumTechnique = minimum des cinq caractéristiques techniques
pénalitéFaiblesse = 0,15 × max(0, 70 - minimumTechnique)

Global = arrondi et borné entre 1 et 100 de
         0,65 × Technique + 0,35 × Mental - pénalitéFaiblesse
```

Le classement nominal est `Low Player < 55`, `Mid Player 55–79`, `Contender 80–89` et
`Top Player 90–100`. Deux garde-fous empêchent cependant une moyenne trompeuse :

- Top Player exige Global et Technique au moins `90`, Mental au moins `85`, et chacune des cinq
  caractéristiques techniques au moins `80` ;
- Contender exige Global et Technique au moins `80`, Mental au moins `75`, et chacune des cinq
  caractéristiques techniques au moins `65`.

L'agressivité est classée comme personnalité : elle oriente les choix sans entrer dans Technique,
Mental ou Global. Le profil historique `LEGACY` inclut aussi Double Block dans Technique et Pression
dans Mental ; il n'est utilisé que par `LEGACY_2V2`.

Limitation actuelle de l'éditeur : l'interface prépare bien un objet `AgentStats` de 14 valeurs, mais
elle n'affiche et ne sauvegarde actuellement que Jeu de jambes, Contrôle de distance, Précision,
Timing attaque, Timing défense, Réaction, Agressivité, Décision, Chamber et Collectif. Dézonage,
Double Block, Pression et Volonté de 50/50 sont fixés à `50` lors de la validation du formulaire.
Le moteur sait utiliser ces quatre statistiques lorsqu'elles proviennent d'une configuration Java ;
leur édition graphique reste donc une limitation connue, pas une absence du moteur.

### Tactiques de préparation

| Tactique | Chamber | Attaque | Dézonage | Appât | Défense | Release | Suivi | Pression | Distance d'approche/Hold |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| Défensive | `×0,45` | `×0,45` | `×1,35` | `×0,70` | `+25` | `-10` | `-25` | `-35` | `1,95 / 1,65 m` |
| Neutre | `×1,00` | `×1,00` | `×1,00` | `×1,00` | `0` | `0` | `0` | `0` | `1,78 / 1,45 m` |
| Agressive | `×1,00` ou `×1,30` sur attaque lisible | `×1,80` | `×0,75` | `×1,35` | `-10` | `+15` | `+25` | `+35` | `1,55 / 1,25 m` |

La tactique défensive cherche d'abord à rester vivante : parade, déplacement, recul et attente de
faute. La tactique agressive avance davantage, maintient plus souvent une menace et tente plus de
Chambers sur les coups lisibles. Ces multiplicateurs changent les priorités ; ils ne garantissent ni
un blocage ni un hit.

### Mouvement, orientation et perception

Chaque Agent possède une `Pos`, une vitesse, une vitesse désirée, un angle du corps, une vitesse
angulaire, un lacet de baïonnette et un angle vertical. Le recul est ralenti par un facteur `0,62` :
un Agent qui fuit uniquement en marche arrière peut être rattrapé, tandis qu'une sortie avant-oblique
conserve davantage de vitesse et permet de tourner autour du danger.

Les Agents ne sont pas constamment face à face. Le corps peut protéger une ligne et laisser le flanc
ou le dos exposé ; la baïonnette peut pointer à gauche ou à droite pour menacer, feinter ou transférer
un stab. L'Agent ne lit pas directement toutes les données réelles : `OpponentAwareness` mémorise une
position, une vitesse, une orientation et un état observés avec confiance, erreur et ancienneté. En
3v3, `AttentionAllocation` sépare menace primaire, secondaire et périphérique. Voir un adversaire ne
provoque donc jamais automatiquement `target = adversaire`.

### Cycle d'attaque et Hold sans durée maximale

Le cycle normal est :

```text
NEUTRAL
→ ATTACK_PREPARE
→ ATTACK_HOLD facultatif
→ ATTACK_ACTIVE
→ RECOVERY
→ NEUTRAL
```

`ATTACK_PREPARE` peut passer directement à l'attaque : c'est plus rapide, mais la décision est plus
risquée et peut produire un stab mal préparé. Si l'Agent entre en `ATTACK_HOLD`, il peut conserver la
pointe armée sans limite automatique tout en avançant, orbitant ou dézonant. Le Hold ne prend fin que
par une décision explicite : libération, réalignement, feinte, parade, changement de garde, stab
volontairement imparfait ou abandon. Un échec entre en récupération ; un coup bloqué place l'attaquant
en `BLOCK_STUN` pendant `0,30 s`.

Les timings de base sont :

| Direction | Portée de thrust | Préparation | Actif | Récupération |
|---|---:|---:|---:|---:|
| Haute | `1,52 m` | `0,36 s` | `0,15 s` | `0,48 s` |
| Basse | `1,30 m` | `0,22 s` | `0,09 s` | `0,34 s` |

Le haut porte plus loin mais est plus lent. Le bas est plus court et plus rapide. L'angle vertical
applique à chaque timing :

```text
facteurTiming = 1 + 0,35 × clamp(angleVertical / 35°, -1, 1)
```

Pointer vers le sol rend donc le stab plus rapide ; pointer vers le ciel l'allonge. La portée projetée
est aussi réduite par le cosinus de cet angle. L'attaque n'est jamais un simple tirage de réussite :
pendant la phase active, le segment balayé par la pointe doit réellement entrer dans le rayon du corps
adverse.

### Attaques haute/basse, parades et zones corporelles

Une attaque haute se lit en orange avec `↑`; une attaque basse se lit en cyan avec `↓`. La baïonnette
devient jaune pendant l'attaque et cyan pendant la parade. Une parade doit avoir la bonne hauteur,
être orientée vers la menace et être active au moment du contact. Un Agent en parade ne peut pas
attaquer simultanément, mais il peut se déplacer pour sortir de la pression.

Les zones verticales sont :

| Zone | Hauteur | Conséquence normale |
|---|---:|---|
| Jambes | `0,00–0,65 m` | `40` dégâts ; souvent une erreur de longueur ou de visée |
| Torse | `0,65–1,50 m` | grande cible ; `95 %` de 100 dégâts, sinon `50` |
| Tête | `1,50–1,80 m` | petite cible ; `100` dégâts |

Un clean backstab, une punition d'appât ou un Chamber parfait inflige `100` dégâts. Un Agent meurt à
zéro point de vie. La taille des zones n'est pas transformée en pourcentage abstrait : c'est la hauteur
réelle de la pointe au contact qui choisit la zone.

### Chamber simple et Chamber parfait

Le Chamber intercepte un stab avec une attaque de même direction au lieu d'une parade classique :

```text
CHAMBER_PREPARE → CHAMBER_HOLD → CHAMBER_ACTIVE
```

Le Chamber simple utilise une fenêtre de timing de `0,025 s`. Son retour peut être bloqué ou repris
par un autre Chamber ; il est rarement létal automatiquement, car la position et le jeu de jambes
déterminent encore la zone touchée. Les chaînes subissent une pénalité croissante pour éviter des
dizaines de Chambers garantis.

Le Chamber parfait utilise une fenêtre de `0,004 s` et exige une statistique Chamber strictement
supérieure à `90`. Il est très rare et létal s'il entre réellement en collision. La statistique ne
force pas son usage : elle augmente la capacité à reconnaître et exécuter la fenêtre.

### Appât, feinte et erreur de distance

L'appât déforme volontairement la lecture de distance ou présente une ouverture. Un adversaire qui
tombe dans le piège libère trop tôt, rate physiquement puis ne peut plus bloquer pendant sa récupération.
L'appâteur peut alors punir. Toutefois, Décision, Jeu de jambes et Agressivité peuvent pousser la cible
à avancer suffisamment avant de frapper ; l'appâteur doit alors réussir sa défense selon Timing défense
et Réaction.

Une feinte change l'information donnée à l'adversaire : changement de hauteur, passage vers la parade
ou maintien de pression. Elle ne téléporte pas l'arme et ne supprime pas les temps physiques. Après des
corrections infructueuses, le Hold choisit explicitement entre réalignement, abandon et libération
imparfaite ; aucun timer caché ne force le stab.

### Dézonage et changement de cible

Dézoner signifie sortir de son duel local, pas simplement reculer. Les variantes comprennent recul,
sortie avant-gauche/droite, orbite, transfert vers un autre adversaire et abandon du transfert. Un
Agent peut dézoner en Hold ou en parade. La statistique Dézonage règle sa préférence ; la réussite du
changement de cible utilise surtout Décision, Jeu de jambes, Réaction et Contrôle de distance.

À longue distance, un Agent doit rester en état d'alerte et revenir vers une menace utile. En combat
collectif, il peut quitter son vis-à-vis pour aider un partenaire ou créer temporairement un 2v1.

### Dos, flancs et backstab

Une parade ne couvre jamais l'arrière. L'Agent doit tourner son corps, se déplacer ou rejoindre son
équipe. La protection du dos dépend principalement du Jeu de jambes, puis de la Réaction et de la
Décision. Regarder derrière soi expose naturellement la ligne avant ; en 2v2/3v3, l'Agent arbitre donc
entre plusieurs menaces.

Une cible parfaitement de dos et physiquement à portée constitue une forte occasion de backstab, mais
pas un kill abstrait garanti : un attaquant faible peut mal estimer la distance, viser à côté ou toucher
les jambes. Une menace latérale éloignée peut aussi rester périphérique et être comprise trop tard.

### Pression, 50/50, 2v1 et Double Block

Un Agent en Hold est offensif mais vulnérable ; un Agent en parade est temporairement inoffensif mais
survit si sa garde correspond. La pression cherche à maintenir cet avantage de timing par la distance,
l'angle, le déplacement, la pointe et les feintes. La cible pressée n'est pas paralysée : elle choisit
notamment `PRESSURE_ESCAPE_LEFT`, `PRESSURE_ESCAPE_RIGHT`, `PRESSURE_JOIN_TEAM` ou le recul. Reculer
peut cependant ouvrir son flanc au second adversaire.

Le `50/50` est la volonté de lancer presque en même temps que l'adversaire. La statistique correspondante
ne rend pas le coup meilleur : elle ne fait qu'accepter ou refuser plus souvent ce risque. Un Agent qui
refuse privilégie la parade lorsqu'il lit le départ adverse.

En 2v1, les deux attaquants cherchent à fixer, flanker, tenir une pointe et feinter pour provoquer une
réponse. Une attaque haute et une attaque basse presque simultanées imposent une `DOUBLE_BLOCK`. Sa
réussite utilise la capacité composite donnée plus haut, puis la géométrie et la fenêtre réelle. Le
défenseur peut aussi chercher une sortie, un timing de spam ou rejoindre un allié avant l'encerclement.

### Collectif 2v2 et groupfight 3v3

Le Collectif ne crée pas une chorégraphie rigide. Les partenaires cherchent une distance de soutien,
protègent les lignes de punition et tournent ensemble quand leurs déplacements individuels convergent.
Une rotation visible est le résultat des quatre ou six joueurs qui cherchent une meilleure ligne,
évitent les pointes et conservent leur couverture ; ce n'est pas une action globale imposée à tous.

Le 2v2 moderne reste un `GROUP_2V2` jusqu'à la première mort, puis devient un vrai `TWO_VS_ONE`. Le
3v3 utilise `GROUP_3V3`, `GROUP_3V2` ou `GROUP_3V1` tant qu'une équipe conserve trois joueurs, puis
transmet le combat aux moteurs validés `GROUP_2V2`, `TWO_VS_ONE` ou `DUEL` selon l'effectif.

## 4. `TeamManeuver` persistante

Types disponibles :

```text
HOLD_LINE
ROTATE_LEFT / ROTATE_RIGHT
PRESS_FORWARD / GIVE_GROUND
SHIFT_LEFT / SHIFT_RIGHT
COMPRESS / EXPAND
CROSS_COVER
RECOVER_FORMATION
```

Chaque manœuvre mémorise l'équipe, le début, trois durées, les centres de départ, la direction, la
confiance, la raison et les joueurs intérieur/extérieur. Les deux équipes évaluent et choisissent
indépendamment ; une équipe n'hérite jamais du vecteur choisi par l'autre.

Pour une qualité collective normalisée `q` calculée à partir de décision et collectif :

```text
durée minimale  = 0,70 + 0,40q seconde
durée préférée  = 1,40 + 1,10q seconde
durée maximale  = 3,00 + 1,00q secondes
```

Avant la durée minimale, la manœuvre persiste sauf vraie urgence. Après la durée préférée, elle peut
être réévaluée. Après la durée maximale, répéter exactement le même choix reçoit une pénalité afin
d'éviter une boucle éternelle.

Les évaluations utilisent couverture `C`, espace latéral `L`, sécurité du bord `B`, qualité de
formation `F`, potentiel de ligne `A`, menace, pression avant et bonus propre au type :

```text
score commun = 0,20C + 0,18L + 0,14B + 0,16F + 0,14A
score final  = score commun + bonus géométrique du type + bruit de décision borné
```

Le bruit diminue quand décision et collectif augmentent. Le choix final est tiré parmi les options
proches de la meilleure : fenêtre `0,10 + 0,18(1-q)`, pondération exponentielle de température
`0,10`. Un bon duo est donc cohérent sans être parfaitement déterministe ; un duo faible explore
plus souvent une option seulement acceptable. La seed rend toutefois chaque replay reproductible.

Interruptions immédiates : échange d'attaque, revenge, contre-couverture, stun, bord imminent,
séparation grave, ennemi entre les partenaires, mort ou entrée en `TWO_VS_ONE`. Une urgence de forme
devient `COMPRESS` ou `RECOVER_FORMATION` au lieu de recréer la même manœuvre à chaque frame.

## 5. Rotation propre au duo

La rotation utilise :

```text
teamCenter     = moyenne des deux partenaires vivants
enemyCenter    = moyenne des deux adversaires vivants
forward        = normalisé(enemyCenter - teamCenter)
lateral        = perpendiculaire(forward) × direction de la manœuvre
```

Elle n'utilise pas le centre des quatre joueurs pour propulser les Agents. Le rôle intérieur est le
joueur le plus proche du centre du groupfight ; en cas d'égalité, la position dans le sens du virage
décide. Les rôles persistent pendant la manœuvre afin de ne pas alterner à chaque frame.

Références avant accélération et contraintes :

```text
extérieur : 0,76 m/s latéral + 0,12 m/s avant
intérieur : 0,37 m/s latéral + 0,15 m/s avant
facteur de jeu de jambes = 0,82 + 0,002 × footwork
```

Hors urgence, la projection demandée est maintenue vers environ `0,62 m/s` pour l'extérieur et
`0,42 m/s` pour l'intérieur. Ce ne sont pas des téléportations : accélération, inertie, recul ralenti,
collision, bord et état physique restent appliqués. Sur 500 seeds, la différence tangentielle réelle
extérieur moins intérieur vaut `+0,135 m/s` hors urgence.

Les autres vecteurs de duo sont également physiques : pression avant oblique, recul oblique,
translation latérale, rapprochement/écartement des partenaires, couverture croisée ou retour vers
la position de couverture.

## 6. Composition du mouvement et priorités

`modernGroupVelocity()` compose explicitement :

```text
teamManeuverVelocity
localManeuverVelocity
formationVelocity + partnerVelocity
weaponAvoidanceVelocity
collisionVelocity
boundaryVelocity
```

Une correction locale ordinaire est multipliée par `0,58`; une vraie sortie de ligne de pointe garde
sa pleine force. La hiérarchie pratique est :

```text
bord/collision imminente
→ attaque physiquement imminente
→ parade, revenge, contre-couverture
→ évitement de pointe
→ correction locale
→ manœuvre d'équipe
→ garde-fou de formation
```

`LocalManeuver` n'a donc pas disparu. Un Agent peut éviter une pointe pendant que son partenaire
continue `ROTATE_RIGHT`. `HOLD_POSITION` est un vrai arrêt de `0,18–0,42 s` : la propulsion commune
est suspendue pour cet Agent, sauf si une pointe, une collision ou le bord impose une sortie. Le
batch final sépare `13,233 s` de Holds intentionnels et `14,783 s` de faible vitesse incidente.

## 7. Attaque, parade et collision

Une attaque suit toujours préparation, Hold éventuel, phase active et récupération. Le Hold peut
durer tant qu'aucune autre action de baïonnette ne l'annule. Haut et bas ont des portées et timings
différents ; l'angle vertical et le lacet gauche/droite modifient la géométrie réelle.

Un impact exige le balayage réel de la pointe dans une zone corporelle. La tête est petite, le torse
large et les jambes correspondent souvent à un coup trop long ou mal ciblé. Une parade exige le bon
état, la bonne direction et l'intersection physique. Un stab arrière ne peut pas être bloqué vers
l'arrière ; perception périphérique, réaction, décision, déplacement et dézonage déterminent si
l'Agent protège son dos à temps.

La restauration du mouvement n'accorde aucun bonus d'attaque. Tout stab ordinaire du groupfight
passe encore par `assessAttackOpportunity()` puis `TeamAttackArbitration`. Aucun seuil de portée,
temps de préparation ou délai automatique n'a été assoupli.

## 8. Opportunités et explications

`AttackOpportunity` contient raison, fenêtre, ETA d'impact, ligne alliée, couverture adverse, risque
de revenge, score et rejet. `AttackOpportunityEvidence` fige au commit les angles observés, état,
temps restant, corridor de pointe, vitesse d'entrée, couverture et confiance. La catégorie
`TARGET_MOVING_INTO_LINE` exige une vraie traversée prédite ; elle n'est pas inventée en l'absence de
preuve.

`DecisionTrace` distingue désormais `TEAM_MANEUVER` et `LOCAL_MOVEMENT`. Le debug affiche la
manœuvre de chaque équipe, le rôle intérieur/extérieur, son âge et sa contribution. Une chaîne de
mort contient séparément :

```text
TeamManeuver
→ mouvement local
→ décision d'attaque
→ réaction de la victime
→ décision de couverture alliée
→ résolution physique
```

Une donnée absente est écrite `NONE_OBSERVED`; elle n'est jamais reconstruite après l'impact.

## 9. Validation visuelle

```powershell
java -cp .\out VisualValidationCapture 41 73 119
java -cp .\out VisualValidationCapture --representative
```

Chaque dossier `out/visual-validation/seed-N/` contient :

- `contact-sheet.png` : 16 instants à vitesse 1× ;
- `trajectories.png` : trajectoires jusqu'à la première mort ;
- `timeline.txt` : `TeamManeuver`, rôles, durées, raisons et `LocalManeuver` ;
- `decisions.txt` : opportunités et preuves ;
- `first-kill.txt` : explication et chaîne causale ;
- `motion.txt` et `suspicions.txt` : audit du mouvement.

La version fade précédente mesurait environ `8,2 %` de biais collectif et `61,4 %` de micro-mouvement.
La version hybride finale mesure `39,8 %` de contribution de duo, `27,9 %` locale, `19,9 %` de
formation et `12,2 %` de collision/bord. Les captures 41/73/119 ont été régénérées après calibration.

## 10. Résultats du batch final

> Référence historique pré-6.3. Ces chiffres décrivent la version validée avant la neutralisation
> permanente des morts. Ils devront être remplacés par les campagnes post-6.3 du jalon 6.3.4.

Commande :

```powershell
java -cp .\out FinalAcceptanceTest 500
```

Résultat final :

```text
500/500 matchs terminés
0 timeout, stalemate, thrashing, sortie d'arène ou position invalide
0 activation de l'ancien système
0 échec déterministe
500/500 premières morts avec KillCausalChain

TeamManeuver démarrées : 4052
ROTATE_LEFT / RIGHT : 1117 / 1162
PRESS_FORWARD / GIVE_GROUND : 519 / 23
COMPRESS / EXPAND : 345 / 90
CROSS_COVER / RECOVER_FORMATION : 14 / 768
distance latérale cumulée : 3556,485 m
variation de surface : 5194,712

premières morts : 415 normales, 84 revenge, 1 chamber
revenge : 1005 occasions, 104 commits, 84 kills
post-élimination TWO_VS_ONE : 500/500
```

## 11. Tests utiles

```powershell
java -cp .\out TeamManeuverTest
java -cp .\out ModernGeometryTest
java -cp .\out ModernGroup2v2Test
java -cp .\out TwoVsOneCoordinationTest
java -cp .\out AttackOpportunityTest
java -cp .\out DecisionExplanationTest
java -cp .\out AttributeBehaviorAudit
java -cp .\out FinalAcceptanceTest 500
```

`TeamManeuverTest` couvre rotations visibles, rotations opposées, pression, Hold individuel,
priorité de pointe, shift, compression, expansion, dynamisme sur cinq secondes et déterminisme.

## 12. Note sur l'ancien moteur

`LEGACY_2V2` reste compilable à titre historique. Les champs globaux `GroupPhase.ROTATE`, vitesse
angulaire et direction historique subsistent dans certains diagnostics de compatibilité, mais ils ne
propulsent plus `modernGroupVelocity()`. Ils ne doivent pas être réactivés pour régler le match
amical moderne.

## 13. Jalon expérimental 3v3 — mouvement collectif

Le 3v3 possède un chemin séparé, sélectionnable depuis le menu et en ligne de commande :

```powershell
java -cp .\out Main modern-3v3 41
java -cp .\out Main controlled-3v3 41
```

`TeamConfiguration3` transporte le nom de l'équipe et ses trois configurations. L'écran de
préparation réutilise exactement le même éditeur d'attributs que le 2v2, mais affiche trois fiches
par équipe. `DuelSimulation` crée ensuite six `Musketeer` et un seul `GROUP_3V3`. Il ne crée pas
trois `DUEL` invisibles et ne réutilise pas le ciblage 2v2 comme structure collective.

### Formation géométrique

Chaque équipe possède une instance de `TeamFormation3`. À chaque tick, elle calcule :

- le centre moyen des trois joueurs avec des opérations `Pos` ;
- l'axe `forward`, dirigé vers le centre adverse ;
- l'axe `lateral`, perpendiculaire à `forward` ;
- la largeur, la profondeur et la cohésion de la formation ;
- les rôles `LEFT`, `CENTER`, `RIGHT`, obtenus en projetant les positions sur `lateral`.

Les rôles sont donc des observations géométriques, pas des numéros attribués définitivement au
spawn. Un croisement réel peut échanger deux rôles. Une durée minimale de `0,55–0,90 s`, modulée
par décision et collectif, et une marge spatiale de `0,30 m` empêchent les échanges de rôle à
chaque micro-croisement.

### Manœuvres indépendantes et rotation

Les deux équipes choisissent leur propre `TeamManeuver` persistante parmi les types déjà employés
par le moteur moderne : rotation, pression, recul, décalage, compression, expansion, couverture
croisée et récupération. Une rotation n'applique pas la même vitesse aux trois joueurs : la
consigne tangentielle est `0,84 m/s` pour l'extérieur, `0,56 m/s` pour le centre et `0,28 m/s`
pour l'intérieur, avant l'effet du jeu de jambes, de l'inertie, de la correction de formation,
des collisions et du bord. Cette différence forme une courbe collective lisible sans téléporter
les joueurs ni les attacher à une orbite rigide.

Au jalon 1, `updateExperimental3v3()` ne passe volontairement jamais dans la sélection d'attaque,
la parade ou la résolution des impacts. Tous les Agents restent `NEUTRAL / WAIT` avec 100 points de
vie. Ce verrou sert à valider d'abord la géométrie et le mouvement. Les attaques, les morts et les
transitions vers 2v2/2v1 appartiennent aux jalons
suivants ; ils ne sont pas présentés ici comme terminés.

### Tests et captures du jalon 1

```powershell
java -cp .\out Experimental3v3Test
java -cp .\out VisualValidation3v3Capture
```

`Experimental3v3Test` vérifie l'unique `GROUP_3V3`, l'absence totale d'attaque, les rôles
géométriques, l'hystérésis, les manœuvres indépendantes, l'ordre de vitesse extérieur/centre/
intérieur et le déterminisme. Les tests `Modern2v2Test`, `TeamManeuverTest` et
`ModernGroup2v2Test` sont aussi exécutés comme non-régression.

Pour les seeds 41, 73 et 119, `out/visual-validation-3v3/seed-N/` contient
`contact-sheet.png`, `trajectories.png` et `formations.txt`. La feuille montre seize instants espacés
de `1 s`; le fichier texte conserve les rôles, dimensions, cohésion et manœuvre des deux équipes.

## 14. Jalon expérimental 3v3 — attention limitée

Le jalon 2 ajoute une `AttentionAllocation` par Agent sans modifier `MultiThreatAttention`, qui reste
utilisée par le `TWO_VS_ONE`. Cette séparation empêche la nouvelle perception à trois de recalibrer
le moteur 2v2.

Chaque allocation contient trois adversaires distincts :

```text
primaryThreat      poids habituel 0,50–0,66
secondaryThreat    part restante principale
peripheralThreat   poids habituel 0,10–0,17
```

Les trois poids sont recalculés et normalisés à exactement `1`. Ils ne représentent pas une chance
de toucher : ils distribuent la fréquence et la précision de l'observation. La saillance d'une
menace dépend de la distance, de sa position dans le champ visuel, de sa vitesse de rapprochement,
d'une attaque évidente et d'un danger arrière très proche.

### Changement de menace principale

Le meilleur score ne provoque jamais directement `target = nouvelAdversaire`. Une menace ordinaire
doit dépasser la menace actuelle d'au moins `0,16` après un verrou de `0,55–0,90 s`. Elle devient
d'abord `pendingPrimary`. La réorientation dure ensuite environ `0,14–0,38 s` selon Réaction et
Décision. Un stab imminent à courte portée autorise un délai urgent de `0,08 s`, mais pas un échange
instantané. Après la bascule, `precisionPenaltyUntil` maintient une courte incertitude sur la nouvelle
cible. La menace secondaire possède sa propre hystérésis de `0,42 s` et une marge de `0,14`.

### Qualité de perception

`OpponentAwareness` conserve désormais le niveau d'attention, le cooldown d'observation, la
confiance détaillée, l'heure et le nombre d'observations. Les cadences indicatives sont :

```text
primaire     0,075–0,125 s
secondaire   0,17–0,29 s
périphérique 0,42–0,72 s
```

La mesure principale actualise position, vitesse, corps, pointe et état avec une faible erreur. La
mesure secondaire est plus bruitée et ne lit l'état que si l'action est évidente ou si la lecture
réussit. La périphérie produit surtout une dernière position, une vitesse approximative et une
alerte : `stateDetailFresh` reste faux et l'état exact d'attaque/parade n'est jamais copié. La marge
d'erreur de position vaut environ `1×`, `2,25×` et `4,60×` l'erreur de base suivant les trois niveaux.

Le mouvement regarde la menace principale terminée, pas `pendingPrimary`. Une simple détection ne
change donc ni la cible ni la direction du corps avant la fin du coût de réorientation.

### Debug, métriques et tests

La touche `D` affiche pour chaque Agent `P`, `S`, `R`, les poids et la bascule en attente. Les fiches
compactes montrent aussi les trois associations. `Group3v3Metrics` compte les changements primaires
retardés et terminés, les changements secondaires, les détections périphériques et les poids moyens.

```powershell
java -cp .\out Experimental3v3AttentionTest
java -cp .\out VisualValidation3v3Capture
```

Les tests A–E contrôlent la normalisation, les trois cadences, la confiance décroissante, le délai
de bascule, l'absence de lecture exacte périphérique et le déterminisme. Chaque dossier de capture
contient maintenant `attention.txt`, en plus des images et de `formations.txt`. À ce jalon précis,
les attaques n'étaient pas encore activées.

## 15. Jalon expérimental 3v3 — réseau physique de couverture

Le jalon 3 crée un `CoverNetwork` séparé pour chaque équipe. Il ne réutilise ni ne modifie le
`CoverAssessment` du 2v2. Pour trois protecteurs, deux alliés possibles et trois ennemis, le moteur
évalue exactement `3 × 2 × 3 = 18` relations par équipe et par mise à jour :

```text
protecteur → allié protégé / ennemi menaçant
```

Un `CoverLink` conserve les participants, la viabilité, les ETA d'alignement, préparation et contact,
l'ETA totale de punition, la sécurité de ligne, la confiance perceptive, la qualité et une raison de
refus explicite. Le réseau indexe ces liens par allié protégé (`incomingLinks`) et par protecteur
(`outgoingLinks`).

### Calcul physique d'un lien

Le calcul utilise `OpponentAwareness.lastKnown`, jamais la position réelle cachée d'un ennemi non
observé. Sans visibilité ni mémoire, le lien vaut `enemy_untracked`.

```text
alignEta   = erreur angulaire de la pointe / vitesse de rotation estimée
prepareEta = préparation restante de la baïonnette
contactEta = distance restant hors portée / vitesse de déplacement estimée
punishEta  = alignEta + prepareEta + contactEta
```

L'ETA de menace adverse applique le même principe à la dernière position, pointe, portée et état
connus. `lineSafety` mesure la distance minimale des alliés au segment protecteur–ennemi. La qualité
est ensuite :

```text
quality = 0,38 × marge temporelle
        + 0,23 × sécurité de ligne
        + 0,20 × confiance perceptive
        + 0,07 × proximité de l'ennemi
        + 0,12 × portée de soutien vers l'allié
        + 0,08 × qualité mentale du protecteur
```

Le lien est refusé si la perception est trop incertaine, si un allié bloque la ligne, si l'allié est
à plus de `2,85 m`, si la punition arrive plus de `0,42 s` après la menace ou si la qualité reste sous
`0,34`. Une séparation visible n'est donc pas automatiquement une rupture : la ligne et les ETA
doivent réellement cesser d'être crédibles.

### Mesures du réseau

```text
connectivity = liens viables / 18
couverture d'un membre = moyenne, pour les trois ennemis,
                        du meilleur lien viable reçu
weakestMemberCoverage = plus petite couverture individuelle
```

Une aile éloignée peut perdre ses liens entrants tandis que l'autre aile les conserve. Le centre
obtient souvent davantage de liens sortants uniquement parce que sa géométrie lui permet de joindre
les deux ailes ; aucun bonus caché ne lui est accordé.

Le réseau influence la prochaine sélection de `TeamManeuver` : couverture minimale sous `0,10` →
`RECOVER_FORMATION`, connectivité sous `0,16` → `COMPRESS`. Il ajoute aussi une faible correction vers
le centre pour un membre découvert et vers l'aile faible pour le joueur central. Pendant une rotation,
cette correction est multipliée par `0,18`, afin de ne pas effacer la différence de vitesse
extérieur/centre/intérieur validée au jalon 1.

### Debug, métriques et validation

Avec `D`, la carte affiche les meilleurs liens viables seulement, et non les 36 évaluations. Le texte
indique liens actifs, connectivité, membre faible et couverture. `Group3v3Metrics` compte créations,
ruptures, membres sans couverture, liens du centre/des ailes et moyennes du réseau.

```powershell
java -cp .\out Experimental3v3CoverNetworkTest
java -cp .\out VisualValidation3v3Capture
```

Les tests A–E vérifient les 18 relations, les liens physiques viables, la rupture locale d'une aile,
la conservation de l'autre côté, l'influence sur la prochaine manœuvre, la confiance issue de
l'attention et le déterminisme. Les captures contiennent maintenant `cover-network.txt`. Ce test
isolé conserve les attaques désactivées afin de mesurer le réseau sans interférence.

## 16. Jalon expérimental 3v3 — plans d'attaque et impacts

Le jalon 4 active les attaques dans `MODERN_3V3` et `CONTROLLED_3V3`. Le choix collectif est séparé
de `TeamAttackArbitration` du 2v2. Une équipe produit maintenant un `TeamAttackPlan` contenant zéro,
un ou deux attaquants, leurs `AttackOpportunity`, les membres qui restent en couverture, la sécurité
combinée des lignes et le risque de récupération.

### Opportunités perçues et couvreurs multiples

Un Agent évalue seulement ses menaces primaire et secondaire. Une menace périphérique est refusée
avec `LOW_CONFIDENCE_PERCEPTION`, même si le moteur connaît son objet Java. Il faut une position
mémorisée, une confiance suffisante, une distance physique compatible avec la portée et une pointe
alignable.

`OpposingCoverSummary` filtre le `CoverNetwork` de la cible avec deux contraintes : le lien doit être
viable et son protecteur doit être connu de l'attaquant. Il conserve :

```text
meilleur couvreur et fastestPunishEta
secondPunishEta
nombre de couvreurs crédibles
combinedRisk borné entre 0 et 1
```

Le risque principal compare `fastestPunishEta` avec l'impact plus la récupération de l'attaquant. Le
deuxième couvreur ajoute au maximum `35 %` de son risque : les menaces ne sont donc pas additionnées
magiquement. La couverture propre de l'attaquant réduit ensuite le risque final jusqu'à `42 %`.

Une opportunité ordinaire est refusée si la cible est hors portée, si un allié coupe la ligne, si la
confiance est sous `0,32`, si l'erreur d'arme dépasse `48°`, si la meilleure punition adverse est trop
rapide, si la fenêtre se ferme avant l'impact ou si le score de décision est insuffisant.

### Arbitrage zéro, un ou deux attaquants

Le plan trie la meilleure occasion de chaque Agent. Le premier attaquant viable peut être retenu. Un
deuxième n'est autorisé que si :

- il vise un adversaire différent ;
- les deux segments de stab ne se croisent pas dangereusement ;
- le troisième partenaire reste hors des deux attaques ;
- ce troisième partenaire possède encore au moins un `CoverLink` viable.

Le troisième attaquant n'est jamais ajouté. `tripleAttackerPlans` reste donc exactement à zéro et
`tripleAttackRejections` enregistre les situations où trois occasions existaient. Après un plan, un
cooldown dépendant de l'agressivité évite une réévaluation à chaque tick.

### Préparation, défense et résolution physique

Les attaquants passent réellement par `ATTACK_PREPARE → ATTACK_ACTIVE → RECOVERY`. Le plan force une
libération directe, mais `ordinaryDirectTargetEscaped()` peut encore transformer l'action en Hold si
la cible sort de portée avant le départ.

Le défenseur ne lit pas l'attaque réelle. Il réagit à son `OpponentAwareness` :

```text
délai de base = 0,36 - 0,0024 × Réaction
+ 0,00 s si menace primaire
+ 0,09 s si secondaire
+ 0,22 s si périphérique
+ 0,10 s si la direction exacte n'est pas fraîche
```

Après ce délai, `BLOCK_PREPARE` utilise encore le timing défensif et peut choisir une mauvaise hauteur.
La parade réussit uniquement lorsque la direction correspond, que le défenseur fait face à la ligne
et que la pointe entre réellement en collision. Une parade place l'attaquant en `BLOCK_STUN`. Un raté
entre en `RECOVERY`, dont le mouvement ajoute `0,38 m/s` loin de la cible, conformément au recul de
récupération déjà établi.

Les dommages continuent d'utiliser `resolveAttack()`, la pointe balayée, les zones corporelles et
`checkHit()`. Aucune réussite n'est tirée directement depuis le score du plan.

### Limite volontaire du jalon

Cette limite décrivait le jalon 4 : il s'arrêtait à la première élimination pour éviter un faux 3v2
ou trois duels. Depuis le jalon 6, le moteur reconstruit explicitement `GROUP_3V2`/`GROUP_3V1`, puis
rend le contrôle aux moteurs 2v2, 2v1 ou 1v1 quand leurs effectifs sont atteints. La première mort
conserve toujours sa cause physique, son meilleur allié de couverture et sa chaîne causale.

### Tests et captures

```powershell
java -cp .\out Experimental3v3AttackPlanTest
java -cp .\out VisualValidation3v3Capture
```

Les tests A–E vérifient le résumé des couvreurs perçus, l'existence d'un double plan sûr, le refus
systématique du troisième attaquant, les hits/parades/éliminations physiques, le recul de récupération
et le déterminisme. Les seeds 41, 73 et 119 produisent désormais `attacks.txt` et `first-kill.txt` ;
les quinze premières cases du contact sheet sont espacées de `1 s`. La capture continue ensuite
jusqu'à trente secondes si nécessaire pour obtenir une première mort explicable, puis remplace la
dernière case par l'état exact de cette élimination.

## 17. Jalon expérimental 3v3 — revenge et contre-couverture

Le jalon 5 généralise la punition après parade ou stab raté sans réutiliser l'arbitrage du
`MODERN_2V2`. Un échange 3v3 est volontairement borné : une attaque initiale peut produire un seul
revenger principal et un seul contre-couvreur principal. Une parade ou un raté de ces deux actions
termine l'échange ; il ne crée jamais une chaîne récursive de revenges.

### Classement des candidats au revenge

Les deux partenaires de la victime potentielle sont évalués séparément sous forme de
`TeamRevengeCandidate`. Le candidat ne peut pas lire directement l'attaquant : celui-ci doit être une
menace primaire ou secondaire connue avec une confiance d'au moins `0,24`. Une menace périphérique
reste trop imprécise pour déclencher cette punition.

```text
impactEta = temps d'alignement perçu
          + temps de préparation restant
          + temps pour entrer à portée

vulnerability = BLOCK_STUN restant + récupération
             ou RECOVERY restante
             ou récupération normale après le raté
```

La ligne est contrôlée contre les deux alliés. Le moteur compte aussi les `CoverLink` viables qui
resteraient utilisables sans le candidat : un Agent ne gagne donc pas automatiquement parce qu'il est
le plus proche s'il détruit toute la couverture de son équipe.

```text
score = 0,36 × avantage temporel
      + 0,20 × sécurité de ligne
      + 0,17 × compétence technique/collective
      + 0,12 × couverture préservée
      + 0,15 × sécurité face à la contre-couverture
```

Le candidat est refusé s'il est occupé, en récupération, étourdi, trop loin, mal informé, bloqué par
un allié, trop lent pour la fenêtre ou insuffisamment sûr. Seul le meilleur candidat viable devient
`COMMIT_REVENGE`; l'autre conserve ses liens et son mouvement de couverture.

### Un seul contre-couvreur principal

Après le choix du revenger, les deux partenaires de l'attaquant vulnérable sont évalués. Le moteur
utilise leur perception du revenger, leur ETA d'alignement/préparation/contact, la sécurité de leur
ligne et leurs attributs Décision/Collectif. Le meilleur candidat viable devient
`COUNTER_COVER`, prépare son arme en Hold et ne relâche que lorsque le revenger engage réellement et
entre dans sa ligne physique.

Cette menace n'annule pas magiquement le revenge. Son ETA entre déjà dans le score du revenger ; le
contre-couvreur doit ensuite orienter sa baïonnette, atteindre la portée et réussir la collision. Les
autres Agents ne sont ni synchronisés ni convertis en attaquants supplémentaires.

### Résolution, debug et métriques

Les coups de revenge et de contre-couverture utilisent les états, parades, zones corporelles et
collisions ordinaires. Les métriques distinguent candidats, occasions, engagements, refus,
annulations, parades, ratés, hits et kills pour les deux rôles. `preventedRevengeChains` prouve qu'une
action secondaire bloquée ou ratée ferme l'échange au lieu d'en engendrer un nouveau.

Avec `D`, l'interface affiche le revenger, sa cible, les liens préservés, le risque de contre-couverture,
le score, puis le contre-couvreur avec son ETA. `attacks.txt` conserve désormais les événements
`GROUP3_REVENGE_*`, `GROUP3_COUNTER_COVER_*` et `GROUP3_EXCHANGE_FINISHED`.

```powershell
java -cp .\out Experimental3v3RevengeTest
java -cp .\out VisualValidation3v3Capture
```

Les tests A–E garantissent un seul revenger, un risque borné, au maximum un contre-couvreur, l'absence
de chaîne infinie, une résolution physique naturelle et le déterminisme. Le jalon 6 prolonge désormais
ces échanges après la première élimination.

## 18. Jalon expérimental 3v3 — clusters et transitions d'effectif

Le jalon 6 sépare clairement deux notions : un regroupement local ne change pas l'Engagement, tandis
qu'une élimination peut reconstruire l'architecture de combat. Le match ne s'arrête plus au premier
mort et continue jusqu'à la victoire d'une équipe.

### `LocalEngagementCluster` : description, pas ordre collectif

Toutes les `0,15 s`, le moteur construit un graphe biparti entre adversaires vivants. Deux Agents sont
reliés s'ils sont à moins de `2,75 m` ou si leur meilleur ETA d'impact est inférieur à `0,85 s`.
Les composantes connectées deviennent des `LocalEngagementCluster` contenant les membres bleus et
rouges, leur centre, rayon, meilleur ETA, confiance d'isolation, caractère contesté et raison.

Un cluster peut donc décrire :

- une ligne centrale partagée ;
- un 2v1 temporaire encore couvert ;
- une aile en cours d'isolement ;
- une isolation durable.

Il ne crée jamais directement `DUEL` ou `TWO_VS_ONE`, ne verrouille aucune cible et ne commande pas
une rotation. Il renseigne seulement l'attention, le debug et les futures décisions géométriques.

### Isolation fondée sur les ETA et la couverture

Lorsqu'un Agent est seul dans un cluster face à plusieurs ennemis :

```text
isolationConfidence = 0,34 × surcharge numérique
                    + 0,34 × perte de CoverNetwork
                    + 0,32 × retard du meilleur soutien allié
```

La confiance doit atteindre `0,66` pendant environ `0,35 s` pour produire une isolation durable.
Une séparation visuelle seule ne suffit donc pas. Un allié hors du cluster mais capable d'arriver
avant ou peu après l'impact rend le 2v1 `contested`.

### Transitions collectives 3v2 et 3v1

Après chaque élimination, le moteur nettoie les cibles mortes, observations, plans d'attaque, résumés
de couverture et clusters. Les formations et `CoverNetwork` sont recalculés immédiatement.

```text
3 contre 2 → GROUP_3V2 collectif
3 contre 1 → GROUP_3V1 collectif
```

Le 3v2 n'est jamais transformé automatiquement en `2v1 + 1v1`. La formation à trois reste souple ;
le duo survivant utilise deux positions latérales adaptées. En 3v1, deux attaquants peuvent considérer
la même cible depuis des lignes distinctes, mais un troisième stab simultané reste interdit et un
couvreur est conservé.

### Retour vers les moteurs validés

Quand aucun camp ne possède encore trois survivants, le moteur 3v3 est fermé proprement :

```text
2 contre 2 → EngagementType.GROUP_2V2
2 contre 1 → EngagementType.TWO_VS_ONE
1 contre 1 → EngagementType.DUEL
```

Le handoff réutilise les constructeurs, rôles `FIX/FLANK/SURVIVE`, manœuvres, décisions et collisions
existants. Le `TWO_VS_ONE` n'a pas été recalibré. Après une nouvelle mort, le handoff peut progresser
de 2v2 vers 2v1, puis vers 1v1, jusqu'à `COMPLETE`.

### Tests, debug et validation

Avec `D`, l'interface affiche la phase courante et chaque cluster avec membres, ETA, confiance
d'isolation et raison. Les métriques comptent clusters, 2v1 temporaires, clusters contestés,
isolations durables, créations 3v2/3v1, handoffs et références mortes supprimées.

```powershell
java -cp .\out Experimental3v3TransitionTest
java -cp .\out VisualValidation3v3Capture
```

Les tests A–G vérifient les clusters contestés, l'isolation persistante, l'absence de scission du 3v2,
le 3v1 collectif, les trois handoffs, le déterminisme et des matchs naturels complets sans position
invalide. `contact-sheet.png` reste centré sur la première mort ; `transition-sheet.png` montre les
huit instants suivants. `transitions.txt` enregistre les changements collectifs et handoffs, tandis
que `match-result.txt` confirme l'heure, la phase finale et le vainqueur du match complet.

### Finition et batch de 500 seeds

Les clusters influencent maintenant réellement le moteur sans commander les joueurs : une menace du
même cluster reçoit un faible bonus de saillance dans l'attention, et une cible durablement seule face
à au moins deux ennemis peut produire `TARGET_ISOLATED_FROM_COVER_NETWORK`. Chaque occasion conserve
un `ClusterOpportunityEvidence` figé : nombres locaux, confiance d'isolation, ETA minimum, état
contesté et raison. Une légère correction de mouvement ramène l'isolé vers sa formation, sans position
imposée ni verrouillage de cible.

Un blocage découvert par le batch concernait deux stabs en Hold contre une même parade. Le Hold reste
sans durée maximale. Cependant, quand deux partenaires tiennent réellement la même cible et la même
hauteur, le premier maintient la menace et le second choisit explicitement la hauteur opposée. Cela
peut ouvrir un stab ou exiger une parade haut/bas ; le troisième joueur reste couvreur.

```powershell
java -cp .\out Experimental3v3BatchTest 100
java -cp .\out Experimental3v3BatchTest 500
```

Résultat final des seeds 1–500 : `500` matchs terminés, `0` timeout, `0` position invalide,
`0` sortie d'arène et `0` échec de déterminisme. Le batch a observé `305` isolations durables,
`1553` engagements issus d'un cluster, `247` hits correspondants, `394` transitions 3v2,
`314` transitions 3v1 et les trois types de handoff.

### Correctif du freeze post-élimination — jalon 2 transactionnel

Le changement d'effectif du `MODERN_3V3` ne reconstruit plus directement le moteur depuis plusieurs
branches. À la fin de la résolution physique d'un tick, `captureRoster()` produit un
`RosterSnapshot` contenant deux masques stables de trois bits et les nombres de survivants. Le moteur
appelle ensuite uniquement :

```text
requestRosterTransition(snapshot)
→ applyPendingRosterTransition()
```

`processedRoster` représente l'effectif déjà installé et `pendingRosterTransition` l'effectif final
observé pendant le tick. Si deux Agents meurent pendant le même pas physique, leurs décès apparaissent
donc dans un seul snapshot et une seule reconstruction. Le snapshot traité et l'ancien
`experimentalAliveMask` sont mis à jour immédiatement après l'installation réussie.

Chaque transition réserve un identifiant monotone et conserve deux `TransitionTrace` structurés : une
requête avec `applied=false`, puis sa conclusion avec `applied=true`. Les deux entrées contiennent le
même identifiant, le tick, le temps simulé, les contrôleurs et phases avant/après, les masques, les
effectifs et la raison. Ces traces restent en mémoire et ne produisent aucun spam de console.

Les gardes `transitionInProgress` et `transitionAppliedThisTick` empêchent respectivement une
réentrance et une deuxième application pendant le même tick. Un `TransitionKey` déjà appliqué provoque
une erreur explicite au lieu d'être ignoré ou reconstruit indéfiniment. Les métriques exposent les
requêtes, applications, doublons, tentatives récursives et tentatives multiples par tick.

```powershell
java -cp .\out Experimental3v3TransitionTest
java '-Djava.awt.headless=true' -cp .\out Experimental3v3SwingTransitionTest
java -cp .\out Experimental3v3BatchTest 500
```

Les tests A–J couvrent maintenant la première mort 3v3→3v2, une double mort simultanée et l'absence de
réapplication pendant 1 000 ticks. Le batch 3v3 termine toujours `500/500` matchs sans timeout ni
divergence déterministe. Il produit `1463` requêtes et exactement `1463` applications, avec `0`
requête dupliquée, `0` application dupliquée, `0` réentrance et `0` seconde application dans un même
tick. Les 28 critères 2v2 passent encore sur 500 seeds.

### Contrôleur actif irréversible — jalon 3

`SimulationScenario` et le moteur actif ont maintenant deux responsabilités distinctes. Le scénario
reste la description immuable de l'origine du match (`MODERN_3V3` ou `CONTROLLED_3V3`). Le champ
`activeCombatController`, de type `ActiveCombatController`, désigne exclusivement le moteur autorisé à
recevoir le prochain tick :

```text
GROUP_3V3 → GROUP_3V2 → GROUP_3V1
                    ↘ GROUP_2V2 → TWO_VS_ONE → DUEL → COMPLETE
```

La boucle expérimentale utilise un `switch` sur ce contrôleur. Les trois contrôleurs de groupe passent
par le moteur collectif 3v3 ; `GROUP_2V2`, `TWO_VS_ONE` et `DUEL` passent uniquement par le moteur de
handoff existant. Les conditions internes de ce dernier consultent également le contrôleur, et non
plus le scénario initial ou seulement `experimentalGroupPhase`.

Lors d'un handoff, l'ancien engagement 3v3 est brisé, `modernGroup3Engagement` devient `null`, le nouveau
contrôleur est installé dans la même transaction et sa conformité avec le contrôleur demandé est
vérifiée avant de valider `processedRoster`. Puisque les morts ne reviennent pas, aucun chemin ne permet
de réinstaller un contrôleur 3v3 après `GROUP_2V2`, `TWO_VS_ONE` ou `DUEL`.

Le test K force successivement 2v2, 2v1 et 1v1. Il vérifie que le scénario reste 3v3, que le nouveau
contrôleur reçoit le tick suivant et que tous les compteurs des anciens contrôleurs cessent
d'augmenter. Sur 500 matchs naturels, les ticks observés sont :

```text
GROUP_3V3=277675  GROUP_3V2=77612  GROUP_3V1=123060
GROUP_2V2=85589   TWO_VS_ONE=125222  DUEL=21114
```

Les `1463` transitions restent appliquées exactement une fois, sans doublon, réentrance ou seconde
application dans le même tick. Aucun paramètre tactique n'a été modifié.

### Purge centrale des références mortes — jalon 4

Avant de reconstruire un contrôleur, la transaction appelle désormais une seule méthode :
`purgeDeadReferences(aliveAgents())`. L'ensemble `alive` utilise l'identité des objets et sert de source
de vérité commune à toute la purge. Elle traite, pour chaque Agent survivant, les cibles et marques,
relations de couverture, références de pression et dézonage, menace tactique, cible de mouvement,
`AttentionAllocation`, `MultiThreatAttention`, observations et cartes de menaces.

Les objets composés sont invalidés si l'un de leurs participants est mort : `CoverAssessment`,
`AttackOpportunity`, `RevengeOpportunity`, `CounterCoverAssessment`, `WeaponThreatLane` et
`LocalManeuver`. Dans le groupe collectif, la même passe nettoie les liens de couverture actifs,
clusters, temps d'isolation, preuves figées, plans d'attaque, revenge et contre-couverture.

Les tableaux originaux de `TeamFormation3` et `CoverNetwork` restent les identités stables du roster et
ne sont jamais utilisés directement comme cible. En revanche, leurs rôles, liens viables et plans actifs
ne peuvent conserver un mort. Une manœuvre dont un rôle disparaît n'est pas supprimée : son type, sa
durée et sa direction sont conservés, puis `rebindGroup3Maneuver()` réaffecte intérieur et extérieur aux
survivants. Cela évite un nouveau tirage aléatoire et préserve le gameplay. Les `CoverLink` marqués
`dead_participant` et `viable=false` restent uniquement des résultats diagnostiques ; ils ne comptent
pas comme liens actifs.

Après l'installation du nouveau contrôleur, `auditDeadReferences()` produit un `DeadReferenceAudit` :

```text
activeDeadTargets
deadAttentionReferences
deadCoverLinks
deadClusterMembers
deadFormationMembers
deadPlanMembers
details
```

En cas de compteur non nul, la transition échoue immédiatement avec les détails au lieu de continuer
avec un état corrompu. Le test L injecte volontairement le même mort dans la cible, le plan, la
couverture, la pression, le mouvement et l'attention, puis vérifie leur disparition.

Sur 500 matchs : `1463` transitions appliquées, `1463` purges, `0` référence morte après transition,
`500/500` matchs terminés et `0` échec déterministe. Le test Swing et les 28 critères 2v2 restent
valides.

### Journal borné, watchdog et failsafe — jalon 5

Les transitions utilisent maintenant `TransitionLogger`. Une requête et son application produisent
chacune une ligne structurée avec identifiant, tick, temps, contrôleurs, phases, masques et raison. Le
logger conserve au maximum `256` lignes et `256` clés de déduplication ; lorsque la limite est atteinte,
la ligne et la clé les plus anciennes sont retirées ensemble. Le mode normal ne fait aucun
`System.out.println()` : `consoleLinesEmitted` reste donc à zéro. Un futur mode de diagnostic peut
activer la console sans perdre la déduplication.

`emitOnce(key, line)` garantit qu'une même erreur structurelle ne remplit pas la console. Les métriques
séparent `consoleLinesEmitted`, `duplicateConsoleLinesSuppressed` et `storedLinesDropped`. Le test M
envoie 1 000 fois la même erreur : une seule ligne est stockée et `999` doublons sont supprimés. Après
300 clés supplémentaires, le buffer reste exactement à 256 lignes. Le journal tactique général est
également limité à `4096` événements.

Après chaque tick visible, `ProgressSnapshot` capture le tick, le temps simulé, le contrôleur, le roster,
les six positions et les six états. Le watchdog compare deux snapshots successifs et mesure :

```text
ticksWithoutAnyPositionChange
ticksWithoutAnyStateChange
repeatedTransitionStateTicks
logicalFreezeReports
```

Un freeze logique exige cinq secondes simulées sans aucun déplacement, changement d'état, changement
de contrôleur ni changement de roster. Un Hold individuel n'est donc pas une erreur si les autres
Agents continuent de bouger ou de combattre. Le test N maintient volontairement un Agent en Hold pendant
six secondes et confirme qu'aucun freeze global n'est signalé.

Le failsafe conserve les demandes de roster des 60 derniers ticks. Si le même roster non traité est
demandé plus de deux fois, le match est mis en pause une seule fois et un `TransitionFailsafeReport`
fige le scénario, le contrôleur, les rosters courant/traité/pending, la dernière transition, le dernier
audit, les engagements actifs et une pile simplifiée. Le test O vérifie qu'une quatrième demande ne
crée ni second rapport ni nouvelle ligne.

Sur les 500 matchs naturels : `maximumTicksWithoutPositionChange=0`,
`maximumTicksWithoutStateChange=2112`, `maximumRepeatedTransitionStateTicks=0`,
`logicalFreezeReports=0`, `transitionFailsafePauses=0` et `consoleLinesEmitted=0`. Une longue période
sans changement d'état n'est pas considérée comme figée puisque les positions continuent d'évoluer.

### Mode graphique de reproduction et validation finale — jalon 6

La commande suivante ouvre le même `MapPanel`, le même `javax.swing.Timer` et le même chemin de combat
que le menu 3v3 normal :

```powershell
java -cp .\out Main debug-3v3-transition 41
```

`Main` conserve `MODERN_3V3` comme scénario et active uniquement un `TransitionDebugRecorder` sur le
panneau créé. À la première mort, le recorder mémorise l'heure et l'effectif, puis observe encore
15 secondes réelles à vitesse 1×. À chaque callback, il compare les six positions et états, compte les
nouveaux départs d'attaque et les morts suivantes. Le timer et les repaint continuent même si la partie
se termine avant la fin de la fenêtre d'observation.

La création du rapport est déclenchée depuis l'EDT, mais l'écriture UTF-8 utilise un
`CompletableFuture` : aucun accès disque n'allonge le callback Swing. Le fichier se trouve dans :

```text
out/debug/transition-seed-<seed>.log
```

Il contient la progression post-mort, les transitions REQUEST/APPLIED, tous les compteurs
transactionnels, le dernier audit, le watchdog, le résultat du match et les métriques EDT. Le test
`Debug3v3TransitionSeedsTest` crée en parallèle trois vrais panneaux à 1×, appelle régulièrement leur
méthode de rendu dans une image et attend les quinze secondes complètes pour les seeds 41, 73 et 119.

```powershell
java '-Djava.awt.headless=true' -cp .\out Debug3v3TransitionSeedsTest
```

Résultats observés :

```text
seed 41  : 891 ticks, 396 changements de position, 57 changements d'état,
           12 attaques, 2 morts suivantes, EDT max 14,590 ms, match terminé
seed 73  : 888 ticks, 765 changements de position, 77 changements d'état,
           15 attaques, 2 morts suivantes, EDT max 4,984 ms, match terminé
seed 119 : 891 ticks, 510 changements de position, 47 changements d'état,
           9 attaques, 3 morts suivantes, EDT max 4,711 ms, match terminé
```

Pour les trois seeds : `duplicateTransitionApplications=0`, `recursiveTransitionAttempts=0`,
`deadReferencesAfterTransition=0`, `logicalFreezeReports=0`, `edtTicksOver50Ms=0` et
`consoleLinesEmitted=0`. Les fichiers produits sont directement consultables dans `out/debug`.

### Approche 3v3 dynamique — nouveau jalon 1 : modèle et persistance

Le nouveau chantier d'approche commence par une couche de décision qui ne touche encore ni aux
vitesses ni aux attaques. `Group3ContactBand` sépare `FAR_APPROACH`, `SPACE_CONTEST`, `CONTACT` et
`ACTIVE_EXCHANGE`. La sélection utilise la distance entre centres, la plus petite distance adverse, le
nombre d'oppositions sous portée potentielle et les états physiques d'attaque/parade. Des seuils de
sortie différents des seuils d'entrée fournissent l'hystérésis ; un contact réel peut passer directement
de FAR à CONTACT sans attendre un tick intermédiaire.

Chaque équipe possède indépendamment un `Group3ApproachPlan`. Il conserve un `ApproachPattern`, une
durée minimale/préférée/maximale, les rôles temporaires conducteur/soutien/arrière, le signe latéral,
trois engagements de mouvement, le centre initial et un point de rencontre prédit. Le point part du
milieu des équipes puis reçoit un décalage latéral borné entre `0,45` et `1,30 m`, avec correction du
bord de l'arène.

La sélection utilise un générateur dérivé de la seed, de l'équipe et du numéro de plan. Elle ne consomme
pas le `Random` principal du combat : ajouter le modèle ne décale donc pas les futurs tirages d'attaque.
Les plans bleu et rouge restent déterministes mais ne sont pas des miroirs obligatoires. Un plan dure au
moins `1,2–1,8 s`, vise `2,0–3,4 s` et expire entre `4,0–5,0 s`. Il peut être interrompu par une menace de
pointe imminente, une formation réellement brisée, un bord critique ou une isolation durable.

Les tests A–E de `Group3ApproachPlanTest` vérifient le point latéral, les trois rôles distincts, la
persistance au-delà des intervalles de perception, le déterminisme, l'indépendance des équipes,
l'hystérésis et l'absence de création de duel au premier contact. Le batch 3v3 de 100 seeds et les 28
critères 2v2 passent sans régression. Le steering utilisera ces plans au jalon suivant ; ce premier jalon
ne prétend donc pas encore modifier visuellement les trajectoires.

### Approche 3v3 dynamique — jalon 2 : steering, vitesse et profondeur

Le plan persistant pilote désormais réellement les Agents pendant `FAR_APPROACH` et
`SPACE_CONTEST`. La méthode `group3ApproachVelocity()` transforme le motif, les engagements du plan,
le rôle temporaire et le point de rencontre en une vitesse composée dans le repère de la formation.
Elle ne téléporte aucun joueur et n'impose aucune position : cette vitesse passe toujours par
`integrateVelocity()`, donc par l'accélération et l'inertie liées au jeu de jambes, puis par la limite de
vitesse, la pénalité de recul, les collisions et les limites de l'arène.

À longue distance, la projection avant demandée est bornée entre `0,78` et `1,05 m/s` et la projection
latérale entre `0,28` et `0,58 m/s`. Dans `SPACE_CONTEST`, la poussée avant descend progressivement dans
la plage `0,42–0,78 m/s`, tandis que la contestation latérale monte dans la plage `0,42–0,72 m/s`. Le
motif module ces valeurs : un sweep accentue la composante latérale, une contre-rotation sacrifie une
partie de l'avance, et une entrée retardée ralentit légèrement la progression. La distance latérale au
`predictedMeetingPoint` module aussi la poussée sans pouvoir inverser brutalement le plan.

Les trois rôles n'utilisent pas la même projection. Le conducteur reçoit un facteur avant de `1,12`, le
soutien `1,00` et l'arrière `0,88`; leur moyenne conserve donc la consigne collective. Une correction de
profondeur souple vise respectivement `+depthCommitment`, `+0,22 × depthCommitment` et
`-0,68 × depthCommitment`. Il s'agit d'une force bornée, non d'une destination obligatoire : évitement,
menace, bord et collision peuvent toujours déformer la ligne.

`Group3SteeringComposition` rend la somme explicite :

```text
desiredVelocity = approachContribution
                + teamManeuverContribution
                + localManeuverContribution
                + formationContribution
                + coverContribution
                + avoidanceContribution
+ boundaryContribution
```

L'évitement 3v3 parcourt explicitement les deux partenaires vivants; il ne réutilise pas le raccourci
2v2 qui ne connaît qu'un seul coéquipier.

Pendant `FAR_APPROACH`, les poids manœuvre/formation/couverture sont `0,18 / 0,32 / 0,28`; pendant
`SPACE_CONTEST`, ils deviennent `0,42 / 0,65 / 0,62`. Dès `CONTACT`, ou si le plan a été annulé par une
urgence physique, ils reviennent à `1,0`. Ainsi, une pointe imminente ou une formation réellement brisée
conserve toute sa priorité. Inversement, les seuils de `RECOVER_FORMATION` et `COMPRESS` sont assouplis
à longue distance : un lien momentanément imparfait ne ramène plus immédiatement tout le monde au
centre.

Les normes de chaque contribution sont accumulées dans `Group3v3Metrics`, ce qui permet de vérifier que
l'approche domine réellement loin du contact sans deviner son effet à l'écran. Les tests A–G de
`Group3ApproachSteeringTest` contrôlent le déplacement avant et latéral, la profondeur conducteur/arrière,
la dominance des contributions, la restitution du moteur existant au contact, le déterminisme exact,
les plages de vitesse réellement appliquées et l'absence de récupération abusive pour une faiblesse de
couverture lointaine. Les attaques, leurs seuils, le `TWO_VS_ONE`, le moteur 2v2 et les transitions
d'effectif restent inchangés.

### Premier contact 3v3 — jalon 3 : événements et audit des fronts plats

Le moteur distingue maintenant le passage global en bande `CONTACT` du premier contact individuel de
chaque Agent. Après le déplacement physique, `measureGroup3FirstContacts()` cherche l'adversaire vivant
le plus proche. Lorsque sa distance est inférieure ou égale à `2,95 m`, un `FirstContactEvent` immuable
enregistre l'Agent, cet adversaire, le temps simulé, son `FormationRole3`, la distance réelle et la
présence d'une couverture physique suffisante. Un Agent ne reçoit qu'un seul premier événement : les
changements ultérieurs de cible ne réécrivent donc pas l'histoire.

La dispersion temporelle est calculée par :

```text
firstContactSpread = dernier temps de premier contact
                   - premier temps de premier contact
```

Une fenêtre glissante de `0,12 s` mesure aussi le plus grand nombre de premiers contacts simultanés.
Elle alimente séparément `simultaneousThreePlayerContacts` et `simultaneousSixPlayerContacts`. Ces
compteurs sont descriptifs : aucun délai artificiel n'est ajouté aux Agents.

Avant le premier événement, `measureGroup3PreContactGeometry()` accumule les déplacements avant et
latéraux des deux centres, la largeur, la profondeur, la profondeur maximale, la déviation par rapport
aux lignes centrales initiales et la corrélation des chemins. Pour comparer deux équipes qui avancent
l'une vers l'autre, la corrélation emploie :

```text
teamPathCorrelation = -dot(normalize(blueDelta), normalize(redDelta))
```

Une valeur proche de `1` décrit donc des trajectoires opposées mais presque parfaitement symétriques.
Les temps jusqu'à `SPACE_CONTEST`, au premier contact et à la première attaque sont conservés
indépendamment.

`FlatFrontConvergenceAudit` est finalisé lorsque les six Agents ont un événement, `1,50 s` après le
premier événement, ou juste avant une transition provoquée par une élimination. Il combine six signaux :

```text
déplacement latéral < 0,55 m
profondeur moyenne < 0,28 m
au moins quatre contacts comprimés sous 0,18 s
corrélation des chemins > 0,88
déviation moyenne de ligne < 0,32 m
durée d'approche > 4,20 s
```

Trois signaux au moins sont nécessaires pour déclarer l'approche suspecte. La chaîne `reason` conserve
tous les signaux présents, même lorsque le total reste insuffisant. L'audit ne change ni cible, ni plan,
ni vitesse, ni décision d'attaque : il sert uniquement à trouver les seeds visuellement fades.

`Group3FirstContactAuditTest` couvre un contact aile–centre–arrière décalé, un front plat simultané, le
maintien d'un unique contrôleur `GROUP_3V3` sans `DUEL`, et le déterminisme exact. Sur 100 matchs :
`approachAudits=100`, `flatFrontConvergences=1`, `simultaneousSixPlayerContacts=15`, dispersion moyenne
`0,669 s`, déplacement latéral pré-contact moyen `0,787 m`, sans timeout, freeze, sortie d'arène,
référence morte ni échec déterministe.

### Validation visuelle de l'approche — jalon 4

`ApproachVisualValidationCapture` rejoue les seeds `41`, `73` et `119` avec un pas physique de
`1/120 s`, soit le même écoulement simulé qu'à vitesse `1×`. Chaque seed est exécutée deux fois : la
version active normale, puis une baseline visuelle qui désactive uniquement la contribution
`Group3ApproachPlan`. Cette option est inaccessible depuis l'interface et ne réactive ni `FRONT_LINE`,
ni un ancien contrôleur; elle sert seulement à reconstruire le déplacement pré-jalon avec la même seed,
les mêmes Agents et le même moteur d'attaque.

La commande reproductible est :

```powershell
java '-Djava.awt.headless=true' -cp .\out ApproachVisualValidationTest
```

Pour chaque seed, huit images sont prises à `0,00`, `0,40`, `0,80`, `1,20`, `1,60`, `2,00`, `2,40` et
`2,80 s`. Elles affichent la bande de contact, le nombre d'événements individuels, la courbure de la
formation, le corps, la pointe et l'état simple des armes. Les trajectoires sont échantillonnées toutes
les `0,05 s`. Les fichiers se trouvent sous :

```text
out/approach-validation-3v3/seed-<seed>/
```

Chaque dossier contient `old-contact-sheet.png`, `new-contact-sheet.png`, les deux trajectoires, deux
planches comparatives et les rapports `approach.txt`, `first-contact.txt` et `flat-front-audit.txt`. Les
versions `old-*` et `new-*` des rapports restent également disponibles pour l'analyse différentielle.

Résultats observés :

| Seed | Premier contact baseline → actif | Spread baseline → actif | Latéral baseline → actif | Profondeur baseline → actif |
|---:|---:|---:|---:|---:|
| 41 | `3,467 → 1,608 s` | `0,092 → 0,192 s` | `0,101 → 0,774 m` | `0,009 → 0,382 m` |
| 73 | `3,475 → 1,658 s` | `0,083 → 0,475 s` | `0,086 → 0,914 m` | `0,015 → 0,476 m` |
| 119 | `3,467 → 1,367 s` | `0,092 → 1,183 s` | `0,103 → 0,682 m` | `0,009 → 0,201 m` |

Les trois baselines sont classées plates et produisent un contact simultané des six Agents. Les trois
versions actives sont non suspectes et ne produisent aucun contact simultané à six dans la fenêtre de
`0,12 s`. La seed 119 a la profondeur la plus faible, mais aussi le spread le plus large et une courbure
claire. Les paramètres du jalon 2 sont donc conservés : augmenter encore la profondeur pour cette seule
seed rigidifierait les seeds 41 et 73 et constituerait un réglage excessif sur l'échantillon visuel.

`ApproachVisualValidationTest` vérifie l'existence et la résolution de tous les fichiers, puis exige pour
chaque seed un contact plus rapide, un spread supérieur, au moins quatre fois plus de déplacement
latéral, au moins huit fois plus de profondeur, et le passage d'un audit plat à un audit non suspect.

### Acceptation finale de l'approche 3v3 — jalon 5

> Cette acceptation est celle du moteur d'approche avant le chantier Dead Agent 6.1–6.3. Les
> paramètres d'approche n'ont pas été modifiés, mais le lot complet n'a pas encore été rejoué après
> l'intégration de `DeathPose`.

Le jalon final n'ajoute aucun comportement au moteur. Il fige les paramètres retenus au jalon 4 et
exécute les validations complètes avec le même code que l'application. La séquence reproductible est :

```powershell
javac -encoding UTF-8 -d .\out .\src\*.java
java '-Djava.awt.headless=true' -cp .\out ApproachVisualValidationTest
java -cp .\out Group3FirstContactAuditTest
java -cp .\out Group3ApproachSteeringTest
java -cp .\out Group3ApproachPlanTest
java -cp .\out Experimental3v3TransitionTest
java -cp .\out Experimental3v3BatchTest 100
java -cp .\out Experimental3v3BatchTest 500
java -cp .\out FinalAcceptanceTest 500
```

Le lot 3v3 de 500 matchs donne :

```text
matchesCompleted=500
timeouts=0
blueWins=254
redWins=246
transitionRequests=1577
transitionApplications=1577
deadReferencesAfterTransition=0
logicalFreezeReports=0
transitionFailsafePauses=0
approachAudits=500
flatFrontConvergences=1
simultaneousSixPlayerContacts=49
averageFirstContactSpread=0,738 s
averagePreContactLateralDistance=0,787 m
averagePreContactDepth=0,305 m
averageTimeToFirstContact=1,497 s
averageTimeToFirstAttack=4,482 s
invalidPositions=0
arenaExits=0
determinismFailures=0
consoleLinesEmitted=0
```

Le lot 2v2 de 500 matchs termine également `500/500`, sans timeout, stalemate, position invalide,
sortie d'arène, thrashing ni échec déterministe. `FinalAcceptanceTest` confirme ses 28 critères, y
compris le maintien du groupfight jusqu'à la première mort, le handoff physique vers le `TWO_VS_ONE`,
les chaînes causales et l'absence d'activation des anciens systèmes.

Correspondance des 28 critères demandés pour l'approche :

1. Le watchdog reste silencieux : `logicalFreezeReports=0`.
2. La console normale reste silencieuse : `consoleLinesEmitted=0`.
3. Les transitions restent transactionnelles : `1577` requêtes pour `1577` applications.
4. Le 2v2 passe son acceptation complète sur 500 matchs.
5. Le `TWO_VS_ONE` passe toujours ses tests B–F.
6. Le premier contact moyen arrive à `1,497 s`.
7. Le déplacement latéral pré-contact moyen atteint `0,787 m`.
8. Les choix bleu et rouge utilisent des RNG dérivés indépendants.
9. Les rôles temporaires permettent à une aile de conduire.
10. La profondeur moyenne pré-contact atteint `0,305 m`.
11. Un seul audit sur 500 reste classé plat.
12. Seulement 49 matchs sur 500 ont six contacts dans la fenêtre de `0,12 s`.
13. Les seuils lointains de couverture n'imposent plus une récupération immédiate.
14. L'annulation d'un plan restitue tous les poids aux urgences physiques.
15. Le moteur d'attention limitée et ses délais ne sont pas modifiés.
16. Les attaques passent toujours par `AttackOpportunity`.
17. Le plan d'approche ne lance aucun stab.
18. Revenge n'est pas recalibré.
19. Contre-couverture n'est pas recalibrée.
20. Dégâts, collisions d'arme et parades restent physiques.
21. Les clusters restent descriptifs.
22. Les tests de contact confirment qu'aucun `DUEL` fixe n'est créé.
23. Aucun comportement n'est codé pour une seed; les seeds 41/73/119 ne sont que des entrées de capture.
24. Les plans persistent et les tests ne détectent aucun thrashing.
25. Les replays déterministes passent sur les lots 100 et 500.
26. Les transitions et matchs graphiques restent actifs après une mort.
27. Les batchs 100 et 500 passent pour le 3v3 et l'acceptation 2v2.
28. Les README français et chinois décrivent le modèle, les formules, les tests et les résultats.

Les systèmes de transition gelés, `GROUP_2V2`, `TWO_VS_ONE`, les seuils d'attaque, les timings, les
parades, les dégâts, revenge et contre-couverture n'ont reçu aucune modification pendant les jalons
d'approche. Le seul chemin alternatif est le baseline de capture, désactivé par défaut et inaccessible au
jeu normal.

## 19. Audit des joueurs morts — jalons 6.1 et 6.2

Le watchdog global prouvait que le match continuait après une élimination, mais il ne prouvait pas que
le cadavre était réellement neutre. `DeadAgentIsolationTest` a donc instrumenté séparément : mouvement,
intégration de vitesse, rotation du corps et de l'arme, collisions, évitement, bord, formation,
couverture, cluster, attention et sélection de cible.

Le bug reproduit était un véritable déplacement logique post-mortem : dans le chemin 3v3 collectif,
un Agent `DEAD` pouvait encore passer par perception, steering, intégration et correction de bord. Le
rendu gris n'était donc pas la cause ; il révélait un joueur mort toujours admis dans des systèmes
actifs.

### Expériences différentielles A, B, C et D1

Les expériences utilisent la même seed et la même mort contrôlée :

| Variante | Modification expérimentale | Conclusion |
|---|---|---|
| A | comportement courant de l'ancienne version | reproduisait le mort actif |
| B | cadavre masqué au rendu seulement | permettait de distinguer logique et dessin |
| C | décalage visuel seulement | confirmait qu'une transformation graphique ne devait pas changer le combat |
| D1 | neutralisation logique du mort | immobilisait et excluait le mort sans modifier le rendu |

Les captures différentielles comparent les survivants à plusieurs checkpoints du même tick : attention,
cible, formation, couverture, manœuvre, décision de combat, steering, intégration, résolution physique
et fin de tick. Pour les seeds `41`, `73` et `119`, la première contamination structurelle de l'ancienne
version apparaît au tick `61`, immédiatement après la reconstruction du réseau de couverture : le mort
figurait encore dans `activeParticipants`. La propagation différait ensuite selon la seed : qualité des
liens de couverture pour 41/73, puis consommation RNG précoce pour 119.

Les rapports sont conservés sous :

```text
out/dead-agent-differential/6.2b.2-seed-summary.txt
out/dead-agent-differential/seed-41/6.2b.2/
out/dead-agent-differential/seed-73/6.2b.2/
out/dead-agent-differential/seed-119/6.2b.2/
```

Cette analyse explique pourquoi une comparaison naïve de deux fins de match était insuffisante : dès
qu'une branche consomme un tirage aléatoire supplémentaire, les survivants peuvent diverger même après
suppression de la cause physique initiale.

### Audit du RNG et bande sémantique expérimentale

Le jalon 6.2b.3.1 inventorie tous les appels au RNG de `DuelSimulation` sans les remplacer :

```text
157 appels
153 lignes source
63 finalités sémantiques
0 appel classé OTHER
```

`RandomDrawPurpose` distingue notamment perception, bruit de position, choix de manœuvre, décision de
parade, distance estimée, délai de réaction, Chamber, backstab, dézonage, feinte, appât, visée et dégâts.
`SemanticRandomDrawKey` identifie un tirage par tick, checkpoint, demandeur, sujet, finalité et index
local. `SemanticRandomTape` sait enregistrer, rejouer et fournir un fallback déterministe, tout en
détectant les clés dupliquées et les requêtes non appariées.

Important : cette infrastructure n'est pas branchée au RNG de production. `SimulationRandom` et les
157 appels gardent leur comportement actuel. Le tape sert à préparer des comparaisons causales ; il ne
change ni probabilités ni replays normaux.

Commandes correspondantes :

```powershell
java -cp .\out DeadAgentCheckpointNonIntrusionTest
java -cp .\out DeadAgentInternalContaminationTest
java -cp .\out DeadAgentInternalSeedSummaryTest
java -cp .\out RandomDrawAuditTest
java -cp .\out SemanticRandomTapeTest
```

## 20. Jalon 6.3 — neutralisation permanente des joueurs morts

La décision du jalon 6.3 est de corriger l'invariant de production immédiatement, sans attendre le
branchement d'un replay RNG complet. Un joueur mort peut rester dans l'historique et être dessiné, mais
il ne doit plus participer à aucune décision ni interaction active.

### 6.3.1 intégré : pose de mort et gel central

Lors du premier passage `alive → dead`, `DeathPose` capture une seule fois :

```text
position
angle du corps
angle mondial de la baïonnette
```

La transition centrale `eliminate()` applique ensuite le même ordre pour toute mort de production :

1. capturer la pose avant toute modification ;
2. placer `alive` à faux et l'état à `DEAD` ;
3. supprimer cibles, menaces, attention, pression, manœuvres et intentions actives ;
4. restaurer la pose et annuler vitesse, vitesse désirée et vitesse angulaire ;
5. purger dans le même tick toutes les références mortes des survivants et structures collectives.

`freezeDeadAgents()` réaffirme l'invariant au début du tick et après la résolution physique. Même si
une future branche écrit accidentellement une vitesse ou un angle sur le cadavre, la position, le corps
et la baïonnette reviennent à `DeathPose`. Le gel est idempotent.

`DeadAgentParticipationDomain` sépare les domaines actifs des quatre domaines passifs autorisés :

```text
autorisés après la mort : RENDER, ROSTER_HISTORY, DEATH_EVENT, RESULT_ACCOUNTING

interdits après la mort : mouvement, vitesse, rotations, évitement, bord,
formation, couverture, attention, cible, attaque, revenge,
contre-couverture, manœuvre d'équipe, approche et steering
```

Le cadavre reste donc visible et gris à sa pose de mort. Il n'est ni un zombie, ni une cible, ni un
obstacle physique, ni un membre de couverture. Les variantes `HIDDEN_RENDER_ONLY` et
`VISUAL_OFFSET_ONLY` restent disponibles pour les anciens tests différentiels, mais ne définissent
pas le comportement normal.

### Validation déjà exécutée après 6.3.1

Au 2 août 2026, les validations suivantes passent sur le code courant :

```text
compilation UTF-8 de tous les fichiers
SimulationSmokeTest
DeadAgentIsolationTest
DeadAgentNeutralizationDifferentialTest
Experimental3v3TransitionTest A–O
git diff --check
```

Résultat central de `DeadAgentIsolationTest` après 2 000 ticks post-mort :

```text
maximumDisplacementAfterDeath = 0.0
mouvement / intégration / rotations = 0
collisions / évitement / bord = 0
formation / couverture / cluster = 0
attention / sélection de cible = 0
position, corps et baïonnette identiques à DeathPose
```

Dans le test différentiel actuel, A et D1 sont désormais identiques : l'ancien mode normal bénéficie
du même invariant permanent que la neutralisation expérimentale.

### Travail 6.3 restant

Le jalon complet n'est pas encore déclaré terminé. Les étapes prévues sont :

- 6.3.2 : assertions dédiées aux réseaux de couverture, attention, cible et combat ;
- 6.3.3 : arrêt dans le tick de mort, morts multiples, gel Swing réel et comparaison normal/D1 sur
  les seeds 41/73/119 ;
- 6.3.4 : campagnes 100 puis 500, rapports `out/dead-agent-fix/6.3/` et validation documentaire finale.

Les anciens résultats de 500 seeds restent affichés plus haut comme références historiques. Ils ne
constituent pas encore une certification post-6.3.

## 21. Suite de tests recommandée

Compilation et tests fonctionnels principaux :

```powershell
javac -encoding UTF-8 -d .\out .\src\*.java
java -cp .\out SimulationSmokeTest
java -cp .\out TeamManeuverTest
java -cp .\out ModernGeometryTest
java -cp .\out ModernGroup2v2Test
java -cp .\out TwoVsOneCoordinationTest
java -cp .\out AttackOpportunityTest
java -cp .\out DecisionExplanationTest
java -cp .\out AttributeBehaviorAudit
```

3v3 et transitions :

```powershell
java -cp .\out Experimental3v3AttentionTest
java -cp .\out Experimental3v3CoverNetworkTest
java -cp .\out Experimental3v3AttackPlanTest
java -cp .\out Experimental3v3RevengeTest
java -cp .\out Experimental3v3TransitionTest
java -cp .\out Group3ApproachPlanTest
java -cp .\out Group3ApproachSteeringTest
java -cp .\out Group3FirstContactAuditTest
```

Tests visuels/headless :

```powershell
java '-Djava.awt.headless=true' -cp .\out ApproachVisualValidationTest
java '-Djava.awt.headless=true' -cp .\out Debug3v3TransitionSeedsTest
```

Les lots longs ne doivent être relancés comme acceptation finale qu'après la fin de 6.3.2 et 6.3.3 :

```powershell
java -cp .\out Experimental3v3BatchTest 100
java -cp .\out Experimental3v3BatchTest 500
java -cp .\out FinalAcceptanceTest 500
```
