package ru.mirea.reference.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.mirea.reference.data.AuthRepository
import ru.mirea.reference.data.HistoryRepository
import ru.mirea.reference.data.PreferencesStore
import ru.mirea.reference.data.SearchHistoryItem
import ru.mirea.reference.data.TermsRepository
import ru.mirea.reference.shared.CategoryDto
import ru.mirea.reference.shared.TermDto
import ru.mirea.reference.shared.TermSummaryDto

data class AuthUiState(val loading: Boolean = false, val error: String? = null)

class AuthViewModel(private val repository: AuthRepository) : ViewModel() {
    private val _state = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    fun login(email: String, password: String, onSuccess: () -> Unit) = submit(onSuccess) {
        require(email.isNotBlank() && password.isNotBlank()) { "Заполните адрес и пароль" }
        repository.login(email, password)
    }

    fun register(name: String, email: String, password: String, repeatPassword: String, onSuccess: () -> Unit) = submit(onSuccess) {
        require(password == repeatPassword) { "Пароли не совпадают" }
        repository.register(name, email, password)
    }

    private fun submit(onSuccess: () -> Unit, action: suspend () -> Unit) {
        if (_state.value.loading) return
        viewModelScope.launch {
            _state.value = AuthUiState(loading = true)
            runCatching { action() }
                .onSuccess { _state.value = AuthUiState(); onSuccess() }
                .onFailure { _state.value = AuthUiState(error = it.message ?: "Неизвестная ошибка") }
        }
    }
}

sealed interface SearchContentState {
    data object Idle : SearchContentState
    data object Loading : SearchContentState
    data class Results(val items: List<TermSummaryDto>) : SearchContentState
    data object Empty : SearchContentState
    data class Error(val message: String) : SearchContentState
}

class SearchViewModel(
    private val savedStateHandle: SavedStateHandle,
    private val repository: TermsRepository,
    private val historyRepository: HistoryRepository,
) : ViewModel() {
    val query = savedStateHandle.getStateFlow(queryKey, "")
    private val _content = MutableStateFlow<SearchContentState>(SearchContentState.Idle)
    val content: StateFlow<SearchContentState> = _content.asStateFlow()
    private val _categories = MutableStateFlow<List<CategoryDto>>(emptyList())
    val categories: StateFlow<List<CategoryDto>> = _categories.asStateFlow()
    private val _selectedCategory = MutableStateFlow<Long?>(null)
    val selectedCategory: StateFlow<Long?> = _selectedCategory.asStateFlow()
    val history: StateFlow<List<SearchHistoryItem>> = historyRepository.items
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch { runCatching { repository.categories() }.onSuccess { _categories.value = it } }
        if (query.value.isNotBlank()) search()
    }

    fun updateQuery(value: String) {
        savedStateHandle[queryKey] = value
        if (value.isBlank()) _content.value = SearchContentState.Idle
    }

    fun clearQuery() {
        savedStateHandle[queryKey] = ""
        _content.value = SearchContentState.Idle
    }

    fun selectCategory(id: Long?) {
        _selectedCategory.value = if (_selectedCategory.value == id) null else id
        if (query.value.isNotBlank()) search()
    }

    fun search() {
        val request = query.value.trim()
        if (request.isBlank()) return
        viewModelScope.launch {
            _content.value = SearchContentState.Loading
            runCatching { repository.search(request, _selectedCategory.value) }
                .onSuccess { _content.value = if (it.isEmpty()) SearchContentState.Empty else SearchContentState.Results(it) }
                .onFailure { _content.value = SearchContentState.Error(it.message ?: "Не удалось выполнить поиск") }
        }
    }

    fun open(term: TermSummaryDto, onOpen: (Long) -> Unit) {
        viewModelScope.launch { historyRepository.add(term); onOpen(term.id) }
    }

    fun openHistory(item: SearchHistoryItem, onOpen: (Long) -> Unit) = onOpen(item.termId)

    fun clearHistory() {
        viewModelScope.launch { historyRepository.clear() }
    }

    private companion object { const val queryKey = "search_query" }
}

sealed interface DetailUiState {
    data object Loading : DetailUiState
    data class Content(val term: TermDto) : DetailUiState
    data class Error(val message: String) : DetailUiState
}

class DetailViewModel(id: Long, repository: TermsRepository) : ViewModel() {
    private val _state = MutableStateFlow<DetailUiState>(DetailUiState.Loading)
    val state: StateFlow<DetailUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            runCatching { repository.term(id) }
                .onSuccess { _state.value = DetailUiState.Content(it) }
                .onFailure { _state.value = DetailUiState.Error(it.message ?: "Не удалось загрузить термин") }
        }
    }
}

class SettingsViewModel(private val preferences: PreferencesStore, private val authRepository: AuthRepository) : ViewModel() {
    val darkTheme = preferences.darkTheme.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    fun setDarkTheme(enabled: Boolean) { viewModelScope.launch { preferences.setDarkTheme(enabled) } }
    fun logout(onComplete: () -> Unit) { viewModelScope.launch { authRepository.logout(); onComplete() } }
}

class ViewModelFactory(private val creator: (CreationExtras) -> ViewModel) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T = creator(extras) as T
}

fun CreationExtras.savedStateHandle(): SavedStateHandle = createSavedStateHandle()

