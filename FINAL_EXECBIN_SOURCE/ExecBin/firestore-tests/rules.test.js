// Firestore security rules tests (Firebase Emulator Suite).
// Run from the project root:
//   firebase emulators:exec --only firestore --project demo-execbin "npm --prefix firestore-tests test"
const { test, before, beforeEach, after } = require("node:test");
const fs = require("node:fs");
const path = require("node:path");
const {
  initializeTestEnvironment, assertSucceeds, assertFails,
} = require("@firebase/rules-unit-testing");
const {
  doc, getDoc, setDoc, updateDoc, deleteDoc, collection, query, where, getDocs, serverTimestamp,
} = require("firebase/firestore");

const ALICE = "alice";
const BOB = "bob";
const EVE = "eve";
const CHAT = "alice_bob"; // sorted uid pair

let env;

before(async () => {
  const [host, port] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8080").split(":");
  env = await initializeTestEnvironment({
    projectId: "demo-execbin",
    firestore: {
      rules: fs.readFileSync(path.join(__dirname, "..", "firestore.rules"), "utf8"),
      host,
      port: Number(port),
    },
  });
});

after(async () => { await env.cleanup(); });

beforeEach(async () => {
  await env.clearFirestore();
  await env.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    for (const uid of [ALICE, BOB, EVE]) {
      await setDoc(doc(db, "publicProfiles", uid), { uid, username: uid, nickname: uid, publicKey: "k" });
      await setDoc(doc(db, "users", uid), { uid, securityPin: "", hasPinEnabled: false });
    }
    await setDoc(doc(db, "privateChats", CHAT), { participants: [ALICE, BOB], lastMessage: "x", lastTimestamp: 1 });
    await setDoc(doc(db, "privateChats", CHAT, "messages", "m1"), {
      senderId: BOB, username: BOB, text: "cipher", read: false, reactions: [],
    });
    await setDoc(doc(db, "globalChat", "g1"), { senderId: BOB, username: BOB, text: "hello", reactions: [] });
    await setDoc(doc(db, "devices", "devA"), { deviceId: "devA", userId: ALICE, trusted: true });
    await setDoc(doc(db, "deviceAccounts", "devA"), { deviceId: "devA", accounts: [ALICE] });
  });
});

const as = (uid) => env.authenticatedContext(uid).firestore();
const anon = () => env.unauthenticatedContext().firestore();

// ---------------------------------------------------------------- users
test("users: owner can read/write, others and anonymous cannot", async () => {
  await assertSucceeds(getDoc(doc(as(ALICE), "users", ALICE)));
  await assertSucceeds(updateDoc(doc(as(ALICE), "users", ALICE), { fcmToken: "t" }));
  await assertFails(getDoc(doc(as(BOB), "users", ALICE)));
  await assertFails(updateDoc(doc(as(BOB), "users", ALICE), { hasPinEnabled: false }));
  await assertFails(getDoc(doc(anon(), "users", ALICE)));
});

test("securityAlerts: owner only", async () => {
  await assertSucceeds(setDoc(doc(as(ALICE), "users", ALICE, "securityAlerts", "a1"), { read: false }));
  await assertSucceeds(getDocs(query(collection(as(ALICE), "users", ALICE, "securityAlerts"), where("read", "==", false))));
  await assertFails(setDoc(doc(as(EVE), "users", ALICE, "securityAlerts", "a2"), { read: false }));
  await assertFails(getDocs(collection(as(EVE), "users", ALICE, "securityAlerts")));
});

// ---------------------------------------------------------------- publicProfiles
test("publicProfiles: readable by signed-in users only", async () => {
  await assertSucceeds(getDoc(doc(as(EVE), "publicProfiles", ALICE)));
  await assertSucceeds(getDocs(query(collection(as(EVE), "publicProfiles"), where("username", "==", "alice"))));
  await assertFails(getDoc(doc(anon(), "publicProfiles", ALICE)));
});

