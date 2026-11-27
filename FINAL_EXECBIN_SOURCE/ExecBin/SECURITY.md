# ExecBin — Security Design and Review

Last review: 2026-10-25. This document describes what the app protects, how, what was fixed
in the latest review, and what it does **not** guarantee. It deliberately avoids overstating.

## Verification status

| Check | Status |
|---|---|
| Android unit tests (27, incl. crypto regression tests) | ✅ pass |
| Cloud Functions type check + unit tests (7) | ✅ pass |
| Firestore rules in the emulator (29) | ✅ pass |
| Debug + release builds, lint | ✅ pass (0 errors; warnings reviewed in `docs/LINT_REVIEW.md`) |
| Real devices (UI flows, Android versions, Play Integrity) | ❌ **not done** — `docs/DEVICE_TEST_CHECKLIST.md` |
| Rules deployed and tested against the live project / existing data | ❌ **not done** |
| App Check enforcement enabled | ❌ manual console step |
| Independent security audit / penetration test | ❌ not done |

Until the ❌ rows are done, this is a reviewed and tested codebase, **not** a verified
production deployment.

## Threat model in one paragraph

ExecBin aims to keep 1:1 message content confidential from the server operator and from other
users, keep each user's data writable only by them, and make stolen or tampered devices harder
to abuse. It does not defend against a fully compromised (rooted + instrumented) device, and the
server (Firebase) is trusted to distribute the correct public keys unless users compare safety
numbers.

## Cryptography (private chats)

All primitives live in `app/.../crypto/CryptoManager.kt` and are unit-tested; `MainActivity`
calls them (it no longer has its own copies).

| Part | Implementation |
|---|---|
| Identity key | P-256 key pair generated on-device; private key (PKCS#8) stored in EncryptedSharedPreferences (AES-256-GCM, Android Keystore master key); public key published in `publicProfiles` |
| Key agreement | ECDH P-256 → HKDF-SHA256 (zero salt, `info` = `execbin-msg-key` / `execbin-pfs-msg-key`) → AES-256 key |
| Message encryption | AES-256-GCM, 12-byte random IV per message, 128-bit tag |
| Per-message keys | Each text message uses a fresh ephemeral sender key; the recipient copy and the sender's own copy are encrypted under different derived keys |
| Media | Photos/voice encrypted with the static ECDH key (AES-256-GCM), EXIF/GPS stripped |
| Key validation | Contact public keys must decode to a P-256 point before use |
| Key change notice | The app remembers each contact's key and warns when it changes |
| Verification | Safety number (iterated SHA-256 over both keys) + QR code |
| Rotation | Identity key rotates every 30 days; one previous key is kept to read older messages |

### Forward secrecy — honest assessment

The per-message ephemeral key protects against later compromise of the **sender's** keys only.
Anyone who obtains the **recipient's** long-term private key (or the retained previous key) can
decrypt every message sent to it that is still stored in Firestore. Forward secrecy is therefore
limited to the 30-day rotation window plus one retained key. A Double Ratchet (Signal protocol /
libsignal) would be needed for real forward secrecy. A unit test documents this property.

Other protocol limits: ciphertexts carry no associated data (not bound to chat/sender in the
crypto itself — Firestore rules enforce sender identity instead); no key backup, so reinstalling
or losing the device makes server-stored history unreadable (the local encrypted cache is lost
with it).

## Local storage

- **Secrets** (private keys, PIN lockout state, metadata key): EncryptedSharedPreferences only. If
  secure storage can't be opened, the corrupted file is reset once; if it still fails the app
  shows an error instead of falling back to plaintext. Old plaintext fallback files are migrated
  into encrypted storage and deleted.
- **Message cache**: Room + SQLCipher with a random 256-bit key. If it can't be opened it is
  rebuilt (it only mirrors Firestore); if SQLCipher is unavailable the cache is memory-only.
  It never falls back to an unencrypted file; an old plaintext cache from earlier versions is
  migrated into the encrypted DB and deleted.
- **Firestore SDK cache** (100 MB): stores private messages only as ciphertext, but public data
  (global chat, profiles) in the SDK's normal, unencrypted on-device cache.
- **Backups**: `allowBackup=false`, and data-extraction rules exclude all data from cloud backup
  and Android 12+ device-to-device transfer.

## Authentication, PIN, biometrics, sessions

- Firebase Authentication with verified email.
- **PIN** (6 digits): PBKDF2-HMAC-SHA256, 100,000 iterations, random 16-byte salt, constant-time
  compare; legacy SHA-256 hashes upgrade automatically. Exponential lockout after failures.
- **Important limitation:** the PIN is enforced by the app, not by the server. Someone holding the
  account's Firebase credentials could use the Firestore API directly (subject to the rules, i.e.
  only that user's own data). Treat the PIN as a device/app lock and second step in the official
  client, not as a server-side second factor.
- **Biometric unlock** (BIOMETRIC_STRONG) is offered only on a device where that account's PIN was
  already verified, so it can't replace the PIN on a new device.
- **Session**: the app signs out after 3 days of inactivity (local check).
- **Device trust**: trusted locally for 30 days; a `trustedAt` timestamp is also written to the
  user's device document, but expiry is enforced on the client.

## Device integrity

- **Firebase App Check + Play Integrity** is the real attestation: Google verifies the device
  and app, and Firebase rejects requests without a valid token **once enforcement is enabled in
  the Firebase Console** (see README §4). Until then App Check only measures. Debug builds use the
  App Check debug provider.
- If the app can't obtain a token it tells the user that the server may refuse the device; it
  does not pretend the check passed.
- Root detection is a local heuristic (easy to bypass). It shows a one-time warning with
  "Continue anyway" rather than blocking.
- Anti-tamper (signature baseline), Frida/debugger detection and honeypot preference files are
  deterrents only. Note: if hooking is detected the app deletes its local E2E keys, which makes
  stored history unreadable on that device.

## Firestore security rules

`firestore.rules` (29 emulator tests in `firestore-tests/`; full operation-by-operation review in `docs/FIRESTORE_RULES_REVIEW.md`):

- `users/{uid}` and `securityAlerts`: owner only.
- `publicProfiles`: readable by signed-in users, writable by the owner; username immutable;
  size limits on key/photo.
- `devices`, `deviceAccounts`: only the account that owns the device record / is listed on it.
- `globalChat`: signed-in read; create only as yourself with your real username; edits/deletes by
  the sender; reactions limited to adding/removing your own entry.
- `privateChats`: only the two members encoded in the chat id; chat list query by `participants`;
  members may update only the preview and their own typing flag; messages created only as
  yourself; sender-only edits (re-encrypted fields); read receipts only by the recipient and only
  false→true; sender-only deletes.
- `reports`: create-only, one per reporter per message (`<uid>_<messageId>`), fixed field set,
  enum reasons, server timestamp, the reported message must exist, belong to the reported user
  and be visible to the reporter; no client reads.
- Everything else (including `moderationUsage`): denied.

## Abuse reports and AI moderation with E2E encryption

| Report about | What the server/AI can see |
|---|---|
| Global chat message | The message text, read by the Cloud Function from Firestore (not trusted from the client) |
| Private chat message, reporter opts in | Only that one message's text, supplied by the reporter — stored as `reporter_supplied_unverified` (it can't be proven against the ciphertext) |
| Private chat message, no opt-in | Nothing beyond metadata (who, which message, reason) → manual review |

