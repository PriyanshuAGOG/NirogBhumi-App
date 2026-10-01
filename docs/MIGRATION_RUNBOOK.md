# Health data migration runbook

Brings older health records up to the canonical shape in `docs/HEALTH_DATA_SCHEMA.md`. The app reads old and new
shapes (tolerant readers), so **nothing breaks if this is never run**; the backfill only makes the data tidier and
makes server-side exports/reports simpler. Status: built and tested against the emulator; **not yet run anywhere**.

## Order (do not reorder)

1. **Readers** shipped in the app (tolerant of every old shape). Done in this branch.
2. **Writers** shipped: canonical payloads for manual entries and Health Connect imports. Done in this branch.
3. **Rules** deployed from `main` (60-minute correction window, import read-only). Older app builds keep *logging*
   but cannot *edit* within the window until updated.
4. **Backfill** (this runbook): dry run on staging -> review -> apply on staging -> verify -> dry run on production ->
   review -> apply.
5. **Validate** with the checks below. Only then consider retiring a legacy reader (there is no deadline).

## What the backfill does

Additive only. `source` and `createdAt` are never changed; original fields stay.

| Collection | Change |
|---|---|
| `weightLogs` | adds `valueKg` from `weightKg` (old Health Connect imports) |
| `sleepLogs` | entries with real start/wake instants (`sleepTime`/`wakeTime`) gain `sleepStartAt`, `sleepEndAt`, `durationMinutes`, `measuredAt`. Hours-only and clock-text entries are **left alone** (cannot be reconstructed; readers handle them). Anything over 18 h or ending before it starts is left alone. |
| `glucoseReadings`, `bpReadings`, `medicationLogs`, `weightLogs`, `sleepLogs` | missing `measuredAt` is set to `createdAt` |
| `walkLogs` | old per-interval Health Connect step documents are summed into one total per **local** day (`<uid>_steps_day_<date>`, time zone from `users/{uid}.timezone`, default Asia/Kolkata). The interval documents are **kept** unless `--delete-legacy-steps` is passed. |

Every changed document also gets `schemaVersion: 2` and `backfilledAt`. A second run changes nothing.

## Commands (repo root, Node 22)

```bash
# 1. Try it on the emulator first (seed some old-shape docs, or use the emulator export)
FIRESTORE_EMULATOR_HOST=127.0.0.1:8080 node firebase/scripts/health-backfill.cjs --project demo-nirog-bhumi

# 2. Staging DRY RUN (nothing is written). Read the JSON report: scanned / changed / fieldsAdded / sampleIds.
gcloud auth application-default login
node firebase/scripts/health-backfill.cjs --project <staging-project-id>

# 3. Canary: apply to a small slice, then check those documents in the console
node firebase/scripts/health-backfill.cjs --project <staging-project-id> --apply --limit 200 --collections weightLogs,sleepLogs

# 4. Apply everything on staging, then repeat 2-4 on production with --allow-production
node firebase/scripts/health-backfill.cjs --project <staging-project-id> --apply

# Optional, only after the above is validated: replace thousands of step intervals by the daily totals
node firebase/scripts/health-backfill.cjs --project <staging-project-id> --apply --collections walkLogs --delete-legacy-steps
```

The script refuses: no `--project` and no emulator; a `demo-` project outside the emulator; any real project without
`--allow-production`. Use a **staging** project for the first real run. (A staging Firebase project does not exist yet -
it is an owner item in `docs/RELEASE_CHECKLIST.md`. Do not use production as the test bed.)

## Validate

- Report of the second run shows `changed: 0` for every collection.
- In the console, open three members with old data: sugar, weight and sleep history match what the app shows.
- Health Connect: sync a test phone twice; the daily total document is updated in place (same id), no new interval documents appear.
- Export a test member: `weight.csv` has `valueKg`, `sleep.csv` has `durationMinutes`.

## Roll back

The backfill only adds fields, so "rolling back" means deleting the added fields on the documents that have
`schemaVersion == 2` (`backfilledAt` marks when). The only destructive step is `--delete-legacy-steps`; take a
Firestore export (`gcloud firestore export`) of `walkLogs` before using it and keep it until validation is done.
