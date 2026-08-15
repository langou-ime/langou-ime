// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ui.setup

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.os.bundleOf
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.osfans.trime.R
import com.osfans.trime.daemon.RimeDaemon
import com.osfans.trime.daemon.RimeSession
import com.osfans.trime.databinding.ActivitySetupBinding
import com.osfans.trime.langou.theme.LangouPinyinLayout
import com.osfans.trime.ui.setup.SetupPage.Companion.availablePages
import com.osfans.trime.ui.setup.SetupPage.Companion.firstUndonePage
import kotlinx.coroutines.launch
import timber.log.Timber

class SetupActivity : FragmentActivity() {
    private lateinit var binding: ActivitySetupBinding
    private lateinit var viewPager: ViewPager2
    private lateinit var rime: RimeSession
    private val pages by lazy { availablePages() }
    private val viewModel: SetupViewModel by viewModels()
    private var keyboardPreparationFinished = false

    companion object {
        private var binaryCount = false

        fun shouldSetup() = !binaryCount && SetupPage.hasUndonePage()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivitySetupBinding.inflate(layoutInflater)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, windowInsets ->
            val sysBars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.root.setPadding(
                sysBars.left,
                sysBars.top,
                sysBars.right,
                sysBars.bottom,
            )
            windowInsets
        }
        setContentView(binding.root)
        // The system already requires one explicit confirmation for each protected capability.
        // Do not add redundant Previous/Next taps around those confirmations.
        binding.prevButton.visibility = android.view.View.GONE
        binding.nextButton.visibility = android.view.View.GONE
        binding.skipButton.apply {
            text = getString(R.string.setup__skip)
            setOnClickListener { finish() }
        }
        viewPager = binding.viewpager
        viewPager.adapter = Adapter()
        viewPager.isUserInputEnabled = false
        viewModel.permissionsDone.observe(this) { updateCompletionState() }
        viewModel.keyboardReady.observe(this) { updateCompletionState() }
        // Skip to undone page
        firstUndonePage()?.let { viewPager.currentItem = it.ordinal }
        binaryCount = true
        rime = RimeDaemon.createSession(javaClass.name)
        prepareChineseKeyboard()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus) return
        val fragment = supportFragmentManager.findFragmentByTag("f${viewPager.currentItem}")
        (fragment as? SetupFragment)?.sync()
        val currentIndex = viewPager.currentItem
        val navigation =
            SetupNavigator.next(
                currentIndex = currentIndex,
                doneStates = pages.map(SetupPage::isDone),
            )
        when {
            navigation.nextIndex == null -> {
                viewModel.permissionsDone.value = true
            }
            navigation.nextIndex != currentIndex -> {
                val nextIndex = navigation.nextIndex
                viewPager.setCurrentItem(nextIndex, true)
                if (navigation.launchAction) {
                    // Let ViewPager settle before opening the next Android confirmation screen.
                    // A cancelled screen is not relaunched because its page remains unfinished.
                    viewPager.post {
                        if (!isFinishing &&
                            viewPager.currentItem == nextIndex &&
                            !pages[nextIndex].isDone()
                        ) {
                            pages[nextIndex].getButtonAction(this)
                        }
                    }
                }
            }
        }
    }

    private fun prepareChineseKeyboard() {
        keyboardPreparationFinished = false
        viewModel.keyboardReady.value = false
        updateCompletionState()
        lifecycleScope.launch {
            val ready =
                runCatching {
                    rime.runOnReady {
                        deploySchema(LangouPinyinLayout.FULL_PINYIN_SCHEMA)
                        deploySchema(LangouPinyinLayout.NINE_KEY_SCHEMA)
                        var selected = selectSchema(LangouPinyinLayout.FULL_PINYIN_SCHEMA)
                        if (!selected) {
                            deploy()
                            selected = selectSchema(LangouPinyinLayout.FULL_PINYIN_SCHEMA)
                        }
                        selected
                    }
                }.onFailure { failure ->
                    Timber.e(failure, "Unable to prepare Langou Chinese keyboard during setup")
                }.getOrDefault(false)
            keyboardPreparationFinished = true
            viewModel.keyboardReady.value = ready
        }
    }

    private fun updateCompletionState() {
        if (viewModel.canFinish()) {
            finish()
            return
        }
        if (viewModel.permissionsDone.value == true) {
            binding.skipButton.apply {
                if (keyboardPreparationFinished) {
                    isEnabled = true
                    text = getString(R.string.setup__retry_keyboard)
                    setOnClickListener { prepareChineseKeyboard() }
                } else {
                    isEnabled = false
                    text = getString(R.string.setup__preparing_keyboard)
                }
            }
        }
    }

    override fun onDestroy() {
        if (::rime.isInitialized) {
            RimeDaemon.destroySession(javaClass.name)
        }
        super.onDestroy()
    }

    private inner class Adapter : FragmentStateAdapter(this) {
        override fun getItemCount(): Int = pages.size

        override fun createFragment(position: Int): Fragment = SetupFragment().apply {
            arguments = bundleOf("page" to pages[position])
        }
    }
}
