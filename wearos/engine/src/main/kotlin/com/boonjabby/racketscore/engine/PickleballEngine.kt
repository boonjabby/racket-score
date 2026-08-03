package com.boonjabby.racketscore.engine

enum class Side { ME, OPPONENT }
enum class CourtSide { LEFT, RIGHT }
enum class Sport(val label: String, val shortLabel: String) {
    PICKLEBALL_DOUBLES("Pickleball doubles", "Pickleball"),
    PICKLEBALL_SINGLES("Pickleball singles", "Singles"),
    TENNIS("Tennis / padel", "Tennis"),
    BADMINTON("Badminton", "Badminton"),
    TABLE_TENNIS("Table tennis", "Table tennis"),
    SQUASH("Squash", "Squash"),
    RACQUETBALL("Racquetball", "Racquetball"),
}

data class GameState(
    val sport: Sport = Sport.PICKLEBALL_DOUBLES,
    val meScore: Int = 0,
    val opponentScore: Int = 0,
    val meTennisPoints: Int = 0,
    val opponentTennisPoints: Int = 0,
    val server: Side = Side.ME,
    val serverNumber: Int = 2,
    val serverCourt: CourtSide = CourtSide.RIGHT,
    val openingServe: Boolean = true,
    val winner: Side? = null,
) {
    fun score(side: Side) = if (side == Side.ME) meScore else opponentScore
    fun tennisPoints(side: Side) = if (side == Side.ME) meTennisPoints else opponentTennisPoints
}

object PickleballEngine {
    fun newGame(
        firstServer: Side = Side.ME,
        serverNumber: Int = 2,
        sport: Sport = Sport.PICKLEBALL_DOUBLES,
    ) = GameState(
        sport = sport,
        server = firstServer,
        serverNumber = if (sport == Sport.PICKLEBALL_DOUBLES) serverNumber else 1,
        openingServe = sport == Sport.PICKLEBALL_DOUBLES && serverNumber == 2,
    )

    fun rally(state: GameState, rallyWinner: Side): GameState {
        if (state.winner != null) return state
        return when (state.sport) {
            Sport.TENNIS -> tennisRally(state, rallyWinner)
            Sport.PICKLEBALL_DOUBLES -> pickleballDoublesRally(state, rallyWinner).withWinner(rallyWinner, 11)
            Sport.PICKLEBALL_SINGLES -> sideOutRally(state, rallyWinner).withWinner(rallyWinner, 11)
            Sport.RACQUETBALL -> sideOutRally(state, rallyWinner).withWinner(rallyWinner, 15)
            Sport.BADMINTON -> rallyPoint(state, rallyWinner).withWinner(rallyWinner, 21, 30)
            Sport.TABLE_TENNIS -> tableTennisRally(state, rallyWinner).withWinner(rallyWinner, 11)
            Sport.SQUASH -> rallyPoint(state, rallyWinner).withWinner(rallyWinner, 11)
        }
    }

    private fun pickleballDoublesRally(state: GameState, winner: Side): GameState = when {
        winner == state.server -> state.addPoint(winner).copy(serverCourt = state.serverCourt.opposite())
        state.openingServe -> state.copy(server = winner, serverNumber = 1, serverCourt = CourtSide.RIGHT, openingServe = false)
        state.serverNumber == 1 -> state.copy(serverNumber = 2, serverCourt = state.serverCourt.opposite())
        else -> state.copy(server = winner, serverNumber = 1, serverCourt = CourtSide.RIGHT)
    }

    private fun sideOutRally(state: GameState, winner: Side) =
        if (winner == state.server) state.addPoint(winner) else state.copy(server = winner)

    private fun rallyPoint(state: GameState, winner: Side) = state.addPoint(winner).copy(server = winner)

    private fun tableTennisRally(state: GameState, winner: Side): GameState {
        val next = state.addPoint(winner)
        val interval = if (maxOf(next.meScore, next.opponentScore) >= 10) 1 else 2
        val total = next.meScore + next.opponentScore
        return if (total % interval == 0) next.copy(server = next.server.other()) else next
    }

    private fun tennisRally(state: GameState, winner: Side): GameState {
        val loser = winner.other()
        var next = if (winner == Side.ME) state.copy(meTennisPoints = state.meTennisPoints + 1)
        else state.copy(opponentTennisPoints = state.opponentTennisPoints + 1)
        if (next.tennisPoints(winner) >= 4 && next.tennisPoints(winner) - next.tennisPoints(loser) >= 2) {
            next = next.addPoint(winner).copy(
                meTennisPoints = 0,
                opponentTennisPoints = 0,
                server = next.server.other(),
            )
            next = next.withWinner(winner, 6)
        }
        return next
    }

    private fun GameState.addPoint(side: Side) = if (side == Side.ME) copy(meScore = meScore + 1)
    else copy(opponentScore = opponentScore + 1)

    private fun GameState.withWinner(side: Side, target: Int, cap: Int? = null): GameState {
        val score = score(side)
        val rival = score(side.other())
        val won = (score >= target && score - rival >= 2) || (cap != null && score >= cap)
        return if (won) copy(winner = side) else this
    }

    fun serviceCourt(state: GameState): CourtSide = if (state.sport == Sport.PICKLEBALL_DOUBLES) {
        state.serverCourt
    } else if (state.score(state.server) % 2 == 0) CourtSide.RIGHT else CourtSide.LEFT

    /** Court position as seen by the scorer, with the opponent's end mirrored. */
    fun scorerCourt(state: GameState): CourtSide {
        val court = serviceCourt(state)
        return if (state.server == Side.OPPONENT) court.opposite() else court
    }

    fun displayScore(state: GameState, side: Side): String = if (state.sport == Sport.TENNIS) {
        tennisDisplay(state.tennisPoints(side), state.tennisPoints(side.other()))
    } else state.score(side).toString()

    private fun tennisDisplay(points: Int, rival: Int): String = when {
        points >= 3 && rival >= 3 && points > rival -> "AD"
        points >= 3 -> "40"
        points == 2 -> "30"
        points == 1 -> "15"
        else -> "0"
    }

    fun announcement(state: GameState): String {
        val receiver = state.server.other()
        val servingName = if (state.server == Side.ME) "My side" else "Opponent"
        val serverNumber = if (state.sport == Sport.PICKLEBALL_DOUBLES) ", ${state.serverNumber}" else ""
        return "${displayScore(state, state.server)}, ${displayScore(state, receiver)}$serverNumber. $servingName serves."
    }
}

fun Side.other() = if (this == Side.ME) Side.OPPONENT else Side.ME
fun CourtSide.opposite() = if (this == CourtSide.RIGHT) CourtSide.LEFT else CourtSide.RIGHT
