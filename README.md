# ExecBin — Product Overview

Based on the source code in `FINAL_EXECBIN_SOURCE.zip` (inspected 2026-10-25). Statements below
describe what the code implements. "Implemented" means present in the source and covered by the
build; it does **not** mean tested on a real device (see `TEST_AND_VERIFICATION_REPORT.md`).

## 1. Purpose and audience

ExecBin is an Android messenger focused on private 1:1 conversations with end-to-end encryption,
plus a public group ("global") chat. It targets users who want encrypted chats with extra device
protections (PIN lock, biometric unlock, device management, security alerts). The code contains
no monetization (no ads, billing or subscriptions).

## 2. Platform and configuration (from `app/build.gradle.kts`)

| Item | Value |
|---|---|
| Package / application id | `com.example.execbin` (placeholder — must be changed before Google Play) |
| `compileSdk` / `targetSdk` | 36 / 36 |
| `minSdk` | 23 (Android 6.0) |
| Version | `versionCode 1`, `versionName "1.0"` |
| Language / UI | Kotlin; UI built programmatically in code (no XML layouts, no Jetpack Compose) |
| Java target | 11 |
| Build | Gradle 9.1.0 wrapper, Android Gradle Plugin 9.0.1, Kotlin 2.0.21 |
| Release build | R8 minification + resource shrinking enabled; signed only if `keystore.properties` exists |

## 3. Architecture

- **Single-activity app.** `MainActivity.kt` (9,222 lines) contains the screens, Firebase calls
  and most logic. Supporting files: `crypto/CryptoManager.kt` (all cryptography, unit-tested),
  `database/` (Room + SQLCipher cache), `StringsManager.kt` (10-language string tables),
  `PrivacyPolicy.kt` (10-language policy text), `InlineGalleryPicker.kt`, `LineIconDrawable.kt`,
  `MyFirebaseMessagingService.kt`, `DeviceUtils.kt`.
- **Backend:** Firebase only — no custom server apart from one Cloud Function.
- **Cloud Function** (`functions/`, TypeScript, firebase-functions v1 API): triggered when a
  `reports` document is created; optionally asks Anthropic's Claude Haiku model to classify the
  reported text.

## 4. Firebase and external services actually used

| Service | Used for |
|---|---|
| Firebase Authentication | Email + password sign-up/sign-in, email verification, password reset, password change |
| Cloud Firestore | Profiles, chats, messages (encrypted for private chats), devices, alerts, reports |
| Firebase Cloud Messaging | Device push token is stored; a receiving service exists. **No server-side sender exists** |
| Firebase App Check | Play Integrity provider in release builds, debug provider in debug builds |
| Firebase Crashlytics | Crash reports (user id and device model attached) |
| Cloud Functions + Secret Manager | Report triage function; `ANTHROPIC_API_KEY` stored as a secret |
| Anthropic API (optional) | Classification of reported global-chat messages or reporter-shared text |

Not used: Firebase Storage (media is stored inline in Firestore as base64), Analytics, Remote
Config, Google Sign-In (the `play-services-auth` dependency is declared but unused).

## 5. Features and workflows

| Area | Status | Notes |
|---|---|---|
| Registration / login | Implemented | Email + password; username chosen at sign-up with a client-side uniqueness check (not enforced server-side); email must be verified |
| Password reset / change | Implemented | Firebase Auth emails / re-authentication |
| Profile | Implemented | Nickname, bio, photo (stored as base64), online status, "hide last seen" |
| User search | Implemented | Prefix search on username |
| Private 1:1 chat | Implemented | End-to-end encrypted text, replies, edits, deletes, reactions, read receipts, typing indicator, pagination ("load more"), in-chat search |
| Photos and voice messages | Implemented | Encrypted in private chats; EXIF/GPS stripped from photos; inline base64 (size-limited) |
| Global chat | Implemented | Public, **not** end-to-end encrypted; text, photos, edits, deletes, reactions |
| Identity verification | Implemented | Safety number + QR code display; key-change warning. No in-app QR scanner |
| PIN 2-step lock | Implemented (client-enforced) | PBKDF2-hashed 6-digit PIN, lockout timers, required on untrusted devices |
| Biometric unlock | Implemented | Only after the PIN was verified on that device |
| Device management | Implemented | Up to 5 devices per account, remove ("kick") other devices after a 12-hour cooldown, new-login security alerts |
| Multiple accounts per device | Implemented | Account switcher |
| Blocking users | Partially implemented | Hides blocked users' messages on this device only (stored locally, not synced, not enforced server-side) |
| Reporting messages | Implemented | Reason codes; one report per message; optional sharing of a private message's text with moderators |
| AI moderation | Implemented (backend, optional) | Needs Cloud Function deployment + Anthropic key; results only stored in Firestore — **no moderator dashboard or automatic enforcement** |
| Push notifications | Partially implemented | Local notifications while the app process runs; no server push when the app is closed |
| Account deletion | Implemented | Deletes profile, private data, devices and own global messages (private chat messages remain) |
| Localization | Implemented | 10 languages: English, Spanish, French, German, Arabic, Russian, Portuguese, Italian, Chinese, Japanese |
| Themes / onboarding / privacy policy | Implemented | Dark/light theme, first-run tutorial, in-app privacy policy (contact address is a placeholder) |

## 6. Storage and offline behaviour

- **Local message cache:** Room database encrypted with SQLCipher (random 256-bit key); decrypted
  messages are cached so they stay readable offline and after key rotation.
- **Firestore offline cache:** enabled, 100 MB. Private messages are cached as ciphertext; public
  data (profiles, global chat) is cached unencrypted by the Firestore SDK.
- **Secrets** (private keys, PIN lockout state): EncryptedSharedPreferences backed by the Android Keystore.
- **Backups:** disabled; all data excluded from cloud backup and device-to-device transfer.

## 7. Security controls (summary)

ECDH P-256 + HKDF-SHA256 + AES-256-GCM end-to-end encryption with per-message ephemeral keys,
30-day key rotation, Firestore Security Rules (with 29 emulator tests), App Check with Play
Integrity, root warning, anti-tamper/anti-hooking deterrents, certificate pinning for Google
endpoints (Android 7+). Details and limitations: `SECURITY.md`.

## 8. Dependencies on external parties

Running ExecBin requires the buyer's own Firebase/Google Cloud project (Blaze plan for Cloud
Functions), a Google Play developer account for distribution, and — only for AI moderation — an
Anthropic API account. None of these accounts are included in the sale package.

## 9. Main limitations

Not tested on real devices after the latest changes; limited forward secrecy; client-enforced PIN;
no server push notifications; no key backup; several old/pre-release dependencies. Full list:
`KNOWN_LIMITATIONS.md`.
