# Repertory: project scope

(Working name for the filmography tracker. May change before release.)

## What we're building

A free, open-source Android app that lets you pick people from film (directors, actors, writers, cinematographers, composers) and work your way through their filmography, ticking off what you've seen and tracking progress per person and role.

Film only for now. Books may come later, so keep the data model open to a second media type but don't build for it.

Target: publish on **Google Play** and **F-Droid**. Free, no ads, no tip jar, no affiliate links (keeps TMDB use non-commercial).

App name: **Repertory** (working name). Package / applicationId: `io.github.wrightdk.repertory`.

## Principles

- **Local-first.** All user data lives on the device. No accounts, no backend, no analytics, no crash reporting services.
- **Only network call is to TMDB** for film and person data.
- **F-Droid compatible from day one.** No Google Play Services, Firebase, Crashlytics, or any proprietary SDK. Fully FOSS toolchain. Must build from public source.
- **No Google runtime dependencies.** That includes downloadable Google Fonts (the font provider goes through Play Services). Fonts are bundled in `res/font` (Newsreader, IBM Plex Sans; both OFL).
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

- API v3.
- **API key handling (decided):**
  - Use a TMDB key registered specifically for this app, not a personal one.
  - The key is committed to the repo in `gradle.properties` as `TMDB_API_KEY`. It is effectively public: F-Droid builds from public source with no secret store, and any key shipped in an APK can be extracted anyway.
  - `local.properties` may override it for development. Resolution order in `build.gradle.kts`: `local.properties` → environment variable `TMDB_API_KEY` → `gradle.properties`. Exposed via `BuildConfig.TMDB_API_KEY`.
  - Settings has an optional "Use my own TMDB API key" field. If set, it overrides the built-in key at runtime (stored locally). This keeps the app working if the built-in key is ever revoked, without waiting for a new release.
  - On 401 or repeated 429 responses, show a clear message explaining the data service is unavailable and pointing to the own-key setting. Never crash.
  - Cache aggressively so normal use makes few API calls.
- Useful endpoints:
  - `/search/person`
  - `/person/{id}` and `/person/{id}/combined_credits` (or `movie_credits`)
  - `/movie/{id}`
  - `/configuration` for image base URLs
- Cache fetched person and film data locally so the app works offline once loaded.
- **Attribution (required):** About screen with the TMDB logo and the line "This product uses the TMDB API but is not endorsed or certified by TMDB." TMDB logo less prominent than our own branding.

## Design

The mockups in `design/` are the source of truth for v1. `screens.html` has the exact values (colours, type, spacing, copy); the PNGs show each screen in light and dark. The folder is kept locally and not committed. The Compose theme in `ui/theme` already carries the colour tokens and type.

- Direction: a notebook and a repertory programme. Warm paper (warm charcoal in dark), ink-black type, hairline rules, years in a column like a screening schedule. Newsreader for names and titles, IBM Plex Sans for everything else, sentence case throughout.
- A muted green is the only accent and means "seen": ticks, progress, the tally. Unseen films keep full-ink titles. Ochre with dashed outlines marks unreleased films.
- Role labels: Directing → Director, Acting → Actor, Writing → Writer, Camera → Cinematographer, Sound → Composer, Production → Producer, Editing → Editor.

## Core concepts

- A **list** is one person tracked in one role (department). The same person can be on the list more than once, once per role, and each has its own progress.
- **Seen state belongs to the film, not the list.** Ticking a film counts on every list that contains it.
- **Removing a list only removes the list.** Ticked films stay saved, still count on any other list they're in, and show as seen again if the person is added back.
- **Hidden films don't count towards progress.** Progress counts released films that the list's current filters show. Unreleased films never count.
- **No ratings or notes** anywhere in the app. A viewing is "seen" plus an optional date.

## v1 features

1. **Home** ("Your list")
   - Top bar: app name, search icon (searches within your list by name or role), settings icon.
   - Sort chips: Name (by surname, then role), Progress, Recent. With Recent, the role line adds when it was last updated ("Director, 2 days ago").
   - One row per list: photo, name, role label, "X of Y seen" (or "All Y seen"), thin progress bar. A person on the list twice gets two rows.
   - "Add person" extended FAB opens search.
   - **First launch** (nothing on the list): "Nobody on your list yet." with a short line of explanation, a "Find someone" button, and a "Suggestions" row of chips. Tapping a chip opens search for that name. Suggestions come from a bundled JSON of ~100 well-known directors and actors (TMDB ID, name, department only, no credits). It's generated by a script in `/scripts`, run manually and committed; the app build never needs network access.
2. **Search** (TMDB `/search/person`)
   - Search field in the top bar with clear button. Results label ("3 matches"), then rows with photo, name, and known-for department as a role label. People already on the list get an "On list" badge.
   - No results: "No one called “x”." plus "Check the spelling, or try just the surname."
   - Tapping a result opens Add to your list.
