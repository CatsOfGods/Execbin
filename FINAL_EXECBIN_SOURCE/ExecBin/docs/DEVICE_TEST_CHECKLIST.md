# Real-Device Test Checklist

Nothing in this list has been run on a physical phone yet. Automated checks so far: Android unit
tests (27), Cloud Functions tests (7), Firestore rules emulator tests (29), debug + release builds,
lint (0 errors). Those don't exercise the UI, the real Firebase project, Play Integrity, or
device-specific behaviour — that's what this list is for.

Mark each item ✅ / ❌ and note the device + Android version.

## 0. Before you start

- [ ] **Use a test Firebase project first** (or accept that tests write to your live data).
- [ ] Back up the currently deployed Firestore rules (Console → Firestore → Rules → copy).
- [ ] `app/google-services.json` from the project you're testing against.
- [ ] Debug build: App Check debug token registered (Console → App Check → Manage debug tokens;
      the token is printed in Logcat by `DebugAppCheckProvider` on first launch).
- [ ] If testing the new rules: deploy them to the **test** project only
      (`firebase deploy --only firestore:rules --project <test-project>`).
- [ ] Devices: **two phones** (accounts A and B). Ideally also one Android 6–8 device or emulator
      (API 23–27), one Android 14+ device, and one Android 12+ device for the transfer check.
- [ ] Keep Logcat open, filtered on `ExecBin` and `PERMISSION_DENIED`.

## 1. Install, sign-up, login

- [ ] **I1** Fresh install on phone 1, register account A, verify email → home screen appears.
- [ ] **I2** Profile shows username; no "Profile:" or "Auth:" error toast.
- [ ] **I3** Register account B on phone 2. Search for A by username → found.
- [ ] **I4** Android 6–8 device: app starts, opening a chat and the PIN/biometric screen does **not** crash.

## 2. PIN, biometrics, session (security fixes)

- [ ] **P1** Set a PIN on A. Sign out, sign in again on the same phone → trusted device, no PIN asked (within 30 days).
- [ ] **P2** Sign in to A on a **second phone** → PIN screen appears. The "Use biometric" option must **not** appear there. Wrong PINs → lockout timer grows (30 s after 3 failures).
- [ ] **P3** After entering the correct PIN once on that phone, sign out/in again after trust expires (or clear app data except login) → biometric option may now appear.
- [ ] **P4** Fail-closed: on a phone where A is *not* trusted, enable airplane mode, open the app → it must ask for the PIN (or show an error), **not** go straight to the home screen.
- [ ] **P5** Disable PIN in settings (requires current PIN) → next login on a new device skips the PIN.
- [ ] **P6** Session: leave the app unused for 3+ days (or move the phone's clock forward) → "session expired", signed out.

## 3. Private chat (E2E)

Run between A (phone 1) and B (phone 2).

- [ ] **C1** Header shows 🔒 end-to-end encrypted. Send text both ways → readable on both phones.
- [ ] **C2** Reply to a message → reply preview readable on both sides.
- [ ] **C3** **Edit** a message (your own) → the *other* phone shows the edited text (previously showed "decryption failed").
- [ ] **C4** Edit a message that has a reply quote → quote still readable on both phones.
- [ ] **C5** Delete your own message → gone on both. You can't delete the other person's.
- [ ] **C6** React, change reaction, remove reaction → updates on both phones.
- [ ] **C7** Read receipts turn on when the other person opens the chat.
- [ ] **C8** Typing indicator appears on the other phone and disappears.
- [ ] **C9** Send a **photo** → appears on both; open full screen; save to gallery. Location data removed (check EXIF of saved file).
- [ ] **C10** Send a **voice message** → plays on both phones.
- [ ] **C11** Send a ~4,000-character message (paste) → delivered. Try a long emoji-only message too.
- [ ] **C12** Close and reopen the chat, and restart the app → old messages still readable (from the encrypted cache and from Firestore).
- [ ] **C13** Airplane mode, open the chat → cached messages visible; sending is blocked or queued without plaintext.

## 4. Keys and identity verification

- [ ] **K1** Open the identity verification screen on both phones → same 60-digit safety number and QR.
- [ ] **K2** Set one phone's **system language to Arabic** (or Persian) → safety number still shows ASCII digits 0-9 and matches the other phone.
- [ ] **K3** Key change warning: on phone 2, clear app data (or reinstall) for B and sign in again → when A opens the chat with B, a "Security key changed" dialog appears once. (Expected side effect: B can't read old messages after reinstalling — no key backup.)
- [ ] **K4** After K3, new messages work in both directions.

## 5. Global chat

- [ ] **G1** Send text and a photo → visible on the other phone.
- [ ] **G2** Edit / delete your own message; can't edit or delete others'.
- [ ] **G3** Reactions work.

## 6. Reports and moderation

- [ ] **R1** Report a **global** message → "report submitted". In Console, `reports/<uid>_<msgId>` exists with `status: pending`; if the function is deployed, it becomes `ai_reviewed` (or `needs_manual_review`) with `textSource: server_global_chat`.
- [ ] **R2** Report the same message again → "You've already reported this message."
- [ ] **R3** Report a **private** message **without** ticking "share text" → `needs_manual_review`, `aiSkippedReason: e2e_no_text_shared`, no message text stored.
- [ ] **R4** Report another private message **with** the box ticked → `reportedText` present, `textSource: reporter_supplied_unverified`.
- [ ] **R5** Report reason labels are translated in another UI language, but Firestore stores codes (`spam`, `harassment`…).

## 7. Devices and multiple accounts

- [ ] **D1** Settings → devices lists both phones for A. Kick the other phone (after the cooldown) → it's signed out.
- [ ] **D2** Add account B on phone 1 (account switcher), switch A ↔ B → each account sees only its own chats and keys.
- [ ] **D3** Security alert ("new login") appears on A's first phone when A signs in on a new phone.

## 8. Device integrity, storage, backups

- [ ] **S1** Rooted device or rooted emulator (if available) → one-time warning with "Continue anyway"; tapping it opens the app; warning doesn't reappear.
- [ ] **S2** Release build installed from Play (internal testing track) → no "Device verification failed" dialog on a normal phone. In Console → App Check, requests show as **verified**.
- [ ] **S3** With App Check **enforced** on the test project: debug build without a registered token can't load data; registered debug build and Play build work.
- [ ] **S4** Android 12+: set up a new phone with device-to-device transfer from the old one → ExecBin data is **not** transferred (app starts signed out / fresh).
- [ ] **S5** Upgrade test: install the **previous** app version, sign in, send messages, then install this version over it → still signed in, old messages readable, keys intact.

## 9. Photos permission (lint: SelectedPhotoAccess)

- [ ] **P3a** Android 14+: when asked for photo access, choose **"Select photos"** → the in-app gallery shows the selected photos (if it shows nothing or keeps asking, this needs a fix).
- [ ] **P3b** Android 13: "Allow" → full gallery. Android 10–12: storage permission → gallery works.

## 10. Notifications and other

- [ ] **N1** App in background (not swiped away): message from B → notification on A with decrypted text.
- [ ] **N2** App swiped away / phone idle for a while: message from B → **expected to NOT notify**: no server sends push messages (`MyFirebaseMessagingService` only receives). Decide whether that's acceptable or add a Cloud Function that sends FCM data messages.
- [ ] **N3** Switch UI language (all 10) → new dialogs (secure storage, key change, report checkbox) are translated.
- [ ] **N4** Delete account → signed out; profile gone from search; global messages deleted.

## Results

| Item | Device / Android | Result | Notes |
|---|---|---|---|
| | | | |
