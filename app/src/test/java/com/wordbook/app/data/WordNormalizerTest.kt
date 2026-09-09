package com.wordbook.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class WordNormalizerTest {
    @Test
    fun trimsPunctuationAndLowercases() {
        assertEquals("contemplate", WordNormalizer.normalize("  ‘Contemplate.’  "))
    }

    @Test
    fun keepsApostrophesAndHyphensInsideWord() {
        assertEquals("mother-in-law's", WordNormalizer.normalize("mother-in-law's"))
    }

    @Test
    fun handlesEmptyInput() {
        assertEquals("", WordNormalizer.normalize(null))
    }
}
