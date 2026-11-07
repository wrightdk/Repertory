package io.github.wrightdk.repertory.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.wrightdk.repertory.AppContainer
import io.github.wrightdk.repertory.data.RoleSummary
import io.github.wrightdk.repertory.data.local.entity.ImageConfig
import io.github.wrightdk.repertory.data.local.entity.Person
import io.github.wrightdk.repertory.data.remote.TmdbError
import io.github.wrightdk.repertory.data.remote.TmdbResult
import io.github.wrightdk.repertory.ui.creditJobLabel
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AddPersonHeader(
    val name: String,
    /** "1928–1999", "b. 1983", or null when TMDB has no dates. */
    val lifeDates: String?,
    val photoUrl: String?,
)

/** One "Track as" row. */
data class RoleRow(
    /** TMDB department, shown as the row title ("Writing"). */
    val department: String,
    val creditCount: Int,
    /** "Writer", for "12 credits as Writer". */
    val jobLabel: String,
    /** Already a list: shown with a bookmark and can't be ticked. */
    val tracked: Boolean,
    val checked: Boolean,
)

sealed interface AddPersonContent {
    /** First fetch from TMDB, nothing cached yet. */
    data object Loading : AddPersonContent

    /** Nothing cached and TMDB couldn't be reached. */
    data class Error(val error: TmdbError) : AddPersonContent

    data class Loaded(val header: AddPersonHeader, val roles: List<RoleRow>) : AddPersonContent {
        /** Ticked rows, in display order. */
        val selectedCount: Int get() = roles.count { it.checked }
    }
}

data class AddPersonUiState(
    val content: AddPersonContent = AddPersonContent.Loading,
    /** Adding is in progress; the button ignores further taps. */
    val saving: Boolean = false,
)

/**
 * Add to your list: pick which of a person's departments to track. Loading
 * fetches the person and their credits from TMDB into Room (or uses the cache),
 * so by the time lists are added, the person, films and credits are all stored.
 */
class AddPersonViewModel(
    private val personId: Int,
    private val refreshPerson: suspend (personId: Int) -> TmdbResult<Unit>,
    private val person: Flow<Person?>,
    private val roles: suspend (personId: Int) -> List<RoleSummary>,
    trackedDepartments: Flow<List<String>>,
    private val imageConfig: suspend () -> ImageConfig,
    private val track: suspend (personId: Int, departments: List<String>) -> List<Long>,
) : ViewModel() {

    private sealed interface Load {
        data object Loading : Load
        data class Failed(val error: TmdbError) : Load
        data class Done(val roles: List<RoleSummary>) : Load
    }

    private val load = MutableStateFlow<Load>(Load.Loading)

    /** Ticked departments; null until the first load picks the default. */
    private val selection = MutableStateFlow<Set<String>?>(null)
    private val saving = MutableStateFlow(false)
    private val images = MutableStateFlow(ImageConfig.DEFAULT)
    private val tracked = trackedDepartments.map { it.toSet() }

    private val _addedListId = MutableStateFlow<Long?>(null)

    /** Set once lists have been added: the id of the first one, for opening its filmography. */
    val addedListId: StateFlow<Long?> = _addedListId.asStateFlow()

    val uiState: StateFlow<AddPersonUiState> =
        combine(load, person, tracked, selection, combine(images, saving, ::Pair)) { l, p, t, sel, (config, isSaving) ->
            AddPersonUiState(content = content(l, p, t, sel.orEmpty(), config), saving = isSaving)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AddPersonUiState())

    init {
        viewModelScope.launch { images.value = imageConfig() }
        reload()
    }

    fun retry() = reload()

    fun toggle(department: String) {
        val s = uiState.value.content as? AddPersonContent.Loaded ?: return
        val row = s.roles.firstOrNull { it.department == department } ?: return
        if (row.tracked) return
        selection.update { cur ->
            val set = cur.orEmpty()
            if (department in set) set - department else set + department
        }
    }

    fun add() {
        val content = uiState.value.content as? AddPersonContent.Loaded ?: return
        if (saving.value || _addedListId.value != null) return
        val departments = content.roles.filter { it.checked }.map { it.department }
        if (departments.isEmpty()) return
        saving.value = true
        viewModelScope.launch {
            try {
                _addedListId.value = track(personId, departments).first()
            } finally {
                saving.value = false
            }
        }
    }

    private fun reload() {
        load.value = Load.Loading
        viewModelScope.launch {
            val refreshed = refreshPerson(personId)
            val cached = roles(personId)
            load.value = when {
                // Stale or partial cache beats an error: the user can still add from it.
                refreshed is TmdbResult.Failure && cached.isEmpty() -> Load.Failed(refreshed.error)
                else -> Load.Done(cached)
            }
            if (selection.value == null && load.value is Load.Done) {
                val known = person.first()?.knownForDepartment
                val alreadyTracked = tracked.first()
                selection.value = setOfNotNull(
                    known?.takeIf { k -> k !in alreadyTracked && cached.any { it.department == k } },
                )
            }
        }
    }

    private fun content(
        l: Load,
        p: Person?,
        tracked: Set<String>,
        selected: Set<String>,
        config: ImageConfig,
    ): AddPersonContent = when {
        l is Load.Failed -> AddPersonContent.Error(l.error)
        l is Load.Done && p != null -> AddPersonContent.Loaded(
            header = AddPersonHeader(
                name = p.name,
                lifeDates = lifeDates(p.birthday, p.deathday),
                photoUrl = config.profileUrl(p.profilePath, PHOTO_MIN_WIDTH_PX),
            ),
            roles = l.roles.map { r ->
                val isTracked = r.department in tracked
                RoleRow(
                    department = r.department,
                    creditCount = r.creditCount,
                    jobLabel = creditJobLabel(r.department, r.topJob),
                    tracked = isTracked,
                    checked = !isTracked && r.department in selected,
                )
            },
        )
        // Refresh "succeeded" from a fresh cache but the person row is gone (shouldn't happen).
        l is Load.Done -> AddPersonContent.Error(TmdbError.NotFound)
        else -> AddPersonContent.Loading
    }

    companion object {
        /**
         * The photo is 88dp wide, so ~264px on a 3x screen, but TMDB's profile
         * widths jump from w185 to original (often several MB). w185 is plenty
         * sharp at this size on the 2.6x Pixel and far lighter.
         */
        private const val PHOTO_MIN_WIDTH_PX = 185

        fun factory(container: AppContainer, personId: Int): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val repo = container.tmdbRepository
                val trackedDao = container.database.trackedPersonDao()
                AddPersonViewModel(
                    personId = personId,
                    refreshPerson = { repo.refreshPerson(it) },
                    person = repo.observePerson(personId),
                    roles = repo::roles,
                    trackedDepartments = trackedDao.observeDepartments(personId),
                    imageConfig = repo::imageConfig,
                    track = { id, departments -> trackedDao.track(id, departments, System.currentTimeMillis()) },
                )
            }
        }
    }
}

/** "1928–1999", "b. 1983", "d. 1999", or null with no dates. */
internal fun lifeDates(birthday: LocalDate?, deathday: LocalDate?): String? = when {
    birthday != null && deathday != null -> "${birthday.year}–${deathday.year}"
    birthday != null -> "b. ${birthday.year}"
    deathday != null -> "d. ${deathday.year}"
    else -> null
}
