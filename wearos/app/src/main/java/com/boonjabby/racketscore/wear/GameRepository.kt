package com.boonjabby.racketscore.wear

import android.content.Context
import com.boonjabby.racketscore.engine.CourtSide
import com.boonjabby.racketscore.engine.GameState
import com.boonjabby.racketscore.engine.LiveMatchSnapshot
import com.boonjabby.racketscore.engine.PickleballEngine
import com.boonjabby.racketscore.engine.Side
import com.boonjabby.racketscore.engine.Sport
import java.util.UUID

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

    fun loadOpeningSport(): Sport = runCatching {
        Sport.valueOf(preferences.getString("opening-sport", Sport.PICKLEBALL_DOUBLES.name)!!)
    }.getOrDefault(Sport.PICKLEBALL_DOUBLES)

    fun loadSpeechEnabled(): Boolean = preferences.getBoolean("speech-enabled", true)

    fun loadVibrationEnabled(): Boolean = preferences.getBoolean("vibration-enabled", true)

    fun loadKeepScreenAwake(): Boolean = preferences.getBoolean("keep-screen-awake", false)

    fun saveSettings(speechEnabled: Boolean, vibrationEnabled: Boolean, keepScreenAwake: Boolean) {
        preferences.edit()
            .putBoolean("speech-enabled", speechEnabled)
            .putBoolean("vibration-enabled", vibrationEnabled)
            .putBoolean("keep-screen-awake", keepScreenAwake)
            .apply()
    }

    fun saveOpeningSetup(server: Side, serverNumber: Int, sport: Sport) {
        preferences.edit()
            .putString("opening-server", server.name)
            .putInt("opening-server-number", serverNumber)
            .putString("opening-sport", sport.name)
            .apply()
    }

    fun beginSession() {
        preferences.edit()
            .putString("live-match-id", UUID.randomUUID().toString())
            .putLong("live-sequence", 0)
            .apply()
    }

    fun save(game: GameState, undoStack: List<GameState>): LiveMatchSnapshot {
        val nextSequence = preferences.getLong("live-sequence", 0) + 1
        val matchId = currentMatchId()
        preferences.edit()
            .putString("game", encode(game))
            .putString("undo", undoStack.takeLast(30).joinToString("|") { encode(it) })
            .putLong("live-sequence", nextSequence)
            .apply()
        return LiveMatchSnapshot(matchId, nextSequence, System.currentTimeMillis(), game)
    }

    fun currentSnapshot(game: GameState) = LiveMatchSnapshot(
        matchId = currentMatchId(),
        sequence = preferences.getLong("live-sequence", 0),
        updatedAtMillis = System.currentTimeMillis(),
        game = game,
    )

    private fun currentMatchId(): String {
        preferences.getString("live-match-id", null)?.let { return it }
        val generated = UUID.randomUUID().toString()
        preferences.edit().putString("live-match-id", generated).apply()
        return generated
    }

    private fun encode(state: GameState) = listOf(
        "v2",
        state.sport.name,
        state.meScore,
        state.opponentScore,
        state.meTennisPoints,
        state.opponentTennisPoints,
        state.server.name,
        state.serverNumber,
        state.serverCourt.name,
        state.openingServe,
        state.winner?.name.orEmpty(),
    ).joinToString(",")

    private fun decode(encoded: String): GameState? = runCatching {
        val values = encoded.split(",")
        if (values.firstOrNull() != "v2") return@runCatching decodeLegacy(values)
        GameState(
            sport = Sport.valueOf(values[1]),
            meScore = values[2].toInt(),
            opponentScore = values[3].toInt(),
            meTennisPoints = values[4].toInt(),
            opponentTennisPoints = values[5].toInt(),
            server = Side.valueOf(values[6]),
            serverNumber = values[7].toInt(),
            serverCourt = CourtSide.valueOf(values[8]),
            openingServe = values[9].toBooleanStrict(),
            winner = values.getOrNull(10)?.takeIf(String::isNotEmpty)?.let(Side::valueOf),
        )
    }.getOrNull()

    private fun decodeLegacy(values: List<String>) = GameState(
        meScore = values[0].toInt(),
        opponentScore = values[1].toInt(),
        server = Side.valueOf(values[2]),
        serverNumber = values[3].toInt(),
        serverCourt = CourtSide.valueOf(values[4]),
        openingServe = values[5].toBooleanStrict(),
        winner = values.getOrNull(6)?.takeIf(String::isNotEmpty)?.let(Side::valueOf),
    )
}
