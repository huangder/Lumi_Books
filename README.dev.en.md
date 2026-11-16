<!-- Language: en | Audience: dev -->

<div align="center">

<img src="docs/readme-icon.png" alt="Lumi" width="96">

# Lumi Developer Guide

Technical notes on the Android local e-book reader, for contributors and developers building on it.

[![Version](https://img.shields.io/badge/version-2.3.11-coral)](https://github.com/huangder/Lumi_Books/releases)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue)](LICENSE)
[![Android](https://img.shields.io/badge/Android-8.0%2B-blue)](https://developer.android.com)

[中文](README.dev.md) · **English** · [日本語](README.dev.ja.md) · [한국어](README.dev.ko.md)

[User Guide](README.en.md) · **Developer Guide**

</div>

## Tech stack

| Category | Technology | Version |
|------|------|------|
| Language | Kotlin | 2.3.10 |
| Build | Android Gradle Plugin | 8.13.2 |
| UI | Jetpack Compose (BOM) | 2026.06.01 |
| Dependency injection | Hilt | 2.58 |
| Database | Room | 2.8.4 |
| Preferences | DataStore Preferences | 1.1.1 |
| Navigation | Navigation Compose | 2.8.9 |
| Coroutines | kotlinx-coroutines-android | 1.9.0 |
| Background work | WorkManager | 2.10.0 |
| EPUB publisher-layout rendering | AndroidX WebKit | 1.16.0 |
| HTML parsing | Jsoup | 1.22.2 |
| PDF parsing | pdfbox-android | 2.0.27.0 |
| Markdown | CommonMark | 0.24.0 |
| Networking | OkHttp | 4.12.0 |
| Image loading | Coil | 2.7.0 |
| Blur and glass effects | Haze 1.1.1, Backdrop (`third_party/backdrop`) | — |
| SDK | minSdk 26, compileSdk 37, targetSdk 37 | — |
| JVM | Java 17 | — |

## Architecture

The app uses an MVVM + Repository architecture, with Hilt for dependency injection.

```
ui/        Compose screens and ViewModels: home, reader, settings, statistics, bookshelf, etc.
domain/    Domain models and repository interfaces
data/      Room, DataStore, repository implementations, remote data sources such as WebDAV / OPDS, backup and sync
util/      Parsers (EPUB, PDF, TXT, Markdown, Word, Kindle), layout and cache utilities
tts/       System speech and external speech interfaces
widget/    Home screen widgets
edition/   Feature interfaces between the open-source and official editions
```

- **UI**: `MainActivity` hosts the main screens through Navigation Compose (`ui/navigation/NavGraph.kt`). The welcome page, full-screen listening and listening settings are separate Activities.
- **Dependency injection**: `EBookReaderApp` is annotated with `@HiltAndroidApp`, ViewModels use `@HiltViewModel`, and the modules are in `di/`.
- **Database**: The current Room version is 28. All migrations are registered with `addMigrations` in `di/AppModule.kt`. When you change a table schema, increase the version number and add a migration. Do not rely on destructive migration.
- **Preferences and secrets**: User settings are stored in DataStore. Credentials such as the WebDAV, translation, speech and MinerU Tokens are encrypted with Android KeyStore.
- **Reader**: EPUB publisher layout uses a WebView and keeps the publisher's CSS and fonts. Reader layout uses Canvas + StaticLayout with unified fonts and typesetting.

## Project structure

```
android_books/
├── app/
│   ├── build.gradle.kts
│   └── src/
│       ├── main/            Shared code and resources
│       │   ├── java/com/huangder/lumibooks/
│       │   ├── assets/      Bundled HTML, fonts, dictionaries, changelog
│       │   └── res/         Resources (7 UI languages)
│       ├── oss/             Open-source edition feature implementations
│       ├── premium/         Private source for the official edition (ignored by Git, separate repository, see below)
│       ├── test/            Local unit tests
│       ├── androidTest/     Instrumented tests
│       └── debug/           Debug entry points
│
├── benchmark/               Startup and scrolling performance tests
├── third_party/             Third-party modules maintained in this repository (Backdrop, subtitle view, Word conversion, etc.)
├── tools/                   Probes, test scripts and helper tools for EPUB and the reader
├── devdocs/                 Development documents
├── docs/                    Project website (GitHub Pages)
└── .github/                 Pages deployment workflow and issue templates
```

## Build

### Environment

- Android Studio (latest stable version), or the command line
- JDK 17
- Android SDK: compileSdk 37 and the matching build-tools
- Set `ANDROID_HOME` for command-line builds. On Windows, use `gradlew.bat`

### Open-source edition

If you clone only this repository, the build output is the open-source edition:

```bash
./gradlew :app:assembleDebug
# APK: app/build/oss/outputs/apk/debug/
```

Common check commands:

```bash
./gradlew :app:compileDebugKotlin                 # Kotlin compilation
./gradlew :app:testDebugUnitTest                  # Local unit tests
./gradlew :app:compileDebugAndroidTestKotlin      # Compile check for instrumented tests
```

### Official edition and edition boundary

The open-source and official editions share one codebase. The differences are isolated by the interfaces in `edition/`: this repository provides implementations that compile on their own, and the private implementations of the official edition live in `app/src/premium/`. This directory is ignored by the public repository. Developers with access to the private repository can clone it when needed:

```bash
git clone --branch main https://github.com/huangder/Lumi_Books-premium.git app/src/premium
```

When `app/src/premium/java` exists, the default build switches to the official edition, and the output is in `app/build/official/`. To verify the open-source edition in this setup, add `-Plumi.oss=true`:

```bash
./gradlew '-Plumi.oss=true' :app:assembleDebug
```

The two editions use different build directories, so code generated by KSP and Hilt is not mixed. When you change a shared interface, a data format or the build configuration, check that both the open-source and official editions compile. For more detailed rules, see [devdocs/editions.md](devdocs/editions.md).

### Signing and release

- Release builds enable R8 shrinking and resource shrinking (`isMinifyEnabled = true`).
- The repository does not include a signing configuration. Before releasing, configure your own `signingConfig` for release in `app/build.gradle.kts`.
- For special build types such as startup diagnostics, see `buildTypes` in `app/build.gradle.kts`.

## Contributing

Issues and pull requests are welcome.

- **Bug reports**: Submit them through [GitHub Issues](https://github.com/huangder/Lumi_Books/issues). Include the device model, Android version and steps to reproduce.
- **Feature requests**: Start a discussion in Issues.
- **Code contributions**:
  1. Fork the repository
  2. Create a feature branch (`feature/xxx` or `fix/xxx`)
  3. Develop, and confirm that `./gradlew :app:compileDebugKotlin` passes
  4. Submit a PR to the `main` branch
- **Commit convention**: Follow Conventional Commits: `feat:`, `fix:`, `refactor:`, `docs:`, `chore:`, `perf:`.
- **Code style**: Follow the official Kotlin style and the existing patterns in the project.

The detailed documents in `devdocs/` are currently mostly in Chinese. Translations into other languages are welcome.

## Documentation index

| Document | Path |
|------|------|
| In-app changelog | [app/src/main/assets/changelog.md](app/src/main/assets/changelog.md) |
| Online changelog | [huangder.top/changelog](https://huangder.top/changelog.html) |
| Editions and build boundary | [devdocs/editions.md](devdocs/editions.md) |
| Requirements | [devdocs/requirements.md](devdocs/requirements.md) |
| Technical specification | [devdocs/technical-spec.md](devdocs/technical-spec.md) |
| Design specification | [devdocs/design-spec.md](devdocs/design-spec.md) |
| Current UI and motion specification | [devdocs/lumi-ui-motion-design-system.md](devdocs/lumi-ui-motion-design-system.md) |
| Project status | [devdocs/project-status.md](devdocs/project-status.md) |
| Development plan | [devdocs/development-plan.md](devdocs/development-plan.md) |

## License

Lumi's original code is open-sourced under the [Apache License 2.0](LICENSE).

Third-party dependencies and adapted code continue to follow their own licenses:

| Component | License | Notes |
|------|--------|------|
| [Backdrop](https://github.com/Kyant0/AndroidLiquidGlass) | Apache 2.0 | Liquid Glass effect, adapted |
| [Haze](https://github.com/chrisbanes/haze) | Apache 2.0 | Frosted glass effect |
| [PDFBox Android](https://github.com/TomRoush/PdfBox-Android) | Apache 2.0 | PDF text extraction |
| [CommonMark](https://github.com/commonmark/commonmark-java) | BSD 2-Clause | Markdown parsing |
| [Jsoup](https://jsoup.org/) | MIT | HTML parsing |
| [Coil](https://coil-kt.github.io/coil/) | Apache 2.0 | Image loading |
| [OkHttp](https://square.github.io/okhttp/) | Apache 2.0 | HTTP client |

For the full third-party notices, see [NOTICE](NOTICE). The license texts are in [app/src/main/assets/licenses/](app/src/main/assets/licenses/).

© 2026 Huangder

## Links

- Website: [huangder.top](https://huangder.top)
- GitHub: [github.com/huangder/Lumi_Books](https://github.com/huangder/Lumi_Books)
- Email: huangder0104@126.com
