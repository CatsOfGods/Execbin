# Handover Notes

Read this first. It summarizes what the buyer receives, what has and hasn't been verified, and
what the buyer must set up with their own accounts. Details are in the linked documents.

## 1. What is included

| Path | Contents |
|---|---|
| `app/` | Android app source (Kotlin), resources, ProGuard rules, unit tests (`app/src/test`), instrumented test stub |
| `build.gradle.kts`, `settings.gradle.kts`, `gradle.properties`, `gradle/`, `gradlew`, `gradlew.bat` | Gradle build and wrapper (Gradle 9.1.0, AGP 9.0.1) |
| `firestore.rules`, `firebase.json`, `.firebaserc` | Firestore security rules and Firebase CLI config (project id placeholder) |
| `firestore-tests/` | Firestore rules tests for the Firebase Emulator |
| `functions/` | Cloud Function (TypeScript) for AI triage of abuse reports, with unit tests |
| `firebase-wipe/` | Developer script that deletes **all** Firestore data (Admin SDK; for test projects) |
| `config/detekt/`, `.github/workflows/ci.yml` | Static analysis config and GitHub Actions CI |
| `keystore.properties.example`, `functions/.env.example` | Templates for settings the buyer provides |
| `README.md` | Setup, build, deploy and test instructions |
| `SECURITY.md` | Security design, fixes, verification status, known limitations |
| `docs/FIRESTORE_RULES_REVIEW.md`, `docs/LINT_REVIEW.md`, `docs/DEVICE_TEST_CHECKLIST.md` | Rules review, lint review, real-device test plan |
| `THIRD_PARTY_NOTICES.md` | Third-party licenses |

**Not included (by design):** `google-services.json`, signing keystore and passwords,
`local.properties`, Firebase service-account keys, API keys, build outputs, `node_modules`,
Git history, IDE settings. The previous owner's Firebase project, Google Play listing,
signing key, domain and service accounts are **not** part of this package.

## 2. What has been tested — and what hasn't

Verified on the final archive (extracted into a clean folder, 2026-10-25), using a placeholder
`google-services.json` because no real Firebase project is included:

| Check | Command | Result |
|---|---|---|
| Android unit tests | `./gradlew :app:testDebugUnitTest` | ✅ 27/27 passed |
| Debug build | `./gradlew :app:assembleDebug` | ✅ APK built |
| Release bundle (unsigned, no keystore) | `./gradlew :app:bundleRelease` | ✅ AAB built |
| Android lint | `./gradlew :app:lintDebug` | ✅ 0 errors, 508 warnings (reviewed in `docs/LINT_REVIEW.md`) |
| Cloud Functions type check + unit tests | `cd functions && npm install && npm test` | ✅ compiled, 7/7 passed |
| Firestore rules (emulator) | see README §7 | ✅ 29/29 passed |

**Not tested:**

- The app on real phones (UI flows, Android version differences, notifications, photo
  permissions) → `docs/DEVICE_TEST_CHECKLIST.md`
- Against a real Firebase project and real data; the Firestore rules have never been deployed
- Play Integrity / App Check with a Play-distributed build; App Check enforcement
- The Cloud Function deployed with a real Anthropic API key
- Instrumented (on-device) tests — only the template stub exists
- No independent security audit or penetration test has been performed

## 3. Known limitations (summary)

Full list in `SECURITY.md`. Most important:

- Forward secrecy is limited to the 30-day key-rotation window (no Double Ratchet).
- No key backup: reinstalling or losing a phone makes server-stored chat history unreadable.
- The PIN / 2-step lock and device trust are enforced in the app, not by the server.
- No server sends push notifications; chat notifications only arrive while the app process is alive.
- Several dependencies are old or pre-release (Room alpha, `security-crypto` alpha, SQLCipher 4.6.1,
  Firebase BoM 33) — upgrade plan in `docs/LINT_REVIEW.md`.