test("publicProfiles: owner updates, username immutable, others denied", async () => {
  await assertSucceeds(updateDoc(doc(as(ALICE), "publicProfiles", ALICE), { nickname: "Al", isOnline: true }));
  await assertSucceeds(updateDoc(doc(as(ALICE), "publicProfiles", ALICE), { publicKey: "newkey" }));
  await assertFails(updateDoc(doc(as(ALICE), "publicProfiles", ALICE), { username: "admin" }));
  await assertFails(updateDoc(doc(as(EVE), "publicProfiles", ALICE), { publicKey: "evekey" }));
  await assertFails(setDoc(doc(as(EVE), "publicProfiles", "newuser"), { uid: "newuser", username: "x" }));
  await assertSucceeds(setDoc(doc(as("carol"), "publicProfiles", "carol"), { uid: "carol", username: "carol", publicKey: "k" }));
});

// ---------------------------------------------------------------- devices / deviceAccounts
test("devices: owner can read/query/delete; others cannot", async () => {
  await assertSucceeds(getDocs(query(collection(as(ALICE), "devices"), where("userId", "==", ALICE))));
  await assertSucceeds(getDocs(query(collection(as(ALICE), "devices"),
    where("userId", "==", ALICE), where("deviceId", "==", "devA"))));
  await assertFails(getDocs(query(collection(as(EVE), "devices"), where("userId", "==", ALICE))));
  await assertFails(getDoc(doc(as(EVE), "devices", "devA")));
  await assertFails(deleteDoc(doc(as(EVE), "devices", "devA")));
  await assertSucceeds(updateDoc(doc(as(ALICE), "devices", "devA"), { trustedAt: 123 }));
  await assertSucceeds(deleteDoc(doc(as(ALICE), "devices", "devA")));
});

test("devices: can only register a device under your own uid and its own id", async () => {
  await assertSucceeds(setDoc(doc(as(BOB), "devices", "devB"), { deviceId: "devB", userId: BOB, trusted: false }));
  await assertFails(setDoc(doc(as(BOB), "devices", "devC"), { deviceId: "devC", userId: ALICE }));
  await assertFails(setDoc(doc(as(BOB), "devices", "devD"), { deviceId: "other", userId: BOB }));
});

test("deviceAccounts: members of the device list only", async () => {
  await assertSucceeds(getDoc(doc(as(ALICE), "deviceAccounts", "devA")));
  await assertSucceeds(getDoc(doc(as(BOB), "deviceAccounts", "missing")));
  await assertFails(getDoc(doc(as(EVE), "deviceAccounts", "devA")));
  // a second account signs in on the device
  await assertSucceeds(setDoc(doc(as(BOB), "deviceAccounts", "devA"), { deviceId: "devA", accounts: [ALICE, BOB] }));
  // removing yourself is allowed
  await assertSucceeds(updateDoc(doc(as(ALICE), "deviceAccounts", "devA"), { accounts: [BOB] }));
});

// ---------------------------------------------------------------- globalChat
test("globalChat: signed-in users read; sender must be yourself with your real username", async () => {
  await assertSucceeds(getDoc(doc(as(EVE), "globalChat", "g1")));
  await assertFails(getDoc(doc(anon(), "globalChat", "g1")));
  await assertSucceeds(setDoc(doc(as(EVE), "globalChat", "g2"), { senderId: EVE, username: EVE, text: "hi", reactions: [] }));
  await assertFails(setDoc(doc(as(EVE), "globalChat", "g3"), { senderId: BOB, username: BOB, text: "spoof" }));
  await assertFails(setDoc(doc(as(EVE), "globalChat", "g4"), { senderId: EVE, username: BOB, text: "spoof name" }));
  await assertFails(setDoc(doc(as(EVE), "globalChat", "g5"), { senderId: EVE, username: EVE, text: "x".repeat(4001) }));
});

