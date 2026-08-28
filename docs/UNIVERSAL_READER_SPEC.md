# LumaRead Universal Reader â€” Product Design & Feature Specification

**Product:** LumaRead
**Document type:** Product, UX, and engineering specification
**Audience:** App developers, designers, QA
**Primary markets:** India (English + Hindi), with a Kindle-like reading surface and Firefly mark #4 branding
**Date:** 28 August 2026
**Baseline:** LumaRead 1.1 (Android, local-first PDF + Read Aloud + MP3/M4B audiobooks)
**Target:** Universal, offline-first reader across phone, tablet, and web

This document is the source of truth for *what* to build and *why*. Implementation details that already exist in the Android app are called out as **Baseline**. Gaps are **Target**.

---

## 1. Product intent

LumaRead is a **local, import-only** library for reading and listening. The user owns the files. There is no account, no storefront, no cloud sync, and no mandatory network. The app must feel like a quiet, paper-warm Kindle: covers, progress, bookmarks, and a reading surface that disappears. Speech is a first-class companion for commuting and screen-off use, not a bolt-on.

**Success looks like:** a bilingual Indian reader can import a mixed shelf (PDF textbook, EPUB novel, Hindi DOC notes, scanned DJVU, MP3/M4B audiobook), open any title in under a second for the first page, resume at the exact line they left, never lose progress on a crash, and keep using the app on a train with airplane mode on.

### 1.1 Principles (non-negotiable)

| ID | Principle | Implication |
|----|-----------|-------------|
| P1 | Offline-first | Core import, open, read, listen, bookmark, annotate, backup, and restore work with no network. |
| P2 | Local-only library | No accounts, no cloud library, no telemetry that uploads book content. |
| P3 | Fast first page | Opening a book shows content immediately. Indexing, OCR, chapter maps, and embeddings run in the background. |
| P4 | Crash-safe progress | All writes that users care about go through a transactional store. Partial writes never become the source of truth. |
| P5 | Precise resume | Resume is page + line (reflow) or page + region (fixed layout) or timestamp (audio), not â€œlast chapter.â€ |
| P6 | Honest status | Library tiles always show whether a file is available, missing, importing, or needs repair. |
| P7 | Optional intelligence | AI, translation, and recommendations are off by default and never block reading. |
| P8 | Settings survive updates | Preferences, gestures, voices, and theme persist across app updates via versioned local schema. |

### 1.2 Non-goals

- Kindle/Google Play storefront, DRM store, or social reading network.
- Decrypting DRM-protected MOBI/AZW/AZW3/KFX.
- Mandatory cloud backup or cross-device sync (local export/import is the sync story).
- Requiring a Google/Apple/Microsoft account.
- Always-on internet for TTS (offline voices are required; network voices are optional).

---

## 2. Platforms & form factors

LumaRead is **one product**, three surfaces. Shared domain model, shared backup format, platform-native UI.

| Surface | Form factor | UI density | Navigation |
|---------|-------------|------------|------------|
| Phone | 360â€“430 dp width | Single column library; full-screen reader; chrome auto-hides | Bottom library chrome; swipe pages; edge taps |
| Tablet | â‰¥600 dp width | Cover grid + optional list; two-page spread optional for reflow | Persistent sidebar or rail; two-finger swipe; hardware keys |
| Web | Desktop + tablet browser | Kindle-like shelf; keyboard-first | Arrow keys, space, click edges; drag-and-drop import |

**Responsive rules**

- Breakpoints: compact `<600 dp`, medium `600â€“839 dp`, expanded `â‰¥840 dp`.
- Library: list on compact; 3â€“6 column cover grid on medium/expanded.
- Reader: single page default; optional two-page spread on landscape tablet/web for reflow formats only (not comics/DJVU unless user opts in).
- Web must run as a **Progressive Web App**: installable, file access via File System Access API where available, IndexedDB + OPFS for books and WAL.

