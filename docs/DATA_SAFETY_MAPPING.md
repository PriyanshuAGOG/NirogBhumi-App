# Google Play Data Safety — declaration mapping

What to declare in the Play Console **Data Safety** form, mapped from the data
the app actually collects (source of truth: the collections read by
`exportUserData` in `firebase/functions/src/index.ts`, the manifest
permissions, and the SDKs in `app/build.gradle.kts`). Verify against the final
build before submitting.

## Summary answers

- **Does your app collect or share user data?** Yes (collect). **Share:** No —
  data is processed by service providers (Firebase/Cloud, payment gateway,
  FCM, Crashlytics) on our behalf, which Play treats as "processing", not
  "sharing", provided they only act on our instructions. Declare **no data
  shared for advertising or analytics with third parties**.
- **Is all collected data encrypted in transit?** Yes.
- **Do you provide a way to request data deletion?** Yes — in-app and the
  hosted `account-deletion.html` URL.
- **Is data collection required or optional?** Health data is required to use
  the app; anonymized-research and product-update consents are optional.

## Data types collected

| Play data type | Collected | Purpose(s) | Required? |
|---|---|---|---|
| Name | Yes | App functionality, account | Required |
| Email address | Yes | Account, App functionality | Required |
| Phone number | Yes | Account (OTP), App functionality | Required |
| User IDs | Yes | Account, App functionality | Required |
| City / approximate area (text field) | Yes | App functionality (program context) | Optional |
| **Health info** (glucose, BP, sleep, weight, activity, medications, check-ins, lab reports) | Yes | App functionality | Required |
| Fitness info (steps/activity, incl. Health Connect import) | Yes | App functionality | Required |
| Photos (profile photo, uploaded lab report images) | Yes | App functionality | Optional |
| Voice/audio (chat voice notes; on-device speech recognition) | Yes | App functionality | Optional |
| Purchase history (past orders shown read-only; consultation requests carry a fee *note* only — the app takes no payment) | Yes | App functionality | Optional |
| Messages (coach chat, coach inbox, consultation request text: concern, preferred time, support requests emailed to our support inbox) | Yes | App functionality, Customer support | Optional |
| Health profile answers (diabetes type incl. optional free text for "Other", blood-pressure status, medication yes/no, goals) | Yes | App functionality, Personalization | Optional |
| App activity / in-app actions | Yes | Analytics, App functionality | Optional |
| Crash logs & diagnostics | Yes | App functionality (stability) | Optional |
| Device or other IDs | Yes | App functionality, Analytics | — |
| **Precise or coarse location (GPS)** | **No** | — | — |
| Contacts | No | — | — |

## Reassessment after the health-data and export changes

Re-checked against the code in this branch; the declarations above still hold, with these clarifications:

- **Export file.** A member's data export is a ZIP built on request, stored privately, reachable only through a 15-minute
  signed link or their own authenticated access; an email with a 3-hour link may be sent. The email contains no health values.
  The ZIP is deleted automatically after 30 days and is erased with the account.
- **Support requests** are emailed to our support inbox (text the member typed: subject, message, app version).
  Declared under Messages / Customer support. They are not shared with anyone else.
- **Education content** is fetched from our own website (nirogbhumi.com) and a short copy of the article list is saved on the phone for offline use. No user data is sent with the request beyond a search word the member types.
- **In-app web page (store)** is disabled in builds without a store address; when enabled it loads only our own host, with no
  JavaScript bridge, no file/camera/microphone/location access and third-party cookies off.
- **No new permissions.** Health Connect stays read-only (steps, sleep, weight, blood glucose, blood pressure); no advertising
  ID; no location.
- **Deletion wording.** The app says what is erased, what is kept by law (payment records, security logs) and that copies in
  our cloud provider's backups clear on their own schedule; Health readings are erased unless the member opted into anonymised research.

## Security & deletion section

- Data encrypted in transit: **Yes**.
- Users can request data deletion: **Yes** — link the hosted
  `/legal/account-deletion.html` URL.
- Committed to Play Families / independent security review: as applicable.

## Health apps declaration

This is a **health app** (health condition management / self-monitoring).
Complete the Play **Health apps declaration** and, if you surface Health
Connect data, the Health Connect–specific declaration. Do not use Health
Connect data for advertising; access only the record types the app actually
reads (steps, sleep, weight, blood glucose, blood pressure — see
the manifest `health.READ_*` permissions). Heart rate is **not** requested
(removed in Sprint 1; Play rejects unused Health Connect permissions).

## Advertising ID

The app declares **no advertising ID use**. `com.google.android.gms.permission.AD_ID`
is removed from the merged manifest (`tools:node="remove"`), and CI fails if it,
or any permission not in the allowlist, reappears (`scripts/verify_release_manifest.py`).

## Permissions to justify in the listing

| Permission | Why | Notes |
|---|---|---|
| `INTERNET` | Sync with Firebase | — |
| `POST_NOTIFICATIONS` | Reminders, care updates | Runtime-requested |
| `RECORD_AUDIO` | Chat voice notes + voice entry of readings | Not for background/continuous capture |
| `health.READ_*` | Import steps/sleep/weight/glucose/BP from Health Connect | Health Connect declaration required |
| `REQUEST_INSTALL_PACKAGES` | **Debug build only** — tester sideload self-update | Absent from the Play AAB (in `src/debug` manifest) |

> Note: `REQUEST_INSTALL_PACKAGES` must **not** appear in the release AAB. It
> lives in `app/src/debug/AndroidManifest.xml` only; confirm it is absent from
> the uploaded bundle's merged manifest.
