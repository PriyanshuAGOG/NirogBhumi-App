#!/usr/bin/env bash
# End-to-end console test: real Chromium against the console build, talking to the
# Firebase Auth + Firestore + Functions emulators. Never touches production.
set -euo pipefail
cd "$(dirname "$0")/.."
# Local sandboxes ship Chromium in /opt/pw-browsers; CI installs Playwright's own.
[ -d /opt/pw-browsers ] && export PLAYWRIGHT_BROWSERS_PATH="${PLAYWRIGHT_BROWSERS_PATH:-/opt/pw-browsers}"

(cd firebase/functions && npm ci --silent && npx tsc)
(cd console && npm ci --silent)
(cd e2e && npm ci --silent 2>/dev/null || npm install --silent)

# Build the console pointed at the emulators (VITE_USE_EMULATORS is build-time only).
(cd console && \
  VITE_USE_EMULATORS=true VITE_FIREBASE_API_KEY=fake-key VITE_FIREBASE_AUTH_DOMAIN=localhost \
  VITE_FIREBASE_PROJECT_ID=demo-nirog-bhumi VITE_FIREBASE_STORAGE_BUCKET=demo.appspot.com \
  VITE_FIREBASE_MESSAGING_SENDER_ID=1 VITE_FIREBASE_APP_ID=1:1:web:e2e \
  npx vite build --outDir ../e2e/dist-emulator --emptyOutDir --logLevel warn)

npx --yes firebase-tools@15.22.3 emulators:exec --only auth,firestore,functions --project demo-nirog-bhumi \
  "node e2e/seed.mjs && node e2e/console.e2e.mjs"
