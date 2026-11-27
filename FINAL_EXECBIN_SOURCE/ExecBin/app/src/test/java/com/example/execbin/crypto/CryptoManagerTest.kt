package com.example.execbin.crypto

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Base64
import javax.crypto.spec.SecretKeySpec
import kotlin.random.Random

/**
 * Unit tests for [CryptoManager].
 *
 * These run on the JVM (no Android runtime) thanks to the dependency-free refactor.
 * Each test focuses on one crypto primitive; together they verify the full E2E
 * handshake + messaging flow between two synthetic "users" (Alice and Bob).
 */
class CryptoManagerTest {

    private lateinit var crypto: CryptoManager

    @Before
    fun setUp() {
        crypto = CryptoManager(JvmBase64)
    }

    // region AES-GCM roundtrip ──────────────────────────────────────────────

    @Test
    fun `encryptMessage then decryptMessage returns the original plaintext`() {
        val key = randomAesKey()
        val plaintext = "hello world — привет — 你好"

        val ciphertext = crypto.encryptMessage(plaintext, key)
        val decrypted = crypto.decryptMessage(ciphertext, key)

        assertEquals(plaintext, decrypted)
    }

    @Test
    fun `encryptMessage produces different ciphertexts for the same input (fresh IV)`() {
        val key = randomAesKey()
        val plaintext = "same input, should encrypt differently each time"

        val c1 = crypto.encryptMessage(plaintext, key)
        val c2 = crypto.encryptMessage(plaintext, key)

        assertNotEquals("IV must be fresh per encryption", c1, c2)
        assertEquals(plaintext, crypto.decryptMessage(c1, key))
        assertEquals(plaintext, crypto.decryptMessage(c2, key))
    }

    @Test
    fun `decryptMessage returns sentinel for tampered ciphertext`() {
        val key = randomAesKey()
        val ciphertext = crypto.encryptMessage("authentic", key)

        // Flip a byte in the ciphertext (after Base64 decode)
        val raw = Base64.getDecoder().decode(ciphertext).copyOf()
        raw[raw.size - 1] = (raw[raw.size - 1].toInt() xor 0x01).toByte()
        val tampered = Base64.getEncoder().encodeToString(raw)

        val result = crypto.decryptMessage(tampered, key)
        assertEquals(CryptoManager.DECRYPT_FAILED_SENTINEL, result)
    }

    @Test
    fun `decryptMessage returns sentinel when using wrong key`() {
        val keyA = randomAesKey()
        val keyB = randomAesKey()
        val ciphertext = crypto.encryptMessage("secret", keyA)

        val result = crypto.decryptMessage(ciphertext, keyB)
        assertEquals(CryptoManager.DECRYPT_FAILED_SENTINEL, result)
    }

    @Test
    fun `encryptBytes and decryptBytes roundtrip binary data`() {
        val key = randomAesKey()
        val data = Random.nextBytes(2048)

        val encrypted = crypto.encryptBytes(data, key)
        val decrypted = crypto.decryptBytes(encrypted, key)

        assertNotNull(decrypted)
        assertArrayEquals(data, decrypted)
    }

    @Test
    fun `decryptBytes returns null for too-short input`() {
        val key = randomAesKey()
        assertNull(crypto.decryptBytes(ByteArray(5), key))
    }

    // endregion

    // region ECDH shared key derivation ─────────────────────────────────────

    @Test
    fun `both parties derive the same shared key via ECDH`() {
        val alice = crypto.generateIdentityKeyPair()
        val bob = crypto.generateIdentityKeyPair()

        val alicePubB64 = Base64.getEncoder().encodeToString(alice.publicKey)
        val bobPubB64 = Base64.getEncoder().encodeToString(bob.publicKey)

        val aliceView = crypto.deriveSharedKey(alice.privateKey, bobPubB64)
        val bobView = crypto.deriveSharedKey(bob.privateKey, alicePubB64)

        assertNotNull(aliceView)
        assertNotNull(bobView)
        assertArrayEquals(aliceView!!.encoded, bobView!!.encoded)
    }

