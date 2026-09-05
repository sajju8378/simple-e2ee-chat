package com.simplee2eechat.app

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

class ApiClient(private val baseUrl: String, private val token: String) {
    data class User(val id: String, val displayName: String, val publicKey: String)
    data class Message(val id: String, val from: String, val to: String, val envelope: Map<String, Any?>, val createdAt: String)
    data class AuthResult(val id: String, val token: String, val publicKey: String)

    fun health(): JSONObject = request("GET", "/health", null)

    fun getUser(id: String): User {
        val json = request("GET", "/v1/users/${id.uppercase()}", null)
        return User(
            json.getString("id"),
            json.optString("displayName", "Friend"),
            json.getString("publicKey")
        )
    }

    fun sendMessage(to: String, from: String, envelope: Map<String, Any>): String {
        val encrypted = JSONObject()
        envelope.forEach { (key, value) -> encrypted.put(key, value) }
        val body = JSONObject()
            .put("to", to)
            .put("from", from)
            .put("envelope", encrypted)
        return request("POST", "/v1/messages", body).getString("id")
    }

    fun conversation(peer: String): List<Message> {
        val json = request("GET", "/v1/conversations/${peer.uppercase()}", null)
        return parseMessages(json.getJSONArray("messages"))
    }

    private fun parseMessages(array: JSONArray): List<Message> {
        val result = mutableListOf<Message>()
        for (i in 0 until array.length()) {
            val message = array.getJSONObject(i)
            val envelope = message.getJSONObject("envelope")
            val map = mutableMapOf<String, Any?>()
            val keys = envelope.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                map[key] = envelope.get(key)
            }
            result.add(
                Message(
                    message.getString("id"),
                    message.getString("from"),
                    message.getString("to"),
                    map,
                    message.getString("createdAt")
                )
            )
        }
        return result
    }

    private fun request(method: String, path: String, body: JSONObject?): JSONObject {
        val connection = URL(baseUrl.trimEnd('/') + path).openConnection() as HttpURLConnection
        connection.requestMethod = method
        connection.connectTimeout = 15000
        connection.readTimeout = 15000
        connection.setRequestProperty("Accept", "application/json")
        if (token.isNotBlank()) connection.setRequestProperty("Authorization", "Bearer $token")
        if (body != null) {
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.outputStream.use { it.write(body.toString().toByteArray(StandardCharsets.UTF_8)) }
        }
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val text = stream?.bufferedReader()?.use { it.readText() } ?: "{}"
        connection.disconnect()
        if (code !in 200..299) {
            throw IllegalStateException(JSONObject(text).optString("error", "Server error ($code)"))
        }
        return JSONObject(text)
    }

    companion object {
        fun health(base: String): JSONObject = requestStatic(base, "GET", "/health", null)

        fun register(base: String, name: String, password: String, publicKey: String): AuthResult {
            val body = JSONObject()
                .put("displayName", name)
                .put("passwordHash", Crypto.passwordHash(password))
                .put("publicKey", publicKey)
            val json = requestStatic(base, "POST", "/v1/register", body)
            return AuthResult(json.getString("id"), json.getString("token"), json.getString("publicKey"))
        }

        fun login(base: String, id: String, password: String): AuthResult {
            val body = JSONObject()
                .put("id", id)
                .put("passwordHash", Crypto.passwordHash(password))
            val json = requestStatic(base, "POST", "/v1/login", body)
            return AuthResult(json.getString("id"), json.getString("token"), json.getString("publicKey"))
        }

        private fun requestStatic(base: String, method: String, path: String, body: JSONObject?): JSONObject {
            val connection = URL(base.trimEnd('/') + path).openConnection() as HttpURLConnection
            connection.requestMethod = method
            connection.connectTimeout = 15000
            connection.readTimeout = 15000
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.use { it.write(body.toString().toByteArray(StandardCharsets.UTF_8)) }
            }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() } ?: "{}"
            connection.disconnect()
            if (code !in 200..299) {
                throw IllegalStateException(JSONObject(text).optString("error", "Server error ($code)"))
            }
            return JSONObject(text)
        }
    }
}
