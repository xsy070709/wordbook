package com.wordbook.app

import android.app.Application
import com.wordbook.app.data.DictionaryDatabase
import com.wordbook.app.data.VocabularyDatabase

class WordbookApplication : Application() {
    val dictionaryDatabase by lazy { DictionaryDatabase(this) }
    val vocabularyDatabase by lazy { VocabularyDatabase(this) }
}
