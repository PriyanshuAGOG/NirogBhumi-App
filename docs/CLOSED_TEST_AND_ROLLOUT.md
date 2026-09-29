# Closed test, staged rollout and go-live

The path from "CI is green" to "live for everyone", in order. Each step says who does it
(**Owner** = needs Play Console / Firebase Console / GCP access; **Code** = already
automated in this repo).

## 0. Before the first upload (Owner, one time)

- [ ] Play developer account verified; app `in.nirogbhumi.app` created (Health & Fitness, free).
- [ ] GitHub environment `production` has these secrets: `GOOGLE_SERVICES_JSON_BASE64`,
      `ANDROID_KEYSTORE_BASE64`, `ANDROID_STORE_PASSWORD`, `ANDROID_KEY_ALIAS`,
      `ANDROID_KEY_PASSWORD`, `GOOGLE_PLAY_SERVICE_ACCOUNT_JSON`.
      *Upload key*: create once with `keytool -genkeypair -v -keystore upload.jks -alias upload -keyalg RSA -keysize 2048 -validity 10000`; back it up somewhere safe (a password manager + an offline copy). With **Play App Signing** (default) a lost upload key can be reset by Google; a lost *signing* key cannot.
- [ ] Play service account created (Play Console > Setup > API access) with release permission for this app only.
- [ ] Cloud Functions callable invoker fixed (org policy `iam.allowedPolicyMemberDomains`; see `docs/RELEASE_CHECKLIST.md`). Confirm the **Deploy Firebase** run on `main` ends green.
- [ ] Firebase Console > Project settings > your Android app: add the **upload key** SHA-1/SHA-256 (the first `Upload Android App Bundle` run prints them) **and**, after the first upload, the **Play App Signing** certificate SHA-1/SHA-256 (Play Console > Setup > App signing). Both are required for phone auth, Google sign-in and App Check (Play Integrity).
- [ ] Firebase Console > App Check: register the Android app with **Play Integrity**; monitor first, **enforce** Firestore/Storage/Functions only after the closed test shows verified traffic.
- [ ] Legal pages have no `[placeholder]` (see `docs/PLAY_CONSOLE_DECLARATIONS.md`).
- [ ] Console (Play) forms completed: App content, Data safety, Health apps, Health Connect, Store listing.

## 1. First upload — internal testing (Owner triggers, Code builds)

Run **Actions > Upload Android App Bundle to Google Play** with: track `internal`,
status `draft`, version name `1.0.0`, *changes not sent for review* on.

What the workflow does for you: runs unit tests and `lintRelease`, builds the signed AAB
with a rising version code (commit count), verifies the signature and the merged
manifest, prints the upload-key fingerprints, uploads the AAB **and** the R8 mapping file.

Then in Play Console: create the internal release from the draft, add the team (up to 100
by email), and install from the opt-in link.

**Internal-test acceptance (on at least 2 real phones — one small screen / large font, one recent Android)**

1. Install from Play. Cold start < ~3 s to the welcome screen; no debug banner, no
   "Developer settings" row (those are debug-only).
2. Sign up with email; sign in with phone OTP; sign in with Google (needs the Play signing SHA).
3. Consent screens, profile (fields blank), goal, join a batch with a real code.
4. Log sugar / BP / weight / sleep / walk / meal photo; see trends; offline: airplane mode →
   log → reconnect syncs.
5. Health Connect: grant, sync, deny; the rationale screen opens from the Health Connect settings.
6. Care: Plans & guidance shows what the coach posted; push arrives when "Notify" is on;
   tapping opens the right screen.
7. Request a consultation; the admin confirms it; push + "My consultations" update; reminder
   an hour before; cancel works.
8. Chat: send text/photo/voice; report a message; coach sees it in Moderation.
9. Export my data (file arrives), schedule deletion, cancel deletion. (Do the *real* deletion only with a throwaway account.)
10. TalkBack on Today, the consultation form and Data controls; font scale 1.3; dark mode; rotation; process death (background 30 min).
11. Play **pre-launch report** (Play Console > Testing > Pre-launch report): no crashes,
    accessibility warnings triaged.

## 2. Closed test (Owner)

New **personal** developer accounts must run a closed test with **≥12 opted-in testers for
14 continuous days** before Play allows a production application. (Organisation accounts
are exempt — check which type this account is.)

- Recruit 15–20 people (buffer for drop-outs): a mix of real prospective members and the
  coaches. Add them as a Google Group/email list to a **closed testing** track.
- Promote the *same build* that passed internal testing (Actions run again with track `alpha`
  only if the code changed — otherwise use *Promote release* in the Console so the exact
  artifact is kept).
- Keep them opted in for the full 14 days; ask each to open the app at least every 2–3 days.
  Play measures opt-in, not engagement, but real use is what produces the useful bugs.
- Collect feedback in one place (a form + a WhatsApp group). Triage daily; ship fixes as a
  new closed-test build (a new upload restarts nothing — the 14 days count opt-in time).
- Watch **Firebase Crashlytics** (crash-free users ≥ 99.5%) and **Play vitals**
  (ANR < 0.47%, crash < 1.09% are the "bad behaviour" thresholds).

## 3. Apply for production access (Owner)

After day 14 Play Console shows *Apply for production*. Answer the questions with the real
test summary (what testers did, what was fixed). Approval usually takes up to a few days.

## 4. Production — staged rollout (Owner triggers, Code builds)

1. Track `production`, status `inProgress`, **release percentage 5%** (set in the Console).
2. Watch for 24–48 h: Crashlytics, Play vitals, Cloud Functions errors, Firestore usage,
   `errorReports` in the console, support inbox.
3. Raise to 20% → 50% → 100% only while the numbers stay inside the thresholds in §2.
4. **Halt** (Console > Halt rollout, or the workflow's `halted` status) on: a crash-free
   drop below 99%, any data-integrity or privacy defect, or login failing for a
   meaningful group.

## 5. After launch

- Backend deploys continue from `main` (Deploy Firebase). Rules/functions changes must be
  backward compatible with the oldest app version still in use — old installs remain for
  weeks. Never remove a field/collection/callable the previous release reads until that
  release is below ~2% of users.
- Keep `CONSENT_VERSION` in step with the legal pages when the policy changes materially.
- Each release: bump nothing by hand — the version code is stamped from the commit count;
  update `play/whatsnew/*` with what changed.
