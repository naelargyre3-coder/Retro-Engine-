# Retro Engine — journal de développement

Ce journal est mis à jour à chaque intervention sur le dépôt. Les statuts distinguent les changements réellement enregistrés, les contrôles exécutés et les tâches encore bloquées.

## 2026-11-02 — Revue du cahier des charges et de l'icône officielle

### Référence consultée
- Cahier des charges complet fourni dans la conversation : architecture modulaire, fonctionnement réel, aucune fonction simulée, tests et limites documentés.
- Dépôt : `naelargyre3-coder/Retro-Engine-`, branche `main`.

### Vérifications effectuées
- `app/src/main/AndroidManifest.xml` : l'application n'a pas de déclaration `android:icon` ni `android:roundIcon`.
- `app/src/main/res/values/strings.xml` : le nom affiché est déjà `Retro Engine`.
- `app/src/main/res/` : aucune ressource mipmap d'icône n'est actuellement présente dans l'arborescence consultée.
- Le logo `retro_engine_logo.png` existe dans le Dropbox de l'utilisateur, mais son contenu binaire n'a pas pu être transféré dans l'environnement de modification du dépôt pendant cette intervention.

### Résultat
- Aucun fichier applicatif ni manifeste n'a été modifié pour éviter de fabriquer une icône de substitution ou de prétendre intégrer le logo original sans disposer de ses octets.
- L'intégration de l'icône officielle reste **en attente du fichier PNG source directement accessible**.
- Le nom Android `Retro Engine` est déjà correctement configuré.
- La compilation et la vérification sur appareil n'ont pas été lancées pour cette intervention, car aucune modification d'icône n'a été effectuée.

### Étape suivante
Importer le fichier original dans le dépôt à l'emplacement `app/src/main/res/drawable-nodpi/retro_engine_logo.png` ou le joindre directement à la conversation. Ensuite, générer les icônes adaptatives et les variantes de densité, mettre à jour le manifeste, compiler, inspecter l'APK et vérifier l'affichage après installation.


## 2026-11-02 — Publication du cahier des charges et mise en place du suivi permanent

### Modifications enregistrées
- Création de `docs/CAHIER_DES_CHARGES.md` : publication dans le dépôt de la spécification fonctionnelle et technique complète, avec les systèmes visés, le parcours utilisateur, les moteurs, les commandes, les exigences de sécurité/confidentialité, les phases et les critères d'acceptation.
- Le cahier des charges précise que les fonctions ne peuvent être annoncées comme prises en charge qu'après intégration et vérification réelles.
- Le journal devient le registre des interventions : chaque ajout ou correction devra documenter l'objectif, les fichiers touchés, le résultat et les tests.
- L'icône est explicitement hors périmètre de cette intervention : aucun fichier d'icône, ressource graphique ou manifeste n'a été modifié.

### État technique repris dans le suivi
Les éléments suivants sont déjà présents dans la base du projet consultée avant cette intervention ; ils ne sont pas de nouveaux ajouts de cette entrée :
- socle Android/Gradle, import local et détection des extensions de ROM ;
- inspection de base des en-têtes NES ;
- compilation CI du cœur Nestopia pour arm64-v8a et frontend JNI libretro ;
- première intégration de rendu vidéo/audio et des entrées ;
- sauvegarde SRAM et commandes d'états de sauvegarde libretro.

Ces éléments restent expérimentaux tant qu'ils ne sont pas validés sur un appareil. La génération d'APK autonome par ROM, les autres moteurs, la personnalisation complète, les filtres et la signature de production restent à réaliser.

### Vérification
- Le fichier du cahier des charges a été créé sur la branche `main`.
- Aucun build Android n'a été déclenché par la seule publication de documentation.
- Aucun test sur appareil n'a été effectué dans le cadre de cette intervention.

