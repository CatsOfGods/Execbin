"""
EXECBIN — FULL WIPE: Delete ALL Auth users + ALL Firestore data

Usage:
  1. Download service account key from Firebase Console:
     Project Settings > Service Accounts > Generate New Private Key
  2. Save as "serviceAccountKey.json" in this folder
  3. Run: py wipe_all.py
"""

import json
import os
import sys

# Fix Windows console encoding
sys.stdout.reconfigure(encoding='utf-8', errors='replace')

try:
    import firebase_admin
    from firebase_admin import credentials, auth, firestore
except ImportError:
    print("ERROR: firebase-admin not installed. Run: py -m pip install firebase-admin")
    sys.exit(1)

# Find service account key
KEY_PATH = os.path.join(os.path.dirname(os.path.abspath(__file__)), "serviceAccountKey.json")

if not os.path.exists(KEY_PATH):
    print("\n" + "=" * 55)
    print("  ERROR: serviceAccountKey.json not found!")
    print("=" * 55)
    print()
    print("  To get it:")
    print("  1. Open this URL in your browser:")
    print("     https://console.firebase.google.com/project/YOUR_FIREBASE_PROJECT_ID/settings/serviceaccounts/adminsdk")
    print("  2. Click 'Generate New Private Key'")
    print("  3. Save the file as:")
    print(f"     {KEY_PATH}")
    print("  4. Run this script again: py wipe_all.py")
    print()
    sys.exit(1)

# Initialize Firebase Admin
cred = credentials.Certificate(KEY_PATH)
firebase_admin.initialize_app(cred, {"projectId": "YOUR_FIREBASE_PROJECT_ID"})
db = firestore.client()


def delete_all_auth_users():
    """Delete every single Firebase Auth user."""
    print("\n🔥 DELETING ALL AUTH USERS...")
    total = 0
    page = auth.list_users()

    while page:
        for user in page.users:
            try:
                auth.delete_user(user.uid)
                total += 1
                print(f"  ✓ Deleted user: {user.email or user.uid}")
            except Exception as e:
                print(f"  ⚠ Failed to delete {user.uid}: {e}")

        page = page.get_next_page()

    print(f"✅ Deleted {total} auth users\n")
    return total


def delete_collection(coll_ref, batch_size=400):
    """Recursively delete all docs (and subcollections) in a collection."""
    total = 0

    while True:
        docs = list(coll_ref.limit(batch_size).stream())
        if not docs:
            break

        for doc in docs:
            # Delete subcollections first
            for subcoll in doc.reference.collections():
                sub_count = delete_collection(subcoll, batch_size)
                total += sub_count

            doc.reference.delete()
            total += 1

        print(f"  ✓ {coll_ref.id}: deleted {total} docs so far...")

    return total


def delete_all_firestore():
    """Delete all known collections."""
    collections = [
        "users",
        "publicProfiles",
        "devices",
        "deviceAccounts",
        "privateChats",       # has messages subcollection
        "globalChat",
        "reports",
        # Honeypots
        "admin_config",
        "api_keys",
        "payments",
        "payment_methods",
        "auth_tokens",
        "user_credentials",
        "sessions",
        "encryption_keys",
        "user_emails",
        "backups",
    ]

    print("🔥 DELETING ALL FIRESTORE DATA...")
    grand_total = 0

    for col_name in collections:
        try:
            coll_ref = db.collection(col_name)
            # Quick check if collection has docs
            first = list(coll_ref.limit(1).stream())
            if not first:
                print(f"  ○ {col_name}: empty (skipped)")
                continue

            count = delete_collection(coll_ref)
            grand_total += count
            print(f"✅ {col_name}: {count} documents deleted")
        except Exception as e:
            print(f"  ○ {col_name}: {e}")

    print(f"\n✅ Total Firestore documents deleted: {grand_total}")


def main():
    print()
    print("╔════════════════════════════════════════╗")
    print("║   EXECBIN — FULL DATABASE WIPE          ║")
    print("║   Project: YOUR_FIREBASE_PROJECT_ID                  ║")
    print("╚════════════════════════════════════════╝")

    # Step 1: Wipe Auth
    delete_all_auth_users()

    # Step 2: Wipe Firestore
    delete_all_firestore()

    print()
    print("═" * 42)
    print("✅ WIPE COMPLETE — Everything is clean!")
    print("   All auth accounts: DELETED")
    print("   All Firestore data: DELETED")
    print("   Ready for fresh tester rollout")
    print("═" * 42)
    print()


if __name__ == "__main__":
    main()
