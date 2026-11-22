# Etwow — notes pour les agents

Application Android Kotlin/Compose `com.etwow`, dépôt public
`https://github.com/JC-LGMS/Etwow` (branche `main`). Ce fichier fixe le périmètre, les règles et
les pièges connus ; `README.md` décrit le produit et le build.

## Périmètre fonctionnel

Application Android compagnon d'une trottinette électrique ETWOW connectée en BLE :

- connexion automatique dès que la trottinette s'allume (service de premier plan) ;
- tableau de bord : vitesse, batterie, distance depuis l'allumage, odomètre total ;
- commandes : verrouillage/déverrouillage, phare, limite de vitesse ;
- widget 1×1 : logo + coche verte à 25 km/h (rouge sinon), un appui passe à 25 km/h ;
- phare automatique 30 min avant le coucher et 30 min après le lever (calcul local, override manuel) ;
- informations techniques : état décodé, trames brutes, liaison BLE ;
- mode simulateur pour vérifier sur l'émulateur, qui n'a pas de Bluetooth.

Hors périmètre tant que non demandé : zero start, réglages constructeur, mise à jour firmware,
historique de trajets, comptes ou cloud.

## Cible technique

- Application Android Kotlin + Jetpack Compose, Material3.
- `compileSdk` / `targetSdk` 37, `minSdk` 26, JDK 17, build-tools 36.0.0.
- Gradle 9.9.1, AGP 9.5.1, **Kotlin 2.2.10 fourni par AGP** (built-in), Compose BOM 2026.09.00.
- Plugins : `com.android.application` + `org.jetbrains.kotlin.plugin.compose` uniquement.
  **Ne jamais appliquer `org.jetbrains.kotlin.android`** : conflit avec le Kotlin intégré à AGP.
  Pas de bloc `kotlinOptions` ni `composeOptions` (inutiles avec AGP 9 ; `buildFeatures.compose =
  true` suffit).
- Package / `applicationId` : à fixer avant le premier build, puis consigner ici et dans
  `README.md`.

## Chaîne d'outils (spécifique à cette machine)

`gradle` n'est **pas** dans le `PATH`. Le dossier de distribution peut changer de hachage :

```powershell
Get-ChildItem "$env:USERPROFILE\.gradle\wrapper\dists" -Recurse -Filter gradle.bat
```

Chemins vérifiés le 21/11/2026 :

| Outil | Chemin |
|---|---|
| JDK 17 | `%LOCALAPPDATA%\Temp\opencode\tools\jdk-17.0.21+9` |
| Gradle 9.9.1 | `%USERPROFILE%\.gradle\wrapper\dists\gradle-9.9.1-bin\dol8b552jwt4yhd7ihm3iahwb\gradle-9.9.1\bin\gradle.bat` |
| SDK Android | `%LOCALAPPDATA%\Android\Sdk` (plateforme `android-37.0`) |
| adb | `%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe` |
| emulator | `%LOCALAPPDATA%\Android\Sdk\emulator\emulator.exe` (AVD **Pixel_10**, image API 37.2) |
| apksigner | `%LOCALAPPDATA%\Android\Sdk\build-tools\36.0.0\apksigner.bat` |
| keytool | `C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe` |

Préambule avant tout appel Gradle :

```powershell
$env:JAVA_HOME = "$env:LOCALAPPDATA\Temp\opencode\tools\jdk-17.0.21+9"
$g   = "$env:USERPROFILE\.gradle\wrapper\dists\gradle-9.9.1-bin\dol8b552jwt4yhd7ihm3iahwb\gradle-9.9.1\bin\gradle.bat"
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
```

- Prendre **17.0.21+9**, pas `jdk-17.0.20.1+1` : cette copie n'a ni `javac` ni `keytool`.
- `keytool` manque aussi dans 17.0.21 : utiliser celui du JBR d'Android Studio.
- `gradlew.bat` peut n'être qu'un stub sans `gradle-wrapper.jar` : passer par `$g`.
- `--offline` suffit en général (AGP, Kotlin et Compose sont en cache). En cas de dépendance
  nouvelle, relancer **une fois** sans `--offline`.

## Build, installation, vérification

`local.properties` doit contenir `sdk.dir=C:/Users/jc_lg/AppData/Local/Android/Sdk` (non
versionné), sinon Gradle ne trouve pas le SDK.

