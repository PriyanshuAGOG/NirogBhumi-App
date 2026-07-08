# Nirog Bhumi — End-to-End Platform Build Plan

Engineering companion to `docs/PRD.md`. This is the delivery workflow for the
whole platform: the member Android app, the web admin/coach console, the
Firebase backend, and the supporting systems (roles, moderation, notifications,
analytics, observability, security, CI/CD) needed to run live.

Guiding rule: **ship in verifiable vertical slices, foundation first.** The
Android app cannot be compiled inside the dev sandbox (Gradle/plugin fetch is
network-blocked), so every Android slice is verified through the GitHub Actions
build before the next slice stacks on it. The web console is verified locally
(`npm run build`).

---

## 1. System architecture

```
┌─────────────────────┐     ┌──────────────────────┐
│  Member App (Android│     │  Admin / Coach Console│
│  Kotlin + Compose)  │     │  (web: React + TS)    │
└──────────┬──────────┘     └───────────┬───────────┘
           │  Firebase SDKs (realtime)  │
           ▼                            ▼
   ┌───────────────────────────────────────────────┐
   │                Firebase (asia-south1)          │
   │  Auth · Firestore (realtime listeners) ·       │
   │  Cloud Storage · Cloud Functions · FCM ·       │
   │  App Check · Crashlytics · Analytics · Hosting │
   └───────────────────────────────────────────────┘
```

- **Realtime** everywhere uses Firestore snapshot listeners (chat, batch pulse,
  announcements, calendar, moderation queue) — no polling. Both clients bind the
  same collections; security rules are the single enforcement point.
- **Auth & roles:** Firebase Auth (phone OTP, email, Google). Roles are custom
  claims (`role: admin | coach | expert | user`). The console requires
  `admin`/`coach`; the member app is `user`. Claims are set by a callable
  Function guarded by super-admin.
- **Writes that need trust** (setting roles, moderation actions, program/batch
  management, announcements fan-out, batch-stat aggregation) go through Cloud
  Functions or admin-only rules — never client-trusted.
- **Hosting:** the console deploys to Firebase Hosting (`admin.nirogbhumi…`),
  same project, so it shares Auth/Firestore/Storage with zero extra infra.

---

## 2. Roles & surfaces

| Role | Where | Can do |
|---|---|---|
| `user` | Android app | Own health data, own program (chat/calendar read, general chat post) |
| `coach` | Web console | Manage assigned batches: post announcements, edit calendar, see batch consistency, message members, review reports |
| `admin` | Web console | Everything coach can, across all programs + create programs/codes, assign coaches, manage content (Learn), set roles |
| `super_admin` | Web console | Admin + destructive ops (role grants, data-deletion approvals) |

---

## 3. Data model (authoritative; extends existing collections)

Existing: `users, profiles, glucoseReadings, bpReadings, sleepLogs, walkLogs,
weightLogs, labReports, dailyCheckins, checklistLogs, consultations,
dailyActions, weeklyReports, notifications, programs, announcements,
programChatMessages, reportedMessages, deviceConnections, dataExportRequests,
deletionRequests, auditLogs, supportRequests`.

New / extended for v2:
- `programs/{id}` +: `coachId, coachName, coachPhoto, startDate, durationWeeks,
  memberCount, phases[] ({title, startWeek, endWeek})`.
- `programEvents/{id}`: `programId, title, type(live|walk|lab|qa), startsAt,
  endsAt?, location?, link?, description, bring?, createdBy, updatedAt`.
  Rules: read = program member; write = admin/assigned coach.
- `programMembers/{programId_uid}`: `programId, uid, name, joinedAt, status,
  lastReadGeneralAt, lastReadAnnouncementsAt, contributionKm`. Enables member
  roster + unread badges + per-member coach view.
- `batchStats/{programId_day}`: `checkedInCount, memberCount, collectiveKm`
  (Function-aggregated, member-readable).
- `users/{uid}` +: `checkinHourHint, lastCheckinAt, healthFileUpdatedAt`.
- `contentItems` (Learn) already exists — console gains authoring UI.

Every new collection ships with security rules + composite indexes in the same
slice that introduces it.

---

## 4. Delivery phases (each = one or more verifiable slices)

### Phase 0 — Foundations ✅ done
- [x] Design-system tokens (`DesignSystem.kt`, `Type.kt`) — colors on the
  existing brand palette, spacing/radius/elevation/type scale.
- [x] Reusable Compose components (`NirogComponents.kt`): `NirogCard`,
  `SectionLabel`, `StatusChip`, `PrimaryButton`/`GoldButton`, `FocusCard`,
  `RowCard`, `AvatarStack`, `InsightCard`.
- [ ] Downloadable Google Fonts (Fraunces + Manrope) — currently aliased to
  platform serif/sans-serif; real faces are a follow-up one-line swap.

> **2026-07-04 reconciliation note:** Phases 1-5 below are the *original*
> planning doc and had drifted badly out of date - most of their `[ ]` items
> were actually completed in later sessions without this section being
> updated. Section 8 (below) is the actively-maintained backlog; treat it,
> not this section, as the source of truth for "what's still open." The
> checkboxes here have been corrected to match a fresh code audit, but this
> section is otherwise left as historical record rather than rewritten.

### Phase 1 — Member app core loop ✅ done
1. [ ] `DashboardHub.kt` (Today/Track/Insights/Care tabs) still uses ad-hoc
   hardcoded `Color(0xFF...)` values throughout instead of the shared
   `NirogColor`/`NirogSpace`/`NirogType` design-token system already used in
   Chat/Announcements/Calendar/Details screens - the one genuinely open
   visual-consistency item found in this reconciliation. Not a functional
   bug; see Section 8 item 20 for the plan to close it.
2. [x] Check-in v2 device pre-fill banner - `HealthConnectManager.syncToday()`
   silently pre-fills today's steps/sleep from a connected device at the
   top of `CheckInFlow.kt`, contrary to this item's stale "not yet built."
