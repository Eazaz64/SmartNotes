package com.eazaz.smartnotes.data

import android.content.Context
import com.eazaz.smartnotes.calc.CalcEngine
import org.json.JSONObject

/** App-wide calculation settings. */
data class CalcSettings(
    val defaultMode: CalcEngine.Mode = CalcEngine.Mode.AUTO,
    /** Symbol used for unknown words and a bare "=". */
    val defaultOp: Char = '+',
    /** Word -> symbol, e.g. "total" -> '+', "remaining" -> '-'. */
    val words: Map<String, Char> = CalcEngine.Config.defaultWords(),
) {
    fun toConfig(): CalcEngine.Config = CalcEngine.Config(words, defaultOp)

    /** The mode a note actually uses. */
    fun modeFor(note: Note): CalcEngine.Mode = when (note.calcMode) {
        "auto" -> CalcEngine.Mode.AUTO
        "manual" -> CalcEngine.Mode.MANUAL
        "off" -> CalcEngine.Mode.OFF
        else -> defaultMode
    }

    companion object {
        fun modeKey(mode: CalcEngine.Mode): String = mode.name.lowercase()
    }
}

class SettingsStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("calc_settings", Context.MODE_PRIVATE)

    fun load(): CalcSettings {
        val mode = runCatching { CalcEngine.Mode.valueOf(prefs.getString("mode", "AUTO")!!) }
            .getOrDefault(CalcEngine.Mode.AUTO)
        val op = prefs.getString("op", "+")!!.firstOrNull() ?: '+'
        val words = prefs.getString("words", null)?.let { json ->
            runCatching {
                val o = JSONObject(json)
                o.keys().asSequence().associateWith { o.getString(it).first() }
            }.getOrNull()
        } ?: CalcEngine.Config.defaultWords()
        return CalcSettings(mode, op, words)
    }

    fun save(s: CalcSettings) {
        val o = JSONObject()
        s.words.forEach { (w, op) -> o.put(w, op.toString()) }
        prefs.edit()
            .putString("mode", s.defaultMode.name)
            .putString("op", s.defaultOp.toString())
            .putString("words", o.toString())
            .apply()
    }
}
