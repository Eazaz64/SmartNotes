package com.eazaz.smartnotes

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.eazaz.smartnotes.data.AppData
import com.eazaz.smartnotes.data.Note
import com.eazaz.smartnotes.data.NoteRepository
import com.eazaz.smartnotes.calc.CalcEngine
import com.eazaz.smartnotes.data.CalcSettings
import com.eazaz.smartnotes.data.SettingsStore
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

class NotesViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = NoteRepository(app)
    private val _data = MutableStateFlow(AppData())
    val data: StateFlow<AppData> = _data.asStateFlow()

    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    private val settingsStore = SettingsStore(app)
    private val _settings = MutableStateFlow(settingsStore.load())
    val settings: StateFlow<CalcSettings> = _settings.asStateFlow()

    private fun changeSettings(change: (CalcSettings) -> CalcSettings) {
        _settings.update(change)
        settingsStore.save(_settings.value)
    }

    fun setDefaultMode(mode: CalcEngine.Mode) = changeSettings { it.copy(defaultMode = mode) }
    fun setDefaultOp(op: Char) = changeSettings { it.copy(defaultOp = op) }
    fun setWord(word: String, op: Char) {
        val w = word.trim().lowercase()
        if (w.isNotEmpty()) changeSettings { it.copy(words = it.words + (w to op)) }
    }
    fun removeWord(word: String) = changeSettings { it.copy(words = it.words - word) }
    fun resetWords() = changeSettings { it.copy(words = CalcEngine.Config.defaultWords()) }

    private val idGen = AtomicLong(System.currentTimeMillis())
    fun newId(): Long = idGen.incrementAndGet()

    init {
        viewModelScope.launch {
            val weekAgo = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(7)
            val loadedData = repo.load()
            _data.value = loadedData.copy(
                notes = loadedData.notes.filter { (it.trashedAt ?: Long.MAX_VALUE) > weekAgo && !it.isEmpty },
            )
            val maxId = _data.value.notes.flatMap { n -> listOf(n.id) + n.items.map { it.id } }.maxOrNull() ?: 0
            if (maxId >= idGen.get()) idGen.set(maxId + 1)
            _loaded.value = true
            launchAutoSave()
        }
    }

    @OptIn(FlowPreview::class)
    private fun launchAutoSave() {
        viewModelScope.launch {
            _data.drop(1).debounce(400).collect { repo.save(it) }
        }
    }

    /** Called when the app goes to background so nothing typed is lost. */
    fun saveNow() {
        val snapshot = _data.value
        if (!_loaded.value) return
        viewModelScope.launch { withContext(NonCancellable) { repo.save(snapshot) } }
    }

    fun note(id: Long): Note? = _data.value.notes.firstOrNull { it.id == id }

    fun createNote(checklist: Boolean, label: String?): Long {
        val id = newId()
        val note = Note(
            id = id,
            isChecklist = checklist,
            items = if (checklist) listOf(com.eazaz.smartnotes.data.CheckItem(newId(), "")) else emptyList(),
            labels = listOfNotNull(label),
        )
        _data.update { it.copy(notes = listOf(note) + it.notes) }
        return id
    }

    fun updateNote(note: Note) {
        _data.update { d ->
            d.copy(notes = d.notes.map { if (it.id == note.id) note.copy(updated = System.currentTimeMillis()) else it })
        }
    }

    private fun modify(id: Long, change: (Note) -> Note) {
        _data.update { d -> d.copy(notes = d.notes.map { if (it.id == id) change(it) else it }) }
    }

    fun togglePin(id: Long) = modify(id) { it.copy(pinned = !it.pinned) }
    fun setArchived(id: Long, archived: Boolean) = modify(id) { it.copy(archived = archived, pinned = if (archived) false else it.pinned) }
    fun trash(id: Long) = modify(id) { it.copy(trashedAt = System.currentTimeMillis(), pinned = false) }
    fun restore(id: Long) = modify(id) { it.copy(trashedAt = null) }

    fun deleteForever(id: Long) {
        _data.update { d -> d.copy(notes = d.notes.filterNot { it.id == id }) }
    }

    fun emptyTrash() {
        _data.update { d -> d.copy(notes = d.notes.filterNot { it.isTrashed }) }
    }

    /** Removes the note if the user left it empty. Returns true if it was discarded. */
    fun discardIfEmpty(id: Long): Boolean {
        val n = note(id) ?: return false
        if (n.isEmpty) {
            deleteForever(id)
            return true
        }
        return false
    }

    // ---------- labels

    fun addLabel(name: String) {
        val clean = name.trim()
        if (clean.isEmpty()) return
        _data.update { d ->
            if (d.labels.any { it.equals(clean, true) }) d else d.copy(labels = (d.labels + clean).sortedBy { it.lowercase() })
        }
    }

    fun deleteLabel(name: String) {
        _data.update { d ->
            d.copy(
                labels = d.labels - name,
                notes = d.notes.map { if (name in it.labels) it.copy(labels = it.labels - name) else it },
            )
        }
    }

    fun toggleLabel(id: Long, label: String) = modify(id) {
        it.copy(labels = if (label in it.labels) it.labels - label else it.labels + label)
    }

    // ---------- backup

    suspend fun exportBackup(uri: Uri): Boolean = runCatching { repo.exportTo(uri, _data.value) }.isSuccess

    /** Merges a backup into current notes. Returns number of notes restored, or -1 on failure. */
    suspend fun importBackup(uri: Uri): Int {
        val incoming = runCatching { repo.importFrom(uri) }.getOrNull() ?: return -1
        _data.update { d ->
            val byId = d.notes.associateBy { it.id }.toMutableMap()
            incoming.notes.forEach { byId[it.id] = it }
            d.copy(
                notes = byId.values.sortedByDescending { it.updated },
                labels = (d.labels + incoming.labels).distinctBy { it.lowercase() }.sortedBy { it.lowercase() },
            )
        }
        val maxId = incoming.notes.flatMap { n -> listOf(n.id) + n.items.map { it.id } }.maxOrNull() ?: 0
        if (maxId >= idGen.get()) idGen.set(maxId + 1)
        return incoming.notes.size
    }
}
