package com.simplee2eechat.app

import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.SecureRandom
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
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
        java.security.MessageDigest.getInstance("SHA-256").digest(password.toByteArray(StandardCharsets.UTF_8))
    )

    // The server stores only this opaque blob. It cannot decrypt the private key because
    // the encryption key is derived locally from the user's password.
    fun encryptPrivateKeyBackup(privateKey: String, password: String): String {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val aes = deriveBackupKey(password, salt)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, aes, GCMParameterSpec(128, iv))
        val body = c.doFinal(privateKey.toByteArray(StandardCharsets.UTF_8))
        return "1.${encode(salt)}.${encode(iv)}.${encode(body)}"
    }

    fun decryptPrivateKeyBackup(backup: String, password: String): String {
        val parts = backup.split('.')
        if (parts.size != 4 || parts[0] != "1") throw IllegalArgumentException("Invalid encrypted key backup")
        val salt = decode(parts[1]); val iv = decode(parts[2]); val body = decode(parts[3])
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, deriveBackupKey(password, salt), GCMParameterSpec(128, iv))
        return String(c.doFinal(body), StandardCharsets.UTF_8)
    }

    private fun deriveBackupKey(password: String, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password.toCharArray(), salt, 150_000, 256)
        val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        spec.clearPassword()
        return SecretKeySpec(bytes, "AES")
    }

    fun encrypt(plain: String, recipientPublicKey: String): Map<String, Any> {
        val aes = ByteArray(32).also { Random.nextBytes(it) }
        val iv = ByteArray(12).also { Random.nextBytes(it) }
        val gcm = Cipher.getInstance("AES/GCM/NoPadding")
        gcm.init(Cipher.ENCRYPT_MODE, SecretKeySpec(aes, "AES"), GCMParameterSpec(128, iv))
        val ciphertext = gcm.doFinal(plain.toByteArray(StandardCharsets.UTF_8))

        val rsa = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding")
        rsa.init(Cipher.ENCRYPT_MODE, decodePublicKey(recipientPublicKey))
        val wrappedKey = rsa.doFinal(aes)

        return mapOf("v" to 1,"alg" to "RSA-OAEP-256/AES-256-GCM","key" to encode(wrappedKey),"iv" to encode(iv),"ciphertext" to encode(ciphertext))
    }

    fun decrypt(envelope: Map<String, Any?>, privateKey: String): String {
        val rsa = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding")
        rsa.init(Cipher.DECRYPT_MODE, decodePrivateKey(privateKey))
        val aes = rsa.doFinal(decode(envelope["key"].toString()))
        val gcm = Cipher.getInstance("AES/GCM/NoPadding")
        gcm.init(Cipher.DECRYPT_MODE, SecretKeySpec(aes, "AES"), GCMParameterSpec(128, decode(envelope["iv"].toString())))
        return String(gcm.doFinal(decode(envelope["ciphertext"].toString())), StandardCharsets.UTF_8)
    }

    private fun decodePublicKey(value: String) = KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(decode(value)))
    private fun decodePrivateKey(value: String) = KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(decode(value)))
    private fun encode(bytes: ByteArray) = Base64.encodeToString(bytes, Base64.NO_WRAP)
    private fun decode(value: String) = Base64.decode(value, Base64.DEFAULT)
}
