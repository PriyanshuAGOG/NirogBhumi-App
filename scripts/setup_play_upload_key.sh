#!/usr/bin/env bash
# One-time: makes the PERMANENT upload key for Google Play and stores it as GitHub secrets, so the "Build signed Android App Bundle"
# workflow can produce the .aab you upload to Play Console.
#
# Run in Google Cloud Shell:
#   rm -rf NirogBhumi-App && git clone --depth 1 -b main https://github.com/PriyanshuAGOG/NirogBhumi-App.git && bash NirogBhumi-App/scripts/setup_play_upload_key.sh
#
# This key is DIFFERENT from the tester key. Google Play re-signs what you upload with its own app-signing key; this upload key only proves
# the upload came from you. Losing it is recoverable (Play Console > App integrity > request an upload-key reset), but slow, so BACK IT UP.
# Safe to run again: it never overwrites a key that already exists.
set -euo pipefail

REPO="${REPO:-PriyanshuAGOG/NirogBhumi-App}"
KEYSTORE="${KEYSTORE:-$HOME/nirog-upload.jks}"
SECRETS_FILE="${SECRETS_FILE:-$HOME/nirog-upload-secrets.txt}"
ALIAS="nirog-upload"

need() { command -v "$1" >/dev/null 2>&1 || { echo "Missing tool: $1 (Cloud Shell has it)."; exit 1; }; }
need keytool; need openssl; need gh; need base64

echo "== 1/3 Upload key"
if [ -f "$KEYSTORE" ] && [ -f "$SECRETS_FILE" ]; then
  echo "Reusing the key you already made: $KEYSTORE"
  # shellcheck disable=SC1090
  . "$SECRETS_FILE"
else
  if [ -f "$KEYSTORE" ]; then echo "$KEYSTORE exists but $SECRETS_FILE does not. Move the old key aside and run again."; exit 1; fi
  STORE_PASSWORD="$(openssl rand -hex 16)"
  KEY_PASSWORD="$STORE_PASSWORD"
  keytool -genkeypair -v -keystore "$KEYSTORE" -storetype PKCS12 -alias "$ALIAS" -keyalg RSA -keysize 2048 -validity 10000 \
    -storepass "$STORE_PASSWORD" -keypass "$KEY_PASSWORD" \
    -dname "CN=Nirog Bhumi, O=Nirog Bhumi Private Limited, C=IN" >/dev/null 2>&1
  chmod 600 "$KEYSTORE"
  umask 077
  { echo "STORE_PASSWORD=$STORE_PASSWORD"; echo "KEY_PASSWORD=$KEY_PASSWORD"; } > "$SECRETS_FILE"
  echo "Made $KEYSTORE and saved its passwords in $SECRETS_FILE."
  echo "BACK BOTH FILES UP NOW (Cloud Shell > three dots > Download) and keep them in your password manager."
fi

CERT_TEXT="$(keytool -list -v -keystore "$KEYSTORE" -alias "$ALIAS" -storepass "$STORE_PASSWORD" 2>/dev/null)"
SHA1="$(printf '%s\n' "$CERT_TEXT" | sed -n 's/^[[:space:]]*SHA1:[[:space:]]*//p' | head -1)"
SHA256="$(printf '%s\n' "$CERT_TEXT" | sed -n 's/^[[:space:]]*SHA256:[[:space:]]*//p' | head -1)"
echo "SHA-1:   $SHA1"
echo "SHA-256: $SHA256"

echo; echo "== 2/3 Save as GitHub secrets (environment: production)"
if ! gh auth status >/dev/null 2>&1; then
  echo "Signing in to GitHub (open the link it prints, paste the code, approve)."
  gh auth login -h github.com -p https -w
fi
set_secret() { printf '%s' "$2" | gh secret set "$1" --env production --repo "$REPO" && echo "Saved $1."; }
set_secret ANDROID_KEYSTORE_BASE64 "$(base64 -w 0 "$KEYSTORE")"
set_secret ANDROID_STORE_PASSWORD "$STORE_PASSWORD"
set_secret ANDROID_KEY_ALIAS "$ALIAS"
set_secret ANDROID_KEY_PASSWORD "$KEY_PASSWORD"

echo; echo "== 3/3 Done"
cat <<MSG
Next:
  1. Add BOTH fingerprints above in Firebase Console > Project settings > General > Your apps > Nirog Bhumi (Android) > Add fingerprint.
  2. Run the GitHub workflow "Build signed Android App Bundle" (Actions tab > Run workflow, branch main) and download the
     artifact "nirog-bhumi-release-aab" when it finishes. Unzip it to get the .aab file.
  3. Upload that .aab in Play Console (see docs/PLAY_UPLOAD_STEPS.md).
  4. After the FIRST upload, Play Console > Release > Setup > App signing shows Google's own signing key. Add ITS SHA-1 and SHA-256
     to Firebase too (same screen as step 1). Phone and Google sign-in on the Play version need both.
MSG
