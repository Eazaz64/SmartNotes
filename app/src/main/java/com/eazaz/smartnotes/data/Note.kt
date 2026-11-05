package com.eazaz.smartnotes.data

import org.json.JSONArray
import org.json.JSONObject

data class CheckItem(
    val id: Long,
    val text: String,
    val checked: Boolean = false,
)

data class Note(
    val id: Long,
    val title: String = "",
    val content: String = "",
    val isChecklist: Boolean = false,
    val items: List<CheckItem> = emptyList(),
    val color: Int = 0,
    val pinned: Boolean = false,
    val archived: Boolean = false,
    val trashedAt: Long? = null,
    val labels: List<String> = emptyList(),
    /** "auto", "manual", "off", or null = use the app default. */
    val calcMode: String? = null,
    /** Lines the user removed from calculations (see CalcEngine keys). */
    val excluded: List<String> = emptyList(),
    val created: Long = System.currentTimeMillis(),
    val updated: Long = System.currentTimeMillis(),
) {
    val isTrashed: Boolean get() = trashedAt != null

    val isEmpty: Boolean
        get() = title.isBlank() && content.isBlank() && items.all { it.text.isBlank() }

    fun matches(query: String): Boolean {
        if (query.isBlank()) return true
        val q = query.trim()
        return title.contains(q, true) || content.contains(q, true) ||
            items.any { it.text.contains(q, true) } || labels.any { it.contains(q, true) }
    }

    /** Switch between plain text and checklist, keeping the text. */
    fun toggleChecklist(newId: () -> Long): Note =
        if (isChecklist) {
            copy(isChecklist = false, content = items.joinToString("\n") { it.text }, items = emptyList())
        } else {
            val lines = content.split("\n").filter { it.isNotBlank() }.ifEmpty { listOf("") }
            copy(isChecklist = true, content = "", items = lines.map { CheckItem(newId(), it) })
        }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("title", title)
        put("content", content)
        put("isChecklist", isChecklist)
        put("items", JSONArray().apply {
            items.forEach { put(JSONObject().put("id", it.id).put("text", it.text).put("checked", it.checked)) }
        })
        put("color", color)
        put("pinned", pinned)
        put("archived", archived)
        if (trashedAt != null) put("trashedAt", trashedAt)
        put("labels", JSONArray(labels))
        if (calcMode != null) put("calcMode", calcMode)
        put("excluded", JSONArray(excluded))
        put("created", created)
        put("updated", updated)
    }

    companion object {
        fun fromJson(o: JSONObject): Note {
            val itemsArr = o.optJSONArray("items") ?: JSONArray()
            val labelsArr = o.optJSONArray("labels") ?: JSONArray()
            val excludedArr = o.optJSONArray("excluded") ?: JSONArray()
            return Note(
                id = o.getLong("id"),
                title = o.optString("title"),
                content = o.optString("content"),
                isChecklist = o.optBoolean("isChecklist"),
                items = (0 until itemsArr.length()).map {
                    val i = itemsArr.getJSONObject(it)
                    CheckItem(i.getLong("id"), i.optString("text"), i.optBoolean("checked"))
                },
                color = o.optInt("color"),
                pinned = o.optBoolean("pinned"),
                archived = o.optBoolean("archived"),
                trashedAt = if (o.has("trashedAt")) o.getLong("trashedAt") else null,
                labels = (0 until labelsArr.length()).map { labelsArr.getString(it) },
                calcMode = when {
                    o.has("calcMode") -> o.getString("calcMode")
                    !o.optBoolean("autoCalc", true) -> "off" // older backups
                    else -> null
                },
                excluded = (0 until excludedArr.length()).map { excludedArr.getString(it) },
                created = o.optLong("created", System.currentTimeMillis()),
                updated = o.optLong("updated", System.currentTimeMillis()),
            )
        }
    }
}
