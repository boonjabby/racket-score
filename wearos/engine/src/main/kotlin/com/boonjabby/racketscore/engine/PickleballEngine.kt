package com.boonjabby.racketscore.engine

enum class Side { ME, OPPONENT }
enum class CourtSide { LEFT, RIGHT }

data class GameState(
    val meScore: Int = 0,
    val opponentScore: Int = 0,
    val server: Side = Side.ME,
    val serverNumber: Int = 2,
    val serverCourt: CourtSide = CourtSide.RIGHT,
    val openingServe: Boolean = true,
    val winner: Side? = null,
) {
    fun score(side: Side) = if (side == Side.ME) meScore else opponentScore
}

object PickleballEngine {
    fun newGame(firstServer: Side = Side.ME, serverNumber: Int = 2) = GameState(
        server = firstServer,
        serverNumber = serverNumber,
        openingServe = serverNumber == 2,
    )

    fun rally(state: GameState, rallyWinner: Side): GameState {
        if (state.winner != null) return state

        var next = state
        if (rallyWinner == state.server) {
            next = if (rallyWinner == Side.ME) {
                state.copy(meScore = state.meScore + 1, serverCourt = state.serverCourt.opposite())
            } else {
                state.copy(opponentScore = state.opponentScore + 1, serverCourt = state.serverCourt.opposite())
            }
        } else if (state.openingServe) {
            next = state.copy(
                server = rallyWinner,
                serverNumber = 1,
                serverCourt = CourtSide.RIGHT,
                openingServe = false,
            )
        } else if (state.serverNumber == 1) {
            next = state.copy(serverNumber = 2, serverCourt = state.serverCourt.opposite())
        } else {
            next = state.copy(server = rallyWinner, serverNumber = 1, serverCourt = CourtSide.RIGHT)
        }

        val winnerScore = next.score(rallyWinner)
        val otherScore = next.score(rallyWinner.other())
        return if (winnerScore >= 11 && winnerScore - otherScore >= 2) next.copy(winner = rallyWinner) else next
    }

    /** Court position as seen by the scorer, with the opponent's end mirrored. */
    fun scorerCourt(state: GameState): CourtSide =
        if (state.server == Side.OPPONENT) state.serverCourt.opposite() else state.serverCourt

    fun announcement(state: GameState): String {
        val receiver = state.server.other()
        val servingName = if (state.server == Side.ME) "My side" else "Opponent"
        return "${state.score(state.server)}, ${state.score(receiver)}, ${state.serverNumber}. $servingName serves."
    }
}

fun Side.other() = if (this == Side.ME) Side.OPPONENT else Side.ME
fun CourtSide.opposite() = if (this == CourtSide.RIGHT) CourtSide.LEFT else CourtSide.RIGHT
