package com.wordbook.app

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.print.PrintAttributes
import android.print.PrintManager
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.wordbook.app.data.WordNormalizer
import com.wordbook.app.printing.PrintContent
import com.wordbook.app.printing.WordPrintAdapter
import com.wordbook.app.ui.WordbookApp
import com.wordbook.app.ui.WordbookViewModel
import com.wordbook.app.update.AppUpdate
import com.wordbook.app.update.GitHubUpdateChecker
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
    private var availableUpdate by mutableStateOf<AppUpdate?>(null)
    private var pendingNotificationUpdate: AppUpdate? = null
    private val updateChecker by lazy { GitHubUpdateChecker(applicationContext) }

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            pendingNotificationUpdate?.let { update ->
                if (granted) showUpdateNotification(update)
            }
            pendingNotificationUpdate = null
        }

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
                availableUpdate = availableUpdate,
                onDismissUpdate = { availableUpdate = null },
                onIgnoreUpdate = ::ignoreUpdate,
                onOpenUpdate = ::openUpdate,
            )
        }
        if ((application as WordbookApplication).markUpdateCheckStarted()) checkForUpdate()
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

    private fun checkForUpdate() {
        lifecycleScope.launch {
            runCatching { updateChecker.checkForUpdate() }.getOrNull()?.let { update ->
                availableUpdate = update
                notifyUpdate(update)
            }
        }
    }

    private fun notifyUpdate(update: AppUpdate) {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            pendingNotificationUpdate = update
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            showUpdateNotification(update)
        }
    }

    private fun showUpdateNotification(update: AppUpdate) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    UPDATE_CHANNEL_ID,
                    "应用更新",
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply { description = "生词本新版本提醒" },
            )
        }
        val releaseIntent = Intent(Intent.ACTION_VIEW, Uri.parse(update.releaseUrl))
        val pendingIntent = PendingIntent.getActivity(
            this,
            update.version.hashCode(),
            releaseIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(this, UPDATE_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("生词本 v${update.version} 可用")
            .setContentText(update.title)
            .setStyle(NotificationCompat.BigTextStyle().bigText(update.notes))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        manager.notify(UPDATE_NOTIFICATION_ID, notification)
    }

    private fun ignoreUpdate(update: AppUpdate) {
        updateChecker.ignore(update.version)
        availableUpdate = null
    }

    private fun openUpdate(update: AppUpdate) {
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(update.releaseUrl))) }
            .onFailure { toast("无法打开更新页面") }
        availableUpdate = null
    }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()

    private companion object {
        const val UPDATE_CHANNEL_ID = "app_updates"
        const val UPDATE_NOTIFICATION_ID = 4300
    }
}