**Parity requirement:** A backup produced on Android must restore on web (and vice versa) with books, progress, bookmarks, notes, highlights, skip rules, and settings intact, subject to file-presence (see Â§8).

---

## 3. Branding & visual design

### 3.1 Firefly mark #4

**Wordmark:** LumaRead
**Mark:** Firefly logo **#4** from the brand exploration set â€” the chosen canonical mark for shipping.

**Mark usage**

- App icon: firefly on warm paper field (`#F8F3E9` light / `#171511` dark).
- Splash / empty library: mark + â€œYour books stay on this device.â€
- Notification small icon: simplified single-colour firefly silhouette.
- Do not animate the mark during reading. Motion is reserved for progress and import.

If asset files are not yet in the repo, treat #4 as the locked brand decision and place vector + Android adaptive icon + PWA maskable icon in `brand/firefly-04/` before UI freeze.

### 3.2 Kindle-inspired UI (not a clone)

Borrow Kindleâ€™s *quiet reading*, not Amazon chrome.

| Kindle pattern to keep | LumaRead interpretation |
|------------------------|-------------------------|
| Cover-forward home | â€œYour libraryâ€ grid with progress bar on cover |
| Continue reading | Horizontal row of in-progress titles (already in `LibraryRules.continueReading`) |
| Hidden chrome | Tap center to toggle toolbar; chrome never covers text for more than one tap |
| Paper / sepia / dark | Existing `LumaThemeMode`: PAPER, SEPIA, DARK |
| Serif body | Existing serif type scale; Hindi uses a Devanagari-capable family (e.g. Noto Serif Devanagari) at the same sizes |
| Progress at bottom | Thin page/location bar; tap to jump |
| Font / spacing sheet | Bottom sheet, not a settings maze |

**Colour tokens (baseline, keep)**

- Paper background `#F8F3E9`, ink `#27231D`, accent brown `#7A4D16`.
- Sepia and dark as in `LumaTheme.kt`.
- Status colours: Available = ink; Missing = error container; Importing = primary; Repair = tertiary/warning.

**Motion:** 150â€“220 ms fades. No parallax. Page turn: horizontal slide matching swipe direction, 200 ms ease-out.

**Typography for Hindi:** UI chrome bilingual (see Â§12). Body text respects the bookâ€™s script. Never fake Devanagari with Latin fallback.

---

## 4. Information architecture

```
App
â”œâ”€â”€ Library (default)
â”‚   â”œâ”€â”€ Continue reading
â”‚   â”œâ”€â”€ Filters: All | Books | Audiobooks | Favourites
â”‚   â”œâ”€â”€ Search (title / author / filename)
â”‚   â””â”€â”€ Book tile â†’ open reader or player
â”œâ”€â”€ Reader (ebook / document / comic)
â”‚   â”œâ”€â”€ Page surface
â”‚   â”œâ”€â”€ Chrome: back, bookmark, read aloud, overflow
â”‚   â”œâ”€â”€ Contents / locations
â”‚   â”œâ”€â”€ Notes & highlights
â”‚   â””â”€â”€ Skip rules
â”œâ”€â”€ Audiobook player
â”‚   â”œâ”€â”€ Artwork, chapters, speed, sleep timer
â”‚   â””â”€â”€ Read-along (if matching ebook exists â€” optional later)
â”œâ”€â”€ Import
â”‚   â””â”€â”€ System picker / drag-drop / share-in
â”œâ”€â”€ Backup & restore
â””â”€â”€ Settings
    â”œâ”€â”€ Reading (theme, font, gestures)
    â”œâ”€â”€ Voice Studio (language, gender, engine, speed)
    â”œâ”€â”€ Library & storage
    â””â”€â”€ Optional extras (AI, translation) â€” off by default
```

No sign-in screen. First launch: 3-panel onboarding (import, offline, voices) that can be skipped.

---

## 5. Library (import-only)

### 5.1 Behaviour

