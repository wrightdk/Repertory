# Repertory

A free, open-source Android app for working through filmographies. Pick directors, actors, writers, cinematographers or composers, tick off the films you've seen, and track your progress per person.

**Status:** early development. Nothing to install yet.

- Local-first: your data stays on your device. No accounts, ads, analytics or tracking.
- Film data comes from [TMDB](https://www.themoviedb.org/), the only network service the app talks to.
- Planned for Google Play and F-Droid.

## Building

1. The app's TMDB API key lives in `gradle.properties` as `TMDB_API_KEY`. To use your own key for development, get one from [TMDB](https://www.themoviedb.org/settings/api) and put it in `local.properties` in the project root (git-ignored), which takes precedence:

   ```properties
   TMDB_API_KEY=your_key_here
   ```

2. Alternatively, set a `TMDB_API_KEY` environment variable (also overrides `gradle.properties`).
3. Build and install a debug build on a connected device:

   ```bash
   ./gradlew installDebug
   ```

## Testing

Unit tests run offline against saved TMDB responses in `app/src/test/resources/tmdb/`:

```bash
./gradlew testDebugUnitTest
```

To check that the build's TMDB key works against the real API (the only test that uses the network):

```bash
./gradlew testDebugUnitTest -PtmdbLive --tests '*TmdbLiveKeyTest'
```

## Suggestions

The people offered on the empty Home screen are bundled in `app/src/main/assets/suggestions.json`, so the build never needs the network. To change them, edit the list in `scripts/generate_suggestions.py` and run it (it looks each name up on TMDB with the same key the build uses), then commit the JSON:

```bash
python3 scripts/generate_suggestions.py
```

## Attribution

This application uses TMDB and the TMDB APIs but is not endorsed, certified, or otherwise approved by TMDB.

Bundled third-party assets:

- [Newsreader](https://github.com/productiontype/Newsreader) and [IBM Plex Sans](https://github.com/IBM/plex) fonts, under the SIL Open Font License 1.1 (see `third_party/fonts/`).
- [Material Symbols](https://github.com/google/material-design-icons) icons, under the Apache License 2.0.

## Licence

[GPL-3.0](LICENSE)
