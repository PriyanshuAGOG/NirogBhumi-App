# Owner setup guide: what only you can do, in order

Everything here needs a login that the code and CI do not have (Google Cloud, Firebase, Play Console, your
domain, GitHub settings). Do the steps in order; each ends with a "check" so you know it worked.

The one official contact address is **priyanshu@nirogbhumi.com**. It is the support address, the privacy and
account-deletion address and the Grievance Officer's address. Use it every time a form below asks for an email.

Project: `nirog-bhumi-app` (number `126409331898`), region `asia-south1`, Android app `in.nirogbhumi.app`.

---

## Step 1. Make the mailbox real (15 minutes, do this first)

Several later steps send mail to or from this address, and the privacy policy promises it works.

1. In your domain's mail host (Google Workspace, Zoho, etc.) make sure `priyanshu@nirogbhumi.com` exists and you can
   sign in to it.
2. Send it a test message from a personal account and reply to it.
3. Deliverability, so exports and support alerts don't land in spam. At your DNS host, add the records your mail
   host tells you to add for **SPF**, **DKIM** and **DMARC** (Google Workspace: Admin console > Apps > Google
   Workspace > Gmail > Authenticate email).

Check: a message to the address arrives, and the host's "check DNS" page shows SPF, DKIM and DMARC as valid.

## Step 2. Unblock the callable functions (the launch blocker)

Until this is done, every callable function (joining a batch, access codes, announcements, data export, account
deletion, consultation cancel) fails in production.

You need the **Organization Policy Administrator** role on the Google Cloud organization.

Option A, console: Google Cloud Console > IAM & Admin > Organization policies > *Domain restricted sharing*
(`iam.allowedPolicyMemberDomains`) > pick project `nirog-bhumi-app` > Manage policy > *Override parent's policy* >
Rules: *Allow all* > Set policy.

Option B, Cloud Shell:

```bash
cat > /tmp/allow-all.yaml <<'EOF'
name: projects/nirog-bhumi-app/policies/iam.allowedPolicyMemberDomains
spec:
  rules:
    - allowAll: true
EOF
gcloud org-policies set-policy /tmp/allow-all.yaml
```

Check: after Step 5 (deploy), the "Ensure public invoker" step in the Deploy Firebase run is green.

## Step 3. Decide the merge (needs your yes)

Deploys only run from `main`, and today all of this work is on the branch
`claude/firebase-setup-apk-build-s8b1ol`. Tell me "open the PR" and I will open one pull request (Sprint 3 + readiness
work, with evidence and rollback notes). You review and merge it. Nothing deploys until it is on `main`.

## Step 4. Install the email sender (Firebase extension)

1. Firebase Console > Extensions > *Trigger Email from Firestore* > Install (or from a terminal:
   `npx firebase-tools@15.22.3 ext:install firebase/firestore-send-email --project nirog-bhumi-app`).
2. Settings to enter:
   - Location: `asia-south1`.
   - Email documents collection: `mail`.
   - Default FROM address: `Nirog Bhumi <priyanshu@nirogbhumi.com>` (keep it the official address, not a no-reply one).
   - Default REPLY-TO: `priyanshu@nirogbhumi.com`.
   - SMTP connection URI: from your mail host. For Google Workspace with an app password (needs 2-step verification
     on, and your admin must allow app passwords):
     `smtps://priyanshu%40nirogbhumi.com:APP_PASSWORD@smtp.gmail.com:465`. Or use SendGrid/Brevo and paste their SMTP URI.
     Store the password as a secret when the installer asks; never paste it into the repo or in chat.
3. Send a test: Firestore > `mail` > Add document with fields `to: "priyanshu@nirogbhumi.com"` and a
   `message` map (`subject`, `text`). The extension writes a `delivery` field when it has sent it.

Check: the test mail arrives, and `delivery.state` reads `SUCCESS`. Delete the test document afterwards.

Support alerts need nothing more: the functions send them to the official address by default.

## Step 5. Deploy (after the merge)

1. GitHub > Actions > *Deploy Firebase* > Run workflow on `main`. (One-time deploy authentication is described in
   `docs/deploy-wif-setup.md`; if that was never done, do it first, it is a few `gcloud` commands.)
2. Run the same for the console (Firebase Hosting) if it is a separate workflow in your Actions list. The hosted legal
   pages are served from there at `/legal/privacy-policy.html`, `/legal/terms.html`, `/legal/grievance.html`,
   `/legal/account-deletion.html`.

Check: the run is green, and from the console you can post an announcement.

## Step 6. Allow signed download links (exports and Health File)

Cloud Shell, once:

