package com.eazaz.smartnotes.ui

import android.text.format.DateUtils
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FormatColorReset
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Label
import androidx.compose.material.icons.outlined.Notes
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.eazaz.smartnotes.NotesViewModel
import com.eazaz.smartnotes.R
import com.eazaz.smartnotes.calc.CalcEngine
import com.eazaz.smartnotes.data.CalcSettings
import com.eazaz.smartnotes.data.CheckItem
import com.eazaz.smartnotes.data.Note
import com.eazaz.smartnotes.share.NoteSharer
import com.eazaz.smartnotes.ui.theme.NoteColors

enum class CloseReason { Back, Trashed, Archived }

/** Runs auto-calculation on a text field change. */
private fun calcValue(old: TextFieldValue, new: TextFieldValue, enabled: Boolean): TextFieldValue {
    if (!enabled || new.text == old.text) return new
    val out = CalcEngine.process(new.text, new.selection.end, new.text.length < old.text.length)
    return if (out.text != new.text) TextFieldValue(out.text, TextRange(out.cursor)) else new
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EditorScreen(
    note: Note,
    allLabels: List<String>,
    settings: CalcSettings,
    vm: NotesViewModel,
    snackbar: SnackbarHostState,
    onClose: (CloseReason) -> Unit,
) {
    val context = LocalContext.current
    val bg = NoteColors.of(note.color)
    val onBg = MaterialTheme.colorScheme.onSurface
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    var title by remember(note.id) { mutableStateOf(TextFieldValue(note.title, TextRange(note.title.length))) }
    var content by remember(note.id) { mutableStateOf(TextFieldValue(note.content, TextRange(note.content.length))) }
    var focusItemId by remember(note.id) { mutableStateOf<Long?>(null) }
    var showColors by remember { mutableStateOf(false) }
    var showLabels by remember { mutableStateOf(false) }
    var showShare by remember { mutableStateOf(false) }
    val contentFocus = remember { FocusRequester() }
    var pickLine by remember(note.id) { mutableStateOf<Int?>(null) }
    var showModeMenu by remember { mutableStateOf(false) }

    val mode = settings.modeFor(note)
    val cfg = remember(settings) { settings.toConfig() }
    val excludedSet = remember(note.excluded) { note.excluded.toSet() }

    fun recalc(text: String, cursor: Int, protect: Boolean, excl: Set<String> = excludedSet) =
        CalcEngine.process(text, cursor, protect, mode, cfg, excl)

    /** Recalculate the whole note now (after changing mode or what is left out). */
    fun recalcNow(newMode: CalcEngine.Mode, excl: Set<String>, base: Note) {
        if (base.isChecklist) {
            if (newMode != CalcEngine.Mode.OFF) {
                vm.updateNote(base.copy(items = base.items.map { it.copy(text = CalcEngine.processLine(it.text) ?: it.text) }))
            } else vm.updateNote(base)
            return
        }
        val out = CalcEngine.process(content.text, content.selection.end, false, newMode, cfg, excl)
        content = TextFieldValue(out.text, TextRange(out.cursor.coerceAtMost(out.text.length)))
        vm.updateNote(base.copy(content = out.text))
    }

    // Numbers counted in Auto mode, shown in colour (grey + struck out when left out).
    val highlights = remember(content.text, mode, cfg, excludedSet) {
        if (mode != CalcEngine.Mode.AUTO || note.isChecklist) emptyList<CalcEngine.Highlight>()
        else CalcEngine.process(content.text, 0, false, mode, cfg, excludedSet)
            .let { o -> if (o.text == content.text) o.highlights.toList() else emptyList() }
    }
    val hlColor = MaterialTheme.colorScheme.primary
    val visual = remember(highlights, hlColor, muted) {
        VisualTransformation { t ->
            TransformedText(
                buildAnnotatedString {
                    append(t)
                    highlights.forEach { h ->
                        if (h.end <= t.length) addStyle(
                            if (h.excluded) SpanStyle(color = muted, textDecoration = TextDecoration.LineThrough)
                            else SpanStyle(color = hlColor, fontWeight = FontWeight.SemiBold),
                            h.start, h.end,
                        )
                    }
                },
                OffsetMapping.Identity,
            )
        }
    }
    val cursorHl = if (content.selection.collapsed) {
        highlights.firstOrNull { content.selection.start in it.start..it.end }
    } else null

    BackHandler { onClose(CloseReason.Back) }

    // When switching back from a checklist, load the joined text into the editor.
    LaunchedEffect(note.isChecklist) {
        if (!note.isChecklist && content.text != note.content) {
            content = TextFieldValue(note.content, TextRange(note.content.length))
        }
    }
    // New note: put the cursor where the user will type.
    LaunchedEffect(note.id) {
        if (note.isEmpty) {
            if (note.isChecklist) focusItemId = note.items.firstOrNull()?.id
            else runCatching { contentFocus.requestFocus() }
        }
    }

    val textStyle = MaterialTheme.typography.bodyLarge.copy(color = onBg)
    val cursor = SolidColor(MaterialTheme.colorScheme.primary)

    Scaffold(
        containerColor = bg,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {},
                colors = TopAppBarDefaults.topAppBarColors(containerColor = bg, scrolledContainerColor = bg),
                navigationIcon = {
                    IconButton(onClick = { onClose(CloseReason.Back) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = { vm.togglePin(note.id) }) {
                        Icon(
                            if (note.pinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                            stringResource(if (note.pinned) R.string.unpin else R.string.pin),
                        )
                    }
                    Box {
                        IconButton(onClick = { showShare = true }) {
                            Icon(Icons.Outlined.Share, stringResource(R.string.share))
                        }
                        DropdownMenu(expanded = showShare, onDismissRequest = { showShare = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.share_image)) },
                                leadingIcon = { Icon(Icons.Outlined.Image, null) },
                                onClick = { showShare = false; NoteSharer.shareImage(context, note) },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.share_pdf)) },
                                leadingIcon = { Icon(Icons.Outlined.PictureAsPdf, null) },
                                onClick = { showShare = false; NoteSharer.sharePdf(context, note) },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.share_text)) },
                                leadingIcon = { Icon(Icons.Outlined.Notes, null) },
                                onClick = { showShare = false; NoteSharer.shareText(context, note) },
                            )
                        }
                    }
                    IconButton(onClick = {
                        val nowArchived = !note.archived
                        vm.setArchived(note.id, nowArchived)
                        onClose(if (nowArchived) CloseReason.Archived else CloseReason.Back)
                    }) {
                        Icon(
                            if (note.archived) Icons.Outlined.Unarchive else Icons.Outlined.Archive,
                            stringResource(if (note.archived) R.string.unarchive else R.string.archive_action),
                        )
                    }
                },
            )
        },
        bottomBar = {
            Surface(color = bg) {
              Column(Modifier.navigationBarsPadding().imePadding()) {
                if (cursorHl != null) {
                    val num = content.text.substring(cursorHl.start, cursorHl.end.coerceAtMost(content.text.length))
                    HelperBar {
                        TextButton(onClick = {
                            val newExcl = if (cursorHl.excluded) note.excluded - cursorHl.key else note.excluded + cursorHl.key
                            val updated = note.copy(excluded = newExcl)
                            recalcNow(mode, newExcl.toSet(), updated)
                        }) {
                            Text(stringResource(if (cursorHl.excluded) R.string.add_back else R.string.remove_from_calc, num))
                        }
                    }
                } else if (mode == CalcEngine.Mode.MANUAL && pickLine != null && !note.isChecklist) {
                    HelperBar {
                        Text(
                            stringResource(R.string.pick_numbers),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f).padding(start = 12.dp),
                        )
                        TextButton(onClick = {
                            val line = pickLine ?: return@TextButton
                            val lines = content.text.split("\n").toMutableList()
                            if (line < lines.size) {
                                lines[line] = lines[line].trimEnd() + " ="
                                val joined = lines.joinToString("\n")
                                val endOfLine = lines.take(line + 1).sumOf { it.length + 1 } - 1
                                val out = recalc(joined, endOfLine, false)
                                content = TextFieldValue(out.text, TextRange(out.cursor))
                                vm.updateNote(note.copy(content = out.text))
                            }
                            pickLine = null
                        }) { Text(stringResource(R.string.done)) }
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 4.dp).height(52.dp),
                ) {
                    IconButton(onClick = { showColors = true }) { Icon(Icons.Outlined.Palette, stringResource(R.string.color)) }
                    IconButton(onClick = { vm.updateNote(note.toggleChecklist(vm::newId)) }) {
                        Icon(
                            if (note.isChecklist) Icons.Outlined.Notes else Icons.Outlined.CheckBox,
                            stringResource(if (note.isChecklist) R.string.hide_checkboxes else R.string.show_checkboxes),
                        )
                    }
                    IconButton(onClick = { showLabels = true }) { Icon(Icons.Outlined.Label, stringResource(R.string.labels)) }
                    Box {
                        IconButton(onClick = { showModeMenu = true }) {
                            Icon(
                                if (mode != CalcEngine.Mode.OFF) Icons.Filled.Calculate else Icons.Outlined.Calculate,
                                stringResource(R.string.calc_mode),
                                tint = if (mode != CalcEngine.Mode.OFF) MaterialTheme.colorScheme.primary else muted,
                            )
                        }
                        DropdownMenu(expanded = showModeMenu, onDismissRequest = { showModeMenu = false }) {
                            Text(
                                stringResource(R.string.calc_mode),
                                style = MaterialTheme.typography.labelMedium,
                                color = muted,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            )
                            val options = listOf(
                                null to stringResource(R.string.mode_default, modeName(settings.defaultMode)),
                                "auto" to modeName(CalcEngine.Mode.AUTO),
                                "manual" to modeName(CalcEngine.Mode.MANUAL),
                                "off" to modeName(CalcEngine.Mode.OFF),
                            )
                            options.forEach { (key, name) ->
                                DropdownMenuItem(
                                    text = { Text(name) },
                                    trailingIcon = { if (note.calcMode == key) Icon(Icons.Filled.Check, null) },
                                    onClick = {
                                        showModeMenu = false
                                        pickLine = null
                                        val updated = note.copy(calcMode = key)
                                        recalcNow(settings.modeFor(updated), excludedSet, updated)
                                    },
                                )
                            }
                        }
                    }
                    Text(
                        stringResource(R.string.edited, DateUtils.formatDateTime(
                            context, note.updated,
                            DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_MONTH,
                        )),
                        style = MaterialTheme.typography.labelSmall,
                        color = muted,
                        maxLines = 1,
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                    )
                    IconButton(onClick = { vm.trash(note.id); onClose(CloseReason.Trashed) }) {
                        Icon(Icons.Outlined.Delete, stringResource(R.string.delete))
                    }
                }
              }
            }
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            // Title
            Box {
                if (title.text.isEmpty()) {
                    Text(stringResource(R.string.title_hint), style = MaterialTheme.typography.titleLarge, color = muted)
                }
                BasicTextField(
                    value = title,
                    onValueChange = { v ->
                        val clean = if (v.text.contains('\n')) v.copy(text = v.text.replace("\n", " ")) else v
                        title = clean
                        if (clean.text != note.title) vm.updateNote(note.copy(title = clean.text))
                    },
                    textStyle = MaterialTheme.typography.titleLarge.copy(color = onBg),
                    cursorBrush = cursor,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = {
                        if (note.isChecklist) focusItemId = note.items.firstOrNull { !it.checked }?.id
                        else runCatching { contentFocus.requestFocus() }
                    }),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(14.dp))

            if (note.isChecklist) {
                ChecklistEditor(
                    note = note,
                    settings = settings,
                    vm = vm,
                    textStyle = textStyle,
                    focusItemId = focusItemId,
                    onFocusHandled = { focusItemId = null },
                    onFocusItem = { focusItemId = it },
                )
            } else {
                Box {
                    if (content.text.isEmpty()) {
                        Text(stringResource(R.string.note_hint), style = textStyle, color = muted)
                    }
                    BasicTextField(
                        value = content,
                        onValueChange = { v ->
                            val old = content
                            if (v.text == old.text) {
                                // Only the cursor moved (a tap). In Manual mode a tap on a number adds it.
                                val target = pickLine
                                if (mode == CalcEngine.Mode.MANUAL && target != null && v.selection.collapsed) {
                                    val tok = CalcEngine.numberAt(v.text, v.selection.start)
                                    if (tok != null && CalcEngine.lineOf(v.text, tok[0]) != target) {
                                        val out = CalcEngine.manualAppend(v.text, target, v.text.substring(tok[0], tok[1]))
                                        content = TextFieldValue(out.text, TextRange(out.cursor))
                                        vm.updateNote(note.copy(content = out.text))
                                        return@BasicTextField
                                    }
                                    if (CalcEngine.lineOf(v.text, v.selection.start) != target) pickLine = null
                                }
                                content = v
                            } else {
                                val nv = if (mode == CalcEngine.Mode.OFF) v else {
                                    val out = recalc(v.text, v.selection.end, v.text.length < old.text.length)
                                    if (out.text != v.text) TextFieldValue(out.text, TextRange(out.cursor)) else v
                                }
                                content = nv
                                if (nv.text != note.content) vm.updateNote(note.copy(content = nv.text))
                                if (mode == CalcEngine.Mode.MANUAL) {
                                    val line = CalcEngine.lineOf(nv.text, nv.selection.end)
                                    pickLine = when {
                                        CalcEngine.isManualStart(nv.text, line) -> line
                                        pickLine == line && CalcEngine.isManualOpen(nv.text, line) -> line
                                        else -> null
                                    }
                                }
                            }
                        },
                        textStyle = textStyle,
                        visualTransformation = visual,
                        cursorBrush = cursor,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 240.dp)
                            .focusRequester(contentFocus),
                    )
                }
            }

            if (note.isEmpty && mode != CalcEngine.Mode.OFF) {
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.calc_tip), style = MaterialTheme.typography.bodySmall, color = muted)
            }

            if (note.labels.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    note.labels.forEach { LabelChip(it) { showLabels = true } }
                }
            }
            Spacer(Modifier.height(48.dp))
        }
    }

    // ---------- color picker
    if (showColors) {
        ModalBottomSheet(onDismissRequest = { showColors = false }, containerColor = bg) {
            Text(
                stringResource(R.string.color),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(start = 20.dp, bottom = 12.dp),
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 32.dp),
            ) {
                repeat(NoteColors.count) { i ->
                    val selected = note.color == i
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(NoteColors.of(i))
                            .border(
                                if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                                else BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                CircleShape,
                            )
                            .clickable { vm.updateNote(note.copy(color = i)) },
                    ) {
                        when {
                            selected -> Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.primary)
                            i == 0 -> Icon(Icons.Outlined.FormatColorReset, null, tint = muted)
                        }
                    }
                }
            }
        }
    }

    // ---------- labels
    if (showLabels) {
        var newLabel by remember { mutableStateOf("") }
        fun addNew() {
            val clean = newLabel.trim()
            if (clean.isEmpty()) return
            val name = allLabels.firstOrNull { it.equals(clean, true) } ?: clean
            vm.addLabel(name)
            if (name !in note.labels) vm.toggleLabel(note.id, name)
            newLabel = ""
        }
        AlertDialog(
            onDismissRequest = { showLabels = false },
            title = { Text(stringResource(R.string.labels)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    OutlinedTextField(
                        value = newLabel,
                        onValueChange = { newLabel = it },
                        placeholder = { Text(stringResource(R.string.create_label)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { addNew() }),
                        trailingIcon = { IconButton(onClick = { addNew() }) { Icon(Icons.Filled.Add, stringResource(R.string.add)) } },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    allLabels.forEach { l ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().clickable { vm.toggleLabel(note.id, l) },
                        ) {
                            Checkbox(checked = l in note.labels, onCheckedChange = { vm.toggleLabel(note.id, l) })
                            Text(l)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showLabels = false }) { Text(stringResource(R.string.done)) } },
        )
    }
}

@Composable
private fun ChecklistEditor(
    note: Note,
    settings: CalcSettings,
    vm: NotesViewModel,
    textStyle: TextStyle,
    focusItemId: Long?,
    onFocusHandled: () -> Unit,
    onFocusItem: (Long) -> Unit,
) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val unchecked = note.items.filter { !it.checked }
    val checked = note.items.filter { it.checked }

    fun setItems(items: List<CheckItem>) = vm.updateNote(note.copy(items = items))

    @Composable
    fun row(item: CheckItem) = key(item.id) {
        ChecklistRow(
            item = item,
            autoCalc = settings.modeFor(note) != CalcEngine.Mode.OFF,
            textStyle = textStyle,
            requestFocus = focusItemId == item.id,
            onFocusHandled = onFocusHandled,
            onText = { t -> setItems(note.items.map { if (it.id == item.id) it.copy(text = t) else it }) },
            onToggle = { setItems(note.items.map { if (it.id == item.id) it.copy(checked = !it.checked) else it }) },
            onRemove = { setItems(note.items.filterNot { it.id == item.id }) },
            onEnter = { before, after ->
                val newItem = CheckItem(vm.newId(), after)
                val list = note.items.toMutableList()
                val idx = list.indexOfFirst { it.id == item.id }
                list[idx] = list[idx].copy(text = before)
                list.add(idx + 1, newItem)
                setItems(list)
                onFocusItem(newItem.id)
            },
        )
    }

    unchecked.forEach { row(it) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                val newItem = CheckItem(vm.newId(), "")
                val list = note.items.toMutableList()
                val lastUnchecked = list.indexOfLast { !it.checked }
                list.add(lastUnchecked + 1, newItem)
                setItems(list)
                onFocusItem(newItem.id)
            }
            .padding(vertical = 10.dp),
    ) {
        Spacer(Modifier.width(12.dp))
        Icon(Icons.Filled.Add, null, tint = muted)
        Spacer(Modifier.width(14.dp))
        Text(stringResource(R.string.list_item), color = muted, style = textStyle)
    }

    if (checked.isNotEmpty()) {
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.checked_items, checked.size),
            style = MaterialTheme.typography.labelMedium,
            color = muted,
            modifier = Modifier.padding(start = 12.dp, top = 6.dp, bottom = 4.dp),
        )
        checked.forEach { row(it) }
    }
}

