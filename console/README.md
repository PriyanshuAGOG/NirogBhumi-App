# Nirog Bhumi — Staff Console

Web admin / coach console for Nirog Bhumi. React + TypeScript + Vite on the
Firebase modular SDK (v10). It shares the same Firebase project
(`nirog-bhumi-app`, region `asia-south1`) as the Android member app, so it binds
the same Auth, Firestore collections, and security rules — no extra backend.

Access is staff-only: after email/password sign-in the console reads the user's
ID-token custom claim `role` and admits only `admin`, `coach`, or `super_admin`.

## What's in the console

- Role-gated auth shell (`AuthProvider`, sign-in page, staff-only gate).
- Left-sidebar `AppShell` with nav: Overview, Moderation, Batches,
  Announcements, Calendar, Programs & Codes, Content, Consultations,
  Support, Users & Roles, Settings.
- Every page is realtime (`onSnapshot`) and connected: Dashboard summary
  tiles; Moderation queue (dismiss/remove reports); Batches (roster + Batch
  Pulse + coach→member messaging); Announcements composer; Calendar CRUD
  with optional auto-announce on change; Programs & program-code
  management; Content (Learn) authoring; read-only Consultations; Support
  inbox; Users & Roles (role changes via the `setUserRole` callable
  Function - shows a clear inline message if that Function isn't deployed
  yet); Settings.

## Setup

### 1. Firebase config (`.env`)

1. In the [Firebase console](https://console.firebase.google.com/) open project
   **nirog-bhumi-app** → Project settings → *Your apps* → **Add app → Web**
   (or reuse an existing Web App).
2. Copy the generated config values.
3. Create a local `.env` from the template and paste them in:

   ```sh
   cp .env.example .env
   ```

   Fill the two placeholders (`VITE_FIREBASE_API_KEY`, `VITE_FIREBASE_APP_ID`);
   the other values are pre-filled for this project. `.env` is gitignored —
   never commit real config.

### 2. Install & run

```sh
npm install
npm run dev     # local dev server (http://localhost:5173)
npm run build   # type-check + production build to dist/
npm run preview # serve the production build locally
```

## Deploying to Firebase Hosting

The root `firebase.json` already has the `hosting` block pointed at
`console/dist` with the SPA catch-all rewrite. From the repo root:

```sh
npm --prefix console run build
firebase deploy --only hosting --project nirog-bhumi-app
```

That publishes the console to the project's default Hosting URL
(`https://nirog-bhumi-app.web.app` / `.firebaseapp.com`). A custom domain
(e.g. `admin.nirogbhumi.app`) can be added later in the Firebase console
under Hosting → Add custom domain.

## Deploying to Vercel (recommended for easy access anywhere)

`console/vercel.json` is already configured (build command, output dir,
SPA rewrite so client-side routes don't 404 on refresh). Since the console
lives in a subdirectory of this repo, Vercel needs to be told that.

**Via the Vercel dashboard (one-time setup, auto-redeploys on every push):**
1. [vercel.com/new](https://vercel.com/new) → import the `NirogBhumi-App`
   GitHub repo.
2. Under **Root Directory**, click *Edit* and select `console`.
3. Framework preset should auto-detect as **Vite** — leave build command/
   output directory as default (picked up from `vercel.json`).
4. Under **Environment Variables**, add all six (values from
   `.env.example`, `VITE_FIREBASE_API_KEY`/`VITE_FIREBASE_APP_ID` from your
   Firebase Web App registration):
   ```
   VITE_FIREBASE_API_KEY=<your web apiKey>
   VITE_FIREBASE_AUTH_DOMAIN=nirog-bhumi-app.firebaseapp.com
   VITE_FIREBASE_PROJECT_ID=nirog-bhumi-app
   VITE_FIREBASE_STORAGE_BUCKET=nirog-bhumi-app.firebasestorage.app
   VITE_FIREBASE_MESSAGING_SENDER_ID=126409331898
   VITE_FIREBASE_APP_ID=<your web appId>
   ```
5. Deploy. Every future push to this branch/repo auto-redeploys.

**Via the Vercel CLI (deploy right now, no GitHub connection needed):**
```sh
cd console
npx vercel          # first run: log in, link/create project, confirm root dir
npx vercel --prod   # promote to the production URL
```
The CLI prompts for env vars on first deploy if `.env` isn't picked up
automatically — paste the same six values.

**Important:** since this is a private admin tool, don't rely on the
Vercel URL being secret — access is enforced by the app's own role check
(only `admin`/`coach`/`super_admin` custom claims get past the sign-in
gate), not by hiding the URL.

## Design tokens

`src/styles/tokens.css` mirrors the member app's palette and type (Fraunces +
Manrope, forest / cream / gold, 24px cards, soft two-layer shadows) so both
surfaces read as one product. Keep these values in sync with
`docs/prototype.html` and the Android `DesignSystem.kt`.

## Notes / follow-ups

- The Moderation listener prefers the indexed query
  `where('status','==','open') orderBy('createdAt','desc')` - the required
  composite index is in `firebase/firestore.indexes.json` and deployed. If a
  legacy doc predates the `status` field, the listener falls back to an
  unfiltered `orderBy('createdAt','desc')` and filters client-side.
- Moderation actions currently write directly to the report doc (admin update is
  allowed by existing rules). The BUILD_PLAN's callable-Function moderation path
  (audit logs, message tombstoning) is a later slice.
- `setUserRole` (the Users & Roles page) requires the Cloud Function of the
  same name to be deployed (`firebase deploy --only functions:setUserRole`).
