/*
 * SPDX-FileCopyrightText: 2026 Langou Input Method contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.bar.ui

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isVisible
import com.osfans.trime.R
import com.osfans.trime.data.theme.ColorManager
import com.osfans.trime.langou.ai.AiSuggestionSelection
import splitties.dimensions.dp
import splitties.views.dsl.core.Ui

class AiSuggestionsUi(
    override val ctx: Context,
) : Ui {
    private val content =
        LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(4), dp(4), dp(4))
        }

    private var selection = AiSuggestionSelection {}
    private val suggestionChips =
        List(MAX_SUGGESTIONS) {
            createChip("", multiline = true).also { chip ->
                chip.isVisible = false
                chip.setOnClickListener { selection.selectDisplayed(chip.text) }
            }
        }
    private val refreshChip =
        createChip(ctx.getString(R.string.langou_ai_refresh)).apply {
            setOnClickListener { refreshAction() }
        }
    private val forgetChip =
        createChip(ctx.getString(R.string.langou_ai_forget_chat)).apply {
            setOnClickListener { forgetAction() }
        }
    private var refreshAction: () -> Unit = {}
    private var forgetAction: () -> Unit = {}
    private var suggestionLayoutAttached = false

    override val root: HorizontalScrollView =
        HorizontalScrollView(ctx).apply {
            isHorizontalScrollBarEnabled = false
            isFillViewport = false
            addView(
                content,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                ),
            )
        }

    fun showLoading() {
        selection.update(emptyList())
        suggestionLayoutAttached = false
        content.removeAllViews()
        content.addView(createMascot(), mascotLayoutParams())
        content.addView(
            createChip(ctx.getString(R.string.langou_ai_generating), enabled = false),
            chipLayoutParams(),
        )
    }

    fun showContextLoading() {
        selection.update(emptyList())
        suggestionLayoutAttached = false
        content.removeAllViews()
        content.addView(createMascot(), mascotLayoutParams())
        content.addView(
            createChip(ctx.getString(R.string.langou_ai_reading_context), enabled = false),
            chipLayoutParams(),
        )
    }

    fun showRetry(onClick: () -> Unit) {
        selection.update(emptyList())
        suggestionLayoutAttached = false
        content.removeAllViews()
        content.addView(createMascot(), mascotLayoutParams())
        content.addView(
            createChip(ctx.getString(R.string.langou_ai_retry)).apply {
                setOnClickListener { onClick() }
            },
            chipLayoutParams(),
        )
    }

    fun showPermissionRequired(onClick: () -> Unit) {
        selection.update(emptyList())
        suggestionLayoutAttached = false
        content.removeAllViews()
        content.addView(createMascot(), mascotLayoutParams())
        content.addView(
            createChip(ctx.getString(R.string.langou_ai_permission_required)).apply {
                setOnClickListener { onClick() }
            },
            chipLayoutParams(),
        )
    }

    fun showSuggestions(
        values: List<String>,
        onRefresh: () -> Unit,
        onForget: () -> Unit,
        onSelect: (String) -> Unit,
    ): Boolean {
        selection = AiSuggestionSelection(onSelect).apply { update(values) }
        refreshAction = onRefresh
        forgetAction = onForget
        ensureSuggestionLayout()
        suggestionChips.forEachIndexed { index, chip ->
            chip.text = selection.items.getOrNull(index).orEmpty()
            chip.isVisible = index < selection.items.size
        }
        root.post { root.scrollTo(0, 0) }
        return selection.items.isNotEmpty()
    }

    fun clear() {
        selection.update(emptyList())
        suggestionLayoutAttached = false
        content.removeAllViews()
    }

    private fun ensureSuggestionLayout() {
        if (suggestionLayoutAttached) return
        content.removeAllViews()
        content.addView(createMascot(), mascotLayoutParams())
        suggestionChips.forEach { chip ->
            content.addView(chip, chipLayoutParams())
        }
        content.addView(refreshChip, compactChipLayoutParams())
        content.addView(forgetChip, compactChipLayoutParams())
        suggestionLayoutAttached = true
    }

    private fun createChip(
        value: String,
        enabled: Boolean = true,
        multiline: Boolean = false,
    ): TextView =
        TextView(ctx).apply {
            text = value
            isSingleLine = !multiline
            maxLines = if (multiline) 2 else 1
            ellipsize = TextUtils.TruncateAt.END
            isClickable = enabled
            gravity = Gravity.CENTER
            setPadding(dp(14), 0, dp(14), 0)
            if (multiline) {
                maxWidth = ctx.dp(MAX_SUGGESTION_WIDTH_DP)
            }
            setTextColor(ColorManager.getColor("candidate_text_color"))
            alpha = if (enabled) 1f else 0.72f
            background =
                GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(16).toFloat()
                    setColor(ColorManager.getColor("hilited_candidate_back_color"))
                }
        }

    private fun createMascot() =
        ImageView(ctx).apply {
            setImageResource(R.drawable.langou_ai_status)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            contentDescription = null
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }

    private fun mascotLayoutParams() =
        LinearLayout.LayoutParams(
            ctx.dp(32),
            LinearLayout.LayoutParams.MATCH_PARENT,
        ).apply {
            marginEnd = ctx.dp(4)
        }

    private fun chipLayoutParams() =
        LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.MATCH_PARENT,
        ).apply {
            marginEnd = ctx.dp(6)
        }

    private fun compactChipLayoutParams() =
        chipLayoutParams().apply {
            marginEnd = ctx.dp(4)
        }

    private companion object {
        const val MAX_SUGGESTIONS = 3
        const val MAX_SUGGESTION_WIDTH_DP = 280
    }
}
