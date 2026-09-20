package ru.mirea.reference.server

import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.testApplication
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import ru.mirea.reference.shared.AuthResponse
import ru.mirea.reference.shared.RegistrationRequest
import ru.mirea.reference.shared.SearchResponse
import ru.mirea.reference.shared.TermDto

class ApplicationTest {
    @Test
    fun registrationAndProtectedSearchWorkEndToEnd() {
        val directory = createTempDirectory("reference-test")
        try {
            testApplication {
                application { module(directory.resolve("test.db").toString(), "test-secret-with-sufficient-length") }
                val client = createClient {
                    install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
                }

                val registration = client.post("/api/auth/register") {
                    contentType(ContentType.Application.Json)
                    setBody(RegistrationRequest("Тест", "test@example.ru", "password123"))
                }
                assertEquals(HttpStatusCode.Created, registration.status)
                val auth = registration.body<AuthResponse>()

                val search = client.get("/api/terms") {
                    bearerAuth(auth.token)
                    url { parameters.append("query", "Kotlin") }
                }
                assertEquals(HttpStatusCode.OK, search.status)
                val results = search.body<SearchResponse>()
                assertTrue(results.items.isNotEmpty())

                val details = client.get("/api/terms/${results.items.first().id}") { bearerAuth(auth.token) }
                assertEquals(HttpStatusCode.OK, details.status)
                assertTrue(details.body<TermDto>().definition.isNotBlank())
            }
        } finally {
            directory.toFile().deleteRecursively()
        }
    }

    @Test
    fun protectedRouteRejectsAnonymousRequest() = testApplication {
        val directory = createTempDirectory("reference-test")
        try {
            application { module(directory.resolve("test.db").toString(), "test-secret-with-sufficient-length") }
            assertEquals(HttpStatusCode.Unauthorized, client.get("/api/terms?query=Ktor").status)
        } finally {
            directory.toFile().deleteRecursively()
        }
    }
}
