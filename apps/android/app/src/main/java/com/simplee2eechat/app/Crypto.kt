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
        val g=KeyPairGenerator.getInstance("RSA");g.initialize(2048);val kp=g.generateKeyPair()
        return Generated(enc(kp.public.encoded),enc(kp.private.encoded))
    }
    fun passwordHash(password:String):String=enc(MessageDigest.getInstance("SHA-256").digest(password.toByteArray(StandardCharsets.UTF_8)))
    fun encrypt(plain:String,recipientPublicKey:String):Map<String,Any>{
        val aes=ByteArray(32).also{Random.nextBytes(it)};val iv=ByteArray(12).also{Random.nextBytes(it)}
        val g=Cipher.getInstance("AES/GCM/NoPadding");g.init(Cipher.ENCRYPT_MODE,SecretKeySpec(aes,"AES"),GCMParameterSpec(128,iv))
        val ciphertext=g.doFinal(plain.toByteArray(StandardCharsets.UTF_8))
        val r=Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");r.init(Cipher.ENCRYPT_MODE,publicKey(recipientPublicKey))
        return mapOf("v" to 1,"alg" to "RSA-OAEP-256/AES-256-GCM","key" to enc(r.doFinal(aes)),"iv" to enc(iv),"ciphertext" to enc(ciphertext))
    }
    fun decrypt(envelope:Map<String,Any?>,privateKey:String):String{
        val r=Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");r.init(Cipher.DECRYPT_MODE,privateKey(privateKey))
        val aes=r.doFinal(dec(envelope["key"].toString()));val g=Cipher.getInstance("AES/GCM/NoPadding")
        g.init(Cipher.DECRYPT_MODE,SecretKeySpec(aes,"AES"),GCMParameterSpec(128,dec(envelope["iv"].toString())))
        return String(g.doFinal(dec(envelope["ciphertext"].toString())),StandardCharsets.UTF_8)
    }
    private fun public(s:String)=KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(dec(s)))
    private fun privateKey(s:String)=KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(dec(s)))
    private fun enc(b:ByteArray)=Base64.encodeToString(b,Base64.NO_WRAP)
    private fun dec(s:String)=Base64.decode(s,Base64.DEFAULT)
}
