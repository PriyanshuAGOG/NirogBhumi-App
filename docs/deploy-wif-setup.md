# One-time setup: automated Firebase deploys via Workload Identity Federation

`deploy-firebase.yml` deploys Firestore/Storage rules and Cloud Functions
straight from GitHub Actions. It used to authenticate with a service-account
**key** (`FIREBASE_SERVICE_ACCOUNT_JSON`), but this project's org policy
blocks service-account key creation entirely — that's why every backend
change so far has had to be deployed manually from Cloud Shell.

Workload Identity Federation (WIF) sidesteps that: GitHub's own OIDC token is
exchanged for a short-lived GCP access token at run time. No key is ever
created, stored, or rotated — so the org policy that blocks key creation
doesn't apply. This is Google's own recommended approach for GitHub Actions
→ GCP auth.

Run everything below **once**, from Cloud Shell (`gcloud` is already
authenticated there as you).

## 1. Create the Workload Identity Pool + GitHub provider

```sh
PROJECT_ID="nirog-bhumi-app"
PROJECT_NUMBER="126409331898"
REPO="PriyanshuAGOG/NirogBhumi-App"

gcloud services enable iamcredentials.googleapis.com sts.googleapis.com --project="$PROJECT_ID"

gcloud iam workload-identity-pools create "github-pool" \
  --project="$PROJECT_ID" --location="global" \
  --display-name="GitHub Actions"

gcloud iam workload-identity-pools providers create-oidc "github-provider" \
  --project="$PROJECT_ID" --location="global" \
  --workload-identity-pool="github-pool" \
  --display-name="GitHub OIDC" \
  --attribute-mapping="google.subject=assertion.sub,attribute.repository=assertion.repository" \
  --attribute-condition="assertion.repository=='${REPO}'" \
  --issuer-uri="https://token.actions.githubusercontent.com"
```

The `--attribute-condition` is the important security boundary — it means
**only workflow runs from this exact repo** can use this provider, not any
GitHub repo that happens to know the provider's resource name.

## 2. Create a dedicated deploy service account

Don't reuse the default compute service account — a purpose-built one keeps
the blast radius of a compromised GitHub Actions run limited to exactly what
deploys need.

```sh
gcloud iam service-accounts create github-deployer \
  --project="$PROJECT_ID" \
  --display-name="GitHub Actions Firebase deployer"

DEPLOYER="github-deployer@${PROJECT_ID}.iam.gserviceaccount.com"

# Let the WIF pool impersonate this service account - scoped to this repo only.
gcloud iam service-accounts add-iam-policy-binding "$DEPLOYER" \
  --project="$PROJECT_ID" \
  --role="roles/iam.workloadIdentityUser" \
  --member="principalSet://iam.googleapis.com/projects/${PROJECT_NUMBER}/locations/global/workloadIdentityPools/github-pool/attribute.repository/${REPO}"
```

## 3. Grant the deploy service account what `firebase deploy` actually needs

`firebase deploy --only firestore,storage,functions` touches rules, indexes,
Cloud Functions (2nd gen, which is Cloud Run + Artifact Registry + Eventarc
under the hood), and needs to act as the functions runtime service account.

```sh
for ROLE in \
  roles/firebase.admin \
  roles/cloudfunctions.admin \
  roles/run.admin \
  roles/eventarc.admin \
  roles/artifactregistry.writer \
  roles/cloudbuild.builds.builder \
  roles/pubsub.editor \
  roles/iam.serviceAccountUser
do
  gcloud projects add-iam-policy-binding "$PROJECT_ID" \
    --member="serviceAccount:${DEPLOYER}" \
    --role="$ROLE" --condition=None
done
```

If a future deploy run still fails with a permission error, it'll name the
exact missing role in the error (the same way the very first Cloud Functions
deploy this project ever did needed `roles/cloudbuild.builds.builder` added
on the fly) — grant that specific role the same way and re-run.

## Known issue: new (never-before-deployed) callable functions fail to get
their public invoker set

Confirmed in production (deploy runs `28781548024`, `28782811791` on
2026-07-06): every function that already existed deploys and updates fine,
but the **first-ever** deploy of a brand-new `onCall` function fails with:

```
Failed to set the IAM Policy on the Service projects/nirog-bhumi-app/locations/asia-south1/services/<name>
Unable to set the invoker for the IAM policy on the following functions: <name>
```

This happened for `createStaffAccount`, `ensureProgramMembership`, and
`bootstrapSuperAdmin` - three functions this session added. The function's
*code* still deploys successfully (the underlying Cloud Run service gets
created), but without a public-invoker IAM binding, every client call to it
hits a platform-level 403 before Firebase's own `context.auth` check ever
runs - the client SDK typically surfaces this as `unauthenticated` or
`permission-denied`, indistinguishable from a real app-level auth bug. This
is almost certainly why `ensureProgramMembership` (the Care+ chat self-heal
fix) never actually took effect for real accounts even after being deployed.

`roles/run.admin` (granted above) normally includes
`run.services.setIamPolicy`, so this points at either an incomplete role
grant or an org policy (e.g. Domain Restricted Sharing / Public Access
Prevention) that blocks granting `allUsers` as a Cloud Run invoker
regardless of the grantee's role.

**This almost certainly also explains the original, most-reported bug in
this project**: `redeemProgramCode` (the program-enrollment function) was
itself a brand-new function when it was first deployed, and every symptom
matches exactly - users get `unauthenticated` trying to redeem a program
code, which is `requireUser()`'s own error for "no valid auth on this
request," i.e. the request never got a chance to prove who it was, because
it was rejected at the platform layer before reaching our code at all. If
its first deploy hit this same IAM gap and nobody happened to fix it since
(subsequent code updates never re-attempt the IAM step - only a function's
literal first-ever deploy does), it has likely been broken since the day it
was added, independent of anything client-side.

