# Book Catalog

A personal Android book catalog. Build and install the debug app with:

```sh
./gradlew :app:assembleDebug
```

To enable Google Books lookups, add an optional API key to the ignored
`local.properties` file in the project root:

```properties
GOOGLE_BOOKS_API_KEY=your_key
```

Open Library and Library of Congress lookups work without a key. The app queries
all metadata sources in parallel and remains usable without network access for
saved books. Any one source can fail without blocking the lookup. Library of
Congress records supply cataloged subjects, summaries, and, when the publisher
provided them, BISAC headings; those headings are used directly instead of
HomeChat mapping. A Library of Congress record is ignored when its title
disagrees with Open Library or Google. Its catalog endpoint is HTTP-only, so the
app allows cleartext traffic to `lx2.loc.gov`.
Titles and subtitles use Chicago-style title capitalization: principal words
are capitalized, short articles/conjunctions/prepositions are lowercased except
at the beginning/end and after a colon, while mixed-case names and known
acronyms are preserved. This is applied to metadata results and when saving edits.

To map lookup subjects to BISAC headings, add the HomeChat OpenAI-
compatible endpoint settings to the same ignored file:

```properties
HOMECHAT_LLM_BASE_URL=http://your-homechat-host:8080/v1
HOMECHAT_LLM_MODEL=your-configured-model-id
HOMECHAT_LLM_API_KEY=your-local-llm-key
```

HomeChat's model runs on the LAN host configured in its `.env` file. The app
sends only the book title, authors, source subjects, and a shortened description
to that local endpoint. It stores the complete BISAC heading, including any
official slash-separated qualifiers. If mapping fails, source subjects are kept
and the edit screen shows a warning. The app allows cleartext traffic only to
the configured LLM host when its URL uses HTTP.
Existing books can be remapped from the edit screen with **Map subjects to BISAC**.
HomeChat also rewrites each looked-up description as 3–4 sentences about the
book's content, dropping marketing such as bestseller lists and review quotes.
When no source has a description, the model is told to search the web unless
the book is a famous classic it knows with certainty, to write only from what
it knows or finds, and to leave the description empty if neither identifies the
book. It sends the ISBN, title, subtitle, authors, publication date, and source
description. Web search uses the Brave Search API, enabled by adding a key to
`local.properties`:

```properties
BRAVE_SEARCH_API_KEY=your-brave-search-key
```

The key is built into the app, and searches go from the phone straight to
Brave. Without a key, the model writes only from its own knowledge. Existing books can be updated from the edit screen with
**Summarize description** (or **Write description** when it is empty).
When taking a cover photo, the app detects a likely book-cover rectangle on-device.
The crop remains adjustable, and low-confidence detection falls back to the manual frame.