3. [x] **Body Report** screen (instant payoff, real data, honest empty
   states) + **Health File** renderer with real on-device PDF generation
   and share, plus proper error surfacing on failure.
4. [x] **Rhythm** screen (7-day ring, 30-day non-punitive grid, gentle
   nudge) reading real logged-days from Firestore.

### Phase 2 — Care+ cohort (realtime) ✅ done
5. [x] Chat icon top-right on the Care tab (replacing the bell there) →
   Chat Hub. Batch-home restyle done (Care+ home restructured onto
   `ProgramStatusHero`/`CareTile` - see Section 8 items 10/17).
6. [x] **Chat Hub**: Announcements (read-only for members) + General rooms,
   long-press report, non-member unlock, unread badges (`peekLatestActivity`/
   `markProgramRead`, `ChatHub.kt`).
7. [x] **Program Calendar** now reads real `programEvents` (month-grid
   calendar, Section 8 item 59) with a genuine one-off "Remind me"
   (`EventReminderWorker`).
8. [x] `batchStats` aggregation: real-time PII-free check-in counting via
   `recordBatchCheckin()` on the glucose/BP triggers, plus a daily collective
   walking-minutes rollup. `ProgramStatusHero` on Android reads it live,
   honest "be the first" zero-state.
   - **Found & fixed in the process:** nothing ever wrote `programMembers`
     on enrollment (the console's roster/Batch Pulse depended on a
     collection that was always empty) — `redeemProgramCode` now writes it
     atomically with the enrollment.

### Phase 3 — Web admin / coach console ✅ done (`console/`)
9. [x] Scaffold: React + TS + Vite + Firebase SDK + role-gated auth shell.
10. [x] **Moderation queue** (realtime `reportedMessages`).
11. [x] Batch management: roster, Batch Pulse, coach→member messaging
    (`coachMessages`), CSV roster export, bulk notifications (Section 8 items
    15, 20/21).
12. [x] Announcement composer (with templates, Section 8 item 20) +
    **Calendar editor** (create/edit/delete events, optional auto-announce
    on change).
13. [x] Programs & codes admin, plus true per-coach batch scoping
    (`programStaff(programId)` rules helper - Section 8 item 3, done in a
    later session; the old "any coach can manage any program" MVP
    simplification no longer applies).
14. [x] Content (Learn) authoring; consultations view (read-only); support
    inbox.
15. [x] Deploy target wired (`firebase.json` hosting block, `console/dist`,
    SPA rewrite) and automated - `deploy-firebase.yml` builds+deploys the
    console on every backend deploy (Section 8 item 8).

### Phase 4 — USP deepening & polish ✅ done
16. [x] Focus-card personalization (`checkinHourHint`, `TodayFocusEngine`);
    Daily Insight engine (`computeSleepGlucoseInsight`) beyond a static
    rule-based takeaway.
17. [x] Progressive onboarding: first-week checklist (Section 8 item 14);
    notification tone/logic audited (quiet hours, daily cap, checkin-hour
    smart scheduling).
18. [x] Health File share-link (Storage + real signed access, Section 8
    item 18) beyond the on-device PDF share-sheet, which also still works.

### Phase 5 — Enterprise hardening (cross-cutting) ✅ done
- **Security:** rules unit tests (`firebase/rules-tests`, 42+ tests);
  per-coach least-privilege claims via `programStaff()`; PII handling
  reviewed in the full security audit (Section 8 pre-item-1 history).
  App Check enforcement on prod is the one item here not independently
  re-verified in this reconciliation pass.
- **Observability:** structured `auditLogs` for admin actions
  (`setUserRole`, moderation, calendar edits). Crashlytics/Sentry wiring not
  independently re-verified in this pass.
- **Analytics:** `AnalyticsLogger` events (`log_added`, `checkin_completed`,
  `program_joined`, `chat_message_sent`, `announcement_posted`, data
  export/deletion) plus `screen_view` tracking (Section 8 item 7).
- **Offline & resilience:** Firestore `PersistentCacheSettings` enabled app-
  wide (Section 8 item 17); graceful empty/error states throughout, global
  `SnackbarHostState` surfacing every `CloudResult.Failure`.
- **Accessibility:** icon-level `contentDescription` audit + text-field
  `label`/`semantics` audit (Section 8 item 7); Care+ hero contrast spot-
  checked (Section 8 item 17, WCAG AA pass).
- **QA:** unit tests for `TodayFocusEngine`, `computeSleepGlucoseInsight`,
  `VersionChecker`; 42+ Firestore rules tests. Compose UI tests for the
  check-in flow/Rhythm screen specifically are not yet written - the one
  genuinely open QA item from this section.
- **CI/CD:** `build-firebase-debug-apk.yml` (push-triggered, WIF-based App
  Distribution), `ci.yml`, `deploy-firebase.yml` (rules/indexes/storage/
  functions/hosting, WIF-based) all live and green.

---

## 5. Realtime & moderation specifics

- **Chat:** `programChatMessages` (general) and `announcements` streamed with
  `.limitToLast`/paged listeners; optimistic local echo; report writes to
  `reportedMessages`.
- **Moderation:** console subscribes to `reportedMessages where status ==
  'open'`; actions (dismiss / warn / remove message / mute member) run through
  a callable Function that writes the moderation result + an `auditLogs` entry
  and, if removing, tombstones the offending message. Members never see
  moderation mechanics.
- **Batch Pulse:** never exposes individual rankings to members; per-member
  detail is coach/admin-only via `programMembers`.

---

## 6. Known operational blockers (must clear before "live")

1. **Fixed 2026-07-03**: `build-firebase-debug-apk.yml` (the workflow that
   actually uploads to Firebase App Distribution) was `workflow_dispatch`
   only, so a new build only ever reached testers if someone remembered to
   manually trigger it - CI's own `ci.yml` never uploads anywhere, it only
   produces an ephemeral compile-check artifact. Testers were stuck on a
   build from the prior evening while ~9 commits shipped with no signal.
   Now triggers automatically on push to `main`/`claude/**` when app code
   changes (`FIREBASE_TOKEN`/`APP_DISTRIBUTION_TESTERS` secrets confirmed
   still valid - the distribution step succeeded on the first auto-run).
