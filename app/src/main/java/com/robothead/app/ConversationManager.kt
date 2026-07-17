package com.robothead.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

class ConversationManager(
    private val context: Context,
    private val apiKey: String,
    private val scope: CoroutineScope,
    private val systemPrompt: String,
    private val voicePitch: Float,
    private val onMouthAmount: (Float) -> Unit,
    private val onListeningChanged: (Boolean) -> Unit,
    private val captureFaceSnapshot: suspend () -> String?
) {
    private var speechRecognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var running = false
    private val utteranceCounter = AtomicInteger(0)

    fun start() {
        running = true
        tts = TextToSpeech(context) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
            if (ttsReady) {
                tts?.language = Locale.KOREAN
                tts?.setPitch(voicePitch)
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) {
                        onMouthAmount(0f)
                    }

                    @Deprecated("deprecated in base class, still required to override")
                    override fun onError(utteranceId: String?) {
                        onMouthAmount(0f)
                    }
                })
            }
        }
    }

    fun stop() {
        running = false
        speechRecognizer?.destroy()
        tts?.shutdown()
    }

    fun listen() {
        if (!running || !SpeechRecognizer.isRecognitionAvailable(context)) return

        speechRecognizer?.destroy()
        onListeningChanged(true)
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}

                override fun onError(error: Int) {
                    onListeningChanged(false)
                }

                override fun onResults(results: Bundle?) {
                    onListeningChanged(false)
                    val text = results
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                    if (!text.isNullOrBlank()) {
                        handleUserSpeech(text)
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR")
        }
        speechRecognizer?.startListening(intent)
    }

    private fun isFaceQuestion(text: String) =
        text.contains("얼굴") && (text.contains("어때") || text.contains("어떠"))

    private fun handleUserSpeech(text: String) {
        scope.launch(Dispatchers.IO) {
            val reply = try {
                if (isFaceQuestion(text)) {
                    val snapshot = captureFaceSnapshot()
                    if (snapshot != null) {
                        GroqClient.chatVision(
                            apiKey,
                            systemPrompt,
                            "이 사진 속 사람 얼굴 특징을 재밌고 다정하게 한두 문장으로 묘사해줘",
                            snapshot
                        )
                    } else {
                        "카메라로 얼굴이 잘 안 보이네, 조금 더 가까이 와줄래?"
                    }
                } else {
                    GroqClient.chat(apiKey, systemPrompt, text)
                }
            } catch (e: Exception) {
                "미안, 지금 대답하기 좀 힘드네."
            }
            withContext(Dispatchers.Main) { speak(reply) }
        }
    }

    fun speak(text: String) {
        if (!ttsReady) return
        animateMouthWhileSpeaking(text.length)
        tts?.stop()
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "robothead-${utteranceCounter.incrementAndGet()}")
    }

    private fun animateMouthWhileSpeaking(textLength: Int) {
        scope.launch(Dispatchers.Main) {
            val durationMs = (textLength * 90L).coerceIn(600L, 8000L)
            val steps = (durationMs / 120L).toInt().coerceAtLeast(1)
            repeat(steps) { i ->
                if (!running) return@launch
                onMouthAmount(if (i % 2 == 0) 0.8f else 0.2f)
                delay(120L)
            }
            onMouthAmount(0f)
        }
    }
}
