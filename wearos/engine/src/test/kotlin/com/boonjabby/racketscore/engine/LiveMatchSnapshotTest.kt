package com.boonjabby.racketscore.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LiveMatchSnapshotTest {
    @Test
    fun `snapshot round trips every scoring field`() {
        val original = LiveMatchSnapshot(
            matchId = "match-123",
            sequence = 42,
            updatedAtMillis = 123456789,
            game = GameState(
                sport = Sport.PICKLEBALL_DOUBLES,
                meScore = 8,
                opponentScore = 6,
                server = Side.OPPONENT,
                serverNumber = 2,
                serverCourt = CourtSide.LEFT,
                openingServe = false,
            ),
        )
        assertEquals(original, LiveMatchSnapshotCodec.decode(LiveMatchSnapshotCodec.encode(original)))
    }

    @Test
    fun `unknown snapshot versions are ignored`() {
        assertNull(LiveMatchSnapshotCodec.decode("99|future"))
    }
}
