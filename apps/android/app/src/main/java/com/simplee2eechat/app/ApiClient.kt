package com.simplee2eechat.app

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class ApiClient(private val baseUrl:String,private val token:String){
 data class User(val id:String,val username:String,val displayName:String,val publicKey:String)
 data class Message(val id:String,val from:String,val to:String,val envelope:Map<String,Any?>,val createdAt:String)
 data class AuthResult(val id:String,val username:String,val token:String,val publicKey:String,val displayName:String)
 data class CallInfo(val id:String,val caller:String,val callee:String,val type:String,val offer:String?,val answer:String?,val status:String)
 fun getUser(id:String)=request("GET","/v1/users/${id.trim()}",null).let{User(it.getString("id"),it.optString("username",""),it.optString("displayName","Friend"),it.getString("publicKey"))}
 fun sendMessage(to:String,from:String,envelope:Map<String,Any>):String{val e=JSONObject();envelope.forEach{(k,v)->e.put(k,v)};return request("POST","/v1/messages",JSONObject().put("to",to).put("from",from).put("envelope",e)).getString("id")}
 fun conversation(peer:String):List<Message>{val a=request("GET","/v1/conversations/$peer",null).getJSONArray("messages");return (0 until a.length()).map{val m=a.getJSONObject(it);val e=m.getJSONObject("envelope");val map=mutableMapOf<String,Any?>();val k=e.keys();while(k.hasNext()){val z=k.next();map[z]=e.get(z)};Message(m.getString("id"),m.getString("from"),m.getString("to"),map,m.getString("createdAt"))}}
 fun createCall(peer:String,type:String,offer:String):CallInfo{val j=request("POST","/v1/calls",JSONObject().put("callee",peer).put("type",type).put("offer",offer));return call(j)}
 fun call(id:String)=request("GET","/v1/calls/$id",null).let(::call)
 fun incoming(peer:String):List<CallInfo>{val a=request("GET","/v1/calls/incoming?peer=${URLEncoder.encode(peer,"UTF-8")}",null).getJSONArray("calls");return (0 until a.length()).map{call(a.getJSONObject(it))}}
 fun answerCall(id:String,answer:String)=request("POST","/v1/calls/$id/answer",JSONObject().put("answer",answer)).let(::call)
 fun candidate(id:String,value:String)=request("POST","/v1/calls/$id/candidate",JSONObject().put("candidate",value))
 fun candidates(id:String)=request("GET","/v1/calls/$id/candidates",null).getJSONArray("candidates").let{a->(0 until a.length()).map{a.getString(it)}}
 fun endCall(id:String)=request("POST","/v1/calls/$id/end",JSONObject())
 private fun call(j:JSONObject)=CallInfo(j.getString("id"),j.getString("caller"),j.getString("callee"),j.getString("type"),j.optString("offer",null),j.optString("answer",null),j.getString("status"))
 private fun request(method:String,path:String,body:JSONObject?):JSONObject=requestJson(baseUrl,method,path,body,token)
 companion object{
  fun register(base:String,username:String,name:String,password:String,publicKey:String):AuthResult{val j=requestStatic(base,"POST","/v1/register",JSONObject().put("username",username).put("displayName",name).put("passwordHash",Crypto.passwordHash(password)).put("publicKey",publicKey));return AuthResult(j.getString("id"),j.getString("username"),j.getString("token"),j.getString("publicKey"),j.getString("displayName"))}
  fun register(base:String,name:String,password:String,publicKey:String):AuthResult{val safe=name.lowercase().replace(Regex("[^a-z0-9_]"),"").ifBlank{"user"}.take(14);return register(base,safe+"_"+java.util.UUID.randomUUID().toString().replace("-","").take(5),name,password,publicKey)}
  fun login(base:String,id:String,password:String):AuthResult{val j=requestStatic(base,"POST","/v1/login",JSONObject().put("id",id).put("passwordHash",Crypto.passwordHash(password)));return AuthResult(j.getString("id"),j.optString("username",""),j.getString("token"),j.getString("publicKey"),j.optString("displayName","User"))}
  private fun requestStatic(base:String,method:String,path:String,body:JSONObject?)=requestJson(base,method,path,body,null)
  private fun requestJson(base:String,method:String,path:String,body:JSONObject?,authToken:String?):JSONObject{val c=URL(base.trimEnd('/')+path).openConnection() as HttpURLConnection;try{c.requestMethod=method;c.connectTimeout=15000;c.readTimeout=15000;c.setRequestProperty("Accept","application/json");if(!authToken.isNullOrBlank())c.setRequestProperty("Authorization","Bearer $authToken");if(body!=null){c.doOutput=true;c.setRequestProperty("Content-Type","application/json; charset=utf-8");c.outputStream.use{it.write(body.toString().toByteArray(StandardCharsets.UTF_8))}};val code=c.responseCode;val s=if(code in 200..299)c.inputStream else c.errorStream;val t=s?.bufferedReader()?.use{it.readText()}?.trim().orEmpty();if(code !in 200..299)throw IllegalStateException(errorMessage(code,t));if(t.isBlank())throw IllegalStateException("Server returned an empty response ($code)");return JSONObject(t)}catch(e:IllegalStateException){throw e}catch(e:Exception){throw IllegalStateException("Network error: ${e.message?:e.javaClass.simpleName}",e)}finally{c.disconnect()}}
  private fun errorMessage(code:Int,text:String)=if(text.isBlank())"Server error ($code)" else try{JSONObject(text).optString("error").ifBlank{"Server error ($code)"}}catch(_:Exception){text.take(240)}
 }
}
