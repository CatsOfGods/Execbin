# Known Limitations and Outstanding Items

Status as of 2026-10-25. Each item states whether it is a confirmed property of the code, an
unverified behaviour, or an open business/legal question.

## 1. Verification gaps (nothing below has been done)

| Item | Status |
|---|---|
| Real-device testing after the latest changes | **Not done.** Checklist: `ExecBin/docs/DEVICE_TEST_CHECKLIST.md` (~50 items, two phones, Android 6–8 and 14+) |
| Deployment of Firestore rules / Cloud Function to any real Firebase project | **Not done** (verified in the emulator only) |
| App Check / Play Integrity with a Play-distributed build; App Check enforcement | **Not done** |
| Cloud Function against the live Anthropic API | **Not done** |
| Instrumented (on-device) automated tests | Only the template stub exists |
| CI workflow on GitHub | Not run (no repository) |
| Independent security audit / penetration test | **Not done** |
| Accessibility review, performance/load testing, battery usage | Not done |
| Upgrade from a previously installed app version (data migration paths) | Implemented in code, not tested on a device |

## 2. Functional limitations (confirmed in code)

- **No server-side push notifications.** The FCM token is stored, but nothing sends pushes;
  notifications appear only while the app process is alive.
- **Blocking is local only:** hides blocked users' messages on that device; not synced or enforced by the server.
- **No moderator dashboard:** reports and AI verdicts are written to Firestore only; no automatic enforcement.
- **No key backup / multi-device key sync:** reinstalling or a new phone cannot read older
  server-stored private messages.
- **Media stored inline** in Firestore as base64 (≈ 900 KB per message limit; photos are
  compressed to 800 px). No Firebase Storage.
- **No in-app QR scanner** for identity verification (QR is displayed; comparison is visual).
- **Usernames not guaranteed unique** (client-side check only).
- **Google Sign-In not implemented** although `play-services-auth` is a dependency.
- **Android 14 "selected photos" access** may not work with the in-app gallery (unverified).
- **Global chat is public and not end-to-end encrypted** (by design).
- **Translations:** 10 languages; strings added in October 2026 not reviewed by native speakers.

## 3. Security limitations (confirmed in code — see `SECURITY.md`)

- Forward secrecy limited to the 30-day key-rotation window (no Double Ratchet).
- PIN, device trust, session timeout and email verification are enforced by the app, not by
  Firestore rules.
- Anti-spam limits are client-side; no server-side rate limiting.
- Any signed-in user can list all public profiles.
- Release builds keep `Log` calls.
- Certificate pins expire 2027-06-01; pinning is not active on Android 6.0.
- Hooking detection deletes local keys (possible data loss on false positives).

## 4. Technical debt / dependencies

- Pre-release or old libraries: Room `2.7.0-alpha12`, `security-crypto 1.1.0-alpha06`
  (library deprecated upstream), SQLCipher 4.6.1, Firebase BoM 33.7.0 (upgrading to 34+ requires
  code changes for removed KTX modules). Plan in `ExecBin/docs/LINT_REVIEW.md`.
- `MainActivity.kt` is a single 9,222-line file (UI + logic); maintainability cost for new features.
- 508 lint warnings remain (reviewed; 9 flagged as potentially important).
- `applicationId` is the placeholder `com.example.execbin`.

## 5. Environmental dependencies

- Requires the buyer's own Firebase project (Blaze plan for functions), Google Play developer
  account, and optionally an Anthropic API account. Costs are usage-based and not estimated here.
- Builds need internet access to Google Maven / Maven Central / npm on first sync.

## 6. Open business, licensing and legal questions

- Provenance of the launcher icon and the name "ExecBin" (no trademark search).
- Disclosure of AI-assisted development and its effect on copyright (jurisdiction-dependent).
- Open-source notice screen required before publishing (SQLCipher, Apache 2.0 components).
- Privacy policy, Data-safety form, and content-moderation obligations for a public chat are the
  publisher's responsibility.
- Scope of what is sold (source only vs. accounts, listing, data) must be fixed in a written agreement.
- See `IP_AND_TRANSFER_CHECKLIST.md`.
