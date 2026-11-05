package com.eazaz.smartnotes.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Label
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.RestoreFromTrash
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import com.eazaz.smartnotes.NotesViewModel
import com.eazaz.smartnotes.R
import com.eazaz.smartnotes.data.AppData
import com.eazaz.smartnotes.data.Note
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val F_NOTES = "notes"
private const val F_ARCHIVE = "archive"
private const val F_TRASH = "trash"
private const val F_LABEL = "label:"

@Composable
fun HomeScreen(
    data: AppData,
    vm: NotesViewModel,
    snackbar: SnackbarHostState,
    onOpen: (Long) -> Unit,
    onCreate: (checklist: Boolean, label: String?) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(DrawerValue.Closed)

    var filter by rememberSaveable { mutableStateOf(F_NOTES) }
    var query by rememberSaveable { mutableStateOf("") }
    var showLabelEditor by remember { mutableStateOf(false) }
    var showLanguage by remember { mutableStateOf(false) }
    var showEmptyTrash by remember { mutableStateOf(false) }
    var trashDialogNote by remember { mutableStateOf<Note?>(null) }

    val currentLabel = if (filter.startsWith(F_LABEL)) filter.removePrefix(F_LABEL) else null
    LaunchedEffect(currentLabel, data.labels) {
        if (currentLabel != null && currentLabel !in data.labels) filter = F_NOTES
    }

    BackHandler(enabled = drawerState.isOpen || filter != F_NOTES || query.isNotEmpty()) {
        when {
            drawerState.isOpen -> scope.launch { drawerState.close() }
            query.isNotEmpty() -> query = ""
            else -> filter = F_NOTES
        }
    }

    val backupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            val ok = vm.exportBackup(uri)
            snackbar.showSnackbar(context.getString(if (ok) R.string.backup_done else R.string.restore_failed))
        }
    }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val n = vm.importBackup(uri)
            snackbar.showSnackbar(
                if (n >= 0) context.getString(R.string.restore_done, n) else context.getString(R.string.restore_failed),
            )
        }
    }

    fun select(f: String) {
        filter = f
        query = ""
        scope.launch { drawerState.close() }
    }

    // ---------- which notes to show
    val visible = data.notes.filter { n ->
        when {
            filter == F_TRASH -> n.isTrashed
            n.isTrashed -> false
            filter == F_ARCHIVE -> n.archived
            currentLabel != null -> currentLabel in n.labels && !n.archived
            query.isNotBlank() -> true // search notes + archive
            else -> !n.archived
        } && n.matches(query)
    }.sortedByDescending { it.updated }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 12.dp)) {
                    Text(
                        stringResource(R.string.app_name),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 16.dp),
                    )
                    DrawerItem(Icons.Outlined.Lightbulb, stringResource(R.string.notes), filter == F_NOTES) { select(F_NOTES) }
                    if (data.labels.isNotEmpty()) {
                        Text(
                            stringResource(R.string.labels),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp),
                        )
                        data.labels.forEach { l ->
                            DrawerItem(Icons.Outlined.Label, l, filter == F_LABEL + l) { select(F_LABEL + l) }
                        }
                    }
                    DrawerItem(Icons.Outlined.Edit, stringResource(R.string.edit_labels), false) {
                        showLabelEditor = true
                        scope.launch { drawerState.close() }
                    }
                    DrawerItem(Icons.Outlined.Archive, stringResource(R.string.archive), filter == F_ARCHIVE) { select(F_ARCHIVE) }
                    DrawerItem(Icons.Outlined.Delete, stringResource(R.string.trash), filter == F_TRASH) { select(F_TRASH) }
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    DrawerItem(Icons.Outlined.Backup, stringResource(R.string.backup), false) {
                        scope.launch { drawerState.close() }
                        val stamp = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(Date())
                        backupLauncher.launch("SmartNotes-backup-$stamp.json")
                    }
                    DrawerItem(Icons.Outlined.Restore, stringResource(R.string.restore_backup), false) {
                        scope.launch { drawerState.close() }
                        restoreLauncher.launch(arrayOf("*/*"))
                    }
                    DrawerItem(Icons.Outlined.Calculate, stringResource(R.string.calculation), false) {
                        scope.launch { drawerState.close() }
                        onOpenSettings()
                    }
                    DrawerItem(Icons.Outlined.Language, stringResource(R.string.language), false) {
                        showLanguage = true
                        scope.launch { drawerState.close() }
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }
        },
    ) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbar) },
            topBar = {
                SearchBar(
                    query = query,
                    onQuery = { query = it },
                    onMenu = { scope.launch { drawerState.open() } },
                )
            },
            bottomBar = {
                if (filter == F_NOTES || currentLabel != null) {
                    BottomAppBar(
                        actions = {
                            IconButton(onClick = { onCreate(true, currentLabel) }) {
                                Icon(Icons.Outlined.CheckBox, contentDescription = stringResource(R.string.new_list))
                            }
                        },
                        floatingActionButton = {
                            FloatingActionButton(
                                onClick = { onCreate(false, currentLabel) },
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                elevation = FloatingActionButtonDefaults.bottomAppBarFabElevation(),
                            ) {
                                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.new_note))
                            }
                        },
                    )
                }
            },
        ) { padding ->
            val pinned = if (filter == F_NOTES || currentLabel != null) visible.filter { it.pinned } else emptyList()
            val others = visible - pinned.toSet()

            Box(Modifier.fillMaxSize().padding(padding)) {
                LazyVerticalStaggeredGrid(
                    columns = StaggeredGridCells.Adaptive(160.dp),
                    contentPadding = PaddingValues(12.dp),
                    verticalItemSpacing = 8.dp,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    val heading = when {
                        filter == F_ARCHIVE -> context.getString(R.string.archive)
                        filter == F_TRASH -> context.getString(R.string.trash)
                        currentLabel != null -> currentLabel
                        else -> null
                    }
                    if (heading != null) {
                        item(span = StaggeredGridItemSpan.FullLine, key = "heading") {
                            Text(heading, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(4.dp))
                        }
                    }
                    if (filter == F_TRASH && visible.isNotEmpty()) {
                        item(span = StaggeredGridItemSpan.FullLine, key = "trashinfo") {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    stringResource(R.string.trash_info),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f).padding(start = 4.dp),
                                )
                                TextButton(onClick = { showEmptyTrash = true }) { Text(stringResource(R.string.empty_trash_btn)) }
                            }
                        }
                    }
                    if (pinned.isNotEmpty()) {
                        item(span = StaggeredGridItemSpan.FullLine, key = "pinnedH") { SectionHeader(stringResource(R.string.pinned)) }
                        items(pinned, key = { it.id }) { n -> NoteCard(n) { onOpen(n.id) } }
                        if (others.isNotEmpty()) {
                            item(span = StaggeredGridItemSpan.FullLine, key = "othersH") { SectionHeader(stringResource(R.string.others)) }
                        }
                    }
                    items(others, key = { it.id }) { n ->
                        NoteCard(n) { if (n.isTrashed) trashDialogNote = n else onOpen(n.id) }
                    }
                }

                if (visible.isEmpty()) {
                    val (icon, msg) = when {
                        query.isNotBlank() -> Icons.Outlined.Lightbulb to R.string.empty_search
                        filter == F_ARCHIVE -> Icons.Outlined.Archive to R.string.empty_archive
                        filter == F_TRASH -> Icons.Outlined.Delete to R.string.empty_trash
                        currentLabel != null -> Icons.Outlined.Label to R.string.empty_label
                        else -> Icons.Outlined.Lightbulb to R.string.empty_notes
                    }
                    Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(icon, null, Modifier.size(96.dp), tint = MaterialTheme.colorScheme.outline)
                        Spacer(Modifier.height(12.dp))
                        Text(stringResource(msg), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }

    // ---------- dialogs
    if (showLabelEditor) LabelEditorDialog(data.labels, vm) { showLabelEditor = false }

    if (showLanguage) LanguageDialog { showLanguage = false }

    if (showEmptyTrash) {
        AlertDialog(
            onDismissRequest = { showEmptyTrash = false },
            text = { Text(stringResource(R.string.empty_trash_confirm)) },
            confirmButton = {
                TextButton(onClick = { vm.emptyTrash(); showEmptyTrash = false }) { Text(stringResource(R.string.delete_forever)) }
            },
            dismissButton = { TextButton(onClick = { showEmptyTrash = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    trashDialogNote?.let { n ->
        AlertDialog(
            onDismissRequest = { trashDialogNote = null },
            title = { Text(stringResource(R.string.trashed_note)) },
            text = { Text(n.title.ifBlank { n.content.take(80) }) },
            confirmButton = {
                TextButton(onClick = { vm.restore(n.id); trashDialogNote = null }) {
                    Icon(Icons.Outlined.RestoreFromTrash, null)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.restore))
                }
            },
            dismissButton = {
                TextButton(onClick = { vm.deleteForever(n.id); trashDialogNote = null }) {
                    Icon(Icons.Outlined.DeleteForever, null)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.delete_forever))
                }
            },
        )
    }
}

@Composable
private fun SearchBar(query: String, onQuery: (String) -> Unit, onMenu: () -> Unit) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .fillMaxWidth()
            .height(52.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onMenu) { Icon(Icons.Filled.Menu, stringResource(R.string.menu)) }
            Box(Modifier.weight(1f)) {
                if (query.isEmpty()) {
                    Text(stringResource(R.string.search_hint), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQuery,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQuery("") }) { Icon(Icons.Filled.Close, stringResource(R.string.clear)) }
            } else {
                Spacer(Modifier.width(16.dp))
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 6.dp, top = 8.dp, bottom = 2.dp),
    )
}

