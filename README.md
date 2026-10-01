# Auto Blocker 위젯

삼성 갤럭시(One UI)의 보안 위험 자동 차단(Auto Blocker)을 홈 화면 위젯 탭 한 번으로 켜고 끄는 앱입니다. 위젯만 있고 런처 아이콘은 없습니다. 기기 SM-F971N, One UI 9.0 에서 관측한 동작을 기준으로 만들었습니다.

## 동작

- 위젯은 접근성 서비스가 rampart 설정 화면에서 마지막으로 본 스위치 상태(캐시)를 방패 아이콘과 두 줄 텍스트(제목 `보안 위험 자동 차단`, 상태 `켜짐` 또는 `꺼짐`)로 표시합니다. 켜짐은 초록, 꺼짐은 호박색입니다. 제목 줄 끝에 `HH:mm 확인` 으로 마지막 확인 시각을 작게 보입니다. 캐시가 없으면 슬레이트 색으로 `상태 미확인` 만 표시합니다. 위젯을 한 번 탭하면 스위치 상태를 읽어 표시가 바뀝니다. 설정 값은 읽지도 쓰지도 않습니다.
- 위젯을 탭하면 투명 액티비티가 켜짐 표식(5초 유효)을 남기고 One UI 의 Auto Blocker 설정 화면(`com.samsung.android.rampart`)을 엽니다.
- 접근성 서비스는 rampart 창 이벤트마다 스위치(`sesl_switchbar_switch`)의 `isChecked()` 를 읽어 캐시를 갱신합니다. 표식이 유효한 5초 안에만 스위치 행(`sesl_switchbar_container`)을 한 번 탭합니다. 같은 창에서 `isChecked()` 가 탭 전 값과 달라지면 홈으로 돌아가고 위젯을 갱신합니다. 값이 바뀌기 전에는 홈이나 뒤로 가기를 보내지 않습니다.
- 끌 때는 시스템이 지문 또는 비밀번호 인증 창을 띄웁니다. 인증은 사용자가 직접 합니다. 인증을 취소해 값이 안 바뀌면 10초 뒤 감시를 접고 아무것도 하지 않습니다.
- 사용자가 직접 그 설정 화면을 열었을 때는 표식이 없으므로 서비스는 아무것도 누르지 않습니다.

## 빌드

Gradle 없이 build-tools(`aapt2`, `d8`, `zipalign`, `apksigner`)와 JDK 17로 빌드합니다.

```
./build.sh
```

결과물은 `build/auto-blocker-widget.apk` 입니다. 디버그 키스토어는 `.keystore/debug.jks` 에 처음 한 번 만들어지며 저장소에는 올라가지 않습니다. SDK 경로는 `ANDROID_SDK_ROOT` 로 바꿀 수 있습니다.

## 설치 (3줄)

```
adb install -r build/auto-blocker-widget.apk
./enable_accessibility.sh <adb시리얼>
```

세 번째로 홈 화면의 빈 곳을 길게 눌러 위젯 메뉴를 열고 `Auto Blocker 위젯` 을 2x1 크기로 배치합니다.

`enable_accessibility.sh` 는 실행 전에 기존 `enabled_accessibility_services` 값을 출력하고 우리 서비스가 없을 때만 `:` 로 이어 붙입니다. 기존 값은 덮어쓰지 않습니다. shell 권한의 `settings put secure` 를 쓰므로 폰 조작이 필요 없습니다.

## 제약

- 일반 앱은 `rampart_main_switch_enabled` 를 읽을 수 없습니다(`Settings key ... is not readable`, 안드로이드 12 이상의 @hide 키 제한). adb shell 에서만 읽힙니다.
- 위젯 표시는 rampart 화면에서 마지막으로 본 값의 캐시입니다. 화면 밖에서 상태가 바뀌면(30분 뒤 자동으로 다시 켜지는 경우 포함) 표시가 낡을 수 있습니다.
- One UI 가 `rampart_main_switch_enabled` 쓰기를 거부하므로(`RAMPART_SettingsProvider: Not allowed to put`) 설정 화면 UI 를 자동으로 누르는 방식입니다.
- 설정 화면의 viewId 가 바뀌면 동작하지 않습니다. One UI 업데이트에 취약합니다.
- One UI 에는 끄고 30분 뒤 다시 켜는 `자동으로 켜기` 설정이 있습니다(관측).
- Auto Blocker 를 켜면 무선 디버깅이 끊깁니다. 끄면 복구됩니다.
- 안드로이드 13 이상의 제한된 설정이 sideload 앱의 접근성 활성화를 UI 에서 막을 수 있습니다. adb 의 `settings put` 경로는 그 제한을 거치지 않습니다. 이 기기에서는 서비스가 `dumpsys accessibility` 에 바인드된 것을 확인했습니다.
- 이 저장소의 검증은 빌드, 서명, 서비스 바인드, 설치 뒤 앱 프로세스 크래시 없음까지입니다. 탭으로 켜고 끄는 경로와 끄기 때의 인증 창 동작은 사용자 시험으로 확인합니다.