test("globalChat: only the sender edits/deletes; anyone may toggle their own reaction", async () => {
  await assertSucceeds(updateDoc(doc(as(BOB), "globalChat", "g1"), { text: "edited", edited: true, editedAt: 1 }));
  await assertFails(updateDoc(doc(as(EVE), "globalChat", "g1"), { text: "hacked", edited: true, editedAt: 1 }));
  await assertSucceeds(updateDoc(doc(as(EVE), "globalChat", "g1"), { reactions: ["eve:👍"] }));
  await assertSucceeds(updateDoc(doc(as(EVE), "globalChat", "g1"), { reactions: ["eve:❤"] })); // replace
  await assertFails(updateDoc(doc(as(EVE), "globalChat", "g1"), { reactions: ["eve:❤", "bob:😡"] })); // forge
  await assertSucceeds(updateDoc(doc(as(BOB), "globalChat", "g1"), { reactions: ["eve:❤", "bob:😂"] }));
  await assertFails(updateDoc(doc(as(EVE), "globalChat", "g1"), { reactions: ["eve:❤"] })); // remove bob's
  await assertFails(deleteDoc(doc(as(EVE), "globalChat", "g1")));
  await assertSucceeds(deleteDoc(doc(as(BOB), "globalChat", "g1")));
});

test("globalChat: account deletion query for own messages works", async () => {
  await assertSucceeds(getDocs(query(collection(as(BOB), "globalChat"), where("senderId", "==", BOB))));
});

// ---------------------------------------------------------------- privateChats
test("privateChats: only participants read the chat and its messages", async () => {
  await assertSucceeds(getDoc(doc(as(ALICE), "privateChats", CHAT)));
  await assertSucceeds(getDocs(collection(as(ALICE), "privateChats", CHAT, "messages")));
  await assertFails(getDoc(doc(as(EVE), "privateChats", CHAT)));
  await assertFails(getDocs(collection(as(EVE), "privateChats", CHAT, "messages")));
});

test("privateChats: chat list query by participants", async () => {
  await assertSucceeds(getDocs(query(collection(as(ALICE), "privateChats"), where("participants", "array-contains", ALICE))));
  await assertFails(getDocs(query(collection(as(EVE), "privateChats"), where("participants", "array-contains", ALICE))));
});

test("privateChats: create requires exactly the two members from the chat id", async () => {
  await assertSucceeds(setDoc(doc(as(ALICE), "privateChats", "alice_eve"), { participants: [ALICE, EVE], lastMessage: "", lastTimestamp: 1 }));
  await assertFails(setDoc(doc(as(EVE), "privateChats", "bob_carol"), { participants: [EVE, BOB] }));
  await assertFails(setDoc(doc(as(ALICE), "privateChats", "alice_dave"), { participants: [ALICE, "dave", EVE] }));
});

test("privateChats: participants update preview and only their own typing flag", async () => {
  await assertSucceeds(updateDoc(doc(as(ALICE), "privateChats", CHAT), { lastMessage: "c", lastTimestamp: 2 }));
  await assertSucceeds(updateDoc(doc(as(ALICE), "privateChats", CHAT), { typing_alice: true }));
  await assertFails(updateDoc(doc(as(ALICE), "privateChats", CHAT), { typing_bob: true }));
  await assertFails(updateDoc(doc(as(ALICE), "privateChats", CHAT), { participants: [ALICE, EVE] }));
});

test("private messages: send as yourself only, with your real username", async () => {
  await assertSucceeds(setDoc(doc(as(ALICE), "privateChats", CHAT, "messages", "m2"),
    { senderId: ALICE, username: ALICE, text: "cipher", read: false, reactions: [] }));
  await assertFails(setDoc(doc(as(ALICE), "privateChats", CHAT, "messages", "m3"),
    { senderId: BOB, username: BOB, text: "spoof" }));
  await assertFails(setDoc(doc(as(EVE), "privateChats", CHAT, "messages", "m4"),
    { senderId: EVE, username: EVE, text: "intrude" }));
  await assertFails(setDoc(doc(as(ALICE), "privateChats", CHAT, "messages", "m5"),
    { senderId: ALICE, username: ALICE, text: "x", mediaUrl: "a".repeat(950001) }));
});

