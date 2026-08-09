/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme

import android.content.res.Configuration
import com.osfans.trime.core.Rime
import com.osfans.trime.data.base.DataManager
import com.osfans.trime.data.prefs.AppPrefs
import com.osfans.trime.ime.symbol.LiquidData
import com.osfans.trime.util.WeakHashSet
import com.osfans.trime.util.appContext
import com.osfans.trime.util.yaml.Yaml
import com.osfans.trime.util.yaml.mapping
import timber.log.Timber
import java.io.File

object ThemeManager {
    fun interface OnThemeChangeListener {
        fun onThemeChange(theme: Theme)
    }

    fun getAllThemes(): List<ThemeItem> {
        val sharedThemes = ThemeFilesManager.listThemes(DataManager.sharedDataDir)
        val userThemes = ThemeFilesManager.listThemes(DataManager.userDataDir)
        return sharedThemes + userThemes
    }

    private lateinit var _activeTheme: Theme

    var activeTheme: Theme
        get() = _activeTheme
        private set(value) {
            if (::_activeTheme.isInitialized && _activeTheme == value) return
            _activeTheme = value
            fireChange()
        }

    private val onChangeListeners = WeakHashSet<OnThemeChangeListener>()

    fun addOnChangedListener(listener: OnThemeChangeListener) {
        onChangeListeners.add(listener)
    }

    fun removeOnChangedListener(listener: OnThemeChangeListener) {
        onChangeListeners.remove(listener)
    }

    private fun fireChange() {
        onChangeListeners.forEach { it.onThemeChange(_activeTheme) }
    }

    val prefs = AppPrefs.defaultInstance().registerProvider(::ThemePrefs)

    private data class ResolvedTheme(
        val configId: String,
        val theme: Theme,
    )

    private fun decodeThemeOrNull(
        raw: String,
        source: String,
    ): Theme? =
        try {
            val node = Yaml.parseToYamlNode(raw)
            val mapping = node.mapping
            if (mapping == null) {
                Timber.w("Failed to load theme from '$source': YAML root is not a mapping")
                null
            } else {
                Theme.decode(mapping)
            }
        } catch (e: Exception) {
            Timber.w(e, "Failed to load theme from '$source'")
            null
        }

    private fun loadThemeByIdOrNull(id: String): Theme? {
        if (!Rime.deployRimeConfigFile(id, "config_version")) {
            Timber.w("Failed to deploy theme config file '$id.yaml'")
        }

        val candidateFiles =
            listOf(
                File(DataManager.resolveDeployedResourcePath(id)),
                DataManager.userDataDir.resolve("$id.yaml"),
                DataManager.sharedDataDir.resolve("$id.yaml"),
            ).distinctBy(File::getAbsolutePath)
        candidateFiles.forEach { file ->
            if (file.isFile) {
                val raw =
                    runCatching { file.readText() }
                        .onFailure { error ->
                            Timber.w(error, "Failed to read theme '${file.absolutePath}'")
                        }.getOrNull()
                if (raw != null) {
                    decodeThemeOrNull(raw, file.absolutePath)?.let { return it }
                }
            }
        }

        if (id == DEFAULT_THEME_ID) {
            val bundledSource = "shared/$DEFAULT_THEME_ID.yaml"
            val bundled =
                runCatching {
                    appContext.assets.open(bundledSource).bufferedReader().use { it.readText() }
                }.onFailure { error ->
                    Timber.w(error, "Failed to read bundled theme '$bundledSource'")
                }.getOrNull()
            if (bundled != null) {
                decodeThemeOrNull(bundled, "asset://$bundledSource")?.let { return it }
            }
        }

        Timber.w("Theme file not found for '$id'")
        return null
    }

    private fun getThemeById(id: String): ResolvedTheme {
        loadThemeByIdOrNull(id)?.let { return ResolvedTheme(id, it) }

        if (id != DEFAULT_THEME_ID) {
            loadThemeByIdOrNull(DEFAULT_THEME_ID)?.let {
                Timber.w("Theme '$id' is unavailable, fallback to default theme '$DEFAULT_THEME_ID'")
                return ResolvedTheme(DEFAULT_THEME_ID, it)
            }
        }

        for (fallbackId in getAllThemes().map { it.configId }.distinct()) {
            loadThemeByIdOrNull(fallbackId)?.let {
                Timber.w("Theme '$id' is unavailable, fallback to available theme '$fallbackId'")
                return ResolvedTheme(fallbackId, it)
            }
        }

        error("No valid theme available")
    }

    private fun evaluateActiveTheme(): Theme {
        val selectedThemeId = prefs.selectedTheme.getValue()
        val resolvedTheme = getThemeById(selectedThemeId)
        val newTheme = resolvedTheme.theme
        if (resolvedTheme.configId != selectedThemeId) {
            prefs.selectedTheme.setValue(resolvedTheme.configId)
        }
        KeyActionManager.resetCache()
        FontManager.resetCache(newTheme)
        ColorManager.switchTheme(newTheme)
        LiquidData.init(newTheme)
        return newTheme
    }

    fun init(configuration: Configuration) {
        _activeTheme = evaluateActiveTheme()
        ColorManager.init(configuration)
    }

    fun selectTheme(configId: String) {
        val resolvedTheme = getThemeById(configId)
        val theme = resolvedTheme.theme
        KeyActionManager.resetCache()
        FontManager.resetCache(theme)
        ColorManager.switchTheme(theme)
        LiquidData.init(theme)
        activeTheme = theme
        prefs.selectedTheme.setValue(resolvedTheme.configId)
    }

    private const val DEFAULT_THEME_ID = "trime"
}
