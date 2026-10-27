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
- **Access matrix (2026-10-24).** `firebase/functions/test/authz-matrix.test.cjs` lists every callable with its allowed audience and fails when a new callable is added without one. It proves signed-out callers are refused everywhere, an ordinary member (with or without a role claim), a coach, and forged or unknown roles (`expert`, empty, `Admin`, `root`) get no staff or admin power, and the one-time super-admin bootstrap refuses look-alike emails.
  - It found two real weaknesses, both fixed: `bulkOnboard` let any signed-in member run it (each row was still refused, so nothing was enrolled, but a member could write junk entries into the audit log and probe which programs exist), and `adminEnrollUser`, `deleteAnnouncement`, `inviteToProgram`, `revokeInvite`, `sendBulkNotification` decided access only after looking things up. All staff functions now refuse non-staff first.
- **Rules matrix.** `firebase/rules-tests/rules-matrix.test.mjs` is generated from the rules file: every collection is probed signed out and as a stranger (create, read, update, delete, forged ownership), and the outgoing-mail queue, system flags and audit log are checked closed to every client including admins. A mutation check (temporarily opening `mail`) made it fail, so it is not vacuous.
- **Hosting headers** (`firebase.json`): `X-Frame-Options: DENY`, `nosniff`, `Referrer-Policy`, `Permissions-Policy` (camera, microphone, geolocation, payment, usb off), COOP, and an enforced CSP of `frame-ancestors 'none'; base-uri 'self'; object-src 'none'; form-action 'self'`. The full script and connection policy is shipped as **Report-Only** because it cannot be exercised against production from CI; the e2e run serves the same headers and fails on any violation. Switch `Content-Security-Policy-Report-Only` to enforcing after one clean look at the real console's browser console.

## Dependencies

| Area | Result of `npm audit --omit=dev` | Judgement |
|---|---|---|
| firebase/rules-tests, e2e | 0 | clean |
| firebase/functions | After `npm audit fix` (2026-10-24): 9 moderate, 0 high, 0 critical. Removed: `proxy-addr` (critical, IP spoofing), `body-parser`, `@fastify/busboy`, `@grpc/grpc-js` (high). Remaining: `uuid` through `firebase-admin` -> Google Cloud clients | `uuid` needs a caller-supplied buffer to be affected; not reachable from our code. Fix arrives with the next `firebase-admin` major; re-check on each upgrade |
| console | 6 (4 high, 2 moderate): `react-router` and the Firebase web SDK's transitive `@grpc/grpc-js` | `grpc-js` has **0 references in the built browser bundle**, so it is not shipped; the suggested fix is a downgrade to firebase 9 and is rejected. The `react-router` advisories need attacker-controlled redirect targets or server-side rendering; the console has neither (no dynamic navigation from URL input). `react-router-dom` needs a major upgrade, tracked. The console is staff-only behind login |
| Android (Gradle) | Not scanned in this environment (no network access to the Android repositories) | Run `./gradlew dependencyUpdates` / Play's pre-launch report and Dependabot before release |

## Findings from the 2026-10-24 pass (stated plainly)

- **The repository is public.** Everything committed is world-readable. No private keys or tokens were found (pattern scan over all tracked files). Two things to know:
  - The Firebase **web API key** is in the deploy workflow. Web keys are public by design, but the key should be restricted in Google Cloud (HTTP referrers = the console domains, API restrictions = only the Firebase APIs used). Owner action.
  - `app/ci-debug.keystore` (password `android`) signs **debug/tester builds only**; the release uses the private upload key and Play App Signing. Anyone can therefore build an APK that Android treats as an upgrade of a tester's debug install. The in-app updater only installs APKs from our own storage after a SHA-256 check, so the risk is a tester being talked into installing a look-alike by hand. Google Sign-In is deliberately not registered for this key. Decision for the owner: keep (testers only), or move the debug key into a GitHub secret (testers reinstall once).
- **Production permissions gap (found live).** The functions' service account had no data roles, so every function that touched Firestore or Auth returned HTTP 500. Fixed by granting `datastore.user`, `firebaseauth.admin`, `storage.objectAdmin`, `firebasecloudmessaging.admin` and token-creator on itself (`docs/GO_LIVE_GUIDE.md` step 5).
- **Callable functions are public by design**, with identity and role checked inside each function (verified by the access matrix). A flood of rejected calls costs a little compute and is not a data risk; set a billing alert, and enable App Check enforcement after the closed test.
- **Not testable from CI or this environment:** production behaviour, real devices, and anything needing a signed-in production session. `scripts/prod_smoke.mjs` covers what can be seen from outside (headers, legal pages, every callable refusing a signed-out caller with 401, database and storage refusing signed-out reads) and is run by the owner after each deploy.

## Open items

1. Independent penetration test of the release build (not done).
2. Android dependency vulnerability scan (Dependabot or OWASP dependency-check) - enable on the repository.
3. Clinical sign-off for the glucose/BP status thresholds (not a security issue, but a patient-safety one).
4. The organisation policy blocking the public invoker was lifted (project-scoped) and the invoker step is green. First production use found the runtime-permission gap above; re-verify with `scripts/prod_smoke.mjs` and a signed-in walkthrough.
5. Email delivery depends on the owner installing the Trigger Email extension.
