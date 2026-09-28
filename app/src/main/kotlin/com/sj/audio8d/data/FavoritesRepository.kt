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

private val Context.favoritesStore by preferencesDataStore(name = "audio8d_favorites")

/** Persists favorite song IDs locally as a JSON-encoded set. No account/cloud sync. */
class FavoritesRepository(private val context: Context) {

    private val key = stringPreferencesKey("favorite_ids")
    private val json = Json { ignoreUnknownKeys = true }

    val favoriteIds: Flow<Set<Long>> = context.favoritesStore.data.map { prefs ->
        val raw = prefs[key] ?: return@map emptySet()
        runCatching { json.decodeFromString<Set<Long>>(raw) }.getOrDefault(emptySet())
    }

    suspend fun toggleFavorite(songId: Long) {
        context.favoritesStore.edit { prefs ->
            val current = prefs[key]?.let { runCatching { json.decodeFromString<Set<Long>>(it) }.getOrDefault(emptySet()) }
                ?: emptySet()
            val updated = if (songId in current) current - songId else current + songId
            prefs[key] = json.encodeToString(updated)
        }
    }
}
