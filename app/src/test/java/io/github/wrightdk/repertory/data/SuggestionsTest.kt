package io.github.wrightdk.repertory.data

import java.io.File
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SuggestionsTest {
    private fun s(id: Int, department: String) = Suggestion(id, "Person $id", department)

    @Test
    fun `picks a handful with no more than two from one department`() {
        val all = (1..30).map { s(it, "Directing") } + (31..40).map { s(it, "Acting") } + s(41, "Camera")
        repeat(50) { seed ->
            val picked = pickSuggestions(all, 4, Random(seed))
            assertEquals(4, picked.size)
            assertEquals(4, picked.distinct().size)
            assertTrue(picked.groupBy { it.department }.values.all { it.size <= 2 })
        }
    }

    @Test
    fun `tops up past the cap when there are too few departments`() {
        val all = (1..10).map { s(it, "Directing") }
        val picked = pickSuggestions(all, 4, Random(1))
        assertEquals(4, picked.distinct().size)
    }

    @Test
    fun `the bundled file parses and has about a hundred people`() {
        val file = File("src/main/assets/suggestions.json")
        val all = parseSuggestions(file.readText())
        assertTrue(all.size in 80..150)
        assertEquals(all.size, all.map { it.id }.distinct().size)
        val departments = all.map { it.department }.toSet()
        assertEquals(setOf("Directing", "Acting", "Camera", "Writing", "Sound"), departments)
    }
}
