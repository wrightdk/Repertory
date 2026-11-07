package io.github.wrightdk.repertory.navigation

import kotlinx.serialization.Serializable

@Serializable
data object HomeRoute

@Serializable
data object SearchRoute

/** Choose which role to track for a person found in search. */
@Serializable
data class AddPersonRoute(val personId: Int)

@Serializable
data class FilmographyRoute(val trackedPersonId: Long)

/**
 * One film, opened from the list [trackedPersonId] (for its credit line).
 * [mediaType] is a [io.github.wrightdk.repertory.data.local.entity.MediaType] name.
 */
@Serializable
data class FilmDetailRoute(val trackedPersonId: Long, val mediaType: String, val filmId: Int)

@Serializable
data object AboutRoute

/** A licence's full text; [kind] is a [io.github.wrightdk.repertory.ui.screens.LicenceKind] name. */
@Serializable
data class LicenceRoute(val kind: String)

/** Settings; with [editApiKey], opens straight onto the own TMDB key dialog (from a key error). */
@Serializable
data class SettingsRoute(val editApiKey: Boolean = false)
