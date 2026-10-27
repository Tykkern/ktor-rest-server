package com.example.library

import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BookApiTest {

    private fun withApp(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        application { module() }
        block()
    }

    private suspend inline fun <reified T> HttpResponse.json(): T = Json.decodeFromString(bodyAsText())

    private suspend fun ApplicationTestBuilder.postJson(body: String) = client.post("/books") {
        contentType(ContentType.Application.Json)
        setBody(body)
    }

    @Test
    fun `GET books - список и фильтры query`() = withApp {
        val all = client.get("/books")
        assertEquals(HttpStatusCode.OK, all.status)
        assertTrue(all.contentType()!!.match(ContentType.Application.Json))
        assertEquals(4, all.json<List<Book>>().size)

        assertEquals(2, client.get("/books") { parameter("author", "толстой") }.json<List<Book>>().size)
        assertEquals(listOf(1866), client.get("/books?year=1866").json<List<Book>>().map { it.year })
        assertEquals(1, client.get("/books?limit=1").json<List<Book>>().size)

        val badYear = client.get("/books?year=abc")
        assertEquals(HttpStatusCode.BadRequest, badYear.status)
        assertEquals("Параметр year должен быть целым числом", badYear.json<ErrorResponse>().error)
        assertEquals(HttpStatusCode.BadRequest, client.get("/books?limit=0").status)
    }

    @Test
    fun `GET books id - параметр пути`() = withApp {
        val found = client.get("/books/1")
        assertEquals(HttpStatusCode.OK, found.status)
        assertEquals("Война и мир", found.json<Book>().title)

        val missing = client.get("/books/99")
        assertEquals(HttpStatusCode.NotFound, missing.status)
        assertEquals(ErrorResponse("Книга не найдена", "id=99"), missing.json<ErrorResponse>())

        assertEquals(HttpStatusCode.BadRequest, client.get("/books/abc").status)
        assertEquals(HttpStatusCode.BadRequest, client.get("/books/0").status)
    }

    @Test
    fun `POST books - создание и ошибки тела`() = withApp {
        val created = postJson("""{"title": " Собачье сердце ", "author": "Михаил Булгаков", "year": 1925}""")
        assertEquals(HttpStatusCode.Created, created.status)
        val book = created.json<Book>()
        assertEquals(Book(5, "Собачье сердце", "Михаил Булгаков", 1925), book)
        assertEquals("/books/5", created.headers[HttpHeaders.Location])
        assertEquals(book, client.get("/books/5").json<Book>())

        val duplicate = postJson("""{"title": "собачье сердце", "author": "михаил булгаков", "year": 1925}""")
        assertEquals(HttpStatusCode.Conflict, duplicate.status)

        assertEquals(HttpStatusCode.BadRequest, postJson("""{"title": "  ", "author": "Автор", "year": 2000}""").status)
        assertEquals(HttpStatusCode.BadRequest, postJson("""{"title": "Книга", "author": "Автор", "year": 3000}""").status)
        assertEquals(HttpStatusCode.BadRequest, postJson("""{"title": "Книга", "author": "Автор"}""").status)
        assertEquals(HttpStatusCode.BadRequest, postJson("{ не json }").status)

        val plainText = client.post("/books") {
            contentType(ContentType.Text.Plain)
            setBody("Книга")
        }
        assertEquals(HttpStatusCode.UnsupportedMediaType, plainText.status)
        assertEquals("Тело запроса должно быть в формате JSON", plainText.json<ErrorResponse>().error)
    }

    @Test
    fun `DELETE books id - удаление`() = withApp {
        assertEquals(HttpStatusCode.NoContent, client.delete("/books/1").status)
        assertEquals(HttpStatusCode.NotFound, client.get("/books/1").status)
        assertEquals(HttpStatusCode.NotFound, client.delete("/books/1").status)
        assertEquals(HttpStatusCode.BadRequest, client.delete("/books/x").status)
        assertEquals(3, client.get("/books").json<List<Book>>().size)
    }

    @Test
    fun `неизвестный маршрут и метод отдают JSON`() = withApp {
        val unknown = client.get("/authors")
        assertEquals(HttpStatusCode.NotFound, unknown.status)
        assertEquals("Маршрут не найден", unknown.json<ErrorResponse>().error)

        val wrongMethod = client.put("/books")
        assertEquals(HttpStatusCode.MethodNotAllowed, wrongMethod.status)
        assertEquals("Метод не поддерживается", wrongMethod.json<ErrorResponse>().error)
    }
}
