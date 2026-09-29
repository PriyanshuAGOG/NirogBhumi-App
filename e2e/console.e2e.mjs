// End-to-end tests of the staff console: a real Chromium driving the production
// console bundle (built with VITE_USE_EMULATORS) against the Firebase Auth,
// Firestore and Cloud Functions emulators, seeded by seed.mjs. Covers the admin
// and the coach persona through the flows that were broken or changed in the
// launch-readiness work (access codes, announcements, data requests, coach
// scoping, batch messaging). Never touches production.
import { createRequire } from 'node:module';
import { chromium } from 'playwright-core';
import http from 'node:http';
import fs from 'node:fs';
import path from 'node:path';

const require = createRequire(new URL('../firebase/functions/package.json', import.meta.url));
process.env.GCLOUD_PROJECT = 'demo-nirog-bhumi';
const { initializeApp } = require('firebase-admin/app');
const { getAuth } = require('firebase-admin/auth');
const { getFirestore } = require('firebase-admin/firestore');
initializeApp();
const adb = getFirestore();
const aauth = getAuth();

const PASSWORD = 'Passw0rd!';
const PORT = 4173;
const BASE = `http://127.0.0.1:${PORT}`;
const DIST = new URL('./dist-emulator/', import.meta.url).pathname;
const ARTIFACTS = new URL('./artifacts/', import.meta.url).pathname;
fs.mkdirSync(ARTIFACTS, { recursive: true });

// ---- tiny static server with SPA fallback ---------------------------------
const MIME = { '.html': 'text/html', '.js': 'text/javascript', '.css': 'text/css', '.svg': 'image/svg+xml', '.json': 'application/json', '.png': 'image/png', '.woff2': 'font/woff2' };
const server = http.createServer((req, res) => {
  const urlPath = decodeURIComponent((req.url ?? '/').split('?')[0]);
  let file = path.join(DIST, urlPath);
  if (!file.startsWith(DIST) || !fs.existsSync(file) || fs.statSync(file).isDirectory()) file = path.join(DIST, 'index.html');
  res.writeHead(200, { 'content-type': MIME[path.extname(file)] ?? 'application/octet-stream' });
  fs.createReadStream(file).pipe(res);
});
await new Promise((resolve) => server.listen(PORT, '127.0.0.1', resolve));

// ---- helpers --------------------------------------------------------------
// Use Playwright's own browser when it is installed (CI: `npx playwright-core
// install chromium`); otherwise fall back to any Chromium already on the machine.
function findChrome() {
  const own = chromium.executablePath();
  if (own && fs.existsSync(own)) return own;
  const root = process.env.PLAYWRIGHT_BROWSERS_PATH ?? '/opt/pw-browsers';
  const dir = fs.existsSync(root) ? fs.readdirSync(root).find((d) => /^chromium-\d+$/.test(d)) : null;
  if (!dir) throw new Error('No Chromium found - run `npx playwright-core install chromium` in e2e/');
  return path.join(root, dir, 'chrome-linux', 'chrome');
}
const executablePath = findChrome();
const browser = await chromium.launch({ executablePath, args: ['--no-sandbox'] });

const results = [];
let currentPage = null;
async function step(name, fn) {
  try {
    await fn();
    results.push({ name, ok: true });
    console.log(`  ✓ ${name}`);
  } catch (error) {
    results.push({ name, ok: false, error });
    console.log(`  ✗ ${name}\n      ${String(error.message).split('\n').slice(0, 3).join('\n      ')}`);
    if (currentPage) await currentPage.screenshot({ path: path.join(ARTIFACTS, `${name.replace(/[^a-z0-9]+/gi, '_').slice(0, 60)}.png`), fullPage: true }).catch(() => {});
  }
}
const snap = (page, name) => page.screenshot({ path: path.join(ARTIFACTS, `shot-${name}.png`), fullPage: true }).catch(() => {});
const expect = (cond, message) => { if (!cond) throw new Error(message); };
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
async function eventually(fn, message, timeout = 15000) {
  const start = Date.now();
  let last;
  while (Date.now() - start < timeout) {
    try { const v = await fn(); if (v) return v; } catch (e) { last = e; }
    await sleep(250);
  }
  throw new Error(`${message}${last ? ` (${last.message})` : ''}`);
}

