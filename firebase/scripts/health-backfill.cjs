#!/usr/bin/env node
'use strict';
// Usage (from the repo root):
//   Emulator (safe):  FIRESTORE_EMULATOR_HOST=127.0.0.1:8080 node firebase/scripts/health-backfill.cjs --project demo-nirog-bhumi
//   Staging dry run:  node firebase/scripts/health-backfill.cjs --project <staging-id>
//   Apply:            add --apply            (only after reading the dry-run report)
//   Also merge legacy per-interval step documents into daily totals and delete them: add --delete-legacy-steps
//   Canary:           --limit 200 --collections weightLogs,sleepLogs
// Needs Application Default Credentials for a real project. Production additionally needs --allow-production.
const { initializeApp } = require(require.resolve('firebase-admin/app', { paths: [require('node:path').join(__dirname, '..', 'functions')] }));
const { getFirestore, FieldValue } = require(require.resolve('firebase-admin/firestore', { paths: [require('node:path').join(__dirname, '..', 'functions')] }));
const { runBackfill, assertSafeTarget } = require('./lib/healthBackfill.cjs');

const args = process.argv.slice(2);
const flag = (name) => args.includes(`--${name}`);
const value = (name) => { const i = args.indexOf(`--${name}`); return i >= 0 ? args[i + 1] : undefined; };

(async () => {
  const project = value('project');
  const target = assertSafeTarget({ project, emulatorHost: process.env.FIRESTORE_EMULATOR_HOST, allowProduction: flag('allow-production') });
  initializeApp({ projectId: project ?? 'demo-nirog-bhumi' });
  const report = await runBackfill({
    db: getFirestore(), FieldValue,
    apply: flag('apply'),
    collections: value('collections')?.split(',').map((s) => s.trim()).filter(Boolean),
    limit: value('limit') ? Number(value('limit')) : Infinity,
    deleteLegacySteps: flag('delete-legacy-steps'),
    log: (m) => console.error(m),
  });
  console.log(JSON.stringify({ target, project, ...report }, null, 2));
  if (!flag('apply')) console.error('\nDry run only: nothing was written. Re-run with --apply to write.');
})().catch((e) => { console.error(String(e.message ?? e)); process.exit(1); });
