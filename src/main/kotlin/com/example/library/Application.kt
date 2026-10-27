package com.example.library

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.application.log
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.server.response.respond
import io.ktor.server.routing.routing
import kotlinx.serialization.json.Json

fun main() {
    embeddedServer(Netty, port = 8080, host = "0.0.0.0", module = Application::module).start(wait = true)
}

fun Application.module() {
    install(ContentNegotiation) {
        json(Json {
            prettyPrint = true
            ignoreUnknownKeys = true
        })
    }

    configureErrors()

    val repository = BookRepository.withSampleData()
    routing {
        bookRoutes(repository)
    }
}

/** Все ошибки уходят клиенту в JSON, а не HTML-страницей движка. */
private fun Application.configureErrors() {
    install(StatusPages) {
        exception<ApiException> { call, cause ->
            call.respond(cause.status, ErrorResponse(cause.message.orEmpty(), cause.details))
        }
        exception<BadRequestException> { call, cause ->
            call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse("Некорректное тело запроса", cause.cause?.message ?: cause.message)
            )
        }
        exception<Throwable> { call, cause ->
            call.application.log.error("Необработанная ошибка", cause)
            call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Внутренняя ошибка сервера"))
        }

        // Пустые ответы движка (неизвестный адрес, метод или Content-Type) заменяем на JSON.
        // Ответы, которые уже собраны в JSON внутри маршрутов, не трогаем.
        status(HttpStatusCode.NotFound, HttpStatusCode.MethodNotAllowed, HttpStatusCode.UnsupportedMediaType) { status ->
            if (content.contentType?.match(ContentType.Application.Json) == true) return@status
            val request = "${call.request.httpMethod.value} ${call.request.path()}"
            val body = when (status) {
                HttpStatusCode.NotFound -> ErrorResponse("Маршрут не найден", request)
                HttpStatusCode.MethodNotAllowed -> ErrorResponse("Метод не поддерживается", request)
                else -> ErrorResponse(
                    "Тело запроса должно быть в формате JSON",
                    "получен Content-Type: ${call.request.headers[HttpHeaders.ContentType] ?: "не указан"}"
                )
            }
            call.respond(status, body)
        }
    }
}
