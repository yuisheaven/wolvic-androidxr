# Wolvic auf Android XR (Samsung Galaxy XR) – Notizen

Dieser Branch (`android-xr`, früher `ccr-5623a11b-xy1xci`) bringt Wolvic auf der Samsung Galaxy XR (SM-I610,
Android XR, OpenXR-Laufzeit „Android XR“, Systemname „Moohan“) zum Laufen. Gebaut wird die
AOSP-Variante mit Gecko: `aospArm64GeckoGeneric`.

Getestet auf dem Gerät: Start, normale Webseiten, YouTube (2D und 360°), WebXR
(https://msub2.github.io/hello-webxr/), Add-ons (AdGuard, Bitwarden mit Einschränkungen),
Hand-Tracking, physische Tastatur.

## Änderungen (älteste zuerst)

| Commit | Was | Für Upstream? |
|---|---|---|
| `[AOSP] Add Android XR support` | Manifest: Full-Space-Startmodus, `libopenxr.google.so`, Feature `android.software.xr.api.openxr`, Berechtigungen `HAND_TRACKING`/`EYE_TRACKING_FINE`; Hand-Tracking-Berechtigung wird beim Start angefragt; Hand-Tracker wird nachträglich angelegt, falls die Berechtigung erst später kommt | ja |
| `[OpenXR] Make eye gaze initialization non-fatal` | Fehler bei der Blicksteuerung beenden die App nicht mehr | ja |
| `[AOSP] Use the OpenXR 1.1 loader` | Loader 1.1.48 wie bei Meta, mit Rückfall auf API 1.0 | ja (nicht zwingend nötig) |
| `[AOSP] Save logcat to Downloads in debug builds` | Nur Debug-Builds: eigenes Log nach `Download/Wolvic/` | nein, nur zum Testen |
| `[AOSP] Use uncompressed cubemaps for the environment` | Android XR kann keine ETC2-Swapchains: PNG-Umgebung wie bei Meta, Absicherung beim Cube-Layer | ja |
| `[AOSP] Fix video crash, upside-down splash and refresh rate` | `WAKE_LOCK` (Gecko stürzte bei jedem Video ab), Start-Logo gespiegelt wie bei Pico, 90 Hz auf Android XR | ja |
| `[OpenXR] Support XR_KHR_composition_layer_equirect2` | 360°/180°-Videos über Equirect v2 (Android XR kennt v1 nicht) | ja |
| `Ignore tracked hands while typing…` / `Ignore hands lowered over a physical keyboard` / `Keyboard zone…` | Tipp-Sperre (2,5 s), Tastaturzone (60 s nach letzter Taste: Hand > 40 cm unter dem Kopf und Strahl > 35° nach unten wird ignoriert), Strg+Alt+H schaltet Hand-Tracking um | eher persönlich, ggf. als Einstellung |
| `[AOSP] Debug logcat saver: drop per-frame…` / `…never write form values or typed keys` | Log-Filter für Spam und sensible Zeilen | nein, nur zum Testen |
| `[Gecko] Speak the upstream WebXR protocol with stock GeckoView` | WebXR mit der GeckoView von maven.mozilla.org (Protokoll v19 statt Wolvics v21) | eventuell, als Build-Option |
| `[OpenXR] Do not use a cube layer on the Android XR emulator` | Der Emulator lehnt Cube-Swapchains ab und hängt danach beim nächsten Swapchain-Aufruf; im Emulator (`ro.boot.qemu=1`) wird daher kein Cube-Layer angelegt. Auf der Brille unverändert | ja |

## Bauen

Voraussetzungen: JDK 21, Android SDK mit Plattform 36 und Build-Tools 35, NDK `27.0.12077973`,
CMake `3.22.1`.

```bash
git submodule update --init --recursive   # entfällt, wenn die Untermodule schon da sind
echo "sdk.dir=/pfad/zum/android-sdk" > local.properties

# Debug (langsam, schreibt Log nach Download/Wolvic/)
./gradlew assembleAospArm64GeckoGenericDebug

# Release (schnell, kein Log). Ohne eigene Release-Signatur mit dem Debug-Schlüssel signieren:
echo "useDebugSigningOnRelease=true" > user.properties
./gradlew assembleAospArm64GeckoGenericRelease
```

APKs landen unter `app/build/outputs/apk/aospArm64GeckoGeneric/`.

Hinweise:
- Ein Update lässt sich nur über eine Version mit **derselben Signatur** installieren. Der
  Debug-Schlüssel liegt unter `~/.android/debug.keystore` des Rechners, auf dem gebaut wird.
  Ein anderer Schlüssel heißt: Wolvic deinstallieren (Einstellungen/Add-ons gehen verloren).
- Maven Central bremst bei vielen Downloads (HTTP 429). Abhilfe: Gradle-Init-Skript, das
  `repo.maven.apache.org` auf `https://maven-central.storage-download.googleapis.com/maven2/`
  umleitet, oder einfach erneut starten.

### Bauen unter Windows

- Der Repo-Pfad darf **keine Leerzeichen** enthalten: Die C++-Include-Pfade werden ungequotet an
  CMake übergeben. Abhilfe ohne Umbenennen: eine Verknüpfung (Junction) anlegen und darüber bauen,
  z. B. `New-Item -ItemType Junction -Path C:\yuisapps\wolvic-build -Target "<Repo-Pfad>"`.
- Signatur ohne den eigenen `~/.android/debug.keystore` zu ersetzen: den Wolvic-Schlüssel als
  `debug.keystore` in einen eigenen Ordner legen und beim Bauen `ANDROID_USER_HOME` darauf setzen.
- `sdkmanager.bat` trennt Argumente am `;` (z. B. `ndk;27.0.12077973`) – Pakete per
  `--package_file=<datei>` übergeben.

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'   # JDK 21
$env:ANDROID_USER_HOME = "$env:USERPROFILE\.android-wolvic"       # enthält debug.keystore
cd C:\yuisapps\wolvic-build
.\gradlew.bat assembleAospArm64GeckoGenericRelease   # Brille
.\gradlew.bat assembleAospX64GeckoGenericRelease     # Emulator (x86_64)
```

## Android-XR-Emulator

Emulator ab 37.x mit dem Image `system-images;android-34;android-xr-v3-playstore;x86_64`,
Geräteprofil `xr_headset_device` (in `config.ini`: `hw.gpu.enabled=yes`, `hw.gpu.mode=host`,
mehr RAM). Das Image bringt eine OpenXR-Laufzeit mit, Wolvic läuft dort als x86_64-Build
(`aospX64GeckoGeneric`): Fenster, Menüleiste und Dialoge werden angezeigt, die Session erreicht
`FOCUSED`. Einschränkungen: die Umgebung bleibt schwarz (kein Cube-Layer, siehe oben), Eingabe
nur per Maus/Tastatur des Emulators, kein Ersatz für Tests auf der Brille.

## Bekannte Einschränkungen

- **WebXR** läuft mit der Standard-GeckoView nur eingeschränkt: nur VR (kein AR/Passthrough),
  Augenposition nur als Verschiebung (ohne Drehung), Hände als generische Controller. Voller
  Umfang braucht eine selbst gebaute GeckoView mit https://github.com/Igalia/wolvic-gecko-patches
  (dann über `dependencySubstitutions.geckoviewTopsrcdir` einbinden; die Build-Option schaltet
  automatisch auf Wolvics Protokoll v21 zurück).
- **Bitwarden-Erweiterung:**
  - funktioniert erst, wenn sie in privaten Fenstern laufen darf (Add-ons → Bitwarden).
    Sonst liefert Gecko `sender.tab` nicht, obwohl die Tabs nicht privat sind – vermutlich ein
    Fehler in Wolvic/Gecko, Kandidat für einen Fehlerbericht.
  - Popup ist abgeschnitten (bekanntes Bitwarden-Problem auf Firefox für Android). Notlösung:
    `moz-extension://<ID>/popup/index.html?uilocation=popout` als Lesezeichen.
  - Das Inline-Menü in Login-Feldern zeigt keine Einträge; Ursache ungeklärt (bräuchte
    Bitwardens Debug-Konsole).
- **Gelegentlicher Neustart der Android-XR-Oberfläche** beim Öffnen von Wolvic. Wolvic selbst
  stürzt dabei nicht ab; Ursache unklar, dafür wären System-Logs nötig
  (`adb logcat -b crash -d`).
- Galaxy XR ist Wolvic als Gerät unbekannt („Unknown Device“). Android XR wird an der Laufzeit
  erkannt (90 Hz). Eine eigene Geräteklasse würde u. a. passende Controller-Modelle erlauben.

## Logs aus Debug-Builds

Debug-Builds schreiben ihr eigenes Logcat nach `Download/Wolvic/wolvic-log-*.txt`. Ab dem
letzten Stand werden Formularwerte, Tastatureingaben und Zeilen mit „password“ verworfen.
Ältere Logs können Zugangsdaten und Tastatureingaben enthalten – nicht weitergeben, löschen.
