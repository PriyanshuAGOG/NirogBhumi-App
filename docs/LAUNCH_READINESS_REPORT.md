# Nirog Bhumi — Launch Readiness Report (QA + Security Audit)

_Audit date: 2026-09-29 · Scope: Android app, admin console, Cloud Functions,
Firestore/Storage rules, CI/CD, Play Store readiness._

How it was tested: every screen route, console page, callable function and
rules path was read line by line. The Firestore rules (both the **live**
`main` rules and this branch's rules) were run in the emulator against the
exact queries the console and app send. The console build was run
(`npm ci && npm run build`, which passes). The Cloud Functions deploy history
was traced from CI logs. The Android build was not run locally (the sandbox
blocks Google Maven), so Android findings come from reading the code and
from CI results.

> **Bottom line:** most of the product has been built, but **production is not
> running what this branch contains, and most admin actions fail for one
> infrastructure reason**. Four things block a Play launch: (1) the two
> branches have split and need merging into one, (2) Cloud Functions invoker
> permissions are missing, (3) account deletion never actually runs, and (4)
> about 35 app screens show generic template content or empty data because
> nothing writes their data. Estimated path to a Play-ready internal/closed
> test: **roughly 2–3 focused build sprints** plus the owner actions in §9.

---

## 1. Why the admin panel errors (root causes)

The failures you hit when sending a campaign, adding users, enrolling or
inviting come down to **three causes**:

### 1.1 Cloud Functions have no public invoker — the main cause
Every callable function first created by CI since **3 Jul** was deployed
without the Cloud Run `allUsers → run.invoker` binding. The deploy tool only
tries to set it once, at creation. That attempt failed (most likely because
the Google Cloud org policy *Domain Restricted Sharing* blocks `allUsers`),
and later deploys never retried it. The browser/app therefore gets a
401/403 before the function runs. The Firebase SDK reports this as
**UNAUTHENTICATED** or **PERMISSION_DENIED**, even though the user is
signed in.

| Function | Used by | Status |
|---|---|---|
| createAnnouncement, previewAnnouncementAudience, deleteAnnouncement, markAnnouncementSeen | Console → Announcements/campaigns; app feed | ❌ broken |
| sendBulkNotification | Console → Members "message" | ❌ broken |
| createStaffAccount, adminEnrollUser | Console → Users (add staff, enroll) | ❌ broken |
| inviteToProgram, revokeInvite, bulkOnboard | Console → Programs (invites, CSV import) | ❌ broken |
| ensureProgramMembership | App → joining a program after signup | ❌ broken (explains the new-device "UNAUTHENTICATED") |
| getHealthFileShareLink | App → Health File share | ❌ broken (falls back to an unsafe permanent link, see §5) |
| bootstrapSuperAdmin | Console first-run | ⚠ worked for you once (probably fixed by hand) |
| redeemProgramCode, setUserRole, requestDataExport, requestAccountDeletion, createAuditLog, all triggers & schedulers | — | ✅ working (created from a laptop before CI) |

**Fix:** code change plus a one-time owner action. Set `invoker: 'public'`
explicitly on every `onCall` so each deploy re-applies the binding. The owner
then lifts or excepts the org policy once, or runs the gcloud loop in
`docs/deploy-wif-setup.md`. I'll also add a CI check that fails the deploy if
any callable is missing the invoker.

### 1.2 Production runs the wrong branch
- `main` (last push 17 Sep) is missing **33 commits** from this branch
  (7–24 Jul work). **Both branches auto-deploy to the same Firebase project
  and publish APKs to the same tester channel.** Whichever pushed last wins.
- **What's live right now:** Firestore rules, Storage rules and the console
  from `main` (17 Sep); Cloud Functions from this branch (24 Jul). The latest
  tester APK (run 121, 17 Sep) was built from `main`.
- Live problems caused by `main`'s rules (emulator-verified):
  - 🔴 **Security hole:** a user can create their own profile with
    `programActive: true` and skip payment/enrollment. The fix (ae75b84)
    was never merged.
  - 🔴 New-device signup `saveProfile` → PERMISSION_DENIED (the bug you
    reported is back live).
  - 🔴 No rules for `consentReceipts` or `coachInboxMessages`, so DPDP
    consent receipts and the coach inbox are denied live.
- `main`'s **release build can't compile**
  (`DebugAppCheckProviderFactory` sits in `main/`). `main` also ships
  `REQUEST_INSTALL_PACKAGES` in the main manifest (Play policy violation) and
  has no legal pages.
- `main` does have hardening this branch lacks: fail-closed APK checksum,
  coach privacy scoping v2 (`assignedCoachToUser`), a Storage role-claim
  guard, and coach-health-access rules tests.
- Merging the two branches conflicts in only **2 files** (`firestore.rules`
  and its test).

**Fix:** merge into a single branch that takes the best of both, and make
**only `main`** deploy to production. This branch would deploy to a
staging project or not deploy at all.

### 1.3 Console features wired to the wrong place

| Console action | What happens | Why |
|---|---|---|
| Programs → **Access codes** | Codes save, but members can't redeem them | `redeemProgramCode` only checks `programs.code`, never the `programCodes` collection (maxUses/expiry ignored) |
| Calendar → "also announce" | Event saves, then "Event could not be saved" appears | Writes `announcements` directly; rules deny all writes (`write: if false`), and the schema is old |
| Batches → message member | Looks sent; the member never gets it | Writes `coachMessages`, which nothing in the app, functions or push reads |
| Member detail → glucose feed | Error / empty | Query sorts by `createdAt`, but the index is on `measuredAt` |
| Members → bulk message | Only reaches "quiet" members of one program | By design, but it's labelled as a general message tool |
| Any page as a **coach** | Denied / empty | Console lists all programs/users unfiltered; rules reject unfiltered coach queries. **The console currently works only for admin/super_admin.** |
| Consultations | Read-only list | No assign, confirm, reschedule, or slot management |
| Settings | Placeholder | Hardcoded version `0.1.0` |
| Experts, consultation slots, products/orders, program plans, expert notes | **No page exists** | See §3 — the app screens depending on this data stay empty |

---

## 2. What works end to end ✅

**Android app**
- Phone OTP / email / Google sign-in (Google sign-in needs the release
  SHA-1/SHA-256 in Firebase, see §9), onboarding, itemized DPDP consent,
  consent receipts (branch rules), profile edit.
- Health logging: glucose, BP, weight, sleep, meals, medications (with
  medication logs), steps. Daily check-in flow. Quick-log home widget.
- Trends and correlation insights (`TrendInsights`), Today focus engine,
  streaks and first-week checklist.
- Health File (summary and PDF share); lab report upload to private Storage.
- Program chat (Care+): text, photos, voice notes, pin (staff), mentions;
  unread badges; coach inbox (branch).
- Reminders (WorkManager), FCM push, quiet hours and daily cap on the server.
- Health Connect import (manual sync, 30 days).
- Data export (`requestDataExport`) and account deletion **request** (see §4).
- Legal Center in the app, and hosted legal pages under `console/public/legal/`.
- Crashlytics (off in debug), App Check / Play Integrity (release), R8
  release build (CI job on this branch).
- The tester self-updater is correctly compiled out of release
  (`UpdateManager.isEnabled = BuildConfig.DEBUG`; permission only in the
  debug manifest).

**Admin console (admin role)**
- Sign-in, dashboard, members list/detail (except the glucose feed),
  program create/edit, content, support queue, error reports, moderation
  (reported messages), role changes (`setUserRole`).

**Backend**
- 27 functions compile. Triggers: new-user setup, notification fan-out,
  moderation, audit log. Scheduled: reminders, daily content, notifications
  sender, deletion processor.

---

## 3. Half-built: screens with no real data 🟡

The app has **53 dedicated screens** and **35 screens rendered by the generic
`CatalogScreen` template**. Those 35 show section headers, spec-driven list
items and a generic action. Many read a Firestore collection that **nothing
ever writes to**: no console page, no function, no seed data. They will
always look empty or demo-like to a real user.

| Collection read | Screens affected | Writer? |
|---|---|---|
| `programPlans` | diet_plan, yoga_plan, yoga_detail, naturopathy_plan, naturopathy_detail | ❌ none |
| `expertNotes` | expert_notes | ❌ none |
| `sugarStories` | sugar_story | ❌ none |
| `orders`, `products` | orders, order_detail, payment_confirmation | ❌ none (no store) |
| `consultationSlots`, `expertAssignments` | consultation_types, pre_consultation, consultation_confirmed, consultation_detail | ❌ none, so **booking dead-ends** (the Continue button is disabled when no slot exists) |
| `userPrograms` | today_program, program_checklist, program_locked | ❌ none |
| `deviceConnections` | device_sync | ❌ none |

Also generic or thin: care_hub, learn_hub, track_hub, insights_hub,
article_detail, weekly_report, family_dashboard, add_family, add_bp,
add_sleep, add_sugar, quick_sugar, quick_walk, upload_lab, today_empty,
loading_state, sugar_reading_detail, health_connect_permissions.

Other half-built logic:
- **Daily content is hardcoded.** `generateDailyContent` writes the same
  action ("Walk 15 minutes after dinner") and the same weekly report for
  every user.
- **The expert role does nothing.** No expert screens, assignments or notes
  workflow.
- **No payments.** Consultations and orders stop at `payment_pending` with
  hardcoded prices (₹699/₹999). If paid digital programs are sold in-app,
  **Google Play Billing** is required; physical goods and real-world
  consultations may use Razorpay.
- **Profile defaults look like real data.** The setup form is pre-filled with
  age 28, 174 cm, 72 kg and city Jaipur, and gender defaults to "Male". The
  form never saves gender, but the Health File displays it. A user who taps
  through saves this fake data as their own.

---

## 4. Blockers for Play and DPDP 🔴

| # | Issue | Why it blocks |
|---|---|---|
| P1 | **Account deletion never runs.** `processApprovedDeletions` only processes `status == 'approved'`, and nothing ever sets `approved` | Play requires working account deletion; DPDP right to erasure |
| P2 | Deletion misses `programChatMessages`, `coachInboxMessages`, `coachNotes`, `coachMessages`, `supportRequests`, `reportedMessages`, `errorReports`, typing status | Incomplete erasure |
| P3 | Health Connect requests `READ_HEART_RATE` but never reads it | Play Health Connect review rejects unused permissions |
| P4 | The Health File share falls back to a **permanent public Storage download URL** when the signed-link function fails (which it currently always does, §1.1) | Health data privacy leak |
| P5 | Legal pages have `[placeholders]`: entity, Grievance Officer, address, dates | Play listing and DPDP §8 require real contacts |
| P6 | The live rules allow the self-enrollment bypass and deny signup (§1.2) | Security and broken first-run |
| P7 | The `releases/**` Storage path is publicly readable (debug APKs) | Exposes internal builds; lock it down or move it before launch |
| P8 | Play Billing decision for paid programs | Policy |
| P9 | Release signing / Play App Signing SHA-1/256 not registered in Firebase | Phone OTP and Google sign-in fail on Play-installed builds |

---

## 5. Security findings (by severity)

1. 🔴 **Self-enrollment bypass live** (users-create rule on `main`). Verified in
   the emulator.
2. 🔴 **Permanent-URL fallback for Health File sharing** (P4). Remove the
   fallback: fail closed.
3. 🟠 **Coach data scope.** This branch's rules let a coach list all users (too
   broad). `main` tightened this with `assignedCoachToUser`, so keep `main`'s
   version when merging.
