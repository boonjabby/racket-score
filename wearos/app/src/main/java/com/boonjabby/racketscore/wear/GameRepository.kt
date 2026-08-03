package com.boonjabby.racketscore.wear

import android.content.Context
import com.boonjabby.racketscore.engine.CourtSide
import com.boonjabby.racketscore.engine.GameState
import com.boonjabby.racketscore.engine.PickleballEngine
import com.boonjabby.racketscore.engine.Side

class GameRepository(context: Context) {
    private val preferences = context.getSharedPreferences("racket-score-watch", Context.MODE_PRIVATE)

    fun loadGame(): GameState = preferences.getString("game", null)?.let(::decode) ?: PickleballEngine.newGame()

    fun loadUndoStack(): List<GameState> = preferences.getString("undo", null)
        ?.split("|")
        ?.mapNotNull(::decode)
        ?: emptyList()

    fun loadOpeningServer(): Side = runCatching {
        Side.valueOf(preferences.getString("opening-server", Side.ME.name)!!)
    }.getOrDefault(Side.ME)

    fun loadOpeningServerNumber(): Int = preferences.getInt("opening-server-number", 2).coerceIn(1, 2)

    fun saveOpeningSetup(server: Side, serverNumber: Int) {
        preferences.edit()
            .putString("opening-server", server.name)
            .putInt("opening-server-number", serverNumber)
            .apply()
    }

    fun save(game: GameState, undoStack: List<GameState>) {
        preferences.edit()
            .putString("game", encode(game))
            .putString("undo", undoStack.takeLast(30).joinToString("|") { encode(it) })
            .apply()
    }

    private fun encode(state: GameState) = listOf(
        state.meScore,
        state.opponentScore,
        state.server.name,
        state.serverNumber,
        state.serverCourt.name,
        state.openingServe,
        state.winner?.name.orEmpty(),
    ).joinToString(",")

    private fun decode(encoded: String): GameState? = runCatching {
        val values = encoded.split(",")
        GameState(
            meScore = values[0].toInt(),
            opponentScore = values[1].toInt(),
            server = Side.valueOf(values[2]),
            serverNumber = values[3].toInt(),
            serverCourt = CourtSide.valueOf(values[4]),
            openingServe = values[5].toBooleanStrict(),
            winner = values.getOrNull(6)?.takeIf(String::isNotEmpty)?.let(Side::valueOf),
        )
    }.getOrNull()
}
