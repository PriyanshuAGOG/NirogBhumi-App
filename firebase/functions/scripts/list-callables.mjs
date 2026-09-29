// Prints the name of every callable (onCall) Cloud Function exported by the
// compiled bundle, one per line. The deploy workflow feeds this list to
// `gcloud functions add-invoker-policy-binding` so each callable's Cloud Run
// service allows public invocation - see the comment on that step in
// .github/workflows/deploy-firebase.yml for why this can't be left to
// firebase-tools. Run after `npm run build`.
import { createRequire } from 'node:module';

process.env.GCLOUD_PROJECT ??= 'list-callables';
const require = createRequire(import.meta.url);
const bundle = require('../lib/index.js');

const callables = Object.entries(bundle)
  .filter(([, fn]) => fn && fn.__endpoint && 'callableTrigger' in fn.__endpoint)
  .map(([name]) => name)
  .sort();

if (!callables.length) {
  console.error('No callable functions found in lib/index.js - was it built?');
  process.exit(1);
}
console.log(callables.join('\n'));
