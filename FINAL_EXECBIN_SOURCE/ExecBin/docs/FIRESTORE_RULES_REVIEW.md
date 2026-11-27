# Firestore Rules Review

Rules: `firestore.rules`. Tests: `firestore-tests/rules.test.js` — **29/29 passing** in the
Firestore emulator on 2026-10-25:

```bash
firebase emulators:exec --only firestore --project demo-execbin "npm --prefix firestore-tests test"
```

**Status: verified in the emulator only. Not yet deployed and not yet tested against the live
project or its existing data.** The emulator proves the rules allow/deny what the tests describe;
it can't prove every real document in production has the expected shape (see "Risks with existing
data").

## 1. Every app operation vs. the rules

Each Firestore call in `MainActivity.kt` / `MyFirebaseMessagingService.kt`, grouped by feature.
"Test" names the emulator test that exercises the same operation.

| Feature | Operation (code) | Rule that allows it | Test |
|---|---|---|---|
| Sign-up | `users/{uid}.set(private data)`; `publicProfiles/{uid}.set(public data)` (`createFirestoreProfile`) | owner write; profile create with `uid == docId`, username, size limits | publicProfiles: owner updates… (create as carol) |
| Login | `publicProfiles/{uid}.get` (`checkAndLoadUserData`); `users/{uid}.get` (`checkPinBeforeEntry`) | signed-in read; owner read | users / publicProfiles tests |
| PIN set/change/disable | `users/{uid}.update(securityPin, hasPinEnabled)` | owner write | users: owner can read/write |
| PIN legacy upgrade | `users/{uid}.update(securityPin)` | owner write | same |
| FCM token | `users/{uid}.update(fcmToken)` (activity + messaging service) | owner write | same |
| Deletion mark | `users/{uid}.update(markedForDeletion)` | owner write | same |
| Presence / last seen | `publicProfiles/{uid}.update(isOnline, lastSeen, hideLastSeen)` | owner update, username unchanged | publicProfiles; legacy profile test |
| Edit profile | `publicProfiles/{uid}.update(nickname, bio, photoBase64)` | owner update, photo ≤ 950,000 chars | publicProfiles |
| Publish / rotate E2E key | `publicProfiles/{uid}.update(publicKey)` | owner update, key ≤ 500 chars | publicProfiles; legacy profile test |
| User search | `publicProfiles` `orderBy(username).startAt..endAt`, `whereEqualTo(username)` | signed-in list | publicProfiles: readable by signed-in users |
| Contact key / header | `publicProfiles/{other}.get` + snapshot listener | signed-in read | same |
| Save device session | `devices/{deviceId}.get` then `.set(...)` (`saveDeviceSession`) | own record; set with `userId == me` | devices tests; shared-device test |
| Device list / limit / trust | `devices` `whereEqualTo(userId)` (+ `deviceId`), `.update(trustedAt)` | list/update own | devices: owner can read/query/delete; legacy device test |
| Kick / remove device | `devices/{id}.delete` | own record | devices |
| Cooldown check | `devices/{currentDeviceId}.get` | own record (fails safely if another account owns it) | shared-device test |
| Account switcher | `deviceAccounts/{deviceId}.get/.set` | listed accounts; adding/removing yourself | deviceAccounts |
| Security alerts | `users/{uid}/securityAlerts.add`, listener `whereEqualTo(read,false)`, `.update(read)` | owner | securityAlerts: owner only |
| Global chat | listener on `globalChat`; `.add(text/media)` | signed-in read; create as self + real username, text ≤ 4,000, media ≤ 950,000 | globalChat tests; longest-message test |
| Global edit / delete | `.update(text, edited, editedAt)`, `.delete` | sender only | globalChat: only the sender edits/deletes |
| Reactions (both chats) | `.update(reactions)` (`toggleReaction`) | add ≤ 1 own entry, remove only own | globalChat + private reactions tests |
| Chat list | `privateChats` `whereArrayContains(participants, me)` | list when `me ∈ participants` | privateChats: chat list query |
| Open chat / typing listener | `privateChats/{chatId}.get` + listener | participant (from chat id) | privateChats: only participants read |
| Send private message | `privateChats/{chatId}/messages.add` | participant, sender = me, real username, text ≤ 100,000 | private messages: send as yourself; longest-message test |
| Chat preview | `privateChats/{chatId}.update(lastMessage, lastTimestamp)` or `.set(participants…)` | update preview / create with exactly both members | privateChats create / update tests |
| Typing indicator | `privateChats/{chatId}.update(typing_<me>)` | only own flag | privateChats: … only their own typing flag |
| Read receipts | batch `.update(read: true)` | recipient only, false→true | private messages: read receipts |
| Edit private message | `.update(text, senderText, replyToText, senderReplyText, ephemeralKey, edited, editedAt)` | sender only | private messages: sender edits re-encrypted fields |
| Delete private message | `.delete` | sender only | private messages: reactions and deletion |
| Notifications | `privateChats/{chatId}/messages` `orderBy(timestamp).limit(1)` | participant | privateChats read test |
| Report | `reports/{uid}_{messageId}.set(...)` | create-only, validated (§3) | reports tests |
| Delete account | `globalChat whereEqualTo(senderId, me)` + batch deletes of own messages, devices, `users/{uid}`, `publicProfiles/{uid}`; `deviceAccounts` update | owner/sender rules | globalChat: account deletion query; users; devices |

