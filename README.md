# Retro Engine

Retro Engine est un atelier Android destiné à préparer des applications de jeu autonomes à partir de ROM compatibles.

## Cahier des charges suivi

- Architecture modulaire avec des moteurs séparés pour NES, SNES, Game Boy, Game Boy Color, Game Boy Advance, Mega Drive / Genesis et Nintendo 64.
- Import local de ROM, identification du format, validation et affichage des informations.
- Personnalisation indépendante du nom et de l'icône de chaque jeu exporté.
- Icône Retro Engine réservée à l'application de création, sans remplacer les icônes des jeux exportés.
- Commandes tactiles configurables, prise en charge des manettes Android et masquage des commandes tactiles lorsqu'une manette utilisable est active.
- Options graphiques proposées uniquement lorsqu'elles sont prises en charge par le moteur concerné.
- Mode local pour importer et préparer les projets ; compilation d'APK dans un environnement de build connecté.
- Pas de faux état de compilation, pas de faux boutons et pas de jeu annoncé comme fonctionnel avant validation réelle.
- Pas de compte, de publicité ni d'analytique activés par défaut.
- Android compileSdk 36 et targetSdk 36.

## État actuel

**Socle Android :** projet Gradle, activité Android, import local par sélecteur de fichiers, détection des consoles par extension et tests unitaires.

**Première étape NES :** inspection de l'en-tête iNES, détection iNES / NES 2.0, lecture du mapper et des tailles PRG-ROM / CHR-ROM. Le décodage avancé des tailles NES 2.0 reste à compléter.

**Intégration native en cours :** le workflow compile le cœur Nestopia libretro pour Android arm64-v8a depuis une révision source figée, puis le place dans l'APK avec sa notice GPL. Le binaire natif est ainsi construit et empaqueté ; il n'est pas encore relié à un frontend Android et ne lance donc pas encore de jeu.

Révision Nestopia utilisée : `f1e02d12664834536bfed7352995a3d0cd1caef4` du dépôt [libretro/nestopia](https://github.com/libretro/nestopia). Le cœur est sous licence GPL-2.0-or-later ; la notice de licence est intégrée à l'APK et l'archive de la source correspondante est publiée avec l'artefact de compilation.

## Compilation

Le workflow GitHub Actions exécute les tests, compile le cœur natif Nestopia, construit l'APK debug et vérifie que la bibliothèque native ainsi que sa notice de licence sont présentes dans l'APK. Les artefacts comprennent l'APK, la bibliothèque native et l'archive de la source correspondante.

## Fonctions non encore disponibles

L'exécution des jeux dans l'application, l'interface frontend libretro, les options graphiques avancées, les profils de manettes, la génération et la signature d'APK autonomes par ROM et l'historique restent à intégrer et à tester. Ces fonctions ne sont pas simulées.

L'image officielle `retro_engine_logo.png` doit encore être ajoutée aux ressources Android dès que le fichier binaire peut être transféré dans le dépôt. Elle ne sera pas utilisée comme icône des jeux exportés.
