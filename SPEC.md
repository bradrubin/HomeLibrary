# Book Catalog Android App — Build Specification

This document is the specification for a personal Android app that catalogs a home book collection. It is written for Claude Code to implement. Build incrementally by milestone (see the end), get each milestone compiling and running before starting the next, and ask before making major deviations from this spec.

---

## 1. Purpose

Scan a book's ISBN barcode (or type the ISBN), look up its details from free internet sources, let the user review and edit them, and save the book in a local database on the phone. Support CSV import/export, with cover images included via a ZIP bundle.

Personal use, single user, sideloaded. No Play Store release, no accounts, no cloud backend.

---

## 2. Tech stack

- **Language:** Kotlin
- **UI:** Jetpack Compose, Material 3, single-activity with Navigation Compose
- **Architecture:** MVVM — Compose screens → ViewModels → Repository → (Room DAO, API clients, file storage)
- **Database:** Room (SQLite), with schema export enabled and versioned migrations
- **Networking:** Retrofit + OkHttp + kotlinx.serialization (or Moshi — pick one and be consistent)
- **Images:** Coil for display; covers downloaded and stored as local files
- **Barcode scanning:** Google code scanner (`play-services-code-scanner`) as the default — it provides a ready-made scanning UI and needs no camera permission. If it proves unsuitable for continuous/batch scanning (Milestone 4), use CameraX + ML Kit barcode-scanning instead.
- **Camera photo for covers:** `ActivityResultContracts.TakePicture` with a FileProvider URI
- **File import/export:** Storage Access Framework (`CreateDocument` / `OpenDocument`) — no broad storage permissions
- **Async:** Coroutines + Flow
- **DI:** Keep it simple — manual dependency injection via an `AppContainer` in the Application class. (Hilt is acceptable if preferred, but not required.)
- **Build:** Gradle Kotlin DSL with a version catalog (`libs.versions.toml`). Use current stable versions of all libraries.
- **minSdk:** 26, **targetSdk:** current stable
- **Package name:** `com.example.bookcatalog` (placeholder — easy to change)

---

## 3. Data model

### `Book` entity (table `books`)

| Field | Type | Notes |
|---|---|---|
| `id` | Long, PK, autogen | |
| `isbn13` | String? | Normalized, digits only. Unique index (nullable — books without ISBNs are allowed). |
| `isbn10` | String? | Derived when the ISBN-13 starts with 978 |
| `title` | String | Required |
| `subtitle` | String? | |
| `authors` | String? | Multiple authors joined with `; ` |
| `publisher` | String? | |
| `publishedDate` | String? | Publication year only, as four digits (e.g., `1999`). Sources return "1999", "1999-05", "May 1999", etc.; keep just the year. |
| `pageCount` | Int? | |
| `format` | String? | User field: `Hardcover` / `Softcover` / `Box set` / `Kindle` / `PDF` / `ePub` / `Audiobook` / `Other` |
| `language` | String? | |
| `subjects` | String? | Joined with `; ` |
| `description` | String? | |
| `coverFile` | String? | Filename only (e.g., `9780385533225.jpg`), stored in app-internal `files/covers/` |
| `coverSourceUrl` | String? | Where the cover came from; `"user-photo"` for camera photos |
| `dataSource` | String? | e.g., `"OpenLibrary+Google"`, `"Manual"` |
| `location` | String? | User field — shelf/room |
| `notes` | String? | User field |
| `rating` | Int? | User field, 0–5 |
| `readStatus` | String? | User field: `unread` / `reading` / `read` |
| `dateAdded` | Long | Epoch millis |
| `dateModified` | Long | Epoch millis |

Keep author/subject lists as delimited strings rather than separate tables — simpler, and maps cleanly to CSV.

---

## 4. ISBN handling (`IsbnUtils`)

Pure Kotlin, fully unit-tested:

