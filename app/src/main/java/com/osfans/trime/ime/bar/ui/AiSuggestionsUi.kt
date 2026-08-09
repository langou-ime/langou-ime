/*
 * SPDX-FileCopyrightText: 2026 Langou Input Method contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.bar.ui

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
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
        content.removeAllViews()
        content.addView(createMascot(), mascotLayoutParams())
        content.addView(
            createChip(ctx.getString(R.string.langou_ai_thinking), enabled = false),
            chipLayoutParams(),
        )
    }

    fun showPermissionRequired(onClick: () -> Unit) {
        selection.update(emptyList())
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
        content.removeAllViews()
        content.addView(createMascot(), mascotLayoutParams())
        content.addView(
            createChip(ctx.getString(R.string.langou_ai_refresh)).apply {
                setOnClickListener { onRefresh() }
            },
            compactChipLayoutParams(),
        )
        content.addView(
            createChip(ctx.getString(R.string.langou_ai_forget_chat)).apply {
                setOnClickListener { onForget() }
            },
            compactChipLayoutParams(),
        )
        selection.items.forEachIndexed { index, text ->
            content.addView(
                createChip(text).apply {
                    setOnClickListener { selection.select(index) }
                },
                chipLayoutParams(),
            )
        }
        return selection.items.isNotEmpty()
    }

    fun clear() {
        selection.update(emptyList())
        content.removeAllViews()
    }

    private fun createChip(
        value: String,
        enabled: Boolean = true,
    ): TextView =
        TextView(ctx).apply {
            text = value
            isSingleLine = true
            isClickable = enabled
            gravity = Gravity.CENTER
            setPadding(dp(14), 0, dp(14), 0)
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
}
