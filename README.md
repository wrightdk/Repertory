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

## Attribution

This product uses the TMDB API but is not endorsed or certified by TMDB.

## Licence

[GPL-3.0](LICENSE)
