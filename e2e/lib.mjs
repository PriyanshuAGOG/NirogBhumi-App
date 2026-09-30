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

/** Reads a ZIP (as produced by the export) into { name: Buffer }. Only what the export writes: stored or deflated entries, no ZIP64. */
export function readZip(buffer) {
  const { inflateRawSync, crc32 } = require('node:zlib');
  let end = buffer.length - 22;
  while (end >= 0 && buffer.readUInt32LE(end) !== 0x06054b50) end--;
  if (end < 0) throw new Error('not a zip file');
  const count = buffer.readUInt16LE(end + 10);
  let pos = buffer.readUInt32LE(end + 16);
  const files = {};
  for (let i = 0; i < count; i++) {
    if (buffer.readUInt32LE(pos) !== 0x02014b50) throw new Error('bad central directory');
    const method = buffer.readUInt16LE(pos + 10), crc = buffer.readUInt32LE(pos + 16), size = buffer.readUInt32LE(pos + 20);
    const nameLen = buffer.readUInt16LE(pos + 28), extraLen = buffer.readUInt16LE(pos + 30), commentLen = buffer.readUInt16LE(pos + 32);
    const local = buffer.readUInt32LE(pos + 42);
    const name = buffer.toString('utf8', pos + 46, pos + 46 + nameLen);
    const dataStart = local + 30 + buffer.readUInt16LE(local + 26) + buffer.readUInt16LE(local + 28);
    const body = buffer.subarray(dataStart, dataStart + size);
    const data = method === 8 ? inflateRawSync(body) : Buffer.from(body);
    if ((crc32(data) >>> 0) !== crc) throw new Error(`crc mismatch for ${name}`);
    files[name] = data;
    pos += 46 + nameLen + extraLen + commentLen;
  }
  return files;
}
