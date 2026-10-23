#!/usr/bin/env python3
"""Lists the permissions in the merged debug manifest (after libraries add theirs) and flags unexpected ones."""
import glob
import re
import sys

# Permissions the app is expected to end up with. Anything else fails the check.
EXPECTED = {
    "android.permission.CAMERA",  # not requested by the app itself; allowed if a library adds it
    "android.permission.INTERNET",  # Play services code scanner / ML Kit libraries may declare it
    "android.permission.ACCESS_NETWORK_STATE",
    "android.permission.REQUEST_INSTALL_PACKAGES",  # the app's own: install a downloaded app update
    "android.permission.WAKE_LOCK",
    "android.permission.FOREGROUND_SERVICE",
    "android.permission.BLUETOOTH",  # the app's own: Bluetooth receipt printer on Android 11 and older (maxSdk 30)
    "android.permission.BLUETOOTH_CONNECT",  # the app's own: Bluetooth receipt printer on Android 12 and newer
    "android.permission.WRITE_EXTERNAL_STORAGE",  # the app's own, Android 9 and older only (maxSdk 28): save backups in Documents/Auxo
    "android.permission.RECEIVE_BOOT_COMPLETED",  # added by WorkManager (scheduled backups)
    "android.permission.USE_BIOMETRIC",  # added by the AndroidX biometric library
    "android.permission.USE_FINGERPRINT",  # added by the AndroidX biometric library
}
DYNAMIC = re.compile(r"\.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION$")

paths = glob.glob("app/build/intermediates/merged_manifest*/debug/**/AndroidManifest.xml", recursive=True)
paths += glob.glob("app/build/intermediates/packaged_manifests/debug/**/AndroidManifest.xml", recursive=True)
if not paths:
    print("::error::No merged manifest found; build the debug APK first")
    sys.exit(1)

found = set()
for path in paths:
    found |= set(re.findall(r'<uses-permission[^>]*android:name="([^"]+)"', open(path, encoding="utf-8").read()))
found = {p for p in found if not DYNAMIC.search(p)}
unexpected = sorted(found - EXPECTED)
print("Permissions in merged manifest:", ", ".join(sorted(found)) or "none")
if unexpected:
    print("::error title=Unexpected permissions::" + ", ".join(unexpected))
    sys.exit(1)
