package com.example.library

/**
 * Хранилище книг в памяти. Данные живут до перезапуска сервера.
 * Методы синхронизированы, так как Netty обрабатывает запросы параллельно.
 */
class BookRepository {
    private val books = LinkedHashMap<Int, Book>()
    private var nextId = 1

    @Synchronized
    fun find(author: String?, year: Int?, limit: Int?): List<Book> =
        books.values
            .filter { author == null || it.author.contains(author, ignoreCase = true) }
            .filter { year == null || it.year == year }
            .take(limit ?: Int.MAX_VALUE)

    @Synchronized
    fun get(id: Int): Book? = books[id]

    /** Возвращает null, если книга с таким названием и автором уже есть. */
    @Synchronized
    fun add(book: NewBook): Book? {
        val duplicate = books.values.any {
            it.title.equals(book.title, ignoreCase = true) && it.author.equals(book.author, ignoreCase = true)
        }
        if (duplicate) return null

        val created = Book(nextId++, book.title, book.author, book.year)
        books[created.id] = created
        return created
    }

    @Synchronized
    fun remove(id: Int): Boolean = books.remove(id) != null

    companion object {
        fun withSampleData() = BookRepository().apply {
            add(NewBook("Война и мир", "Лев Толстой", 1869))
            add(NewBook("Анна Каренина", "Лев Толстой", 1878))
            add(NewBook("Преступление и наказание", "Фёдор Достоевский", 1866))
            add(NewBook("Мастер и Маргарита", "Михаил Булгаков", 1967))
        }
    }
}
