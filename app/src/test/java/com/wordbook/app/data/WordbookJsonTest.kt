package com.wordbook.app.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
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
        assertEquals(1_757_334_930_000, decoded.words.single().addedAt)
        val exportedTime = JSONObject(json).getJSONArray("words").getJSONObject(0).getString("addedAt")
        assertTrue(exportedTime.matches(Regex(".*[+-]\\d{2}:\\d{2}")))
        assertFalse(json.contains("\"phonetic\""))
        assertFalse(json.contains("\"note\""))
    }

    @Test
    fun acceptsUtcAndMillisecondIsoTimes() {
        val utc = WordbookJson.decode(
            """{"words":[{"word":"utc","translation":"时间","addedAt":"2026-09-01T12:15:30Z"}]}""",
        )
        val milliseconds = WordbookJson.decode(
            """{"words":[{"word":"ms","translation":"毫秒","addedAt":"2026-09-01T20:15:30.123+08:00"}]}""",
        )

        assertEquals(1_788_264_930_000, utc.words.single().addedAt)
        assertEquals(1_788_264_930_123, milliseconds.words.single().addedAt)
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
