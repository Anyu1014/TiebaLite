#!/usr/bin/env bash
set -euo pipefail
source /Users/y/toolchains/env.sh
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
MODE="${1:-debug}"
/Users/y/toolchains/bin/node "$ROOT/scripts/sync-config.mjs"
/Users/y/toolchains/bin/xcodegen generate --spec "$ROOT/ios/project.yml" --project "$ROOT/ios" --project-root "$ROOT/ios"
COMMON=(-project "$ROOT/ios/TiebaPure.xcodeproj" -scheme TiebaPure -derivedDataPath "$ROOT/.build/ios")
mkdir -p "$ROOT/logs" "$ROOT/.build"
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
      (cd "$PACKAGE" && /usr/bin/zip -qry archive.ipa Payload)
      /usr/bin/unzip -t "$PACKAGE/archive.ipa"
      OUTPUT="$(/Users/y/toolchains/bin/node "$ROOT/scripts/store-package.mjs" "$PACKAGE/archive.ipa" "TiebaLite-$VERSION-unsigned.ipa")"
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
    EXPORT="$(mktemp -d "$ROOT/.build/ios-export.XXXXXX")"
    /Users/y/toolchains/bin/xcodebuild -exportArchive -archivePath "$ROOT/.build/TiebaLite-signed.xcarchive" -exportOptionsPlist "$2" -exportPath "$EXPORT"
    shopt -s nullglob
    EXPORTED_IPAS=("$EXPORT"/*.ipa)
    [[ ${#EXPORTED_IPAS[@]} -eq 1 ]] || { echo 'Expected one exported IPA' >&2; exit 1; }
    VERSION="$(/usr/libexec/PlistBuddy -c 'Print :CFBundleShortVersionString' "$ROOT/.build/TiebaLite-signed.xcarchive/Products/Applications/TiebaPure.app/Info.plist")"
    OUTPUT="$(/Users/y/toolchains/bin/node "$ROOT/scripts/store-package.mjs" "${EXPORTED_IPAS[0]}" "TiebaLite-$VERSION.ipa")"
    /usr/bin/shasum -a 256 "$OUTPUT" ;;
  *) echo 'Usage: build-ios.sh debug|archive|ipa|test|signed-archive|export <ExportOptions.plist>' >&2; exit 2 ;;
esac
