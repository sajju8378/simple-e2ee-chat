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
    fun generateKeyPair(): Generated { val g=KeyPairGenerator.getInstance("RSA"); g.initialize(2048); val p=g.generateKeyPair(); return Generated(enc(p.public.encoded),enc(p.private.encoded)) }
    fun passwordHash(password:String)=enc(MessageDigest.getInstance("SHA-256").digest(password.toByteArray(StandardCharsets.UTF_8)))

    // One ciphertext, with the AES key wrapped independently for recipient and sender.
    // This lets both ends read their own sent/received history without the server ever seeing plaintext.
    fun encrypt(plain:String, recipientPublicKey:String, senderPublicKey:String):Map<String,Any>{
        val aes=ByteArray(32).also{Random.nextBytes(it)}; val iv=ByteArray(12).also{Random.nextBytes(it)}
        val g=Cipher.getInstance("AES/GCM/NoPadding"); g.init(Cipher.ENCRYPT_MODE,SecretKeySpec(aes,"AES"),GCMParameterSpec(128,iv)); val ct=g.doFinal(plain.toByteArray(StandardCharsets.UTF_8))
        return mapOf("v" to 2,"alg" to "RSA-OAEP-256/AES-256-GCM","keyRecipient" to wrap(aes,recipientPublicKey),"keySender" to wrap(aes,senderPublicKey),"iv" to enc(iv),"ciphertext" to enc(ct))
    }
    fun encrypt(plain:String, recipientPublicKey:String)=encrypt(plain,recipientPublicKey,recipientPublicKey)
    fun decrypt(envelope:Map<String,Any?>, privateKey:String, forSender:Boolean=false):String{
        val keyName=if(forSender && envelope["keySender"]!=null) "keySender" else "keyRecipient"
        val wrapped=envelope[keyName]?.toString() ?: envelope["key"]?.toString() ?: error("Encrypted key missing")
        val r=Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding"); r.init(Cipher.DECRYPT_MODE,decodePrivateKey(privateKey)); val aes=r.doFinal(dec(wrapped))
        val g=Cipher.getInstance("AES/GCM/NoPadding"); g.init(Cipher.DECRYPT_MODE,SecretKeySpec(aes,"AES"),GCMParameterSpec(128,dec(envelope["iv"].toString())))
        return String(g.doFinal(dec(envelope["ciphertext"].toString())),StandardCharsets.UTF_8)
    }
    private fun wrap(aes:ByteArray,publicKey:String):String{val c=Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");c.init(Cipher.ENCRYPT_MODE,decodePublicKey(publicKey));return enc(c.doFinal(aes))}
    private fun decodePublicKey(v:String)=KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(dec(v)))
    private fun decodePrivateKey(v:String)=KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(dec(v)))
    private fun enc(b:ByteArray)=Base64.encodeToString(b,Base64.NO_WRAP)
    private fun dec(v:String)=Base64.decode(v,Base64.DEFAULT)
}
