package com.example.library

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receive
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import java.time.Year

fun Route.bookRoutes(repository: BookRepository) {
    route("/books") {
        // GET /books?author=толстой&year=1869&limit=10
        get {
            val params = call.request.queryParameters
            val author = params["author"]?.trim()?.takeIf { it.isNotEmpty() }
            val year = params.int("year")
            val limit = params.int("limit")
            if (limit != null && limit <= 0) {
                throw ApiException(HttpStatusCode.BadRequest, "Параметр limit должен быть больше нуля", "получено: $limit")
            }

            call.respond(HttpStatusCode.OK, repository.find(author, year, limit))
        }

        get("/{id}") {
            val id = call.bookId()
            val book = repository.get(id) ?: throw bookNotFound(id)
            call.respond(HttpStatusCode.OK, book)
        }

        post {
            val request = call.receive<NewBook>()
            val book = request.copy(title = request.title.trim(), author = request.author.trim())
            validate(book)

            val created = repository.add(book)
                ?: throw ApiException(
                    HttpStatusCode.Conflict,
                    "Такая книга уже есть в каталоге",
                    "${book.author}, «${book.title}»"
                )

            call.response.header(HttpHeaders.Location, "/books/${created.id}")
            call.respond(HttpStatusCode.Created, created)
        }

        delete("/{id}") {
            val id = call.bookId()
            if (!repository.remove(id)) throw bookNotFound(id)
            call.respond(HttpStatusCode.NoContent)
        }
    }
}

private fun ApplicationCall.bookId(): Int {
    val raw = parameters["id"]
    return raw?.toIntOrNull()?.takeIf { it > 0 }
        ?: throw ApiException(
            HttpStatusCode.BadRequest,
            "Идентификатор книги должен быть положительным целым числом",
            "получено: $raw"
        )
}

private fun Parameters.int(name: String): Int? {
    val raw = this[name] ?: return null
    return raw.toIntOrNull()
        ?: throw ApiException(HttpStatusCode.BadRequest, "Параметр $name должен быть целым числом", "получено: $raw")
}

private fun bookNotFound(id: Int) = ApiException(HttpStatusCode.NotFound, "Книга не найдена", "id=$id")

private fun validate(book: NewBook) {
    val error = when {
        book.title.isEmpty() -> "Поле title не должно быть пустым"
        book.title.length > 200 -> "Поле title длиннее 200 символов"
        book.author.isEmpty() -> "Поле author не должно быть пустым"
        book.author.length > 100 -> "Поле author длиннее 100 символов"
        book.year !in 1..Year.now().value -> "Поле year должно быть от 1 до ${Year.now().value}"
        else -> return
    }
    throw ApiException(HttpStatusCode.BadRequest, error)
}
