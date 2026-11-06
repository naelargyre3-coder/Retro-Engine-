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


### Test utilisateur — sauvegarde de The Legend of Zelda (2 novembre 2026)
- L'utilisateur confirme que la sauvegarde fonctionne avec **The Legend of Zelda (NES)** dans Retro Engine.
- Ce résultat valide le fonctionnement de la sauvegarde SRAM pour ce jeu dans l'environnement testé.
- Cela ne prouve pas encore que tous les jeux NES sauvegardent correctement : le comportement dépend du système de sauvegarde de chaque cartouche.


## 2026-11-02 — Correction de l'arrière-plan et de l'occupation de l'écran du menu

### Retour utilisateur
- La capture du menu d'accueil montre une grande zone blanche sous le contenu sombre.
- L'utilisateur souhaite que le menu occupe proprement l'écran et que cet espace blanc disparaisse.

### Fichier modifié
- `app/src/main/java/fr/retroengine/MainActivity.kt`

### Modifications enregistrées
- Le conteneur `ScrollView` reçoit maintenant explicitement le même fond sombre que le menu.
- Activation de `isFillViewport` pour que le contenu puisse occuper toute la hauteur disponible lorsque l'écran est plus grand que le menu.
- Ajout d'un espace flexible et d'un pied de page discret « RETRO ENGINE • VERSION EXPÉRIMENTALE » afin de répartir proprement la hauteur restante sans ajouter de fausse fonctionnalité.
- Le bouton d'import, les informations du moteur NES et le parcours de sélection de ROM sont conservés.

### Vérification et limites
- Correction enregistrée sur la branche `v2.3-touch-controls` dans le commit associé au menu.
- La compilation Android et l'assemblage APK doivent encore confirmer la validité de cette modification.
- Aucun test visuel sur téléphone n'a encore été réalisé après ce changement ; l'utilisateur devra vérifier que le fond sombre couvre bien toute la zone située sous le menu.
- Cette correction ne change pas le fonctionnement de l'import de ROM ni le moteur d'émulation.


## 2026-11-02 — Léger agrandissement de l'image NES en jeu

### Clarification utilisateur
- L'utilisateur souhaite que l'image du jeu NES occupe un peu plus de place à l'écran pendant l'émulation, plutôt que de modifier uniquement le menu principal.

### Fichier modifié
- `app/src/main/jni/retro_frontend.cpp`

### Modifications enregistrées
- Le facteur d'échelle conserve le calcul d'ajustement à l'écran, puis applique un zoom centré de **8 %**.
- Le rendu échantillonne désormais l'image source pour chaque pixel de la surface Android ; les bords sont recadrés de façon symétrique lorsque nécessaire.
- Les proportions de l'image ne sont pas étirées : l'agrandissement réduit les marges noires au prix d'un léger recadrage des bords.
- Les formats de surface RGB565 et RGBA/RGBX8888 conservent leurs chemins de conversion existants.

### Vérification et limites
- Modification enregistrée sur la branche `v2.3-touch-controls`.
- Le workflow Android doit encore confirmer la compilation C++ native et l'assemblage de l'APK.
- Aucun test visuel sur téléphone n'a encore été effectué ; il faudra vérifier que le zoom de 8 % est confortable et ne coupe aucun élément important du jeu.
- Si le recadrage est trop important ou trop faible sur l'écran de l'utilisateur, le facteur de zoom pourra être ajusté.


## 2026-11-02 — Correction du remplissage vidéo et viewport 16:9

### Objectif
Corriger les bandes d'image étirées observées sur téléphone et appliquer le choix utilisateur 16:9 sans déformer les pixels.

### Fichiers modifiés
- `app/src/main/jni/retro_frontend.cpp`
- `docs/PROJECT_LOG.md`

### Modifications
- Remplacement du zoom arbitraire de 1,08 par un viewport 16:9 centré dans la surface Android.
- Mise à l'échelle uniforme de type « cover » : l'image remplit le viewport 16:9 et le surplus est recadré, sans étirement.
- Suppression du comportement fautif qui ramenait les coordonnées hors image aux pixels du bord avec `std::clamp`, ce qui étirait les rangées et colonnes de bord sur les bandes restantes.
- Les zones hors viewport restent noires ; le format source et le stride de la surface sont conservés.
- Ajout d'une validation du pitch source avant lecture des pixels.