```powershell
& $g :app:compileDebugKotlin --console=plain   # vérif rapide
& $g :app:assembleDebug       --console=plain
& $g :app:testDebugUnitTest   --console=plain
& $g :app:assembleRelease     --console=plain

& $adb install -r app\build\outputs\apk\debug\app-debug.apk
& $adb shell am force-stop <applicationId>
& $adb shell am start -n <applicationId>/.MainActivity
```

« Ça compile » n'est **pas** une vérification. Installer, lancer, observer l'écran, puis rapporter
ce qui a été **vu** (pas ce qui devrait se passer).

- Capture : `adb shell screencap -p /sdcard/x.png` puis `adb pull /sdcard/x.png`.
- Interface : `adb shell uiautomator dump /sdcard/ui.xml` puis `adb pull`; viser le centre du
  `bounds` avec `adb shell input tap X Y`, puis re-dumper après chaque action. Les libellés
  accentués ressortent mal dans la console, mais les `bounds` sont fiables.
- **Les `bounds` du dump sont déjà en coordonnées écran** : ne rien ajouter. L'activité démarre
  pourtant à `y=142` (barre d'état) d'après `dumpsys window`, ce qui fait croire à un décalage ;
  il n'y en a pas. Contrôle par balayage de pixels de la capture : écart mesuré ≈ 0 px. Ajouter
  `+142` fait cliquer 142 px trop bas, sur le contrôle suivant.
- Le service n'est **pas exporté** : `adb shell am start-foreground-service …` échoue avec
  « Requires permission not exported from uid ». Les actions se testent par l'UI ou le widget,
  pas depuis le shell.
- Presse-papiers après « Copier » : `adb shell dumpsys clipboard` montre l'horodatage de la
  dernière écriture (contenu masqué pour le shell). C'est la preuve d'un `setPrimaryClip`.
- Données : base ou fichiers de l'app via `run-as` (seulement si l'APK installé est debug).
  Les `SharedPreferences` s'écrivent par l'UI ; `run-as … sh -c 'cat > shared_prefs/…'` marche
  mais le fichier est relu au prochain démarrage seulement (le service garde ses valeurs en RAM).
- L'émulateur n'a **pas de Bluetooth** : sans mode simulateur, rien de la partie BLE n'est
  vérifiable à l'écran. Le simulateur alterne 20 s en roulant / 30 s de cycle : attendre jusqu'à
  30 s pour obtenir vitesse 0 et tester le verrouillage.
- Widget : long-appui sur l'accueil (menu) → *Widgets* → onglet *Parcourir* → *Etwow* → puis
  `adb shell input draganddrop <x> <y> <x2> <y2> 2000` depuis l'aperçu. `input swipe` ne fait
  que faire défiler la liste, il ne déplace pas le widget.

## Protocole BLE ETWOW (piste publique, à vérifier)

Source : projet non officiel `https://github.com/simonrey1/etwowconnect2` (Flutter, 2023),
consulté le 21/11/2026. Il vise les **GT SE / SL / GTS**. Le modèle et le firmware de cette
trottinette ne sont pas connus : **valider chaque trame sur la machine réelle avant de figer une
formule**, et garder le protocole isolé dans un codec pur.

### Appareil et caractéristiques

Le nom annoncé contient `E-TWOW` ou `GTSport` :

| Nom | Service | Lecture/notifications | Écriture |
|---|---|---|---|
| `E-TWOW` | `0000ffe0-0000-1000-8000-00805f9b34fb` | `0000ffe1-…` | `0000ffe1-…` |
| `GTSport` | `0000ff00-0000-1000-8000-00805f9b34fb` | `0000ff01-…` | `0000ff02-…` |

Activer les notifications sur la caractéristique de lecture (descripteur CCCD `0x2902`) avant
d'envoyer quoi que ce soit. Écrire **avec réponse** sur Android.

### Trame de commande

`55 <cmd> 05 <arg> <somme>` — la somme est la somme de tous les octets (0x55 inclus) tronquée à
8 bits. Exemple : `55 05 05 01 60` (0x55 + 0x05 + 0x05 + 0x01 = 0x60).

