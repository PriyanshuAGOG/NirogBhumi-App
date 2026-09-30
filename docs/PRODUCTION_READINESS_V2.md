# Production readiness v2: what was done, with evidence

Branch: `claude/firebase-setup-apk-build-s8b1ol`. Nothing here is deployed: production deploys happen only from `main`, and
no staging project exists yet. Nothing was written to, or tested against, the production Firebase project; every test
ran on local emulators or in GitHub Actions.

## Baselines (recorded before this work)

| What | Value |
|---|---|
| `main` (Sprints 0-2 merged, PR #18) | `0d55ca97de7f760d0952d6e8913fa6bde5c9d651` |
| Branch head when this work started (Sprint 3 release work, unmerged) | `fe5a55daaedffe48210c09603620c9f66c5c43b4`, CI run 196 green |
| Rules tests at baseline | 172 passing |
| Functions tests at baseline | 56 passing |
| CI on the branch | Run 212, commit `0c7ccd4`: all jobs green (Android debug tests + lint, R8 release build + manifest check, rules, functions, console e2e) |
| Android unit/Compose tests | 240 run in CI, all passing on run 212 |
| Rules tests now | 193 passing |
| Functions tests now | 99 passing locally on the Firestore/Auth emulators |
| Emulator e2e now | 26/26 console checks and 16/16 member-journey checks, run locally |
| Android unit + Compose tests at baseline | passing in CI run 196 (no pre-existing failures) |
| Pre-existing failures | none known. Two real defects found and fixed along the way (below) |

## What changed, by requirement

| Requirement | Result | Evidence |
|---|---|---|
| One lifecycle-aware health state; no per-screen copies | Done | `HealthDataStore` -> `HealthUiState` (StateFlow, `collectAsStateWithLifecycle`); every screen reads it; the transient `NirogState` fields are deleted; `docs/HEALTH_DATA_SCHEMA.md`; 160+ JVM tests in `health/domain` and `HealthDataStoreTest` |
| Canonical schemas, legacy readers, `valueKg`, sleep timestamps, `source` enum | Done (readers + writers); backfill built | parsers tolerate every old shape; Health Connect writes canonical payloads; `docs/MIGRATION_RUNBOOK.md`, `firebase/scripts/health-backfill.cjs` (10 tests, dry run by default, guarded). **Backfill not run on any real project** |
| 60-minute correction window, server clock, protected fields, audit, imports read-only | Done | Firestore rules + `health-log-window.test.mjs` (boundaries 59/61 min, tampering, imports, roles, admin audit) + e2e against the real rules |
| Sleep: AM/PM, validation, no silent 24h, <=18h, future, DST, naps | Done | `SleepTimes` + `SleepEditorDialog` (12-hour clock), `SleepTimesTest`, Compose test |
| Accessible metric chart, real axes, no fabricated days | Done | `MetricTrendChart`/`TrendBuilder` (date labels, tap values, list alternative), weight axis padding, per-metric 30-day Trends screen |
| Cross-app consistency (Today, Track, detail, Insights, Trends, reports, Health File, PDFs, coach view) | Done | all read the same state; one "checked in today" definition (`TodayStatus`); one doctor-report summary (`DoctorReport`); coach member view reads canonical weight/sleep/steps; server labels follow the same blood-sugar table |
| Plain-language copy, Upper/Lower BP | Done for every member-facing screen; debug-only expert/admin preview screens not reworded | copy list in commit history; `HealthScreensTest` |
| Diabetes type and goals as enums with migration | Done | `ProfileChoices.kt` + tests; legacy text mapped, unknown text kept as "Other"; skipped answers are no longer reported as "No diabetes" |
| Education: one source, pagination, search, offline, reader | Done | nirogbhumi.com is the single source; `ArticlesScreen`, `ArticleReaderScreen`, `ArticleFeed` tests, offline copy, link checks |
| Store WebView hardened, strict allowlist, Play payments | Done; **disabled** until a store address is configured | `UrlPolicy` (look-alike hosts, credentials, schemes, ports), `SafeWebViewScreen`, `docs/PLAY_PAYMENTS.md` |
| Export: ZIP, short-lived link, email, phone-only path, UI states | Done in code and tests; email needs the owner's mail extension | `exportJob.ts` (+21 tests), e2e unzips and checks CSV/email/link; retention 30 days |
| Support request email | Done in code; needs `SUPPORT_EMAIL` | sanitised, idempotent (tests + e2e) |
| Deletion: typed phrase, recent sign-in, pending banner, honest wording | Done | server enforces `REAUTH_REQUIRED` (tests); banner on every main screen; `DeletionGuards` tests |
| Consent versioning | Done | `CONSENT_VERSION` 2026-09; update banner; optional toggles no longer overwrite the recorded version |
| Legal placeholders, counsel text | **Owner-blocked** | `scripts/check_release_gates.py` still enforces placeholders for non-internal tracks |
| Data Safety reassessment | Done on paper | `docs/DATA_SAFETY_MAPPING.md` |
| Release infra (monotonic versionCode, real test task, diagnostics) | Done earlier (Sprint 3) and kept green | CI release job signs with a throwaway key and verifies the manifest |
| Callable invoker org policy | **Owner-blocked** | every callable is unreachable in production until lifted (`docs/RELEASE_CHECKLIST.md`) |
| Tests at all layers | Partly (see gaps) | JVM, Robolectric/Compose, rules, functions, emulator e2e, analytics guard |
| Security review, dependency and secret scan, PHI-free analytics | Done on paper + tests | `docs/SECURITY_REVIEW.md`, `AnalyticsEventsTest` |
| Real-device matrix, auth acceptance, WebView security QA | **Written, not executed** | `docs/DEVICE_QA_MATRIX.md` |

## Defects found and fixed while doing this

1. **Any admin could rewrite any Firestore document** through a catch-all `allow update: if admin()` meant for two collections (export/deletion requests). Scoped; it would also have bypassed the audited correction rules.
2. **Rhythm crashed on a cold start** because the empty health state used `LocalDate.MIN` as "today". Caught by `RhythmScreenTest` in CI; fixed and pinned by a test.
3. **Three different blood-sugar range tables** (app 80-130 for everything, quick-log 125, server 125/180) made the same reading "Normal" in one place and "High" in another. One table now, mirrored once on the server (still provisional until a clinician signs off).
4. **Server treated imports and HbA1c as check-ins** (and could page a member about a watch reading) and mirrored after-meal values as "fasting sugar". Fixed with tests.
5. **A corrected reading kept its old server label** (a value fixed from 310 to 130 stayed "critical" in the coach view). Update triggers added.
6. **Optional-consent toggles overwrote the recorded consent version**, marking a newer notice as accepted. Fixed.
7. **Account deletion accepted a days-old session.** Now needs a sign-in younger than 5 minutes, enforced on the server.
8. The Health Connect importer re-stamped `createdAt` every sync, wrote one document per step interval (thousands a month) at UTC midnight, and stored `weightKg`/sleep in a different shape from manual entries. Canonical payloads, daily totals on the local day, tolerant readers.

## Honest gaps (nothing below is claimed done)

- **No real-device run.** `docs/DEVICE_QA_MATRIX.md` is the script; it has not been executed on hardware.
- **No staging environment and no production deploy.** Rules, functions and indexes are verified on emulators and CI only.
- **The member-journey e2e has 16 checks, not 32**, plus 26 console checks. The remaining journey steps are covered by unit/rules/functions tests rather than one long script.
- **The backfill has never run** against real data.
- **Android dependency vulnerability scan was not possible** in this environment; enable Dependabot or dependency-check.
- **No independent penetration test.**
- **Clinical thresholds** (blood sugar table, BP "watch" lines) need a clinician's sign-off.
- **Owner actions remain:** lift the org policy (every callable is down in production until then), install the mail extension and set `SUPPORT_EMAIL`, grant the signing role, create staging, finalise the legal text and Grievance Officer, upload key and Play secrets.
- **Copy:** debug-only expert/admin preview screens and the retired generic catalog screens still contain old wording; they are not reachable in release builds.
