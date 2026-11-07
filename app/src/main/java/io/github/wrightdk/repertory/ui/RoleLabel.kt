package io.github.wrightdk.repertory.ui

/**
 * The label the app shows for a TMDB department: "Directing" -> "Director".
 * Departments without a mapping (Art, Crew, Lighting…) are shown as TMDB names them.
 */
fun roleLabel(department: String): String = when (department) {
    "Directing" -> "Director"
    "Acting" -> "Actor"
    "Writing" -> "Writer"
    "Camera" -> "Cinematographer"
    "Sound" -> "Composer"
    "Production" -> "Producer"
    "Editing" -> "Editor"
    else -> department
}
