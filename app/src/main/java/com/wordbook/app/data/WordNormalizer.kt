package com.wordbook.app.data

object WordNormalizer {
    private val edgePunctuation = Regex("^[^A-Za-z'-]+|[^A-Za-z'-]+$")

    fun normalize(input: CharSequence?): String = input
        ?.toString()
        .orEmpty()
        .trim()
        .replace(edgePunctuation, "")
        .lowercase()
        .take(80)
}
