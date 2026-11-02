# Cahier des charges — Retro Engine

> Document de référence fonctionnelle et technique du projet. Toute évolution doit respecter ces exigences. Une fonctionnalité ne doit être annoncée comme disponible que si elle est réellement intégrée et vérifiée.

## 1. Vision et objectif

Retro Engine est une application Android modulaire permettant à l'utilisateur d'importer une ROM qu'il est autorisé à utiliser, de la configurer, puis de produire une application Android autonome contenant le moteur d'émulation correspondant.

L'application doit distinguer clairement :
- l'application **Retro Engine**, qui sert à préparer et gérer les projets ;
- chaque **jeu exporté**, qui possède son propre APK, son nom, son icône et sa configuration.

Aucune compilation, signature, compatibilité, fonction ou vérification ne doit être simulée. Les états affichés doivent correspondre à des opérations réellement exécutées.

## 2. Plateformes visées et moteurs

Consoles prévues :
- Nintendo Entertainment System (NES) ;
- Super Nintendo (SNES) ;
- Game Boy et Game Boy Color (GB/GBC) ;
- Game Boy Advance (GBA) ;
- Mega Drive / Genesis ;
- Nintendo 64 (N64), avec un moteur dédié.

Architecture modulaire : chaque système doit avoir son détecteur, ses règles de validation, son moteur, ses paramètres, ses commandes et ses tests propres.

Une console ne doit être déclarée « prise en charge » que lorsque son moteur est intégré, compilé et testé avec un lancement de jeu réel. La Nintendo 64 doit utiliser un moteur dédié et ne doit pas être présentée comme une simple extension du moteur d'une autre console.

## 3. Parcours utilisateur principal

1. Créer ou ouvrir un projet.
2. Importer une ROM depuis le stockage Android au moyen du sélecteur de fichiers.
3. Détecter le système et analyser le format de la ROM.
4. Afficher un résultat de validation explicite : valide, invalide, incomplet ou non pris en charge.
5. Configurer le jeu : nom, icône, orientation, format d'image, commandes et options graphiques.
6. Vérifier les exigences propres au moteur et les éventuels fichiers système requis.
7. Choisir le mode de sortie : génération d'APK via serveur ou export du projet source.
8. Compiler, signer si demandé, vérifier le paquet et afficher les résultats réels.
9. Télécharger l'APK ou exporter les sources.
10. Retrouver le projet et son historique dans la bibliothèque locale.

## 4. Écrans et interface

L'interface doit adopter une identité rétro soignée et cohérente, tout en restant lisible et adaptée aux écrans Android.

Écrans prévus :
- bibliothèque des projets ;
- importation et analyse de ROM ;
- personnalisation du jeu ;
- émulation ;
- réglages graphiques ;
- configuration des commandes tactiles ;
- configuration et test des manettes ;
- configuration de génération APK ;
- progression de compilation ;
- résultat, vérification et téléchargement ;
- historique ;
- paramètres et confidentialité.

L'interface doit s'adapter au portrait et au paysage. Les écrans et contrôles doivent rester utilisables sur différentes tailles d'appareils.

## 5. Importation et validation des ROM

- Import local via le sélecteur Android, sans demander un accès général inutile au stockage.
- Détection du système à partir de l'extension et, lorsque possible, de la structure interne du fichier.
- Validation du format, de l'en-tête, de la taille et des incohérences connues.
- Messages d'erreur compréhensibles et précis.
- Ne jamais modifier la ROM source sans action explicite de l'utilisateur.
- L'utilisateur est responsable de fournir des ROM qu'il possède ou qu'il est légalement autorisé à utiliser.

La simple reconnaissance d'une extension ne suffit pas à déclarer une ROM valide.

## 6. Personnalisation du jeu exporté

L'utilisateur doit pouvoir définir :
- le nom affiché du jeu ;
- l'icône propre au jeu exporté ;
- l'orientation ;
- le ratio d'affichage ;
- la disposition et la taille des commandes tactiles ;
- les options graphiques réellement prises en charge.

L'icône de l'application **Retro Engine** est indépendante des icônes des jeux générés. Le logo Retro Engine ne doit pas être utilisé automatiquement comme icône des jeux exportés.

## 7. Génération d'APK et export des sources

