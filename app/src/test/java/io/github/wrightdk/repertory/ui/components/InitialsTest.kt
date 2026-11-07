package io.github.wrightdk.repertory.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class InitialsTest {
    @Test fun initials() {
        assertEquals("SK", initials("Stanley Kubrick"))
        assertEquals("M", initials("Madonna"))
        assertEquals("JG", initials("Jean-Luc Godard"))
        assertEquals("AV", initials("Agnès  Varda"))
        assertEquals("ÉR", initials("éric Rohmer"))
        assertEquals("", initials("  "))
    }
}