### Vérification et limites
- Modification enregistrée sur la branche `v2.3-touch-controls`.
- La compilation, les tests automatisés et l'assemblage APK doivent encore être confirmés par le workflow CI.
- Aucun test visuel sur téléphone n'a encore été réalisé après cette correction.
- Le rendu 16:9 est appliqué par défaut dans ce correctif. Le choix utilisateur 4:3/16:9 dans les paramètres et la sélection d'orientation portrait/paysage/automatique restent à implémenter ; ils ne sont pas déclarés disponibles.


## 2026-11-02 — Ajustement du cadrage : image entière sans zoom

### Retour utilisateur
Le rendu 16:9 précédent recadrait l'image (mise à l'échelle « cover »), ce qui produisait un zoom visuel non souhaité.

### Fichier modifié
- `app/src/main/jni/retro_frontend.cpp`

### Correction
- Passage de `std::max` à `std::min` pour utiliser une mise à l'échelle « contain » dans le viewport 16:9.
- L'image complète du jeu est désormais conservée, sans recadrage ni étirement.
- Les espaces non occupés par l'image restent noirs. Le viewport 16:9 reste centré dans la surface Android.
- Le correctif précédent de suppression de l'étirement des pixels de bord est conservé.

### Vérification et limites
- Correction enregistrée sur la branche `v2.3-touch-controls`.
- Compilation CI à vérifier après ce commit.
- Test visuel sur téléphone requis pour confirmer le cadrage réel.
- Le réglage interactif 4:3/16:9 et les modes portrait/paysage/automatique ne sont pas encore implémentés.


## 2026-11-02 — Ajout du choix de ratio 4:3 / 16:9 et correction du rapport de pixels NES

### Objectif
Ajouter un vrai réglage sélectionnable depuis le menu et corriger l'image NES trop étroite observée sur la capture utilisateur.

### Fichiers modifiés
- `app/src/main/java/fr/retroengine/MainActivity.kt`
- `app/src/main/java/fr/retroengine/EmulatorActivity.kt`
- `app/src/main/jni/retro_frontend.cpp`
- `docs/PROJECT_LOG.md`

### Modifications
- Ajout dans le menu d'un choix radio **4:3 / 16:9**, mémorisé localement dans les préférences de l'application (valeur initiale : 16:9).
- Transmission du choix à l'activité d'émulation puis au frontend natif JNI au démarrage du jeu.
- Calcul du viewport natif en fonction du choix 4:3 ou 16:9, au lieu d'imposer 16:9 sans réglage.
- Correction du rapport de pixels NES : une image source typique de 256×240 est affichée au rapport de présentation 4:3, au lieu d'être traitée comme une image carrée/1,067:1.
- En mode 16:9, l'image NES 4:3 complète reste centrée dans le viewport 16:9 ; les zones restantes sont noires. Aucun recadrage, zoom ou étirement n'est appliqué à l'image du jeu.

### Vérification et limites
- Modifications enregistrées sur la branche `v2.3-touch-controls`.
- La compilation Android et native doit être confirmée par le workflow CI ; elle n'est pas déclarée réussie à ce stade.
- Aucun test visuel sur téléphone n'a encore été effectué après cette modification.
- Sur un écran physique 20:9, des marges peuvent subsister autour du viewport 16:9 ; elles sont attendues pour conserver le ratio sélectionné. Le choix 4:3 utilise un viewport plus étroit adapté au format classique des jeux NES.
- Le choix du ratio est appliqué au lancement du jeu ; pour changer le format, revenir au menu, sélectionner l'autre ratio et relancer le jeu.


## 2026-11-02 — Suppression des bandes noires en mode 16:9

### Retour utilisateur
- L'utilisateur confirme que les zones entourées sur la capture correspondent bien aux bandes noires à supprimer.

### Fichiers modifiés
- `app/src/main/jni/retro_frontend.cpp`
- `docs/PROJECT_LOG.md`