### Mode serveur
La génération distante doit exécuter réellement :
- le transfert sécurisé des éléments nécessaires ;
- la validation des paramètres et des fichiers ;
- la préparation du projet Android correspondant au système ;
- la compilation ;
- la signature avec une configuration clairement expliquée ;
- la vérification du résultat ;
- la mise à disposition du fichier APK.

L'application doit afficher les étapes, les erreurs et le résultat du travail réellement effectué. Elle ne doit jamais annoncer une compilation réussie si aucun APK n'a été produit et contrôlé.

### Mode export des sources
L'utilisateur doit pouvoir exporter un projet source exploitable, avec les fichiers nécessaires, les instructions de compilation et les mentions de licences applicables.

### Distinction hors ligne / en ligne
L'importation, l'analyse et la configuration locale doivent rester utilisables hors ligne lorsque les fonctions concernées ne nécessitent pas de serveur. La compilation distante nécessite une connexion et doit être signalée comme telle. L'interface doit distinguer clairement ces deux modes.

## 8. Moteurs d'émulation

Chaque moteur doit être intégré avec une interface commune mais conserver ses paramètres spécifiques. L'intégration doit inclure :
- chargement et déchargement propre du cœur ;
- cycle d'exécution ;
- rendu vidéo ;
- sortie audio ;
- gestion des entrées ;
- gestion des sauvegardes lorsque le moteur les expose ;
- gestion du cycle de vie Android ;
- remontée d'erreurs exploitable.

Le lancement réel d'un jeu compatible est un critère obligatoire avant de déclarer un moteur prêt.

## 9. BIOS et fichiers système

- Certains moteurs peuvent nécessiter des BIOS ou fichiers système.
- Ces fichiers doivent être fournis par l'utilisateur lorsqu'ils ne peuvent pas être redistribués légalement.
- L'application doit expliquer quels fichiers sont nécessaires, comment leur présence est vérifiée et où ils sont utilisés.
- Aucun BIOS propriétaire ne doit être intégré au dépôt ou distribué sans autorisation.
- Un démarrage original de console ne peut être proposé que si le moteur le prend réellement en charge.

## 10. Affichage et filtres graphiques

Options visées :
- rendu pixel / original ;
- filtre CRT et lignes de balayage ;
- filtres 2xBR et 4xBR lorsque le moteur les prend effectivement en charge ;
- shaders spécifiques à la N64 uniquement si le moteur choisi les supporte.

Chaque option affichée doit être réellement fonctionnelle pour le moteur actif. Les options incompatibles doivent être désactivées ou expliquées, et non simulées.

Formats d'image visés : 4:3 et 16:9, dans la limite des possibilités du système et du jeu. L'orientation doit pouvoir être réglée sur portrait, paysage ou automatique lorsque cela est techniquement possible.

## 11. Commandes tactiles

Les commandes doivent être personnalisables par système : position, taille et disposition.

Disposition minimale visée :
- NES : croix directionnelle, A, B, Start et Select ;
- GB/GBC : croix directionnelle, A, B, Start et Select ;
- SNES, GBA, Mega Drive et N64 : disposition correspondant aux boutons réels de chaque système et aux possibilités du moteur.

Les boutons inutiles à un système ne doivent pas être affichés. Par exemple, une manette NES ne doit pas afficher de boutons X et Y. Les commandes doivent être lisibles, accessibles et correctement transmises au moteur.

## 12. Manettes physiques

- Prise en charge des manettes Android compatibles, notamment Bluetooth et USB lorsque le système les expose.
- Détection de connexion et déconnexion.
- Configuration et remappage des boutons.
- Profils par système ou par projet.
- Écran de test montrant les entrées reçues.
- Gestion cohérente des axes, croix directionnelle et boutons.

## 13. Masquage intelligent des commandes tactiles

Lorsque l'application détecte une manette physique compatible et utilisable, elle doit pouvoir masquer les commandes tactiles selon les préférences de l'utilisateur. En l'absence de manette utilisable, les commandes tactiles doivent rester disponibles.

La détection doit tenir compte des connexions et déconnexions pendant une session, et ne pas masquer les commandes sur la seule base d'un périphérique non compatible.

## 14. Configuration et compatibilité APK

Avant la génération, afficher un état de compatibilité fondé sur des vérifications réelles :
- système et moteur sélectionnés ;
- format et validité de la ROM ;
- fichiers système requis ;
- options graphiques et contrôles disponibles ;
- architecture Android ciblée ;
- éléments nécessaires à la compilation et à la signature.

Les avertissements et incompatibilités doivent être visibles avant de lancer le travail.

