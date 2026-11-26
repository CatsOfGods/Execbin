# ExecBin — Security Assessment (Buyer Edition)

Defensive review of the source code in `FINAL_EXECBIN_SOURCE.zip`, 2026-10-25. It extends
`ExecBin/SECURITY.md` inside the archive (same facts, plus a structured review of each area).

**Scope and limits.** This is a source-code review by the seller's development process (with AI
assistance), backed by unit tests and Firestore-emulator tests. It is **not** an independent audit
or penetration test, no live system was tested, and the app has not been tested on real devices
after the latest changes. Passing tests do not prove the absence of vulnerabilities.

Legend — **Fixed:** confirmed issue, fixed in the delivered code. **Open (confirmed):** behaviour
verified in the code, not fixed. **Recommendation:** improvement, no confirmed vulnerability.
**Unverified:** needs device / live-project testing to confirm.

## 1. Architecture in brief

- Private chats: end-to-end encryption on the device (ECDH P-256, HKDF-SHA256, AES-256-GCM, fresh
  12-byte IV per message, per-message ephemeral sender keys, 30-day identity-key rotation). All
  primitives in `crypto/CryptoManager.kt` with 26 unit tests.
- Global chat: public, plaintext in Firestore by design.
- Backend: Firebase Auth, Firestore + Security Rules, App Check, one Cloud Function.

## 2. Review by area

### 2.1 Authentication and email verification
- Firebase Auth email + password; password reset and change via Firebase.
- The app refuses to continue until the email is verified (`isEmailVerified` checks in `MainActivity`).
- **Open (confirmed), medium:** email verification is enforced only by the app. The Firestore
  rules require a signed-in user but not `request.auth.token.email_verified`, so an account with
  an unverified (e.g. disposable) email can use the database through the Firebase API.
  *Recommendation:* add `request.auth.token.email_verified == true` to the rules' `signedIn()`
  check and extend the emulator tests (not done to keep the delivered code unchanged).

### 2.2 Firestore Security Rules
- Full rules with 29 emulator tests; operation-by-operation review in
  `ExecBin/docs/FIRESTORE_RULES_REVIEW.md`. Private data (`users`, `securityAlerts`, `devices`,
  `reports`) is not readable by other users; private chats only by their two members; anonymous
  clients can read nothing; everything not listed is denied.
- **Open (confirmed), low:** any signed-in user can list all public profiles (username,
  nickname, photo, bio, presence, public key). Needed for search; allows bulk collection of
  public profile data. *Recommendation:* limit query size in rules or move search to a function.
- **Open (confirmed), low:** a user who knows another device's Android ID can overwrite that
  device record / add themselves to its account list (impact: signs a session out, reveals which
  accounts share a device). Android IDs are not exposed to other users by the app.
- **Open (confirmed), low:** usernames are not unique at the database level (client-side check only).
- **Unverified:** rules have never been deployed to a real project; existing production data
  shape not checked (see the rules review §5).

### 2.3 Cloud Function (report triage)
- Firestore trigger (not a callable/HTTP endpoint), so there is no public URL to call.
- Input re-validated server-side; global-chat text is read from Firestore, not trusted from the
  client; reporter-shared private text is labelled unverified.
- Cost controls: one report per user per message (rules), 1,000-character input cap, 300 output
  tokens, 10 AI reviews per reporter/day and 500/day total (Firestore transaction), max 5
  instances, 20 s API timeout, strict output validation, errors stored as type only.
- API key held in Secret Manager (`ANTHROPIC_API_KEY`), never in code.
- **Unverified:** not deployed; not tested against the live Anthropic API.

### 2.4 Sensitive data stored in Firestore
| Data | Protection |
|---|---|
| Private message text, replies, media | Ciphertext (E2E); server sees metadata only |
| Message metadata (sender, username, nickname, sender photo, timestamps, read flags, reactions) | Plaintext; readable by chat participants and by the project owner |
| PIN | PBKDF2-HMAC-SHA256 (100k iterations, random salt); readable by the account owner only |
| Email | SHA-256 with a per-install random salt (not used for lookup) |
| Device names / login times in device and alert docs | Encrypted with a per-device key |
| Global chat, public profiles | Plaintext by design |
| Reports | Metadata, optional reporter-shared text; no client can read |

- **Open (confirmed), medium:** the PIN is checked by the app, not the server. Anyone holding the
  account's Firebase credentials can access that user's own data via the API without the PIN, and
  could attempt offline guessing of the 6-digit PIN from its hash (100k PBKDF2 iterations slows
  this but a 6-digit space is small). Treat the PIN as an app lock, not a server-side second factor.

