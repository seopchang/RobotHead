# RobotHead 프로젝트 진행 상황 (2026-07-16 기준)

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
4. 프로젝트 클론 위치: `C:\Users\Administrator\RobotHead` (세션마다 위치가 바뀔 수 있음, 중요하지 않음 — 그냥 `gh repo clone seopchang/RobotHead`로 아무 데나 받으면 됨)
5. 기능 코드 전부 작성 완료:
   - `FaceView.kt`: 검정 배경 + 흰 눈(동공 있음, 오프셋 따라 이동) + 입(벌어짐 정도 조절 가능), 눈 깜빡임 지원
   - `FaceTracker.kt`: CameraX + ML Kit로 전면 카메라 얼굴 위치 추적 → 눈동자 오프셋 전달
   - `GroqClient.kt`: Groq API(chat completions) 호출하는 순수 HTTP 클라이언트
   - `ConversationManager.kt`: STT로 듣기 → Groq 호출 → TTS로 말하기 반복 루프, 말할 때 입 벌림 애니메이션
   - `MainActivity.kt`: 권한 요청(카메라/마이크), 위 컴포넌트 전부 연결, 터치 반응(위쪽=쓰다듬는 느낌으로 눈 가늘게, 아래쪽=놀란 표정), 대기 중 랜덤 깜빡임/시선 이동
6. `gradlew.bat assembleDebug` 빌드 **성공 확인함** (2026-07-16 세션에서 재확인, 여전히 성공)
7. GitHub에 push 완료 (커밋 3개: 초기 스캐폴드 → AGP9 Kotlin 플러그인 수정 → 전체 기능 추가)
8. `local.properties`의 `GROQ_API_KEY`에 실제 키 입력 완료 (사용자가 console.groq.com에서 발급받아 직접 메모장에 입력함). **단, `local.properties`는 `.gitignore`에 있어서 깃에 안 올라감 → PC 초기화되면 이 키도 사라지므로 매번 다시 입력해야 함.**
9. **아직 실기기에서 실행 못 해봄** — 이 PC방 컴퓨터는 Wi-Fi 어댑터가 아예 없고(유선 랜카드만 있음, `Get-NetAdapter`로 확인됨) 유선 연결 IP도 `106.249.15.82` 같은 사설 대역이 아닌 공인 IP라서, **무선(Wireless) ADB 디버깅은 이 PC에서 원천적으로 불가능**함 (폰과 같은 네트워크에 잡을 방법이 없음, 폰 핫스팟에 붙일 Wi-Fi 어댑터도 없음). → **다음엔 무선 디버깅 시도하지 말고 바로 USB 케이블 유선 디버깅으로 진행할 것.**

## 아직 안 한 것 / 다음에 할 일 (우선순위 순)

1. **USB 케이블로 유선 ADB 연결 → 실기기 설치 및 확인** (무선 디버깅 아님, 위 이유 참고)
   - 사용자가 USB 데이터 케이블 구했는지 먼저 확인 (카운터 대여 가능한 경우 많음)
   - 폰: 설정 → 개발자 옵션 → **USB 디버깅** 켜기 (무선 디버깅 아님)
   - 케이블 연결 → 폰에 뜨는 "USB 디버깅 허용" 팝업 승인
   - `adb devices`로 인식 확인 → `gradlew installDebug`로 설치
   - 지금까지는 전부 "컴파일 성공"만 확인한 상태라 런타임 버그(권한 흐름, 카메라 바인딩, TTS 언어팩 등) 있을 수 있음
2. 실기기 테스트하면서 나올 버그 수정 (특히 카메라 권한 거부 시 처리, TTS 한국어 음성팩 설치 여부, 눈 움직임 자연스러움 튜닝)
3. 터치 반응 존을 이마/코/볼처럼 더 세분화하고 싶으면 `MainActivity.setupTouchReactions()` 손보기 (지금은 화면 상/하 절반으로만 단순 구분)

## PC 초기화됐을 경우 재설치 명령어 (2026-07-16 세션에서 검증된 방법)

이 PC는 `winget`이 없고 대신 **Chocolatey(`choco`)가 이미 설치되어 있음** — choco로 진행하면 됨.

```powershell
# PATH 새로고침 없이도 바로 되는지 확인 후, 아래 순서로 진행
choco install git gh -y --no-progress
choco install androidstudio -y --no-progress   # 시간 오래 걸림, run_in_background 권장

# 새 PowerShell 프로세스에서는 매번 PATH를 다시 읽어와야 git/gh/choco가 잡힘 (세션 간 변수 유지 안 됨):
$env:Path = [System.Environment]::GetEnvironmentVariable("Path","Machine") + ";" + [System.Environment]::GetEnvironmentVariable("Path","User")

# GitHub 로그인 (대화형 — 사용자가 브라우저에서 코드 입력해야 함)
gh auth login --hostname github.com --git-protocol https --web
# → 코드/URL이 출력되면 사용자에게 https://github.com/login/device 에서 코드 입력 요청

# 코드 클론
gh repo clone seopchang/RobotHead

# Android SDK cmdline-tools 설치 (Android Studio 자체 설치는 SDK를 자동으로 안 깔아줌)
$sdkRoot = "$env:LOCALAPPDATA\Android\Sdk"
Invoke-WebRequest -Uri "https://dl.google.com/android/repository/commandlinetools-win-14742923_latest.zip" -OutFile "$env:TEMP\cmdline-tools.zip"
Expand-Archive -Path "$env:TEMP\cmdline-tools.zip" -DestinationPath "$env:TEMP\cmdline-tools-extract" -Force
New-Item -ItemType Directory -Force -Path "$sdkRoot\cmdline-tools" | Out-Null
Move-Item -Path "$env:TEMP\cmdline-tools-extract\cmdline-tools" -Destination "$sdkRoot\cmdline-tools\latest" -Force

# 라이선스 대화형 프롬프트 파이핑이 안 먹혀서(스킵됨), CI 방식대로 해시 파일을 직접 생성 (더 안정적)
New-Item -ItemType Directory -Force -Path "$sdkRoot\licenses" | Out-Null
Set-Content -Path "$sdkRoot\licenses\android-sdk-license" -Value @("8933bad161af4178b1185d1a37fbf41ea5269c55","d56f5187479451eabf01fb78af6dfcb131a6481e","24333f8a63b6825ea9c5514f83c2829b004d1fee") -Encoding ascii
Set-Content -Path "$sdkRoot\licenses\android-sdk-preview-license" -Value "84831b9409646a918e30573bab4c9c91346d8abd" -Encoding ascii

$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"   # Android Studio에 JDK 번들되어 있음, 따로 설치 불필요
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
& "$sdkRoot\cmdline-tools\latest\bin\sdkmanager.bat" --sdk_root="$sdkRoot" "platform-tools" "platforms;android-36" "build-tools;36.0.0"

# local.properties 직접 생성 (sdk.dir + GROQ_API_KEY 채워야 함, 이 파일은 .gitignore돼서 매번 새로 만들어야 함)
```

## 다시 시작할 때 이렇게 말하면 됨

> "하던 RobotHead 프로젝트 이어서 하자, PROGRESS.md 봐줘. 이번엔 USB 케이블로 유선 디버깅 연결해서 실기기 테스트부터 시작하면 돼 (무선 디버깅은 이 PC방 컴퓨터에서 안 되는 거 확인함)."

바로 이어서 할 일: USB 케이블 유선 ADB 연결 → `adb devices` 확인 → `gradlew installDebug` → 실제 화면/카메라/대화 동작 확인 → 버그 있으면 수정.
