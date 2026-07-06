// Seeds a demo 6-month Care+ program with a full, realistic session
// schedule so the Program Calendar (month-grid + day detail) can actually
// be exercised end to end instead of tested against an empty calendar.
//
// Reuses the existing member-enrollment path deliberately: this script only
// creates the program + its programEvents. To actually see it as a member,
// redeem the printed code from inside the app (Care+ > enter program code),
// the same flow every real member uses - no direct profile mutation here.
//
// Run from CI (WIF/ADC credentials) or locally with GOOGLE_APPLICATION_CREDENTIALS
// pointed at a real service account key. Safe to re-run: it deletes and
// recreates this program's own events each time rather than accumulating
// duplicates.
import admin from "firebase-admin";

const PROGRAM_CODE = "DEMO6MONTH";
const PROGRAM_NAME = "Demo 6-Month Wellness Journey";
const DURATION_DAYS = 182; // 26 weeks

function requireEnv(name) {
  const value = process.env[name];
  if (!value) throw new Error(`Missing required env var: ${name}`);
  return value;
}

function addDays(base, days) {
  const d = new Date(base);
  d.setDate(d.getDate() + days);
  return d;
}

function atTime(date, hour, minute) {
  const d = new Date(date);
  d.setHours(hour, minute, 0, 0);
  return d;
}

// One realistic week of session types, repeated for 26 weeks with light
// content variation so the calendar doesn't look like it's just tiling one
// identical week - a coach glancing at month 4 should see the same rhythm
// but not literally copy-pasted titles.
function eventsForWeek(weekStart, weekIndex) {
  const events = [];
  const monday = addDays(weekStart, 0);
  const wednesday = addDays(weekStart, 2);
  const friday = addDays(weekStart, 4);

  events.push({
    title: `Live Check-in Session — Week ${weekIndex + 1}`,
    type: "live",
    description: "Group check-in on how the week is going: readings, energy, sleep, and any blockers. Cameras optional.",
    location: "Online via Zoom",
    link: "https://zoom.us/j/demo-nirog-bhumi",
    startsAt: atTime(monday, 18, 0),
    endsAt: atTime(monday, 18, 45),
  });

  events.push({
    title: weekIndex % 2 === 0 ? "Guided Group Walk" : "Guided Group Walk + Stretch",
    type: "walk",
    description: weekIndex % 2 === 0
      ? "30-minute brisk walk at your own pace - log it afterward from the Activity tab."
      : "30-minute walk followed by a short guided stretch sequence to close out.",
    location: "Any safe route near you (park, treadmill, or building loop)",
    startsAt: atTime(wednesday, 7, 0),
    endsAt: atTime(wednesday, 7, 45),
  });

  if (weekIndex % 2 === 1) {
    events.push({
      title: "Lab Test Reminder",
      type: "lab",
      description: "Fortnightly fasting glucose + HbA1c check. Upload the report to Health File once you have it.",
      location: "Any certified diagnostic lab near you",
      bring: "Fasting sample (8-10 hrs), any prior reports for comparison",
      startsAt: atTime(monday, 8, 0),
      endsAt: atTime(monday, 8, 30),
    });
  }

  events.push({
    title: "Ask-the-Coach Q&A",
    type: "qa",
    description: "Open floor for questions on diet, medication timing, or anything from this week's check-in.",
    location: "Online via Zoom",
    link: "https://zoom.us/j/demo-nirog-bhumi-qa",
    startsAt: atTime(friday, 19, 0),
    endsAt: atTime(friday, 19, 30),
  });

  return events;
}

async function main() {
  const projectId = requireEnv("FIREBASE_PROJECT_ID");
  admin.initializeApp({ projectId });
  const db = admin.firestore();

  const existing = await db.collection("programs").where("code", "==", PROGRAM_CODE).limit(1).get();
  const programRef = existing.empty ? db.collection("programs").doc() : existing.docs[0].ref;
  const programId = programRef.id;

  await programRef.set({
    name: PROGRAM_NAME,
    code: PROGRAM_CODE,
    durationDays: DURATION_DAYS,
    isDemo: true,
    updatedAt: admin.firestore.FieldValue.serverTimestamp(),
    ...(existing.empty ? { createdAt: admin.firestore.FieldValue.serverTimestamp() } : {}),
  }, { merge: true });
  console.log(`Program ready: ${programId} (code: ${PROGRAM_CODE})`);

  // Clear this program's own prior events before reseeding, so re-running
  // this script for a fresh test pass doesn't pile up duplicates.
  const priorEvents = await db.collection("programEvents").where("programId", "==", programId).get();
  for (let offset = 0; offset < priorEvents.docs.length; offset += 400) {
    const batch = db.batch();
    priorEvents.docs.slice(offset, offset + 400).forEach((doc) => batch.delete(doc.ref));
    await batch.commit();
  }
  console.log(`Cleared ${priorEvents.size} prior demo events`);

  // Program starts today so "Day N of 182" and the calendar's today-marker
  // both read naturally regardless of when this is run.
  const today = new Date();
  today.setHours(0, 0, 0, 0);
  const programStart = today;

  const totalEvents = [];
  for (let week = 0; week < 26; week++) {
    const weekStart = addDays(programStart, week * 7);
    totalEvents.push(...eventsForWeek(weekStart, week));
  }

  for (let offset = 0; offset < totalEvents.length; offset += 400) {
    const batch = db.batch();
    totalEvents.slice(offset, offset + 400).forEach((event) => {
      const ref = db.collection("programEvents").doc();
      batch.set(ref, {
        programId,
        title: event.title,
        type: event.type,
        description: event.description,
        location: event.location,
        link: event.link ?? null,
        bring: event.bring ?? null,
        startsAt: admin.firestore.Timestamp.fromDate(event.startsAt),
        endsAt: admin.firestore.Timestamp.fromDate(event.endsAt),
        createdBy: "seed-demo-program",
        updatedAt: admin.firestore.FieldValue.serverTimestamp(),
      });
    });
    await batch.commit();
  }

  console.log(`Seeded ${totalEvents.length} events across 26 weeks for program ${programId}`);
  console.log(`Redeem code "${PROGRAM_CODE}" from inside the app (Care+ > enter program code) to see it as a member.`);
}

main().catch((error) => {
  console.error(error);
  process.exit(1);
});
