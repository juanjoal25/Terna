package com.ternagames.terna.core.data.auth

import org.junit.Assert.assertEquals
import org.junit.Test

class LargeGooglePhotoTest {
    @Test
    fun `pide la foto de Google en 400 px`() {
        assertEquals(
            "https://lh3.googleusercontent.com/a/ACg8ocK=s400-c",
            largeGooglePhoto("https://lh3.googleusercontent.com/a/ACg8ocK=s96-c"),
        )
    }

    @Test
    fun `deja igual las URL sin tamaño`() {
        val url = "https://example.com/foto.png"
        assertEquals(url, largeGooglePhoto(url))
    }
}