async function idTokenFor(email) {
  const res = await fetch('http://127.0.0.1:9099/identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=fake', {
    method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify({ email, password: PASSWORD, returnSecureToken: true }),
  });
  const json = await res.json();
  if (!json.idToken) throw new Error(`sign-in failed for ${email}: ${JSON.stringify(json)}`);
  return json.idToken;
}
/** Calls a deployed callable on the functions emulator exactly as the Firebase SDK would. */
async function callFn(name, data, email) {
  const token = await idTokenFor(email);
  const res = await fetch(`http://127.0.0.1:5001/demo-nirog-bhumi/asia-south1/${name}`, {
    method: 'POST', headers: { 'content-type': 'application/json', authorization: `Bearer ${token}` }, body: JSON.stringify({ data }),
  });
  const json = await res.json();
  return json;
}

async function newPersona(email) {
  const context = await browser.newContext({ viewport: { width: 1360, height: 900 } });
  const page = await context.newPage();
  const problems = [];
  page.on('console', (msg) => { if (msg.type() === 'error') problems.push(msg.text()); });
  page.on('pageerror', (err) => problems.push(`pageerror: ${err.message}`));
  await page.goto(BASE);
  await page.locator('input[type="email"]').fill(email);
  await page.locator('input[type="password"]').fill(PASSWORD);
  await page.getByRole('button', { name: /sign in/i }).click();
  await page.locator('nav, aside').first().waitFor({ timeout: 20000 });
  currentPage = page;
  return { page, problems, context };
}
async function go(page, route) {
  await page.locator(`a[href="${route}"]`).first().click();
  await page.waitForURL(`**${route}`);
  await page.waitForLoadState('networkidle').catch(() => {});
}
const noBanner = async (page, what) => {
  await sleep(1200);
  const banners = await page.locator('.banner-error').allTextContents();
  expect(banners.length === 0, `${what} shows an error banner: ${banners.join(' | ')}`);
};
const navLabels = (page) => page.locator('nav a, aside a').allTextContents().then((t) => t.map((x) => x.replace(/[^\w &+]/g, '').trim()));
const tileValue = async (page, label) => {
  const tile = page.locator('.tile', { hasText: label }).first();
  await tile.waitFor();
  return eventually(async () => {
    const text = (await tile.locator('.tile-count').innerText()).trim();
    return /^\d+$/.test(text) ? Number(text) : null;
  }, `tile "${label}" never showed a number`);
};

// ===========================================================================
console.log('\nAdmin console');
const admin = await newPersona('admin@test.dev');
let accessCode = '';

await step('admin sees the whole navigation', async () => {
  const labels = (await navLabels(admin.page)).join('|');
  for (const item of ['Overview', 'Members', 'Batches', 'Announcements', 'Programs', 'Consultations', 'Data Requests', 'Users', 'Error Reports', 'Content']) expect(labels.includes(item), `nav is missing ${item}: ${labels}`);
});

await step('dashboard counts are real numbers (no failed tiles)', async () => {
  expect((await tileValue(admin.page, 'Total members')) === 8, 'Total members should be 8');
  expect((await tileValue(admin.page, 'Open reports')) === 2, 'Open reports should be 2 (one per batch)');
  expect((await tileValue(admin.page, 'Support requests')) === 1, 'Support requests should be 1');
  expect((await tileValue(admin.page, 'Active Care+ members')) === 2, 'Active Care+ members should be 2');
  expect((await tileValue(admin.page, 'Programs')) === 2, 'Programs should be 2');
  await eventually(async () => (await admin.page.locator('.quiet-row', { hasText: 'Asha Member' }).count()) === 1, 'quiet member Asha should be listed');
  await snap(admin.page, 'admin-dashboard');
});