## 15. Sécurité Android

- Cible Android SDK 36 ou supérieure, selon la compatibilité des dépendances.
- Java 17 pour la chaîne de compilation actuelle.
- Permissions minimales nécessaires.
- Pas de compte obligatoire, de publicité ou d'analytique par défaut.
- Signature APK correcte et vérification du paquet produit.
- Ne pas revendiquer de certification, de sécurité absolue ou de compatibilité universelle sans preuve.
- Les clés de signature et secrets de build ne doivent pas être exposés dans le dépôt.

## 16. Confidentialité et données

- Les ROM et configurations locales restent sur l'appareil tant qu'un transfert distant n'est pas explicitement requis.
- Expliquer quels fichiers sont envoyés au serveur lors d'une compilation distante, dans quel but et pendant combien de temps ils sont conservés.
- Ne pas transférer de fichiers sans action explicite de l'utilisateur.
- Fournir une politique claire de suppression des projets et fichiers temporaires côté serveur.
- Pas de télémétrie ou d'analytique par défaut.

## 17. Bibliothèque et historique local

L'utilisateur doit pouvoir retrouver ses projets, leur configuration, les dates d'opération, le système associé et l'état des dernières générations.

L'historique ne doit pas inventer de résultats. Les échecs et interruptions doivent être conservés comme tels.

## 18. Architecture et licences

- Architecture modulaire, testable et maintenable.
- Séparation entre interface, logique métier, détection/validation, moteurs, génération et stockage.
- Chaque moteur doit être accompagné de sa licence, de sa révision source et des notices requises.
- Respecter les obligations de redistribution, attribution, publication du code source et notices de chaque dépendance.
- Documenter les versions et révisions utilisées pour pouvoir reproduire les builds.

## 19. Phases de développement

1. Socle Android, navigation et bibliothèque locale.
2. Importation, détection et validation des ROM.
3. Premier moteur complet et vérifié (NES).
4. Commandes tactiles et manettes spécifiques à chaque système.
5. Sauvegardes et options graphiques réellement prises en charge.
6. Intégration progressive des moteurs SNES, GB/GBC, GBA et Mega Drive.
7. Intégration dédiée N64.
8. Personnalisation complète des projets exportés.
9. Génération APK distante, signature et vérification.
10. Export des sources, confidentialité, tests de bout en bout et stabilisation.

Les phases peuvent évoluer, mais les dépendances techniques et les critères d'acceptation doivent rester explicites.

## 20. Critères d'acceptation

Une version ne peut être considérée comme prête que si les points applicables ci-dessous sont vérifiés :

1. Une ROM peut être importée localement sans permission de stockage excessive.
2. Le système est détecté correctement et les formats invalides sont rejetés avec un message explicite.
3. Les systèmes non intégrés sont identifiés comme non pris en charge.
4. Un jeu de test légalement fourni démarre réellement sur chaque moteur déclaré disponible.
5. L'image du jeu et le son sont effectivement rendus.
6. Les entrées tactiles correspondent aux boutons du système émulé.
7. Les manettes compatibles sont détectées et leurs entrées sont remappables.
8. Les commandes tactiles ne sont masquées que lorsqu'une manette utilisable est disponible et selon le réglage choisi.
9. Les sauvegardes annoncées fonctionnent après fermeture et réouverture, sur un appareil de test.
10. Les options graphiques annoncées modifient réellement le rendu.
11. Le nom et l'icône personnalisés sont présents dans l'APK du jeu exporté.
12. L'APK est réellement compilé et signé selon le mode choisi.
13. Le paquet généré est inspecté et sa signature vérifiée.
14. Les fichiers et notices de licences sont présents conformément aux obligations applicables.
15. Les tests automatisés et les tests de lancement documentés passent ; les limites restantes sont publiées.

## 21. Règles de suivi du projet

- Ce document est la référence fonctionnelle du projet.
- Toute modification doit être accompagnée d'une entrée dans `docs/PROJECT_LOG.md`.
- Chaque entrée doit préciser la date, l'objectif, les fichiers touchés, le comportement ajouté ou corrigé, les tests exécutés et leurs résultats.
- Distinguer explicitement « implémenté », « compilé », « testé sur appareil » et « à faire ».
- Ne jamais transformer une intention ou une tâche planifiée en fonctionnalité terminée.
- Mettre à jour le README lorsque l'état général ou les instructions de build changent.