- `normalize(input)`: strip spaces, hyphens; uppercase `x` → `X`.
- `isValidIsbn10`, `isValidIsbn13`: check-digit validation.
- `isbn10To13`, `isbn13To10` (only for 978 prefix).
- `fromBarcode(raw)`: accept only 13-digit EAN codes starting with `978` or `979` that pass validation. Reject everything else — in particular the 5-digit price add-on barcode that appears next to many book barcodes, and UPC codes on mass-market paperbacks (show a message suggesting manual entry).
- Manual entry accepts ISBN-10 or ISBN-13 with or without hyphens; converts to ISBN-13 for storage and lookup.

---

## 5. Metadata lookup

### Sources (both free)

**Open Library** — no key needed.
- `GET https://openlibrary.org/api/books?bibkeys=ISBN:{isbn13}&format=json&jscmd=data`
- Useful fields: `title`, `subtitle`, `authors[].name`, `publishers[].name`, `publish_date`, `number_of_pages`, `subjects[].name`, `cover.large|medium|small`
- Set a descriptive `User-Agent` header on all Open Library requests (e.g., `BookCatalog/1.0 (personal use; contact@example.com)`), as Open Library requests.
- Covers: prefer the URL returned in `cover` (cover-ID based). Avoid the `covers.openlibrary.org/b/isbn/...` pattern for routine use — it is rate-limited per IP.

**Google Books** — key optional.
- `GET https://www.googleapis.com/books/v1/volumes?q=isbn:{isbn13}` (append `&key={KEY}` if configured)
- Useful fields in `items[0].volumeInfo`: `title`, `subtitle`, `authors`, `publisher`, `publishedDate`, `pageCount`, `description`, `categories`, `language`, `imageLinks.thumbnail`
- Rewrite `http://` image URLs to `https://`. The thumbnail URL often accepts `&zoom=0`/`&zoom=2` for a larger image and sometimes contains `&edge=curl` — remove that parameter.
- API key: read from `local.properties` (`GOOGLE_BOOKS_API_KEY=...`) into `BuildConfig`; works without one. Never commit the key.

### Merge rules

Query both sources in parallel (`async`), with a ~10 s timeout each. A failure in one source must not block the other.

Field priority:
- `title`, `subtitle`, `authors`, `publisher`, `subjects`: Open Library first, Google fallback
- `pageCount`, `description`, `language`: Google first, Open Library fallback
- `publishedDate`: the year from Open Library, then Google
- Cover: Open Library large → Google (zoomed) → none

Record which sources contributed in `dataSource`.

If neither source finds the ISBN, open the edit screen with only the ISBN filled in and a message that no data was found.

Design the lookup layer behind an interface (`BookMetadataSource`) so more sources can be added later.

### Cover download

- Download the chosen cover to `files/covers/{isbn13 or id}.jpg`.
- Skip/replace images that are placeholder-sized (Open Library sometimes returns a 1×1 image; treat anything under ~50 px wide as "no cover").
- Downscale to a max of ~800 px on the long edge, JPEG quality ~85, to keep storage and export size reasonable.

---

## 6. Screens

1. **Library (home)**
   - List of books: cover thumbnail, title, authors, year
   - Search box (title/author/ISBN/publisher), sort (title, author, date added, publication date)
   - Optional grid view toggle (cover wall)
   - FAB / actions: Scan, Enter ISBN, Add manually
   - Overflow menu: Import, Export, Settings
2. **Scan** — launches the scanner; on a valid ISBN, runs lookup and goes to Edit. If the ISBN already exists in the library, show "Already in your library" with options to open it or add another copy.
3. **Enter ISBN** — text field with validation feedback; then lookup → Edit.
4. **Edit Book** — all fields editable; cover area with buttons: Re-fetch, Take photo, Choose from gallery, Remove. Save/Cancel. Used for new books (pre-filled from lookup) and existing ones.
5. **Book Detail** — read-only view with large cover; Edit and Delete (with confirmation).
6. **Import/Export** — format choice and progress; results summary.
7. **Settings** — Google Books API key status, default import conflict behavior, about/attribution.

Show "Book data and covers courtesy of Open Library and Google Books" on the About/Settings screen.

---

## 7. Import / Export

### CSV format

