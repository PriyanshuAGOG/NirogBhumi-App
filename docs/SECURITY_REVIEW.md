# Security review (Android app, Firebase backend, console)

A MASVS-style walk through what the app and backend do today, with the evidence for each line and what is still open.
"Verified" means a test or a CI check enforces it; "by inspection" means I read the code and it is not enforced by a test.
Reviewed on the branch `claude/firebase-setup-apk-build-s8b1ol`; **not a third-party penetration test** (that is still
worth commissioning before a public launch).

## Storage and privacy (MASVS-STORAGE)

| Control | Status | Evidence |
|---|---|---|
| No cloud/device backup of app data | Verified | `allowBackup="false"`, backup and extraction rules, merged-manifest check `scripts/verify_release_manifest.py` (CI release job) |
| No secrets in the APK or repository | By inspection + scan | No private keys or provider secrets found by pattern scan; the Firebase web API key in `deploy-firebase.yml` is a public identifier (it is in every Firebase app). Keystore, Play and deploy credentials live in GitHub secrets |
| Health records only in Firestore under the member's uid | Verified | Rules tests (193), app-writes contract tests |
| Exports are private, short-lived | Verified | Owner-only 15-minute links, emailed link 3 h, files deleted after 30 days, functions tests |
| Analytics contains no health values or personal text | Verified | `AnalyticsEventsTest` fails when a new event or parameter appears until it is reviewed; only event names, collection names, counts/flags and hour of day are sent |
| Logs contain no health values | By inspection | The only `Log.e` calls report a screen name and the error message |

## Network (MASVS-NETWORK)

- Cleartext traffic disabled (`usesCleartextTraffic="false"`), verified by the manifest check.
- Firebase SDK endpoints only; App Check is installed (debug provider in debug builds only, Play Integrity in release).
- Certificate pinning is **not** used (Firebase/Google endpoints rotate certificates; pinning would risk outages). Decision recorded; revisit only if the threat model changes.
- The in-app web page (store) loads only our own host over https: parsed-host allowlist, `UrlPolicy` (tests cover look-alike hosts, embedded credentials, other schemes, ports); everything else opens in the browser or is refused; TLS errors are never bypassed.

## Platform and WebView (MASVS-PLATFORM)

- Exported components are an explicit allowlist (launcher activity, Firebase auth/Health Connect services), enforced in CI.
- Permissions are a denylist/allowlist check in CI (no advertising ID, no install-packages, no location).
- WebView hardening in one place (`hardenWebView`): no file/content access, no downloads, no pop-up windows, no mixed content, no JavaScript bridge, no camera/microphone/location, third-party cookies off, cache/history cleared on exit. It is disabled unless a store address is configured.
- Deep links / push routes map to fixed screen names, not arbitrary URLs (by inspection).
- Self-update installer is debug-builds-only (`BuildConfig.DEBUG` guard, unit tests for the installer's security checks).

## Authentication and session (MASVS-AUTH)

- Phone OTP, email+password and Google sign-in via Firebase Auth.
- Irreversible actions: account deletion needs a sign-in younger than 5 minutes, enforced **by the server** (`REAUTH_REQUIRED`), plus a typed phrase in the app; cancelling needs neither. Verified by functions tests.
- Roles are custom claims set by Cloud Functions; rules never trust a client-set role (rules tests per role: member, coach, expert, admin, anonymous).

## Backend authorisation (rules and functions)

- Health logs: 60-minute correction window on the server clock, protected provenance fields, audit trail, import read-only, experts/coaches read-only (rules tests, 40+ cases in `health-log-window.test.mjs`).
- A leftover rule that let any admin update **any** document was found and scoped to the two request collections it was written for.
- Support requests are length-capped and cannot forge their email status; the outbound `mail` queue has no client access.
- All callables require sign-in; callables that act on another member require the right role; export links are owner-only.
- Email content is treated as untrusted: header-injection characters removed, HTML escaped, recipients validated (functions tests).
- Spreadsheet formula injection is defused in CSV exports (functions tests).

## Dependencies

| Area | Result of `npm audit --omit=dev` | Judgement |
|---|---|---|
| firebase/rules-tests, e2e | 0 | clean |
| firebase/functions | 9 moderate/high advisories, all transitive through `firebase-admin` -> Google Cloud clients (`uuid`, `@grpc/grpc-js`) | The `grpc-js` advisories concern gRPC **servers**; `uuid` needs a caller-supplied buffer. Not reachable from our code. Fix arrives with the next `firebase-admin` major; re-check on each upgrade |
| console | `react-router` (open-redirect advisory, moderate) and the Firebase web SDK's transitive `grpc-js` | The suggested fix for the SDK is a **downgrade** to firebase 9 and is not acceptable; `react-router-dom` needs a major upgrade, tracked. The console is staff-only behind login |
| Android (Gradle) | Not scanned in this environment (no network access to the Android repositories) | Run `./gradlew dependencyUpdates` / Play's pre-launch report and Dependabot before release |

## Open items

1. Independent penetration test of the release build (not done).
2. Android dependency vulnerability scan (Dependabot or OWASP dependency-check) - enable on the repository.
3. Clinical sign-off for the glucose/BP status thresholds (not a security issue, but a patient-safety one).
4. The organisation policy that blocks the public invoker means **no callable works in production today** (owner action in `docs/RELEASE_CHECKLIST.md`). Until fixed, export, deletion and every function-backed feature are down in production; the rules and app can be reviewed but not exercised end to end there.
5. Email delivery depends on the owner installing the Trigger Email extension.
