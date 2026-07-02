# Nirog Bhumi — Product Requirements Document (v2 UX Overhaul)

Status: Approved direction (prototype reviewed). This PRD specifies the
complete UX/UI system and the new feature stack. Companion documents:
`docs/UX_PSYCHOLOGY_PLAN.md` (research & rationale) and
`docs/prototype.html` (interactive visual reference — the source of truth
for look & feel).

---

## 1. Product thesis

Nirog Bhumi is a **calm, premium, Ayurveda-rooted health companion** for
people managing diabetes and blood pressure, with two layers:

- **Free layer** — a daily tracking ritual that takes under 2–3 minutes,
  pays the user back immediately (Body Report) and cumulatively (Health
  File), and never guilts them.
- **Care+ layer** — a paid program experience framed as a **batch**: a
  named coach, a shared journey calendar, group chat, and collective
  (never competitive) accountability.

One sentence per tier:

> Free: "Two minutes a day, and your health file is always ready for any
> doctor."
> Care+: "You're not doing this alone — your batch and your coach are on
> the same path."

### The USP stack

1. **Health File** — every check-in builds an always-current, doctor-ready
   summary (shareable as PDF/link). Indian patients carry paper files to
   every appointment; we make that file live, complete, and one tap away.
   No mainstream tracker leads with this.
2. **Body Report** — instant, plain-language payoff after each check-in:
   what today's numbers mean + one concrete suggestion. Logging is never
   a dead-end form.
3. **Batch, not subscription** — Care+ is a cohort with a human coach and
   collective goals, not a feature gate.
4. **Non-punitive by design** — no streaks that reset, no red "missed"
   marks, no leaderboards. Rhythm, not guilt.

---

## 2. Design principles

1. **One thing at a time.** Every screen has exactly one primary action.
   Anything secondary is visually demoted (pills, below the fold).
2. **Give before you ask.** Every data request is preceded or immediately
   followed by value (insight, report, pre-fill). Microcopy explains *why
   we ask*.
3. **Never punish.** Missed days are neutral (empty, not red). Copy is
   warm and forward-looking. Progress views can never "hit zero."
4. **Calm premium.** Fraunces display type + Manrope body, deep forest
   green + cream + muted gold, generous spacing, soft two-layer shadows,
   150–200 ms eased motion. No confetti, no badges, no gamification
   theater.
5. **Honest data only.** No fabricated numbers, no fake trends. Empty
   states teach; they never simulate.

---

## 3. Users & tiers

| | Free user | Care+ member | Admin / Coach |
|---|---|---|---|
| Daily Check-in + device sync | ✅ | ✅ | ✅ |
| Body Report + Health File | ✅ | ✅ | ✅ |
| Rhythm (consistency) view | ✅ | ✅ | ✅ |
| Insights, Learn articles | ✅ | ✅ | ✅ |
| Family Circle sharing | ✅ | ✅ | ✅ |
| Batch home, journey map | — | ✅ | ✅ |
| Group chat + announcements | — | ✅ (announce: read-only) | ✅ (can post announcements) |
| Program calendar | — | ✅ (view) | ✅ (create/edit events) |
| Batch pulse / collective goals | — | ✅ | ✅ (sees per-member detail) |
| Coach messaging | — | ✅ | ✅ |

Admin is a Firebase custom claim (`role: admin`), already enforced in
Firestore rules.

---

## 4. Information architecture

Five bottom tabs (unchanged): **Today · Track · Insights · Care · Learn**.

- **Today** — greeting, "Today's One Thing" focus card, Daily Insight,
  demoted quick pills, Health File row, Family Circle teaser.
- **Track** — Daily Check-in entry point + per-metric overview screens
  (Sugar, BP, Weight, Sleep, Walking & Activity) + Rhythm view.
- **Insights** — trends over real data, weekly recap.
- **Care (Care+)** — batch home. Top-right of this tab shows a **chat
  icon (with unread badge)** instead of the notification bell; tapping it
  opens the **Chat Hub**. Body: batch header, coach card, Batch Pulse,
  journey map, next calendar event, announcement teaser.
- **Learn** — articles (WordPress) + consultation booking (website).

Global top bar (Today/Track/Insights/Learn): profile avatar left,
notification bell right. **Care tab only:** chat icon right (this is the
dedicated chat entry the product requires).