| Action | Trame |
|---|---|
| Verrouiller | `55 05 05 01 60` |
| Déverrouiller | `55 05 05 00 5F` |
| Phare allumé | `55 06 05 01 61` |
| Phare éteint | `55 06 05 00 60` |
| Limite L0 = 42 km/h | `55 02 05 00 5C` |
| Limite L1 = 6 km/h | `55 02 05 01 5D` |
| Limite L2 = 20 km/h | `55 02 05 02 5E` |
| Limite L3 = 25 km/h | `55 02 05 03 5F` |
| Zero start on/off (hors périmètre) | `55 03 05 01 5E` / `55 03 05 00 5D` |
| Init compteur (dangereux, ne pas envoyer) | `55 09 05 00 63` (trip) / `55 09 05 01 64` (odomètre) |
| Change d'unité (km/miles, non utilisé) | `55 08 05 00` / `55 08 05 01` |
| Klaxon, réglage du son (non utilisé) | `55 18 05 <arg> <somme>` — opcode 0x18 |

Source des opcodes : application officielle **E-TWOW 4.4 (XAPK APKPure, `e.twow2`)** décompilée
le 22/11/2026. C'est une application **uni-app** : la logique est dans
`assets/apps/__UNI__4B5207A/www/app-service.js`, constante `deviceBluetooth`
(`lightBluetooth:6, mainSpeedLimitBluetooth:2, lockScooterBluetooth:5,
changeUnitBluetooth:8, zeroStartBluetooth:3, resetKmBluetooth:9, resetTripBluetooth:0,
resetOddoBluetooth:1, hornToneBluetooth:24`). L'app distingue « nouvel appareil » (trames de
20 octets `AA 55 00 0D <opcode> <arg> … XOR 0D`) et « ancien appareil » (trame `55 <opcode> 05 <arg>
<somme>`, `oldBuildValueBase`) : nos scooters sont dans le second cas. Opcodes du nouveau format,
pour mémoire et non utilisés ici : 5/6 clignotants, 8 unité, 9 mode (0=WALK,1=ECO,2=DRIVE,3=SPORT),
11/12/13 lectures d'état, 18/21/22 klaxon et divers.

### Notifications

Le premier octet est le type ; vérifier la longueur **avant** d'indexer : une trame tronquée ne doit
jamais planter le service.

Règles reprises du **parseur de l'application officielle** décompilée, documenté dans
`Laufbursche42/etwow-unlock` (`app.js`, en-tête et section « RX telemetry parser ») : trames de
4 octets, `byte[0]` = type, **valeur int16 petit-boutiste sur les octets 1 et 2**, `byte[3]` =
somme de contrôle (somme des octets précédents).

| Type | Trame réelle (GTS) | Règle |
|---|---|---|
| 1 | `01 00 00 01` | vitesse = int16LE / 10 → 0,0 km/h |
| 2 | `02 64 00 66` | batterie = int16LE brut → 100 % |
| 3 | `03 10 00 13` | `0x80` zero start, `0x20` verrou, `0x10` phare, `& 0x07` cran de limite |
| 4 | `04 67 24 8F` | distance = int16LE / 10 → 0x2467/10 = **931,9 km** (odomètre affiché : 931) |
| 5 | `05 31 32 33 34 9D` | **absent du parseur officiel** (6 octets, « 1234 ») : ignoré |

Conséquences vérifiées sur la machine de test (22/11/2026), confirmées par le code de l'app
officielle 4.4 (`case 4` du parseur « ancien appareil ») :
- le **type 4 est l'odomètre**, pas le trip : sa valeur colle à l'afficheur ;
- le **trip n'est pas transmis** : l'app officielle le **calcule** en cumulant les écarts
  d'odomètre et le persiste (`saved_trip`). C'est ce que fait `ScooterService.updateTrip`, avec la
  valeur en `SharedPreferences` (`savedTrip`) ; un odomètre qui recule = compteur remis à zéro ;
- l'ancienne formule de vitesse `lo + (flag ? 255 : 0)` était fausse au-delà de 25,5 km/h ;
- **polarité du verrou à confirmer** : le code officiel lit `be[2]` (0x20) et l'inverse
  (`lock = bit ? "0" : "1"`). Notre app considère 0x20 = verrouillé ; à vérifier en verrouillant
  sur la machine réelle (l'UI affiche l'état décodé).

Le projet `simonrey1/etwowconnect2` lit les octets un par un (`hi + lo`, `b2+b1+b0`, etc.) : ses
formules sont fausses au-delà de 255 et ne doivent pas servir de référence. Seul le jeu de commandes
`55 <cmd> 05 <arg> <somme>` de ce projet est juste.

Statut (octet 1 du type 3), bits vérifiés sur la machine : `0x80` zero start, `0x20` verrou,
`0x10` phare, bits de poids faible = cran de limite (0 = 42, 1 = 6, 2 = 20, 3 = 25).
Exemples observés : `0x00` = tout éteint, cran 0 ; `0x10` = phare seul.

