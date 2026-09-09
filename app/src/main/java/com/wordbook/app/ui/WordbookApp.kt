package com.wordbook.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wordbook.app.data.DictionaryEntry
import com.wordbook.app.data.Notebook
import com.wordbook.app.data.SavedWord
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Ink = Color(0xFF17201D)
private val Paper = Color(0xFFF7F8F4)
private val Sage = Color(0xFF386A55)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WordbookApp(viewModel: WordbookViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    MaterialTheme(
        colorScheme = MaterialTheme.colorScheme.copy(
            primary = Sage,
            onSurface = Ink,
            surface = Paper,
            background = Paper,
        ),
    ) {
        Scaffold(
            containerColor = Paper,
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            when (state.section) {
                                AppSection.LOOKUP -> "查词"
                                AppSection.WORDS -> "我的生词"
                                AppSection.NOTEBOOKS -> "分册"
                            },
                            fontWeight = FontWeight.SemiBold,
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Paper),
                )
            },
            bottomBar = {
                NavigationBar(containerColor = Color.White) {
                    NavigationItem("查词", Icons.Outlined.Search, state.section == AppSection.LOOKUP) {
                        viewModel.selectSection(AppSection.LOOKUP)
                    }
                    NavigationItem("生词本", Icons.Outlined.Book, state.section == AppSection.WORDS) {
                        viewModel.selectSection(AppSection.WORDS)
                    }
                    NavigationItem("分册", Icons.Outlined.Folder, state.section == AppSection.NOTEBOOKS) {
                        viewModel.selectSection(AppSection.NOTEBOOKS)
                    }
                }
            },
            floatingActionButton = {
                when (state.section) {
                    AppSection.WORDS -> {
                        var showManualEntry by remember { mutableStateOf(false) }
                        FloatingActionButton(onClick = { showManualEntry = true }) {
                            Icon(Icons.Outlined.Add, contentDescription = "手动录入生词")
                        }
                        if (showManualEntry) {
                            ManualWordDialog(
                                notebooks = state.notebooks,
                                onDismiss = { showManualEntry = false },
                                onConfirm = { word, translation, phonetic, notebookId ->
                                    viewModel.addManualWord(word, translation, phonetic, notebookId) {
                                        if (it) showManualEntry = false
                                    }
                                },
                            )
                        }
                    }
                    AppSection.NOTEBOOKS -> {
                        var showCreate by remember { mutableStateOf(false) }
                        FloatingActionButton(onClick = { showCreate = true }) {
                            Icon(Icons.Outlined.Add, contentDescription = "新建分册")
                        }
                        if (showCreate) {
                            NotebookNameDialog(
                                title = "新建分册",
                                initialName = "",
                                onDismiss = { showCreate = false },
                                onConfirm = { name ->
                                    viewModel.addNotebook(name) { if (it) showCreate = false }
                                },
                            )
                        }
                    }
                    AppSection.LOOKUP -> Unit
                }
            },
        ) { innerPadding ->
            when (state.section) {
                AppSection.LOOKUP -> LookupScreen(
                    state = state,
                    onQueryChange = viewModel::updateLookupQuery,
                    onSuggestion = viewModel::chooseSuggestion,
                    onAdd = viewModel::addCurrentWord,
                    onClearHistory = viewModel::clearSearchHistory,
                    modifier = Modifier.padding(innerPadding),
                )
                AppSection.WORDS -> WordsScreen(
                    state = state,
                    onFilterChange = viewModel::updateWordFilter,
                    onNotebookFilter = viewModel::selectNotebookFilter,
                    onRemove = viewModel::removeWord,
                    onMove = viewModel::moveWord,
                    onUpdateNote = viewModel::updateNote,
                    modifier = Modifier.padding(innerPadding),
                )
                AppSection.NOTEBOOKS -> NotebooksScreen(
                    notebooks = state.notebooks,
                    onRename = viewModel::renameNotebook,
                    onDelete = viewModel::deleteNotebook,
                    modifier = Modifier.padding(innerPadding),
                )
            }
        }
    }
}

