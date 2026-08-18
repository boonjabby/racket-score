package com.boonjabby.racketscore.mobile

import android.content.Context
import androidx.core.content.edit
import com.boonjabby.racketscore.engine.LiveMatchSnapshot
import com.boonjabby.racketscore.engine.LiveMatchSnapshotCodec
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.SecureRandom
import java.util.concurrent.Executors

data class CloudShareState(
    val code: String? = null,
    val sharing: Boolean = false,
    val busy: Boolean = false,
    val status: CloudShareStatus = CloudShareStatus.IDLE,
    val lastPublishedAt: Long? = null,
    val message: String? = null,
)

enum class CloudShareStatus { IDLE, CONNECTING, LIVE, OFFLINE, STOPPED, EXPIRED }

class CloudShareRepository(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    private val executor = Executors.newSingleThreadExecutor()
    private val random = SecureRandom()

    fun loadState(): CloudShareState {
        val code = preferences.getString(CODE, null)
        val startedAt = preferences.getLong(STARTED_AT, 0L)
        if (code != null && System.currentTimeMillis() - startedAt >= SHARE_LIFETIME_MILLIS) {
            preferences.edit { remove(CODE); remove(STARTED_AT) }
            return CloudShareState(status = CloudShareStatus.EXPIRED, message = "The previous sharing code expired")
        }
        val sharing = !code.isNullOrBlank()
        val status = preferences.getString(STATUS, null)?.let { runCatching { CloudShareStatus.valueOf(it) }.getOrNull() }
            ?: if (sharing) CloudShareStatus.LIVE else CloudShareStatus.IDLE
        return CloudShareState(
            code = code,
            sharing = sharing,
            status = status,
            lastPublishedAt = preferences.getLong(LAST_PUBLISHED_AT, 0L).takeIf { it > 0L },
            message = preferences.getString(MESSAGE, null),
        )
    }

    fun start(snapshot: LiveMatchSnapshot, callback: (CloudShareState) -> Unit) {
        executor.execute {
            callback(CloudShareState(busy = true, status = CloudShareStatus.CONNECTING))
            runCatching {
                val session = ensureSession()
                val code = generateCode()
                request(
                    path = "/rest/v1/live_matches",
                    method = "POST",
                    token = session.token,
                    body = JSONObject()
                        .put("owner_id", session.userId)
                        .put("public_code", code)
                        .put("sequence", snapshot.sequence)
                        .put("snapshot", snapshotJson(snapshot))
                        .put("status", if (snapshot.game.winner == null) "live" else "complete"),
                    prefer = "return=minimal",
                )
                preferences.edit {
                    putString(CODE, code)
                    putLong(STARTED_AT, System.currentTimeMillis())
                    putLong(LAST_PUBLISHED_AT, System.currentTimeMillis())
                    putString(STATUS, CloudShareStatus.LIVE.name)
                    remove(MESSAGE)
                }
                loadState()
            }.onSuccess(callback).onFailure {
                callback(CloudShareState(message = friendlyError(it)))
            }
        }
    }

    fun publish(snapshot: LiveMatchSnapshot) {
        val code = preferences.getString(CODE, null) ?: return
        executor.execute {
            runCatching {
                val session = ensureSession()
                request(
                    path = "/rest/v1/live_matches?public_code=eq.$code",
                    method = "PATCH",
                    token = session.token,
                    body = JSONObject()
                        .put("sequence", snapshot.sequence)
                        .put("snapshot", snapshotJson(snapshot))
                        .put("status", if (snapshot.game.winner == null) "live" else "complete"),
                    prefer = "return=minimal",
                )
                preferences.edit {
                    putLong(LAST_PUBLISHED_AT, System.currentTimeMillis())
                    putString(STATUS, CloudShareStatus.LIVE.name)
                    remove(MESSAGE)
                }
            }.onFailure {
                preferences.edit {
                    putString(STATUS, CloudShareStatus.OFFLINE.name)
                    putString(MESSAGE, "Score saved on this phone. Live viewers will catch up after the next rally online.")
                }
            }
        }
    }

    fun stop(callback: (CloudShareState) -> Unit) {
        val code = preferences.getString(CODE, null)
        if (code == null) {
            callback(CloudShareState(message = "Sharing is already off"))
            return
        }
        executor.execute {
            callback(loadState().copy(busy = true))
            runCatching {
                val session = ensureSession()
                request(
                    path = "/rest/v1/live_matches?public_code=eq.$code",
                    method = "PATCH",
                    token = session.token,
                    body = JSONObject().put("status", "closed"),
                    prefer = "return=minimal",
                )
                preferences.edit {
                    remove(CODE)
                    remove(STARTED_AT)
                    remove(LAST_PUBLISHED_AT)
                    putString(STATUS, CloudShareStatus.STOPPED.name)
                    putString(MESSAGE, "Live sharing stopped")
                }
                loadState()
            }.onSuccess(callback).onFailure {
                callback(loadState().copy(message = friendlyError(it)))
            }
        }
    }

    private fun ensureSession(): Session {
        val savedToken = preferences.getString(TOKEN, null)
        val savedUser = preferences.getString(USER_ID, null)
        val savedRefresh = preferences.getString(REFRESH_TOKEN, null)
        val savedExpiry = preferences.getLong(EXPIRES_AT, 0L)
        if (!savedToken.isNullOrBlank() && !savedUser.isNullOrBlank() && savedExpiry > System.currentTimeMillis() / 1000 + 60) {
            return Session(savedToken, savedUser, savedRefresh.orEmpty(), savedExpiry)
        }

        if (!savedRefresh.isNullOrBlank() && !savedUser.isNullOrBlank()) {
            val refreshed = runCatching {
                sessionFrom(JSONObject(request(
                    "/auth/v1/token?grant_type=refresh_token",
                    "POST",
                    body = JSONObject().put("refresh_token", savedRefresh),
                )))
            }.getOrNull()
            if (refreshed != null) {
                saveSession(refreshed)
                return refreshed
            }
        }

        val response = request("/auth/v1/signup", "POST", body = JSONObject())
        val session = sessionFrom(JSONObject(response))
        saveSession(session)
        return session
    }

    private fun sessionFrom(json: JSONObject) = Session(
        token = json.getString("access_token"),
        userId = json.getJSONObject("user").getString("id"),
        refreshToken = json.getString("refresh_token"),
        expiresAt = json.optLong("expires_at", System.currentTimeMillis() / 1000 + json.optLong("expires_in", 3600)),
    )

    private fun saveSession(session: Session) {
        preferences.edit {
            putString(TOKEN, session.token)
            putString(USER_ID, session.userId)
            putString(REFRESH_TOKEN, session.refreshToken)
            putLong(EXPIRES_AT, session.expiresAt)
        }
    }

    private fun request(
        path: String,
        method: String,
        token: String? = null,
        body: JSONObject? = null,
        prefer: String? = null,
    ): String {
        val connection = URL("$SUPABASE_URL$path").openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            connection.setRequestProperty("apikey", PUBLISHABLE_KEY)
            connection.setRequestProperty("Content-Type", "application/json")
            token?.let { connection.setRequestProperty("Authorization", "Bearer $it") }
            prefer?.let { connection.setRequestProperty("Prefer", it) }
            if (body != null) {
                connection.doOutput = true
                connection.outputStream.bufferedWriter().use { it.write(body.toString()) }
            }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (status !in 200..299) throw CloudRequestException(status, response)
            return response
        } finally {
            connection.disconnect()
        }
    }

    private fun snapshotJson(snapshot: LiveMatchSnapshot) = JSONObject()
        .put("encoded", LiveMatchSnapshotCodec.encode(snapshot))
        .put("matchId", snapshot.matchId)
        .put("sequence", snapshot.sequence)
        .put("updatedAtMillis", snapshot.updatedAtMillis)
        .put("sport", snapshot.game.sport.name)
        .put("meScore", snapshot.game.meScore)
        .put("opponentScore", snapshot.game.opponentScore)
        .put("server", snapshot.game.server.name)
        .put("serverNumber", snapshot.game.serverNumber)
        .put("serverCourt", snapshot.game.serverCourt.name)
        .put("winner", snapshot.game.winner?.name ?: JSONObject.NULL)

    private fun generateCode(): String = buildString(8) {
        repeat(8) { append(ALPHABET[random.nextInt(ALPHABET.length)]) }
    }

    private fun friendlyError(error: Throwable): String {
        val raw = error.message.orEmpty()
        return when {
            raw.contains("anonymous_provider_disabled") -> "Cloud sharing needs anonymous sign-in enabled"
            raw.contains("duplicate key") -> "Please try again to create a new code"
            else -> "Could not reach live sharing. Check your connection and try again."
        }
    }

    fun listen(listener: android.content.SharedPreferences.OnSharedPreferenceChangeListener) =
        preferences.registerOnSharedPreferenceChangeListener(listener)

    fun stopListening(listener: android.content.SharedPreferences.OnSharedPreferenceChangeListener) =
        preferences.unregisterOnSharedPreferenceChangeListener(listener)

    private data class Session(val token: String, val userId: String, val refreshToken: String, val expiresAt: Long)
    private class CloudRequestException(status: Int, body: String) : Exception("$status: $body")

    companion object {
        private const val SUPABASE_URL = "https://qpvxeucamyaupnxepwdl.supabase.co"
        private const val PUBLISHABLE_KEY = "sb_publishable_tv5LzPDm-aXMz4j_4Ll4dA_TVQGkcDH"
        private const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        private const val PREFERENCES = "racket-score-cloud-share"
        private const val TOKEN = "anonymous-access-token"
        private const val REFRESH_TOKEN = "anonymous-refresh-token"
        private const val EXPIRES_AT = "anonymous-expires-at"
        private const val USER_ID = "anonymous-user-id"
        private const val CODE = "active-public-code"
        private const val STARTED_AT = "active-share-started-at"
        private const val LAST_PUBLISHED_AT = "last-published-at"
        private const val STATUS = "share-status"
        private const val MESSAGE = "share-message"
        private const val SHARE_LIFETIME_MILLIS = 8L * 60L * 60L * 1000L
    }
}