- User adds files via OS picker, share sheet, drag-and-drop (web/tablet), or â€œOpen with LumaRead.â€
- App **copies or takes persistable URI permission** so the book survives after the picker closes.
- **Preferred:** copy into app-private storage (`books/{bookId}/original.ext`) so backup and missing-file repair are deterministic. URI-only links are allowed but marked **Linked** (can become Missing if the user deletes the source).
- Duplicate detection: same content hash (SHA-256 of file) â†’ offer â€œOpen existingâ€ vs â€œKeep both.â€
- Remove from library: delete metadata; optionally delete the copied file. Never delete a user file that was only linked without confirmation.

### 5.2 Book identity

`bookId` = stable UUID assigned at import. Content hash is stored separately for dedupe and repair. Title comes from metadata, then filename. Author when present.

### 5.3 Library filters & sort (**Baseline + Target**)

**Baseline:** All / Books / Audiobooks / Favourites; Recent / Title.
**Target:** add Authors, Unfinished, Missing files; sort by Author, Added date, Progress.

---

## 6. Format support

Treat each format as a **reader pipeline**: ingest â†’ normalize â†’ render â†’ locate â†’ speak.

### 6.1 Capability matrix

| Format | Render mode | Text extract | TTS | TOC | Annotations | Notes |
|--------|-------------|--------------|-----|-----|-------------|-------|
| EPUB 2/3 | Reflow | Yes | Yes | Nav/NCX | Character offsets | Primary novel format |
| PDF | Fixed | Embedded text + OCR | Yes | Outline | Quad points | **Baseline** |
| TXT | Reflow | Yes | Yes | Heuristic | Offsets | Encoding detect UTF-8/16, Hindi |
| HTML | Reflow (sanitized) | Yes | Yes | Headings | Offsets | No remote scripts; sandbox |
| Markdown | Reflow | Yes | Yes | Headings | Offsets | GFM subset |
| DOC/DOCX | Reflow via convert | Yes | Yes | Headings | Offsets | Convert on import; store original |
| RTF | Reflow | Yes | Yes | Weak | Offsets | |
| ODT | Reflow | Yes | Yes | Headings | Offsets | |
| FB2 | Reflow | Yes | Yes | Sections | Offsets | Common in RU/IN scans |
| MOBI / AZW3 **unencrypted only** | Reflow | Yes | Yes | TOC | Offsets | Reject DRM with clear message |
| DJVU | Fixed | OCR / hidden text | After OCR | Optional | Regions | Background OCR |
| CBZ / CBR | Comic (image) | Optional OCR | Optional | Filenames | Page bookmarks | Preload adjacent pages |
| MP3 | Audio | n/a | n/a (file is audio) | ID3 / CUE / one-track | Time bookmarks | **Baseline** |
| M4B / M4A | Audio | n/a | n/a | `chpl` / QuickTime | Time bookmarks | **Baseline** chapters |
| WAV / OGG / OPUS / FLAC | Audio | n/a | n/a | Optional | Time | Common audiobook extras |

**DRM:** If encryption is detected, status = **Unsupported (protected)**. Do not attempt circumvention. Tell the user to use a DRM-free file.

**DOC/DOCX/ODT:** Import converts to an internal HTML or EPUB-like package. Original file is retained in the book folder for backup fidelity.

### 6.2 Opening performance

| Phase | Time budget | What happens |
|-------|-------------|--------------|
| First paint | <400 ms after tap (warm cache) / <1.2 s cold for typical EPUB/PDF first page | Cover + page 1 from cache or quick renderer |
| Interactive | Same as first paint | Swipe, bookmark, theme |
| Background job | Unbounded, cancellable | Full text index, OCR queue, chapter detection, cover extract, hash |
| Speak-ready | First page text as soon as extract/OCR for *that page* finishes | Do not wait for whole-book OCR |

UI shows a non-blocking **Indexingâ€¦** chip; reading is never blocked.

---

## 7. Reader: navigation, gestures, skip

### 7.1 Page navigation

**Gestures (defaults; user-remappable in Settings)**

