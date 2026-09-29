# Operations runbook

Who to call and what to do when something breaks. Keep this short enough to use at 2 a.m.

## Where things live

| Thing | Where |
|---|---|
| App (Android) | Play Console; CI builds in GitHub Actions |
| Backend | Firebase project `nirog-bhumi-app`: Firestore, Storage, Auth, Functions (region `asia-south1`), FCM |
| Admin console | Firebase Hosting (built from `console/`) — admins and coaches |
| Deploys | `Deploy Firebase` workflow, automatically on push to `main` when `firebase/**` changes; manual run available |
| Crashes | Firebase Crashlytics (release builds only) + Play vitals |
| App-side errors | Firestore `errorReports` (visible to admins in the console) |
| Function logs | Cloud Logging, filter by function name; `severity>=ERROR` |
| Push queue | Firestore `notifications` (swept every 15 minutes by `sendPendingNotifications`) |

## Severity and first response

| Sev | Example | Do this first |
|---|---|---|
| 1 | Members can't sign in / data exposed / wrong person's data shown | Halt the Play rollout; if it is a rules/functions change, redeploy the previous good commit (below); tell the owner immediately |
| 2 | A feature broken for everyone (push not sent, announcements failing) | Check Cloud Logging + the last deploy; fix forward or roll back |
| 3 | One user's problem | Support inbox; use the admin console; escalate only if it repeats |

## Roll back the backend

1. `git revert <bad-commit>` on `main` (or `git revert -m 1 <merge>` for a merged PR), push. The deploy workflow runs automatically.
2. If the deploy itself is broken, run **Deploy Firebase** manually from the last known good commit (Actions > Run workflow > pick the tag/branch).
3. Firestore *rules and indexes* deploy in seconds; *functions* take a few minutes. Index builds continue in the background — queries needing a new index fail until it is ready.

## Roll back the app

Play cannot un-publish a version code, but you can:
1. **Halt** the staged rollout (Console > Production > Halt rollout).
2. Ship the fix as a new build (the version code always rises automatically).
3. For a bad *backend* interaction, prefer rolling back the backend, not the app.

## Data subject requests (DPDP)

- In-app delete: schedules erasure in 7 days; the hourly job `processDueDeletions` completes it. Admin > Data Requests shows status and lets an admin approve/reject emailed requests.
- If a deletion is stuck in `processing`: it retries automatically up to 5 times, then shows `failed` with a reason in Data Requests. Check Cloud Logging for `eraseUserData`, fix the cause (usually a permission or a missing index), then use **Retry** in the console.
- Export: the member taps Export; the callable writes the file and pushes "ready". If it fails, the reason is in `dataExportRequests`.
- Statutory timelines and the Grievance Officer process: `docs/DPDP_COMPLIANCE.md`.

## Push notifications not arriving

1. Does the member have a token? The `users/{uid}` document must have `fcmToken`. No token → the queued push is marked `failed` with `failureReason: missing_token`; the member needs to open the app once with notifications allowed.
2. Is the queue draining? `notifications` docs with `status: "pending"` older than ~20 min mean the sweeper is failing: check `sendPendingNotifications` logs.
3. Routine **reminders** (type `reminder`) respect the member's quiet hours (default 21:00–07:00, adjustable in Notification settings) and a daily cap, and are deferred (`deferredReason`) rather than dropped. Human messages (coach chat, announcements, consultation updates) are never deferred.
4. FCM rejects invalid tokens; the sender records `failed` with the error and does not retry that push.

## Cost and abuse

- Budget alerts should already exist on the GCP project (Owner). If one fires: check Firestore reads (a runaway listener in a new app version is the usual cause), Storage egress, function invocations.
- App Check enforcement (after the closed test) blocks non-app traffic; if members suddenly can't load anything, check the Play Integrity quota before assuming an outage.

## Routine checks (weekly, 10 minutes)

- Crashlytics: new crash clusters; Play vitals: ANR/crash rates.
- Console > Data Requests: nothing older than the statutory window.
- Cloud Functions: error count by function; the scheduled jobs ran (`sendPendingNotifications`, `processDueDeletions`, daily pulse).
- Firestore usage trend; Storage size trend.
- `npm audit` / Dependabot: apply non-breaking fixes.
