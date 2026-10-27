#!/usr/bin/env node
// Post-deploy smoke test for production. Read-only and signed-out: it creates no accounts and writes nothing.
// Run in Cloud Shell (Node 18+):   node scripts/prod_smoke.mjs
// or without cloning:              curl -fsS https://raw.githubusercontent.com/PriyanshuAGOG/NirogBhumi-App/main/scripts/prod_smoke.mjs | node -
//
// What it proves, from the outside, the way an attacker or a first-time visitor sees the system:
//   * the console and the legal pages are up, carry the security headers, and name only the official contact address;
//   * every callable function is reachable AND refuses a signed-out caller (401 from the function itself:
//     403 means the public invoker is still blocked, 404 means not deployed, 5xx means broken);
//   * the database and file storage refuse signed-out reads.
// It cannot prove anything that needs a signed-in person (sign-up, logging a reading, an announcement): those are in
// docs/DEVICE_QA_MATRIX.md and must be done by hand.

const PROJECT = process.env.PROJECT_ID || 'nirog-bhumi-app';
const REGION = process.env.REGION || 'asia-south1';
const SITE = process.env.SITE_URL || `https://${PROJECT}.web.app`;
const BUCKET = process.env.BUCKET || `${PROJECT}.firebasestorage.app`;
const OFFICIAL = 'priyanshu@nirogbhumi.com';

// Keep in step with `node firebase/functions/scripts/list-callables.mjs` (CI's authz-matrix test fails when a callable is added
// without a declared access level; add it here as well).
const CALLABLES = ['adminEnrollUser', 'adminManageDeletion', 'bootstrapSuperAdmin', 'bulkOnboard', 'cancelAccountDeletion', 'cancelConsultation',
  'createAnnouncement', 'createAuditLog', 'createStaffAccount', 'deleteAnnouncement', 'ensureProgramMembership', 'getExportDownloadLink',
  'getHealthFileShareLink', 'inviteToProgram', 'markAnnouncementSeen', 'previewAnnouncementAudience', 'redeemProgramCode',
  'requestAccountDeletion', 'requestDataExport', 'revokeInvite', 'sendBulkNotification', 'setUserRole'];

const rows = [];
const record = (area, name, ok, detail = '') => { rows.push({ area, name, ok, detail }); console.log(`${ok ? 'PASS' : 'FAIL'}  ${area}: ${name}${detail ? `  (${detail})` : ''}`); };
const get = async (url, init) => { try { return await fetch(url, { redirect: 'manual', ...init }); } catch (e) { return { status: 0, headers: new Headers(), text: async () => String(e.message || e) }; } };

console.log(`Smoke test for project ${PROJECT} (${SITE})\n`);

// 1. Console and headers
{
  const res = await get(`${SITE}/`);
  record('hosting', 'console loads', res.status === 200, `HTTP ${res.status}`);
  const h = res.headers;
  record('hosting', 'X-Frame-Options is DENY', (h.get('x-frame-options') || '').toUpperCase() === 'DENY');
  record('hosting', 'X-Content-Type-Options is nosniff', (h.get('x-content-type-options') || '').toLowerCase() === 'nosniff');
  record('hosting', 'Referrer-Policy is set', !!h.get('referrer-policy'));
  record('hosting', 'framing is blocked by the CSP (frame-ancestors none)', /frame-ancestors\s+'none'/.test(h.get('content-security-policy') || ''));
  record('hosting', 'HTTPS is enforced (HSTS)', !!h.get('strict-transport-security'));
  const http = await get(`http://${PROJECT}.web.app/`);
  record('hosting', 'plain http redirects to https', [301, 302, 307, 308].includes(http.status), `HTTP ${http.status}`);
}

// 2. Legal pages
for (const page of ['privacy-policy', 'terms', 'grievance', 'account-deletion']) {
  const res = await get(`${SITE}/legal/${page}.html`);
  const body = res.status === 200 ? await res.text() : '';
  record('legal', `${page}.html opens without signing in`, res.status === 200 && /<html/i.test(body), `HTTP ${res.status}`);
  if (res.status !== 200) continue;
  const emails = [...new Set((body.match(/[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+\.[A-Za-z.]{2,}/g) || []).map((e) => e.toLowerCase()))];
  record('legal', `${page}.html names only the official contact address`, emails.every((e) => e === OFFICIAL), emails.join(', ') || 'no address');
  const placeholders = (body.match(/class="placeholder"[^>]*>([^<]*)</g) || []).length;
  // Not a failure for the internal track; it is a blocker for a production upload.
  console.log(`      ${placeholders === 0 ? 'no placeholders left' : `NOTE ${placeholders} placeholder(s) still on this page (blocks a production upload)`}`);
}

// 3. Callable functions: reachable, and they refuse a signed-out caller
for (const name of CALLABLES) {
  const res = await get(`https://${REGION}-${PROJECT}.cloudfunctions.net/${name}`, { method: 'POST', headers: { 'content-type': 'application/json' }, body: '{"data":{}}' });
  let status = '';
  try { status = JSON.parse(await res.text())?.error?.status || ''; } catch { /* not JSON */ }
  const good = res.status === 401 && status === 'UNAUTHENTICATED';
  const why = res.status === 403 ? 'HTTP 403: not publicly invokable (org policy / invoker binding)' : res.status === 404 ? 'HTTP 404: not deployed' : res.status >= 500 ? `HTTP ${res.status}: function error` : `HTTP ${res.status} ${status}`;
  record('functions', `${name} refuses a signed-out caller`, good, good ? '' : why);
}

// 4. Database and storage refuse signed-out reads
for (const c of ['users', 'glucoseReadings', 'bpReadings', 'sleepLogs', 'consultations', 'supportRequests', 'dataExportRequests', 'auditLogs', 'mail']) {
  const res = await get(`https://firestore.googleapis.com/v1/projects/${PROJECT}/databases/(default)/documents/${c}?pageSize=1`);
  record('database', `${c} cannot be read signed out`, res.status === 403 || res.status === 401, `HTTP ${res.status}`);
}
{
  const res = await get(`https://firebasestorage.googleapis.com/v0/b/${BUCKET}/o?prefix=users%2F&maxResults=1`);
  record('storage', 'member files cannot be listed signed out', res.status === 403 || res.status === 401, `HTTP ${res.status}`);
}

const failed = rows.filter((r) => !r.ok);
console.log(`\n${rows.length - failed.length}/${rows.length} checks passed.`);
if (failed.length) {
  console.log('\nFailures:');
  for (const r of failed) console.log(`  - ${r.area}: ${r.name}${r.detail ? ` (${r.detail})` : ''}`);
  process.exit(1);
}
