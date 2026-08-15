/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.core

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.RectF
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.text.InputType
import android.view.InputDevice
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.view.inputmethod.CursorAnchorInfo
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InlineSuggestionsRequest
import android.view.inputmethod.InlineSuggestionsResponse
import android.widget.FrameLayout
import android.widget.Toast
import androidx.annotation.Keep
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.lifecycleScope
import com.osfans.trime.R
import com.osfans.trime.core.KeyModifiers
import com.osfans.trime.core.KeyValue
import com.osfans.trime.core.RimeApi
import com.osfans.trime.core.RimeKeyMapping
import com.osfans.trime.core.RimeMessage
import com.osfans.trime.daemon.RimeDaemon
import com.osfans.trime.daemon.RimeSession
import com.osfans.trime.data.prefs.AppPrefs
import com.osfans.trime.data.prefs.PreferenceDelegate
import com.osfans.trime.data.prefs.PreferenceDelegateProvider
import com.osfans.trime.data.theme.ColorManager
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.data.theme.ThemeManager
import com.osfans.trime.ime.composition.CandidatesView
import com.osfans.trime.ime.keyboard.InputFeedbackManager
import com.osfans.trime.langou.LangouClientFactory
import com.osfans.trime.langou.LangouPreferences
import com.osfans.trime.langou.ai.AutoSuggestionGate
import com.osfans.trime.langou.ai.ChatApplicationMapper
import com.osfans.trime.langou.ai.SuggestionDecision
import com.osfans.trime.langou.ai.SuggestionTrigger
import com.osfans.trime.langou.context.ContextAccessSettings
import com.osfans.trime.langou.context.ContextCaptureState
import com.osfans.trime.langou.context.ContextPermissionStatus
import com.osfans.trime.langou.context.ContextSnapshotStore
import com.osfans.trime.langou.context.stableSignature
import com.osfans.trime.langou.memory.ConversationIdentity
import com.osfans.trime.langou.memory.ConversationMemory
import com.osfans.trime.langou.memory.ConversationMemoryController
import com.osfans.trime.langou.memory.LangouMemoryFactory
import com.osfans.trime.langou.memory.MemoryRetriever
import com.osfans.trime.langou.memory.StoredTurn
import com.osfans.trime.langou.network.ConversationTurn
import com.osfans.trime.langou.network.SuggestionRequest
import com.osfans.trime.langou.privacy.ContextSignals
import com.osfans.trime.langou.privacy.SensitiveContextPolicy
import com.osfans.trime.langou.theme.LangouPinyinLayout
import com.osfans.trime.receiver.RimeIntentReceiver
import com.osfans.trime.util.any
import com.osfans.trime.util.findSectionFrom
import com.osfans.trime.util.forceShowSelf
import com.osfans.trime.util.monitorCursorAnchor
import com.osfans.trime.util.styledFloat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import splitties.bitflags.hasFlag
import splitties.systemservices.clipboardManager
import splitties.systemservices.inputMethodManager
import timber.log.Timber
import java.util.Locale
import java.util.UUID

/** [輸入法][InputMethodService]主程序  */

open class TrimeInputMethodService : LifecycleInputMethodService() {
    private lateinit var rime: RimeSession
    private val jobs = Channel<Job>(capacity = Channel.UNLIMITED)

    private val prefs = AppPrefs.defaultInstance()
    private lateinit var decorView: View
    private lateinit var contentView: FrameLayout
    private lateinit var lastKnownConfig: Configuration
    private var inputView: InputView? = null
    private var candidatesView: CandidatesView? = null
    private val navBarManager = NavigationBarManager()
    private val inputDeviceManager =
        InputDeviceManager onChange@{
            val w = window.window ?: return@onChange
            navBarManager.evaluate(w, useVirtualKeyboard = it)
        }
    private val rimeIntentReceiver = RimeIntentReceiver()

    private var lastCommittedText: String = ""

    private var composingText: String = ""

    private var cursorUpdateIndex = 0

    private val langouApi by lazy(LangouClientFactory::api)
    private val langouSessionManager by lazy {
        LangouClientFactory.sessionManager(applicationContext, langouApi)
    }
    private val langouSettings by lazy {
        getSharedPreferences(LangouPreferences.SETTINGS_FILE, MODE_PRIVATE)
    }
    private val langouSuggestionGate = AutoSuggestionGate()
    private var langouSuggestionJob: Job? = null
    private val langouMemoryStore by lazy { LangouMemoryFactory.store(applicationContext) }
    private val langouMemoryController by lazy {
        ConversationMemoryController(langouMemoryStore)
    }
    private val langouIdentityResolver by lazy(LangouMemoryFactory::identityResolver)
    private val langouMemoryRetriever = MemoryRetriever()
    private val langouEphemeralMemories = mutableMapOf<String, ConversationMemory>()

    private val recreateInputViewPrefs: Array<PreferenceDelegate<*>> =
        arrayOf(prefs.keyboard.hideInputBar)

    @Keep
    private val recreateInputViewListener =
        PreferenceDelegate.OnChangeListener<Any> { _, _ ->
            replaceInputView(ThemeManager.activeTheme)
        }

    @Keep
    private val recreateCandidatesViewListener =
        PreferenceDelegateProvider.OnChangeListener {
            replaceCandidateView(ThemeManager.activeTheme)
        }

    val debugActiveKeyboardId: String
        get() = inputView?.activeKeyboardId().orEmpty()

    val debugSelectedSchemaId: String
        get() = runCatching { rime.run { statusCached.schemaId } }.getOrDefault("")

    val debugAiSuggestionTexts: List<String>
        get() = debugAiSuggestions.toList()

    @Keep
    private val onThemeChangeListener =
        ThemeManager.OnThemeChangeListener {
            replaceInputViews(it)
        }

    @Keep
    private val onColorChangeListener =
        ColorManager.OnColorChangeListener {
            ContextCompat.getMainExecutor(this).execute {
                replaceInputViews(it)
            }
        }

    private fun postJob(
        scope: CoroutineScope,
        block: suspend () -> Unit,
    ): Job {
        val job = scope.launch(start = CoroutineStart.LAZY) { block() }
        jobs.trySend(job)
        return job
    }

    /**
     * Post a rime operation to [jobs] to be executed
     *
     * Unlike `rime.runOnReady` or `rime.launchOnReady` where
     * subsequent operations can start if the prior operation is not finished (suspended),
     * [postRimeJob] ensures that operations are executed sequentially.
     */
    fun postRimeJob(block: suspend RimeApi.() -> Unit) = postJob(rime.lifecycleScope) { rime.runOnReady(block) }

