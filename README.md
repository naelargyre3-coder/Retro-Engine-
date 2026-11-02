# Retro Engine

Retro Engine est un atelier Android destiné à préparer des applications de jeu autonomes à partir de ROM compatibles.

## Objectif du projet

- Importer localement une ROM, identifier sa console et contrôler son format.
- Permettre la personnalisation du nom et de l'icône de chaque jeu exporté.
- Générer des APK autonomes uniquement après une compilation et une vérification réelles.
- Garder l'icône Retro Engine séparée des icônes des jeux exportés.
- Proposer des commandes tactiles et des manettes Android.
- Ajouter progressivement NES, SNES, Game Boy / Color, Game Boy Advance, Mega Drive / Genesis et Nintendo 64.
- Respecter les licences des moteurs et ne pas intégrer de ROM ou BIOS propriétaires.
- Ne pas afficher de faux états de compilation ou de fausses fonctions.

## État d'avancement

**Socle Android :** projet Gradle, import local par sélecteur de fichiers, détection des consoles et tests unitaires.

**Inspection NES :** lecture de l'en-tête iNES, détection iNES / NES 2.0, lecture du mapper et des tailles PRG-ROM / CHR-ROM. Le décodage étendu NES 2.0 reste à compléter.

**Cœur NES :** le workflow compile Nestopia libretro pour Android arm64-v8a depuis une révision source figée et intègre sa notice GPL.

**Frontend libretro Android — première implémentation :** une bibliothèque JNI distincte charge dynamiquement Nestopia, fournit les callbacks libretro vidéo/audio/entrée, lit une ROM depuis un fichier local, dessine les images dans une Surface Android et transmet le son à AudioTrack. L'écran d'émulation ajoute des commandes tactiles et accepte les événements de manettes/claviers Android.

Cette première implémentation est expérimentale. Le build vérifie la compilation et la présence des deux bibliothèques natives dans l'APK, mais cela ne remplace pas un test d'exécution sur appareil. La sauvegarde SRAM NES est chargée depuis un fichier privé propre à chaque ROM et réécrite à la mise en pause et à l’arrêt du moteur. Deux commandes permettent également de créer et restaurer un état de sauvegarde libretro, stocké séparément pour chaque ROM. Ces fonctions doivent encore être validées sur un téléphone. La cible native actuelle est arm64-v8a uniquement.

## Compilation

GitHub Actions exécute les tests unitaires, compile Nestopia, compile le frontend Android, construit l'APK debug et vérifie la présence des deux bibliothèques natives et de la licence. Les artefacts incluent l'APK, les bibliothèques et l'archive de la source Nestopia correspondante.

## Limites restantes

- Pas encore de génération d'APK autonome par ROM.
- Pas encore d'interface de personnalisation de jeu.
- Sauvegardes SRAM automatiques et états de sauvegarde manuels intégrés ; validation sur appareil encore nécessaire.
- Pas encore de filtres graphiques.
- Pas encore de moteurs intégrés pour les autres consoles.
- Pas encore de signature de production ni de pipeline de publication.
- Le logo officiel `retro_engine_logo.png` n'est pas encore intégré au dépôt. Le fichier source doit être importé avant de générer les ressources d'icône. Il s'agit uniquement de l'icône de Retro Engine ; les jeux exportés garderont leurs propres icônes personnalisables.

## Suivi du développement

Le journal des interventions, vérifications, changements réellement enregistrés et tâches bloquées est disponible dans [docs/PROJECT_LOG.md](docs/PROJECT_LOG.md).

## Licences

Nestopia est intégré selon sa licence GPL, avec la notice et la révision source fournies dans les artefacts. Les déclarations API libretro de `libretro_minimal.h` sont dérivées de l'en-tête officiel [libretro.h](https://github.com/libretro/RetroArch/blob/master/libretro-common/include/libretro.h), distribué sous licence MIT.
