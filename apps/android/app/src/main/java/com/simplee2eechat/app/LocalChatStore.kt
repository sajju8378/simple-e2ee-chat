package com.simplee2eechat.app

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class LocalChatStore(context: Context) : SQLiteOpenHelper(context, "chat_history.db", null, 1) {
    data class Item(val messageId:String,val peerId:String,val peerName:String,val senderId:String,val createdAt:Long,val payload:String)
    data class Chat(val peerId:String,val peerName:String,val lastAt:Long,val lastText:String)

    override fun onCreate(db:SQLiteDatabase){
        db.execSQL("CREATE TABLE messages(message_id TEXT PRIMARY KEY,peer_id TEXT NOT NULL,peer_name TEXT NOT NULL,sender_id TEXT NOT NULL,created_at INTEGER NOT NULL,payload TEXT NOT NULL)")
        db.execSQL("CREATE INDEX idx_messages_peer_time ON messages(peer_id,created_at)")
    }
    override fun onUpgrade(db:SQLiteDatabase,oldVersion:Int,newVersion:Int){ }

    fun upsert(messageId:String,peerId:String,peerName:String,senderId:String,createdAt:Long,payload:String){
        val v=ContentValues().apply{put("message_id",messageId);put("peer_id",peerId);put("peer_name",peerName);put("sender_id",senderId);put("created_at",createdAt);put("payload",encrypt(payload))}
        writableDatabase.insertWithOnConflict("messages",null,v,SQLiteDatabase.CONFLICT_REPLACE)
    }
    fun items(peerId:String):List<Item>{
        val out=mutableListOf<Item>(); readableDatabase.query("messages",null,"peer_id=?",arrayOf(peerId),null,null,"created_at ASC").use{c->while(c.moveToNext())out.add(Item(c.getString(c.getColumnIndexOrThrow("message_id")),c.getString(c.getColumnIndexOrThrow("peer_id")),c.getString(c.getColumnIndexOrThrow("peer_name")),c.getString(c.getColumnIndexOrThrow("sender_id")),c.getLong(c.getColumnIndexOrThrow("created_at")),decrypt(c.getString(c.getColumnIndexOrThrow("payload")))))};return out
    }
    fun chats():List<Chat>{
        val out=mutableListOf<Chat>();readableDatabase.rawQuery("SELECT peer_id,peer_name,MAX(created_at) last_at FROM messages GROUP BY peer_id ORDER BY last_at DESC",null).use{c->while(c.moveToNext()){val id=c.getString(0);val name=c.getString(1);val latest=items(id).lastOrNull()?.payload.orEmpty();out.add(Chat(id,name,c.getLong(2),latest))}};return out
    }
    fun deletePeer(peerId:String){writableDatabase.delete("messages","peer_id=?",arrayOf(peerId))}
    fun closeStore(){close()}

    private fun key():SecretKey{
        val ks=KeyStore.getInstance("AndroidKeyStore").apply{load(null)}
        val existing=ks.getKey(KEY_ALIAS,null) as? SecretKey
        if(existing!=null)return existing
        val kg=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore")
        kg.init(KeyGenParameterSpec.Builder(KEY_ALIAS,KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256).build())
        return kg.generateKey()
    }

    // Android Keystore requires the provider to generate the GCM IV when randomized encryption is enabled.
    // Store that generated IV beside the ciphertext; never supply our own IV during encryption.
    private fun encrypt(value:String):String{
        val c=Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE,key())
        val ct=c.doFinal(value.toByteArray(StandardCharsets.UTF_8))
        return Base64.encodeToString(c.iv,Base64.NO_WRAP)+":"+Base64.encodeToString(ct,Base64.NO_WRAP)
    }
    private fun decrypt(value:String):String{
        return try{
            val p=value.split(":",limit=2)
            if(p.size!=2) return "[local message unavailable]"
            val c=Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.DECRYPT_MODE,key(),GCMParameterSpec(128,Base64.decode(p[0],Base64.DEFAULT)))
            String(c.doFinal(Base64.decode(p[1],Base64.DEFAULT)),StandardCharsets.UTF_8)
        }catch(_:Exception){"[local message unavailable]"}
    }
    companion object{private const val KEY_ALIAS="simple_e2ee_chat_history_key"}
}
