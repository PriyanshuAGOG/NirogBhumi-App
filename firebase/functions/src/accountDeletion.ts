// Account deletion (DPDP erasure / Google Play "delete account and data").
//
// Lifecycle of a deletionRequests/{id} document:
//
//   scheduled ──(grace period elapses)──► processing ──► completed
//       │  ▲                                  │
//       │  └────── retry (attempts < 5) ──────┤
//       ├─► cancelled  (member changed their mind, during the grace period)
//       ├─► approved   (an admin skipped the wait - e.g. an emailed request)
//       └─► rejected   (an admin declined it)            failed (attempts exhausted)
//
// The grace period protects against accidental taps and a hijacked session -
// deletion is irreversible - while still guaranteeing the request is honoured
// without anyone having to remember to approve it (before this, nothing ever
// moved a request to "approved", so no account was ever actually deleted).
//
// What "deleted" means is decided by the member's OWN optional research
// consent (users/{uid}.consent.research): if they opted in, their health
// readings are kept with every link back to them removed; if not (the default),
// the readings are erased too. Everything that identifies a person is always
// removed, including files, chat, inbox threads and the login itself.
import { FieldValue, Timestamp, type DocumentReference, type DocumentSnapshot, type Firestore, type Query, type WriteBatch } from 'firebase-admin/firestore';
import { createHash } from 'node:crypto';

export const DELETION_GRACE_DAYS = 7;
export const MAX_DELETION_ATTEMPTS = 5;
const STALE_PROCESSING_MS = 2 * 60 * 60 * 1000;
const BATCH_SIZE = 400;
const MAX_DRAIN_ROUNDS = 5000;

export interface DeletionDeps {
  db: Firestore;
  /** Deletes every Storage object under a prefix (a no-op if there are none). */
  deleteFiles(prefix: string): Promise<void>;
  /** Deletes the Firebase Auth login. Must treat "already gone" as success. */
  deleteAuthUser(uid: string): Promise<void>;
}

/** Health readings: erased, or anonymized when the member opted into research. */
export const HEALTH_COLLECTIONS = ['glucoseReadings', 'bpReadings', 'sleepLogs', 'walkLogs', 'weightLogs', 'medicationLogs', 'checklistLogs', 'dailyCheckins', 'labReports'];
/** Collections whose documents belong to one member (field `userId`) and are always erased. */
export const PERSONAL_COLLECTIONS = ['profiles', 'dailyActions', 'weeklyReports', 'sugarStories', 'consultations', 'userPrograms', 'programPlans', 'expertNotes', 'notifications', 'deviceConnections', 'supportRequests', 'errorReports', 'programChatMessages', 'dataExportRequests'];
/** Financial records are retained for tax/audit law, but stripped of who they belong to. */
const ORDER_PII_FIELDS = ['userId', 'profileId', 'name', 'phone', 'email', 'address', 'shippingAddress', 'notes'];

const PENDING_STATUSES = ['scheduled', 'approved', 'processing', 'requested', 'awaiting_verification'];

const hashUid = (uid: string) => createHash('sha256').update(uid).digest('hex');

/** Repeatedly runs `query` in pages of 400, applying `apply` to each doc, until nothing matches any more. `apply` must stop the doc matching. */
async function drain(db: Firestore, query: Query, apply: (batch: WriteBatch, doc: DocumentSnapshot) => void): Promise<number> {
  let total = 0;
  for (let round = 0; round < MAX_DRAIN_ROUNDS; round++) {
    const snap = await query.limit(BATCH_SIZE).get();
    if (snap.empty) return total;
    const batch = db.batch();
    snap.docs.forEach(doc => apply(batch, doc));
    await batch.commit();
    total += snap.size;
  }
  throw new Error('drain exceeded its round limit - a query is not shrinking');
}

const eraseAll = (db: Firestore, query: Query) => drain(db, query, (batch, doc) => batch.delete(doc.ref));

export interface EraseOptions { keepAnonymizedHealth: boolean }

