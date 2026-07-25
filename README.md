# Doc2MD — Universal Document Reader & Markdown Workspace Converter

[![Android CI](https://github.com/aistudio/Doc2MD/actions/workflows/android.yml/badge.svg)](https://github.com/aistudio/Doc2MD/actions)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-purple.svg)](https://kotlinlang.org)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-M3-blue.svg)](https://developer.android.com/jetpack/compose)
[![License](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)

**Doc2MD** is a powerful, lightweight, privacy-focused Android application designed to convert, view, and organize virtually any local document format into a clean, editable, and portable **Markdown Workspace**.

It integrates seamlessly into Android's system **"Open With"** menu and Storage Access Framework (SAF), allowing you to open documents directly from any cloud storage, file manager, or email attachment without storing full original copies on your device.

---

## 🚀 Key Features

* **Universal Format Support**: Converts PDF, DOCX, PPTX, XLSX, CSV, JSON, HTML, TXT, Markdown, and source code files into Markdown.
* **Page-by-Page & Slide Navigation**: Preserves individual pages and PowerPoint slides with dedicated tabbed navigation.
* **Compressed Visual Previews**: Generates high-quality WebP page thumbnails and slide previews to keep visual structure intact while minimizing storage footprint.
* **Rich Metadata & Content Extraction**:
  * **PDF**: Page rendering, text structure, layout preservation.
  * **DOCX**: Headings, paragraphs, formatted bullet lists, embedded tables, and inline extracted images.
  * **PPTX**: Slide text, bullet points, and extracted speaker notes.
  * **XLSX**: Multi-sheet conversion into native Markdown tables and extracted chart visuals.
  * **CSV & JSON**: Clean, pretty-formatted Markdown tables and syntax-highlighted code blocks.
* **Built-in Interactive Markdown Viewer**:
  * Rendered preview with custom typography, blockquotes, code syntax blocks, and image embeds.
  * In-document live text search with keyword highlighting.
  * Toggle between rendered preview and raw Markdown view.
  * One-tap code and block copying.
* **ZIP Workspace Export**: Export any converted document along with its extracted images and previews as a portable `.zip` archive.
* **Storage Optimization & Cache Control**:
  * Granular cache management: clear previews, purge temp buffers, or clear converted files while preserving document history.
  * Real-time storage consumption dashboard.
* **Reading Progress & Search**: Remembers your last read page/slide position for every document. Search across all converted documents by title or text content.
* **Material Design 3 & Theme Support**: Built with dynamic light/dark theme toggle adhering to Material 3 standards.

---

## 📁 Supported File Formats

| Format | Category | Extraction Capabilities |
| :--- | :--- | :--- |
| **`.pdf`** | Document | Compressed page previews (WebP), structural layout |
| **`.docx`, `.doc`** | Word Document | Headings (H1–H3), lists, paragraphs, tables, embedded images |
| **`.pptx`, `.ppt`** | Presentation | Slide structure, bullet lists, speaker notes, slide images |
| **`.xlsx`, `.xls`** | Spreadsheet | Multi-sheet tables, exported charts & media assets |
| **`.csv`** | Data | Markdown formatted tables |
| **`.json`** | Data | Formatted JSON code blocks with syntax highlighting |
| **`.html`, `.htm`** | Web | Stripped HTML formatting translated to Markdown headers & body |
| **`.txt`, `.md`** | Text | Direct raw Markdown rendering |
| **Source Code** | Code (`.kt`, `.java`, `.py`, `.js`, `.ts`, `.cpp`, `.rs`, `.sql`, etc.) | Code block formatting with copy affordances |

---

## 🏗️ Architecture & Tech Stack

Doc2MD strictly follows **Clean Architecture** and **MVVM** design principles with standard Kotlin best practices:

* **UI Layer**: Jetpack Compose + Material 3, Navigation Compose, Coil (Image Loading).
* **Architecture**: ViewModel, Kotlin Coroutines, StateFlow, Coroutine Flow.
* **Data Persistence**:
  * **Room Database**: Local storage for converted document records, metadata, and per-page content.
  * **DataStore Preferences**: User settings and theme mode configuration.
* **Converters**: Modular engine (`PDFConverter`, `DocxConverter`, `PptxConverter`, `XlsxConverter`, `TextConverter`).
* **Storage**: Android Storage Access Framework (SAF) and `FileProvider` for secure sharing and ZIP export.

---

## 💾 Storage & Privacy Design

* **No Unnecessary File Duplication**: Doc2MD does not duplicate original source files. It parses documents on-the-fly into Markdown text and compressed WebP previews.
* **Cache Management Dashboard**: Users can inspect storage usage and clean up image previews or temp files at any time without losing reading history.
* **100% Offline & Private**: All document processing happens locally on device. No internet server uploads or third-party cloud SDKs.

---

## 🛠️ Building & Running

### Prerequisites
* **Android Studio**: Ladybug (2024.2.1) or newer
* **JDK**: Version 17
* **Android SDK**: Min SDK 24 (Android 7.0), Target SDK 36

### Build Steps

```bash
# Clone the repository
git clone https://github.com/aistudio/Doc2MD.git
cd Doc2MD

# Build Debug APK
./gradlew assembleDebug

# Run Unit & Robolectric Tests
./gradlew testDebugUnitTest
```

---

## ⚙️ CI/CD & GitHub Actions Workflow

This repository includes a pre-configured `.github/workflows/android.yml` workflow that automatically:
1. Runs linter and unit tests on every `push` and `pull_request` to `main`.
2. Builds signed/unsigned APKs.
3. Automatically attaches built APK artifacts to GitHub Releases on tag creation.

---

## 📄 License

```text
MIT License

Copyright (c) 2026 Doc2MD

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction rights to use, copy, modify, merge, publish,
distribute, sublicense, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.
```
