# Production release checklist

## Firebase owner actions

- Create production and staging Firebase projects in the India-compatible region selected by the owner.
- Register `in.nirogbhumi.app`; add `app/google-services.json` and SHA-1/SHA-256 certificates.
- Enable Phone and Email/Password Auth, Firestore, Storage, Functions, Messaging, Analytics, Crashlytics, and App Check.
- Deploy rules, indexes, and Functions; test rules with separate user, assigned expert, unassigned expert, admin, and anonymous sessions.
- Configure budget alerts, retention, backups, least-privilege IAM, staff custom claims, and audit-log monitoring.
- Add Razorpay/UPI secrets through Firebase secret management; never commit them.

## Health and privacy

- Obtain Indian privacy/health-law review for the final legal text and consent records.
- Publish Terms, Privacy Policy, Medical Disclaimer, Consent Notice, Data Deletion, Refund, Shipping, and Program Terms on the production domain.
- Verify export and deletion fulfillment, incident response, staff access review, and vendor agreements.
- Complete Google Play Data safety and Health apps declaration using the final SDK/data inventory.

## Device acceptance

- Phone OTP: success, invalid, resend, quota, SIM/network failure, and account disabled.
- All 86 screen routes at 390×844, small Android, tablet, font scale 1.3, dark system bars, TalkBack, Hindi text expansion, offline/reconnect, and process death.
- Sugar/BP caution thresholds reviewed and signed off by a qualified clinician.
- Private uploads cannot be accessed by another user or an unassigned expert.
- Consultation payment/webhook idempotency, refund, reschedule, and failure recovery.
- Order stock transaction, payment idempotency, cancellation, refund, and delivery updates.
- Notification quiet hours, permissions, per-day cap, timezone, and medication safety copy.

## Play Console

- Use a private upload key and Play App Signing; build `bundleRelease` with release environment variables.
- Create the Play Console app for `in.nirogbhumi.app`, enable the Google Play Developer API, and grant a least-privilege Play service account access to the app.
- Add `GOOGLE_PLAY_SERVICE_ACCOUNT_JSON` as a GitHub Actions production secret containing the Play service-account JSON.
- Use `.github/workflows/upload-google-play.yml` to build the signed AAB and upload it to the selected Play track after release secrets are configured.
- For a brand-new draft app, upload the first build as `draft` or complete the first manual Play Console setup before promoting automated uploads.
- Upload icon, feature graphic, phone/tablet screenshots, short/full descriptions, support email/site, privacy URL, and account deletion URL.
- Run internal, closed, and staged production tracks; resolve pre-launch, accessibility, crash, ANR, and security findings.
