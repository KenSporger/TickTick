#!/usr/bin/env bash
set -euo pipefail

version="${1:?usage: scripts/package-release.sh VERSION}"
release_dir="${2:-dist}"
mkdir -p "$release_dir"

./gradlew assembleDebug
apk_name="ticktick-universal-${version}.apk"
backend_name="ticktick-backend-${version}.tar.gz"
cp app/build/outputs/apk/debug/app-debug.apk "$release_dir/$apk_name"
tar -czf "$release_dir/$backend_name" backend/app backend/requirements.txt backend/run.sh backend/ticktick.service

apk_sha="$(sha256sum "$release_dir/$apk_name" | cut -d' ' -f1)"
backend_sha="$(sha256sum "$release_dir/$backend_name" | cut -d' ' -f1)"
sed -e "s/__VERSION__/$version/g" -e "s/__APK_NAME__/$apk_name/g" -e "s/__APK_SHA__/$apk_sha/g" \
  -e "s/__BACKEND_NAME__/$backend_name/g" -e "s/__BACKEND_SHA__/$backend_sha/g" \
  release-manifest.template.json > "$release_dir/release-manifest.json"
