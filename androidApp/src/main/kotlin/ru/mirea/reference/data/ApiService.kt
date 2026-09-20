package ru.mirea.reference.data

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.ResponseException
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import ru.mirea.reference.shared.ApiError
import ru.mirea.reference.shared.AuthResponse
import ru.mirea.reference.shared.CategoryDto
import ru.mirea.reference.shared.LoginRequest
import ru.mirea.reference.shared.RegistrationRequest
import ru.mirea.reference.shared.SearchResponse
import ru.mirea.reference.shared.TermDto

class ApiService(baseUrl: String, private val preferences: PreferencesStore) {
    private val root = baseUrl.trimEnd('/')
    private val client = HttpClient(Android) {
        expectSuccess = true
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true; explicitNulls = false })
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 15_000
            connectTimeoutMillis = 10_000
        }
        install(Logging) {
            level = LogLevel.INFO
            sanitizeHeader { it.equals("Authorization", ignoreCase = true) }
        }
    }

    suspend fun register(payload: RegistrationRequest): AuthResponse = request {
        client.post("$root/api/auth/register") { contentType(ContentType.Application.Json); setBody(payload) }.body()
    }

    suspend fun login(payload: LoginRequest): AuthResponse = request {
        client.post("$root/api/auth/login") { contentType(ContentType.Application.Json); setBody(payload) }.body()
    }

    suspend fun categories(): List<CategoryDto> = authorized {
        client.get("$root/api/categories") { bearerAuth(it) }.body()
    }

    suspend fun search(query: String, categoryId: Long?): SearchResponse = authorized { token ->
        client.get("$root/api/terms") {
            bearerAuth(token)
            parameter("query", query)
            categoryId?.let { parameter("categoryId", it) }
        }.body()
    }

    suspend fun term(id: Long): TermDto = authorized {
        client.get("$root/api/terms/$id") { bearerAuth(it) }.body()
    }

    private suspend fun <T> authorized(block: suspend (String) -> T): T {
        val token = preferences.token() ?: throw ApiException("Сеанс завершён. Войдите снова")
        return request { block(token) }
    }

    private suspend fun <T> request(block: suspend () -> T): T = try {
        block()
    } catch (error: ResponseException) {
        val apiError = runCatching { error.response.body<ApiError>() }.getOrNull()
        throw ApiException(apiError?.message ?: "Сервер вернул ошибку ${error.response.status.value}", error)
    } catch (error: ApiException) {
        throw error
    } catch (error: Throwable) {
        throw ApiException("Не удалось подключиться к серверу", error)
    }
}

class ApiException(message: String, cause: Throwable? = null) : Exception(message, cause)
