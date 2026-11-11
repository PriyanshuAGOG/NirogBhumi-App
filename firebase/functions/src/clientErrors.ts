import { createHash } from 'node:crypto';
import { FieldValue, Timestamp, type Firestore } from 'firebase-admin/firestore';
import { HttpsError } from 'firebase-functions/v2/https';

/**
 * Problems the app hits BEFORE anyone is signed in (the SMS code not sending, a sign-in refused) can never be written
 * to Firestore by the app, because the rules only let a signed-in person write their own documents. This is the one
 * door for them: anyone may call it, so it is strict about everything it keeps.
 *
 *  - It stores text only, clipped, with phone numbers and email addresses replaced before anything is written.
 *  - It keeps at most one document per distinct problem per day (a counter goes up), so a flood is one row.
 *  - It is throttled per network address, per installation and overall, and quietly drops the excess.
 *  - The caller's account is taken from the sign-in the platform verified, never from the payload.
 */
export const MAX_PER_SOURCE_PER_HOUR = 20;
export const MAX_ALL_PER_HOUR = 600;
const MAX_INSTALLS_LISTED = 25;

const control = /[\u0000-\u001f\u007f]+/g;
const phoneLike = /\+?\d[\d\s-]{7,}\d/g;
const emailLike = /[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}/g;

/** Plain, clipped text with anything that looks like a phone number or an email address removed. */
export function clip(value: unknown, max: number): string {
  if (typeof value !== 'string') return '';
  return value.replace(control, ' ').replace(emailLike, '[email]').replace(phoneLike, '[number]').replace(/\s+/g, ' ').trim().slice(0, max);
}

function whole(value: unknown, min: number, max: number): number | null {
  return typeof value === 'number' && Number.isFinite(value) && value >= min && value <= max ? Math.trunc(value) : null;
}

export interface CleanClientError {
  screen: string; message: string; code: string | null; detail: string;
  appVersion: string; versionCode: number | null; device: string; androidSdk: number | null; installId: string;
}

export function cleanClientError(data: unknown): CleanClientError {
  const d = (data && typeof data === 'object' ? data : {}) as Record<string, unknown>;
  const screen = clip(d.screen, 60);
  const message = clip(d.message, 500);
  if (!screen || !message) throw new HttpsError('invalid-argument', 'screen and message are required');
  return {
    screen, message,
    code: clip(d.code, 80) || null,
    detail: clip(d.detail, 300),
    appVersion: clip(d.appVersion, 30),
    versionCode: whole(d.versionCode, 0, 100_000_000),
    device: clip(d.device, 60),
    androidSdk: whole(d.androidSdk, 1, 100),
    installId: clip(d.installId, 64).replace(/[^A-Za-z0-9_-]/g, ''),
  };
}

const sha = (text: string) => createHash('sha256').update(text).digest('hex');

export async function recordClientError(
  db: Firestore, data: unknown, callerUid: string | null, networkKey: string, now: Date = new Date(),
): Promise<{ accepted: boolean }> {
  const e = cleanClientError(data);
  const hour = Math.floor(now.getTime() / 3_600_000);
  const day = now.toISOString().slice(0, 10);
  // Numbers and long ids are blanked so "code 123456 expired" and "code 654321 expired" are one problem.
  const fingerprint = sha([e.screen, e.code ?? '', e.message.replace(/\d+/g, '#'), e.appVersion].join('|')).slice(0, 24);
  const reportRef = db.doc(`errorReports/c_${fingerprint}_${day}`);
  const limitRefs = [
    { ref: db.doc(`rateLimits/err_net_${networkKey}_${hour}`), max: MAX_PER_SOURCE_PER_HOUR },
    ...(e.installId ? [{ ref: db.doc(`rateLimits/err_inst_${e.installId}_${hour}`), max: MAX_PER_SOURCE_PER_HOUR }] : []),
    { ref: db.doc(`rateLimits/err_all_${hour}`), max: MAX_ALL_PER_HOUR },
  ];

  return db.runTransaction(async tx => {
    const [report, ...limits] = await Promise.all([tx.get(reportRef), ...limitRefs.map(l => tx.get(l.ref))]);
    if (limits.some((snap, i) => (snap.get('count') ?? 0) >= limitRefs[i].max)) return { accepted: false };
    const expireAt = Timestamp.fromMillis(now.getTime() + 3 * 3_600_000);
    limitRefs.forEach((l, i) => tx.set(l.ref, { count: (limits[i].get('count') ?? 0) + 1, expireAt }, { merge: true }));

    const when = FieldValue.serverTimestamp();
    if (!report.exists) {
      tx.set(reportRef, {
        userId: callerUid, screen: e.screen, message: e.message, code: e.code, detail: e.detail, source: 'app_client',
        signedIn: callerUid !== null, appVersion: e.appVersion, versionCode: e.versionCode, device: e.device, androidSdk: e.androidSdk,
        installs: e.installId ? [e.installId] : [], count: 1, resolved: false, firstSeenAt: when, createdAt: when,
      });
    } else {
      const installs: string[] = report.get('installs') ?? [];
      tx.update(reportRef, {
        count: FieldValue.increment(1), createdAt: when, resolved: false,
        ...(e.installId && !installs.includes(e.installId) && installs.length < MAX_INSTALLS_LISTED ? { installs: FieldValue.arrayUnion(e.installId) } : {}),
      });
    }
    return { accepted: true };
  });
}

/** A short, non-reversible key for the caller's network address. */
export function networkKey(ip: unknown): string { return sha(String(ip ?? '')).slice(0, 16); }
