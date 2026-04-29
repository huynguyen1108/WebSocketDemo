package com.example.core.security

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Serializer
import com.example.core.common.ApplicationScope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
private data class SessionData(
    val token: String = "",
    val userId: String = "",
    val serverUrl: String = "",
)

private class SessionDataSerializer(private val crypto: CryptoManager) : Serializer<SessionData> {
    override val defaultValue = SessionData()
    private val delegate = serializer<SessionData>()

    override suspend fun readFrom(input: InputStream): SessionData = try {
        val encrypted = input.readBytes().toString(Charsets.UTF_8)
        if (encrypted.isBlank()) defaultValue
        else Json.decodeFromString(delegate, crypto.decrypt(encrypted))
    } catch (_: Exception) {
        defaultValue
    }

    override suspend fun writeTo(t: SessionData, output: OutputStream) {
        output.write(crypto.encrypt(Json.encodeToString(delegate, t)).toByteArray(Charsets.UTF_8))
    }
}

@Singleton
class TokenDataStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val crypto: CryptoManager,
    @ApplicationScope private val scope: CoroutineScope,
) : TokenStore {

    private val ds: DataStore<SessionData> = DataStoreFactory.create(
        serializer = SessionDataSerializer(crypto),
        produceFile = { context.dataDir.resolve("datastore/session.json") },
        scope = scope,
    )

    @Volatile private var cached: SessionData? = null
    private val lock = Any()

    init {
        // Pre-warm in background so sync getters typically hit the cache.
        scope.launch { ensureLoaded() }
    }

    private fun ensureLoaded(): SessionData = cached ?: synchronized(lock) {
        cached ?: runBlocking { ds.data.first() }.also { cached = it }
    }

    override fun getToken(): String? = ensureLoaded().token.takeIf { it.isNotEmpty() }
    override fun getUserId(): String? = ensureLoaded().userId.takeIf { it.isNotEmpty() }
    override fun getServerUrl(): String? = ensureLoaded().serverUrl.takeIf { it.isNotEmpty() }
    override fun hasSession(): Boolean = ensureLoaded().token.isNotEmpty()

    override fun saveSession(token: String, userId: String, serverUrl: String) {
        val updated = SessionData(token, userId, serverUrl)
        cached = updated
        scope.launch { ds.updateData { updated } }
    }

    override fun clearSession() {
        cached = SessionData()
        scope.launch { ds.updateData { SessionData() } }
    }
}
