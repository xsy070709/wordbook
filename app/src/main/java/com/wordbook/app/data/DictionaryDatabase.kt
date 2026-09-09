package com.wordbook.app.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import java.io.File

class DictionaryDatabase(private val context: Context) {
    private val database: SQLiteDatabase by lazy {
        val target = File(context.noBackupFilesDir, DATABASE_NAME)
        if (!target.exists()) {
            target.parentFile?.mkdirs()
            context.assets.open(ASSET_PATH).use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
        }
        SQLiteDatabase.openDatabase(target.path, null, SQLiteDatabase.OPEN_READONLY)
    }

    fun findExact(rawWord: String): DictionaryEntry? {
        val word = WordNormalizer.normalize(rawWord)
        if (word.isBlank()) return null

        return database.query(
            "words",
            arrayOf("word", "phonetic", "translation"),
            "word = ? COLLATE NOCASE",
            arrayOf(word),
            null,
            null,
            null,
            "1",
        ).use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            DictionaryEntry(
                word = cursor.getString(0),
                phonetic = cursor.getString(1).orEmpty(),
                translation = cursor.getString(2).orEmpty(),
            )
        }
    }

    fun suggest(rawPrefix: String, limit: Int = 30): List<DictionaryEntry> {
        val prefix = WordNormalizer.normalize(rawPrefix)
        if (prefix.isBlank()) return emptyList()

        return database.query(
            "words",
            arrayOf("word", "phonetic", "translation"),
            "word LIKE ? ESCAPE '\\'",
            arrayOf("${escapeLike(prefix)}%"),
            null,
            null,
            "word COLLATE NOCASE ASC",
            limit.coerceIn(1, 100).toString(),
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        DictionaryEntry(
                            word = cursor.getString(0),
                            phonetic = cursor.getString(1).orEmpty(),
                            translation = cursor.getString(2).orEmpty(),
                        ),
                    )
                }
            }
        }
    }

    private fun escapeLike(value: String): String = value
        .replace("\\", "\\\\")
        .replace("%", "\\%")
        .replace("_", "\\_")

    private companion object {
        // Bump the installed filename whenever the bundled dictionary changes so
        // upgrades do not keep using an older copy from noBackupFilesDir.
        const val DATABASE_NAME = "dictionary-v2.db"
        const val ASSET_PATH = "database/dictionary.db"
    }
}
