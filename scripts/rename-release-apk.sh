#!/usr/bin/env bash
# Renames the release APK to chipbox-<versionName>.apk so the published CI artifact is identifiable
# at a glance. The version name is whatever the appVersioning {} block in apps/android/build.gradle.kts
# derived from the latest git tag ("<tag>.<commits-since-tag>"); we read it (and the real output file
# name) straight out of AGP's output-metadata.json so there's a single source of truth — no need to
# recompute the version here.
set -euo pipefail

DIR="apps/android/build/outputs/apk/release"
META="$DIR/output-metadata.json"

if [ ! -f "$META" ]; then
  echo "No output-metadata.json at $META — was :apps:android:assembleRelease run first?" >&2
  exit 1
fi

VERSION_NAME=$(jq -r '.elements[0].versionName' "$META")
OUTPUT_FILE=$(jq -r '.elements[0].outputFile' "$META")

if [ -z "$VERSION_NAME" ] || [ "$VERSION_NAME" = "null" ]; then
  echo "Could not read versionName from $META" >&2
  exit 1
fi

mv "$DIR/$OUTPUT_FILE" "$DIR/chipbox-$VERSION_NAME.apk"
echo "Renamed $OUTPUT_FILE -> chipbox-$VERSION_NAME.apk"
