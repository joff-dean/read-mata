# Read Mata

관심 있는 최신 글을 읽기 큐로 만들고 Android TTS로 들려주는 개인 라디오 프로토타입입니다.

## 현재 구현된 범위

- HTTPS RSS 2.0 / Atom 주소 직접 입력
- 최신 항목 최대 20개 로드 및 중복 제거
- 제목·요약의 HTML 정리와 XML 외부 엔티티 차단
- 한국어 Android TTS로 제목과 피드 요약 연속 재생
- 재생, 일시정지, 다음 글 조작과 현재 진행 상태 표시
- 오디오 포커스 처리: 다른 앱의 오디오가 시작되면 일시정지
- 앱이 화면에서 사라질 때 음성 중지

현재 버전은 피드가 제공하는 `title`과 `description`/`summary`/`content`만 읽습니다. 링크된 기사 본문을 다시 내려받거나, 일반 웹사이트를 탐색하거나, 다른 Android 앱을 조작하지는 않습니다.

## 구조

```text
HTTPS RSS/Atom
    -> HttpFeedSource (크기·시간·HTTPS 제한)
    -> RssAtomParser + HtmlText
    -> FeedItem 읽기 큐
    -> TextChunker
    -> Android TextToSpeech
    -> Compose 화면
```

주요 코드는 다음 위치에 있습니다.

- `feed/`: 소스 어댑터, HTTP 로더, RSS/Atom 파서
- `playback/`: TTS 큐, 오디오 포커스, 문장 분할
- `ui/`: Jetpack Compose 화면
- `MainController.kt`: 로딩 상태와 재생 계층 연결

## 빌드

요구 환경은 JDK 17과 Android SDK 36입니다. Android Studio에서 프로젝트를 열어 실행하거나 PowerShell에서 다음 명령을 사용할 수 있습니다.

```powershell
$env:JAVA_HOME='C:\Program Files\Microsoft\jdk-17.0.19.10-hotspot'
$env:ANDROID_SDK_ROOT='D:\AndroidStudioProjects\SDK'
.\gradlew.bat testDebugUnitTest assembleDebug
```

생성되는 디버그 APK:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## 사용법

1. 앱을 실행합니다.
2. HTTPS RSS/Atom URL을 입력합니다. 초기값은 AndroidX 릴리스 노트 피드입니다.
3. **최신 글 불러오기**를 누릅니다.
4. 목록을 확인한 뒤 **재생**을 누릅니다.
5. 앱을 다른 화면으로 보내면 현재 음성이 멈추며, 돌아와 재생하면 현재 문단 처음부터 다시 읽습니다.

## 의도적으로 제외한 것

- 로그인·쿠키가 필요한 페이지 및 유료 콘텐츠 우회
- 임의 웹페이지의 안정적인 본문 추출
- 백그라운드·잠금 화면 재생과 미디어 알림
- WebView 내 자동 탐색
- AccessibilityService를 통한 외부 앱 조작
- 관심도 모델, 읽음 기록, 구독 저장

## 다음 구현 순서

1. 특정 뉴스/블로그용 `ArticleAdapter`를 추가해 링크 본문을 안전하게 추출합니다.
2. 구독 소스, 읽음 기록, 사용자 키워드를 로컬 DB에 저장하고 큐 점수를 계산합니다.
3. 백그라운드 재생 단계에서는 TTS를 Media3 `Player`로 감싸고 `MediaSessionService`로 재생 소유권을 옮깁니다.
4. WebView 탐색은 허용된 도메인과 사전 정의 액션으로 제한해 추가합니다.
5. 외부 앱 자동화는 마지막 단계에서 개인용 Accessibility 확장으로 분리하고, 앱별 어댑터·사용자 확인·중단 스위치를 둡니다.

Accessibility 확장을 Play Store에 배포하려면 접근성 도구 여부, 고지·동의, 자동화 동작과 관련된 최신 Google Play 정책을 별도로 검토해야 합니다.
