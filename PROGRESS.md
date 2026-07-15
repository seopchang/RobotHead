# RobotHead 프로젝트 진행 상황 (2026-07-15 기준)

동아리 부스 전시용 "로봇 머리" 앱. 안드로이드 폰 화면에 얼굴(눈/입)을 띄우고,
카메라로 지나가는 사람 얼굴을 추적해 시선을 맞추고, 음성 대화(STT/TTS + Groq LLM)를
하고, 터치하면 반응하는 독립 실행 앱. 로봇 몸체(아두이노)와의 하드웨어 통신은 없음.

## ⚠️ 중요: 이 PC는 PC방 컴퓨터

재부팅/재시작하면 로컬 디스크 내용이 초기화될 수 있음. **코드는 GitHub에 있어서 안전하지만,
Android Studio / SDK / Git / GitHub CLI 같은 설치 프로그램은 다음에 다시 설치해야 할 수 있음.**
이 문서 맨 아래에 재설치용 명령어를 정리해뒀음.

## 기술 스택 결정 사항

- Kotlin, 네이티브 안드로이드 (React Native/Expo 아님 — 카메라 얼굴추적 기능 때문에 기각)
- 화면 렌더링: Custom View + Canvas
- 시선 추적: CameraX + ML Kit Face Detection (예정, 아직 미구현)
- 대화: 안드로이드 내장 SpeechRecognizer(STT, 무료) → Groq API(Llama 3.1 8B Instant, 무료 티어) → 안드로이드 내장 TextToSpeech(무료)
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
   - `FaceView.kt`: 검정 배경 + 하늘색 눈 2개 + 입 라인을 Canvas로 그리는 커스텀 View
   - `MainActivity.kt`: FaceView를 전체화면(상태바/네비바 숨김)으로 표시
5. `gradlew.bat assembleDebug` 빌드 **성공 확인함** (디버그 APK 생성됨)
6. GitHub에 push 완료 (커밋 2개: 초기 스캐폴드, AGP9 Kotlin 플러그인 수정)

## 아직 안 한 것 / 다음에 할 일 (우선순위 순)

1. **폰에 실제로 설치해서 눈으로 확인** — 지금 여기서 멈춤. 사용자가 폰에 무선 디버깅(개발자 옵션 → 무선 디버깅 → 페어링 코드로 기기 페어링) 켜고 IP:포트/코드 알려주면 `adb pair`, `adb connect`로 연결 후 `gradlew installDebug`로 설치 예정.
2. 카메라 얼굴 추적 (ML Kit) 연동 — 사람 얼굴 위치 따라 눈동자 움직이기
3. STT/TTS + Groq API 대화 루프 연결 — Groq API 키 아직 발급 안 받음, 다음 세션에서 발급 필요
4. 터치 반응 (이마/코/볼 영역별 표정 변화)
5. 대기 애니메이션 (사람 없을 때 랜덤 깜빡임/시선 이동)
6. 전체 기능 테스트

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

## 내일 다시 시작할 때 이렇게 말하면 됨

> "어제 하던 RobotHead 프로젝트 이어서 하자, PROGRESS.md 봐줘"
