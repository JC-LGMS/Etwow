# Etwow

Application Android compagnon de la trottinette électrique ETWOW : connexion Bluetooth
automatique, tableau de bord, commandes, widget et phare automatique.

Dépôt : <https://github.com/JC-LGMS/Etwow> — `AGENTS.md` fixe les règles de travail et les pièges
connus.

## Fonctionnalités

- **Connexion automatique** en Bluetooth Low Energy dès que la trottinette s'allume, via un
  service de premier plan (scan filtré par nom/service, reconnexion avec temporisation).
- **Deux protocoles** : trames historiques `55 …` (E-TWOW / GTSport) et trames `AA 55 …` de
  20 octets des modèles récents (BR2262e-s / TK / ET-WOW), détectées automatiquement.
- **Tableau de bord** : vitesse, batterie, distance depuis l'allumage (si le firmware l'envoie),
  odomètre total.
- **Commandes** : verrouillage/déverrouillage (uniquement à l'arrêt), phare, klaxon, zero start,
  choix de la limite de vitesse (6, 20, 25 ou 42 km/h) et, sur les modèles récents, mode de
  conduite (WALK/ECO/DRIVE/SPORT).
- **Widget** : 1×1, logo et **point** vert quand la limite est à **25 km/h** (rouge sinon, gris hors
  ligne) ; un appui passe à 25 km/h, et hors ligne il reconnecte puis applique 25 km/h.
- **Phare automatique** : allumé 30 min avant le coucher, éteint 30 min après le lever, calculé
  localement à partir de la position ; priorité à une action manuelle jusqu'à la prochaine
  transition.
- **Trois écrans** : tableau de bord et commandes, *Réglages*, *Informations techniques* (état
  décodé, trames brutes horodatées, liaison).
- **Mode simulateur** : indispensable sur l'émulateur, qui n'a pas de Bluetooth.

## Compatibilité

Le protocole public connu vise les **E-TWOW GT SE / SL / GTS** (projet non officiel
`simonrey1/etwowconnect2`). Les UUID, trames et formules sont documentés dans `AGENTS.md` comme
**piste à confirmer** sur la trottinette réelle : le modèle exact et le firmware ne sont pas encore
connus. L'application officielle E-TWOW Connect expose les mêmes fonctions de base (limite,
phare, verrouillage).

## Chaîne d'outils

| Élément | Version |
|---|---|
| JDK | 17 |
| Gradle | 9.9.1 |
| AGP | 9.5.1 (Kotlin 2.2.10 **fourni par AGP**) |
| compileSdk / targetSdk | 37 |
| minSdk | 26 |
| build-tools | 36.0.0 |
| Compose BOM | 2026.09.00 (Material3) |

Les chemins exacts des outils sur cette machine sont dans `AGENTS.md` (`gradle` n'est pas dans le
`PATH`).

## Structure prévue

```text
Etwow/
├─ app/
│  ├─ build.gradle.kts          # version auto, signature release, nom de l'APK, R8
│  ├─ proguard-rules.pro
│  └─ src/
│     ├─ main/
│     │  ├─ java/<package>/
│     │  │  ├─ MainActivity.kt
│     │  │  ├─ ble/             # transport BLE + simulateur
│     │  │  ├─ protocol/        # trames Etwow (pur, testable)
│     │  │  ├─ service/         # service de premier plan, auto-connexion, phare auto
│     │  │  ├─ solar/           # lever/coucher du soleil (pur)
│     │  │  ├─ data/            # état + réglages
│     │  │  ├─ ui/              # écrans Compose
│     │  │  └─ widget/          # widget RemoteViews
│     │  └─ res/                # manifeste, thèmes, layout du widget
│     └─ test/                  # tests unitaires JVM
├─ build.gradle.kts
├─ settings.gradle.kts
├─ gradle.properties
├─ local.properties             # non versionné
├─ version.properties           # versionCode, versionName, sourceHash (versionné)
├─ keystore.properties          # non versionné
└─ etwow-release.keystore       # non versionné — à sauvegarder
```

Le package / `applicationId` n'est pas encore fixé ; le consigner ici et dans `AGENTS.md` dès
qu'il est choisi.

## Dépendances prévues

- Compose BOM, `androidx.compose.material3:material3`, `androidx.compose.ui:ui`,
  `activity-compose`, `core-ktx`, `lifecycle-viewmodel-compose`, `kotlinx-coroutines-android`.
- Le Bluetooth (`android.bluetooth`), la localisation (`LocationManager`), les réglages
  (`SharedPreferences`) et le widget (`RemoteViews`) viennent du framework : **aucune bibliothèque
  BLE, Glance, Room ou réseau** n'est nécessaire au démarrage.
- JUnit 4 pour les tests unitaires.

## Build

```powershell
$env:JAVA_HOME = "$env:LOCALAPPDATA\Temp\opencode\tools\jdk-17.0.21+9"
$g   = "$env:USERPROFILE\.gradle\wrapper\dists\gradle-9.9.1-bin\dol8b552jwt4yhd7ihm3iahwb\gradle-9.9.1\bin\gradle.bat"
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"

& $g :app:assembleDebug --console=plain
& $g :app:testDebugUnitTest --console=plain
& $g :app:assembleRelease --console=plain
```

APK de release : `app/build/outputs/apk/release/Etwow-<version>-release-<date>.apk`, signé et
optimisé par R8.

## Version et journal

- La version affichée dans l'application est `v0.x` ; elle avance **une fois par évolution
  terminée**, pas à chaque compilation.
- `version.properties` est la seule source de vérité ; un incrément non commité est conservé.
- Chaque version a son entrée de journal (heure + puces courtes), écrite **avant la dernière
  compilation** et commitée avec les sources et `version.properties`.
- Un commit = une version. Commit et push uniquement sur demande explicite.

## Vérification

« Ça compile » n'est pas une vérification. Sur l'émulateur : mode simulateur, puis contrôle à
l'écran du tableau de bord, des commandes, du widget, du phare automatique et des infos
techniques. Sur la trottinette réelle : reconnexion après coupure d'alimentation, valeurs
comparées à l'afficheur, verrouillage à l'arrêt, phare et limite de vitesse. La séquence `adb` et
la liste de contrôle sont dans `AGENTS.md`.

## Limites connues

- L'émulateur n'a pas de Bluetooth : le simulateur est le seul moyen d'y vérifier l'application.
- La connexion automatique dépend des permissions, du mode d'économie de batterie et du constructeur
  Android ; elle ne peut pas être garantie à 100 % en arrière-plan.
- Le protocole est non officiel : l'odomètre et le trip de certains firmwares peuvent utiliser un
  autre ordre d'octets, à vérifier avant de figer les formules.
- Le mode 42 km/h est réservé à un usage privé ; la limite légale en France est 25 km/h.
- Tension, courant et température ne seront affichés que si une trame les expose réellement ; le
  protocole public connu ne les documente pas.
