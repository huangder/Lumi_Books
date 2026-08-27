<!-- Language: en | Audience: user -->

<div align="center">

🌐 [中文](README.md) · **English** · [日本語](README.ja.md) · [한국어](README.ko.md)

📖 **User Guide** · [⚙️ Developer Docs](README.dev.en.md)

</div>

---

# Lumi — Born for Reading

> A clean, elegant Android local ebook reader. Supports EPUB, PDF, TXT. Privacy-first, local-first.

[![Version](https://img.shields.io/badge/version-1.7.8-coral)](https://github.com/huangder/Lumi_Books/releases)
[![License](https://img.shields.io/badge/license-MIT-green)](LICENSE)
[![Android](https://img.shields.io/badge/Android-8.0%2B-blue)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.3.10-purple)](https://kotlinlang.org)
[![Sponsor](https://img.shields.io/badge/%E2%9D%A4%EF%B8%8F-Sponsor-ff385c)](https://huangder.top/sponsor.html)

<p align="center">
  <img src="docs/hero.png" alt="Lumi Reader Screenshot" width="80%">
</p>

---

## ✨ Features

### 📖 Reading Experience
- Supports EPUB, PDF, and TXT formats
- **EPUB dual rendering modes**: Book Layout (preserves publisher HTML/CSS/fonts) + Reader Layout (unified typography)
- Custom Canvas + StaticLayout rendering engine with smooth page-turn animations
- **Four page-turn effects**: Slide, Scroll, Fade, Curl (realistic page-curl simulation)
- **Bionic Reading**: Bold word prefixes to improve reading speed and focus
- Multiple reading themes (Day / Night / Sepia / Green + custom colors)
- Fine typography controls: font size, line height, letter spacing, paragraph spacing, independent four-side margins, first-line indent
- Custom text colors & background (HSL color picker + photo from gallery)
- **Custom fonts**: Built-in LXGW WenKai, Fangsong, Kaiti + import your own (.ttf)
- Smart gesture system (tap left/right to turn pages, center for menu, drag-to-flip)
- Volume key page turning (configurable direction)
- Configurable corner info display (chapter / progress / page number / battery)
- **Chinese conversion**: Per-book switch between Original / Simplified / Traditional characters
- Screen sleep timeout override (keep screen on while reading)

### 🎧 TTS & Audiobook
- System TTS engine integration
- **Third-party TTS API support**: OpenAI-compatible TTS, Xiaomi MiMo Chat TTS
- Streaming audio cache with configurable auto-cleanup limits
- Foreground service with notification controls (play / pause / prev / next sentence)

### ✨ Text Selection & Annotations
- Native Android text selection + custom Compose floating menu
- 6-color highlights with tap-to-view and color editing
- Notes linked to highlighted text
- Full-text search with blinking highlight indicators
- Bookmarks: add / delete / quick jump
- **Annotation export** (bookmarks + highlights + notes)

### 📚 Bookshelf Management
- Import local files (system file picker + book folder authorization, no copying needed)
- Auto-extracted book covers (Coil image loading)
- **Full-text search** across your entire library
- **Custom tags** for organizing books
- Custom covers (pick from gallery)
- Edit book metadata (title, author)
- Favorites & delete management
- Long-press context menu with glassmorphism overlay

### 📊 Reading Statistics
- Today's reading time + weekly trends
- Customizable daily reading goal
- Week / Month / Year tab navigation
- Monthly heatmap (calendar grid, colored by reading time)
- Yearly heatmap (GitHub contribution graph style, 53×7 Canvas rendering)
- Weekly bar chart
- Reading streak tracking

### ☁️ Cloud Sync & Backup
- **WebDAV cloud sync**: Manual or automatic sync of books, reading progress, notes, and bookmarks to your own WebDAV server
- Connection test + last sync timestamp
- **Local backup & restore** (ZIP export/import: database + DataStore + covers + books)
- Storage usage visualization

### 🎨 Themes & Appearance
- **Three app themes**: Default (customizable, initially Lumi Pink), Material 3 (dynamic color), Liquid Glass
- Liquid Glass: adjustable transparency + HDR touch highlight
- Dark mode (follow system / light / dark)
- Motion Pro animations (Beta)
- Optional splash screen
- Predictive back gesture support
- UI element transition animations
- **Multi-language support**: 7 locales — Simplified Chinese, Traditional Chinese (Taiwan, China), Traditional Chinese (Hong Kong, China), Traditional Chinese (Macau, China), English, 日本語, 한국어

### ⚙️ Smart PDF Processing
- **Local extraction**: On-device PDF text layer extraction (no scanned PDF support)
- **MinerU cloud parsing** (third-party service):
  - Lightweight mode: free, no token, up to 10MB / 20 pages
  - Precision mode: personal token required, up to 200MB / 200 pages, VLM-powered
  - Manual mode: process on MinerU website, then import ZIP/Markdown result
- Convert PDFs to reflowable ebooks with consistent reading experience

### 🔒 Privacy Protection
- **Local-first** — Only connects to GitHub when checking for updates; no personal data sent
- **Zero third-party SDKs** — No analytics, ads, push notifications, or crash reporting
- **Android sandbox storage** — All data stored in app-private directories only
- **Open source, auditable** — Full source code publicly available for review
- Privacy policy & terms displayed on first launch with transparent disclosure
- Sensitive credentials (WebDAV, TTS API Key) encrypted via Android KeyStore

---

## 📥 Get It

Download the latest APK from [GitHub Releases](https://github.com/huangder/Lumi_Books/releases).

---

## 📄 Changelog

See [Changelog](https://huangder.top/changelog.html)

---

## ⭐ Star History

<p align="center">
  <a href="https://star-history.com/#huangder/Lumi_Books&Date">
    <img src="https://api.star-history.com/svg?repos=huangder/Lumi_Books&type=Date" alt="Star History Chart" width="600">
  </a>
</p>

---

## 📜 License

Lumi's original code is open-sourced under the [MIT License](LICENSE). Third-party dependencies and adaptations follow their respective licenses. The Liquid Glass implementation is adapted from [AndroidLiquidGlass / Backdrop](https://github.com/Kyant0/AndroidLiquidGlass) under Apache 2.0. © 2026 Huangder

---

## 🔗 Links

- 🌐 Website: [huangder.top](https://huangder.top)
- 📦 GitHub: [github.com/huangder/Lumi_Books](https://github.com/huangder/Lumi_Books)
- 📧 Email: huangder0104@126.com

---

<p align="center">
  <sub>A good book deserves a good reader.</sub>
</p>