2. **Android compile can't be verified in-sandbox** → we rely on the Actions
   build per slice; keep slices small and green.
3. **Fonts/App Distribution** and **App Check debug** caveats from earlier
   still apply (debug keystore must not be trusted against the live project).
4. **Fixed 2026-07-06**: `deploy-firebase.yml` was also `workflow_dispatch`
   only (same class of bug as #1 above) - backend changes (functions/rules)
   sat committed but undeployed for hours at a time. Now triggers
   automatically on push to `main`/`claude/**` when `firebase/**` changes.
5. **Open, owner-blocked, likely explains the recurring "unauthenticated"
   enrollment bug**: confirmed live in production (deploy runs
   `28781548024`, `28782811791`) - the very first deploy of any brand-new
   `onCall` function fails to get its public-invoker IAM binding set
   (`createStaffAccount`, `ensureProgramMembership`, `bootstrapSuperAdmin` all
   hit this). The function's code deploys fine, but every client call to it
   gets a platform-level 403 before our own auth check ever runs - visible to
   users as a generic `unauthenticated`/`permission-denied`, indistinguishable
   from an app-level bug. Subsequent code updates never re-attempt the IAM
   step (only a function's literal first deploy does), so once stuck, a
   function stays stuck silently. `redeemProgramCode` was itself brand-new
   when first deployed - if it hit this same gap, it explains the repeatedly
   reported "unauthenticated" error joining a program with a code,
   independent of any client-side fix. Exact one-time `gcloud` fix (grant
   `roles/run.invoker` to `allUsers` on every `onCall` service, safe/no-op if
   already set) documented in `docs/deploy-wif-setup.md` — **owner action
   required**, cannot be done from this environment (no gcloud credentials
   against the live project).
6. **Confirmed 2026-07-06, root cause identified**: the actual blocker behind
   #5 is a Domain Restricted Sharing org policy (`iam.allowedPolicyMemberDomains`)
   that rejects `allUsers` as a Cloud Run invoker outright, plus the deploying
   account lacking `roles/orgpolicy.policyAdmin` to override it at the
   project level. Needs whoever administers the org (a different person than
   the one running deploys, confirmed) to either grant that role or run the
   override directly - exact commands in `docs/deploy-wif-setup.md`.

---

## 7. Definition of done (per slice)

Code + security rules + indexes (if any) committed together · CI build green ·
prototype/PRD parity on the screen · no fabricated data · warm/non-punitive
copy · audit entry for any admin write. A slice isn't "done" until the Actions
build passes.

---

## 8. Post-launch backlog (standing `/goal`, worked via `/loop`)

Phases 0-3 above shipped; a full security audit (Android/Functions/rules/
console) also found and fixed every Critical/High finding (see git log for
the security-hardening commit). This phase is the next tranche, prioritized
by the user. Work sequentially, CI-verified per slice, small commits.

1. [x] **Deploy automation** — `deploy-firebase.yml` now uses Workload
   Identity Federation instead of a service-account key (the org policy
   that blocks key creation doesn't block WIF, since no key is ever
   created). One-time `gcloud` setup documented in
   `docs/deploy-wif-setup.md` — **owner action required** to actually run
   it once before the workflow will authenticate successfully.
2. [ ] **Real typography** (Fraunces + Manrope) — blocked: this sandbox's
   network policy doesn't allow fetching font binaries from Google
   Fonts/GitHub. Needs the font files supplied another way (owner upload,
   or a network policy change on the environment) before this can ship.
3. [x] **Per-coach batch scoping** — new `programStaff(programId)` rules
   helper (admin, or the coach whose uid matches that `programs/{id}.coachId`)
   replaces bare `staff()` on programs/programEvents/programMembers/
   announcements/coachMessages/reportedMessages/batchStats. **Operational
   note:** any coach account that doesn't yet have `coachId` set on their
   program (via the console's Programs page) will see zero batches/members
   until an admin sets it - this field existed before but was never
   enforced, so this is a real behavior change on deploy, not just an
   additive one. coachNotes and the health-log collection group are
   deliberately left staff()-wide for now (documented in firestore.rules)
   pending rules unit tests (item 7) to verify a chained
   member->program->coach check actually behaves as intended before
   applying it somewhere health-data-sensitive.
4. [x] **Unread badges in Chat Hub** — a one-shot peek (not a live
   listener) on entering Chat Hub compares the newest message/announcement
   timestamp against the member's own `lastReadGeneralAt`/
   `lastReadAnnouncementsAt` (written on entering each room). Rules let a
   member touch only those two fields on their own roster doc.
5. [x] **Razorpay cleanup** — removed `PaymentResultListener`, the
   `razorpay-checkout` dependency, `RazorpayPaymentLauncher`, and the dead
   `createPaymentOrder` call site. The unreachable `care_hub` →
   `consult_stepper` → `payment_confirmation` chain still exists as inert
   scaffold (confirmed no live entry point) pending item 11's real rebuild.
6. [x] **Medication logging** — `medicationLogs` collection (rules,
   indexes), a 4th Daily Check-in step (taken/missed + optional name),
   a Track-tab quick-log chip, and coach visibility (including a missed
   dose in the Member Detail alert panel) in the console.
7. [x] **Rules unit tests, analytics events, and accessibility pass** —
   `firebase/rules-tests` (`@firebase/rules-unit-testing` against the real
   emulator, wired into CI's `firebase-rules` job) - 27 tests covering the
   highest-risk logic added this session: per-coach `programStaff()`
   scoping (assigned coach passes, unassigned coach denied, admin always
   passes), the two field-restricted self-update rules (chat reactions,
   unread-badge read markers), the users/{uid} program-field
   self-enrollment lock (including `checkinHourHint`), and health-log
   read/delete scoping. All 27 pass, which is real verification (not just
   "the rules file compiles") for exactly the logic that had none before.
   Extend this suite rather than re-deferring coachNotes/health-log
   program scoping blind next time.
   New `AnalyticsLogger` (never throws, never blocks a repository call)
   is called from `HealthRepository`'s success paths - the single cloud
   boundary - for `log_added`, `checkin_completed`, `program_joined`,
   `chat_message_sent`, `announcement_posted`, `data_export_requested`,
   and `account_deletion_requested`, complementing the existing
   `screen_view` tracking in `MainActivity`.
   Accessibility: an icon-level audit (every `IconButton`/clickable-`Icon`
   `contentDescription` across `ui/screens/`) found no genuine gaps -
   real gap was text fields relying only on `placeholder`, which TalkBack
   does not reliably expose as a persistent name. Fixed with a real
   `label` where visible fits (medication name, check-in numeric fields,
   search, email/password, program code) and an invisible
   `Modifier.semantics { contentDescription = ... }` where a visible
   label would break a compact design (OTP boxes, chat composer, the
   shared `OutlinedProfileField`).
8. [~] **Console deploy** — `deploy-firebase.yml` now also builds the
   console and includes `hosting` in the deploy target, so every backend
   deploy keeps `nirog-bhumi-app.web.app` in sync automatically (it was
   previously a separate, easy-to-forget manual step). Not fully closed:
   no way to independently browse/verify the Vercel URL from this sandbox
   (no general web access) - owner should confirm that URL separately, or
   rely on the Firebase Hosting URL going forward since it's now part of
   the automated pipeline.
9. [x] **Smart reminder timing** — a new `DAILY_CHECKIN` reminder type
   (in the existing Notification Settings toggle list, no new UI needed).
   `checkinHourHint` on `users/{uid}` is an exponential moving average
   updated on every genuine check-in completion (not an empty skip-
   through); `ReminderScheduler.scheduleSmart()` aligns the on-device
   WorkManager periodic request's first fire ~15 minutes before that
   hour, falling back to a safe elapsed-24h schedule until the hint
   loads or if the member has none yet.
10. [x] **Care+ community features** — @mentions (rendering-only
    highlighting of "@Name" tokens, no roster autocomplete yet), pin-a-
    message (staff-only, rules-enforced via `programStaff()`, banner at
    top of General), photo sharing, and voice notes (tap-mic record,
    per-bubble MediaPlayer playback) all shipped. Photos and voice notes
    share the same `program-chat-{photos,audio}/{programId}/{uid}`
    Storage pattern: a live Firestore membership check so the whole
    batch can view them, not just the uploader - the first storage.rules
    test coverage in this repo (9 new tests). Full rules-tests suite is
    now 42/42, including a real fix to the test harness itself (Node was
    running the two test files concurrently against one shared emulator,
    which made cross-service `firestore.get()` calls fail intermittently
    - `--test-concurrency=1` fixed it, not a rules bug).
11. [x] **Consultations** — by owner decision, this is now an honest
    external handoff rather than a rebuilt in-app flow: a real, reachable
    "Book a Consultation" row on the Care+ tab opens
    `nirogbhumi.com/consultation` in the browser. The old inert
    `care_hub -> consult_stepper -> payment_confirmation` in-app scaffold
    (unreachable since the Razorpay removal) is left as-is, not deleted,
    pending a real in-app rebuild later if the owner wants one.
12. [x] **Health data intelligence** — `computeSleepGlucoseInsight()`
    cross-references a member's own fasting glucoseReadings against the
    previous night's sleepLogs (Asia/Kolkata calendar day), surfaced on
    the Today tab and the insight_detail screen. Conservative by design:
    needs >=3 nights per bucket and an >=8 mg/dL average difference,
    shows nothing at all otherwise rather than a fabricated placeholder.
13. [x] **Shareable Health File link** — "Get shareable link / QR code"
    on the Health File screen reuses the existing `uploadPrivateFile`
    Storage path (owner-only, already allows PDF) and renders a
    client-side QR (`com.google.zxing:core`, new dependency). Documented
    honestly in the dialog copy: it's a bearer-token URL, not a true
    expiring signed URL - that would need a Cloud Function on the Admin
    SDK, a real gap flagged rather than silently implied as more secure
    than it is.
14. [x] **Retention/habit formation** — `checkinStreak` (consecutive
    Asia/Kolkata calendar days, computed as a side effect of
    `recordCheckinCompletion`, no new rule needed - owner-writable
    fields already permitted) shown warmly as a "$N-day rhythm" pill on
    Today from day 2 on, never framed as a loss to avoid. A first-week
    checklist (computed client-side from existing state, no new
    Firestore field) guides new members through their first reading,
    first check-in, and meeting their Care+ batch, and disappears once
    done. A one-time milestone banner (🎉 "$N-day rhythm!") fires right
    at the check-in that hits 7/30/100 days - a real event moment, not a
    persistent badge, so it can't repeat on a later view of the same
    day's already-completed check-in.
15. [x] **Admin console utility** — announcement templates (`ANNOUNCEMENT_TEMPLATES`
    dropdown on the composer), bulk "message all quiet members" (per-program
    bulk notification via the existing `sendBulkNotification` callable), and
    CSV roster export (`Members.tsx`, client-side CSV with proper quote
    escaping) all shipped.
16. [x] **Self-update system** — the app now detects, downloads, verifies,
    and installs new builds over itself without a manual APK reinstall
    cycle. `appUpdates/{channel}` (public-read, admin-write) and
    `releases/{channel}/*` Storage rules, both with passing
    `firebase/rules-tests`. Android: `VersionChecker` (numeric semver
    compare), `UpdateManager`/`UpdateRepository`/`ApkDownloader`/
    `UpdateInstaller` (SHA-256 verify + `FileProvider` install intent),
    a lifecycle-scoped on-launch/on-foreground/every-30-min check loop
    plus a 6-hourly WorkManager backstop, a Material `UpdateDialog`
    (mandatory updates omit "Later"), and a Developer Settings screen
    (version/build/git-commit, channel picker, manual check, release
    notes). `build-firebase-debug-apk.yml` now stamps a real
    `versionCode`/`versionName`/git-commit onto every build and
    (best-effort, via WIF) publishes release metadata + the APK to the
    `development` channel. Full architecture/testing-checklist/future-
    work writeup in `docs/self-update-system.md`. **Owner action
    required**: the CI publish step needs the same one-time WIF
    `gcloud` setup as `deploy-firebase.yml` (`docs/deploy-wif-setup.md`)
    — confirmed via a live CI run that `FIREBASE_WIF_PROVIDER`/
    `FIREBASE_DEPLOY_SERVICE_ACCOUNT` aren't populated yet, so the
    publish step currently skips itself cleanly (by design) rather than
    failing the build; everything else (detection, download, verify,
    install, Developer Settings) works today independent of that setup.
17. [x] **Core-dynamism audit** — a full re-verification pass over the
    22-item backlog the user asked for next: most of it (mentions, pin,
    unread badges, reply threading, typing indicators, trend correlation,
    Health File link, PDF export, admin templates/bulk/CSV, streaks,
    first-week checklist, milestones, analytics, a11y, rules-tests) turned
    out to already be shipped from earlier slices - verified by reading the
    actual code and rules rather than re-building blind, to avoid
    duplicate/conflicting implementations. Concrete gaps found and fixed:
    - Extended the `checklistLogs`-ordering fix (item that started this
      audit) to every other unordered `listenUserCollection` call reading
      a "recent N" from a per-user growing collection - `bpReadings`,
      `sleepLogs`, `walkLogs`, `labReports`, `glucoseReadings`, `orders`,
      `notifications` across Overview/Rhythm/HealthFile/Dashboard/Details
      screens. Two of these (`TodayTab`'s `checkedInToday` glucose/BP/
      weight check, and the BP "latest reading" `limit(1)` fetch) were
      real, live bugs, not just theoretical - an unordered `limit(1)`
      cannot actually return "the latest" anything.
    - `completedProtocols` (the one manual "movement" Daily Protocol item)
      was in-memory-only and reset every app restart despite showing as
      checked. Now persisted the same way as the other checklist items
      (`checklistLogs` doc keyed by day).
    - New composite index (`notifications`: `userId` + `createdAt` desc)
      to support the above without breaking the existing `status`+
      `scheduledFor`/`sentAt` indexes used by delivery.
    - Found and fixed an unrelated pre-existing bug while in this code:
      the Notification Inbox read a `category` field no Cloud Function
      has ever written (they all write `type`) - every notification was
      silently rendering the generic bell icon regardless of its real
      kind.
    - Added the weekly digest notification described in the original
      suggestion list ("you logged N/7 days this week") - `generateDailyContent`'s
      existing Monday `weeklyReports` job now also writes a `notifications`
      doc with the same logging-coverage math the Insights screen already
      shows, scheduled ~4h out so it lands mid-morning instead of during
      most users' default quiet hours; skipped entirely for a fully
      inactive user rather than nagging with "0/7."
    - Added unit tests for the two pure-logic modules that had none:
      `TodayFocusEngine` and `computeSleepGlucoseInsight`.
    - Day-key audit: confirmed the two coexisting day-key systems
      (`localDayKey`, device-local, for personal "did I do X today"
      signals; the Asia/Kolkata string keys, for anything that must match
      a Cloud-Function-written shared document like `batchStats`) are a
      deliberate, consistently-applied split, not a bug - no raw
      UTC-millis-window comparisons found anywhere in the app.
    - Cold-start empty-state flash: already mitigated by Firestore's
      explicit `PersistentCacheSettings` (enabled in
      `NirogBhumiApplication`), which serves the last cached snapshot
      instantly before revalidating - a hand-rolled DataStore cache layer
      would just duplicate that.
18. [~] **Real signed URL for the Health File link** — closes the one honest
    gap the earlier Health File share feature flagged in its own dialog copy:
    the "shareable link" was a non-expiring Storage download-token URL, not
    a true signed URL. New `getHealthFileShareLink` callable generates a
    real V4 signed URL (7-day expiry) via the Admin SDK. Requires one owner
    action (`roles/iam.serviceAccountTokenCreator` self-bound to the
    Functions runtime service account - see
    `docs/health-file-signed-url-setup.md`, same shape as the WIF setup);
    until that's done, the function throws a clear `failed-precondition`
    and the Android client automatically falls back to the previous
    non-expiring link rather than breaking the feature - the dialog copy
    honestly reflects whichever kind of link the member actually got.
19. [x] **Milestone moments, extended** — the existing check-in-streak
    milestone (7/30/100 days) now has two siblings using the same one-time-
    toast pattern: a "N walks logged" celebration (10/30/100, checked via a
    Firestore `count()` aggregation query right after a timed walk saves)
    and a "Day 30/60/90" program milestone on the Care+ hero, de-duplicated
    with a local `SharedPreferences` flag since these are device-side
    celebratory moments, not data other screens need to agree on.
20. [x] **`DashboardHub.kt` design-token restyle** — closed Phase 1 item 1's
    last open visual-consistency gap: every ad-hoc `Color(0xFF...)` on
    Today/Track/Insights/Care replaced with `NirogColor`/`NirogSpace`/
    `NirogType`, matching Chat/Announcements/Calendar/Details. Compose UI
    tests added for the check-in flow and Rhythm screen alongside it.
21. [x] **Chat fixes, enterprise error reporting, and admin console
    performance/correctness pass** — user-reported bugs run to ground with
    root causes, not just symptom patches:
    - Voice notes and photo sending were fully broken: `startRecording()`
      set `isRecording = true` even when `MediaRecorder.prepare()/start()`
      threw (mic held by another app, no mic present), leaving the UI stuck
      showing "Recording…" forever. Fixed with real success-checking, the
      modern `MediaRecorder(context)` constructor, and switched the photo
      picker to the system `PickVisualMedia` (no storage-permission edge
      case). Confirmed via code + rules audit (not just re-reading the
      diff) that mentions rendering and the photo/voice Storage paths are
      otherwise correctly wired - see the enrollment-gating note below for
      why they can still *look* broken to a given account.
    - `redeemProgramCode` "unauthenticated": a callable Function is a real
      network round-trip needing a genuinely fresh ID token, unlike
      Firestore writes which queue locally regardless of token state. Now
      forces a token refresh before the first attempt and retries once
      more on an `UNAUTHENTICATED` failure specifically, and every failure
      message embeds the real `FirebaseFunctionsException` code for
      diagnosis instead of a generic string.
    - New production error-reporting pipeline: every `CloudResult.Failure`
      surfaced to a real user also writes to `errorReports` (screen,
      message, code, `resolved`) via the same single global choke point
      MainActivity already used to show the snackbar. New admin-only
      Error Reports console page (bounded/ordered query, open/resolved/all
      filter, mark-resolved) makes real-device-only failures visible
      without needing to reproduce them blind.
    - Admin console Dashboard tiles used permanent `onSnapshot` listeners
      just to read a count, including one on the *entire* `users`
      collection - which every check-in app-wide rewrites - for the
      "Total members" tile. Replaced with periodic `getCountFromServer`
      aggregation polling (45s). Separately found "quiet member" tracking
      silently broken everywhere (Dashboard/Members/Batches/MemberDetail
      all read `lastCheckinAt` off `programMembers`, but it was only ever
      written to `users/{uid}`) - added a Firestore trigger
      (`onUserCheckinMirror`) to mirror it server-side, and bounded the
      now-hot-write `programMembers`/`users` listeners so per-admin reads
      scale with what's needed rather than the whole platform's write
      volume.
    - Coaches (the actual per-batch program managers) could not post
      announcements or pin/unpin chat messages from the app at all - the
      client's admin check only matched the literal claim `role ==
      "admin"`, excluding both `coach` and `super_admin`, even though the
      Firestore rules already authorized both (`programStaff()` =
      `admin() || assignedCoach(programId)`). Fixed with a
      `canManageProgram(programId)` helper that mirrors the server rule
      exactly (coach eligibility resolved via a query that only the
      assigned coach could get a result from).
    - Announcements restyled as a WhatsApp-Community-style broadcast
      channel: a channel-identity header (icon + program name + "only
      your coach posts here") and a channel-badge icon per post instead
      of an individual sender identity, reinforcing the one-to-many
      read-only mental model rather than looking like a chat thread.
    - Root-cause note for the user's combined "enrollment fails AND chat/
      voice/images/mentions don't work" report: every Care+ Firestore/
      Storage rule (`programMember()`) requires `activeProgramId`/
      `programActive == true` on `users/{uid}`, which only
      `redeemProgramCode` ever sets. If enrollment doesn't complete, every
      downstream Care+ feature is rules-gated shut for that account
      regardless of whether its own code is correct - consistent with
      everything else in this item auditing clean on inspection.
22. [x] **Super admin bootstrap, admin-side manual/pre-enrollment, and the
    confirmed IAM/org-policy deploy blocker** — closes the loop on the
    session-long "unauthenticated" enrollment saga with a real fix path,
    plus two new staff-side onboarding tools that don't depend on it:
    - `bootstrapSuperAdmin` (one-time, self-disabling via a Firestore
      marker doc) resolves the chicken-and-egg problem where no path to
      `super_admin` existed before the very first one; `setUserRole`/
      `createStaffAccount` now allow granting `super_admin` too, gated so
      only an existing `super_admin` can.
    - `adminEnrollUser`: staff can manually enroll or move any existing
      user into a program from the Users & Roles page (which now shows
      every user's program status inline) - a working fallback for the
      exact case a user's own `redeemProgramCode` call is stuck.
    - `inviteToProgram`/`revokeInvite` + `onUserCreate`'s
      `consumeMatchingInvite`: staff can pre-enroll someone by phone number
      or email *before* they've ever signed up (Programs page, "Onboard by
      phone or email") - the moment an account with that exact contact is
      created, `onUserCreate` auto-enrolls them, no code needed at all.
      `onUserCheckinMirror` extended to also mirror `fullName` into the
      roster once the member sets it, since auto-enrollment happens before
      a profile exists.
    - `deploy-firebase.yml` was `workflow_dispatch`-only (same class of bug
      as the APK build workflow) - real fixes sat committed but undeployed
      for hours. Fixed to auto-deploy on push.
    - Root-caused *why* fixes weren't taking effect even once deployed: the
      very first deploy of any brand-new `onCall` function fails to get its
      public-invoker IAM binding set, confirmed live across 5 different
      functions this session. Traced to its actual root cause with the
      owner's help: a Domain Restricted Sharing org policy blocks `allUsers`
      outright, and the deploying account lacks Organization Policy
      Administrator to override it - a people/access problem, not
      something fixable from code or CI. Exact remediation documented in
      `docs/deploy-wif-setup.md`, **pending action from whoever administers
      the org**.
    - Also fixed: the console's program editor only ever wrote
      `durationWeeks`, but every enrollment path read a `durationDays`
      field nothing had ever written, so `programDurationDays` silently
      came out 0 for every member (shows as "Day N" with no total instead
      of "Day N of 42"). Both paths now derive it from `durationWeeks`.
23. [x] **CSV bulk import (Programs/Batches/Calendar) + full member roster** —
    the enterprise-scale onboarding/scheduling ask: stop making staff add
    people or schedule events one at a time.
    - New shared `console/src/lib/csv.ts` (RFC4180-ish parser handling
      quoted/escaped fields, a serializer, and a browser-download helper) -
      Members.tsx's previously hand-rolled CSV export now runs on the same
      code instead of its own copy.
    - **Calendar**: "Download CSV template" / "Upload CSV" on the program
      calendar - bulk-creates `programEvents` directly via a chunked
      `writeBatch` (event writes were already plain client Firestore writes,
      so this needed no new backend function and works immediately,
      independent of the org-policy IAM block below). Per-row validation
      (title, valid type, parseable start time, safe http(s) link) with a
      clear per-row error summary instead of a silent partial import.
    - **Programs & Batches**: new `bulkOnboard` callable - given a CSV of
      contacts (+ program, on the global Programs page; program is implied
      per-batch on the Batches page), resolves each row to either an
      immediate enroll (if an Auth account already exists for that
      email/phone) or a pending invite (if not) in one server-side pass,
      returning a per-row outcome for an honest import summary instead of
      firing N individual calls. Same staff/own-program authorization
      boundary as `adminEnrollUser`/`inviteToProgram`, checked per row.
      Both pages also get a "Download CSV template" button. Like every
      other function added this session, `bulkOnboard` is itself a
      brand-new `onCall` and will need the same owner-side IAM/org-policy
      fix (added to the list in `docs/deploy-wif-setup.md`) before it's
      actually reachable.
    - **Members page**: rebuilt to query `users` (every account) instead of
      `programMembers` (enrolled-only) - the roster now shows every member
      platform-wide with a "Not enrolled" filter/status, not just Care+
      participants. No changes needed to `MemberDetail.tsx` - it already
      read every log collection straight off the uid, independent of
      program enrollment, so the full history (glucose/BP/sleep/walk/
      weight/lab reports/medications/check-ins/coach notes) was already
      reachable for any member once the roster itself stopped filtering
      them out. Batches.tsx's existing per-batch roster already linked to
      this same full-history view per member, so no separate "batch member
      detail" page was needed - just the CSV bulk-add shortcut above.
24. [x] **Account deletion → anonymization** — a deliberate product/policy
    change: `processApprovedDeletions` (`firebase/functions/src/index.ts`)
    used to erase every trace of a departing member. It now deletes only
    identifying context - `profiles`, `dailyActions`, `weeklyReports`,
    `sugarStories`, `consultations`, `userPrograms`, `programPlans`,
    `expertNotes`, `notifications`, `deviceConnections`, the `programMembers`
    roster row, and every raw uploaded file (lab scans, photos, PDFs - these
    show a name on their face) - and *anonymizes in place* the actual
    health-metric collections (`glucoseReadings`, `bpReadings`, `sleepLogs`,
    `walkLogs`, `weightLogs`, `medicationLogs`, `checklistLogs`,
    `dailyCheckins`, `labReports`): `userId`/`profileId` (and, for
    `labReports`, the free-text `labName`/`notes`/`fileUrl`) are stripped
    rather than the document being deleted. Firestore rules already gate
    every read in those collections on `resource.data.userId ==
    request.auth.uid`, so once that field is gone the record is unreadable
    by any individual user's client - it exists from then on only for
    internal, aggregate analysis (the stated goal: find out what actually
    helps people manage/reverse conditions like type-2 diabetes, without
    needing to know whose reading it was). The Auth account, `users/{uid}`
    doc, and all personal collections are still deleted outright - this
    isn't a "keep everything" softening, just a narrower definition of what
    counts as identifying.
    - Android: `DataControlsScreen` (`DetailsScreens.kt`) copy changed
      throughout from "delete"/"deletion" to "anonymize"/"anonymization",
      plus a new "What does anonymizing mean?" link that opens a plain-
      language explainer dialog (what gets deleted, what gets kept and how,
      and why - framed around the diabetes-reversal research goal). The
      underlying `requestAccountDeletion` method/`deletionRequests`
      collection name is unchanged - renaming was judged higher-risk than
      the payoff for a purely cosmetic identifier match, so a doc comment
      on the interface method carries the semantic change instead.
25. [x] **Privacy & Consent screen redesign** — the old screen's
    title+badge `Row` had no `weight`/wrap on the title `Text`, so a long
    title plus the REQUIRED/OPTIONAL badge could overflow the card at
    smaller widths (the reported "misaligned"); the outer content `Column`
    also had no `verticalScroll`, so it clipped rather than scrolled once
    content exceeded the viewport (the reported "irresponsive"). Both are
    fixed (`Modifier.weight(1f, fill = false)` on the title, `verticalScroll
    (rememberScrollState())` on the content column) as part of a full
    rewrite of `PrivacyConsentScreen`/`ConsentRow` in `DetailsScreens.kt`:
    "Expert review" consent is now a genuinely interactive `Switch`
    (previously static display only) that persists through the same
    `saveProfile("consent"...)` shape the onboarding consent step writes,
    with optimistic update + rollback on failure; health-data-storage and
    medical-disclaimer consent are shown as Required (non-togglable in
    place, since the app can't function without them - anonymizing the
    account via Data Controls is the stated way to withdraw them, said
    plainly in the row copy rather than left implied); a new "Anonymized
    data & research" card explains in plain language what anonymizing does
    and links straight to Data Controls; a new "Legal documents" section
    (`LegalLinkRow`) deep-links into specific `LegalCenterScreen` accordion
    sections via a new `NirogState.legalInitialSection` field (defaults to
    "Medical Disclaimer" when unset, preserving old behavior for any other
    entry point into Legal Center) instead of dumping the member on one
    generic "read the policy" link.
26. [x] **Announcement deletion + 24h default expiry, and a full audience-
    targeting rebuild** — replaces the old model (one `announcements` doc
    per program, fanned out by a Firestore trigger keyed on a single
    `programId`, with no delete/update rule at all - a genuine pre-existing
    gap, and read-scoped so loosely any enrolled member of *any* program
    could read *every* program's announcements). Composing now goes through
    one `createAnnouncement` callable (`firebase/functions/src/index.ts`)
    that resolves a five-way audience - a specific program/batch (or several
    at once), all enrolled Care+ members, every app user, members inactive
    N+ days (`lastCheckinAt`, missing counted as inactive too - a plain range
    query would silently exclude anyone who's *never* checked in), or
    everyone not currently enrolled - into a concrete recipient list
    (`resolveAudienceUids`, capped at 5,000 per send), then fans out across
    any combination of three channels: in-app (a denormalized copy written
    to each recipient's own `users/{uid}/announcements/{id}` subcollection -
    trivially rules-scoped to `owner(uid)`, and what `AnnouncementsScreen`
    now reads from instead of a per-program query), push (the existing
    `notifications`/`sendPendingNotifications` pipeline, `type:'announcement'`
    bypassing quiet hours/caps same as before), and email (writes into a
    `mail` collection in the shape the Firebase "Trigger Email" extension
    expects - honestly partial: the extension isn't installed yet, an
    owner-gated prerequisite documented in `docs/deploy-wif-setup.md` #6,
    same pattern as the org-policy IAM fix). A coach may only target
    program(s) they're the assigned coach of; every cross-program scope is
    admin-only. Every announcement gets a 24-hour default expiry
    (`expiresAt`, overridable per send) enforced two ways: immediately in
    the UI (the console list and the app's `AnnouncementsScreen` both stop
    showing it), and for real within 15 minutes by piggybacking the cleanup
    onto the existing `sendPendingNotifications` schedule rather than adding
    a 4th Cloud Scheduler job (`deleteAnnouncementDoc` removes the source
    doc plus every recipient's fan-out copy, tracked via a `recipientUids`
    array on the source doc rather than a collection-group query). A new
    `deleteAnnouncement` callable does the same thing on demand - a coach
    may only remove their own post, admin/super_admin may remove any. The
    console's Announcements page gained a full targeting UI (audience
    scope selector, multi-program checklist, inactive-days input, three
    channel checkboxes, a live "Preview audience" recipient-count check via
    `previewAnnouncementAudience` before sending, and a Delete button per
    past announcement); mobile compose is unchanged in scope (still posts
    to the member's own program only) but now goes through the same
    `createAnnouncement` callable instead of a raw client write, and gained
    its own delete button (staff-only, confirm dialog). New rules-tests
    cover both the source doc (staff-read-only, zero client writes) and the
    fan-out subcollection (owner-read-only, zero client writes).
27. [x] **Full-platform security audit, round 2** — a deliberate re-pass
    (`firebase/firestore.rules`, `firebase/storage.rules`,
    `firebase/functions/src/index.ts`, the Android manifest/update-install
    flow, and both npm dependency trees) covering everything added since
    the round-1 audit (item... security items above): CSV bulk import,
    manual/pre-invite enrollment, super-admin bootstrap, and the
    announcements rebuild. Found and fixed one real, exploitable gap: the
    `users/{uid}` **create** rule only ever locked down the `role` field
    ("bootstrap self-enrollment bypass") - `programActive`, `activeProgramId`,
    `activeProgramName`, `programDurationDays`, and `status` were all still
    free for *any* signed-in client to set on their very first
    `users/{uid}` write, because the existing **update** rule's "must equal
    the already-stored value" guard has nothing to compare against on a
    *create* (the doc doesn't exist yet). A scripted attacker (no app
    needed, just a valid Firebase Auth account and the public Firestore SDK)
    could sign up and, in the moment before `onUserCreate`'s merge-only
    trigger writes its own fields, `setDoc` their own profile doc with
    `programActive: true` and any `activeProgramId` they chose - free
    Care+ enrollment into any program of their choosing, bypassing
    `redeemProgramCode` entirely, since the trigger's merge never touches
    those keys and would silently leave the attacker's version in place.
    Fixed by requiring those fields be entirely *absent* on create (not
    just role-restricted), mirroring the update rule's boundary - they can
    now only ever be set by `admin()` or a Cloud Function via the Admin
    SDK, matching the original intent. Four new rules-tests cover the
    create path specifically (the existing "self-enrollment bypass fix"
    describe block only ever covered *update*). One legitimate client call
    (`WelcomeFlow.kt`'s onboarding-complete `saveProfile`) was echoing
    `programActive` back on every completion - always redundant, since it's
    only ever a local mirror of what `redeemProgramCode` already set
    server-side - removed rather than special-cased, so the client no
    longer depends on that field passing through at all.
    - Also fixed, lower severity: `console`'s `firebase` dependency
      (10.14.1, the latest available 10.x) carried a **High**-severity
      transitive `undici` advisory plus 9 moderate ones; bumped to
      `^11.10.0` (typecheck + build verified clean, no code changes
      needed) - resolved all but the un-related dev-server-only
      `esbuild`/`vite` moderate finding (affects `vite dev`, not the
      deployed production build, left as-is rather than force-upgrading
      Vite for a risk that doesn't reach production). `firebase-admin` in
      `firebase/functions` bumped `^13.4.0` → `^14.1.0` for the same
      reason (marginal improvement; the remaining moderate findings are
      several layers deep inside Google's own `@google-cloud`/`google-gax`
      dependency chain, already at the latest published version - not
      independently fixable from this repo).
    - Everything else checked and found clean: every `onCall` in
      `index.ts` requires `requireUser()` and checks the server-issued
      role custom claim (never a client-supplied role/uid); ownership
      checks (`getHealthFileShareLink`, `requestDataExport`,
      `exportUserData`) are all path/uid-prefix-scoped so one member can
      never reach another's private Storage files or export data;
      `storage.rules`'s `safeType()`/`safeAudioType()` content-type regexes
      are full-string matches (no substring-injection room to smuggle a
      different MIME type through); `bootstrapSuperAdmin` is a hardcoded
      single email + a transaction-guarded one-time marker (already
      consumed, permanently disabled); the Android manifest has
      `allowBackup="false"`, full `data_extraction_rules`/`backup_rules`
      exclusions, a narrow `FileProvider` path config, no custom deep-link
      scheme, and the self-update install flow's checksum verification and
      `FileProvider`/`FLAG_GRANT_READ_URI_PERMISSION` usage are sound.
      A full pass over the admin console, remaining CI/CD workflow
      hardening, and further Android review (WebView/logging/local storage)
      is still outstanding - continuing this audit.
