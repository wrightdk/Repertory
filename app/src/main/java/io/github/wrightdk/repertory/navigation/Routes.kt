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

@Serializable
data class FilmDetailRoute(val filmId: Int)

@Serializable
data object AboutRoute

@Serializable
data object SettingsRoute
