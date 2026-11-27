# ExecBin

End-to-end encrypted Android messenger (Kotlin) with a Firebase backend:
Authentication, Cloud Firestore, Cloud Messaging, App Check (Play Integrity),
Crashlytics, and a Cloud Function that triages abuse reports with Claude.

Features: 1:1 chats encrypted on-device (ECDH P-256 + HKDF + AES-256-GCM, per-message
ephemeral keys), encrypted photos and voice messages, public global chat, reactions,
replies, edits, read receipts, typing indicators, safety-number / QR identity
verification, PIN 2-step lock with brute-force lockout, biometric unlock, device
management, encrypted local cache (SQLCipher), 10 UI languages.

Documentation:

| Document | Contents |
|---|---|
| [HANDOVER.md](HANDOVER.md) | **Start here:** what is included, what was tested, buyer setup, rights and licenses |
| [SECURITY.md](SECURITY.md) | Security design, fixes from the latest review, known limitations |
| [docs/FIRESTORE_RULES_REVIEW.md](docs/FIRESTORE_RULES_REVIEW.md) | Every Firestore operation the app makes vs. the rules; who can read what |
| [docs/LINT_REVIEW.md](docs/LINT_REVIEW.md) | All lint warnings, split into potentially important vs. harmless |
| [docs/DEVICE_TEST_CHECKLIST.md](docs/DEVICE_TEST_CHECKLIST.md) | Manual test plan for real phones |
| [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) | Third-party licenses (incl. required SQLCipher notice) |

**Status:** automated tests and builds pass (see §7). The app has **not** yet been tested on
real devices against a real Firebase project, and the new Firestore rules have not been
deployed. Don't treat it as production-ready until the device checklist and the manual setup
below are done.

## Manual setup checklist

Everything that can't be done from the code:

- [ ] Create a Firebase project (Blaze plan) and add `app/google-services.json` (§1)
- [ ] Change the package name from `com.example.execbin` (§1)
- [ ] Create a release keystore and `keystore.properties` (§1)
- [ ] Enable Authentication (email/password), Firestore, Cloud Messaging (§4)
- [ ] App Check: register Play Integrity + SHA-256, register debug tokens, **then enforce** for Firestore (§4)
- [ ] Link the Cloud project in Play Console → App integrity (§4)
- [ ] Deploy Firestore rules — test project first, back up existing rules (§5, `docs/FIRESTORE_RULES_REVIEW.md` §5)
- [ ] Optional: set `ANTHROPIC_API_KEY` secret and deploy the Cloud Function (§6)
- [ ] Replace `privacy@your-domain.com` in `PrivacyPolicy.kt`; publish a privacy policy URL
- [ ] Play Console Data-safety form: declares device id (`ANDROID_ID`), email (hashed), messages (E2E), crash data (Crashlytics), photos/audio (user content)
- [ ] Decide on background notifications: no server currently sends push messages (`docs/DEVICE_TEST_CHECKLIST.md` N2)
- [ ] Update the certificate pins in `network_security_config.xml` before 2027-06-01
- [ ] Run `docs/DEVICE_TEST_CHECKLIST.md`

---

## 1. What is not included

All credentials and account-specific configuration were removed. You need your own:

| Item | Where it goes | How to get it |
|---|---|---|
| `google-services.json` | `app/google-services.json` | Firebase Console → Project settings → Add Android app (use your package name) → download |
| Release keystore + `keystore.properties` | project root (see `keystore.properties.example`) | Android Studio → Build → Generate Signed App Bundle → create new keystore |
| Firebase project id | `.firebaserc` (`YOUR_FIREBASE_PROJECT_ID`) | Firebase Console |
| Anthropic API key (optional, for AI report triage) | Firebase secret `ANTHROPIC_API_KEY` | console.anthropic.com |
| Privacy policy contact | `PrivacyPolicy.kt` (`privacy@your-domain.com`) | your support address |
| Service account key (admin wipe script only) | `firebase-wipe/serviceAccountKey.json` | Firebase Console → Service accounts → Generate new private key. **Never commit it.** |

Before publishing, change `applicationId` / `namespace` (`com.example.execbin`) in
`app/build.gradle.kts` — Google Play rejects `com.example.*` — and register that package
name in Firebase.

