# Go-live guide: everything left between today and a production release

Written after PR #19 merged (`main` at `fcc128a`) and Deploy Firebase run 36 succeeded (all callable functions
public-invokable). Status is stated plainly: **built and CI-tested** is not the same as **verified on a real phone**
or **verified in production**. Nothing below is ticked until someone has done it.

Official contact address everywhere: `priyanshu@nirogbhumi.com`. Project `nirog-bhumi-app`, app `in.nirogbhumi.app`,
region `asia-south1`. Click-by-click detail for several steps is in `docs/OWNER_SETUP_GUIDE.md`; rollout order is in
`docs/CLOSED_TEST_AND_ROLLOUT.md`.

Legend: **You** = needs your logins. **Gautam** = needs the Workspace/organization admin. **Claude** = I can do it once
you give me the input. **Both** = joint.

---

## Where things stand

| Area | State |
|---|---|
| Code on `main`, CI | Merged; branch CI was fully green (Android tests + lint, R8 release build, rules, functions, console e2e) |
| Firestore/Storage rules, indexes, Hosting (console, legal pages), Cloud Functions | Deployed from `main`; invoker step green |
| Tester APK from `main` | Built (run 149); not yet checked on a phone by anyone |
| Org policy for callable functions | Lifted by Gautam (confirmed by deploy) |
| Live check of callables in production | **Not done** (I could not reach them from my environment) |
| Email sending | **Not set up** (extension not installed) |
| Signed download links | **Not set up** (IAM grant pending) |
| Legal pages | Contact + Grievance Officer filled; entity, address, dates still placeholders |
| Real-device QA, staging, clinician sign-off, Play listing, closed test | **Not done** |

---

## Phase 1. Make production actually work (do this week)

1. **Check the callables from the real console and a phone** (You, 30 min). Open `https://nirog-bhumi-app.web.app`,
   sign in, post an announcement. On a phone with the tester APK, sign up and redeem an access code. If anything
   fails, send me the error text.
2. **Become super admin** (You). Create the Auth user `priyanshu@nirogbhumi.com` if missing (Firebase Console >
   Authentication > Users), sign in to the console, use the Users page bootstrap, sign out and in again.
3. **Install Trigger Email from Firestore** (You, 15 min). Firebase Console > Extensions. Location `asia-south1`,
   collection `mail`, FROM `Nirog Bhumi <priyanshu@nirogbhumi.com>`, REPLY-TO the same, SMTP URI from your mail host
   (Google Workspace needs an app password; Gautam must allow it, or use Brevo/SendGrid). Test by adding a `mail`
   document and checking `delivery.state = SUCCESS`, then delete it.
4. **Mailbox hygiene** (You + Gautam). SPF, DKIM and DMARC for nirogbhumi.com so exports and alerts do not land in
   spam.
5. **Runtime permissions for the functions** (You, 2 min; found in production on 2026-10-20: without these every data
   function fails with PERMISSION_DENIED / HTTP 500). Functions run as the Compute default account, which new
   organizations create with no data roles:
   ```bash
   SA="126409331898-compute@developer.gserviceaccount.com"
   for r in roles/datastore.user roles/firebaseauth.admin roles/storage.objectAdmin roles/firebasecloudmessaging.admin; do
     gcloud projects add-iam-policy-binding nirog-bhumi-app --member="serviceAccount:$SA" --role="$r" --condition=None --quiet
   done
   ```
   Then the signed-link grant below.
   **Signed-link grant** (You, 2 min, Cloud Shell):
   ```bash
   RUNTIME_SA="126409331898-compute@developer.gserviceaccount.com"  # the account the functions run as
   gcloud iam service-accounts add-iam-policy-binding "$RUNTIME_SA" --project=nirog-bhumi-app \
     --member="serviceAccount:${RUNTIME_SA}" --role="roles/iam.serviceAccountTokenCreator"
   ```
   Check: request a data export in a test account; Download works and the email link opens.
6. **Put the official email in the outside-the-code places** (You): Firebase Auth Google provider support email,
   OAuth consent screen (support + developer contact, authorized domain, privacy URL), Play contact details, billing
   alert recipients, Crashlytics alerts.
7. **Billing safety** (You). Cloud Billing > Budgets & alerts: set a monthly budget with alerts to the official
   address. Functions are public-invokable, so a flood of rejected calls costs a little; an alert catches it.
8. **Tidy Gautam's temporary access** (You). Remove the Service Usage Consumer role you gave him on the project if
   he no longer needs it.

## Phase 2. Legal and policy (blocks the Play upload)

9. **Send me the legal details** (You, then Claude): the entity is filled in as Nirog Bhumi Private Limited (tell me if
    the registered name differs, e.g. spelling or "Pvt. Ltd."). Still needed: registered office / postal address,
   Grievance Officer's full legal name (if wanted), and the publication date. I fill them in and push; the strict
   release gate (`scripts/check_release_gates.py --strict`) refuses closed/production uploads until no `[placeholder]`
   is left.
10. **Counsel review** (You). Have an Indian privacy/health-law professional read the privacy policy, terms, deletion
    page and consent text. They are drafts, as the page footers say. If the text changes materially, I bump
    `CONSENT_VERSION` together with the pages.
11. **Data processor agreements** (You). Google (Firebase/Cloud), your mail provider. Keep copies.
12. **Clinician sign-off** (You). A qualified clinician confirms or changes the blood-sugar and BP alert levels
    (urgent below 54 or at/above 300; low below 70; high above 130, or 180 after a meal). If they change them, tell
    me and I change app and server together (tests pin the boundaries).