**Owner action required** - from Cloud Shell (using your own elevated
login, the same one the original manual deploys before WIF existed already
used successfully). This is safe to run for every `onCall` function in the
codebase, not just the newest ones - it's a no-op for any service that
already has the binding:

```sh
PROJECT_ID="nirog-bhumi-app"
REGION="asia-south1"

for SERVICE in \
  redeemprogramcode ensureprogrammembership requestdataexport \
  requestaccountdeletion gethealthfilesharelink createauditlog \
  setuserrole createstaffaccount bootstrapsuperadmin sendbulknotification \
  adminenrolluser invitetoprogram revokeinvite bulkonboard \
  createannouncement previewannouncementaudience deleteannouncement; do
  gcloud run services add-iam-policy-binding "$SERVICE" \
    --project="$PROJECT_ID" --region="$REGION" \
    --member="allUsers" --role="roles/run.invoker"
done
```

After running this, have the reporter retry joining a program with a code -
if `redeemProgramCode` was indeed stuck since its first deploy, this should
be the fix for the "unauthenticated" enrollment bug that's been reported
repeatedly this session.

If this command itself fails with an organization policy error (rather than
a permission error), the constraint name in that error is the actual
blocker - it needs an exception added for this project, or an
`allAuthenticatedUsers`/service-account-scoped invoker binding used instead
(which would require a matching change in each function's code to restrict
`invoker` accordingly, since `onCall` defaults to public). Re-run this same
command for any future function the first time it's deployed, until the
underlying WIF service account's grant is confirmed sufficient end to end.

**Confirmed 2026-07-06**: this is exactly what happens here. The command
above fails for every function with:

```
ERROR: (gcloud.run.services.add-iam-policy-binding) FAILED_PRECONDITION: One or more users named in the policy do not belong to a permitted customer, perhaps due to an organization policy.
```

`gcloud org-policies describe iam.allowedPolicyMemberDomains --project=nirog-bhumi-app --effective`
confirms the **Domain Restricted Sharing** constraint is active, scoped to
Cloud Identity customer `C01cisooi`, inherited from above the project -
`allUsers` isn't part of any customer, so it's blocked outright regardless
of the caller's IAM role (this is why `roles/run.admin` from step 3 above
doesn't help - it's an org policy, not a permission gap).

Attempting the standard override also fails, for a second, separate
reason - the account running these commands doesn't hold Organization
Policy Administrator on this project:

```
ERROR: (gcloud.org-policies.set-policy) [<account>] does not have permission to access projects instance [nirog-bhumi-app] ...: Permission 'orgpolicy.policies.create' denied ...
```

**This needs whoever actually administers the Cloud org/Workspace for this
project** (confirmed to be a different person than whoever is running these
deploy commands) to do ONE of the following:

1. Grant `roles/orgpolicy.policyAdmin` on the `nirog-bhumi-app` project to
   the account that needs to make this change, who can then run the
   `set-policy` override from step 3 above themselves, or
2. Make the override directly themselves:
   ```sh
   cat > /tmp/allow-all-domains.yaml <<'EOF'
   name: projects/nirog-bhumi-app/policies/iam.allowedPolicyMemberDomains
   spec:
     rules:
     - allowAll: true
   EOF
   gcloud org-policies set-policy /tmp/allow-all-domains.yaml
   ```
3. Then re-run the `add-iam-policy-binding` loop above for every function in
   the `for SERVICE in ...` list - it should succeed once the domain
   restriction no longer blocks `allUsers` for this project.

Until this happens, every `onCall` function added to this project will
continue to silently fail its first deploy's invoker step, and stay
uninvokable (looking like an app-level auth bug) until someone with this
access runs the fix.

## 4. Add the two GitHub repo secrets

Get the provider's full resource name:

```sh
gcloud iam workload-identity-pools providers describe "github-provider" \
  --project="$PROJECT_ID" --location="global" \
  --workload-identity-pool="github-pool" \
  --format="value(name)"
```

In the GitHub repo → **Settings → Secrets and variables → Actions**, add:

| Secret name | Value |
|---|---|
| `FIREBASE_WIF_PROVIDER` | the full resource name printed above (`projects/.../locations/global/workloadIdentityPools/github-pool/providers/github-provider`) |
| `FIREBASE_DEPLOY_SERVICE_ACCOUNT` | `github-deployer@nirog-bhumi-app.iam.gserviceaccount.com` |

The old `FIREBASE_SERVICE_ACCOUNT_JSON` secret can be deleted once this is
confirmed working — it was never usable anyway.

## 5. Verify

Trigger **Actions → Deploy Firebase → Run workflow** on any branch (defaults
to project `nirog-bhumi-app`). It should authenticate via WIF and run the
same `firebase deploy` the manual Cloud Shell process did. From then on,
backend changes on this branch (or any branch) can be deployed with one
click from the Actions tab instead of a Cloud Shell session.

## 6. Owner action: enable the email channel for announcements

`createAnnouncement`'s email channel writes documents into a `mail`
collection in the shape the Firebase **"Trigger Email"** extension expects
(`{ to: [address], message: { subject, text } }`), but nothing processes
that collection until the extension is actually installed and configured:

1. Firebase Console → your project → **Extensions** → install **"Trigger Email"**.
2. Point it at an SMTP provider (SendGrid, Mailgun, or any SMTP relay) and
   supply those credentials during setup - this is the one secret-bearing
   step only the project owner can do.
3. Leave its configured Firestore collection as `mail` (the default) so it
   matches what `createAnnouncement` already writes to.

Until this is done, checking "Email" as a delivery channel queues real
documents but nothing sends - the in-app and push channels work regardless,
this only gates the third channel.
