import { initializeApp, type FirebaseApp } from 'firebase/app'
import { connectAuthEmulator, getAuth, type Auth } from 'firebase/auth'
import { connectFirestoreEmulator, getFirestore, type Firestore } from 'firebase/firestore'
import { connectFunctionsEmulator, getFunctions, type Functions } from 'firebase/functions'

/**
 * Firebase config is read from Vite env vars (VITE_FIREBASE_*). Copy
 * `.env.example` to `.env` and fill the placeholders from the Firebase console
 * Web App registration. Never hardcode secrets here.
 */
const firebaseConfig = {
  apiKey: import.meta.env.VITE_FIREBASE_API_KEY,
  authDomain: import.meta.env.VITE_FIREBASE_AUTH_DOMAIN,
  projectId: import.meta.env.VITE_FIREBASE_PROJECT_ID,
  storageBucket: import.meta.env.VITE_FIREBASE_STORAGE_BUCKET,
  messagingSenderId: import.meta.env.VITE_FIREBASE_MESSAGING_SENDER_ID,
  appId: import.meta.env.VITE_FIREBASE_APP_ID,
}

// If these env vars are missing at build time (e.g. a hosting provider's own
// build - Vercel, say - that doesn't have them configured, unlike the
// GitHub Actions workflow that deploys Firebase Hosting), Vite inlines
// `undefined`, and every Firestore/Storage/Functions call fails with a
// bare "permission-denied" - every single page looks like it "has no
// data", with nothing pointing at the actual cause unless someone happens
// to check the browser console. Exported so App.tsx can show an
// unmissable configuration-error screen instead of silently rendering a
// console that can never load anything.
export const firebaseConfigError =
  !firebaseConfig.apiKey || !firebaseConfig.appId || !firebaseConfig.projectId
    ? 'Firebase web config (VITE_FIREBASE_*) is missing from this build. ' +
      'If this deployment isn\'t built by this repo\'s GitHub Actions workflow, ' +
      'add VITE_FIREBASE_API_KEY, VITE_FIREBASE_AUTH_DOMAIN, VITE_FIREBASE_PROJECT_ID, ' +
      'VITE_FIREBASE_STORAGE_BUCKET, VITE_FIREBASE_MESSAGING_SENDER_ID, and VITE_FIREBASE_APP_ID ' +
      'as build-time environment variables (see console/.env.example), then redeploy.'
    : null
if (firebaseConfigError) console.error(`[firebase] ${firebaseConfigError}`)

export const app: FirebaseApp = initializeApp(firebaseConfig)
export const auth: Auth = getAuth(app)
export const db: Firestore = getFirestore(app)
// Callable Cloud Functions live in the same region as the rest of the project
// (asia-south1). Used e.g. for the super-admin `setUserRole` callable.
export const functions: Functions = getFunctions(app, 'asia-south1')

// End-to-end test builds (e2e/run.sh) point the console at the local Firebase
// emulators instead of the real project. The flag is a build-time constant that
// is never set for the production build, so this block is dead code there.
if (import.meta.env.VITE_USE_EMULATORS === 'true') {
  connectAuthEmulator(auth, 'http://127.0.0.1:9099', { disableWarnings: true })
  connectFirestoreEmulator(db, '127.0.0.1', 8080)
  connectFunctionsEmulator(functions, '127.0.0.1', 5001)
}
