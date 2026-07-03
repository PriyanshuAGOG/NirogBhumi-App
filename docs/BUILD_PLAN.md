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

### Phase 1 — Member app core loop ✅ mostly done
1. [ ] Full Today-screen restyle onto the kit (Today already leads with a
   single "One Action" focus card from earlier work; a Health File entry
   point was added, but a full token-based re-skin is still open).
2. [ ] Check-in v2 device pre-fill banner (Health Connect steps/sleep
   auto-confirm) — not yet built; check-in is still manual-entry only.
3. [x] **Body Report** screen (instant payoff, real data, honest empty
   states) + **Health File** renderer with real on-device PDF generation
   and share, plus proper error surfacing on failure.
4. [x] **Rhythm** screen (7-day ring, 30-day non-punitive grid, gentle
   nudge) reading real logged-days from Firestore.

### Phase 2 — Care+ cohort (realtime) ✅ mostly done
5. [x] Chat icon top-right on the Care tab (replacing the bell there) →
   Chat Hub. [ ] Full batch-home restyle (header/coach card/journey map)
   onto the kit is still open — the existing Care+ layout from earlier work
   is functional but pre-dates the token system.
6. [x] **Chat Hub**: Announcements (read-only for members) + General rooms
   (reusing the existing chat/announcement screens), long-press report,
   non-member unlock. [ ] Unread badges not yet wired.
7. [x] **Program Calendar** now reads real `programEvents` (Upcoming
   Events section) with a genuine one-off "Remind me" (`EventReminderWorker`).
8. [x] `batchStats` aggregation: real-time PII-free check-in counting via
   `recordBatchCheckin()` on the glucose/BP triggers, plus a daily collective
   walking-minutes rollup. [x] `BatchPulseCard` on Android reads it live,
   honest "be the first" zero-state.
   - **Found & fixed in the process:** nothing ever wrote `programMembers`
     on enrollment (the console's roster/Batch Pulse depended on a
     collection that was always empty) — `redeemProgramCode` now writes it
     atomically with the enrollment.

### Phase 3 — Web admin / coach console ✅ done (`console/`)
9. [x] Scaffold: React + TS + Vite + Firebase SDK + role-gated auth shell.
10. [x] **Moderation queue** (realtime `reportedMessages`).
11. [x] Batch management: roster, Batch Pulse, coach→member messaging
    (`coachMessages`).
12. [x] Announcement composer + **Calendar editor** (create/edit/delete
    events, optional auto-announce on change).
13. [x] Programs & codes admin. [ ] Coach assignment UI and true
    per-coach batch scoping (rules currently treat any coach as able to
    manage any program - a documented, deliberate MVP simplification, see
    `firestore.rules` `staff()`).
14. [x] Content (Learn) authoring; consultations view (read-only); support
    inbox.
15. [x] Deploy target wired (`firebase.json` hosting block, `console/dist`,
    SPA rewrite). [ ] Not yet actually deployed to a live Hosting URL.

### Phase 4 — USP deepening & polish (not started)
16. Focus-card personalization (`checkinHourHint`), Daily Insight engine
    beyond the current rule-based takeaway.
17. Progressive onboarding micro-questions; notification tone/logic audit.
18. Health File share-link (Storage + signed access) beyond the current
    on-device PDF share-sheet.

### Phase 5 — Enterprise hardening (cross-cutting, not deferred) — not started
- **Security:** App Check enforced on prod; rules unit tests
  (`@firebase/rules-unit-testing`); least-privilege claims (see Phase 3.13
  coach-scoping gap above); PII handling review.
- **Observability:** Crashlytics (app), Cloud Functions logging + alerts,
  Sentry (console), structured `auditLogs` for every admin action
  (`setUserRole` already writes one; extend to moderation/calendar edits).
- **Analytics:** the PRD success metrics wired as Analytics events
  (check-in completion time, Health File shares, D7 retention funnels).
- **Offline & resilience:** Firestore offline persistence on; optimistic chat
  sends with retry; graceful empty/error states everywhere (Body Report,
  Rhythm, Batch Pulse already fail open to honest empty states rather than
  crashing; Health File now surfaces real share/generation errors - audit
  the remaining screens the same way).
- **Accessibility:** content descriptions, 4.5:1 contrast (status colors
  chosen for it), dynamic-type friendly scale, one-handed reach.
- **QA:** Compose UI tests for the check-in flow and Rhythm; console
  component tests; a manual release checklist.
- **CI/CD:** ✅ `FIREBASE_TOKEN` secret refreshed, auto-distribution
  confirmed working. [ ] Console build+deploy workflow; Functions deploy
  workflow; rules/index deploy-on-change workflow (all currently manual).

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
10. [~] **Care+ community features** — @mentions (rendering-only
    highlighting of "@Name" tokens, no roster autocomplete yet) and
    pin-a-message (staff-only, rules-enforced via `programStaff()`, banner
    at top of General, 6 new rules-unit tests) shipped. Photo sharing and
    voice notes still open - need Storage upload plumbing wired into the
    chat composer specifically (the general upload path already exists
    for Health File/lab reports).
11. [ ] **Consultations** — build a real non-payment booking flow
    end to end (replaces the inert scaffold from item 5).
12. [ ] **Health data intelligence** — basic trend correlation insight.
13. [ ] **Shareable Health File link** (signed URL / QR).
14. [ ] **Retention/habit formation** — streak number, first-week
    checklist, milestone moments.
15. [ ] **Admin console utility** — announcement templates, bulk "message
    all quiet members," CSV roster export.
