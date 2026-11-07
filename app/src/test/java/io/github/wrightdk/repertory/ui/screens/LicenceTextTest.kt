package io.github.wrightdk.repertory.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Test

class LicenceTextTest {
    @Test fun `joins hard-wrapped lines and splits at blank lines`() {
        val text = """
                    GNU GENERAL PUBLIC LICENSE
                       Version 3, 29 June 2007

  The GNU General Public License is a free, copyleft license for
software and other kinds of works.


  0. Definitions.
""".trimStart('\n')
        assertEquals(
            listOf(
                "GNU GENERAL PUBLIC LICENSE",
                "Version 3, 29 June 2007",
                "The GNU General Public License is a free, copyleft license for software and other kinds of works.",
                "0. Definitions.",
            ),
            reflowLicenceText(text),
        )
    }

    @Test fun `an all-caps heading stands alone`() {
        val text = "PREAMBLE\nThe goals of the Open Font License (OFL) are to stimulate\nworldwide development"
        assertEquals(
            listOf("PREAMBLE", "The goals of the Open Font License (OFL) are to stimulate worldwide development"),
            reflowLicenceText(text),
        )
    }

    @Test fun `drops rules of dashes`() {
        val text = "Copyright 2020\n\n-----\nSIL OPEN FONT LICENSE Version 1.1\n-----\n\nPREAMBLE"
        assertEquals(listOf("Copyright 2020", "SIL OPEN FONT LICENSE Version 1.1", "PREAMBLE"), reflowLicenceText(text))
    }
}