### Règle pour les prochaines interventions
À chaque modification de l'application, ajouter une entrée datée à ce journal dans le même lot de travail, avec :
1. résumé du changement ;
2. liste des fichiers modifiés ;
3. résultat attendu et résultat réellement obtenu ;
4. tests exécutés (ou raison pour laquelle ils ne l'ont pas été) ;
5. limites et prochaines étapes.


## 2026-11-02 — Première correction de l'interface de commandes NES

### Objectif
Appliquer le retour utilisateur sur la manette tactile : une disposition propre à la NES, sans boutons X/Y visibles, avec START et SELECT dans la barre supérieure.

### Fichier modifié
- `app/src/main/java/fr/retroengine/EmulatorActivity.kt`

### Modifications effectuées
- Déplacement des commandes tactiles `SELECT` et `START` dans la barre supérieure, à côté du statut et des commandes de sauvegarde.
- Suppression des boutons tactiles `X` et `Y` de l'écran NES.
- Conservation des deux boutons d'action NES `B` et `A` dans la zone inférieure droite.
- Conservation de la croix directionnelle dans la zone inférieure gauche.
- Les bits d'entrée et le mappage des événements de manette physique n'ont pas été modifiés.

### Vérification et limites
- Modification enregistrée sur `main` dans le commit `8d70c20d84778e076aa9d31180e1c900d636d110`.
- Le workflow Android déclenché par ce commit doit encore confirmer la compilation et les tests.
- Aucun test visuel ni test de manipulation sur téléphone n'a été effectué ici.
- Cette modification ne corrige pas encore le problème signalé précédemment concernant l'image du jeu qui ne s'affiche pas ; ce problème nécessite un diagnostic distinct sur appareil avec des journaux d'exécution.


## 2026-11-02 — Synchronisation du cycle de vie de la surface vidéo native

### Objectif
Sécuriser l'accès à la fenêtre Android native lorsque le thread d'émulation dessine une image pendant qu'Android signale un changement ou une destruction de surface.

### Fichiers modifiés
- `app/src/main/jni/retro_frontend.cpp`
- `docs/PROJECT_LOG.md`

### Modifications effectuées
- Ajout d'un mutex dédié à la fenêtre `ANativeWindow`.
- Protection de l'accès et du rendu vidéo dans `drawFrame`.
- Protection du remplacement de la fenêtre dans `nativeStart` et `nativeSetSurface`.
- Protection de la libération de la fenêtre dans `nativeStop`.
- Le verrou est conservé pendant le dessin et la publication du buffer afin d'empêcher qu'une fenêtre soit libérée pendant son utilisation.

### Vérification et limites
- Modification enregistrée dans un commit unique afin que le journal et le code restent synchronisés.
- Les tests unitaires du workflow précédent ont réussi ; la compilation native et la création de l'APK de cette nouvelle modification doivent être vérifiées par le workflow déclenché par ce commit.
- Aucun test sur téléphone n'a été effectué.
- Cette correction élimine une condition de concurrence possible entre le rendu et le cycle de vie de la surface ; elle ne prouve pas à elle seule que la cause de l'écran noir est résolue.
- Prochaine étape : vérifier la compilation complète, puis ajouter des diagnostics vidéo visibles/logués si l'image ne s'affiche toujours pas sur appareil.


## 2026-11-02 — Repositionnement des commandes NES et croix directionnelle

### Retour utilisateur
- La croix directionnelle n'était pas perçue comme complète : l'utilisateur ne voyait que haut, bas et gauche.
- START et SELECT doivent être groupés au centre de la partie supérieure de l'écran.

### Fichier modifié
- `app/src/main/java/fr/retroengine/EmulatorActivity.kt`

### Modifications effectuées
- Reconstruction de la croix tactile en trois rangées formant explicitement un « + » : haut au-dessus, gauche et droite sur la rangée centrale, bas en dessous.
- Ajout explicite et maintien du bouton directionnel droit avec le bit `RIGHT`.
- Création d'un groupe SELECT/START centré horizontalement dans la barre supérieure.
- Conservation du statut à gauche et des commandes SAUVER/CHARGER à droite.
- Conservation des boutons d'action B et A en bas à droite.

### Vérification et limites
- La modification du code et cette entrée de journal sont enregistrées ensemble sur `main`.
- Le workflow du commit précédent `01e31a39832284bd0a0d37c640695aeba0a9b99a` a réussi : tests unitaires, compilation du cœur Nestopia ARM64, compilation du frontend natif, assemblage APK et vérification des bibliothèques/licences.
- Le nouveau workflow doit vérifier cette modification d'interface.
- Aucun test visuel sur téléphone n'a encore été effectué.
- Le problème d'écran noir n'est pas déclaré résolu ; le correctif de synchronisation de surface est compilé, mais seul un essai sur appareil permettra de confirmer le rendu réel.


## 2026-11-02 — Instrumentation du rendu vidéo NES

### Objectif
Identifier la cause de l'écran noir persistant sans présenter une hypothèse comme une correction confirmée.

### Fichiers modifiés
- `app/src/main/jni/retro_frontend.cpp`
- `docs/PROJECT_LOG.md`

### Modifications
- Ajout de journaux limités aux cinq premiers appels de la boucle d'émulation et du callback vidéo.
- Enregistrement des dimensions de l'image, du pitch, du format de pixels et du format/dimensions de la surface Android.
- Journalisation des échecs de verrouillage de la fenêtre native.
- Journalisation des étapes d'initialisation du cœur et du chargement de la ROM.

### Vérification et limites
- Le code est enregistré sur `main`; le workflow de ce commit doit encore confirmer compilation et assemblage APK.
- Aucun essai sur téléphone ni journal Logcat de l'appareil n'est disponible à cette étape.
- L'écran noir reste non résolu. Les traces doivent permettre de distinguer absence de callback vidéo, boucle inactive et échec de publication de la surface.

## 2026-11-02 — Diagnostic vidéo visible et prise en charge des formats de surface Android

### Objectif
Rendre le diagnostic de l'écran noir exploitable directement depuis le téléphone et éviter de supposer que le buffer de la surface Android est toujours en RGB565.

### Fichiers modifiés
- `app/src/main/jni/retro_frontend.cpp`
- `app/src/main/java/fr/retroengine/EmulatorActivity.kt`
- `docs/PROJECT_LOG.md`

### Modifications
- Le rendu vérifie désormais le format réel du buffer Android après verrouillage.
- Ajout de chemins d'écriture distincts pour les surfaces RGB565, RGBA8888 et RGBX8888, en tenant compte du stride réel.
- Ajout de compteurs pour les callbacks vidéo, les images émulateur, les échecs de verrouillage, les échecs de publication et les formats de surface non pris en charge.
- Enregistrement des dimensions, du pitch, du format de pixels source, du format et des dimensions du buffer Android, ainsi que du résultat de configuration de la géométrie.
- Ajout d'un diagnostic compact visible dans la barre supérieure de l'émulateur, actualisé environ une fois par seconde.

### Vérification et limites
- La modification vise à diagnostiquer les différences de format de buffer entre appareils et à prendre en charge les formats Android courants.
- Le workflow CI doit confirmer la compilation C++/Kotlin et l'assemblage APK.
- Aucun essai sur téléphone n'a encore été réalisé ; l'écran noir n'est donc pas déclaré résolu.
- Si l'image reste noire, les compteurs visibles permettront de distinguer l'absence de callbacks, un échec d'accès à la surface ou un échec de publication.

### Étape suivante
Installer l'APK CI sur le téléphone, lancer une ROM NES et relever les compteurs affichés dans la barre supérieure avant de poursuivre les ajustements des manettes des autres consoles.


## 2026-11-02 — Correction du nom du symbole AV libretro

### Retour utilisateur
- Le téléphone affiche : « Symbole libretro manquant : retro_get_av_info ».

### Cause identifiée
- Le chargeur JNI construisait le nom exporté à partir de `LOAD(get_av_info)`, ce qui recherchait littéralement `retro_get_av_info`.
- L'API libretro déclare et exporte `retro_get_system_av_info`. Le nom abrégé utilisé par le frontend ne correspondait donc pas au symbole du cœur Nestopia.

### Fichiers modifiés
- `app/src/main/jni/retro_frontend.cpp`
- `docs/PROJECT_LOG.md`

### Correction
- Renommage du pointeur de fonction interne en `get_system_av_info`.
- Le chargeur recherche désormais `retro_get_system_av_info`, puis appelle cette fonction pour lire la fréquence vidéo et audio.

### Vérification et limites
- La correction du nom est enregistrée dans le code source ; elle ne prouve pas encore que le cœur se lance sur le téléphone.
- Le workflow CI doit confirmer la compilation native et l'assemblage APK.
- Après installation, vérifier que le message « symbole libretro manquant » a disparu, puis confirmer l'affichage et le son d'une ROM NES.


## 2026-11-02 — Préparation de la version de test V2.3

### Objectifs utilisateur
- Supprimer les informations techniques affichées en haut à gauche pendant le jeu.
- Supprimer les commandes manuelles « SAUVER » et « CHARGER ».
- Conserver les sauvegardes de jeu dans le stockage privé de Retro Engine.
- Corriger la manette tactile NES : quatre directions identiques, alignées et avec des zones tactiles de même taille.
- Garder uniquement A/B en bas à droite et SELECT/START centrés en haut.

### Fichiers modifiés
- `app/src/main/java/fr/retroengine/EmulatorActivity.kt`
- `app/build.gradle`

### Modifications enregistrées
- Barre supérieure réduite au groupe SELECT/START centré ; le panneau de diagnostic visible a été retiré.
- Croix directionnelle reconstruite avec quatre boutons de 50 × 50 dp et un espacement central symétrique.
- Boutons A et B définis à 58 × 58 dp.
- SELECT et START définis à 76 × 42 dp.
- Suppression des boutons tactiles de sauvegarde/chargement d'état.
- Conservation de la sauvegarde SRAM automatique lors de la mise en pause, dans le répertoire privé `filesDir/saves`.
- Numéro de version Android réglé sur `2.3` (versionCode 3).

### Vérification et limites
- Modifications enregistrées sur la branche `v2.3-touch-controls`.
- La compilation Android, les tests automatisés et l'inspection de l'APK restent à exécuter par le workflow CI.
- Aucun test tactile réel sur téléphone n'a encore été effectué ; l'efficacité des zones tactiles doit être confirmée après installation.
- Les sauvegardes SRAM restent dans les données privées de l'application, pas dans le fichier APK lui-même. La désinstallation de l'application peut supprimer ces données.
- Le retrait des commandes manuelles ne garantit pas à lui seul la persistance d'une sauvegarde d'état instantanée : la sauvegarde automatique existante concerne la SRAM du jeu.

### Étape suivante
Ouvrir une pull request vers `main` pour déclencher les tests et la compilation de l'APK V2.3, puis installer cette version sur téléphone pour valider les commandes tactiles et la persistance des sauvegardes.



## 2026-11-02 — Ajustements V2.3 après retour sur capture d’écran

### Version suivie
- Version Android du projet : **2.3** (versionCode 3).
- Branche de travail : `v2.3-touch-controls`.
- Ces corrections restent dans le cycle de test V2.3 ; elles ne constituent pas une version publiée ni validée sur téléphone.

### Retours utilisateur
- Des bandes noires sont visibles autour de l’image de jeu.
- Les barres système Android (heure, batterie, notifications et navigation) restent affichées pendant le jeu.
- Certaines commandes ne répondent pas, particulièrement avec une manette physique.
- La croix directionnelle n’a pas un aspect uniforme ; la touche bas paraît notamment tronquée.

### Fichier modifié
- `app/src/main/java/fr/retroengine/EmulatorActivity.kt`

### Modifications enregistrées
- Activation du mode immersif Android pour masquer les barres d’état et de navigation pendant l’émulation ; réapplication lorsque la fenêtre reprend le focus.
- Agrandissement de la zone de commandes inférieure afin que les trois rangées de la croix directionnelle disposent de la hauteur nécessaire.
- Alignement des cellules de la croix sur une grille régulière ; l’espace central est dimensionné pour correspondre aux boutons et à leurs marges.
- Séparation des entrées tactiles et des entrées physiques afin qu’un relâchement sur un périphérique ne neutralise pas une touche encore maintenue sur l’autre.
- Suivi des touches physiques simultanées par code de touche.
- Ajout de la lecture des axes de joystick et du chapeau directionnel (HAT) pour les manettes qui transmettent les directions sous forme d’axes.
- Interception des événements de touches au niveau de l’activité pour améliorer leur réception même lorsque la surface de jeu a le focus.

### À propos des bandes noires
- La capture montre des marges noires latérales autour de l’image NES. Le rendu actuel conserve le rapport largeur/hauteur de l’image source ; ces marges évitent de déformer l’image sur un écran de téléphone très large.
- Leur suppression complète nécessiterait d’étirer ou de recadrer l’image. Aucun étirement ni recadrage n’a été appliqué dans cette intervention afin de préserver l’image du jeu.

### Vérification et limites
- Les changements sont enregistrés sur la branche `v2.3-touch-controls`.
- La compilation CI doit confirmer que les modifications Kotlin sont valides et que l’APK peut être assemblé.
- Aucun test avec une manette physique n’a été effectué dans cet environnement.
- Le masquage des barres système et l’alignement visuel doivent être contrôlés sur le téléphone après installation.
- La réception des axes analogiques dépend des événements réellement émis par la manette Android ; plusieurs modèles de manettes devront être testés.
- La version reste **V2.3** tant que ce cycle de corrections et de validation n’est pas terminé.

### Prochaine vérification
1. Compiler l’APK V2.3.
2. Tester les quatre directions, A/B, SELECT et START sur écran tactile.
3. Tester une manette physique : croix, stick analogique, boutons et combinaisons simultanées.
4. Vérifier que les barres Android restent masquées pendant le jeu.
5. Confirmer si les marges noires latérales doivent être conservées (image non déformée) ou si l’utilisateur préfère un affichage étiré.
