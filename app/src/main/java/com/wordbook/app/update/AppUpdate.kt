package com.wordbook.app.update

data class AppUpdate(
    val version: String,
    val title: String,
    val notes: String,
    val releaseUrl: String,
)
