package ru.mirea.reference.server

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import java.time.Instant
import java.util.Date
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import ru.mirea.reference.shared.ApiError
import ru.mirea.reference.shared.AuthResponse
import ru.mirea.reference.shared.LoginRequest
import ru.mirea.reference.shared.RegistrationRequest
import ru.mirea.reference.shared.SearchResponse

fun main() {
    embeddedServer(Netty, port = 8080, host = "0.0.0.0", module = Application::module).start(wait = true)
}

@JvmOverloads
fun Application.module(
    databasePath: String = System.getenv("DB_PATH") ?: "data/reference.db",
    jwtSecret: String = System.getenv("JWT_SECRET") ?: "change-this-secret-before-production",
) {
    val database = DatabaseFactory(databasePath)
    runBlocking { database.initialize() }
    val authRepository = AuthRepository(database)
    val termsRepository = TermsRepository(database)
    val tokenService = TokenService(jwtSecret)
    val applicationLog = environment.log

    install(CallLogging)
    install(ContentNegotiation) {
        json(Json { ignoreUnknownKeys = true; explicitNulls = false })
    }
    install(CORS) {
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowHeader(HttpHeaders.Authorization)
        allowHeader(HttpHeaders.ContentType)
        anyHost()
    }
    install(StatusPages) {
        exception<DuplicateEmailException> { call, error ->
            call.respond(HttpStatusCode.Conflict, ApiError("email_exists", error.message.orEmpty()))
        }
        exception<IllegalArgumentException> { call, error ->
            call.respond(HttpStatusCode.BadRequest, ApiError("validation_error", error.message ?: "Некорректный запрос"))
        }
        exception<Throwable> { call, error ->
            applicationLog.error("Необработанная ошибка", error)
            call.respond(HttpStatusCode.InternalServerError, ApiError("internal_error", "Внутренняя ошибка сервера"))
        }
    }
    install(Authentication) {
        jwt("auth-jwt") {
            realm = "it-reference"
            verifier(tokenService.verifier)
            validate { credential ->
                credential.payload.getClaim("userId").asLong()?.let { JWTPrincipal(credential.payload) }
            }
            challenge { _, _ -> call.respond(HttpStatusCode.Unauthorized, ApiError("unauthorized", "Требуется авторизация")) }
        }
    }

    routing {
        get("/health") { call.respond(mapOf("status" to "ok")) }
        route("/api/auth") {
            post("/register") {
                val request = call.receive<RegistrationRequest>().validated()
                val user = authRepository.register(request.name, request.email, request.password)
                call.respond(HttpStatusCode.Created, AuthResponse(tokenService.create(user.id, user.email), user))
            }
            post("/login") {
                val request = call.receive<LoginRequest>()
                val user = authRepository.authenticate(request.email, request.password)
                    ?: return@post call.respond(HttpStatusCode.Unauthorized, ApiError("invalid_credentials", "Неверный адрес или пароль"))
                call.respond(AuthResponse(tokenService.create(user.id, user.email), user))
            }
        }
        authenticate("auth-jwt") {
            route("/api") {
                get("/categories") { call.respond(termsRepository.categories()) }
                get("/terms") {
                    val query = call.request.queryParameters["query"].orEmpty()
                    val categoryId = call.request.queryParameters["categoryId"]?.toLongOrNull()
                    val items = termsRepository.search(query, categoryId)
                    call.respond(SearchResponse(query, items, items.size))
                }
                get("/terms/{id}") {
                    val id = call.parameters["id"]?.toLongOrNull()
                        ?: return@get call.respond(HttpStatusCode.BadRequest, ApiError("invalid_id", "Некорректный идентификатор"))
                    val term = termsRepository.byId(id)
                        ?: return@get call.respond(HttpStatusCode.NotFound, ApiError("not_found", "Термин не найден"))
                    call.respond(term)
                }
            }
        }
    }
}

private fun RegistrationRequest.validated(): RegistrationRequest {
    require(name.trim().length >= 2) { "Имя должно содержать не менее двух символов" }
    require(Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$").matches(email.trim())) { "Введите корректный адрес электронной почты" }
    require(password.length >= 8) { "Пароль должен содержать не менее восьми символов" }
    return copy(name = name.trim(), email = email.trim())
}

private class TokenService(secret: String) {
    private val algorithm = Algorithm.HMAC256(secret)
    val verifier = JWT.require(algorithm).withIssuer(issuer).withAudience(audience).build()

    fun create(userId: Long, email: String): String = JWT.create()
        .withIssuer(issuer)
        .withAudience(audience)
        .withClaim("userId", userId)
        .withClaim("email", email)
        .withExpiresAt(Date.from(Instant.now().plusSeconds(60 * 60 * 24)))
        .sign(algorithm)

    private companion object {
        const val issuer = "it-reference-server"
        const val audience = "it-reference-android"
    }
}
