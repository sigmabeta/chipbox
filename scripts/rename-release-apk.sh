#!/usr/bin/env bash
# Renames each release APK to chipbox-<versionName>-<abi>.apk so the published CI artifacts are
# identifiable at a glance. With ABI splits (apps/android/build.gradle.kts splits {}), assembleRelease
# emits one APK per ABI (arm64-v8a, x86_64) plus a universal one; output-metadata.json lists them all,
# each with its outputFile and either a UNIVERSAL type or an ABI filter value.
#
# The version name is read from the file the app-versioning plugin generates during
# assembleRelease (build/outputs/app_versioning/release/version_name.txt — "<tag>.<commits>", per
# the appVersioning {} block). That file, NOT the APK's output-metadata.json, is the source of truth
# for the version: the plugin applies the name via a versionNameOverride that AGP does not write back
# into output-metadata.json (its versionName field stays null there). The output file names themselves
# are unaffected by the override, so they're still read from the metadata.
set -euo pipefail

DIR="apps/android/build/outputs/apk/release"
META="$DIR/output-metadata.json"
VERSION_FILE="apps/android/build/outputs/app_versioning/release/version_name.txt"

if [ ! -f "$VERSION_FILE" ]; then
  echo "No version name at $VERSION_FILE — was :apps:android:assembleRelease run first?" >&2
  exit 1
fi

VERSION_NAME="$(cat "$VERSION_FILE")"

if [ -z "$VERSION_NAME" ]; then
  # app-versioning wrote an empty name, i.e. it found no git tag. Dump tag visibility so the CI log
  # explains why (packed vs loose refs, tags missing from the workspace, shallow clone, etc.).
  {
    echo "Empty version name in $VERSION_FILE — app-versioning found no git tag."
    echo "  git tag count: $(git tag --list 2>&1 | wc -l)"
    echo "  git describe : $(git describe --tags 2>&1 || true)"
    echo "  loose refs/tags: $(ls .git/refs/tags 2>&1 | tr '\n' ' ')"
    echo "  packed tags    : $(grep 'refs/tags' .git/packed-refs 2>/dev/null | wc -l) entries"
  } >&2
  exit 1
fi

# For each output APK, derive its ABI label (the ABI filter value, or "universal" when unfiltered) and
# rename to chipbox-<version>-<abi>.apk.
jq -r '.elements[]
        | [ (.filters | map(select(.filterType == "ABI") | .value)[0] // "universal"), .outputFile ]
        | @tsv' "$META" \
  | while IFS=$'\t' read -r ABI OUTPUT_FILE; do
      mv "$DIR/$OUTPUT_FILE" "$DIR/chipbox-$VERSION_NAME-$ABI.apk"
      echo "Renamed $OUTPUT_FILE -> chipbox-$VERSION_NAME-$ABI.apk"
    done
