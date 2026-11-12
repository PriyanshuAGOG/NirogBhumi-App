# Getting the app onto Google Play: the .aab file and the upload

Everything the build needs is in place except the permanent **upload key** (only you can make it). With it, one click builds the
signed `.aab`. Do the steps in order. Official contact address everywhere: priyanshu@nirogbhumi.com.

## A. Make the upload key (once, 5 minutes, Cloud Shell as priyanshu@nirogbhumi.com)

```bash
rm -rf NirogBhumi-App && git clone --depth 1 -b main https://github.com/PriyanshuAGOG/NirogBhumi-App.git && bash NirogBhumi-App/scripts/setup_play_upload_key.sh
```

It makes the key, saves it as four GitHub secrets (it asks you to sign in to GitHub once), and prints the key's SHA-1 and SHA-256.
**Download `nirog-upload.jks` and `nirog-upload-secrets.txt` from Cloud Shell and keep them in your password manager.**
This is a different key from the tester key; do not reuse it.

## B. Tell Firebase about the key (2 minutes)

Firebase Console > Project settings (gear) > General > Your apps > Nirog Bhumi (Android) > **Add fingerprint**: add the SHA-1 and the
SHA-256 printed in step A. Phone and Google sign-in on the Play version need them.

## C. Build the file

GitHub > Actions > **Build signed Android App Bundle** > Run workflow (branch `main`). About 10 minutes. It runs the unit tests, the
release lint, builds and signs the bundle, checks the merged manifest for anything Play would reject, and verifies the signature.
When it is green, open the run and download the artifact **`nirog-bhumi-release-aab`** (a zip); unzip it for the `.aab` file.
Keep `mapping.txt` from the same zip: it decodes crash reports for that exact build.

The version number is stamped automatically from the commit count, so every build from `main` is higher than the last (Play requires that).

## D. Play Console (first upload, internal testing)

1. Play Console > **Create app**: name *Nirog Bhumi*, default language English (India), *App*, *Free*, tick the declarations.
2. **Release > Testing > Internal testing > Create new release**. Keep **Play App Signing** on (the default). Upload the `.aab`.
3. **Testers**: add a tester list (emails), save, and copy the opt-in link. Internal testing needs no review and is available within minutes.
4. Complete the App content forms from the answer sheets in `docs/PLAY_CONSOLE_DECLARATIONS.md` and `docs/DATA_SAFETY_MAPPING.md`.
   Privacy policy: `https://nirog-bhumi-app.web.app/legal/privacy-policy.html`. Account deletion page: `.../legal/account-deletion.html`.
5. Store listing text, icon and feature graphic: `docs/PLAY_STORE_LISTING.md` and `play/graphics/`.

## E. After the first upload (important for sign-in)

Play Console > **Release > Setup > App signing** shows *Google's* app-signing certificate. Add **its SHA-1 and SHA-256 to Firebase too**
(same screen as step B). The version people download from Play is signed with Google's key, so without this, phone and Google sign-in
fail on it. Also enable **Play Integrity API** (Google Cloud Console > APIs & Services > Library) and register the app under
Firebase > App Check > Play Integrity. On Play installs the "I'm not a robot" web page for the SMS code disappears.

## F. Before closed testing or production

- The legal pages still contain owner placeholders (registered address, dates). Internal testing allows them; closed and production uploads
  are blocked by `scripts/check_release_gates.py --strict` until they are filled in. Send me the details and I fill them in.
- Counsel review, clinician sign-off on alert levels, and the real-device QA matrix: see `docs/GO_LIVE_GUIDE.md`.
- The Play version has no in-app updater (Play updates it) and no Developer screen; both exist only in tester builds by design.
