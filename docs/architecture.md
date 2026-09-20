# Архитектура приложения

```mermaid
flowchart TD
    UI[Jetpack Compose] --> VM[ViewModel]
    VM --> R[Repositories]
    R --> KC[Ktor Client]
    KC --> API[Ktor REST API]
    API --> DB[(SQLite)]
```

Клиент использует однонаправленный поток данных: экран отправляет событие во ViewModel, ViewModel вызывает репозиторий и публикует неизменяемое состояние, а Compose перестраивает затронутую часть интерфейса. `SavedStateHandle` хранит текст запроса; DataStore хранит токен, тему и десять последних открытых терминов.

Сервер разделён на маршруты, репозитории и слой доступа к данным. Пароли хешируются PBKDF2-HMAC-SHA256 с индивидуальной случайной солью. После входа клиент получает подписанный JWT сроком на сутки и передаёт его в заголовке `Authorization`.

## Схема данных

```mermaid
erDiagram
    USERS {
        integer id PK
        text name
        text email UK
        text password_hash
        text password_salt
        text created_at
    }
    CATEGORIES ||--o{ TERMS : contains
    CATEGORIES {
        integer id PK
        text name UK
        text description
    }
    TERMS {
        integer id PK
        text title UK
        text short_definition
        text definition
        text example
        integer category_id FK
    }
```

