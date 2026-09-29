# Play Console — answers to paste, form by form

Everything the Play Console asks before a first release, with the answer that
matches what the app actually does. Sources of truth: `app/src/main/AndroidManifest.xml`,
`docs/DATA_SAFETY_MAPPING.md`, `docs/DPDP_COMPLIANCE.md`, `docs/PLAY_STORE_LISTING.md`,
and the hosted pages in `console/public/legal/`. Re-check a row whenever the feature
behind it changes. (Nothing here can be submitted from code; the owner does it in the
Console.)

**URLs to have ready** (replace `<console-domain>` with the hosted console/Hosting domain):

| Field | Value |
|---|---|
| Privacy policy | `https://<console-domain>/legal/privacy-policy.html` |
| Account deletion (web) | `https://<console-domain>/legal/account-deletion.html` |
| Terms | `https://<console-domain>/legal/terms.html` |
| Medical disclaimer | `https://<console-domain>/legal/medical-disclaimer.html` |
| Support / grievance | `https://<console-domain>/legal/grievance.html` + the owner's support mailbox |

Prerequisite: the legal pages must have **no `[placeholder]` left** (operating entity,
address, dates, retention, Grievance Officer) before these URLs are entered — Play
reviews the page text.

---

## 1. App content

| Question | Answer |
|---|---|
| App or game | App |
| Free or paid | Free (nothing in the app takes payment) |
| Category | Health & Fitness |
| Tags | Health, Diabetes, Wellness (choose what the Console offers) |
| Contains ads | **No** |
| Target audience | **18 and over** only (dependent profiles exist, but the account holder is an adult) |
| Appeals to children | No |
| News app | No |
| Government app | No |
| Financial features | None (no payments, loans, banking, crypto) |
| Gambling | No |
| COVID-19 contact tracing / status app | No |
| Data safety form | See §3 |
| Health apps declaration | See §4 |

### Content rating questionnaire (IARC)
Answer honestly; expected outcome is a low rating (3+/Everyone) apart from the
user-interaction note.

- Violence, sexual content, profanity, controlled substances, gambling: **No** to all.
- **Users can interact / exchange content:** **Yes** — batch community chat (text, photo,
  voice notes) between members and coaches. Mitigations to state if asked: in-app
  **Report message**, staff moderation page, coaches/admins can remove content.
- Shares user location: **No**.
- Allows purchases of digital goods: **No**.
- Provides health information / medical advice: the app tracks and educates; it is **not a
  medical device** and gives no diagnosis (the disclaimer is shown in onboarding and in the
  Legal Center).

### Ads and advertising ID
- Ads: **No**. Advertising ID: the app does **not** use it. `AD_ID` and the Privacy Sandbox
  ad permissions are stripped from the manifest and CI fails if they return.
  In the Console's "Advertising ID" question choose **No**.

---

## 2. App access (reviewer instructions)

The app requires a login. Google's reviewers cannot receive an SMS OTP, so choose
**"All or some functionality is restricted"** and provide **email + password** access:

1. The owner creates a dedicated reviewer account (in the app: choose the email option on the sign-in screen and create an account), separate from any real member. Do **not** reuse a personal account.
2. Put in the Console: email, password, and the note: *"Sign in with email. After consent and profile, the Care tab shows plans and consultations. To see coach content, use access code `<a demo batch code>` under Care > Enter program code."*
3. Create one demo batch + a couple of plans in the console for that reviewer so the reviewer sees real screens (empty states are fine but less convincing).
4. Never share credentials that reach production member data; the reviewer account has no admin role.

---

## 3. Data safety

Enter the table in `docs/DATA_SAFETY_MAPPING.md` (it is kept in sync with the shipped
features, including consultation requests, coach messages and program resources).
Quick reference:

- **Collected:** Yes. **Shared with third parties:** **No** (Firebase/Google Cloud, FCM and
  Crashlytics act as our processors).
- **Encrypted in transit:** Yes. **Deletion request supported:** Yes — in the app
  (*Profile > Export or delete my data*, 7-day grace period, cancellable) and on
  the web URL above.
