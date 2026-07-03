# Self-update system

How the app finds, downloads, verifies, and installs new builds over itself
without the Play Store — and without a manual "download the APK, uninstall,
reinstall" cycle for every test build.

## Why this exists

Before this system, testing a new build meant: build APK → download it →
delete the old install → reinstall → sign back in → re-navigate to whatever
screen you were testing. This automates everything up to the final "tap
Install" the OS itself requires (there is no way around that last tap
without being a Play Store install or a device-owner app — see "Ceiling" below).

## Architecture

```
                    ┌─────────────────────────┐
 git push  ───────► │ build-firebase-debug-apk │
                    │ .yml (GitHub Actions)     │
                    └───────────┬───────────────┘
                                │ assembleDebug -PVERSION_CODE=... -PVERSION_NAME=... -PGIT_COMMIT=...
                                │ sha256sum the APK
                                ▼
                    ┌─────────────────────────┐
                    │ publish-release.mjs       │  (firebase/functions/scripts)
                    │ - upload APK to Storage    │
                    │ - write appUpdates/{ch}    │
                    └───────────┬───────────────┘
                                │
                     ┌──────────┴──────────┐
                     ▼                     ▼
          Firebase Storage         Firestore appUpdates/{channel}
       releases/{channel}/{code}.apk   { latestVersionCode, latestVersionName,
                                          minSupportedVersionCode, apkUrl,
                                          checksum, releaseNotes, forceUpdate,
                                          fileSizeBytes, releaseDate }
                     ▲
                     │ read (public, pre-auth)
                     │
          ┌──────────┴───────────────────────────────┐
          │  Android app (com.nirogbhumi.app.update)   │
          │                                             │
          │  UpdateManager.checkNow()                   │
          │    ├─ UpdateRepository.fetchLatest()  ← Firestore read
          │    ├─ VersionChecker (numeric compare)
          │    └─ UpdatePrefs (channel, dismissed version, last check)
          │                                             │
          │  UpdateDialog (Compose) ── "Update Now" ──►  │
          │    ApkDownloader (DownloadManager) ──────►   downloads APK
          │    UpdateInstaller.verifyChecksum() ─────►   SHA-256 check
          │    UpdateInstaller.installApk() ─────────►   system installer
          └─────────────────────────────────────────────┘
```

### Components

| File | Responsibility |
|---|---|
| `update/UpdateModels.kt` | `UpdateInfo` (Firestore doc shape), `UpdateChannelOption`, `DownloadState` sealed interface |
| `update/VersionChecker.kt` | Numeric, segment-by-segment semver comparison — never string comparison |
| `update/UpdatePrefs.kt` | Local state: selected channel, last-check time, per-version dismissal, pending APK path |
| `update/UpdateRepository.kt` | Reads `appUpdates/{channel}` from Firestore — deliberately independent of `HealthRepository` since this must work pre-auth |
| `update/ApkDownloader.kt` | Wraps the system `DownloadManager` (background continuation, retries, and a progress notification, all for free); HTTPS-only |
| `update/UpdateInstaller.kt` | SHA-256 verification, `REQUEST_INSTALL_PACKAGES` permission checks, launches the system package installer via `FileProvider` |
| `update/UpdateManager.kt` | Orchestrates check → dismiss/mandatory logic; owns the WorkManager backstop |
| `update/UpdateCheckWorker.kt` | Background-only backstop (`CoroutineWorker`, every 6h) for when the app isn't open at all |
| `ui/components/UpdateDialog.kt` | The one Material dialog covering prompt → downloading → verifying → ready → failed |
| `MainActivity.kt` (`UpdateLifecycleEffects`) | Drives on-launch / on-foreground / every-30-min-while-open checks and download-progress polling |
| Profile → Developer settings (`DetailsScreens.kt`) | Version/build/commit display, channel picker, manual check, release notes viewer |

## Detection triggers

1. **On launch** — `UpdateLifecycleEffects`' `LaunchedEffect` fires `runCheck()` immediately on first composition.
2. **On foreground** — the same effect is scoped to `repeatOnLifecycle(Lifecycle.State.RESUMED)`, so backgrounding and re-foregrounding the app re-enters the block and checks again.
3. **Every 30 minutes while open** — inside that same `RESUMED` block, a `while (true) { delay(30 min); runCheck() }` loop; it's cancelled automatically the moment the app leaves `RESUMED`, so it never runs while backgrounded.
4. **Manual** — the "Check for updates" row in Developer Settings calls `UpdateManager.checkNow` directly.
5. **Background backstop** — `UpdateCheckWorker`, a `PeriodicWorkRequestBuilder` job enqueued in `NirogBhumiApplication.onCreate`, runs every 6 hours regardless of whether the app is open, and posts a notification if it finds something. WorkManager has a 15-minute floor, which is why the tight 30-minute foreground loop is a separate, UI-layer mechanism rather than a second WorkManager job.

