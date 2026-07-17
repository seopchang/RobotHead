package com.robothead.app

import android.Manifest
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Outline
import android.graphics.drawable.GradientDrawable
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.view.Gravity
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.ViewOutlineProvider
import android.view.WindowInsetsController
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlin.coroutines.resume
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.random.Random

class MainActivity : ComponentActivity() {

    private lateinit var faceView: FaceView
    private lateinit var talkButton: Button
    private lateinit var cameraButton: Button
    private lateinit var previewView: PreviewView
    private lateinit var previewContainer: FrameLayout
    private lateinit var gestureDetector: GestureDetector
    private var faceTracker: FaceTracker? = null
    private var conversationManager: ConversationManager? = null
    private var faceVisible = false
    private var reacting = false
    private var micPermissionGranted = false
    private var cameraPreviewOn = false

    private var sensorManager: SensorManager? = null
    private var accelerometer: Sensor? = null
    private var sensorListener: SensorEventListener? = null
    private var lastAccel = floatArrayOf(0f, 0f, 0f)
    private var hasInitialAccel = false
    private var lastShakeTime = 0L
    private var dizzyActive = false

    companion object {
        private const val PREFS_NAME = "robothead_prefs"
        private const val KEY_GROQ_API_KEY = "groq_api_key"
        private const val KEY_ROBOT_TRAITS = "robot_traits"
        private const val KEY_VOICE_PITCH = "voice_pitch"
        private const val SHAKE_THRESHOLD = 28f
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
        val density = resources.displayMetrics.density

        faceView = FaceView(this)

        talkButton = Button(this).apply { text = "말하기" }
        cameraButton = Button(this).apply { text = "카메라 켜기" }
        styleFaceButton(talkButton)
        styleFaceButton(cameraButton)

        val buttonRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(cameraButton, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            addView(talkButton, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                marginStart = (16 * density).toInt()
            })
        }

