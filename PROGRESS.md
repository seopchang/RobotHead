# RobotHead 프로젝트 진행 상황 (2026-07-15 기준)

동아리 부스 전시용 "로봇 머리" 앱. 안드로이드 폰 화면에 얼굴(눈/입)을 띄우고,
카메라로 지나가는 사람 얼굴을 추적해 시선을 맞추고, 음성 대화(STT/TTS + Groq LLM)를
하고, 터치하면 반응하는 독립 실행 앱. 로봇 몸체(아두이노)와의 하드웨어 통신은 없음.

## ⚠️ 중요: 이 PC는 PC방 컴퓨터 (매번 100% 초기화됨)

껐다 켜면 로컬 디스크 내용이 무조건 초기화됨 (확실함, "혹시"가 아님). **코드는 GitHub에 있어서 안전하지만,
Android Studio / SDK / Git / GitHub CLI 등 설치했던 프로그램은 전부 사라져서 매 세션 처음부터 다시 설치해야 함.**
"설치되어 있는지 확인" 같은 거 먼저 해볼 필요 없이, 바로 재설치부터 시작하면 됨.
이 문서 맨 아래에 재설치용 명령어를 정리해뒀음.

## 기술 스택 결정 사항

- Kotlin, 네이티브 안드로이드 (React Native/Expo 아님 — 카메라 얼굴추적 기능 때문에 기각)
- 화면 렌더링: Custom View + Canvas (`FaceView.kt`)
- 시선 추적: CameraX + ML Kit Face Detection (`FaceTracker.kt`) — 전면 카메라로 가장 큰 얼굴 추적, 없으면 idle 랜덤 시선으로 폴백
- 대화: 안드로이드 내장 SpeechRecognizer(STT, 무료) → Groq API(Llama 3.1 8B Instant, 무료 티어) → 안드로이드 내장 TextToSpeech(무료) (`ConversationManager.kt`, `GroqClient.kt`)
- 프로젝트 버전: AGP 9.2.0, Gradle 9.4.1, Kotlin 2.4.10(AGP 내장 Kotlin 지원 사용, kotlin-android 플러그인 안 씀), compileSdk/targetSdk 36, minSdk 26
- 패키지명: `com.robothead.app`

## GitHub

- 저장소: https://github.com/seopchang/RobotHead (private)
- 계정: `seopchang` (gh CLI로 로그인됨, 이 PC에 인증 정보 저장돼있으나 PC 초기화되면 재로그인 필요: `gh auth login --web`)

## 지금까지 한 것

1. Android Studio 설치 완료 (`C:\Program Files\Android\Android Studio`)
2. Android SDK 커맨드라인 도구로 platform-tools, android-36, build-tools 36.0.0 설치 완료 (`%LOCALAPPDATA%\Android\Sdk`)
3. Git, GitHub CLI 설치 완료
4. 프로젝트 스캐폴드 생성 (`C:\Users\Administrator\AndroidStudioProjects\RobotHead`)
5. 기능 코드 전부 작성 완료 (컴파일 확인됨, **폰에서 실제 동작은 아직 확인 안 함**):
   - `FaceView.kt`: 검정 배경 + 흰 눈(동공 있음, 오프셋 따라 이동) + 입(벌어짐 정도 조절 가능), 눈 깜빡임 지원
   - `FaceTracker.kt`: CameraX + ML Kit로 전면 카메라 얼굴 위치 추적 → 눈동자 오프셋 전달
   - `GroqClient.kt`: Groq API(chat completions) 호출하는 순수 HTTP 클라이언트
   - `ConversationManager.kt`: STT로 듣기 → Groq 호출 → TTS로 말하기 반복 루프, 말할 때 입 벌림 애니메이션
   - `MainActivity.kt`: 권한 요청(카메라/마이크), 위 컴포넌트 전부 연결, 터치 반응(위쪽=쓰다듬는 느낌으로 눈 가늘게, 아래쪽=놀란 표정), 대기 중 랜덤 깜빡임/시선 이동
6. `gradlew.bat assembleDebug` 빌드 **성공 확인함** (디버그 APK 생성됨, 최신 기능 포함해서도 성공)
7. GitHub에 push 완료 (커밋 3개: 초기 스캐폴드 → AGP9 Kotlin 플러그인 수정 → 전체 기능 추가)

## 아직 안 한 것 / 다음에 할 일 (우선순위 순)

1. **폰에 실제로 설치해서 눈으로 확인** — 아직 한 번도 실제 기기에서 실행 안 해봄. 지금까지는 전부 "컴파일 성공"만 확인한 상태라 런타임 버그(권한 흐름, 카메라 바인딩, TTS 언어팩 등) 있을 수 있음.
   - 사용자가 케이블이 없어서 무선 디버깅으로 진행하기로 함. 폰에서 개발자 옵션 → 무선 디버깅 켜고 "페어링 코드로 기기 페어링" 눌러서 나오는 IP:포트/6자리 코드 알려주면 `adb pair`, `adb connect`로 연결 후 `gradlew installDebug`로 설치 예정.
2. **Groq API 키 발급 및 입력 필요** — `local.properties`의 `GROQ_API_KEY=` 줄에 키 채워넣어야 대화 기능 실제로 동작함 (지금은 비어있어서 대화 시도하면 조용히 다시 듣기만 반복함). console.groq.com에서 무료로 발급 가능.
3. 실기기 테스트하면서 나올 버그 수정 (특히 카메라 권한 거부 시 처리, TTS 한국어 음성팩 설치 여부, 눈 움직임 자연스러움 튜닝)
4. 터치 반응 존을 이마/코/볼처럼 더 세분화하고 싶으면 `MainActivity.setupTouchReactions()` 손보기 (지금은 화면 상/하 절반으로만 단순 구분)

## PC 초기화됐을 경우 재설치 명령어 (참고용)

```powershell
# Android Studio (약 1.5GB)
Invoke-WebRequest -Uri "https://edgedl.me.gvt1.com/android/studio/install/2026.1.2.10/android-studio-quail2-windows.exe" -OutFile "$env:USERPROFILE\Downloads\android-studio-installer.exe"
Start-Process -FilePath "$env:USERPROFILE\Downloads\android-studio-installer.exe" -ArgumentList "/S" -Wait

# Android SDK 커맨드라인 도구 (약 150MB) — 압축 풀고 licenses 파일 만들고 sdkmanager로 설치하는 절차 필요
# (자세한 절차는 이 대화의 어시스턴트가 기억하고 있음 — 새 세션에서 다시 설명 요청하면 됨)

# Git, GitHub CLI
# https://github.com/git-for-windows/git/releases 에서 최신 -64-bit.exe
# https://github.com/cli/cli/releases 에서 최신 windows_amd64.msi

# 코드는 아래 명령어로 다시 받으면 됨
git clone https://github.com/seopchang/RobotHead.git
```

## 다시 시작할 때 이렇게 말하면 됨

> "하던 RobotHead 프로젝트 이어서 하자, PROGRESS.md 봐줘"

바로 이어서 할 일: 폰 무선 디버깅 연결 → 설치 → 실제 화면/카메라/대화 동작 확인 → 버그 있으면 수정.
