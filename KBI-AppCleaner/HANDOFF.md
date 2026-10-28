# 우리아이 앱청소 — 인계 문서 (HANDOFF)

최종 수정: 2026-10-28 · v1.0.0 (versionCode 1) · 만든 곳: BI MEDIA (빛마루) · mykbi@daum.net

## 무엇을 하는 앱인가
아이에게 폰을 빌려준 뒤 깔린 게임·광고 앱 등을 **체크해서 한 번에 삭제**하는 부모용 안드로이드 앱.

## 화면 구성
- 상단 큰 버튼 **📌 폰 빌려주기 전에 눌러두세요** → 지금 앱 목록을 기억(스냅샷)
- 탭: **새로 깔린 앱**(첫 화면) · 게임 · 출처 불명 · 전체
  - 새로 깔린 앱 기간: 기억 이후 / 오늘 / 3일 / 7일 / 30일
- 앱마다 배지: 최근 설치 · 게임/게임 같음 · 설치 출처(플레이스토어/갤럭시스토어/출처 불명 등) · 내 앱🔒
- 꾹 누르기 → "내 앱" 보호(체크 안 됨)
- 아래 빨간 버튼 → 확인 창 → 안드로이드 삭제 창이 앱마다 차례로 뜸(사용자가 '확인'만 누름) → 결과 요약

## 기술 메모
- Kotlin, View 방식(XML), Material 3, minSdk 26 / target·compile 36 (2026 플레이 신규 앱 요구)
- 패키지 ID: `com.kbi.appcleaner` (플레이 등록 후엔 변경 불가)
- 앱 목록: `QUERY_ALL_PACKAGES` 안 씀 → `<queries>` 런처 인텐트로 아이콘 있는 앱만 조회 (심사 쉬움)
- 삭제: `REQUEST_DELETE_PACKAGES` + `Intent.ACTION_DELETE` 순차 실행, 돌아오면 설치 여부로 성공/건너뜀 판정
- 게임 판정: `ApplicationInfo.category == CATEGORY_GAME`(확정) / 제작사·패키지명 추정("게임 같음")
- 출처 판정: `getInstallSourceInfo().installingPackageName`
- 저장: SharedPreferences(내 앱 목록, 스냅샷) — 외부 전송 없음, 인터넷 권한 없음

## 파일 구조
```
app/src/main/java/com/kbi/appcleaner/
  MainActivity.kt   화면·탭·일괄삭제 흐름
  AppScanner.kt     앱 목록 읽기, 게임/출처 판정
  AppAdapter.kt     목록 한 줄 표시
  Prefs.kt          내 앱·스냅샷 저장
app/src/main/res/   화면(layout), 글자(values/strings.xml), 색, 아이콘
.github/workflows/build.yml   GitHub에서 자동 빌드
docs/index.html     개인정보처리방침 (GitHub Pages로 공개)
store/              플레이스토어 아이콘 512, 그래픽 이미지, 등록 문구
```

## 빌드 방법
GitHub에 올리면 Actions가 자동으로 빌드 → Actions 탭 → 최근 실행 → 아래 Artifacts에서 받기
- `1-테스트용-APK` : 항상 만들어짐, 폰에 바로 설치해 시험
- `2-플레이스토어-업로드용-AAB`, `3-정식-APK` : Secrets에 서명 키 4개를 넣어야 만들어짐

## 업데이트할 때
1. `app/build.gradle.kts`의 `versionCode`를 1씩 올리고 `versionName`도 바꾸기
2. 서명 키는 **반드시 같은 키** 사용 (서명키 zip 보관)

## 다음 할 일 후보
- 영어 추가 (values-en/strings.xml) — 해외 출시 시
- 앱별 "마지막 사용일" 표시(사용 기록 접근 권한 필요)
- 위젯/알림: "새 앱이 깔렸어요"
