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

Open Library lookups work without a key. The app queries both metadata sources
in parallel and remains usable without network access for saved books.