@Composable
private fun RowScope.NavigationItem(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
) {
    NavigationBarItem(
        selected = selected,
        onClick = onClick,
        icon = { Icon(icon, contentDescription = null) },
        label = { Text(label) },
    )
}

@Composable
private fun LookupScreen(
    state: WordbookUiState,
    onQueryChange: (String) -> Unit,
    onSuggestion: (DictionaryEntry) -> Unit,
    onAdd: (Long?) -> Unit,
    onClearHistory: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var chooseNotebook by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            OutlinedTextField(
                value = state.lookupQuery,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("输入英文单词") },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                shape = RoundedCornerShape(18.dp),
            )
        }

        state.exactEntry?.let { entry ->
            item {
                DictionaryCard(
                    entry = entry,
                    saved = state.savedExactWord,
                    onAdd = { chooseNotebook = true },
                )
            }
        }

        if (state.lookupFinished && state.lookupQuery.isNotBlank() && state.exactEntry == null) {
            item {
                EmptyMessage("没有找到完全匹配的词条", "可以从下方近似结果中选择。")
            }
        }

        if (state.suggestions.isNotEmpty() && state.exactEntry == null) {
            item { SectionLabel("联想") }
            items(state.suggestions, key = { it.word }) { entry ->
                SuggestionRow(entry, onClick = { onSuggestion(entry) })
            }
        }

        if (state.lookupQuery.isBlank() && state.searchHistory.isNotEmpty()) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionLabel("搜索历史")
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onClearHistory) { Text("清空") }
                }
            }
            items(state.searchHistory, key = { it.word }) { history ->
                Surface(
                    color = Color.White,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().clickable { onQueryChange(history.word) },
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Outlined.Search, contentDescription = null, tint = Sage)
                        Spacer(Modifier.width(12.dp))
                        Text(history.word, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }

        if (state.lookupQuery.isBlank() && state.searchHistory.isEmpty()) {
            item {
                EmptyMessage(
                    "离线查词",
                    "输入或粘贴英文单词，也可以在其他应用中选中文本后选择“生词本”。",
                )
            }
        }
    }

    if (chooseNotebook) {
        NotebookPickerDialog(
            notebooks = state.notebooks,
            selectedId = null,
            onDismiss = { chooseNotebook = false },
            onSelect = {
                onAdd(it)
                chooseNotebook = false
            },
        )
    }
}

@Composable
private fun DictionaryCard(entry: DictionaryEntry, saved: Boolean, onAdd: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(22.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(entry.word, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            if (entry.phonetic.isNotBlank()) {
                Text("/${entry.phonetic.trim('/')} /", color = Sage)
            }
            HorizontalDivider(color = Color(0xFFE5E9E4))
            Text(entry.translation, style = MaterialTheme.typography.bodyLarge)
            if (saved) {
                OutlinedButton(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.Check, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("已在生词本")
                }
            } else {
                Button(onClick = onAdd, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.BookmarkAdd, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("加入生词本")
                }
            }
        }
    }
}

@Composable
private fun SuggestionRow(entry: DictionaryEntry, onClick: () -> Unit) {
    Surface(
        color = Color.White,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(entry.word, fontWeight = FontWeight.SemiBold)
            Text(
                entry.translation.replace('\n', ' '),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = Color(0xFF5F6964),
            )
        }
    }
}

