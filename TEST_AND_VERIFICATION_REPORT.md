# Test and Verification Report

## How to read this report

- **Independently re-verified** — re-run on 2026-10-25 against the delivered
  `FINAL_EXECBIN_SOURCE.zip` (extracted into an empty folder) while preparing this package; logs are
  in `Evidence/`.
- **Reported earlier** — results from earlier runs during development, listed for completeness.
- **Not performed** — no evidence exists; do not assume it.

Automated tests and builds show that the code compiles and that the tested units behave as
specified. They do **not** show the app works on real devices, against a real Firebase project,
or that it is free of vulnerabilities.

## 1. Environment

| Item | Value |
|---|---|
| OS | Windows 11 Home (seller's development PC) |
| JDK | OpenJDK 21.0.8 (Android Studio bundled) |
| Gradle / AGP / Kotlin | 9.1.0 (wrapper) / 9.0.1 / 2.0.21 |
| Node.js / npm / Firebase CLI | 24.14.1 / 11.11.0 / 15.14.0 |
| Buyer-provided files | Replaced for the build only: placeholder `app/google-services.json` (same as CI), Android Studio-style `local.properties`. Neither is in the archive |
| Network note | The PC's antivirus intercepts HTTPS; dependency downloads used a temporary trust store containing its root certificate. No effect on the build output |

## 2. Archive identity

| Check | Result | Evidence |
|---|---|---|
| SHA-256 of delivered ZIP equals the seller's final archive and its recorded checksum | ✅ `de5a4309f6597e6768f0db137db1f96fb65d30656ff519dcdd21fdda9621d68b` | `SHA256SUMS.txt`, `Evidence/secret_scan_and_integrity_report.txt` |
| Integrity (`unzip -t` CRC check, .NET full read of all entries) | ✅ no errors | same |
| File count | ✅ 81 files (127 entries incl. folders) | `Evidence/archive_listing.txt` |
| Required files present (35 checked: source, Gradle wrapper, rules, functions, tests, docs, templates) | ✅ all present | `Evidence/verification_run_summary.txt` |
| Forbidden files (keys, keystores, credentials, build output, logs, `node_modules`) | ✅ none | same |

An older, superseded archive also exists on the seller's PC (`Documents\ExecBin-for-sale.zip`,
68 files, SHA-256 `f1e5f0cc…6c05`, created before the security fixes). It is **not** the
delivered version and is not included here.

## 3. Results

| Check | Command (in the extracted `ExecBin/` folder) | Previously reported | Independently re-verified 2026-10-25 | Evidence |
|---|---|---|---|---|
| Android unit tests | `./gradlew :app:testDebugUnitTest` | 27/27 | ✅ **27/27 passed** (26 crypto + 1 example), 0 failures | `Evidence/android_unit_test_results.txt` |
| Debug build | `./gradlew :app:assembleDebug` | passing | ✅ APK produced | `Evidence/gradle_build_test_lint.log` |
| Release build | `./gradlew :app:bundleRelease` | passing | ✅ AAB produced (unsigned — no keystore by design) | same |
| Android lint | `./gradlew :app:lintDebug` | 0 errors, 508 warnings | ✅ **0 errors, 508 warnings** | `Evidence/lint-results-debug.txt`; review in `ExecBin/docs/LINT_REVIEW.md` |
| Cloud Functions | `cd functions && npm install && npm test` | 7/7 | ✅ TypeScript compiled, **7/7 passed** | `Evidence/functions_npm_test.log` |
| Firestore Rules | `firebase emulators:exec --only firestore --project demo-execbin "npm --prefix firestore-tests test"` | 29/29 | ✅ **29/29 passed** (local emulator, demo project) | `Evidence/firestore_rules_emulator_test.log` |
| Secret / personal-data scan | targeted + broad pattern scans | no secrets | ✅ no secrets; 29 broad-scan matches reviewed, all false positives | `Evidence/secret_scan_and_integrity_report.txt` |
| Dependency license inventory | `gradlew :app:dependencies` + POM lookup; npm lockfiles | — | ✅ 158 Android + 337 npm entries; flags listed | `THIRD_PARTY_NOTICES.md`, `Evidence/*dependencies*` |

All Gradle tasks ran in one invocation: `BUILD SUCCESSFUL in 2m 39s`.

## 4. Not performed (no evidence)

| Item | Status |
|---|---|
| Firebase deployment (rules, function) | **Not performed** |
| App publication (Google Play or elsewhere) | **Not performed** |
| Real-device testing after the latest changes | **Not completed** — use `ExecBin/docs/DEVICE_TEST_CHECKLIST.md` |
| Signed release build | Not performed (no keystore in the package) |
| Instrumented / UI tests | Not performed (only a template stub exists) |
| Cloud Function against the live Anthropic API | Not performed |
| App Check / Play Integrity with a Play build | Not performed |
| GitHub Actions CI run | Not performed |
| Independent security audit or penetration test | **Has not occurred** |
| Performance, accessibility, battery testing | Not performed |

## 5. Limitations of the evidence

- The build used a placeholder Firebase configuration; the app was compiled, not run.
- Unit tests cover the cryptography module only; most UI and Firebase logic in `MainActivity` has
  no automated tests.
- Rules tests run in the local emulator; real production data shapes may differ.
- Pattern-based secret scanning cannot prove the absence of secrets.
- Logs in `Evidence/` were sanitized (local folder paths and the user name replaced with
  `<EXTRACTED_ARCHIVE>` / `<USER_HOME>`); content is otherwise unchanged.
