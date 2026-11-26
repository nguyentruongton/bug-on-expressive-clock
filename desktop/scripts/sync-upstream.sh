#!/usr/bin/env bash
set -euo pipefail

# Automates syncing with upstream/main and verifies Desktop Shadow Overrides + Kotlin compilation.

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
cd "$REPO_ROOT"

SHADOW_FILES=(
    "app/src/main/java/app/expressiveclock/ui/ClockViewModel.kt"
    "app/src/main/java/app/expressiveclock/data/ClockStore.kt"
    "app/src/main/java/app/expressiveclock/ui/theme/Theme.kt"
    "app/src/main/java/app/expressiveclock/ui/Glass.kt"
    "app/src/main/java/app/expressiveclock/ui/screens/SettingsScreen.kt"
    "app/src/main/java/app/expressiveclock/ui/TimeFormat.kt"
    "app/src/main/java/app/expressiveclock/ui/responsive/AppWindowSizeInfo.kt"
    "app/src/main/java/app/expressiveclock/ui/sheets/AlarmSoundPage.kt"
)

echo "==> Checking upstream remote..."
if git remote get-url upstream >/dev/null 2>&1; then
    git fetch upstream
    echo "==> Merging upstream/main (no-commit check)..."
    git merge --no-edit upstream/main
else
    echo "WARN: Remote 'upstream' not configured. Skipping fetch/merge and inspecting HEAD~1..HEAD."
fi

BASE_REF="ORIG_HEAD"
if ! git rev-parse --verify "$BASE_REF" >/dev/null 2>&1; then
    BASE_REF="HEAD~1"
fi

echo "==> Checking Shadow Override drift ($BASE_REF..HEAD)..."
DRIFT_FOUND=0
for f in "${SHADOW_FILES[@]}"; do
    if ! git diff --quiet "$BASE_REF" HEAD -- "$f" 2>/dev/null; then
        echo "  [DRIFT WARNING] Upstream modified shadowed file: $f"
        DRIFT_FOUND=1
    fi
done

if [[ "$DRIFT_FOUND" -eq 0 ]]; then
    echo "  OK: No upstream changes in shadowed files."
fi

echo "==> Verifying desktop Kotlin compilation..."
./gradlew -p desktop compileKotlin --console=plain

echo "==> Verifying zero-touch upstream integrity..."
git status --short app/ gradle/
echo "==> Done."
