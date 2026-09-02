# Arena AI Mobile — README

**arena.ai (LMArena) as a native-feeling Android app.**

An unofficial, fully local Android client for arena.ai built as a complete
WebView browser tuned for the arena: downloads manager, uploads from gallery
& camera, mic/camera for sites, location, notifications, ad/tracker blocking,
desktop mode, dark mode, share-target receiving, and in-app APK updates.
Google sign-in works inside the app exactly like it does in Chrome — the
site's own OAuth + reCAPTCHA run in the embedded browser and your session
cookie persists between launches.

> ⚠️ arena.ai is an existing independent service (see
> [research notes](docs/ARENA-ENDPOINT-RESEARCH.md)). This app is a *personal,
> unofficial client*. It is not affiliated with or endorsed by arena.ai, and
> it cannot be published on Google Play under their branding.

---

## Get the APK

The **GitHub Actions workflow builds the signed APK automatically** on every
push to `main` and on manual dispatch:

1. Open the **Actions** tab of this repository.
2. Click the newest **“Build APK”** run.
3. Scroll to **Artifacts** → download **`ArenaAI-v1.0.0-signed`**.
4. Unzip and install `ArenaAI-v1.0.0.apk` on your phone
   (allow “install unknown apps” when prompted).

To publish an installable **release** (so the in-app “update available”
notification works): attach the APK to a GitHub Release whose asset name
ends with `-<versionCode>.apk` (e.g. `ArenaAI-1.0.0-1.apk`). The app checks
`api.github.com/repos/kfirimcchaki/Arena.ai/releases/latest` at most once per
day and notifies when a newer build exists.

## Build it yourself

Requires JDK 17 and the Android SDK (command-line tools). Then:

```bash
chmod +x gradlew
./gradlew :app:assembleRelease
# output: app/build/outputs/apk/release/app-release.apk
# install: adb install -r app/build/outputs/apk/release/app-release.apk
```

Signing: CI generates a fresh keystore per build so every build is
installable. For a stable signature (updates over the top of old installs),
export your own keystore and set the env vars below — see
[docs/SIGNING.md](docs/SIGNING.md).

```bash
export RELEASE_STORE_FILE=/path/to/keystore.jks
export RELEASE_STORE_PASSWORD=...
export RELEASE_KEY_ALIAS=...
export RELEASE_KEY_PASSWORD=...
```

## What's inside

| Area | Details |
|---|---|
| Home | Branded quick-start page (aurora UI, search box, mode tiles) — one tap to arena.ai. Settings can boot straight into `https://arena.ai`. |
| Arena routes | Deep links researched from arena.ai's own sitemap: `/` battle, `/direct`, `/image`, `/video`, `/code`, `/agent`, `/leaderboard`, `/history/search`, `/side-by-side`, `/c/{id}` |
| Downloads | Full `DownloadManager` integration to public Downloads; media long-press → save to gallery / share / open; `data:` downloads; “Save page (HTML)”; system downloads folder opens in-app |
| Uploads | `<input type=file>` with gallery, files, **multiple**, plus camera/video capture shortcuts — camera permission is requested on demand |
| Mic & camera in sites | `onPermissionRequest` grants (webcam chat, voice input, etc.) with per-origin prompts |
| Location | Geolocation prompt, remember-per-origin |
| Fullscreen video | YouTube/HTML5 video goes fullscreen in a custom overlay, back gesture exits |
| Popups | `window.open`/`target=_blank` handled inside the app; system links (`tel:`, `mailto:`, custom schemes) go to the OS |
| Search | Google / Bing / DuckDuckGo / Startpage / Yahoo from the address bar |
| View | Desktop-site UA toggle, forced dark web content, per-site text zoom |
| Privacy | Ad & tracker host blocking (built-in blocklist) + cosmetic hiding; per-site “allow ads”; one-tap “clear browsing data” |
| Sharing in | Share any link/text to Arena AI from any app → opens here |
| Updates | Daily check against GitHub releases + manual “Check for updates”, download & install with “allow unknown sources” flow |
| Session | Cookies, localStorage & site data persist across launches; Google/email login works in-app |

## Repository layout

```
app/src/main/java/dev/arena/mobile/   Java sources (pure framework — no deps)
app/src/main/assets/                   blocklist.txt, cosmetic.js, quick-start page (www/)
app/src/main/res/                      theme, vector icons, adaptive launcher icon
gradle/                                wrapper (jar pinned, no network needed)
.github/workflows/build.yml            APK build + signing + artifact upload
docs/                                  BUILDING, SIGNING, FEATURES, research notes
scripts/build-apk.sh                   local one-shot build
```

Min SDK 26 (Android 8.0+), target 34. The APK is tiny — no third-party
runtime dependencies; it renders with the device's system WebView (updated
through Google Play, same engine family as Chrome).

## Honest scope notes

* arena.ai has **no public API** — chat/evaluation endpoints require an
  authenticated session and reCAPTCHA (see [research](docs/ARENA-ENDPOINT-RESEARCH.md)).
  The app is therefore a full browser, not a thin API client; that is the
  only robust way to support the site, Google login, and every arena mode.
* Chrome extensions need Chromium's extension APIs, which Android's system
  WebView does not provide. Real “Kiwi-with-extensions” support would require
  building Chromium itself (100+ GB, hours on a workstation) — out of scope
  for a small APK. Everything else a mobile Chrome does is in.
* This app is unofficial and unaffiliated. It uses no arena.ai trademarks in
  its package name (`dev.arena.mobile`) and labels itself a client app.
