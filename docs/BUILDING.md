# Building the APK

## Option A — GitHub Actions (recommended, zero setup)

1. Push to `main` (or go to **Actions → Build APK → Run workflow**).
2. When the run finishes, open it and download the artifact
   **`ArenaAI-v1.0.0-signed`**.
3. Unzip → `ArenaAI-v1.0.0.apk` is a signed release APK. Copy it to your
   phone and tap it to install (Android asks to allow “install unknown apps”
   for your file manager/browser — allow it).

The CI runner generates a fresh signing keystore for every build, so the APK
is always properly signed and installable.

## Option B — Local build

Requirements:

* **JDK 17** (Temurin/OpenJDK)
* **Android SDK command-line tools**, with `platforms;android-34` and
  `build-tools;34.0.0` installed (AGP downloads the rest automatically)
* Network access to `dl.google.com` and `services.gradle.org` (Gradle 8.7 is
  downloaded by the wrapper on first run)

```bash
# environment
export ANDROID_HOME=$HOME/Android/Sdk          # or wherever your SDK lives
export JAVA_HOME=/path/to/jdk17

# one-shot build script
./scripts/build-apk.sh

# …or manually:
./gradlew :app:assembleRelease
adb install -r app/build/outputs/apk/release/app-release.apk
```

Without `RELEASE_STORE_FILE` set, the release APK is signed with the debug
key — fine for sideloading on your own devices. For anything you plan to
keep installed across updates, use your own keystore (see SIGNING.md).

## Versions

Override the version code/name with `-Pvc=2 -Pvn=1.1.0`:

```bash
./gradlew :app:assembleRelease -Pvc=2 -Pvn=1.1.0
```

## Reproducible wrapper

`gradle/wrapper/gradle-wrapper.jar` and `gradlew` are committed, and
`gradle/wrapper/gradle-wrapper.properties` pins Gradle 8.7, so builds do not
need a pre-installed Gradle.
