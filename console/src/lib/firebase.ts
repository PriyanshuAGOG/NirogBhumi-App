import { initializeApp, type FirebaseApp } from 'firebase/app'
import { getAuth, type Auth } from 'firebase/auth'
import { getFirestore, type Firestore } from 'firebase/firestore'
import { getFunctions, type Functions } from 'firebase/functions'

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

if (!firebaseConfig.apiKey || !firebaseConfig.appId) {
  // Surface a clear signal during local dev if env is not set up yet.
  console.warn(
    '[firebase] VITE_FIREBASE_API_KEY / VITE_FIREBASE_APP_ID are empty. ' +
      'Copy console/.env.example to console/.env and fill in the Web App config.',
  )
}

export const app: FirebaseApp = initializeApp(firebaseConfig)
export const auth: Auth = getAuth(app)
export const db: Firestore = getFirestore(app)
// Callable Cloud Functions live in the same region as the rest of the project
// (asia-south1). Used e.g. for the super-admin `setUserRole` callable.
export const functions: Functions = getFunctions(app, 'asia-south1')
