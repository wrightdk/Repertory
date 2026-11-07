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
- **Attribution (required):** About screen with the TMDB logo and the line "This application uses TMDB and the TMDB APIs but is not endorsed, certified, or otherwise approved by TMDB." TMDB logo less prominent than our own branding.

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
   - Sort chips: Name (by surname, accents ignored, then role), Progress (furthest along first, lists still loading last), Recent (last updated first). With Recent, the role line adds when it was last updated ("Director, 2 days ago"). The chosen sort is remembered between launches (`SettingsStore.homeSort`).
   - One row per list: photo, name, role label, "X of Y seen" (or "All Y seen"), thin progress bar. A person on the list twice gets two rows.
   - A list whose film details are still loading (see Filmography) shows its count and progress bar in a loading state, never a provisional number (`TmdbRepository.observeListIdsMissingDetails`).
   - "Add person" extended FAB opens search.
   - **First launch** (nothing on the list): "Nobody on your list yet." with a short line of explanation, a "Find someone" button, and a "Suggestions" row of chips. Four chips, picked at random (at most two from one department); tapping one opens Add to your list for that person. Suggestions come from `app/src/main/assets/suggestions.json`, ~100 well-known directors, actors, cinematographers, writers and composers (TMDB ID, name, department only, no credits). It's generated by `scripts/generate_suggestions.py`, run manually and committed; the app build never needs network access.
2. **Search** (TMDB `/search/person`)
   - Search field in the top bar with clear button. Results label ("3 matches"), then rows with photo, name, and known-for department as a role label. People already on the list get an "On list" badge.
   - No results: "No one called “x”." plus "Check the spelling, or try just the surname."
   - Tapping a result opens Add to your list.
