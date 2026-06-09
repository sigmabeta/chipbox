#!/usr/bin/env bash
# Renames the release APK to chipbox-<versionName>.apk so the published CI artifact is identifiable
# at a glance.
#
# The version name is read from the file the app-versioning plugin generates during
# assembleRelease (build/outputs/app_versioning/release/version_name.txt — "<tag>.<commits>", per
# the appVersioning {} block in apps/android/build.gradle.kts). That file, NOT the APK's
# output-metadata.json, is the source of truth: the plugin applies the name via a versionNameOverride
# that AGP does not write back into output-metadata.json (its versionName field stays null there).
# The output file name itself is unaffected by the override, so it's still read from the metadata.
set -euo pipefail

DIR="apps/android/build/outputs/apk/release"
META="$DIR/output-metadata.json"
VERSION_FILE="apps/android/build/outputs/app_versioning/release/version_name.txt"

if [ ! -f "$VERSION_FILE" ]; then
  echo "No version name at $VERSION_FILE — was :apps:android:assembleRelease run first?" >&2
  exit 1
fi

VERSION_NAME="$(cat "$VERSION_FILE")"
OUTPUT_FILE=$(jq -r '.elements[0].outputFile' "$META")

if [ -z "$VERSION_NAME" ]; then
  echo "Empty version name in $VERSION_FILE" >&2
  exit 1
fi

mv "$DIR/$OUTPUT_FILE" "$DIR/chipbox-$VERSION_NAME.apk"
echo "Renamed $OUTPUT_FILE -> chipbox-$VERSION_NAME.apk"