## Version comparison

`VersionChecker.compare(a, b)` splits each version on `.`, drops any
`-beta`/`-rc1` suffix, parses each segment as an `Int` (a missing/garbage
segment reads as `0`), and compares segment-by-segment. `1.0.9` vs `1.0.10`
correctly resolves `1.0.10` as newer even though `"9" > "10"` as strings.

In practice `UpdateManager.checkNow` compares the numeric Android
`versionCode` (`info.latestVersionCode <= currentVersionCode`), since
`versionCode` is the always-numeric, always-monotonic source of truth (CI
stamps it from `git rev-list --count HEAD`); `versionName` is display-only.

## Mandatory vs. optional updates

An update is **mandatory** when either:
- `currentVersionCode < info.minSupportedVersionCode`, or
- `info.forceUpdate == true`

`UpdateDialog` omits the "Later" button entirely when mandatory (per the
spec: mandatory updates only offer "Update Now"). Optional updates that get
dismissed are remembered per-version in `UpdatePrefs` (`dismissedVersionCode`)
— dismissing version 42 doesn't suppress a prompt for version 43.

## Download → verify → install

1. `ApkDownloader.enqueue` hands the (HTTPS-only, rejected otherwise) `apkUrl`
   to the system `DownloadManager`, targeting
   `context.getExternalFilesDir(null)/updates/nirog-bhumi-{versionCode}.apk`.
   `DownloadManager` handles background continuation and its own progress
   notification.
2. `UpdateLifecycleEffects` polls `ApkDownloader.queryProgress` (off the main
   thread) every 500ms and reflects it into `DownloadState.InProgress`.
3. On success, `UpdateInstaller.verifyChecksum` streams the file through
   SHA-256 (off the main thread) and compares against the checksum CI
   computed and stored in Firestore. A mismatch surfaces as
   `DownloadState.Failed` and blocks install — this is what stops a
   corrupted download or a tampered/substituted APK from ever reaching the
   installer.
4. `UpdateInstaller.installApk` gets a `content://` URI via `FileProvider`
   and fires `ACTION_VIEW` with MIME type
   `application/vnd.android.package-archive`, handing off to the system
   package installer.

### Ceiling: why there's still one tap

Installing over Play or being a device-owner app are the only ways to make
an Android install fully silent. Neither applies here, so the OS's own
package-installer confirmation screen is unavoidable — everything before
that tap (check, download, verify) is fully automatic. The user still needs
to grant "install unknown apps" for this app once
(`Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES`,
`UpdateInstaller.requestInstallPermissionIntent`), same as any non-Play APK.

### Data preservation

Installing an APK "over" an existing install (same `applicationId`, same
signing key) is a standard Android package **update**, not an
uninstall/reinstall — the OS preserves the app's data directory, so Firebase
Auth session, Firestore's local cache, SharedPreferences, and any files
under app-private storage all survive automatically. This is exactly the
mechanism the Play Store itself uses; nothing here reimplements or works
around it.

## Firestore/Storage layout

- `appUpdates/{channel}` (`production` | `testing` | `development`) — public
  read, admin-only write (`firebase/firestore.rules`). Public read is
  required because a signed-out or freshly-installed app still needs to
  detect updates.
- `releases/{channel}/{versionCode}.apk` in Storage — public read, admin-only
  write, restricted to `< 200MB` and an APK/octet-stream content type
  (`firebase/storage.rules`).
- CI's Admin SDK write (via Workload Identity Federation, no service-account
  key) bypasses security rules entirely — the public-read/admin-write rule
  shape exists to stop any *other* client from writing fake release metadata
  or a malicious APK, not to gate CI.

## CI publish flow

`build-firebase-debug-apk.yml`, on every push to `main`/`claude/**` that
touches app code:
1. Computes `VERSION_CODE` (`git rev-list --count HEAD`), `VERSION_NAME`
   (`1.0.$VERSION_CODE`), `GIT_COMMIT` (short SHA) and passes them to
   `./gradlew assembleDebug` as `-P` overrides.
2. Computes the SHA-256 of the resulting APK.
3. Authenticates to GCP via `google-github-actions/auth` (WIF — no stored
   service-account key).
4. Runs `firebase/functions/scripts/publish-release.mjs`, which uploads the
   APK to `releases/development/{versionCode}.apk` (with a
   `firebaseStorageDownloadTokens` custom-metadata token so the resulting URL
   works with the client Storage SDK) and writes `appUpdates/development`.

Both the WIF auth step and the publish step are `continue-on-error: true` —
same reasoning as the existing App Distribution step: a missing/expired
credential must not turn a good compile into a red build.

