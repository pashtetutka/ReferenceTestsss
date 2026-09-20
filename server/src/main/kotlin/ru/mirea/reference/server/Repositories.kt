package ru.mirea.reference.server

import java.sql.Connection
import java.sql.SQLException
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import ru.mirea.reference.shared.CategoryDto
import ru.mirea.reference.shared.TermDto
import ru.mirea.reference.shared.TermSummaryDto
import ru.mirea.reference.shared.UserDto

class AuthRepository(private val database: DatabaseFactory) {
    suspend fun register(name: String, email: String, password: String): UserDto {
        val normalizedEmail = email.trim().lowercase()
        val salt = ByteArray(16).also(SecureRandom()::nextBytes)
        val hash = PasswordHasher.hash(password, salt)
        return try {
            database.query { connection ->
                connection.prepareStatement(
                    "INSERT INTO users(name, email, password_hash, password_salt) VALUES (?, ?, ?, ?)",
                    java.sql.Statement.RETURN_GENERATED_KEYS,
                ).use { statement ->
                    statement.setString(1, name.trim())
                    statement.setString(2, normalizedEmail)
                    statement.setString(3, Base64.getEncoder().encodeToString(hash))
                    statement.setString(4, Base64.getEncoder().encodeToString(salt))
                    statement.executeUpdate()
                    statement.generatedKeys.use { keys ->
                        check(keys.next()) { "Не удалось получить идентификатор пользователя" }
                        UserDto(keys.getLong(1), name.trim(), normalizedEmail)
                    }
                }
            }
        } catch (error: SQLException) {
            if (error.message.orEmpty().contains("UNIQUE", ignoreCase = true)) {
                throw DuplicateEmailException()
            }
            throw error
        }
    }

    suspend fun authenticate(email: String, password: String): UserDto? = database.query { connection ->
        connection.prepareStatement(
            "SELECT id, name, email, password_hash, password_salt FROM users WHERE email = ? COLLATE NOCASE",
        ).use { statement ->
            statement.setString(1, email.trim())
            statement.executeQuery().use { result ->
                if (!result.next()) return@query null
                val salt = Base64.getDecoder().decode(result.getString("password_salt"))
                val expected = Base64.getDecoder().decode(result.getString("password_hash"))
                if (!PasswordHasher.matches(password, salt, expected)) return@query null
                UserDto(result.getLong("id"), result.getString("name"), result.getString("email"))
            }
        }
    }
}

class TermsRepository(private val database: DatabaseFactory) {
    suspend fun categories(): List<CategoryDto> = database.query { connection ->
        connection.prepareStatement("SELECT id, name, description FROM categories ORDER BY name").use { statement ->
            statement.executeQuery().use { result ->
                buildList {
                    while (result.next()) {
                        add(CategoryDto(result.getLong("id"), result.getString("name"), result.getString("description")))
                    }
                }
            }
        }
    }

    suspend fun search(query: String, categoryId: Long?): List<TermSummaryDto> = database.query { connection ->
        val sql = buildString {
            append(
                """
                SELECT t.id, t.title, t.short_definition, t.category_id, c.name AS category_name
                FROM terms t JOIN categories c ON c.id = t.category_id
                WHERE (LOWER(t.title) LIKE ? OR LOWER(t.short_definition) LIKE ? OR LOWER(t.definition) LIKE ?)
                """.trimIndent(),
            )
            if (categoryId != null) append(" AND t.category_id = ?")
            append(" ORDER BY CASE WHEN LOWER(t.title) = ? THEN 0 WHEN LOWER(t.title) LIKE ? THEN 1 ELSE 2 END, t.title LIMIT 50")
        }
        connection.prepareStatement(sql).use { statement ->
            val normalized = query.trim().lowercase()
            val contains = "%$normalized%"
            statement.setString(1, contains)
            statement.setString(2, contains)
            statement.setString(3, contains)
            var index = 4
            if (categoryId != null) statement.setLong(index++, categoryId)
            statement.setString(index++, normalized)
            statement.setString(index, "$normalized%")
            statement.executeQuery().use { result -> result.toSummaries() }
        }
    }

    suspend fun byId(id: Long): TermDto? = database.query { connection ->
        connection.prepareStatement(
            """
            SELECT t.id, t.title, t.short_definition, t.definition, t.example,
                   t.category_id, c.name AS category_name
            FROM terms t JOIN categories c ON c.id = t.category_id WHERE t.id = ?
            """.trimIndent(),
        ).use { statement ->
            statement.setLong(1, id)
            statement.executeQuery().use { result ->
                if (!result.next()) return@query null
                TermDto(
                    id = result.getLong("id"),
                    title = result.getString("title"),
                    shortDefinition = result.getString("short_definition"),
                    definition = result.getString("definition"),
                    example = result.getString("example"),
                    categoryId = result.getLong("category_id"),
                    categoryName = result.getString("category_name"),
                )
            }
        }
    }

    private fun java.sql.ResultSet.toSummaries(): List<TermSummaryDto> = buildList {
        while (next()) {
            add(
                TermSummaryDto(
                    id = getLong("id"),
                    title = getString("title"),
                    shortDefinition = getString("short_definition"),
                    categoryId = getLong("category_id"),
                    categoryName = getString("category_name"),
                ),
            )
        }
    }
}

private object PasswordHasher {
    private const val iterations = 600_000
    private const val keyLength = 256

    fun hash(password: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, iterations, keyLength)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    fun matches(password: String, salt: ByteArray, expected: ByteArray): Boolean {
        val actual = hash(password, salt)
        var difference = actual.size xor expected.size
        for (index in actual.indices) {
            difference = difference or (actual[index].toInt() xor expected.getOrElse(index) { 0 }.toInt())
        }
        return difference == 0
    }
}

class DuplicateEmailException : IllegalArgumentException("Пользователь с таким адресом уже существует")