- Health info & fitness info: collected, required for core function, purpose *App
  functionality* only; **not** used for advertising, **not** shared.
- Optional: anonymised-research and product-update consents (separate, off by default,
  withdrawable in Privacy & consent).
- Location: **not collected**. Contacts: **not collected**. Advertising ID: **not collected**.

---

## 4. Health apps declaration

- **App type:** Health condition management / self-monitoring (diabetes and metabolic
  health), with coach-led guided programs.
- **Medical device / diagnosis:** No. Include the statement: *"Nirog Bhumi supports
  self-tracking and education. It does not diagnose, treat or replace a doctor, and it is
  not for emergencies."*
- **Regulated as a medical device anywhere:** No.
- **Uses Health Connect:** Yes — **read-only** import of steps, sleep, weight, blood
  glucose and blood pressure, initiated by the user.
- **Clinical / research studies:** No formal study. An optional, separate consent lets
  members allow **anonymised** data for research; it is off by default.
- **Health data shared with third parties:** No.
- **Emergency:** the app tells users to contact emergency services/their doctor for
  unwell symptoms (shown at consultation request and in the disclaimer).

---

## 5. Health Connect permissions declaration

Declare **read** access only. One justification per data type; the same sentence works
for each: *"Used to pre-fill the member's own daily log so they do not have to type it;
shown only to the member (and to the assigned coach if the member has joined a program
and chosen to share). Never sold, shared for advertising, or used for anything other than
the in-app health features."*

| Permission | Feature it powers |
|---|---|
| `READ_STEPS` | Daily walking total on Today/Track and the weekly report |
| `READ_SLEEP` | Sleep duration in the check-in and sleep insights |
| `READ_WEIGHT` | Weight trend |
| `READ_BLOOD_GLUCOSE` | Sugar log import and glucose trend |
| `READ_BLOOD_PRESSURE` | BP log import and trend |

Not requested (and must stay absent): heart rate, background read, history beyond what
the user grants. The privacy-policy link and the in-app rationale screen
(`PermissionsRationaleActivity`) are the required disclosures.

---

## 6. Permissions declared elsewhere in the Console

| Permission | Status | Justification |
|---|---|---|
| `RECORD_AUDIO` | Runtime, optional | Voice notes in community chat and voice entry of readings. Never records in the background. |
| `POST_NOTIFICATIONS` | Runtime, optional | Reminders, coach messages, consultation confirmations. Quiet hours and a daily cap are enforced server-side. |
| `INTERNET` / `ACCESS_NETWORK_STATE` | Normal | Sync with the backend. |
| `WAKE_LOCK`, `RECEIVE_BOOT_COMPLETED`, `FOREGROUND_SERVICE` | Normal (from libraries) | Scheduled reminders (WorkManager) and push handling. |
| `REQUEST_INSTALL_PACKAGES` | **Absent** | Present only in the debug tester build. |

No sensitive-permission declarations (SMS, call log, location, all-files access,
exact alarms, accessibility) are needed.

---

## 7. Store listing checklist

- Short description, full description (English + Hindi): `docs/PLAY_STORE_LISTING.md`.
- **App icon** 512×512 PNG: `play/graphics/icon-512.png` (generated; regenerate if the launcher icon changes).
- **Feature graphic** 1024×500: `play/graphics/feature-graphic.png` (generated).
- **Phone screenshots** (at least 2, up to 8, 16:9 to 9:16, min 320 px): capture on a real device — Today, Track (sugar log), Insights, Care > Plans & guidance, Request a consultation, Privacy & data. Do not use emulator chrome or debug builds (the debug build shows the tester update banner).
- 7-inch/10-inch tablet screenshots: optional, recommended if you want tablet placement.
- Contact email, website, phone (optional).
- Release notes: `play/whatsnew/whatsnew-en-US`, `whatsnew-hi-IN` (sent by the upload workflow).

---

## 8. Testing requirement (new personal developer accounts)

Personal accounts created after Nov 2023 must run a **closed test with at least 12
testers opted in for 14 continuous days** before applying for production access.
Organisation accounts are exempt. See `docs/CLOSED_TEST_AND_ROLLOUT.md` for the plan.
