#!/usr/bin/env python3
"""Fails the build if the merged RELEASE manifest would put something risky in front of Google Play.

The merged manifest (app + every library, after the manifest merger) is what Play and users
actually get, so it is checked - not just app/src/main/AndroidManifest.xml. It guards the
things that have gone wrong or get policy-rejected before:
  * permissions Play restricts or that we deliberately do not use (self-update installs, heart
    rate, location, contacts, SMS, phone state, broad storage, the advertising ID, ...)
  * a debuggable / cleartext / backup-enabled release
  * an exported component that is not on the reviewed list (an accidentally exported service or
    receiver is an attack surface)
Every permission in the manifest is printed so reviewers can eyeball the full list in the CI log.

Usage: verify_release_manifest.py [path/to/AndroidManifest.xml ...]
With no arguments it searches app/build/intermediates for the merged release manifest.
"""
import glob
import re
import sys
import xml.etree.ElementTree as ET

ANDROID = "{http://schemas.android.com/apk/res/android}"

FORBIDDEN_PERMISSIONS = {
    "android.permission.REQUEST_INSTALL_PACKAGES": "self-updating outside Play is forbidden (debug-only)",
    "android.permission.health.READ_HEART_RATE": "not read by the app; Health Connect review rejects unused permissions",
    "android.permission.ACCESS_FINE_LOCATION": "the app collects no location (Data Safety says so)",
    "android.permission.ACCESS_COARSE_LOCATION": "the app collects no location (Data Safety says so)",
    "android.permission.ACCESS_BACKGROUND_LOCATION": "the app collects no location",
    "android.permission.READ_CONTACTS": "contacts are not collected",
    "android.permission.WRITE_CONTACTS": "contacts are not collected",
    "android.permission.READ_SMS": "SMS permissions are restricted by Play",
    "android.permission.RECEIVE_SMS": "SMS permissions are restricted by Play",
    "android.permission.SEND_SMS": "SMS permissions are restricted by Play",
    "android.permission.READ_PHONE_STATE": "device identifiers are not collected",
    "android.permission.READ_CALL_LOG": "restricted by Play",
    "android.permission.CAMERA": "not used today - add it deliberately, with a Data Safety update",
    "android.permission.READ_EXTERNAL_STORAGE": "use the photo picker / scoped storage",
    "android.permission.WRITE_EXTERNAL_STORAGE": "use scoped storage",
    "android.permission.MANAGE_EXTERNAL_STORAGE": "restricted by Play",
    "android.permission.SYSTEM_ALERT_WINDOW": "not needed",
    "android.permission.QUERY_ALL_PACKAGES": "restricted by Play",
    "android.permission.USE_FULL_SCREEN_INTENT": "not needed",
    "android.permission.SCHEDULE_EXACT_ALARM": "restricted by Play; WorkManager is used instead",
    "android.permission.USE_EXACT_ALARM": "restricted by Play",
    "com.google.android.gms.permission.AD_ID": "the app shows no ads and shares nothing for advertising; keep the advertising ID out",
    "android.permission.ACCESS_ADSERVICES_AD_ID": "Privacy Sandbox advertising ID (merged in by Firebase Analytics); the app has no advertising",
    "android.permission.ACCESS_ADSERVICES_ATTRIBUTION": "Privacy Sandbox ad attribution (merged in by Firebase Analytics); the app has no advertising",
    "android.permission.ACCESS_ADSERVICES_TOPICS": "Privacy Sandbox ad topics; the app has no advertising",
}

# Components that are intentionally exported (launcher, Health Connect rationale, widget).
# Library components protected by a signature/system permission are allowed automatically.
EXPORTED_ALLOWLIST = {
    ".MainActivity", "com.nirogbhumi.app.MainActivity",
    ".health.PermissionsRationaleActivity", "com.nirogbhumi.app.health.PermissionsRationaleActivity",
    ".ViewPermissionUsageActivity", "com.nirogbhumi.app.ViewPermissionUsageActivity",
    ".widget.HealthQuickLogWidgetReceiver", "com.nirogbhumi.app.widget.HealthQuickLogWidgetReceiver",
    # Library components that must be exported to work, reviewed once:
    # Firebase Auth's browser-return activities (OAuth / reCAPTCHA redirects) and the
    # Health Connect SDK's bind service that Health Connect itself connects to.
    "com.google.firebase.auth.internal.GenericIdpActivity",
    "com.google.firebase.auth.internal.RecaptchaActivity",
    "androidx.health.platform.client.impl.sdkservice.HealthDataSdkService",
}


def find_default_manifests():
    patterns = [
        "app/build/intermediates/merged_manifests/release*/**/AndroidManifest.xml",
        "app/build/intermediates/merged_manifest/release*/**/AndroidManifest.xml",
        "app/build/intermediates/packaged_manifests/release*/**/AndroidManifest.xml",
    ]
    found = []
    for pattern in patterns:
        found += glob.glob(pattern, recursive=True)
    return sorted(set(found))


def check(path):
    problems = []
    root = ET.parse(path).getroot()
    # tools:node="remove" entries only exist in source manifests to strip a
    # permission that a library would merge in; they are not shipped.
    tools_node = "{http://schemas.android.com/tools}node"
    permissions = sorted({
        e.get(ANDROID + "name")
        for e in root.iter("uses-permission")
        if e.get(ANDROID + "name") and e.get(tools_node) != "remove"
    })
    print(f"\n{path}\n  permissions ({len(permissions)}):")
    for name in permissions:
        print(f"    {name}")
    for name in permissions:
        if name in FORBIDDEN_PERMISSIONS:
            problems.append(f"forbidden permission {name}: {FORBIDDEN_PERMISSIONS[name]}")

    app = root.find("application")
    if app is None:
        return [f"{path}: no <application> element"]
    if app.get(ANDROID + "debuggable") == "true":
        problems.append("release build is debuggable")
    if app.get(ANDROID + "usesCleartextTraffic") == "true":
        problems.append("release build allows cleartext traffic")
    if app.get(ANDROID + "allowBackup") == "true":
        problems.append("release build allows app data backup (health data would leave the device via cloud backup)")

    for tag in ("activity", "activity-alias", "service", "receiver", "provider"):
        for element in app.iter(tag):
            if element.get(ANDROID + "exported") != "true":
                continue
            name = element.get(ANDROID + "name") or "?"
            if element.get(ANDROID + "permission"):
                continue  # protected by a permission (e.g. Firebase/WorkManager receivers)
            if name not in EXPORTED_ALLOWLIST:
                problems.append(f"unreviewed exported {tag}: {name}")
    return problems


def main(argv):
    paths = argv[1:] or find_default_manifests()
    if not paths:
        print("ERROR: no merged release manifest found under app/build/intermediates - run bundleRelease first", file=sys.stderr)
        return 2
    all_problems = []
    for path in paths:
        all_problems += [f"{path}: {p}" for p in check(path)]
    if all_problems:
        print("\nRelease manifest check FAILED:", file=sys.stderr)
        for problem in all_problems:
            print(f"  - {problem}", file=sys.stderr)
        return 1
    print(f"\nRelease manifest check passed ({len(paths)} manifest file(s)).")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
