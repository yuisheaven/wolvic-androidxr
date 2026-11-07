# Wolvic auf Android XR (Samsung Galaxy XR) – Notizen

Dieser Branch (`ccr-5623a11b-xy1xci`) bringt Wolvic auf der Samsung Galaxy XR (SM-I610,
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
