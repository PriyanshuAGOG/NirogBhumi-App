// Shared harness for the Cloud Functions unit tests. These run against the
// local Firestore emulator only (never production): the admin SDK is pointed
// at it via FIRESTORE_EMULATOR_HOST, and FCM is replaced with a recording stub
// so no real notification can ever be sent from a test.
process.env.GCLOUD_PROJECT = 'demo-nirog-bhumi';
process.env.FIRESTORE_EMULATOR_HOST ??= '127.0.0.1:8080';

const path = require('node:path');

const sent = [];
const messagingStub = {
  getMessaging: () => ({
    send: async message => {
      if (message.token === 'bad-token') throw new Error('messaging/registration-token-not-registered');
      sent.push(message);
      return 'stub-message-id';
    },
  }),
};
const messagingPath = require.resolve('firebase-admin/messaging');
require.cache[messagingPath] = { id: messagingPath, filename: messagingPath, loaded: true, exports: messagingStub };

const fns = require(path.join(__dirname, '..', 'lib', 'index.js'));
const { getFirestore } = require('firebase-admin/firestore');
const db = getFirestore();

async function resetFirestore() {
  const host = process.env.FIRESTORE_EMULATOR_HOST;
  const res = await fetch(`http://${host}/emulator/v1/projects/${process.env.GCLOUD_PROJECT}/databases/(default)/documents`, { method: 'DELETE' });
  if (!res.ok) throw new Error(`Could not reset the Firestore emulator (${res.status}) - is it running?`);
  sent.length = 0;
}

/** Invoke an onCall function as `uid` with the given custom-claim role. */
function call(fn, data, uid = 'user1', role) {
  return fn.run({ auth: uid ? { uid, token: role ? { role } : {} } : undefined, data, rawRequest: {}, acceptsStreaming: false });
}

/** Invoke a Firestore onDocumentCreated trigger with a fake event. */
function trigger(fn, id, data) {
  return fn.run({ data: { id, data: () => data }, params: { messageId: id } });
}

async function rejectsWithCode(promise, code) {
  try { await promise; } catch (error) {
    if (error.code !== code) throw new Error(`expected error code "${code}" but got "${error.code}": ${error.message}`);
    return error;
  }
  throw new Error(`expected error code "${code}" but the call succeeded`);
}

module.exports = { fns, db, sent, resetFirestore, call, trigger, rejectsWithCode };