3. **Add to your list**
   - Person header: photo, name, life dates ("1928–1999" or "b. 1983").
   - "Track as" with "Each role is its own list with its own progress." Then one checkbox row per department from their credits, with a credit count ("12 credits as Writer"). Only the seven departments with a role label (see Design) are offered; TMDB's others (Crew, Art, Visual Effects…) aren't. The known-for department is pre-ticked unless already tracked.
   - Roles already on the list show a bookmark icon and "Already on your list" in place of a checkbox.
   - Credit counts match what the list will show by default: they apply the global filter defaults from Settings (see Filter rules for the one gap, shorts), and a department whose count drops to zero isn't offered (`TmdbRepository.roles`).
   - Bottom button: "Add 1 list" / "Add N lists", or a disabled "Choose a role" when nothing is ticked. Adding opens the filmography of the first list added (decided: kept over the mockup's return to home with a snackbar); Back from there goes home.
4. **Filmography** (one list)
   - Header: back, "On your list" button (opens the remove dialog), photo, name, role and life dates ("Director, 1928–1999", "Actor, born 1933").
   - Big "X of Y" count with the percentage on the right.
   - Segmented progress bar: one cell per shown film in chronological order, filled green when seen, outlined when not, wrapping onto more rows for long filmographies. Unreleased films are a dashed ochre cell and don't count.
   - Filter summary line that opens the filter sheet: "Hiding shorts", "Hiding TV, appearances as self and minor roles", "Showing everything", prefixed "Custom list: " when the list overrides the defaults.
   - Films in chronological order under decade headers ("1950s"), with the year in a left column, then poster, title and a meta line: runtime for crew roles ("85 min"), "as Character" for acting. Non-feature categories, when shown, prefix the meta line ("Short", "Documentary", "Minor role").
   - Seen films show "Seen 11 Mar 2022", or just "Seen" with no date. The circle on the right ticks a film straight from the list (dated today) and shows a snackbar ("Ticked off Lolita, 10 of 13 seen"). Tapping the row opens film detail.
   - Unreleased films sit inline in date order with an "Unreleased, due 26 Nov 2026" label and a dashed circle that can't be ticked. No separate upcoming section.
   - First open of a list fetches from TMDB ("Fetching filmography from TMDB…" with skeleton rows), then it's cached.
   - The count must be final when first shown. Adding a list fetches every film's details (runtime, status) in the background (`FilmDetailsSync`: rate-limited, retried, resumed at app start for lists that never finished). Until they're all in, the count, tally and filter line show a loading state; if the fetch stops, an error with "Try again".
5. **Remove from your list**: dialog "Remove Michael Caine as Actor from your list?" with "Films you’ve ticked stay saved and will show again if you add them back." and Cancel / Remove, then home with a snackbar ("Removed Michael Caine as Actor"). The mockup's body copy ("will be lost") is wrong and not used (see Core concepts). Removing deletes only the `TrackedPerson` row; the list's own filter override goes with it.
6. **Filters**
   - Categories: Shorts (40 minutes or less), TV (series, TV films and specials), Documentaries (non-fiction features), Appearances as self (interviews, making-ofs, talk shows), Minor roles (small or uncredited parts). Each switch means "show".
   - Rules are in "Filter rules" below.
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
   - "Use your own TMDB API key" item (not in the design; see TMDB section), styled like the About row with an on/off line ("On. Key ending 1a2b."). Its dialog checks the key with TMDB before saving (`AppContainer.checkTmdbApiKey`) and catches a pasted v4 read access token; Remove goes back to the built-in key. Saving or removing retries any list whose details fetch stopped.
   - Key errors (401, or 429 after retries) on Search, Add to your list and Filmography say so plainly and offer "Use your own key" ("Change your key" once one is set), which opens Settings with the key dialog up (`ui/screens/TmdbErrors.kt`).
   - About row.
9. **About**: app icon (drawn from the launcher icon's adaptive layers) and name, version, "A notebook for working through filmographies. No accounts, no ads, no tracking. Your list is kept on this phone and never sent to us.", Licence (GNU General Public License v3.0), Fonts (Newsreader and IBM Plex Sans, SIL OFL 1.1), Source code link, and a "Film data" section with the TMDB logo (`res/drawable/tmdb_logo.xml`, TMDB's official blue short logo, unaltered) and attribution line.
   - Licence and Fonts open the full text. The texts are copied from `LICENSE` and `third_party/fonts/*/OFL.txt` into assets at build time (`copyLicenceTexts` in `app/build.gradle.kts`), so there's one copy of each.

## Filter rules (agreed)

All in one place in code (`data/FilmFilters.kt`) and used everywhere a count or list is built.

- **Per film, per list.** A film's categories come from the film plus this person's credits on it in the list's department. A film can be in several categories, and it's hidden if **any** of its categories is switched off. So with the defaults a TV documentary is hidden (TV off) even though Documentaries is on.
- **Hidden means gone:** out of the rows, the ledger, "X of Y" and the percentage. Canceled films never appear. Unreleased films appear but never count.
- **Shorts:** TMDB runtime of 40 minutes or less (the Academy's definition). Unknown runtime (no details yet, or TMDB sends 0/none) is not a short: when in doubt, show.
- **TV:** a TV series credit (including miniseries and talk shows), or a movie with TMDB's "TV Movie" genre (10770). TMDB has no "special" type; specials are usually tagged TV Movie, and untagged ones show as films.
- **Documentaries:** TMDB's "Documentary" genre (99).
- **Appearances as self (acting lists only):** a cast credit whose character starts with Self / Himself / Herself / Themselves / Themself, with any suffix ("Self - Host", "Self (archive footage)", "Self / Professor Brand"), or whose character is the person's own name (ignoring a trailing parenthetical). The film counts as self only if **every** credit of theirs on it is self. Never applies to crew.
- **Minor roles (acting lists only):** a non-self cast credit that is any of:
  1. uncredited: character contains "(uncredited)", whatever the billing;
  2. archive footage only: character contains "(archive footage)";
  3. billed deep: TMDB cast order of 20 or more (21st billed or lower).
  Voice roles aren't minor in themselves; they go through the same checks. No order or a blank character isn't minor unless 1 or 2 applies. The film counts as minor only if **every** credit of theirs on it is minor. Never applies to crew.
  The cutoff of 20 was checked against Caine, Oldman and Damon: 15 would hide real supporting parts (Sirius Black at 16 and 19), 20 still catches cameos (Truman in *Oppenheimer* at 77).
- **Sheet counts** ("3 in this list") are every film in the list in that category, shown or not, unreleased included, canceled not. A film in several categories counts under each.
- **Summary line** names only hidden categories that have films in this list; with none, "Showing everything". "Custom list: " prefix when the list has an override.
- **Overrides** store all five switches. One that ends up equal to the defaults is cleared. Changing the defaults later doesn't touch lists with an override.
- **Add to your list counts** apply the global defaults. Runtimes aren't known until a list is added, so shorts are only left out for films whose details are already cached; the count can drop a little once the list loads. Accepted rather than fetching every film's details up front.

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
- Own name or pseudonym as developer
