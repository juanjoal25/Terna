package com.ternagames.terna.core.data.user

import org.junit.Assert.assertEquals
import org.junit.Test

class ProfileNamesTest {
    @Test
    fun `recorta a 20 caracteres y limpia espacios`() {
        assertEquals("María Fernanda Gómez", ProfileNames.sanitize("  María   Fernanda Gómez Restrepo "))
    }

    @Test
    fun `usa un nombre por defecto si viene vacío`() {
        assertEquals("Jugador", ProfileNames.sanitize(null))
        assertEquals("Jugador", ProfileNames.sanitize("   "))
    }
}