    @Test
    fun `shared key is 32 bytes (AES-256)`() {
        val alice = crypto.generateIdentityKeyPair()
        val bob = crypto.generateIdentityKeyPair()
        val bobPubB64 = Base64.getEncoder().encodeToString(bob.publicKey)

        val key = crypto.deriveSharedKey(alice.privateKey, bobPubB64)
        assertNotNull(key)
        assertEquals(32, key!!.encoded.size)
    }

    @Test
    fun `different keypairs produce different shared secrets`() {
        val alice = crypto.generateIdentityKeyPair()
        val bob = crypto.generateIdentityKeyPair()
        val charlie = crypto.generateIdentityKeyPair()

        val bobPubB64 = Base64.getEncoder().encodeToString(bob.publicKey)
        val charliePubB64 = Base64.getEncoder().encodeToString(charlie.publicKey)

        val aliceBob = crypto.deriveSharedKey(alice.privateKey, bobPubB64)!!
        val aliceCharlie = crypto.deriveSharedKey(alice.privateKey, charliePubB64)!!

        assertFalse(
            "Alice↔Bob and Alice↔Charlie must not share the same key",
            aliceBob.encoded.contentEquals(aliceCharlie.encoded)
        )
    }

    @Test
    fun `deriveSharedKey returns null for empty public key`() {
        val alice = crypto.generateIdentityKeyPair()
        assertNull(crypto.deriveSharedKey(alice.privateKey, ""))
    }

    @Test
    fun `deriveSharedKey returns null for empty private key`() {
        val bob = crypto.generateIdentityKeyPair()
        val bobPubB64 = Base64.getEncoder().encodeToString(bob.publicKey)
        assertNull(crypto.deriveSharedKey(ByteArray(0), bobPubB64))
    }

    // endregion

    // region End-to-end: ECDH + AES ─────────────────────────────────────────

    @Test
    fun `Alice encrypts with shared key, Bob decrypts with his shared key`() {
        val alice = crypto.generateIdentityKeyPair()
        val bob = crypto.generateIdentityKeyPair()
        val alicePubB64 = Base64.getEncoder().encodeToString(alice.publicKey)
        val bobPubB64 = Base64.getEncoder().encodeToString(bob.publicKey)

        val aliceShared = crypto.deriveSharedKey(alice.privateKey, bobPubB64)!!
        val bobShared = crypto.deriveSharedKey(bob.privateKey, alicePubB64)!!

        val plaintext = "send this securely"
        val ciphertext = crypto.encryptMessage(plaintext, aliceShared)

        assertEquals(plaintext, crypto.decryptMessage(ciphertext, bobShared))
    }

    // endregion

    // region PFS ─────────────────────────────────────────────────────────────

    @Test
    fun `PFS sender and recipient keys each decrypt their intended side`() {
        val alice = crypto.generateIdentityKeyPair()
        val bob = crypto.generateIdentityKeyPair()
        val alicePubB64 = Base64.getEncoder().encodeToString(alice.publicKey)
        val bobPubB64 = Base64.getEncoder().encodeToString(bob.publicKey)

        // Alice sends to Bob
        val pfs = crypto.generatePfsKeys(
            theirStaticPublicKeyBase64 = bobPubB64,
            myStaticPublicKeyBase64 = alicePubB64
        )!!

        val plaintext = "forward-secret payload"
        val ctxtForBob = crypto.encryptMessage(plaintext, pfs.recipientKey)
        val ctxtForAlice = crypto.encryptMessage(plaintext, pfs.senderKey)

        // Bob derives his key from his static priv + ephemeral pub
        val bobDerived = crypto.derivePfsDecryptionKey(bob.privateKey, pfs.ephemeralPublicKeyBase64)!!
        // Alice derives her key from her static priv + ephemeral pub
        val aliceDerived = crypto.derivePfsDecryptionKey(alice.privateKey, pfs.ephemeralPublicKeyBase64)!!

        assertEquals(plaintext, crypto.decryptMessage(ctxtForBob, bobDerived))
        assertEquals(plaintext, crypto.decryptMessage(ctxtForAlice, aliceDerived))
    }

