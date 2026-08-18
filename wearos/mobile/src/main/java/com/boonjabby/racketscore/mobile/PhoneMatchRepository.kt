package com.boonjabby.racketscore.mobile

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.boonjabby.racketscore.engine.LiveMatchSnapshot
import com.boonjabby.racketscore.engine.LiveMatchSnapshotCodec

class PhoneMatchRepository(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun save(snapshot: LiveMatchSnapshot) {
        val current = loadLatest()
        if (current != null && current.matchId == snapshot.matchId && current.sequence >= snapshot.sequence) return

        val existingHistory = loadHistory().filterNot { it.matchId == snapshot.matchId }
        val nextHistory = if (snapshot.game.winner != null) listOf(snapshot) + existingHistory else existingHistory
        preferences.edit {
            putString(LATEST, LiveMatchSnapshotCodec.encode(snapshot))
            putString(HISTORY, nextHistory.take(50).joinToString("\n") { LiveMatchSnapshotCodec.encode(it) })
        }
    }

    fun loadLatest(): LiveMatchSnapshot? = preferences.getString(LATEST, null)?.let(LiveMatchSnapshotCodec::decode)

    fun loadHistory(): List<LiveMatchSnapshot> = preferences.getString(HISTORY, null)
        ?.lineSequence()
        ?.mapNotNull(LiveMatchSnapshotCodec::decode)
        ?.toList()
        ?: emptyList()

    fun listen(listener: SharedPreferences.OnSharedPreferenceChangeListener) =
        preferences.registerOnSharedPreferenceChangeListener(listener)

    fun stopListening(listener: SharedPreferences.OnSharedPreferenceChangeListener) =
        preferences.unregisterOnSharedPreferenceChangeListener(listener)

    companion object {
        private const val PREFERENCES = "racket-score-phone"
        private const val LATEST = "latest-live-match"
        private const val HISTORY = "completed-match-history"
    }
}