### Options prises en charge (au-delà de verrou/phare/limite)

| Option | Ancien protocole | Nouveau protocole |
|---|---|---|
| Zero start | `55 03 05 <0|1>` | opcode `0E`, action 0/1 |
| Remise à zéro du trip | `55 09 05 00` | opcode `0F`, action 0 |
| Klaxon | `55 18 05 <ton>` | opcode `12`, action 0 |
| Mode de conduite (WALK/ECO/DRIVE/SPORT) | — (ancien modèle non concerné) | opcode `09`, action 0..3 |
| Unité km/miles | `55 08 05 <0|1>` | opcode `08` — **non implémenté** (changerait tous les affichages) |
| Remise à zéro de l'odomètre | `55 09 05 01` | opcode `0F`, action 1 — **non implémenté** (destructif) |

Le klaxon et la remise à zéro n'attendent pas de confirmation (aucune notification ne la porte) :
`writeOnly` + journal. Le mode de conduite et la limite du nouveau protocole sont appliqués
localement après écriture, faute de retour d'état dans les trames `0B`/`0C`.

### Nouveau protocole (appareils `BR2262e-s*`, `TK*`, `ET-WOW*`)

L'application officielle choisit le format d'après le **nom** de l'appareil
(`checkDevice` → préfixes ci-dessus). Même service FFE0/FF00, mêmes caractéristiques.

- **Commande** : 20 octets `AA 55 00 0D <opcode> <action> 00 ×12 <XOR des octets 0..17> 0D`.
  Opcodes utiles : `01` verrou (1 = verrouiller), `03` phare (1 = allumer), `04` petits feux,
  `08` limite (action 0..3 = 42/6/20/25), `09` mode de conduite (0 WALK, 1 ECO, 2 DRIVE, 3 SPORT),
  `05`/`06` clignotants, `18` klaxon, `0B`/`0C`/`0D` lectures d'état.
- **Notification** : 20 octets, `byte[4]` = opcode, XOR des octets 0..17 en `byte[18]`, `0D` en
  `byte[19]`.
  - opcode `0B` : vitesse (`byte[13]`, km/h entier), batterie (`byte[14]`, %), **trip**
    (`byte[15..16]` big-endian / 10), verrou (`byte[17]` bit0), frein (bit2), tension
    (`byte[5..6]`/100), courant, températures.
  - opcode `0C` : **odomètre** 32 bits big-endian `byte[5..8]` / 10, phare (`byte[11]` 0x04),
    zero start (`byte[11]` 0x80), mode de conduite one-hot dans `byte[10]` (0x08 WALK, 0x10 ECO,
    0x20 DRIVE, 0x04 SPORT), bits d'état batterie/moteur dans `byte[11]`.
- Le nouveau protocole **transmet le trip** : dans ce cas l'application l'affiche tel quel, sans
  cumul d'écarts (le cumul reste le repli pour l'ancien protocole).
