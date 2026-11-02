#!/usr/bin/env bash
# One-time setup so a small group of testers can install the app and sign in with a phone number.
# Run it in Google Cloud Shell, signed in as priyanshu@nirogbhumi.com (the Firebase project owner):
#
#   curl -fsSL https://raw.githubusercontent.com/PriyanshuAGOG/NirogBhumi-App/claude/firebase-setup-apk-build-s8b1ol/scripts/setup_tester_release.sh -o setup_tester_release.sh && bash setup_tester_release.sh
#
# (Download first, then run: it asks you to sign in to GitHub partway through, which does not work when piped.)
#
# What it does (safe to run again; it never overwrites a key that already exists):
#   1. Makes a PRIVATE tester signing key (kept in your Cloud Shell home, never in the repo).
#   2. Registers that key's SHA-1 and SHA-256 with the Firebase Android app. Phone sign-in checks this: a build whose key is
#      not registered is refused with "reCAPTCHA checks were unsuccessful".
#   3. Creates the App Distribution group "nirog-testers" (the group your invite link belongs to).
#   4. Saves the key and its passwords as secrets on the GitHub "production" environment, so every build is signed with it.
#
# It does not touch production data, change any rules, or send anything to anyone.
set -euo pipefail

PROJECT_ID="${PROJECT_ID:-nirog-bhumi-app}"
PROJECT_NUMBER="${PROJECT_NUMBER:-126409331898}"
APP_ID="${APP_ID:-1:126409331898:android:b4e478566dcbeeb7009e83}"
REPO="${REPO:-PriyanshuAGOG/NirogBhumi-App}"
GROUP_ID="${GROUP_ID:-nirog-testers}"
KEYSTORE="${KEYSTORE:-$HOME/nirog-tester.keystore}"
SECRETS_FILE="${SECRETS_FILE:-$HOME/nirog-tester-secrets.txt}"
ALIAS="nirog-tester"

say() { printf '\n== %s\n' "$*"; }
need() { command -v "$1" >/dev/null 2>&1 || { echo "Missing tool: $1 (Cloud Shell has it; this script is meant to run there)."; exit 1; }; }
need keytool; need gcloud; need curl; need python3; need openssl

# ---------------------------------------------------------------- 1. the key
say "1/4 Tester signing key"
if [ -f "$KEYSTORE" ] && [ -f "$SECRETS_FILE" ]; then
  echo "Reusing the key you already made: $KEYSTORE"
  # shellcheck disable=SC1090
  . "$SECRETS_FILE"
else
  if [ -f "$KEYSTORE" ]; then
    echo "$KEYSTORE exists but $SECRETS_FILE (its passwords) does not. Move the old key aside and run again."
    exit 1
  fi
  STORE_PASSWORD="$(openssl rand -hex 16)"
  KEY_PASSWORD="$STORE_PASSWORD"   # PKCS12 uses one password for both
  keytool -genkeypair -v -keystore "$KEYSTORE" -storetype PKCS12 -alias "$ALIAS" -keyalg RSA -keysize 2048 -validity 10000 \
    -storepass "$STORE_PASSWORD" -keypass "$KEY_PASSWORD" \
    -dname "CN=Nirog Bhumi Tester, O=Nirog Bhumi Private Limited, C=IN" >/dev/null 2>&1
  chmod 600 "$KEYSTORE"
  umask 077
  {
    echo "STORE_PASSWORD=$STORE_PASSWORD"
    echo "KEY_PASSWORD=$KEY_PASSWORD"
  } > "$SECRETS_FILE"
  echo "Made $KEYSTORE and saved its passwords in $SECRETS_FILE."
  echo "BACK BOTH FILES UP (Cloud Shell > three dots > Download). If they are lost, testers must uninstall and reinstall the app."
fi

CERT_TEXT="$(keytool -list -v -keystore "$KEYSTORE" -alias "$ALIAS" -storepass "$STORE_PASSWORD" 2>/dev/null)"
SHA1="$(printf '%s\n' "$CERT_TEXT" | sed -n 's/^[[:space:]]*SHA1:[[:space:]]*//p' | head -1 | tr -d ':')"
SHA256="$(printf '%s\n' "$CERT_TEXT" | sed -n 's/^[[:space:]]*SHA256:[[:space:]]*//p' | head -1 | tr -d ':')"
[ -n "$SHA1" ] && [ -n "$SHA256" ] || { echo "Could not read the key's fingerprints."; exit 1; }
echo "SHA-1:   $SHA1"
echo "SHA-256: $SHA256"

