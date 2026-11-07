package io.github.wrightdk.repertory.ui.screens

import io.github.wrightdk.repertory.R
import org.junit.Assert.assertEquals
import org.junit.Test

class OwnApiKeyDialogTest {
    @Test fun `a 32 character v3 key passes`() {
        assertEquals(null, apiKeyFormatError("0123456789abcdef0123456789ABCDEF"))
    }

    @Test fun `the v4 read access token is caught with its own message`() {
        assertEquals(R.string.own_key_error_token, apiKeyFormatError("eyJhbGciOiJIUzI1NiJ9.eyJhdWQiOiIxMjMifQ.abc"))
    }

    @Test fun `anything else of the wrong shape is a format error`() {
        assertEquals(R.string.own_key_error_format, apiKeyFormatError("0123456789abcdef"))
        assertEquals(R.string.own_key_error_format, apiKeyFormatError("0123456789abcdef 0123456789abcde"))
    }
}