---

## 5. Pillar A — Design system (foundation, everything inherits)

Tokens (authoritative values live in `docs/prototype.html` `:root`):

- **Color roles:** `surface / surface-alt / surface-card / surface-sunken`,
  `ink-primary / ink-secondary / ink-muted / ink-on-accent`,
  `accent-forest / accent-forest-soft / accent-gold / accent-gold-soft /
  accent-terracotta`, and status roles `in-range / attention / critical /
  neutral` each with a paired soft background. Status colors are the ONLY
  way clinical state is communicated, used identically everywhere.
- **Type:** Fraunces (display/headings, weights 500–700) + Manrope (body,
  400–800). Scale: 34/24/21/19 display-heading, 14 body, 12.5 secondary,
  11.5 caption, 11 overline (gold, letterspaced, uppercase).
- **Spacing:** 4 / 8 / 12 / 16 / 24 / 32 / 48.
- **Radius:** 10 (small), 16 (medium), 24 (cards), pill (buttons/chips).
- **Elevation:** two-layer soft shadow for cards; stronger float shadow
  for sheets/dialogs. No hard borders on cards.
- **Icons:** single outlined set, 2 px stroke, filled variant only for the
  active bottom-nav item.
- **Motion:** 150–200 ms ease-out for screen transitions, chip selection,
  progress fills; check-in step advance slides horizontally.

Compose implementation: a `NirogTheme` object exposing these as semantic
tokens (`NirogColor.statusInRange`, `NirogSpace.md`, …); ad-hoc hex values
in screens are refactored to tokens.

---

## 6. Pillar B — Daily Check-in (the 2-minute layer; free + Care+)

**Goal: full check-in ≤ 2–3 minutes; ≤ 45 seconds with a connected device.**

### Flow (sequential wizard, every step skippable)

0. **Synced pre-fill banner** — if Health Connect is linked, the flow
   opens with "⌚ 6,240 steps · 7 h 12 m sleep already synced — 2 steps
   left." Synced steps are shown as confirm-only cards, not input forms.
1. **Blood sugar** — stepper + chips (Fasting / Post-meal / HbA1c).
2. **Blood pressure** — systolic/diastolic steppers.
3. **Weight** — stepper, pre-filled from last entry.
4. **Sleep** — pre-filled from device if synced; else bedtime/wake pickers
   (duration auto-computed).
5. **Activity** — steps from device; manual add for walk/yoga/exercise.
6. **Body Report** (the reward screen — see below).

