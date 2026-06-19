import { initializeApp } from 'firebase-admin/app';
import { FieldValue, Timestamp, getFirestore } from 'firebase-admin/firestore';
import { createHash } from 'node:crypto';

async function main() {
if (process.env.ALLOW_NIROG_SEED !== 'yes') throw new Error('Set ALLOW_NIROG_SEED=yes to confirm seeding');
initializeApp(); const db = getFirestore(); const batch = db.batch(); const now = FieldValue.serverTimestamp();

const products = [
  { id: 'steel-jal-neti-pot', name: 'Steel Jal Neti Pot', category: 'jal_neti', description: 'A durable neti pot for an expert-taught nasal cleansing routine.', price: 699, stock: 50, usageInstructions: 'Use only after learning the correct technique with sterile or appropriately prepared water.', safetyNote: 'Do not use when nasal passages are fully blocked, bleeding, or after recent nasal surgery without medical advice.' },
  { id: 'acupressure-tool-set', name: 'Acupressure Tool Set', category: 'acupressure', description: 'Simple wooden tools for assigned relaxation routines.', price: 650, stock: 40, usageInstructions: 'Use gentle pressure only as shown in your routine.', safetyNote: 'Stop if painful; avoid broken skin or areas with reduced sensation.' },
  { id: 'yoga-support-kit', name: 'Yoga Support Kit', category: 'yoga', description: 'Strap and support blocks for comfortable home practice.', price: 1299, stock: 35, usageInstructions: 'Follow your assigned routine and remain within a comfortable range.', safetyNote: 'Stop if dizzy, breathless, or unwell.' },
  { id: 'metabolic-routine-kit', name: 'Metabolic Routine Kit', category: 'kit', description: 'A practical collection of tools used across Nirog Bhumi lifestyle programs.', price: 2499, stock: 20, usageInstructions: 'Use each item only according to its enclosed instructions or expert plan.', safetyNote: 'This kit does not replace medical treatment.' }
];
products.forEach(product => batch.set(db.doc(`products/${product.id}`), { productId: product.id, slug: product.id, images: [], compareAtPrice: null, active: true, relatedContentIds: [], createdAt: now, updatedAt: now, ...product }, { merge: true }));

const articles = [
  ['walking-after-meals', 'Why walking after meals helps sugar', 'diabetes', 'A short, comfortable walk after a meal may support post-meal glucose patterns.', 'Start gently with 10 to 15 minutes after a meal if your doctor says walking is safe for you. Track the pattern rather than judging one reading.'],
  ['sleep-and-fasting-sugar', 'Sleep and fasting sugar', 'sleep', 'Sleep timing and duration can be useful context for morning readings.', 'Log sleep and fasting sugar consistently for several weeks. Look for repeated patterns and discuss concerning readings with your clinician.'],
  ['balanced-indian-plate', 'Building a balanced Indian plate', 'food', 'Use familiar foods with thoughtful portions and combinations.', 'A practical plate can include non-starchy vegetables, a protein source, and an appropriate portion of grains or roti. Individual needs vary.'],
  ['bp-measurement-routine', 'A calmer blood pressure routine', 'bp', 'Consistent measurement conditions make trends easier to interpret.', 'Sit quietly, support your arm, use a suitable cuff, and measure at a similar time. Seek medical advice for concerning readings or symptoms.'],
  ['gentle-breathing', 'Two minutes of gentle breathing', 'yoga', 'A brief comfortable breathing pause can support a calmer daily rhythm.', 'Sit comfortably and breathe without strain or breath holding. Stop if dizzy or uncomfortable.']
];
articles.forEach(([id,title,category,summary,body]) => batch.set(db.doc(`contentItems/${id}`), { contentId: id, title, slug: id, category, summary, body, imageUrl: null, readTimeMinutes: 2, language: 'en', status: 'published', targetSegments: ['type_2','prediabetes'], relatedProductIds: [], relatedProgramIds: [], createdBy: 'seed', publishedAt: now, createdAt: now, updatedAt: now }, { merge: true }));

batch.set(db.doc('programs/metabolic-rhythm-24-week'), { programId: 'metabolic-rhythm-24-week', name: '24-Week Metabolic Rhythm Program', description: 'Structured food, movement, sleep, yoga, and expert accountability support.', durationWeeks: 24, active: true, createdAt: now, updatedAt: now }, { merge: true });

const times = ['10:30 AM','2:15 PM','4:30 PM'];
for (let day = 1; day <= 14; day++) {
  const date = new Date(); date.setDate(date.getDate() + day); if ([0,6].includes(date.getDay())) continue;
  const dateKey = date.toISOString().slice(0,10); const dateLabel = date.toLocaleDateString('en-IN', { weekday: 'short', day: 'numeric', month: 'short', timeZone: 'Asia/Kolkata' });
  times.forEach((timeLabel, index) => batch.set(db.doc(`consultationSlots/${dateKey}_${index}`), { expertId: 'unassigned', dateKey, dateLabel, timeLabel, active: true, booked: false, createdAt: now, updatedAt: now }, { merge: true }));
}

const programCode = process.env.NIROG_PROGRAM_CODE?.trim().toUpperCase();
if (programCode) {
  const hash = createHash('sha256').update(programCode).digest('hex');
  batch.set(db.doc(`programCodes/${hash}`), { programId: 'metabolic-rhythm-24-week', durationWeeks: 24, active: true, uses: 0, maxUses: Number(process.env.NIROG_PROGRAM_CODE_MAX_USES ?? 100), expiresAt: Timestamp.fromMillis(Date.now() + 180 * 86400000), createdAt: now, updatedAt: now });
}

await batch.commit(); console.log('Nirog Bhumi catalog, education, program, and consultation slots seeded.');
}

main().catch(error => { console.error(error); process.exitCode = 1; });
