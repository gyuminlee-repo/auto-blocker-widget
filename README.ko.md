# ShieldTap

<p align="center">
  <img src="docs/media/icon.png" alt="ShieldTap 아이콘: 초록 바탕에 체크 표시가 있는 흰 방패" width="100">
</p>

<p align="center">
  <a href="README.md">English</a> · <strong>한국어</strong>
</p>

<p align="center">
  <a href="#install">설치</a> ·
  <a href="docs/guide.ko.md#동작-원리">동작 원리</a> ·
  <a href="docs/guide.ko.md#보안-메모">보안</a> ·
  <a href="#docs">문서</a> ·
  <a href="https://github.com/shieldtap/shieldtap/releases/latest">릴리스</a>
</p>

<p align="center">
  <a href="LICENSE"><img src="https://img.shields.io/badge/license-Apache--2.0-2E7D32?labelColor=333333" alt="Apache-2.0 라이선스"></a>
  <a href="https://github.com/shieldtap/shieldtap/releases/latest"><img src="https://img.shields.io/github/v/release/shieldtap/shieldtap?label=release&amp;color=2E7D32&amp;labelColor=333333" alt="최신 릴리스"></a>
  <a href="docs/guide.ko.md#호환성"><img src="https://img.shields.io/badge/One%20UI-6%2B-2E7D32?labelColor=333333" alt="One UI 6 이상"></a>
  <a href="docs/guide.ko.md#보안-메모"><img src="https://img.shields.io/badge/permissions-0-2E7D32?labelColor=333333" alt="요청 권한 0개"></a>
</p>

---

<p align="center">
  <img src="docs/widget_states.png" width="600" alt="ShieldTap 아이콘과 이름 아래 2x1 위젯이 세 개씩 두 줄 있다. 컬러 줄은 초록 켜짐, 호박색 꺼짐, 슬레이트색 상태 미확인. 모노톤 줄은 같은 세 상태가 명도로 갈린다. 켜짐은 밝은 회색 바탕에 진한 글자, 꺼짐은 어두운 회색, 상태 미확인은 중간 회색이다. 위젯마다 방패 아이콘이 있다.">
</p>

<p align="center"><sub>위젯의 세 상태(켜짐, 꺼짐, 미확인) · 기기 캡처가 아니라 앱 레이아웃과 영어 문구로 그린 미리보기 · 한국어 폰에서는 <code>켜짐</code> 처럼 보입니다</sub></p>

**홈 화면에서 탭 한 번으로 보안 위험 자동 차단을 켜고 끕니다.**

갤럭시의 보안 위험 자동 차단은 켜 두면 안전합니다. 그런데 APK 를 설치하거나 무선 디버깅을 할 때마다 설정 깊숙이 들어가 꺼야 합니다. ShieldTap 은 그 스위치를 크기 조절이 되는 위젯(기본 2x1)으로 꺼내 현재 상태를 색과 글자로 보여 줍니다.

<table>
  <tr>
    <td width="50%" valign="top">
      <img src="docs/media/setup_steps.png" width="100%" alt="설정 안내 화면에 다섯 단계가 있다. 1단계 제한된 설정 허용이 펼쳐져 Open App info 와 Done 버튼이 보이고 2~5단계는 아래에서 기다린다.">
      <br><b>다섯 단계 안내대로 설정</b>
      <br><sub>단계마다 필요한 설정 화면을 버튼으로 열고 끝나면 체크 표시로 바뀝니다.</sub>
    </td>
    <td width="50%" valign="top">
      <img src="docs/media/finish_up.png" width="100%" alt="1~4단계에 체크 표시가 있고 5단계가 펼쳐져 Play Protect 설정 열기, Auto Blocker 켜기, 자동 켜기 옵션 열기, 설정 마무리 버튼이 보인다.">
      <br><b>설치 뒤 보호 기능 다시 켜기</b>
      <br><sub>5단계에서 설치하려고 끈 Play 프로텍트 앱 검사와 보안 위험 자동 차단을 다시 켭니다.</sub>
    </td>
  </tr>
  <tr>
    <td width="50%" valign="top">
      <img src="docs/media/all_set.png" width="100%" alt="초록 테두리의 준비 완료 카드에 차단 켜짐과 마지막 확인 시각이 있고 아래 다섯 단계가 모두 체크 표시다.">
      <br><b>상태를 한눈에 확인</b>
      <br><sub>설정이 끝나면 마지막으로 읽은 상태와 그 시각을 보여 줍니다.</sub>
    </td>
    <td width="50%" valign="top">
      <img src="docs/media/language.png" width="100%" alt="앱 맨 아래 언어 목록에 시스템 기본값이 선택돼 있고 English 와 한국어가 다른 선택지로 있다.">
      <br><b>영어나 한국어 선택</b>
      <br><sub>폰 언어를 따르고 화면 맨 아래에서 직접 고를 수도 있습니다.</sub>
    </td>
  </tr>
