# Production release checklist

## Firebase owner actions

- **BLOCKER — lift the org policy blocking callable-function invoker.** The GCP org policy `iam.allowedPolicyMemberDomains` (Domain Restricted Sharing) prevents binding the public (`allUsers`) Cloud Run invoker that HTTPS **callable** functions require. Any *newly created* callable then fails to deploy at the "set invoker" step and returns 403 when called. This currently blocks `createAnnouncement`, `deleteAnnouncement`, `markAnnouncementSeen`, and `previewAnnouncementAudience` — so **posting announcements and "seen by N" are broken** until fixed. Fix (one-time): in GCP Console → IAM & Admin → Organization Policies → `iam.allowedPolicyMemberDomains`, add a **project-level override** for `nirog-bhumi-app` set to *Allow All* (or add your allowed values plus `allUsers`/`allAuthenticatedUsers`); alternatively grant the deploy service account `roles/run.admin` if the cause is a missing role. Then re-run the **Deploy Firebase** workflow — the invoker binds and the deploy goes green. This fixes every current and future callable at once; the alternative is refactoring those callables to the Firestore-trigger dispatch pattern (no public invoker).
- **How to lift it (exact steps, needs the Organization Policy Administrator role, ~2 minutes).** Console: IAM & Admin > Organization policies > *Domain restricted sharing* (`iam.allowedPolicyMemberDomains`) > select project `nirog-bhumi-app` > Manage policy > *Override parent's policy* > Rules: *Allow all* > Set policy. Or with gcloud:
  ```bash
  cat > /tmp/allow-all.yaml <<'EOF'
  name: projects/nirog-bhumi-app/policies/iam.allowedPolicyMemberDomains
  spec:
    rules:
      - allowAll: true
  EOF
  gcloud org-policies set-policy /tmp/allow-all.yaml
  ```
  Then re-run **Deploy Firebase** (Actions > Deploy Firebase > Run workflow on `main`). Every callable turns green in the "Ensure public invoker" step, and the retired `queueDeletionRequest` stub can be deleted (see the comment in `firebase/functions/src/index.ts`). Until then **every callable is unreachable**: sign-up batch join, access codes, announcements, data export/deletion and consultation cancel all fail with UNAUTHENTICATED, so do not ship to Play before this is done. Verify afterwards from the console: post an announcement, and from a test phone redeem a code.
- Create production and staging Firebase projects in the India-compatible region selected by the owner.
- Register `in.nirogbhumi.app`; add `app/google-services.json` and SHA-1/SHA-256 certificates.
- Enable Phone and Email/Password Auth, Firestore, Storage, Functions, Messaging, Analytics, Crashlytics, and App Check.
- Deploy rules, indexes, and Functions; test rules with separate user, assigned expert, unassigned expert, admin, and anonymous sessions.
- Configure budget alerts, retention, backups, least-privilege IAM, staff custom claims, and audit-log monitoring.
- Add Razorpay/UPI secrets through Firebase secret management; never commit them.
- **Email for exports and support (owner).** Data-export links and support-request alerts are written to the Firestore `mail` collection; something has to send them. Install the Firebase extension **Trigger Email from Firestore** (SMTP or SendGrid), then set the function environment variable `SUPPORT_EMAIL` to the inbox that should receive support requests (for example `support@nirogbhumi.com`; until it is set support alerts are skipped and marked `skipped_not_configured`). Without the extension, exports still work in the app; only the email copy is missing.
- **Signed export/share links (owner).** Give the Functions runtime service account the `roles/iam.serviceAccountTokenCreator` role on itself (same one-time grant as `docs/health-file-signed-url-setup.md`). Without it the app falls back to its own authenticated download and emails say "open the app".
- **Staging project (owner).** Create a staging Firebase project. The health-data backfill (`docs/MIGRATION_RUNBOOK.md`), rules changes and function changes are rehearsed there before production.
- **Store address (owner, optional).** Pass `-PSTORE_URL=https://nirogbhumi.com/shop/` to the Gradle build only when physical-goods checkout is ready (see `docs/PLAY_PAYMENTS.md`); otherwise the store card stays "coming soon".

