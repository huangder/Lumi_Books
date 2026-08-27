<!-- Language: ko | Audience: dev -->

<div align="center">

🌐 [中文](README.dev.md) · [English](README.dev.en.md) · [日本語](README.dev.ja.md) · **한국어**

[📖 사용자 가이드](README.ko.md) · ⚙️ **개발자 문서**

</div>

---

# Lumi — 개발자 가이드

> Lumi Android 전자책 리더의 기술 문서. 기여자 및 개발자 대상.

[![Version](https://img.shields.io/badge/version-1.7.8-coral)](https://github.com/huangder/Lumi_Books/releases)
[![License](https://img.shields.io/badge/license-GPLv3-green)](LICENSE)
[![Android](https://img.shields.io/badge/Android-8.0%2B-blue)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.3.10-purple)](https://kotlinlang.org)

---

## 📋 기술 스택

| 분류 | 기술 | 버전 |
|------|------|------|
| 언어 | Kotlin | 2.3.10 |
| UI 프레임워크 | Jetpack Compose (BOM) | 2026.06.01 |
| 아키텍처 | MVVM + Repository | — |
| DI | Hilt | 2.58 |
| 데이터베이스 | Room | 2.8.4 |
| 설정 저장 | DataStore Preferences | 1.1.1 |
| 네비게이션 | Navigation Compose | 2.8.9 |
| 이미지 로딩 | Coil | 2.7.0 |
| 비동기 처리 | Kotlin Coroutines | 1.9.0 |
| EPUB 렌더링 | Android WebKit | 1.16.0 |
| HTML 파싱 | Jsoup | 1.22.2 |
| PDF 파싱 | pdfbox-android | 2.0.27.0 |
| 네트워킹 | OkHttp | 4.12.0 |
| Markdown | CommonMark | 0.24.0 |
| 블러 효과 | Haze | 1.1.1 |
| 리퀴드 글래스 | Backdrop | 1.0.6 |
| 백그라운드 작업 | WorkManager | 2.10.0 |
| 색상 추출 | Palette KTX | 1.0.0 |
| 빌드 시스템 | AGP | 8.13.2 |
| 어노테이션 처리 | KSP | 2.3.10 |
| 최소 SDK | Android 8.0 (API 26) | — |
| 타겟 SDK | Android 15 (API 35) | — |
| 컴파일 SDK | Android 16 (API 36) | — |
| JVM 타겟 | Java 17 | — |

---

## 🏗 아키텍처 개요

Lumi는 **MVVM + Repository** 아키텍처와 Hilt 의존성 주입을 채택하고 있습니다：

```
┌─────────────────────────────────────────────┐
│  UI 계층 (Compose 화면 + ViewModel)          │
│  ui/home/  ui/reader/  ui/settings/  ...     │
├─────────────────────────────────────────────┤
│  도메인 계층 (모델)                           │
│  domain/model/  Book, Bookmark, Note, ...    │
├─────────────────────────────────────────────┤
│  데이터 계층 (Repository + DAO + DataStore)  │
│  data/local/  data/repository/               │
│  data/sync/   WebdavSyncManager              │
├─────────────────────────────────────────────┤
│  유틸리티 계층 (파서, 헬퍼)                   │
│  util/parser/  EPUB, PDF, TXT               │
│  util/  LocaleHelper, FontManager, ...       │
└─────────────────────────────────────────────┘
```

- **Single Activity + Navigation Compose**：`MainActivity`가 주요 Compose 네비게이션 그래프를 호스팅. 설정, 환영 화면 등은 네이티브 전환 애니메이션을 위해 별도 Activity 사용.
- **Hilt**：`@HiltAndroidApp` → `@HiltViewModel` → `@Inject constructor`.
- **Room**：`BookDao`, `BookmarkDao`, `ReadingRecordDao`. DB 마이그레이션은 `fallbackToDestructiveMigration()` 사용.
- **DataStore**：사용자 설정（테마, 읽기 설정, TTS 설정 등）저장.
- **리더 엔진**：EPUB은 두 가지 모드 지원 — Canvas + StaticLayout（통일 타이포그래피）및 WebView（출판사 CSS 레이아웃 충실히 유지）.

---

## 📁 프로젝트 구조

```
android_books/
├── LICENSE                     # GPLv3 라이선스 & 서드파티 고지
├── README.md                   # 사용자용 README
├── README.dev.md               # 개발자 가이드（본 파일）
├── build.gradle.kts            # 루트 빌드 설정
├── settings.gradle.kts         # 프로젝트 설정
│
├── app/                        # Android 애플리케이션 모듈
│   ├── build.gradle.kts        # 앱 빌드 설정
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── assets/             # 내장 HTML, 글꼴, 변경 이력
│       ├── java/com/huangder/lumibooks/
│       │   ├── EBookReaderApp.kt       # Application 클래스
│       │   ├── MainActivity.kt         # 메인 Activity
│       │   ├── data/                   # 데이터 계층
│       │   │   ├── local/              # Room DAO/Entity, DataStore
│       │   │   ├── repository/         # Repository 구현
│       │   │   └── sync/              # WebdavSyncManager
│       │   ├── domain/model/           # 도메인 모델
│       │   ├── di/                     # Hilt 모듈
│       │   ├── ui/                     # 프레젠테이션 계층
│       │   │   ├── home/               # 홈 + 책장
│       │   │   ├── reader/             # 리더（코어）
│       │   │   │   └── engine/         # Canvas 레이아웃 엔진
│       │   │   ├── statistics/         # 독서 통계
│       │   │   ├── settings/           # 설정
│       │   │   ├── bookshelf/          # 책장 컴포넌트
│       │   │   ├── components/         # 공유 Compose 컴포넌트
│       │   │   ├── welcome/            # 온보딩
│       │   │   └── navigation/         # 네비게이션 그래프
│       │   └── util/                   # 유틸리티
│       │       ├── parser/             # EPUB/PDF/TXT 파서
│       │       ├── LocaleHelper.kt     # 다국어 지원
│       │       └── ...
│       └── res/                        # 리소스（8개 로케일 디렉터리）
│
├── devlog/                     # 개발 로그（날짜순）
├── devdocs/                    # 프로젝트 문서
│   ├── requirements.md         # 요구사항
│   ├── technical-spec.md       # 기술 명세
│   ├── design-spec.md          # 디자인 명세
│   ├── ui-design-spec.md       # UI 디자인 구현
│   ├── project-status.md       # 프로젝트 현황
│   ├── development-plan.md     # 개발 계획
│   └── CHANGELOG.md            # 변경 이력（v1.0.01.124）
│
├── docs/                       # 웹사이트 소스 (GitHub Pages → huangder.top)
│   ├── index.html              # 홈페이지
│   ├── features.html           # 기능 소개
│   ├── tech.html               # 기술 명세
│   ├── privacy.html            # 개인정보 처리방침
│   └── ...
│
└── .github/workflows/          # CI（GitHub Pages 배포）
```

> **참고**：`devdocs/` 및 `devlog/`의 상세 개발 문서는 주로 중국어로 작성되어 있습니다.

---

## 🔨 빌드

### 사전 요구사항
- **Android Studio**（최신 안정판 권장）
- **JDK 17**
- **Android SDK 36**（compileSdk）및 35（targetSdk）빌드 도구

### 절차

```bash
# 1. 저장소 클론
git clone https://github.com/huangder/Lumi_Books.git
cd Lumi_Books

# 2. Android Studio에서 열고 Gradle 동기화 대기

# 3. 기기 연결 또는 에뮬레이터（API 26+）실행 후 실행

# 또는 명령줄에서 빌드：
./gradlew assembleDebug
# APK 출력: app/build/outputs/apk/debug/app-debug.apk

# 릴리스 빌드（서명 설정 필요）：
./gradlew assembleRelease
# APK 출력: app/build/outputs/apk/release/
```

### 서명 설정

릴리스 빌드에는 `app/`에 `keystore.properties`를 배치하거나 `build.gradle.kts`에서 서명 정보를 구성해야 합니다. 기본 release buildType은 minify가 비활성화（`isMinifyEnabled = false`）되어 있습니다.

---

## 🤝 기여

Issue와 Pull Request를 환영합니다.

- **버그 보고**：[GitHub Issues](https://github.com/huangder/Lumi_Books/issues)에 기기 모델, Android 버전, 재현 절차를 포함하여 제출해 주세요.
- **기능 제안**：Issues에서 논의를 시작해 주세요.
- **코드 기여**：
  1. 저장소 포크
  2. 기능 브랜치 생성（`feature/xxx` 또는 `fix/xxx`）
  3. 개발 후 `./gradlew compileDebugKotlin`으로 확인
  4. `main` 브랜치로 PR 제출
- **커밋 규칙**：Conventional Commits 준수 — `feat:` / `fix:` / `refactor:` / `docs:` / `chore:` / `perf:`.
- **코드 스타일**：Kotlin 공식 코드 스타일과 기존 프로젝트 패턴을 따라 주세요.

---

## 📚 문서 색인

| 문서 | 경로 |
|------|------|
| 변경 이력（v1.0.01） | [devdocs/CHANGELOG.md](devdocs/CHANGELOG.md) |
| 온라인 변경 이력 | [huangder.top/changelog](https://huangder.top/changelog.html) |
| 요구사항 | [devdocs/requirements.md](devdocs/requirements.md) |
| 기술 명세 | [devdocs/technical-spec.md](devdocs/technical-spec.md) |
| 디자인 명세 | [devdocs/design-spec.md](devdocs/design-spec.md) |
| UI 디자인 구현 | [devdocs/ui-design-spec.md](devdocs/ui-design-spec.md) |
| 프로젝트 현황 | [devdocs/project-status.md](devdocs/project-status.md) |
| 개발 계획 | [devdocs/development-plan.md](devdocs/development-plan.md) |
| 개발 로그 | [devlog/](devlog/) |
| 앱 내 변경 이력 | [app/src/main/assets/changelog.md](app/src/main/assets/changelog.md) |

---

## 📜 라이선스

Lumi의 오리지널 코드는 [GNU GPLv3](LICENSE)에 따라 오픈소스로 공개됩니다.

서드파티 종속성 및 개작 코드는 각각의 라이선스를 따릅니다：

| 구성 요소 | 라이선스 | 비고 |
|-----------|---------|------|
| [Backdrop](https://github.com/Kyant0/AndroidLiquidGlass) | Apache 2.0 | 리퀴드 글래스 효과, 개작됨 |
| [Haze](https://github.com/chrisbanes/haze) | Apache 2.0 | 블러 효과 |
| [PDFBox Android](https://github.com/TomRoush/PdfBox-Android) | Apache 2.0 | PDF 텍스트 추출 |
| [CommonMark](https://github.com/commonmark/commonmark-java) | BSD 2-Clause | Markdown 파싱 |
| [Jsoup](https://jsoup.org/) | MIT | HTML 파싱 |
| [legado-E SimulationPageDelegate](https://github.com/Luoyacheng/legado-E) | GPLv3 | 컬 페이지 넘김 기하 및 렌더링 개작 |
| [Coil](https://coil-kt.github.io/coil/) | Apache 2.0 | 이미지 로딩 |
| [OkHttp](https://square.github.io/okhttp/) | Apache 2.0 | HTTP 클라이언트 |

전체 서드파티 라이선스 전문은 [app/src/main/assets/licenses/](app/src/main/assets/licenses/)에서 확인할 수 있습니다.

© 2026 Huangder

---

## 🔗 링크

- 🌐 웹사이트：[huangder.top](https://huangder.top)
- 📦 GitHub：[github.com/huangder/Lumi_Books](https://github.com/huangder/Lumi_Books)
- 📧 이메일：huangder0104@126.com