4. 🟠 **Both branches deploy to production.** Anyone pushing `claude/**` changes
   production rules and functions.
5. 🟠 **Public debug APKs** in Storage (P7).
6. 🟡 **Cloud Functions have zero tests** (`npm test` = `tsc` only).
   `npm audit`: 12 moderate. `firebase-functions` is outdated.
7. 🟡 No per-user rate limits on callables (bulk and invite functions are
   staff-only, which is acceptable).
8. ✅ Good: Storage rules have owner scoping, size/type limits and a
   role-claim guard (main). App Check is on. Consent receipts are
   append-only. Callables all begin with `requireUser()` and role checks.
   Audit log exists.

---

## 6. Admin console page-by-page

| Page | Status | Notes |
|---|---|---|
| Dashboard | ✅ admin / ❌ coach | Unfiltered users query |
| Moderation | ✅ | |
| Members | ✅ list · ❌ bulk message (invoker) | |
| Member detail | 🟡 | Glucose feed index |
| Batches | 🟡 | Coach message goes nowhere |
| Announcements | ❌ | All 4 functions blocked by invoker |
| Calendar | 🟡 | Events OK; "also announce" fails |
| Programs | 🟡 | CRUD OK; invites, CSV import and access codes broken |
| Content (admin) | ✅ | |
| Consultations | 🟡 | Read-only |
| Support | ✅ | |
| Users (admin) | 🟡 | Role change OK; add staff and enroll broken (invoker) |
| Error reports | ✅ | |
| Settings | 🟡 | Placeholder |
| **Missing** | ❌ | Experts, slots, program plans, expert notes, products/orders, deletion approvals |

