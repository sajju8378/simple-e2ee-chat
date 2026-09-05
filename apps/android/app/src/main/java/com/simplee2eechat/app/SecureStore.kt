package com.simplee2eechat.app

import android.content.Context
import android.util.Base64
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class SecureStore(private val context:Context){
    private val prefs=context.getSharedPreferences("e2ee_account",Context.MODE_PRIVATE)
    private val alias="simple_e2ee_local_key"
    init{ensureKey()}
    fun saveAccount(id:String,token:String,privateKey:String,publicKey:String){prefs.edit().putString("id",id).putString("token",token).putString("public",publicKey).putString("private",encrypt(privateKey)).apply()}
    fun userId()=prefs.getString("id",null)
    fun token()=prefs.getString("token",null)
    fun privateKeyBlob():String?=prefs.getString("private",null)?.let{decrypt(it)}
    fun clear(){prefs.edit().clear().apply()}
    private fun ensureKey(){val ks=KeyStore.getInstance("AndroidKeyStore").apply{load(null)};if(!ks.containsAlias(alias)){val g=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");g.init(KeyGenParameterSpec.Builder(alias,KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());g.generateKey()}}
    private fun key():SecretKey{val ks=KeyStore.getInstance("AndroidKeyStore").apply{load(null)};return ks.getKey(alias,null) as SecretKey}
    private fun encrypt(value:String):String{val c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.ENCRYPT_MODE,key());return Base64.encodeToString(c.iv+c.doFinal(value.toByteArray()),Base64.NO_WRAP)}
    private fun decrypt(value:String):String{val all=Base64.decode(value,Base64.DEFAULT);val iv=all.copyOfRange(0,12);val body=all.copyOfRange(12,all.size);val c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.DECRYPT_MODE,key(),GCMParameterSpec(128,iv));return String(c.doFinal(body))}
}
