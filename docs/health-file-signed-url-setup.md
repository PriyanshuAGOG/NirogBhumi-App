# One-time setup: real expiring links for the Health File share feature

`getHealthFileShareLink` (in `firebase/functions/src/index.ts`) generates a
true, time-limited (7-day) V4 signed URL for a member's Health File PDF,
replacing the permanent Storage download-token URL the app used before.

Generating a signed URL from Cloud Functions requires the function's own
runtime service account to be able to sign a blob via the IAM Credentials
API - which needs `roles/iam.serviceAccountTokenCreator` bound to itself.
Without this one-time grant, `getHealthFileShareLink` throws a clear
`failed-precondition` error and the Android app **automatically falls back**
to the previous non-expiring Storage link, so the feature keeps working
either way - this setup only upgrades it to a real expiring link.

Run once, from Cloud Shell:

```sh
PROJECT_ID="nirog-bhumi-app"
# Default Cloud Functions v2 runtime service account for this project.
RUNTIME_SA="${PROJECT_ID}@appspot.gserviceaccount.com"

gcloud iam service-accounts add-iam-policy-binding "$RUNTIME_SA" \
  --project="$PROJECT_ID" \
  --member="serviceAccount:${RUNTIME_SA}" \
  --role="roles/iam.serviceAccountTokenCreator"
```

No redeploy is required for this grant to take effect on the next call -
IAM changes apply immediately to the existing deployed function.

## Verifying it worked

Generate a Health File link in the app. If the dialog says "This link stops
working in 7 days," the signed URL is live. If it still says "It doesn't
expire on its own yet," the grant above hasn't propagated yet (or hasn't
been run) - the feature still works, just with the older non-expiring link.