| Gesture | Default action |
|---------|----------------|
| Swipe left | Next page / next screen of reflow |
| Swipe right | Previous page |
| Tap right 30% | Next page |
| Tap left 30% | Previous page |
| Tap center | Toggle chrome |
| Vertical swipe (reflow) | Scroll within page if overflow; else next/prev |
| Pinch | Zoom (fixed layout, comics, PDF) |
| Two-finger swipe (tablet) | Next/prev chapter |
| Volume keys (optional) | Next/prev page while reading |
| Keyboard (web) | â† â†’ Space PgUp PgDn |

RTL: if UI language is not RTL, book pagination still follows the bookâ€™s writing direction for Arabic later; for EN/HI LTR remains default.

**Baseline:** PDF pinch-zoom/pan and next/prev controls exist; **Target** is full swipe + edge tap + chrome hide matching Kindle.

### 7.2 Skip while reading and while speaking

Skip is a first-class control for textbooks, PDFs with headers, and bilingual notes.

**Modes**

1. **Skip this line** â€” current line / sentence is omitted from TTS and visually dimmed.
2. **Skip this paragraph** â€” current block omitted.
3. **Skip custom section** â€” user selects a range (text selection or box on PDF/DJVU) and names a rule, e.g. â€œRunning headers,â€ â€œFootnotes,â€ â€œPage numbers.â€

**Rule scope:** This book only, or â€œApply to similar pagesâ€ (same PDF crop box / same CSS selector / same heading pattern). Rules persist in the book package.

**TTS:** Skipped ranges are not spoken. Visual skip shows a faint strikethrough or reduced opacity, with a â€œShow skippedâ€ toggle.

**Custom skip during Read Aloud:** notification and on-screen skip-forward: line, paragraph, section (next heading), chapter.

### 7.3 Precise bookmarks & resume (**P5**)

A **resume point** is always stored, even if the user never taps Bookmark.

| Media | Resume locator |
|-------|----------------|
| Reflow (EPUB, TXT, HTML, MD, converted office) | `cfi` or equivalent: spine id + character offset + (optional) line index in current pagination |
| PDF / DJVU | `pageIndex` + `lineIndex` from text layer, or `pageIndex` + normalized y if no text |
| Comic | `pageIndex` |
| Audio | `positionMs` + `chapterIndex` |

**Bookmark** (user-created) stores the same locator plus optional title and timestamp. Multiple bookmarks per book.

**Baseline:** page-level `Set<Int>` bookmarks. **Target:** replace with locator objects; migrate page ints to `{page, line: 0}`.

**Exact line:** For reflow, after layout, map character offset â†’ visible line. On resume, scroll so that line is at ~20% from top (Kindle-like), highlighted for 800 ms then fade.

---

## 8. Status indicators

Every library tile and book detail must show exactly one primary status.

| Status | When | Visual | User action |
|--------|------|--------|-------------|
| **Available** | Copied file present and readable | No badge, or subtle check on detail | Open |
| **Linked** | Persistable URI, file still resolvable | â€œLinkedâ€ | Open; offer â€œCopy into libraryâ€ |
| **Missing** | URI dead or copy deleted | Amber/error â€œFile missingâ€ | Relink / re-import |
| **Importing** | Copy/convert in progress | Progress ring + percent | Cancel |
| **Indexing** | Openable, background work | Thin progress on cover | Open anyway |
| **Needs repair** | DB row exists, package incomplete, hash mismatch, convert failed | â€œRepairâ€ | Repair job or re-import |
| **Unsupported** | DRM or unknown codec | â€œCanâ€™t openâ€ | Explain; keep file out of reader |
| **OCR needed** | Scanned page, no text | â€œText not readyâ€ on Read Aloud | Queue OCR; still show page |

Import progress is per-book and global (notification: â€œImporting 2 of 5â€). Failed import leaves a **Needs repair** stub, not a silent drop.

---

