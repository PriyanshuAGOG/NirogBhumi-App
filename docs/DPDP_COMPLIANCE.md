# DPDP Act 2023 compliance map

How the app meets each obligation under India's Digital Personal Data
Protection Act, 2023 (DPDP Act), what is handled in code, and what the owner
(as Data Fiduciary) must still do. **Not legal advice** — have counsel review
before launch.

Role: Nirog Bhumi (the operating entity) is the **Data Fiduciary**. Google
Firebase/Cloud, the payment gateway, notification and diagnostics vendors are
**Data Processors** acting under contract.

| DPDP obligation | Where handled | Owner action still required |
|---|---|---|
| **Notice** before/at consent (data, purposes, rights, withdrawal, how to complain) | Onboarding consent screen + Legal Center "Consent Notice"/"Your rights"; hosted `privacy-policy.html` | Finalize entity, contacts, retention periods with counsel |
| **Consent** free, specific, informed, unambiguous, itemized | Onboarding: 3 required itemized checkboxes; optional consents separate & opt-in | — |
| **Consent record** (proof of what was agreed, when) | `recordConsentReceipt()` → immutable `users/{uid}/consentReceipts` (server-timestamped, versioned); rules forbid update/delete | — |
| **Withdrawal** as easy as giving | Privacy & consent: optional consents toggle off instantly; required consent → account anonymize/delete | — |
| **Right to access** | Data Controls → export (Cloud Function `requestDataExport` → a ZIP with `data.json`, CSVs and a README, downloaded through a 15-minute owner-only link; an emailed link (3 h) when the account has an email; files deleted after 30 days) | — |
| **Right to correction/completion** | Profile edit; health logs editable/quick-correct | — |
| **Right to erasure** | Profile → Export or delete my data (`requestAccountDeletion`: typed confirmation, a sign-in younger than 5 minutes enforced by the server, scheduled 7 days out, cancellable from the screen or the banner shown on every main screen, then runs automatically via `processApprovedDeletions`); admin console **Data Requests** page for emailed requests; hosted `account-deletion.html` (works without the app). Erases identity, uploads, chats, inbox, and health readings unless the member opted into anonymized research. Covered by 15 emulator tests. | Confirm the scheduler runs in prod after first deploy (Cloud Scheduler job `processApprovedDeletions`) |
| **Right to grievance redressal** | Legal Center "Grievance Officer & complaints"; hosted `grievance.html` | **Appoint a Grievance Officer**, publish name + address + working mailbox |
| **Right to nominate** | Stated in Privacy Policy §6 / Legal Center "Your rights" | Operational process for acting on a nomination |
| **Children's data (s.9)** — verifiable parental consent; no tracking/targeted ads | Add Family Member: under-18 detection → guardian-consent affirmation + `isMinor`/`guardianConsent` stored; no ad SDKs in app | Keep ad/tracking SDKs out; document age-assurance approach |
| **Purpose limitation & minimization** | Only the collections in the data map are collected; no precise location; no data sale | — |
| **Data-processor contracts** | Firebase/Cloud, gateway, FCM, Crashlytics | Sign/keep DPA-style terms with each processor |
| **Retention / erase on purpose completion** | Deletion flow retains only lawful minimum (payments, fraud, disputes) | Set concrete retention periods in policy |
| **Security safeguards** | Encryption in transit, Firestore/Storage rules (per-batch coach scoping), App Check/Play Integrity, audited admin access | Least-privilege IAM, backups, access review |
| **Breach notification** to Board + affected users | Stated in Privacy Policy §8 | Incident-response runbook + Board reporting process |
| **Complaint escalation** to Data Protection Board of India | Stated in grievance page + in-app | — |

## Consent versioning / re-consent

`CONSENT_VERSION` (in `NirogModels.kt`, currently `"2026-09"`) is stamped on every consent receipt and on
`users/{uid}.consent.version` (the version of the notice the member accepted for the **required** consents). When the
notice/policies materially change, bump this string together with the hosted pages. Members whose recorded version is
older see an "updated privacy notice" banner on the main screens with **Read it** and **I agree**; agreeing writes a new
dated receipt and updates the recorded version. Nothing is blocked while they decide, but the banner stays until they act.
Toggling an optional consent (research, marketing) does **not** change the recorded version; its receipt carries the version in force.
Members who have never recorded a version go through the normal onboarding consent step instead.

## Owner checklist (before Play submission)

1. Appoint and publish a **Grievance Officer** (name, email, postal address).
2. Have counsel finalize the entity, jurisdiction, retention periods, and the
   processor list in the hosted `/legal/*.html` pages (replace every
   `[placeholder]`).
3. Host the pages at a stable public URL (the console's Vercel deploy serves
   them at `/legal/...`; ideally also publish at the primary domain).
4. Put the **Privacy Policy URL** and **account-deletion URL** into the Play
   Console listing and Data Safety form.
5. Keep advertising/behavioral-tracking SDKs out of the app (children's-data
   rule + Data Safety "no data shared for ads").
