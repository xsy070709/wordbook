package com.wordbook.app.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.util.Locale

class VocabularyDatabase(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE notebooks (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL COLLATE NOCASE UNIQUE,
                created_at INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE saved_words (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                word TEXT NOT NULL COLLATE NOCASE UNIQUE,
                phonetic TEXT NOT NULL DEFAULT '',
                translation TEXT NOT NULL,
                note TEXT NOT NULL DEFAULT '',
                added_at INTEGER NOT NULL,
                notebook_id INTEGER,
                FOREIGN KEY(notebook_id) REFERENCES notebooks(id) ON DELETE SET NULL
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX saved_words_order ON saved_words(word COLLATE NOCASE)")
        db.execSQL("CREATE INDEX saved_words_notebook ON saved_words(notebook_id)")
        createSearchHistoryTable(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE saved_words ADD COLUMN note TEXT NOT NULL DEFAULT ''")
        }
        if (oldVersion < 3) {
            createSearchHistoryTable(db)
        }
    }

    private fun createSearchHistoryTable(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE search_history (
                word TEXT NOT NULL COLLATE NOCASE PRIMARY KEY,
                searched_at INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX search_history_recent ON search_history(searched_at DESC)")
    }

    fun recordSearch(word: String) {
        val cleanWord = WordNormalizer.normalize(word)
        if (cleanWord.isBlank()) return
        writableDatabase.insertWithOnConflict(
            "search_history",
            null,
            ContentValues().apply {
                put("word", cleanWord)
                put("searched_at", System.currentTimeMillis())
            },
            SQLiteDatabase.CONFLICT_REPLACE,
        )
        writableDatabase.execSQL(
            """
            DELETE FROM search_history
            WHERE word NOT IN (
                SELECT word FROM search_history ORDER BY searched_at DESC LIMIT 100
            )
            """.trimIndent(),
        )
    }

    fun listSearchHistory(limit: Int = 100): List<SearchHistoryEntry> = readableDatabase.rawQuery(
        "SELECT word, searched_at FROM search_history ORDER BY searched_at DESC LIMIT ?",
        arrayOf(limit.coerceIn(1, 100).toString()),
    ).use { cursor ->
        buildList {
            while (cursor.moveToNext()) {
                add(SearchHistoryEntry(word = cursor.getString(0), searchedAt = cursor.getLong(1)))
            }
        }
    }

    fun clearSearchHistory() {
        writableDatabase.delete("search_history", null, null)
    }

    fun deleteSearchHistory(word: String) {
        writableDatabase.delete(
            "search_history",
            "word = ? COLLATE NOCASE",
            arrayOf(WordNormalizer.normalize(word)),
        )
    }

    fun addWord(entry: DictionaryEntry, notebookId: Long?): Boolean {
        val values = ContentValues().apply {
            put("word", entry.word)
            put("phonetic", entry.phonetic)
            put("translation", entry.translation)
            put("added_at", System.currentTimeMillis())
            if (notebookId == null) putNull("notebook_id") else put("notebook_id", notebookId)
        }
        return writableDatabase.insertWithOnConflict(
            "saved_words",
            null,
            values,
            SQLiteDatabase.CONFLICT_IGNORE,
        ) != -1L
    }

    fun upsertManualWord(
        rawWord: String,
        rawTranslation: String,
        rawPhonetic: String,
        notebookId: Long?,
    ): Boolean {
        val word = WordNormalizer.normalize(rawWord)
        val translation = rawTranslation.trim().take(4_000)
        val phonetic = rawPhonetic.trim().trim('/').take(100)
        if (word.isBlank() || translation.isBlank()) return false

        val values = ContentValues().apply {
            put("word", word)
            put("phonetic", phonetic)
            put("translation", translation)
            put("added_at", System.currentTimeMillis())
            if (notebookId == null) putNull("notebook_id") else put("notebook_id", notebookId)
        }
        val inserted = writableDatabase.insertWithOnConflict(
            "saved_words",
            null,
            values,
            SQLiteDatabase.CONFLICT_IGNORE,
        )
        if (inserted != -1L) return true

        // For an existing word, update editable content while preserving its id and added date.
        values.remove("word")
        values.remove("added_at")
        return writableDatabase.update(
            "saved_words",
            values,
            "word = ? COLLATE NOCASE",
            arrayOf(word),
        ) > 0
    }

    fun removeWord(id: Long) {
        writableDatabase.delete("saved_words", "id = ?", arrayOf(id.toString()))
    }

    fun moveWord(id: Long, notebookId: Long?) {
        val values = ContentValues().apply {
            if (notebookId == null) putNull("notebook_id") else put("notebook_id", notebookId)
        }
        writableDatabase.update("saved_words", values, "id = ?", arrayOf(id.toString()))
    }

    fun updateNote(id: Long, note: String) {
        writableDatabase.update(
            "saved_words",
            ContentValues().apply { put("note", note.trim().take(4_000)) },
            "id = ?",
            arrayOf(id.toString()),
        )
    }

    fun isSaved(word: String): Boolean = readableDatabase.rawQuery(
        "SELECT 1 FROM saved_words WHERE word = ? COLLATE NOCASE LIMIT 1",
        arrayOf(word),
    ).use { it.moveToFirst() }

    fun listWords(
        query: String = "",
        notebookId: Long? = null,
        notebookIds: Set<Long>? = null,
    ): List<SavedWord> {
        val where = mutableListOf<String>()
        val args = mutableListOf<String>()
        if (query.isNotBlank()) {
            where += "(s.word LIKE ? ESCAPE '\\' OR s.translation LIKE ? ESCAPE '\\' OR s.note LIKE ? ESCAPE '\\')"
            val pattern = "%${escapeLike(query.trim())}%"
            args += pattern
            args += pattern
            args += pattern
        }
        if (notebookId != null) {
            where += "s.notebook_id = ?"
            args += notebookId.toString()
        }
        if (notebookIds != null) {
            if (notebookIds.isEmpty()) return emptyList()
            where += "s.notebook_id IN (${notebookIds.joinToString(",") { "?" }})"
            args += notebookIds.map(Long::toString)
        }
        val whereSql = if (where.isEmpty()) "" else "WHERE ${where.joinToString(" AND ")}"
        return readableDatabase.rawQuery(
            """
            SELECT s.id, s.word, s.phonetic, s.translation, s.note, s.added_at,
                   s.notebook_id, n.name
            FROM saved_words s
            LEFT JOIN notebooks n ON n.id = s.notebook_id
            $whereSql
            ORDER BY s.word COLLATE NOCASE ASC
            """.trimIndent(),
            args.toTypedArray(),
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        SavedWord(
                            id = cursor.getLong(0),
                            word = cursor.getString(1),
                            phonetic = cursor.getString(2),
                            translation = cursor.getString(3),
                            note = cursor.getString(4),
                            addedAt = cursor.getLong(5),
                            notebookId = if (cursor.isNull(6)) null else cursor.getLong(6),
                            notebookName = if (cursor.isNull(7)) null else cursor.getString(7),
                        ),
                    )
                }
            }
        }
    }

    fun addNotebook(name: String): Boolean {
        val cleanName = name.trim().take(40)
        if (cleanName.isBlank()) return false
        return writableDatabase.insertWithOnConflict(
            "notebooks",
            null,
            ContentValues().apply {
                put("name", cleanName)
                put("created_at", System.currentTimeMillis())
            },
            SQLiteDatabase.CONFLICT_IGNORE,
        ) != -1L
    }

    fun renameNotebook(id: Long, name: String): Boolean {
        val cleanName = name.trim().take(40)
        if (cleanName.isBlank()) return false
        return try {
            writableDatabase.update(
                "notebooks",
                ContentValues().apply { put("name", cleanName) },
                "id = ?",
                arrayOf(id.toString()),
            ) > 0
        } catch (_: Exception) {
            false
        }
    }

    fun deleteNotebook(id: Long) {
        writableDatabase.delete("notebooks", "id = ?", arrayOf(id.toString()))
    }

    fun listNotebooks(): List<Notebook> = readableDatabase.rawQuery(
        """
        SELECT n.id, n.name, n.created_at, COUNT(s.id)
        FROM notebooks n
        LEFT JOIN saved_words s ON s.notebook_id = n.id
        GROUP BY n.id
        ORDER BY n.name COLLATE NOCASE ASC
        """.trimIndent(),
        emptyArray(),
    ).use { cursor ->
        buildList {
            while (cursor.moveToNext()) {
                add(
                    Notebook(
                        id = cursor.getLong(0),
                        name = cursor.getString(1),
                        createdAt = cursor.getLong(2),
                        wordCount = cursor.getInt(3),
                    ),
                )
            }
        }
    }

    fun exportData(): TransferData = TransferData(
        notebooks = listNotebooks().map { it.name },
        words = listWords().map {
            TransferWord(
                word = it.word,
                translation = it.translation,
                phonetic = it.phonetic,
                note = it.note,
                notebook = it.notebookName,
                addedAt = it.addedAt,
            )
        },
    )

    fun wordsForPrint(notebookIds: Set<Long>?): List<SavedWord> = listWords(notebookIds = notebookIds)

    fun importData(data: TransferData): ImportResult {
        val db = writableDatabase
        var added = 0
        var updated = 0
        var notebooksCreated = 0
        db.beginTransaction()
        try {
            val notebookIds = mutableMapOf<String, Long>()
            db.rawQuery("SELECT id, name FROM notebooks", emptyArray()).use { cursor ->
                while (cursor.moveToNext()) {
                    notebookIds[cursor.getString(1).lowercase(Locale.ROOT)] = cursor.getLong(0)
                }
            }
            data.notebooks.forEach { name ->
                val key = name.lowercase(Locale.ROOT)
                if (key !in notebookIds) {
                    val id = db.insertOrThrow(
                        "notebooks",
                        null,
                        ContentValues().apply {
                            put("name", name)
                            put("created_at", System.currentTimeMillis())
                        },
                    )
                    notebookIds[key] = id
                    notebooksCreated++
                }
            }
            data.words.forEach { item ->
                val notebookId = item.notebook?.let { notebookIds.getValue(it.lowercase(Locale.ROOT)) }
                val existingId = db.rawQuery(
                    "SELECT id FROM saved_words WHERE word = ? COLLATE NOCASE LIMIT 1",
                    arrayOf(item.word),
                ).use { cursor -> if (cursor.moveToFirst()) cursor.getLong(0) else null }
                val values = ContentValues().apply {
                    put("translation", item.translation)
                    put("phonetic", item.phonetic)
                    put("note", item.note)
                    if (notebookId == null) putNull("notebook_id") else put("notebook_id", notebookId)
                }
                if (existingId == null) {
                    values.put("word", item.word)
                    values.put("added_at", item.addedAt ?: System.currentTimeMillis())
                    db.insertOrThrow("saved_words", null, values)
                    added++
                } else {
                    db.update("saved_words", values, "id = ?", arrayOf(existingId.toString()))
                    updated++
                }
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return ImportResult(added, updated, notebooksCreated)
    }

    private fun escapeLike(value: String): String = value
        .replace("\\", "\\\\")
        .replace("%", "\\%")
        .replace("_", "\\_")

    private companion object {
        const val DATABASE_NAME = "wordbook.db"
        const val DATABASE_VERSION = 3
    }
}