- Android 14 "selected photos" access is untested and may not work with the in-app gallery.
- Usernames are not guaranteed unique.
- UI translations for 10 languages exist; recently added strings were not reviewed by native speakers.

## 4. What the buyer must set up (own accounts and credentials)

1. Firebase project on the Blaze plan → `app/google-services.json`, `.firebaserc` project id.
2. New package name (replace `com.example.execbin`; Google Play rejects `com.example.*`).
3. Release keystore + `keystore.properties` (from `keystore.properties.example`).
4. Firebase Authentication (Email/Password), Firestore, Cloud Messaging, Crashlytics (optional).
5. App Check with Play Integrity (+ SHA-256 of the app-signing key, debug tokens).
6. Optional AI triage: Anthropic account, `firebase functions:secrets:set ANTHROPIC_API_KEY`.
7. Support/privacy contact: replace `privacy@your-domain.com` in `PrivacyPolicy.kt`.

Step-by-step instructions: `README.md`.

## 5. Before deploying to Firebase

- Deploy to a **test** Firebase project first; run the emulator rules tests and the device checklist.
- If an existing project is used: back up its current rules, check existing documents
  (`docs/FIRESTORE_RULES_REVIEW.md` §5), deploy **only** the rules
  (`firebase deploy --only firestore:rules`), and watch for `PERMISSION_DENIED` errors.
- Deploy the Cloud Function only after setting the secret; review the daily AI limits
  (`functions/.env.example`).
- Enable App Check **enforcement** only after verified traffic shows up in the App Check metrics.
- `firebase-wipe/` deletes everything — never point it at production.

## 6. Before publishing on Google Play

- New package name, own signing key (Play App Signing), version code/name.
- Run `docs/DEVICE_TEST_CHECKLIST.md` on real devices (including Android 6–8 and 14+).
- Add an open-source licenses screen or notice (SQLCipher's license requires it) — see
  `THIRD_PARTY_NOTICES.md`.
- Publish a privacy policy URL; complete the Data-safety form (email (hashed), device id
  `ANDROID_ID`, messages (end-to-end encrypted content), photos/audio, crash data, push token).
- Decide on background notifications (no server-side push exists).
- Consider the dependency upgrades listed in `docs/LINT_REVIEW.md`.
- Update or remove the certificate pins in `network_security_config.xml` before 2027-06-01.

## 7. Rights and ownership — points for the seller to confirm in the sale agreement

These are not legal advice; they are facts about the package that affect what can be transferred.

- **Third-party code** stays under its own licenses (`THIRD_PARTY_NOTICES.md`); Google's
  proprietary SDKs (Firebase Auth, Play services, Play Integrity) require the buyer to accept
  Google's terms. Nothing in the package grants rights in those components.
- **Online services and accounts** (Firebase project, Google Play developer account and listing,
  Anthropic account, any website or domain used for the app) are not included in the source package.
  Transferring any of them is a separate step.
- **App name "ExecBin":** no trademark search has been done.
- **Launcher icon** (`app/src/main/res/mipmap-*`, `ic_launcher-playstore.png`): its origin
  (own design, generated, or licensed) should be confirmed by the seller.
- **AI-assisted development:** parts of the code and documentation were written or modified with
  AI coding assistants, including the security review changes in October 2026. Depending on the
  jurisdiction, purely AI-generated material may not be protected by copyright in the same way
  as human-written code; the seller should disclose this to the buyer.
- **No open-source license file** is included on purpose: the buyer's rights come from the sale
  agreement, not from a public license.

---

## Verification record

All results in §2 come from running the commands above on this archive's contents after
extracting it into an empty folder (Windows 11, Android Studio JDK 21, Node 24, Firebase CLI 15),
with a placeholder `google-services.json` and an Android Studio-style `local.properties`.
The archive was also scanned for credentials and personal data (none found). The seller keeps
the SHA-256 checksum of the delivered ZIP in `ExecBin-for-sale.zip.sha256` next to the archive.