    @Test
    fun `PFS ephemeral keys are unique per call`() {
        val alice = crypto.generateIdentityKeyPair()
        val bob = crypto.generateIdentityKeyPair()
        val alicePubB64 = Base64.getEncoder().encodeToString(alice.publicKey)
        val bobPubB64 = Base64.getEncoder().encodeToString(bob.publicKey)

        val k1 = crypto.generatePfsKeys(bobPubB64, alicePubB64)!!
        val k2 = crypto.generatePfsKeys(bobPubB64, alicePubB64)!!

        assertNotEquals(k1.ephemeralPublicKeyBase64, k2.ephemeralPublicKeyBase64)
        assertFalse(
            "PFS recipient keys must differ per message",
            k1.recipientKey.encoded.contentEquals(k2.recipientKey.encoded)
        )
    }

    @Test
    fun `PFS sender key differs from recipient key (prevents cross-decryption)`() {
        val alice = crypto.generateIdentityKeyPair()
        val bob = crypto.generateIdentityKeyPair()
        val alicePubB64 = Base64.getEncoder().encodeToString(alice.publicKey)
        val bobPubB64 = Base64.getEncoder().encodeToString(bob.publicKey)

        val pfs = crypto.generatePfsKeys(bobPubB64, alicePubB64)!!
        assertFalse(
            "Sender and recipient keys must not be identical",
            pfs.senderKey.encoded.contentEquals(pfs.recipientKey.encoded)
        )
    }

    // endregion

    // region Safety number ──────────────────────────────────────────────────

    @Test
    fun `safety number is symmetric (order-independent)`() {
        val alice = crypto.generateIdentityKeyPair()
        val bob = crypto.generateIdentityKeyPair()
        val alicePubB64 = Base64.getEncoder().encodeToString(alice.publicKey)
        val bobPubB64 = Base64.getEncoder().encodeToString(bob.publicKey)

        val aliceSide = crypto.computeSafetyNumber(alicePubB64, bobPubB64)
        val bobSide = crypto.computeSafetyNumber(bobPubB64, alicePubB64)

        assertEquals(aliceSide, bobSide)
    }

    @Test
    fun `safety number format is 12 groups of 5 digits separated by spaces`() {
        val alice = crypto.generateIdentityKeyPair()
        val bob = crypto.generateIdentityKeyPair()
        val alicePubB64 = Base64.getEncoder().encodeToString(alice.publicKey)
        val bobPubB64 = Base64.getEncoder().encodeToString(bob.publicKey)

        val number = crypto.computeSafetyNumber(alicePubB64, bobPubB64)
        val groups = number.split(' ')

        assertEquals(12, groups.size)
        groups.forEach { g ->
            assertEquals("each group must be 5 chars: '$g'", 5, g.length)
            assertTrue("each group must be digits: '$g'", g.all { it.isDigit() })
        }
    }

    @Test
    fun `safety number changes when either party's key changes`() {
        val alice = crypto.generateIdentityKeyPair()
        val bob = crypto.generateIdentityKeyPair()
        val charlie = crypto.generateIdentityKeyPair()

        val alicePub = Base64.getEncoder().encodeToString(alice.publicKey)
        val bobPub = Base64.getEncoder().encodeToString(bob.publicKey)
        val charliePub = Base64.getEncoder().encodeToString(charlie.publicKey)

        val aliceBob = crypto.computeSafetyNumber(alicePub, bobPub)
        val aliceCharlie = crypto.computeSafetyNumber(alicePub, charliePub)

        assertNotEquals(aliceBob, aliceCharlie)
    }

