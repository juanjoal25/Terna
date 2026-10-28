package com.ternagames.terna.core.data.user

import com.ternagames.terna.core.model.Avatars
import com.ternagames.terna.core.model.PlayerProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ParseStatsTest {
    @Test
    fun `suma partidas jugadas y ganadas de todos los modos`() {
        // Firestore entrega los números como Long.
        val stats = parseStats(
            mapOf(
                "TERNA_CLASSIC" to mapOf("games" to 35L, "wins" to 20L, "sets" to 64L),
                "TERNA_SPICY" to mapOf("games" to 7L, "wins" to 1L),
            ),
        )
        val profile = PlayerProfile("u1", "Parra", Avatars.PHOTO, anonymous = false, level = 1, xp = 0, coins = 0, stats = stats)

        assertEquals(42, profile.gamesPlayed)
        assertEquals(21, profile.gamesWon)
        assertEquals(64, stats.getValue("TERNA_CLASSIC").sets)
    }

    @Test
    fun `sin partidas o con datos malformados queda en cero`() {
        assertTrue(parseStats(null).isEmpty())
        assertTrue(parseStats("roto").isEmpty())

        val stats = parseStats(mapOf("TERNA_CLASSIC" to "roto", "TERNA_SPICY" to mapOf("games" to "x", "wins" to -3L)))
        assertEquals(setOf("TERNA_SPICY"), stats.keys)
        assertEquals(0, stats.getValue("TERNA_SPICY").games)
        assertEquals(0, stats.getValue("TERNA_SPICY").wins)
    }
}