## Health and privacy (DPDP Act 2023 + Play)

- Obtain Indian privacy/health-law review for the final legal text and consent records.
- The canonical legal pages now live as hosted static files in `console/public/legal/` (served at `<console-domain>/legal/...`). Replace every `[placeholder]` (operating entity, dates, retention periods, Grievance Officer) before publishing; ideally also publish at the primary domain. These cover Privacy Policy, Terms, Medical Disclaimer, Account Deletion, and the Grievance mechanism.
- **Appoint a Grievance Officer** (DPDP s.13) and publish their name, working mailbox (e.g. `grievance@nirogbhumi.com`), and postal address in the legal pages and the in-app Legal Center.
- Put the **hosted Privacy Policy URL** and the **hosted account-deletion URL** (`/legal/account-deletion.html`, reachable without installing the app) into the Play Console listing and Data Safety form — the deletion URL is a hard Play requirement.
- Complete the Play **Data Safety** form and **Health apps declaration** using `docs/DATA_SAFETY_MAPPING.md`; confirm "no data shared for advertising".
- `REQUEST_INSTALL_PACKAGES`, the advertising ID and any undeclared permission are blocked automatically: the CI "Android release build" job runs `scripts/verify_release_manifest.py` on the merged release manifest (also checks debuggable, cleartext, backup and exported components).
- Verify export and deletion fulfillment end-to-end in production, incident response, staff access review, and processor (DPA) agreements.
- On any material policy change, bump `CONSENT_VERSION` (`NirogModels.kt`) together with the hosted pages. Members who accepted an earlier version see an "updated privacy notice" banner and accept it again (a new dated receipt is written); it is now `2026-09`. See `docs/DPDP_COMPLIANCE.md` for the full obligation map.

## Device acceptance

- Phone OTP: success, invalid, resend, quota, SIM/network failure, and account disabled.
- All 86 screen routes at 390×844, small Android, tablet, font scale 1.3, dark system bars, TalkBack, Hindi text expansion, offline/reconnect, and process death.
- Sugar/BP caution thresholds reviewed and signed off by a qualified clinician.
- Private uploads cannot be accessed by another user or an unassigned expert.
- Consultation requests (no payment in the app): request -> "waiting" -> confirmed in the console -> push + 1-hour reminder -> reschedule/decline/cancel; Plans & guidance from the coach; Data Controls (export, schedule/cancel deletion). Payments, a store and refunds are out of scope for v1.
- Notification quiet hours, permissions, per-day cap, timezone, and medication safety copy.

## Play Console

> Step-by-step: `docs/CLOSED_TEST_AND_ROLLOUT.md` (order of operations), `docs/PLAY_CONSOLE_DECLARATIONS.md` (answers to paste), `docs/RUNBOOK.md` (operations). `scripts/check_release_gates.py` runs automatically before every upload.

- Use a private upload key and Play App Signing; build `bundleRelease` with release environment variables.
- Create the Play Console app for `in.nirogbhumi.app`, enable the Google Play Developer API, and grant a least-privilege Play service account access to the app.
- Add `GOOGLE_PLAY_SERVICE_ACCOUNT_JSON` as a GitHub Actions production secret containing the Play service-account JSON.
- Use `.github/workflows/upload-google-play.yml` to build the signed AAB and upload it to the selected Play track after release secrets are configured.
- Review `docs/PLAY_STORE_LISTING.md`, then copy the approved short/full descriptions and production URLs into Play Console.
- Keep localized release notes in `play/whatsnew/`; the Play upload workflow sends these notes with each AAB upload.
- For a brand-new draft app, upload the first build as `draft` or complete the first manual Play Console setup before promoting automated uploads.
- Upload icon, feature graphic, phone/tablet screenshots, short/full descriptions, support email/site, privacy URL, and account deletion URL.
- Run internal, closed, and staged production tracks; resolve pre-launch, accessibility, crash, ANR, and security findings.
