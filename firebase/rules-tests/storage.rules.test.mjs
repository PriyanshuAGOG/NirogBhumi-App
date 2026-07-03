// Storage security rules unit tests, run only against the local emulator.
// Covers program-chat-photos, the one Storage path that does a live
// cross-service Firestore read (programMember()) rather than a plain
// owner-uid check - exactly the kind of rule that could silently be wrong
// with no way to notice without a real test against a real emulator.
import { readFileSync } from 'node:fs';
import { before, after, beforeEach, describe, it } from 'node:test';
import { initializeTestEnvironment, assertSucceeds, assertFails } from '@firebase/rules-unit-testing';
import { doc, setDoc } from 'firebase/firestore';
import { ref, uploadBytes, getBytes } from 'firebase/storage';

let testEnv;

before(async () => {
  testEnv = await initializeTestEnvironment({
    projectId: 'demo-nirog-bhumi',
    firestore: {
      rules: readFileSync(new URL('../firestore.rules', import.meta.url), 'utf8'),
      host: 'localhost',
      port: 8080,
    },
    storage: {
      rules: readFileSync(new URL('../storage.rules', import.meta.url), 'utf8'),
      host: 'localhost',
      port: 9199,
    },
  });
});

after(async () => {
  await testEnv.cleanup();
});

beforeEach(async () => {
  await testEnv.clearFirestore();
  await testEnv.clearStorage();
});

const memberStorage = (uid) => testEnv.authenticatedContext(uid).storage();
// Not a real decodable PNG - the rules only ever inspect the upload's
// declared contentType/size metadata, never the byte content itself.
const tinyBytes = new Uint8Array([137, 80, 78, 71]);

describe('program-chat-photos storage rules', () => {
  beforeEach(async () => {
    await testEnv.withSecurityRulesDisabled(async (ctx) => {
      const db = ctx.firestore();
      await setDoc(doc(db, 'users/mem1'), { userId: 'mem1', activeProgramId: 'progA', programActive: true });
      await setDoc(doc(db, 'users/mem2'), { userId: 'mem2', activeProgramId: 'progB', programActive: true });
      await setDoc(doc(db, 'users/mem3'), { userId: 'mem3', activeProgramId: 'progA', programActive: true });
    });
  });

  it('lets a program member upload a photo under their own uid in their own program', async () => {
    const storageRef = ref(memberStorage('mem1'), 'program-chat-photos/progA/mem1/photo1.jpg');
    await assertSucceeds(uploadBytes(storageRef, tinyBytes, { contentType: 'image/jpeg' }));
  });

  it("denies uploading under someone else's uid, even within the same program", async () => {
    const storageRef = ref(memberStorage('mem1'), 'program-chat-photos/progA/mem3/photo1.jpg');
    await assertFails(uploadBytes(storageRef, tinyBytes, { contentType: 'image/jpeg' }));
  });

  it('denies a member uploading into a program they are not an active member of', async () => {
    const storageRef = ref(memberStorage('mem2'), 'program-chat-photos/progA/mem2/photo1.jpg');
    await assertFails(uploadBytes(storageRef, tinyBytes, { contentType: 'image/jpeg' }));
  });

  it('lets a batchmate read a photo another member uploaded to the same program', async () => {
    await testEnv.withSecurityRulesDisabled(async (ctx) => {
      const seedRef = ref(ctx.storage(), 'program-chat-photos/progA/mem1/photo1.jpg');
      await uploadBytes(seedRef, tinyBytes, { contentType: 'image/jpeg' });
    });
    const readerRef = ref(memberStorage('mem3'), 'program-chat-photos/progA/mem1/photo1.jpg');
    await assertSucceeds(getBytes(readerRef));
  });

  it('denies a member in a different program from reading the photo', async () => {
    await testEnv.withSecurityRulesDisabled(async (ctx) => {
      const seedRef = ref(ctx.storage(), 'program-chat-photos/progA/mem1/photo1.jpg');
      await uploadBytes(seedRef, tinyBytes, { contentType: 'image/jpeg' });
    });
    const readerRef = ref(memberStorage('mem2'), 'program-chat-photos/progA/mem1/photo1.jpg');
    await assertFails(getBytes(readerRef));
  });
});
