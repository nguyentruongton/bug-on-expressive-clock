#!/usr/bin/env python3
"""Check the actual AAB for shared R8 foreground-start API outlines.

Run with: python3 scripts/check_release_foreground_boundary.py app-release.aab
Requires Android SDK dexdump (or pass --dexdump /path/to/dexdump).
This checks bytecode structure, not Google Play's private reachability analysis.
"""
import argparse
import os
from pathlib import Path
import re
import subprocess
import tempfile
import zipfile


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("aab", type=Path)
    sdk = Path(os.environ.get("ANDROID_HOME", Path.home() / "Library/Android/sdk"))
    available = sorted((sdk / "build-tools").glob("*/dexdump"))
    parser.add_argument("--dexdump", type=Path, default=available[-1] if available else None)
    args = parser.parse_args()
    if args.dexdump is None:
        parser.error("Android SDK dexdump not found; pass --dexdump")
    calls = []
    with zipfile.ZipFile(args.aab) as bundle, tempfile.TemporaryDirectory() as folder:
        for name in bundle.namelist():
            if not name.startswith("base/dex/") or not name.endswith(".dex"):
                continue
            dex = Path(folder) / Path(name).name
            dex.write_bytes(bundle.read(name))
            dump = subprocess.check_output([str(args.dexdump), "-d", str(dex)]).decode(errors="replace")
            for block in re.split(r"(?m)(?=^Class #)", dump):
                owner = re.search(r"Class descriptor\s+: '([^']+)'", block)
                for line in block.splitlines():
                    if "invoke-" in line and "Landroid/app/Service;.startForeground:" in line:
                        calls.append((owner[1] if owner else "unknown", line.strip()))
        mapping = bundle.read("BUNDLE-METADATA/com.android.tools.build.obfuscation/proguard.map").decode()
        for section in re.split(r"(?m)(?=^[^ #\n].* -> .*:$)", mapping):
            if "AlarmService$" in section and any(x in section for x in (
                "BootReceiver$", "ClockWidgetProvider$onReceive", "DigitalClockWidgetProvider$onReceive",
            )):
                raise SystemExit("FAIL: boot/widget and ringing coroutine classes merged")
    for owner, line in calls:
        print(owner, line)
    if len(calls) != 1 or calls[0][0] != "Lapp/expressiveclock/alarm/AlarmService;":
        raise SystemExit("FAIL: foreground-start boundary is missing, duplicated or shared")
    if "startForeground:(ILandroid/app/Notification;)V" not in calls[0][1]:
        raise SystemExit("FAIL: expected manifest-typed foreground API")
    print("PASS: foreground start stays in AlarmService; no shared API outline or boot/ringing coroutine merge")


if __name__ == "__main__":
    main()
