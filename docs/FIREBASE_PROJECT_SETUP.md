# Firebase project setup

Target Firebase project:

- Project ID: `nirog-bhumi-app`
- Project number: `126409331898`
- Android package name: `in.nirogbhumi.app`

## Android app registration

Register the Android app in the existing Firebase project and fetch `google-services.json` with the Firebase CLI:

```bash
npx -y firebase-tools@latest apps:create ANDROID "Nirog Bhumi Android" --package-name "in.nirogbhumi.app" --project nirog-bhumi-app
npx -y firebase-tools@latest apps:sdkconfig ANDROID <ANDROID_APP_ID> --project nirog-bhumi-app > app/google-services.json
```

`app/google-services.json` is intentionally ignored by Git. For GitHub Actions, base64 encode the file and store it as `GOOGLE_SERVICES_JSON_BASE64`.

## Authentication providers

The repository includes an `auth` block in `firebase.json` for deployable provider configuration:

- Email/password Authentication
- Google Sign-in

Deploy supported provider configuration with:

```bash
npx -y firebase-tools@latest deploy --only auth --project nirog-bhumi-app
```

Phone Authentication still needs to be enabled in the Firebase Console because Firebase CLI auth configuration does not enable the Phone provider. Add test phone numbers/codes in Firebase Console under Authentication > Sign-in method > Phone.

Do not commit Firebase access tokens, phone-auth test tokens, service-account JSON, keystores, or `google-services.json`.
