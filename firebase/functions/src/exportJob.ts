import { FieldValue, type Firestore } from 'firebase-admin/firestore';
import { buildExportZip } from './dataExport.js';
import { escapeHtml, looksLikeEmail, queueEmail } from './email.js';

export const EXPORT_LINK_EMAIL_TTL_MS = 3 * 60 * 60 * 1000;
export const EXPORT_LINK_APP_TTL_MS = 15 * 60 * 1000;

/** Collections keyed by `userId` that belong in the export. (Health readings first: they are what people ask for.) */
export const EXPORT_COLLECTIONS = ['users', 'profiles', 'glucoseReadings', 'bpReadings', 'sleepLogs', 'walkLogs', 'weightLogs', 'labReports', 'dailyCheckins', 'dailyActions', 'weeklyReports', 'sugarStories', 'consultations', 'userPrograms', 'programPlans', 'checklistLogs', 'expertNotes', 'notifications', 'deviceConnections', 'medicationLogs', 'orders', 'supportRequests', 'programChatMessages'];

export interface ExportDeps {
  db: Firestore;
  saveFile(path: string, data: Buffer, contentType: string, ownerUid: string): Promise<void>;
  /** A time-limited download link, or null when the platform cannot sign (the IAM grant is missing). */
  signUrl(path: string, ttlMs: number, downloadName: string): Promise<string | null>;
  now?: () => Date;
}

export type ExportOutcome = 'completed' | 'already_done' | 'missing';

async function gather(db: Firestore, uid: string, now: Date): Promise<Record<string, unknown>> {
  const exported: Record<string, unknown> = { exportedAt: now.toISOString(), formatVersion: 2 };
  for (const name of EXPORT_COLLECTIONS) {
    if (name === 'users') { const user = await db.doc(`users/${uid}`).get(); exported.users = user.exists ? [{ id: user.id, ...user.data() }] : []; continue; }
    const snapshot = await db.collection(name).where('userId', '==', uid).get();
    exported[name] = snapshot.docs.map(doc => ({ id: doc.id, ...doc.data() }));
  }
  // Not keyed by `userId`: coach-inbox threads, program roster entries and the consent receipts (what they agreed to, and when).
  exported.coachInboxMessages = (await db.collection('coachInboxMessages').where('memberUid', '==', uid).get()).docs.map(doc => ({ id: doc.id, ...doc.data() }));
  exported.programMembers = (await db.collection('programMembers').where('uid', '==', uid).get()).docs.map(doc => ({ id: doc.id, ...doc.data() }));
  exported.consentReceipts = (await db.collection(`users/${uid}/consentReceipts`).get()).docs.map(doc => ({ id: doc.id, ...doc.data() }));
  return exported;
}

/**
 * Builds the member's export: one ZIP (data.json, CSVs, README), stored privately; then tells them. Safe to run
 * twice for the same request (a retried trigger): a finished request is left alone and the email is queued under a
 * fixed key, so at most one email ever goes out.
 */
export async function processExportRequest(deps: ExportDeps, requestId: string): Promise<ExportOutcome> {
  const { db } = deps;
  const now = deps.now?.() ?? new Date();
  const ref = db.doc(`dataExportRequests/${requestId}`);
  const snap = await ref.get();
  if (!snap.exists) return 'missing';
  const uid = String(snap.get('userId') ?? '');
  if (!uid) { await ref.set({ status: 'failed', errorCode: 'no_owner', updatedAt: FieldValue.serverTimestamp() }, { merge: true }); return 'missing'; }
  if (snap.get('status') === 'completed') return 'already_done';

  await ref.set({ status: 'processing', updatedAt: FieldValue.serverTimestamp() }, { merge: true });
  try {
    const exported = await gather(db, uid, now);
    const { zip, counts } = buildExportZip(exported, now);
    const path = `users/${uid}/exports/${requestId}.zip`;
    await deps.saveFile(path, zip, 'application/zip', uid);

    // Tell them: a push/in-app notification always; an email when the account has an address (phone-only accounts skip it).
    const email = String((await db.doc(`users/${uid}`).get()).get('email') ?? '').trim();
    let emailStatus: string;
    let linkIncluded = false;
    if (!looksLikeEmail(email)) {
      emailStatus = 'skipped_no_email';
    } else {
      try {
        const link = await deps.signUrl(path, EXPORT_LINK_EMAIL_TTL_MS, 'nirog-bhumi-data-export.zip');
        linkIncluded = !!link;
        const hours = EXPORT_LINK_EMAIL_TTL_MS / 3_600_000;
        const text = [
          'Your Nirog Bhumi data export is ready.',
          '',
          link ? `Download it here (the link works for ${hours} hours and only for you): ${link}` : 'Open the Nirog Bhumi app > Profile > Export or delete my data to download it.',
          '',
          'It is a ZIP file with your readings as spreadsheets and everything in one data file. It contains health information, so keep it private.',
          'Need a fresh link? Open the app and ask for a new export any time.',
          '',
          'If you did not ask for this, please contact us right away.',
        ].join('\n');
        const html = `<p>Your Nirog Bhumi data export is ready.</p><p>${link ? `<a href="${escapeHtml(link)}">Download your export</a> (the link works for ${hours} hours and only for you).` : 'Open the Nirog Bhumi app &gt; Profile &gt; Export or delete my data to download it.'}</p><p>It is a ZIP file with your readings as spreadsheets and everything in one data file. It contains health information, so keep it private.</p><p>If you did not ask for this, please contact us right away.</p>`;
        emailStatus = await queueEmail(db, `export_${requestId}`, { to: email, subject: 'Your Nirog Bhumi data export is ready', text, html });
      } catch (error) {
        console.error('export email failed', error);
        emailStatus = 'failed';
      }
    }

    await ref.set({ status: 'completed', storagePath: path, format: 'zip', counts, emailStatus, emailLinkIncluded: linkIncluded, completedAt: FieldValue.serverTimestamp(), updatedAt: FieldValue.serverTimestamp() }, { merge: true });
    await db.collection('notifications').add({ userId: uid, profileId: null, title: 'Your data export is ready', body: 'Open Privacy and Data Controls to download it.', type: 'privacy', status: 'scheduled', scheduledFor: FieldValue.serverTimestamp(), createdAt: FieldValue.serverTimestamp() });
    return 'completed';
  } catch (error) {
    console.error('export failed', error);
    await ref.set({ status: 'failed', errorCode: 'export_failed', updatedAt: FieldValue.serverTimestamp() }, { merge: true });
    throw error;
  }
}

/** A short-lived link for the signed-in owner of a completed export. Returns null when signing is unavailable. */
export async function exportDownloadLink(deps: Pick<ExportDeps, 'db' | 'signUrl'>, uid: string, requestId: string): Promise<{ url: string | null; expiresInMinutes: number; storagePath: string }> {
  const snap = await deps.db.doc(`dataExportRequests/${requestId}`).get();
  const storagePath = String(snap.get('storagePath') ?? '');
  if (!snap.exists || snap.get('userId') !== uid || snap.get('status') !== 'completed' || !storagePath.startsWith(`users/${uid}/exports/`)) {
    const error = new Error('not-found'); (error as { code?: string }).code = 'not-found'; throw error;
  }
  const url = await deps.signUrl(storagePath, EXPORT_LINK_APP_TTL_MS, 'nirog-bhumi-data-export.zip');
  return { url, expiresInMinutes: EXPORT_LINK_APP_TTL_MS / 60_000, storagePath };
}