Every push to `main`/`claude/**` publishes to the **development** channel.
Promoting a build to `testing` or `production` is currently a manual step
(run `publish-release.mjs` by hand with `RELEASE_CHANNEL=testing` or
`=production` and the same `VERSION_CODE`/`APK_PATH`/`APK_CHECKSUM`) — see
"Future improvements" below for automating that too.

## Security

- **HTTPS-only downloads** — `ApkDownloader.enqueue` rejects any non-`https`
  scheme before ever calling `DownloadManager`.
- **Checksum verification** — every download is SHA-256-verified against the
  checksum CI computed against the *actual built APK*, before install is
  ever offered. A blank checksum (a doc not yet written by CI) is treated as
  "skip verification" rather than blocking install outright, so an old,
  pre-migration `appUpdates` doc doesn't hard-lock the flow.
- **Downgrade protection** — `UpdateManager.checkNow` only ever surfaces an
  update when `info.latestVersionCode > currentVersionCode`; there's no path
  that offers an older build.
- **Source restriction** — the client only ever fetches from the exact
  `apkUrl` Firestore returned moments earlier; nothing threads a
  user-/intent-supplied URL through the same path.
- **Write restriction** — `firebase/storage.rules` and `firebase/firestore.rules`
  restrict writes to `appUpdates/*` and `releases/*` to `admin()`; every rule
  has an automated test in `firebase/rules-tests/`.

## Testing checklist

- [ ] Fresh install → `appUpdates/development` has a higher `latestVersionCode`
      than the installed build → dialog appears within a few seconds of
      opening the app.
- [ ] Tap **Update Now** → progress bar advances, percentage updates.
- [ ] Background the app mid-download → download continues (verify via the
      system notification) → foreground again → progress reflects correctly.
- [ ] Download completes → "Verifying update integrity…" briefly shown →
      "Ready to install" state.
- [ ] Tap **Install** → system installer opens → confirm → app relaunches on
      the new version; sign-in state, cached data, and any local health
      logs are all still present.
- [ ] Tap **Later** on a non-mandatory update → dialog dismisses → does not
      reappear on next launch for *that* version → does reappear if a newer
      version is later published.
- [ ] Set `forceUpdate: true` (or `minSupportedVersionCode` above the
      installed version) on the Firestore doc → dialog appears with **no**
      "Later" button.
- [ ] Turn off network mid-download → `DownloadState.Failed` with a
      readable message → **Retry** re-enqueues successfully once back online.
- [ ] Corrupt the checksum field in Firestore → download completes but
      verification fails → install is blocked with a clear error, **Retry**
      re-downloads.
- [ ] Revoke "install unknown apps" for the app in system settings → attempt
      install → confirm the OS's own permission prompt appears instead of a
      silent failure.
- [ ] Developer Settings shows the correct version name/code, package name,
      git commit (a real short SHA after a CI-built APK, `"local"` on a
      manual build), selected channel, and "last checked" relative time.
- [ ] Switching the channel in Developer Settings and re-checking pulls from
      the newly-selected `appUpdates/{channel}` doc.
- [ ] `firebase/rules-tests` (`npm test` in `firebase/rules-tests`) — all
      `appUpdates`/`releases` rule tests pass.
- [ ] CI: `build-firebase-debug-apk.yml` run is green, and the resulting
      `appUpdates/development` doc's `checksum` matches
      `sha256sum` of the uploaded APK.

## Future improvements

- **Automate promotion to testing/production** — a `workflow_dispatch`
  job that re-points an already-uploaded `versionCode` at a different
  channel (copy the Storage object + rewrite the Firestore doc) instead of
  requiring a manual script run.
- **Delta/patch updates** — for a large APK, downloading the full package
  every time is wasteful; a binary-diff approach would cut bandwidth for
  frequent small releases.
- **Resumable download UI** — `DownloadManager` already resumes
  transparently at the OS level; surfacing "resumed from 40%" in the UI
  (rather than just re-showing progress) would be a small trust-building
  polish.
- **Release notes as structured data** — currently a single free-text
  field split into bullet lines client-side; a `List<String>` field in
  Firestore would remove that parsing entirely.
- **Signature verification, not just checksum** — SHA-256 catches
  corruption and confirms "this is the exact byte-for-byte APK CI built,"
  but doesn't independently prove *who* signed it the way a full APK
  signature-scheme check would; today that trust is entirely delegated to
  HTTPS + Firestore's admin-write rule.
- **Staged rollout percentages** — `forceUpdate`/`minSupportedVersionCode`
  are all-or-nothing; a `rolloutPercent` field plus a stable per-install
  hash would let a release ramp gradually.
