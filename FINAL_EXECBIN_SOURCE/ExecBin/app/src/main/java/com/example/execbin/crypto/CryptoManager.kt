package com.example.execbin.crypto

import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.spec.ECGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Pure JVM cryptography manager for ExecBin's E2E encryption.
 *
 * Extracted from [MainActivity] into a standalone, dependency-free class so that:
 *   - It can be unit-tested on the JVM (no Android runtime required)
 *   - Crypto logic is auditable in one place
 *   - Business code doesn't mix crypto primitives with UI / Firestore
 *
 * This class is **stateless**. Callers own the private key material and pass it
 * in as encoded bytes. The only randomness source is [SecureRandom].
 *
 * Protocol summary:
 *   - Long-term identity: secp256r1 (P-256) ECDH keypair
 *   - Shared secret: ECDH(myPriv, theirPub) → HKDF-SHA256 → AES-256 key
 *   - Message encryption: AES-GCM, 12-byte IV, 128-bit tag, IV || ciphertext || tag
 *   - PFS: per-message ephemeral secp256r1 keypair. Ephemeral public ships with
 *     the ciphertext; ephemeral private is discarded immediately.
 *   - Safety number: iterated SHA-256 (5200x) of sorted-concatenated public keys,
 *     encoded as 12 groups of 5 decimal digits.
 *
 * This is NOT a cleanroom implementation of Signal Protocol / Double Ratchet.
 * For production E2E messaging, strongly consider replacing with libsignal.
 */
class CryptoManager(private val base64: Base64Codec) {

    // region AES-GCM symmetric encryption ───────────────────────────────────

    /**
     * Encrypts [plainText] with AES-256-GCM. Returns Base64(IV || ciphertext || tag).
     * Throws on failure so callers never silently send plaintext.
     */
    fun encryptMessage(plainText: String, key: SecretKeySpec): String {
        val combined = encryptBytes(plainText.toByteArray(Charsets.UTF_8), key)
        return base64.encode(combined)
    }

    /**
     * Decrypts a ciphertext produced by [encryptMessage]. Returns the plaintext,
     * or [DECRYPT_FAILED_SENTINEL] if decryption fails for any reason.
     */
    fun decryptMessage(cipherBase64: String, key: SecretKeySpec): String {
        return try {
            val combined = base64.decode(cipherBase64)
            val plain = decryptBytes(combined, key) ?: return DECRYPT_FAILED_SENTINEL
            String(plain, Charsets.UTF_8)
        } catch (_: Exception) {
            DECRYPT_FAILED_SENTINEL
        }
    }

    /** Encrypts arbitrary bytes with AES-256-GCM. Returns IV || ciphertext || tag. */
    fun encryptBytes(data: ByteArray, key: SecretKeySpec): ByteArray {
        val cipher = Cipher.getInstance(AES_GCM_TRANSFORM)
        val iv = ByteArray(IV_LEN)
        secureRandom.nextBytes(iv)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        val encrypted = cipher.doFinal(data)
        return iv + encrypted
    }

    /** Decrypts IV || ciphertext || tag produced by [encryptBytes]. Returns null on failure. */
    fun decryptBytes(data: ByteArray, key: SecretKeySpec): ByteArray? {
        return try {
            if (data.size < IV_LEN + 1) return null
            val iv = data.copyOfRange(0, IV_LEN)
            val ciphertext = data.copyOfRange(IV_LEN, data.size)
            val cipher = Cipher.getInstance(AES_GCM_TRANSFORM)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
            cipher.doFinal(ciphertext)
        } catch (_: Exception) {
            null
        }
    }

    // endregion

    // region Long-term keypair / ECDH ────────────────────────────────────────

    /** Generates a fresh secp256r1 keypair. Returns (publicKeyDER, privateKeyDER) as raw bytes. */
    fun generateIdentityKeyPair(): KeyPairBytes {
        val kg = KeyPairGenerator.getInstance(EC_ALGORITHM)
        kg.initialize(ECGenParameterSpec(EC_CURVE))
        val pair = kg.generateKeyPair()
        return KeyPairBytes(
            publicKey = pair.public.encoded,
            privateKey = pair.private.encoded
        )
    }

