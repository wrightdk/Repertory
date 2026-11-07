package io.github.wrightdk.repertory.navigation

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.currentStateAsState
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
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

/**
 * Screen changes use Material's shared axis X: both screens slide a short way
 * while the old one fades out fast and the new one fades in after it, so the
 * two never show through each other. The library default (a 700 ms fade) made
 * Back feel sluggish. The predictive back gesture runs the same motion.
 */
private const val TRANSITION_MS = 300
private const val FADE_OUT_MS = 90

/** Material's spec is 30 dp; a little more makes the direction read on a phone. */
private val SLIDE_DISTANCE = 48.dp

// Material 3 easing curves (cubic approximations of the emphasized set).
private val Emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)
private val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
private val EmphasizedAccelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

private fun enter(offset: Int) =
    slideInHorizontally(tween(TRANSITION_MS, easing = Emphasized)) { offset } +
        fadeIn(tween(TRANSITION_MS - FADE_OUT_MS, delayMillis = FADE_OUT_MS, easing = EmphasizedDecelerate))

private fun exit(offset: Int) =
    slideOutHorizontally(tween(TRANSITION_MS, easing = Emphasized)) { offset } +
        fadeOut(tween(FADE_OUT_MS, easing = EmphasizedAccelerate))

/**
 * A destination whose screen ignores touches while it isn't the resumed one,
 * i.e. while it's fading in or out. Without this, a tap just after Back lands on
 * the screen that's leaving (it's drawn on top), e.g. Home's settings icon
 * hitting the filmography's "On your list" button and opening the remove dialog.
 */
private inline fun <reified T : Any> NavGraphBuilder.screen(
    noinline content: @Composable (NavBackStackEntry) -> Unit,
) {
    composable<T> { entry ->
        val state by entry.lifecycle.currentStateAsState()
        Box {
            content(entry)
            if (!state.isAtLeast(Lifecycle.State.RESUMED)) {
                Box(
                    Modifier
                        .matchParentSize()
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                while (true) {
                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                    event.changes.forEach { it.consume() }
                                    if (event.changes.none { it.pressed }) break
                                }
                            }
                        },
                )
            }
        }
    }
}

@Composable
fun RepertoryNavHost() {
    val navController = rememberNavController()
    val back: NavBackStackEntry.() -> Unit = { ifResumed { navController.popBackStack() } }
    // From a 401/429 error: Settings with the own-key dialog open. Back returns to the error to try again.
    val openApiKeySetting: NavBackStackEntry.() -> Unit = { ifResumed { navController.navigate(SettingsRoute(editApiKey = true)) } }

    val slide = with(LocalDensity.current) { SLIDE_DISTANCE.roundToPx() }

    NavHost(
        navController = navController,
        startDestination = HomeRoute,
        // Forward: the new screen comes in from the right, the old one moves left.
        // Back (button or gesture): the reverse.
        enterTransition = { enter(slide) },
        exitTransition = { exit(-slide) },
        popEnterTransition = { enter(-slide) },
        popExitTransition = { exit(slide) },
        predictivePopEnterTransition = { enter(-slide) },
        predictivePopExitTransition = { exit(slide) },
    ) {
        screen<HomeRoute> { entry ->
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
        screen<SearchRoute> { entry ->
            SearchScreen(
                onBack = { entry.back() },
                onPersonSelected = { personId -> entry.ifResumed { navController.navigate(AddPersonRoute(personId)) } },
                onOpenApiKeySetting = { entry.openApiKeySetting() },
            )
        }
        screen<AddPersonRoute> { entry ->
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
        screen<FilmographyRoute> { entry ->
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
        screen<FilmDetailRoute> { entry ->
            val route = entry.toRoute<FilmDetailRoute>()
            FilmDetailScreen(
                trackedPersonId = route.trackedPersonId,
                mediaType = MediaType.valueOf(route.mediaType),
                filmId = route.filmId,
                onBack = { entry.back() },
            )
        }
        screen<SettingsRoute> { entry ->
            SettingsScreen(
                onBack = { entry.back() },
                onAbout = { entry.ifResumed { navController.navigate(AboutRoute) } },
                openApiKeyDialog = entry.toRoute<SettingsRoute>().editApiKey,
            )
        }
        screen<AboutRoute> { entry ->
            AboutScreen(onBack = { entry.back() }, onOpenLicence = { kind -> entry.ifResumed { navController.navigate(LicenceRoute(kind.name)) } })
        }
        screen<LicenceRoute> { entry ->
            LicenceScreen(kind = LicenceKind.valueOf(entry.toRoute<LicenceRoute>().kind), onBack = { entry.back() })
        }
    }
}
