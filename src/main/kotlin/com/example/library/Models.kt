package com.example.library

import io.ktor.http.HttpStatusCode
import kotlinx.serialization.Serializable

/** Книга в каталоге. */
@Serializable
data class Book(
    val id: Int,
    val title: String,
    val author: String,
    val year: Int
)

/** Тело запроса на добавление книги. */
@Serializable
data class NewBook(
    val title: String,
    val author: String,
    val year: Int
)

/** Единый формат ответа об ошибке. */
@Serializable
data class ErrorResponse(
    val error: String,
    val details: String? = null
)

/** Ошибка, которую сервер отдаёт клиенту с конкретным HTTP-кодом. */
class ApiException(
    val status: HttpStatusCode,
    message: String,
    val details: String? = null
) : Exception(message)
