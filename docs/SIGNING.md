# Signing

Android requires every APK to be signed. Signing keys are how Android knows
an update belongs to the same app.

## How this project signs

* **CI builds** generate a brand-new keystore per build (random password,
  never leaves the runner). Every artifact is signed & installable. Downside:
  each build has a *different* signature, so installing build N over build
  M fails with `INSTALL_FAILED_UPDATE_INCOMPATIBLE` — uninstall first.
* **Stable personal builds:** generate your own keystore once and reuse it.
  Then every future APK updates cleanly over the previous one, and the
  in-app updater can install newer builds without uninstalling.

## Create your own keystore (do this once)

```bash
keytool -genkeypair -v \
  -keystore arena-release.keystore \
  -alias arena \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -storepass CHANGE_ME -keypass CHANGE_ME \
  -dname "CN=Arena Mobile, O=Personal, C=US"
```

Keep `arena-release.keystore` safe and **never commit it** (the repo
`.gitignore` excludes `*.keystore` and `*.jks`). If you lose it you cannot
update an installed app signed with it.

## Build with your keystore

```bash
export RELEASE_STORE_FILE=/abs/path/arena-release.keystore
export RELEASE_STORE_PASSWORD=CHANGE_ME
export RELEASE_KEY_ALIAS=arena
export RELEASE_KEY_PASSWORD=CHANGE_ME
./gradlew :app:assembleRelease
```

Or via GitHub Actions: add repository secrets with those four names and the
workflow signs with them automatically (falling back to a fresh keystore
when the secrets are absent).

## Verify

```bash
apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk
```