test("private messages: read receipts only by the recipient, only to true", async () => {
  await assertFails(updateDoc(doc(as(BOB), "privateChats", CHAT, "messages", "m1"), { read: true }));
  await assertSucceeds(updateDoc(doc(as(ALICE), "privateChats", CHAT, "messages", "m1"), { read: true }));
  await assertFails(updateDoc(doc(as(ALICE), "privateChats", CHAT, "messages", "m1"), { read: false }));
});

test("private messages: sender edits re-encrypted fields; recipient cannot edit text", async () => {
  await assertSucceeds(updateDoc(doc(as(BOB), "privateChats", CHAT, "messages", "m1"), {
    text: "c2", senderText: "s2", replyToText: "", senderReplyText: "", ephemeralKey: "e2", edited: true, editedAt: 5,
  }));
  await assertFails(updateDoc(doc(as(ALICE), "privateChats", CHAT, "messages", "m1"), { text: "forged", edited: true, editedAt: 5 }));
  await assertFails(updateDoc(doc(as(BOB), "privateChats", CHAT, "messages", "m1"), { senderId: ALICE }));
});

test("private messages: reactions and deletion", async () => {
  await assertSucceeds(updateDoc(doc(as(ALICE), "privateChats", CHAT, "messages", "m1"), { reactions: ["alice:👍"] }));
  await assertFails(updateDoc(doc(as(ALICE), "privateChats", CHAT, "messages", "m1"), { reactions: ["alice:👍", "bob:👍"] }));
  await assertFails(deleteDoc(doc(as(ALICE), "privateChats", CHAT, "messages", "m1")));
  await assertSucceeds(deleteDoc(doc(as(BOB), "privateChats", CHAT, "messages", "m1")));
});

// ---------------------------------------------------------------- reports
const report = (over = {}) => ({
  reporterId: ALICE, reportedUserId: BOB, reportedUsername: BOB, messageHash: "h",
  chatType: "private", chatId: CHAT, messageId: "m1", reasonCode: "spam",
  createdAt: serverTimestamp(), status: "pending", ...over,
});

test("reports: participant can report a message once; nobody can read reports", async () => {
  await assertSucceeds(setDoc(doc(as(ALICE), "reports", "alice_m1"), report({ reportedText: "the message" })));
  await assertFails(setDoc(doc(as(ALICE), "reports", "alice_m1"), report())); // duplicate = update
  await assertFails(getDoc(doc(as(ALICE), "reports", "alice_m1")));
});

test("reports: global chat report without client-supplied text", async () => {
  await assertSucceeds(setDoc(doc(as(EVE), "reports", "eve_g1"),
    report({ reporterId: EVE, chatType: "global", chatId: "global", messageId: "g1" })));
  await assertFails(setDoc(doc(as(ALICE), "reports", "alice_g1"),
    report({ chatType: "global", chatId: "global", messageId: "g1", reportedText: "fake text" })));
});

test("reports: rejects forged, oversized and malformed reports", async () => {
  // outsider reporting a private message
  await assertFails(setDoc(doc(as(EVE), "reports", "eve_m1"), report({ reporterId: EVE })));
  // wrong reported user / nonexistent message / self-report
  await assertFails(setDoc(doc(as(ALICE), "reports", "alice_m1"), report({ reportedUserId: EVE })));
  await assertFails(setDoc(doc(as(ALICE), "reports", "alice_nope"), report({ messageId: "nope" })));
  // doc id must match reporter + message
  await assertFails(setDoc(doc(as(ALICE), "reports", "random"), report()));
  // unknown reason, extra fields, oversized text, client-set status, client timestamp
  await assertFails(setDoc(doc(as(ALICE), "reports", "alice_m1"), report({ reasonCode: "Спам" })));
  await assertFails(setDoc(doc(as(ALICE), "reports", "alice_m1"), report({ messageText: "x" })));
  await assertFails(setDoc(doc(as(ALICE), "reports", "alice_m1"), report({ reportedText: "x".repeat(1001) })));
  await assertFails(setDoc(doc(as(ALICE), "reports", "alice_m1"), report({ status: "ai_reviewed" })));
  await assertFails(setDoc(doc(as(ALICE), "reports", "alice_m1"), report({ createdAt: 1 })));
});

