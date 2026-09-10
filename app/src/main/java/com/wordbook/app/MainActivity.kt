package com.wordbook.app

import android.content.Intent
import android.content.Context
import android.os.Bundle
import android.print.PrintAttributes
import android.print.PrintManager
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import com.wordbook.app.data.WordNormalizer
import com.wordbook.app.printing.PrintContent
import com.wordbook.app.printing.WordPrintAdapter
import com.wordbook.app.ui.WordbookApp
import com.wordbook.app.ui.WordbookViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    private val viewModel: WordbookViewModel by viewModels {
        WordbookViewModel.factory(application as WordbookApplication)
    }
    private var pendingExport: String? = null

    private val importLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@registerForActivityResult
        lifecycleScope.launch {
            val json = runCatching {
                withContext(Dispatchers.IO) {
                    contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                        ?: error("无法读取文件")
                }
            }
            json.fold(
                onSuccess = { content ->
                    viewModel.importJson(content) { result ->
                        result.fold(
                            onSuccess = {
                                toast("导入完成：新增 ${it.added}，更新 ${it.updated}，新建分册 ${it.notebooksCreated}")
                            },
                            onFailure = { toast("导入失败：${it.message ?: "文件格式错误"}") },
                        )
                    }
                },
                onFailure = { toast("导入失败：${it.message ?: "无法读取文件"}") },
            )
        }
    }

    private val exportLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val json = pendingExport.also { pendingExport = null }
        if (uri == null || json == null) return@registerForActivityResult
        lifecycleScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    contentResolver.openOutputStream(uri, "rwt")?.bufferedWriter(Charsets.UTF_8)?.use { it.write(json) }
                        ?: error("无法写入文件")
                }
            }
            toast(if (result.isSuccess) "生词本已导出" else "导出失败：${result.exceptionOrNull()?.message}")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntent(intent)
        setContent {
            WordbookApp(
                viewModel = viewModel,
                onImport = { importLauncher.launch(arrayOf("application/json", "text/json", "text/plain")) },
                onExport = ::exportWordbook,
                onPrint = ::printWords,
            )
        }
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

    private fun exportWordbook() {
        viewModel.exportJson { result ->
            result.fold(
                onSuccess = {
                    pendingExport = it
                    val timestamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
                    exportLauncher.launch("wordbook-export-$timestamp.json")
                },
                onFailure = { toast("导出失败：${it.message ?: "无法生成文件"}") },
            )
        }
    }

    private fun printWords(notebookIds: Set<Long>?, scopeName: String, content: PrintContent) {
        viewModel.loadWordsForPrint(notebookIds) { words ->
            if (words.isEmpty()) {
                toast("没有可打印的生词")
                return@loadWordsForPrint
            }
            val title = "生词本 · $scopeName"
            val manager = getSystemService(Context.PRINT_SERVICE) as PrintManager
            manager.print(
                title,
                WordPrintAdapter(this, title, words, content),
                PrintAttributes.Builder().setMediaSize(PrintAttributes.MediaSize.ISO_A4).build(),
            )
        }
    }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()
}
