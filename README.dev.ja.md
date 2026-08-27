<!-- Language: ja | Audience: dev -->

<div align="center">

🌐 [中文](README.dev.md) · [English](README.dev.en.md) · **日本語** · [한국어](README.dev.ko.md)

[📖 ユーザーガイド](README.ja.md) · ⚙️ **開発者ドキュメント**

</div>

---

# Lumi — 開発者ガイド

> Lumi Android 電子書籍リーダーの技術ドキュメント。コントリビューターおよび開発者向け。

[![Version](https://img.shields.io/badge/version-1.7.8-coral)](https://github.com/huangder/Lumi_Books/releases)
[![License](https://img.shields.io/badge/license-GPLv3-green)](LICENSE)
[![Android](https://img.shields.io/badge/Android-8.0%2B-blue)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.3.10-purple)](https://kotlinlang.org)

---

## 📋 技術スタック

| カテゴリ | 技術 | バージョン |
|----------|------|-----------|
| 言語 | Kotlin | 2.3.10 |
| UI フレームワーク | Jetpack Compose (BOM) | 2026.06.01 |
| アーキテクチャ | MVVM + Repository | — |
| DI | Hilt | 2.58 |
| データベース | Room | 2.8.4 |
| 設定保存 | DataStore Preferences | 1.1.1 |
| ナビゲーション | Navigation Compose | 2.8.9 |
| 画像読み込み | Coil | 2.7.0 |
| 非同期処理 | Kotlin Coroutines | 1.9.0 |
| EPUB レンダリング | Android WebKit | 1.16.0 |
| HTML 解析 | Jsoup | 1.22.2 |
| PDF 解析 | pdfbox-android | 2.0.27.0 |
| ネットワーク | OkHttp | 4.12.0 |
| Markdown | CommonMark | 0.24.0 |
| ブラー効果 | Haze | 1.1.1 |
| リキッドグラス | Backdrop | 1.0.6 |
| バックグラウンドタスク | WorkManager | 2.10.0 |
| 色抽出 | Palette KTX | 1.0.0 |
| ビルドシステム | AGP | 8.13.2 |
| アノテーション処理 | KSP | 2.3.10 |
| 最小 SDK | Android 8.0 (API 26) | — |
| ターゲット SDK | Android 15 (API 35) | — |
| コンパイル SDK | Android 16 (API 36) | — |
| JVM ターゲット | Java 17 | — |

---

## 🏗 アーキテクチャ概要

Lumi は **MVVM + Repository** アーキテクチャを採用し、Hilt による依存性注入を行っています：

```
┌─────────────────────────────────────────────┐
│  UI 層 (Compose 画面 + ViewModel)            │
│  ui/home/  ui/reader/  ui/settings/  ...     │
├─────────────────────────────────────────────┤
│  ドメイン層 (モデル)                          │
│  domain/model/  Book, Bookmark, Note, ...    │
├─────────────────────────────────────────────┤
│  データ層 (Repository + DAO + DataStore)     │
│  data/local/  data/repository/               │
│  data/sync/   WebdavSyncManager              │
├─────────────────────────────────────────────┤
│  ユーティリティ層 (パーサー, ヘルパー)         │
│  util/parser/  EPUB, PDF, TXT               │
│  util/  LocaleHelper, FontManager, ...       │
└─────────────────────────────────────────────┘
```

- **Single Activity + Navigation Compose**：`MainActivity` がメインの Compose ナビゲーショングラフをホスト。設定やウェルカム画面はネイティブ遷移アニメーション用に独立した Activity を使用。
- **Hilt**：`@HiltAndroidApp` → `@HiltViewModel` → `@Inject constructor`。
- **Room**：`BookDao`、`BookmarkDao`、`ReadingRecordDao`。DB マイグレーションは `fallbackToDestructiveMigration()` を使用。
- **DataStore**：ユーザー設定（テーマ、読書設定、TTS 設定など）を保存。
- **リーダーエンジン**：EPUB は2つのモードに対応 — Canvas + StaticLayout（統一タイポグラフィ）と WebView（出版社 CSS レイアウトを忠実に保持）。

---

## 📁 プロジェクト構造

```
android_books/
├── LICENSE                     # GPLv3 ライセンス & サードパーティ通知
├── README.md                   # ユーザー向け README
├── README.dev.md               # 開発者ガイド（本ファイル）
├── build.gradle.kts            # ルートビルド設定
├── settings.gradle.kts         # プロジェクト設定
│
├── app/                        # Android アプリケーションモジュール
│   ├── build.gradle.kts        # アプリビルド設定
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── assets/             # 内蔵 HTML、フォント、変更履歴
│       ├── java/com/huangder/lumibooks/
│       │   ├── EBookReaderApp.kt       # Application クラス
│       │   ├── MainActivity.kt         # メイン Activity
│       │   ├── data/                   # データ層
│       │   │   ├── local/              # Room DAO/Entity、DataStore
│       │   │   ├── repository/         # Repository 実装
│       │   │   └── sync/              # WebdavSyncManager
│       │   ├── domain/model/           # ドメインモデル
│       │   ├── di/                     # Hilt モジュール
│       │   ├── ui/                     # プレゼンテーション層
│       │   │   ├── home/               # ホーム + 本棚
│       │   │   ├── reader/             # リーダー（コア）
│       │   │   │   └── engine/         # Canvas レイアウトエンジン
│       │   │   ├── statistics/         # 読書統計
│       │   │   ├── settings/           # 設定
│       │   │   ├── bookshelf/          # 本棚コンポーネント
│       │   │   ├── components/         # 共有 Compose コンポーネント
│       │   │   ├── welcome/            # オンボーディング
│       │   │   └── navigation/         # ナビゲーショングラフ
│       │   └── util/                   # ユーティリティ
│       │       ├── parser/             # EPUB/PDF/TXT パーサー
│       │       ├── LocaleHelper.kt     # 多言語サポート
│       │       └── ...
│       └── res/                        # リソース（8ロケールディレクトリ）
│
├── devlog/                     # 開発ログ（日付順）
├── devdocs/                    # プロジェクトドキュメント
│   ├── requirements.md         # 要件定義
│   ├── technical-spec.md       # 技術仕様
│   ├── design-spec.md          # デザイン仕様
│   ├── ui-design-spec.md       # UI デザイン実装
│   ├── project-status.md       # プロジェクト状況
│   ├── development-plan.md     # 開発計画
│   └── CHANGELOG.md            # 変更履歴（v1.0.01.124）
│
├── docs/                       # ウェブサイトソース (GitHub Pages → huangder.top)
│   ├── index.html              # ホームページ
│   ├── features.html           # 機能紹介
│   ├── tech.html               # 技術仕様
│   ├── privacy.html            # プライバシーポリシー
│   └── ...
│
└── .github/workflows/          # CI（GitHub Pages デプロイ）
```

> **注意**：`devdocs/` および `devlog/` 内の詳細な開発ドキュメントは主に中国語です。

---

## 🔨 ビルド

### 前提条件
- **Android Studio**（最新安定版推奨）
- **JDK 17**
- **Android SDK 36**（compileSdk）および 35（targetSdk）ビルドツール

### 手順

```bash
# 1. リポジトリをクローン
git clone https://github.com/huangder/Lumi_Books.git
cd Lumi_Books

# 2. Android Studio で開き、Gradle 同期を待つ

# 3. デバイスを接続またはエミュレータ（API 26+）を起動し、実行

# またはコマンドラインでビルド：
./gradlew assembleDebug
# APK 出力先: app/build/outputs/apk/debug/app-debug.apk

# リリースビルド（署名設定が必要）：
./gradlew assembleRelease
# APK 出力先: app/build/outputs/apk/release/
```

### 署名設定

リリースビルドには `app/` に `keystore.properties` を配置するか、`build.gradle.kts` で署名情報を設定する必要があります。デフォルトの release buildType では minify が無効（`isMinifyEnabled = false`）です。

---

## 🤝 コントリビューション

Issue と Pull Request を歓迎します。

- **バグ報告**：[GitHub Issues](https://github.com/huangder/Lumi_Books/issues) にデバイスモデル、Android バージョン、再現手順を添えて提出してください。
- **機能提案**：Issues でディスカッションを開始してください。
- **コード貢献**：
  1. リポジトリをフォーク
  2. 機能ブランチを作成（`feature/xxx` または `fix/xxx`）
  3. 開発し `./gradlew compileDebugKotlin` で確認
  4. `main` ブランチに PR を提出
- **コミット規約**：Conventional Commits に従う — `feat:` / `fix:` / `refactor:` / `docs:` / `chore:` / `perf:`。
- **コードスタイル**：Kotlin 公式コードスタイルと既存のプロジェクトパターンに従ってください。

---

## 📚 ドキュメント索引

| ドキュメント | パス |
|-------------|------|
| 変更履歴（v1.0.01） | [devdocs/CHANGELOG.md](devdocs/CHANGELOG.md) |
| オンライン変更履歴 | [huangder.top/changelog](https://huangder.top/changelog.html) |
| 要件定義 | [devdocs/requirements.md](devdocs/requirements.md) |
| 技術仕様 | [devdocs/technical-spec.md](devdocs/technical-spec.md) |
| デザイン仕様 | [devdocs/design-spec.md](devdocs/design-spec.md) |
| UI デザイン実装 | [devdocs/ui-design-spec.md](devdocs/ui-design-spec.md) |
| プロジェクト状況 | [devdocs/project-status.md](devdocs/project-status.md) |
| 開発計画 | [devdocs/development-plan.md](devdocs/development-plan.md) |
| 開発ログ | [devlog/](devlog/) |
| アプリ内変更履歴 | [app/src/main/assets/changelog.md](app/src/main/assets/changelog.md) |

---

## 📜 ライセンス

Lumi のオリジナルコードは [GNU GPLv3](LICENSE) の下でオープンソース公開されています。

サードパーティ依存ライブラリと改変コードはそれぞれのライセンスに従います：

| コンポーネント | ライセンス | 備考 |
|---------------|-----------|------|
| [Backdrop](https://github.com/Kyant0/AndroidLiquidGlass) | Apache 2.0 | リキッドグラス効果、改変あり |
| [Haze](https://github.com/chrisbanes/haze) | Apache 2.0 | ブラー効果 |
| [PDFBox Android](https://github.com/TomRoush/PdfBox-Android) | Apache 2.0 | PDF テキスト抽出 |
| [CommonMark](https://github.com/commonmark/commonmark-java) | BSD 2-Clause | Markdown 解析 |
| [Jsoup](https://jsoup.org/) | MIT | HTML 解析 |
| [legado-E SimulationPageDelegate](https://github.com/Luoyacheng/legado-E) | GPLv3 | カールページめくりの幾何・描画改変 |
| [Coil](https://coil-kt.github.io/coil/) | Apache 2.0 | 画像読み込み |
| [OkHttp](https://square.github.io/okhttp/) | Apache 2.0 | HTTP クライアント |

完全なサードパーティライセンス文書は [app/src/main/assets/licenses/](app/src/main/assets/licenses/) をご覧ください。

© 2026 Huangder

---

## 🔗 リンク

- 🌐 ウェブサイト：[huangder.top](https://huangder.top)
- 📦 GitHub：[github.com/huangder/Lumi_Books](https://github.com/huangder/Lumi_Books)
- 📧 メール：huangder0104@126.com
