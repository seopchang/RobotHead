package com.robothead.app

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

object GroqClient {
    private const val ENDPOINT = "https://api.groq.com/openai/v1/chat/completions"
    private const val MODEL = "llama-3.1-8b-instant"

    fun chat(apiKey: String, systemPrompt: String, userMessage: String): String {
        val connection = URL(ENDPOINT).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.setRequestProperty("Authorization", "Bearer $apiKey")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true
            connection.connectTimeout = 10_000
            connection.readTimeout = 15_000

            val messages = JSONArray().apply {
                put(JSONObject().put("role", "system").put("content", systemPrompt))
                put(JSONObject().put("role", "user").put("content", userMessage))
            }
            val body = JSONObject()
                .put("model", MODEL)
                .put("messages", messages)
                .put("temperature", 0.7)

            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }

            val responseCode = connection.responseCode
            val stream = if (responseCode in 200..299) connection.inputStream else connection.errorStream
            val responseText = stream.bufferedReader(Charsets.UTF_8).use { it.readText() }

            if (responseCode !in 200..299) {
                throw IOException("Groq API error ($responseCode): $responseText")
            }

            return JSONObject(responseText)
                .getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content")
        } finally {
            connection.disconnect()
        }
    }
}
