package ru.mirea.reference.server

import java.nio.file.Files
import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DatabaseFactory(private val databasePath: String) {
    init {
        Class.forName("org.sqlite.JDBC")
        Path.of(databasePath).parent?.let(Files::createDirectories)
    }

    private fun connection(): Connection = DriverManager.getConnection("jdbc:sqlite:$databasePath").apply {
        createStatement().use { statement ->
            statement.execute("PRAGMA foreign_keys = ON")
            statement.execute("PRAGMA journal_mode = WAL")
        }
    }

    suspend fun <T> query(block: (Connection) -> T): T = withContext(Dispatchers.IO) {
        connection().use(block)
    }

    suspend fun initialize() = query { connection ->
        connection.createStatement().use { statement ->
            statement.executeUpdate(
                """
                CREATE TABLE IF NOT EXISTS users (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    name TEXT NOT NULL,
                    email TEXT NOT NULL UNIQUE COLLATE NOCASE,
                    password_hash TEXT NOT NULL,
                    password_salt TEXT NOT NULL,
                    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """.trimIndent(),
            )
            statement.executeUpdate(
                """
                CREATE TABLE IF NOT EXISTS categories (
                    id INTEGER PRIMARY KEY,
                    name TEXT NOT NULL UNIQUE,
                    description TEXT NOT NULL
                )
                """.trimIndent(),
            )
            statement.executeUpdate(
                """
                CREATE TABLE IF NOT EXISTS terms (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    title TEXT NOT NULL UNIQUE COLLATE NOCASE,
                    short_definition TEXT NOT NULL,
                    definition TEXT NOT NULL,
                    example TEXT NOT NULL,
                    category_id INTEGER NOT NULL REFERENCES categories(id)
                )
                """.trimIndent(),
            )
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_terms_category ON terms(category_id)")
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_terms_title ON terms(title COLLATE NOCASE)")
        }
        seed(connection)
    }

    private fun seed(connection: Connection) {
        val categories = listOf(
            Triple(1L, "Kotlin", "Язык Kotlin и его базовые конструкции"),
            Triple(2L, "Android", "Компоненты и архитектура Android-приложений"),
            Triple(3L, "Сеть", "HTTP, API и клиент-серверное взаимодействие"),
            Triple(4L, "Базы данных", "Хранение и обработка структурированных данных"),
            Triple(5L, "Архитектура", "Подходы к проектированию программных систем"),
        )
        connection.prepareStatement(
            "INSERT OR IGNORE INTO categories(id, name, description) VALUES (?, ?, ?)",
        ).use { statement ->
            categories.forEach { (id, name, description) ->
                statement.setLong(1, id)
                statement.setString(2, name)
                statement.setString(3, description)
                statement.addBatch()
            }
            statement.executeBatch()
        }

        val terms = listOf(
            SeedTerm("Корутина", "Легковесная единица асинхронного выполнения", "Корутина приостанавливает выполнение без блокировки потока и возобновляется после готовности результата.", "viewModelScope.launch { repository.search(query) }", 1),
            SeedTerm("Null safety", "Система контроля nullable-типов", "Kotlin различает типы, допускающие null, и типы, для которых отсутствие значения запрещено на уровне компилятора.", "val title: String? = response.title", 1),
            SeedTerm("Data class", "Класс для хранения данных", "Data class автоматически получает equals, hashCode, toString, componentN и copy на основе свойств первичного конструктора.", "data class User(val id: Long, val name: String)", 1),
            SeedTerm("Jetpack Compose", "Декларативный инструментарий интерфейса Android", "Compose формирует интерфейс из функций с аннотацией Composable и обновляет только зависящие от изменившегося состояния участки.", "@Composable fun TermCard(term: TermSummaryDto)", 2),
            SeedTerm("ViewModel", "Хранилище состояния экрана", "ViewModel отделяет состояние и бизнес-логику экрана от Activity и переживает конфигурационные изменения.", "class SearchViewModel : ViewModel()", 2),
            SeedTerm("SavedStateHandle", "Механизм сохранения простого состояния", "SavedStateHandle позволяет восстановить поисковый запрос после пересоздания Activity или процесса.", "savedStateHandle[QUERY_KEY] = value", 2),
            SeedTerm("REST", "Архитектурный стиль сетевого API", "REST использует ресурсы, стандартную семантику HTTP-методов и представления данных для взаимодействия клиента и сервера.", "GET /api/terms?query=ktor", 3),
            SeedTerm("JWT", "Компактный формат подписанного токена", "JSON Web Token содержит набор утверждений и цифровую подпись, позволяющую серверу проверить целостность токена.", "Authorization: Bearer eyJ...", 3),
            SeedTerm("HTTP 401", "Ответ об отсутствии действительной аутентификации", "Сервер возвращает 401, когда защищённый ресурс запрошен без действительного токена доступа.", "call.respond(HttpStatusCode.Unauthorized)", 3),
            SeedTerm("Транзакция", "Неделимая последовательность операций с БД", "Транзакция обеспечивает согласованное выполнение нескольких операций и откатывает изменения при ошибке.", "connection.autoCommit = false", 4),
            SeedTerm("Индекс", "Структура ускорения поиска в таблице", "Индекс хранит упорядоченные значения выбранных столбцов и уменьшает объём данных, просматриваемых при запросе.", "CREATE INDEX idx_terms_title ON terms(title)", 4),
            SeedTerm("SQLite", "Встраиваемая реляционная СУБД", "SQLite хранит базу в одном файле и работает внутри процесса сервера без отдельной службы управления базой.", "jdbc:sqlite:data/reference.db", 4),
            SeedTerm("MVVM", "Разделение интерфейса, состояния и модели", "В MVVM представление наблюдает состояние ViewModel, а доступ к данным выполняется через модель и репозитории.", "Screen -> ViewModel -> Repository -> API", 5),
            SeedTerm("Repository", "Абстракция источников данных", "Репозиторий скрывает сетевые и локальные источники от слоя представления и предоставляет единый интерфейс операций.", "interface TermsRepository { suspend fun search(query: String) }", 5),
            SeedTerm("DTO", "Объект передачи данных", "DTO фиксирует контракт обмена между клиентом и сервером и не содержит логики пользовательского интерфейса.", "@Serializable data class TermDto(...)", 5),
        )
        connection.prepareStatement(
            """
            INSERT OR IGNORE INTO terms(title, short_definition, definition, example, category_id)
            VALUES (?, ?, ?, ?, ?)
            """.trimIndent(),
        ).use { statement ->
            terms.forEach { term ->
                statement.setString(1, term.title)
                statement.setString(2, term.shortDefinition)
                statement.setString(3, term.definition)
                statement.setString(4, term.example)
                statement.setLong(5, term.categoryId)
                statement.addBatch()
            }
            statement.executeBatch()
        }
    }
}

private data class SeedTerm(
    val title: String,
    val shortDefinition: String,
    val definition: String,
    val example: String,
    val categoryId: Long,
)

