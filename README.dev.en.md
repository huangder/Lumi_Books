<!-- Language: en | Audience: dev -->

<div align="center">

🌐 [中文](README.dev.md) · **English** · [日本語](README.dev.ja.md) · [한국어](README.dev.ko.md)

[📖 User Guide](README.en.md) · ⚙️ **Developer Docs**

</div>

---

# Lumi — Developer Guide

> Technical documentation for the Lumi Android ebook reader. For contributors and developers.

[![Version](https://img.shields.io/badge/version-1.7.8-coral)](https://github.com/huangder/Lumi_Books/releases)
[![License](https://img.shields.io/badge/license-MIT-green)](LICENSE)
[![Android](https://img.shields.io/badge/Android-8.0%2B-blue)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.3.10-purple)](https://kotlinlang.org)

---

## 📋 Tech Stack

| Category | Technology | Version |
|----------|-----------|---------|
| Language | Kotlin | 2.3.10 |
| UI Framework | Jetpack Compose (BOM) | 2026.06.01 |
| Architecture | MVVM + Repository | — |
| DI | Hilt | 2.58 |
| Database | Room | 2.8.4 |
| Preferences | DataStore Preferences | 1.1.1 |
| Navigation | Navigation Compose | 2.8.9 |
| Image Loading | Coil | 2.7.0 |
| Async | Kotlin Coroutines | 1.9.0 |
| EPUB Rendering | Android WebKit | 1.16.0 |
| HTML Parsing | Jsoup | 1.22.2 |
| PDF Parsing | pdfbox-android | 2.0.27.0 |
| Networking | OkHttp | 4.12.0 |
| Markdown | CommonMark | 0.24.0 |
| Blur Effects | Haze | 1.1.1 |
| Liquid Glass | Backdrop | 1.0.6 |
| Background Tasks | WorkManager | 2.10.0 |
| Color Extraction | Palette KTX | 1.0.0 |
| Build System | AGP | 8.13.2 |
| Annotation Processing | KSP | 2.3.10 |
| Min SDK | Android 8.0 (API 26) | — |
| Target SDK | Android 15 (API 35) | — |
| Compile SDK | Android 16 (API 36) | — |
| JVM Target | Java 17 | — |

---

## 🏗 Architecture Overview

Lumi follows **MVVM + Repository** architecture with Hilt dependency injection:

```
┌─────────────────────────────────────────────┐
│  UI Layer (Compose Screens + ViewModels)     │
│  ui/home/  ui/reader/  ui/settings/  ...     │
├─────────────────────────────────────────────┤
│  Domain Layer (Models)                        │
│  domain/model/  Book, Bookmark, Note, ...    │
├─────────────────────────────────────────────┤
│  Data Layer (Repository + DAO + DataStore)   │
│  data/local/  data/repository/               │
│  data/sync/   WebdavSyncManager              │
├─────────────────────────────────────────────┤
│  Util Layer (Parsers, Helpers)               │
│  util/parser/  EPUB, PDF, TXT               │
│  util/  LocaleHelper, FontManager, ...       │
└─────────────────────────────────────────────┘
```

- **Single Activity + Navigation Compose**: `MainActivity` hosts the main Compose navigation graph. Settings, welcome, and other auxiliary screens use separate Activities for native transition animations.
- **Hilt**: `@HiltAndroidApp` → `@HiltViewModel` → `@Inject constructor`.
- **Room**: `BookDao`, `BookmarkDao`, `ReadingRecordDao`. Database migrations use `fallbackToDestructiveMigration()`.
- **DataStore**: Stores user preferences (themes, reading settings, TTS config, etc.).
- **Reader Engine**: EPUB supports two modes — Canvas + StaticLayout (unified typography) and WebView (faithful publisher CSS layout).

---

## 📁 Project Structure

```
android_books/
├── LICENSE                     # MIT License & third-party notices
├── README.md                   # User-facing README
├── README.dev.md               # Developer guide (this file)
├── build.gradle.kts            # Root build config
├── settings.gradle.kts         # Project settings
│
├── app/                        # Android application module
│   ├── build.gradle.kts        # App build config
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── assets/             # Bundled HTML, fonts, changelog
│       ├── java/com/huangder/lumibooks/
│       │   ├── EBookReaderApp.kt       # Application class
│       │   ├── MainActivity.kt         # Main Activity
│       │   ├── data/                   # Data layer
│       │   │   ├── local/              # Room DAO/Entity, DataStore
│       │   │   ├── repository/         # Repository implementations
│       │   │   └── sync/              # WebdavSyncManager
│       │   ├── domain/model/           # Domain models
│       │   ├── di/                     # Hilt Modules
│       │   ├── ui/                     # Presentation layer
│       │   │   ├── home/               # Home + Bookshelf
│       │   │   ├── reader/             # Reader (core)
│       │   │   │   └── engine/         # Canvas layout engine
│       │   │   ├── statistics/         # Reading stats
│       │   │   ├── settings/           # Settings
│       │   │   ├── bookshelf/          # Bookshelf components
│       │   │   ├── components/         # Shared Compose components
│       │   │   ├── welcome/            # Onboarding
│       │   │   └── navigation/         # Nav graph
│       │   └── util/                   # Utilities
│       │       ├── parser/             # EPUB/PDF/TXT parsers
│       │       ├── LocaleHelper.kt     # Multi-language support
│       │       └── ...
│       └── res/                        # Resources (8 locale directories)
│
├── devlog/                     # Development logs (by date)
├── devdocs/                    # Project documentation
│   ├── requirements.md         # Requirements
│   ├── technical-spec.md       # Technical spec
│   ├── design-spec.md          # Design spec
│   ├── ui-design-spec.md       # UI design implementation
│   ├── project-status.md       # Project status
│   ├── development-plan.md     # Development plan
│   └── CHANGELOG.md            # Changelog (v1.0.01.124)
│
├── docs/                       # Website source (GitHub Pages → huangder.top)
│   ├── index.html              # Homepage
│   ├── features.html           # Features
│   ├── tech.html               # Tech specs
│   ├── privacy.html            # Privacy policy
│   └── ...
│
└── .github/workflows/          # CI (GitHub Pages deployment)
```

> **Note**: Detailed development documentation in `devdocs/` and `devlog/` is primarily in Chinese.

---

## 🔨 Build

### Prerequisites
- **Android Studio** (latest stable recommended)
- **JDK 17**
- **Android SDK 36** (compileSdk) and 35 (targetSdk) build tools

### Steps

```bash
# 1. Clone the repository
git clone https://github.com/huangder/Lumi_Books.git
cd Lumi_Books

# 2. Open in Android Studio, wait for Gradle sync

# 3. Connect a device or launch an emulator (API 26+), then click Run

# Or build from command line:
./gradlew assembleDebug
# APK output: app/build/outputs/apk/debug/app-debug.apk

# Release build (requires signing config):
./gradlew assembleRelease
# APK output: app/build/outputs/apk/release/
```

### Signing

Release builds require a `keystore.properties` file in `app/` or signing configuration in `build.gradle.kts`. The default release buildType has minify disabled (`isMinifyEnabled = false`).

---

## 🤝 Contributing

Issues and pull requests are welcome.

- **Bug reports**: Submit via [GitHub Issues](https://github.com/huangder/Lumi_Books/issues) with device model, Android version, and reproduction steps.
- **Feature requests**: Start a discussion in Issues.
- **Code contributions**:
  1. Fork the repository
  2. Create a feature branch (`feature/xxx` or `fix/xxx`)
  3. Develop and verify with `./gradlew compileDebugKotlin`
  4. Submit a PR to the `main` branch
- **Commit conventions**: Follow Conventional Commits — `feat:` / `fix:` / `refactor:` / `docs:` / `chore:` / `perf:`.
- **Code style**: Follow Kotlin official code style and existing project patterns.

---

## 📚 Documentation Index

| Document | Path |
|----------|------|
| Changelog (v1.0.01) | [devdocs/CHANGELOG.md](devdocs/CHANGELOG.md) |
| Online Changelog | [huangder.top/changelog](https://huangder.top/changelog.html) |
| Requirements | [devdocs/requirements.md](devdocs/requirements.md) |
| Technical Spec | [devdocs/technical-spec.md](devdocs/technical-spec.md) |
| Design Spec | [devdocs/design-spec.md](devdocs/design-spec.md) |
| UI Design Implementation | [devdocs/ui-design-spec.md](devdocs/ui-design-spec.md) |
| Project Status | [devdocs/project-status.md](devdocs/project-status.md) |
| Development Plan | [devdocs/development-plan.md](devdocs/development-plan.md) |
| Development Logs | [devlog/](devlog/) |
| In-app Changelog | [app/src/main/assets/changelog.md](app/src/main/assets/changelog.md) |

---

## 📜 License

Lumi's original code is open-sourced under the [MIT License](LICENSE).

Third-party dependencies and adaptations follow their respective licenses:

| Component | License | Notes |
|-----------|---------|-------|
| [Backdrop](https://github.com/Kyant0/AndroidLiquidGlass) | Apache 2.0 | Liquid glass effects, adapted |
| [Haze](https://github.com/chrisbanes/haze) | Apache 2.0 | Blur effects |
| [PDFBox Android](https://github.com/TomRoush/PdfBox-Android) | Apache 2.0 | PDF text extraction |
| [CommonMark](https://github.com/commonmark/commonmark-java) | BSD 2-Clause | Markdown parsing |
| [Jsoup](https://jsoup.org/) | MIT | HTML parsing |
| [Coil](https://coil-kt.github.io/coil/) | Apache 2.0 | Image loading |
| [OkHttp](https://square.github.io/okhttp/) | Apache 2.0 | HTTP client |

Full third-party license texts are in [app/src/main/assets/licenses/](app/src/main/assets/licenses/).

© 2026 Huangder

---

## 🔗 Links

- 🌐 Website: [huangder.top](https://huangder.top)
- 📦 GitHub: [github.com/huangder/Lumi_Books](https://github.com/huangder/Lumi_Books)
- 📧 Email: huangder0104@126.com
