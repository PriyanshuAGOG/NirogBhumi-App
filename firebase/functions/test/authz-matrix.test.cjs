// Who may call what. This table is the access contract for every callable function: a new callable that is not
// listed here fails the first test, so nobody can add one without deciding who is allowed to use it.
const { test, before } = require('node:test');
const assert = require('node:assert/strict');
const { fns, db, resetFirestore, call, rejectsWithCode } = require('./helpers.cjs');

const callables = Object.entries(fns).filter(([, fn]) => fn && fn.__endpoint && 'callableTrigger' in fn.__endpoint).map(([name]) => name).sort();

// 'member' = any signed-in person acting on their own data; 'staff' = admin, super_admin or coach; 'admin' = admin or super_admin;
// 'bootstrap' = only the owner's own email address, once.
const POLICY = {
  adminEnrollUser: 'staff', adminManageDeletion: 'admin', bootstrapSuperAdmin: 'bootstrap', bulkOnboard: 'staff',
  cancelAccountDeletion: 'member', cancelConsultation: 'member', createAnnouncement: 'staff', createAuditLog: 'admin',
  createStaffAccount: 'admin', deleteAnnouncement: 'staff', ensureProgramMembership: 'member', getExportDownloadLink: 'member',
  getHealthFileShareLink: 'member', inviteToProgram: 'staff', markAnnouncementSeen: 'member', previewAnnouncementAudience: 'staff',
  redeemProgramCode: 'member', requestAccountDeletion: 'member', requestDataExport: 'member', revokeInvite: 'staff',
  sendBulkNotification: 'staff', setUserRole: 'admin',
};

// Deliberately generic, plausible input so a function reaches its access check rather than failing on missing fields.
const DATA = {
  uid: 'victim', programId: 'p1', programIds: ['p1'], id: 'x1', requestId: 'x1', announcementId: 'x1', code: 'ABCD1234', role: 'super_admin',
  email: 'someone@example.org', contact: 'someone@example.org', title: 'Hello', body: 'World', rows: [{ email: 'a@example.org', name: 'A' }],
  audience: { scope: 'all_users' }, channels: { inApp: true }, storagePath: 'users/victim/health_file.pdf', password: 'Passw0rd!x', fullName: 'X Y',
  action: 'approve', userIds: ['victim'], uids: ['victim'], entityType: 'user', entityId: 'victim',
};

before(resetFirestore);

test('every callable has a declared access level', () => {
  const missing = callables.filter(name => !POLICY[name]);
  const stale = Object.keys(POLICY).filter(name => !callables.includes(name));
  assert.deepEqual(missing, [], `Add these new callables to POLICY with the right access level: ${missing.join(', ')}`);
  assert.deepEqual(stale, [], `POLICY lists functions that no longer exist: ${stale.join(', ')}`);
});

test('nobody who is signed out can call any function', async () => {
  for (const name of callables) await rejectsWithCode(call(fns[name], DATA, null), 'unauthenticated').catch(e => { throw new Error(`${name}: ${e.message}`); });
});

test('an ordinary member is refused by every staff, admin and bootstrap function', async () => {
  for (const name of callables.filter(n => POLICY[n] !== 'member')) {
    await rejectsWithCode(call(fns[name], DATA, 'plain-member', 'user'), 'permission-denied').catch(e => { throw new Error(`${name}: ${e.message}`); });
  }
});

test('a member with no role claim at all is refused the same way', async () => {
  for (const name of callables.filter(n => POLICY[n] !== 'member')) {
    await rejectsWithCode(call(fns[name], DATA, 'no-role-member'), 'permission-denied').catch(e => { throw new Error(`${name}: ${e.message}`); });
  }
});

test('a coach is refused every admin-only and bootstrap function', async () => {
  for (const name of callables.filter(n => POLICY[n] === 'admin' || POLICY[n] === 'bootstrap')) {
    await rejectsWithCode(call(fns[name], DATA, 'a-coach', 'coach'), 'permission-denied').catch(e => { throw new Error(`${name}: ${e.message}`); });
  }
});

test('a role the server does not know (expert, empty, forged text) gets no staff or admin power', async () => {
  for (const role of ['expert', '', 'Admin', 'superadmin', 'root']) {
    for (const name of callables.filter(n => POLICY[n] === 'admin' || POLICY[n] === 'bootstrap')) {
      await rejectsWithCode(call(fns[name], DATA, 'odd-role', role), 'permission-denied').catch(e => { throw new Error(`${name} with role "${role}": ${e.message}`); });
    }
  }
});

test('a member cannot give themselves a role, or create staff, by any route', async () => {
  await db.doc('users/plain-member').set({ role: 'user', email: 'plain@example.org' });
  await rejectsWithCode(call(fns.setUserRole, { uid: 'plain-member', role: 'super_admin' }, 'plain-member', 'user'), 'permission-denied');
  await rejectsWithCode(call(fns.createStaffAccount, { email: 'x@example.org', password: 'Passw0rd!x', role: 'admin', fullName: 'X' }, 'plain-member', 'user'), 'permission-denied');
  assert.equal((await db.doc('users/plain-member').get()).get('role'), 'user');
});

test('the one-time super admin bootstrap refuses any email other than the owner, even a look-alike', async () => {
  for (const email of ['attacker@example.org', 'PRIYANSHU@nirogbhumi.com.evil.example', 'priyanshu+1@nirogbhumi.com', 'xpriyanshu@nirogbhumi.com', '']) {
    await rejectsWithCode(call(fns.bootstrapSuperAdmin, {}, 'impostor', undefined, { email }), 'permission-denied').catch(e => { throw new Error(`"${email}": ${e.message}`); });
  }
});
