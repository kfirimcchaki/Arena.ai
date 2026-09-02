#!/usr/bin/env bash
#
# Arena AI Mobile — one-shot local APK build (needs Android SDK + JDK 17).
# Full instructions: docs/BUILDING.md
set -euo pipefail

cd "$(dirname "$0")/.."

echo "==> Arena AI Mobile build"
echo "    Android SDK : ${ANDROID_HOME:-${ANDROID_SDK_ROOT:-<unset>}}"

# Optional local signing (otherwise the APK is signed with the debug key).
if [[ -n "${RELEASE_STORE_FILE:-}" ]]; then
  echo "    Release keystore : $RELEASE_STORE_FILE"
else
  echo "    Signing         : debug key (installable, not for stores)"
fi

./gradlew :app:assembleRelease --stacktrace

APK="app/build/outputs/apk/release/app-release.apk"
echo
echo "==> Done: $APK"
ls -lh "$APK"
echo
echo "Install on a connected device:  adb install -r \"$APK\""
