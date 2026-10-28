package com.ternagames.terna.feature.game

import com.ternagames.terna.core.designsystem.audio.TernaSfx
import com.ternagames.terna.core.model.GameResult
import com.ternagames.terna.core.model.HandEnd
import com.ternagames.terna.core.model.MatchEvent
import com.ternagames.terna.core.model.MatchPlayer
import com.ternagames.terna.core.model.MatchRules
import com.ternagames.terna.core.model.MatchState
import com.ternagames.terna.core.model.RevealedCard
import com.ternagames.terna.core.model.TurnInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GameLogicTest {

    private fun player(uid: String, seat: Int, hand: Int) = MatchPlayer(uid, seat, uid.replaceFirstChar { it.uppercase() }, "jaguar_01", false, true, hand, emptyList())

    private fun match(
        revealed: List<RevealedCard> = emptyList(),
        myHand: List<Int> = listOf(1, 5, 9),
        events: List<MatchEvent> = emptyList(),
        rules: MatchRules = MatchRules(),
        turnPlayer: String = "ana",
        startsAt: Long = 1_000,
        center: List<Boolean> = listOf(true, true, false),
    ) = MatchState(
        id = "m1", roomId = "r1", ruleSet = "TERNA_CLASSIC", finished = false, turnSeconds = 20, rules = rules,
        players = listOf(player("ana", 0, myHand.size), player("beto", 1, 3), player("carla", 2, 0)),
        seq = 5, turn = TurnInfo(turnPlayer, 2, startsAt, startsAt + 20_000), revealed = revealed, center = center,
        events = events, result = null, myHand = myHand,
    )

    private fun fromHand(owner: String, end: HandEnd, value: Int) = RevealedCard(owner, end, null, value, false)
    private fun fromCenter(slot: Int, value: Int) = RevealedCard(null, null, slot, value, false)

    @Test
    fun `solo se puede jugar en mi turno y cuando termina la pausa`() {
        val m = match(startsAt = 10_000)
        assertFalse(GameLogic.canAct(m, "ana", serverNow = 5_000))
        assertTrue(GameLogic.canAct(m, "ana", serverNow = 9_800)) // dentro de la tolerancia de 300 ms
        assertTrue(GameLogic.canAct(m, "ana", serverNow = 10_001))
        assertFalse(GameLogic.canAct(m, "beto", serverNow = 10_001))
    }

    @Test
    fun `los rivales sin cartas ocultas no se ofrecen como objetivo`() {
        val m = match()
        assertEquals(listOf("beto"), GameLogic.otherTargets(m, "ana").map { it.uid })
    }

    @Test
    fun `las cartas ya destapadas de un rival no cuentan como ocultas`() {
        val m = match(revealed = listOf(fromHand("beto", HandEnd.Low, 4), fromHand("beto", HandEnd.High, 4), fromHand("beto", HandEnd.Low, 4)))
        assertEquals(0, GameLogic.hiddenCards(m, "beto"))
        assertTrue(GameLogic.otherTargets(m, "ana").isEmpty())
    }

    @Test
    fun `revelarse a uno mismo depende de la regla`() {
        assertEquals(listOf("beto", "ana"), GameLogic.revealTargets(match(), "ana").map { it.uid })
        val sinYo = match(rules = MatchRules(allowSelfReveal = false))
        assertEquals(listOf("beto"), GameLogic.revealTargets(sinYo, "ana").map { it.uid })
    }

    @Test
    fun `el centro solo se ofrece si queda alguna carta sin destapar`() {
        assertTrue(GameLogic.canRevealCenter(match()))
        assertFalse(GameLogic.canRevealCenter(match(center = listOf(false, false))))
        assertFalse(GameLogic.canRevealCenter(match(center = listOf(true), revealed = listOf(fromCenter(0, 7)))))
    }

    @Test
    fun `hacer terna solo cuando falta una carta y esta en un extremo de mi mano`() {
        val unos = listOf(fromHand("beto", HandEnd.Low, 1), fromCenter(0, 1))
        val nueves = listOf(fromHand("beto", HandEnd.High, 9), fromCenter(0, 9))

        assertEquals(HandEnd.Low, GameLogic.completeEnd(match(revealed = unos, myHand = listOf(1, 5, 9)), "ana"))
        assertEquals(HandEnd.High, GameLogic.completeEnd(match(revealed = nueves, myHand = listOf(1, 5, 9)), "ana"))
        // El número está en mi mano pero en el medio: no es la carta menor ni la mayor.
        val cincos = listOf(fromHand("beto", HandEnd.Low, 5), fromCenter(0, 5))
        assertNull(GameLogic.completeEnd(match(revealed = cincos, myHand = listOf(1, 5, 9)), "ana"))
        // Falta más de una carta.
        assertNull(GameLogic.completeEnd(match(revealed = listOf(fromHand("beto", HandEnd.Low, 1)), myHand = listOf(1, 5, 9)), "ana"))
        // No hay nada destapado.
        assertNull(GameLogic.completeEnd(match(), "ana"))
    }

    @Test
    fun `hacer terna tiene en cuenta mis propias cartas ya destapadas`() {
        // Ya destapé mi carta menor (un 1): la siguiente menor es el 1 repetido.
        val revealed = listOf(fromHand("ana", HandEnd.Low, 1), fromCenter(0, 1))
        assertEquals(HandEnd.Low, GameLogic.completeEnd(match(revealed = revealed, myHand = listOf(1, 1, 9)), "ana"))
        // Con una sola copia, la carta menor ya está usada y no queda ninguna con el 1.
        assertNull(GameLogic.completeEnd(match(revealed = revealed, myHand = listOf(1, 5, 9)), "ana"))
    }

    @Test
    fun `pasar turno solo con la regla activa y el minimo de cartas`() {
        val reglas = MatchRules(allowPass = true, minRevealsBeforePass = 1)
        assertFalse(GameLogic.canPass(match(rules = reglas)))
        assertTrue(GameLogic.canPass(match(rules = reglas, revealed = listOf(fromCenter(0, 3)))))
        assertFalse(GameLogic.canPass(match(revealed = listOf(fromCenter(0, 3)))))
    }

    @Test
    fun `tras un fallo las cartas siguen a la vista durante la pausa y luego desaparecen`() {
        val fallidas = listOf(fromHand("beto", HandEnd.Low, 1), fromCenter(1, 2))
        val m = match(events = listOf(MatchEvent.TurnFailed(5, "ana", fallidas), MatchEvent.TurnStarted(6, "beto", 3)), startsAt = 10_000)

        val enPausa = GameLogic.tray(m, serverNow = 9_000)
        assertEquals(GameLogic.TrayKind.Failed, enPausa.kind)
        assertEquals(fallidas, enPausa.cards)
        assertEquals(mapOf(1 to 2), GameLogic.faceUpCenter(enPausa))

        val despues = GameLogic.tray(m, serverNow = 10_500)
        assertEquals(GameLogic.TrayKind.Live, despues.kind)
        assertTrue(despues.cards.isEmpty())
    }

    @Test
    fun `una terna completada se muestra en la mesa durante la pausa`() {
        val cartas = listOf(fromHand("beto", HandEnd.Low, 1), fromCenter(0, 1), fromHand("ana", HandEnd.Low, 1))
        val m = match(events = listOf(MatchEvent.SetCompleted(7, "ana", 1, cartas), MatchEvent.TurnStarted(8, "beto", 3)), startsAt = 5_000)
        assertEquals(GameLogic.TrayKind.Completed, GameLogic.tray(m, 4_000).kind)
    }

    @Test
    fun `el resultado de la partida se puede leer del modelo`() {
        val r = GameResult(listOf("ana"), "INSTANT")
        assertEquals("INSTANT", r.reason)
    }

    @Test
    fun `efectos de sonido distinguen victoria normal por 3 ternas y victoria jackpot 777`() {
        val m = match()
        val winNormal = listOf(
            MatchEvent.SetCompleted(6, "ana", 4, emptyList()),
            MatchEvent.GameOver(7, GameResult(listOf("ana"), "SETS")),
        )
        assertEquals(TernaSfx.VictoryNormal, GameViewModel.sfxFor(winNormal, m, "ana"))

        val winJackpot = listOf(
            MatchEvent.SetCompleted(6, "ana", 7, emptyList()),
            MatchEvent.GameOver(7, GameResult(listOf("ana"), "INSTANT")),
        )
        assertEquals(TernaSfx.VictoryJackpot, GameViewModel.sfxFor(winJackpot, m, "ana"))

        val ternaOnly = listOf(MatchEvent.SetCompleted(6, "ana", 4, emptyList()))
        assertEquals(TernaSfx.TernaComplete, GameViewModel.sfxFor(ternaOnly, m, "ana"))

        val claimFail = listOf(MatchEvent.ClaimFailed(6, "ana", "beto", 5))
        assertEquals(TernaSfx.AuraLoss, GameViewModel.sfxFor(claimFail, m, "ana"))

        val firstFlip = listOf(MatchEvent.CardRevealed(6, "ana", fromCenter(0, 3)))
        assertEquals(TernaSfx.CardFlip, GameViewModel.sfxFor(firstFlip, match(revealed = listOf(fromCenter(0, 3))), "ana"))

        val secondMatch = listOf(MatchEvent.CardRevealed(7, "ana", fromHand("beto", HandEnd.Low, 3)))
        assertEquals(
            TernaSfx.CardMatch,
            GameViewModel.sfxFor(secondMatch, match(revealed = listOf(fromCenter(0, 3), fromHand("beto", HandEnd.Low, 3))), "ana"),
        )
    }
}