### Correction
- En mode 16:9, le rendu utilise maintenant une mise à l'échelle uniforme de type « cover » calculée sur toute la surface Android, au lieu de placer l'image 4:3 dans un viewport plus petit.
- L'image remplit donc la surface sans bandes noires dues au cadrage ; les parties excédentaires sont recadrées au centre, sans étirement.
- Le mode 4:3 conserve l'image entière avec son rapport d'origine ; des marges peuvent rester dans ce mode lorsque la surface n'est pas au format 4:3.
- Les conversions RGB565 et RGBA/RGBX8888, le stride et la validation du pitch source sont conservés.

### Vérification et limites
- Le changement de code est enregistré sur la branche `v2.3-touch-controls`.
- La compilation native et l'assemblage APK doivent encore être vérifiés par le workflow CI.
- Aucun test visuel sur téléphone n'a été réalisé après cette modification ; la suppression effective des bandes doit être confirmée après installation.
- En mode 16:9, le recadrage central peut couper une partie de l'image NES, mais les proportions restent correctes.

### Étape suivante
- Compiler l'APK V2.3, l'installer sur le téléphone et vérifier que l'image remplit l'écran sans bandes noires gênantes.

## 2026-11-02 — Suppression des bandes sombres derrière les commandes virtuelles

### Retour utilisateur
- L'utilisateur soupçonne que les bandes visibles proviennent des commandes tactiles.

### Cause confirmée dans le code
- La barre supérieure SELECT/START et le conteneur inférieur de la manette étaient des vues superposées à la surface vidéo.
- Ces deux conteneurs appliquaient un fond noir semi-transparent sur toute la largeur, pouvant ressembler à des bordures.
- La surface vidéo elle-même restait dimensionnée sur toute la zone de jeu ; les commandes ne réduisaient pas sa taille.

### Fichier modifié
- `app/src/main/java/fr/retroengine/EmulatorActivity.kt`

### Correction enregistrée
- Remplacement des fonds noirs semi-transparents des deux conteneurs par des fonds transparents.
- Conservation des boutons tactiles individuels, de leurs zones de contact et de leur mappage d'entrée.
- Le choix du ratio 16:9 et le rendu natif ne sont pas modifiés.

### Vérification et limites
- Modification enregistrée sur la branche `v2.3-touch-controls`.
- La compilation CI et l'assemblage de l'APK restent à vérifier.
- Aucun test visuel sur téléphone n'a encore été réalisé ; il faudra confirmer que les bandes sombres ont disparu sans gêner la visibilité des boutons.


## 2026-11-02 — Retour au cadre 16:9 centré

### Clarification utilisateur
- L'utilisateur ne souhaite pas que l'image remplisse tout l'écran.
- Il souhaite un cadre de jeu 16:9, centré, avec l'image complète et sans déformation.

### Fichier modifié
- `app/src/main/jni/retro_frontend.cpp`

### Modification
- Le mode 16:9 calcule désormais un rectangle 16:9 centré dans la surface Android.
- L'image NES au rapport de présentation 4:3 est ajustée en mode « contain » à l'intérieur de ce cadre.
- Aucun recadrage ni étirement ; les espaces autour de l'image et à l'extérieur du cadre restent noirs.
- Le mode 4:3 conserve son ajustement avec l'image entière.

### Vérification et limites
- Modification enregistrée sur la branche `v2.3-touch-controls`.
- Compilation CI, assemblage APK et contrôle visuel sur téléphone à effectuer.
- Aucun résultat de test sur appareil n'est revendiqué.


## 2026-11-02 — Vérification CI du cadre 16:9 centré

### Contrôle du comportement
- Relecture de `drawFrame` dans `app/src/main/jni/retro_frontend.cpp` : le mode 16:9 calcule un viewport 16:9 centré dans la surface Android.
- L'image NES est mise à l'échelle uniformément en mode « contain » à l'intérieur de ce viewport, en conservant le rapport de présentation 4:3.
- Le buffer est initialisé en noir avant le dessin ; les zones non occupées restent donc noires.
- Les conteneurs des commandes tactiles restent transparents dans `EmulatorActivity.kt`, sans modifier le rendu vidéo.

### Vérification automatisée
- Workflow Android GitHub Actions n°78, commit testé : `7dbe2faacdf66982fea301455043c474043acd5f`.
- Résultat : **succès**.
- Étapes confirmées : tests unitaires, compilation du cœur Nestopia arm64-v8a, compilation du frontend natif arm64-v8a, assemblage de l'APK debug et vérification des bibliothèques natives et licences.
- Artefact APK téléversé par le workflow.