TOKEN="$(gcloud auth print-access-token)"
MANUAL=()   # things the script could not do itself; printed at the end with exact steps

call() { # method url json [quota-project-header]
  local extra=()
  [ -n "${4:-}" ] && extra=(-H "x-goog-user-project: $4")
  curl -sS -o /tmp/api.out -w '%{http_code}' -X "$1" "$2" -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' "${extra[@]}" -d "$3"
}
# First as the project, then (if your account may not bill calls to the project) as the shared Google Cloud SDK client.
api() { # method url json
  local code
  code="$(call "$1" "$2" "$3" "$PROJECT_ID")"
  if [ "$code" = "403" ]; then code="$(call "$1" "$2" "$3")"; fi
  printf '%s' "$code"
}

# ------------------------------------------------- 2. fingerprints in Firebase
say "2/4 Register the key with Firebase (needed for phone sign-in)"
register() { # type hash
  local code
  code="$(api POST "https://firebase.googleapis.com/v1beta1/projects/$PROJECT_ID/androidApps/$APP_ID/sha" "{\"shaHash\":\"$2\",\"certType\":\"$1\"}")"
  case "$code" in
    200) echo "Registered $1." ;;
    409) echo "$1 was already registered." ;;
    *)   echo "Could not register $1 automatically (HTTP $code); the manual step is listed at the end."
         MANUAL+=("Firebase Console > Project settings (gear) > General > Your apps > Nirog Bhumi (Android) > Add fingerprint > paste the $1 value: $2") ;;
  esac
}
register SHA_1 "$SHA1"
register SHA_256 "$SHA256"

# ------------------------------------------------------- 3. the tester group
say "3/4 App Distribution group \"$GROUP_ID\""
code="$(api POST "https://firebaseappdistribution.googleapis.com/v1/projects/$PROJECT_NUMBER/groups?groupId=$GROUP_ID" '{"displayName":"Nirog Bhumi testers"}')"
case "$code" in
  200) echo "Group created." ;;
  409) echo "Group already exists." ;;
  *)   echo "Could not create the group automatically (HTTP $code); the manual step is listed at the end."
       MANUAL+=("Firebase Console > App Distribution > Testers & Groups > Add group > name it anything, but set the group ID to: $GROUP_ID") ;;
esac

# ------------------------------------------------------- 4. GitHub secrets
say "4/4 Save the key as GitHub secrets (environment: production)"
need gh
if ! gh auth status >/dev/null 2>&1; then
  echo "Signing in to GitHub (a code and link will appear; open the link, paste the code, approve)."
  gh auth login -h github.com -p https -w
fi
KEYSTORE_B64="$(base64 -w 0 "$KEYSTORE")"
set_secret() { printf '%s' "$2" | gh secret set "$1" --env production --repo "$REPO" && echo "Saved $1."; }
set_secret TESTER_KEYSTORE_BASE64 "$KEYSTORE_B64"
set_secret TESTER_STORE_PASSWORD "$STORE_PASSWORD"
set_secret TESTER_KEY_ALIAS "$ALIAS"
set_secret TESTER_KEY_PASSWORD "$KEY_PASSWORD"

say "Done"
if [ "${#MANUAL[@]}" -gt 0 ]; then
  echo
  echo "TWO-MINUTE MANUAL STEPS (your account is not allowed to make these changes from here):"
  for m in "${MANUAL[@]}"; do echo "  * $m"; done
fi
cat <<EOF
Next:
  - Start a build so it is signed with the new key:
      gh workflow run "Build Firebase-connected debug APK" --repo $REPO --ref claude/firebase-setup-apk-build-s8b1ol
  - Then follow docs/TESTER_GUIDE.md to turn on the invite link and add your testers.
  - Anyone who installed an earlier test build must uninstall it once (the signing key changed).
EOF