UX rules: segmented progress bar (gold fill); "Skip this step" always
visible; reassurance microcopy ("Nothing is graded here — log what's true
today"); numeric steppers with large tap targets instead of keyboards
where possible; the whole flow works one-handed.

### Body Report (instant payoff)

Immediately after the last step, one screen:
- Per-metric status rows using status tokens ("Fasting sugar 96 — in
  range", "BP 124/82 — steady", "Sleep 7 h 12 m").
- **One** plain-language insight sentence computed from the user's own
  recent data ("3 of your last 4 fasting readings were in range — up from
  2 last week").
- **One** concrete, gentle suggestion ("Good day for a 15-minute walk
  after lunch").
- Footer: "Health File updated · Share" action.

No scores, no grades, no percentage-of-perfection.

### Health File (cumulative payoff — flagship USP)

- Auto-maintained summary: profile basics, current medications-free by
  design (we removed meds tracking) — conditions, latest + 30/90-day
  metric summaries, lab report attachments, consistency pattern.
- Rendered as a clean shareable document (PDF generated on-device;
  Phase 2: share link). One tap from Today ("Health File — updated
  today · Share") and from the Body Report.
- Positioning line inside the file: "Prepared with Nirog Bhumi — data
  logged by the patient."

### Device sync

- Health Connect (already integrated for steps) extended to sleep and
  heart rate where available. Sync status surfaced in the check-in
  banner and in Track. Manual entry always remains available.

---

## 7. Pillar C — Today screen ("Today's One Thing")

- **Focus card** (forest gradient, gold CTA): the single next-best action,
  chosen by rule: earliest unlogged metric for today, weighted by the
  user's own typical logging hour ("You usually log this around 7:30 AM").
  CTA opens the check-in wizard at that step.
- **Daily Insight card** (sunken surface, bulb icon): one real
  observation from the user's data with a small sparkline; falls back to
  one piece of plain-language health literacy when data is thin.
- **"Also today" pills**: remaining metrics as quiet pills.
- **Health File row**: last-updated state + Share.
- **Family Circle teaser**: avatar stack + "Share this week with family."
- Care+ members additionally see a slim batch strip ("9 of 12 batchmates
  checked in today") linking to the Care tab.

---

## 8. Pillar D — Rhythm (non-punitive consistency)

- **7-day ring** ("4/7 days logged") — a fill, never a countdown; cannot
  hit a shaming zero state (0/7 renders as an invitation, not an alarm).
- **30-day grid** — logged days in `status-in-range` green; missed days
  stay neutral (never red); future days dashed.
- **Warm recap copy**: "You checked in 4 of the last 7 days — steady
  progress." Gaps framed neutrally, always paired with one specific,
  encouraging next step.
- **Gentle nudge card** after ≥2 quiet days: "It's been 2 days since your
  last check-in. No worries — let's pick it back up today." One tap into
  the wizard.
- Weekly recap (existing Cloud Function output) adopts the same tone.
- **Explicitly banned:** streak counters, fire emoji, "don't break the
  chain," red missed-day marks, leaderboards.

---

## 9. Pillar E — Care+ cohort layer

### 9.1 Batch home (Care tab, member view)

Top to bottom:
1. **Batch header** (forest gradient): program name, "Your Batch — July
   2026," avatar stack + "12 people on this journey with you."
2. **Coach card**: photo/initials, name, "Your program coach," Message
   button. The coach is always a named human — never "Admin."
3. **Batch Pulse** (accountability without competition):
   - "9 of 12 batchmates checked in today" (presence, no names, no ranks).
   - **Collective goal** progress bar: "Batch goal: 250 km walked
     together this month — 182 done." Cooperative framing only.
   - Member's own contribution shown only to themselves and the coach.
4. **Journey map**: program phases as a vertical path with "You are
   here" (replaces any grid-calendar feel on the home surface).
5. **Next event card**: soonest calendar event with date chip → Calendar.
6. **Announcement teaser**: latest coach announcement → Chat Hub
   (Announcements room).
7. **Batch chat teaser**: unread count → Chat Hub (General room).

Non-member view of the Care tab: calm upsell describing the batch
experience (coach, calendar, chat, collective goals) + "Have a program
code?" entry. No feature-gate nagging anywhere else in the app.

### 9.2 Chat (dedicated section)

**Entry:** chat icon with unread badge in the **top-right of the Care
tab** (replacing the bell on this tab only). Tapping opens the Chat Hub.

**Chat Hub** — a room list, launching with exactly two rooms:
1. **📣 Announcements** — read-only for members; only admins/coaches can
   post. Composer hidden for members; a quiet "Only your coach posts
   here" note instead. Posts support text (Phase 2: image + event links).
2. **💬 General — <Batch name>** — all batch members can post. Plain
   text messages, sender name + relative time, own messages
   right-aligned in forest, others left on card surface.

Rules & safety (existing infra):
- Long-press any message → Report (writes to `reportedMessages`; admins
  review). Hint shown in the room header.
- Membership scoped by `programActive` + `activeProgramId` (existing
  Firestore rules for `programChatMessages` / `announcements` already
  match this model).
- Unread badge = messages newer than the user's `lastReadAt` per room
  (stored on the user's enrollment doc).

Deliberately excluded for v2 (anti-overwhelm): DMs between members,
threads, reactions, media in general chat, multiple topic rooms. The
coach "Message" button opens a support flow, not a DM system.

### 9.3 Program Calendar (admin-managed)

- **Member view:** month header + week strip with event dots; agenda
  list below. Each event: date chip, time, title, type tag (Live
  session / Group walk / Lab week / Q&A), venue or join-link, "what to
  bring" note. Tap → detail sheet with full description and "Remind me"
  (schedules a local reminder via existing WorkManager path).
- **Admin view:** same screen + edit affordances — "+ Add event" and a
  pencil on each event (title, date/time, type, description, link).
  Members never see edit controls.
- Schedule changes: editing an event can optionally auto-post an
  announcement ("Saturday's session moved to 7 pm") — one checkbox in
  the admin edit sheet, so schedule news never silently changes.
- Data: new `programEvents` collection — read: program members; write:
  admin only.

### 9.4 Community accountability & progress (summary of mechanics)

| Mechanic | What it is | What it is NOT |
|---|---|---|
| Batch Pulse | "9 of 12 checked in today" | Not a named list, not a ranking |
| Collective goal | Batch-level shared target bar | Not individual leaderboards |
| Journey map | Shared phase progress ("Week 3–6 — you are here") | Not per-person completion % |
| Coach visibility | Coach sees per-member consistency; reaches out privately when someone fades | Never surfaced to other members |
| Milestones | Batch-level celebrations posted by coach in Announcements | No auto-generated badges |

---

## 10. Notifications

- **One trigger at a time**: max one health nudge per day by default
  (server cap already exists), skipped automatically if the metric is
  already logged.
- Types: check-in reminder (user-scheduled window), gentle re-entry (after
  2+ quiet days), coach announcement (Care+), event reminder (Care+),
  weekly recap.
- Tone audit: every string supportive, none nagging. In-app inbox
  (existing `notifications` collection) mirrors pushes.
- Bell (non-Care tabs) → notification inbox. Chat icon (Care tab) → Chat
  Hub. These are distinct surfaces and never mixed.

---

## 11. Data model (Firestore deltas)

Existing collections stay. New/changed:

- `programEvents/{id}`: `programId, title, type, startsAt, endsAt?,
  location?, link?, description, bring?, createdBy, updatedAt`.
  Rules: read = program member of `programId`; create/update/delete =
  admin.
- `announcements` (existing): unchanged (admin create, member read).
- `programChatMessages` (existing): unchanged (member create/read within
  own program).
- User enrollment doc gains: `lastReadGeneralAt`, `lastReadAnnouncementsAt`
  (unread badges), `checkinHourHint` (typical logging time for the focus
  card).
- `batchStats/{programId_day}` (server-aggregated by a scheduled
  function within the existing 3-job budget — appended to
  `generateDailyContent`): `checkedInCount, memberCount, collectiveKm`.
  Read: program members. Write: functions only.
- Health File is **rendered client-side** from existing collections — no
  new stored document in v2 (PDF generated on device).

---

## 12. Permissions matrix (enforcement summary)

| Action | Free | Care+ member | Admin |
|---|---|---|---|
| Read/write own health logs | ✅ | ✅ | ✅ |
| Read announcements | — | ✅ | ✅ |
| Post announcements | — | — | ✅ |
| Read/post general chat | — | ✅ (own program) | ✅ |
| Report a message | — | ✅ | ✅ (reviews) |
| Read program events | — | ✅ (own program) | ✅ |
| Create/edit program events | — | — | ✅ |
| Read batch stats | — | ✅ (own program) | ✅ |

---

## 13. Success metrics

- **D7 check-in retention** (any check-in on day 7) — primary.
- Median check-in completion time (target: <150 s manual, <45 s synced).
- Health File shares per active user per month (USP validation).
- Care+ : % of batch checking in daily; chat weekly-active %; event
  "Remind me" rate.
- Qualitative: zero user reports of guilt/shame language.

---

## 14. Build phases

**Phase 1 — Look & feel + core loop (highest visible impact)**
1. Compose design-system tokens (`NirogTheme`) + restyle Today, Track,
   bottom nav, top bars per prototype.
2. Check-in wizard v2: pre-fill banner, sleep/activity steps, Body
   Report screen.
3. Rhythm screen (ring + 30-day grid + nudge) replacing any
   streak-shaped UI.

**Phase 2 — Care+ cohort**
4. Care tab batch home v2 (header, coach card, Batch Pulse, journey,
   teasers) + chat icon top-right.
5. Chat Hub (2 rooms) on existing chat/announcement collections +
   unread badges.
6. Program Calendar (member view, then admin editing + auto-announce).
7. `batchStats` aggregation inside `generateDailyContent`.

**Phase 3 — USP deepening**
8. Health File renderer + PDF share.
9. Focus-card personalization (`checkinHourHint`), Daily Insight engine.
10. Progressive onboarding micro-questions; notification tone audit.

**Out of scope for v2:** DMs, threads/reactions, media in chat, extra
chat rooms, leaderboards (permanently), meal/medication tracking
(removed by design), payments (deferred), share-link Health File
(Phase 2 of the feature, PDF first).