## 9. Text-to-speech (English + Hindi, male + female)

### 9.1 Voice Studio (**Baseline exists; extend**)

User picks:

- **Language:** English (India) `en-IN`, Hindi `hi-IN`. Auto-detect per paragraph when mixed; user can lock language.
- **Gender:** Female / Male. Map to installed engine voices; if only one gender is installed, show install guidance (Android TTS settings / browser speechSynthesis / optional offline pack).
- **Engine mode (keep):** Natural (may use network voice) / Offline (network-required voices hidden) / System default.
- **Speed:** Discrete **1Ã—, 1.25Ã—, 1.5Ã—, 2Ã—** for audiobook *and* as presets for TTS. Fine slider may remain for TTS (baseline 0.65â€“1.75) but presets must be one tap.
- **Pitch:** Keep baseline range.

Preview samples:

- EN: â€œThis is a preview of your LumaRead voice.â€
- HI: â€œà¤¯à¤¹ à¤²à¥‚à¤®à¤¾à¤°à¥‡à¤¡ à¤†à¤µà¤¾à¤œà¤¼ à¤•à¤¾ à¤¨à¤®à¥‚à¤¨à¤¾ à¤¹à¥ˆà¥¤â€

### 9.2 Playback behaviour

- Foreground media session; lock-screen and notification: play/pause, skip line/paragraph, next/prev page or chapter, stop.
- Audio focus: pause for calls; resume after transient loss (**Baseline**).
- Screen-off reading with wake lock as today.
- Highlight the spoken sentence on-screen when the reader is visible.
- Hindi: use `BreakIterator` with `hi` locale; never split mid-akshara.

### 9.3 Audiobooks vs TTS

| | TTS Read Aloud | Audiobook file |
|--|----------------|----------------|
| Source | Extracted/OCR text | MP3/M4B/etc. |
| Voices | en-IN / hi-IN male+female | Narrator in file; Voice Studio does not replace the file |
| Speed | Presets 1 / 1.25 / 1.5 / 2 | Same presets (`LibraryRules.PLAYBACK_SPEEDS`) |
| Skip | Line / paragraph / section | Â±15s and chapter |

Clarification: â€œSelectable Indian-English and Hindi voicesâ€ apply to **TTS of ebooks**, not to replacing an M4B narrator. UI copy must not imply voice-changing a commercial audiobook.

---

## 10. Audiobook player

**Baseline:** `AudiobookService`, M4B chapters, speed, sleep timer, library filter.

**Target additions**

- Lock screen artwork from embedded cover.
- Bookmarks at current timestamp.
- Playback speeds only 1 / 1.25 / 1.5 / 2 (snap via `normalizeSpeed`).
- Chapter list from M4B `chpl`, ID3, or CUE.
- If file has no chapters, synthesize â€œPart 1â€ as whole file.

---

## 11. 24-hour chapter resume prompt

**Trigger:** User pauses (or app backgrounds with a mid-chapter resume point) and does not return to that book for **â‰¥ 24 hours**. On next open of that book (reader or TTS), show an **optional** sheet:

> Youâ€™ve been away from *{chapter title}* for a day.
> [Resume where I left off]  [Hear / read a short recap]  [Donâ€™t ask again for this book]

**Recap**

- Default: **local extractive summary** of the current chapter up to the resume locator (first N sentences + last N sentences, or heading-based outline). Works offline. No network.
- If user enabled Optional AI (Â§16) and network is available, they may request a richer recap; if AI fails, fall back to extractive.
- Recap is never auto-played without tapping the recap action.
- â€œDonâ€™t ask againâ€ is per-book; global toggle in Settings.

Store `pausedAt`, `chapterId`, `resumeLocator` on pause. Compare `now - pausedAt >= 24h`.

---

## 12. Localization (English + Hindi)

