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
| **Right to access** | Data Controls → export (Cloud Function `requestDataExport` → JSON of ~20 collections) | — |
| **Right to correction/completion** | Profile edit; health logs editable/quick-correct | — |
| **Right to erasure** | Data Controls → delete/anonymize (`requestAccountDeletion`); hosted `account-deletion.html` (works without the app) | Verify deletion worker runs end-to-end in prod |
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

`CONSENT_VERSION` (in `NirogModels.kt`, currently `"2025-07"`) is stamped on
every consent receipt and on `users/{uid}.consent`. When the notice/policies
materially change, bump this string; a future slice can compare the stored
version and route returning users back through the consent screen. The hosted
policy pages and this constant should be bumped together.

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
