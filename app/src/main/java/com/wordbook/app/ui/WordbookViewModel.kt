package com.wordbook.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.wordbook.app.WordbookApplication
import com.wordbook.app.data.DictionaryDatabase
import com.wordbook.app.data.DictionaryEntry
import com.wordbook.app.data.Notebook
import com.wordbook.app.data.SavedWord
import com.wordbook.app.data.SearchHistoryEntry
import com.wordbook.app.data.VocabularyDatabase
import com.wordbook.app.data.WordNormalizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class AppSection { LOOKUP, WORDS, NOTEBOOKS }

data class WordbookUiState(
    val section: AppSection = AppSection.LOOKUP,
    val lookupQuery: String = "",
    val exactEntry: DictionaryEntry? = null,
    val suggestions: List<DictionaryEntry> = emptyList(),
    val lookupFinished: Boolean = false,
    val savedWords: List<SavedWord> = emptyList(),
    val wordFilter: String = "",
    val selectedNotebookId: Long? = null,
    val notebooks: List<Notebook> = emptyList(),
    val savedExactWord: Boolean = false,
    val searchHistory: List<SearchHistoryEntry> = emptyList(),
)

class WordbookViewModel(
    private val dictionary: DictionaryDatabase,
    private val vocabulary: VocabularyDatabase,
) : ViewModel() {
    private val _state = MutableStateFlow(WordbookUiState())
    val state: StateFlow<WordbookUiState> = _state.asStateFlow()
    private var lookupJob: Job? = null

    init {
        refreshPersonalData()
    }

    fun selectSection(section: AppSection) {
        _state.update { it.copy(section = section) }
    }

    fun updateLookupQuery(value: String) {
        _state.update {
            it.copy(
                lookupQuery = value,
                exactEntry = null,
                suggestions = emptyList(),
                lookupFinished = value.isBlank(),
                savedExactWord = false,
            )
        }
        lookupJob?.cancel()
        if (value.isBlank()) return
        lookupJob = viewModelScope.launch {
            delay(120)
            val result = withContext(Dispatchers.IO) {
                val exact = dictionary.findExact(value)
                Triple(exact, dictionary.suggest(value), exact?.let { vocabulary.isSaved(it.word) } == true)
            }
            _state.update {
                if (it.lookupQuery != value) it else it.copy(
                    exactEntry = result.first,
                    suggestions = result.second,
                    lookupFinished = true,
                    savedExactWord = result.third,
                )
            }
            result.first?.let { entry ->
                vocabulary.recordSearch(entry.word)
                refreshSearchHistoryNow()
            }
        }
    }

    fun chooseSuggestion(entry: DictionaryEntry) {
        _state.update {
            it.copy(
                lookupQuery = entry.word,
                exactEntry = entry,
                suggestions = emptyList(),
                lookupFinished = true,
            )
        }
        viewModelScope.launch(Dispatchers.IO) {
            vocabulary.recordSearch(entry.word)
            val saved = vocabulary.isSaved(entry.word)
            val history = vocabulary.listSearchHistory()
            _state.update { it.copy(savedExactWord = saved, searchHistory = history) }
        }
    }

    fun openExternalWord(word: String) {
        selectSection(AppSection.LOOKUP)
        updateLookupQuery(word)
    }

    fun clearSearchHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            vocabulary.clearSearchHistory()
            _state.update { it.copy(searchHistory = emptyList()) }
        }
    }

    fun addCurrentWord(notebookId: Long?) {
        val entry = _state.value.exactEntry ?: return
        viewModelScope.launch(Dispatchers.IO) {
            vocabulary.addWord(entry, notebookId)
            refreshPersonalDataNow()
            _state.update { it.copy(savedExactWord = true) }
        }
    }

    fun addManualWord(
        word: String,
        translation: String,
        phonetic: String,
        notebookId: Long?,
        onResult: (Boolean) -> Unit = {},
    ) = mutate(onResult) {
        vocabulary.upsertManualWord(word, translation, phonetic, notebookId)
    }

    fun updateWordFilter(value: String) {
        _state.update { it.copy(wordFilter = value) }
        refreshWords()
    }

    fun selectNotebookFilter(id: Long?) {
        _state.update { it.copy(selectedNotebookId = id) }
        refreshWords()
    }

    fun removeWord(id: Long) = mutate { vocabulary.removeWord(id) }

    fun moveWord(id: Long, notebookId: Long?) = mutate { vocabulary.moveWord(id, notebookId) }

    fun updateNote(id: Long, note: String) = mutate { vocabulary.updateNote(id, note) }

    fun addNotebook(name: String, onResult: (Boolean) -> Unit = {}) = mutate(onResult) {
        vocabulary.addNotebook(name)
    }

    fun renameNotebook(id: Long, name: String, onResult: (Boolean) -> Unit = {}) = mutate(onResult) {
        vocabulary.renameNotebook(id, name)
    }

    fun deleteNotebook(id: Long) = mutate { vocabulary.deleteNotebook(id) }

    private fun refreshPersonalData() {
        viewModelScope.launch(Dispatchers.IO) { refreshPersonalDataNow() }
    }

    private fun refreshWords() {
        val snapshot = _state.value
        viewModelScope.launch(Dispatchers.IO) {
            val words = vocabulary.listWords(snapshot.wordFilter, snapshot.selectedNotebookId)
            _state.update { it.copy(savedWords = words) }
        }
    }

    private fun refreshPersonalDataNow() {
        val snapshot = _state.value
        val notebooks = vocabulary.listNotebooks()
        val selected = snapshot.selectedNotebookId?.takeIf { id -> notebooks.any { it.id == id } }
        val words = vocabulary.listWords(snapshot.wordFilter, selected)
        val history = vocabulary.listSearchHistory()
        _state.update {
            it.copy(
                notebooks = notebooks,
                savedWords = words,
                selectedNotebookId = selected,
                searchHistory = history,
            )
        }
    }

    private fun refreshSearchHistoryNow() {
        val history = vocabulary.listSearchHistory()
        _state.update { it.copy(searchHistory = history) }
    }

    private fun mutate(block: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            block()
            refreshPersonalDataNow()
        }
    }

    private fun mutate(onResult: (Boolean) -> Unit, block: () -> Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = block()
            refreshPersonalDataNow()
            withContext(Dispatchers.Main) { onResult(result) }
        }
    }

    companion object {
        fun factory(application: WordbookApplication): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T = WordbookViewModel(
                    dictionary = application.dictionaryDatabase,
                    vocabulary = application.vocabularyDatabase,
                ) as T
            }
    }
}