- App UI: English and Hindi (user language = OS or in-app override).
- All chrome strings in `values` + `values-hi`.
- Reader body: book language, not UI language.
- Dates/numbers: Indian English conventions where UI is EN; Hindi locale for HI.
- Voice labels: â€œEnglish (India) Â· Femaleâ€, â€œà¤¹à¤¿à¤¨à¥à¤¦à¥€ Â· à¤ªà¥à¤°à¥à¤·â€, etc.

---

## 13. Annotations & export

### 13.1 Types

| Type | Locator | Body |
|------|---------|------|
| Highlight | Text range or PDF quads | Colour (4 swatches) |
| Note | Same + text | User text, EN/HI |
| Bookmark | Resume locator | Optional label |

### 13.2 Export (portable)

From book overflow or Settings:

| Format | Contents |
|--------|----------|
| **EPUB 3** (annotation sidecar or extra HTML) | Highlights + notes as extra spine item or `annotation-json` |
| **JSON** (LumaRead Annotation Pack v1) | Stable schema, UTF-8 |
| **Markdown** | Title, location, quote, note |
| **CSV** | For spreadsheets |
| **Plain text** | |

Export **never requires network**. Import of the same JSON/Markdown round-trips locators when the book hash matches.

---

## 14. Data, transactions, backup

### 14.1 Local storage layout

```
app-private/
  lumaread.db          # WAL SQLite (source of truth for metadata)
  lumaread.db-wal
  lumaread.db-shm
  settings.json        # versioned preferences (or table)
  books/{bookId}/
    original.ext
    package/           # normalized EPUB-like or page cache
    cover.jpg
    text-index/
    ocr-cache/
    annotations.json
    skip-rules.json
  backups/             # last successful export copy optional
```

Web: OPFS mirror of this tree; IndexedDB for query indexes.

### 14.2 Transactional save (**P4**)

**What must be transactional:** library rows, resume locators, bookmarks, annotations, skip rules, settings, favourite, playback position.

**How**

1. SQLite with **WAL** and `IMMEDIATE`/`EXCLUSIVE` transactions for multi-row updates.
2. Never write JSON files as the live store without: write `*.tmp` â†’ `fsync` â†’ atomic rename. If rename fails, previous file remains.
3. Resume position: debounce 500 ms, then one transaction. Also flush on pause, page turn, `onStop`, process death (`onTrimMemory`).
4. `saveBooks` full-table replace (**Baseline**) must remain inside `beginTransaction` / `setTransactionSuccessful` (already). Prefer **per-book upsert** for hot path to reduce lock time.
5. After crash: WAL recovery; if annotations file is `.tmp` leftover, ignore it.
6. Integrity: `PRAGMA foreign_keys=ON`; `bookId` FKs for annotations.
7. **No progress lost** acceptance: kill app during page turn 100 times in test; last committed locator is â‰¤1 page/line behind and never a corrupt DB.

### 14.3 Settings survival across updates (**P8**)

- Schema version on DB and on `settings.json` (`schemaVersion: N`).
- Migrations are additive (`onUpgrade` pattern already used).
- Gesture map, theme, Voice Studio, skip defaults, â€œdonâ€™t ask recapâ€, optional extras flags: all in versioned settings, never reset unless key missing.
- If a setting is unknown after downgrade, ignore extra keys; if missing after upgrade, apply documented defaults **without wiping others**.

### 14.4 Backup & restore

**Backup package:** `LumaRead-Backup-YYYYMMDD.lrbak` â€” zip of:

- `manifest.json` (app version, schema, createdAt, book count, hashes)
- `lumaread.db` (checkpointed)
- `settings.json`
- `books/**` (originals + annotations + skip rules; optional exclude bulky OCR cache)

**Restore:** User picks file â†’ validate manifest â†’ restore into a staging dir â†’ atomic swap â†’ reopen DB. If validation fails, original library untouched.

**Partial restore:** â€œMetadata onlyâ€ if files are huge; status becomes Missing until relink.

No account. Backup lives where the user saves it (Downloads, USB, drive app of their choice).

---

## 15. Domain model (target)