- Implémentation : `EtwowProtocol.newFrame/lockNew/lightOnNew/setModeNew`, `isNewFrame` (signature +
  XOR), `decodeNew` (les deux trames sont ramenées à l'octet de statut commun 0x80/0x20/0x10/mode).
  `ScooterService` bascule sur le nouveau format dès que le nom de l'appareil ou la première trame
  le montrent ; le scan accepte les préfixes de noms et la caractéristique `FF04`.
- **Non vérifié sur matériel** : aucun appareil de ce type n'a été testé ; le *simulateur* sait
  parler ce format (`Réglages` → « Simulateur, nouveau protocole » → `TK-SIM`), ce qui permet de
  vérifier décodage, commandes et UI sur l'émulateur.

### Pièges de protocole

- **Ne jamais verrouiller en roulant.** Le projet public refuse le verrouillage si la vitesse
  n'est pas nulle et signale que la trottinette peut s'arrêter brutalement. Désactiver le bouton
  dès que la vitesse est > 0, et ne jamais mettre un verrou en file d'attente.
- **Les notifications sont la vérité**, pas la réponse d'écriture : une écriture réussie ne prouve
  pas que la trottinette a appliqué l'ordre. Mettre à jour l'UI et le widget seulement après la
  notification correspondante (ou afficher « en attente »).
- **Mode 0 = 42 km/h** : usage privé. L'application ne le sélectionne jamais par défaut. La limite
  légale française est 25 km/h. La phrase d'avertissement sous les boutons a été retirée sur
  demande de l'utilisateur le 22/11/2026 : le libellé « 42 km/h (privé) » suffit, ne pas la
  réintroduire sans le lui demander. Les vitesses s'affichent triées (6, 20, 25, 42).
- **Le verrou est un état, pas un bouton momentané** : après reconnexion, relire le statut avant
  d'afficher « verrouillé ».
- **Adresse MAC** : vérifier si elle est stable après extinction/rallumage. Si elle est aléatoire,
  ne jamais se fier à l'adresse enregistrée : scanner par nom/service à chaque reconnexion.
- **Firmware** : les modèles et firmwares peuvent différer. Si une trame ne correspond pas, ne pas
  la forcer : capturer avec l'app officielle (voir plus bas) et mettre à jour le codec.
- **Commandes inconnues** : ne pas envoyer `init compteur` ni de trame non documentée. Tester sur
  béquille, roue décollée, prêt à couper l'alimentation.

## Architecture BLE et service

- `ble/ScooterTransport` : interface (connexion, commandes, flux de notifications, état). Deux
  implémentations : `BleScooterTransport` (Android) et `SimulatedScooterTransport` (émulateur,
  mêmes trames, réponses simulées).
- `protocol/EtwowProtocol` : codec **pur Kotlin**, sans Android : encodage des commandes, somme de
  contrôle, décodage des notifications, modèle `ScooterState`. Toutes les formules douteuses sont
  isolées ici et testées.
- `service/ScooterService` : service de premier plan, propriétaire unique de la connexion GATT,
  auto-connexion, phare automatique, actions du widget, écriture de l'état partagé.
- `data/Settings` : `SharedPreferences` (auto-connexion, phare auto, simulateur, position).
- `solar/SolarTimes` : calcul pur lever/coucher (algorithme NOAA), sans réseau.
- `ui/` : `MainScreen.kt` porte trois écrans dans le même composable (`Page.Main`,
  `Page.Settings`, `Page.Info`) — pas de bibliothèque de navigation : un `remember` + une ligne
  « ← Retour » + `BackHandler`. `widget/` : `RemoteViews`.

### Connexion automatique

- Démarrer le service quand l'application s'ouvre si l'auto-connexion est activée ; un
  `BOOT_COMPLETED` peut le relancer après un redémarrage du téléphone (à vérifier selon l'API 37).
- Scan `BluetoothLeScanner` filtré par nom (`E-TWOW`, `GTSport`) ou UUID de service, avec repli si
  le nom est nul. Au premier résultat, `connectGatt(..., autoConnect = false)` pour aller vite.
- Après déconnexion : `autoConnect = true` sur la dernière adresse **si elle est stable**, sinon
  nouveau scan avec temporisation (1 s, 2 s, 5 s, 10 s, 30 s max). Le statut GATT 133 est courant :
  fermer le GATT et réessayer.
- Une seule instance GATT, une seule opération à la fois (file + mutex + timeout). Android refuse
  une écriture pendant une lecture ou une écriture de descripteur.
- `onConnectionStateChange` → `close()` systématique avant de recréer ; sinon les ressources
  fuient.
- Le service tourne tant que l'auto-connexion est active. L'utilisateur doit mettre l'application
  en batterie « sans restriction » ; ne pas forcer `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`.
- Écouter `BluetoothAdapter.ACTION_STATE_CHANGED` pour reprendre quand le Bluetooth est rallumé.
  Sur API 26–30, le scan BLE exige aussi la localisation activée.

### Permissions (manifeste)

- API 26–30 : `BLUETOOTH`, `BLUETOOTH_ADMIN`, `ACCESS_FINE_LOCATION` (tous avec
  `maxSdkVersion="30"`).
- API 31+ : `BLUETOOTH_SCAN` (`usesPermissionFlags="neverForLocation"`) et `BLUETOOTH_CONNECT`.
- `ACCESS_COARSE_LOCATION` pour le lever/coucher du soleil.
- `POST_NOTIFICATIONS` (API 33+) pour la notification du service.
- `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_CONNECTED_DEVICE`, service déclaré
  `android:foregroundServiceType="connectedDevice"`.
- `uses-feature android:name="android.hardware.bluetooth_le" android:required="false"` pour que
  l'émulateur puisse installer l'APK ; l'app affiche « Bluetooth indisponible » ou le simulateur.
- **Piège vérifié sur un vrai téléphone (22/11/2026, crash au lancement)** : sur targetSdk 34+,
  `startForeground(..., CONNECTED_DEVICE)` exige la permission normale
  `FOREGROUND_SERVICE_CONNECTED_DEVICE` **et au moins une** permission d'exécution parmi
  `BLUETOOTH_CONNECT/SCAN/ADVERTISE`… (`SecurityException` sinon, et le processus meurt).
  Démarrer le service juste après `requestPermissions` (accord asynchrone) plante donc à la
  première ouverture. La correction, à conserver :
  1. `MainActivity` ne démarre le service que si `BLUETOOTH_SCAN` **et** `BLUETOOTH_CONNECT` sont
     déjà accordées, et le démarre dans `onRequestPermissionsResult` après acceptation ;
  2. `BootReceiver` vérifie `BLUETOOTH_CONNECT` avant de démarrer ;
  3. `ScooterService.onCreate` entoure `startForeground` d'un `try/catch (SecurityException)` et
     `stopSelf()` : aucun chemin (widget, réglages, redémarrage) ne doit tuer le processus.
  Reproduction sur l'émulateur : `adb shell pm clear com.etwow` puis lancer l'app — sans
  autorisation accordée, l'ancien code plantait avec la même trace.
- Vérifier les restrictions de démarrage de service de premier plan en arrière-plan (Android 12+)
  et les exigences exactes de l'API 37 avant de conclure.

### Widget 25 km/h

- Widget **1×1** : le logo et un **point** — **vert** à 25 km/h, **rouge** sinon, **gris** hors
  ligne. Pas de texte, pas de bouton.
- `updatePeriodMillis = 0` : le widget est mis à jour par le service à chaque changement d'état,
  pas par un réveil périodique.
- Clic → `PendingIntent.getForegroundService` avec l'action `SET_25` ; le widget ne fait jamais de
  BLE lui-même (durée de vie d'un `BroadcastReceiver` trop courte).
- **Tout le widget est cliquable** et ne fait qu'une chose : passer à 25 km/h. Réappuyer ne change
  rien (plus de retour à la limite précédente, décidé par l'utilisateur le 22/11/2026).
- **Hors ligne, l'appui relance la connexion puis envoie 25 km/h dès qu'elle aboutit**
  (`pending25`) : c'est un ordre différé assumé, contrairement aux autres commandes.

### Phare automatique

- `SolarTimes` calcule lever/coucher pour la date et la position, dans le fuseau du téléphone
  (`ZonedDateTime`, donc changement d'heure géré). Aucun appel réseau.
- Position : `LocationManager.getLastKnownLocation` avec `ACCESS_COARSE_LOCATION` ; si absente,
  réglage manuel de coordonnées ou de ville. Sans position, désactiver le phare auto et le dire.
- Marge de 30 min avant le coucher et après le lever (allumé 30 min avant le coucher, éteint
  30 min après le lever), avec hystérésis : n'envoyer la commande qu'au changement d'état, pas à
  chaque notification.
- Le résumé affiche le lever avant le coucher : « lever 08:09 · coucher 17:04 ».
- Une action manuelle sur le phare suspend l'automatisme **jusqu'à la prochaine transition** ; le
  réglage reste activé. Ne pas se battre avec l'utilisateur.
- Cas polaires (aucun lever/coucher) : ne pas planter, désactiver l'automatisme pour la journée.
- Recalculer au changement de position/fuseau et à la transition ; une coroutine planifiée pendant
  que le service est connecté suffit, pas de WorkManager.
- Rythme réel : `applyAutoLight()` est rappelé à chaque trame de statut (donc à la connexion et à
  chaque changement) **et** par un tick de 60 s. Sans le rappel sur statut, la première application
  attendait une minute après la connexion — visible à l'écran comme un phare qui ne s'allume pas.
- L'override retient la valeur souhaitée au moment du geste manuel ; il ne se lève que quand le
  souhait calculé change (transition), pas au redémarrage du service.

### Informations techniques

- Afficher : nom, adresse, RSSI, services/caractéristiques, MTU, dernière notification, état décodé
  (mode, verrou, phare, batterie, vitesse, trip, odo, octet de statut).
- Journal de trames brutes horodatées, **borné** (200 entrées), avec bouton copier. C'est l'outil de
  débogage pour un nouveau firmware.
- Ne pas inventer tension, courant ou température : afficher « non exposé par le protocole connu »
  tant qu'aucune trame ne les a identifiés.

## Version et journal des modifications

- **La version avance toute seule, une fois par évolution terminée**, pas à chaque compilation.
  Elle s'affiche dans l'application sous la forme `v0.x` (`"v" + versionName`).
- Fichier unique de vérité : `version.properties` à la racine, **versionné** :
  `versionCode`, `versionName` (`0.1`, `0.2`, …), `sourceHash`.
- Mécanisme (modèle maison fonctionnel :
  `C:\Perso\Projets\Android\PressIA-JCP\app\build.gradle.kts`) : en haut de
  `app/build.gradle.kts`, avant `android {}`, calculer un SHA-256 sur `app/src/main/**` +
  `app/build.gradle.kts` (chemins triés). Si l'empreinte diffère de `sourceHash` :
  - `git diff --quiet HEAD -- version.properties` renvoie 1 (fichier modifié) → **conserver**
    l'incrément en cours (évolution non commitée) ;
  - sinon incrémenter `versionCode` de 1 et la mineure (`0.1` → `0.2`), puis écrire le fichier.
  Réécrire `sourceHash` **à chaque** build, même sans incrément.
- **Premier build** (fichier absent) : écrire `versionCode=1`, `versionName=0.1` et l'empreinte,
  sans incrément.
- **Initialiser git et commiter `version.properties` avec la première version.** Tant que le
  fichier est non suivi, `git diff HEAD` le voit comme propre : chaque modification de source
  rebumpe. Une fois suivi et commité, le comportement attendu s'applique.
- **Hors dépôt git, `git diff --quiet HEAD` renvoie 1** — indistinguable de « fichier modifié ».
  Le script lance donc d'abord `git rev-parse --verify HEAD` et considère qu'il n'y a pas de HEAD
  si elle échoue : dans ce cas il ne bumpe plus et laisse l'incrément en attente (`pending`).
  État du 21/11/2026 : aucun dépôt, `versionCode=1`, `versionName=0.1`, `pending=1`.
- Ordre d'une évolution :
  1. modifier les sources et compiler autant que nécessaire (le premier build incrémente, les
     suivants conservent) ;
  2. écrire l'entrée du journal (version, date **et heure**, puces courtes) ;
  3. **recompiler après la dernière édition** : `sourceHash` doit décrire exactement les sources
     commitées, sinon le build suivant ouvrira une version de plus sans rien de neuf ;
  4. installer et vérifier à l'écran ;
  5. commiter sources + journal + `version.properties` **ensemble** : un commit = une version.
- Évolution abandonnée : `git restore version.properties` puis recompiler, sinon le fichier porte
  un numéro pour un changement inexistant.
- Une modification qui ne touche ni `app/src/main` ni `app/build.gradle.kts` (tests,
  documentation, outillage) **ne change ni la version ni le journal**.
- Le journal vit dans le code (prévu : `ui/Changelog.kt` + écran dédié, comme `OBD2`) ; il est
  donc couvert par l'empreinte. Un APK compilé avant l'édition du journal affiche l'ancienne
  liste : recompiler puis contrôler l'écran.

## Release (signée, R8)

- Nom complet obligatoire, défini par `androidComponents.onVariants` :
  `Etwow-<version>-release-<date>.apk` (`<date>` = `yyyyMMdd`). Ne jamais livrer
  `app-release.apk`.
- **Ne pas déplacer l'APK hors de `outputs/`** pour le renommer : l'empaquetage incrémental
  reconstruisait alors un APK tronqué. Écrire directement le bon nom, ou copier.
- `isMinifyEnabled = true` + `proguard-android-optimize.txt`. Garder `proguard-rules.pro` vide
  tant qu'aucune bibliothèque n'utilise la réflexion. Sauvegarder
  `app/build/outputs/mapping/release/mapping.txt` avec chaque release.
- Signature : `etwow-release.keystore` à la racine, alias `etwow`, mots de passe dans
  `keystore.properties` (**non versionné**). Même clé pour toutes les mises à jour ; sauvegarder
  les deux fichiers, sinon plus aucune mise à jour possible.
- Clé **créée le 22/11/2026** avec le `keytool` du JBR (RSA 4096, 10 000 jours, alias `etwow`),
  empreinte SHA-256 du certificat :
  `e34ee14b04200da8937b16b57ac15977fbc74fd7a6f36336525fb06f231c0019`. Vérification :
  `apksigner.bat verify --print-certs <apk>`.
- `apksigner.bat` ne trouve pas Java tout seul : définir `JAVA_HOME` (celui du JDK de l'agent
  suffit) avant de l'appeler, sinon « JAVA_HOME is not set » et code de sortie trompeur.
- Release 0.2 publiée le 22/11/2026 (`gh release create v0.2 <apk>`), APK
  `Etwow-0.2-release-20261122.apk` (1,41 Mo, R8), `mapping` copié en
  `outputs/apk/release/mapping-0.2.txt`. La release a été installée sur l'émulateur après
  désinstallation de la debug : vérifié qu'elle démarre, connecte le simulateur et affiche l'état.
- Signatures debug ≠ release : installer une release par-dessus une debug exige une
  désinstallation (données perdues). **Ne jamais désinstaller, désactiver ni effacer les données
  d'une autre application sans accord explicite** — demander avant.

## Tests

- La logique pure (codec, checksum, décodage des trames, calcul solaire, machine à états du widget
  et du phare auto) a un **test unitaire JUnit 4** dans `app/src/test/`, lancé par
  `:app:testDebugUnitTest`.
- Tests minimum : trames de commande exactes, notification tronquée/type inconnu sans crash, statut
  verrou/phare/mode, bascule 25 ↔ mode précédent, lever/coucher connus (Paris, solstices) et cas
  polaire, hystérésis du phare, override manuel.
- Trip/odomètre : un test avec une trame réelle dès que l'ordre des octets est confirmé.
- Le reste se vérifie **à l'écran** (pas d'`androidTest` par défaut).

## Vérification

### Émulateur (mode simulateur obligatoire)

1. Installer, lancer, activer le simulateur : le tableau de bord affiche vitesse, batterie, trip,
   odo.
2. Verrouiller à l'arrêt : l'état passe à verrouillé ; à vitesse > 0, le bouton est désactivé et
   aucune trame n'est envoyée.
3. Phare on/off, puis chaque limite (6/20/25/42) : la valeur affichée suit la notification.
4. Widget 1×1 : coche grise hors ligne, rouge hors 25, appui → 25 puis coche verte ; un second
   appui reste à 25 ; hors ligne, l'appui relance la connexion sans mettre l'ordre en attente.
5. Phare auto : injecter une heure/position de test, vérifier l'allumage 30 min avant le coucher et
   l'extinction 30 min après le lever, l'hystérésis et l'override manuel.
6. Infos techniques : trames brutes, décodage, copie du journal.
7. Couper la liaison simulée : l'UI passe hors ligne, puis se reconnecte et relit l'état.

### Trottinette réelle

1. Noter le modèle et le firmware ; vérifier le nom BLE annoncé et les UUID.
2. Éteindre/rallumer la trottinette : mesurer le délai de reconnexion automatique.
3. Comparer vitesse, batterie, trip et odomètre avec l'afficheur de la trottinette.
4. Verrouiller/déverrouiller à l'arrêt ; refuser le verrou en roulant.
5. Phare on/off ; appui widget → 25 (coche verte) et le reste ; vérifier la notification de confirmation.
6. Phare auto au coucher (ou avancer l'heure du téléphone) ; vérifier l'override manuel.
7. Infos techniques : noter toute trame inconnue pour l'analyse.

### Capturer le protocole

Options développeur Android → activer le journal HCI Bluetooth, reproduire les actions avec
l'application officielle, puis récupérer `btsnoop_hci.log` (souvent via `adb bugreport`) et
l'ouvrir dans Wireshark. `nRF Connect` sert à lister services et caractéristiques. Ne pas envoyer
de trame devinée : comparer d'abord la capture.

## Règles de travail

- **Commit uniquement sur demande explicite.** Commit et push vont ensemble. Sans dépôt distant,
  le signaler au lieu d'inventer une destination.
- Toute décision non évidente et tout piège rencontré (avec sa raison) s'écrivent ici **au moment
  où ils sont compris**, pour qu'un futur agent ne les rejoue pas.
- `README.md` décrit ce que fait l'app, sa structure, ses dépendances et son build ; ce fichier
  garde les décisions, contraintes et pièges.
- `.gitignore` doit exclure au minimum `.gradle/`, `.kotlin/`, `build/`, `local.properties`,
  `keystore.properties` et `*.keystore` (secrets et artefacts).
- Ne pas ajouter de dépendance, d'option ou d'abstraction « pour plus tard ».
