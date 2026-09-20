package ru.mirea.reference.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.mirea.reference.data.SearchHistoryItem
import ru.mirea.reference.shared.TermSummaryDto

@Composable
fun LoginScreen(viewModel: AuthViewModel, onLoggedIn: () -> Unit, onRegister: () -> Unit) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val state by viewModel.state.collectAsStateWithLifecycle()
    AuthLayout(title = "ИТ Справочник", subtitle = "Войдите, чтобы искать термины") {
        OutlinedTextField(email, { email = it }, label = { Text("Электронная почта") }, singleLine = true, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(password, { password = it }, label = { Text("Пароль") }, singleLine = true, modifier = Modifier.fillMaxWidth(), visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
        state.error?.let { ErrorText(it) }
        Spacer(Modifier.height(20.dp))
        Button(onClick = { viewModel.login(email, password, onLoggedIn) }, enabled = !state.loading, modifier = Modifier.fillMaxWidth()) {
            if (state.loading) CircularProgressIndicator(Modifier.height(20.dp).width(20.dp), strokeWidth = 2.dp) else Text("Войти")
        }
        TextButton(onClick = onRegister, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Создать аккаунт") }
    }
}

@Composable
fun RegisterScreen(viewModel: AuthViewModel, onRegistered: () -> Unit, onBack: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var repeat by remember { mutableStateOf("") }
    val state by viewModel.state.collectAsStateWithLifecycle()
    AuthLayout(title = "Регистрация", subtitle = "Заполните данные нового аккаунта", onBack = onBack) {
        OutlinedTextField(name, { name = it }, label = { Text("Имя") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(email, { email = it }, label = { Text("Электронная почта") }, singleLine = true, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(password, { password = it }, label = { Text("Пароль от 8 символов") }, singleLine = true, modifier = Modifier.fillMaxWidth(), visualTransformation = PasswordVisualTransformation())
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(repeat, { repeat = it }, label = { Text("Повторите пароль") }, singleLine = true, modifier = Modifier.fillMaxWidth(), visualTransformation = PasswordVisualTransformation())
        state.error?.let { ErrorText(it) }
        Spacer(Modifier.height(20.dp))
        Button(onClick = { viewModel.register(name, email, password, repeat, onRegistered) }, enabled = !state.loading, modifier = Modifier.fillMaxWidth()) {
            if (state.loading) CircularProgressIndicator(Modifier.height(20.dp).width(20.dp), strokeWidth = 2.dp) else Text("Зарегистрироваться")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(viewModel: SearchViewModel, onOpenTerm: (Long) -> Unit, onSettings: () -> Unit) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val content by viewModel.content.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    var focused by remember { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    Scaffold(
        topBar = {
            TopAppBar(title = { Text("ИТ Справочник") }, actions = { IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, "Настройки") } })
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = query,
                onValueChange = viewModel::updateQuery,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).onFocusChanged { focused = it.isFocused },
                placeholder = { Text("Введите термин, например Ktor") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = if (query.isNotEmpty()) {
                    {
                        IconButton(onClick = { viewModel.clearQuery(); keyboard?.hide(); focusManager.clearFocus() }) {
                            Icon(Icons.Default.Clear, "Очистить")
                        }
                    }
                } else null,
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { viewModel.search(); keyboard?.hide(); focusManager.clearFocus() }),
            )
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(categories, key = { it.id }) { category ->
                    AssistChip(onClick = { viewModel.selectCategory(category.id) }, label = { Text(if (selectedCategory == category.id) "✓ ${category.name}" else category.name) })
                }
            }
            when {
                focused && query.isBlank() && history.isNotEmpty() -> HistoryList(history, viewModel::clearHistory) { viewModel.openHistory(it, onOpenTerm) }
                else -> SearchContent(content, viewModel::search) { viewModel.open(it, onOpenTerm) }
            }
        }
    }
}

@Composable
private fun SearchContent(state: SearchContentState, onRetry: () -> Unit, onOpen: (TermSummaryDto) -> Unit) {
    when (state) {
        SearchContentState.Idle -> Placeholder("Найдите нужное понятие", "Поиск выполняется по названию и определению")
        SearchContentState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        SearchContentState.Empty -> Placeholder("Ничего не найдено", "Измените запрос или выберите другую категорию")
        is SearchContentState.Error -> Placeholder("Поиск не выполнен", state.message) { OutlinedButton(onClick = onRetry) { Text("Обновить") } }
        is SearchContentState.Results -> LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(state.items, key = { it.id }) { term ->
                Card(onClick = { onOpen(term) }, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(term.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(term.categoryName, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(6.dp))
                        Text(term.shortDefinition, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryList(items: List<SearchHistoryItem>, onClear: () -> Unit, onOpen: (SearchHistoryItem) -> Unit) {
    LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("История поиска", style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = onClear) { Text("Очистить историю") }
            }
        }
        items(items, key = { it.termId }) { item ->
            Card(onClick = { onOpen(item) }, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.History, null)
                    Spacer(Modifier.width(12.dp))
                    Column { Text(item.title); Text(item.categoryName, style = MaterialTheme.typography.labelSmall) }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TermDetailScreen(viewModel: DetailViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Scaffold(topBar = { TopAppBar(title = { Text("Термин") }, navigationIcon = { BackButton(onBack) }) }) { padding ->
        when (val value = state) {
            DetailUiState.Loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            is DetailUiState.Error -> Box(Modifier.fillMaxSize().padding(padding)) { Placeholder("Не удалось загрузить термин", value.message) }
            is DetailUiState.Content -> LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                item { Text(value.term.title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold) }
                item { Text(value.term.categoryName, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge) }
                item { Text(value.term.shortDefinition, style = MaterialTheme.typography.titleMedium) }
                item { Text("Определение", fontWeight = FontWeight.Bold); Text(value.term.definition) }
                item { Text("Пример", fontWeight = FontWeight.Bold); Card { Text(value.term.example, Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium) } }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel, onBack: () -> Unit, onLoggedOut: () -> Unit) {
    val darkTheme by viewModel.darkTheme.collectAsStateWithLifecycle()
    Scaffold(topBar = { TopAppBar(title = { Text("Настройки") }, navigationIcon = { BackButton(onBack) }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text("Тёмная тема", style = MaterialTheme.typography.titleMedium); Text("Изменить цветовое оформление приложения") }
                Switch(checked = darkTheme, onCheckedChange = viewModel::setDarkTheme)
            }
            OutlinedButton(onClick = { viewModel.logout(onLoggedOut) }, modifier = Modifier.fillMaxWidth()) { Text("Выйти из аккаунта") }
        }
    }
}

@Composable
private fun AuthLayout(title: String, subtitle: String, onBack: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 28.dp), verticalArrangement = Arrangement.Center) {
        if (onBack != null) BackButton(onBack)
        Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(subtitle, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(28.dp))
        content()
    }
}

@Composable
private fun Placeholder(title: String, text: String, action: (@Composable () -> Unit)? = null) {
    Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
        if (action != null) { Spacer(Modifier.height(16.dp)); action() }
    }
}

@Composable
private fun ErrorText(message: String) { Text(message, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 10.dp)) }

@Composable
private fun BackButton(onBack: () -> Unit) { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") } }