```text
Book
  id, title, authors[], language, mediaKind
  sourceHash, storage: COPIED | LINKED
  status: see Â§8
  addedAt, lastOpenedAt, favourite
  coverPath

Locator
  kind: CFI | PDF_LINE | PDF_REGION | COMIC_PAGE | AUDIO_MS
  ... typed fields

Resume = Locator + updatedAt + chapterId?

Bookmark = id + Locator + label + createdAt

Highlight = id + range + color + createdAt
Note = Highlight + body

SkipRule = id + scope (BOOK | PATTERN) + selector + createdAt

Playback
  speed âˆˆ {1, 1.25, 1.5, 2}
  ttsVoiceId, ttsGender, ttsLang, offlineOnly
  positionMs | sentenceIndex

ChapterPause
  bookId, pausedAt, chapterId, locator
```

**Baseline `BookItem`** is a subset (PDF/AUDIO, page bookmarks). Migrate with DB version bump; keep `uri` during transition.

---

## 16. Optional extras (off by default)

Master switch: **Settings â†’ Optional extras**. Each item separate. Disabled = no network calls, no model download beyond user action.

| Extra | Default | Offline | Purpose |
|-------|---------|---------|---------|
| AI chapter recap | Off | No (unless on-device model later) | Richer 24h recap |
| Recommendations | Off | Yes if â€œsimilar on this deviceâ€ only | Local similarity from titles/tags; never a store |
| Translation | Off | On-device ML Kit if present; else optional network | Word/sentence lookup ENâ†”HI |
| Cloud TTS | Off | No | Natural voices |

When off, code paths are not invoked. UI does not nag. First use shows data-use copy: â€œText of the current chapter may be sent to {provider}. Your library is not uploaded.â€

---

## 17. Privacy & security

- Book bytes and extracted text stay on device unless the user exports a backup or enables an extra that they confirm.
- Web: no third-party analytics by default.
- Sanitise HTML (no script, no remote fonts unless user allows).
- Backup files may contain the userâ€™s entire library â€” document that clearly.
- Android: use app-private storage; SAF persistable permissions for Linked books.

---

## 18. Accessibility

- TalkBack / VoiceOver / web a11y: all chrome labelled; reading surface exposes text where possible.
- TTS and screen reader must not fight: if TalkBack is speaking, pause Read Aloud or vice versa (user preference).
- Contrast: paper/sepia/dark meet WCAG AA for chrome; user font scale 80â€“200%.
- Hit targets â‰¥48 dp.
- Reduced motion: skip page-flip animation.

---

## 19. Architecture (engineering)

```
â”Œâ”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”
â”‚  UI: Library / Reader / Player / Settingsâ”‚
â””â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”¬â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”˜
                  â”‚
â”Œâ”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â–¼â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”
â”‚  Domain: Library, Progress, Annotations â”‚
â”‚  transactional repository               â”‚
â””â”€â”€â”€â”€â”€â”€â”€â”¬â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”¬â”€â”€â”€â”€â”€â”€â”€â”€â”€â”¬â”€â”€â”€â”€â”€â”€â”€â”€â”€â”˜
        â”‚             â”‚         â”‚
   Ingestors      Renderers   Speech/Audio
   (format)       (page/reflow) (TTS / exo)
        â”‚
   Job queue (index, OCR, convert) â€” WorkManager / web Worker
```

**Platform notes**

- **Android (baseline):** Compose, `LibraryRepository` SQLite, `PdfRenderer`, PdfBox, ML Kit OCR Latin+Devanagari, `TextToSpeech`, `AudiobookService`.
- **Tablet:** same APK, window size classes.
- **Web:** Kotlin Multiplatform later *or* shared backup schema + TypeScript PWA. Do not block Android on KMP. **Ship Android format parity first**; web consumes the same `.lrbak` and locator schema.

**Job queue rules:** User-visible work (open page) always preempts OCR. One OCR page at a time unless charging + idle.

---

## 20. UX flows (happy path)

### Import

