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

**Phase 0 — socle Android :** projet Gradle, activité Android, import local par sélecteur de fichiers, détection des consoles par extension et tests unitaires.

**Pas encore inclus :** cœurs d'émulation, exécution des jeux, options graphiques avancées, profils de manettes, génération et signature d'APK autonomes par ROM et historique. Ces fonctions seront intégrées par étapes et ne seront pas simulées.

L'image officielle retro_engine_logo.png doit être ajoutée aux ressources Android dès que le fichier binaire peut être transféré dans le dépôt. Elle ne sera pas utilisée comme icône des jeux exportés.

## Compilation

Le workflow GitHub Actions exécute les tests et produit app/build/outputs/apk/debug/app-debug.apk. L'APK est publié comme artefact de workflow après une exécution réussie.


## Première étape NES

L'import des fichiers .nes effectue maintenant une inspection de l'en-tête iNES :
- signature du fichier et présence des 16 octets d'en-tête ;
- identification iNES / NES 2.0 ;
- lecture du mapper et des tailles PRG-ROM / CHR-ROM ;
- vérification de la taille minimale pour iNES classique, avec prise en compte du trainer.

Limites actuelles : le décodage avancé des tailles NES 2.0 reste à compléter. Cette inspection ne lance pas de jeu. Le cœur d'émulation Nestopia (GPL-2.0) est à intégrer dans une étape distincte, avec ses obligations de licence et de fourniture du code source correspondant.