    private suspend fun updateRimeOption(api: RimeApi) {
        try {
            api.setRuntimeOption("soft_cursor", prefs.keyboard.useSoftCursor.getValue()) // 軟光標
        } catch (e: Exception) {
            Timber.e(e)
        }
    }

    private fun registerReceiver() {
        val intentFilter =
            IntentFilter().apply {
                addAction(RimeIntentReceiver.ACTION_DEPLOY)
                addAction(RimeIntentReceiver.ACTION_SYNC_USER_DATA)
            }
        ContextCompat.registerReceiver(
            this,
            rimeIntentReceiver,
            intentFilter,
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    override fun onCreate() {
        activeInstance = this
        rime = RimeDaemon.createSession(javaClass.name)
        lifecycleScope.launch {
            jobs.consumeEach { it.join() }
        }
        lifecycleScope.launch {
            rime.run { messageFlow }.collect {
                handleRimeMessage(it)
            }
        }
        lifecycleScope.launch {
            ContextSnapshotStore.snapshots
                .distinctUntilChangedBy { snapshot ->
                    snapshot?.stableSignature()
                }.collect { snapshot ->
                    if (snapshot == null) {
                        langouSuggestionJob?.cancel()
                        inputView?.dismissAiSuggestions()
                        return@collect
                    }
                    val editorInfo = currentInputEditorInfo ?: return@collect
                    if (
                        isInputViewShown &&
                        snapshot.packageName == editorInfo.packageName &&
                        ContextSnapshotStore.get(editorInfo.packageName) == snapshot
                    ) {
                        scheduleLangouSuggestions(editorInfo)
                    }
                }
        }
        recreateInputViewPrefs.forEach {
            it.registerOnChangeListener(recreateInputViewListener)
        }
        prefs.candidates.registerOnChangeListener(recreateCandidatesViewListener)
        ThemeManager.init(resources.configuration)
        ThemeManager.addOnChangedListener(onThemeChangeListener)
        ColorManager.addOnChangedListener(onColorChangeListener)
        InputFeedbackManager.init(this)
        registerReceiver()
        super.onCreate()
        Timber.d("onCreate")
        decorView = window.window!!.decorView
        contentView = decorView.findViewById(android.R.id.content)
        lastKnownConfig = Configuration(resources.configuration)
    }

    private fun handleRimeMessage(it: RimeMessage<*>) {
        when (it) {
            is RimeMessage.CommitTextMessage -> {
                if (!it.data.text.isNullOrEmpty()) {
                    commitText(it.data.text)
                }
            }
            is RimeMessage.InlinePreeditMessage -> {
                updateComposingText(it.data)
            }
            is RimeMessage.KeyMessage ->
                it.data.let msg@{
                    if (it.isVirtual) {
                        when (it.value.value) {
                            RimeKeyMapping.RimeKey_Return -> handleReturnKey()
                            else -> {
                                val keyCode = it.value.keyCode
                                if (keyCode != KeyEvent.KEYCODE_UNKNOWN) {
                                    // recognized keyCode
                                    sendDownUpKeyEvent(
                                        keyCode,
                                        it.modifiers.metaState or meta(
                                            alt = it.modifiers.alt,
                                            shift = it.modifiers.shift,
                                            ctrl = it.modifiers.ctrl,
                                            meta = it.modifiers.meta,
                                        ),
                                    )
                                    if (it.modifiers.ctrl && keyCode == KeyEvent.KEYCODE_C) clearTextSelection()
                                } else {
                                    if (it.value.value > 0) {
                                        runCatching {
                                            commitText(Character.toString(it.value.value))
                                        }.getOrElse { t -> Timber.w(t, "Unhandled Virtual KeyEvent: $it") }
                                    } else {
                                        Timber.w("Unhandled Virtual KeyEvent: $it")
                                    }
                                }
                            }
                        }
                    } else {
                        val keyCode = it.value.keyCode
                        if (keyCode != KeyEvent.KEYCODE_UNKNOWN) {
                            // recognized keyCode
                            val eventTime = SystemClock.uptimeMillis()
                            if (it.modifiers.release) {
                                sendUpKeyEvent(eventTime, keyCode, it.modifiers.metaState)
                            } else {
                                sendDownKeyEvent(eventTime, keyCode, it.modifiers.metaState)
                            }
                        } else {
                            if (!it.modifiers.release && it.value.value > 0) {
                                runCatching {
                                    commitText(Character.toString(it.value.value))
                                }.getOrElse { t -> Timber.w(t, "Unhandled Rime KeyEvent: $it") }
                            } else {
                                Timber.w("Unhandled Rime KeyEvent: $it")
                            }
                        }
                    }
                }
            is RimeMessage.DeployMessage -> {
                if (it.data == RimeMessage.DeployMessage.State.Success) {
                    ThemeManager.reloadSelectedThemeAfterDeployment()
                }
            }
            else -> {}
        }
    }

    private fun replaceInputView(theme: Theme): InputView {
        val newInputView = InputView(this, rime, theme)
        setInputView(newInputView)
        inputDeviceManager.setInputView(newInputView)
        navBarManager.setupInputView(newInputView)
        inputView = newInputView
        return newInputView
    }

    private fun replaceCandidateView(theme: Theme): CandidatesView {
        val newCandidatesView = CandidatesView(this, rime, theme)
        contentView.removeView(candidatesView)
        contentView.addView(newCandidatesView)
        inputDeviceManager.setCandidatesView(newCandidatesView)
        navBarManager.setupInputView(newCandidatesView)
        candidatesView = newCandidatesView
        return newCandidatesView
    }

    private fun replaceInputViews(theme: Theme) {
        navBarManager.evaluate(window.window!!)
        replaceInputView(theme)
        replaceCandidateView(theme)
        inputView?.updateEnterKeyLabel(currentInputEditorInfo)
    }

    override fun onDestroy() {
        InputFeedbackManager.destroy()
        inputView = null
        recreateInputViewPrefs.forEach {
            it.unregisterOnChangeListener(recreateInputViewListener)
        }
        prefs.candidates.unregisterOnChangeListener(recreateCandidatesViewListener)
        ThemeManager.removeOnChangedListener(onThemeChangeListener)
        ColorManager.removeOnChangedListener(onColorChangeListener)
        if (activeInstance === this) {
            activeInstance = null
        }
        super.onDestroy()
        unregisterReceiver(rimeIntentReceiver)
        RimeDaemon.destroySession(javaClass.name)
    }

    private fun handleReturnKey() {
        currentInputEditorInfo.run {
            if (inputType and InputType.TYPE_MASK_CLASS == InputType.TYPE_NULL ||
                imeOptions.hasFlag(EditorInfo.IME_FLAG_NO_ENTER_ACTION)
            ) {
                sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
                return
            }
            if (!actionLabel.isNullOrEmpty() && actionId != EditorInfo.IME_ACTION_UNSPECIFIED) {
                currentInputConnection.performEditorAction(actionId)
                return
            }
            when (val action = imeOptions and EditorInfo.IME_MASK_ACTION) {
                EditorInfo.IME_ACTION_UNSPECIFIED,
                EditorInfo.IME_ACTION_NONE,
                -> sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)

                else -> currentInputConnection.performEditorAction(action)
            }
        }
    }

    /**
     * https://github.com/fcitx5-android/fcitx5-android/blob/fe3a618c8fd18842305d2f8ec2880fcc67ec1679/app/src/main/java/org/fcitx/fcitx5/android/input/FcitxInputMethodService.kt#L523-#L547
     */
    override fun onConfigurationChanged(newConfig: Configuration) {
        postRimeJob { clearComposition() }
        val keyboardUiModeMask = ActivityInfo.CONFIG_KEYBOARD or
            ActivityInfo.CONFIG_KEYBOARD_HIDDEN or
            ActivityInfo.CONFIG_UI_MODE
        val diff = lastKnownConfig.diff(newConfig)
        Timber.d("onConfigurationChanged diff=$diff")
        if (diff and keyboardUiModeMask != diff) {
            super.onConfigurationChanged(newConfig)
        }
        lastKnownConfig.setTo(newConfig)
    }

    override fun onWindowShown() {
        super.onWindowShown()
        // navbar foreground/background color would reset every time window shows
        navBarManager.update(window.window!!)
    }

    private val contentSize = floatArrayOf(0f, 0f)
    private val decorLocation = floatArrayOf(0f, 0f)
    private val decorLocationInt = intArrayOf(0, 0)
    private var decorLocationUpdated = false

    private fun updateDecorLocation() {
        contentSize[0] = contentView.width.toFloat()
        contentSize[1] =
            if (inputDeviceManager.isVirtualKeyboard) {
                inputViewLocation[1].toFloat()
            } else {
                contentView.height.toFloat()
            }
        decorView.getLocationOnScreen(decorLocationInt)
        decorLocation[0] = decorLocationInt[0].toFloat()
        decorLocation[1] = decorLocationInt[1].toFloat()
        // contentSize and decorLocation can be completely wrong,
        // when measuring right after the very first onStartInputView() of an IMS' lifecycle
        if (contentSize[0] > 0 && contentSize[1] > 0) {
            decorLocationUpdated = true
        }
    }

    private val anchorPosition = RectF()

    private fun workaroundNullCursorAnchorInfo() {
        anchorPosition.set(0f, contentSize[1], 0f, contentSize[1])
        candidatesView?.updateCursorAnchor(anchorPosition, contentSize)
    }

    override fun onUpdateCursorAnchorInfo(info: CursorAnchorInfo) {
        val bounds = info.getCharacterBounds(0)
        // update anchorPosition
        if (bounds == null) {
            // composing is disabled in target app or trime settings
            // use the position of the insertion marker instead
            anchorPosition.top = info.insertionMarkerTop
            anchorPosition.left = info.insertionMarkerHorizontal
            anchorPosition.bottom = info.insertionMarkerBottom
            anchorPosition.right = info.insertionMarkerHorizontal
        } else {
            // for different writing system (e.g. right to left languages),
            // we have to calculate the correct RectF
            val horizontal = if (candidatesView?.layoutDirection == View.LAYOUT_DIRECTION_RTL) bounds.right else bounds.left
            anchorPosition.top = bounds.top
            anchorPosition.left = horizontal
            anchorPosition.bottom = bounds.bottom
            anchorPosition.right = horizontal
        }
        if (!decorLocationUpdated) {
            updateDecorLocation()
        }
        if (anchorPosition.any(Float::isNaN)) {
            workaroundNullCursorAnchorInfo()
            return
        }
        info.matrix.mapRect(anchorPosition)
        val (dX, dY) = decorLocation
        anchorPosition.offset(-dX, -dY)
        candidatesView?.updateCursorAnchor(anchorPosition, contentSize)
    }

    override fun onUpdateSelection(
        oldSelStart: Int,
        oldSelEnd: Int,
        newSelStart: Int,
        newSelEnd: Int,
        candidatesStart: Int,
        candidatesEnd: Int,
    ) {
        super.onUpdateSelection(
            oldSelStart,
            oldSelEnd,
            newSelStart,
            newSelEnd,
            candidatesStart,
            candidatesEnd,
        )
        cursorUpdateIndex += 1
        handleCursorUpdate(newSelStart, newSelEnd, candidatesStart, candidatesEnd, cursorUpdateIndex)
        inputView?.updateSelection(newSelStart, newSelEnd)
    }

    private fun handleCursorUpdate(
        newSelStart: Int,
        newSelEnd: Int,
        candidatesStart: Int,
        candidatesEnd: Int,
        updateIndex: Int,
    ) {
        if (newSelStart != newSelEnd) return
        if (candidatesStart == candidatesEnd) return
        if (newSelStart in candidatesStart..candidatesEnd) {
            val position = newSelStart - candidatesStart
            if (position != composingText.length) {
                postRimeJob {
                    if (updateIndex != cursorUpdateIndex) return@postRimeJob
                    Timber.d("handleCursorUpdate: move rime cursor to $position")
                    moveCursorPos(position)
                }
            }
        } else {
            Timber.d("handleCursorUpdate: clear composition")
            postRimeJob {
                clearComposition()
            }
        }
    }

    private val inputViewLocation = intArrayOf(0, 0)

    override fun onComputeInsets(outInsets: Insets) {
        if (inputDeviceManager.isVirtualKeyboard) {
            inputView?.keyboardView?.getLocationInWindow(inputViewLocation)
            outInsets.apply {
                contentTopInsets = inputViewLocation[1]
                visibleTopInsets = inputViewLocation[1]
                touchableInsets = Insets.TOUCHABLE_INSETS_VISIBLE
            }
        } else {
            val n = decorView.findViewById<View>(android.R.id.navigationBarBackground)?.height ?: 0
            val h = decorView.height - n
            outInsets.apply {
                contentTopInsets = h
                visibleTopInsets = h
                touchableInsets = Insets.TOUCHABLE_INSETS_VISIBLE
            }
        }
    }

    // always show InputView since we delegate CandidatesView's visibility to it
    @SuppressLint("MissingSuperCall")
    override fun onEvaluateInputViewShown() = true

    fun superEvaluateInputViewShown() = super.onEvaluateInputViewShown()

    override fun onCreateInputView(): View? {
        Timber.d("onCreateInputView")
        replaceInputViews(ThemeManager.activeTheme)
        // We will call `setInputView` by ourselves. This is fine.
        return null
    }

    override fun setInputView(view: View) {
        super.setInputView(view)
        val inputArea = contentView.findViewById<FrameLayout>(android.R.id.inputArea)
        inputArea.updateLayoutParams<ViewGroup.LayoutParams> {
            height = ViewGroup.LayoutParams.MATCH_PARENT
        }
        view.updateLayoutParams<ViewGroup.LayoutParams> {
            height = ViewGroup.LayoutParams.MATCH_PARENT
        }
    }

    override fun onConfigureWindow(
        win: Window,
        isFullscreen: Boolean,
        isCandidatesOnly: Boolean,
    ) {
        win.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    }

    override fun onStartInput(
        attribute: EditorInfo,
        restarting: Boolean,
    ) {
        composingText = ""
        Timber.d("onStartInput: restarting=$restarting")
        val isNullType = attribute.inputType and InputType.TYPE_MASK_CLASS == InputType.TYPE_NULL
        postRimeJob {
            if (restarting) {
                // when input restarts in the same editor, clear previous composition
                clearComposition()
            }
            setRuntimeOption("no_inline_preedit", isNullType)
        }
    }

    private val inlineSuggestions by prefs.general.inlineSuggestions

    @RequiresApi(Build.VERSION_CODES.R)
    override fun onCreateInlineSuggestionsRequest(uiExtras: Bundle): InlineSuggestionsRequest? {
        if (!inlineSuggestions || !inputDeviceManager.isVirtualKeyboard) return null
        return InlineSuggestions.createRequest(this)
    }

    @SuppressLint("NewApi")
    override fun onInlineSuggestionsResponse(response: InlineSuggestionsResponse): Boolean {
        if (!inputDeviceManager.isVirtualKeyboard) return false
        return inputView?.handleInlineSuggestions(response) == true
    }

    override fun onStartInputView(
        attribute: EditorInfo,
        restarting: Boolean,
    ) {
        Timber.d("onStartInputView: restarting=$restarting")
        InputFeedbackManager.startInput()
        postRimeJob {
            updateRimeOption(this)
        }
        val (useVirtualKeyboard, useCandidatesView) =
            inputDeviceManager.evaluateOnStartInputView(attribute, this)
        if (useVirtualKeyboard) {
            inputView?.startInput(attribute, restarting)
            ensureLangouSchemaSelected()
            updateLangouCaptureState(attribute)
            scheduleLangouSuggestions(attribute)
        } else {
            ContextCaptureState.deactivate()
        }
        if (useCandidatesView) {
            if (currentInputConnection?.monitorCursorAnchor() != true) {
                if (!decorLocationUpdated) {
                    updateDecorLocation()
                }
                workaroundNullCursorAnchorInfo()
            }
        }
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        Timber.d("onFinishInputView: finishingInput=$finishingInput")
        ContextCaptureState.deactivate()
        decorLocationUpdated = false
        inputDeviceManager.onFinishInputView()
        currentInputConnection?.apply {
            finishComposingText()
            monitorCursorAnchor(false)
        }
        composingText = ""
        langouSuggestionJob?.cancel()
        langouSuggestionJob = null
        langouSuggestionGate.reset()
        inputView?.dismissAiSuggestions()
        postRimeJob {
            clearComposition()
        }
        InputFeedbackManager.finishInput()
    }

    private fun updateLangouCaptureState(editorInfo: EditorInfo) {
        val packageName = editorInfo.packageName.orEmpty()
        val enabled = langouSettings.getBoolean(LangouPreferences.AI_AUTO_SUGGEST, true)
        val supported = ChatApplicationMapper.map(packageName) != null
        val safe =
            SensitiveContextPolicy.canCollect(
                ContextSignals(
                    inputType = editorInfo.inputType,
                    packageName = packageName,
                ),
            )
        if (
            enabled &&
            supported &&
            safe &&
            ContextPermissionStatus.isAccessibilityEnabled(this)
        ) {
            Timber.i("Langou capture active package=%s", packageName)
            ContextCaptureState.activate(packageName)
        } else {
            Timber.i(
                "Langou capture inactive package=%s enabled=%s supported=%s safe=%s accessibility=%s",
                packageName,
                enabled,
                supported,
                safe,
                ContextPermissionStatus.isAccessibilityEnabled(this),
            )
            ContextCaptureState.deactivate()
        }
    }

    fun syncLangouKeyboardToSchema(schemaId: String) {
        inputView?.syncKeyboardToSchema(schemaId)
    }

    private fun ensureLangouSchemaSelected() {
        postRimeJob {
            val currentSchema = selectedSchemaId()
            val managedSchema = LangouPinyinLayout.ensureManagedSchema(currentSchema)
            if (managedSchema == currentSchema) {
                syncLangouKeyboardToSchema(managedSchema)
                return@postRimeJob
            }
            Timber.i(
                "Langou schema fallback current=%s target=%s",
                currentSchema,
                managedSchema,
            )
            commitComposition()
            var selected = selectSchema(managedSchema)
            if (!selected) {
                deploy()
                selected = selectSchema(managedSchema)
            }
            if (selected) {
                syncLangouKeyboardToSchema(managedSchema)
            }
        }
    }

    private fun scheduleLangouSuggestions(
        info: EditorInfo? = currentInputEditorInfo,
        trigger: SuggestionTrigger = SuggestionTrigger.ContextChange,
    ) {
        langouSuggestionJob?.cancel()
        if (!langouSettings.getBoolean(LangouPreferences.AI_AUTO_SUGGEST, true)) {
            Timber.i("Langou suggestions skipped reason=disabled")
            ContextCaptureState.deactivate()
            inputView?.dismissAiSuggestions()
            return
        }
        val editorInfo = info ?: return
        val packageName = editorInfo.packageName.orEmpty()
        val application = ChatApplicationMapper.map(packageName)
        if (application == null) {
            Timber.i("Langou suggestions skipped reason=unsupported_app package=%s", packageName)
            ContextCaptureState.deactivate()
            inputView?.dismissAiSuggestions()
            return
        }
        val signals =
            ContextSignals(
                inputType = editorInfo.inputType,
                packageName = packageName,
        )
        if (!SensitiveContextPolicy.canCollect(signals)) {
            Timber.i("Langou suggestions skipped reason=sensitive package=%s", packageName)
            ContextCaptureState.deactivate()
            inputView?.dismissAiSuggestions()
            return
        }
        if (!ContextPermissionStatus.isAccessibilityEnabled(this)) {
            Timber.i("Langou suggestions waiting reason=permission_required package=%s", packageName)
            ContextCaptureState.deactivate()
            inputView?.showAiPermissionRequired {
                ContextAccessSettings.open(this)
            }
            return
        }
        if (ContextSnapshotStore.get(packageName) == null) {
            Timber.i("Langou suggestions waiting reason=context_loading package=%s", packageName)
            inputView?.showAiContextLoading(preserveExistingSuggestions = debugAiSuggestions.isNotEmpty())
        }

        langouSuggestionJob =
            lifecycleScope.launch {
                delay(LANGOU_SUGGESTION_DEBOUNCE_MS)
                val snapshot = ContextSnapshotStore.get(packageName) ?: return@launch
                val identity =
                    langouIdentityResolver.resolve(
                        application = application,
                        conversationHint = snapshot.conversationHint,
                        confidence = snapshot.identityConfidence,
                        contextSeed = stableConversationSeed(snapshot.turns),
                    )
                val capturedTurns =
                    snapshot.turns.map { turn ->
                        StoredTurn(
                            role = turn.role,
                            text = turn.text,
                            capturedAtEpochMillis = snapshot.capturedAtEpochMillis,
                        )
                    }
                val memory = loadConversationMemory(identity, capturedTurns)
                val retrieved =
                    langouMemoryRetriever.retrieve(
                        memory = memory,
                        latestText = snapshot.turns.lastOrNull()?.text.orEmpty(),
                    )
                val turns =
                    retrieved
                        .turns
                        .takeLast(MAX_LANGOU_TURNS)
                        .map { turn ->
                            ConversationTurn(role = turn.role, text = turn.text)
                        }
                val context =
                    focusedSuggestionContext(turns)
                        .takeLast(MAX_LANGOU_GATE_CONTEXT_CHARACTERS)
                if (
                    langouSuggestionGate.evaluate(
                        signals = signals,
                        conversationId = identity.id,
                        context = context,
                        trigger = trigger,
                    ) !=
                    SuggestionDecision.Generate
                ) {
                    Timber.i(
                        "Langou suggestions skipped reason=gate package=%s trigger=%s turns=%s",
                        packageName,
                        trigger,
                        turns.size,
                    )
                    return@launch
                }
                Timber.i(
                    "Langou suggestions generating package=%s trigger=%s turns=%s memory=%s",
                    packageName,
                    trigger,
                    turns.size,
                    retrieved.summary.isNotBlank(),
                )
                inputView?.showAiLoading(preserveExistingSuggestions = debugAiSuggestions.isNotEmpty())
                var requestSucceeded = false
                try {
                    val session = langouSessionManager.validSession()
                    val suggestionTexts = mutableListOf<String>()
                    val requestJob = coroutineContext[Job]
                    val suggestionRequest =
                        SuggestionRequest(
                            requestId =
                                "req_${UUID.randomUUID().toString().replace("-", "")}",
                            deviceId = session.deviceId,
                            application = application,
                            locale = Locale.getDefault().toLanguageTag().take(16),
                            turns = turns,
                            conversationId = identity.id,
                            memorySummary = retrieved.summary.takeIf(String::isNotBlank),
                            trigger =
                                if (trigger == SuggestionTrigger.ManualRefresh) {
                                    "manual_refresh"
                                } else {
                                    "context_change"
                                },
                            saveHistory =
                                langouSettings.getBoolean(
                                    LangouPreferences.SAVE_HISTORY,
                                    true,
                                ),
                        )
                    langouApi.streamSuggestions(
                        bearerToken = session.tokens.accessToken,
                        request = suggestionRequest,
                        onMemory = onMemory@{ update ->
                            if (requestJob?.isActive != true) return@onMemory
                            val activeIdentity =
                                resolveActiveConversationIdentity(packageName, application)
                            if (activeIdentity?.id != identity.id) return@onMemory
                            lifecycleScope.launch(Dispatchers.IO) {
                                runCatching {
                                    langouMemoryController.saveSummary(
                                        requestIdentity = identity,
                                        activeConversationId = activeIdentity.id,
                                        updateConversationId = update.conversationId,
                                        summary = update.summary,
                                    )
                                }.onFailure { failure ->
                                    Timber.w(
                                        "Encrypted summary unavailable: ${failure.javaClass.simpleName}",
                                    )
                                }
                            }
                        },
                    ) { suggestion ->
                        val currentSuggestions =
                            synchronized(suggestionTexts) {
                                suggestionTexts += suggestion.text
                                suggestionTexts.toList()
                            }
                        lifecycleScope.launch {
                            if (requestJob?.isActive != true) return@launch
                            Timber.i(
                                "Langou suggestions received package=%s count=%s",
                                packageName,
                                currentSuggestions.size,
                            )
                            inputView?.showAiSuggestions(
                                values = currentSuggestions,
                                onRefresh = {
                                    scheduleLangouSuggestions(
                                        currentInputEditorInfo,
                                        SuggestionTrigger.ManualRefresh,
                                    )
                                },
                                onForget = {
                                    forgetActiveConversationMemory(
                                        packageName = packageName,
                                        application = application,
                                        requestIdentity = identity,
                                    )
                                },
                                onSelect = { text ->
                                    commitText(text)
                                    debugAiSuggestions = emptyList()
                                    inputView?.dismissAiSuggestions()
                                },
                            )
                            debugAiSuggestions = currentSuggestions
                        }
                    }
                    requestSucceeded =
                        synchronized(suggestionTexts) { suggestionTexts.isNotEmpty() }
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (failure: Exception) {
                    Timber.w(
                        "Langou AI unavailable: ${failure.javaClass.simpleName}",
                    )
                    inputView?.showAiRetry {
                        scheduleLangouSuggestions(
                            currentInputEditorInfo,
                            SuggestionTrigger.ManualRefresh,
                        )
                    }
                } finally {
                    langouSuggestionGate.complete(identity.id, context, requestSucceeded)
                }
            }
    }

    private fun resolveActiveConversationIdentity(
        packageName: String,
        application: String,
    ): ConversationIdentity? {
        val snapshot = ContextSnapshotStore.get(packageName) ?: return null
        if (snapshot.packageName != currentInputEditorInfo?.packageName) return null
        return langouIdentityResolver.resolve(
            application = application,
            conversationHint = snapshot.conversationHint,
            confidence = snapshot.identityConfidence,
            contextSeed = stableConversationSeed(snapshot.turns),
        )
    }

    private fun stableConversationSeed(turns: List<com.osfans.trime.langou.context.ChatTurn>): String =
        turns
            .take(3)
            .joinToString("\n") { turn ->
                "${turn.role}:${turn.text.trim().replace(Regex("\\s+"), " ").take(80)}"
            }

    private fun focusedSuggestionContext(turns: List<ConversationTurn>): String {
        val latestOtherIndex = turns.indexOfLast { it.role == "other" }
        val focusedTurns =
            if (latestOtherIndex >= 0) {
                turns.subList(maxOf(0, latestOtherIndex - 3), latestOtherIndex + 1)
            } else {
                turns.takeLast(4)
            }
        return focusedTurns.joinToString("\n") { turn -> "${turn.role}:${turn.text}" }
    }

    private fun forgetActiveConversationMemory(
        packageName: String,
        application: String,
        requestIdentity: ConversationIdentity,
    ) {
        val activeIdentity =
            resolveActiveConversationIdentity(packageName, application) ?: return
        if (activeIdentity.id != requestIdentity.id) return
        lifecycleScope.launch {
            if (requestIdentity.persistent) {
                withContext(Dispatchers.IO) {
                    langouMemoryController.deleteConversation(requestIdentity.id)
                }
            } else {
                langouEphemeralMemories.remove(requestIdentity.id)
            }
            langouSuggestionGate.reset()
            inputView?.dismissAiSuggestions()
            Toast
                .makeText(
                    this@TrimeInputMethodService,
                    R.string.langou_current_memory_cleared,
                    Toast.LENGTH_SHORT,
                ).show()
        }
    }

    private suspend fun loadConversationMemory(
        identity: ConversationIdentity,
        capturedTurns: List<StoredTurn>,
    ): ConversationMemory {
        val now = System.currentTimeMillis()
        val fallback =
            ConversationMemory(
                conversationId = identity.id,
                turns = capturedTurns,
                updatedAtEpochMillis = now,
            )
        if (identity.persistent) {
            return withContext(Dispatchers.IO) {
                runCatching {
                    langouMemoryStore.merge(identity.id, capturedTurns)
                }.onFailure { failure ->
                    Timber.w(
                        "Encrypted chat memory unavailable: ${failure.javaClass.simpleName}",
                    )
                }.getOrDefault(fallback)
            }
        }

        val existing = langouEphemeralMemories[identity.id]
        val mergedTurns =
            (existing?.turns.orEmpty() + capturedTurns)
                .filter { now - it.capturedAtEpochMillis < LANGOU_MEMORY_RETENTION_MILLIS }
                .fold(mutableListOf<StoredTurn>()) { result, turn ->
                    if (result.lastOrNull()?.let { it.role == turn.role && it.text == turn.text } != true) {
                        result += turn
                    }
                    result
                }.takeLast(MAX_LANGOU_MEMORY_TURNS)
        return ConversationMemory(
            conversationId = identity.id,
            turns = mergedTurns,
            summary = existing?.summary.orEmpty(),
            updatedAtEpochMillis = now,
        ).also { memory -> langouEphemeralMemories[identity.id] = memory }
    }

    fun commitText(text: String) {
        val ic = currentInputConnection ?: return

        // when composing text equals commit content, finish composing text as-is
        if (composingText.isNotEmpty() && composingText == text) {
            ic.finishComposingText()
        } else {
            ic.commitText(text, 1)
        }
        lastCommittedText = text
        composingText = ""
        InputFeedbackManager.textCommitSpeak(text)
    }

    /**
     * Constructs a meta state integer flag which can be used for setting the `metaState` field when sending a KeyEvent
     * to the input connection. If this method is called without a meta modifier set to true, the default value `0` is
     * returned.
     *
     * @param ctrl Set to true to enable the CTRL meta modifier. Defaults to false.
     * @param alt Set to true to enable the ALT meta modifier. Defaults to false.
     * @param shift Set to true to enable the SHIFT meta modifier. Defaults to false.
     *
     * @return An integer containing all meta flags passed and formatted for use in a [KeyEvent].
     */
    fun meta(
        alt: Boolean = false,
        ctrl: Boolean = false,
        shift: Boolean = false,
        meta: Boolean = false,
        sym: Boolean = false,
    ): Int {
        var metaState = 0
        if (alt) metaState = KeyEvent.META_ALT_ON or KeyEvent.META_ALT_LEFT_ON
        if (ctrl) metaState = metaState or KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON
        if (shift) metaState = metaState or KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON
        if (meta) metaState = metaState or KeyEvent.META_META_ON or KeyEvent.META_META_LEFT_ON
        if (sym) metaState = metaState or KeyEvent.META_SYM_ON
        return metaState
    }

    private fun sendDownKeyEvent(
        eventTime: Long,
        keyEventCode: Int,
        metaState: Int = 0,
    ): Boolean {
        val ic = currentInputConnection ?: return false
        return ic.sendKeyEvent(
            KeyEvent(
                eventTime,
                eventTime,
                KeyEvent.ACTION_DOWN,
                keyEventCode,
                0,
                metaState,
                KeyCharacterMap.VIRTUAL_KEYBOARD,
                0,
                KeyEvent.FLAG_SOFT_KEYBOARD or KeyEvent.FLAG_KEEP_TOUCH_MODE,
                InputDevice.SOURCE_KEYBOARD,
            ),
        )
    }

    private fun sendUpKeyEvent(
        eventTime: Long,
        keyEventCode: Int,
        metaState: Int = 0,
    ): Boolean {
        val ic = currentInputConnection ?: return false
        return ic.sendKeyEvent(
            KeyEvent(
                eventTime,
                SystemClock.uptimeMillis(),
                KeyEvent.ACTION_UP,
                keyEventCode,
                0,
                metaState,
                KeyCharacterMap.VIRTUAL_KEYBOARD,
                0,
                KeyEvent.FLAG_SOFT_KEYBOARD or KeyEvent.FLAG_KEEP_TOUCH_MODE,
                InputDevice.SOURCE_KEYBOARD,
            ),
        )
    }

    /**
     * Same as [InputMethodService.sendDownUpKeyEvents] but also allows to set meta state.
     *
     * @param keyEventCode The key code to send, use a key code defined in Android's [KeyEvent].
     * @param metaState Flags indicating which meta keys are currently pressed.
     *
     * @return True on success, false if an error occurred or the input connection is invalid.
     */
    fun sendDownUpKeyEvent(
        keyEventCode: Int,
        metaState: Int = meta(),
    ): Boolean {
        val eventTime = SystemClock.uptimeMillis()
        if (metaState and KeyEvent.META_ALT_ON != 0) {
            sendDownKeyEvent(eventTime, KeyEvent.KEYCODE_ALT_LEFT)
        }
        if (metaState and KeyEvent.META_CTRL_ON != 0) {
            sendDownKeyEvent(eventTime, KeyEvent.KEYCODE_CTRL_LEFT)
        }
        if (metaState and KeyEvent.META_SHIFT_ON != 0) {
            sendDownKeyEvent(eventTime, KeyEvent.KEYCODE_SHIFT_LEFT)
        }
        if (metaState and KeyEvent.META_META_ON != 0) {
            sendDownKeyEvent(eventTime, KeyEvent.KEYCODE_META_LEFT)
        }
        if (metaState and KeyEvent.META_SYM_ON != 0) {
            sendDownKeyEvent(eventTime, KeyEvent.KEYCODE_SYM)
        }
        sendDownKeyEvent(eventTime, keyEventCode, metaState)
        sendUpKeyEvent(eventTime, keyEventCode, metaState)
        if (metaState and KeyEvent.META_SYM_ON != 0) {
            sendUpKeyEvent(eventTime, KeyEvent.KEYCODE_SYM)
        }
        if (metaState and KeyEvent.META_META_ON != 0) {
            sendUpKeyEvent(eventTime, KeyEvent.KEYCODE_META_LEFT)
        }
        if (metaState and KeyEvent.META_SHIFT_ON != 0) {
            sendUpKeyEvent(eventTime, KeyEvent.KEYCODE_SHIFT_LEFT)
        }
        if (metaState and KeyEvent.META_CTRL_ON != 0) {
            sendUpKeyEvent(eventTime, KeyEvent.KEYCODE_CTRL_LEFT)
        }
        if (metaState and KeyEvent.META_ALT_ON != 0) {
            sendUpKeyEvent(eventTime, KeyEvent.KEYCODE_ALT_LEFT)
        }
        return true
    }

    private fun forwardKeyEvent(event: KeyEvent): Boolean {
        val keyVal = KeyValue.fromKeyEvent(event)
        if (keyVal.value != RimeKeyMapping.RimeKey_VoidSymbol) {
            val modifiers = KeyModifiers.fromKeyEvent(event)
            postRimeJob {
                processKey(keyVal, modifiers, isVirtual = false)
            }
            return true
        }
        Timber.d("Skipped KeyEvent: $event")
        return false
    }

    override fun onKeyDown(
        keyCode: Int,
        event: KeyEvent,
    ): Boolean {
        if (inputDeviceManager.evaluateOnKeyDown(event, this)) {
            decorLocationUpdated = false
            forceShowSelf()
        }
        return forwardKeyEvent(event) || super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(
        keyCode: Int,
        event: KeyEvent,
    ): Boolean = forwardKeyEvent(event) || super.onKeyUp(keyCode, event)

    // Added in API level 14, deprecated in 29
    // it's needed because editors still use it even on API 36
    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onViewClicked(focusChanged: Boolean) {
        super.onViewClicked(focusChanged)
        inputDeviceManager.evaluateOnViewClicked(this)
    }

    @RequiresApi(34)
    override fun onUpdateEditorToolType(toolType: Int) {
        super.onUpdateEditorToolType(toolType)
        inputDeviceManager.evaluateOnUpdateEditorToolType(toolType, this)
    }

    fun switchToPrevIme() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                switchToPreviousInputMethod()
            } else {
                @Suppress("DEPRECATION")
                inputMethodManager.switchToLastInputMethod(window.window!!.attributes.token)
            }
        } catch (e: Exception) {
            Timber.e(e, "Unable to switch to the previous IME.")
            inputMethodManager.showInputMethodPicker()
        }
    }

