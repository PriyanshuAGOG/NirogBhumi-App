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

### Phase 0 — Foundations ✅ in progress
- [x] Design-system tokens (`DesignSystem.kt`, `Type.kt`) — colors on the
  existing brand palette, spacing/radius/elevation/type scale.
- [ ] Downloadable Google Fonts (Fraunces + Manrope) slice.
- [ ] Reusable Compose components: `NirogCard`, `SectionLabel`, `StatusChip`,
  `PrimaryButton`, `FocusCard`, `RowCard`, `AvatarStack` — the kit every
  screen composes from.

### Phase 1 — Member app core loop (highest visible impact)
1. Restyle shells (top bars, bottom nav, Today) onto the kit.
2. Check-in v2: device pre-fill banner (Health Connect steps/sleep), sleep &
   activity steps, segmented progress, skip-always.
3. **Body Report** screen (instant payoff) + **Health File** renderer & PDF
   share (the USP pair).
4. **Rhythm** screen (7-day ring, 30-day non-punitive grid, gentle nudge).

### Phase 2 — Care+ cohort (realtime)
5. Care tab batch home: header, coach card, Batch Pulse (presence +
   collective goal), journey map, next-event, teasers; chat icon top-right.
6. **Chat Hub**: Announcements (read-only for members) + General rooms, live
   listeners, unread badges, long-press report.
7. **Program Calendar** (member view) → detail sheet + "Remind me".
8. `batchStats` aggregation folded into the existing daily Function.

### Phase 3 — Web admin / coach console (new app, `console/`)
9. Scaffold: React + TS + Vite + Firebase SDK + role-gated auth shell.
10. **Moderation queue** (realtime `reportedMessages`) — first working screen.
11. Batch management: roster, per-member consistency, message a member.
12. Announcement composer + **Calendar editor** (create/edit events, optional
    auto-announce on change).
13. Programs & codes admin; coach assignment; role management (super-admin).
14. Content (Learn) authoring; consultations view; support inbox.
15. Deploy to Firebase Hosting.

### Phase 4 — USP deepening & polish
16. Focus-card personalization (`checkinHourHint`), Daily Insight engine.
17. Progressive onboarding micro-questions; notification tone/logic audit.
18. Health File share-link (Storage + signed access) beyond PDF.

### Phase 5 — Enterprise hardening (cross-cutting, not deferred)
- **Security:** App Check enforced on prod; rules unit tests
  (`@firebase/rules-unit-testing`); least-privilege claims; PII handling review.
- **Observability:** Crashlytics (app), Cloud Functions logging + alerts,
  Sentry (console), structured `auditLogs` for every admin action.
- **Analytics:** the PRD success metrics wired as Analytics events
  (check-in completion time, Health File shares, D7 retention funnels).
- **Offline & resilience:** Firestore offline persistence on; optimistic chat
  sends with retry; graceful empty/error states everywhere.
- **Accessibility:** content descriptions, 4.5:1 contrast (status colors
  chosen for it), dynamic-type friendly scale, one-handed reach.
- **QA:** Compose UI tests for the check-in flow and Rhythm; console
  component tests; a manual release checklist.
- **CI/CD:** existing Actions build for the app (fix stale `FIREBASE_TOKEN`
  secret to restore auto-distribution); add a console build+deploy workflow;
  Functions deploy workflow; rules/index deploy on change.

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

1. **CI `FIREBASE_TOKEN` secret expired** → auto-distribution of new APKs is
   broken. Fix: `firebase login:ci` → update repo secret (Settings →
   Environments → production). Owner action.
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