## 2. Who can read what (private data exposure)

| Collection | Contents | Who can read |
|---|---|---|
| `users/{uid}` | salted email hash, PIN hash, FCM token, flags | **owner only** (tested: no get, list or query by others) |
| `users/{uid}/securityAlerts` | encrypted device name/time, device id | owner only |
| `publicProfiles/{uid}` | username, nickname, photo, bio, online/last seen, public key | any signed-in user (by design: search, chat headers, key exchange). Contains no email or private data. |
| `devices/{id}` | encrypted device name/timestamps, device id, trusted flag | the account that owns the record |
| `deviceAccounts/{id}` | uids of accounts signed in on that device | accounts in that list |
| `globalChat` | public messages (not E2E) | any signed-in user (public by design) |
| `privateChats/{a_b}` + `messages` | ciphertext, ephemeral keys, sender username, timestamps, read flags | the two participants only |
| `reports` | report metadata, optional reporter-shared text | **no client** — Admin SDK / Cloud Function only |
| `moderationUsage` | AI budget counters | no client |
| anything else | — | denied |

Anonymous (signed-out) clients can read nothing (tested).

What the server operator (anyone with Firebase console/Admin access) can see: all metadata
(who talks to whom, when, usernames, profile data, global chat), but **not** private message
content, which is ciphertext.

## 3. Report validation

Create only (no read/update/delete); doc id must be `<reporterId>_<messageId>` (one report per
user per message); fixed field set; reason in `spam | harassment | hate_speech | violence | other`;
`status == 'pending'`; `createdAt == request.time`; the message must exist, belong to
`reportedUserId`, and be readable by the reporter; no self-reports; `reportedText` only for private
chats and ≤ 1,000 characters.

## 4. Known gaps (accepted, documented)

- **Device id takeover:** a user who knows another device's `ANDROID_ID` can overwrite that
  device record or add themselves to its `deviceAccounts` list. Impact: signs out / reveals
  which accounts use that device. Needed for multi-account devices; ids are not exposed to other users.
- **Login alert on shared devices:** when a second account signs in on a device owned by another
  account, the app can't read the old record, so it skips the "new device" alert (it still saves
  the session).
- **Usernames not unique:** rules can't enforce uniqueness without a `usernames` index collection.
- **Nickname and photo in messages aren't checked against the profile** (username is). A user can
  set any nickname anyway, so this adds no new spoofing ability beyond the profile itself.
- **PIN is client-enforced:** rules can't require the PIN; the user's own data is still protected
  from other users.

## 5. Risks with existing (live) data — check before deploying

The rules are written so documents from earlier app versions keep working: profiles without
`uid`/`username` and device docs without `deviceId` can still be updated (tested). Still, before
deploying:

1. Export or note the **currently deployed rules** (Firebase Console → Firestore → Rules) so you
   can roll back.
2. Spot-check a few real documents: `privateChats` docs must have a `participants` array (chats
   without it won't appear in the chat list — that was already the case), and message docs must
   have `senderId`.
3. Deploy only the rules (`firebase deploy --only firestore:rules`), then run the device checklist
   (`docs/DEVICE_TEST_CHECKLIST.md`) with two accounts. Watch the rules monitoring (allowed vs. denied requests) in the
   Firebase Console and Logcat for `PERMISSION_DENIED`; roll back if real features break.