### Limites restantes
- La CI confirme la compilation et les contrôles automatisés, pas le rendu visuel sur un téléphone.
- Le cadre 16:9 est un viewport logique : il n'est pas dessiné comme un contour décoratif. L'image 4:3 est centrée à l'intérieur, avec des marges noires latérales.
- Le test sur appareil reste nécessaire pour confirmer le résultat réel sur l'écran cible.


## 2026-11-05 — Première intégration de l'overlay tactile NES

### Référence consultée
- Cahier des charges et historique relus dans `docs/PROJECT_LOG.md`.
- Branche de travail : `v2.3-touch-controls`.
- L'intégration conserve le masque d'entrées natif Nestopia et la séparation existante entre entrées tactiles et matérielles.

### Fichiers créés ou modifiés
- `app/src/main/java/fr/retroengine/ControllerLayout.kt` — configuration NES normalisée, indépendante de la taille d'écran, avec positions distinctes selon l'orientation.
- `app/src/main/java/fr/retroengine/VirtualControllerView.kt` — dessin semi-transparent, D-pad et suivi multi-pointeur, diagonales, zones tactiles élargies, vibration courte.
- `app/src/main/java/fr/retroengine/EmulatorActivity.kt` — remplacement des anciens widgets tactiles par la vue overlay superposée à la surface vidéo.
- `app/src/main/AndroidManifest.xml` — déclaration de la permission Android VIBRATE.

### Comportement intégré
- Overlay NES : D-pad à gauche, B à gauche de A à droite, SELECT et START au centre en bas.
- Opacité initiale : 50 %, enregistrée dans les préférences locales du jeu.
- Positions relatives adaptées au mode portrait et paysage.
- Le suivi des identifiants de pointeurs permet de maintenir plusieurs commandes simultanément.
- Le masque combiné continue d'être transmis par le mécanisme d'entrée existant.
- Aucun bouton menu, mode déplacement ou bouton de réinitialisation n'est ajouté, conformément aux dernières décisions utilisateur.

### Vérification et limites
- Les fichiers ont été enregistrés sur la branche `v2.3-touch-controls`.
- La compilation CI déclenchée par les commits doit encore être consultée ; aucune réussite de compilation n'est revendiquée ici.
- Aucun essai tactile sur téléphone n'a été réalisé dans cette intervention.
- L'interface de réglage de l'opacité/vibration, le curseur de taille, la détection Bluetooth et la persistance des réglages par APK restent à intégrer.
- Les layouts SNES, Game Boy, Game Boy Color, GBA, Mega Drive et N64 restent à créer.
- Le réglage portrait/paysage utilise actuellement deux géométries intégrées au layout ; la restauration Android et la rotation réelle doivent être testées sur appareil.
- Le code de sauvegarde SRAM existant n'a pas été remplacé. L'isolation des données par APK généré devra être assurée lors du chantier de génération autonome des APK.

### Prochaine étape
Vérifier le workflow GitHub Actions de cette branche, corriger toute erreur de compilation, puis tester les directions, diagonales, boutons simultanés, vibration et orientation sur téléphone avant d'étendre l'architecture aux autres consoles.


## 2026-11-05 — Agrandissement et rapprochement visuel de la maquette NES

### Retour utilisateur
- La première APK installée compile et se télécharge correctement.
- Les commandes tactiles sont trop petites et leur apparence ne correspond pas à la maquette.

### Fichiers modifiés
- `app/src/main/java/fr/retroengine/ControllerLayout.kt`
- `app/src/main/java/fr/retroengine/VirtualControllerView.kt`
- `docs/PROJECT_LOG.md`

