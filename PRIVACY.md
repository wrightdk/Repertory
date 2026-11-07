# Privacy policy

Last updated: 7 November 2026

Repertory is a free, open-source Android app for working through the filmographies of people from film. This page explains what it does with your data. The short version: it collects nothing.

## What we collect

Nothing. There is no server run by the developer, no account to create, and no way for the developer to see anything you do in the app.

The app has no analytics, no advertising, no crash reporting and no tracking of any kind. It doesn't use Google Play Services, Firebase or any other third-party SDK that gathers data.

## What stays on your phone

The people you track, the films you've ticked as seen, the dates you've given them, your filter choices and your settings are all stored on your phone and nowhere else. Removing the app removes this data, unless you've made a backup (see below).

## What the app sends over the internet

The app contacts one service: [TMDB](https://www.themoviedb.org) (The Movie Database). It uses TMDB to search for people and to fetch their filmographies, film details, posters and photos, and "Where to watch" information. Posters and photos are loaded from TMDB's image servers.

What TMDB can see is what any website or app can see when it makes a request: your IP address, and the search terms or the person and film IDs the app asks for. The app doesn't send TMDB your list, your ticks, your settings or any identifier of yours. It doesn't send your country either. For "Where to watch", TMDB returns data for every country and the app picks out yours on the phone.

TMDB has its own privacy policy, which applies to those requests: <https://www.themoviedb.org/privacy-policy>. "Where to watch" data comes from JustWatch via TMDB. The app doesn't contact JustWatch itself. The JustWatch logo is bundled in the app, and the attribution link only opens JustWatch's website if you tap it.

The app also won't contact anyone else. The "Source code" and attribution links open in your browser only when you tap them.

## Your own TMDB API key

In Settings you can enter your own TMDB API key. If you do, it's stored on your phone and used in place of the app's built-in key when talking to TMDB. It isn't sent anywhere else, it isn't included in the backup file described below, and it's excluded from Android's automatic backup.

## Backing up and restoring your data

Settings lets you export your data to a JSON file and restore it from one. You choose where the file is saved and where it's restored from, using Android's file picker. The app doesn't upload it. What happens to the file after that is up to you.

The file contains your tracked lists, ticked films and dates, filter settings, theme, home sort order and chosen country. It doesn't contain your API key.

## Android's own backup

The app allows Android's standard backup, so if you have device backup switched on (for example Google's backup to your Google account) or you move to a new phone with Android's transfer tool, your list and ticks can be included. That backup is handled by Android and your backup provider under their own privacy terms. The developer has no access to it. You can turn it off in your phone's system settings. Your own API key is excluded from it.

## Children

The app doesn't collect personal information from anyone, including children.

## Changes

If this policy changes, the new version will be committed to the project's repository with an updated date at the top. The full history is in the repository.

## Contact

Questions can go in an issue at <https://github.com/wrightdk/Repertory/issues>.
