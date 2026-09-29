// Small shared helpers for the end-to-end suites (step runner, polling, admin SDK).
import { createRequire } from 'node:module';

export const require = createRequire(new URL('../firebase/functions/package.json', import.meta.url));
process.env.GCLOUD_PROJECT = 'demo-nirog-bhumi';

export const results = [];
export async function step(name, fn) {
  try {
    await fn();
    results.push({ name, ok: true });
    console.log(`  ✓ ${name}`);
  } catch (error) {
    results.push({ name, ok: false, error });
    console.log(`  ✗ ${name}\n      ${String(error?.message ?? error).split('\n')[0]}`);
  }
}
export const expect = (cond, message) => { if (!cond) throw new Error(message); };
export const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
export async function eventually(fn, message, timeout = 20000) {
  const start = Date.now();
  let last;
  while (Date.now() - start < timeout) {
    try { const v = await fn(); if (v) return v; } catch (e) { last = e; }
    await sleep(300);
  }
  throw new Error(`${message}${last ? ` (${last.message})` : ''}`);
}
/** Runs fn and returns the thrown error's code, or null if it succeeded. */
export async function errorCode(fn) {
  try { await fn(); return null; } catch (e) { return e?.code ?? String(e?.message ?? e); }
}
export function summarize(label) {
  const failed = results.filter((r) => !r.ok);
  console.log(`\n${results.length - failed.length}/${results.length} ${label} checks passed`);
  return failed.length ? 1 : 0;
}
