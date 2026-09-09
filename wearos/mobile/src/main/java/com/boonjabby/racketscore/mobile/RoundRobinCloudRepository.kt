package com.boonjabby.racketscore.mobile

import android.content.Context
import androidx.core.content.edit
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.SecureRandom
import java.util.concurrent.Executors

/** Publishes a read-only event snapshot. The anonymous owner is the only writer. */
internal class RoundRobinCloudRepository(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    private val executor = Executors.newSingleThreadExecutor()
    private val random = SecureRandom()

    fun loadState(): CloudShareState {
        val code = preferences.getString(CODE, null)
        return CloudShareState(
            code = code,
            sharing = !code.isNullOrBlank(),
            status = if (code == null) CloudShareStatus.IDLE else CloudShareStatus.LIVE,
            lastPublishedAt = preferences.getLong(LAST_PUBLISHED, 0L).takeIf { it > 0 },
            message = preferences.getString(MESSAGE, null),
        )
    }

    fun start(session: RobinSession, callback: (CloudShareState) -> Unit) = executor.execute {
        callback(loadState().copy(busy = true, status = CloudShareStatus.CONNECTING))
        runCatching {
            val auth = ensureSession()
            val code = generateCode()
            request("/rest/v1/live_matches", "POST", auth.token, JSONObject()
                .put("owner_id", auth.userId).put("public_code", code)
                .put("sequence", sequence(session)).put("snapshot", snapshot(session))
                .put("status", "live"), "return=minimal")
            preferences.edit { putString(CODE, code); putLong(LAST_PUBLISHED, System.currentTimeMillis()); remove(MESSAGE) }
            loadState()
        }.onSuccess(callback).onFailure { callback(loadState().copy(busy = false, status = CloudShareStatus.OFFLINE, message = friendlyError(it))) }
    }

    fun publish(session: RobinSession, callback: ((CloudShareState) -> Unit)? = null) {
        val code = preferences.getString(CODE, null) ?: return
        executor.execute {
            runCatching {
                val auth = ensureSession()
                request("/rest/v1/live_matches?public_code=eq.$code", "PATCH", auth.token, JSONObject()
                    .put("sequence", sequence(session)).put("snapshot", snapshot(session)).put("status", "live"), "return=minimal")
                preferences.edit { putLong(LAST_PUBLISHED, System.currentTimeMillis()); remove(MESSAGE) }
                loadState()
            }.onSuccess { callback?.invoke(it) }.onFailure {
                preferences.edit { putString(MESSAGE, "Event saved on this phone. Viewers will catch up when online.") }
                callback?.invoke(loadState().copy(status = CloudShareStatus.OFFLINE))
            }
        }
    }

    fun stop(callback: (CloudShareState) -> Unit) {
        val code = preferences.getString(CODE, null) ?: return callback(CloudShareState(message = "Sharing is already off"))
        executor.execute {
            runCatching {
                val auth = ensureSession()
                request("/rest/v1/live_matches?public_code=eq.$code", "DELETE", auth.token, prefer = "return=minimal")
                preferences.edit { clear() }
                CloudShareState(status = CloudShareStatus.STOPPED, message = "Event sharing stopped")
            }.onSuccess(callback).onFailure { callback(loadState().copy(message = friendlyError(it))) }
        }
    }

    private fun snapshot(session: RobinSession): JSONObject = RoundRobinRepository.encode(session)
        .put("kind", "round_robin").put("title", "Round Robin")

    private fun sequence(session: RobinSession) = session.completedGames.toLong() * 1000 + session.courts.sumOf { it.gameNumber }.toLong()

    private fun ensureSession(): Session {
        val token = preferences.getString(TOKEN, null)
        val user = preferences.getString(USER, null)
        val refresh = preferences.getString(REFRESH, null)
        val expiry = preferences.getLong(EXPIRY, 0)
        if (!token.isNullOrBlank() && !user.isNullOrBlank() && expiry > System.currentTimeMillis() / 1000 + 60) return Session(token, user, refresh.orEmpty(), expiry)
        if (!refresh.isNullOrBlank()) runCatching {
            session(JSONObject(request("/auth/v1/token?grant_type=refresh_token", "POST", body = JSONObject().put("refresh_token", refresh))))
        }.getOrNull()?.let { saveSession(it); return it }
        return session(JSONObject(request("/auth/v1/signup", "POST", body = JSONObject()))).also(::saveSession)
    }

    private fun session(json: JSONObject) = Session(json.getString("access_token"), json.getJSONObject("user").getString("id"), json.getString("refresh_token"), json.optLong("expires_at", System.currentTimeMillis() / 1000 + 3600))
    private fun saveSession(value: Session) = preferences.edit { putString(TOKEN, value.token); putString(USER, value.userId); putString(REFRESH, value.refresh); putLong(EXPIRY, value.expiry) }

    private fun request(path: String, method: String, token: String? = null, body: JSONObject? = null, prefer: String? = null): String {
        val connection = URL("$SUPABASE_URL$path").openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method; connection.connectTimeout = 10_000; connection.readTimeout = 10_000
            connection.setRequestProperty("apikey", PUBLISHABLE_KEY); connection.setRequestProperty("Content-Type", "application/json")
            token?.let { connection.setRequestProperty("Authorization", "Bearer $it") }; prefer?.let { connection.setRequestProperty("Prefer", it) }
            body?.let { connection.doOutput = true; connection.outputStream.bufferedWriter().use { writer -> writer.write(it.toString()) } }
            val status = connection.responseCode
            val response = (if (status in 200..299) connection.inputStream else connection.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (status !in 200..299) error("$status: $response")
            return response
        } finally { connection.disconnect() }
    }

    private fun generateCode() = buildString { repeat(8) { append(ALPHABET[random.nextInt(ALPHABET.length)]) } }
    private fun friendlyError(error: Throwable) = if (error.message.orEmpty().contains("duplicate key")) "Please try again to create a code" else "Could not reach event sharing. Check your connection and try again."
    private data class Session(val token: String, val userId: String, val refresh: String, val expiry: Long)

    companion object {
        private const val SUPABASE_URL = "https://qpvxeucamyaupnxepwdl.supabase.co"
        private const val PUBLISHABLE_KEY = "sb_publishable_tv5LzPDm-aXMz4j_4Ll4dA_TVQGkcDH"
        private const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        private const val PREFERENCES = "racket-score-round-robin-cloud"
        private const val CODE = "code"; private const val LAST_PUBLISHED = "last-published"; private const val MESSAGE = "message"
        private const val TOKEN = "token"; private const val USER = "user"; private const val REFRESH = "refresh"; private const val EXPIRY = "expiry"
    }
}