    fun switchToNextIme() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                switchToNextInputMethod(false)
            } else {
                @Suppress("DEPRECATION")
                inputMethodManager.switchToNextInputMethod(window.window!!.attributes.token, false)
            }
        } catch (e: Exception) {
            Timber.e(e, "Unable to switch to the next IME.")
            inputMethodManager.showInputMethodPicker()
        }
    }

    fun shareText(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val ic = currentInputConnection ?: return false
            val cs = ic.getSelectedText(0)
            if (cs == null) ic.performContextMenuAction(android.R.id.selectAll)
            return ic.performContextMenuAction(android.R.id.shareText)
        }
        return false
    }

    /** 編輯操作 */
    fun hookKeyboard(
        code: Int,
        mask: Int,
    ): Boolean {
        val ic = currentInputConnection ?: return false
        // 没按下 Ctrl 键
        if (mask != KeyEvent.META_CTRL_ON) {
            return false
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (prefs.keyboard.hookCtrlZY.getValue()) {
                when (code) {
                    KeyEvent.KEYCODE_Y -> return ic.performContextMenuAction(android.R.id.redo)
                    KeyEvent.KEYCODE_Z -> return ic.performContextMenuAction(android.R.id.undo)
                }
            }
        }

        when (code) {
            KeyEvent.KEYCODE_A -> {
                // 全选
                return if (prefs.keyboard.hookCtrlA.getValue()) {
                    ic.performContextMenuAction(android.R.id.selectAll)
                } else {
                    false
                }
            }

            KeyEvent.KEYCODE_X -> {
                // 剪切
                if (prefs.keyboard.hookCtrlCV.getValue()) {
                    val etr = ExtractedTextRequest()
                    etr.token = 0
                    val et = ic.getExtractedText(etr, 0)
                    if (et != null) {
                        if (et.selectionStart != et.selectionEnd) return ic.performContextMenuAction(android.R.id.cut)
                    }
                }
                Timber.w("hookKeyboard cut fail")
                return false
            }

            KeyEvent.KEYCODE_C -> {
                // 复制
                if (prefs.keyboard.hookCtrlCV.getValue()) {
                    val etr = ExtractedTextRequest()
                    etr.token = 0
                    val et = ic.getExtractedText(etr, 0)
                    if (et != null) {
                        if (et.selectionStart != et.selectionEnd) {
                            ic.performContextMenuAction(android.R.id.copy).also { result ->
                                if (result) {
                                    clearTextSelection()
                                }
                                return result
                            }
                        }
                    }
                }
                Timber.w("hookKeyboard copy fail")
                return false
            }

            KeyEvent.KEYCODE_V -> {
                // 粘贴
                if (prefs.keyboard.hookCtrlCV.getValue()) {
                    val etr = ExtractedTextRequest()
                    etr.token = 0
                    val et = ic.getExtractedText(etr, 0)
                    if (et == null) {
                        Timber.d("hookKeyboard paste, et == null, try commitText")
                        val clipboardText = clipboardManager.primaryClip?.getItemAt(0)?.coerceToText(this)
                        if (ic.commitText(clipboardText, 1)) {
                            return true
                        }
                    } else if (ic.performContextMenuAction(android.R.id.paste)) {
                        return true
                    }
                    Timber.w("hookKeyboard paste fail")
                }
                return false
            }

            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (prefs.keyboard.hookCtrlLR.getValue()) {
                    val etr = ExtractedTextRequest()
                    etr.token = 0
                    val et = ic.getExtractedText(etr, 0)
                    if (et != null) {
                        val moveTo = et.text.findSectionFrom(et.startOffset + et.selectionEnd)
                        ic.setSelection(moveTo, moveTo)
                        return true
                    }
                }
            }

            KeyEvent.KEYCODE_DPAD_LEFT ->
                if (prefs.keyboard.hookCtrlLR.getValue()) {
                    val etr = ExtractedTextRequest()
                    etr.token = 0
                    val et = ic.getExtractedText(etr, 0)
                    if (et != null) {
                        val moveTo = et.text.findSectionFrom(et.startOffset + et.selectionStart, true)
                        ic.setSelection(moveTo, moveTo)
                        return true
                    }
                }
        }
        return false
    }

    fun clearTextSelection() {
        val ic = currentInputConnection ?: return
        val etr = ExtractedTextRequest().apply { token = 0 }
        val et = currentInputConnection.getExtractedText(etr, 0)
        et?.let {
            if (it.selectionStart != it.selectionEnd) {
                ic.setSelection(it.selectionEnd, it.selectionEnd)
            }
        }
    }

    internal fun updateComposingText(text: String) {
        val ic = currentInputConnection ?: return
        ic.beginBatchEdit()
        if (composingText.isNotEmpty() || text.isNotEmpty()) {
            if (!ic.getSelectedText(0).isNullOrEmpty()) {
                ic.deleteSurroundingText(1, 0)
            }
            ic.setComposingText(text, 1)
            if (text.isEmpty()) {
                ic.finishComposingText()
            }
        }
        composingText = text
        ic.endBatchEdit()
    }

    fun getActiveText(type: Int): String {
        val rimeComposition = rime.run { compositionCached }
        val selected = currentInputConnection?.getSelectedText(0)?.toString()
        val commitPreview = rimeComposition.commitTextPreview
        val preedit = rimeComposition.preedit ?: ""
        val beforeCursor = getTextAroundCursor(1024, before = true) ?: ""
        val afterCursor = getTextAroundCursor(before = false) ?: ""
        val lastCommitted = lastCommittedText

        return sequenceOf(
            when (type) {
                2 -> preedit
                3 -> selected
                4 -> beforeCursor
                1 -> lastCommitted
                else -> null
            },
            commitPreview,
            selected,
            lastCommitted,
            beforeCursor,
            afterCursor,
        )
            .firstOrNull { it?.isNotEmpty() == true } ?: ""
    }

    private fun getTextAroundCursor(
        initialStep: Int = 1024,
        before: Boolean,
    ): String? {
        val ic = currentInputConnection ?: return null
        var step = initialStep
        while (true) {
            val text = (if (before) ic.getTextBeforeCursor(step, 0) else ic.getTextAfterCursor(step, 0)) ?: return ""
            if (text.length < step) return text.toString()
            step *= 2
        }
    }

    override fun onEvaluateFullscreenMode(): Boolean = false

    private var showingDialog: Dialog? = null

    fun showDialog(dialog: Dialog) {
        showingDialog?.dismiss()
        dialog.window?.also {
            it.attributes.apply {
                token = decorView.windowToken
                type = WindowManager.LayoutParams.TYPE_APPLICATION_ATTACHED_DIALOG
            }
            it.addFlags(
                WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM or WindowManager.LayoutParams.FLAG_DIM_BEHIND,
            )
            it.setDimAmount(styledFloat(android.R.attr.backgroundDimAmount))
        }
        dialog.setOnDismissListener {
            showingDialog = null
        }
        dialog.show()
        showingDialog = dialog
    }

    companion object {
        @Volatile
        private var activeInstance: TrimeInputMethodService? = null

        @Volatile
        private var debugAiSuggestions: List<String> = emptyList()

        @JvmStatic
        fun debugSelectedSchemaId(): String = activeInstance?.debugSelectedSchemaId.orEmpty()

        @JvmStatic
        fun debugActiveKeyboardId(): String = activeInstance?.debugActiveKeyboardId.orEmpty()

        @JvmStatic
        fun debugAiSuggestionTexts(): List<String> = activeInstance?.debugAiSuggestionTexts.orEmpty()

        const val LANGOU_SUGGESTION_DEBOUNCE_MS = 80L
        const val MAX_LANGOU_TURNS = 12
        const val MAX_LANGOU_MEMORY_TURNS = 100
        const val LANGOU_MEMORY_RETENTION_MILLIS = 30L * 24L * 60L * 60L * 1_000L
        const val MAX_LANGOU_GATE_CONTEXT_CHARACTERS = 2_000
    }
}