    @Test
    fun `safety number returns empty string when a key is missing`() {
        val alice = crypto.generateIdentityKeyPair()
        val alicePub = Base64.getEncoder().encodeToString(alice.publicKey)

        assertEquals("", crypto.computeSafetyNumber("", alicePub))
        assertEquals("", crypto.computeSafetyNumber(alicePub, ""))
        assertEquals("", crypto.computeSafetyNumber("", ""))
    }

    // endregion

    // region Regression tests (security review) ──────────────────────────────

    @Test
    fun `isValidP256PublicKey accepts real P-256 keys`() {
        val alice = crypto.generateIdentityKeyPair()
        assertTrue(crypto.isValidP256PublicKey(Base64.getEncoder().encodeToString(alice.publicKey)))
    }

    @Test
    fun `isValidP256PublicKey rejects garbage, empty, other curves and RSA`() {
        assertFalse(crypto.isValidP256PublicKey(""))
        assertFalse(crypto.isValidP256PublicKey("not base64 !!"))
        assertFalse(crypto.isValidP256PublicKey(Base64.getEncoder().encodeToString(ByteArray(91))))

        val p384 = java.security.KeyPairGenerator.getInstance("EC").apply {
            initialize(java.security.spec.ECGenParameterSpec("secp384r1"))
        }.generateKeyPair()
        assertFalse(crypto.isValidP256PublicKey(Base64.getEncoder().encodeToString(p384.public.encoded)))

        val rsa = java.security.KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        assertFalse(crypto.isValidP256PublicKey(Base64.getEncoder().encodeToString(rsa.public.encoded)))
    }

    @Test
    fun `ECDH with the wrong private key still derives a key, so fallbacks must check decryption`() {
        // Regression: the app's post-rotation fallback used to trigger only when key *derivation* failed,
        // which never happens. Decryption with the current key fails; the previous key must be tried next.
        val bobOld = crypto.generateIdentityKeyPair()
        val bobNew = crypto.generateIdentityKeyPair()
        val alice = crypto.generateIdentityKeyPair()
        val bobOldPub = Base64.getEncoder().encodeToString(bobOld.publicKey)
        val alicePub = Base64.getEncoder().encodeToString(alice.publicKey)

        val pfs = crypto.generatePfsKeys(bobOldPub, alicePub)!!
        val cipher = crypto.encryptMessage("sent before Bob rotated", pfs.recipientKey)

        val withNewKey = crypto.derivePfsDecryptionKey(bobNew.privateKey, pfs.ephemeralPublicKeyBase64)
        assertNotNull("derivation succeeds even with the wrong key", withNewKey)
        assertEquals(CryptoManager.DECRYPT_FAILED_SENTINEL, crypto.decryptMessage(cipher, withNewKey!!))

        val withOldKey = crypto.derivePfsDecryptionKey(bobOld.privateKey, pfs.ephemeralPublicKeyBase64)!!
        assertEquals("sent before Bob rotated", crypto.decryptMessage(cipher, withOldKey))
    }

    @Test
    fun `edited message re-encrypted with a fresh ephemeral key decrypts for both sides`() {
        // Regression: edits used to replace only "text" with a static-key ciphertext while the message kept
        // its old ephemeralKey, so the recipient couldn't decrypt edited messages.
        val alice = crypto.generateIdentityKeyPair()
        val bob = crypto.generateIdentityKeyPair()
        val alicePub = Base64.getEncoder().encodeToString(alice.publicKey)
        val bobPub = Base64.getEncoder().encodeToString(bob.publicKey)

        val original = crypto.generatePfsKeys(bobPub, alicePub)!!
        val edit = crypto.generatePfsKeys(bobPub, alicePub)!!
        val editedText = crypto.encryptMessage("edited", edit.recipientKey)
        val editedSenderCopy = crypto.encryptMessage("edited", edit.senderKey)

        val bobKey = crypto.derivePfsDecryptionKey(bob.privateKey, edit.ephemeralPublicKeyBase64)!!
        val aliceKey = crypto.derivePfsDecryptionKey(alice.privateKey, edit.ephemeralPublicKeyBase64)!!
        assertEquals("edited", crypto.decryptMessage(editedText, bobKey))
        assertEquals("edited", crypto.decryptMessage(editedSenderCopy, aliceKey))

        // The old ephemeral key can't decrypt the edit — which is why ephemeralKey must be updated with it
        val staleKey = crypto.derivePfsDecryptionKey(bob.privateKey, original.ephemeralPublicKeyBase64)!!
        assertEquals(CryptoManager.DECRYPT_FAILED_SENTINEL, crypto.decryptMessage(editedText, staleKey))
    }

