/**
 * NUCLEAR WIPE — Deletes ALL Firebase Auth users + ALL Firestore data
 *
 * Usage:
 *   1. Download your service account key from Firebase Console:
 *      Project Settings → Service Accounts → Generate New Private Key
 *   2. Save it as "serviceAccountKey.json" in this folder
 *   3. Run: node wipe-all.mjs
 */

import { initializeApp, cert } from 'firebase-admin/app';
import { getAuth } from 'firebase-admin/auth';
import { getFirestore } from 'firebase-admin/firestore';
import { readFileSync } from 'fs';

// Load service account
let serviceAccount;
try {
    serviceAccount = JSON.parse(readFileSync('./serviceAccountKey.json', 'utf8'));
} catch (e) {
    console.error('\n❌ ERROR: serviceAccountKey.json not found!');
    console.error('\nTo get it:');
    console.error('  1. Go to https://console.firebase.google.com/project/YOUR_FIREBASE_PROJECT_ID/settings/serviceaccounts/adminsdk');
    console.error('  2. Click "Generate New Private Key"');
    console.error('  3. Save the downloaded file as "serviceAccountKey.json" in this folder');
    console.error('  4. Run this script again: node wipe-all.mjs\n');
    process.exit(1);
}

initializeApp({
    credential: cert(serviceAccount),
    projectId: 'YOUR_FIREBASE_PROJECT_ID'
});

const auth = getAuth();
const db = getFirestore();

// ==========================================
// DELETE ALL AUTH USERS
// ==========================================
async function deleteAllAuthUsers() {
    console.log('\n🔥 DELETING ALL AUTH USERS...');
    let totalDeleted = 0;
    let nextPageToken;

    do {
        const listResult = await auth.listUsers(1000, nextPageToken);
        if (listResult.users.length === 0) break;

        const uids = listResult.users.map(u => u.uid);
        const result = await auth.deleteUsers(uids);
        totalDeleted += result.successCount;

        if (result.failureCount > 0) {
            console.log(`  ⚠ ${result.failureCount} failed to delete`);
            result.errors.forEach(e => console.log(`    - ${e.error.message}`));
        }

        console.log(`  ✓ Deleted ${totalDeleted} users so far...`);
        nextPageToken = listResult.pageToken;
    } while (nextPageToken);

    console.log(`✅ Deleted ${totalDeleted} total auth users`);
}

// ==========================================
// DELETE ALL DOCUMENTS IN A COLLECTION
// ==========================================
async function deleteCollection(collectionPath) {
    const collRef = db.collection(collectionPath);
    let totalDeleted = 0;

    while (true) {
        const snapshot = await collRef.limit(400).get();
        if (snapshot.empty) break;

        const batch = db.batch();
        for (const doc of snapshot.docs) {
            // Delete subcollections first
            const subcollections = await doc.ref.listCollections();
            for (const subcol of subcollections) {
                await deleteCollection(`${collectionPath}/${doc.id}/${subcol.id}`);
            }
            batch.delete(doc.ref);
        }
        await batch.commit();
        totalDeleted += snapshot.size;
        console.log(`  ✓ ${collectionPath}: deleted ${totalDeleted} docs...`);
    }

    return totalDeleted;
}

// ==========================================
// MAIN
// ==========================================
async function main() {
    console.log('╔══════════════════════════════════════╗');
    console.log('║   EXECBIN — FULL DATABASE WIPE       ║');
    console.log('║   Project: YOUR_FIREBASE_PROJECT_ID               ║');
    console.log('╚══════════════════════════════════════╝');

    // 1. Delete all auth users
    await deleteAllAuthUsers();

    // 2. Delete all Firestore collections
    const collections = [
        'users',
        'publicProfiles',
        'devices',
        'deviceAccounts',
        'privateChats',    // includes messages subcollection
        'globalChat',
        'reports',
        // Honeypot collections (may not exist, that's fine)
        'admin_config',
        'api_keys',
        'payments',
        'payment_methods',
        'auth_tokens',
        'user_credentials',
        'sessions',
        'encryption_keys',
        'user_emails',
        'backups'
    ];

    console.log('\n🔥 DELETING ALL FIRESTORE DATA...');
    for (const col of collections) {
        try {
            const count = await deleteCollection(col);
            if (count > 0) console.log(`✅ ${col}: ${count} documents deleted`);
            else console.log(`  ○ ${col}: empty (skipped)`);
        } catch (e) {
            console.log(`  ○ ${col}: ${e.message}`);
        }
    }

    console.log('\n══════════════════════════════════════');
    console.log('✅ WIPE COMPLETE — Everything is clean!');
    console.log('   All auth accounts: DELETED');
    console.log('   All Firestore data: DELETED');
    console.log('   Ready for fresh tester rollout');
    console.log('══════════════════════════════════════\n');
    process.exit(0);
}

main().catch(e => {
    console.error('❌ Fatal error:', e.message);
    process.exit(1);
});
