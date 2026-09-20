package ru.mirea.reference.data

import kotlinx.coroutines.flow.Flow
import ru.mirea.reference.shared.AuthResponse
import ru.mirea.reference.shared.CategoryDto
import ru.mirea.reference.shared.LoginRequest
import ru.mirea.reference.shared.RegistrationRequest
import ru.mirea.reference.shared.TermDto
import ru.mirea.reference.shared.TermSummaryDto

class AuthRepository(private val api: ApiService, private val preferences: PreferencesStore) {
    val isAuthorized: Flow<Boolean> = preferences.isAuthorized

    suspend fun login(email: String, password: String): AuthResponse =
        api.login(LoginRequest(email.trim(), password)).also { preferences.saveToken(it.token) }

    suspend fun register(name: String, email: String, password: String): AuthResponse =
        api.register(RegistrationRequest(name.trim(), email.trim(), password)).also { preferences.saveToken(it.token) }

    suspend fun logout() = preferences.clearToken()
}

class TermsRepository(private val api: ApiService) {
    suspend fun categories(): List<CategoryDto> = api.categories()
    suspend fun search(query: String, categoryId: Long?): List<TermSummaryDto> = api.search(query, categoryId).items
    suspend fun term(id: Long): TermDto = api.term(id)
}

class HistoryRepository(private val preferences: PreferencesStore) {
    val items: Flow<List<SearchHistoryItem>> = preferences.history

    suspend fun add(term: TermSummaryDto) {
        preferences.updateHistory { current ->
            (listOf(SearchHistoryItem(term.id, term.title, term.categoryName)) + current.filterNot { it.termId == term.id })
                .take(maxItems)
        }
    }

    suspend fun clear() = preferences.updateHistory { emptyList() }

    private companion object {
        const val maxItems = 10
    }
}
