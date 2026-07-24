#!/usr/bin/env node
/**
 * One-time backfill: creates `inviteCodes/{code} -> { groupId }` for every group
 * that already exists.
 *
 * Joining used to resolve a code with a query over `groups`. That is a list
 * operation, and the security rules now deny it (allowing it would let anyone
 * enumerate every group in the app). Joining reads `inviteCodes/{code}` instead —
 * so without this backfill, codes for groups created before the change stop
 * working.
 *
 * Run once, after deploying the rules:
 *
 *   npm install firebase-admin
 *   GOOGLE_APPLICATION_CREDENTIALS=/path/to/serviceAccountKey.json \
 *     node scripts/backfill-invite-codes.js --dry-run
 *   GOOGLE_APPLICATION_CREDENTIALS=... node scripts/backfill-invite-codes.js
 *
 * The Admin SDK bypasses security rules, which is why this runs server-side.
 */

const admin = require('firebase-admin');

const DRY_RUN = process.argv.includes('--dry-run');

admin.initializeApp({ projectId: 'bachat-khata-db36c' });
const db = admin.firestore();

async function main() {
  const groups = await db.collection('groups').get();
  console.log(`Found ${groups.size} group(s).`);

  let created = 0;
  let skipped = 0;
  const collisions = [];

  for (const group of groups.docs) {
    const code = group.get('inviteCode');
    if (!code) {
      console.warn(`  ! ${group.id} has no inviteCode — skipping`);
      skipped++;
      continue;
    }

    const codeRef = db.collection('inviteCodes').doc(String(code));
    const existing = await codeRef.get();

    if (existing.exists) {
      const owner = existing.get('groupId');
      if (owner === group.id) {
        skipped++; // already backfilled, nothing to do
      } else {
        // Two groups issued the same code before codes were uniqueness-checked.
        // Left alone deliberately: reassigning would silently redirect joiners.
        collisions.push({ code, keeps: owner, loses: group.id });
      }
      continue;
    }

    if (!DRY_RUN) {
      await codeRef.set({
        groupId: group.id,
        createdAt: group.get('createdAt') || admin.firestore.Timestamp.now(),
        backfilled: true,
      });
    }
    created++;
    console.log(`  + ${code} -> ${group.id}${DRY_RUN ? ' (dry run)' : ''}`);
  }

  console.log(`\nCreated: ${created}   Skipped: ${skipped}   Collisions: ${collisions.length}`);

  if (collisions.length) {
    console.log('\nDuplicate codes — resolve by hand, each needs a new code issued:');
    for (const c of collisions) {
      console.log(`  code ${c.code}: kept by ${c.keeps}, NOT mapped for ${c.loses}`);
    }
    process.exitCode = 1;
  }
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
