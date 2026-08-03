package com.boonjabby.racketscore.engine

import kotlin.test.Test
import kotlin.test.assertEquals

class PickleballEngineTest {
    @Test
    fun `official opening serve side-outs to opponent server one`() {
        val next = PickleballEngine.rally(PickleballEngine.newGame(), Side.OPPONENT)
        assertEquals(Side.OPPONENT, next.server)
        assertEquals(1, next.serverNumber)
        assertEquals(CourtSide.RIGHT, next.serverCourt)
        assertEquals(CourtSide.LEFT, PickleballEngine.scorerCourt(next))
    }

    @Test
    fun `server two takes the opposite physical court`() {
        val start = PickleballEngine.newGame(serverNumber = 1)
        val secondServer = PickleballEngine.rally(start, Side.OPPONENT)
        assertEquals(Side.ME, secondServer.server)
        assertEquals(2, secondServer.serverNumber)
        assertEquals(CourtSide.LEFT, secondServer.serverCourt)
    }

    @Test
    fun `serving player moves after scoring then partner remains opposite`() {
        val scored = PickleballEngine.rally(PickleballEngine.newGame(serverNumber = 1), Side.ME)
        assertEquals(CourtSide.LEFT, scored.serverCourt)
        val secondServer = PickleballEngine.rally(scored, Side.OPPONENT)
        assertEquals(2, secondServer.serverNumber)
        assertEquals(CourtSide.RIGHT, secondServer.serverCourt)
    }

    @Test
    fun `spoken score is from server perspective`() {
        val state = GameState(meScore = 3, opponentScore = 1, server = Side.OPPONENT, serverNumber = 2)
        assertEquals("1, 3, 2. Opponent serves.", PickleballEngine.announcement(state))
    }

    @Test
    fun `game is won at eleven by two`() {
        val state = GameState(meScore = 10, opponentScore = 9, server = Side.ME, serverNumber = 1)
        val next = PickleballEngine.rally(state, Side.ME)
        assertEquals(Side.ME, next.winner)
    }
}
