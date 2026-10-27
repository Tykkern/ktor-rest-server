# Ktor REST Server

Учебный REST-сервер на Kotlin и Ktor: каталог книг с маршрутами GET, POST и
DELETE. Запросы и ответы передаются в JSON через плагин ContentNegotiation и
kotlinx.serialization.

## Стек

| Компонент | Версия |
| --- | --- |
| Kotlin | 2.1.20 |
| Ktor Server (Netty) | 3.1.1 |
| kotlinx.serialization | через `ktor-serialization-kotlinx-json` |
| Gradle | 8.5, wrapper лежит в репозитории |
| JDK | 17 или 21 |

## Запуск

Нужен установленный JDK 17 или 21. Gradle ставить не надо, wrapper скачает его
при первом запуске.

```bash
git clone https://github.com/Tykkern/ktor-rest-server.git
cd ktor-rest-server

# Linux, macOS, Git Bash
./gradlew run

# Windows (cmd или PowerShell)
gradlew.bat run
```

Когда в логе появится строка `Responding at http://127.0.0.1:8080`, сервер
готов принимать запросы на `http://localhost:8080`. Остановить его можно
через Ctrl+C.

Тесты запускаются так:

```bash
./gradlew test
```

Тесты поднимают приложение через `ktor-server-test-host` и проверяют все
маршруты, query- и path-параметры и коды ошибок.

## Структура проекта

```text
ktor-rest-server/
├── build.gradle.kts                  сборка и зависимости
├── settings.gradle.kts
├── gradlew, gradlew.bat, gradle/     Gradle Wrapper
└── src/
    ├── main/kotlin/com/example/library/
    │   ├── Application.kt            точка входа, ContentNegotiation, обработка ошибок
    │   ├── BookRoutes.kt             маршруты /books, разбор параметров, проверка тела
    │   ├── BookRepository.kt         хранилище книг в памяти
    │   └── Models.kt                 Book, NewBook, ErrorResponse
    ├── main/resources/logback.xml    настройки логирования
    └── test/kotlin/com/example/library/
        └── BookApiTest.kt            тесты API
```

## API

| Метод | Путь | Что делает | Код при успехе |
| --- | --- | --- | --- |
| GET | `/books` | список книг, фильтры через query | 200 |
| GET | `/books/{id}` | одна книга по id из пути | 200 |
| POST | `/books` | добавляет книгу | 201 |
| DELETE | `/books/{id}` | удаляет книгу по id из пути | 204 |

Книга в ответах выглядит так:

```json
{
  "id": 1,
  "title": "Война и мир",
  "author": "Лев Толстой",
  "year": 1869
}
```

### GET /books

Возвращает массив книг. Все query-параметры необязательные и сочетаются друг
с другом.

| Параметр | Тип | Описание |
| --- | --- | --- |
| `author` | строка | часть имени автора, регистр не важен |
| `year` | целое число | год издания |
| `limit` | целое число больше 0 | сколько книг вернуть максимум |

Ответы: `200 OK` с массивом (если ничего не нашлось, массив пустой),
`400 Bad Request`, если `year` или `limit` не число или `limit` меньше 1.

```bash
curl "http://localhost:8080/books?year=1869&limit=1"
```

Фильтр по автору на кириллице проще проверить в браузере:
`http://localhost:8080/books?author=толстой`.

### GET /books/{id}

Ответы: `200 OK` с книгой; `400 Bad Request`, если `id` не положительное целое
число; `404 Not Found`, если такой книги нет.

```bash
curl http://localhost:8080/books/1
```

### POST /books

Тело запроса:

```json
{ "title": "The Hobbit", "author": "J. R. R. Tolkien", "year": 1937 }
```

Требования к полям: `title` от 1 до 200 символов, `author` от 1 до 100
символов, `year` от 1 до текущего года. Пробелы по краям строк сервер
обрезает.

Ответы:

- `201 Created`: книга создана, в теле она же с новым `id`, в заголовке
  `Location` её адрес;
- `400 Bad Request`: битый JSON, нет обязательного поля или поле не прошло
  проверку;
- `409 Conflict`: книга с тем же названием и автором уже есть (регистр не
  учитывается);
- `415 Unsupported Media Type`: тело отправлено не как `application/json`.

```bash
curl -i -X POST http://localhost:8080/books \
  -H "Content-Type: application/json" \
  -d '{"title":"The Hobbit","author":"J. R. R. Tolkien","year":1937}'
```

Если тело с русскими буквами отправлять через curl из консоли Windows,
кириллица уходит не в UTF-8 и сервер получает искажённый текст. Такое тело
лучше сохранить в файл в кодировке UTF-8 и передать его так:
`curl -X POST http://localhost:8080/books -H "Content-Type: application/json" --data-binary @book.json`.

### DELETE /books/{id}

Ответы: `204 No Content`, если книга удалена; `400 Bad Request`, если `id`
не положительное целое число; `404 Not Found`, если такой книги нет.

```bash
curl -i -X DELETE http://localhost:8080/books/1
```

## Ошибки

Любая ошибка приходит в JSON одного формата:

```json
{
  "error": "Книга не найдена",
  "details": "id=99"
}
```

Поле `details` есть не всегда.

| Код | Когда |
| --- | --- |
| 400 Bad Request | неверный параметр или тело запроса |
| 404 Not Found | нет книги с таким id или нет такого маршрута |
| 405 Method Not Allowed | метод не поддерживается, например `PUT /books` |
| 409 Conflict | такая книга уже есть |
| 415 Unsupported Media Type | тело запроса не в JSON |
| 500 Internal Server Error | непредвиденная ошибка сервера |

## Хранение данных

Книги хранятся в памяти процесса. При старте в каталоге четыре книги, после
перезапуска сервера всё возвращается к этому набору.
