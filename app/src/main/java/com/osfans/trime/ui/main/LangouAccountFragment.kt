/*
 * SPDX-FileCopyrightText: 2026 Langou Input Method contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ui.main

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.SwitchPreferenceCompat
import com.osfans.trime.BuildConfig
import com.osfans.trime.R
import com.osfans.trime.data.theme.ColorManager
import com.osfans.trime.data.theme.ThemeManager
import com.osfans.trime.langou.LangouClientFactory
import com.osfans.trime.langou.LangouPreferences
import com.osfans.trime.langou.account.PhoneNormalizer
import com.osfans.trime.langou.memory.ConversationMemoryController
import com.osfans.trime.langou.memory.LangouMemoryFactory
import com.osfans.trime.langou.network.ClientSettings
import com.osfans.trime.langou.update.ReleaseDecision
import com.osfans.trime.langou.update.ReleaseSignatureVerifier
import com.osfans.trime.langou.update.ReleaseUpdatePolicy
import com.osfans.trime.ui.common.PaddingPreferenceFragment
import com.osfans.trime.util.addCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LangouAccountFragment : PaddingPreferenceFragment() {
    private val api by lazy(LangouClientFactory::api)
    private val sessionManager by lazy {
        LangouClientFactory.sessionManager(requireContext().applicationContext, api)
    }
    private val settings by lazy {
        requireContext().getSharedPreferences(
            LangouPreferences.SETTINGS_FILE,
            Context.MODE_PRIVATE,
        )
    }
    private val localMemoryController by lazy {
        ConversationMemoryController(
            LangouMemoryFactory.store(requireContext().applicationContext),
        )
    }

    private lateinit var status: Preference
    private lateinit var phone: EditTextPreference
    private lateinit var code: EditTextPreference
    private lateinit var sendCode: Preference
    private lateinit var signIn: Preference
    private lateinit var theme: ListPreference
    private lateinit var autoSuggest: SwitchPreferenceCompat
    private lateinit var saveHistory: SwitchPreferenceCompat
    private lateinit var diagnostics: SwitchPreferenceCompat

    override fun onCreatePreferences(
        savedInstanceState: Bundle?,
        rootKey: String?,
    ) {
        val ctx = requireContext()
        preferenceScreen =
            preferenceManager.createPreferenceScreen(ctx).apply {
                addCategory(getString(R.string.langou_account_category)) {
                    isIconSpaceReserved = false
                    status = accountStatus(ctx)
                    phone = phonePreference(ctx)
                    code = codePreference(ctx)
                    sendCode = actionPreference(ctx, R.string.langou_send_code) { requestCode() }
                    signIn = actionPreference(ctx, R.string.langou_sign_in) { verifyAndSignIn() }
                    addPreference(status)
                    addPreference(phone)
                    addPreference(code)
                    addPreference(sendCode)
                    addPreference(signIn)
                }
                addCategory(getString(R.string.langou_preferences_category)) {
                    isIconSpaceReserved = false
                    theme = themePreference(ctx)
                    autoSuggest =
                        switchPreference(
                            ctx,
                            R.string.langou_auto_suggest,
                            R.string.langou_auto_suggest_summary,
                            LangouPreferences.AI_AUTO_SUGGEST,
                            true,
                        )
                    saveHistory =
                        switchPreference(
                            ctx,
                            R.string.langou_save_history,
                            R.string.langou_save_history_summary,
                            LangouPreferences.SAVE_HISTORY,
                            true,
                        )
                    diagnostics =
                        switchPreference(
                            ctx,
                            R.string.langou_diagnostics,
                            R.string.langou_diagnostics_summary,
                            LangouPreferences.DIAGNOSTICS,
                            false,
                        )
                    addPreference(theme)
                    addPreference(autoSuggest)
                    addPreference(saveHistory)
                    addPreference(diagnostics)
                }
                addCategory(getString(R.string.langou_privacy_category)) {
                    isIconSpaceReserved = false
                    addPreference(
                        actionPreference(
                            ctx,
                            R.string.langou_clear_history,
                            R.string.langou_clear_history_summary,
                        ) {
                            confirmClearHistory()
                        },
                    )
                    addPreference(
                        actionPreference(
                            ctx,
                            R.string.langou_check_update,
                            getString(R.string.langou_current_version, BuildConfig.VERSION_NAME),
                        ) {
                            checkForUpdate()
                        },
                    )
                }
            }
    }

    override fun onStart() {
        super.onStart()
        updateAccountStatus()
        lifecycleScope.launch {
            runCatching {
                val current = sessionManager.validSession()
                applyRemoteSettings(api.getSettings(current.tokens.accessToken))
            }
            updateAccountStatus()
        }
    }

    private fun accountStatus(context: Context) =
        Preference(context).apply {
            isSelectable = false
            isIconSpaceReserved = false
            setTitle(R.string.langou_account_status)
        }

    private fun phonePreference(context: Context) =
        EditTextPreference(context).apply {
            isPersistent = false
            isIconSpaceReserved = false
            setTitle(R.string.langou_phone)
            setSummary(R.string.langou_phone_summary)
            setOnBindEditTextListener {
                it.inputType = InputType.TYPE_CLASS_PHONE
                it.hint = "13800138000"
            }
        }

    private fun codePreference(context: Context) =
        EditTextPreference(context).apply {
            isPersistent = false
            isIconSpaceReserved = false
            setTitle(R.string.langou_sms_code)
            setSummary(R.string.langou_sms_code_summary)
            setOnBindEditTextListener {
                it.inputType = InputType.TYPE_CLASS_NUMBER
                it.hint = "123456"
            }
        }

    private fun actionPreference(
        context: Context,
        title: Int,
        summary: Int? = null,
        onClick: () -> Unit,
    ) = Preference(context).apply {
        isIconSpaceReserved = false
        setTitle(title)
        summary?.let(::setSummary)
        setOnPreferenceClickListener {
            onClick()
            true
        }
    }

    private fun actionPreference(
        context: Context,
        title: Int,
        summary: String,
        onClick: () -> Unit,
    ) = Preference(context).apply {
        isIconSpaceReserved = false
        setTitle(title)
        this.summary = summary
        setOnPreferenceClickListener {
            onClick()
            true
        }
    }

    private fun themePreference(context: Context) =
        ListPreference(context).apply {
            isPersistent = false
            isIconSpaceReserved = false
            setTitle(R.string.langou_skin)
            entries =
                arrayOf(
                    getString(R.string.langou_skin_cream),
                    getString(R.string.langou_skin_soda),
                    getString(R.string.langou_skin_moon),
                )
            entryValues = arrayOf("cream", "soda", "moon")
            value = settings.getString(LangouPreferences.THEME, "cream")
            summaryProvider = ListPreference.SimpleSummaryProvider.getInstance()
            setOnPreferenceChangeListener { _, newValue ->
                val id = newValue as String
                settings.edit().putString(LangouPreferences.THEME, id).apply()
                applyKeyboardTheme(id)
                syncSettings()
                true
            }
        }

    private fun switchPreference(
        context: Context,
        title: Int,
        summary: Int,
        key: String,
        defaultValue: Boolean,
    ) = SwitchPreferenceCompat(context).apply {
        isPersistent = false
        isIconSpaceReserved = false
        setTitle(title)
        setSummary(summary)
        isChecked = settings.getBoolean(key, defaultValue)
        setOnPreferenceChangeListener { _, newValue ->
            settings.edit().putBoolean(key, newValue as Boolean).apply()
            syncSettings()
            true
        }
    }

    private fun requestCode() {
        val normalized = PhoneNormalizer.toE164(phone.text.orEmpty())
        if (normalized == null) {
            toast(R.string.langou_invalid_phone)
            return
        }
        launchAction(sendCode) {
            val response = api.sendSms(normalized)
            sendCode.summary = getString(R.string.langou_code_sent, response.retryAfter)
            toast(R.string.langou_code_sent_short)
        }
    }

    private fun verifyAndSignIn() {
        val normalized = PhoneNormalizer.toE164(phone.text.orEmpty())
        val enteredCode = code.text.orEmpty()
        if (normalized == null) {
            toast(R.string.langou_invalid_phone)
            return
        }
        if (!PhoneNormalizer.validCode(enteredCode)) {
            toast(R.string.langou_invalid_code)
            return
        }
        launchAction(signIn) {
            val previous = sessionManager.validSession()
            val userTokens = api.verifySms(normalized, enteredCode, previous.deviceId)
            var merged = true
            if (previous.tokens.subjectType == "guest") {
                merged =
                    runCatching {
                        api.mergeGuest(userTokens.accessToken, previous.tokens.refreshToken)
                    }.isSuccess
            }
            sessionManager.replaceSession(userTokens)
            updateAccountStatus()
            toast(if (merged) R.string.langou_sign_in_success else R.string.langou_merge_deferred)
            syncSettings()
        }
    }

    private fun syncSettings() {
        lifecycleScope.launch {
            runCatching {
                val current = sessionManager.validSession()
                api.putSettings(current.tokens.accessToken, localSettings())
            }
        }
    }

    private fun applyRemoteSettings(remote: ClientSettings) {
        settings
            .edit()
            .putString(LangouPreferences.THEME, remote.theme)
            .putBoolean(LangouPreferences.AI_AUTO_SUGGEST, remote.autoSuggest)
            .putBoolean(LangouPreferences.SAVE_HISTORY, remote.saveHistory)
            .putBoolean(LangouPreferences.DIAGNOSTICS, remote.diagnostics)
            .apply()
        theme.value = remote.theme
        autoSuggest.isChecked = remote.autoSuggest
        saveHistory.isChecked = remote.saveHistory
        diagnostics.isChecked = remote.diagnostics
        applyKeyboardTheme(remote.theme)
    }

    private fun localSettings() =
        ClientSettings(
            theme = settings.getString(LangouPreferences.THEME, "cream") ?: "cream",
            autoSuggest = settings.getBoolean(LangouPreferences.AI_AUTO_SUGGEST, true),
            saveHistory = settings.getBoolean(LangouPreferences.SAVE_HISTORY, true),
            diagnostics = settings.getBoolean(LangouPreferences.DIAGNOSTICS, false),
        )

    private fun applyKeyboardTheme(id: String) {
        val schemeId = if (id == "cream") "default" else id
        ThemeManager.activeTheme.colorSchemes
            .firstOrNull { it.id == schemeId }
            ?.let(ColorManager::setColorScheme)
    }

    private fun confirmClearHistory() {
        AlertDialog
            .Builder(requireContext())
            .setTitle(R.string.langou_clear_history)
            .setMessage(R.string.langou_clear_history_confirm)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.delete) { _, _ ->
                lifecycleScope.launch {
                    val localResult =
                        runCatching {
                            withContext(Dispatchers.IO) {
                                localMemoryController.deleteAll()
                            }
                        }
                    val cloudResult =
                        runCatching {
                            val current = sessionManager.validSession()
                            api.deleteAllHistory(current.tokens.accessToken)
                        }
                    toast(
                        when {
                            localResult.isFailure -> R.string.langou_network_error
                            cloudResult.isSuccess -> R.string.langou_history_cleared
                            else -> R.string.langou_local_history_cleared
                        },
                    )
                }
            }.show()
    }

    private fun checkForUpdate() {
        lifecycleScope.launch {
            val release = runCatching { api.latestRelease("android") }.getOrNull()
            when {
                release == null -> toast(R.string.langou_network_error)
                !ReleaseSignatureVerifier.verify(
                    release,
                    BuildConfig.LANGOU_RELEASE_PUBLIC_KEY,
                ) -> toast(R.string.langou_update_signature_invalid)
                ReleaseUpdatePolicy.evaluate(
                    BuildConfig.VERSION_NAME,
                    release,
                ) == ReleaseDecision.Current -> toast(R.string.langou_up_to_date)
                ReleaseUpdatePolicy.evaluate(
                    BuildConfig.VERSION_NAME,
                    release,
                ) == ReleaseDecision.Rejected -> toast(R.string.langou_update_signature_invalid)
                else -> {
                    val mandatory =
                        ReleaseUpdatePolicy.evaluate(
                            BuildConfig.VERSION_NAME,
                            release,
                        ) == ReleaseDecision.Mandatory
                    AlertDialog
                        .Builder(requireContext())
                        .setTitle(getString(R.string.langou_update_available, release.version))
                        .setMessage(
                            getString(
                                if (mandatory) {
                                    R.string.langou_update_mandatory
                                } else {
                                    R.string.langou_update_verified
                                },
                                release.size / BYTES_PER_MEGABYTE,
                            ),
                        ).setNegativeButton(android.R.string.cancel, null)
                        .setPositiveButton(R.string.langou_download_update) { _, _ ->
                            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(release.url)))
                        }
                        .show()
                }
            }
        }
    }

    private fun updateAccountStatus() {
        val stored = runCatching(sessionManager::storedSession).getOrNull()
        status.summary =
            when (stored?.tokens?.subjectType) {
                "user" -> getString(R.string.langou_account_user)
                "guest" -> getString(R.string.langou_account_guest)
                else -> getString(R.string.langou_account_initializing)
            }
    }

    private fun launchAction(
        preference: Preference,
        action: suspend () -> Unit,
    ) {
        preference.isEnabled = false
        lifecycleScope.launch {
            try {
                action()
            } catch (_: Exception) {
                toast(R.string.langou_network_error)
            } finally {
                preference.isEnabled = true
            }
        }
    }

    private fun toast(message: Int) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

    private companion object {
        const val BYTES_PER_MEGABYTE = 1024L * 1024L
    }
}
