package com.wordbook.app

import android.app.Application
import com.wordbook.app.data.DictionaryDatabase
import com.wordbook.app.data.VocabularyDatabase
import java.util.concurrent.atomic.AtomicBoolean

class WordbookApplication : Application() {
    val dictionaryDatabase by lazy { DictionaryDatabase(this) }
    val vocabularyDatabase by lazy { VocabularyDatabase(this) }
    private val updateCheckStarted = AtomicBoolean(false)

    fun markUpdateCheckStarted(): Boolean = updateCheckStarted.compareAndSet(false, true)
}
