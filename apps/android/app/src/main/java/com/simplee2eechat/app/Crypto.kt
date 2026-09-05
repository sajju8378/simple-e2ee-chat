package com.simplee2eechat.app

import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.random.Random

object Crypto {
    data class Generated(val publicKey: String, val privateKey: String)

    fun generateKeyPair(): Generated {
        val generator = KeyPairGenerator.getInstance("RSA")
        generator.initialize(2048)
        val pair = generator.generateKeyPair()
        return Generated(encode(pair.public.encoded), encode(pair.private.encoded))
    }

    fun passwordHash(password: String): String = encode(
        MessageDigest.getInstance("SHA-256").digest(password.toByteArray(StandardCharsets.UTF_8))
    )

    fun encrypt(plain: String, recipientPublicKey: String): Map<String, Any> {
        val aes = ByteArray(32).also { Random.nextBytes(it) }
        val iv = ByteArray(12).also { Random.nextBytes(it) }
        val gcm = Cipher.getInstance("AES/GCM/NoPadding")
        gcm.init(Cipher.ENCRYPT_MODE, SecretKeySpec(aes, "AES"), GCMParameterSpec(128, iv))
        val ciphertext = gcm.doFinal(plain.toByteArray(StandardCharsets.UTF_8))

        val rsa = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding")
        rsa.init(Cipher.ENCRYPT_MODE, decodePublicKey(recipientPublicKey))
        val wrappedKey = rsa.doFinal(aes)

        return mapOf(
            "v" to 1,
            "alg" to "RSA-OAEP-256/AES-256-GCM",
            "key" to encode(wrappedKey),
            "iv" to encode(iv),
            "ciphertext" to encode(ciphertext)
        )
    }

    fun decrypt(envelope: Map<String, Any?>, privateKey: String): String {
        val rsa = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding")
        rsa.init(Cipher.DECRYPT_MODE, decodePrivateKey(privateKey))
        val aes = rsa.doFinal(decode(envelope["key"].toString()))

        val gcm = Cipher.getInstance("AES/GCM/NoPadding")
        gcm.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(aes, "AES"),
            GCMParameterSpec(128, decode(envelope["iv"].toString()))
        )
        return String(gcm.doFinal(decode(envelope["ciphertext"].toString())), StandardCharsets.UTF_8)
    }

    private fun decodePublicKey(value: String) = KeyFactory.getInstance("RSA")
        .generatePublic(X509EncodedKeySpec(decode(value)))

    private fun decodePrivateKey(value: String) = KeyFactory.getInstance("RSA")
        .generatePrivate(PKCS8EncodedKeySpec(decode(value)))

    private fun encode(bytes: ByteArray) = Base64.encodeToString(bytes, Base64.NO_WRAP)
    private fun decode(value: String) = Base64.decode(value, Base64.DEFAULT)
}
