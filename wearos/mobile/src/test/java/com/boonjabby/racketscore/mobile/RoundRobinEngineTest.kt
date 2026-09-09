package com.boonjabby.racketscore.mobile

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RoundRobinEngineTest {
    @Test fun `supports eight asynchronous courts`() {
        val session = RobinEngine.start((1..36).map { "Player $it" }, 8)
        assertEquals(8, session.courts.size)
        assertEquals(4, session.waiting.size)
        assertEquals(36, (session.courts.flatMap { it.teamA + it.teamB } + session.waiting).distinct().size)
    }

    @Test fun `one court advances while other courts remain unchanged`() {
        val first = RobinEngine.start((1..12).map { "Player $it" }, 2)
        val otherCourt = first.courts[1]
        val completed = first.copy(courts = first.courts.map { if (it.number == 1) it.copy(winner = 0) else it })
        val next = RobinEngine.advanceCourt(completed, 1, RobinMode.FAIR_QUEUE)
        assertEquals(otherCourt, next.courts[1])
        assertEquals(2, next.courts[0].gameNumber)
        assertTrue(first.waiting.all { it in next.courts[0].teamA + next.courts[0].teamB })
    }

    @Test fun `same court shuffle switches partners and preserves queue`() {
        val first = RobinEngine.start((1..8).map { "Player $it" }, 1)
        val finished = first.courts.single().copy(winner = 0)
        val next = RobinEngine.advanceCourt(first.copy(courts = listOf(finished)), 1, RobinMode.SAME_COURT_SHUFFLE)
        assertEquals(first.waiting, next.waiting)
        assertEquals(setOf(finished.teamA[0], finished.teamB[0]), next.courts.single().teamA.toSet())
    }

    @Test fun `split winners pairs each winner with a waiting challenger`() {
        val first = RobinEngine.start((1..8).map { "Player $it" }, 1)
        val finished = first.courts.single().copy(winner = 0)
        val next = RobinEngine.advanceCourt(first.copy(courts = listOf(finished)), 1, RobinMode.SPLIT_WINNERS)
        assertTrue(finished.teamA[0] in next.courts.single().teamA)
        assertTrue(finished.teamA[1] in next.courts.single().teamB)
        assertTrue(first.waiting.take(2).all { it in next.courts.single().teamA + next.courts.single().teamB })
    }

    @Test fun `amending one court cannot double book a player from another court`() {
        val session = RobinEngine.start((1..12).map { "Player $it" }, 2)
        val courtOne = session.courts[0]
        val replacement = session.waiting.first()
        val selected = (courtOne.teamA + courtOne.teamB).dropLast(1) + replacement
        val amended = RobinEngine.amendCourt(session, courtOne.number, selected, paused = setOf((courtOne.teamA + courtOne.teamB).last()))

        assertEquals(selected.toSet(), (amended.courts[0].teamA + amended.courts[0].teamB).toSet())
        assertTrue(amended.courts[1] == session.courts[1])
        assertTrue(amended.paused.single() !in amended.waiting)
    }
}