test("reports: a sender can't report their own message", async () => {
  await assertFails(setDoc(doc(as(BOB), "reports", "bob_m1"), report({ reporterId: BOB, reportedUserId: BOB })));
});

// ---------------------------------------------------------------- functionality preservation
test("legacy profile without uid field can still update presence and key, not username", async () => {
  await env.withSecurityRulesDisabled(async (ctx) => {
    await setDoc(doc(ctx.firestore(), "publicProfiles", "old"), { username: "old", publicKey: "k" });
  });
  await assertSucceeds(updateDoc(doc(as("old"), "publicProfiles", "old"), { isOnline: true, lastSeen: 1 }));
  await assertSucceeds(updateDoc(doc(as("old"), "publicProfiles", "old"), { publicKey: "rotated" }));
  await assertFails(updateDoc(doc(as("old"), "publicProfiles", "old"), { username: "new" }));
});

test("legacy device doc without deviceId field can be marked trusted", async () => {
  await env.withSecurityRulesDisabled(async (ctx) => {
    await setDoc(doc(ctx.firestore(), "devices", "devOld"), { userId: ALICE, deviceName: "Pixel" });
  });
  await assertSucceeds(updateDoc(doc(as(ALICE), "devices", "devOld"), { trustedAt: 1 }));
});

test("longest allowed message (4000 emoji, encrypted) fits the private-message limit", async () => {
  // 4000 emoji × 4 UTF-8 bytes + IV/tag, base64 → ~21.4k chars, for text and senderText
  const big = "A".repeat(21400);
  await assertSucceeds(setDoc(doc(as(ALICE), "privateChats", CHAT, "messages", "big"),
    { senderId: ALICE, username: ALICE, text: big, senderText: big, read: false, reactions: [] }));
  await assertSucceeds(setDoc(doc(as(ALICE), "globalChat", "big"),
    { senderId: ALICE, username: ALICE, text: "😀".repeat(2000), reactions: [] }));
});

test("second account signing in on a shared device takes over the device record", async () => {
  // App flow: get() the device doc (denied: it belongs to Alice), then set() it anyway in the failure path
  await assertFails(getDoc(doc(as(BOB), "devices", "devA")));
  await assertSucceeds(setDoc(doc(as(BOB), "devices", "devA"), { deviceId: "devA", userId: BOB, trusted: true }));
  await assertFails(getDoc(doc(as(ALICE), "devices", "devA")));
});

// ---------------------------------------------------------------- private data exposure
test("other users' private collections can't be listed or queried", async () => {
  await assertFails(getDocs(collection(as(EVE), "users")));
  await assertFails(getDocs(query(collection(as(EVE), "users"), where("hasPinEnabled", "==", true))));
  await assertFails(getDocs(collection(as(EVE), "devices")));
  await assertFails(getDocs(collection(as(EVE), "deviceAccounts")));
  await assertFails(getDocs(collection(as(EVE), "reports")));
  await assertFails(getDocs(collection(as(EVE), "privateChats")));
  await assertFails(getDocs(query(collection(as(EVE), "privateChats"), where("participants", "array-contains", BOB))));
});

test("anonymous clients can't read anything", async () => {
  for (const p of [["publicProfiles", ALICE], ["globalChat", "g1"], ["privateChats", CHAT], ["devices", "devA"], ["deviceAccounts", "devA"]]) {
    await assertFails(getDoc(doc(anon(), ...p)));
  }
});

// ---------------------------------------------------------------- everything else
test("server-only collections are closed to clients", async () => {
  await assertFails(getDoc(doc(as(ALICE), "moderationUsage", "2026-10-25")));
  await assertFails(setDoc(doc(as(ALICE), "moderationUsage", "2026-10-25"), { total: 0 }));
  await assertFails(setDoc(doc(as(ALICE), "anythingElse", "x"), { a: 1 }));
});
