package com.eazaz.smartnotes

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eazaz.smartnotes.ui.CloseReason
import com.eazaz.smartnotes.ui.EditorScreen
import com.eazaz.smartnotes.ui.HomeScreen
import com.eazaz.smartnotes.ui.SettingsScreen
import com.eazaz.smartnotes.ui.theme.SmartNotesTheme
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private val vm: NotesViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SmartNotesTheme { App(vm) }
        }
    }

    override fun onStop() {
        super.onStop()
        vm.saveNow()
    }
}

@Composable
private fun App(vm: NotesViewModel) {
    val data by vm.data.collectAsStateWithLifecycle()
    val loaded by vm.loaded.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var openId by rememberSaveable { mutableStateOf<Long?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    fun message(text: String, undo: (() -> Unit)? = null) {
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            val result = snackbar.showSnackbar(
                message = text,
                actionLabel = if (undo != null) context.getString(R.string.undo) else null,
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) undo?.invoke()
        }
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        if (!loaded) return@Box

        val current = openId?.let { id -> data.notes.firstOrNull { it.id == id } }
        LaunchedEffect(openId, current == null) {
            if (openId != null && current == null) openId = null
        }

        if (showSettings) {
            SettingsScreen(settings = settings, vm = vm, onBack = { showSettings = false })
        } else if (current != null) {
            EditorScreen(
                note = current,
                allLabels = data.labels,
                settings = settings,
                vm = vm,
                snackbar = snackbar,
                onClose = { reason ->
                    val id = current.id
                    openId = null
                    when (reason) {
                        CloseReason.Back ->
                            if (vm.discardIfEmpty(id)) message(context.getString(R.string.empty_discarded))
                        CloseReason.Trashed ->
                            message(context.getString(R.string.note_trashed)) { vm.restore(id) }
                        CloseReason.Archived ->
                            message(context.getString(R.string.note_archived)) { vm.setArchived(id, false) }
                    }
                },
            )
        } else {
            HomeScreen(
                data = data,
                vm = vm,
                snackbar = snackbar,
                onOpen = { openId = it },
                onCreate = { checklist, label -> openId = vm.createNote(checklist, label) },
                onOpenSettings = { showSettings = true },
            )
        }
    }
}