</table>

<p align="center"><sub>그림은 기기 캡처가 아니라 앱 문구와 레이아웃으로 다시 그린 영어 화면입니다. 한국어 폰에서는 같은 자리에 한국어 문구가 나옵니다.</sub></p>

- **탭 한 번으로 전환.** 컬러 스타일에서 켜짐은 초록, 꺼짐은 호박색이고 상태를 마지막으로 읽은 시각이 함께 나옵니다. [위젯 상태 →](docs/guide.ko.md#위젯-상태)
- **크기 조절과 여러 스타일.** 한 칸까지 줄이면 방패 아이콘만 보입니다. 위젯 목록에서 3x1 위젯(ShieldTap + Play 프로텍트)을 고르면 Play 프로텍트 설정 바로가기가 함께 보입니다. 위젯을 길게 눌러 설정을 열면 위젯마다 컬러, 모노톤, 사용자 지정 중에서 고를 수 있습니다. Android 12 이상에서는 배경화면 색을 따르는 시스템 색도 고를 수 있습니다. 사용자 지정은 상태마다 색을 프리셋, 색상·채도·명도 슬라이더, #RRGGBB 코드로 고릅니다. [위젯 상태 →](docs/guide.ko.md#위젯-상태)
- **빠른 설정에서도 전환.** 알림창을 내려 타일 편집(연필 또는 편집)을 열고 ShieldTap 을 타일 목록으로 끌어 놓습니다. 타일은 위젯과 같은 상태를 보여 주고 같은 방식으로 전환합니다. [빠른 설정 타일 →](docs/guide.ko.md#빠른-설정-타일)
- **자동화 앱 요청도 받습니다.** MacroDroid 나 Tasker 를 이미 쓰고 있다면 ShieldTap 을 통해 켜고 끌 수 있습니다. 끌 때는 여전히 지문 또는 비밀번호 인증을 거칩니다. [고급: 자동화 앱 →](#advanced-automation-apps)
- **권한 0개.** 매니페스트에 인터넷을 포함해 `uses-permission` 이 하나도 없습니다. 어떤 데이터도 폰 밖으로 나가지 않습니다. [보안 메모 →](docs/guide.ko.md#보안-메모)
- **화면 하나만 봅니다.** 접근성 서비스는 보안 위험 자동 차단 시스템 앱의 이벤트만 받습니다. 위젯, 빠른 설정 타일, 설정 안내의 `보안 위험 자동 차단 켜기` 버튼 중 하나를 누르거나 자동화 앱이 켜기 또는 끄기를 요청한 뒤 5초 안에만 탭합니다. [동작 원리 →](docs/guide.ko.md#동작-원리)
- **인증은 그대로.** 끌 때 뜨는 지문 또는 비밀번호 인증은 사용자가 직접 통과합니다.
- **좌표가 아닌 ID 로 스위치를 찾습니다.** 좌표가 아니라 내부 ID 로 찾으므로 화면 크기와 해상도와는 관계없습니다. 지금까지 확인한 기기는 1대입니다. [호환성 →](docs/guide.ko.md#호환성)
- **영어와 한국어 지원.** 앱 맨 아래의 언어 선택은 Android 13 이상에서 시스템의 앱별 언어와 같은 값입니다.

---

<a id="install"></a>

## 설치

**[최신 APK 받기](https://github.com/shieldtap/shieldtap/releases/latest)** (`shieldtap-v<버전>.apk`). 받은 파일을 폰에서 엽니다.

보안 위험 자동 차단이 있는 One UI 6 이상이 필요합니다. 설치는 Android 8.0(API 26) 이상이면 되지만 One UI 6 미만에는 켤 대상이 없습니다. One UI 9.0 기기에서 확인했습니다. [호환성 →](docs/guide.ko.md#호환성)

**설치 전에 두 가지를 끕니다.**

1. **보안 위험 자동 차단.** 설정 > 보안 및 개인정보 보호 > 보안 위험 자동 차단. 켜져 있으면 공식 스토어 밖의 APK 설치가 막힙니다.
2. **Play 프로텍트 앱 검사.** Play 스토어 > 프로필 > Play 프로텍트 > ⚙ > `Play 프로텍트로 앱 검사`. 내려받은 APK 가 접근성 권한을 요청하면 Play 프로텍트가 설치를 막습니다. ShieldTap 은 접근성 서비스입니다.

**그다음 설정합니다.**

1. APK 를 설치하고 Play 프로텍트 앱 검사를 다시 켭니다.
2. 앱 서랍의 **ShieldTap** 을 열어 화면의 다섯 단계를 따릅니다. ① `권한 허용(제한된 설정)` ② `접근성 켜기` ③ `홈 화면에 위젯 추가` ④ `현재 상태 읽어 오기` ⑤ `설치 뒤 마무리` 순서입니다.
3. 5단계에서 보안 위험 자동 차단을 다시 켭니다. One UI 8.5 이상이면 `자동으로 켜기` 를 켜 두세요. 위젯으로 끈 뒤 30분이 지나면 다시 켜집니다.
4. `준비 완료` 가 뜨면 끝입니다. 그다음부터 위젯을 탭할 때마다 켜짐과 꺼짐이 바뀝니다. [단계별 설정 →](docs/guide.ko.md#단계별-설정)

**업데이트**는 앱의 `업데이트 확인` 으로 합니다. 최신 릴리스 페이지가 열리면 새 APK 를 받아 덮어 설치합니다. 설정과 위젯은 그대로 남습니다. **삭제**는 `앱 정보 열기 (삭제)` 로 합니다. [업데이트와 삭제 →](docs/guide.ko.md#업데이트와-삭제)

개발자는 adb 로 설치하고 스크립트로 접근성을 켤 수 있습니다. [개발자용 adb 설치 →](docs/guide.ko.md#개발자용-adb-설치)

<a id="advanced-automation-apps"></a>

## 고급: 자동화 앱

APK 설치는 가끔 하는 일입니다. 보안 위험 자동 차단 창에 설치가 막히면 위젯이나 빠른 설정 타일을 누르세요. 다른 앱이 필요 없고 배터리도 들지 않습니다. MacroDroid 나 Tasker 를 이미 쓰고 있다면 창이 뜰 때 ShieldTap 에 끄기를 요청하게 할 수 있습니다. 끌 때는 여전히 지문 또는 비밀번호 인증을 거치고 인증 뒤에는 설치하던 화면으로 돌아옵니다.

| | MacroDroid (확인함) | Tasker (미확인) |
|---|---|---|
| 트리거 | 화면 내용(Screen Content)에 창 제목: 한국어 `출처를 알 수 없는 앱 차단됨`, 영어 `Unknown app blocked` | Event > Plugin > AutoInput > UI Update 에 같은 제목 |
| 자기 편집 화면 제외 | `Don't read when MacroDroid is open` 켜기 | App 조건에 Tasker 를 고르고 Invert 켜기 |
| 동작 | 인텐트 보내기(Send Intent): 대상 `Activity`, 액션 `com.gml.autoblocker.action.TURN_OFF`, 패키지 `com.gml.autoblocker` | Send Intent: 같은 액션과 패키지, 클래스 `com.gml.autoblocker.ActionActivity`, 대상 `Activity` |

MacroDroid 무료판은 화면 내용을 2초마다 읽습니다. `TURN_ON` 과 앱 바로가기도 같은 방식으로 씁니다. 여러 언어를 함께 잡는 정규식과 화면을 읽지 않는 트리거는 [자동화 앱 연동 →](docs/guide.ko.md#자동화-앱-연동) 에 있습니다.

<a id="docs"></a>

## 문서

[사용 안내](docs/guide.ko.md)부터 보세요. [위젯 상태](docs/guide.ko.md#위젯-상태) · [호환성](docs/guide.ko.md#호환성) · [단계별 설정](docs/guide.ko.md#단계별-설정) · [업데이트와 삭제](docs/guide.ko.md#업데이트와-삭제) · [개발자용 adb 설치](docs/guide.ko.md#개발자용-adb-설치) · [자동화 앱 연동](docs/guide.ko.md#자동화-앱-연동) · [동작 원리](docs/guide.ko.md#동작-원리) · [보안 메모](docs/guide.ko.md#보안-메모) · [제약](docs/guide.ko.md#제약) · [아이콘 출처](docs/guide.ko.md#아이콘-출처).

## 개발

Gradle 없이 빌드합니다. Android build-tools(`aapt2`, `d8`, `zipalign`, `apksigner`), `android-34` 플랫폼, JDK 17 이 필요합니다. SDK 가 기본 경로에 없으면 `ANDROID_SDK_ROOT` 를 지정합니다.

```bash
git clone https://github.com/shieldtap/shieldtap.git
cd shieldtap
./build.sh
```

결과물은 `build/shieldtap-v<버전>.apk` 입니다. 버전은 HEAD 커밋 제목 맨 앞의 `vA.BB.CC.DD` 라벨에서 정해집니다. `VERSION_NAME` 과 `VERSION_CODE` 를 함께 주면 그 값을 씁니다. 직접 빌드한 APK 는 릴리스 APK 와 서명이 다릅니다. 직접 빌드한 것을 설치하려면 릴리스 앱을 먼저 지우세요.

## 라이선스

[Apache-2.0](LICENSE). Copyright 2026 Gyu Min Lee. Material Icons 고지는 [NOTICE](NOTICE) 에 있습니다. 배포할 때는 `LICENSE` 와 `NOTICE` 를 함께 넣으세요.

ShieldTap 은 개인이 만든 비공식 앱이며 삼성전자와 관계가 없습니다. Auto Blocker(보안 위험 자동 차단)는 삼성전자의 기능 이름입니다.