3. **Add to your list**
   - Person header: photo, name, life dates ("1928–1999" or "b. 1983").
   - "Track as" with "Each role is its own list with its own progress." Then one checkbox row per department from their credits, with a credit count ("12 credits as Writer"). The known-for department is pre-ticked unless already tracked.
   - Roles already on the list show a bookmark icon and "Already on your list" in place of a checkbox.
   - Bottom button: "Add 1 list" / "Add N lists", or a disabled "Choose a role" when nothing is ticked. Adding returns home with a snackbar ("Added Stanley Kubrick as Writer").
4. **Filmography** (one list)
   - Header: back, "On your list" button (opens the remove dialog), photo, name, role and life dates ("Director, 1928–1999", "Actor, born 1933").
   - Big "X of Y" count with the percentage on the right.
   - Segmented progress bar: one cell per shown film in chronological order, filled green when seen, outlined when not, wrapping onto more rows for long filmographies. Unreleased films are a dashed ochre cell and don't count.
   - Filter summary line that opens the filter sheet: "Hiding shorts", "Hiding TV, appearances as self and minor roles", "Showing everything", prefixed "Custom list: " when the list overrides the defaults.
   - Films in chronological order under decade headers ("1950s"), with the year in a left column, then poster, title and a meta line: runtime for crew roles ("85 min"), "as Character" for acting. Non-feature categories, when shown, prefix the meta line ("Short", "Documentary", "Minor role").
   - Seen films show "Seen 11 Mar 2022", or just "Seen" with no date. The circle on the right ticks a film straight from the list (dated today) and shows a snackbar ("Ticked off Lolita, 10 of 13 seen"). Tapping the row opens film detail.
   - Unreleased films sit inline in date order with an "Unreleased, due 26 Nov 2026" label and a dashed circle that can't be ticked. No separate upcoming section.
   - First open of a list fetches from TMDB ("Fetching filmography from TMDB…" with skeleton rows), then it's cached.
5. **Remove from your list**: dialog "Remove Michael Caine as Actor from your list?" with Cancel / Remove, then home with a snackbar ("Removed Michael Caine as Actor"). The mockup's body copy says ticked films "will be lost"; that's wrong. The copy should say ticked films stay saved (see Core concepts).
6. **Filters**
   - Categories: Shorts (under 40 minutes), TV (series, TV films and specials), Documentaries (non-fiction features), Appearances as self (interviews, making-ofs, talk shows), Minor roles (small or uncredited parts). Each switch means "show".
   - Global defaults live in Settings. Out of the box everything is hidden except Documentaries.
   - Per-list overrides in a "Show in this list" bottom sheet: "Changes apply to this list only. Hidden films don't count towards your progress." Each switch shows a count ("3 in this list" / "None in this list"). "Use defaults" (shown only when the list is customised) and "Done". An override that ends up matching the defaults goes back to following them.
7. **Film detail**
   - Poster, "1968, 149 min", title, credit line for the list it was opened from ("Directed by Stanley Kubrick", "Written by …", "Michael Caine as Harry Palmer").
   - One Seen toggle ("Mark as seen" / "Seen"). When seen, an optional "Date seen" field with a date picker and a "Today" shortcut. Ticking fills in today; the date can be changed or cleared.
   - Unreleased films have no toggle, just "Not out yet. Due [date]. You can tick it off once it's released."
   - "Counts toward": every tracked list the film is in, with that list's progress ("Stanley Kubrick as Director, 9 of 13").
8. **Settings**
   - "Show in lists": the filter defaults.
   - Theme: Light / Dark / System segmented button.
   - "Use your own TMDB API key" item (not in the design; see TMDB section). Style it like the other rows.
   - About row.
9. **About**: app icon and name, version, "A notebook for working through filmographies. No accounts, no ads, no tracking. Your list stays on this phone.", Licence (GNU General Public License v3.0), Source code link, and a "Film data" section with the TMDB logo and attribution line.

## v1.1 (after v1 ships)

- **Letterboxd CSV import.** Letterboxd's data export includes watched films (title, year, Letterboxd URI). Match to TMDB by title + year; show a review screen for unmatched or ambiguous results.
- **Export / import my data** as JSON, so users can back up and move devices.
- **"Next up"**: earliest unseen film for each tracked person, shown on home.
- **Where to watch** on the film page: TMDB watch providers for GB (streaming, rent, buy). Must credit JustWatch as the data source with their logo. Cache for about a day only.

## Later (not now)

- Curated "core works" or suggested viewing order
- Books (Wikidata / Open Library)
- iOS

## Data model (starting point)

- `Person` (tmdbId, name, profilePath, cachedAt)
- `TrackedPerson` (personId, department/role, addedAt)
- `Film` (tmdbId, title, releaseDate, posterPath, runtime, isShort, isTv, isDocumentary, cachedAt)
- `Credit` (personId, filmId, department, job/character, order); "appearance as self" and "minor role" are per credit
- `Viewing` (filmId, seenOn nullable). Keyed by film only and never deleted when a TrackedPerson is removed.
- Filter defaults (global, Settings) plus an optional override per TrackedPerson

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
7. Settings, About / attribution screen

## Open questions

- Final app name (Repertory is the working name)
- What counts as a "minor role" for actors (cast order cutoff? exclude voice-only?). Which categories are hidden by default is decided (see Filters).
- Own name or pseudonym as developer