Cost/abuse limits: one report per user per message (rules), 1,000-character input cap, 300 output
tokens, per-reporter (10/day) and global (500/day) AI budgets enforced in a Firestore transaction,
5 function instances max, 20 s API timeout. Model output is strictly validated; anything else goes
to manual review. E2E encryption is not weakened for moderation.

## Fixed in this review

| Issue | Severity | Fix |
|---|---|---|
| Message cache fell back to an **unencrypted** database holding decrypted messages | High | Removed; memory-only fallback; legacy plaintext DB migrated and deleted |
| Secure prefs fell back to **plaintext** prefs holding private keys | High | Removed; reset-once then error screen; legacy file migrated and deleted |
| PIN screen skipped when Firestore couldn't be read (offline) — and the device was then marked trusted | High | Fails closed (asks for the PIN unless known to be off or device already trusted) |
| Biometric button on the PIN screen let **any** fingerprint pass 2-step verification on a new device | High | Biometric only after this account's PIN was verified on this device |
| A contact publishing a malformed public key made the app **delete its own private key** | High | Contact keys validated first; local keys regenerate only if they are actually missing |
| Media in private chats sent **unencrypted** if the shared key wasn't ready | High | Blocked with "encryption not ready" |
| Edits in private chats stored **plaintext** when the key wasn't ready | High | Blocked |
| Edited private messages undecryptable for the recipient (old ephemeral key) | Medium | Edits re-encrypt text and reply with a fresh ephemeral key |
| Post-rotation fallback to the previous key never ran (ECDH can't "fail") | Medium | Fallback now triggers on AES-GCM failure, for text and media |
| Decryption failures overwrote cached plaintext | Medium | Failures are not cached; cached plaintext shown instead |
| Contact key changes went unnoticed (MITM signal) | Medium | Key-change warning + safety-number prompt |
| Android 12+ device-to-device transfer not excluded | Medium | Data-extraction rules exclude everything |
| Play Integrity code unused; failures silently ignored | Medium | App Check (Play Integrity, server-verified), debug provider, user warning on failure |
| Root detection result discarded | Low | One-time warning |
| Firestore rules not in the project; reports accepted any fields (e.g. text to the paid AI) | High | Full rules + emulator tests |
| AI moderation could never run (no text in reports) and had no cost limits | Medium | Redesigned flow (above) with budgets and validation |
| Report reasons were translated strings | Low | Stable reason codes |
| Crypto unit tests didn't cover production code | Medium | `MainActivity` now uses `CryptoManager`; regression tests added |
| Biometric prompt used an API 28 executor (crash on Android 6–8, minSdk is 23) | Bug | `ContextCompat.getMainExecutor` |
| Safety numbers used the device's digit script (Arabic, Persian, Hindi, Thai…), so two users saw different characters for the same number | Medium | `Locale.ROOT`; regression test |
| Gradle wrapper pointed at 7.3.1 (AGP 9 needs 9.x) | Build | Wrapper set to 9.1.0 |

## Known limitations (not fixed)

- No real forward secrecy (see above); no multi-device key sync; no key backup/recovery.
- PIN and device trust are client-enforced.
- Device ids (Android ID) identify device documents; someone who knows another user's device id
  could overwrite that device record with their own (it would only sign that device's session out).
- Usernames are not guaranteed unique by the rules (would need a `usernames` index collection).
- Inline base64 media is limited by the 1 MiB Firestore document size.
- The certificate pins in `network_security_config.xml` expire 2027-06-01; update or remove them
  before then.
- Anti-tamper, hooking detection and honeypots can be bypassed by a determined attacker.
- Several dependencies are old or pre-release (Room alpha, `security-crypto` alpha, SQLCipher
  4.6.1, Firebase BoM 33) — see `docs/LINT_REVIEW.md` for the upgrade plan.
- Not security, but worth knowing: no server sends push messages, so chat notifications only
  arrive while the app process is alive.