- UTF-8 **with BOM** (so Excel opens accented characters correctly), RFC 4180 quoting, CRLF line endings, header row.
- Use a real CSV library (e.g., Apache Commons CSV or kotlin-csv) — titles contain commas and quotes.
- Columns, in this order:

```
isbn13,isbn10,title,subtitle,authors,publisher,published_date,page_count,format,language,subjects,description,location,notes,rating,read_status,date_added,cover_file,cover_source_url
```

- `date_added` as ISO-8601 (`2026-10-21`).
- Multi-value fields use `; ` inside the cell.

### Export options

1. **ZIP bundle (full backup):** `books.csv` + `covers/` folder containing the files named in `cover_file`. Filename default: `book-catalog-YYYY-MM-DD.zip`.
2. **CSV only:** same CSV, no images (for spreadsheets).

### Import

- Accept either a `.zip` (as above) or a plain `.csv`.
- Header-based column mapping; tolerate missing or extra columns and any column order. `title` is the only required column (ISBN strongly expected).
- For each row:
  - ZIP with a matching file in `covers/` → copy it in.
  - No image but `cover_source_url` or ISBN present → optionally re-download covers (setting: "Fetch missing covers after import", throttled to ~1 request/second).
- **Duplicates** (same ISBN-13 already in library): user chooses for the whole import — Skip, Overwrite, or Keep both.
- Validate ISBNs; rows with invalid ISBNs import with the ISBN field blank and are counted in the summary.
- Run in a coroutine with a progress indicator; finish with a summary: imported / updated / skipped / errors (with row numbers).
- Wrap DB writes in a transaction.

---

## 8. Non-functional requirements

- Fully usable offline for browsing/editing; lookups just fail gracefully with a clear message.
- No crash on network errors, malformed API responses, or malformed CSV rows.
- Handle configuration changes (rotation) without losing in-progress edits.
- Dark mode supported via Material 3 dynamic color.
- Permissions: `INTERNET` only (plus camera if CameraX path is used).

---

## 9. Project structure (suggested)

```
app/src/main/java/com/example/bookcatalog/
  BookCatalogApp.kt          // Application + AppContainer
  MainActivity.kt
  data/
    db/                      // AppDatabase, BookDao, Book entity, migrations
    remote/                  // OpenLibraryApi, GoogleBooksApi, DTOs
    metadata/                // BookMetadataSource, merger
    covers/                  // CoverStore (download, resize, save, delete)
    io/                      // CsvExporter, CsvImporter, ZipBundler
    BookRepository.kt
  domain/
    IsbnUtils.kt
  ui/
    library/ scan/ edit/ detail/ importexport/ settings/
    theme/
    navigation/
```

---

## 10. Testing

- **Unit tests (required):** `IsbnUtils` (valid/invalid, conversions, price add-on rejection), metadata merger (field priority, missing fields), CSV round trip (commas, quotes, newlines in descriptions, unicode, empty fields).
- **Instrumented tests:** Room DAO basics; ZIP export → import round trip restores identical rows and covers.
- Sample ISBNs for manual testing: `9780385533225`, `9780547928227`, `9780140449136`, `0306406152` (ISBN-10).

---

## 11. Milestones

1. **Skeleton:** project setup, Room DB, Library list, manual Add/Edit/Delete with no network. Runs on device.
2. **Lookup:** ISBN entry → Open Library + Google lookup → merge → Edit screen; cover download and display.
3. **Scanning:** barcode scan flow, duplicate detection.
4. **Batch scanning (nice to have):** "scan many" mode — scan several books in a row, queue lookups, then review the list and save all.
5. **Import/Export:** CSV and ZIP, both directions, with conflict handling and summary.
6. **Polish:** search/sort, grid view, bulk select/deselect with individual mark/unmark for delete and export, unit and round-trip tests passing.

At the end of each milestone, summarize what was built, how to run it, and anything that deviated from this spec.

---

## 12. Out of scope (for now)

Cloud sync, multiple users, lending tracking, Goodreads integration, price lookups, Play Store publishing.