@Composable
private fun ChecklistRow(
    item: CheckItem,
    autoCalc: Boolean,
    textStyle: TextStyle,
    requestFocus: Boolean,
    onFocusHandled: () -> Unit,
    onText: (String) -> Unit,
    onToggle: () -> Unit,
    onRemove: () -> Unit,
    onEnter: (before: String, after: String) -> Unit,
) {
    var value by remember { mutableStateOf(TextFieldValue(item.text, TextRange(item.text.length))) }
    var focused by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    LaunchedEffect(item.text) {
        if (item.text != value.text) value = TextFieldValue(item.text, TextRange(item.text.length))
    }
    LaunchedEffect(requestFocus) {
        if (requestFocus) {
            runCatching { focusRequester.requestFocus() }
            onFocusHandled()
        }
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = item.checked, onCheckedChange = { onToggle() })
        Box(Modifier.weight(1f).padding(vertical = 6.dp)) {
            if (value.text.isEmpty()) Text(stringResource(R.string.list_item), style = textStyle, color = muted)
            BasicTextField(
                value = value,
                onValueChange = { v ->
                    val nl = v.text.indexOf('\n')
                    if (nl >= 0) {
                        onEnter(v.text.substring(0, nl), v.text.substring(nl + 1).replace("\n", " "))
                        return@BasicTextField
                    }
                    val nv = calcValue(value, v, autoCalc)
                    value = nv
                    if (nv.text != item.text) onText(nv.text)
                },
                textStyle = if (item.checked) textStyle.copy(color = muted, textDecoration = TextDecoration.LineThrough) else textStyle,
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .onFocusChanged { focused = it.isFocused },
            )
        }
        if (focused) {
            IconButton(onClick = onRemove) { Icon(Icons.Filled.Close, stringResource(R.string.delete), tint = muted) }
        }
    }
}

@Composable
private fun HelperBar(content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Column {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp).height(44.dp),
            content = content,
        )
    }
}

@Composable
fun modeName(mode: CalcEngine.Mode): String = stringResource(
    when (mode) {
        CalcEngine.Mode.AUTO -> R.string.mode_auto
        CalcEngine.Mode.MANUAL -> R.string.mode_manual
        CalcEngine.Mode.OFF -> R.string.mode_off
    },
)
