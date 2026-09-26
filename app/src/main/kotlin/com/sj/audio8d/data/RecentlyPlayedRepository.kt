package com.sj.audio8d.data

import android.content.Context
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.recentStore by preferencesDataStore(name = "audio8d_recent")
private const val MAX_RECENT = 30

/** Lightweight, locally persisted list of recently played song IDs, most-recent first. */
class RecentlyPlayedRepository(private val context: Context) {

    private val key = stringPreferencesKey("recent_ids")
    private val json = Json { ignoreUnknownKeys = true }

    val recentIds: Flow<List<Long>> = context.recentStore.data.map { prefs ->
        val raw = prefs[key] ?: return@map emptyList()
        runCatching { json.decodeFromString<List<Long>>(raw) }.getOrDefault(emptyList())
    }

    suspend fun recordPlayed(songId: Long) {
        context.recentStore.edit { prefs ->
            val current = prefs[key]?.let { runCatching { json.decodeFromString<List<Long>>(it) }.getOrDefault(emptyList()) }
                ?: emptyList()
            val updated = (listOf(songId) + current.filterNot { it == songId }).take(MAX_RECENT)
            prefs[key] = json.encodeToString(updated)
        }
    }
}
