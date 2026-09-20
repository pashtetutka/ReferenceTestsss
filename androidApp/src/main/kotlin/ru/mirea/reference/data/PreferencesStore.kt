package ru.mirea.reference.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

class PreferencesStore(private val dataStore: DataStore<Preferences>) {
    private val json = Json { ignoreUnknownKeys = true }
    val isAuthorized: Flow<Boolean> = dataStore.data.map { !it[tokenKey].isNullOrBlank() }
    val darkTheme: Flow<Boolean> = dataStore.data.map { it[darkThemeKey] ?: false }
    val history: Flow<List<SearchHistoryItem>> = dataStore.data.map { preferences ->
        preferences[historyKey]?.let(::decodeHistory).orEmpty()
    }

    suspend fun token(): String? = dataStore.data.first()[tokenKey]

    suspend fun saveToken(value: String) {
        dataStore.edit { it[tokenKey] = value }
    }

    suspend fun clearToken() {
        dataStore.edit { it.remove(tokenKey) }
    }

    suspend fun setDarkTheme(enabled: Boolean) {
        dataStore.edit { it[darkThemeKey] = enabled }
    }

    suspend fun updateHistory(transform: (List<SearchHistoryItem>) -> List<SearchHistoryItem>) {
        dataStore.edit { preferences ->
            val current = preferences[historyKey]?.let(::decodeHistory).orEmpty()
            preferences[historyKey] = json.encodeToString(ListSerializer(SearchHistoryItem.serializer()), transform(current))
        }
    }

    private fun decodeHistory(value: String): List<SearchHistoryItem> = runCatching {
        json.decodeFromString(ListSerializer(SearchHistoryItem.serializer()), value)
    }.getOrDefault(emptyList())

    private companion object {
        val tokenKey = stringPreferencesKey("access_token")
        val darkThemeKey = booleanPreferencesKey("dark_theme")
        val historyKey = stringPreferencesKey("search_history")
    }
}

@Serializable
data class SearchHistoryItem(
    val termId: Long,
    val title: String,
    val categoryName: String,
)

