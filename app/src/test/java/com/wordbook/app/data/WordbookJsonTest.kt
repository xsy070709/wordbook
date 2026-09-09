package com.wordbook.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class WordbookJsonTest {
    @Test
    fun roundTripPreservesSupportedFieldsAndOmitsEmptyOptionalFields() {
        val json = WordbookJson.encode(
            TransferData(
                notebooks = listOf("阅读积累"),
                words = listOf(
                    TransferWord(
                        word = "abandon",
                        translation = "放弃",
                        notebook = "阅读积累",
                        addedAt = 1_757_334_930_000,
                    ),
                ),
            ),
        )

        val decoded = WordbookJson.decode(json)

        assertEquals(listOf("阅读积累"), decoded.notebooks)
        assertEquals("abandon", decoded.words.single().word)
        assertEquals("阅读积累", decoded.words.single().notebook)
        assertFalse(json.contains("\"phonetic\""))
        assertFalse(json.contains("\"note\""))
    }

    @Test
    fun unknownFieldsAreIgnored() {
        val decoded = WordbookJson.decode(
            """{"future":true,"words":[{"word":"future","translation":"未来","extra":1}]}""",
        )

        assertEquals("future", decoded.words.single().word)
    }

    @Test
    fun duplicateWordsAreRejectedCaseInsensitively() {
        assertThrows(IllegalArgumentException::class.java) {
            WordbookJson.decode(
                """{"words":[{"word":"Test","translation":"测试"},{"word":"test","translation":"测试"}]}""",
            )
        }
    }

    @Test
    fun referencedNotebookIsCreatedImplicitly() {
        val decoded = WordbookJson.decode(
            """{"words":[{"word":"book","translation":"书","notebook":"阅读"}]}""",
        )

        assertEquals(listOf("阅读"), decoded.notebooks)
    }
}