@Composable
private fun DrawerItem(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    NavigationDrawerItem(
        icon = { Icon(icon, null) },
        label = { Text(label) },
        selected = selected,
        onClick = onClick,
    )
}

@Composable
private fun LabelEditorDialog(labels: List<String>, vm: NotesViewModel, onDismiss: () -> Unit) {
    var newLabel by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.edit_labels)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = newLabel,
                    onValueChange = { newLabel = it },
                    placeholder = { Text(stringResource(R.string.create_label)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { vm.addLabel(newLabel); newLabel = "" }),
                    trailingIcon = {
                        IconButton(onClick = { vm.addLabel(newLabel); newLabel = "" }) { Icon(Icons.Filled.Add, stringResource(R.string.add)) }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                labels.forEach { l ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Label, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(12.dp))
                        Text(l, Modifier.weight(1f))
                        IconButton(onClick = { vm.deleteLabel(l) }) {
                            Icon(Icons.Outlined.Delete, stringResource(R.string.delete_label))
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.done)) } },
    )
}

@Composable
private fun LanguageDialog(onDismiss: () -> Unit) {
    val current = AppCompatDelegate.getApplicationLocales().toLanguageTags()
    val options = listOf("" to stringResource(R.string.lang_system), "en" to "English", "bn" to "বাংলা")
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.language)) },
        text = {
            Column {
                options.forEach { (tag, name) ->
                    val selected = if (tag.isEmpty()) current.isEmpty() else current.startsWith(tag)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onDismiss()
                                AppCompatDelegate.setApplicationLocales(
                                    if (tag.isEmpty()) LocaleListCompat.getEmptyLocaleList()
                                    else LocaleListCompat.forLanguageTags(tag),
                                )
                            }
                            .padding(vertical = 4.dp),
                    ) {
                        RadioButton(selected = selected, onClick = null)
                        Spacer(Modifier.width(12.dp))
                        Text(name)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