@Composable
private fun WordsScreen(
    state: WordbookUiState,
    onFilterChange: (String) -> Unit,
    onNotebookFilter: (Long?) -> Unit,
    onRemove: (Long) -> Unit,
    onMove: (Long, Long?) -> Unit,
    onUpdateNote: (Long, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedWord by remember { mutableStateOf<SavedWord?>(null) }

    Column(modifier.fillMaxSize()) {
        OutlinedTextField(
            value = state.wordFilter,
            onValueChange = onFilterChange,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
            singleLine = true,
            label = { Text("检索单词、释义或批注") },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            shape = RoundedCornerShape(18.dp),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                FilterChip(
                    selected = state.selectedNotebookId == null,
                    onClick = { onNotebookFilter(null) },
                    label = { Text("全部") },
                )
            }
            items(state.notebooks, key = { it.id }) { notebook ->
                FilterChip(
                    selected = state.selectedNotebookId == notebook.id,
                    onClick = { onNotebookFilter(notebook.id) },
                    label = { Text(notebook.name) },
                )
            }
        }
        if (state.savedWords.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                EmptyMessage("这里还没有单词", "去查词页收藏，或点右下角的 + 手动录入。")
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                itemsIndexed(state.savedWords, key = { _, word -> word.id }) { index, word ->
                    val initial = word.word.firstOrNull()?.uppercaseChar()?.toString().orEmpty()
                    val previousInitial = state.savedWords.getOrNull(index - 1)
                        ?.word?.firstOrNull()?.uppercaseChar()?.toString()
                    if (initial != previousInitial) SectionLabel(initial)
                    SavedWordRow(word, onClick = { selectedWord = word })
                }
            }
        }
    }

    selectedWord?.let { word ->
        SavedWordDialog(
            word = word,
            notebooks = state.notebooks,
            onDismiss = { selectedWord = null },
            onMove = { notebookId ->
                onMove(word.id, notebookId)
                selectedWord = null
            },
            onSaveNote = { note ->
                onUpdateNote(word.id, note)
                selectedWord = null
            },
            onDelete = {
                onRemove(word.id)
                selectedWord = null
            },
        )
    }
}

