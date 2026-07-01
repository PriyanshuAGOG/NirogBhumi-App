# APK testing guide

This is the current distribution plan: a debug APK you install directly on a
physical Android device, built by GitHub Actions so nobody needs a local
Android SDK.

## 1. One-time: get a Firebase-connected build

The debug APK needs `app/google-services.json` for project `nirog-bhumi-app`
(Android app `1:126409331898:android:b4e478566dcbeeb7009e83`). Without it the
APK still installs, but auth/Firestore/Storage report "Firebase not
configured".

1. Add `GOOGLE_SERVICES_JSON_BASE64` as a secret on the repo's `production`
   GitHub environment. See `docs/github-production.env.example` for how to
   produce it (either via local Firebase CLI + `base64 -w 0`, or by running
   `.github/workflows/setup-firebase-project.yml` and base64-encoding the
   `google-services-json` artifact it uploads).
2. Run `.github/workflows/build-firebase-debug-apk.yml`
   (Actions tab > "Build Firebase-connected debug APK" > Run workflow).
3. When it finishes, open the run and download the
   `nirog-bhumi-firebase-debug-apk` artifact (a zip containing `app-debug.apk`).

## 2. Install on your phone

1. Unzip the artifact to get `app-debug.apk` and copy it to your phone
   (email, cloud drive, USB, or `adb push`).
2. On the phone: Settings > Apps > Special access > Install unknown apps,
   allow it for whichever app you used to open the file (Files, Chrome,
   Gmail, etc.). This is only needed because the APK isn't from the Play
   Store.
3. Open the APK file on the phone and tap Install.
4. If you have `adb` and a cable instead: enable Developer Options > USB
   debugging, then `adb install -r app-debug.apk`.

## 3. What to test

- Phone OTP and email/password sign-in (Phone provider must be enabled in
  Firebase Console > Authentication > Sign-in method, with test numbers
  added there for pre-launch testing).
- Onboarding/consent flow, dashboard tabs (Today/Track/Insights/Care/Learn).
- Logging a metric (sugar, BP, sleep, steps, water) and confirming it
  persists after killing and reopening the app (offline cache).
- Notification permission prompt and a test push (Cloud Messaging).
- Any Health Connect permission prompts on supported devices.

## 4. Reinstalling after a new build

Re-run the same workflow, download the new artifact, and reinstall — debug
builds share a signature so `adb install -r` (or a plain re-install) upgrades
in place without uninstalling first.

## 5. Moving beyond APK-only distribution

Once OTP quota/billing, the Play Console app, and a signing key are set up,
switch to `.github/workflows/release-android.yml` (signed `bundleRelease`)
and `.github/workflows/upload-google-play.yml` per `docs/RELEASE_CHECKLIST.md`.
