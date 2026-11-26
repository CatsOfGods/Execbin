# Buyer Handover — Technical Transfer

This document covers the technical side of taking over ExecBin. Legal and commercial points are in
`IP_AND_TRANSFER_CHECKLIST.md`. Nothing in this package contains real credentials.

## 1. What is transferred in this package

`FINAL_EXECBIN_SOURCE.zip` (81 files, one top-level `ExecBin/` folder):

| Path in archive | Contents |
|---|---|
| `app/` | Android app: Kotlin source (`com.example.execbin`), resources, ProGuard/R8 rules, unit tests, instrumented-test stub |
| `build.gradle.kts`, `settings.gradle.kts`, `gradle.properties`, `gradle/`, `gradlew*` | Gradle build + wrapper (Gradle 9.1.0) |
| `firestore.rules`, `firebase.json`, `.firebaserc` | Security rules and Firebase CLI configuration (project id placeholder) |
| `firestore-tests/` | Emulator tests for the rules (29) |
| `functions/` | Cloud Function source (TypeScript) + unit tests (7) + lockfile |
| `firebase-wipe/` | Developer script that deletes **all** Firestore data — test projects only |
| `config/detekt/`, `.github/workflows/ci.yml` | Static analysis and CI configuration |
| `keystore.properties.example`, `functions/.env.example` | Templates for buyer-provided settings |
| `README.md`, `HANDOVER.md`, `SECURITY.md`, `THIRD_PARTY_NOTICES.md`, `docs/` | Developer documentation, rules review, lint review, device test checklist |

The archive contains no Git history, no build outputs, no `node_modules`, no IDE settings.

## 2. What is NOT transferred automatically

Source code alone does not move any account or service. Each of the following is separate and,
if part of the deal, must be transferred by its own procedure:

| Item | Notes |
|---|---|
| Seller's Firebase / Google Cloud project | Contains any existing users, data, Auth accounts, billing. Not included; the buyer sets up their own project unless a project transfer is agreed separately |
| Existing user data | Not included in the package. Transferring personal data has privacy-law implications |
| Google Play developer account / store listing | Not included. A Play app transfer is a separate Google process |
| App signing key / upload key | Not included and must not be sent inside the package |
| Package name / application id | The code uses the placeholder `com.example.execbin`; the buyer chooses their own |
| Domain, website, privacy-policy URL, support email | Not included; the in-app policy uses `privacy@your-domain.com` |
| Anthropic API account and key | Not included |
| Crashlytics history, App Check config, FCM settings | Belong to the seller's Firebase project; not included |

## 3. Required tools

See `BUILD_AND_SETUP.md` §1. Verified combination: JDK 21, Gradle 9.1.0 (wrapper), AGP 9.0.1,
Kotlin 2.0.21, Android SDK 36, Node 24 (functions target Node 20), Firebase CLI 15.14.

## 4. Configuration the buyer must provide

1. Own Firebase project (Blaze plan) → `app/google-services.json`, project id in `.firebaserc`.
2. New application id / namespace in `app/build.gradle.kts`.
3. Release keystore + `keystore.properties`.
4. Firebase Console: Email/Password auth, Firestore, App Check (Play Integrity + SHA-256 + debug tokens).
5. Optional: `ANTHROPIC_API_KEY` secret, `functions/.env` limits.
6. Replace `privacy@your-domain.com` in `app/src/main/java/com/example/execbin/PrivacyPolicy.kt`.

## 5. Firebase project ownership and setup order

1. Create a **test** Firebase project first.
2. Run the emulator rules tests, then deploy rules (and optionally the function) to the test project.
3. Run `ExecBin/docs/DEVICE_TEST_CHECKLIST.md` on real phones against the test project.
4. Create the production project; repeat configuration; enable App Check enforcement after
   verified traffic appears.
5. Budget alerts in Google Cloud are recommended (Firestore reads/writes, Cloud Functions,
   Anthropic usage are pay-per-use).

## 6. Identifiers, signing, release and Google Play

- **Application id:** must be changed from `com.example.execbin` (Google Play rejects `com.example.*`).
  Once published, the application id can never change for that listing.
- **Signing:** create a new upload key; use Play App Signing. The release build type already
  enables R8 minification and resource shrinking.
- **Version:** `versionCode 1` / `versionName "1.0"` — increase for each release.
- **Before publishing:** privacy policy URL, Data-safety form (email (hashed), device id
  `ANDROID_ID`, messages, photos/audio, crash data, push token), open-source licenses notice
  (SQLCipher requires it — see `THIRD_PARTY_NOTICES.md`), content rating, and a decision about
  user-generated-content moderation (global chat is public; reports exist but there is no
  moderator dashboard).
- **Permissions declared:** Internet, network state, read external storage (ignored by Android 13+),
  read media images, post notifications, record audio.

## 7. Handover acceptance checklist (buyer)

- [ ] Received `FINAL_EXECBIN_SOURCE.zip`; SHA-256 matches `SHA256SUMS.txt` *as communicated by the seller through a separate trusted channel*
- [ ] Archive opens; 81 files; `ExecBin/README.md` and `ExecBin/HANDOVER.md` present
- [ ] Project opens and syncs in Android Studio
- [ ] `./gradlew :app:testDebugUnitTest :app:assembleDebug` succeeds with your `google-services.json`
- [ ] `cd functions && npm install && npm test` succeeds
- [ ] Firestore rules emulator tests succeed
- [ ] App runs on at least one real phone against your test Firebase project (sign-up, private chat, photo)
- [ ] Confirmed no credentials were received inside the package (none should be)
- [ ] Read `KNOWN_LIMITATIONS.md` and `SECURITY.md`
- [ ] Agreed in writing which accounts/assets (if any) transfer beyond the source code
- [ ] IP / licensing items in `IP_AND_TRANSFER_CHECKLIST.md` resolved to your satisfaction
