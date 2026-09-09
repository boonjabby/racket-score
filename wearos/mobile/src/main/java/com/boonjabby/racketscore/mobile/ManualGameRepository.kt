package com.boonjabby.racketscore.mobile

import android.content.Context
import androidx.core.content.edit
import com.boonjabby.racketscore.engine.GameState
import com.boonjabby.racketscore.engine.LiveMatchSnapshot
import com.boonjabby.racketscore.engine.LiveMatchSnapshotCodec
import com.boonjabby.racketscore.engine.PickleballEngine
import com.boonjabby.racketscore.engine.Side
import com.boonjabby.racketscore.engine.Sport
import java.util.UUID

data class ManualPreferences(
    val speech: Boolean = true,
    val vibration: Boolean = true,
    val keepAwake: Boolean = true,
    val umpireMode: Boolean = false,
    val mySideName: String = "My Side",
    val opponentName: String = "Opponent",
    val courtTheme: CourtTheme = CourtTheme.HIGH_CONTRAST,
)

enum class CourtTheme(val label: String) {
    HIGH_CONTRAST("High contrast"),
    BLUE_RED("Blue / Red"),
    GREEN_BLUE("Green / Blue"),
    PURPLE_ORANGE("Purple / Orange"),
}

class ManualGameRepository(context: Context) {
    private val preferences = context.getSharedPreferences("racket-score-manual", Context.MODE_PRIVATE)

    fun loadGame(): GameState = preferences.getString("game", null)
        ?.let(LiveMatchSnapshotCodec::decode)?.game
        ?: PickleballEngine.newGame()

    fun loadUndo(): List<GameState> = preferences.getString("undo", null)
        ?.lineSequence()?.mapNotNull(LiveMatchSnapshotCodec::decode)?.map { it.game }?.toList()
        ?: emptyList()

    fun save(game: GameState, undo: List<GameState>) {
        preferences.edit {
            putString("game", encode(game))
            putString("undo", undo.takeLast(40).joinToString("\n", transform = ::encode))
            val existing = loadHistory().filterNot { it.matchId == currentMatchId() }
            if (game.winner != null) {
                putString("history", (listOf(snapshot(game)) + existing).take(50).joinToString("\n", transform = LiveMatchSnapshotCodec::encode))
            } else {
                // An accidental match point can be undone from the winner screen.
                // Remove the now-retracted result from match history as well.
                putString("history", existing.take(50).joinToString("\n", transform = LiveMatchSnapshotCodec::encode))
            }
        }
    }

    fun start(game: GameState) {
        preferences.edit { putString("match-id", UUID.randomUUID().toString()) }
        save(game, emptyList())
    }

    fun loadHistory(): List<LiveMatchSnapshot> = preferences.getString("history", null)
        ?.lineSequence()?.mapNotNull(LiveMatchSnapshotCodec::decode)?.toList() ?: emptyList()

    fun loadPreferences() = ManualPreferences(
        speech = preferences.getBoolean("speech", true),
        vibration = preferences.getBoolean("vibration", true),
        keepAwake = preferences.getBoolean("awake", true),
        umpireMode = preferences.getBoolean("umpire", false),
        mySideName = preferences.getString("my-side-name", "My Side").orEmpty().ifBlank { "My Side" },
        opponentName = preferences.getString("opponent-name", "Opponent").orEmpty().ifBlank { "Opponent" },
        courtTheme = preferences.getString("court-theme", null)
            ?.let { saved -> CourtTheme.values().firstOrNull { it.name == saved } }
            ?: CourtTheme.HIGH_CONTRAST,
    )

    fun savePreferences(value: ManualPreferences) = preferences.edit {
        putBoolean("speech", value.speech)
        putBoolean("vibration", value.vibration)
        putBoolean("awake", value.keepAwake)
        putBoolean("umpire", value.umpireMode)
        putString("my-side-name", value.mySideName.trim().ifBlank { "My Side" })
        putString("opponent-name", value.opponentName.trim().ifBlank { "Opponent" })
        putString("court-theme", value.courtTheme.name)
    }

    private fun currentMatchId(): String = preferences.getString("match-id", null) ?: "manual-current"
    private fun snapshot(game: GameState) = LiveMatchSnapshot(currentMatchId(), 0, System.currentTimeMillis(), game)
    private fun encode(game: GameState) = LiveMatchSnapshotCodec.encode(snapshot(game))
}
