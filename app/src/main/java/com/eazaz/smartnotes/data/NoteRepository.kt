package com.eazaz.smartnotes.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Everything the app stores. Kept as one JSON file so it can later sync to a cloud account. */
data class AppData(
    val notes: List<Note> = emptyList(),
    val labels: List<String> = emptyList(),
)

/**
 * Offline storage: notes live in app-private storage as JSON.
 * Swap this class (or add a sync layer next to it) when moving to login + cloud sync.
 */
class NoteRepository(context: Context) {
    private val appContext = context.applicationContext
    private val file = File(appContext.filesDir, "notes.json")
    private val mutex = Mutex()

    suspend fun load(): AppData = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (!file.exists()) return@withLock AppData()
            runCatching { decode(file.readText()) }.getOrElse { AppData() }
        }
    }

    suspend fun save(data: AppData) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val tmp = File(file.parentFile, "notes.json.tmp")
            tmp.writeText(encode(data))
            if (!tmp.renameTo(file)) {
                file.writeText(tmp.readText())
                tmp.delete()
            }
        }
    }

    suspend fun exportTo(uri: Uri, data: AppData) = withContext(Dispatchers.IO) {
        appContext.contentResolver.openOutputStream(uri, "wt")?.use {
            it.write(encode(data).toByteArray(Charsets.UTF_8))
        } ?: error("Cannot open file")
    }

    suspend fun importFrom(uri: Uri): AppData = withContext(Dispatchers.IO) {
        val text = appContext.contentResolver.openInputStream(uri)?.use {
            it.readBytes().toString(Charsets.UTF_8)
        } ?: error("Cannot open file")
        decode(text)
    }

    companion object {
        private const val VERSION = 1

        fun encode(data: AppData): String = JSONObject().apply {
            put("app", "SmartNotes")
            put("version", VERSION)
            put("labels", JSONArray(data.labels))
            put("notes", JSONArray().apply { data.notes.forEach { put(it.toJson()) } })
        }.toString()

        fun decode(text: String): AppData {
            val o = JSONObject(text)
            val notesArr = o.getJSONArray("notes")
            val labelsArr = o.optJSONArray("labels") ?: JSONArray()
            return AppData(
                notes = (0 until notesArr.length()).map { Note.fromJson(notesArr.getJSONObject(it)) },
                labels = (0 until labelsArr.length()).map { labelsArr.getString(it) },
            )
        }
    }
}
