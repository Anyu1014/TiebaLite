#!/usr/bin/env bash
set -euo pipefail
source /Users/y/toolchains/env.sh
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT/android"
printf 'sdk.dir=/Users/y/toolchains/android-sdk\n' > local.properties
MODE="${TIEBALITE_ANDROID_MODE:-debug}"
if [[ "$MODE" == release ]]; then
  : "${TIEBALITE_KEYSTORE:?Set the release keystore path}"
  : "${TIEBALITE_KEY_PASSWORD_FILE:?Set the private password file path}"
  ./gradlew --no-daemon testDebugUnitTest assembleRelease "$@"
elif [[ "$MODE" == debug ]]; then
  ./gradlew --no-daemon testDebugUnitTest assembleDebug "$@"
else
  echo 'TIEBALITE_ANDROID_MODE must be debug or release' >&2
  exit 2
fi
VERSION="$(/Users/y/toolchains/bin/node -p 'require("../app-config.json").version')"
NAME="TiebaLite-$VERSION.apk"
if [[ "$MODE" == debug ]]; then NAME="TiebaLite-$VERSION-debug.apk"; fi
SOURCE_APK="app/build/outputs/apk/$MODE/app-$MODE.apk"
/Users/y/toolchains/bin/apksigner verify --verbose "$SOURCE_APK"
OUTPUT="$(/Users/y/toolchains/bin/node "$ROOT/scripts/store-package.mjs" "$SOURCE_APK" "$NAME")"
/usr/bin/shasum -a 256 "$OUTPUT"
