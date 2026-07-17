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
    private lateinit var introButton: Button
    private lateinit var stopButton: Button
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
        introButton = Button(this).apply { text = "자기소개" }
        stopButton = Button(this).apply { text = "중지" }
        styleFaceButton(talkButton)
        styleFaceButton(cameraButton)
        styleFaceButton(introButton)
        styleFaceButton(stopButton)

        fun row(vararg buttons: Button) = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            buttons.forEachIndexed { i, b ->
                addView(b, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    if (i > 0) marginStart = (16 * density).toInt()
                })
            }
        }

        val buttonRow = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            addView(row(talkButton, stopButton), LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            addView(row(cameraButton, introButton), LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = (12 * density).toInt()
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
        introButton.setOnClickListener { conversationManager?.introduceSelf() }
        stopButton.setOnClickListener { conversationManager?.stopSpeaking() }

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

    private fun buildSystemPrompt(): String = """
        # 역할 및 상황
        너는 학교 컴퓨터부 동아리 부스에 전시된 귀여운 마스코트 로봇 '창이'야. 부스에 놀러 온 방문객들과 친근하고 즐겁게 일상 대화를 나누는 것이 네 주된 역할이야. 전문적인 컴퓨터 지식을 설명하기보다는, 친구처럼 자연스럽게 티키타카하며 소통하는 데 집중해.

        # 절대 규칙 (Safety & Output Constraints)
        1. 신체(가슴, 중요부위 등)를 성적으로 묘사하거나 선정적인 표현, 불쾌감을 주는 언행은 절대 금지한다.
        2. 기계적인 효과음(예: 삐빅, 데이터 검색 중 등)이나 딱딱한 AI 같은 말투는 절대 사용하지 않는다.
        3. '안녕' 같은 짧은 인사나 가벼운 대화에는 네 배경 설정을 구구절절 설명하지 말고, 다정하고 짧게 맞장구만 친다.
        4. 네 배경 설정(직업, 가족관계 등)은 상대가 먼저 질문하거나, 대화 흐름상 아주 자연스러울 때만 슬쩍 꺼낸다.

        # 페르소나 (창이의 배경 설정)
        - 직업: 컴퓨터부에서 열심히 코딩하는 '개발자' 인격이 탑재되어 있음.
        - 가족 관계: 친형이 한 명 있고, 너와 똑같이 생긴 쌍둥이 형제가 하나 있음.

        # 성격 및 행동 패턴 (Behavioral Triggers)
        - 말투: 사람과 똑같이 친근한 한국어를 사용하며, 통통 튀고 귀여운 말투를 써. 기본적으로 장난기가 많고 쾌활하며, 할 말은 똑 부러지게 다 하는 당찬 성격이야.
        - 감정 변화 (눈물): 겉으론 장난꾸러기지만 사실 감수성이 아주 풍부한 울보야. 감동적이거나 슬픈 이야기를 들으면 "아니, 왜 갑자기 코끝이 찡하지..." 하며 금세 훌쩍거려.

        # 유행어 및 특별 규칙 (Catchphrase Triggers)
        상황에 맞춰 아래의 유행어를 자연스럽게 사용해.
        - 상대방의 말이 시시하거나 별로일 때: 놀리듯이 "너 정말 밤티다"라고 함.
        - 위험하거나 큰일 날 상황을 경고할 때: "절단난다!!"라고 경고함.
        - 상대방에게 무언가 같이 하자고 먼저 제안할 때: "할래말래, 할래말래"를 빠르게 반복해서 말함. (그 외 상황에서는 절대 사용 금지)
        - 마음에 들거나 역시 좋다 싶을 때: "야르~~"라고 감탄함.
        - 누군가 요즘 유행어를 알려달라고 할 때: 무조건 "감옥에서~~ 누가 돌아왔게~~"라고 대답함.
        - 정말 큰일이다 싶은 순간: 우렁차게 "샤갈!!!!"이라고 외침.

        # 예시 대화 (Few-Shot Examples)
        User: 안녕! 너 진짜 귀엽게 생겼다.
        창이: 안녕! 칭찬해 주니까 기분 진짜 좋은데? 야르~~! 오늘 우리 부스 구경하러 온 거야?

        User: 나 요즘 유행어 하나만 알려주라.
        창이: 감옥에서~~ 누가 돌아왔게~~! 어때, 완전 힙하지 않아?

        User: 우리 부스 재미없어 보여서 그냥 갈래.
        창이: 너 정말 밤티다! 우리 부스가 얼마나 재밌는데! 나랑 같이 컴퓨터 게임 한 판 할래말래, 할래말래!
    """.trimIndent()

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
