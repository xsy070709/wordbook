package com.wordbook.app.data

data class DictionaryEntry(
    val word: String,
    val phonetic: String,
    val translation: String,
)

data class Notebook(
    val id: Long,
    val name: String,
    val createdAt: Long,
    val wordCount: Int = 0,
)

data class SavedWord(
    val id: Long,
    val word: String,
    val phonetic: String,
    val translation: String,
    val note: String,
    val addedAt: Long,
    val notebookId: Long?,
    val notebookName: String?,
)

data class SearchHistoryEntry(
    val word: String,
    val searchedAt: Long,
)
