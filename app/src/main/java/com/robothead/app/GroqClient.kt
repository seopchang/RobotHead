package com.robothead.app

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

object GroqClient {
    private const val ENDPOINT = "https://api.groq.com/openai/v1/chat/completions"
    private const val MODEL = "llama-3.1-8b-instant"
    private const val VISION_MODEL = "meta-llama/llama-4-scout-17b-16e-instruct"

    fun chat(apiKey: String, systemPrompt: String, userMessage: String): String {
        val messages = JSONArray().apply {
            put(JSONObject().put("role", "system").put("content", systemPrompt))
            put(JSONObject().put("role", "user").put("content", userMessage))
        }
        return send(apiKey, MODEL, messages)
    }

    fun chatVision(apiKey: String, systemPrompt: String, userMessage: String, imageBase64: String): String {
        val content = JSONArray().apply {
            put(JSONObject().put("type", "text").put("text", userMessage))
            put(
                JSONObject().put("type", "image_url").put(
                    "image_url",
                    JSONObject().put("url", "data:image/jpeg;base64,$imageBase64")
                )
            )
        }
        val messages = JSONArray().apply {
            put(JSONObject().put("role", "system").put("content", systemPrompt))
            put(JSONObject().put("role", "user").put("content", content))
        }
        return send(apiKey, VISION_MODEL, messages)
    }

    private fun send(apiKey: String, model: String, messages: JSONArray): String {
        val connection = URL(ENDPOINT).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.setRequestProperty("Authorization", "Bearer $apiKey")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true
            connection.connectTimeout = 10_000
            connection.readTimeout = 20_000

            val body = JSONObject()
                .put("model", model)
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
