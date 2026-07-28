<!-- Language: ja | Audience: user -->

<div align="center">

🌐 [中文](README.md) · [English](README.en.md) · **日本語** · [한국어](README.ko.md)

📖 **ユーザーガイド** · [⚙️ 開発者ドキュメント](README.dev.ja.md)

</div>

---

# Lumi — 読書のために生まれた

> シンプルでエレガントな Android ローカル電子書籍リーダー。EPUB、PDF、TXT 対応。プライバシー最優先、完全オフライン。

[![Version](https://img.shields.io/badge/version-1.6.00-coral)](https://github.com/huangder/Lumi_Books/releases)
[![License](https://img.shields.io/badge/license-MIT-green)](LICENSE)
[![Android](https://img.shields.io/badge/Android-8.0%2B-blue)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.3.10-purple)](https://kotlinlang.org)
[![Sponsor](https://img.shields.io/badge/%E2%9D%A4%EF%B8%8F-%E3%82%B9%E3%83%9D%E3%83%B3%E3%82%B5%E3%83%BC-ff385c)](https://huangder.top/sponsor.html)

<p align="center">
  <img src="docs/hero.png" alt="Lumi 読書画面" width="80%">
</p>

---

## ✨ 機能

### 📖 読書体験
- EPUB、PDF、TXT の3形式に対応
- **EPUB デュアルレンダリング**：ブックレイアウト（出版社の HTML/CSS/フォントを保持）+ リーダーレイアウト（統一タイポグラフィ）
- Canvas + StaticLayout 独自レンダリングエンジン、スムーズなページめくりアニメーション
- **4種類のページめくり効果**：スライド、スクロール、フェード、カール（リアルなめくり表現）
- **Bionic Reading（バイオニックリーディング）**：単語の前半を太字にして読書速度と集中力を向上
- 複数の読書テーマ（昼 / 夜 / セピア / グリーン + カスタムカラー）
- 詳細なタイポグラフィ設定（フォントサイズ、行間、文字間隔、段落間隔、四辺独立マージン、字下げ）
- カスタム文字色と背景色（HSL カラーピッカー + ギャラリー画像）
- **カスタムフォント**：内蔵 LXGW WenKai、Fangsong、Kaiti + 独自フォント追加（.ttf）
- スマートジェスチャー（左右タップでページ送り、中央でメニュー、ドラッグでめくり）
- 音量ボタンでのページめくり（方向設定可能）
- コーナー情報表示のカスタマイズ（章 / 進捗 / ページ番号 / バッテリー）
- **中国語簡繁変換**：書籍ごとに原文 / 簡体字 / 繁体字を切替
- スクリーンタイムアウト上書き（読書中は画面オンのまま）

### 🎧 TTS（読み上げ）& オーディオブック
- システム TTS エンジン連携
- **サードパーティ TTS API**：OpenAI 互換 TTS、Xiaomi MiMo Chat TTS
- ストリーミング音声キャッシュ（自動クリーンアップ制限設定可）
- フォアグラウンドサービス + 通知コントロール（再生 / 停止 / 前の文 / 次の文）

### ✨ テキスト選択と注釈
- Android ネイティブテキスト選択 + カスタム Compose フローティングメニュー
- 6色ハイライト（タップで表示と色変更）
- ハイライトテキストに関連付けたノート
- 全文検索（点滅ハイライト表示）
- しおり：追加 / 削除 / クイックジャンプ
- **注釈エクスポート**（しおり + ハイライト + ノート）

### 📚 本棚管理
- ローカルファイルのインポート（システムファイル選択 + 書籍フォルダ認証、コピー不要）
- 自動抽出書籍カバー（Coil 画像読み込み）
- **全文検索**：ライブラリ全体を横断検索
- **カスタムタグ**：書籍の分類に
- カスタムカバー（ギャラリーから選択）
- 書籍メタデータの編集（タイトル、著者）
- お気に入り / 削除管理
- 長押しコンテキストメニュー（ガラスモーフィズム表示）

### 📊 読書統計
- 今日の読書時間 + 週間トレンド
- カスタマイズ可能な毎日の読書目標
- 週 / 月 / 年 タブナビゲーション
- 月間ヒートマップ（カレンダーグリッド、読書時間で色分け）
- 年間ヒートマップ（GitHub コントリビューショングラフ風、53×7 Canvas 描画）
- 週間棒グラフ
- 連続読書記録

### ☁️ クラウド同期とバックアップ
- **WebDAV クラウド同期**：書籍、読書進捗、ノート、しおりを自分の WebDAV サーバーに手動または自動同期
- 接続テスト + 最終同期時刻表示
- **ローカルバックアップ＆復元**（ZIP エクスポート/インポート：データベース + DataStore + カバー + 書籍）
- ストレージ使用量の可視化

### 🎨 テーマと外観
- **3つのアプリテーマ**：Lumi Pink（デフォルト）、Material 3（ダイナミックカラー）、Liquid Glass（リキッドグラス）
- Liquid Glass：透明度調整 + HDR タッチハイライト
- ダークモード（システム連動 / ライト / ダーク）
- Motion Pro アニメーション（ベータ）
- オプションのスプラッシュスクリーン
- 予測型バックジェスチャー対応
- UI 要素のトランジションアニメーション
- **多言語対応**：7ロケール（zh-CN、zh-TW、zh-HK、zh-MO、English、日本語、한국어）

### ⚙️ スマート PDF 処理
- **ローカル抽出**：デバイス上で PDF テキストレイヤーを抽出（スキャン PDF 非対応）
- **MinerU クラウド解析**（サードパーティサービス）：
  - ライトモード：無料、トークン不要、10MB / 20ページまで
  - 高精度モード：個人トークン必要、200MB / 200ページまで、VLM 駆動
  - 手動モード：MinerU ウェブサイトで処理後、ZIP/Markdown をインポート
- PDF をリフロー型電子書籍に変換し、統一された読書体験を実現

### 🔒 プライバシー保護
- **ローカル優先** — アップデート確認時のみ GitHub サーバーに接続、個人情報は送信しません
- **サードパーティ SDK ゼロ** — 分析、広告、プッシュ通知、クラッシュレポートなし
- **Android サンドボックスストレージ** — 全データはアプリ専用ディレクトリに保存
- **オープンソース、監査可能** — 全ソースコードを公開、誰でも確認可能
- 初回起動時にプライバシーポリシーと利用規約を表示し、透明性を確保
- 機密情報（WebDAV、TTS API キー）は Android KeyStore で暗号化

---

## 📥 入手方法

[GitHub Releases](https://github.com/huangder/Lumi_Books/releases) から最新 APK をダウンロードしてください。

---

## 📄 変更履歴

[変更履歴](https://huangder.top/changelog.html) をご覧ください。

---

## ⭐ スター履歴

<p align="center">
  <a href="https://star-history.com/#huangder/Lumi_Books&Date">
    <img src="https://api.star-history.com/svg?repos=huangder/Lumi_Books&type=Date" alt="Star History Chart" width="600">
  </a>
</p>

---

## 📜 ライセンス

Lumi のオリジナルコードは [MIT License](LICENSE) の下でオープンソース公開されています。サードパーティ依存ライブラリと改変コードはそれぞれのライセンスに従います。Liquid Glass の実装は Apache 2.0 ライセンスの [AndroidLiquidGlass / Backdrop](https://github.com/Kyant0/AndroidLiquidGlass) を改変して使用しています。© 2026 Huangder

---

## 🔗 リンク

- 🌐 ウェブサイト：[huangder.top](https://huangder.top)
- 📦 GitHub：[github.com/huangder/Lumi_Books](https://github.com/huangder/Lumi_Books)
- 📧 メール：huangder0104@126.com

---

<p align="center">
  <sub>良い本には、良いリーダーを。</sub>
</p>
