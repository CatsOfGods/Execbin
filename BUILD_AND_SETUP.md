# Build and Setup

Instructions for the source in `FINAL_EXECBIN_SOURCE.zip`. The same content in shorter form is in
`ExecBin/README.md` inside the archive. Steps marked **(not verified here)** could not be executed
on the seller's machine because they need the buyer's own Firebase / Google accounts.

## 1. Prerequisites

| Tool | Version | Status |
|---|---|---|
| Android Studio | Any version that supports AGP 9.0.1 (the build used its bundled JDK 21) | Build verified with JDK 21.0.8 |
| JDK | 17 or newer for Gradle/AGP; 21 recommended (also used by the Firestore emulator) | Verified with 21 |
| Gradle | 9.1.0 via the wrapper (`gradlew`, `gradlew.bat`) — no separate install needed | Verified |
| Android Gradle Plugin / Kotlin | 9.0.1 / 2.0.21 (`build.gradle.kts`) | Verified |
| Android SDK | Platform 36 (`compileSdk = 36`), build-tools installed by Android Studio | Verified |
| Node.js / npm | Node 20+ (functions declare `"node": "20"`); verification ran with Node 24.14.1, npm 11.11 | Verified (Node 24) |
| Firebase CLI | `npm i -g firebase-tools`; verification used 15.14.0 | Verified (emulator only) |

CI (`.github/workflows/ci.yml`) uses JDK 17 on Ubuntu; it was **not run** as part of this
verification (no GitHub repository).

## 2. Open and sync the project

1. Unzip `FINAL_EXECBIN_SOURCE.zip`; open the `ExecBin` folder in Android Studio.
2. Android Studio creates `local.properties` with your SDK path automatically. (If you write it
   by hand on Windows, escape the colon: `sdk.dir=C\:/path/to/Android/Sdk`.)
3. Add `app/google-services.json` (§3) **before** the first build — the Google Services Gradle
   plugin fails the build without it.
4. Gradle sync downloads all dependencies from Google Maven, Maven Central and the Gradle Plugin
   Portal.

## 3. Firebase configuration (buyer's own project) — (not verified here)

1. Create a Firebase project and upgrade it to the **Blaze** plan (needed for Cloud Functions and secrets).
2. Choose your own package name and replace `com.example.execbin` in `app/build.gradle.kts`
   (`namespace` and `applicationId`). Moving the Kotlin package directory is optional.
3. Firebase Console → Add Android app with that package name → download `google-services.json`
   → put it in `app/`.
4. Authentication → enable **Email/Password**.
5. Firestore → create the database.
6. App Check → register the app with **Play Integrity** and add your app-signing SHA-256. For debug
   builds, copy the debug token from Logcat (`DebugAppCheckProvider`) into App Check → Manage
   debug tokens. Enforce App Check for Firestore only after verified traffic appears.
7. Put your project id into `.firebaserc` (replace `YOUR_FIREBASE_PROJECT_ID`).

**Smoke build without Firebase:** for a compile-only check you can use the placeholder
`google-services.json` that `.github/workflows/ci.yml` writes. The resulting app cannot connect to
any backend. This is how the seller's verification build was run.

## 4. Build commands (verified)

```bash
./gradlew :app:testDebugUnitTest     # unit tests
./gradlew :app:assembleDebug         # debug APK  -> app/build/outputs/apk/debug/
./gradlew :app:bundleRelease         # release AAB -> app/build/outputs/bundle/release/
./gradlew :app:lintDebug             # lint report -> app/build/reports/lint-results-debug.html
```

Release signing: copy `keystore.properties.example` to `keystore.properties`, fill in your
keystore path, alias and passwords (never commit it). Without that file the release bundle is
built **unsigned** (this is what the verification produced).

## 5. Cloud Functions (optional AI report triage)

```bash
cd functions
npm install
npm test                # TypeScript compile + 7 unit tests (verified)
cd ..
firebase functions:secrets:set ANTHROPIC_API_KEY      # (not verified here)
firebase deploy --only functions                       # (not verified here)
```

Settings (environment variables, optional): copy `functions/.env.example` to `functions/.env`.

| Variable | Default | Meaning |
|---|---|---|
| `MODERATION_DAILY_LIMIT` | 500 | AI reviews per day, all users |
| `MODERATION_PER_REPORTER_LIMIT` | 10 | AI reviews per reporter per day |
| `ANTHROPIC_API_KEY` | — | **Secret**, set with `firebase functions:secrets:set`; never in `.env` or code |

## 6. Firestore Security Rules

Test in the emulator (verified, 29 tests; uses a `demo-` project id, touches no real project):

```bash
cd firestore-tests && npm install && cd ..
firebase emulators:exec --only firestore --project demo-execbin "npm --prefix firestore-tests test"
```

Deploy **(not verified here — no deployment was performed)**, first to a test project:

```bash
firebase deploy --only firestore:rules --project <your-test-project>
```

`firebase deploy` without `--only` would deploy rules and functions together. No composite indexes
are required.

## 7. Secrets and configuration files

| File | In archive? | Purpose |
|---|---|---|
| `app/google-services.json` | No — buyer creates | Firebase app config |
| `keystore.properties` + `.jks` | No — buyer creates (`keystore.properties.example` included) | Release signing |
| `local.properties` | No — Android Studio creates | SDK path |
| `.firebaserc` | Yes, placeholder project id | Firebase CLI default project |
| `functions/.env` | No (`.env.example` included) | Optional limits |
| `firebase-wipe/serviceAccountKey.json` | No — only for the data-wipe dev script | Admin credentials; never commit |

All of these are listed in `.gitignore`.

## 8. Troubleshooting (based on issues seen during verification)

| Symptom | Cause / fix |
|---|---|
| `File google-services.json is missing` | Add your own file (§3) or the CI placeholder for a compile-only build |
| Lint error `PropertyEscape` in `local.properties` | Hand-written Windows path; escape `:` (`C\:/...`) or let Android Studio generate the file |
| `PKIX path building failed` while downloading dependencies | An antivirus or proxy intercepts HTTPS. Add its root certificate to the JDK trust store (or pass `-Djavax.net.ssl.trustStore=...`). For npm/Firebase CLI use `NODE_EXTRA_CA_CERTS` |
| `node --test` finds no tests | Run `npm test` (it uses the correct glob `test/*.test.js`) |
| Gradle complains about version | Use the wrapper (`./gradlew`), which pins Gradle 9.1.0 |
| App shows "Device verification failed" | App Check token missing: register the debug token (debug builds) or install from Play (release) |
