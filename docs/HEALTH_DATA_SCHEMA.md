# Health data: schema, ownership and rules

One page that says what a health record looks like, who may write it, and how every screen reads it.
Code: `app/src/main/java/com/example/health/domain/` (pure Kotlin, JVM-tested) and `data/HealthDataStore.kt`.
Rules: `firebase/firestore.rules` (section "Health logs"). Rules tests: `firebase/rules-tests/health-log-window.test.mjs`.

## One read path

Every screen that shows a reading (Today, Track, each metric screen, Insights, 30-day trends, Rhythm,
Body Report, Health File and its PDF, the weekly PDF) reads one `HealthUiState` from `HealthDataStore.ui`
(a `StateFlow`, collected with `collectAsStateWithLifecycle`). Firestore is the source of truth:

- one bounded, ordered listener per collection (`createdAt` desc; 300 glucose, 150 BP/weight/sleep,
  250 activity, 120 medication, 50 lab reports), re-sorted by `measuredAt` so imports and late entries
  land on the right day;
- a write appears at once (Firestore returns it from the local cache) and is replaced by the server
  copy when it lands; after a save no screen has a variable to forget to update;
- family-profile entries (another `profileId`) are excluded from the signed-in member's view;
- loading, "couldn't load" (per metric, with Try again) and stale/offline are explicit states, never an
  empty screen that looks like "no data".

There are no per-screen authoritative copies. The old `NirogState` fields (`fastingSugarValue`,
`sugarLogs`, `latestBpReading`, `sleepHours/Minutes`, `stepsLogged`, `checkedInToday`) were removed.

## Collections and fields

All documents carry `userId`, `profileId`, `createdAt` (server time), `source`. `measuredAt` is when the
thing happened (for sleep: when the member woke up).

| Collection | Canonical fields | Legacy shapes still read |
|---|---|---|
| `glucoseReadings` | `value` (mg/dL; HbA1c in %), `unit`, `readingType` (`fasting`, `post_meal`, `random`, `hba1c`, `device`), `measuredAt` | `context` text |
| `bpReadings` | `systolic`, `diastolic`, `pulse?`, `context?`, `measuredAt` | - |
| `weightLogs` | `valueKg`, `measuredAt` | `weightKg` (old Health Connect imports) |
| `sleepLogs` | `sleepStartAt`, `sleepEndAt`, `durationMinutes`, `measuredAt` (= `sleepEndAt`) | `sleepTime`/`wakeTime` (Dates), `duration` (hours), clock text (`HH:mm`), `hours` |
| `walkLogs` | `minutes`, `seconds?`, `activityType`, `estimatedSteps?`, `mealRelation?`; imports: `steps`, `granularity:"day"`, `dayKey` | per-interval import docs (`steps` + `startTime`/`endTime`), `source:"timer"` |
| `medicationLogs` | `taken`, `name?`, `measuredAt` | - |
| `labReports` | `reportType`, `notes?`, `fileUrl?` | - |

`source` is one of `manual`, `health_connect`, `device`, `admin_correction`, `migration`. Clients create
only `manual` (older builds sent `timer`, tolerated) and `health_connect`. `admin_correction` and
`migration` are written by staff tooling / the backfill only.

Parsers (`HealthParsers`) are the single place that understands the legacy shapes; a record that cannot
be interpreted is skipped, never guessed. Sleep entries that are implausible (over 18 h, or ending in the
future) are flagged `isSuspect` and left out of averages.

## Health Connect import

- Deterministic document ids (`<uid>_<prefix>_<providerId>`; steps: `<uid>_steps_day_<yyyy-MM-dd>`), so a
  re-sync updates the same document.
- `createdAt` is the provider's time and never changes between syncs; `importedAt` is the server time of
  the latest sync. (The old importer re-stamped `createdAt` on every run, which pushed old imports to the
  front of every bounded query.)
- Steps are one total per **local** day (was one document per step interval, thousands per month, and
  UTC midnight). Where an old per-interval document and a daily total exist for the same day, the daily
  total wins in the reader so steps are never counted twice.