## 2. Requirements

- Android Studio (bundled JDK 21), Android SDK 36
- Node.js 20+ and the Firebase CLI (`npm i -g firebase-tools`) for rules, tests and functions
- A Java runtime (JDK 21 recommended) on `PATH` for the Firestore emulator
- Firebase project on the **Blaze** plan (required for Cloud Functions and secrets)

## 3. Build the app

```bash
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

The build needs `app/google-services.json` (the Google Services plugin fails without it). To
compile without a Firebase project — e.g. to check the source builds — use the placeholder
file that `.github/workflows/ci.yml` writes; the resulting app can't connect to any backend.

Debug builds don't need a keystore. Release builds are signed only when
`keystore.properties` exists:

```bash
./gradlew :app:bundleRelease
```

## 4. Firebase setup (console)

1. **Authentication** → enable **Email/Password** (the app signs in with email + password and requires a verified email). Google Sign-In is not implemented (`play-services-auth` is declared but unused).
2. **Firestore** → create the database (production mode).
3. **Cloud Messaging** → enabled by default.
4. **App Check** → register the Android app with the **Play Integrity** provider (add your
   app-signing SHA-256 from Play Console → App integrity). For debug builds, run the app once,
   copy the debug token from Logcat (`DebugAppCheckProvider`) and add it under
   App Check → Apps → Manage debug tokens.
   After the app is live and the App Check metrics show verified traffic, click
   **Enforce** for Cloud Firestore. Until you enforce, App Check only reports — it does not block.
5. **Google Cloud Console** → make sure the *Play Integrity API* is enabled for the project
   (App Check usually enables it), and link the Cloud project in Play Console → App integrity.
6. **Crashlytics** → enable if you want crash reports.

## 5. Deploy security rules

Rules live in `firestore.rules` and are referenced from `firebase.json`.
**Test them first** (see §7), then deploy only the rules:

```bash
firebase deploy --only firestore:rules
```

> `firebase deploy` without `--only` deploys rules *and* functions. If the project already
> has rules in production, compare them before deploying.

No composite indexes are required; `firebase.json` intentionally doesn't manage indexes.

## 6. Deploy the Cloud Function (AI report triage, optional)

```bash
firebase functions:secrets:set ANTHROPIC_API_KEY
cd functions && npm install && npm test && cd ..
firebase deploy --only functions
```

Cost controls (defaults shown; override by copying `functions/.env.example` to `functions/.env`):
`MODERATION_DAILY_LIMIT=500` AI reviews per day in total, `MODERATION_PER_REPORTER_LIMIT=10` per
reporter per day.
Each review is one short Claude Haiku call (`max_tokens: 300`, input capped at 1,000 characters).
Reports over budget, without text, or with an unparseable answer are marked
`needs_manual_review` — review them in the Firestore `reports` collection.

## 7. Tests

| Suite | Command |
|---|---|
| Android unit tests (crypto, regression tests) | `./gradlew :app:testDebugUnitTest` |
| Android lint | `./gradlew :app:lintDebug` |
| Cloud Functions type check + unit tests | `cd functions && npm install && npm test` |
| Firestore rules (emulator, 29 tests) | `cd firestore-tests && npm install && cd .. && firebase emulators:exec --only firestore --project demo-execbin "npm --prefix firestore-tests test"` |

The rules tests use a `demo-` project id, so they never touch a real Firebase project.

## 8. Project layout

```
app/                     Android app (MainActivity.kt holds most UI + logic)
  crypto/CryptoManager.kt   all E2E primitives, unit-tested
  database/                 SQLCipher-encrypted Room cache
functions/               Cloud Function: AI triage of abuse reports
firestore.rules          Firestore security rules
firestore-tests/         Emulator tests for the rules
firebase-wipe/           Admin script that deletes ALL Firestore data (dev only)
config/detekt/           Static analysis config
.github/workflows/ci.yml CI: unit tests + debug build (+ lint/detekt/ktlint, non-blocking)
```

CI expects a `GOOGLE_SERVICES_JSON_B64` repository secret (base64 of your
`google-services.json`); without it a placeholder is used and only the build is checked.
