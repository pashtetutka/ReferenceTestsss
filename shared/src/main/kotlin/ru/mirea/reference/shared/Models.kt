package ru.mirea.reference.shared

import kotlinx.serialization.Serializable

@Serializable
data class RegistrationRequest(
    val name: String,
    val email: String,
    val password: String,
)

@Serializable
data class LoginRequest(
    val email: String,
    val password: String,
)

@Serializable
data class UserDto(
    val id: Long,
    val name: String,
    val email: String,
)

@Serializable
data class AuthResponse(
    val token: String,
    val user: UserDto,
)

@Serializable
data class CategoryDto(
    val id: Long,
    val name: String,
    val description: String,
)

@Serializable
data class TermSummaryDto(
    val id: Long,
    val title: String,
    val shortDefinition: String,
    val categoryId: Long,
    val categoryName: String,
)

@Serializable
data class TermDto(
    val id: Long,
    val title: String,
    val shortDefinition: String,
    val definition: String,
    val example: String,
    val categoryId: Long,
    val categoryName: String,
)

@Serializable
data class SearchResponse(
    val query: String,
    val items: List<TermSummaryDto>,
    val total: Int,
)

@Serializable
data class ApiError(
    val code: String,
    val message: String,
)