@Composable
private fun ManualWordDialog(
    notebooks: List<Notebook>,
    onDismiss: () -> Unit,
    onConfirm: (String, String, String, Long?) -> Unit,
) {
    var word by remember { mutableStateOf("") }
    var translation by remember { mutableStateOf("") }
    var phonetic by remember { mutableStateOf("") }
    var notebookId by remember { mutableStateOf<Long?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("手动录入生词") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = word,
                    onValueChange = { word = it.take(80) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("英文单词或短语 *") },
                )
                OutlinedTextField(
                    value = translation,
                    onValueChange = { translation = it.take(4_000) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 6,
                    label = { Text("中文释义 *") },
                )
                OutlinedTextField(
                    value = phonetic,
                    onValueChange = { phonetic = it.take(100) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("音标（可选）") },
                )
                Text("选择分册", style = MaterialTheme.typography.labelLarge)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        FilterChip(
                            selected = notebookId == null,
                            onClick = { notebookId = null },
                            label = { Text("未分册") },
                        )
                    }
                    items(notebooks, key = { it.id }) { notebook ->
                        FilterChip(
                            selected = notebookId == notebook.id,
                            onClick = { notebookId = notebook.id },
                            label = { Text(notebook.name) },
                        )
                    }
                }
                Text(
                    "若单词已存在，将更新释义和分册并保留原加入日期。",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF68716D),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(word, translation, phonetic, notebookId) },
                enabled = word.isNotBlank() && translation.isNotBlank(),
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun SavedWordRow(word: SavedWord, onClick: () -> Unit) {
    Surface(
        color = Color.White,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(word.word, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                word.notebookName?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = Sage)
                }
            }
            Text(
                word.translation.replace('\n', ' '),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = Color(0xFF5F6964),
            )
            if (word.note.isNotBlank()) {
                Text(
                    "批注：${word.note.replace('\n', ' ')}",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = Sage,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun SavedWordDialog(
    word: SavedWord,
    notebooks: List<Notebook>,
    onDismiss: () -> Unit,
    onMove: (Long?) -> Unit,
    onSaveNote: (String) -> Unit,
    onDelete: () -> Unit,
) {
    var choosingNotebook by remember { mutableStateOf(false) }
    var note by remember(word.id, word.note) { mutableStateOf(word.note) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(word.word, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (word.phonetic.isNotBlank()) Text("/${word.phonetic.trim('/')} /", color = Sage)
                Text(word.translation)
                HorizontalDivider()
                Text("加入日期：${formatDate(word.addedAt)}", style = MaterialTheme.typography.bodySmall)
                Text("分册：${word.notebookName ?: "未分册"}", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it.take(4_000) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 5,
                    label = { Text("批注") },
                    placeholder = { Text("记录例句、用法或记忆提示") },
                )
                OutlinedButton(onClick = { choosingNotebook = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("移动到其他分册")
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSaveNote(note) }) { Text("保存") } },
        dismissButton = {
            TextButton(onClick = onDelete) {
                Icon(Icons.Outlined.Delete, contentDescription = null)
                Text("移除")
            }
        },
    )
    if (choosingNotebook) {
        NotebookPickerDialog(
            notebooks = notebooks,
            selectedId = word.notebookId,
            onDismiss = { choosingNotebook = false },
            onSelect = onMove,
        )
    }
}

@Composable
private fun NotebooksScreen(
    notebooks: List<Notebook>,
    onRename: (Long, String, (Boolean) -> Unit) -> Unit,
    onDelete: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var editing by remember { mutableStateOf<Notebook?>(null) }
    var deleting by remember { mutableStateOf<Notebook?>(null) }

    if (notebooks.isEmpty()) {
        Box(modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            EmptyMessage("还没有分册", "点右下角的 + 新建一个，例如“小说阅读”或“考试”。")
        }
    } else {
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(notebooks, key = { it.id }) { notebook ->
                Surface(color = Color.White, shape = RoundedCornerShape(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 18.dp, top = 10.dp, bottom = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(notebook.name, fontWeight = FontWeight.SemiBold)
                            Text("${notebook.wordCount} 个单词", style = MaterialTheme.typography.bodySmall)
                        }
                        IconButton(onClick = { editing = notebook }) {
                            Icon(Icons.Outlined.Edit, contentDescription = "重命名")
                        }
                        IconButton(onClick = { deleting = notebook }) {
                            Icon(Icons.Outlined.Delete, contentDescription = "删除")
                        }
                    }
                }
            }
        }
    }

    editing?.let { notebook ->
        NotebookNameDialog(
            title = "重命名分册",
            initialName = notebook.name,
            onDismiss = { editing = null },
            onConfirm = { name -> onRename(notebook.id, name) { if (it) editing = null } },
        )
    }
    deleting?.let { notebook ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("删除“${notebook.name}”？") },
            text = { Text("其中的单词会保留，并移动到“未分册”。") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(notebook.id)
                    deleting = null
                }) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("取消") } },
        )
    }
}

@Composable
private fun NotebookPickerDialog(
    notebooks: List<Notebook>,
    selectedId: Long?,
    onDismiss: () -> Unit,
    onSelect: (Long?) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择分册") },
        text = {
            LazyColumn {
                item {
                    PickerRow("未分册", selectedId == null) { onSelect(null) }
                }
                items(notebooks, key = { it.id }) { notebook ->
                    PickerRow(notebook.name, selectedId == notebook.id) { onSelect(notebook.id) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun PickerRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f))
        if (selected) Icon(Icons.Outlined.Check, contentDescription = null, tint = Sage)
    }
}

@Composable
private fun NotebookNameDialog(
    title: String,
    initialName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember(initialName) { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(40) },
                singleLine = true,
                label = { Text("分册名称") },
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun EmptyMessage(title: String, body: String) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text(body, color = Color(0xFF68716D), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        color = Sage,
        fontWeight = FontWeight.Bold,
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.padding(top = 6.dp, bottom = 2.dp),
    )
}

private fun formatDate(timestamp: Long): String = SimpleDateFormat("yyyy年M月d日", Locale.CHINA)
    .format(Date(timestamp))
