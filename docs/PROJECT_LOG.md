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