/** Removes (or de-identifies) everything linked to `uid`, then the login. Idempotent: safe to re-run after a partial failure. */
export async function eraseUserData(deps: DeletionDeps, uid: string, options: EraseOptions): Promise<void> {
  const { db } = deps;
  const userSnap = await db.doc(`users/${uid}`).get();
  const emails = new Set<string>();
  const phones = new Set<string>();
  const email = String(userSnap.get('email') ?? '').trim();
  const phone = String(userSnap.get('phone') ?? userSnap.get('phoneNumber') ?? '').trim();
  if (email) { emails.add(email); emails.add(email.toLowerCase()); }
  if (phone) phones.add(phone);

  const programIds = new Set<string>();
  const activeProgramId = String(userSnap.get('activeProgramId') ?? '');
  if (activeProgramId) programIds.add(activeProgramId);

  // Chat first, remembering which programs the member spoke in (their photos
  // and voice notes live under per-program Storage paths).
  await drain(db, db.collection('programChatMessages').where('userId', '==', uid), (batch, doc) => {
    const programId = String(doc.get('programId') ?? '');
    if (programId) programIds.add(programId);
    batch.delete(doc.ref);
  });

  for (const name of PERSONAL_COLLECTIONS) {
    if (name === 'programChatMessages') continue;
    await eraseAll(db, db.collection(name).where('userId', '==', uid));
  }

  for (const name of HEALTH_COLLECTIONS) {
    const query = db.collection(name).where('userId', '==', uid);
    if (!options.keepAnonymizedHealth) { await eraseAll(db, query); continue; }
    await drain(db, query, (batch, doc) => {
      const update: Record<string, unknown> = { userId: FieldValue.delete(), profileId: FieldValue.delete(), anonymizedAt: FieldValue.serverTimestamp() };
      // A lab report carries free text (lab name, notes) and a Storage file
      // whose scan shows a name on its face - the file is deleted below, so the
      // dangling URL and any identifying text go with it.
      if (name === 'labReports') { update.labName = FieldValue.delete(); update.notes = FieldValue.delete(); update.fileUrl = FieldValue.delete(); }
      batch.set(doc.ref, update, { merge: true });
    });
  }

  await drain(db, db.collection('orders').where('userId', '==', uid), (batch, doc) => {
    const update: Record<string, unknown> = { anonymizedAt: FieldValue.serverTimestamp(), userIdHash: hashUid(uid) };
    ORDER_PII_FIELDS.forEach(field => { update[field] = FieldValue.delete(); });
    batch.set(doc.ref, update, { merge: true });
  });

  await eraseAll(db, db.collection('programTypingStatus').where('uid', '==', uid));
  await eraseAll(db, db.collection('coachInboxMessages').where('memberUid', '==', uid));
  await eraseAll(db, db.collection('coachMessages').where('toUid', '==', uid));
  await eraseAll(db, db.collection('coachNotes').where('targetUid', '==', uid));

  // Moderation records are kept for community safety, minus the identity of
  // whoever reported, and minus the text (and identity) when this member was
  // the one reported.
  await drain(db, db.collection('reportedMessages').where('reporterId', '==', uid), (batch, doc) => batch.set(doc.ref, { reporterId: FieldValue.delete(), reporterAnonymizedAt: FieldValue.serverTimestamp() }, { merge: true }));
  await drain(db, db.collection('reportedMessages').where('reportedUserId', '==', uid), (batch, doc) => batch.set(doc.ref, { reportedUserId: FieldValue.delete(), reportedText: FieldValue.delete(), reportedAnonymizedAt: FieldValue.serverTimestamp() }, { merge: true }));

  await eraseAll(db, db.collection('programInvites').where('consumedByUid', '==', uid));
  for (const value of emails) await eraseAll(db, db.collection('programInvites').where('contact', '==', value));
  for (const value of phones) await eraseAll(db, db.collection('programInvites').where('contact', '==', value));

  // The staff-facing announcement docs list recipient uids.
  await drain(db, db.collection('announcements').where('recipientUids', 'array-contains', uid), (batch, doc) => batch.update(doc.ref, { recipientUids: FieldValue.arrayRemove(uid) }));

  // Roster row + this member's daily-check-in marker in every batch they were in.
  const roster = await db.collection('programMembers').where('uid', '==', uid).get();
  roster.docs.forEach(doc => { const id = String(doc.get('programId') ?? ''); if (id) programIds.add(id); });
  await eraseAll(db, db.collection('programMembers').where('uid', '==', uid));
  for (const programId of programIds) {
    const days = await db.collection('batchStats').where('programId', '==', programId).get();
    for (let offset = 0; offset < days.docs.length; offset += BATCH_SIZE) {
      const batch = db.batch();
      days.docs.slice(offset, offset + BATCH_SIZE).forEach(day => batch.delete(day.ref.collection('checkedInMembers').doc(uid)));
      await batch.commit();
    }
  }

  // Raw uploads nearly always show identifying detail - deleted, never anonymized in place.
  const prefixes = [`users/${uid}/`, `lab-reports/${uid}/`, `meal-photos/${uid}/`, `consultation-attachments/${uid}/`, `reports/${uid}/`];
  for (const programId of programIds) prefixes.push(`program-chat-photos/${programId}/${uid}/`, `program-chat-audio/${programId}/${uid}/`);
  for (const prefix of prefixes) await deps.deleteFiles(prefix);

  // recursiveDelete also removes users/{uid}/announcements, consentReceipts and any other subcollection.
  await db.recursiveDelete(db.doc(`users/${uid}`));
  await deps.deleteAuthUser(uid);
}

export interface ScheduleResult { requestId: string; scheduledFor: Timestamp; alreadyPending: boolean; status: string }

