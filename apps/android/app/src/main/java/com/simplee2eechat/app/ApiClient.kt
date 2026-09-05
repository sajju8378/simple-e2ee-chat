package com.simplee2eechat.app

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

class ApiClient(private val baseUrl:String,private val token:String){
    data class User(val id:String,val publicKey:String)
    data class Message(val id:String,val from:String,val to:String,val envelope:Map<String,Any?>,val createdAt:String)
    data class AuthResult(val id:String,val token:String,val publicKey:String)
    fun getUser(id:String):User{val o=request("GET","/v1/users/${id.uppercase()}",null);return User(o.getString("id"),o.getString("publicKey"))}
    fun sendMessage(to:String,from:String,envelope:Map<String,Any>):String{val e=JSONObject();envelope.forEach{(k,v)->e.put(k,v)};return request("POST","/v1/messages",JSONObject().put("to",to).put("from",from).put("envelope",e)).getString("id")}
    fun conversation(peer:String):List<Message>{val o=request("GET","/v1/conversations/${peer.uppercase()}",null);return parse(o.getJSONArray("messages"))}
    private fun parse(a:org.json.JSONArray):List<Message>{val out=mutableListOf<Message>();for(i in 0 until a.length()){val m=a.getJSONObject(i);val e=m.getJSONObject("envelope");val map=mutableMapOf<String,Any?>();e.keys().forEach{k->map[k]=e.get(k)};out.add(Message(m.getString("id"),m.getString("from"),m.getString("to"),map,m.getString("createdAt")))}return out}
    private fun request(method:String,path:String,body:JSONObject?):JSONObject{val c=(URL(baseUrl.trimEnd('/')+path).openConnection() as HttpURLConnection);c.requestMethod=method;c.connectTimeout=15000;c.readTimeout=15000;c.setRequestProperty("Accept","application/json");c.setRequestProperty("Authorization","Bearer $token");if(body!=null){c.doOutput=true;c.setRequestProperty("Content-Type","application/json");c.outputStream.use{it.write(body.toString().toByteArray(StandardCharsets.UTF_8))}};val code=c.responseCode;val s=if(code in 200..299)c.inputStream else c.errorStream;val text=s?.bufferedReader()?.use{it.readText()}?:"{}";if(code !in 200..299)throw IllegalStateException(JSONObject(text).optString("error","Server error ($code)"));return JSONObject(text)}
    companion object{
        fun register(base:String,name:String,password:String,publicKey:String):AuthResult{val o=requestStatic(base,"POST","/v1/register",JSONObject().put("displayName",name).put("passwordHash",Crypto.passwordHash(password)).put("publicKey",publicKey));return AuthResult(o.getString("id"),o.getString("token"),o.getString("publicKey"))}
        fun login(base:String,id:String,password:String):AuthResult{val o=requestStatic(base,"POST","/v1/login",JSONObject().put("id",id).put("passwordHash",Crypto.passwordHash(password)));return AuthResult(o.getString("id"),o.getString("token"),o.getString("publicKey"))}
        private fun requestStatic(base:String,method:String,path:String,body:JSONObject):JSONObject{val c=(URL(base.trimEnd('/')+path).openConnection() as HttpURLConnection);c.requestMethod=method;c.connectTimeout=15000;c.readTimeout=15000;c.doOutput=true;c.setRequestProperty("Content-Type","application/json");c.outputStream.use{it.write(body.toString().toByteArray(StandardCharsets.UTF_8))};val code=c.responseCode;val s=if(code in 200..299)c.inputStream else c.errorStream;val text=s?.bufferedReader()?.use{it.readText()}?:"{}";if(code !in 200..299)throw IllegalStateException(JSONObject(text).optString("error","Server error ($code)"));return JSONObject(text)}
    }
}
