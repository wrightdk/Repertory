package io.github.wrightdk.repertory.ui

/**
 * The label the app shows for a TMDB department: "Directing" -> "Director".
 * Departments without a mapping (Art, Crew, Lighting…) are shown as TMDB names them.
 */
fun roleLabel(department: String): String = knownRoleLabel(department) ?: department

/** The app's label for the departments the design names, else null. */
fun knownRoleLabel(department: String): String? = when (department) {
    "Directing" -> "Director"
    "Acting" -> "Actor"
    "Writing" -> "Writer"
    "Camera" -> "Cinematographer"
    "Sound" -> "Composer"
    "Production" -> "Producer"
    "Editing" -> "Editor"
    else -> null
}

/**
 * What a person was in [department], for "12 credits as Writer": the app's role
 * label where the design names one, else their most frequent job there
 * ("Production Design" for Art), else the department itself.
 */
fun creditJobLabel(department: String, topJob: String?): String =
    knownRoleLabel(department) ?: topJob ?: department
