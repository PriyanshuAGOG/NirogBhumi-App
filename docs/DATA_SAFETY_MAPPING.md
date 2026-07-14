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
| Purchase history (orders, bookings) | Yes | App functionality | Optional |
| App activity / in-app actions | Yes | Analytics, App functionality | Optional |
| Crash logs & diagnostics | Yes | App functionality (stability) | Optional |
| Device or other IDs | Yes | App functionality, Analytics | — |
| **Precise or coarse location (GPS)** | **No** | — | — |
| Contacts | No | — | — |

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
reads (steps, sleep, weight, heart rate, blood glucose, blood pressure — see
the manifest `health.READ_*` permissions).

## Permissions to justify in the listing

| Permission | Why | Notes |
|---|---|---|
| `INTERNET` | Sync with Firebase | — |
| `POST_NOTIFICATIONS` | Reminders, care updates | Runtime-requested |
| `RECORD_AUDIO` | Chat voice notes + voice entry of readings | Not for background/continuous capture |
| `health.READ_*` | Import steps/sleep/weight/HR/glucose/BP from Health Connect | Health Connect declaration required |
| `REQUEST_INSTALL_PACKAGES` | **Debug build only** — tester sideload self-update | Absent from the Play AAB (in `src/debug` manifest) |

> Note: `REQUEST_INSTALL_PACKAGES` must **not** appear in the release AAB. It
> lives in `app/src/debug/AndroidManifest.xml` only; confirm it is absent from
> the uploaded bundle's merged manifest.