### Ajustements réalisés
- Agrandissement des boutons A/B (rayon visuel porté d'environ 0,065 à 0,110 de la dimension de référence en paysage).
- Augmentation de l'écartement entre A et B pour conserver une séparation confortable.
- Agrandissement de SELECT/START.
- Croix directionnelle élargie et zones tactiles directionnelles étendues.
- Boutons d'action redessinés en carrés arrondis avec contour clair plus marqué, au lieu des simples cercles de la première version.

### Vérification et limites
- Le workflow précédent n°82 a réussi (compilation APK confirmée par GitHub Actions).
- Ces nouveaux ajustements doivent encore être compilés par le prochain workflow.
- Aucun test visuel sur téléphone n'a encore été effectué pour cette nouvelle apparence.
- Le retour exact à la maquette reste à affiner à partir d'une capture de l'APK installée et, si nécessaire, de la maquette de référence.


## 2026-11-05 — Reprise visuelle de la manette NES selon la maquette

### Retour utilisateur
- Les commandes précédentes ne ressemblaient pas à la maquette et leur agrandissement excessif ne convenait pas.
- Le plan de reprise visuelle a été approuvé : D-pad gris en relief, boutons A/B rouges circulaires, SELECT/START regroupés en haut au centre, sans L/R sur le layout NES.

### Fichiers modifiés
- `app/src/main/java/fr/retroengine/ControllerLayout.kt`
- `app/src/main/java/fr/retroengine/VirtualControllerView.kt`
- `docs/PROJECT_LOG.md`

### Corrections effectuées
- Suppression des boutons A/B carrés arrondis et retour à deux boutons circulaires rouges, de diamètre réduit et mieux proportionné.
- Ajout d'un contour lumineux discret, d'une ombre et d'un reflet pour retrouver l'aspect bombé de boutons physiques.
- Refonte de la croix directionnelle : branches anthracite, biseau clair, ombre portée et repères directionnels.
- Déplacement de SELECT et START dans un petit groupe centré en haut de l'écran.
- Conservation des zones tactiles plus larges que les dessins, du multitouch, des diagonales, de la vibration réglable et de l'opacité initiale de 50 %.
- Aucun bouton L/R, menu, mode déplacement ou réinitialisation n'a été ajouté.

### Vérification et limites
- Les changements sont enregistrés dans ce commit sur `v2.3-touch-controls`.
- La compilation et l'assemblage APK doivent être confirmés par le workflow GitHub Actions déclenché par ce commit.
- Aucun test tactile ou contrôle visuel sur téléphone n'a été effectué ici.
- Les réglages d'opacité/vibration sont conservés dans les préférences existantes ; un panneau de réglage utilisateur, la taille réglable et la détection Bluetooth restent des tâches distinctes non déclarées comme terminées.


## 2026-11-05 — Amélioration des textures de la manette tactile NES

### Clarification utilisateur
- La demande concerne le **rendu des textures**, et non la taille ni la disposition des commandes.
- La référence visuelle attendue est la capture fournie : boutons rouges brillants, D-pad gris métallique en relief et commandes système à finition métallique.
- Le cahier des charges reste prioritaire : la manette NES affiche uniquement la croix, A, B, START et SELECT. Aucun bouton L/R n'est ajouté à la NES.

### Fichier modifié
- `app/src/main/java/fr/retroengine/VirtualControllerView.kt`

### Modifications enregistrées
- Boutons A/B : coque sombre, bague métallique dégradée, face rouge en plusieurs nuances, reflet radial, éclat supérieur et contour intérieur.
- SELECT/START : finition gris acier, coque en dégradé, surface satinée, liseré clair et ombrage inférieur.
- D-pad : branches anthracite avec dégradés directionnels, biseaux, relief, ombre portée et gravures directionnelles.
- Le dessin conserve les dimensions, coordonnées, zones tactiles, masques d'entrée et commandes définis auparavant.
- L'opacité initiale reste à 50 %. Le multitouch, les diagonales et la vibration ne sont pas modifiés.

### Vérification et limites
- Modification enregistrée sur la branche `v2.3-touch-controls`, commit `1d95c7115d44dbc7e0f0ef86485ed939a3118a68`.
- La compilation Kotlin/Android et l'assemblage APK doivent encore être confirmés par GitHub Actions.
- Aucun rendu de l'APK n'a été inspecté sur téléphone après ce changement ; la ressemblance finale avec la capture reste à confirmer visuellement.
- Aucun test tactile sur appareil n'a été effectué dans cette intervention.

## 2026-11-05 — Repositionnement de SELECT et START en bas au centre

### Retour utilisateur
- SELECT et START doivent être placés en bas au centre de l'écran, et non en haut.

### Fichier modifié
- `app/src/main/java/fr/retroengine/ControllerLayout.kt`

### Modification enregistrée
- La coordonnée verticale normalisée des deux boutons système passe de 0,105 à 0,91, en portrait comme en paysage.
- Les positions horizontales centrées, dimensions, zones tactiles et masques d'entrée restent inchangés.

### Vérification et limites
- Modification enregistrée sur `v2.3-touch-controls`.
- La compilation doit être vérifiée par GitHub Actions.
- Le remplacement des dégradés par les textures bitmap directement extraites de la capture reste à réaliser ; la conformité visuelle complète n'est donc pas encore atteinte.

## 2026-11-05 — Intégration des textures bitmap de la maquette NES

### Objectif
- Remplacer les dégradés dessinés en Kotlin par les images de texture extraites de la capture fournie.
- Conserver la géométrie des commandes et garder SELECT/START en bas au centre.

### Fichiers modifiés
- `app/src/main/java/fr/retroengine/VirtualControllerView.kt`
- `app/src/main/res/drawable-nodpi/nes_controller_atlas.webp`
- `docs/PROJECT_LOG.md`

### Modifications
- Intégration d'un atlas WebP avec transparence, contenant le D-pad anthracite en relief, les boutons rouges A/B et les capsules métalliques SELECT/START de la maquette.
- Le rendu utilise directement les régions bitmap d'origine au lieu de reconstruire les matériaux avec des dégradés.
- L'atlas est embarqué dans les ressources de l'application : aucun téléchargement n'est nécessaire pendant l'émulation.
- Les coordonnées et dimensions de la disposition sont conservées ; SELECT et START restent à la position basse centrale définie précédemment.
- Les zones tactiles, masques d'entrée, diagonales, multitouch, vibration et opacité par défaut de 50 % restent inchangés.

### Vérification et limites
- Modification enregistrée sur `v2.3-touch-controls`.
- La compilation et les tests automatisés doivent être confirmés par le workflow GitHub Actions.
- Aucun test visuel sur téléphone n'a été réalisé ici ; la validation finale du rendu reste à faire après installation.


## 2026-11-05 — Recréation native des textures NES (sans image copiée)

### Retour utilisateur et correction de méthode
- L'intégration précédente était incorrecte : elle réutilisait les pixels découpés dans la capture de référence au lieu de recréer les textures.
- La demande porte sur des touches virtuelles dessinées dans l'application, et non sur la génération d'une image.

### Fichiers modifiés
- `app/src/main/java/fr/retroengine/VirtualControllerView.kt`
- `app/src/main/res/drawable-nodpi/nes_controller_atlas.webp` (suppression de la ressource copiée)
- `docs/PROJECT_LOG.md`

### Modifications
- Suppression de la dépendance à l'atlas bitmap extrait de la capture.
- Reconstruction native avec Android Canvas et dégradés : croix directionnelle anthracite en relief avec branches séparées et flèches gravées ; boutons A/B rouges bombés avec bague sombre, reflets et libellés ; SELECT/START en capsules gris acier avec biseau et surface satinée.
- Chaque touche est dessinée indépendamment à l'exécution. Aucun pixel de la capture n'est utilisé comme texture.
- Géométrie, positions, zones tactiles, entrées, diagonales, multitouch, vibration et opacité restent inchangés.

### Vérification et limites
- Changement de code préparé pour la branche `v2.3-touch-controls`.
- La compilation Kotlin/Android et les tests doivent être vérifiés par GitHub Actions après le commit.
- Aucun rendu sur téléphone n'est revendiqué ; la ressemblance exacte doit être évaluée sur l'appareil après installation.


## 2026-11-05 — Passage des touches NES en pixel-art natif

### Objectif
- Appliquer le style pixel-art demandé pour toutes les commandes virtuelles NES, sans reprendre de pixels d'une capture ou d'une image externe.

### Fichiers modifiés
- `app/src/main/java/fr/retroengine/VirtualControllerView.kt`
- `docs/PROJECT_LOG.md`

### Modifications
- Remplacement des dégradés continus et des contours lissés par des formes en blocs et des bandes de couleurs à bords francs.
- Croix directionnelle anthracite reconstruite en rectangles pixelisés, avec relief clair/sombre et repères directionnels carrés.
- Boutons A/B rouges en marches d'escalier, avec reflets rectangulaires et lettrage monospace.
- SELECT/START gris acier avec coins en escalier, liserés rectangulaires et lettrage monospace.
- Aucun changement apporté aux positions, dimensions, zones tactiles, diagonales, multitouch, vibration ou opacité.

### Vérification et limites
- Code enregistré sur la branche `v2.3-touch-controls`.
- Le workflow GitHub Actions doit confirmer la compilation Kotlin/Android et l'assemblage APK.
- Aucun aperçu de l'APK ni test sur téléphone n'est disponible à cette étape ; le rendu pixel-art final reste à valider sur appareil.


## 2026-11-05 — Annulation du rendu pixel-art et retour au style lisse de la maquette NES

### Clarification utilisateur
- L'utilisateur confirme qu'il ne souhaite pas de rendu pixel-art pour les commandes.
- La maquette de référence montre des boutons 3D lisses : D-pad gris métallique, boutons A/B rouges brillants et capsules SELECT/START gris acier.

### Fichiers modifiés
- `app/src/main/java/fr/retroengine/VirtualControllerView.kt`
- `docs/PROJECT_LOG.md`

### Modifications
- Suppression du dessin en rectangles et bandes pixelisées.
- Retour à un rendu Android Canvas anti-crénelé avec dégradés linéaires/radiaux, ombres, biseaux, reflets et contours arrondis.
- A/B sont dessinés comme des boutons circulaires rouges bombés avec bague métallique et lettrage blanc.
- SELECT/START sont dessinés comme des capsules gris foncé avec double contour acier et texte clair.
- La croix directionnelle est reconstruite avec des branches arrondies gris métallique, un moyeu central et des repères directionnels.
- Les positions, dimensions, zones tactiles, masques d'entrée, diagonales, multitouch, vibration et opacité par défaut de 50 % sont conservés.

### Vérification et limites
- Modification enregistrée sur la branche `v2.3-touch-controls`.
- La compilation Kotlin/Android et l'assemblage APK doivent être contrôlés par GitHub Actions.
- Aucun test visuel ni test tactile sur téléphone n'a été réalisé dans cette intervention.
- Le rendu est une reconstruction native à partir de la maquette ; il ne réutilise aucun pixel extrait de la capture.



## 2026-11-06 — Recalage géométrique de l'overlay NES sur la maquette

### Retour utilisateur et référence
- La capture de l'APK confirme un écart majeur avec le panneau NES de `retro_engine_maquette_v2.png`.
- La comparaison porte sur les proportions et les coordonnées relatives au panneau complet, pas uniquement sur la couleur ou les effets.

### Fichiers modifiés
- `app/src/main/java/fr/retroengine/ControllerLayout.kt`
- `app/src/main/java/fr/retroengine/VirtualControllerView.kt`
- `docs/PROJECT_LOG.md`

### Corrections apportées
- En paysage, repositionnement du D-pad à 22 % de la largeur et 67 % de la hauteur, conformément à la maquette.
- Recalage des boutons B et A à 77,7 % et 92,8 % de la largeur, et à 73 % de la hauteur.
- Repositionnement de SELECT/START à 44,5 % et 63,5 % de la largeur, en bas à 94,5 % de la hauteur.
- Révision des dimensions de dessin en paysage pour retrouver l'échelle relative du panneau de référence ; les réglages portrait sont conservés.
- Remplacement des repères directionnels trop clairs par des repères sombres et discrets, plus proches des creux de la maquette.
- Les masques d'entrée, zones tactiles, diagonales, multitouch, vibration et opacité par défaut restent inchangés.

### Vérification et limites
- Modification du code enregistrée sur la branche de test ; la compilation CI doit encore être vérifiée.
- Aucun test visuel sur appareil n'a été effectué après cette modification.
- La fidélité finale des matériaux et du rendu à 50 % d'opacité devra être contrôlée sur la prochaine capture de l'APK.


### Complément de recalage
- Le D-pad est étiré horizontalement selon le rapport du panneau de référence (530 × 290) et celui de l'écran paysage, tout en conservant des branches visuellement équilibrées.
- La zone de détection directionnelle a été élargie pour couvrir le nouveau dessin ; elle reste indépendante du rendu.
