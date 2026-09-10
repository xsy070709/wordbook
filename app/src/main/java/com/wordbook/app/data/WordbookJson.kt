package com.wordbook.app.data

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object WordbookJson {
    fun encode(data: TransferData): String {
        val root = JSONObject()
        root.put("notebooks", JSONArray().apply {
            data.notebooks.sortedWith(String.CASE_INSENSITIVE_ORDER).forEach(::put)
        })
        root.put("words", JSONArray().apply {
            data.words.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.word }).forEach { item ->
                put(JSONObject().apply {
                    put("word", item.word)
                    put("translation", item.translation)
                    if (item.phonetic.isNotBlank()) put("phonetic", item.phonetic)
                    if (item.note.isNotBlank()) put("note", item.note)
                    item.notebook?.takeIf(String::isNotBlank)?.let { put("notebook", it) }
                    item.addedAt?.let { put("addedAt", formatTime(it)) }
                })
            }
        })
        return root.toString(2) + "\n"
    }

    fun decode(json: String): TransferData {
        val root = try {
            JSONObject(json)
        } catch (error: JSONException) {
            throw IllegalArgumentException("文件不是有效的 JSON：${error.message}")
        }
        val notebookNames = mutableListOf<String>()
        val notebookKeys = mutableSetOf<String>()
        val notebooks = root.optJSONArray("notebooks")
        if (root.has("notebooks") && notebooks == null) invalid("notebooks", "必须是数组")
        notebooks?.forEachIndexed("notebooks") { index, value ->
            val name = requireString(value, "notebooks[$index]").trim()
            validateNotebook(name, "notebooks[$index]")
            if (!notebookKeys.add(name.lowercase(Locale.ROOT))) invalid("notebooks[$index]", "分册名称重复")
            notebookNames += name
        }

        val words = root.optJSONArray("words") ?: invalid("words", "必须是数组")
        val wordKeys = mutableSetOf<String>()
        val parsedWords = mutableListOf<TransferWord>()
        words.forEachIndexed("words") { index, value ->
            val path = "words[$index]"
            val item = value as? JSONObject ?: invalid(path, "必须是对象")
            val word = requireFieldString(item, "word", path).let(WordNormalizer::normalize)
            if (word.isBlank()) invalid("$path.word", "不能为空")
            if (!wordKeys.add(word.lowercase(Locale.ROOT))) invalid("$path.word", "生词重复")
            val translation = requireFieldString(item, "translation", path).trim()
            if (translation.isBlank()) invalid("$path.translation", "不能为空")
            if (translation.length > 4_000) invalid("$path.translation", "不能超过 4000 个字符")
            val phonetic = optionalString(item, "phonetic", path).trim().trim('/').also {
                if (it.length > 100) invalid("$path.phonetic", "不能超过 100 个字符")
            }
            val note = optionalString(item, "note", path).trim().also {
                if (it.length > 4_000) invalid("$path.note", "不能超过 4000 个字符")
            }
            val notebook = optionalNullableString(item, "notebook", path)?.trim()?.takeIf(String::isNotBlank)
            notebook?.let {
                validateNotebook(it, "$path.notebook")
                if (notebookKeys.add(it.lowercase(Locale.ROOT))) notebookNames += it
            }
            val addedAt = optionalNullableString(item, "addedAt", path)?.let {
                parseTime(it) ?: invalid("$path.addedAt", "必须是包含时区的 ISO 8601 时间")
            }
            parsedWords += TransferWord(word, translation, phonetic, note, notebook, addedAt)
        }
        return TransferData(notebookNames, parsedWords)
    }

    private fun JSONArray.forEachIndexed(path: String, block: (Int, Any) -> Unit) {
        for (index in 0 until length()) {
            val value = get(index)
            if (value == JSONObject.NULL) invalid("$path[$index]", "不能为 null")
            block(index, value)
        }
    }

    private fun requireFieldString(item: JSONObject, name: String, path: String): String {
        if (!item.has(name) || item.isNull(name)) invalid("$path.$name", "为必填字段")
        return requireString(item.get(name), "$path.$name")
    }

    private fun optionalString(item: JSONObject, name: String, path: String): String {
        if (!item.has(name) || item.isNull(name)) return ""
        return requireString(item.get(name), "$path.$name")
    }

    private fun optionalNullableString(item: JSONObject, name: String, path: String): String? {
        if (!item.has(name) || item.isNull(name)) return null
        return requireString(item.get(name), "$path.$name")
    }

    private fun requireString(value: Any, path: String): String =
        value as? String ?: invalid(path, "必须是字符串")

    private fun validateNotebook(name: String, path: String) {
        if (name.isBlank()) invalid(path, "不能为空")
        if (name.length > 40) invalid(path, "不能超过 40 个字符")
    }

    private fun invalid(path: String, reason: String): Nothing =
        throw IllegalArgumentException("$path $reason")

    private fun formatTime(timestamp: Long): String {
        val value = timeFormat().format(Date(timestamp))
        return value.dropLast(2) + ":" + value.takeLast(2)
    }

    private fun parseTime(value: String): Long? {
        val normalized = normalizeTimeZone(value)
        return listOf("yyyy-MM-dd'T'HH:mm:ssZ", "yyyy-MM-dd'T'HH:mm:ss.SSSZ").firstNotNullOfOrNull { pattern ->
            val position = ParsePosition(0)
            val date = timeFormat(pattern).parse(normalized, position)
            date?.time?.takeIf { position.index == normalized.length }
        }
    }

    private fun normalizeTimeZone(value: String): String = when {
        value.endsWith('Z') -> value.dropLast(1) + "+0000"
        value.length >= 6 && value[value.length - 3] == ':' &&
            value[value.length - 6] in charArrayOf('+', '-') -> value.removeRange(value.length - 3, value.length - 2)
        else -> value
    }

    private fun timeFormat(pattern: String = "yyyy-MM-dd'T'HH:mm:ssZ") = SimpleDateFormat(pattern, Locale.US).apply {
        isLenient = false
        timeZone = TimeZone.getDefault()
    }
}
