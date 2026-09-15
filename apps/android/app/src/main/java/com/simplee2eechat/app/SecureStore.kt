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

class SecureStore(private val context: Context) {
    private val prefs = context.getSharedPreferences("e2ee_account", Context.MODE_PRIVATE)
    private val alias = "simple_e2ee_local_key"
    init { ensureKey() }
    fun saveAccount(id:String,token:String,privateKey:String,publicKey:String,displayName:String,password:String){prefs.edit().putString("id",id).putString("token",token).putString("public",publicKey).putString("name",displayName).putString("password",encrypt(password)).putString("private",encrypt(privateKey)).apply()}
    fun userId():String?=prefs.getString("id",null);fun token():String?=prefs.getString("token",null);fun displayName():String?=prefs.getString("name",null);fun publicKey():String?=prefs.getString("public",null);fun password():String?=prefs.getString("password",null)?.let{decrypt(it)};fun privateKeyBlob():String?=prefs.getString("private",null)?.let{decrypt(it)};fun clear(){prefs.edit().clear().apply()}
    private fun ensureKey(){val ks=KeyStore.getInstance("AndroidKeyStore").apply{load(null)};if(!ks.containsAlias(alias)){val g=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");g.init(KeyGenParameterSpec.Builder(alias,KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());g.generateKey()}}
    private fun key():SecretKey=KeyStore.getInstance("AndroidKeyStore").apply{load(null)}.getKey(alias,null) as SecretKey
    private fun encrypt(v:String):String{val c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.ENCRYPT_MODE,key());return Base64.encodeToString(c.iv+c.doFinal(v.toByteArray()),Base64.NO_WRAP)}
    private fun decrypt(v:String):String{val a=Base64.decode(v,Base64.DEFAULT);val c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.DECRYPT_MODE,key(),GCMParameterSpec(128,a.copyOfRange(0,12)));return String(c.doFinal(a.copyOfRange(12,a.size)))}
}
