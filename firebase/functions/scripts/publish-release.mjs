// Publishes a freshly-built APK to Firebase Storage and writes the
// appUpdates/{channel} Firestore doc the Android app's self-update system
// reads (see app/src/main/java/com/example/update/UpdateRepository.kt).
//
// Run from CI after WIF auth (google-github-actions/auth) has populated
// Application Default Credentials - never from a developer machine with a
// personal service-account key, since this writes to a public-read,
// admin-write collection (firebase/firestore.rules: appUpdates/{channel}).
//
// Required env vars: FIREBASE_PROJECT_ID, FIREBASE_STORAGE_BUCKET,
// RELEASE_CHANNEL, APK_PATH, VERSION_CODE, VERSION_NAME, APK_CHECKSUM.
// Optional: MIN_SUPPORTED_VERSION_CODE (defaults to VERSION_CODE, i.e. not
// mandatory), FORCE_UPDATE ("true"/"false"), RELEASE_NOTES.
import { randomUUID } from "node:crypto";
import { statSync } from "node:fs";
import admin from "firebase-admin";

function requireEnv(name) {
  const value = process.env[name];
  if (!value) throw new Error(`Missing required env var: ${name}`);
  return value;
}

async function main() {
  const projectId = requireEnv("FIREBASE_PROJECT_ID");
  const bucketName = requireEnv("FIREBASE_STORAGE_BUCKET");
  const channel = requireEnv("RELEASE_CHANNEL");
  const apkPath = requireEnv("APK_PATH");
  const versionCode = Number.parseInt(requireEnv("VERSION_CODE"), 10);
  const versionName = requireEnv("VERSION_NAME");
  const checksum = requireEnv("APK_CHECKSUM").toLowerCase();

  if (!Number.isFinite(versionCode) || versionCode <= 0) {
    throw new Error(`VERSION_CODE must be a positive integer, got: ${process.env.VERSION_CODE}`);
  }
  if (!/^[0-9a-f]{64}$/.test(checksum)) {
    throw new Error("APK_CHECKSUM must be a 64-character lowercase hex SHA-256 digest");
  }
  if (!["production", "testing", "development"].includes(channel)) {
    throw new Error(`RELEASE_CHANNEL must be production|testing|development, got: ${channel}`);
  }

  // Defaults to 1 (i.e. "no minimum enforced") rather than guessing at a
  // cutoff - forcing an update is a deliberate, explicit decision made by
  // setting MIN_SUPPORTED_VERSION_CODE (or FORCE_UPDATE) on the workflow
  // run, not something this script should infer on its own.
  const minSupportedVersionCode = Number.parseInt(process.env.MIN_SUPPORTED_VERSION_CODE ?? "1", 10);
  const forceUpdate = process.env.FORCE_UPDATE === "true";
  const releaseNotes = process.env.RELEASE_NOTES ?? "";

  const fileSizeBytes = statSync(apkPath).size;

  admin.initializeApp({ projectId, storageBucket: bucketName });
  const bucket = admin.storage().bucket();
  const destPath = `releases/${channel}/${versionCode}.apk`;
  const downloadToken = randomUUID();

  console.log(`Uploading ${apkPath} (${fileSizeBytes} bytes) -> gs://${bucketName}/${destPath}`);
  await bucket.upload(apkPath, {
    destination: destPath,
    metadata: {
      contentType: "application/vnd.android.package-archive",
      // The client Firebase Storage SDK's public getDownloadURL()-style URL
      // format needs this token in custom metadata - it's how the console
      // and client SDKs generate a working "?alt=media&token=..." URL
      // without relying on legacy bucket-level object ACLs, which fail
      // outright on buckets with uniform bucket-level access enabled.
      metadata: { firebaseStorageDownloadTokens: downloadToken },
    },
  });

  const encodedPath = encodeURIComponent(destPath);
  const apkUrl = `https://firebasestorage.googleapis.com/v0/b/${bucketName}/o/${encodedPath}?alt=media&token=${downloadToken}`;

  const db = admin.firestore();
  await db.collection("appUpdates").doc(channel).set({
    channel,
    latestVersionCode: versionCode,
    latestVersionName: versionName,
    minSupportedVersionCode,
    apkUrl,
    checksum,
    releaseNotes,
    forceUpdate,
    fileSizeBytes,
    releaseDate: admin.firestore.FieldValue.serverTimestamp(),
  });

  console.log(`Published appUpdates/${channel}: versionCode=${versionCode} versionName=${versionName} forceUpdate=${forceUpdate}`);
}

main().catch((error) => {
  console.error(error);
  process.exit(1);
});
