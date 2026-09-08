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

    fun saveAccount(id: String, token: String, privateKey: String, publicKey: String, displayName: String, password: String) {
        // Keep a per-account encrypted copy so logging out and signing into another
        // account on the same phone never mixes the two private keys.
        val prefix = accountPrefix(id)
        prefs.edit()
            .putString("${prefix}token", token)
            .putString("${prefix}public", publicKey)
            .putString("${prefix}name", displayName)
            .putString("${prefix}password", encrypt(password))
            .putString("${prefix}private", encrypt(privateKey))
            .putString("id", id)
            .putString("token", token)
            .putString("public", publicKey)
            .putString("name", displayName)
            .putString("password", encrypt(password))
            .putString("private", encrypt(privateKey))
            .apply()
    }

    fun userId(): String? = prefs.getString("id", null)
    fun token(): String? = prefs.getString("token", null)
    fun displayName(): String? = prefs.getString("name", null)
    fun password(): String? = prefs.getString("password", null)?.let { decrypt(it) }
    fun privateKeyBlob(): String? = prefs.getString("private", null)?.let { decrypt(it) }

    fun savedPrivateKey(id: String): String? {
        val direct = prefs.getString("${accountPrefix(id)}private", null)?.let { decrypt(it) }
        if (!direct.isNullOrBlank()) return direct
        // Migrate the account that was stored by older APKs before multi-account support.
        if (prefs.getString("id", null).equals(id, ignoreCase = true)) return privateKeyBlob()
        return null
    }

    fun activateSavedAccount(id: String, token: String, publicKey: String, displayName: String, password: String, privateKey: String) {
        val prefix = accountPrefix(id)
        prefs.edit()
            .putString("${prefix}token", token)
            .putString("${prefix}public", publicKey)
            .putString("${prefix}name", displayName)
            .putString("${prefix}password", encrypt(password))
            .putString("${prefix}private", encrypt(privateKey))
            .putString("id", id)
            .putString("token", token)
            .putString("public", publicKey)
            .putString("name", displayName)
            .putString("password", encrypt(password))
            .putString("private", encrypt(privateKey))
            .apply()
    }

    // Logout removes only the active account/session. Saved encrypted account keys remain
    // on the phone so the user can sign back into an account created on this device.
    fun logout() { prefs.edit().remove("id").remove("token").remove("public").remove("name").remove("password").remove("private").apply() }

    // Retained for callers that explicitly want to erase every local account.
    fun clear() { prefs.edit().clear().apply() }

    private fun accountPrefix(id: String): String = "account_${Base64.encodeToString(id.trim().uppercase().toByteArray(), Base64.URL_SAFE or Base64.NO_WRAP)}_"

    private fun ensureKey() {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (!ks.containsAlias(alias)) {
            val g = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            g.init(
                KeyGenParameterSpec.Builder(
                    alias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build()
            )
            g.generateKey()
        }
    }

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        return ks.getKey(alias, null) as SecretKey
    }

    private fun encrypt(value: String): String {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key())
        return Base64.encodeToString(c.iv + c.doFinal(value.toByteArray()), Base64.NO_WRAP)
    }

    private fun decrypt(value: String): String {
        val all = Base64.decode(value, Base64.DEFAULT)
        val iv = all.copyOfRange(0, 12)
        val body = all.copyOfRange(12, all.size)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
        return String(c.doFinal(body))
    }
}