    @Test
    fun `documents limited forward secrecy - recipient static key decrypts past per-message ciphertexts`() {
        // Not a bug to fix in place: per-message ephemeral *sender* keys do not give forward secrecy against
        // compromise of the recipient's long-term key. Only key rotation (and deleting old keys) bounds it.
        val alice = crypto.generateIdentityKeyPair()
        val bob = crypto.generateIdentityKeyPair()
        val pfs = crypto.generatePfsKeys(
            Base64.getEncoder().encodeToString(bob.publicKey),
            Base64.getEncoder().encodeToString(alice.publicKey)
        )!!
        val cipher = crypto.encryptMessage("past message", pfs.recipientKey)

        val stolenBobKey = bob.privateKey
        val attackerKey = crypto.derivePfsDecryptionKey(stolenBobKey, pfs.ephemeralPublicKeyBase64)!!
        assertEquals("past message", crypto.decryptMessage(cipher, attackerKey))
    }

    @Test
    fun `safety number uses ASCII digits regardless of device locale`() {
        // Regression: String.format without a locale produced Arabic-Indic digits on Arabic-locale phones,
        // so two users comparing safety numbers saw different characters for the same value.
        val alice = Base64.getEncoder().encodeToString(crypto.generateIdentityKeyPair().publicKey)
        val bob = Base64.getEncoder().encodeToString(crypto.generateIdentityKeyPair().publicKey)
        val expected = crypto.computeSafetyNumber(alice, bob)
        val original = java.util.Locale.getDefault()
        try {
            for (tag in listOf("ar-EG", "fa-IR", "hi-IN-u-nu-deva", "th-TH-u-nu-thai")) {
                java.util.Locale.setDefault(java.util.Locale.forLanguageTag(tag))
                val number = crypto.computeSafetyNumber(alice, bob)
                assertTrue("$tag produced non-ASCII digits: $number", number.matches(Regex("[0-9 ]+")))
                assertEquals(expected, number)
            }
        } finally {
            java.util.Locale.setDefault(original)
        }
    }

    @Test
    fun `media encrypted with the static shared key decrypts only with the matching pair`() {
        val alice = crypto.generateIdentityKeyPair()
        val bob = crypto.generateIdentityKeyPair()
        val eve = crypto.generateIdentityKeyPair()
        val bobPub = Base64.getEncoder().encodeToString(bob.publicKey)
        val alicePub = Base64.getEncoder().encodeToString(alice.publicKey)

        val aliceKey = crypto.deriveSharedKey(alice.privateKey, bobPub)!!
        val bobKey = crypto.deriveSharedKey(bob.privateKey, alicePub)!!
        val eveKey = crypto.deriveSharedKey(eve.privateKey, alicePub)!!
        val photo = Random.nextBytes(4096)

        val encrypted = crypto.encryptBytes(photo, aliceKey)
        assertArrayEquals(photo, crypto.decryptBytes(encrypted, bobKey))
        assertNull(crypto.decryptBytes(encrypted, eveKey))
    }

    // endregion

    // region Helpers ─────────────────────────────────────────────────────────

    private object JvmBase64 : CryptoManager.Base64Codec {
        override fun encode(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)
        override fun decode(text: String): ByteArray = Base64.getDecoder().decode(text)
    }

    private fun randomAesKey(): SecretKeySpec {
        val raw = ByteArray(32)
        Random.nextBytes(raw)
        return SecretKeySpec(raw, "AES")
    }

    // endregion
}