```bash
PROJECT_ID="nirog-bhumi-app"
RUNTIME_SA="${PROJECT_ID}@appspot.gserviceaccount.com"
gcloud iam service-accounts add-iam-policy-binding "$RUNTIME_SA" \
  --project="$PROJECT_ID" \
  --member="serviceAccount:${RUNTIME_SA}" \
  --role="roles/iam.serviceAccountTokenCreator"
```

Check: in a test account, request a data export. The in-app Download button works, and the email says the link works
for a few hours.

## Step 7. Put the official email into the places outside the code

These live in Google/Firebase/Play consoles, so I could not change them. Use `priyanshu@nirogbhumi.com` in all of them.

- Firebase Console > Authentication > Sign-in method > Google > *Public-facing name* and *Support email*. (Google only
  accepts an address that is your own Google account or a Google Group you manage; if it refuses, add the address as an
  alias of your Google account or create a Group with it.)
- Google Cloud Console > APIs & Services > *OAuth consent screen* (called Google Auth Platform > Branding): *User
  support email*, *Developer contact email*, *Authorized domain* `nirogbhumi.com`, privacy-policy link
  `https://<your-domain>/legal/privacy-policy.html`.
- Play Console > your app > Grow > Store presence > Store settings > *Store listing contact details*: email, website
  (and phone if you want one). Also Play Console > Settings > Developer account > contact details.
- Cloud Billing > Budgets & alerts: add the address as a recipient.
- Firebase Console > Project settings > General: *Public settings* support email. Crashlytics: alert recipients.

Check: open each page again and confirm no other address is listed.

## Step 8. Finish the legal pages

Done by me: the email everywhere, and the Grievance Officer named as **Priyanshu**, reachable at the official address.

Still needed from you (I will fill these in as soon as you send them; nothing here can be guessed):

1. Your **full legal name** for the Grievance Officer (if you want more than "Priyanshu" shown).
2. The **operating legal entity** name (company or proprietor) and **registered office / postal address**, used on the
   privacy policy, account-deletion page and grievance page. The DPDP rules expect a postal address for the officer.
3. The **"last updated" dates**. Use the day you publish.
4. Indian privacy/health-law counsel should read the final text (it is a draft, as the page footers say).

The release gate (`scripts/check_release_gates.py --strict`, used by closed and production uploads) refuses to upload
while any `[placeholder]` is left, so you cannot forget.

## Step 9. Staging project

1. Firebase Console > Add project > `nirog-bhumi-staging`, same region, enable Auth (phone, email), Firestore, Storage.
2. `cp .firebaserc.example .firebaserc` and add the staging id; `firebase use staging`.
3. Deploy rules, indexes and functions there. Rehearse the health-data backfill in dry-run mode first
   (`docs/MIGRATION_RUNBOOK.md`), then production.

## Step 10. App signing and Play

Follow `docs/CLOSED_TEST_AND_ROLLOUT.md` section 0. In short:

1. Create the upload key once (keep two backups):
   `keytool -genkeypair -v -keystore upload.jks -alias upload -keyalg RSA -keysize 2048 -validity 10000`
2. GitHub > Settings > Environments > `production` > add secrets: `GOOGLE_SERVICES_JSON_BASE64`,
   `ANDROID_KEYSTORE_BASE64`, `ANDROID_STORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD`,
   `GOOGLE_PLAY_SERVICE_ACCOUNT_JSON`.
3. Play Console > Setup > API access: create the service account with release permission for this app only.
4. Firebase Console > Project settings > your Android app: add the upload-key and the Play-App-Signing SHA-1 and SHA-256
   (needed for phone sign-in, Google sign-in and App Check).
5. Firebase Console > App Check: register the app with Play Integrity; watch first, enforce later.
6. Fill the Play forms from the answer sheets: `docs/PLAY_CONSOLE_DECLARATIONS.md` and `docs/DATA_SAFETY_MAPPING.md`.
   The account-deletion URL is a hard Play requirement.
7. Run the upload workflow to the **internal** track first, then the closed test (a personal developer account needs 12
   testers opted in for 14 days before production).

## Step 11. Things only a person can sign off

- A qualified clinician confirms or changes the blood-sugar and blood-pressure alert levels
  (`docs/RELEASE_CHECKLIST.md`, "Device acceptance"). If they change them, tell me and I change app and server together.
- Real-device testing on at least two phones using `docs/DEVICE_QA_MATRIX.md`. Nothing has been tested on a physical phone.
- Privacy review by counsel; processor agreements with Google (Firebase) and your mail provider.
- Optional: `-PSTORE_URL=https://nirogbhumi.com/shop/` only when the shop is ready (`docs/PLAY_PAYMENTS.md`).

---

### Quick order of work

1 mailbox, 2 org policy, 3 say "open the PR" and merge, 4 email extension, 5 deploy, 6 signing grant,
7 outside-the-code emails, 8 send me the legal details, 9 staging, 10 Play, 11 sign-offs.
