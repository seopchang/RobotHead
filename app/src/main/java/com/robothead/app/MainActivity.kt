package com.robothead.app

import android.Manifest
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.WindowInsetsController
import android.widget.EditText
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

class MainActivity : ComponentActivity() {

    private lateinit var faceView: FaceView
    private lateinit var gestureDetector: GestureDetector
    private var faceTracker: FaceTracker? = null
    private var conversationManager: ConversationManager? = null
    private var faceVisible = false
    private var reacting = false
    private var micPermissionGranted = false

    companion object {
        private const val PREFS_NAME = "robothead_prefs"
        private const val KEY_GROQ_API_KEY = "groq_api_key"
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results[Manifest.permission.CAMERA] == true) startFaceTracking()
        if (results[Manifest.permission.RECORD_AUDIO] == true) {
            micPermissionGranted = true
            ensureApiKeyThenStartConversation()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        faceView = FaceView(this)
        setContentView(faceView)
        hideSystemBars()
        setupTouchReactions()
        startIdleAnimation()
        requestNeededPermissions()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    override fun onDestroy() {
        super.onDestroy()
        conversationManager?.stop()
    }

    private fun hideSystemBars() {
        window.insetsController?.let { controller ->
            controller.hide(android.view.WindowInsets.Type.systemBars())
            controller.systemBarsBehavior =
                WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    private fun requestNeededPermissions() {
        val needed = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) needed += Manifest.permission.CAMERA
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) needed += Manifest.permission.RECORD_AUDIO

        if (needed.isEmpty()) {
            micPermissionGranted = true
            startFaceTracking()
            ensureApiKeyThenStartConversation()
        } else {
            permissionLauncher.launch(needed.toTypedArray())
        }
    }

    private fun startFaceTracking() {
        if (faceTracker != null) return
        faceTracker = FaceTracker(
            context = this,
            lifecycleOwner = this,
            onFaceMoved = { x, y ->
                faceVisible = true
                faceView.setEyeOffset(x, y)
            },
            onFaceLost = {
                faceVisible = false
            }
        ).also { it.start() }
    }

    private fun getStoredApiKey(): String? =
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getString(KEY_GROQ_API_KEY, null)

    private fun saveApiKey(key: String) {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
            .putString(KEY_GROQ_API_KEY, key)
            .apply()
    }

    private fun ensureApiKeyThenStartConversation() {
        if (!micPermissionGranted) return
        val key = getStoredApiKey()
        if (key.isNullOrBlank()) {
            showApiKeyDialog()
        } else {
            restartConversation(key)
        }
    }

    private fun restartConversation(apiKey: String) {
        conversationManager?.stop()
        conversationManager = ConversationManager(
            context = this,
            apiKey = apiKey,
            scope = lifecycleScope,
            onMouthAmount = { amount -> faceView.setMouthOpenAmount(amount) }
        ).also { it.start() }
    }

    private fun showApiKeyDialog() {
        val input = EditText(this).apply {
            hint = "Groq API 키 (console.groq.com)"
            setText(getStoredApiKey() ?: "")
        }
        AlertDialog.Builder(this)
            .setTitle("Groq API 키 입력")
            .setView(input)
            .setPositiveButton("저장") { _, _ ->
                val key = input.text.toString().trim()
                if (key.isNotBlank()) {
                    saveApiKey(key)
                    restartConversation(key)
                }
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun setupTouchReactions() {
        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean {
                val isUpperHalf = e.y < faceView.height * 0.5f
                triggerReaction(pet = isUpperHalf)
                return true
            }

            override fun onLongPress(e: MotionEvent) {
                showApiKeyDialog()
            }
        })
        faceView.setOnTouchListener { _, event -> gestureDetector.onTouchEvent(event) }
    }

    private fun triggerReaction(pet: Boolean) {
        if (reacting) return
        reacting = true
        lifecycleScope.launch {
            if (pet) {
                faceView.setEyeOpenAmount(0.4f)
            } else {
                faceView.setEyeOpenAmount(1f)
                faceView.setMouthOpenAmount(0.6f)
            }
            delay(700)
            faceView.setEyeOpenAmount(1f)
            faceView.setMouthOpenAmount(0f)
            reacting = false
        }
    }

    private fun startIdleAnimation() {
        lifecycleScope.launch {
            while (true) {
                delay(Random.nextLong(2500, 5000))
                if (!reacting) {
                    faceView.setEyeOpenAmount(0f)
                    delay(120)
                    faceView.setEyeOpenAmount(1f)
                }
            }
        }
        lifecycleScope.launch {
            while (true) {
                delay(Random.nextLong(1500, 3500))
                if (!faceVisible && !reacting) {
                    faceView.setEyeOffset(
                        Random.nextFloat() * 1.2f - 0.6f,
                        Random.nextFloat() * 0.6f - 0.3f
                    )
                }
            }
        }
    }
}
