#!/usr/bin/env bash
set -euo pipefail
: "${ANDROID_HOME:=${ANDROID_SDK_ROOT:-}}"
if [[ -z "${ANDROID_HOME}" ]]; then
  echo 'Defina ANDROID_HOME para o Android SDK ou abra o projeto no Android Studio.' >&2
  exit 1
fi
if ! command -v gradle >/dev/null 2>&1; then
  echo 'Instale Gradle 8.11.1 ou use a compilação automática por GitHub Actions.' >&2
  exit 1
fi
gradle --no-daemon :app:assembleDebug
printf 'APK: %s\n' 'app/build/outputs/apk/debug/app-debug.apk'