/** Member (or admin, on their behalf) asks for deletion. Idempotent while a request is already pending. */
export async function scheduleAccountDeletion(db: Firestore, uid: string, source: 'app' | 'admin' | 'email', now = Date.now(), graceDays = DELETION_GRACE_DAYS): Promise<ScheduleResult> {
  const existing = await db.collection('deletionRequests').where('userId', '==', uid).get();
  const pending = existing.docs.find(doc => PENDING_STATUSES.includes(String(doc.get('status'))));
  if (pending) {
    return { requestId: pending.id, alreadyPending: true, status: String(pending.get('status')), scheduledFor: pending.get('scheduledFor') ?? Timestamp.fromMillis(now) };
  }
  const scheduledFor = Timestamp.fromMillis(now + graceDays * 86_400_000);
  const ref = db.collection('deletionRequests').doc();
  await ref.set({ userId: uid, status: 'scheduled', source, scheduledFor, graceDays, attempts: 0, createdAt: FieldValue.serverTimestamp(), updatedAt: FieldValue.serverTimestamp() });
  return { requestId: ref.id, alreadyPending: false, status: 'scheduled', scheduledFor };
}

/** Member cancels during the grace period. Returns false if there was nothing cancellable. */
export async function cancelAccountDeletion(db: Firestore, uid: string): Promise<boolean> {
  const snap = await db.collection('deletionRequests').where('userId', '==', uid).where('status', '==', 'scheduled').get();
  if (snap.empty) return false;
  const batch = db.batch();
  snap.docs.forEach(doc => batch.set(doc.ref, { status: 'cancelled', cancelledAt: FieldValue.serverTimestamp(), updatedAt: FieldValue.serverTimestamp() }, { merge: true }));
  await batch.commit();
  return true;
}

export interface ProcessSummary { completed: number; retrying: number; failed: number; skipped: number }

/** Scheduler entry point: erases every request that is approved, whose grace period elapsed, or that got stuck mid-run. */
export async function processDueDeletions(deps: DeletionDeps, now = Date.now(), limit = 10): Promise<ProcessSummary> {
  const { db } = deps;
  const nowTs = Timestamp.fromMillis(now);
  const requests = db.collection('deletionRequests');
  const [approved, due, stale] = await Promise.all([
    requests.where('status', '==', 'approved').limit(limit).get(),
    requests.where('status', '==', 'scheduled').where('scheduledFor', '<=', nowTs).limit(limit).get(),
    requests.where('status', '==', 'processing').where('updatedAt', '<=', Timestamp.fromMillis(now - STALE_PROCESSING_MS)).limit(limit).get(),
  ]);
  const refs = new Map<string, DocumentReference>();
  [...approved.docs, ...due.docs, ...stale.docs].forEach(doc => refs.set(doc.id, doc.ref));

  const summary: ProcessSummary = { completed: 0, retrying: 0, failed: 0, skipped: 0 };
  for (const ref of [...refs.values()].slice(0, limit)) {
    // Claim it transactionally so two overlapping scheduler runs never erase the same member twice.
    const claimed = await db.runTransaction(async tx => {
      const snap = await tx.get(ref);
      const status = String(snap.get('status'));
      const eligible = status === 'approved'
        || (status === 'scheduled' && ((snap.get('scheduledFor') as Timestamp | undefined)?.toMillis() ?? Infinity) <= now)
        || (status === 'processing' && ((snap.get('updatedAt') as Timestamp | undefined)?.toMillis() ?? Infinity) <= now - STALE_PROCESSING_MS);
      if (!eligible || !snap.get('userId')) return null;
      const attempts = Number(snap.get('attempts') ?? 0) + 1;
      tx.set(ref, { status: 'processing', attempts, updatedAt: Timestamp.fromMillis(now) }, { merge: true });
      return { uid: String(snap.get('userId')), attempts };
    });
    if (!claimed) { summary.skipped++; continue; }

    try {
      const userSnap = await db.doc(`users/${claimed.uid}`).get();
      const keepAnonymizedHealth = userSnap.get('consent.research') === true || userSnap.get('consent')?.research === true;
      await eraseUserData(deps, claimed.uid, { keepAnonymizedHealth });
      await ref.set({
        status: 'completed', mode: keepAnonymizedHealth ? 'anonymized_health_retained' : 'erased', completedAt: FieldValue.serverTimestamp(), updatedAt: FieldValue.serverTimestamp(),
        userIdHash: hashUid(claimed.uid), userId: FieldValue.delete(), lastError: FieldValue.delete(),
      }, { merge: true });
      await db.collection('auditLogs').add({ actorId: 'system', actorRole: 'system', action: 'account_deletion_completed', entityType: 'user', entityId: hashUid(claimed.uid), metadata: { mode: keepAnonymizedHealth ? 'anonymized_health_retained' : 'erased', attempts: claimed.attempts }, createdAt: FieldValue.serverTimestamp() });
      summary.completed++;
    } catch (error) {
      console.error('Account deletion failed', ref.id, error);
      const exhausted = claimed.attempts >= MAX_DELETION_ATTEMPTS;
      await ref.set({ status: exhausted ? 'failed' : 'approved', lastError: String((error as Error)?.message ?? error).slice(0, 500), updatedAt: FieldValue.serverTimestamp() }, { merge: true });
      if (exhausted) summary.failed++; else summary.retrying++;
    }
  }
  return summary;
}