---

## 7. CI/CD state

- `ci.yml`: Android debug build, R8 release (this branch only), functions
  `tsc`, rules emulator tests.
- `build-firebase-debug-apk.yml`: runs on push to main/claude/** and
  distributes to testers. Because both branches trigger it, testers get
  whichever branch pushed last.
- `deploy-firebase.yml`: deploys rules/indexes/functions/console on
  firebase/** changes, **from both branches**.
- `release-android.yml` / `upload-google-play.yml`: exist; not yet run
  against a real Play listing.

---

## 8. Build plan to Play Store (proposed order)

**Sprint 0 — Stabilize production (1–2 days)**
1. Merge this branch and `main` into one branch. Take `main`'s coach
   scoping, APK checksum and Storage guard; take this branch's signup fix,
   create-lock, consent receipts, coach inbox, legal pages, release build
   fixes and debug-only manifest.
2. Restrict production deploys to `main` only.
3. Add `invoker: 'public'` to all callables, plus a CI invoker check. The
   owner lifts the org policy (§9).
4. Fix `redeemProgramCode` to honour `programCodes`; add the missing
   indexes; fix Calendar announce (route it through `createAnnouncement`);
   route Batches messages into `coachInboxMessages` + push.

**Sprint 1 — Play blockers (3–5 days)**
5. Account deletion: auto-approve after a grace period (or add an admin
   approval page) and delete every collection listed in P2. Add a test.
6. Remove `READ_HEART_RATE`; remove the Health File permanent-URL fallback.
7. Replace the profile pre-fills with blank fields and placeholders; save
   gender.
8. Coach-scoped console queries, so the console works for coaches.
9. Function unit tests for enrollment, deletion and announcements; fix
   the `npm audit` warnings.

**Sprint 2 — Fill the empty screens (1–2 weeks)**
10. Console pages for experts and consultation slots, program plans
    (diet/yoga/naturopathy), expert notes, and sugar stories/content. Seed
    real starter content.
11. Consultation booking end to end without payment (request → assign
    expert → confirm → reminder).
12. Personalized daily content (use the user's logs instead of the
    hardcoded action).
13. Decide on the store and payments: hide orders/products for v1, or
    integrate Razorpay (physical goods) and Play Billing (digital programs).
14. Hide or finish the remaining generic hubs; there should be no demo
    content in the release build.

**Sprint 3 — Release**
15. Signed AAB via `release-android.yml` → internal testing → closed test
    (Play requires a closed test with ≥12 testers for 14 days on new
    personal developer accounts).
16. Data Safety form (`docs/DATA_SAFETY_MAPPING.md`), Health apps
    declaration, Health Connect declaration, listing assets
    (`docs/PLAY_STORE_LISTING.md`).

---

## 9. Owner-only actions (can't be done from code)

1. **Allow public invoker for Cloud Functions.** Lift or add an exception
   to the org policy `iam.allowedPolicyMemberDomains` for the project, or
   run the gcloud loop in `docs/deploy-wif-setup.md`.
2. Register the **release upload key and Play App Signing SHA-1/SHA-256**
   in Firebase (phone auth, Google sign-in, App Check).
3. Appoint a **Grievance Officer**. Fill every legal `[placeholder]`
   (entity, address, dates), with counsel review.
4. Decide on **payments** (Play Billing for digital programs vs Razorpay
   for goods/consultations) and pricing.
5. Provide **real content**: program plans, expert list and availability,
   articles and stories.
6. Play Console: developer account, app listing, closed-test tester list,
   Health apps declaration.
7. Decide whether this branch keeps deploying to production, or gets a
   separate staging Firebase project (recommended).

---

## 10. Progress log

### Sprint 0 — stabilize production (done on branch, awaiting merge to `main`)
- ✅ Branch merged with `main`: keeps `main`'s coach scoping, APK checksum
  and Storage guard, and this branch's signup fix, create-lock, consent
  receipts, coach inbox and legal pages. 111 rules tests pass.
- ✅ Production deploys only from `main`; a new deploy step re-applies the
  public invoker to every callable and fails loudly if the org policy still
  blocks it.
- ✅ Access codes: `redeemProgramCode` honours console codes (active,
  expiry, max uses, no double-counting), with clear error messages. The
  console normalises, validates and can generate and copy codes.
- ✅ Batches "Message" now reaches the member (in the coach inbox) and
  pushes immediately, in both directions.
- ✅ Calendar "also announce" uses the announcement function and no longer
  mislabels a saved event as failed.
- ✅ Glucose feed index added.
- ⏳ Needs the owner: the org-policy change in §9 item 1 (until then the
  invoker step will report failure), and merging this branch to `main` to
  deploy.

### Sprint 1 — Play blockers and quality gate (done on branch)
- ✅ **Account deletion works end to end.** Root cause found while fixing it:
  the app wrote `deletionRequests` / `dataExportRequests` straight to Firestore,
  which the rules always denied, so **"Export my data" and "Delete my account"
  never worked in the app**, and nothing ever approved a request for the
  scheduler. Now: the app uses the callables; deletion is scheduled 7 days out
  (cancellable in the app), then runs automatically; an admin **Data Requests**
  page handles emailed requests; erasure covers identity, uploads, chat, coach
  inbox/notes, support, reports, invites, roster and login, and health readings
  too unless the member opted into anonymized research. Data export now also
  includes chat, inbox, orders and consent receipts.
- ✅ Heart-rate Health Connect permission removed. Health File sharing fails
  closed (never a permanent public URL).
- ✅ Profile setup no longer pre-fills fake data (28 / Male / 174 / 72 / Jaipur);
  gender is optional; ranges are validated in onboarding and Edit profile.
- ✅ Coach console works: coaches previously hit permission-denied on almost
  every page. Program-scoped queries everywhere; `programInvites` (which had no
  rule at all, so the Invites list failed even for admins) fixed; announcement
  master docs (which list every recipient) restricted to their author + admins.
- ✅ Console bugs found by the new browser tests: member names missing from
  Members/Users (the app stores `fullName`), stale "0 members" on programs, an
  unstyled filter control.
- ✅ Quality gate now in CI: 33 Cloud Functions emulator tests, 156 rules tests
  (including console-query and Android-write contracts), and a 24-check browser
  end-to-end suite (admin + coach) against the emulators. Android: unit tests and
  the R8 release build pass.
- Known and accepted: `npm audit` still lists moderate advisories that need
  breaking upgrades (functions: transitive `uuid` inside Google client
  libraries; console: dev-server `esbuild`, `react-router`). Staff-only console,
  no untrusted input reaches them.
- ⚠ Not verifiable without a device: the Android screens changed in Sprint 1
  (Data Controls, onboarding profile, Health File) compile and their logic is
  unit/contract tested, but need a manual pass on a phone - see the checklist
  in the hand-off message.

### Sprint 2 — finish the reachable product, scale, Play guards (done on branch)
- ✅ **Reachability audit.** Many screens flagged as "half-built" in §5 belong to
  the generic `CatalogScreen` cluster that nothing links to. Decision: do not
  ship or polish unreachable screens; finish the flows a member can actually
  reach, and make the store/payments explicitly out of scope for v1 (the app
  shows past orders read-only; no checkout exists, and the Play listing no longer
  promises shopping).
- ✅ **Plans & guidance.** Coaches (or admins) write diet / yoga / naturopathy /
  guidance notes per batch in the console ("Plans & Guidance"), optionally pushing
  to the batch. Members see them in Care+ > Plans & guidance. Rules: only the
  batch's members and staff read, only admin or the assigned coach writes, http(s)
  links only. Replaces the orphaned per-user `programPlans` screens.
- ✅ **Consultations without payment.** Members request (type, concern, preferred
  time, share-readings consent, emergency acknowledgement) and track status in
  My consultations; staff confirm a time, expert and join details in the console
  (reschedule / decline with reason / cancel / complete). Push on every change and
  exactly one "starts in an hour" reminder, kept in step with the appointment.
  Fees are arranged by the team; nothing takes payment. Rules stop a member
  self-confirming or attaching a link.
- ✅ **Scale.** The daily job wrote a hardcoded dailyActions/weeklyReports doc per
  user in a single batch (would fail at 501 users; nothing read them) — removed;
  batch pulse and the Monday digest are paged. The notification sender now drains
  up to 1,000 pushes per run at 25 concurrent (was 100 sequential: a 5,000-member
  campaign would have taken ~12 hours).
- ✅ **Play guards.** Advertising-ID permission removed from the merged manifest;
  CI verifies the merged release manifest (permission allowlist, no debuggable /
  cleartext / backup, exported-component allowlist). Listing, Data Safety mapping
  and release checklist updated to match what the app really does.
- ✅ **Tests.** Rules 166 (console queries, app writes, resources, consultations),
  Functions emulator 55, browser e2e (admin, coach, full member journey incl.
  Storage, plans and consultations), Android unit/Compose tests for the new
  screens. All run in CI.
- ⚠ Not verifiable without a device: Plans & guidance, Request/My consultations,
  Data Controls, onboarding profile and Health File screens compile and are
  Robolectric-tested, but need a manual pass on a phone.
- ⏳ Owner actions unchanged: Cloud Run public-invoker org policy, release SHA-1/256
  in Firebase, Grievance Officer + legal placeholders, real content, merge to `main`.
