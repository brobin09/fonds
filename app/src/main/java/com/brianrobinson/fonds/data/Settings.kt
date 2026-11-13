package com.brianrobinson.fonds.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object Engine {
    const val AUTO = "auto"      // on-device first, Claude for handwriting if a key is set
    const val NANO = "nano"      // on-device Gemini Nano only
    const val CLAUDE = "claude"  // Claude for everything
    const val OFF = "off"        // text recognition only, describe by hand
}

data class SettingsState(
    val engine: String = Engine.AUTO,
    val claudeKey: String = "",
    val claudeModel: String = "claude-sonnet-5-5",
    val claudeForHandwritten: Boolean = true,
    val claudeFallback: Boolean = false,
    val processorName: String = "",
    val defaultRepository: String = "",
    val onePerPageDefault: Boolean = false,
    val keepScreenOn: Boolean = true,
    val theme: String = "system",
    val dynamicColor: Boolean = false,
    val includeDraftsInExport: Boolean = false,
    val includeItemsInExport: Boolean = true,
    val seenIntro: Boolean = false,
    val lastSourceRepository: String = "",
    val lastSourceCollection: String = "",
    val lastSourceLocation: String = "",
    val nanoLimitFixApplied: Boolean = false,
) {
    val hasClaude: Boolean get() = claudeKey.isNotBlank()
}

class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("fonds_settings", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(read())
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    val current: SettingsState get() = _state.value

    fun update(transform: (SettingsState) -> SettingsState) {
        val next = transform(_state.value)
        write(next)
        _state.value = next
    }

    private fun read(): SettingsState {
        val d = SettingsState()
        return SettingsState(
            engine = prefs.getString("engine", d.engine) ?: d.engine,
            claudeKey = prefs.getString("claudeKey", d.claudeKey) ?: d.claudeKey,
            claudeModel = prefs.getString("claudeModel", d.claudeModel) ?: d.claudeModel,
            claudeForHandwritten = prefs.getBoolean("claudeForHandwritten", d.claudeForHandwritten),
            claudeFallback = prefs.getBoolean("claudeFallback", d.claudeFallback),
            processorName = prefs.getString("processorName", d.processorName) ?: d.processorName,
            defaultRepository = prefs.getString("defaultRepository", d.defaultRepository) ?: d.defaultRepository,
            onePerPageDefault = prefs.getBoolean("onePerPageDefault", d.onePerPageDefault),
            keepScreenOn = prefs.getBoolean("keepScreenOn", d.keepScreenOn),
            theme = prefs.getString("theme", d.theme) ?: d.theme,
            dynamicColor = prefs.getBoolean("dynamicColor", d.dynamicColor),
            includeDraftsInExport = prefs.getBoolean("includeDraftsInExport", d.includeDraftsInExport),
            includeItemsInExport = prefs.getBoolean("includeItemsInExport", d.includeItemsInExport),
            seenIntro = prefs.getBoolean("seenIntro", d.seenIntro),
            lastSourceRepository = prefs.getString("lastSourceRepository", "") ?: "",
            lastSourceCollection = prefs.getString("lastSourceCollection", "") ?: "",
            lastSourceLocation = prefs.getString("lastSourceLocation", "") ?: "",
            nanoLimitFixApplied = prefs.getBoolean("nanoLimitFixApplied", false),
        )
    }

    private fun write(s: SettingsState) {
        prefs.edit()
            .putString("engine", s.engine)
            .putString("claudeKey", s.claudeKey)
            .putString("claudeModel", s.claudeModel)
            .putBoolean("claudeForHandwritten", s.claudeForHandwritten)
            .putBoolean("claudeFallback", s.claudeFallback)
            .putString("processorName", s.processorName)
            .putString("defaultRepository", s.defaultRepository)
            .putBoolean("onePerPageDefault", s.onePerPageDefault)
            .putBoolean("keepScreenOn", s.keepScreenOn)
            .putString("theme", s.theme)
            .putBoolean("dynamicColor", s.dynamicColor)
            .putBoolean("includeDraftsInExport", s.includeDraftsInExport)
            .putBoolean("includeItemsInExport", s.includeItemsInExport)
            .putBoolean("seenIntro", s.seenIntro)
            .putString("lastSourceRepository", s.lastSourceRepository)
            .putString("lastSourceCollection", s.lastSourceCollection)
            .putString("lastSourceLocation", s.lastSourceLocation)
            .putBoolean("nanoLimitFixApplied", s.nanoLimitFixApplied)
            .apply()
    }
}