1. Tap + â†’ picker (multi-select).
2. Sheet: â€œImporting 3 filesâ€¦â€ with per-file progress.
3. First file becomes Available; user can open it while others import.
4. Failures â†’ Needs repair with reason (DRM, corrupt zip, disk full).

### Read

1. Tap cover â†’ first page / resume locator (no spinner over full screen; skeleton page ok).
2. Swipe / tap edges.
3. Bookmark = current line.
4. Read Aloud â†’ Voice Studio if no voice chosen; else start at current sentence.

### Away 24h

1. Pause mid-chapter.
2. Return after 24h â†’ recap sheet.
3. Resume or recap then resume.

### Missing file

1. Tile shows Missing.
2. Tap â†’ Relink picker.
3. Hash matches â†’ restore locators; mismatch â†’ â€œThis looks like a different fileâ€ + offer new book vs replace.

---

## 21. Phased delivery

Map to current LumaRead so work is incremental.

| Phase | Outcome | Includes |
|-------|---------|----------|
| **0 Baseline** | Shipped 1.1 | PDF, TTS EN/HI, OCR, MP3/M4B, local SQLite, speeds, Voice Studio, Kindle-ish paper theme |
| **1 Resume & trust** | P4â€“P6, P8 | Line-level locators, WAL annotations, status badges, settings versioning, swipe navigation, skip line/paragraph |
| **2 Formats** | Universal documents | EPUB, TXT, HTML, MD, DOCX, RTF, ODT, FB2, unencrypted MOBI/AZW3 |
| **3 Image books** | Scans & comics | DJVU, CBZ/CBR, background OCR queue UX |
| **4 Backup & notes** | Portability | `.lrbak`, highlight/note export JSON/MD/CSV |
| **5 Tablet polish** | Two-pane library, two-page spread, hardware keys |
| **6 Web PWA** | Same backup schema, File System Access / OPFS |
| **7 Optional extras** | AI recap, local recommendations, translation toggles |
| **8 24h recap** | Extractive recap (can land after Phase 1 locators + chapter map) |

Phase 8 depends on chapter detection for EPUB/PDF outline/M4B; for PDFs without outline, treat â€œchapterâ€ as 10% slices or detected headings.

---

## 22. Acceptance criteria (QA)

1. Airplane mode: import from local file (Android: already on device), open, bookmark, TTS with offline voice, audiobook 1.5Ã—, backup to Downloads, restore after clear-data â€” all succeed.
2. Kill process during swipe: on relaunch, book opens at last committed line/page; DB opens without repair dialog.
3. Hindi EPUB + English PDF on the same shelf; Voice Studio female hi-IN and male en-IN selectable when engines provide them.
4. DRM AZW3: Unsupported message, no crash.
5. Missing linked PDF: Missing badge; relink restores bookmarks.
6. Custom skip of running headers: TTS does not speak them after rule save.
7. Pause 24h (test harness may inject clock): recap sheet appears once; Dismiss â†’ resume locator unchanged.
8. App update migration: theme, gestures, voices preserved.
9. Export highlights to Markdown; file opens in another editor with quote + location.
10. First page of 400 MB PDF visible before OCR of page 200 starts.

---

## 23. Open decisions (do not block Phase 1)

- Web stack: KMP vs separate PWA sharing only `.lrbak`.
- Bundled offline neural TTS vs system engines only (size vs quality).
- Two-page spread default on/off for tablets.
- Whether Linked books are discouraged in UI copy (recommended: yes, prefer Copy).

---

## 24. Glossary

| Term | Meaning |
|------|---------|
| Locator | Canonical pointer to a place in a book |
| Copied vs Linked | File in app storage vs persistable URI |
| Firefly #4 | Locked logo variant for shipping |
| Extractive recap | On-device summary from existing chapter text |
| `.lrbak` | Local backup archive |

---

*End of specification. Implement against principles P1â€“P8; when a feature conflicts with offline or crash-safety, those win.*
