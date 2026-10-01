# Auto Blocker 위젯

삼성 갤럭시(One UI)의 보안 위험 자동 차단(Auto Blocker)을 홈 화면 위젯 탭 한 번으로 켜고 끄는 앱입니다. 위젯만 있고 런처 아이콘은 없습니다.

## 빌드

Gradle 없이 build-tools(`aapt2`, `d8`, `zipalign`, `apksigner`)와 JDK 17로 빌드합니다.

```
./build.sh
```

결과물은 `build/auto-blocker-widget.apk` 입니다. 디버그 키스토어는 `.keystore/debug.jks` 에 처음 한 번 만들어지며 저장소에는 올라가지 않습니다. SDK 경로는 `ANDROID_SDK_ROOT` 로 바꿀 수 있습니다.

## 설치

```
adb install -r build/auto-blocker-widget.apk
```

## 권한 부여 (한 번만)

설정 쓰기에 `WRITE_SECURE_SETTINGS` 가 필요합니다. 설치 직후 다음을 실행합니다.

```
adb shell pm grant com.gml.autoblocker android.permission.WRITE_SECURE_SETTINGS
```

권한이 없으면 위젯에 `권한 필요` 가 표시되고 Toast 로 같은 명령을 안내합니다.

## 키 확인 절차 (사용 전 필수)

위젯은 `Settings.Secure` 의 `auto_blocker_enabled` 키를 읽고 씁니다. 이 키 이름은 커뮤니티 자료 기반이며 기기에서 확인하지 못했습니다. 설정 앱에서 보안 및 개인정보 보호 > 보안 위험 자동 차단을 켜고 끄면서 값이 0과 1로 바뀌는지 먼저 봅니다.

```
adb shell settings get secure auto_blocker_enabled
```

값이 바뀌지 않거나 `null` 이면 키 이름이 다른 것입니다. 이 경우 `src/com/gml/autoblocker/AutoBlockerWidget.java` 의 `KEY` 상수 한 곳만 고치고 다시 빌드합니다.

## 위젯 추가

홈 화면의 빈 곳을 길게 눌러 위젯 메뉴를 열고 `Auto Blocker 위젯` 을 찾아 2x1 크기로 배치합니다. 켜짐은 초록, 꺼짐은 회색, 오류는 빨강으로 표시됩니다. 탭하면 토글합니다. 쓴 값을 다시 읽어 일치하지 않으면 실패로 표시합니다. 자동 갱신 주기는 안드로이드가 허용하는 최소값(30분)입니다.

## 제약

- 키 이름 `auto_blocker_enabled` 는 미확인입니다.
- One UI 업데이트로 키 이름이나 동작이 바뀔 수 있습니다.
- 이 저장소의 검증은 빌드, 서명, 매니페스트 확인까지입니다. 실제 기기 동작은 검증하지 못했습니다.
- 위젯 수신기는 시스템 위젯 갱신을 받기 위해 `exported` 입니다. 다른 앱이 토글 브로드캐스트를 보낼 수 있습니다.
