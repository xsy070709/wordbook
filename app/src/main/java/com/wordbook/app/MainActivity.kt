package com.wordbook.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.wordbook.app.data.WordNormalizer
import com.wordbook.app.ui.WordbookApp
import com.wordbook.app.ui.WordbookViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: WordbookViewModel by viewModels {
        WordbookViewModel.factory(application as WordbookApplication)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntent(intent)
        setContent { WordbookApp(viewModel) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val text = when (intent?.action) {
            Intent.ACTION_PROCESS_TEXT -> intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)
            Intent.ACTION_SEND -> intent.getCharSequenceExtra(Intent.EXTRA_TEXT)
            else -> null
        }
        WordNormalizer.normalize(text).takeIf { it.isNotBlank() }?.let(viewModel::openExternalWord)
    }
}
