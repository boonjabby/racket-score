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

    @Test
    fun `pickleball singles only scores on serve`() {
        val start = PickleballEngine.newGame(sport = Sport.PICKLEBALL_SINGLES)
        val sideOut = PickleballEngine.rally(start, Side.OPPONENT)
        assertEquals(0, sideOut.opponentScore)
        assertEquals(Side.OPPONENT, sideOut.server)
    }

    @Test
    fun `badminton wins at thirty without a two point margin`() {
        val state = GameState(sport = Sport.BADMINTON, meScore = 29, opponentScore = 29)
        assertEquals(Side.ME, PickleballEngine.rally(state, Side.ME).winner)
    }

    @Test
    fun `table tennis changes serve every two points before deuce`() {
        val start = PickleballEngine.newGame(sport = Sport.TABLE_TENNIS)
        val one = PickleballEngine.rally(start, Side.ME)
        val two = PickleballEngine.rally(one, Side.OPPONENT)
        assertEquals(Side.ME, one.server)
        assertEquals(Side.OPPONENT, two.server)
    }

    @Test
    fun `tennis displays deuce and advantage`() {
        val deuce = GameState(sport = Sport.TENNIS, meTennisPoints = 3, opponentTennisPoints = 3)
        assertEquals("40", PickleballEngine.displayScore(deuce, Side.ME))
        val advantage = PickleballEngine.rally(deuce, Side.ME)
        assertEquals("AD", PickleballEngine.displayScore(advantage, Side.ME))
    }
}