### 2.5 Local database and cache
- **Fixed:** message cache previously fell back to an unencrypted database; now SQLCipher only,
  memory-only fallback, legacy plaintext DB migrated and deleted.
- **Fixed:** secrets previously fell back to plaintext SharedPreferences; now EncryptedSharedPreferences
  only, with an error screen if secure storage is unavailable.
- Firestore SDK offline cache (100 MB) stores public data unencrypted on the device (normal SDK behaviour).
- Backups and Android 12+ device transfer excluded for all data.

### 2.6 Logging and accidental exposure
- 39 `Log` calls in `MainActivity`; a pattern review found only exception messages and status
  text — no message content, keys, tokens or PINs. (Pattern-based review; not a guarantee.)
- **Recommendation:** strip `Log` calls from release builds (R8 `-assumenosideeffects`) — logs are
  currently kept in release.
- Crashlytics receives the Firebase user id and device model/manufacturer/OS version with crashes.
  Declare this in the privacy policy / Data-safety form.

### 2.7 Secrets and configuration
- No credentials in the archive (scan results in `TEST_AND_VERIFICATION_REPORT.md`).
- `google-services.json` (not secret, but project-specific), keystore, `local.properties`,
  service-account key and `functions/.env` are excluded and listed in `.gitignore`.
- The Firebase web API key that will be inside the buyer's `google-services.json` is not a secret;
  protection comes from Security Rules and App Check.

### 2.8 Android permissions and components
- Only `MainActivity` (launcher) is exported; the FCM service is not exported. No content
  providers, no broadcast receivers declared by the app.
- Permissions: Internet, network state, read external storage (ignored on Android 13+), read
  media images, notifications, record audio — all used by features.
- **Unverified:** Android 14 "selected photos" partial access may not work with the in-app gallery.

### 2.9 Network security
- Cleartext traffic disabled. Certificate pins for Firebase/Google endpoints (expire 2027-06-01;
  update or remove before then). The network security config is ignored on Android 6.0 (API 23),
  so pinning applies from Android 7.

### 2.10 Release build
- R8 minification and resource shrinking on; release not debuggable (default). ProGuard keep
  rules are broad for Firebase and the app's model classes (reduces obfuscation, does not expose data).
- Signing only via the buyer's own keystore; none included.

### 2.11 Sessions and devices
- Inactivity sign-out after 3 days (checked on app start; local). Device trust 30 days (local,
  with a server timestamp). Biometric unlock only after the PIN was verified on that device.
- Root detection is a warning only; App Check (Play Integrity) is the real attestation and must
  be **enforced in the Firebase Console** to block non-genuine clients.

## 3. Abuse scenarios and remaining risks

| Scenario | Current mitigation | Residual risk |
|---|---|---|
| Spam in global chat | Client-side rate limit (5 msgs / 10 s, duplicate block, 4,000-char limit); App Check once enforced | Client limits can be bypassed by API clients until App Check is enforced; no server-side rate limit; no moderator tooling |
| Throwaway accounts | Email verification in the app | Not enforced by rules (2.1) |
| Report flooding / AI cost | One report per message; daily AI budgets | Manual review queue can still grow |
| Storage/read cost abuse | Size limits in rules (media ≤ 950k chars, text limits) | Firestore usage is billed per operation — set budget alerts |
| Key substitution (server-side MITM) | Key-change warning, safety numbers | Users must actually compare safety numbers |
| Stolen unlocked phone | PIN/biometric lock, 3-day inactivity timeout | Client-enforced |
| Recipient key compromise | 30-day key rotation | No real forward secrecy (no Double Ratchet) |
| Reinstall / lost phone | — | No key backup: server-stored history becomes unreadable |
| Hooking/tamper detected | Local keys deleted | Can cause data loss on false positives |

## 4. Fixed during the October 2026 review

Plaintext DB fallback; plaintext key-storage fallback; PIN skipped when offline; biometric
bypassing 2-step on new devices; contact-supplied malformed key wiping local keys; unencrypted
media/edits when the key wasn't ready; undecryptable edits; dead post-rotation key fallback;
decrypt failures overwriting cache; no key-change warning; device-to-device transfer not excluded;
App Check not wired; missing Firestore rules; unvalidated reports reaching the paid AI; API 26/28
crashes on Android 6–8; locale-dependent safety-number digits. Details: `ExecBin/SECURITY.md`.

## 5. Recommended before production

1. Enforce email verification in rules (2.1). 2. Enforce App Check for Firestore.
3. Run the real-device checklist. 4. Upgrade Room (alpha), `security-crypto` (alpha), SQLCipher,
Firebase BoM (`ExecBin/docs/LINT_REVIEW.md`). 5. Strip release logs. 6. Consider an independent
security review before marketing the app as secure.