await step('programs page lists both batches and the pending invite without errors', async () => {
  await go(admin.page, '/programs');
  await admin.page.getByText('July Batch').first().waitFor();
  await admin.page.getByText('August Batch').first().waitFor();
  await eventually(async () => (await admin.page.getByText('invitee@test.dev').count()) > 0, 'invite row should be listed (programInvites rules)');
  await noBanner(admin.page, 'Programs');
});

await step('create an access code (Generate, max uses 2) for July Batch', async () => {
  await admin.page.getByRole('button', { name: '+ New code' }).click();
  await admin.page.getByRole('button', { name: 'Generate' }).click();
  accessCode = await admin.page.locator('.modal input.input').first().inputValue();
  expect(/^[A-Z2-9]{8}$/.test(accessCode), `generated code looks wrong: "${accessCode}"`);
  await admin.page.locator('.modal select.select').selectOption({ label: 'July Batch' });
  await admin.page.locator('.modal input[type="number"]').fill('2');
  await admin.page.getByRole('button', { name: 'Create code' }).click();
  await admin.page.locator('.code-row', { hasText: accessCode }).waitFor();
  const doc = (await adb.doc(`programCodes/${accessCode}`).get()).data();
  expect(doc.programId === 'progA' && doc.maxUses === 2 && doc.uses === 0 && doc.active === true, `stored code is wrong: ${JSON.stringify(doc)}`);
  await admin.page.getByRole('button', { name: '+ New code' }).click();
  await admin.page.locator('.modal input.input').first().fill(accessCode.toLowerCase());
  await admin.page.getByRole('button', { name: 'Create code' }).click();
  await admin.page.getByText(/already exists/i).waitFor();
  await admin.page.getByRole('button', { name: 'Cancel' }).click();
});

await step('members can redeem that code until it runs out (real callable, real rules)', async () => {
  const first = await callFn('redeemProgramCode', { code: accessCode.toLowerCase() }, 'newbie@test.dev');
  expect(first.result?.activeProgramId === 'progA', `first redeem failed: ${JSON.stringify(first)}`);
  const second = await callFn('redeemProgramCode', { code: accessCode }, 'leaving@test.dev');
  expect(second.result?.activeProgramId === 'progA', `second redeem failed: ${JSON.stringify(second)}`);
  const third = await callFn('redeemProgramCode', { code: accessCode }, 'ravi@test.dev');
  expect(third.error?.status === 'RESOURCE_EXHAUSTED', `third redeem should be refused: ${JSON.stringify(third)}`);
  expect(/reached its limit/i.test(third.error.message), `unhelpful message: ${third.error.message}`);
  expect((await adb.doc('users/memB').get()).get('activeProgramId') === 'progB', 'refused member must stay in their batch');
  expect((await adb.doc(`programCodes/${accessCode}`).get()).get('uses') === 2, 'uses should be exactly 2');
});

await step('the console reflects usage (2 / 2 used)', async () => {
  await admin.page.reload();
  await admin.page.locator('.code-row', { hasText: accessCode }).getByText(/2 \/ 2 used/).waitFor();
  await eventually(async () => (await admin.page.locator('.prog-members').allTextContents()).join('|') === '1 member|3 members', 'programs should show live roster counts (1 and 3), not the stale stored field');
  await snap(admin.page, 'admin-programs');
});

