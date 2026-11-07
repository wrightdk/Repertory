package io.github.wrightdk.repertory.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
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
import io.github.wrightdk.repertory.ui.screens.LicenceKind
import io.github.wrightdk.repertory.ui.screens.LicenceScreen
import io.github.wrightdk.repertory.ui.screens.SearchScreen
import io.github.wrightdk.repertory.ui.screens.SettingsScreen

/** A snackbar message for Home, set by the screen that returns there. */
private const val HOME_MESSAGE = "home_message"

/**
 * Runs [block] only while this screen is the resumed one. A second tap during
 * the transition (a double-tapped back arrow or row) would otherwise act on the
 * stack twice, e.g. popping Home and leaving a blank screen.
 */
private inline fun NavBackStackEntry.ifResumed(block: () -> Unit) {
    if (lifecycle.currentState == Lifecycle.State.RESUMED) block()
}

@Composable
fun RepertoryNavHost() {
    val navController = rememberNavController()
    val back: NavBackStackEntry.() -> Unit = { ifResumed { navController.popBackStack() } }
    // From a 401/429 error: Settings with the own-key dialog open. Back returns to the error to try again.
    val openApiKeySetting: NavBackStackEntry.() -> Unit = { ifResumed { navController.navigate(SettingsRoute(editApiKey = true)) } }

    NavHost(navController = navController, startDestination = HomeRoute) {
        composable<HomeRoute> { entry ->
            val message by entry.savedStateHandle.getStateFlow<String?>(HOME_MESSAGE, null).collectAsStateWithLifecycle()
            HomeScreen(
                message = message,
                onMessageShown = { entry.savedStateHandle[HOME_MESSAGE] = null },
                onAddPerson = { entry.ifResumed { navController.navigate(SearchRoute) } },
                onOpenList = { id -> entry.ifResumed { navController.navigate(FilmographyRoute(id)) } },
                onSuggestion = { personId -> entry.ifResumed { navController.navigate(AddPersonRoute(personId)) } },
                onSettings = { entry.ifResumed { navController.navigate(SettingsRoute()) } },
            )
        }
        composable<SearchRoute> { entry ->
            SearchScreen(
                onBack = { entry.back() },
                onPersonSelected = { personId -> entry.ifResumed { navController.navigate(AddPersonRoute(personId)) } },
                onOpenApiKeySetting = { entry.openApiKeySetting() },
            )
        }
        composable<AddPersonRoute> { entry ->
            val route = entry.toRoute<AddPersonRoute>()
            AddPersonScreen(
                personId = route.personId,
                onBack = { entry.back() },
                onAdded = { trackedPersonId ->
                    // Adding finishes the search flow: drop Search and Add Person
                    // so Back from the filmography returns Home.
                    navController.navigate(FilmographyRoute(trackedPersonId)) {
                        popUpTo(HomeRoute)
                    }
                },
                onOpenApiKeySetting = { entry.openApiKeySetting() },
            )
        }
        composable<FilmographyRoute> { entry ->
            val route = entry.toRoute<FilmographyRoute>()
            FilmographyScreen(
                trackedPersonId = route.trackedPersonId,
                onBack = { entry.back() },
                onFilmSelected = { mediaType, filmId ->
                    entry.ifResumed { navController.navigate(FilmDetailRoute(route.trackedPersonId, mediaType.name, filmId)) }
                },
                onRemoved = { message ->
                    // Home is always at the bottom of the stack, also when the list was just added.
                    navController.getBackStackEntry<HomeRoute>().savedStateHandle[HOME_MESSAGE] = message
                    navController.popBackStack<HomeRoute>(inclusive = false)
                },
                onOpenApiKeySetting = { entry.openApiKeySetting() },
            )
        }
        composable<FilmDetailRoute> { entry ->
            val route = entry.toRoute<FilmDetailRoute>()
            FilmDetailScreen(
                trackedPersonId = route.trackedPersonId,
                mediaType = MediaType.valueOf(route.mediaType),
                filmId = route.filmId,
                onBack = { entry.back() },
            )
        }
        composable<SettingsRoute> { entry ->
            SettingsScreen(
                onBack = { entry.back() },
                onAbout = { entry.ifResumed { navController.navigate(AboutRoute) } },
                openApiKeyDialog = entry.toRoute<SettingsRoute>().editApiKey,
            )
        }
        composable<AboutRoute> { entry ->
            AboutScreen(onBack = { entry.back() }, onOpenLicence = { kind -> entry.ifResumed { navController.navigate(LicenceRoute(kind.name)) } })
        }
        composable<LicenceRoute> { entry ->
            LicenceScreen(kind = LicenceKind.valueOf(entry.toRoute<LicenceRoute>().kind), onBack = { entry.back() })
        }
    }
}