    /**
     * Derives the static shared AES-256 key between two parties via ECDH + HKDF-SHA256.
     *
     * @param myPrivateKeyBytes PKCS#8-encoded private key bytes (owner's key)
     * @param theirPublicKeyBase64 Base64(X.509-encoded public key)
     * @return AES-256 SecretKeySpec, or null on failure
     */
    fun deriveSharedKey(myPrivateKeyBytes: ByteArray, theirPublicKeyBase64: String): SecretKeySpec? {
        return try {
            if (theirPublicKeyBase64.isEmpty() || myPrivateKeyBytes.isEmpty()) return null
            val myPriv = KeyFactory.getInstance(EC_ALGORITHM)
                .generatePrivate(PKCS8EncodedKeySpec(myPrivateKeyBytes))
            val theirPub = KeyFactory.getInstance(EC_ALGORITHM)
                .generatePublic(X509EncodedKeySpec(base64.decode(theirPublicKeyBase64)))
            val shared = ecdh(myPriv, theirPub)
            SecretKeySpec(hkdfSha256(shared, HKDF_INFO_MSG), "AES")
        } catch (_: Exception) {
            null
        }
    }

    // endregion

    // region Perfect Forward Secrecy ─────────────────────────────────────────

    /**
     * Generates a one-shot PFS key pair for a single outgoing message.
     *
     * Returns [PfsKeys] containing:
     *   - recipientKey: AES key the recipient will derive via ECDH(their_priv, eph_pub)
     *   - senderKey:    AES key the sender retains to decrypt their own copy
     *   - ephemeralPublicKeyBase64: ships with the ciphertext
     *
     * The ephemeral private key is not returned — it is dropped from scope and garbage
     * collected, providing forward secrecy: even if [myStaticPublicKeyBase64] leaks later,
     * past PFS-encrypted messages remain secure.
     */
    fun generatePfsKeys(
        theirStaticPublicKeyBase64: String,
        myStaticPublicKeyBase64: String
    ): PfsKeys? {
        return try {
            val kg = KeyPairGenerator.getInstance(EC_ALGORITHM)
            kg.initialize(ECGenParameterSpec(EC_CURVE))
            val eph = kg.generateKeyPair()
            val ephPubB64 = base64.encode(eph.public.encoded)

            val recipientKey = derivePfsKey(eph.private, theirStaticPublicKeyBase64) ?: return null
            val senderKey = derivePfsKey(eph.private, myStaticPublicKeyBase64) ?: return null

            PfsKeys(recipientKey, senderKey, ephPubB64)
            // eph.private falls out of scope here
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Derives a PFS decryption key on the receiving side using the local static
     * private key and the ephemeral public key from the incoming message.
     */
    fun derivePfsDecryptionKey(
        myPrivateKeyBytes: ByteArray,
        ephemeralPublicKeyBase64: String
    ): SecretKeySpec? {
        return try {
            val myPriv = KeyFactory.getInstance(EC_ALGORITHM)
                .generatePrivate(PKCS8EncodedKeySpec(myPrivateKeyBytes))
            derivePfsKey(myPriv, ephemeralPublicKeyBase64)
        } catch (_: Exception) {
            null
        }
    }

    private fun derivePfsKey(privateKey: PrivateKey, peerPublicKeyBase64: String): SecretKeySpec? {
        return try {
            val peerPub = KeyFactory.getInstance(EC_ALGORITHM)
                .generatePublic(X509EncodedKeySpec(base64.decode(peerPublicKeyBase64)))
            val shared = ecdh(privateKey, peerPub)
            SecretKeySpec(hkdfSha256(shared, HKDF_INFO_PFS), "AES")
        } catch (_: Exception) {
            null
        }
    }

    // endregion

    // region Safety number ────────────────────────────────────────────────────

    /**
     * Computes a Signal-style safety number: 60 decimal digits in 12 space-separated
     * groups of 5, derived from 5200 iterations of SHA-256 over the sorted concatenation
     * of both users' public keys.
     *
     * The number is symmetric — both parties compute the same value iff their keys match.
     * Returns "" if either key is empty.
     */
    fun computeSafetyNumber(myPublicKeyBase64: String, theirPublicKeyBase64: String): String {
        if (myPublicKeyBase64.isEmpty() || theirPublicKeyBase64.isEmpty()) return ""
        val combined = if (myPublicKeyBase64 < theirPublicKeyBase64) {
            myPublicKeyBase64 + theirPublicKeyBase64
        } else {
            theirPublicKeyBase64 + myPublicKeyBase64
        }
        return try {
            val md = MessageDigest.getInstance("SHA-256")
            var hash = combined.toByteArray(Charsets.UTF_8)
            repeat(SAFETY_NUMBER_ITERATIONS) { hash = md.digest(hash) }
            buildString {
                for (i in 0 until 12) {
                    if (i > 0) append(' ')
                    val b0 = hash[i * 2].toInt() and 0xFF
                    val b1 = hash[i * 2 + 1].toInt() and 0xFF
                    val n = ((b0 shl 8) or b1) % 100_000
                    // Locale.ROOT: always ASCII digits, so both phones show and encode the same characters
                    append(String.format(java.util.Locale.ROOT, "%05d", n))
                }
            }
        } catch (_: Exception) {
            ""
        }
    }

    // endregion

    // region Internals ────────────────────────────────────────────────────────

    private fun ecdh(priv: PrivateKey, pub: java.security.PublicKey): ByteArray {
        val ka = KeyAgreement.getInstance("ECDH")
        ka.init(priv)
        ka.doPhase(pub, true)
        return ka.generateSecret()
    }

    /** HKDF-SHA256 (RFC 5869) with all-zero salt and a single L=32 byte output block. */
    private fun hkdfSha256(ikm: ByteArray, info: String): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        // Extract
        mac.init(SecretKeySpec(ByteArray(32), "HmacSHA256"))
        val prk = mac.doFinal(ikm)
        // Expand (T = HMAC(PRK, info || 0x01), single block)
        mac.init(SecretKeySpec(prk, "HmacSHA256"))
        mac.update(info.toByteArray(Charsets.UTF_8))
        return mac.doFinal(byteArrayOf(0x01))
    }

    // endregion

    /**
     * True if [publicKeyBase64] is Base64(X.509 DER) of an EC public key on P-256.
     * Used to reject malformed/hostile keys published by a contact before any key handling.
     */
    fun isValidP256PublicKey(publicKeyBase64: String): Boolean {
        return try {
            if (publicKeyBase64.isEmpty()) return false
            val key = KeyFactory.getInstance(EC_ALGORITHM)
                .generatePublic(X509EncodedKeySpec(base64.decode(publicKeyBase64)))
            key is java.security.interfaces.ECPublicKey && key.params.curve.field.fieldSize == 256 &&
                key.params.order == P256_ORDER
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Base64 (standard alphabet, no line breaks). Injected because java.util.Base64 needs API 26
     * while the app supports API 23: the app passes android.util.Base64, JVM tests pass java.util.Base64.
     */
    interface Base64Codec {
        fun encode(bytes: ByteArray): String
        fun decode(text: String): ByteArray
    }

    companion object {
        const val DECRYPT_FAILED_SENTINEL = "__DECRYPT_FAILED__"

        private val P256_ORDER = java.math.BigInteger(
            "FFFFFFFF00000000FFFFFFFFFFFFFFFFBCE6FAADA7179E84F3B9CAC2FC632551", 16
        )

        private const val EC_ALGORITHM = "EC"
        private const val EC_CURVE = "secp256r1"
        private const val AES_GCM_TRANSFORM = "AES/GCM/NoPadding"
        private const val IV_LEN = 12
        private const val GCM_TAG_BITS = 128

        private const val HKDF_INFO_MSG = "execbin-msg-key"
        private const val HKDF_INFO_PFS = "execbin-pfs-msg-key"

        private const val SAFETY_NUMBER_ITERATIONS = 5200

        private val secureRandom = SecureRandom()
    }

    // region Data classes ─────────────────────────────────────────────────────

    data class KeyPairBytes(
        /** X.509 DER-encoded public key (suitable for Base64 → Firestore). */
        val publicKey: ByteArray,
        /** PKCS#8 DER-encoded private key (suitable for encrypted local storage). */
        val privateKey: ByteArray,
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is KeyPairBytes) return false
            return publicKey.contentEquals(other.publicKey) &&
                privateKey.contentEquals(other.privateKey)
        }

        override fun hashCode(): Int =
            31 * publicKey.contentHashCode() + privateKey.contentHashCode()
    }

    data class PfsKeys(
        /** Key the recipient will derive after they see the ephemeral pub. */
        val recipientKey: SecretKeySpec,
        /** Key the sender retains to decrypt their own stored ciphertext copy. */
        val senderKey: SecretKeySpec,
        /** Ephemeral public key (X.509 DER, Base64) — ships with the ciphertext. */
        val ephemeralPublicKeyBase64: String,
    )

    // endregion
}
