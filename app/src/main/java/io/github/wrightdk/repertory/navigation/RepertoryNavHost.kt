package io.github.wrightdk.repertory.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import io.github.wrightdk.repertory.ui.screens.AboutScreen
import io.github.wrightdk.repertory.ui.screens.AddPersonScreen
import io.github.wrightdk.repertory.ui.screens.FilmDetailScreen
import io.github.wrightdk.repertory.ui.screens.FilmographyScreen
import io.github.wrightdk.repertory.ui.screens.HomeScreen
import io.github.wrightdk.repertory.ui.screens.SearchScreen

// Placeholder ID so Home can open a filmography before the real list exists.
private const val SAMPLE_TRACKED_PERSON_ID = 1L

@Composable
fun RepertoryNavHost() {
    val navController = rememberNavController()
    val back: () -> Unit = { navController.popBackStack() }

    NavHost(navController = navController, startDestination = HomeRoute) {
        composable<HomeRoute> {
            HomeScreen(
                onSearch = { navController.navigate(SearchRoute) },
                onOpenFilmography = { navController.navigate(FilmographyRoute(SAMPLE_TRACKED_PERSON_ID)) },
                onAbout = { navController.navigate(AboutRoute) },
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
                onFilmSelected = { filmId -> navController.navigate(FilmDetailRoute(filmId)) },
            )
        }
        composable<FilmDetailRoute> { entry ->
            val route = entry.toRoute<FilmDetailRoute>()
            FilmDetailScreen(filmId = route.filmId, onBack = back)
        }
        composable<AboutRoute> {
            AboutScreen(onBack = back)
        }
    }
}
