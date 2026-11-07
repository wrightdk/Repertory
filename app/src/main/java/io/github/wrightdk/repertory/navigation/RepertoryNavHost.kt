package io.github.wrightdk.repertory.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import io.github.wrightdk.repertory.data.local.entity.MediaType
import io.github.wrightdk.repertory.ui.screens.AboutScreen
import io.github.wrightdk.repertory.ui.screens.AddPersonScreen
import io.github.wrightdk.repertory.ui.screens.FilmDetailScreen
import io.github.wrightdk.repertory.ui.screens.FilmographyScreen
import io.github.wrightdk.repertory.ui.screens.HomeScreen
import io.github.wrightdk.repertory.ui.screens.SearchScreen
import io.github.wrightdk.repertory.ui.screens.SettingsScreen

/** A snackbar message for Home, set by the screen that returns there. */
private const val HOME_MESSAGE = "home_message"

@Composable
fun RepertoryNavHost() {
    val navController = rememberNavController()
    val back: () -> Unit = { navController.popBackStack() }

    NavHost(navController = navController, startDestination = HomeRoute) {
        composable<HomeRoute> { entry ->
            val message by entry.savedStateHandle.getStateFlow<String?>(HOME_MESSAGE, null).collectAsStateWithLifecycle()
            HomeScreen(
                message = message,
                onMessageShown = { entry.savedStateHandle[HOME_MESSAGE] = null },
                onAddPerson = { navController.navigate(SearchRoute) },
                onOpenList = { id -> navController.navigate(FilmographyRoute(id)) },
                onSuggestion = { personId -> navController.navigate(AddPersonRoute(personId)) },
                onSettings = { navController.navigate(SettingsRoute) },
            )
        }
        composable<SearchRoute> {
            SearchScreen(
                onBack = back,
                onPersonSelected = { personId -> navController.navigate(AddPersonRoute(personId)) },
            )
        }
        composable<AddPersonRoute> { entry ->
            val route = entry.toRoute<AddPersonRoute>()
            AddPersonScreen(
                personId = route.personId,
                onBack = back,
                onAdded = { trackedPersonId ->
                    // Adding finishes the search flow: drop Search and Add Person
                    // so Back from the filmography returns Home.
                    navController.navigate(FilmographyRoute(trackedPersonId)) {
                        popUpTo(HomeRoute)
                    }
                },
            )
        }
        composable<FilmographyRoute> { entry ->
            val route = entry.toRoute<FilmographyRoute>()
            FilmographyScreen(
                trackedPersonId = route.trackedPersonId,
                onBack = back,
                onFilmSelected = { mediaType, filmId ->
                    navController.navigate(FilmDetailRoute(route.trackedPersonId, mediaType.name, filmId))
                },
                onRemoved = { message ->
                    // Home is always at the bottom of the stack, also when the list was just added.
                    navController.getBackStackEntry<HomeRoute>().savedStateHandle[HOME_MESSAGE] = message
                    navController.popBackStack<HomeRoute>(inclusive = false)
                },
            )
        }
        composable<FilmDetailRoute> { entry ->
            val route = entry.toRoute<FilmDetailRoute>()
            FilmDetailScreen(
                trackedPersonId = route.trackedPersonId,
                mediaType = MediaType.valueOf(route.mediaType),
                filmId = route.filmId,
                onBack = back,
            )
        }
        composable<SettingsRoute> {
            SettingsScreen(onBack = back, onAbout = { navController.navigate(AboutRoute) })
        }
        composable<AboutRoute> {
            AboutScreen(onBack = back)
        }
    }
}
