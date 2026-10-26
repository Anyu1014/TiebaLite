#!/usr/bin/env bash
set -euo pipefail
source /Users/y/toolchains/env.sh
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
MODE="${1:-debug}"
/Users/y/toolchains/bin/node "$ROOT/scripts/sync-config.mjs"
/Users/y/toolchains/bin/xcodegen generate --spec "$ROOT/ios/project.yml" --project "$ROOT/ios" --project-root "$ROOT/ios"
COMMON=(-project "$ROOT/ios/TiebaPure.xcodeproj" -scheme TiebaPure -derivedDataPath "$ROOT/.build/ios")
mkdir -p "$ROOT/logs" "$ROOT/releases/ios"
case "$MODE" in
  debug)
    /Users/y/toolchains/bin/xcodebuild "${COMMON[@]}" -configuration Debug -sdk iphoneos -destination 'generic/platform=iOS' build CODE_SIGNING_ALLOWED=NO ;;
  archive|ipa)
    ARCHIVE="$ROOT/.build/TiebaLite.xcarchive"
    /Users/y/toolchains/bin/xcodebuild "${COMMON[@]}" -configuration Release -sdk iphoneos -destination 'generic/platform=iOS' -archivePath "$ARCHIVE" archive CODE_SIGNING_ALLOWED=NO
    if [[ "$MODE" == ipa ]]; then
      PACKAGE="$(mktemp -d "$ROOT/.build/ipa.XXXXXX")"
      trap 'rm -rf "$PACKAGE"' EXIT
      mkdir -p "$PACKAGE/Payload"
      /usr/bin/ditto "$ARCHIVE/Products/Applications/TiebaPure.app" "$PACKAGE/Payload/TiebaPure.app"
      VERSION="$(/usr/libexec/PlistBuddy -c 'Print :CFBundleShortVersionString' "$PACKAGE/Payload/TiebaPure.app/Info.plist")"
      OUTPUT="$ROOT/releases/ios/TiebaLite-$VERSION-unsigned.ipa"
      (cd "$PACKAGE" && /usr/bin/zip -qry archive.ipa Payload)
      /usr/bin/unzip -t "$PACKAGE/archive.ipa"
      mv "$PACKAGE/archive.ipa" "$OUTPUT"
      /usr/bin/shasum -a 256 "$OUTPUT"
    fi ;;
  test)
    : "${SIMULATOR_UDID:?Set SIMULATOR_UDID to an available iOS simulator}"
    /Users/y/toolchains/bin/xcodebuild "${COMMON[@]}" -destination "platform=iOS Simulator,id=$SIMULATOR_UDID" test -only-testing:TiebaPureTests -skip-testing:TiebaPureTests/AnonymousLiveSmokeTests ;;
  signed-archive)
    : "${DEVELOPMENT_TEAM:?Set the signing team and install matching provisioning profiles}"
    /Users/y/toolchains/bin/xcodebuild "${COMMON[@]}" -configuration Release -destination 'generic/platform=iOS' -archivePath "$ROOT/.build/TiebaLite-signed.xcarchive" archive DEVELOPMENT_TEAM="$DEVELOPMENT_TEAM" ;;
  export)
    : "${2:?Provide an ExportOptions.plist matching your signing method}"
    /Users/y/toolchains/bin/xcodebuild -exportArchive -archivePath "$ROOT/.build/TiebaLite-signed.xcarchive" -exportOptionsPlist "$2" -exportPath "$ROOT/releases/ios" ;;
  *) echo 'Usage: build-ios.sh debug|archive|ipa|test|signed-archive|export <ExportOptions.plist>' >&2; exit 2 ;;
esac
