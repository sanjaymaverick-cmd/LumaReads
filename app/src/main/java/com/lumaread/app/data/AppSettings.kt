package com.lumaread.app.data

import android.content.Context
import com.lumaread.app.ui.theme.LumaThemeMode
import org.json.JSONObject

data class AppSettings(
    val schemaVersion: Int = CURRENT_SCHEMA,
    val theme: LumaThemeMode = LumaThemeMode.PAPER,
    val swipeNavigation: Boolean = true,
    val edgeTapNavigation: Boolean = true,
    val chromeAutoHide: Boolean = true,
    val voiceName: String = "",
    val speed: Float = 1f,
    val pitch: Float = 1f
) {
    companion object {
        const val CURRENT_SCHEMA = 1
    }
}

class AppSettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(): AppSettings {
        migrateLegacy()
        val raw = prefs.getString(KEY, null) ?: return AppSettings()
        return decode(raw)
    }

    fun save(settings: AppSettings) {
        val next = settings.copy(schemaVersion = AppSettings.CURRENT_SCHEMA)
        prefs.edit().putString(KEY, encode(next)).apply()
    }

    private fun migrateLegacy() {
        if (prefs.contains(KEY)) return
        val appearance = context.applicationContext.getSharedPreferences("lumaread_appearance", Context.MODE_PRIVATE)
        val voice = context.applicationContext.getSharedPreferences("lumaread_voice", Context.MODE_PRIVATE)
        val theme = runCatching {
            LumaThemeMode.valueOf(appearance.getString("theme", null) ?: "PAPER")
        }.getOrDefault(LumaThemeMode.PAPER)
        save(
            AppSettings(
                theme = theme,
                voiceName = voice.getString("voice_name", "").orEmpty(),
                speed = voice.getFloat("speed", 1f),
                pitch = voice.getFloat("pitch", 1f)
            )
        )
    }

    private val context = context.applicationContext

    companion object {
        private const val PREFS = "lumaread_settings"
        private const val KEY = "settings_json"

        fun encode(settings: AppSettings): String = JSONObject().apply {
            put("schemaVersion", settings.schemaVersion)
            put("theme", settings.theme.name)
            put("swipeNavigation", settings.swipeNavigation)
            put("edgeTapNavigation", settings.edgeTapNavigation)
            put("chromeAutoHide", settings.chromeAutoHide)
            put("voiceName", settings.voiceName)
            put("speed", settings.speed.toDouble())
            put("pitch", settings.pitch.toDouble())
        }.toString()

        fun decode(raw: String): AppSettings {
            val json = JSONObject(raw)
            val version = json.optInt("schemaVersion", 0)
            val theme = runCatching {
                LumaThemeMode.valueOf(json.optString("theme", "PAPER"))
            }.getOrDefault(LumaThemeMode.PAPER)
            return AppSettings(
                schemaVersion = version.coerceAtLeast(1),
                theme = theme,
                swipeNavigation = json.optBoolean("swipeNavigation", true),
                edgeTapNavigation = json.optBoolean("edgeTapNavigation", true),
                chromeAutoHide = json.optBoolean("chromeAutoHide", true),
                voiceName = json.optString("voiceName"),
                speed = json.optDouble("speed", 1.0).toFloat(),
                pitch = json.optDouble("pitch", 1.0).toFloat()
            )
        }
    }
}