await step('announcement: preview audience, send to July Batch, member gets a feed copy', async () => {
  await go(admin.page, '/announcements');
  await admin.page.getByPlaceholder("This week's focus").fill('E2E schedule update');
  await admin.page.getByPlaceholder(/Share what's ahead/).fill('Walk at 7am on Saturday.');
  await admin.page.locator('label.check', { hasText: 'July Batch' }).locator('input').check();
  await admin.page.getByRole('button', { name: 'Preview audience' }).click();
  // asha + newbie + leaving joined July Batch's roster (programMembers) => 3
  await admin.page.getByRole('button', { name: /Reaches 3 people/ }).waitFor();
  await admin.page.getByRole('button', { name: 'Send announcement' }).click();
  await eventually(async () => (await admin.page.getByText('E2E schedule update').count()) > 0, 'sent announcement should appear in the list');
  await noBanner(admin.page, 'Announcements');
  await snap(admin.page, 'admin-announcements');
  const feed = await adb.collection('users/memA/announcements').get();
  expect(feed.size === 1 && feed.docs[0].get('title') === 'E2E schedule update', 'Asha should have the announcement in her feed');
  expect(feed.docs[0].get('authorId') === 'admin1', 'fan-out copy should carry authorId');
  expect((await adb.collection('users/memB/announcements').get()).size === 0, 'Ravi (other batch) must not receive it');
});

await step('data requests: see the scheduled deletion, erase now with confirmation', async () => {
  await go(admin.page, '/data-requests');
  const card = admin.page.locator('.sup-card', { hasText: 'Leaving Member' });
  await card.waitFor();
  await card.getByText(/Waiting/).waitFor();
  await snap(admin.page, 'admin-data-requests');
  await card.getByRole('button', { name: 'Erase now' }).click();
  await admin.page.getByText('Erase Leaving Member now?').waitFor();
  await admin.page.getByRole('button', { name: 'Erase permanently' }).click();
  await admin.page.getByText(/Approved\. The account will be erased/).waitFor();
  const req = (await adb.collection('deletionRequests').where('userId', '==', 'memDel').get()).docs[0];
  expect(req.get('status') === 'approved' && req.get('approvedBy') === 'admin1', `request should be approved by admin: ${req.get('status')}`);
});

await step('data requests: start a deletion for an emailed request; unknown email is refused', async () => {
  await admin.page.getByRole('button', { name: /Start a deletion for an emailed request/ }).click();
  await admin.page.getByPlaceholder(/name@example.com/).fill('nobody@test.dev');
  await admin.page.getByRole('button', { name: 'Continue…' }).click();
  await admin.page.getByRole('button', { name: 'Erase permanently' }).click();
  await admin.page.getByText(/No account matches/i).waitFor();
  await admin.page.getByRole('button', { name: 'Cancel' }).click();
  await admin.page.getByPlaceholder(/name@example.com/).fill('newbie@test.dev');
  await admin.page.getByRole('button', { name: 'Continue…' }).click();
  await admin.page.getByRole('button', { name: 'Erase permanently' }).click();
  await admin.page.getByText(/Deletion started/).waitFor();
  const req = (await adb.collection('deletionRequests').where('userId', '==', 'newbie').get()).docs[0];
  expect(req?.get('status') === 'approved' && req.get('source') === 'email', 'emailed request should be approved immediately');
});

await step('batches: message a member -> lands in the coach inbox and a push record is made', async () => {
  await go(admin.page, '/batches');
  await admin.page.getByText('July Batch').first().click();
  const row = admin.page.locator('.member-row', { hasText: 'Asha Member' });
  await row.waitFor();
  await row.getByRole('button', { name: 'Message' }).click();
  await admin.page.getByPlaceholder('How are things going this week?').fill('Great consistency this week, Asha!');
  await admin.page.getByRole('button', { name: 'Send message' }).click();
  await admin.page.getByText(/Your message to Asha Member was sent/).waitFor();
  const msg = (await adb.collection('coachInboxMessages').where('memberUid', '==', 'memA').get()).docs[0];
  expect(msg && msg.get('fromUid') === 'admin1' && msg.get('programId') === 'progA' && msg.get('senderRole') === 'coach', 'inbox message missing or malformed');
  const note = await eventually(async () => (await adb.collection('notifications').where('userId', '==', 'memA').where('type', '==', 'coach_message').get()).docs[0], 'push trigger should record a notification');
  expect(note.get('failureReason') === 'missing_token', 'no device token in the emulator, so it must be recorded as missing_token');
});

await step('users: add a coach account and enroll a member', async () => {
  await go(admin.page, '/users');
  await admin.page.getByRole('button', { name: 'Add admin/coach' }).click();
  await admin.page.locator('input[type="email"]').last().fill('newcoach@test.dev');
  await admin.page.getByRole('button', { name: 'Create account' }).click();
  await eventually(async () => (await aauth.getUserByEmail('newcoach@test.dev').catch(() => null))?.customClaims?.role === 'coach', 'staff account should exist with the coach role');
  await noBanner(admin.page, 'Users (add staff)');
});

await step('users: enroll an un-enrolled member into a batch', async () => {
  await admin.page.getByPlaceholder(/Search by name/).fill('Walk In');
  const row = admin.page.locator('.user-row, .card', { hasText: 'Walk In' }).first();
  await row.getByRole('button', { name: 'Enroll' }).click();
  await admin.page.locator('.perm-grid select.select').last().selectOption({ label: 'July Batch' });
  await admin.page.getByRole('button', { name: 'Confirm' }).click();
  await admin.page.getByText(/Enrolled in July Batch/).waitFor();
  const u = (await adb.doc('users/walkin').get()).data();
  expect(u.programActive === true && u.activeProgramId === 'progA', `member should be enrolled: ${JSON.stringify(u)}`);
  expect((await adb.doc('programMembers/progA_walkin').get()).exists, 'roster entry should exist');
});

await step('admin members page shows real names (the app stores fullName) and search works', async () => {
  await go(admin.page, '/members');
  await admin.page.getByText('Asha Member').first().waitFor();
  await admin.page.getByText('Ravi Member').first().waitFor();
  await admin.page.getByPlaceholder(/Search by name/).fill('ravi');
  await eventually(async () => (await admin.page.getByText('Asha Member').count()) === 0, 'search should filter by name');
  await noBanner(admin.page, 'Members');
});

await step('consultations: validate, confirm a time, reschedule (reminder replaced), decline with a reason', async () => {
  const inputDate = (daysAhead, hour) => { const d = new Date(Date.now() + daysAhead * 86_400_000); d.setHours(hour, 30, 0, 0); const p = (n) => String(n).padStart(2, '0'); return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}T${p(d.getHours())}:${p(d.getMinutes())}`; };
  await go(admin.page, '/consultations');
  const card = admin.page.locator('.cons-card', { hasText: 'Diet review' });
  await card.waitFor();
  await card.getByText('Asha Member').waitFor();
  await card.getByText(/My fasting sugar is high on weekends/).waitFor();
  await snap(admin.page, 'admin-consultations');
  await card.getByRole('button', { name: 'Confirm a time' }).click();
  await admin.page.getByRole('button', { name: 'Confirm & notify' }).click();
  await admin.page.getByText('Pick the date and time.').waitFor();
  await admin.page.locator('input[type="datetime-local"]').fill(inputDate(-1, 10));
  await admin.page.getByRole('button', { name: 'Confirm & notify' }).click();
  await admin.page.getByText('That time is in the past.').waitFor();
  await admin.page.locator('input[type="datetime-local"]').fill(inputDate(2, 10));
  await admin.page.getByRole('button', { name: 'Confirm & notify' }).click();
  await admin.page.getByText("Add the expert's name.").waitFor();
  await admin.page.getByPlaceholder('Dr. Meera').fill('Dr. Meera');
  await admin.page.getByPlaceholder('https://meet.…').fill('not-a-link');
  await admin.page.getByRole('button', { name: 'Confirm & notify' }).click();
  await admin.page.getByText(/video link/).waitFor();
  await admin.page.getByPlaceholder('https://meet.…').fill('https://meet.example.com/asha');
  await admin.page.getByPlaceholder(/payment link/).fill('₹699 - we will send a payment link');
  await admin.page.getByRole('button', { name: 'Confirm & notify' }).click();
  await admin.page.getByText(/Confirmed\. The member has been notified/).waitFor();
  let doc = (await adb.doc('consultations/consA').get()).data();
  expect(doc.status === 'confirmed' && doc.expertName === 'Dr. Meera' && doc.mode === 'video' && doc.joinLink === 'https://meet.example.com/asha' && doc.confirmedBy === 'admin1', `consultation not confirmed properly: ${JSON.stringify(doc)}`);
  const first = await eventually(async () => { const s = await adb.collection('notifications').where('consultationId', '==', 'consA').get(); return s.size >= 2 ? s.docs.map((d) => d.data()) : null; }, 'confirmation push and reminder should be queued');
  expect(first.some((n) => n.title === 'Consultation confirmed' && n.userId === 'memA'), 'member is told it is confirmed');
  expect(first.filter((n) => n.kind === 'reminder').length === 1, 'one reminder queued');
  // reschedule: the old reminder must be replaced, not duplicated
  await admin.page.getByRole('tab', { name: 'Upcoming' }).click().catch(async () => admin.page.getByRole('button', { name: 'Upcoming' }).click());
  const up = admin.page.locator('.cons-card', { hasText: 'Diet review' });
  await up.getByRole('button', { name: 'Reschedule' }).click();
  await admin.page.locator('input[type="datetime-local"]').fill(inputDate(4, 16));
  await admin.page.getByRole('button', { name: 'Save new time' }).click();
  await admin.page.getByText(/Updated\. The member has been notified of the new time/).waitFor();
  await eventually(async () => { const s = await adb.collection('notifications').where('consultationId', '==', 'consA').where('status', '==', 'scheduled').get(); const r = s.docs.filter((d) => d.get('kind') === 'reminder'); return r.length === 1 && r[0].get('scheduledFor').toMillis() > Date.now() + 3 * 86_400_000 ? true : null; }, 'the reminder should move to the new time (exactly one)');
  expect((await adb.collection('notifications').where('consultationId', '==', 'consA').get()).docs.some((d) => d.get('title') === 'Consultation rescheduled'), 'member is told about the new time');
  // decline the other request with a reason
  await admin.page.getByRole('button', { name: /Requests/ }).click();
  const other = admin.page.locator('.cons-card', { hasText: 'Naturopathy' });
  await other.getByRole('button', { name: 'Decline' }).click();
  await admin.page.getByRole('button', { name: 'Decline & notify' }).click();
  await admin.page.getByText('Tell the member why, and what to do next.').waitFor();
  await admin.page.locator('.modal textarea').fill('Fully booked this week - please request again from Monday.');
  await admin.page.getByRole('button', { name: 'Decline & notify' }).click();
  await admin.page.getByText(/Declined\. The member has been told/).waitFor();
  doc = (await adb.doc('consultations/consB').get()).data();
  expect(doc.status === 'declined' && /request again from Monday/.test(doc.declineReason), 'declined with the reason');
  await eventually(async () => (await adb.collection('notifications').where('consultationId', '==', 'consB').get()).docs.some((d) => /couldn't schedule/i.test(d.get('title')) && /Monday/.test(d.get('body'))), 'decline reason should reach the member');
  await noBanner(admin.page, 'Consultations');
});

await step('member detail and moderation load for admin', async () => {
  await admin.page.goto(`${BASE}/members/memA`);
  await admin.page.getByText('Asha Member').first().waitFor();
  await noBanner(admin.page, 'Member detail');
  await admin.page.goto(`${BASE}/moderation`);
  await eventually(async () => (await admin.page.getByText(/spammy text in A/).count()) > 0 && (await admin.page.getByText(/spammy text in B/).count()) > 0, 'admin should see reports from both batches');
});

await step('no permission errors leaked into the admin browser console', async () => {
  const bad = admin.problems.filter((p) => /permission|insufficient|unauthenticated|FirebaseError/i.test(p));
  expect(bad.length === 0, `console errors: ${bad.slice(0, 3).join(' || ')}`);
});
await admin.context.close();

// ===========================================================================
console.log('\nCoach console (Coach Anita runs July Batch only)');
const coach = await newPersona('coacha@test.dev');

await step('coach navigation hides admin-only areas', async () => {
  const labels = (await navLabels(coach.page)).join('|');
  for (const item of ['Users', 'Consultations', 'Data Requests', 'Error Reports', 'Content']) expect(!labels.includes(item), `coach should not see ${item}`);
  for (const item of ['Members', 'Batches', 'Announcements', 'Programs', 'Moderation']) expect(labels.includes(item), `coach should see ${item}`);
});

await step('coach dashboard works and counts only their batch', async () => {
  await coach.page.waitForLoadState('networkidle').catch(() => {});
  expect((await tileValue(coach.page, 'Open reports')) === 1, 'coach should see only their batch\'s report');
  expect((await tileValue(coach.page, 'Active Care+ members')) === 4 || (await tileValue(coach.page, 'Active Care+ members')) === 3, 'coach roster count should be their batch only (3 after redeems)');
  expect((await tileValue(coach.page, 'Programs')) === 1, 'coach runs one program');
  await snap(coach.page, 'coach-dashboard');
  await noBanner(coach.page, 'Coach dashboard');
});

await step('coach programs page shows only their batch, its code and invite, and no New program button', async () => {
  await go(coach.page, '/programs');
  await coach.page.getByText('July Batch').first().waitFor();
  expect((await coach.page.getByText('August Batch').count()) === 0, 'must not see the other coach\'s batch');
  await coach.page.locator('.code-row', { hasText: accessCode }).waitFor();
  await eventually(async () => (await coach.page.getByText('invitee@test.dev').count()) > 0, 'coach should see invites for their batch');
  expect((await coach.page.getByRole('button', { name: '+ New program' }).count()) === 0, 'coach must not be offered "New program"');
  await noBanner(coach.page, 'Coach programs');
});

await step('coach members page lists only their batch members', async () => {
  await go(coach.page, '/members');
  await coach.page.getByText('Asha Member').first().waitFor();
  expect((await coach.page.getByText('Ravi Member').count()) === 0, 'must not see another batch\'s members');
  await noBanner(coach.page, 'Coach members');
  await snap(coach.page, 'coach-members');
});

await step('coach can open their own member but not another batch\'s member', async () => {
  await coach.page.goto(`${BASE}/members/memA`);
  await coach.page.getByText('Asha Member').first().waitFor();
  await noBanner(coach.page, 'Coach member detail');
  coach.problems.length = 0;
  await coach.page.goto(`${BASE}/members/memB`);
  await coach.page.getByText(/Could not load this member|insufficient|permission/i).first().waitFor({ timeout: 10000 });
  expect((await coach.page.getByText('Ravi Member').count()) === 0, 'Ravi\'s profile must not be readable by Coach Anita');
});

await step('coach moderation shows only their batch\'s report', async () => {
  await coach.page.goto(`${BASE}/moderation`);
  await eventually(async () => (await coach.page.getByText(/spammy text in A/).count()) > 0, 'coach should see their report');
  expect((await coach.page.getByText(/spammy text in B/).count()) === 0, 'coach must not see the other batch\'s report');
  await noBanner(coach.page, 'Coach moderation');
});

await step('coach announcements: limited scope, can send to their batch', async () => {
  await go(coach.page, '/announcements');
  await noBanner(coach.page, 'Coach announcements');
  await coach.page.getByPlaceholder("This week's focus").fill('Coach note');
  await coach.page.getByPlaceholder(/Share what's ahead/).fill('See you at the walk.');
  await coach.page.locator('label.check', { hasText: 'July Batch' }).locator('input').check();
  expect((await coach.page.locator('label.check', { hasText: 'August Batch' }).count()) === 0, 'coach must not be able to target another batch');
  await coach.page.getByRole('button', { name: 'Send announcement' }).click();
  await eventually(async () => (await coach.page.getByText('Coach note').count()) > 0, 'coach should see their own post');
  expect((await coach.page.getByText('E2E schedule update').count()) === 0, 'coach must not see announcements they did not author');
});

await step('coach batches: roster + message works', async () => {
  await go(coach.page, '/batches');
  await coach.page.getByText('July Batch').first().click();
  const row = coach.page.locator('.member-row', { hasText: 'Asha Member' });
  await row.waitFor();
  await row.getByRole('button', { name: 'Message' }).click();
  await coach.page.getByPlaceholder('How are things going this week?').fill('Checking in from Coach Anita');
  await coach.page.getByRole('button', { name: 'Send message' }).click();
  await coach.page.getByText(/Your message to Asha Member was sent/).waitFor();
  expect((await adb.collection('coachInboxMessages').where('fromUid', '==', 'coachA').get()).size === 1, 'coach message should be stored');
});

await step('coach shares a plan with their batch: validation, save, members notified, edit, delete', async () => {
  await go(coach.page, '/resources');
  await coach.page.getByRole('heading', { name: 'Plans & guidance' }).waitFor();
  expect((await coach.page.locator('select.select option', { hasText: 'August Batch' }).count()) === 0, 'coach must only be able to pick their own batch');
  await coach.page.getByRole('button', { name: '+ New resource' }).click();
  await coach.page.getByRole('button', { name: 'Share with batch' }).click();
  await coach.page.getByText('Give it a short title.').waitFor();
  await coach.page.getByPlaceholder(/Week 1 - building your plate/).fill('Gentle morning flow');
  await coach.page.locator('.modal select.select').first().selectOption({ label: 'Yoga' });
  await coach.page.locator('.modal textarea').fill('Cat-cow, child pose, slow breathing. Ten minutes. Stop if dizzy.');
  await coach.page.locator('.modal input[inputmode="url"]').fill('javascript:alert(1)');
  await coach.page.getByRole('button', { name: 'Share with batch' }).click();
  await coach.page.getByText(/must start with http/).waitFor();
  await coach.page.locator('.modal input[inputmode="url"]').fill('https://nirogbhumi.com/yoga');
  await coach.page.getByRole('button', { name: 'Share with batch' }).click();
  await coach.page.getByText(/Shared with July Batch/).waitFor();
  await coach.page.locator('.res-card', { hasText: 'Gentle morning flow' }).waitFor();
  const stored = (await adb.collection('programResources').where('programId', '==', 'progA').get()).docs[0];
  expect(stored.get('createdBy') === 'coachA' && stored.get('category') === 'yoga' && stored.get('link') === 'https://nirogbhumi.com/yoga', `stored resource is wrong: ${JSON.stringify(stored.data())}`);
  const pushes = await eventually(async () => { const s = await adb.collection('notifications').where('type', '==', 'program_resource').get(); return s.size >= 3 ? s : null; }, 'the batch should be queued a notification');
  expect(pushes.docs.every((d) => d.get('status') === 'scheduled' && !['coachA'].includes(d.get('userId'))), 'pushes are scheduled and never go to the author');
  await snap(coach.page, 'coach-resources');
  await coach.page.locator('.res-card', { hasText: 'Gentle morning flow' }).getByRole('button', { name: 'Edit' }).click();
  await coach.page.locator('.modal input.input').nth(1).fill('Gentle morning flow (v2)');
  await coach.page.getByRole('button', { name: 'Save changes' }).click();
  await coach.page.getByText('Gentle morning flow (v2)').waitFor();
  await coach.page.locator('.res-card', { hasText: 'v2' }).getByRole('button', { name: 'Delete' }).click();
  await coach.page.getByRole('button', { name: 'Remove' }).click();
  await eventually(async () => (await adb.collection('programResources').where('programId', '==', 'progA').get()).empty, 'resource should be deleted');
  await noBanner(coach.page, 'Plans & guidance');
});

await step('coach calendar loads', async () => {
  await go(coach.page, '/calendar');
  await noBanner(coach.page, 'Coach calendar');
});
await coach.context.close();

// ---------------------------------------------------------------------------
await browser.close();
server.close();
const failed = results.filter((r) => !r.ok);
console.log(`\n${results.length - failed.length}/${results.length} end-to-end checks passed`);
process.exit(failed.length ? 1 : 0);
