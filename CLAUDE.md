# Repertory: project scope

(Working name for the filmography tracker. May change before release.)

## What we're building

A free, open-source Android app that lets you pick people from film (directors, actors, writers, cinematographers, composers) and work your way through their filmography, ticking off what you've seen and tracking progress per person.

Film only for now. Books may come later, so keep the data model open to a second media type but don't build for it.

Target: publish on **Google Play** and **F-Droid**. Free, no ads, no tip jar, no affiliate links (keeps TMDB use non-commercial).

App name: **Repertory** (working name). Package / applicationId: `io.github.wrightdk.repertory`.

## Principles

- **Local-first.** All user data lives on the device. No accounts, no backend, no analytics, no crash reporting services.
- **Only network call is to TMDB** for film and person data.
- **F-Droid compatible from day one.** No Google Play Services, Firebase, Crashlytics, or any proprietary SDK. Fully FOSS toolchain. Must build from public source.
- **Open source licence:** GPL-3.0 (decided; see `LICENSE`).

## Stack

- Kotlin, Jetpack Compose, Material 3
- Room (via KSP) for local storage
- Retrofit + OkHttp + kotlinx.serialization for TMDB
- Coil for images
- No DI framework: manual wiring through the Application class
- Gradle with version catalog
- Min SDK: 26 unless there's a reason otherwise

## Dev environment

- Claude Code runs locally on an M1 MacBook Air (16GB) with Android Studio installed
- Test device: Pixel 9, connected via USB or wireless debugging
- Build and install with `./gradlew installDebug`; check the device with `adb devices`
- Android emulator available as a fallback

## Data source: TMDB

- API v3. Key goes in `local.properties` as `TMDB_API_KEY=...` (or the `TMDB_API_KEY` env var), exposed as `BuildConfig.TMDB_API_KEY`. For F-Droid the key will need to be in the build; that's normal for TMDB apps. App should fail gracefully if the key is invalid or rate-limited.
- Useful endpoints:
  - `/search/person`
  - `/person/{id}` and `/person/{id}/combined_credits` (or `movie_credits`)
  - `/movie/{id}`
  - `/configuration` for image base URLs
- Cache fetched person and film data locally so the app works offline once loaded.
- **Attribution (required):** About screen with the TMDB logo and the line "This product uses the TMDB API but is not endorsed or certified by TMDB." TMDB logo less prominent than our own branding.

## v1 features

1. **Search people** by name via TMDB.
2. **Add a person to my list**, choosing which role I'm tracking (Directing, Acting, Writing, Camera, Sound, etc., derived from their credits' departments). The same person can be added under more than one role.
3. **Filmography view** for that person and role:
   - Chronological by release date; unreleased films shown but marked
   - Default filters hide: shorts, TV, documentaries/appearances as "Self", very minor or uncredited roles. Each filter can be toggled back on.
   - Poster, title, year
4. **Mark as seen** with optional date seen, rating, and short note. Seen state is per film, not per person (seeing a film ticks it for every person it appears under).
5. **Progress** per person: "8 of 13 seen", with a progress bar. Progress respects the current filters.
6. **Home screen**: all tracked people with their progress, sortable by name, progress, or recently updated.
7. **About screen** with TMDB attribution, licence, and source link.

## v1.1 (after v1 ships)

- **Letterboxd CSV import.** Letterboxd's data export includes watched films (title, year, Letterboxd URI). Match to TMDB by title + year; show a review screen for unmatched or ambiguous results.
- **Export / import my data** as JSON, so users can back up and move devices.
- **"Next up"**: earliest unseen film for each tracked person, shown on home.

## Later (not now)

- Where to watch (TMDB watch providers, sourced from JustWatch, which has its own attribution requirement)
- Curated "core works" or suggested viewing order
- Books (Wikidata / Open Library)
- iOS

## Data model (starting point)

- `Person` (tmdbId, name, profilePath, cachedAt)
- `TrackedPerson` (personId, department/role, addedAt)
- `Film` (tmdbId, title, releaseDate, posterPath, runtime, isShort, isDocumentary, cachedAt)
- `Credit` (personId, filmId, department, job/character, order)
- `Viewing` (filmId, seenOn nullable, rating nullable, note nullable)
- Filter preferences per TrackedPerson

## Store requirements to plan for

**Google Play**
- One-off developer fee
- New personal accounts: closed test with at least 12 testers opted in continuously for 14 days before production access
- Privacy policy (simple: we collect nothing) and Data Safety form
- Developer name is public; decide name vs pseudonym

**F-Droid**
- Public repo, FOSS licence, builds from source with FOSS toolchain
- Fastlane metadata structure in repo (`fastlane/metadata/android/en-GB/`) for listing text and screenshots
- Expect the NonFreeNet anti-feature label because the app depends on TMDB. This is fine.
- Reproducible builds are a bonus, not required to start

## Build order for the first session

1. Scaffold the project (Compose, Room, networking, DI if wanted) with the licence, README, and `.gitignore` that keeps the API key out of git
2. TMDB client + person search screen
3. Add person / choose role, persist to Room
4. Filmography screen with filters
5. Mark as seen, progress calculation
6. Home screen
7. About / attribution screen

## Open questions

- Final app name (Repertory is the working name)
- Exact default filter rules (what counts as "minor role" for actors: cast order cutoff? exclude voice-only?)
- Own name or pseudonym as developer