## Phase 3. Real-device QA (nothing has run on a physical phone yet)

13. **Run the matrix** in `docs/DEVICE_QA_MATRIX.md` on at least two phones (one small screen with large font, one
    recent Android): sign-up with phone OTP, email, Google; consent; profile; join a batch; log each health metric;
    correct an entry inside 60 minutes and confirm it is refused after; sleep AM/PM; trends; offline then
    reconnect; Health Connect grant/sync/deny; export (ZIP, email, link); deletion (typed phrase, re-sign-in,
    banner, cancel); push notifications; TalkBack; font scale 1.3; dark mode; rotation; process death.
14. **Record results** (You). A pass/fail per row with date and device. Send me failures and I fix them. Do a real
    deletion only with a throwaway account.
15. **Staging project** (You, then Claude). Create `nirog-bhumi-staging`, then I rehearse rules, functions and the
    health-data backfill there (`docs/MIGRATION_RUNBOOK.md`: dry run first, review the report, then production). The
    backfill has never been run on real data.

## Phase 4. Signing and Play Console (You)

16. **Upload key**: `keytool -genkeypair -v -keystore upload.jks -alias upload -keyalg RSA -keysize 2048 -validity 10000`.
    Keep two backups (password manager plus offline).
17. **GitHub secrets** (Settings > Environments > `production`): `GOOGLE_SERVICES_JSON_BASE64`,
    `ANDROID_KEYSTORE_BASE64`, `ANDROID_STORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD`,
    `GOOGLE_PLAY_SERVICE_ACCOUNT_JSON`. Never paste these in chat.
18. **Play service account**: Play Console > Setup > API access; release permission for this app only.
19. **Play app**: create `in.nirogbhumi.app` (Health & Fitness, free); Play App Signing on.
20. **SHA fingerprints in Firebase**: add the upload-key SHA-1/SHA-256 (printed by the first upload run) and, after
    the first upload, the Play App Signing SHA-1/SHA-256, to the Android app in Firebase Project settings. Phone
    sign-in, Google sign-in and App Check need both.
21. **App Check**: register with Play Integrity, monitor first, enforce Firestore/Storage/Functions only after the
    closed test shows verified traffic.
22. **Fill the Play forms** from `docs/PLAY_CONSOLE_DECLARATIONS.md` and `docs/DATA_SAFETY_MAPPING.md`: Data safety,
    Health apps declaration, Health Connect permissions, content rating, target audience (adults 18+), ads (none),
    store listing (`docs/PLAY_STORE_LISTING.md`, graphics in `play/`). Privacy policy URL
    `https://nirog-bhumi-app.web.app/legal/privacy-policy.html` and the account-deletion URL
    `.../legal/account-deletion.html` (a hard Play requirement) must open without signing in. Check both in a private
    window first.
23. **Payments classification**: the app takes no payments in v1 (see `docs/PLAY_PAYMENTS.md`). Keep the store card
    on "coming soon" unless you pass `-PSTORE_URL` for physical goods.

## Phase 5. Testing tracks and launch

24. **Internal track** (You trigger): Actions > *Upload Android App Bundle to Google Play*: track `internal`,
    status `draft`, version name `1.0.0`. Add the team in Play Console and run the acceptance list in
    `docs/CLOSED_TEST_AND_ROLLOUT.md` section 1, plus the Play pre-launch report.
25. **Closed test**: the developer account is an organization account (Nirog Bhumi Private Limited), which Play
    exempts from the "12 testers for 14 days" rule. Confirm in Play Console (Dashboard) that no such requirement is
    shown. A closed test is still strongly advised: recruit 10 to 20 real people for at least a week, keep a single
    feedback channel, watch Crashlytics (crash-free users at least 99.5%) and Play vitals.
26. **Production access**: if Play Console asks you to apply, answer with an honest test summary; otherwise create the
    production release directly.
27. **Staged rollout**: production, `inProgress`, 5%; watch 24 to 48 hours (Crashlytics, vitals, Cloud Functions
    errors, support inbox); then 20%, 50%, 100% while numbers stay healthy. Halt on a crash-free drop below 99%, any
    privacy or data-integrity defect, or login failing for a group.
28. **Day-one operations** (`docs/RUNBOOK.md`): who watches the support inbox, who handles a deletion or export
    complaint (DPDP response period), how to roll back (revert on `main`, redeploy; Play halt), staff access review.

---

## What I (Claude) will do when you give me the input

- Fill the legal placeholders and push (needs step 9).
- Apply clinician threshold changes in app and server together (step 12).
- Fix anything the real-device QA finds (step 14).
- Rehearse the migration and backfill on staging and run the production backfill dry run (step 15).
- Re-run Deploy Firebase and verify after each backend change, and keep CI green. I do not merge or open PRs unless
  you ask.

## Known gaps (not hidden)

No real-device QA, no staging rehearsal, backfill never run on real data, no penetration test, no Android dependency
scan, journey e2e has 16 checks rather than 32, some debug-only admin and expert screens still carry old wording
(not shipped in release builds).

## Suggested order (shortest path)

1-8 (production works) → 9 and 12 (legal details, clinician) → 13-14 (phones) → 16-23 (signing, Play forms) →
24 (internal) → 25-26 (closed test, about a week; run 15 and 10 in parallel) → 27 (staged rollout).
