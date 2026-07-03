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
