// Deny-by-default matrix, generated from the rules file itself. Every collection the rules mention is probed as a
// signed-out visitor and as a stranger (a signed-in member who owns nothing), against a document that belongs to
// someone else. Anything that is open must be listed here on purpose; a new collection that is open by accident
// shows up as a failure with its name. Collections the app never writes (the outgoing-mail queue, system flags)
// must be closed to every client, staff included.
import { readFileSync } from 'node:fs';
import { before, after, beforeEach, describe, it } from 'node:test';
import assert from 'node:assert/strict';
import { initializeTestEnvironment } from '@firebase/rules-unit-testing';
import { addDoc, collection, deleteDoc, doc, getDoc, setDoc, updateDoc } from 'firebase/firestore';

const RULES = readFileSync(new URL('../firestore.rules', import.meta.url), 'utf8');
const mentioned = new Set([...RULES.matchAll(/match \/([A-Za-z][A-Za-z0-9_]*)\/\{/g)].map((m) => m[1]));
const FROM_WILDCARDS = ['glucoseReadings', 'bpReadings', 'sleepLogs', 'walkLogs', 'weightLogs', 'medicationLogs', 'labReports', 'dailyCheckins', 'checklistLogs',
  'dailyActions', 'weeklyReports', 'sugarStories', 'userPrograms', 'programPlans', 'expertNotes', 'notifications', 'deviceConnections', 'orders', 'dataExportRequests', 'deletionRequests'];
const NEVER_FROM_CLIENTS = ['mail', 'system', 'secrets', 'mailQueue', 'adminOnly', 'config'];
const UNIVERSE = [...new Set([...mentioned, ...FROM_WILDCARDS])].filter((c) => !['databases', 'users'].includes(c)).concat('users');

// Open on purpose. Reading by any visitor: the app-update manifest and published content.
const PUBLIC_READ = new Set(['appUpdates']);
// Readable by any signed-in person on purpose (shared, non-personal or filtered by the rule itself).
const SIGNED_IN_READ = new Set(['programs', 'consultationSlots']);

let env;
before(async () => { env = await initializeTestEnvironment({ projectId: 'demo-nirog-bhumi', firestore: { rules: RULES, host: 'localhost', port: 8080 } }); });
after(async () => { await env.cleanup(); });
beforeEach(async () => {
  await env.clearFirestore();
  await env.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    for (const c of [...UNIVERSE, ...NEVER_FROM_CLIENTS]) {
      await setDoc(doc(db, `${c}/victimdoc`), { userId: 'victim', uid: 'victim', programId: 'p1', coachId: 'coach-x', status: 'active', text: 'private', name: 'Victim', expertId: 'ex1', requestedBy: 'victim' });
    }
  });
});

const anon = () => env.unauthenticatedContext().firestore();
const stranger = () => env.authenticatedContext('stranger', {}).firestore();
const staff = (role) => env.authenticatedContext('staff1', { role }).firestore();
const ok = async (p) => { try { await p; return true; } catch { return false; } };

describe('signed-out visitors', () => {
  it('cannot read anything except the public manifest, and cannot write anything anywhere', async () => {
    const open = [];
    for (const c of [...UNIVERSE, ...NEVER_FROM_CLIENTS]) {
      if (await ok(getDoc(doc(anon(), `${c}/victimdoc`))) && !PUBLIC_READ.has(c)) open.push(`read ${c}`);
      if (await ok(setDoc(doc(anon(), `${c}/new1`), { userId: 'x', text: 'x' }))) open.push(`create ${c}`);
      if (await ok(updateDoc(doc(anon(), `${c}/victimdoc`), { text: 'hacked' }))) open.push(`update ${c}`);
      if (await ok(deleteDoc(doc(anon(), `${c}/victimdoc`)))) open.push(`delete ${c}`);
    }
    assert.deepEqual(open, [], `Open to signed-out visitors: ${open.join(', ')}`);
  });
});

describe('a stranger (signed in, owns nothing)', () => {
  it('cannot read, edit or delete another person\'s documents', async () => {
    const open = [];
    for (const c of UNIVERSE) {
      if (await ok(getDoc(doc(stranger(), `${c}/victimdoc`))) && !PUBLIC_READ.has(c) && !SIGNED_IN_READ.has(c)) open.push(`read ${c}`);
      if (await ok(updateDoc(doc(stranger(), `${c}/victimdoc`), { text: 'hacked' }))) open.push(`update ${c}`);
      if (await ok(deleteDoc(doc(stranger(), `${c}/victimdoc`)))) open.push(`delete ${c}`);
    }
    assert.deepEqual(open, [], `A stranger can reach: ${open.join(', ')}`);
  });

  it('cannot create a document that claims to belong to someone else', async () => {
    const open = [];
    for (const c of UNIVERSE) {
      if (await ok(setDoc(doc(stranger(), `${c}/forged1`), { userId: 'victim', uid: 'victim', text: 'forged' }))) open.push(c);
      if (await ok(addDoc(collection(stranger(), c), { userId: 'victim', uid: 'victim', text: 'forged' }))) open.push(`${c} (auto id)`);
    }
    assert.deepEqual(open, [], `A stranger can plant data as another person in: ${open.join(', ')}`);
  });
});

describe('collections the app never writes', () => {
  it('are closed to members, coaches, experts and admins alike (the mail queue would let anyone send email as us)', async () => {
    const open = [];
    for (const c of NEVER_FROM_CLIENTS) {
      for (const [who, db] of [['member', stranger()], ['coach', staff('coach')], ['expert', staff('expert')], ['admin', staff('admin')], ['super_admin', staff('super_admin')]]) {
        if (await ok(setDoc(doc(db, `${c}/new1`), { to: 'someone@example.org', message: { subject: 's', text: 't' } }))) open.push(`${who} creates ${c}`);
        if (await ok(updateDoc(doc(db, `${c}/victimdoc`), { text: 'x' }))) open.push(`${who} updates ${c}`);
        if (await ok(deleteDoc(doc(db, `${c}/victimdoc`)))) open.push(`${who} deletes ${c}`);
        if (await ok(getDoc(doc(db, `${c}/victimdoc`)))) open.push(`${who} reads ${c}`);
      }
    }
    assert.deepEqual(open, [], `Unexpectedly open: ${open.join(', ')}`);
  });

  it('the audit log cannot be written or changed by any client, admins included', async () => {
    for (const db of [stranger(), staff('coach'), staff('admin'), staff('super_admin')]) {
      assert.equal(await ok(setDoc(doc(db, 'auditLogs/new1'), { action: 'x' })), false);
      assert.equal(await ok(updateDoc(doc(db, 'auditLogs/victimdoc'), { action: 'x' })), false);
      assert.equal(await ok(deleteDoc(doc(db, 'auditLogs/victimdoc'))), false);
    }
  });
});