- Read-only permissions only (steps, sleep, weight, blood glucose, blood pressure).

## Corrections (60-minute window)

A member can correct an entry they typed for 60 minutes after it was created. "60 minutes" is judged by the
server clock (`request.time`) in Firestore rules: `request.time < createdAt + 60 min`, so **exactly 60
minutes is already expired**. The phone's clock and `measuredAt` play no part.

Rules for a correction (`memberCorrection` in the rules; `Corrections.editableKeys` in the app must
match `correctableKeys`):

- only the listed fields change (glucose: value, unit, readingType, measuredAt; BP: systolic, diastolic,
  pulse, context, measuredAt; weight: valueKg, measuredAt; sleep: sleepStartAt, sleepEndAt,
  durationMinutes, measuredAt; walk: minutes, seconds, activityType, estimatedSteps, mealRelation,
  measuredAt; medication: taken, name, measuredAt);
- `userId`, `profileId`, `source`, `createdAt`, `providerRecordId` are unchangeable;
- the audit trail is mandatory: `lastCorrectedAt == request.time`, `correctionCount` goes up by exactly 1,
  `updatedAt == request.time`;
- values stay sensible (glucose 20-800, HbA1c 3-20, systolic 60-260, diastolic 30-180, weight 20-300 kg,
  sleep 1-1080 min with a coherent start/end, nothing in the future beyond 15 minutes of clock drift);
- an imported entry (`source: health_connect`) cannot be corrected by a member at all, and a manual entry
  cannot be turned into an import (or the reverse);
- experts and coaches are read-only; an admin correction must carry `lastCorrectedBy` (their uid) and
  `lastCorrectedAt` and leaves provenance fields untouched; deleting your own reading stays possible.

Creating an entry needs the server `createdAt`, your own `userId`, source `manual`, sensible values and no
forged audit fields. Range checks apply to whatever value fields are present so older app versions that
send other shapes keep working; **corrections require the canonical fields**, so an older build's "Edit"
after this rule is deployed is refused with a clear message (see rollout order below).

## "Checked in today"

One definition everywhere (`TodayStatus` in `HealthState.kt`): the member saved at least one of blood
sugar (not HbA1c), blood pressure, weight or medication **today in the phone's calendar day** by
entering it themselves. Not counted: HbA1c, anything imported from Health Connect or a device, sleep and
walking (shown as "also logged today"). Rhythm's 7/30-day grid, streak copy, Today's prompts and the
check-in counts in the reports use `HealthStateBuilder.checkInDates`.

## Rollout order (readers, writers, rules, backfill)

1. Ship the app with the tolerant readers **and** canonical writers (this change). Nothing is deleted.
2. Let testers update (debug builds self-update; internal track). Older builds keep logging (create
   rules are lenient) but cannot edit within the window until they update.
3. Deploy the rules from `main`. Verify in the emulator suite first (done in CI) and in staging.
4. Run the backfill in dry-run against staging, inspect the report, then production (see
   `docs/MIGRATION_RUNBOOK.md`; not yet run anywhere).
5. Only after the backfill validates, consider removing legacy readers. They are cheap; there is no
   deadline.

## Data export

`requestDataExport` (one per hour; a failed attempt does not count) builds one ZIP per request: `data.json` (everything,
times as ISO text), six CSVs (blood sugar, blood pressure, weight, sleep, activity, medication; cells that could run as
spreadsheet formulas are defused) and a README. It is stored privately at `users/<uid>/exports/<requestId>.zip`, reachable
through a 15-minute signed link (`getExportDownloadLink`, owner only) and emailed (3-hour link, no health values in the
email) when the account has an address. Phone-only accounts get the in-app notification instead. Files are deleted after 30
days (`purgeOldExports`) and with the account.

## Not done yet / owner decisions

- The backfill exists and is tested (`firebase/scripts/health-backfill.cjs`) but has not been run on any real project.
- A staging Firebase project does not exist; production must not be used for testing.
- Clinical sign-off for the glucose/BP status thresholds (`GlucoseRanges`, the BP "watch" lines).
