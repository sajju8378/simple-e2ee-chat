package com.simplee2eechat.app

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

class ApiClient(private val baseUrl:String,private val token:String){
 data class User(val id:String,val username:String,val displayName:String,val publicKey:String){constructor(id:String,displayName:String,publicKey:String):this(id,"",displayName,publicKey)}
 data class Message(val id:String,val from:String,val to:String,val envelope:Map<String,Any?>,val createdAt:String)
 data class AuthResult(val id:String,val username:String,val token:String,val publicKey:String,val displayName:String,val keyBackup:String)
 fun getUser(id:String):User=request("GET","/v1/users/${id.trim()}",null).let{User(it.getString("id"),it.optString("username",""),it.optString("displayName","Friend"),it.getString("publicKey"))}
 fun searchUsers(q:String):List<User>{val a=request("GET","/v1/users/search?q="+java.net.URLEncoder.encode(q,"UTF-8"),null).getJSONArray("users");return (0 until a.length()).map{val x=a.getJSONObject(it);User(x.getString("id"),x.optString("username",""),x.optString("displayName","Friend"),x.getString("publicKey"))}}
 fun sendMessage(to:String,from:String,envelope:Map<String,Any>):String{val e=JSONObject();envelope.forEach{(k,v)->e.put(k,v)};return request("POST","/v1/messages",JSONObject().put("to",to).put("from",from).put("envelope",e)).getString("id")}
 fun conversation(peer:String):List<Message>{val a=request("GET","/v1/conversations/$peer",null).getJSONArray("messages");return (0 until a.length()).map{val m=a.getJSONObject(it);val e=m.getJSONObject("envelope");val map=mutableMapOf<String,Any?>();val k=e.keys();while(k.hasNext()){val z=k.next();map[z]=e.get(z)};Message(m.getString("id"),m.getString("from"),m.getString("to"),map,m.getString("createdAt"))}}
 fun uploadKeyBackup(keyBackup:String){request("POST","/v1/account/backup",JSONObject().put("keyBackup",keyBackup))}
 fun logout(){request("POST","/v1/logout",null)}
 private fun request(method:String,path:String,body:JSONObject?):JSONObject=requestJson(baseUrl,method,path,body,token)
 companion object{
  fun register(base:String,username:String,name:String,password:String,publicKey:String,keyBackup:String):AuthResult{val j=requestStatic(base,"POST","/v1/register",JSONObject().put("username",username).put("displayName",name).put("passwordHash",Crypto.passwordHash(password)).put("publicKey",publicKey).put("keyBackup",keyBackup));return AuthResult(j.getString("id"),j.getString("username"),j.getString("token"),j.getString("publicKey"),j.getString("displayName"),j.optString("keyBackup",""))}
  fun register(base:String,username:String,name:String,password:String,publicKey:String):AuthResult=register(base,username,name,password,publicKey,Crypto.encryptPrivateKeyBackup("legacy-placeholder",password))
  fun register(base:String,name:String,password:String,publicKey:String):AuthResult{val safe=name.lowercase().replace(Regex("[^a-z0-9_]"),"").ifBlank{"user"}.take(14);val username=safe+"_"+java.util.UUID.randomUUID().toString().replace("-","").take(5);return register(base,username,name,password,publicKey)}
  fun login(base:String,id:String,password:String):AuthResult{val j=requestStatic(base,"POST","/v1/login",JSONObject().put("id",id).put("passwordHash",Crypto.passwordHash(password)));return AuthResult(j.getString("id"),j.optString("username",""),j.getString("token"),j.getString("publicKey"),j.optString("displayName","User"),j.optString("keyBackup",""))}
  private fun requestStatic(base:String,method:String,path:String,body:JSONObject?):JSONObject=requestJson(base,method,path,body,null)
  private fun requestJson(base:String,method:String,path:String,body:JSONObject?,authToken:String?):JSONObject{val c=URL(base.trimEnd('/')+path).openConnection() as HttpURLConnection;try{c.requestMethod=method;c.connectTimeout=15000;c.readTimeout=15000;c.setRequestProperty("Accept","application/json");if(!authToken.isNullOrBlank())c.setRequestProperty("Authorization","Bearer $authToken");if(body!=null){c.doOutput=true;c.setRequestProperty("Content-Type","application/json; charset=utf-8");c.outputStream.use{it.write(body.toString().toByteArray(StandardCharsets.UTF_8))}};val code=c.responseCode;val s=if(code in 200..299)c.inputStream else c.errorStream;val t=s?.bufferedReader()?.use{it.readText()}?.trim().orEmpty();if(code !in 200..299)throw IllegalStateException(errorMessage(code,t));if(t.isBlank())throw IllegalStateException("Server returned an empty response ($code)");return JSONObject(t)}catch(e:IllegalStateException){throw e}catch(e:Exception){throw IllegalStateException("Network error: ${e.message?:e.javaClass.simpleName}",e)}finally{c.disconnect()}}
  private fun errorMessage(code:Int,text:String):String{if(text.isBlank())return "Server error ($code)";return try{JSONObject(text).optString("error").ifBlank{"Server error ($code)"}}catch(_:Exception){text.take(240)}}
 }
}