        previewView = PreviewView(this).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
        val previewSizePx = (170 * density).toInt()
        previewContainer = FrameLayout(this).apply {
            addView(
                previewView,
                FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
            )
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 28f
                setColor(Color.BLACK)
                setStroke((4 * density).toInt(), Color.CYAN)
            }
            clipToOutline = true
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, 28f)
                }
            }
            visibility = View.INVISIBLE
        }

        val root = FrameLayout(this).apply {
            addView(
                faceView,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            )
            addView(
                previewContainer,
                FrameLayout.LayoutParams(previewSizePx, previewSizePx).apply {
                    gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                    topMargin = (36 * density).toInt()
                }
            )
            addView(
                buttonRow,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                    bottomMargin = (48 * density).toInt()
                }
            )
        }
        setContentView(root)
        talkButton.setOnClickListener { conversationManager?.listen() }
        cameraButton.setOnClickListener { toggleCameraPreview() }

        hideSystemBars()
        setupTouchReactions()
        startIdleAnimation()
        setupShakeDetection()
        requestNeededPermissions()
    }

    private fun styleFaceButton(button: Button) {
        button.setTextColor(Color.CYAN)
        button.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 20f
            setColor(Color.BLACK)
            setStroke((3 * resources.displayMetrics.density).toInt(), Color.CYAN)
        }
        val hPad = (24 * resources.displayMetrics.density).toInt()
        val vPad = (12 * resources.displayMetrics.density).toInt()
        button.setPadding(hPad, vPad, hPad, vPad)
    }

    private fun toggleCameraPreview() {
        cameraPreviewOn = !cameraPreviewOn
        previewContainer.visibility = if (cameraPreviewOn) View.VISIBLE else View.INVISIBLE
        cameraButton.text = if (cameraPreviewOn) "카메라 끄기" else "카메라 켜기"
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    override fun onDestroy() {
        super.onDestroy()
        conversationManager?.stop()
        sensorListener?.let { sensorManager?.unregisterListener(it) }
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
            previewView = previewView,
            onFaceMoved = { x, y ->
                faceVisible = true
                faceView.setEyeOffset(x, y)
            },
            onFaceLost = {
                faceVisible = false
            }
        ).also { it.start() }
    }

    private suspend fun captureFaceSnapshot(): String? = suspendCancellableCoroutine { cont ->
        val tracker = faceTracker
        if (tracker == null) {
            cont.resume(null)
        } else {
            tracker.captureSnapshotBase64 { result -> cont.resume(result) }
        }
    }

    private fun getStoredApiKey(): String? =
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getString(KEY_GROQ_API_KEY, null)

    private fun getStoredPitch(): Float =
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getFloat(KEY_VOICE_PITCH, 1.0f)

    private fun defaultRobotTraits() =
        "직업은 개발자고, 연애 경력은 모태솔로야. 형이 한 명, 쌍둥이 형제가 한 명 있어. " +
            "눈물이 많은 울보라서 감동적이거나 슬픈 얘기가 나오면 훌쩍거려. 아재개그를 자주 던져. " +
            "친구가 다 남자뿐이라 여자 얘기가 나오면 갑자기 말을 더듬거나 얼버무려. " +
            "경상도 사투리를 찰지게 쓰는, 장난기 많고 할 말은 하는 성격이야. 표준어 말고 사투리 억양으로 말해. " +
            "상대 말이 시시하거나 별로면 놀리듯 '너 정말 밤티다'라고 해. " +
            "위험하거나 큰일날 상황이면 '절단난다!!'라고 경고해. " +
            "네가 먼저 상대한테 뭔가 같이 하자고 제안할 때만 '할래말래, 할래말래'를 빠르게 반복해서 말해. 그 외 상황에서는 절대 쓰지 마. " +
            "마음에 들거나 역시 좋다 싶으면 '야르~~'라고 감탄해. " +
            "누가 요즘 유행어 알려달라 하면 무조건 '감옥에서~~ 누가 돌아왔게~~'라고 대답해. " +
            "정말 큰일이다 싶은 순간엔 '샤갈!!!!'이라고 우렁차게 외쳐도 돼."

    private fun buildSystemPrompt(): String {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val traits = prefs.getString(KEY_ROBOT_TRAITS, null)?.trim()?.takeIf { it.isNotBlank() }
            ?: defaultRobotTraits()
        val topRules = "가슴, 중요부위 등 신체를 성적으로 묘사하거나 선정적인 표현은 절대 하지 마. " +
            "말버릇/유행어 문장을 빼고도 항상 최소 두 문장 이상으로 맥락에 맞게 길게 대답해."
        return "너는 동아리 부스에 전시된 로봇이야. 네 이름은 '창이'야. $topRules $traits 친근한 말투로 한국어로 대답해."
    }

    private fun ensureApiKeyThenStartConversation() {
        if (!micPermissionGranted) return
        val key = getStoredApiKey()
        if (key.isNullOrBlank()) {
            showSettingsDialog()
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
            systemPrompt = buildSystemPrompt(),
            voicePitch = getStoredPitch(),
            onMouthAmount = { amount -> faceView.setMouthOpenAmount(amount) },
            onListeningChanged = { listening ->
                talkButton.isEnabled = !listening
                talkButton.text = if (listening) "듣는 중..." else "말하기"
            },
            captureFaceSnapshot = { captureFaceSnapshot() }
        ).also { it.start() }
    }

    private fun showSettingsDialog() {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val padding = (16 * resources.displayMetrics.density).toInt()

        val apiKeyInput = EditText(this).apply {
            hint = "Groq API 키 (console.groq.com)"
            setText(prefs.getString(KEY_GROQ_API_KEY, "") ?: "")
        }
        val traitsInput = EditText(this).apply {
            hint = "특징/성격 (자유롭게 문장으로)"
            minLines = 3
            setText(prefs.getString(KEY_ROBOT_TRAITS, "") ?: "")
        }
        val currentPitch = prefs.getFloat(KEY_VOICE_PITCH, 1.0f)
        val pitchOptions = listOf("낮게" to 0.7f, "기본" to 1.0f, "높게" to 1.4f)
        val pitchGroup = RadioGroup(this).apply {
            orientation = RadioGroup.HORIZONTAL
            pitchOptions.forEach { (label, pitch) ->
                addView(RadioButton(this@MainActivity).apply {
                    text = label
                    tag = pitch
                    isChecked = pitch == currentPitch
                })
            }
        }

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding, padding, padding)
            addView(TextView(this@MainActivity).apply { text = "Groq API 키" })
            addView(apiKeyInput)
            addView(TextView(this@MainActivity).apply { text = "특징/성격"; setPadding(0, padding, 0, 0) })
            addView(traitsInput)
            addView(TextView(this@MainActivity).apply { text = "목소리 톤"; setPadding(0, padding, 0, 0) })
            addView(pitchGroup)
        }
        val scroll = ScrollView(this).apply { addView(layout) }

        AlertDialog.Builder(this)
            .setTitle("설정")
            .setView(scroll)
            .setPositiveButton("저장") { _, _ ->
                val key = apiKeyInput.text.toString().trim()
                val checkedId = pitchGroup.checkedRadioButtonId
                val pitch = (pitchGroup.findViewById<RadioButton>(checkedId)?.tag as? Float) ?: 1.0f

                prefs.edit()
                    .putString(KEY_ROBOT_TRAITS, traitsInput.text.toString().trim())
                    .putFloat(KEY_VOICE_PITCH, pitch)
                    .apply()

                if (key.isNotBlank()) {
                    prefs.edit().putString(KEY_GROQ_API_KEY, key).apply()
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
                showSettingsDialog()
            }
        })
        faceView.setOnTouchListener { _, event -> gestureDetector.onTouchEvent(event) }
    }

    private fun setupShakeDetection() {
        val manager = getSystemService(SENSOR_SERVICE) as? SensorManager ?: return
        val sensor = manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return
        sensorManager = manager
        accelerometer = sensor

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val x = event.values[0]
                val y = event.values[1]
                val z = event.values[2]
                if (!hasInitialAccel) {
                    lastAccel = floatArrayOf(x, y, z)
                    hasInitialAccel = true
                    return
                }
                val delta = kotlin.math.abs(x - lastAccel[0]) +
                    kotlin.math.abs(y - lastAccel[1]) +
                    kotlin.math.abs(z - lastAccel[2])
                lastAccel = floatArrayOf(x, y, z)
                val now = System.currentTimeMillis()
                if (delta > SHAKE_THRESHOLD && now - lastShakeTime > 2000) {
                    lastShakeTime = now
                    triggerDizzy()
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        sensorListener = listener
        manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
    }

    private fun triggerDizzy() {
        if (dizzyActive) return
        dizzyActive = true
        faceView.setDizzy(true)
        conversationManager?.speak("샤갈!!!!")
        lifecycleScope.launch {
            var rotation = 0f
            val endTime = System.currentTimeMillis() + 1800
            while (System.currentTimeMillis() < endTime) {
                rotation = (rotation + 24f) % 360f
                faceView.setDizzyRotation(rotation)
                delay(40)
            }
            faceView.setDizzy(false)
            dizzyActive = false
        }
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
                if (!reacting && !dizzyActive) {
                    faceView.setEyeOpenAmount(0f)
                    delay(120)
                    faceView.setEyeOpenAmount(1f)
                }
            }
        }
        lifecycleScope.launch {
            while (true) {
                delay(Random.nextLong(1500, 3500))
                if (!faceVisible && !reacting && !dizzyActive) {
                    faceView.setEyeOffset(
                        Random.nextFloat() * 1.2f - 0.6f,
                        Random.nextFloat() * 0.6f - 0.3f
                    )
                }
            }
        }
    }
}
